package com.company.kafka.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown for errors that are not recoverable through retry.
 * 
 * This is a non-retryable exception - the error indicates a permanent
 * failure condition that will not be resolved by retrying the operation.
 * These messages should typically be sent to a dead letter topic for
 * manual investigation and resolution.
 * 
 * Common scenarios that trigger NonRecoverableException:
 * - Authentication failures (invalid credentials, expired tokens)
 * - Authorization errors (insufficient permissions, access denied)
 * - Configuration errors (invalid topic names, missing properties)
 * - Unsupported operation requests (deprecated API calls)
 * - Data integrity violations (foreign key constraints, unique violations)
 * - Malformed data that cannot be parsed or processed
 * - Business rule violations that cannot be automatically resolved
 * - Security violations (suspicious activity, blocked IP addresses)
 * 
 * Production Best Practices:
 * 1. Always acknowledge NonRecoverableException to prevent infinite retry
 * 2. Log these errors with full context for debugging and analysis
 * 3. Route to dead letter topics for manual investigation
 * 4. Include detailed error information for troubleshooting
 * 5. Consider alerting for high volumes of non-recoverable errors
 * 6. Implement error classification to identify patterns
 * 7. Monitor error codes and categories for operational insights
 * 
 * Dead Letter Topic Strategy:
 * - Route to topic-specific DLT (e.g., orders.NON_RECOVERABLE.DLT)
 * - Include error metadata in headers (error code, timestamp, context)
 * - Preserve original message for manual processing
 * - Implement DLT monitoring and alerting
 * - Provide tools for DLT message reprocessing after fixes
 * 
 * Example Usage:
 * <pre>
 * try {
 *     authenticateAndAuthorize(request);
 *     processMessage(message);
 *     acknowledgment.acknowledge();
 * } catch (NonRecoverableException e) {
 *     // Always acknowledge to prevent retry
 *     acknowledgment.acknowledge();
 *     log.error("Non-recoverable error: {} [Code: {}]", 
 *         e.getMessage(), e.getErrorCode());
 *     metrics.counter("non_recoverable.errors", 
 *         "error_code", e.getErrorCode()).increment();
 *     // Will be routed to DLT by error handler
 * }
 * </pre>
 * 
 * @see ValidationException for data validation errors
 * @see RecoverableException for temporary failures that should be retried
 */
public class NonRecoverableException extends RuntimeException {
    
    private final String errorCode;
    private final Map<String, Object> context;
    private final String correlationId;
    private final long timestamp;
    
    /**
     * Creates a NonRecoverableException with a simple error message.
     * 
     * @param message the error description
     */
    public NonRecoverableException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
        this.context = new HashMap<>();
        this.correlationId = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Creates a NonRecoverableException with an error code.
     * 
     * @param message the error description
     * @param errorCode the specific error code for categorization
     */
    public NonRecoverableException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN";
        this.context = new HashMap<>();
        this.correlationId = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Creates a NonRecoverableException with error code and context.
     * 
     * @param message the error description
     * @param errorCode the specific error code
     * @param context additional context information
     */
    public NonRecoverableException(String message, String errorCode, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN";
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.correlationId = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Creates a NonRecoverableException with full context information.
     * 
     * @param message the error description
     * @param errorCode the specific error code
     * @param context additional context information
     * @param correlationId the correlation ID for distributed tracing
     */
    public NonRecoverableException(String message, String errorCode, 
            Map<String, Object> context, String correlationId) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN";
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.correlationId = correlationId;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Creates a NonRecoverableException with a cause.
     * 
     * @param message the error description
     * @param cause the underlying cause
     */
    public NonRecoverableException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
        this.context = new HashMap<>();
        this.correlationId = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Creates a NonRecoverableException with cause and error code.
     * 
     * @param message the error description
     * @param errorCode the specific error code
     * @param cause the underlying cause
     */
    public NonRecoverableException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode != null ? errorCode : "UNKNOWN";
        this.context = new HashMap<>();
        this.correlationId = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Gets the specific error code for categorization and monitoring.
     * 
     * Common error codes:
     * - AUTH_FAILED: Authentication failure
     * - ACCESS_DENIED: Authorization failure
     * - CONFIG_ERROR: Configuration problem
     * - DATA_INTEGRITY: Data integrity violation
     * - UNSUPPORTED_OP: Unsupported operation
     * - SECURITY_VIOLATION: Security policy violation
     * 
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the additional context information.
     * 
     * Context may include:
     * - userId: The user who triggered the error
     * - resourceId: The resource that was being accessed
     * - operation: The operation that was attempted
     * - permissions: The permissions that were checked
     * - configuration: Relevant configuration values
     * 
     * @return a copy of the context map
     */
    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }
    
    /**
     * Gets the correlation ID for distributed tracing.
     * 
     * @return the correlation ID, or null if not provided
     */
    public String getCorrelationId() {
        return correlationId;
    }
    
    /**
     * Gets the timestamp when the exception was created.
     * 
     * @return the timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Adds or updates a context value.
     * 
     * @param key the context key
     * @param value the context value
     * @return this exception instance for method chaining
     */
    public NonRecoverableException withContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }
    
    /**
     * Adds multiple context values.
     * 
     * @param additionalContext the context values to add
     * @return this exception instance for method chaining
     */
    public NonRecoverableException withContext(Map<String, Object> additionalContext) {
        if (additionalContext != null) {
            this.context.putAll(additionalContext);
        }
        return this;
    }
    
    /**
     * Gets a specific context value.
     * 
     * @param key the context key
     * @return the context value, or null if not present
     */
    public Object getContextValue(String key) {
        return context.get(key);
    }
    
    /**
     * Gets a specific context value with type casting.
     * 
     * @param key the context key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the context value cast to the specified type, or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        Object value = context.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Checks if this is an authentication-related error.
     * 
     * @return true if this is an authentication error
     */
    public boolean isAuthenticationError() {
        return "AUTH_FAILED".equals(errorCode) || 
               "INVALID_CREDENTIALS".equals(errorCode) ||
               "TOKEN_EXPIRED".equals(errorCode);
    }
    
    /**
     * Checks if this is an authorization-related error.
     * 
     * @return true if this is an authorization error
     */
    public boolean isAuthorizationError() {
        return "ACCESS_DENIED".equals(errorCode) ||
               "INSUFFICIENT_PERMISSIONS".equals(errorCode) ||
               "FORBIDDEN".equals(errorCode);
    }
    
    /**
     * Checks if this is a configuration-related error.
     * 
     * @return true if this is a configuration error
     */
    public boolean isConfigurationError() {
        return "CONFIG_ERROR".equals(errorCode) ||
               "INVALID_CONFIG".equals(errorCode) ||
               "MISSING_CONFIG".equals(errorCode);
    }
    
    /**
     * Creates a formatted error message with all available context.
     * 
     * @return detailed error message for logging and monitoring
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder(getMessage());
        
        if (errorCode != null && !"UNKNOWN".equals(errorCode)) {
            sb.append(" [ErrorCode: ").append(errorCode).append("]");
        }
        
        if (correlationId != null) {
            sb.append(" [CorrelationId: ").append(correlationId).append("]");
        }
        
        if (!context.isEmpty()) {
            sb.append(" [Context: ").append(context).append("]");
        }
        
        sb.append(" [Timestamp: ").append(timestamp).append("]");
        
        return sb.toString();
    }
    
    /**
     * Creates a map suitable for structured logging and monitoring.
     * 
     * @return a map containing all error information
     */
    public Map<String, Object> toLogMap() {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("message", getMessage());
        logMap.put("errorCode", errorCode);
        logMap.put("timestamp", timestamp);
        
        if (correlationId != null) {
            logMap.put("correlationId", correlationId);
        }
        
        if (!context.isEmpty()) {
            logMap.put("context", context);
        }
        
        if (getCause() != null) {
            logMap.put("cause", getCause().getClass().getSimpleName());
            logMap.put("causeMessage", getCause().getMessage());
        }
        
        return logMap;
    }
}

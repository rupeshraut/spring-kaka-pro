package com.company.kafka.exception;

/**
 * Exception thrown for errors that may be recoverable through retry.
 * 
 * This is a retryable exception - the error may be temporary and could
 * succeed if attempted again after a delay. The retry mechanism should
 * implement exponential backoff and maximum retry limits.
 * 
 * Common scenarios that trigger RecoverableException:
 * - Network connectivity issues (temporary network partitions)
 * - Database connection timeouts or deadlocks
 * - External service unavailability (HTTP 503, connection refused)
 * - Resource contention (thread pool exhaustion, memory pressure)
 * - Temporary capacity limits (rate limiting, quota exceeded)
 * - Kafka broker unavailability during rebalancing
 * - Serialization/deserialization temporary failures
 * 
 * Production Best Practices:
 * 1. DON'T acknowledge the message - let the retry mechanism handle it
 * 2. Implement exponential backoff (1s, 2s, 4s, 8s, 16s, 32s)
 * 3. Set maximum retry limits to prevent infinite loops
 * 4. Monitor retry patterns to identify systemic issues
 * 5. Consider circuit breaker patterns for external dependencies
 * 6. Log retry attempts with correlation IDs for tracing
 * 7. Include attempt number and delay information in monitoring
 * 
 * Retry Strategy Configuration:
 * - Initial delay: 1 second
 * - Maximum delay: 32 seconds
 * - Maximum retries: 10 attempts
 * - Backoff multiplier: 2.0
 * - Jitter: 10% to prevent thundering herd
 * 
 * Example Usage:
 * <pre>
 * try {
 *     callExternalService(message);
 *     acknowledgment.acknowledge();
 * } catch (RecoverableException e) {
 *     // DON'T acknowledge - let retry mechanism handle it
 *     log.warn("Recoverable error, will retry: {}", e.getMessage());
 *     metrics.counter("recoverable.errors").increment();
 *     throw e; // Rethrow to trigger retry
 * }
 * </pre>
 * 
 * @see ValidationException for non-retryable validation errors
 * @see NonRecoverableException for permanent failures
 */
public class RecoverableException extends RuntimeException {
    
    private final String correlationId;
    private final int attemptNumber;
    private final long nextRetryDelayMs;
    private final String serviceName;
    
    /**
     * Creates a RecoverableException with a simple error message.
     * 
     * @param message the error description
     */
    public RecoverableException(String message) {
        super(message);
        this.correlationId = null;
        this.attemptNumber = 0;
        this.nextRetryDelayMs = 0;
        this.serviceName = null;
    }
    
    /**
     * Creates a RecoverableException with retry context information.
     * 
     * @param message the error description
     * @param correlationId the correlation ID for distributed tracing
     * @param attemptNumber the current attempt number (1-based)
     */
    public RecoverableException(String message, String correlationId, int attemptNumber) {
        super(message);
        this.correlationId = correlationId;
        this.attemptNumber = attemptNumber;
        this.nextRetryDelayMs = calculateNextRetryDelay(attemptNumber);
        this.serviceName = null;
    }
    
    /**
     * Creates a RecoverableException with full context information.
     * 
     * @param message the error description
     * @param correlationId the correlation ID for tracing
     * @param attemptNumber the current attempt number
     * @param serviceName the name of the service that failed
     */
    public RecoverableException(String message, String correlationId, int attemptNumber, String serviceName) {
        super(message);
        this.correlationId = correlationId;
        this.attemptNumber = attemptNumber;
        this.nextRetryDelayMs = calculateNextRetryDelay(attemptNumber);
        this.serviceName = serviceName;
    }
    
    /**
     * Creates a RecoverableException with a cause.
     * 
     * @param message the error description
     * @param cause the underlying cause (e.g., ConnectException, SocketTimeoutException)
     */
    public RecoverableException(String message, Throwable cause) {
        super(message, cause);
        this.correlationId = null;
        this.attemptNumber = 0;
        this.nextRetryDelayMs = 0;
        this.serviceName = null;
    }
    
    /**
     * Creates a RecoverableException with cause and retry context.
     * 
     * @param message the error description
     * @param cause the underlying cause
     * @param correlationId the correlation ID for tracing
     * @param attemptNumber the current attempt number
     */
    public RecoverableException(String message, Throwable cause, String correlationId, int attemptNumber) {
        super(message, cause);
        this.correlationId = correlationId;
        this.attemptNumber = attemptNumber;
        this.nextRetryDelayMs = calculateNextRetryDelay(attemptNumber);
        this.serviceName = null;
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
     * Gets the current attempt number (1-based).
     * 
     * @return the attempt number, or 0 if not tracked
     */
    public int getAttemptNumber() {
        return attemptNumber;
    }
    
    /**
     * Gets the calculated delay for the next retry attempt.
     * 
     * @return the delay in milliseconds, or 0 if not calculated
     */
    public long getNextRetryDelayMs() {
        return nextRetryDelayMs;
    }
    
    /**
     * Gets the name of the service that failed.
     * 
     * @return the service name, or null if not provided
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * Checks if the maximum retry attempts have been exceeded.
     * 
     * @param maxRetries the maximum number of retry attempts allowed
     * @return true if retries are exhausted, false otherwise
     */
    public boolean isRetriesExhausted(int maxRetries) {
        return attemptNumber >= maxRetries;
    }
    
    /**
     * Creates a formatted error message with all available context.
     * 
     * @return detailed error message for logging and monitoring
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder(getMessage());
        
        if (correlationId != null) {
            sb.append(" [CorrelationId: ").append(correlationId).append("]");
        }
        
        if (attemptNumber > 0) {
            sb.append(" [Attempt: ").append(attemptNumber).append("]");
        }
        
        if (nextRetryDelayMs > 0) {
            sb.append(" [NextRetryIn: ").append(nextRetryDelayMs).append("ms]");
        }
        
        if (serviceName != null) {
            sb.append(" [Service: ").append(serviceName).append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * Calculates the next retry delay using exponential backoff.
     * 
     * Formula: min(initialDelay * (multiplier ^ (attempt - 1)), maxDelay)
     * With jitter: delay * (0.9 + random(0.2)) to prevent thundering herd
     * 
     * @param attemptNumber the current attempt number (1-based)
     * @return the delay in milliseconds
     */
    private long calculateNextRetryDelay(int attemptNumber) {
        if (attemptNumber <= 0) {
            return 0;
        }
        
        long initialDelayMs = 1000; // 1 second
        double multiplier = 2.0;
        long maxDelayMs = 32000; // 32 seconds
        
        // Calculate exponential backoff
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attemptNumber - 1));
        delay = Math.min(delay, maxDelayMs);
        
        // Add jitter (±10%) to prevent thundering herd
        double jitter = 0.9 + (Math.random() * 0.2);
        delay = (long) (delay * jitter);
        
        return delay;
    }
    
    /**
     * Creates a new RecoverableException for the next retry attempt.
     * 
     * @return a new exception instance with incremented attempt number
     */
    public RecoverableException forNextAttempt() {
        return new RecoverableException(
            getMessage(), 
            getCause(), 
            correlationId, 
            attemptNumber + 1
        );
    }
}

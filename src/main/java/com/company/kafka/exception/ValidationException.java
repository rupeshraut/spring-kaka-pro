package com.company.kafka.exception;

/**
 * Exception thrown when message validation fails.
 * 
 * This is a non-retryable exception - validation errors should not be retried
 * as they indicate data format or content issues that won't be resolved by
 * simply attempting the operation again.
 * 
 * Common scenarios that trigger ValidationException:
 * - Null or empty message content
 * - Invalid JSON format or structure
 * - Missing required fields
 * - Data type mismatches (string where number expected)
 * - Business rule violations (negative amounts, invalid status codes)
 * - Message size exceeding configured limits
 * - Invalid character encoding
 * 
 * Production Best Practices:
 * 1. Always acknowledge ValidationException to prevent infinite retry
 * 2. Log validation failures with detailed context for debugging
 * 3. Route validation failures to dedicated dead letter topics
 * 4. Include the original message content for manual investigation
 * 5. Implement monitoring and alerting for validation error rates
 * 6. Use structured validation rules that can be easily updated
 * 
 * Example Usage:
 * <pre>
 * try {
 *     validateMessage(message);
 *     processMessage(message);
 *     acknowledgment.acknowledge();
 * } catch (ValidationException e) {
 *     // Don't retry - acknowledge to prevent reprocessing
 *     acknowledgment.acknowledge();
 *     log.error("Validation failed: {}", e.getMessage());
 *     metrics.counter("validation.errors").increment();
 * }
 * </pre>
 * 
 * @see RecoverableException for temporary failures that should be retried
 * @see NonRecoverableException for permanent failures
 */
public class ValidationException extends Exception {
    
    private final String messageContent;
    private final String validationRule;
    private final String fieldName;
    
    /**
     * Creates a ValidationException with a simple error message.
     * 
     * @param message the error description
     */
    public ValidationException(String message) {
        super(message);
        this.messageContent = null;
        this.validationRule = null;
        this.fieldName = null;
    }
    
    /**
     * Creates a ValidationException with detailed context information.
     * 
     * @param message the error description
     * @param messageContent the original message content that failed validation
     * @param validationRule the specific validation rule that was violated
     */
    public ValidationException(String message, String messageContent, String validationRule) {
        super(message);
        this.messageContent = messageContent;
        this.validationRule = validationRule;
        this.fieldName = null;
    }
    
    /**
     * Creates a ValidationException with field-specific context.
     * 
     * @param message the error description
     * @param messageContent the original message content
     * @param validationRule the validation rule that failed
     * @param fieldName the specific field that failed validation
     */
    public ValidationException(String message, String messageContent, String validationRule, String fieldName) {
        super(message);
        this.messageContent = messageContent;
        this.validationRule = validationRule;
        this.fieldName = fieldName;
    }
    
    /**
     * Creates a ValidationException with a cause.
     * 
     * @param message the error description
     * @param cause the underlying cause (e.g., JSON parsing error)
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.messageContent = null;
        this.validationRule = null;
        this.fieldName = null;
    }
    
    /**
     * Gets the original message content that failed validation.
     * Useful for debugging and dead letter topic analysis.
     * 
     * @return the original message content, or null if not provided
     */
    public String getMessageContent() {
        return messageContent;
    }
    
    /**
     * Gets the specific validation rule that was violated.
     * 
     * @return the validation rule name/description, or null if not provided
     */
    public String getValidationRule() {
        return validationRule;
    }
    
    /**
     * Gets the specific field name that failed validation.
     * 
     * @return the field name, or null if not field-specific
     */
    public String getFieldName() {
        return fieldName;
    }
    
    /**
     * Creates a formatted error message with all available context.
     * 
     * @return detailed error message for logging and monitoring
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder(getMessage());
        
        if (validationRule != null) {
            sb.append(" [Rule: ").append(validationRule).append("]");
        }
        
        if (fieldName != null) {
            sb.append(" [Field: ").append(fieldName).append("]");
        }
        
        if (messageContent != null && messageContent.length() <= 200) {
            sb.append(" [Content: ").append(messageContent).append("]");
        } else if (messageContent != null) {
            sb.append(" [Content: ").append(messageContent.substring(0, 200)).append("...]");
        }
        
        return sb.toString();
    }
}

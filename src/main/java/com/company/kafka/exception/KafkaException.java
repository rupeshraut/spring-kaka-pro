package com.company.kafka.exception;

/**
 * Base exception for all Kafka-related exceptions in the application
 */
public abstract class KafkaException extends RuntimeException {
    
    public KafkaException(String message) {
        super(message);
    }
    
    public KafkaException(String message, Throwable cause) {
        super(message, cause);
    }
}

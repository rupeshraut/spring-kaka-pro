package com.company.kafka.service;

import com.company.kafka.config.KafkaProperties;
import com.company.kafka.exception.RecoverableException;
import com.company.kafka.exception.ValidationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import com.company.kafka.recovery.KafkaSeekOperations;

import java.util.UUID;

/**
 * Production-ready Kafka consumer service with comprehensive error handling.
 * 
 * Features:
 * - Manual acknowledgment for precise control
 * - Comprehensive error handling with retry logic
 * - Message validation and processing
 * - Correlation ID tracking for distributed tracing
 * - Metrics integration with Micrometer
 * - Dead letter topic support
 * 
 * Pro Tips:
 * 1. Use manual acknowledgment for production reliability
 * 2. Always validate incoming messages before processing
 * 3. Handle different types of errors appropriately
 * 4. Include proper logging with correlation IDs
 * 5. Monitor consumer lag and processing time
 * 6. Implement exponential backoff for retries
 */
@Service
@Slf4j
public class KafkaConsumerService {

    private final KafkaProperties kafkaProperties;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter messagesProcessedCounter;
    private final Counter messagesFailedCounter;
    private final Counter retryableErrorsCounter;
    private final Counter nonRetryableErrorsCounter;
    private final Timer processingTimer;

    public KafkaConsumerService(KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {
        this.kafkaProperties = kafkaProperties;
        this.meterRegistry = meterRegistry;
        
        // Log configuration info for debugging
        log.info("Initializing KafkaConsumerService with topics: orders={}, payments={}, notifications={}", 
            kafkaProperties.topics().orders().name(),
            kafkaProperties.topics().payments().name(), 
            kafkaProperties.topics().notifications().name());
        
        // Initialize metrics
        this.messagesProcessedCounter = Counter.builder("kafka.consumer.messages.processed")
                .description("Total number of messages processed successfully")
                .register(meterRegistry);
                
        this.messagesFailedCounter = Counter.builder("kafka.consumer.messages.failed")
                .description("Total number of failed message processing attempts")
                .register(meterRegistry);
                
        this.retryableErrorsCounter = Counter.builder("kafka.consumer.messages.retryable_errors")
                .description("Total number of retryable errors")
                .register(meterRegistry);
                
        this.nonRetryableErrorsCounter = Counter.builder("kafka.consumer.messages.non_retryable_errors")
                .description("Total number of non-retryable errors")
                .register(meterRegistry);
                
        this.processingTimer = Timer.builder("kafka.consumer.processing.time")
                .description("Time taken to process messages")
                .register(meterRegistry);
    }

    /**
     * Consume order events from the orders topic with comprehensive error handling.
     *
     * @param message the order message payload
     * @param topic the source topic
     * @param partition the partition number
     * @param offset the message offset
     * @param acknowledgment manual acknowledgment
     */
    @KafkaListener(topics = "${kafka.topics.orders.name}", 
                   groupId = "${kafka.consumer.group-id}")
    public void consumeOrderEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId();
        
        log.info("Received order event - topic: {}, partition: {}, offset: {}, correlationId: {}", 
            topic, partition, offset, correlationId);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Validate and process the order event
            validateMessage(message, "order");
            processOrderEvent(message, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            messagesProcessedCounter.increment();
            meterRegistry.counter("kafka.consumer.messages.processed.by.topic", "topic", topic).increment();
            
            log.info("Successfully processed order event - correlationId: {}", correlationId);
            
        } catch (ValidationException e) {
            // Non-retryable validation errors - acknowledge to prevent reprocessing
            nonRetryableErrorsCounter.increment();
            acknowledgment.acknowledge();
            log.error("Validation error for order event - correlationId: {}, error: {}", 
                correlationId, e.getMessage());
                
        } catch (RecoverableException e) {
            // Retryable errors - don't acknowledge, let error handler retry
            retryableErrorsCounter.increment();
            messagesFailedCounter.increment();
            log.error("Recoverable error processing order event - correlationId: {}, error: {}", 
                correlationId, e.getMessage());
            throw e; // Rethrow to trigger retry
            
        } catch (Exception e) {
            // Unknown errors - treat as non-retryable
            nonRetryableErrorsCounter.increment();
            messagesFailedCounter.increment();
            acknowledgment.acknowledge();
            log.error("Unexpected error processing order event - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
                
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Consume payment events from the payments topic.
     *
     * @param message the payment message
     * @param topic the source topic
     * @param partition the partition number
     * @param offset the message offset
     * @param acknowledgment manual acknowledgment
     */
    @KafkaListener(topics = "${kafka.topics.payments.name}", 
                   groupId = "${kafka.consumer.group-id}")
    public void consumePaymentEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId();
        
        log.info("Received payment event - topic: {}, partition: {}, offset: {}, correlationId: {}", 
            topic, partition, offset, correlationId);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Validate and process the payment event
            validateMessage(message, "payment");
            processPaymentEvent(message, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            messagesProcessedCounter.increment();
            meterRegistry.counter("kafka.consumer.messages.processed.by.topic", "topic", topic).increment();
            
            log.info("Successfully processed payment event - correlationId: {}", correlationId);
            
        } catch (ValidationException e) {
            // Non-retryable validation errors
            nonRetryableErrorsCounter.increment();
            acknowledgment.acknowledge();
            log.error("Validation error for payment event - correlationId: {}, error: {}", 
                correlationId, e.getMessage());
                
        } catch (RecoverableException e) {
            // Retryable errors
            retryableErrorsCounter.increment();
            messagesFailedCounter.increment();
            log.error("Recoverable error processing payment event - correlationId: {}, error: {}", 
                correlationId, e.getMessage());
            throw e;
            
        } catch (Exception e) {
            // Unknown errors
            nonRetryableErrorsCounter.increment();
            messagesFailedCounter.increment();
            acknowledgment.acknowledge();
            log.error("Unexpected error processing payment event - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
                
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Consume notification events from the notifications topic.
     *
     * @param message the notification message
     * @param topic the source topic
     * @param partition the partition number
     * @param offset the message offset
     * @param acknowledgment manual acknowledgment
     */
    @KafkaListener(topics = "${kafka.topics.notifications.name}", 
                   groupId = "${kafka.consumer.group-id}")
    public void consumeNotificationEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId();
        
        log.info("Received notification event - topic: {}, partition: {}, offset: {}, correlationId: {}", 
            topic, partition, offset, correlationId);
        
        try {
            // Process the notification
            processNotificationEvent(message, correlationId);
            
            acknowledgment.acknowledge();
            messagesProcessedCounter.increment();
            
            log.info("Successfully processed notification event - correlationId: {}", correlationId);
            
        } catch (Exception e) {
            // Notifications are typically non-critical, so acknowledge anyway
            acknowledgment.acknowledge();
            log.error("Error processing notification event - correlationId: {}, error: {}", 
                correlationId, e.getMessage());
        }
    }

    /**
     * Validate incoming message content.
     *
     * @param message the message to validate
     * @param messageType the type of message for context
     * @throws ValidationException if validation fails
     */
    private void validateMessage(String message, String messageType) throws ValidationException {
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException(messageType + " message cannot be null or empty");
        }
        
        if (message.length() > 10000) { // 10KB limit
            throw new ValidationException(messageType + " message exceeds maximum size limit");
        }
        
        // Add more validation rules as needed
        log.debug("Message validation passed for {}", messageType);
    }

    /**
     * Process an order event with business logic.
     *
     * @param message the order message to process
     * @param correlationId correlation ID for tracing
     * @throws RecoverableException for retryable errors
     */
    private void processOrderEvent(String message, String correlationId) throws RecoverableException {
        try {
            // Simulate business processing with potential for recoverable errors
            Thread.sleep(100); // Simulate processing time
            
            // Simulate occasional recoverable errors (e.g., database connectivity issues)
            if (message.contains("RETRY_ERROR")) {
                throw new RecoverableException("Simulated recoverable error for testing");
            }
            
            log.debug("Order event processed successfully - correlationId: {}", correlationId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecoverableException("Processing interrupted", e);
        }
    }

    /**
     * Process a payment event with business logic.
     *
     * @param message the payment message to process
     * @param correlationId correlation ID for tracing
     * @throws RecoverableException for retryable errors
     */
    private void processPaymentEvent(String message, String correlationId) throws RecoverableException {
        try {
            // Simulate business processing
            Thread.sleep(50); // Simulate processing time
            
            // Simulate occasional recoverable errors
            if (message.contains("PAYMENT_SERVICE_DOWN")) {
                throw new RecoverableException("Payment service temporarily unavailable");
            }
            
            log.debug("Payment event processed successfully - correlationId: {}", correlationId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecoverableException("Processing interrupted", e);
        }
    }

    /**
     * Process a notification event.
     *
     * @param message the notification message
     * @param correlationId correlation ID for tracing
     */
    private void processNotificationEvent(String message, String correlationId) {
        // Simple notification processing - typically fire-and-forget
        log.debug("Notification event processed - correlationId: {}", correlationId);
    }

    /**
     * Generate a unique correlation ID for message tracking.
     *
     * @return correlation ID string
     */
    private String generateCorrelationId() {
        return "consumer-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get consumer health information.
     *
     * @return health status information
     */
    public java.util.Map<String, Object> getHealthInfo() {
        return java.util.Map.of(
            "messagesProcessed", messagesProcessedCounter.count(),
            "messagesFailed", messagesFailedCounter.count(),
            "retryableErrors", retryableErrorsCounter.count(),
            "nonRetryableErrors", nonRetryableErrorsCounter.count(),
            "averageProcessingTime", processingTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }
}

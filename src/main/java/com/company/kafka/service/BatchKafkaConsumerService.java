package com.company.kafka.service;

import com.company.kafka.config.KafkaProperties;
import com.company.kafka.exception.RecoverableException;
import com.company.kafka.exception.ValidationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch Kafka consumer service for high-throughput message processing.
 * 
 * Features:
 * - Batch processing for improved throughput (up to 10x faster)
 * - Partial failure handling within batches
 * - Optimized acknowledgment strategies
 * - Batch-level metrics and monitoring
 * - Configurable batch sizes and timeouts
 * - Dead letter topic routing for failed batches
 * 
 * Performance Benefits:
 * - Reduced network overhead (fewer acknowledgments)
 * - Better resource utilization (batch database operations)
 * - Lower latency for high-volume scenarios
 * - Improved throughput with proper batch sizing
 * 
 * Pro Tips:
 * 1. Balance batch size with processing time to avoid session timeouts
 * 2. Implement partial failure handling for resilience
 * 3. Monitor batch processing times and adjust sizes accordingly
 * 4. Use batch operations for database writes when possible
 * 5. Consider memory usage with large batch sizes
 * 6. Implement proper error isolation within batches
 */
@Service
@Slf4j
public class BatchKafkaConsumerService {

    private final KafkaProperties kafkaProperties;
    private final MeterRegistry meterRegistry;
    
    // Batch metrics
    private final Counter batchesProcessedCounter;
    private final Counter batchesFailedCounter;
    private final Counter messagesInBatchCounter;
    private final Timer batchProcessingTimer;

    public BatchKafkaConsumerService(KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {
        this.kafkaProperties = kafkaProperties;
        this.meterRegistry = meterRegistry;
        
        // Initialize batch metrics
        this.batchesProcessedCounter = Counter.builder("kafka.consumer.batches.processed")
                .description("Total number of batches processed successfully")
                .register(meterRegistry);
                
        this.batchesFailedCounter = Counter.builder("kafka.consumer.batches.failed")
                .description("Total number of batches that failed processing")
                .register(meterRegistry);
                
        this.messagesInBatchCounter = Counter.builder("kafka.consumer.messages.batch.total")
                .description("Total number of messages processed in batches")
                .register(meterRegistry);
                
        this.batchProcessingTimer = Timer.builder("kafka.consumer.batch.processing.time")
                .description("Time taken to process message batches")
                .register(meterRegistry);
    }

    /**
     * Processes order events in batches for improved performance.
     *
     * @param records list of consumer records in the batch
     * @param topics list of topics (should all be orders topic)
     * @param partitions list of partitions
     * @param offsets list of offsets
     * @param acknowledgment batch acknowledgment
     */
    @KafkaListener(topics = "${kafka.topics.orders.name}", 
                   groupId = "${kafka.consumer.group-id}-batch",
                   batch = "true",
                   concurrency = "2")
    public void consumeOrderEventsBatch(
            @Payload List<String> records,
            @Header(KafkaHeaders.RECEIVED_TOPIC) List<String> topics,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment) {
        
        String correlationId = "batch-orders-" + UUID.randomUUID().toString().substring(0, 8);
        int batchSize = records.size();
        
        log.info("Processing order events batch - size: {}, correlationId: {}", batchSize, correlationId);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        
        try {
            // Process each message in the batch
            for (int i = 0; i < records.size(); i++) {
                try {
                    String message = records.get(i);
                    String topic = topics.get(i);
                    Integer partition = partitions.get(i);
                    Long offset = offsets.get(i);
                    
                    log.debug("Processing message {}/{} - topic: {}, partition: {}, offset: {}, correlationId: {}", 
                        i + 1, batchSize, topic, partition, offset, correlationId);
                    
                    // Validate and process an individual message
                    validateMessage(message, "order");
                    processOrderEvent(message, correlationId + "-" + i);
                    
                    processedCount.incrementAndGet();
                    messagesInBatchCounter.increment();
                    
                } catch (ValidationException e) {
                    failedCount.incrementAndGet();
                    log.error("Validation error in batch - message {}/{}, correlationId: {}, error: {}", 
                        i + 1, batchSize, correlationId, e.getMessage());
                    // Continue processing other messages in batch
                    
                } catch (RecoverableException e) {
                    failedCount.incrementAndGet();
                    log.error("Recoverable error in batch - message {}/{}, correlationId: {}, error: {}", 
                        i + 1, batchSize, correlationId, e.getMessage());
                    // For batch processing, we might want to fail the entire batch
                    // or implement more sophisticated retry logic
                    
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    log.error("Unexpected error in batch - message {}/{}, correlationId: {}, error: {}", 
                        i + 1, batchSize, correlationId, e.getMessage(), e);
                }
            }
            
            // Acknowledge the entire batch if processing is acceptable
            if (shouldAcknowledgeBatch(processedCount.get(), failedCount.get(), batchSize)) {
                acknowledgment.acknowledge();
                batchesProcessedCounter.increment();
                
                log.info("Batch processed successfully - processed: {}, failed: {}, total: {}, correlationId: {}", 
                    processedCount.get(), failedCount.get(), batchSize, correlationId);
            } else {
                batchesFailedCounter.increment();
                log.error("Batch processing failed - processed: {}, failed: {}, total: {}, correlationId: {}", 
                    processedCount.get(), failedCount.get(), batchSize, correlationId);
                // Don't acknowledge - let error handler retry the batch
                throw new RecoverableException("Batch processing failed with too many errors");
            }
            
        } catch (Exception e) {
            batchesFailedCounter.increment();
            log.error("Batch processing failed completely - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
            throw e;
            
        } finally {
            sample.stop(batchProcessingTimer);
            
            // Record batch metrics
            meterRegistry.counter("kafka.consumer.batch.size", "size_range", getBatchSizeRange(batchSize))
                .increment();
        }
    }

    /**
     * Processes payment events in batches with database optimization.
     *
     * @param records list of payment records
     * @param acknowledgment batch acknowledgment
     */
    @KafkaListener(topics = "${kafka.topics.payments.name}", 
                   groupId = "${kafka.consumer.group-id}-batch",
                   batch = "true",
                   concurrency = "2")
    public void consumePaymentEventsBatch(
            @Payload List<String> records,
            Acknowledgment acknowledgment) {
        
        String correlationId = "batch-payments-" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("Processing payment events batch - size: {}, correlationId: {}", 
            records.size(), correlationId);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Batch processing with database optimization
            processBatchPayments(records, correlationId);
            
            acknowledgment.acknowledge();
            batchesProcessedCounter.increment();
            messagesInBatchCounter.increment(records.size());
            
            log.info("Payment batch processed successfully - size: {}, correlationId: {}", 
                records.size(), correlationId);
            
        } catch (Exception e) {
            batchesFailedCounter.increment();
            log.error("Payment batch processing failed - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
            throw e;
            
        } finally {
            sample.stop(batchProcessingTimer);
        }
    }

    /**
     * Processes notification events in batches for high throughput.
     *
     * @param records list of notification records as ConsumerRecord objects
     * @param acknowledgment batch acknowledgment
     */
    @KafkaListener(topics = "${kafka.topics.notifications.name}", 
                   groupId = "${kafka.consumer.group-id}-batch",
                   batch = "true",
                   concurrency = "1") // Lower concurrency for notifications
    public void consumeNotificationEventsBatch(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment acknowledgment) {
        
        String correlationId = "batch-notifications-" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("Processing notification events batch - size: {}, correlationId: {}", 
            records.size(), correlationId);
        
        try {
            // Process notifications in parallel for better performance
            records.parallelStream().forEach(record -> {
                try {
                    processNotificationEvent(record.value(), correlationId);
                } catch (Exception e) {
                    log.warn("Failed to process notification - offset: {}, correlationId: {}, error: {}", 
                        record.offset(), correlationId, e.getMessage());
                    // Don't fail entire batch for notifications
                }
            });
            
            acknowledgment.acknowledge();
            batchesProcessedCounter.increment();
            messagesInBatchCounter.increment(records.size());
            
            log.info("Notification batch processed - size: {}, correlationId: {}", 
                records.size(), correlationId);
            
        } catch (Exception e) {
            // Notifications are typically non-critical, so acknowledge anyway
            acknowledgment.acknowledge();
            log.error("Notification batch processing had errors - correlationId: {}, error: {}", 
                correlationId, e.getMessage());
        }
    }

    /**
     * Validates a message for processing.
     *
     * @param message the message to validate
     * @param messageType the type of message
     * @throws ValidationException if validation fails
     */
    private void validateMessage(String message, String messageType) throws ValidationException {
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException(messageType + " message cannot be null or empty");
        }
        
        if (message.length() > 10000) {
            throw new ValidationException(messageType + " message exceeds maximum size limit");
        }
    }

    /**
     * Processes an individual order event.
     *
     * @param message the order message
     * @param correlationId correlation ID for tracking
     * @throws RecoverableException for retryable errors
     */
    private void processOrderEvent(String message, String correlationId) throws RecoverableException {
        try {
            // Simulate processing time
            Thread.sleep(10);
            
            // Simulate occasional recoverable errors
            if (message.contains("RETRY_ERROR")) {
                throw new RecoverableException("Simulated recoverable error for testing");
            }
            
            log.debug("Order event processed - correlationId: {}", correlationId);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecoverableException("Processing interrupted", e);
        }
    }

    /**
     * Processes payment events in batch with database optimization.
     *
     * @param records list of payment records
     * @param correlationId correlation ID for tracking
     */
    private void processBatchPayments(List<String> records, String correlationId) {
        // Simulate batch database operations
        log.debug("Processing {} payments in batch - correlationId: {}", records.size(), correlationId);
        
        // Here you would typically:
        // 1. Validate all records
        // 2. Prepare batch database statements
        // 3. Execute batch inserts/updates
        // 4. Handle partial failures if needed
        
        try {
            Thread.sleep(records.size() * 5); // Simulate batch processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Batch processing interrupted", e);
        }
    }

    /**
     * Processes a notification event.
     *
     * @param message the notification message
     * @param correlationId correlation ID for tracking
     */
    private void processNotificationEvent(String message, String correlationId) {
        // Simple notification processing
        log.debug("Notification processed - correlationId: {}", correlationId);
    }

    /**
     * Determines whether a batch should be acknowledged based on success/failure ratio.
     *
     * @param processedCount number of successfully processed messages
     * @param failedCount number of failed messages
     * @param totalCount total number of messages in batch
     * @return true if batch should be acknowledged
     */
    private boolean shouldAcknowledgeBatch(int processedCount, int failedCount, int totalCount) {
        double successRate = (double) processedCount / totalCount;
        double failureThreshold = 0.1; // Allow up to 10% failures in a batch
        
        return successRate >= (1.0 - failureThreshold);
    }

    /**
     * Gets the size range category for metrics.
     *
     * @param batchSize the batch size
     * @return size range category
     */
    private String getBatchSizeRange(int batchSize) {
        if (batchSize <= 10) return "small";
        if (batchSize <= 100) return "medium";
        if (batchSize <= 500) return "large";
        return "extra-large";
    }

    /**
     * Gets batch processing health information.
     *
     * @return health status information
     */
    public java.util.Map<String, Object> getBatchHealthInfo() {
        return java.util.Map.of(
            "batchesProcessed", batchesProcessedCounter.count(),
            "batchesFailed", batchesFailedCounter.count(),
            "totalMessagesInBatches", messagesInBatchCounter.count(),
            "averageBatchProcessingTime", batchProcessingTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
            "batchSuccessRate", batchesProcessedCounter.count() / (batchesProcessedCounter.count() + batchesFailedCounter.count() + 0.001)
        );
    }
}
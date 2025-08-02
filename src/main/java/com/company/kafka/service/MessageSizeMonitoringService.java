package com.company.kafka.service;

import com.company.kafka.util.MessageSizeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Service demonstrating how to measure message sizes in Kafka operations
 * 
 * Shows practical examples of:
 * - Measuring producer message sizes
 * - Calculating consumer message sizes  
 * - Monitoring size patterns
 * - Performance implications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageSizeMonitoringService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessageSizeCalculator messageSizeCalculator;
    
    /**
     * Send a message with size monitoring
     * 
     * @param topic Target topic
     * @param key Message key
     * @param message Message payload
     * @return Future with send result
     */
    public CompletableFuture<Void> sendMessageWithSizeMonitoring(String topic, String key, Object message) {
        // Create producer record
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, message);
        
        // Add headers for tracing
        record.headers().add("sent-at", Instant.now().toString().getBytes());
        record.headers().add("sender", "message-size-service".getBytes());
        
        // Calculate size before sending
        long messageSize = messageSizeCalculator.calculateProducerRecordSize(record);
        
        log.info("Sending message to topic '{}' - Size: {} ({})", 
                topic, 
                messageSize, 
                messageSizeCalculator.formatSize(messageSize));
        
        // Send message and handle result
        return kafkaTemplate.send(record)
                .thenAccept(result -> {
                    log.info("Message sent successfully - Topic: {}, Partition: {}, Offset: {}, Size: {} bytes",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            messageSize);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to send message of size {} bytes to topic '{}'", 
                            messageSize, topic, throwable);
                    return null;
                });
    }
    
    /**
     * Consumer that monitors message sizes
     * 
     * @param record Consumer record
     * @param acknowledgment Manual acknowledgment
     * @param partition Partition information
     * @param offset Offset information
     */
    @KafkaListener(topics = "${app.kafka.topics.user-events}")
    public void consumeWithSizeMonitoring(
            ConsumerRecord<String, Object> record,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        try {
            // Calculate message size
            long messageSize = messageSizeCalculator.calculateConsumerRecordSize(record);
            
            log.info("Received message - Topic: {}, Partition: {}, Offset: {}, Size: {} ({})",
                    record.topic(),
                    partition,
                    offset,
                    messageSize,
                    messageSizeCalculator.formatSize(messageSize));
            
            // Process based on message size
            if (messageSize > 1024 * 1024) { // 1MB
                log.warn("Large message detected: {} - Consider message compression or splitting",
                        messageSizeCalculator.formatSize(messageSize));
            }
            
            // Process the message
            processMessage(record.value(), messageSize);
            
            // Acknowledge after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process message at partition {} offset {}", 
                    partition, offset, e);
            // Don't acknowledge on error - message will be retried
        }
    }
    
    /**
     * High-throughput consumer with batch size monitoring
     * 
     * @param records Batch of consumer records
     * @param acknowledgment Manual acknowledgment
     */
    @KafkaListener(
            topics = "${app.kafka.topics.high-volume-events}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeBatchWithSizeMonitoring(
            @Payload java.util.List<ConsumerRecord<String, Object>> records,
            Acknowledgment acknowledgment) {
        
        long batchStartTime = System.currentTimeMillis();
        long totalBatchSize = 0;
        
        try {
            for (ConsumerRecord<String, Object> record : records) {
                long messageSize = messageSizeCalculator.calculateConsumerRecordSize(record);
                totalBatchSize += messageSize;
                
                // Process individual message
                processMessage(record.value(), messageSize);
            }
            
            long processingTime = System.currentTimeMillis() - batchStartTime;
            
            log.info("Processed batch - Messages: {}, Total Size: {} ({}), Processing Time: {}ms, " +
                    "Throughput: {}/sec",
                    records.size(),
                    totalBatchSize,
                    messageSizeCalculator.formatSize(totalBatchSize),
                    processingTime,
                    messageSizeCalculator.formatSize(calculateThroughput(totalBatchSize, processingTime)));
            
            // Acknowledge entire batch
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process batch of {} messages", records.size(), e);
            // Don't acknowledge on error
        }
    }
    
    /**
     * Process individual message with size-based logic
     * 
     * @param message Message payload
     * @param messageSize Size in bytes
     */
    private void processMessage(Object message, long messageSize) {
        // Example: Different processing strategies based on message size
        
        if (messageSize < 1024) { // Small messages (< 1KB)
            // Fast processing path
            processSmallMessage(message);
            
        } else if (messageSize < 100 * 1024) { // Medium messages (1KB - 100KB)
            // Standard processing
            processMediumMessage(message);
            
        } else { // Large messages (> 100KB)
            // Careful processing with streaming
            processLargeMessage(message, messageSize);
        }
    }
    
    private void processSmallMessage(Object message) {
        // Fast in-memory processing
        log.debug("Processing small message with fast path");
    }
    
    private void processMediumMessage(Object message) {
        // Standard processing
        log.debug("Processing medium message with standard path");
    }
    
    private void processLargeMessage(Object message, long size) {
        // Streaming or chunked processing
        log.info("Processing large message ({}) with streaming approach", 
                messageSizeCalculator.formatSize(size));
        
        // Pro Tip: For very large messages, consider:
        // 1. Streaming processing instead of loading entire message
        // 2. Using external storage (S3, etc.) with message containing reference
        // 3. Splitting large messages into smaller chunks
        // 4. Implementing backpressure to prevent memory issues
    }
    
    /**
     * Calculate throughput in bytes per second
     * 
     * @param totalBytes Total bytes processed
     * @param timeMs Processing time in milliseconds
     * @return Throughput in bytes per second
     */
    private long calculateThroughput(long totalBytes, long timeMs) {
        if (timeMs == 0) return totalBytes;
        return (totalBytes * 1000) / timeMs;
    }
    
    /**
     * Get current message size statistics
     * 
     * @return Statistics about processed messages
     */
    public MessageSizeCalculator.MessageSizeStats getCurrentStats() {
        return messageSizeCalculator.getStatistics();
    }
    
    /**
     * Reset statistics counters
     */
    public void resetStats() {
        messageSizeCalculator.resetStatistics();
        log.info("Message size statistics reset");
    }
}

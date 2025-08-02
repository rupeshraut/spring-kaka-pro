package com.company.kafka.metrics;

import com.company.kafka.util.MessageSizeCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Metrics collector for Kafka message sizes
 * 
 * Provides detailed metrics for:
 * - Message size distributions
 * - Size-based processing times
 * - Throughput measurements
 * - Size threshold alerts
 */
@Component
@Slf4j
public class MessageSizeMetrics {
    
    private final MeterRegistry meterRegistry;
    private final MessageSizeCalculator messageSizeCalculator;
    
    // Message size metrics
    private DistributionSummary producerMessageSize;
    private DistributionSummary consumerMessageSize;
    private DistributionSummary batchSize;
    
    // Processing time by size category
    private Timer smallMessageProcessingTime;
    private Timer mediumMessageProcessingTime;
    private Timer largeMessageProcessingTime;
    
    // Counters for size categories
    private Counter smallMessagesCounter;
    private Counter mediumMessagesCounter;
    private Counter largeMessagesCounter;
    private Counter oversizedMessagesCounter;
    
    // Throughput metrics
    private DistributionSummary throughputBytesPerSecond;
    
    public MessageSizeMetrics(MeterRegistry meterRegistry, MessageSizeCalculator messageSizeCalculator) {
        this.meterRegistry = meterRegistry;
        this.messageSizeCalculator = messageSizeCalculator;
    }
    
    @PostConstruct
    public void initializeMetrics() {
        // Message size distribution metrics
        producerMessageSize = DistributionSummary.builder("kafka.message.size.producer")
                .description("Size of messages sent to Kafka")
                .baseUnit("bytes")
                .serviceLevelObjectives(1024, 10240, 102400, 1048576) // 1KB, 10KB, 100KB, 1MB
                .register(meterRegistry);
        
        consumerMessageSize = DistributionSummary.builder("kafka.message.size.consumer")
                .description("Size of messages received from Kafka")
                .baseUnit("bytes")
                .serviceLevelObjectives(1024, 10240, 102400, 1048576)
                .register(meterRegistry);
        
        batchSize = DistributionSummary.builder("kafka.batch.size")
                .description("Size of message batches")
                .baseUnit("bytes")
                .serviceLevelObjectives(10240, 102400, 1048576, 10485760) // 10KB, 100KB, 1MB, 10MB
                .register(meterRegistry);
        
        // Processing time metrics by message size
        smallMessageProcessingTime = Timer.builder("kafka.message.processing.time")
                .description("Processing time for small messages")
                .tag("size_category", "small")
                .register(meterRegistry);
        
        mediumMessageProcessingTime = Timer.builder("kafka.message.processing.time")
                .description("Processing time for medium messages")
                .tag("size_category", "medium")
                .register(meterRegistry);
        
        largeMessageProcessingTime = Timer.builder("kafka.message.processing.time")
                .description("Processing time for large messages")
                .tag("size_category", "large")
                .register(meterRegistry);
        
        // Message count by size category
        smallMessagesCounter = Counter.builder("kafka.messages.count")
                .description("Count of small messages")
                .tag("size_category", "small")
                .tag("threshold", "< 1KB")
                .register(meterRegistry);
        
        mediumMessagesCounter = Counter.builder("kafka.messages.count")
                .description("Count of medium messages")
                .tag("size_category", "medium")
                .tag("threshold", "1KB - 100KB")
                .register(meterRegistry);
        
        largeMessagesCounter = Counter.builder("kafka.messages.count")
                .description("Count of large messages")
                .tag("size_category", "large")
                .tag("threshold", "100KB - 1MB")
                .register(meterRegistry);
        
        oversizedMessagesCounter = Counter.builder("kafka.messages.count")
                .description("Count of oversized messages")
                .tag("size_category", "oversized")
                .tag("threshold", "> 1MB")
                .register(meterRegistry);
        
        // Throughput metrics
        throughputBytesPerSecond = DistributionSummary.builder("kafka.throughput.bytes_per_second")
                .description("Message processing throughput in bytes per second")
                .baseUnit("bytes/second")
                .register(meterRegistry);
        
        log.info("Message size metrics initialized successfully");
    }
    
    /**
     * Record producer message size
     * 
     * @param sizeBytes Message size in bytes
     */
    public void recordProducerMessageSize(long sizeBytes) {
        producerMessageSize.record(sizeBytes);
        recordMessageSizeCategory(sizeBytes);
        
        log.debug("Recorded producer message size: {} bytes", sizeBytes);
    }
    
    /**
     * Record consumer message size
     * 
     * @param sizeBytes Message size in bytes
     */
    public void recordConsumerMessageSize(long sizeBytes) {
        consumerMessageSize.record(sizeBytes);
        recordMessageSizeCategory(sizeBytes);
        
        log.debug("Recorded consumer message size: {} bytes", sizeBytes);
    }
    
    /**
     * Record batch size
     * 
     * @param sizeBytes Batch size in bytes
     * @param messageCount Number of messages in batch
     */
    public void recordBatchSize(long sizeBytes, int messageCount) {
        batchSize.record(sizeBytes);
        
        // Record average message size in batch
        if (messageCount > 0) {
            long avgMessageSize = sizeBytes / messageCount;
            recordMessageSizeCategory(avgMessageSize);
        }
        
        log.debug("Recorded batch size: {} bytes ({} messages)", sizeBytes, messageCount);
    }
    
    /**
     * Record processing time with size category
     * 
     * @param sizeBytes Message size in bytes
     * @param processingTimeMs Processing time in milliseconds
     */
    public void recordProcessingTime(long sizeBytes, long processingTimeMs) {
        Timer timer = getTimerForSize(sizeBytes);
        timer.record(processingTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        log.debug("Recorded processing time: {}ms for {} byte message", 
                processingTimeMs, sizeBytes);
    }
    
    /**
     * Record throughput measurement
     * 
     * @param bytesPerSecond Throughput in bytes per second
     */
    public void recordThroughput(long bytesPerSecond) {
        throughputBytesPerSecond.record(bytesPerSecond);
        
        log.debug("Recorded throughput: {} bytes/second ({})", 
                bytesPerSecond, 
                messageSizeCalculator.formatSize(bytesPerSecond));
    }
    
    /**
     * Record message in appropriate size category
     * 
     * @param sizeBytes Message size in bytes
     */
    private void recordMessageSizeCategory(long sizeBytes) {
        if (sizeBytes < 1024) { // < 1KB
            smallMessagesCounter.increment();
        } else if (sizeBytes < 100 * 1024) { // 1KB - 100KB
            mediumMessagesCounter.increment();
        } else if (sizeBytes < 1024 * 1024) { // 100KB - 1MB
            largeMessagesCounter.increment();
        } else { // > 1MB
            oversizedMessagesCounter.increment();
            
            // Log oversized messages for investigation
            log.warn("Oversized message detected: {} - Consider optimization", 
                    messageSizeCalculator.formatSize(sizeBytes));
        }
    }
    
    /**
     * Get appropriate timer for message size
     * 
     * @param sizeBytes Message size in bytes
     * @return Timer for the size category
     */
    private Timer getTimerForSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return smallMessageProcessingTime;
        } else if (sizeBytes < 100 * 1024) {
            return mediumMessageProcessingTime;
        } else {
            return largeMessageProcessingTime;
        }
    }
    
    /**
     * Get current statistics summary
     * 
     * @return Summary of message size metrics
     */
    public MessageSizeMetricsSummary getSummary() {
        MessageSizeCalculator.MessageSizeStats stats = messageSizeCalculator.getStatistics();
        
        return new MessageSizeMetricsSummary(
                stats.messageCount(),
                stats.totalBytes(),
                stats.averageSize(),
                stats.maxSize(),
                stats.minSize(),
                smallMessagesCounter.count(),
                mediumMessagesCounter.count(),
                largeMessagesCounter.count(),
                oversizedMessagesCounter.count(),
                producerMessageSize.mean(),
                consumerMessageSize.mean(),
                batchSize.mean()
        );
    }
    
    /**
     * Summary of message size metrics
     */
    public record MessageSizeMetricsSummary(
            long totalMessages,
            long totalBytes,
            double averageSize,
            long maxSize,
            long minSize,
            double smallMessagesCount,
            double mediumMessagesCount,
            double largeMessagesCount,
            double oversizedMessagesCount,
            double avgProducerMessageSize,
            double avgConsumerMessageSize,
            double avgBatchSize
    ) {
        
        public String getFormattedSummary() {
            return String.format("""
                Message Size Metrics Summary:
                ================================
                Total Messages: %,d
                Total Bytes: %s
                Average Size: %.1f bytes
                Max Size: %s
                Min Size: %s
                
                Size Distribution:
                - Small (< 1KB): %,.0f messages
                - Medium (1KB-100KB): %,.0f messages  
                - Large (100KB-1MB): %,.0f messages
                - Oversized (> 1MB): %,.0f messages
                
                Average Sizes:
                - Producer Messages: %.1f bytes
                - Consumer Messages: %.1f bytes
                - Batch Size: %.1f bytes
                """,
                totalMessages,
                formatBytes(totalBytes),
                averageSize,
                formatBytes(maxSize),
                formatBytes(minSize),
                smallMessagesCount,
                mediumMessagesCount,
                largeMessagesCount,
                oversizedMessagesCount,
                avgProducerMessageSize,
                avgConsumerMessageSize,
                avgBatchSize
            );
        }
        
        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}

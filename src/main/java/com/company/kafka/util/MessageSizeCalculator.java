package com.company.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Utility class for measuring Kafka message sizes
 * 
 * Provides various methods to calculate message sizes including:
 * - Serialized message size
 * - Headers size
 * - Key size  
 * - Total record size
 * - Running statistics
 */
@Component
@Slf4j
public class MessageSizeCalculator {
    
    private final ObjectMapper objectMapper;
    private final LongAdder totalMessages = new LongAdder();
    private final LongAdder totalBytes = new LongAdder();
    private final AtomicLong maxMessageSize = new AtomicLong(0);
    private final AtomicLong minMessageSize = new AtomicLong(Long.MAX_VALUE);
    
    public MessageSizeCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Calculate the size of a producer record in bytes
     * 
     * @param record The producer record to measure
     * @return Size in bytes including key, value, and headers
     */
    public long calculateProducerRecordSize(ProducerRecord<String, Object> record) {
        long totalSize = 0;
        
        // Key size
        if (record.key() != null) {
            totalSize += record.key().getBytes(StandardCharsets.UTF_8).length;
        }
        
        // Value size
        if (record.value() != null) {
            totalSize += calculateValueSize(record.value());
        }
        
        // Headers size
        if (record.headers() != null) {
            totalSize += calculateHeadersSize(record.headers());
        }
        
        // Kafka overhead (approximate)
        totalSize += calculateKafkaOverhead();
        
        updateStatistics(totalSize);
        
        log.debug("Producer record size: {} bytes (key: {}, value: {}, headers: {})", 
                totalSize, 
                record.key() != null ? record.key().length() : 0,
                record.value() != null ? calculateValueSize(record.value()) : 0,
                record.headers() != null ? calculateHeadersSize(record.headers()) : 0);
        
        return totalSize;
    }
    
    /**
     * Calculate the size of a consumer record in bytes
     * 
     * @param record The consumer record to measure
     * @return Size in bytes including key, value, and headers
     */
    public long calculateConsumerRecordSize(ConsumerRecord<String, Object> record) {
        long totalSize = 0;
        
        // Key size
        if (record.key() != null) {
            totalSize += record.key().getBytes(StandardCharsets.UTF_8).length;
        }
        
        // Value size
        if (record.value() != null) {
            totalSize += calculateValueSize(record.value());
        }
        
        // Headers size
        if (record.headers() != null) {
            totalSize += calculateHeadersSize(record.headers());
        }
        
        // Kafka metadata overhead
        totalSize += calculateKafkaOverhead();
        
        updateStatistics(totalSize);
        
        log.debug("Consumer record size: {} bytes (partition: {}, offset: {})", 
                totalSize, record.partition(), record.offset());
        
        return totalSize;
    }
    
    /**
     * Calculate the size of just the message value
     * 
     * @param value The message value object
     * @return Size in bytes of the serialized value
     */
    public long calculateValueSize(Object value) {
        if (value == null) {
            return 0;
        }
        
        long size;
        try {
            if (value instanceof String) {
                size = ((String) value).getBytes(StandardCharsets.UTF_8).length;
            } else if (value instanceof byte[]) {
                size = ((byte[]) value).length;
            } else {
                // For complex objects, serialize to JSON to get size
                String jsonString = objectMapper.writeValueAsString(value);
                size = jsonString.getBytes(StandardCharsets.UTF_8).length;
            }
        } catch (Exception e) {
            log.warn("Failed to calculate value size for object: {}", value.getClass().getSimpleName(), e);
            // Fallback: estimate based on toString()
            size = value.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        
        // Update statistics
        updateStatistics(size);
        
        return size;
    }
    
    /**
     * Calculate the size of message headers
     * 
     * @param headers Kafka headers
     * @return Size in bytes of all headers
     */
    public long calculateHeadersSize(Iterable<Header> headers) {
        long headersSize = 0;
        
        for (Header header : headers) {
            // Header key size
            headersSize += header.key().getBytes(StandardCharsets.UTF_8).length;
            
            // Header value size
            if (header.value() != null) {
                headersSize += header.value().length;
            }
            
            // Header overhead (key length + value length fields)
            headersSize += 8; // 4 bytes for key length + 4 bytes for value length
        }
        
        return headersSize;
    }
    
    /**
     * Estimate Kafka protocol overhead per message
     * 
     * @return Estimated overhead in bytes
     */
    public long calculateKafkaOverhead() {
        // Kafka message overhead includes:
        // - Message length (4 bytes)
        // - CRC (4 bytes) 
        // - Magic byte (1 byte)
        // - Attributes (1 byte)
        // - Timestamp (8 bytes)
        // - Key length (4 bytes)
        // - Value length (4 bytes)
        // - Headers count (4 bytes)
        return 30; // Approximate overhead
    }
    
    /**
     * Calculate batch size for multiple messages
     * 
     * @param messages Array of message values
     * @return Total batch size in bytes
     */
    public long calculateBatchSize(Object... messages) {
        long batchSize = 0;
        
        for (Object message : messages) {
            batchSize += calculateValueSize(message);
            batchSize += calculateKafkaOverhead();
        }
        
        // Add batch overhead
        batchSize += 50; // Batch header overhead
        
        return batchSize;
    }
    
    /**
     * Estimate serialized size before actual serialization
     * 
     * @param value Object to estimate size for
     * @param serializer Serializer to use
     * @return Estimated size in bytes
     */
    public <T> long estimateSerializedSize(T value, Serializer<T> serializer) {
        if (value == null) {
            return 0;
        }
        
        try {
            byte[] serialized = serializer.serialize("dummy-topic", value);
            return serialized != null ? serialized.length : 0;
        } catch (Exception e) {
            log.warn("Failed to serialize object for size estimation: {}", 
                    value.getClass().getSimpleName(), e);
            return calculateValueSize(value); // Fallback
        }
    }
    
    /**
     * Update running statistics
     */
    private void updateStatistics(long messageSize) {
        totalMessages.increment();
        totalBytes.add(messageSize);
        
        // Update max size
        maxMessageSize.updateAndGet(current -> Math.max(current, messageSize));
        
        // Update min size
        minMessageSize.updateAndGet(current -> Math.min(current, messageSize));
    }
    
    /**
     * Get message size statistics
     * 
     * @return Statistics about processed messages
     */
    public MessageSizeStats getStatistics() {
        long count = totalMessages.sum();
        long total = totalBytes.sum();
        
        return new MessageSizeStats(
                count,
                total,
                count > 0 ? (double) total / count : 0.0,
                maxMessageSize.get(),
                minMessageSize.get() == Long.MAX_VALUE ? 0 : minMessageSize.get()
        );
    }
    
    /**
     * Reset statistics
     */
    public void resetStatistics() {
        totalMessages.reset();
        totalBytes.reset();
        maxMessageSize.set(0);
        minMessageSize.set(Long.MAX_VALUE);
    }
    
    /**
     * Convert bytes to human-readable format
     * 
     * @param bytes Size in bytes
     * @return Human-readable string (e.g., "1.5 KB", "2.3 MB")
     */
    public String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Statistics about message sizes
     */
    public record MessageSizeStats(
            long messageCount,
            long totalBytes,
            double averageSize,
            long maxSize,
            long minSize
    ) {
        public String getFormattedStats() {
            return String.format(
                    "Messages: %d, Total: %s, Avg: %.1f B, Max: %s, Min: %s",
                    messageCount,
                    formatBytes(totalBytes),
                    averageSize,
                    formatBytes(maxSize),
                    formatBytes(minSize)
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

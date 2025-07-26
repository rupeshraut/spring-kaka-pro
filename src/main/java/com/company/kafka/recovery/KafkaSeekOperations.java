package com.company.kafka.recovery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka seek operations for advanced error recovery and message reprocessing.
 * 
 * Features:
 * - Seek to specific offsets for message replay
 * - Seek to timestamps for time-based recovery
 * - Seek to beginning/end for full reprocessing
 * - Conditional seek operations based on error patterns
 * - Metrics tracking for seek operations
 * - Consumer position management
 * 
 * Use Cases:
 * - Message replay for error recovery
 * - Time-based message reprocessing
 * - Skip corrupted message ranges
 * - Debug and troubleshooting
 * - Data consistency recovery
 * 
 * Pro Tips:
 * 1. Use seek operations judiciously to avoid message duplication
 * 2. Implement proper offset tracking for recovery operations
 * 3. Monitor seek operations for operational insights
 * 4. Consider downstream impacts of message replay
 * 5. Implement safeguards against infinite loops
 */
@Component
@Slf4j
public class KafkaSeekOperations implements ConsumerSeekAware {

    private final MeterRegistry meterRegistry;
    
    // Consumer seek callback storage
    private final Map<TopicPartition, ConsumerSeekCallback> seekCallbacks = new ConcurrentHashMap<>();
    
    // Seek operation metrics
    private final Counter seekToOffsetCounter;
    private final Counter seekToTimestampCounter;
    private final Counter seekToBeginningCounter;
    private final Counter seekToEndCounter;
    private final Counter seekErrorCounter;

    public KafkaSeekOperations(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize seek metrics
        this.seekToOffsetCounter = Counter.builder("kafka.consumer.seek.offset")
                .description("Number of seek to offset operations")
                .register(meterRegistry);
                
        this.seekToTimestampCounter = Counter.builder("kafka.consumer.seek.timestamp")
                .description("Number of seek to timestamp operations")
                .register(meterRegistry);
                
        this.seekToBeginningCounter = Counter.builder("kafka.consumer.seek.beginning")
                .description("Number of seek to beginning operations")
                .register(meterRegistry);
                
        this.seekToEndCounter = Counter.builder("kafka.consumer.seek.end")
                .description("Number of seek to end operations")
                .register(meterRegistry);
                
        this.seekErrorCounter = Counter.builder("kafka.consumer.seek.errors")
                .description("Number of seek operation errors")
                .register(meterRegistry);
        
        log.info("KafkaSeekOperations initialized with metrics tracking");
    }

    @Override
    public void registerSeekCallback(ConsumerSeekCallback callback) {
        log.debug("Registering seek callback");
        // Store callback for later use - implementation depends on how you want to map callbacks
        // For simplicity, we'll just log this registration
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback seekCallback) {
        log.info("Partitions assigned: {}", assignments.keySet());
        
        // Store seek callbacks for each partition
        for (TopicPartition partition : assignments.keySet()) {
            seekCallbacks.put(partition, seekCallback);
        }
        
        log.debug("Stored seek callbacks for {} partitions", assignments.size());
    }

    @Override
    public void onIdleContainer(Map<TopicPartition, Long> assignments, ConsumerSeekCallback seekCallback) {
        log.debug("Container idle with assignments: {}", assignments.keySet());
        // Can implement idle-based seek operations here if needed
    }

    /**
     * Seeks to a specific offset on a topic partition.
     *
     * @param topic the topic name
     * @param partition the partition number
     * @param offset the target offset
     * @return true if seek operation was successful
     */
    public boolean seekToOffset(String topic, int partition, long offset) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ConsumerSeekCallback callback = seekCallbacks.get(topicPartition);
        
        if (callback == null) {
            log.warn("No seek callback available for partition: {}", topicPartition);
            seekErrorCounter.increment();
            return false;
        }
        
        try {
            log.info("Seeking to offset - topic: {}, partition: {}, offset: {}", topic, partition, offset);
            callback.seek(topic, partition, offset);
            seekToOffsetCounter.increment();
            
            meterRegistry.counter("kafka.consumer.seek.offset.by.topic", "topic", topic).increment();
            
            log.info("Successfully sought to offset - topic: {}, partition: {}, offset: {}", 
                topic, partition, offset);
            return true;
            
        } catch (Exception e) {
            seekErrorCounter.increment();
            log.error("Failed to seek to offset - topic: {}, partition: {}, offset: {}, error: {}", 
                topic, partition, offset, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Seeks to a specific timestamp on a topic partition.
     *
     * @param topic the topic name
     * @param partition the partition number
     * @param timestamp the target timestamp
     * @return true if seek operation was successful
     */
    public boolean seekToTimestamp(String topic, int partition, long timestamp) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ConsumerSeekCallback callback = seekCallbacks.get(topicPartition);
        
        if (callback == null) {
            log.warn("No seek callback available for partition: {}", topicPartition);
            seekErrorCounter.increment();
            return false;
        }
        
        try {
            log.info("Seeking to timestamp - topic: {}, partition: {}, timestamp: {} ({})", 
                topic, partition, timestamp, Instant.ofEpochMilli(timestamp));
            
            callback.seekToTimestamp(topic, partition, timestamp);
            seekToTimestampCounter.increment();
            
            meterRegistry.counter("kafka.consumer.seek.timestamp.by.topic", "topic", topic).increment();
            
            log.info("Successfully sought to timestamp - topic: {}, partition: {}, timestamp: {}", 
                topic, partition, timestamp);
            return true;
            
        } catch (Exception e) {
            seekErrorCounter.increment();
            log.error("Failed to seek to timestamp - topic: {}, partition: {}, timestamp: {}, error: {}", 
                topic, partition, timestamp, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Seeks to the beginning of a topic partition.
     *
     * @param topic the topic name
     * @param partition the partition number
     * @return true if seek operation was successful
     */
    public boolean seekToBeginning(String topic, int partition) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ConsumerSeekCallback callback = seekCallbacks.get(topicPartition);
        
        if (callback == null) {
            log.warn("No seek callback available for partition: {}", topicPartition);
            seekErrorCounter.increment();
            return false;
        }
        
        try {
            log.info("Seeking to beginning - topic: {}, partition: {}", topic, partition);
            callback.seekToBeginning(topic, partition);
            seekToBeginningCounter.increment();
            
            meterRegistry.counter("kafka.consumer.seek.beginning.by.topic", "topic", topic).increment();
            
            log.info("Successfully sought to beginning - topic: {}, partition: {}", topic, partition);
            return true;
            
        } catch (Exception e) {
            seekErrorCounter.increment();
            log.error("Failed to seek to beginning - topic: {}, partition: {}, error: {}", 
                topic, partition, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Seeks to the end of a topic partition.
     *
     * @param topic the topic name
     * @param partition the partition number
     * @return true if seek operation was successful
     */
    public boolean seekToEnd(String topic, int partition) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ConsumerSeekCallback callback = seekCallbacks.get(topicPartition);
        
        if (callback == null) {
            log.warn("No seek callback available for partition: {}", topicPartition);
            seekErrorCounter.increment();
            return false;
        }
        
        try {
            log.info("Seeking to end - topic: {}, partition: {}", topic, partition);
            callback.seekToEnd(topic, partition);
            seekToEndCounter.increment();
            
            meterRegistry.counter("kafka.consumer.seek.end.by.topic", "topic", topic).increment();
            
            log.info("Successfully sought to end - topic: {}, partition: {}", topic, partition);
            return true;
            
        } catch (Exception e) {
            seekErrorCounter.increment();
            log.error("Failed to seek to end - topic: {}, partition: {}, error: {}", 
                topic, partition, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Seeks relative to the current position.
     *
     * @param topic the topic name
     * @param partition the partition number
     * @param offsetDelta the offset delta (positive = forward, negative = backward)
     * @return true if seek operation was successful
     */
    public boolean seekRelative(String topic, int partition, long offsetDelta) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        ConsumerSeekCallback callback = seekCallbacks.get(topicPartition);
        
        if (callback == null) {
            log.warn("No seek callback available for partition: {}", topicPartition);
            seekErrorCounter.increment();
            return false;
        }
        
        try {
            log.info("Seeking relative - topic: {}, partition: {}, delta: {}", topic, partition, offsetDelta);
            callback.seekRelative(topic, partition, offsetDelta, true);
            seekToOffsetCounter.increment();
            
            meterRegistry.counter("kafka.consumer.seek.relative.by.topic", 
                "topic", topic, "direction", offsetDelta >= 0 ? "forward" : "backward").increment();
            
            log.info("Successfully sought relative - topic: {}, partition: {}, delta: {}", 
                topic, partition, offsetDelta);
            return true;
            
        } catch (Exception e) {
            seekErrorCounter.increment();
            log.error("Failed to seek relative - topic: {}, partition: {}, delta: {}, error: {}", 
                topic, partition, offsetDelta, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Seeks to a timestamp with a time duration.
     *
     * @param topic the topic name
     * @param partition the partition number
     * @param duration the duration to seek back from now
     * @return true if seek operation was successful
     */
    public boolean seekToTimeAgo(String topic, int partition, Duration duration) {
        long timestamp = System.currentTimeMillis() - duration.toMillis();
        
        log.info("Seeking to {} ago - topic: {}, partition: {}, target timestamp: {} ({})", 
            duration, topic, partition, timestamp, Instant.ofEpochMilli(timestamp));
        
        return seekToTimestamp(topic, partition, timestamp);
    }

    /**
     * Conditional seek operation based on record evaluation.
     *
     * @param record the consumer record that triggered the condition
     * @param seekCondition the condition to evaluate
     * @return true if seek was performed
     */
    public boolean conditionalSeek(ConsumerRecord<String, Object> record, SeekCondition seekCondition) {
        if (!seekCondition.shouldSeek(record)) {
            log.debug("Seek condition not met for record - topic: {}, partition: {}, offset: {}", 
                record.topic(), record.partition(), record.offset());
            return false;
        }
        
        SeekTarget target = seekCondition.getSeekTarget(record);
        
        switch (target.getType()) {
            case OFFSET:
                return seekToOffset(record.topic(), record.partition(), target.getOffset());
            case TIMESTAMP:
                return seekToTimestamp(record.topic(), record.partition(), target.getTimestamp());
            case BEGINNING:
                return seekToBeginning(record.topic(), record.partition());
            case END:
                return seekToEnd(record.topic(), record.partition());
            case RELATIVE:
                return seekRelative(record.topic(), record.partition(), target.getOffsetDelta());
            default:
                log.warn("Unknown seek target type: {}", target.getType());
                seekErrorCounter.increment();
                return false;
        }
    }

    /**
     * Gets available partitions for seek operations.
     *
     * @return map of topic partitions to their current positions
     */
    public Map<TopicPartition, ConsumerSeekCallback> getAvailablePartitions() {
        return new HashMap<>(seekCallbacks);
    }

    /**
     * Interface for seek conditions.
     */
    public interface SeekCondition {
        boolean shouldSeek(ConsumerRecord<String, Object> record);
        SeekTarget getSeekTarget(ConsumerRecord<String, Object> record);
    }

    /**
     * Seek target specification.
     */
    public static class SeekTarget {
        public enum Type {
            OFFSET, TIMESTAMP, BEGINNING, END, RELATIVE
        }
        
        private final Type type;
        private final long offset;
        private final long timestamp;
        private final long offsetDelta;
        
        private SeekTarget(Type type, long offset, long timestamp, long offsetDelta) {
            this.type = type;
            this.offset = offset;
            this.timestamp = timestamp;
            this.offsetDelta = offsetDelta;
        }
        
        public static SeekTarget offset(long offset) {
            return new SeekTarget(Type.OFFSET, offset, 0, 0);
        }
        
        public static SeekTarget timestamp(long timestamp) {
            return new SeekTarget(Type.TIMESTAMP, 0, timestamp, 0);
        }
        
        public static SeekTarget beginning() {
            return new SeekTarget(Type.BEGINNING, 0, 0, 0);
        }
        
        public static SeekTarget end() {
            return new SeekTarget(Type.END, 0, 0, 0);
        }
        
        public static SeekTarget relative(long offsetDelta) {
            return new SeekTarget(Type.RELATIVE, 0, 0, offsetDelta);
        }
        
        public Type getType() { return type; }
        public long getOffset() { return offset; }
        public long getTimestamp() { return timestamp; }
        public long getOffsetDelta() { return offsetDelta; }
    }

    /**
     * Gets seek operation health information.
     *
     * @return health status information
     */
    public Map<String, Object> getSeekHealthInfo() {
        return Map.of(
            "availablePartitions", seekCallbacks.size(),
            "seekOperations", Map.of(
                "toOffset", seekToOffsetCounter.count(),
                "toTimestamp", seekToTimestampCounter.count(),
                "toBeginning", seekToBeginningCounter.count(),
                "toEnd", seekToEndCounter.count(),
                "errors", seekErrorCounter.count()
            ),
            "partitions", seekCallbacks.keySet()
        );
    }
}
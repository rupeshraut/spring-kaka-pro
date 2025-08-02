package com.company.kafka.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Kafka Property Sizing Calculator
 * 
 * A utility tool to help calculate optimal Kafka property values based on your specific requirements.
 * Run with: --kafka.sizing.enabled=true --kafka.sizing.messages-per-second=1000 --kafka.sizing.message-size=1024
 */
@Component
@ConditionalOnProperty(value = "kafka.sizing.enabled", havingValue = "true")
@Slf4j
public class KafkaSizingCalculator implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        log.info("=".repeat(80));
        log.info("🔧 KAFKA PROPERTY SIZING CALCULATOR");
        log.info("=".repeat(80));
        
        // Get sizing parameters from environment or use defaults
        int messagesPerSecond = getEnvInt("KAFKA_SIZING_MESSAGES_PER_SECOND", 1000);
        int averageMessageSize = getEnvInt("KAFKA_SIZING_MESSAGE_SIZE", 1024);
        int processingTimeMs = getEnvInt("KAFKA_SIZING_PROCESSING_TIME_MS", 10);
        int consumerCount = getEnvInt("KAFKA_SIZING_CONSUMER_COUNT", 3);
        boolean requiresLowLatency = getEnvBoolean("KAFKA_SIZING_LOW_LATENCY", false);
        boolean requiresHighDurability = getEnvBoolean("KAFKA_SIZING_HIGH_DURABILITY", true);
        
        calculateAndRecommend(messagesPerSecond, averageMessageSize, processingTimeMs, 
                             consumerCount, requiresLowLatency, requiresHighDurability);
    }

    private void calculateAndRecommend(int messagesPerSecond, int averageMessageSize, 
                                     int processingTimeMs, int consumerCount,
                                     boolean requiresLowLatency, boolean requiresHighDurability) {
        
        log.info("📊 INPUT PARAMETERS:");
        log.info("   Messages per second: {}", messagesPerSecond);
        log.info("   Average message size: {} bytes", averageMessageSize);
        log.info("   Processing time per message: {} ms", processingTimeMs);
        log.info("   Consumer count: {}", consumerCount);
        log.info("   Low latency required: {}", requiresLowLatency);
        log.info("   High durability required: {}", requiresHighDurability);
        log.info("");

        // Calculate recommendations
        ProducerRecommendations producer = calculateProducerSettings(
            messagesPerSecond, averageMessageSize, requiresLowLatency, requiresHighDurability);
        
        ConsumerRecommendations consumer = calculateConsumerSettings(
            messagesPerSecond, processingTimeMs, requiresLowLatency);
        
        TopicRecommendations topic = calculateTopicSettings(
            messagesPerSecond, consumerCount, requiresHighDurability);

        // Print recommendations
        printProducerRecommendations(producer);
        printConsumerRecommendations(consumer);
        printTopicRecommendations(topic);
        printYamlConfiguration(producer, consumer, topic);
    }

    private ProducerRecommendations calculateProducerSettings(int messagesPerSecond, 
                                                            int averageMessageSize,
                                                            boolean requiresLowLatency, 
                                                            boolean requiresHighDurability) {
        
        int batchSize;
        int lingerMs;
        long bufferMemory;
        String acks;
        String compressionType;
        
        if (requiresLowLatency) {
            // Optimize for latency
            batchSize = Math.max(1024, averageMessageSize * 2);
            lingerMs = 0;
            bufferMemory = 32 * 1024 * 1024; // 32MB
            acks = "1";
            compressionType = "none";
        } else {
            // Optimize for throughput
            int messagesPerBatch = Math.min(100, Math.max(10, 1000 / messagesPerSecond));
            batchSize = Math.max(16384, averageMessageSize * messagesPerBatch);
            lingerMs = requiresHighDurability ? 20 : 100;
            
            // Calculate buffer based on expected load
            long bytesPerSecond = (long) messagesPerSecond * averageMessageSize;
            bufferMemory = Math.max(64 * 1024 * 1024, bytesPerSecond * 2); // 2 seconds of buffer
            
            acks = requiresHighDurability ? "all" : "1";
            compressionType = "snappy";
        }

        return new ProducerRecommendations(batchSize, lingerMs, bufferMemory, acks, compressionType);
    }

    private ConsumerRecommendations calculateConsumerSettings(int messagesPerSecond, 
                                                            int processingTimeMs,
                                                            boolean requiresLowLatency) {
        
        int maxPollRecords;
        int maxPollIntervalMs;
        int sessionTimeoutMs;
        int fetchMinBytes;
        int concurrency;
        
        if (requiresLowLatency) {
            maxPollRecords = Math.min(50, Math.max(1, 1000 / messagesPerSecond));
            maxPollIntervalMs = 2 * 60 * 1000; // 2 minutes
            sessionTimeoutMs = 10 * 1000; // 10 seconds
            fetchMinBytes = 1;
            concurrency = 1;
        } else {
            // Calculate optimal batch size based on processing time
            int processingBudgetMs = 30 * 1000; // 30 seconds budget
            maxPollRecords = Math.min(5000, Math.max(100, processingBudgetMs / processingTimeMs));
            
            // Max poll interval should accommodate processing time
            maxPollIntervalMs = Math.max(5 * 60 * 1000, maxPollRecords * processingTimeMs * 2);
            
            sessionTimeoutMs = 30 * 1000; // 30 seconds
            fetchMinBytes = Math.max(1024, Math.min(1024 * 1024, messagesPerSecond * 50)); // 50ms worth of data
            concurrency = Math.max(1, Math.min(12, messagesPerSecond / 1000));
        }

        return new ConsumerRecommendations(maxPollRecords, maxPollIntervalMs, 
                                         sessionTimeoutMs, fetchMinBytes, concurrency);
    }

    private TopicRecommendations calculateTopicSettings(int messagesPerSecond, 
                                                      int consumerCount,
                                                      boolean requiresHighDurability) {
        
        // Calculate partitions based on throughput and consumer count
        int throughputBasedPartitions = Math.max(1, messagesPerSecond / 10000); // 10K msg/sec per partition
        int consumerBasedPartitions = consumerCount;
        int partitions = Math.max(throughputBasedPartitions, consumerBasedPartitions);
        
        // Cap at reasonable maximum
        partitions = Math.min(partitions, 48);
        
        int replicationFactor = requiresHighDurability ? 3 : 2;
        
        return new TopicRecommendations(partitions, replicationFactor);
    }

    private void printProducerRecommendations(ProducerRecommendations producer) {
        log.info("🚀 PRODUCER RECOMMENDATIONS:");
        log.info("   batch-size: {} ({} KB)", producer.batchSize, producer.batchSize / 1024);
        log.info("   linger-ms: {}", producer.lingerMs);
        log.info("   buffer-memory: {} ({} MB)", producer.bufferMemory, producer.bufferMemory / (1024 * 1024));
        log.info("   acks: '{}'", producer.acks);
        log.info("   compression-type: '{}'", producer.compressionType);
        log.info("");
    }

    private void printConsumerRecommendations(ConsumerRecommendations consumer) {
        log.info("📥 CONSUMER RECOMMENDATIONS:");
        log.info("   max-poll-records: {}", consumer.maxPollRecords);
        log.info("   max-poll-interval: {} ({} minutes)", consumer.maxPollIntervalMs, consumer.maxPollIntervalMs / (60 * 1000));
        log.info("   session-timeout: {} ({} seconds)", consumer.sessionTimeoutMs, consumer.sessionTimeoutMs / 1000);
        log.info("   fetch-min-bytes: {} ({} KB)", consumer.fetchMinBytes, consumer.fetchMinBytes / 1024);
        log.info("   concurrency: {}", consumer.concurrency);
        log.info("");
    }

    private void printTopicRecommendations(TopicRecommendations topic) {
        log.info("📋 TOPIC RECOMMENDATIONS:");
        log.info("   partitions: {}", topic.partitions);
        log.info("   replication-factor: {}", topic.replicationFactor);
        log.info("");
    }

    private void printYamlConfiguration(ProducerRecommendations producer, 
                                      ConsumerRecommendations consumer, 
                                      TopicRecommendations topic) {
        log.info("📄 GENERATED YAML CONFIGURATION:");
        log.info("");
        log.info("kafka:");
        log.info("  producer:");
        log.info("    batch-size: {}", producer.batchSize);
        log.info("    linger-ms: {}", producer.lingerMs);
        log.info("    buffer-memory: {}", producer.bufferMemory);
        log.info("    acks: \"{}\"", producer.acks);
        log.info("    compression-type: \"{}\"", producer.compressionType);
        log.info("    enable-idempotence: {}", "all".equals(producer.acks));
        log.info("");
        log.info("  consumer:");
        log.info("    max-poll-records: {}", consumer.maxPollRecords);
        log.info("    max-poll-interval: {}ms", consumer.maxPollIntervalMs);
        log.info("    session-timeout: {}ms", consumer.sessionTimeoutMs);
        log.info("    fetch-min-bytes: {}", consumer.fetchMinBytes);
        log.info("    concurrency: {}", consumer.concurrency);
        log.info("");
        log.info("  topics:");
        log.info("    orders:");
        log.info("      partitions: {}", topic.partitions);
        log.info("      replication-factor: {}", topic.replicationFactor);
        log.info("");
        log.info("💡 USAGE TIPS:");
        log.info("   - Start with these values and monitor performance");
        log.info("   - Adjust based on actual throughput and latency requirements");
        log.info("   - Monitor consumer lag and producer metrics");
        log.info("   - Load test with realistic data patterns");
        log.info("=".repeat(80));
    }

    private int getEnvInt(String envVar, int defaultValue) {
        String value = System.getenv(envVar);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid value for {}: {}, using default: {}", envVar, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private boolean getEnvBoolean(String envVar, boolean defaultValue) {
        String value = System.getenv(envVar);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    private record ProducerRecommendations(
        int batchSize,
        int lingerMs,
        long bufferMemory,
        String acks,
        String compressionType
    ) {}

    private record ConsumerRecommendations(
        int maxPollRecords,
        int maxPollIntervalMs,
        int sessionTimeoutMs,
        int fetchMinBytes,
        int concurrency
    ) {}

    private record TopicRecommendations(
        int partitions,
        int replicationFactor
    ) {}
}

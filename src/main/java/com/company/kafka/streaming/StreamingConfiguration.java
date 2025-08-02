package com.company.kafka.streaming;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for the unified streaming processor.
 * Supports runtime reconfiguration for optimal performance tuning.
 */
@Data
@Component
@ConfigurationProperties(prefix = "kafka.streaming")
public class StreamingConfiguration {

    /**
     * Core thread pool settings.
     */
    private int coreThreads = 2;
    private int maxThreads = 8;
    private int workerThreads = 4;
    private Duration keepAliveTime = Duration.ofSeconds(60);
    
    /**
     * Queue and buffer settings.
     */
    private int queueCapacity = 1000;
    private int taskQueueCapacity = 500;
    
    /**
     * Health monitoring settings.
     */
    private Duration healthCheckInterval = Duration.ofSeconds(30);
    private double maxErrorRate = 0.05; // 5%
    
    /**
     * Circuit breaker configuration.
     */
    private CircuitBreakerConfig circuitBreakerConfig = new CircuitBreakerConfig();
    
    /**
     * Topic-specific configurations.
     */
    private TopicConfigurations topics = new TopicConfigurations();
    
    @Data
    public static class CircuitBreakerConfig {
        private int failureThreshold = 10;
        private Duration timeout = Duration.ofSeconds(60);
        private int successThreshold = 5;
        private Duration resetTimeout = Duration.ofMinutes(5);
    }
    
    @Data
    public static class TopicConfigurations {
        private TopicConfig orders = new TopicConfig(100, Duration.ofSeconds(30), 2);
        private TopicConfig payments = new TopicConfig(200, Duration.ofSeconds(60), 4);
        private TopicConfig defaultConfig = new TopicConfig(150, Duration.ofSeconds(45), 3);
        
        public TopicConfig getConfigForTopic(String topic) {
            return switch (topic) {
                case "orders-topic" -> orders;
                case "payments-topic" -> payments;
                default -> defaultConfig;
            };
        }
    }
    
    @Data
    public static class TopicConfig {
        private final int bufferSize;
        private final Duration processingTimeout;
        private final int maxConcurrency;
        
        public TopicConfig(int bufferSize, Duration processingTimeout, int maxConcurrency) {
            this.bufferSize = bufferSize;
            this.processingTimeout = processingTimeout;
            this.maxConcurrency = maxConcurrency;
        }
    }
}
package com.company.kafka.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Configuration properties for Kafka partitioning strategies.
 * 
 * This configuration class provides comprehensive settings for both producer
 * partitioning and consumer partition assignment strategies.
 * 
 * Producer Partitioning:
 * - Strategy selection (ROUND_ROBIN, HASH, STICKY, etc.)
 * - Strategy-specific parameters
 * - Performance tuning options
 * 
 * Consumer Assignment:
 * - Assignment strategy selection
 * - Consumer metadata configuration
 * - Load balancing options
 * 
 * Monitoring:
 * - Partition distribution metrics
 * - Rebalancing frequency tracking
 * - Performance monitoring
 * 
 * Example Configuration:
 * 
 * kafka:
 *   partitioning:
 *     producer:
 *       strategy: CUSTOMER_AWARE
 *       sticky:
 *         partition-count: 2
 *       customer:
 *         hash-seed: 54321
 *       priority:
 *         partition-ratio: 0.3
 *     consumer:
 *       assignment-strategy: WORKLOAD_AWARE
 *       metadata:
 *         rack: "us-east-1a"
 *         priority: HIGH
 *         capacity: 2000
 *         region: "us-east"
 *     monitoring:
 *       enabled: true
 *       metrics-interval: PT30S
 * 
 * Environment Variables:
 * - KAFKA_PARTITIONING_PRODUCER_STRATEGY
 * - KAFKA_PARTITIONING_CONSUMER_ASSIGNMENT_STRATEGY
 * - KAFKA_PARTITIONING_MONITORING_ENABLED
 */
@ConfigurationProperties(prefix = "kafka.partitioning")
@Validated
public record PartitioningProperties(
    @Valid @NotNull ProducerPartitioning producer,
    @Valid @NotNull ConsumerPartitioning consumer,
    @Valid @NotNull MonitoringConfig monitoring
) {
    
    /**
     * Producer partitioning configuration.
     */
    public record ProducerPartitioning(
        @NotBlank String strategy,
        @Valid StickyConfig sticky,
        @Valid CustomerConfig customer,
        @Valid PriorityConfig priority,
        @Valid GeographicConfig geographic,
        @Valid TimeBasedConfig timeBased,
        @Valid LoadAwareConfig loadAware
    ) {
        
        /**
         * Sticky partitioning configuration for better batching.
         */
        public record StickyConfig(
            @Min(1) @Max(64) int partitionCount,
            boolean enableThreadAffinity,
            @NotNull Duration affinityTimeout
        ) {
            public StickyConfig() {
                this(1, true, Duration.ofMinutes(5));
            }
        }
        
        /**
         * Customer-aware partitioning configuration.
         */
        public record CustomerConfig(
            long hashSeed,
            @NotBlank String customerIdField,
            boolean enableCaching,
            @Min(100) @Max(100000) int cacheSize
        ) {
            public CustomerConfig() {
                this(12345L, "customerId", true, 10000);
            }
        }
        
        /**
         * Priority-based partitioning configuration.
         */
        public record PriorityConfig(
            double partitionRatio,
            @NotBlank String priorityField,
            @NotNull List<String> highPriorityValues,
            boolean enableDynamicAdjustment
        ) {
            public PriorityConfig() {
                this(0.2, "priority", List.of("HIGH", "CRITICAL", "URGENT"), false);
            }
        }
        
        /**
         * Geographic partitioning configuration.
         */
        public record GeographicConfig(
            @NotNull List<String> regions,
            @NotBlank String regionField,
            boolean enableCrossBorderFallback,
            @NotBlank String defaultRegion
        ) {
            public GeographicConfig() {
                this(List.of("us-east", "us-west", "eu", "asia"), "region", true, "us-east");
            }
        }
        
        /**
         * Time-based partitioning configuration.
         */
        public record TimeBasedConfig(
            @NotNull Duration timeWindow,
            boolean useUtcTime,
            @NotBlank String timeField
        ) {
            public TimeBasedConfig() {
                this(Duration.ofMinutes(1), true, "timestamp");
            }
        }
        
        /**
         * Load-aware partitioning configuration.
         */
        public record LoadAwareConfig(
            @Min(100) @Max(100000) int loadThreshold,
            @NotNull Duration metricsWindow,
            boolean enableHotPartitionAvoidance,
            double loadBalancingFactor
        ) {
            public LoadAwareConfig() {
                this(1000, Duration.ofMinutes(1), true, 1.5);
            }
        }
        
        public ProducerPartitioning() {
            this("HASH", new StickyConfig(), new CustomerConfig(), new PriorityConfig(), 
                 new GeographicConfig(), new TimeBasedConfig(), new LoadAwareConfig());
        }
    }
    
    /**
     * Consumer partition assignment configuration.
     */
    public record ConsumerPartitioning(
        @NotBlank String assignmentStrategy,
        @Valid ConsumerMetadata metadata,
        @Valid RebalancingConfig rebalancing,
        @Valid AffinityConfig affinity
    ) {
        
        /**
         * Consumer metadata for assignment decisions.
         */
        public record ConsumerMetadata(
            @NotBlank String rack,
            @NotBlank String priority,
            @Min(100) @Max(100000) int capacity,
            @NotBlank String region,
            @NotNull List<String> capabilities,
            @NotNull List<String> preferences
        ) {
            public ConsumerMetadata() {
                this("default", "MEDIUM", 1000, "default", 
                     List.of("JSON", "AVRO"), List.of("BATCH_PROCESSING"));
            }
        }
        
        /**
         * Rebalancing behavior configuration.
         */
        public record RebalancingConfig(
            @NotNull Duration maxRebalanceTime,
            @Min(1) @Max(10) int maxRebalanceRetries,
            boolean enableEagerRebalancing,
            boolean enableIncrementalRebalancing,
            @NotNull Duration stabilizationPeriod
        ) {
            public RebalancingConfig() {
                this(Duration.ofMinutes(5), 3, false, true, Duration.ofSeconds(30));
            }
        }
        
        /**
         * Partition affinity configuration.
         */
        public record AffinityConfig(
            boolean enableStickyAssignment,
            double stickinessFactor,
            @NotNull Duration affinityTimeout,
            boolean enableCrossTopicAffinity
        ) {
            public AffinityConfig() {
                this(true, 0.8, Duration.ofMinutes(10), false);
            }
        }
        
        public ConsumerPartitioning() {
            this("LOAD_BALANCED", new ConsumerMetadata(), new RebalancingConfig(), new AffinityConfig());
        }
    }
    
    /**
     * Partitioning monitoring configuration.
     */
    public record MonitoringConfig(
        boolean enabled,
        @NotNull Duration metricsInterval,
        @NotNull Duration reportingInterval,
        @Valid AlertConfig alerts,
        @Valid MetricsConfig metrics
    ) {
        
        /**
         * Alerting configuration for partitioning issues.
         */
        public record AlertConfig(
            boolean enabled,
            double skewThreshold,
            @Min(1) @Max(3600) int hotPartitionThreshold,
            @NotNull Duration alertCooldown,
            @NotNull List<String> alertChannels
        ) {
            public AlertConfig() {
                this(true, 2.0, 100, Duration.ofMinutes(5), List.of("LOG", "METRICS"));
            }
        }
        
        /**
         * Metrics collection configuration.
         */
        public record MetricsConfig(
            boolean enableDetailedMetrics,
            boolean enablePartitionLevelMetrics,
            boolean enableConsumerLevelMetrics,
            @Min(10) @Max(86400) int historyRetentionSeconds,
            @NotNull List<String> exportFormats
        ) {
            public MetricsConfig() {
                this(true, true, true, 3600, List.of("PROMETHEUS", "JMX"));
            }
        }
        
        public MonitoringConfig() {
            this(true, Duration.ofSeconds(30), Duration.ofMinutes(5), 
                 new AlertConfig(), new MetricsConfig());
        }
    }
    
    public PartitioningProperties() {
        this(new ProducerPartitioning(), new ConsumerPartitioning(), new MonitoringConfig());
    }
    
    /**
     * Validates that the configuration is consistent and valid.
     */
    public void validate() {
        validateProducerConfig();
        validateConsumerConfig();
        validateMonitoringConfig();
    }
    
    private void validateProducerConfig() {
        // Validate producer strategy
        try {
            ProducerStrategy.valueOf(producer.strategy().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid producer strategy: " + producer.strategy());
        }
        
        // Validate priority configuration
        if (producer.priority().partitionRatio() < 0.1 || producer.priority().partitionRatio() > 0.9) {
            throw new IllegalArgumentException("Priority partition ratio must be between 0.1 and 0.9");
        }
        
        // Validate geographic configuration
        if (producer.geographic().regions().isEmpty()) {
            throw new IllegalArgumentException("At least one geographic region must be specified");
        }
        
        if (!producer.geographic().regions().contains(producer.geographic().defaultRegion())) {
            throw new IllegalArgumentException("Default region must be in the regions list");
        }
    }
    
    private void validateConsumerConfig() {
        // Validate assignment strategy
        try {
            ConsumerAssignmentStrategy.valueOf(consumer.assignmentStrategy().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid consumer assignment strategy: " + consumer.assignmentStrategy());
        }
        
        // Validate consumer priority
        try {
            ConsumerPriority.valueOf(consumer.metadata().priority().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid consumer priority: " + consumer.metadata().priority());
        }
        
        // Validate rebalancing configuration
        if (consumer.rebalancing().maxRebalanceTime().compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("Max rebalance time must be at least 1 minute");
        }
    }
    
    private void validateMonitoringConfig() {
        if (monitoring.enabled() && monitoring.metricsInterval().isNegative()) {
            throw new IllegalArgumentException("Metrics interval must be positive when monitoring is enabled");
        }
        
        if (monitoring.alerts().enabled() && monitoring.alerts().skewThreshold() < 1.0) {
            throw new IllegalArgumentException("Skew threshold must be at least 1.0");
        }
    }
    
    // Enums for validation
    
    public enum ProducerStrategy {
        ROUND_ROBIN, HASH, STICKY, CUSTOMER_AWARE, GEOGRAPHIC, PRIORITY, TIME_BASED, LOAD_AWARE
    }
    
    public enum ConsumerAssignmentStrategy {
        AFFINITY_BASED, LOAD_BALANCED, RACK_AWARE, PRIORITY_BASED, GEOGRAPHIC, WORKLOAD_AWARE
    }
    
    public enum ConsumerPriority {
        HIGH, MEDIUM, LOW
    }
}
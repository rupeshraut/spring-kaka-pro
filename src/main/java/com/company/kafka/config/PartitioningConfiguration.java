package com.company.kafka.config;

import com.company.kafka.partition.CustomPartitioner;
import com.company.kafka.partition.CustomConsumerAssignmentStrategy;
import com.company.kafka.partition.PartitionMonitor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Configuration class for Kafka partitioning features.
 * 
 * This configuration enables:
 * - Partitioning properties from application configuration
 * - Custom partitioner strategies
 * - Partition monitoring and metrics
 * - Partitioning management APIs
 * 
 * Features Enabled:
 * - PartitioningProperties configuration binding
 * - Custom partitioner registration
 * - Partition monitoring services
 * - Management controller registration
 * 
 * Usage:
 * Add the following to your application.yml:
 * 
 * kafka:
 *   partitioning:
 *     producer:
 *       strategy: CUSTOMER_AWARE
 *       customer:
 *         hash-seed: 54321
 *         customer-id-field: customerId
 *     consumer:
 *       assignment-strategy: WORKLOAD_AWARE
 *       metadata:
 *         rack: us-east-1a
 *         priority: HIGH
 *     monitoring:
 *       enabled: true
 *       metrics-interval: PT30S
 */
@Configuration
@EnableConfigurationProperties(PartitioningProperties.class)
@ConfigurationPropertiesScan("com.company.kafka.config")
public class PartitioningConfiguration {

    /**
     * Create default partitioning properties if not configured.
     */
    @Bean
    public PartitioningProperties defaultPartitioningProperties() {
        return new PartitioningProperties();
    }
    
    /**
     * Create custom partitioner bean for producer configuration.
     */
    @Bean
    public CustomPartitioner customPartitioner() {
        return new CustomPartitioner();
    }
    
    /**
     * Create custom consumer assignment strategy bean.
     */
    @Bean
    public CustomConsumerAssignmentStrategy customConsumerAssignmentStrategy() {
        return new CustomConsumerAssignmentStrategy();
    }
    
    /**
     * Create partition monitor for monitoring partition distribution and metrics.
     */
    @Bean
    public PartitionMonitor partitionMonitor(KafkaAdmin kafkaAdmin, MeterRegistry meterRegistry) {
        return new PartitionMonitor(kafkaAdmin, meterRegistry);
    }
}
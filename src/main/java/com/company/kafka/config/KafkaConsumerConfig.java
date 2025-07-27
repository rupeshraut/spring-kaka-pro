package com.company.kafka.config;

import com.company.kafka.partition.CustomConsumerAssignmentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration
 * 
 * Pro Tips:
 * 1. Use manual acknowledgment for better error handling control
 * 2. Set appropriate max.poll.records based on processing time
 * 3. Configure proper session.timeout.ms and heartbeat.interval.ms
 * 4. Use isolation.level=read_committed for transactional producers
 * 5. Monitor consumer lag regularly
 * 6. Implement proper rebalance listeners
 * 
 * Production Optimizations:
 * - Manual commit provides better control over message processing
 * - Proper fetch configuration improves throughput
 * - Concurrency settings should match partition count
 * - Rebalance listeners help with graceful shutdowns
 * 
 * @author Development Team
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerConfig {

    private final KafkaProperties kafkaProperties;
    private final PartitioningProperties partitioningProperties;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Consumer group configuration
        var consumerProps = kafkaProperties.consumer();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProps.groupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProps.autoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumerProps.enableAutoCommit());
        
        // Performance tuning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProps.maxPollRecords());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 
            (int) consumerProps.maxPollInterval().toMillis());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 
            (int) consumerProps.sessionTimeout().toMillis());
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 
            (int) consumerProps.heartbeatInterval().toMillis());
        
        // Fetch configuration
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, consumerProps.fetchMinBytes());
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 
            (int) consumerProps.fetchMaxWait().toMillis());
        
        // Transaction support
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, consumerProps.isolationLevel());
        
        // JSON deserializer configuration
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.company.kafka.model");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.company.kafka.model.GenericMessage");
        
        // Security configuration (optional - configure if needed)
        // if (kafkaProperties.security().enabled()) {
        //     configureSecurity(props);
        // }
        
        // Custom partition assignment strategy
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, 
            CustomConsumerAssignmentStrategy.CUSTOM_CONSUMER_ASSIGNMENT_STRATEGY_NAME);
        props.putAll(buildConsumerMetadataConfig());
        
        // Monitoring interceptors (optional - configure if needed)
        // if (kafkaProperties.monitoring().enabled() && !kafkaProperties.monitoring().interceptors().isEmpty()) {
        //     props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, 
        //         kafkaProperties.monitoring().interceptors());
        // }
        
        log.info("Consumer configuration initialized for group: {} with custom assignment strategy: {}", 
            consumerProps.groupId(), partitioningProperties.consumer().assignmentStrategy());
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Concurrency configuration
        var consumerProps = kafkaProperties.consumer();
        factory.setConcurrency(consumerProps.concurrency());
        
        // Acknowledgment mode - manual for better control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Error handling - will be configured in KafkaErrorHandlingConfig
        
        // Consumer lifecycle management
        factory.getContainerProperties().setConsumerRebalanceListener(
            new ProductionConsumerRebalanceListener());
        
        // Batch processing configuration (if needed)
        // factory.setBatchListener(true);
        // factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        
        // Metrics listener (optional - configure if needed)
        // factory.getContainerProperties().setConsumerTaskExecutor(
        //     new KafkaMetrics.ConsumerTaskExecutor());
        
        log.info("Kafka listener container factory configured with concurrency: {}, ack-mode: manual", 
            consumerProps.concurrency());
        
        return factory;
    }
    
    // Optional security configuration method
    // private void configureSecurity(Map<String, Object> props) {
    //     var security = kafkaProperties.security();
    //     
    //     props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, security.protocol());
    //     
    //     // SSL Configuration
    //     var ssl = security.ssl();
    //     if (ssl.trustStoreLocation() != null) {
    //         props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.trustStoreLocation());
    //         props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.trustStorePassword());
    //     }
    //     
    //     if (ssl.keyStoreLocation() != null) {
    //         props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keyStoreLocation());
    //         props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keyStorePassword());
    //         props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, ssl.keyStoreType());
    //     }
    //     
    //     // SASL Configuration
    //     var sasl = security.sasl();
    //     if (sasl.mechanism() != null) {
    //         props.put(SaslConfigs.SASL_MECHANISM, sasl.mechanism());
    //     }
    //     
    //     if (sasl.jaasConfig() != null) {
    //         props.put(SaslConfigs.SASL_JAAS_CONFIG, sasl.jaasConfig());
    //     }
    // }
    
    /**
     * Production-ready consumer rebalance listener
     * Handles partition assignment/revocation gracefully
     */
    private static class ProductionConsumerRebalanceListener implements ConsumerAwareRebalanceListener {
        
        @Override
        public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, 
                Collection<TopicPartition> partitions) {
            log.info("Partitions revoked before commit: {}. Consumer will commit offsets.", partitions);
            // Opportunity to commit offsets manually if needed
        }
        
        @Override
        public void onPartitionsRevokedAfterCommit(Consumer<?, ?> consumer, 
                Collection<TopicPartition> partitions) {
            log.info("Partitions revoked after commit: {}. Cleanup can be performed.", partitions);
            // Cleanup resources associated with these partitions
        }
        
        @Override
        public void onPartitionsAssigned(Consumer<?, ?> consumer, 
                Collection<TopicPartition> partitions) {
            log.info("Partitions assigned: {}. Consumer ready to process messages.", partitions);
            // Initialize resources for new partitions
            
            // Optional: Seek to specific offsets if needed
            // partitions.forEach(partition -> consumer.seek(partition, getDesiredOffset(partition)));
        }
        
        @Override
        public void onPartitionsLost(Consumer<?, ?> consumer, 
                Collection<TopicPartition> partitions) {
            log.warn("Partitions lost: {}. Emergency cleanup required.", partitions);
            // Handle partition loss scenario
        }
    }
    
    private Map<String, Object> buildConsumerMetadataConfig() {
        Map<String, Object> metadataConfig = new HashMap<>();
        
        var consumer = partitioningProperties.consumer();
        var metadata = consumer.metadata();
        
        // Set consumer metadata as system properties for the assignment strategy
        System.setProperty("consumer.rack", metadata.rack());
        System.setProperty("consumer.priority", metadata.priority());
        System.setProperty("consumer.capacity", String.valueOf(metadata.capacity()));
        System.setProperty("consumer.region", metadata.region());
        System.setProperty("consumer.capabilities", String.join(",", metadata.capabilities()));
        System.setProperty("consumer.preferences", String.join(",", metadata.preferences()));
        
        // Assignment strategy configuration
        metadataConfig.put("assignment.strategy", consumer.assignmentStrategy());
        
        if (consumer.rebalancing() != null) {
            metadataConfig.put("assignment.rebalance.incremental", 
                consumer.rebalancing().enableIncrementalRebalancing());
        }
        
        if (consumer.affinity() != null) {
            metadataConfig.put("assignment.affinity.timeout", 
                consumer.affinity().affinityTimeout().toMillis());
            metadataConfig.put("assignment.stickiness.factor", 
                consumer.affinity().stickinessFactor());
        }
        
        log.info("Consumer metadata configuration applied: strategy={}, rack={}, priority={}, capacity={}", 
            consumer.assignmentStrategy(), metadata.rack(), metadata.priority(), metadata.capacity());
        
        return metadataConfig;
    }
}

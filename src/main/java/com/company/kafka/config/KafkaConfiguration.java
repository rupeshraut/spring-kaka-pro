package com.company.kafka.config;

import com.company.kafka.error.EnhancedKafkaErrorHandler;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import com.company.kafka.filter.KafkaRecordFilters;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready Kafka configuration with comprehensive error handling and monitoring.
 * 
 * Features:
 * - Production-optimized producer and consumer settings
 * - Comprehensive error handling with retry logic and dead letter topics
 * - SSL/TLS and SASL authentication support
 * - Manual acknowledgment for precise message processing control
 * - Metrics integration with Micrometer
 * - Automatic topic creation for main and dead letter topics
 * - Connection pooling and resource optimization
 * 
 * Producer Configuration:
 * - Idempotence enabled to prevent duplicate messages
 * - Acknowledgment set to 'all' for maximum durability
 * - Infinite retries with exponential backoff
 * - Optimized batching and compression for throughput
 * - Request timeout and delivery timeout for reliability
 * 
 * Consumer Configuration:
 * - Manual acknowledgment for precise control
 * - Auto-commit disabled for transactional safety
 * - Optimized fetch sizes and timeouts
 * - Consumer group management with proper session timeouts
 * - Concurrent processing with configurable thread pools
 * 
 * Security Configuration:
 * - SSL/TLS encryption support with certificate validation
 * - SASL authentication with multiple mechanism support
 * - Network security with proper endpoint identification
 * - Credential management through environment variables
 * 
 * Pro Tips:
 * 1. Always enable idempotence in production
 * 2. Use manual acknowledgment for critical message processing
 * 3. Configure appropriate timeouts for your use case
 * 4. Monitor consumer lag and throughput metrics
 * 5. Test failover scenarios thoroughly
 * 6. Implement proper secret management for credentials
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
@RequiredArgsConstructor
@Slf4j
public class KafkaConfiguration {

    private final KafkaProperties kafkaProperties;
    private final MeterRegistry meterRegistry;

    /**
     * Creates a production-ready Kafka producer factory with idempotence and reliability settings.
     * 
     * @return configured ProducerFactory
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        log.info("Configuring Kafka producer factory with production settings");
        
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Production reliability settings
        KafkaProperties.ProducerProperties producer = kafkaProperties.producer();
        props.put(ProducerConfig.ACKS_CONFIG, producer.acks());                    // Wait for all replicas
        props.put(ProducerConfig.RETRIES_CONFIG, producer.retries());              // Infinite retries
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producer.enableIdempotence()); // Prevent duplicates
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, producer.maxInFlightRequestsPerConnection());
        
        // Performance optimization
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.batchSize());         // Batch size for throughput
        props.put(ProducerConfig.LINGER_MS_CONFIG, producer.lingerMs());           // Wait time for batching
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producer.compressionType());
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producer.bufferMemory());   // Producer buffer memory
        
        // Timeout settings
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) producer.requestTimeout().toMillis());
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) producer.deliveryTimeout().toMillis());
        
        // Transaction settings for exactly-once semantics
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "kafka-producer-tx");
        props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 30000); // 30 seconds
        
        // Add producer interceptors for message enrichment
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, 
            "com.company.kafka.interceptor.KafkaProducerInterceptor");
        
        // Interceptor configuration
        props.put("interceptor.application.name", "spring-kafka-pro");
        props.put("interceptor.application.version", "1.0.0");
        props.put("interceptor.environment", kafkaProperties.security().enabled() ? "production" : "development");
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        log.info("Producer factory configured with idempotence: {}, acks: {}, retries: {}", 
            producer.enableIdempotence(), producer.acks(), producer.retries());
        
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a Kafka template with error handling and metrics integration.
     * 
     * @return configured KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        
        // Add send callbacks for metrics and logging
        template.setDefaultTopic(kafkaProperties.topics().orders().name());
        
        log.info("KafkaTemplate configured with default topic: {}", kafkaProperties.topics().orders().name());
        return template;
    }

    /**
     * Creates a production-ready Kafka consumer factory with manual acknowledgment.
     * 
     * @return configured ConsumerFactory
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        log.info("Configuring Kafka consumer factory with manual acknowledgment");
        
        Map<String, Object> props = new HashMap<>();
        
        // Basic configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Consumer group and offset management
        KafkaProperties.ConsumerProperties consumer = kafkaProperties.consumer();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.groupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.autoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.enableAutoCommit()); // Manual ack
        
        // Performance and reliability settings
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.maxPollRecords());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) consumer.maxPollInterval().toMillis());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, (int) consumer.sessionTimeout().toMillis());
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, (int) consumer.heartbeatInterval().toMillis());
        
        // Fetch configuration for performance
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, consumer.fetchMinBytes());
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, (int) consumer.fetchMaxWait().toMillis());
        
        // Isolation level for transactional reads
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, consumer.isolationLevel());
        
        // JSON deserializer configuration
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.lang.String");
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        log.info("Consumer factory configured with group: {}, auto-commit: {}, max-poll: {}", 
            consumer.groupId(), consumer.enableAutoCommit(), consumer.maxPollRecords());
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates a transactional Kafka template for exactly-once processing.
     * 
     * @return configured transactional KafkaTemplate
     */
    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager() {
        return new KafkaTransactionManager<>(producerFactory());
    }

    /**
     * Creates a Kafka listener container factory with enhanced error handling and concurrency.
     * 
     * @param enhancedKafkaErrorHandler the enhanced error handler with poison pill detection and circuit breaker
     * @return configured ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            EnhancedKafkaErrorHandler enhancedKafkaErrorHandler) {
        
        log.info("Configuring Kafka listener container factory with error handling");
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Configure concurrency
        factory.setConcurrency(kafkaProperties.consumer().concurrency());
        
        // Configure manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Configure enhanced error handling with poison pill detection and circuit breaker
        factory.setCommonErrorHandler(enhancedKafkaErrorHandler.createEnhancedErrorHandler());
        
        // Configure additional container properties
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setPollTimeout(Duration.ofSeconds(3).toMillis());
        
        log.info("Kafka listener container factory configured with concurrency: {}, ack-mode: manual", 
            kafkaProperties.consumer().concurrency());
        
        return factory;
    }

    /**
     * Creates a batch Kafka listener container factory for high-throughput processing.
     * 
     * @param enhancedKafkaErrorHandler the enhanced error handler for retry logic and dead letter topics
     * @return configured batch ConcurrentKafkaListenerContainerFactory
     */
    @Bean("batchKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchKafkaListenerContainerFactory(
            EnhancedKafkaErrorHandler enhancedKafkaErrorHandler) {
        
        log.info("Configuring batch Kafka listener container factory");
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Configure batch processing
        factory.setBatchListener(true);
        
        // Configure concurrency for batch processing
        factory.setConcurrency(kafkaProperties.consumer().concurrency());
        
        // Configure manual acknowledgment for batches
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Configure error handling
        factory.setCommonErrorHandler(enhancedKafkaErrorHandler.createEnhancedErrorHandler());
        
        // Configure batch-specific container properties
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setPollTimeout(Duration.ofSeconds(5).toMillis()); // Longer timeout for batches
        containerProps.setIdleBetweenPolls(Duration.ofMillis(100).toMillis()); // Reduce idle time
        
        log.info("Batch Kafka listener container factory configured with batch processing enabled");
        
        return factory;
    }

    /**
     * Creates a Kafka admin client for topic management and cluster operations.
     * 
     * @return configured KafkaAdmin
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        log.info("Configuring Kafka admin client");
        
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        
        KafkaProperties.AdminProperties admin = kafkaProperties.admin();
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) admin.requestTimeout().toMillis());
        props.put(AdminClientConfig.RETRIES_CONFIG, admin.retries());
        props.put(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG, (int) admin.retryBackoff().toMillis());
        
        // Add security configuration if enabled
        addSecurityConfiguration(props);
        
        return new KafkaAdmin(props);
    }

    /**
     * Creates the orders topic with appropriate partitions and replication.
     * 
     * @return NewTopic for orders
     */
    @Bean
    public NewTopic ordersTopic() {
        KafkaProperties.TopicConfig orders = kafkaProperties.topics().orders();
        return TopicBuilder.name(orders.name())
            .partitions(orders.partitions())
            .replicas(orders.replicationFactor())
            .compact() // Enable log compaction for order updates
            .build();
    }

    /**
     * Creates the payments topic with appropriate partitions and replication.
     * 
     * @return NewTopic for payments
     */
    @Bean
    public NewTopic paymentsTopic() {
        KafkaProperties.TopicConfig payments = kafkaProperties.topics().payments();
        return TopicBuilder.name(payments.name())
            .partitions(payments.partitions())
            .replicas(payments.replicationFactor())
            .build();
    }

    /**
     * Creates the notifications topic with appropriate partitions and replication.
     * 
     * @return NewTopic for notifications
     */
    @Bean
    public NewTopic notificationsTopic() {
        KafkaProperties.TopicConfig notifications = kafkaProperties.topics().notifications();
        return TopicBuilder.name(notifications.name())
            .partitions(notifications.partitions())
            .replicas(notifications.replicationFactor())
            .build();
    }

    /**
     * Creates dead letter topics for error handling.
     * 
     * @return array of dead letter topics
     */
    @Bean
    public NewTopic[] deadLetterTopics() {
        KafkaProperties.TopicsProperties topics = kafkaProperties.topics();
        
        return new NewTopic[] {
            // Orders dead letter topics
            TopicBuilder.name(topics.orders().name() + ".validation.dlt")
                .partitions(1)
                .replicas(topics.orders().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
            TopicBuilder.name(topics.orders().name() + ".non-recoverable.dlt")
                .partitions(1)
                .replicas(topics.orders().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
            TopicBuilder.name(topics.orders().name() + ".dlt")
                .partitions(1)
                .replicas(topics.orders().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
                
            // Payments dead letter topics
            TopicBuilder.name(topics.payments().name() + ".validation.dlt")
                .partitions(1)
                .replicas(topics.payments().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
            TopicBuilder.name(topics.payments().name() + ".non-recoverable.dlt")
                .partitions(1)
                .replicas(topics.payments().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
            TopicBuilder.name(topics.payments().name() + ".dlt")
                .partitions(1)
                .replicas(topics.payments().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
                
            // Notifications dead letter topics
            TopicBuilder.name(topics.notifications().name() + ".validation.dlt")
                .partitions(1)
                .replicas(topics.notifications().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
            TopicBuilder.name(topics.notifications().name() + ".non-recoverable.dlt")
                .partitions(1)
                .replicas(topics.notifications().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build(),
            TopicBuilder.name(topics.notifications().name() + ".dlt")
                .partitions(1)
                .replicas(topics.notifications().replicationFactor())
                .config("retention.ms", "604800000") // 7 days
                .build()
        };
    }

    /**
     * Adds security configuration to the properties map if security is enabled.
     * 
     * @param props the properties map to add security configuration to
     */
    private void addSecurityConfiguration(Map<String, Object> props) {
        KafkaProperties.SecurityProperties security = kafkaProperties.security();
        
        if (!security.enabled()) {
            log.debug("Kafka security is disabled");
            return;
        }
        
        log.info("Configuring Kafka security with protocol: {}", security.protocol());
        props.put("security.protocol", security.protocol());
        
        // SSL Configuration
        if ("SSL".equals(security.protocol()) || "SASL_SSL".equals(security.protocol())) {
            KafkaProperties.SslProperties ssl = security.ssl();
            if (ssl.trustStoreLocation() != null) {
                props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.trustStoreLocation());
                props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.trustStorePassword());
            }
            if (ssl.keyStoreLocation() != null) {
                props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keyStoreLocation());
                props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keyStorePassword());
                // Note: Using keyStorePassword for key password as well since SSL properties only has keyStorePassword
                props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.keyStorePassword());
            }
            
            // Security enhancements
            props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
            props.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLSv1.2");
        }
        
        // SASL Configuration
        if ("SASL_PLAINTEXT".equals(security.protocol()) || "SASL_SSL".equals(security.protocol())) {
            KafkaProperties.SaslProperties sasl = security.sasl();
            if (sasl != null) {
                props.put("sasl.mechanism", sasl.mechanism());
                
                // Use the JAAS config directly if provided, otherwise create a basic one
                if (sasl.jaasConfig() != null && !sasl.jaasConfig().isEmpty()) {
                    props.put("sasl.jaas.config", sasl.jaasConfig());
                } else {
                    // Fallback JAAS config for development - should be configured properly in production
                    String jaasConfig = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                            "username=\"kafka\" password=\"kafka\";";
                    props.put("sasl.jaas.config", jaasConfig);
                }
            }
        }
    }
}

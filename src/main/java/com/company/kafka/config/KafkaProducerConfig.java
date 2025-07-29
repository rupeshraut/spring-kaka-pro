package com.company.kafka.config;

import com.company.kafka.metrics.KafkaMetrics;
import com.company.kafka.partition.CustomPartitioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import com.company.kafka.config.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration
 * 
 * Pro Tips:
 * 1. Always enable idempotence in production (enable.idempotence=true)
 * 2. Set acks=all for maximum durability
 * 3. Use appropriate batching (batch.size + linger.ms) for throughput
 * 4. Enable compression (snappy/lz4) to reduce network usage
 * 5. Configure proper timeouts for your use case
 * 6. Monitor producer metrics regularly
 * 
 * Production Optimizations:
 * - Idempotent producer prevents duplicate messages
 * - max.in.flight.requests.per.connection=1 ensures ordering
 * - Compression reduces bandwidth usage
 * - Proper buffer sizing improves throughput
 * 
 * @author Development Team
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerConfig {

    private final KafkaProperties kafkaProperties;
    private final PartitioningProperties partitioningProperties;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Production reliability settings
        var producerProps = kafkaProperties.producer();
        configProps.put(ProducerConfig.ACKS_CONFIG, producerProps.acks());
        configProps.put(ProducerConfig.RETRIES_CONFIG, producerProps.retries());
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producerProps.enableIdempotence());
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 
            producerProps.maxInFlightRequestsPerConnection());
        
        // Performance tuning
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, producerProps.batchSize());
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, producerProps.lingerMs());
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerProps.compressionType());
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producerProps.bufferMemory());
        
        // Timeout configurations
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 
            (int) producerProps.requestTimeout().toMillis());
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 
            (int) producerProps.deliveryTimeout().toMillis());
        
        // Security configuration (optional - configure if needed)
        // if (kafkaProperties.security().enabled()) {
        //     configureSecurity(configProps);
        // }
        
        // Custom partitioner configuration
        configProps.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, CustomPartitioner.class);
        configProps.putAll(buildPartitionerConfig());
        
        // Monitoring interceptors (optional - configure if needed)
        // if (kafkaProperties.monitoring().enabled() && !kafkaProperties.monitoring().interceptors().isEmpty()) {
        //     configProps.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, 
        //         kafkaProperties.monitoring().interceptors());
        // }
        
        log.info("Producer configuration initialized with custom partitioner strategy: {}", 
            partitioningProperties.producer().strategy());
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        
        // Configure default topic if needed
        // template.setDefaultTopic("default-topic");
        
        // Configure transaction support if needed
        // template.setTransactionIdPrefix("tx-");
        
        // Add custom error handling
        template.setProducerListener(new KafkaMetrics.ProducerMetricsListener());
        
        log.info("KafkaTemplate configured successfully");
        return template;
    }
    
    // Optional security configuration method
    // private void configureSecurity(Map<String, Object> configProps) {
    //     var security = kafkaProperties.security();
    //     
    //     configProps.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, security.protocol());
    //     
    //     // SSL Configuration
    //     var ssl = security.ssl();
    //     if (ssl.trustStoreLocation() != null) {
    //         configProps.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, ssl.trustStoreLocation());
    //         configProps.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.trustStorePassword());
    //     }
    //     
    //     if (ssl.keyStoreLocation() != null) {
    //         configProps.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, ssl.keyStoreLocation());
    //         configProps.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.keyStorePassword());
    //         configProps.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, ssl.keyStoreType());
    //     }
    //     
    //     // SASL Configuration
    //     var sasl = security.sasl();
    //     if (sasl.mechanism() != null) {
    //         configProps.put(SaslConfigs.SASL_MECHANISM, sasl.mechanism());
    //     }
    //     
    //     if (sasl.jaasConfig() != null) {
    //         configProps.put(SaslConfigs.SASL_JAAS_CONFIG, sasl.jaasConfig());
    //     }
    //     
    //     log.info("Security configuration applied: protocol={}, mechanism={}", 
    //         security.protocol(), sasl.mechanism());
    // }
    
    private Map<String, Object> buildPartitionerConfig() {
        Map<String, Object> partitionerConfig = new HashMap<>();
        
        var producer = partitioningProperties.producer();
        
        // Basic partitioner strategy
        partitionerConfig.put("partitioner.strategy", producer.strategy());
        
        // Sticky partitioner configuration
        if (producer.sticky() != null) {
            partitionerConfig.put("partitioner.sticky.partition.count", producer.sticky().partitionCount());
        }
        
        // Customer-aware partitioner configuration
        if (producer.customer() != null) {
            partitionerConfig.put("partitioner.customer.hash.seed", producer.customer().hashSeed());
        }
        
        // Priority partitioner configuration
        if (producer.priority() != null) {
            partitionerConfig.put("partitioner.priority.partition.ratio", producer.priority().partitionRatio());
        }
        
        // Geographic partitioner configuration
        if (producer.geographic() != null) {
            partitionerConfig.put("partitioner.geographic.regions", 
                String.join(",", producer.geographic().regions()));
        }
        
        // Time-based partitioner configuration
        if (producer.timeBased() != null) {
            partitionerConfig.put("partitioner.time.window.ms", 
                producer.timeBased().timeWindow().toMillis());
        }
        
        // Load-aware partitioner configuration
        if (producer.loadAware() != null) {
            partitionerConfig.put("partitioner.load.aware.threshold", 
                producer.loadAware().loadThreshold());
        }
        
        log.info("Partitioner configuration applied: strategy={}, config={}", 
            producer.strategy(), partitionerConfig.size());
        
        return partitionerConfig;
    }
}

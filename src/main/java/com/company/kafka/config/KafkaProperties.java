package com.company.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Production-ready Kafka configuration properties using immutable records.
 * 
 * Features:
 * - Immutable configuration with validation
 * - Nested property groups
 * - Sensible defaults
 * - Duration and size types
 * 
 * Pro Tips:
 * 1. Use records for immutable configuration properties
 * 2. Always validate critical configuration properties
 * 3. Provide defaults in constructors, not field declarations
 * 4. Group related properties into nested records
 */
@ConfigurationProperties(prefix = "kafka")
@Validated
public record KafkaProperties(
    @NotBlank String bootstrapServers,
    @Valid @NotNull ProducerProperties producer,
    @Valid @NotNull ConsumerProperties consumer,
    @Valid @NotNull AdminProperties admin,
    @Valid @NotNull SecurityProperties security,
    @Valid @NotNull TopicsProperties topics
) {
    public KafkaProperties {
        bootstrapServers = bootstrapServers != null ? bootstrapServers : "localhost:9092";
        producer = producer != null ? producer : new ProducerProperties(null, null, null, null, null, null, null, null, null, null);
        consumer = consumer != null ? consumer : new ConsumerProperties(null, null, null, null, null, null, null, null, null, null, null);
        admin = admin != null ? admin : new AdminProperties(null, null, null);
        security = security != null ? security : new SecurityProperties(null, null, null, null);
        topics = topics != null ? topics : new TopicsProperties(null, null, null);
    }

    public record ProducerProperties(
        String acks,
        Integer retries,
        Integer batchSize,
        Integer lingerMs,
        String compressionType,
        Long bufferMemory,
        Boolean enableIdempotence,
        Integer maxInFlightRequestsPerConnection,
        Duration requestTimeout,
        Duration deliveryTimeout
    ) {
        public ProducerProperties {
            acks = acks != null ? acks : "all";
            retries = retries != null ? retries : Integer.MAX_VALUE;
            batchSize = batchSize != null ? batchSize : 32768;
            lingerMs = lingerMs != null ? lingerMs : 20;
            compressionType = compressionType != null ? compressionType : "snappy";
            bufferMemory = bufferMemory != null ? bufferMemory : 67108864L;
            enableIdempotence = enableIdempotence != null ? enableIdempotence : true;
            maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection != null ? maxInFlightRequestsPerConnection : 1;
            requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
            deliveryTimeout = deliveryTimeout != null ? deliveryTimeout : Duration.ofMinutes(2);
        }
    }

    public record ConsumerProperties(
        @NotBlank String groupId,
        String autoOffsetReset,
        Boolean enableAutoCommit,
        Integer maxPollRecords,
        Duration maxPollInterval,
        Duration sessionTimeout,
        Duration heartbeatInterval,
        Integer fetchMinBytes,
        Duration fetchMaxWait,
        String isolationLevel,
        Integer concurrency
    ) {
        public ConsumerProperties {
            groupId = groupId != null ? groupId : "spring-kafka-pro-group";
            autoOffsetReset = autoOffsetReset != null ? autoOffsetReset : "earliest";
            enableAutoCommit = enableAutoCommit != null ? enableAutoCommit : false;
            maxPollRecords = maxPollRecords != null ? maxPollRecords : 500;
            maxPollInterval = maxPollInterval != null ? maxPollInterval : Duration.ofMinutes(5);
            sessionTimeout = sessionTimeout != null ? sessionTimeout : Duration.ofSeconds(30);
            heartbeatInterval = heartbeatInterval != null ? heartbeatInterval : Duration.ofSeconds(10);
            fetchMinBytes = fetchMinBytes != null ? fetchMinBytes : 50000;
            fetchMaxWait = fetchMaxWait != null ? fetchMaxWait : Duration.ofMillis(500);
            isolationLevel = isolationLevel != null ? isolationLevel : "read_committed";
            concurrency = concurrency != null ? concurrency : 3;
        }
    }

    public record AdminProperties(
        Duration requestTimeout,
        Integer retries,
        Duration retryBackoff
    ) {
        public AdminProperties {
            requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
            retries = retries != null ? retries : 3;
            retryBackoff = retryBackoff != null ? retryBackoff : Duration.ofSeconds(1);
        }
    }

    public record SecurityProperties(
        Boolean enabled,
        String protocol,
        @Valid SslProperties ssl,
        @Valid SaslProperties sasl
    ) {
        public SecurityProperties {
            enabled = enabled != null ? enabled : false;
            protocol = protocol != null ? protocol : "PLAINTEXT";
            ssl = ssl != null ? ssl : new SslProperties(null, null, null, null, null);
            sasl = sasl != null ? sasl : new SaslProperties(null, null);
        }
    }

    public record SslProperties(
        String trustStoreLocation,
        String trustStorePassword,
        String keyStoreLocation,
        String keyStorePassword,
        String keyStoreType
    ) {
        public SslProperties {
            trustStoreLocation = trustStoreLocation != null ? trustStoreLocation : "";
            trustStorePassword = trustStorePassword != null ? trustStorePassword : "";
            keyStoreLocation = keyStoreLocation != null ? keyStoreLocation : "";
            keyStorePassword = keyStorePassword != null ? keyStorePassword : "";
            keyStoreType = keyStoreType != null ? keyStoreType : "PKCS12";
        }
    }

    public record SaslProperties(
        String mechanism,
        String jaasConfig
    ) {
        public SaslProperties {
            mechanism = mechanism != null ? mechanism : "PLAIN";
            jaasConfig = jaasConfig != null ? jaasConfig : "";
        }
    }

    public record TopicsProperties(
        @Valid TopicConfig orders,
        @Valid TopicConfig payments,
        @Valid TopicConfig notifications
    ) {
        public TopicsProperties {
            orders = orders != null ? orders : new TopicConfig("orders-topic", 3, 1);
            payments = payments != null ? payments : new TopicConfig("payments-topic", 3, 1);
            notifications = notifications != null ? notifications : new TopicConfig("notifications-topic", 3, 1);
        }
    }

    public record TopicConfig(
        @NotBlank String name,
        Integer partitions,
        Integer replicationFactor
    ) {
        public TopicConfig {
            name = name != null ? name : "default-topic";
            partitions = partitions != null ? partitions : 3;
            replicationFactor = replicationFactor != null ? replicationFactor : 1;
        }
    }
}

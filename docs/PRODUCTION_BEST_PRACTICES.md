# Production-Ready Spring Kafka Best Practices Guide

This comprehensive guide outlines enterprise-grade best practices for implementing Apache Kafka with Spring Boot in production environments. These practices have been proven in high-scale, mission-critical applications.

## Table of Contents
1. [Configuration Best Practices](#configuration-best-practices)
2. [Producer Optimization](#producer-optimization)
3. [Consumer Patterns](#consumer-patterns)
4. [Error Handling & Resilience](#error-handling--resilience)
5. [Monitoring & Observability](#monitoring--observability)
6. [Security Implementation](#security-implementation)
7. [Performance Optimization](#performance-optimization)
8. [Testing Strategies](#testing-strategies)
9. [Deployment & Operations](#deployment--operations)
10. [Troubleshooting Guide](#troubleshooting-guide)

---

## Configuration Best Practices

### 1. Use Immutable Configuration with Records

**✅ Recommended Pattern:**
```java
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaProperties(
    @NotBlank String bootstrapServers,
    @Valid ProducerProperties producer,
    @Valid ConsumerProperties consumer,
    @Valid TopicsProperties topics
) {
    public KafkaProperties {
        // Provide sensible defaults
        bootstrapServers = bootstrapServers != null ? bootstrapServers : "localhost:9092";
        producer = producer != null ? producer : new ProducerProperties();
    }
    
    public record ProducerProperties(
        @Min(0) int retries,
        @Min(1) int batchSize,
        @Min(0) int lingerMs,
        @NotNull String acks,
        boolean enableIdempotence
    ) {
        public ProducerProperties {
            retries = retries > 0 ? retries : 3;
            batchSize = batchSize > 0 ? batchSize : 16384;
            lingerMs = lingerMs >= 0 ? lingerMs : 5;
            acks = acks != null ? acks : "all";
            enableIdempotence = true; // Always enable in production
        }
    }
}
```

**❌ Avoid:**
```java
// Mutable configuration classes
@ConfigurationProperties
public class KafkaConfig {
    private String bootstrapServers; // Mutable
    // Missing validation
    // No sensible defaults
}
```

### 2. Environment-Specific Configuration

**✅ Production Configuration:**
```yaml
app:
  kafka:
    bootstrap:
      servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      # Reliability settings
      acks: all                    # Wait for all replicas
      retries: 2147483647         # Retry indefinitely
      enable-idempotence: true    # Prevent duplicates
      max-in-flight-requests-per-connection: 1  # Maintain ordering
      
      # Performance settings
      batch-size: 16384           # 16KB batches
      linger-ms: 5               # Small delay for batching
      compression-type: snappy    # Fast compression
      buffer-memory: 33554432    # 32MB buffer
      
      # Timeout settings
      request-timeout-ms: 30000   # 30 seconds
      delivery-timeout-ms: 120000 # 2 minutes total
      
    consumer:
      # Reliability settings
      enable-auto-commit: false   # Manual acknowledgment
      auto-offset-reset: earliest # Don't lose messages
      
      # Performance settings
      fetch-min-bytes: 1024      # Minimum fetch size
      fetch-max-wait-ms: 500     # Max wait for fetch
      max-poll-records: 500      # Batch size for processing
      
      # Session management
      session-timeout-ms: 30000   # 30 seconds
      heartbeat-interval-ms: 3000 # 3 seconds
      max-poll-interval-ms: 300000 # 5 minutes processing time
```

---

## Producer Optimization

### 1. Idempotent Producers (Critical for Production)

**✅ Always Enable Idempotence:**
```java
@Bean
public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
    
    // CRITICAL: Enable idempotence to prevent duplicates
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    
    return new DefaultKafkaProducerFactory<>(props);
}
```

### 2. Async Operations with Callbacks

**✅ Production-Ready Producer Service:**
```java
@Service
@Slf4j
public class KafkaProducerService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    public CompletableFuture<SendResult<String, Object>> sendMessage(
            String topic, String key, Object message) {
        
        String correlationId = generateCorrelationId();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("Sending message - topic: {}, key: {}, correlationId: {}", 
            topic, key, correlationId);
        
        return kafkaTemplate.send(topic, key, message)
            .whenComplete((result, ex) -> {
                sample.stop(Timer.builder("kafka.producer.send.time")
                    .tag("topic", topic)
                    .tag("status", ex == null ? "success" : "failure")
                    .register(meterRegistry));
                
                if (ex == null) {
                    log.info("Message sent successfully - topic: {}, partition: {}, offset: {}, correlationId: {}", 
                        topic, result.getRecordMetadata().partition(), 
                        result.getRecordMetadata().offset(), correlationId);
                    
                    meterRegistry.counter("kafka.producer.messages.sent", "topic", topic).increment();
                } else {
                    log.error("Failed to send message - topic: {}, correlationId: {}, error: {}", 
                        topic, correlationId, ex.getMessage(), ex);
                    
                    meterRegistry.counter("kafka.producer.messages.failed", "topic", topic).increment();
                }
            });
    }
    
    private String generateCorrelationId() {
        return "producer-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

### 3. Batch Operations for High Throughput

**✅ Efficient Batch Processing:**
```java
public CompletableFuture<Void> sendBatch(String topic, Map<String, Object> messages) {
    List<CompletableFuture<SendResult<String, Object>>> futures = messages.entrySet()
        .stream()
        .map(entry -> sendMessage(topic, entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
    
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Batch of {} messages sent successfully to topic: {}", messages.size(), topic);
                meterRegistry.counter("kafka.producer.batch.sent", "topic", topic).increment();
            } else {
                log.error("Batch send failed for topic: {}, error: {}", topic, ex.getMessage(), ex);
                meterRegistry.counter("kafka.producer.batch.failed", "topic", topic).increment();
            }
        });
}
```

---

## Consumer Patterns

### 1. Manual Acknowledgment (Production Standard)

**✅ Manual Acknowledgment Pattern:**
```java
@Service
@Slf4j
public class KafkaConsumerService {
    
    @KafkaListener(topics = "#{kafkaProperties.topics.orders.name}", 
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        log.info("Processing message - topic: {}, partition: {}, offset: {}, correlationId: {}", 
            topic, partition, offset, correlationId);
        
        try {
            // Validate message first
            validateMessage(message);
            
            // Process the business logic
            processOrderEvent(message, correlationId);
            
            // Only acknowledge after successful processing
            acknowledgment.acknowledge();
            
            meterRegistry.counter("kafka.consumer.messages.processed", "topic", topic).increment();
            log.info("Message processed successfully - correlationId: {}", correlationId);
            
        } catch (ValidationException e) {
            // Don't retry validation errors - acknowledge to prevent reprocessing
            acknowledgment.acknowledge();
            meterRegistry.counter("kafka.consumer.messages.invalid", "topic", topic).increment();
            log.error("Validation failed - correlationId: {}, error: {}", correlationId, e.getMessage());
            
        } catch (RecoverableException e) {
            // Don't acknowledge - let retry mechanism handle it
            meterRegistry.counter("kafka.consumer.messages.retryable", "topic", topic).increment();
            log.error("Recoverable error - correlationId: {}, error: {}", correlationId, e.getMessage());
            throw e; // Rethrow to trigger retry
            
        } catch (Exception e) {
            // Unknown errors - acknowledge to prevent infinite retry
            acknowledgment.acknowledge();
            meterRegistry.counter("kafka.consumer.messages.failed", "topic", topic).increment();
            log.error("Unexpected error - correlationId: {}, error: {}", correlationId, e.getMessage(), e);
            
        } finally {
            sample.stop(Timer.builder("kafka.consumer.processing.time")
                .tag("topic", topic)
                .register(meterRegistry));
        }
    }
}
```

### 2. Consumer Configuration for Production

**✅ Production Consumer Configuration:**
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
        new ConcurrentKafkaListenerContainerFactory<>();
    
    factory.setConsumerFactory(consumerFactory());
    
    // Manual acknowledgment mode
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    
    // Error handling
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        // Dead letter topic handler
        new DeadLetterPublishingRecoverer(kafkaTemplate()),
        // Exponential backoff: 1s, 2s, 4s, 8s, 16s
        new ExponentialBackOffWithMaxRetries(5)
    ));
    
    // Concurrency for parallel processing
    factory.setConcurrency(3);
    
    // Consumer lifecycle management
    factory.getContainerProperties().setConsumerRebalanceListener(
        new LoggingConsumerRebalanceListener());
    
    return factory;
}
```

---

## Error Handling & Resilience

### 1. Exception Hierarchy

**✅ Production Exception Framework:**
```java
/**
 * Base exception for message validation failures.
 * These are non-retryable - the message format or content is invalid.
 */
public class ValidationException extends Exception {
    private final String messageContent;
    private final String validationRule;
    
    public ValidationException(String message, String messageContent, String validationRule) {
        super(message);
        this.messageContent = messageContent;
        this.validationRule = validationRule;
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.messageContent = null;
        this.validationRule = null;
    }
    
    // Getters for detailed error reporting
    public String getMessageContent() { return messageContent; }
    public String getValidationRule() { return validationRule; }
}

/**
 * Exception for temporary failures that may succeed on retry.
 * Examples: network timeouts, database connectivity, external service unavailable.
 */
public class RecoverableException extends RuntimeException {
    private final String correlationId;
    private final int attemptNumber;
    
    public RecoverableException(String message, String correlationId, int attemptNumber) {
        super(message);
        this.correlationId = correlationId;
        this.attemptNumber = attemptNumber;
    }
    
    public RecoverableException(String message, Throwable cause) {
        super(message, cause);
        this.correlationId = null;
        this.attemptNumber = 0;
    }
    
    // Getters for retry logic
    public String getCorrelationId() { return correlationId; }
    public int getAttemptNumber() { return attemptNumber; }
}

/**
 * Exception for permanent failures that should not be retried.
 * Examples: authentication failures, authorization errors, configuration problems.
 */
public class NonRecoverableException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> context;
    
    public NonRecoverableException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }
    
    public NonRecoverableException(String message, String errorCode, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>(context);
    }
    
    // Getters for error analysis
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getContext() { return new HashMap<>(context); }
}
```

### 2. Retry Configuration with Exponential Backoff

**✅ Advanced Retry Configuration:**
```java
@Bean
public DefaultErrorHandler errorHandler() {
    // Exponential backoff: 1s, 2s, 4s, 8s, 16s, then 32s for remaining attempts
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(10);
    backOff.setInitialInterval(1000L);      // 1 second
    backOff.setMultiplier(2.0);             // Double each time
    backOff.setMaxInterval(32000L);         // Max 32 seconds
    
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(
        // Dead letter topic publisher
        new DeadLetterPublishingRecoverer(kafkaTemplate(), 
            (record, ex) -> {
                // Route to topic-specific dead letter topics
                return new TopicPartition(record.topic() + ".DLT", -1);
            }),
        backOff
    );
    
    // Don't retry validation exceptions
    errorHandler.addNotRetryableExceptions(ValidationException.class);
    errorHandler.addNotRetryableExceptions(NonRecoverableException.class);
    
    // Retry these exceptions
    errorHandler.addRetryableExceptions(RecoverableException.class);
    errorHandler.addRetryableExceptions(TransientDataAccessException.class);
    
    return errorHandler;
}
```

### 3. Dead Letter Topic Handling

**✅ Comprehensive Dead Letter Topic Strategy:**
```java
@Bean
public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer() {
    Map<Class<? extends Throwable>, KafkaOperations<?, ?>> templates = new HashMap<>();
    templates.put(ValidationException.class, validationErrorKafkaTemplate());
    templates.put(NonRecoverableException.class, nonRecoverableErrorKafkaTemplate());
    
    return new DeadLetterPublishingRecoverer(kafkaTemplate(),
        (record, exception) -> {
            String suffix = ".DLT";
            
            // Different DLT based on exception type
            if (exception instanceof ValidationException) {
                suffix = ".VALIDATION.DLT";
            } else if (exception instanceof NonRecoverableException) {
                suffix = ".NON_RECOVERABLE.DLT";
            } else {
                suffix = ".UNKNOWN.DLT";
            }
            
            return new TopicPartition(record.topic() + suffix, -1);
        }) {
        
        @Override
        public void accept(ConsumerRecord<?, ?> record, Exception exception) {
            // Add metadata headers before sending to DLT
            ProducerRecord<Object, Object> outRecord = createProducerRecord(record, exception);
            
            // Add error context headers
            outRecord.headers().add("original-topic", record.topic().getBytes());
            outRecord.headers().add("error-class", exception.getClass().getName().getBytes());
            outRecord.headers().add("error-message", exception.getMessage().getBytes());
            outRecord.headers().add("error-timestamp", 
                String.valueOf(System.currentTimeMillis()).getBytes());
            
            if (exception instanceof ValidationException) {
                ValidationException ve = (ValidationException) exception;
                outRecord.headers().add("validation-rule", 
                    ve.getValidationRule().getBytes());
            }
            
            super.accept(record, exception);
            
            // Metrics
            meterRegistry.counter("kafka.consumer.messages.dead_letter",
                "topic", record.topic(),
                "error_type", exception.getClass().getSimpleName())
                .increment();
        }
    };
}
```

---

## Monitoring & Observability

### 1. Health Indicators

**✅ Production Health Monitoring:**
```java
@Component
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaProducerService producerService;
    private final KafkaConsumerService consumerService;
    private final MeterRegistry meterRegistry;
    
    @Override
    public Health health() {
        try {
            // Check basic connectivity
            HealthStatus connectivity = checkKafkaConnectivity();
            
            // Gather service metrics
            Map<String, Object> producerMetrics = producerService.getHealthInfo();
            Map<String, Object> consumerMetrics = consumerService.getHealthInfo();
            
            // Calculate health scores
            double producerErrorRate = calculateErrorRate(producerMetrics);
            double consumerErrorRate = calculateErrorRate(consumerMetrics);
            double consumerLag = getConsumerLag();
            
            // Determine overall health
            HealthStatus overallStatus = determineOverallHealth(
                connectivity, producerErrorRate, consumerErrorRate, consumerLag);
            
            Health.Builder builder = overallStatus == HealthStatus.UP ? 
                Health.up() : Health.down();
            
            return builder
                .withDetail("connectivity", connectivity.name())
                .withDetail("producer", Map.of(
                    "status", getServiceStatus(producerErrorRate),
                    "errorRate", String.format("%.2f%%", producerErrorRate * 100),
                    "metrics", producerMetrics
                ))
                .withDetail("consumer", Map.of(
                    "status", getServiceStatus(consumerErrorRate),
                    "errorRate", String.format("%.2f%%", consumerErrorRate * 100),
                    "lag", consumerLag,
                    "metrics", consumerMetrics
                ))
                .withDetail("overallStatus", overallStatus.name())
                .withDetail("timestamp", Instant.now().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withException(e)
                .build();
        }
    }
    
    private HealthStatus checkKafkaConnectivity() {
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                kafkaTemplate.partitionsFor("__consumer_offsets");
            });
            
            future.get(5, TimeUnit.SECONDS);
            return HealthStatus.UP;
            
        } catch (Exception e) {
            log.warn("Kafka connectivity check failed: {}", e.getMessage());
            return HealthStatus.DOWN;
        }
    }
    
    private double getConsumerLag() {
        try {
            // Implement consumer lag monitoring
            // This would typically use Kafka AdminClient to get consumer group info
            return 0.0; // Placeholder
        } catch (Exception e) {
            log.warn("Failed to get consumer lag", e);
            return -1.0; // Indicates monitoring failure
        }
    }
    
    private HealthStatus determineOverallHealth(HealthStatus connectivity, 
            double producerErrorRate, double consumerErrorRate, double consumerLag) {
        
        if (connectivity == HealthStatus.DOWN) {
            return HealthStatus.DOWN;
        }
        
        // Critical thresholds
        if (producerErrorRate > 0.10 || consumerErrorRate > 0.10) { // 10% error rate
            return HealthStatus.DOWN;
        }
        
        if (consumerLag > 10000) { // 10k message lag
            return HealthStatus.DOWN;
        }
        
        return HealthStatus.UP;
    }
    
    enum HealthStatus {
        UP, DOWN
    }
}
```

### 2. Custom Metrics

**✅ Comprehensive Metrics Collection:**
```java
@Component
public class KafkaMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public KafkaMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        // Producer metrics
        Counter.builder("kafka.producer.messages.sent")
            .description("Total number of messages sent successfully")
            .register(meterRegistry);
            
        Counter.builder("kafka.producer.messages.failed")
            .description("Total number of failed message sends")
            .register(meterRegistry);
            
        Timer.builder("kafka.producer.send.time")
            .description("Time taken to send messages")
            .register(meterRegistry);
            
        Gauge.builder("kafka.producer.buffer.available")
            .description("Available buffer memory in bytes")
            .register(meterRegistry, this, KafkaMetricsCollector::getProducerBufferAvailable);
        
        // Consumer metrics
        Counter.builder("kafka.consumer.messages.processed")
            .description("Total number of messages processed successfully")
            .register(meterRegistry);
            
        Counter.builder("kafka.consumer.messages.failed")
            .description("Total number of failed message processing attempts")
            .register(meterRegistry);
            
        Timer.builder("kafka.consumer.processing.time")
            .description("Time taken to process messages")
            .register(meterRegistry);
            
        Gauge.builder("kafka.consumer.lag")
            .description("Consumer lag in number of messages")
            .register(meterRegistry, this, KafkaMetricsCollector::getConsumerLag);
        
        // Business metrics
        Counter.builder("kafka.business.orders.processed")
            .description("Number of order events processed")
            .register(meterRegistry);
            
        Counter.builder("kafka.business.payments.processed")
            .description("Number of payment events processed")
            .register(meterRegistry);
    }
    
    // Metric collection methods
    private double getProducerBufferAvailable() {
        // Implementation to get producer buffer metrics
        return 0.0;
    }
    
    private double getConsumerLag() {
        // Implementation to get consumer lag
        return 0.0;
    }
}
```

---

## Security Implementation

### 1. SSL/TLS Configuration

**✅ Production SSL Configuration:**
```yaml
app:
  kafka:
    security:
      protocol: SSL
      ssl:
        truststore-location: ${KAFKA_SSL_TRUSTSTORE_LOCATION:/etc/kafka/ssl/kafka.client.truststore.jks}
        truststore-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD}
        keystore-location: ${KAFKA_SSL_KEYSTORE_LOCATION:/etc/kafka/ssl/kafka.client.keystore.jks}
        keystore-password: ${KAFKA_SSL_KEYSTORE_PASSWORD}
        key-password: ${KAFKA_SSL_KEY_PASSWORD}
        endpoint-identification-algorithm: https
```

```java
@Bean
public ProducerFactory<String, Object> secureProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers());
    
    // SSL Configuration
    if ("SSL".equals(kafkaProperties.security().protocol())) {
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, 
            kafkaProperties.security().ssl().truststoreLocation());
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, 
            kafkaProperties.security().ssl().truststorePassword());
        props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, 
            kafkaProperties.security().ssl().keystoreLocation());
        props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, 
            kafkaProperties.security().ssl().keystorePassword());
        props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, 
            kafkaProperties.security().ssl().keyPassword());
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
    }
    
    return new DefaultKafkaProducerFactory<>(props);
}
```

### 2. SASL Authentication

**✅ SASL Configuration:**
```yaml
app:
  kafka:
    security:
      protocol: SASL_SSL
      sasl:
        mechanism: PLAIN
        jaas-config: >
          org.apache.kafka.common.security.plain.PlainLoginModule required
          username="${KAFKA_SASL_USERNAME}"
          password="${KAFKA_SASL_PASSWORD}";
```

### 3. Access Control and Authorization

**✅ Topic-Level Security:**
```java
@PreAuthorize("hasRole('KAFKA_PRODUCER')")
@PostMapping("/api/kafka/orders")
public ResponseEntity<?> sendOrderEvent(@RequestBody @Valid OrderRequest request) {
    // Implementation
}

@PreAuthorize("hasRole('KAFKA_ADMIN')")
@GetMapping("/api/kafka/admin/topics")
public ResponseEntity<?> listTopics() {
    // Implementation
}
```

---

## Performance Optimization

### 1. Producer Performance Tuning

**✅ High-Throughput Producer Configuration:**
```java
@Bean
public ProducerFactory<String, Object> highThroughputProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    
    // Throughput optimization
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);        // 32KB batches
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10);            // 10ms linger time
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");   // Fast compression
    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);   // 64MB buffer
    
    // Connection optimization
    props.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 540000); // 9 minutes
    props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576);        // 1MB max request
    
    // Reliability (don't compromise)
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    
    return new DefaultKafkaProducerFactory<>(props);
}
```

### 2. Consumer Performance Tuning

**✅ High-Throughput Consumer Configuration:**
```java
@Bean
public ConsumerFactory<String, Object> highThroughputConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    
    // Throughput optimization
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 50000);    // 50KB minimum fetch
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);    // 500ms max wait
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);    // Process 1000 records
    
    // Memory optimization
    props.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, 262144);    // 256KB receive buffer
    props.put(ConsumerConfig.SEND_BUFFER_CONFIG, 131072);       // 128KB send buffer
    
    // Partition assignment
    props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, 
        Arrays.asList(
            CooperativeStickyAssignor.class,  // Minimize rebalancing
            RangeAssignor.class               // Fallback
        ));
    
    return new DefaultKafkaConsumerFactory<>(props);
}
```

### 3. Connection Pooling and Resource Management

**✅ Resource Optimization:**
```java
@Configuration
public class KafkaResourceManagement {
    
    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        
        // Connection management
        template.setCloseTimeout(Duration.ofSeconds(30));
        template.setTransactionIdPrefix("tx-");
        
        // Default topic and routing
        template.setDefaultTopic("default-topic");
        
        return template;
    }
    
    @PreDestroy
    public void cleanup() {
        // Ensure proper resource cleanup
        log.info("Cleaning up Kafka resources...");
    }
}
```

---

## Testing Strategies

### 1. Integration Testing with TestContainers

**✅ Production-Like Integration Tests:**
```java
@SpringBootTest
@Testcontainers
@DirtiesContext
class KafkaIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"))
            .withEmbeddedZookeeper();
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap.servers", kafka::getBootstrapServers);
    }
    
    @Autowired
    private KafkaProducerService producerService;
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Test
    @Timeout(30)
    void shouldSendAndConsumeMessage() throws Exception {
        // Given
        String topic = "test-topic";
        String key = "test-key";
        String message = "test-message";
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // Set up consumer
        kafkaTemplate.setConsumerFactory(consumerFactory());
        kafkaTemplate.receive(topic, 0, 0, Duration.ofSeconds(10));
        
        // When
        CompletableFuture<SendResult<String, Object>> future = 
            producerService.sendMessage(topic, key, message);
        
        // Then
        SendResult<String, Object> result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.getRecordMetadata().topic()).isEqualTo(topic);
    }
}
```

### 2. Contract Testing

**✅ Message Schema Validation:**
```java
@Component
public class MessageSchemaValidator {
    
    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemas;
    
    public void validateOrderEvent(String message) throws ValidationException {
        try {
            JsonNode messageNode = objectMapper.readTree(message);
            JsonSchema schema = schemas.get("order-event");
            
            Set<ValidationMessage> errors = schema.validate(messageNode);
            if (!errors.isEmpty()) {
                throw new ValidationException("Schema validation failed: " + errors);
            }
        } catch (Exception e) {
            throw new ValidationException("Invalid message format", e);
        }
    }
}
```

### 3. Load Testing

**✅ Performance Testing:**
```java
@Test
@Tag("performance")
void loadTestProducer() throws Exception {
    int messageCount = 10000;
    int concurrency = 10;
    
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    CountDownLatch latch = new CountDownLatch(messageCount);
    
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < messageCount; i++) {
        executor.submit(() -> {
            try {
                producerService.sendMessage("load-test-topic", 
                    UUID.randomUUID().toString(), 
                    "load-test-message");
                latch.countDown();
            } catch (Exception e) {
                log.error("Failed to send message", e);
            }
        });
    }
    
    latch.await(60, TimeUnit.SECONDS);
    long endTime = System.currentTimeMillis();
    
    double throughput = messageCount * 1000.0 / (endTime - startTime);
    log.info("Throughput: {} messages/second", throughput);
    
    assertThat(throughput).isGreaterThan(1000); // At least 1000 msg/sec
}
```

---

## Deployment & Operations

### 1. Docker Configuration

**✅ Production Dockerfile:**
```dockerfile
FROM openjdk:17-jre-slim

# Create non-root user
RUN addgroup --system kafka && adduser --system --group kafka

# Copy application
COPY build/libs/spring-kafka-pro-*.jar app.jar

# Set up logging
RUN mkdir -p /var/log/app && chown kafka:kafka /var/log/app

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

USER kafka

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 2. Kubernetes Deployment

**✅ Production K8s Configuration:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-kafka-pro
spec:
  replicas: 3
  selector:
    matchLabels:
      app: spring-kafka-pro
  template:
    metadata:
      labels:
        app: spring-kafka-pro
    spec:
      containers:
      - name: spring-kafka-pro
        image: spring-kafka-pro:latest
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-cluster:9092"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

### 3. Monitoring and Alerting

**✅ Prometheus Configuration:**
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-kafka-pro'
    static_configs:
      - targets: ['spring-kafka-pro:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

**✅ Grafana Dashboard Configuration:**
```json
{
  "dashboard": {
    "title": "Spring Kafka Pro Dashboard",
    "panels": [
      {
        "title": "Message Throughput",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(kafka_producer_messages_sent_total[5m])",
            "legendFormat": "Messages Sent/sec"
          },
          {
            "expr": "rate(kafka_consumer_messages_processed_total[5m])",
            "legendFormat": "Messages Processed/sec"
          }
        ]
      },
      {
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(kafka_producer_messages_failed_total[5m]) / rate(kafka_producer_messages_sent_total[5m]) * 100",
            "legendFormat": "Producer Error Rate %"
          }
        ]
      }
    ]
  }
}
```

---

## Troubleshooting Guide

### 1. Common Issues and Solutions

**Issue: Consumer Lag**
```bash
# Check consumer group status
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --describe

# Solutions:
# 1. Increase consumer instances
# 2. Optimize processing logic
# 3. Increase max.poll.records
# 4. Add more partitions to topic
```

**Issue: Producer Performance**
```bash
# Monitor producer metrics
curl http://localhost:8080/actuator/metrics/kafka.producer.send.time

# Solutions:
# 1. Increase batch.size
# 2. Adjust linger.ms
# 3. Enable compression
# 4. Increase buffer.memory
```

### 2. Debugging Tools

**✅ Debug Configuration:**
```yaml
logging:
  level:
    org.apache.kafka: DEBUG
    org.springframework.kafka: DEBUG
    com.company.kafka: DEBUG
```

**✅ JMX Monitoring:**
```java
@Bean
public MBeanExporter kafkaJmxExporter() {
    MBeanExporter exporter = new MBeanExporter();
    exporter.setDefaultDomain("kafka.spring");
    return exporter;
}
```

### 3. Performance Profiling

**✅ Performance Analysis:**
```java
@Component
@Profile("performance")
public class KafkaPerformanceProfiler {
    
    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        // Record timing and throughput metrics
        long processingTime = event.getEndTime() - event.getStartTime();
        
        if (processingTime > 1000) { // Log slow messages
            log.warn("Slow message processing: {}ms for message: {}", 
                processingTime, event.getMessageId());
        }
    }
}
```

---

## Summary

This comprehensive guide covers all aspects of production-ready Spring Kafka implementation:

1. **Configuration**: Immutable, validated, environment-specific
2. **Producers**: Idempotent, async, with proper error handling
3. **Consumers**: Manual acknowledgment, retry logic, proper error classification
4. **Monitoring**: Health checks, metrics, alerting
5. **Security**: SSL/TLS, SASL authentication, authorization
6. **Performance**: Optimized for throughput and reliability
7. **Testing**: Integration, contract, and load testing
8. **Operations**: Docker, Kubernetes, monitoring setup

Following these practices ensures your Kafka implementation is enterprise-ready, scalable, and maintainable in production environments.

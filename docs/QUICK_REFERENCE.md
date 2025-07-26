# Spring Kafka Pro - Quick Reference Guide

## Quick Start Commands

### Build and Run
```bash
# Build the application
./gradlew clean build

# Run locally
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Build Docker image
docker build -t spring-kafka-pro:latest .

# Run with Docker
docker run -p 8080:8080 spring-kafka-pro:latest
```

### Development Setup
```bash
# Start local Kafka cluster
docker-compose up -d kafka zookeeper

# Create test topics
kafka-topics.sh --create --topic orders-topic --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092

# View application logs
tail -f logs/application.log

# Check health
curl http://localhost:8080/actuator/health
```

## Configuration Quick Reference

### Essential Producer Settings
```yaml
app:
  kafka:
    producer:
      retries: 2147483647           # Infinite retries
      enable-idempotence: true      # Prevent duplicates
      acks: all                     # Wait for all replicas
      batch-size: 16384            # 16KB batches
      linger-ms: 5                 # Wait 5ms for batching
      compression-type: snappy      # Enable compression
```

### Essential Consumer Settings
```yaml
app:
  kafka:
    consumer:
      enable-auto-commit: false     # Manual acknowledgment
      auto-offset-reset: earliest   # Start from beginning
      max-poll-records: 500        # Process 500 records per poll
      session-timeout-ms: 30000    # 30 second session timeout
```

## Common Code Patterns

### Send Message
```java
@Autowired
private KafkaProducerService producerService;

// Async send
CompletableFuture<SendResult<String, Object>> future = 
    producerService.send("orders-topic", "order-123", orderEvent);

// With callback
future.whenComplete((result, ex) -> {
    if (ex != null) {
        log.error("Failed to send message", ex);
    } else {
        log.info("Message sent successfully: {}", result.getRecordMetadata());
    }
});
```

### Consume Message
```java
@KafkaListener(topics = "orders-topic")
public void handleOrderEvent(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        Acknowledgment acknowledgment) {
    
    try {
        // Process message
        processOrder(message);
        
        // Acknowledge successful processing
        acknowledgment.acknowledge();
        
    } catch (ValidationException e) {
        // Don't retry validation errors
        acknowledgment.acknowledge();
        log.warn("Validation error: {}", e.getMessage());
        
    } catch (RecoverableException e) {
        // Let retry mechanism handle
        log.warn("Recoverable error: {}", e.getMessage());
        throw e;
    }
}
```

### Error Handling
```java
// Throw appropriate exceptions
if (order.getAmount() < 0) {
    throw new ValidationException("Order amount cannot be negative", 
        Map.of("amount", order.getAmount()));
}

if (externalService.isDown()) {
    throw new RecoverableException("External service unavailable", 
        "order-service", correlationId);
}

if (order.getCustomerId() == null) {
    throw new NonRecoverableException("Missing customer ID", 
        "MISSING_CUSTOMER", Map.of("orderId", order.getId()));
}
```

## Monitoring Quick Reference

### Health Endpoints
```bash
# Overall health
curl http://localhost:8080/actuator/health

# Kafka-specific health
curl http://localhost:8080/actuator/health/kafka

# Readiness check
curl http://localhost:8080/actuator/health/readiness
```

### Metrics Endpoints
```bash
# All metrics
curl http://localhost:8080/actuator/metrics | jq .

# Kafka producer metrics
curl http://localhost:8080/actuator/metrics/kafka.producer.messages.sent

# Kafka consumer metrics
curl http://localhost:8080/actuator/metrics/kafka.consumer.messages.processed

# JVM memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### Kafka Admin Commands
```bash
# List topics
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe topic
kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic orders-topic

# List consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Check consumer lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --describe

# Reset consumer offsets (CAUTION!)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --reset-offsets --to-latest --execute --topic orders-topic
```

## Testing Quick Reference

### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @InjectMocks
    private KafkaProducerService producerService;
    
    @Test
    void shouldSendMessageSuccessfully() {
        // Given
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockSendResult));
        
        // When
        CompletableFuture<SendResult<String, Object>> result = 
            producerService.send("test-topic", "key", "message");
        
        // Then
        assertThat(result).isCompleted();
    }
}
```

### Integration Tests
```java
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${kafka.bootstrapServers}")
class KafkaIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Test
    void shouldProduceAndConsumeMessage() {
        // Test implementation
    }
}
```

## Troubleshooting Quick Reference

### Common Issues

#### High Consumer Lag
```bash
# Check current lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --describe

# Scale up consumers (if using Kubernetes)
kubectl scale deployment spring-kafka-pro --replicas=5

# Check processing time metrics
curl http://localhost:8080/actuator/metrics/kafka.consumer.processing.time
```

#### Producer Timeouts
```bash
# Check producer metrics
curl http://localhost:8080/actuator/metrics/kafka.producer.request.latency

# Check Kafka broker logs
kubectl logs kafka-broker-0

# Increase timeout in configuration
# producer.request-timeout-ms: 60000
```

#### Memory Issues
```bash
# Check JVM memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Get heap dump
curl -X POST http://localhost:8080/actuator/heapdump

# Check GC metrics
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

### Log Analysis
```bash
# Search for errors
grep -i "error" logs/application.log | tail -20

# Find specific correlation ID
grep "correlation-id-123" logs/application.log

# Monitor real-time logs
tail -f logs/application.log | grep -E "(ERROR|WARN)"
```

## Environment Variables

### Required
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
SPRING_PROFILES_ACTIVE=prod
```

### SSL Configuration
```bash
KAFKA_SECURITY_PROTOCOL=SSL
KAFKA_SSL_TRUSTSTORE_LOCATION=/app/ssl/kafka.client.truststore.jks
KAFKA_SSL_TRUSTSTORE_PASSWORD=password
KAFKA_SSL_KEYSTORE_LOCATION=/app/ssl/kafka.client.keystore.jks
KAFKA_SSL_KEYSTORE_PASSWORD=password
KAFKA_SSL_KEY_PASSWORD=password
```

### SASL Configuration
```bash
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=PLAIN
KAFKA_SASL_USERNAME=kafka-user
KAFKA_SASL_PASSWORD=kafka-password
```

## Performance Checklist

### ✅ Producer Optimization
- [ ] `enable.idempotence=true`
- [ ] `acks=all`
- [ ] `retries=Integer.MAX_VALUE`
- [ ] Appropriate `batch.size` (16KB-64KB)
- [ ] `linger.ms` > 0 for batching
- [ ] Compression enabled (snappy/lz4)

### ✅ Consumer Optimization
- [ ] `enable.auto.commit=false`
- [ ] Manual acknowledgment used
- [ ] Appropriate `max.poll.records`
- [ ] Proper `session.timeout.ms`
- [ ] `fetch.min.bytes` configured
- [ ] Consumer concurrency optimized

### ✅ Application Optimization
- [ ] Async processing enabled
- [ ] Connection pooling configured
- [ ] Thread pool properly sized
- [ ] JVM heap size appropriate
- [ ] GC tuned for workload

## Deployment Checklist

### Pre-Deployment
- [ ] Configuration validated
- [ ] SSL certificates valid
- [ ] Kafka cluster accessible
- [ ] Tests passing
- [ ] Monitoring configured

### During Deployment
- [ ] Zero-downtime strategy
- [ ] Health checks passing
- [ ] Metrics collecting
- [ ] No error spikes
- [ ] Consumer lag stable

### Post-Deployment
- [ ] Smoke tests passed
- [ ] Performance benchmarks met
- [ ] Alerts configured
- [ ] Documentation updated
- [ ] Team notified

## Emergency Procedures

### Immediate Response
```bash
# Check application status
curl -f http://localhost:8080/actuator/health || echo "App is down"

# Check recent logs
tail -100 logs/application.log | grep -E "(ERROR|FATAL)"

# Check consumer lag
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group my-group --describe

# Scale up if needed (Kubernetes)
kubectl scale deployment spring-kafka-pro --replicas=6
```

### Rollback Procedure
```bash
# Kubernetes rollback
kubectl rollout undo deployment/spring-kafka-pro

# Docker rollback
docker stop spring-kafka-pro
docker run -d --name spring-kafka-pro spring-kafka-pro:previous-version

# Check rollback success
curl -f http://localhost:8080/actuator/health
```

---

**📚 For comprehensive documentation, see:**
- [Production Best Practices](PRODUCTION_BEST_PRACTICES.md)
- [Architecture & Deployment Guide](ARCHITECTURE_DEPLOYMENT_GUIDE.md)
- [API Documentation](API_DOCUMENTATION.md)

**🆘 For emergencies:**
- Check #kafka-alerts Slack channel
- Page on-call engineer: +1-555-KAFKA
- Escalation: kafka-team@company.com

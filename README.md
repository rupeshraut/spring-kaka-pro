# Spring Kafka Production Starter

A comprehensive, production-ready Spring Boot application demonstrating best practices for Apache Kafka integration with advanced error handling, monitoring, security, and performance optimizations.

## 🚀 Features

### Core Capabilities
- **Production-Ready Configuration**: Optimized Kafka producer and consumer settings
- **Comprehensive Error Handling**: Retry mechanisms, dead letter topics, and circuit breakers
- **Advanced Monitoring**: Micrometer metrics, health checks, and Prometheus integration
- **Security First**: SSL/TLS encryption and SASL authentication support
- **High Performance**: Optimized batching, compression, and connection pooling
- **Observability**: Distributed tracing with correlation IDs
- **Testing Excellence**: TestContainers integration for reliable testing

### Advanced Features
- **Idempotent Producers**: Prevents duplicate messages in production
- **Manual Acknowledgment**: Precise control over message processing
- **Dead Letter Topics**: Automatic routing of failed messages
- **Consumer Lag Monitoring**: Real-time lag tracking and alerting
- **Graceful Shutdown**: Proper resource cleanup and message completion
- **Multi-Environment Support**: Separate configurations for dev, test, and production

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [Monitoring & Observability](#monitoring--observability)
- [Error Handling](#error-handling)
- [Security](#security)
- [Testing](#testing)
- [Deployment](#deployment)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## 🏃‍♂️ Quick Start

### Prerequisites
- Java 17 or higher
- Docker & Docker Compose (for local Kafka)
- Gradle 8.0+ (or use included wrapper)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/company/spring-kafka-pro.git
   cd spring-kafka-pro
   ```

2. **Start Kafka using Docker Compose**
   ```bash
   docker-compose up -d
   ```

3. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

4. **Verify the setup**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

### Docker Compose for Development

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

## 🏗️ Architecture

### Component Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Producer      │───▶│   Kafka Broker  │◀───│   Consumer      │
│   Service       │    │   (Topics)      │    │   Service       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Metrics &     │    │   Dead Letter   │    │   Message       │
│   Monitoring    │    │   Topics        │    │   Processor     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Key Components

1. **KafkaProducerService**: Handles message publishing with comprehensive error handling
2. **KafkaConsumerService**: Processes incoming messages with retry logic
3. **MessageProcessor**: Contains business logic with proper exception handling
4. **KafkaHealthIndicator**: Monitors Kafka connectivity and health
5. **KafkaMetrics**: Collects and exposes metrics for monitoring
6. **Error Handling**: Automatic retry and dead letter topic routing

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` | No |
| `KAFKA_CONSUMER_GROUP` | Consumer group ID | `spring-kafka-pro-group` | No |
| `KAFKA_SECURITY_ENABLED` | Enable security features | `false` | No |
| `KAFKA_USERNAME` | SASL username | - | If security enabled |
| `KAFKA_PASSWORD` | SASL password | - | If security enabled |

### Producer Configuration (Production)

```yaml
kafka:
  producer:
    acks: all                              # Wait for all replicas
    retries: 2147483647                   # Retry indefinitely
    enable-idempotence: true              # Prevent duplicates
    max-in-flight-requests-per-connection: 1  # Maintain ordering
    batch-size: 32768                     # 32KB batches
    linger-ms: 20                         # Wait 20ms for batching
    compression-type: snappy              # Enable compression
    buffer-memory: 67108864               # 64MB buffer
```

### Consumer Configuration (Production)

```yaml
kafka:
  consumer:
    enable-auto-commit: false             # Manual acknowledgment
    isolation-level: read_committed       # Read only committed messages
    max-poll-records: 500                 # Process 500 records max
    session-timeout-ms: 30000             # 30 second session timeout
    heartbeat-interval-ms: 10000          # 10 second heartbeat
    fetch-min-bytes: 50000                # Minimum 50KB fetch
    concurrency: 3                        # 3 consumer threads
```

## 📊 Monitoring & Observability

### Metrics Available

1. **Producer Metrics**
   - `kafka.producer.messages.sent` - Total messages sent
   - `kafka.producer.messages.failed` - Failed message attempts
   - `kafka.producer.send.time` - Time taken to send messages
   - `kafka.producer.connections.active` - Active connections

2. **Consumer Metrics**
   - `kafka.consumer.messages.processed` - Successfully processed messages
   - `kafka.consumer.messages.failed` - Processing failures
   - `kafka.consumer.processing.time` - Message processing time
   - `kafka.consumer.lag` - Consumer lag by partition

3. **Error Metrics**
   - `kafka.consumer.messages.retryable_errors` - Recoverable errors
   - `kafka.consumer.messages.non_recoverable_errors` - Non-recoverable errors
   - `kafka.consumer.messages.validation_errors` - Validation failures

### Health Checks

Access health information at `/actuator/health`:

```json
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "cluster": {
          "clusterId": "kafka-cluster-1",
          "nodeCount": 3
        },
        "producer": {
          "connections": 2
        },
        "consumer": {
          "availableTopics": 15
        }
      }
    }
  }
}
```

### Prometheus Integration

Metrics are automatically exposed at `/actuator/prometheus` for scraping by Prometheus.

Sample Grafana dashboard queries:
```promql
# Message throughput
rate(kafka_producer_messages_sent_total[5m])

# Consumer lag
kafka_consumer_lag_sum

# Error rate
rate(kafka_consumer_messages_failed_total[5m])
```

## 🔧 Error Handling

### Error Classification

1. **Recoverable Errors** (Will be retried)
   - Network timeouts
   - Temporary service unavailability
   - Resource exhaustion

2. **Non-Recoverable Errors** (Sent to DLT)
   - Business logic violations
   - Data validation failures
   - Permanent service failures

### Dead Letter Topic Strategy

```
Original Topic: orders-topic
├── Validation Errors → orders-topic.validation.dlt
├── Business Errors → orders-topic.business.dlt
├── Deserialization Errors → orders-topic.deserialization.dlt
└── General Errors → orders-topic.dlt
```

### Retry Configuration

```yaml
# Exponential backoff: 1s, 2s, 4s (max 3 retries)
error-handling:
  retry:
    initial-interval: 1000
    multiplier: 2.0
    max-interval: 10000
    max-attempts: 3
```

## 🔒 Security

### SSL/TLS Configuration

```yaml
kafka:
  security:
    enabled: true
    protocol: SSL
    ssl:
      trust-store-location: classpath:kafka.client.truststore.jks
      trust-store-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD}
      key-store-location: classpath:kafka.client.keystore.jks
      key-store-password: ${KAFKA_SSL_KEYSTORE_PASSWORD}
```

### SASL Authentication

```yaml
kafka:
  security:
    enabled: true
    protocol: SASL_SSL
    sasl:
      mechanism: PLAIN
      jaas-config: |
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";
```

## 🧪 Testing

### Running Tests

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# Performance tests
./gradlew performanceTest

# All tests with coverage
./gradlew check
```

### TestContainers Integration

The project uses TestContainers for integration testing:

```java
@SpringBootTest
@Testcontainers
class KafkaIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

## 🚀 Deployment

### Docker Deployment

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY build/libs/spring-kafka-pro-*.jar app.jar

# JVM optimizations
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

Build and run:
```bash
./gradlew jibDockerBuild
docker run -p 8080:8080 -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 spring-kafka-pro:latest
```

### Kubernetes Deployment

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
      - name: app
        image: spring-kafka-pro:latest
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka.kafka.svc.cluster.local:9092"
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
```

## ⚡ Performance Tuning

### Producer Optimization

1. **Batching**: Increase `batch.size` and `linger.ms` for higher throughput
2. **Compression**: Use `snappy` or `lz4` for better performance
3. **Buffer Memory**: Increase `buffer.memory` for high-volume scenarios
4. **Connection Pooling**: Reuse producer instances

### Consumer Optimization

1. **Fetch Size**: Tune `fetch.min.bytes` and `fetch.max.wait.ms`
2. **Poll Records**: Adjust `max.poll.records` based on processing time
3. **Concurrency**: Match consumer threads to partition count
4. **Session Timeout**: Optimize for your processing patterns

### JVM Tuning

```bash
# Recommended JVM flags for production
-XX:+UseG1GC
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0
-XX:+ExitOnOutOfMemoryError
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
```

## 🔍 Troubleshooting

### Common Issues

1. **Consumer Lag**
   ```bash
   # Check consumer lag
   curl http://localhost:8080/actuator/metrics/kafka.consumer.lag
   
   # Solutions:
   # - Increase consumer concurrency
   # - Optimize processing logic
   # - Scale consumer instances
   ```

2. **Producer Timeouts**
   ```bash
   # Check producer metrics
   curl http://localhost:8080/actuator/metrics/kafka.producer.send.time
   
   # Solutions:
   # - Increase request.timeout.ms
   # - Check network connectivity
   # - Verify broker health
   ```

3. **Memory Issues**
   ```bash
   # Monitor JVM memory
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   
   # Solutions:
   # - Increase heap size
   # - Tune batch sizes
   # - Check for memory leaks
   ```

### Debugging

Enable debug logging:
```yaml
logging:
  level:
    com.company.kafka: DEBUG
    org.springframework.kafka: DEBUG
    org.apache.kafka: DEBUG
```

### Health Check Endpoints

- `/actuator/health` - Overall application health
- `/actuator/health/kafka` - Kafka-specific health
- `/actuator/metrics` - All available metrics
- `/actuator/prometheus` - Prometheus formatted metrics

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow Spring Boot best practices
- Write comprehensive tests
- Update documentation
- Ensure security compliance
- Add monitoring and logging

## 📚 Additional Resources

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Documentation](https://spring.io/projects/spring-kafka)
- [Micrometer Documentation](https://micrometer.io/docs)
- [TestContainers Documentation](https://www.testcontainers.org/)

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🏆 Pro Tips Summary

### Configuration
✅ Use immutable configuration with `@ConstructorBinding`
✅ Separate configuration by environment (dev/test/prod)
✅ Validate configuration with Bean Validation
✅ Use Duration and DataSize for time/size properties

### Producer Best Practices
✅ Enable idempotence for exactly-once semantics
✅ Use `acks=all` for maximum durability
✅ Configure appropriate batching and compression
✅ Monitor producer metrics and lag

### Consumer Best Practices
✅ Use manual acknowledgment for precise control
✅ Implement proper error handling with DLT
✅ Configure consumer concurrency appropriately
✅ Monitor consumer lag and processing time

### Error Handling
✅ Distinguish recoverable vs non-recoverable errors
✅ Use exponential backoff for retries
✅ Route poison messages to dead letter topics
✅ Include correlation IDs for tracing

### Monitoring
✅ Expose comprehensive metrics
✅ Implement health checks
✅ Use distributed tracing
✅ Set up alerting on key metrics

### Security
✅ Enable SSL/TLS encryption
✅ Use SASL authentication
✅ Validate and sanitize messages
✅ Implement proper access controls

### Testing
✅ Use TestContainers for integration tests
✅ Test error scenarios thoroughly
✅ Include performance tests
✅ Validate configuration in tests

---

**Built with ❤️ by the Development Team**

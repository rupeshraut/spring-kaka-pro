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

#### 🎯 **Smart Partitioning Strategies**
- **Producer Partitioning**: 8 advanced strategies including customer-aware, geographic, priority-based, and load-aware partitioning
- **Consumer Assignment**: 6 intelligent algorithms for optimal partition-to-consumer assignment
- **Real-time Monitoring**: Live partition distribution tracking and performance analytics
- **Auto-optimization**: Automatic rebalancing recommendations based on workload patterns
- **Management APIs**: RESTful endpoints for runtime configuration and monitoring

#### 🔄 **Enhanced Error Handling**
- **Poison Pill Detection**: Automatic identification and quarantine of consistently failing messages
- **Circuit Breaker Pattern**: Prevents cascade failures when external services are down
- **Exponential Backoff with Jitter**: Intelligent retry logic to prevent thundering herd
- **Smart DLT Routing**: Contextual dead letter topic routing based on error types
- **Error Correlation**: Track related failures across the system

#### ⚡ **High-Performance Processing**
- **Transactional Support**: Exactly-once semantics with Kafka transactions
- **Batch Processing**: Up to 10x performance improvement for high-throughput scenarios
- **Message Filtering**: Intelligent preprocessing to reduce unnecessary processing
- **Producer Interceptors**: Automatic message enrichment with correlation IDs and metadata
- **Container Management**: Runtime control over Kafka listener containers

#### 📊 **Production-Ready Monitoring**
- **Consumer Lag Monitoring**: Real-time tracking with configurable alerting thresholds
- **DLT Analytics**: Comprehensive dead letter topic monitoring and management
- **Circuit Breaker Metrics**: Track external service health and failure patterns
- **Enhanced Health Checks**: Deep health validation for Kafka connectivity
- **Prometheus Integration**: Rich metrics for operational dashboards

#### 🔧 **Operational Excellence**
- **DLT Message Reprocessing**: Selective reprocessing of failed messages with filtering
- **Seek Operations**: Advanced error recovery with offset management
- **Container Lifecycle**: Programmatic start/stop/pause/resume of consumers
- **Configuration Hot-Reload**: Runtime configuration updates without restart
- **Graceful Shutdown**: Proper resource cleanup and message completion

#### 🛡️ **Enterprise Security**
- **SSL/TLS Encryption**: End-to-end encryption for data in transit
- **SASL Authentication**: Multi-mechanism authentication support
- **Message Validation**: Schema validation and business rule enforcement
- **Access Control**: Role-based access to management endpoints
- **Audit Logging**: Comprehensive audit trail for all operations

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Partitioning Strategies](#partitioning-strategies)
- [Configuration](#configuration)
- [Monitoring & Observability](#monitoring--observability)
- [Error Handling](#error-handling)
- [Security](#security)
- [Testing](#testing)
- [Deployment](#deployment)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## 📚 Comprehensive Documentation

For detailed implementation guidance and operational procedures, see our comprehensive documentation:

- **[Developer Guide](DEVELOPER_GUIDE.md)** - Complete reference for building Spring Kafka applications
- **[API Reference](API_REFERENCE.md)** - REST API documentation for all management endpoints
- **[Deployment Guide](DEPLOYMENT_GUIDE.md)** - Production deployment and operational procedures

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

## 🎯 Partitioning Strategies

This application features advanced partitioning strategies for optimal message distribution and consumer assignment.

### Producer Partitioning Strategies

The `CustomPartitioner` provides 8 sophisticated partitioning algorithms:

#### 1. **Round Robin** (`ROUND_ROBIN`)
Distributes messages evenly across all partitions for balanced load distribution.

```yaml
kafka:
  partitioning:
    producer:
      strategy: ROUND_ROBIN
```

**Use Cases:**
- Uniform message processing requirements
- When message ordering is not critical
- Load balancing across all partitions

#### 2. **Hash-Based** (`HASH`)
Uses message key hash for consistent partitioning (default Kafka behavior).

```yaml
kafka:
  partitioning:
    producer:
      strategy: HASH
```

**Use Cases:**
- When message ordering by key is required
- Consistent routing of related messages
- Standard Kafka partitioning behavior

#### 3. **Sticky Partitioning** (`STICKY`)
Assigns messages to specific partitions for better batching efficiency.

```yaml
kafka:
  partitioning:
    producer:
      strategy: STICKY
      sticky:
        partition-count: 2
        enable-thread-affinity: true
        affinity-timeout: PT5M
```

**Use Cases:**
- High-throughput scenarios requiring optimal batching
- Reducing network overhead
- When processing order within thread matters

#### 4. **Customer-Aware** (`CUSTOMER_AWARE`)
Routes messages by customer ID for data locality and processing efficiency.

```yaml
kafka:
  partitioning:
    producer:
      strategy: CUSTOMER_AWARE
      customer:
        hash-seed: 54321
        customer-id-field: customerId
        enable-caching: true
        cache-size: 10000
```

**Use Cases:**
- Customer-specific processing requirements
- Data locality for customer analytics
- Tenant isolation in multi-tenant systems

#### 5. **Geographic Partitioning** (`GEOGRAPHIC`)
Distributes messages based on geographic regions.

```yaml
kafka:
  partitioning:
    producer:
      strategy: GEOGRAPHIC
      geographic:
        regions: ["us-east", "us-west", "eu", "asia"]
        region-field: region
        enable-cross-border-fallback: true
        default-region: us-east
```

**Use Cases:**
- Multi-region deployments
- Compliance with data residency requirements
- Latency optimization for regional processing

#### 6. **Priority-Based** (`PRIORITY`)
Routes high-priority messages to dedicated partitions for faster processing.

```yaml
kafka:
  partitioning:
    producer:
      strategy: PRIORITY
      priority:
        partition-ratio: 0.3
        priority-field: priority
        high-priority-values: ["HIGH", "CRITICAL", "URGENT"]
        enable-dynamic-adjustment: false
```

**Use Cases:**
- SLA-differentiated message processing
- Critical message prioritization
- Quality of Service (QoS) requirements

#### 7. **Time-Based** (`TIME_BASED`)
Partitions messages based on time windows for temporal processing.

```yaml
kafka:
  partitioning:
    producer:
      strategy: TIME_BASED
      time-based:
        time-window: PT1M
        use-utc-time: true
        time-field: timestamp
```

**Use Cases:**
- Time-series data processing
- Batch processing windows
- Temporal data organization

#### 8. **Load-Aware** (`LOAD_AWARE`)
Dynamically routes messages to avoid hot partitions.

```yaml
kafka:
  partitioning:
    producer:
      strategy: LOAD_AWARE
      load-aware:
        load-threshold: 1000
        metrics-window: PT1M
        enable-hot-partition-avoidance: true
        load-balancing-factor: 1.5
```

**Use Cases:**
- Preventing hot partition bottlenecks
- Dynamic load balancing
- Adaptive performance optimization

### Consumer Assignment Strategies

The `CustomConsumerAssignmentStrategy` provides 6 intelligent assignment algorithms:

#### 1. **Affinity-Based** (`AFFINITY_BASED`)
Maintains consumer-partition relationships for sticky assignments.

```yaml
kafka:
  partitioning:
    consumer:
      assignment-strategy: AFFINITY_BASED
      affinity:
        enable-sticky-assignment: true
        stickiness-factor: 0.8
        affinity-timeout: PT10M
```

#### 2. **Load-Balanced** (`LOAD_BALANCED`)
Distributes partitions based on consumer processing capacity.

```yaml
kafka:
  partitioning:
    consumer:
      assignment-strategy: LOAD_BALANCED
      metadata:
        capacity: 2000
        priority: MEDIUM
```

#### 3. **Rack-Aware** (`RACK_AWARE`)
Assigns partitions considering consumer rack locality.

```yaml
kafka:
  partitioning:
    consumer:
      assignment-strategy: RACK_AWARE
      metadata:
        rack: us-east-1a
        region: us-east
```

#### 4. **Priority-Based** (`PRIORITY_BASED`)
Prioritizes high-priority consumers for partition assignment.

```yaml
kafka:
  partitioning:
    consumer:
      assignment-strategy: PRIORITY_BASED
      metadata:
        priority: HIGH
        capacity: 3000
```

#### 5. **Geographic** (`GEOGRAPHIC`)
Groups consumers by geographic region for optimal assignment.

```yaml
kafka:
  partitioning:
    consumer:
      assignment-strategy: GEOGRAPHIC
      metadata:
        region: eu-west-1
        rack: eu-west-1a
```

#### 6. **Workload-Aware** (`WORKLOAD_AWARE`)
Assigns partitions based on consumer capabilities and preferences.

```yaml
kafka:
  partitioning:
    consumer:
      assignment-strategy: WORKLOAD_AWARE
      metadata:
        capabilities: ["BATCH_PROCESSING", "JSON", "AVRO"]
        preferences: ["HIGH_THROUGHPUT", "LOW_LATENCY"]
        capacity: 2500
```

### Monitoring and Management

#### Real-time Monitoring

Monitor partition distribution and performance:

```bash
# Get current partitioning configuration
curl http://localhost:8080/api/kafka/partitioning/config

# Get partition monitoring statistics
curl http://localhost:8080/api/kafka/partitioning/monitor

# Get detailed partition distribution metrics
curl http://localhost:8080/api/kafka/partitioning/distribution
```

#### Performance Optimization

Get recommendations for improving partitioning performance:

```bash
# Get optimization recommendations
curl http://localhost:8080/api/kafka/partitioning/recommendations

# Run optimization analysis (dry run)
curl -X POST "http://localhost:8080/api/kafka/partitioning/optimize?dryRun=true"

# Apply optimizations
curl -X POST http://localhost:8080/api/kafka/partitioning/optimize
```

#### Health Monitoring

Check partitioning system health:

```bash
# Get partitioning health status
curl http://localhost:8080/api/kafka/partitioning/health

# Get detailed metrics
curl http://localhost:8080/api/kafka/partitioning/metrics

# Reset monitoring metrics
curl -X POST http://localhost:8080/api/kafka/partitioning/reset-metrics
```

### Best Practices

#### Producer Partitioning
✅ **Choose the right strategy** based on your use case:
- Use `CUSTOMER_AWARE` for tenant isolation
- Use `PRIORITY` for SLA differentiation  
- Use `GEOGRAPHIC` for compliance requirements
- Use `LOAD_AWARE` for dynamic optimization

✅ **Monitor partition distribution** regularly:
- Track partition skew metrics
- Monitor hot partition detection
- Set up alerts for imbalanced distribution

✅ **Configure appropriate parameters**:
- Set realistic cache sizes for customer-aware partitioning
- Configure proper time windows for time-based partitioning
- Adjust load thresholds based on your traffic patterns

#### Consumer Assignment
✅ **Match strategy to deployment**:
- Use `RACK_AWARE` in multi-AZ deployments
- Use `LOAD_BALANCED` with heterogeneous consumers
- Use `WORKLOAD_AWARE` for specialized processing capabilities

✅ **Configure consumer metadata properly**:
- Set accurate capacity values
- Specify correct rack and region information
- Define realistic capabilities and preferences

✅ **Monitor assignment efficiency**:
- Track consumer utilization metrics
- Monitor rebalancing frequency
- Watch for assignment skew

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

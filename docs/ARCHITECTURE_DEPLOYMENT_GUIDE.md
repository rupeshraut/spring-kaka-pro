# Spring Kafka Production Architecture & Deployment Guide

This guide provides comprehensive documentation for deploying and operating a production-ready Spring Kafka application with enterprise-grade patterns.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Component Design Patterns](#component-design-patterns)
3. [Configuration Management](#configuration-management)
4. [Deployment Strategies](#deployment-strategies)
5. [Monitoring & Observability](#monitoring--observability)
6. [Security Implementation](#security-implementation)
7. [Performance Tuning](#performance-tuning)
8. [Operational Procedures](#operational-procedures)
9. [Troubleshooting Guide](#troubleshooting-guide)
10. [Best Practices Checklist](#best-practices-checklist)

---

## Architecture Overview

### System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Production Environment                        │
├─────────────────────────────────────────────────────────────────┤
│  Load Balancer (HAProxy/NGINX)                                │
│  ├── Health Check: /actuator/health                           │
│  └── SSL Termination                                          │
├─────────────────────────────────────────────────────────────────┤
│  Spring Kafka Application (Multiple Instances)                 │
│  ├── REST API Layer                                           │
│  │   ├── KafkaController (Message endpoints)                 │
│  │   ├── HealthController (Monitoring endpoints)             │
│  │   └── ValidationLayer (Request validation)               │
│  ├── Service Layer                                            │
│  │   ├── KafkaProducerService (Async + Metrics)            │
│  │   ├── KafkaConsumerService (Manual ACK + Retry)         │
│  │   └── MessageProcessingService (Business logic)         │
│  ├── Configuration Layer                                      │
│  │   ├── KafkaProperties (Immutable configuration)         │
│  │   ├── KafkaConfiguration (Bean definitions)             │
│  │   └── SecurityConfiguration (SSL/SASL setup)            │
│  └── Monitoring Layer                                         │
│      ├── KafkaHealthIndicator (Health monitoring)           │
│      ├── MetricsCollector (Micrometer integration)         │
│      └── DistributedTracing (Correlation IDs)              │
├─────────────────────────────────────────────────────────────────┤
│  Apache Kafka Cluster                                          │
│  ├── Broker 1 (Leader for partitions 0,3,6)                  │
│  ├── Broker 2 (Leader for partitions 1,4,7)                  │
│  ├── Broker 3 (Leader for partitions 2,5,8)                  │
│  └── ZooKeeper Ensemble (3 nodes)                             │
├─────────────────────────────────────────────────────────────────┤
│  Monitoring & Alerting                                         │
│  ├── Prometheus (Metrics collection)                          │
│  ├── Grafana (Dashboards & visualization)                     │
│  ├── AlertManager (Alert routing)                             │
│  └── ELK Stack (Centralized logging)                          │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

#### **KafkaProducerService**
- **Purpose**: High-performance message publishing with reliability guarantees
- **Key Features**:
  - Idempotent publishing to prevent duplicates
  - Async operations with CompletableFuture
  - Comprehensive metrics collection
  - Correlation ID generation for tracing
  - Batch operations for efficiency
  - Circuit breaker for external dependencies

#### **KafkaConsumerService**
- **Purpose**: Reliable message consumption with error handling
- **Key Features**:
  - Manual acknowledgment for precise control
  - Retry logic with exponential backoff
  - Dead letter topic routing
  - Message validation and processing
  - Consumer lag monitoring
  - Graceful shutdown handling

#### **KafkaHealthIndicator**
- **Purpose**: Real-time health monitoring and alerting
- **Key Features**:
  - Connectivity monitoring with timeout protection
  - Error rate analysis with configurable thresholds
  - Consumer lag detection and alerting
  - Service status classification (HEALTHY/WARNING/CRITICAL)
  - Detailed health metrics for dashboards

---

## Component Design Patterns

### 1. Configuration Pattern

**Immutable Configuration with Validation**

```java
@ConfigurationProperties(prefix = "app.kafka")
public record KafkaProperties(
    @NotBlank @Size(min = 1, max = 500) 
    String bootstrapServers,
    
    @Valid @NotNull 
    ProducerProperties producer,
    
    @Valid @NotNull 
    ConsumerProperties consumer,
    
    @Valid @NotNull 
    TopicsProperties topics,
    
    @Valid 
    SecurityProperties security
) {
    // Constructor with defaults
    public KafkaProperties {
        // Validate and set defaults
        bootstrapServers = validateBootstrapServers(bootstrapServers);
        producer = producer != null ? producer : new ProducerProperties();
        consumer = consumer != null ? consumer : new ConsumerProperties();
        topics = topics != null ? topics : new TopicsProperties();
        security = security != null ? security : new SecurityProperties();
    }
    
    private static String validateBootstrapServers(String servers) {
        if (servers == null || servers.trim().isEmpty()) {
            throw new IllegalArgumentException("Bootstrap servers cannot be empty");
        }
        // Additional validation logic
        return servers.trim();
    }
    
    public record ProducerProperties(
        @Min(0) int retries,
        @Min(1) int batchSize,
        @Min(0) int lingerMs,
        @Pattern(regexp = "all|0|1") String acks,
        boolean enableIdempotence,
        @Min(1) int maxInFlightRequestsPerConnection,
        CompressionType compressionType,
        @Positive long bufferMemory,
        @Positive int requestTimeoutMs,
        @Positive int deliveryTimeoutMs
    ) {
        public ProducerProperties {
            // Production-ready defaults
            retries = retries > 0 ? retries : Integer.MAX_VALUE;
            batchSize = batchSize > 0 ? batchSize : 16384;
            lingerMs = lingerMs >= 0 ? lingerMs : 5;
            acks = acks != null ? acks : "all";
            enableIdempotence = true; // Always true in production
            maxInFlightRequestsPerConnection = enableIdempotence ? 1 : 5;
            compressionType = compressionType != null ? compressionType : CompressionType.SNAPPY;
            bufferMemory = bufferMemory > 0 ? bufferMemory : 33554432L; // 32MB
            requestTimeoutMs = requestTimeoutMs > 0 ? requestTimeoutMs : 30000;
            deliveryTimeoutMs = deliveryTimeoutMs > 0 ? deliveryTimeoutMs : 120000;
        }
    }
}
```

### 2. Error Handling Pattern

**Comprehensive Exception Hierarchy**

```java
/**
 * Production error handling strategy:
 * 
 * 1. ValidationException (Non-retryable)
 *    - Acknowledge immediately to prevent infinite retry
 *    - Log with full context for debugging
 *    - Route to validation-specific DLT
 * 
 * 2. RecoverableException (Retryable)
 *    - Don't acknowledge - let retry mechanism handle
 *    - Implement exponential backoff
 *    - Set maximum retry limits
 * 
 * 3. NonRecoverableException (Non-retryable)
 *    - Acknowledge to prevent retry
 *    - Route to dead letter topic for manual investigation
 *    - Alert on high volumes
 */

@KafkaListener(topics = "#{kafkaProperties.topics.orders.name}")
public void processOrderEvent(
        @Payload String message,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment acknowledgment) {
    
    String correlationId = generateCorrelationId();
    Timer.Sample sample = Timer.start(meterRegistry);
    
    try {
        // Step 1: Validate message format and content
        validateMessage(message, correlationId);
        
        // Step 2: Process business logic
        OrderEvent orderEvent = parseOrderEvent(message);
        processOrderBusinessLogic(orderEvent, correlationId);
        
        // Step 3: Acknowledge successful processing
        acknowledgment.acknowledge();
        updateSuccessMetrics(topic, correlationId);
        
    } catch (ValidationException e) {
        // Non-retryable: acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
        handleValidationError(e, message, correlationId, topic);
        
    } catch (RecoverableException e) {
        // Retryable: don't acknowledge, let error handler retry
        handleRecoverableError(e, correlationId, topic);
        throw e; // Rethrow to trigger retry mechanism
        
    } catch (NonRecoverableException e) {
        // Non-retryable: acknowledge and route to DLT
        acknowledgment.acknowledge();
        handleNonRecoverableError(e, message, correlationId, topic);
        
    } catch (Exception e) {
        // Unknown error: treat as non-recoverable for safety
        acknowledgment.acknowledge();
        handleUnknownError(e, message, correlationId, topic);
        
    } finally {
        sample.stop(processingTimer);
        logProcessingComplete(correlationId, topic, partition, offset);
    }
}
```

### 3. Metrics and Monitoring Pattern

**Production Metrics Collection**

```java
@Component
public class KafkaMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Business metrics
    private final Counter orderEventsProcessed;
    private final Counter paymentEventsProcessed;
    private final Counter notificationEventsSent;
    
    // Performance metrics
    private final Timer messageProcessingTime;
    private final Timer messageSendTime;
    private final Gauge consumerLag;
    
    // Error metrics
    private final Counter validationErrors;
    private final Counter recoverableErrors;
    private final Counter nonRecoverableErrors;
    
    public KafkaMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        // Business metrics with tags
        orderEventsProcessed = Counter.builder("kafka.business.orders.processed")
            .description("Number of order events successfully processed")
            .tag("service", "kafka-consumer")
            .register(meterRegistry);
            
        // Performance metrics with percentiles
        messageProcessingTime = Timer.builder("kafka.consumer.processing.time")
            .description("Time taken to process messages")
            .publishPercentiles(0.5, 0.75, 0.95, 0.99)
            .register(meterRegistry);
            
        // Error metrics with error classification
        validationErrors = Counter.builder("kafka.consumer.errors.validation")
            .description("Number of validation errors")
            .register(meterRegistry);
    }
    
    // Metric recording methods
    public void recordOrderProcessed(String correlationId, long processingTimeMs) {
        orderEventsProcessed.increment(
            Tags.of(
                "correlationId", correlationId,
                "status", "success"
            )
        );
        messageProcessingTime.record(processingTimeMs, TimeUnit.MILLISECONDS);
    }
    
    public void recordValidationError(String errorType, String topic) {
        validationErrors.increment(
            Tags.of(
                "errorType", errorType,
                "topic", topic
            )
        );
    }
}
```

---

## Configuration Management

### Environment-Specific Configuration

#### **Development Environment**
```yaml
# application-dev.yml
app:
  kafka:
    bootstrap:
      servers: localhost:9092
    producer:
      retries: 3
      enable-idempotence: true
      acks: all
    consumer:
      enable-auto-commit: false
      auto-offset-reset: earliest
    topics:
      orders:
        name: dev-orders-topic
        partitions: 1
        replication-factor: 1
```

#### **Staging Environment**
```yaml
# application-staging.yml
app:
  kafka:
    bootstrap:
      servers: kafka-staging-1:9092,kafka-staging-2:9092,kafka-staging-3:9092
    producer:
      retries: 2147483647  # Infinite retries
      enable-idempotence: true
      acks: all
      batch-size: 16384
      linger-ms: 5
      compression-type: snappy
    consumer:
      enable-auto-commit: false
      auto-offset-reset: earliest
      max-poll-records: 500
      session-timeout-ms: 30000
    topics:
      orders:
        name: staging-orders-topic
        partitions: 3
        replication-factor: 2
```

#### **Production Environment**
```yaml
# application-prod.yml
app:
  kafka:
    bootstrap:
      servers: ${KAFKA_BOOTSTRAP_SERVERS}
    security:
      protocol: ${KAFKA_SECURITY_PROTOCOL:SSL}
      ssl:
        truststore-location: ${KAFKA_SSL_TRUSTSTORE_LOCATION}
        truststore-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD}
        keystore-location: ${KAFKA_SSL_KEYSTORE_LOCATION}
        keystore-password: ${KAFKA_SSL_KEYSTORE_PASSWORD}
        key-password: ${KAFKA_SSL_KEY_PASSWORD}
    producer:
      retries: 2147483647
      enable-idempotence: true
      acks: all
      max-in-flight-requests-per-connection: 1
      batch-size: 32768  # 32KB for high throughput
      linger-ms: 10      # Higher latency for better throughput
      compression-type: lz4
      buffer-memory: 67108864  # 64MB
      request-timeout-ms: 30000
      delivery-timeout-ms: 120000
    consumer:
      enable-auto-commit: false
      auto-offset-reset: earliest
      fetch-min-bytes: 50000     # 50KB minimum fetch
      fetch-max-wait-ms: 500
      max-poll-records: 1000     # Higher batch size
      session-timeout-ms: 30000
      heartbeat-interval-ms: 3000
      max-poll-interval-ms: 300000  # 5 minutes processing time
    topics:
      orders:
        name: ${KAFKA_ORDERS_TOPIC:orders-topic}
        partitions: 12    # High partition count for parallelism
        replication-factor: 3
      payments:
        name: ${KAFKA_PAYMENTS_TOPIC:payments-topic}
        partitions: 12
        replication-factor: 3
```

### Configuration Validation

```java
@Component
@Validated
public class KafkaConfigurationValidator {
    
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration(KafkaProperties kafkaProperties) {
        log.info("Validating Kafka configuration...");
        
        // Validate bootstrap servers
        validateBootstrapServers(kafkaProperties.bootstrapServers());
        
        // Validate producer configuration
        validateProducerConfiguration(kafkaProperties.producer());
        
        // Validate consumer configuration  
        validateConsumerConfiguration(kafkaProperties.consumer());
        
        // Validate topic configuration
        validateTopicConfiguration(kafkaProperties.topics());
        
        log.info("Kafka configuration validation completed successfully");
    }
    
    private void validateBootstrapServers(String bootstrapServers) {
        String[] servers = bootstrapServers.split(",");
        for (String server : servers) {
            if (!isValidServerAddress(server.trim())) {
                throw new ConfigurationException("Invalid bootstrap server: " + server);
            }
        }
    }
    
    private void validateProducerConfiguration(ProducerProperties producer) {
        // Validate production-ready settings
        if (!producer.enableIdempotence()) {
            log.warn("WARNING: Idempotence is disabled - this may cause duplicate messages");
        }
        
        if (!"all".equals(producer.acks())) {
            log.warn("WARNING: acks is not set to 'all' - this may cause data loss");
        }
        
        if (producer.retries() < 1) {
            throw new ConfigurationException("Retries must be >= 1 for production");
        }
    }
}
```

---

## Deployment Strategies

### 1. Docker Containerization

#### **Multi-Stage Dockerfile**
```dockerfile
# Build stage
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Security: Create non-root user
RUN addgroup -S kafka && adduser -S kafka -G kafka

# Install monitoring tools
RUN apk add --no-cache curl netcat-openbsd

# Create directories
RUN mkdir -p /app/logs /app/config /app/ssl && \
    chown -R kafka:kafka /app

# Copy application
COPY --from=builder /app/build/libs/spring-kafka-pro-*.jar /app/app.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Resource limits (overrideable)
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:+UseContainerSupport"

USER kafka
WORKDIR /app
EXPOSE 8080

ENTRYPOINT ["./entrypoint.sh"]
```

#### **Docker Entrypoint Script**
```bash
#!/bin/sh
# entrypoint.sh - Production-ready startup script

set -e

# Configuration validation
if [ -z "$KAFKA_BOOTSTRAP_SERVERS" ]; then
    echo "ERROR: KAFKA_BOOTSTRAP_SERVERS environment variable is required"
    exit 1
fi

# Health check dependencies
echo "Checking Kafka connectivity..."
for server in $(echo $KAFKA_BOOTSTRAP_SERVERS | tr ',' ' '); do
    host=$(echo $server | cut -d':' -f1)
    port=$(echo $server | cut -d':' -f2)
    if ! nc -z "$host" "$port"; then
        echo "ERROR: Cannot connect to Kafka server $server"
        exit 1
    fi
done

echo "Kafka connectivity check passed"

# JVM configuration
export JAVA_OPTS="${JAVA_OPTS:-} -Djava.security.egd=file:/dev/./urandom"
export JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"
export JAVA_OPTS="$JAVA_OPTS -Dlogging.file.path=/app/logs"

# Security configuration
if [ -n "$KAFKA_SSL_TRUSTSTORE_LOCATION" ]; then
    export JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=$KAFKA_SSL_TRUSTSTORE_LOCATION"
    export JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStorePassword=$KAFKA_SSL_TRUSTSTORE_PASSWORD"
fi

echo "Starting Spring Kafka Pro application..."
echo "Java options: $JAVA_OPTS"

exec java $JAVA_OPTS -jar app.jar
```

### 2. Kubernetes Deployment

#### **Production Deployment Manifest**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-kafka-pro
  namespace: kafka-apps
  labels:
    app: spring-kafka-pro
    version: v1.0.0
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0  # Zero downtime deployment
  selector:
    matchLabels:
      app: spring-kafka-pro
  template:
    metadata:
      labels:
        app: spring-kafka-pro
        version: v1.0.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "8080"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      containers:
      - name: spring-kafka-pro
        image: spring-kafka-pro:1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: kafka-config
              key: bootstrap-servers
        - name: KAFKA_SSL_TRUSTSTORE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: kafka-ssl-secrets
              key: truststore-password
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: 120
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: http
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        volumeMounts:
        - name: ssl-certs
          mountPath: /app/ssl
          readOnly: true
        - name: logs
          mountPath: /app/logs
      volumes:
      - name: ssl-certs
        secret:
          secretName: kafka-ssl-certs
      - name: logs
        emptyDir: {}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchLabels:
                  app: spring-kafka-pro
              topologyKey: kubernetes.io/hostname
```

#### **Service and Ingress Configuration**
```yaml
apiVersion: v1
kind: Service
metadata:
  name: spring-kafka-pro-service
  namespace: kafka-apps
spec:
  selector:
    app: spring-kafka-pro
  ports:
  - port: 8080
    targetPort: http
    name: http
  type: ClusterIP

---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring-kafka-pro-ingress
  namespace: kafka-apps
  annotations:
    kubernetes.io/ingress.class: nginx
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/health-check-path: "/actuator/health"
spec:
  tls:
  - hosts:
    - kafka-api.company.com
    secretName: kafka-api-tls
  rules:
  - host: kafka-api.company.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: spring-kafka-pro-service
            port:
              number: 8080
```

### 3. Blue-Green Deployment Strategy

```yaml
# Blue-Green deployment script
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: spring-kafka-pro-rollout
spec:
  replicas: 3
  strategy:
    blueGreen:
      activeService: spring-kafka-pro-active
      previewService: spring-kafka-pro-preview
      autoPromotionEnabled: false
      scaleDownDelaySeconds: 30
      prePromotionAnalysis:
        templates:
        - templateName: health-check
        args:
        - name: service-name
          value: spring-kafka-pro-preview
      postPromotionAnalysis:
        templates:
        - templateName: error-rate-check
        args:
        - name: service-name
          value: spring-kafka-pro-active
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
        image: spring-kafka-pro:1.0.0
        # ... container spec ...
```

---

## Monitoring & Observability

### 1. Prometheus Metrics Configuration

```yaml
# prometheus-config.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "kafka_alerts.yml"

scrape_configs:
  - job_name: 'spring-kafka-pro'
    static_configs:
      - targets: ['spring-kafka-pro:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    scrape_timeout: 10s

alerting:
  alertmanagers:
  - static_configs:
    - targets:
      - alertmanager:9093
```

### 2. Grafana Dashboard

```json
{
  "dashboard": {
    "id": null,
    "title": "Spring Kafka Pro - Production Dashboard",
    "tags": ["kafka", "spring-boot", "production"],
    "style": "dark",
    "timezone": "UTC",
    "panels": [
      {
        "id": 1,
        "title": "Message Throughput",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0},
        "targets": [
          {
            "expr": "rate(kafka_producer_messages_sent_total[5m])",
            "legendFormat": "Messages Sent/sec",
            "refId": "A"
          },
          {
            "expr": "rate(kafka_consumer_messages_processed_total[5m])",
            "legendFormat": "Messages Processed/sec",
            "refId": "B"
          }
        ],
        "yAxes": [
          {
            "label": "Messages/sec",
            "min": 0
          }
        ],
        "alert": {
          "conditions": [
            {
              "query": {"params": ["A", "5m", "now"]},
              "reducer": {"params": [], "type": "last"},
              "evaluator": {"params": [100], "type": "lt"}
            }
          ],
          "executionErrorState": "alerting",
          "for": "5m",
          "frequency": "10s",
          "handler": 1,
          "name": "Low Message Throughput",
          "noDataState": "no_data",
          "notifications": []
        }
      },
      {
        "id": 2,
        "title": "Error Rates",
        "type": "graph",
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 0},
        "targets": [
          {
            "expr": "rate(kafka_producer_messages_failed_total[5m]) / rate(kafka_producer_messages_sent_total[5m]) * 100",
            "legendFormat": "Producer Error Rate %",
            "refId": "A"
          },
          {
            "expr": "rate(kafka_consumer_messages_failed_total[5m]) / rate(kafka_consumer_messages_processed_total[5m]) * 100",
            "legendFormat": "Consumer Error Rate %",
            "refId": "B"
          }
        ],
        "yAxes": [
          {
            "label": "Error Rate %",
            "min": 0,
            "max": 100
          }
        ],
        "thresholds": [
          {
            "value": 5,
            "colorMode": "critical",
            "op": "gt"
          }
        ]
      },
      {
        "id": 3,
        "title": "Consumer Lag",
        "type": "graph",
        "gridPos": {"h": 8, "w": 24, "x": 0, "y": 8},
        "targets": [
          {
            "expr": "kafka_consumer_lag",
            "legendFormat": "Consumer Lag - {{topic}}",
            "refId": "A"
          }
        ],
        "yAxes": [
          {
            "label": "Messages",
            "min": 0
          }
        ],
        "thresholds": [
          {
            "value": 10000,
            "colorMode": "critical",
            "op": "gt"
          }
        ]
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

### 3. Alert Rules

```yaml
# kafka_alerts.yml
groups:
- name: kafka_alerts
  rules:
  - alert: KafkaProducerHighErrorRate
    expr: rate(kafka_producer_messages_failed_total[5m]) / rate(kafka_producer_messages_sent_total[5m]) > 0.05
    for: 2m
    labels:
      severity: warning
      service: spring-kafka-pro
    annotations:
      summary: "High producer error rate detected"
      description: "Producer error rate is {{ $value | humanizePercentage }} for the last 5 minutes"
      
  - alert: KafkaConsumerHighLag
    expr: kafka_consumer_lag > 10000
    for: 1m
    labels:
      severity: critical
      service: spring-kafka-pro
    annotations:
      summary: "High consumer lag detected"
      description: "Consumer lag is {{ $value }} messages for topic {{ $labels.topic }}"
      
  - alert: KafkaConnectivityLost
    expr: up{job="spring-kafka-pro"} == 0
    for: 30s
    labels:
      severity: critical
      service: spring-kafka-pro
    annotations:
      summary: "Kafka application is down"
      description: "Spring Kafka Pro application has been down for more than 30 seconds"
```

---

## Security Implementation

### 1. SSL/TLS Configuration

```yaml
# Kafka broker SSL configuration
listeners=SSL://kafka-broker:9093
security.inter.broker.protocol=SSL
ssl.keystore.location=/etc/kafka/ssl/kafka.server.keystore.jks
ssl.keystore.password=${KAFKA_KEYSTORE_PASSWORD}
ssl.key.password=${KAFKA_KEY_PASSWORD}
ssl.truststore.location=/etc/kafka/ssl/kafka.server.truststore.jks
ssl.truststore.password=${KAFKA_TRUSTSTORE_PASSWORD}
ssl.client.auth=required
ssl.endpoint.identification.algorithm=HTTPS
```

```java
// Application SSL configuration
@Bean
public ProducerFactory<String, Object> secureProducerFactory(KafkaProperties properties) {
    Map<String, Object> props = new HashMap<>();
    
    // Basic configuration
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    
    // SSL Configuration
    SecurityProperties security = properties.security();
    if (security != null && "SSL".equals(security.protocol())) {
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
        
        // SSL properties
        props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, security.ssl().truststoreLocation());
        props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, security.ssl().truststorePassword());
        props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, security.ssl().keystoreLocation());
        props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, security.ssl().keystorePassword());
        props.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, security.ssl().keyPassword());
        
        // Security enhancements
        props.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "https");
        props.put(SslConfigs.SSL_PROTOCOL_CONFIG, "TLSv1.2");
        props.put(SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG, "TLSv1.2,TLSv1.3");
        props.put(SslConfigs.SSL_CIPHER_SUITES_CONFIG, 
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    }
    
    return new DefaultKafkaProducerFactory<>(props);
}
```

### 2. SASL Authentication

```java
@Bean
public ConsumerFactory<String, Object> saslConsumerFactory(KafkaProperties properties) {
    Map<String, Object> props = new HashMap<>();
    
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
    
    // SASL Configuration
    SecurityProperties security = properties.security();
    if (security != null && security.sasl() != null) {
        props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        props.put(SaslConfigs.SASL_MECHANISM, security.sasl().mechanism());
        
        // SASL JAAS Configuration
        String jaasConfig = String.format(
            "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"%s\" password=\"%s\";",
            security.sasl().username(),
            security.sasl().password()
        );
        props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
    }
    
    return new DefaultKafkaConsumerFactory<>(props);
}
```

### 3. Application Security

```java
@EnableWebSecurity
@Configuration
public class SecurityConfiguration {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/api/kafka/**").hasRole("KAFKA_USER")
                .requestMatchers("/api/admin/**").hasRole("KAFKA_ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
            
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        // JWT decoder configuration
        return NimbusJwtDecoder.withJwkSetUri("https://auth.company.com/.well-known/jwks.json")
            .build();
    }
}
```

---

## Performance Tuning

### 1. JVM Tuning

```bash
# Production JVM settings
JAVA_OPTS="
  -Xms2g -Xmx4g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+UseContainerSupport
  -XX:InitiatingHeapOccupancyPercent=45
  -XX:G1HeapRegionSize=16m
  -XX:+UnlockExperimentalVMOptions
  -XX:+UseCGroupMemoryLimitForHeap
  -XX:+PrintGCDetails
  -XX:+PrintGCTimeStamps
  -Xloggc:/app/logs/gc.log
  -XX:+UseGCLogFileRotation
  -XX:NumberOfGCLogFiles=10
  -XX:GCLogFileSize=10M
  -Djava.security.egd=file:/dev/./urandom
  -Dspring.profiles.active=prod
"
```

### 2. Kafka Performance Tuning

#### **Producer Optimization**
```yaml
app:
  kafka:
    producer:
      # High throughput settings
      batch-size: 65536        # 64KB batches
      linger-ms: 20           # Wait up to 20ms for batching
      compression-type: lz4    # Fast compression
      buffer-memory: 134217728 # 128MB buffer
      
      # Connection optimization
      connections-max-idle-ms: 540000  # 9 minutes
      max-request-size: 1048576        # 1MB max request
      
      # Reliability settings (don't compromise)
      retries: 2147483647
      enable-idempotence: true
      acks: all
      max-in-flight-requests-per-connection: 1
```

#### **Consumer Optimization**
```yaml
app:
  kafka:
    consumer:
      # High throughput settings
      fetch-min-bytes: 100000     # 100KB minimum fetch
      fetch-max-wait-ms: 1000     # Wait up to 1 second
      max-poll-records: 2000      # Process 2000 records per poll
      
      # Memory optimization
      receive-buffer-bytes: 524288  # 512KB receive buffer
      send-buffer-bytes: 262144     # 256KB send buffer
      
      # Partition assignment optimization
      partition-assignment-strategy: 
        - org.apache.kafka.clients.consumer.CooperativeStickyAssignor
        - org.apache.kafka.clients.consumer.RangeAssignor
```

### 3. Application-Level Optimization

```java
@Configuration
public class PerformanceOptimization {
    
    @Bean
    @Primary
    public TaskExecutor kafkaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("kafka-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // Concurrency optimization
        factory.setConcurrency(Runtime.getRuntime().availableProcessors());
        
        // Batch processing
        factory.setBatchListener(true);
        
        // Consumer threading
        factory.getContainerProperties().setConsumerTaskExecutor(kafkaTaskExecutor());
        
        return factory;
    }
    
    @Bean
    @ConditionalOnProperty(name = "kafka.optimization.async-processing", havingValue = "true")
    public AsyncConfigurer asyncConfigurer() {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return kafkaTaskExecutor();
            }
            
            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (ex, method, params) -> 
                    log.error("Async execution error in {}: {}", method, ex.getMessage(), ex);
            }
        };
    }
}
```

---

## Operational Procedures

### 1. Deployment Checklist

#### **Pre-Deployment**
- [ ] Configuration validation completed
- [ ] SSL certificates updated and valid
- [ ] Database connections tested
- [ ] Kafka cluster health verified
- [ ] Resource quotas checked (CPU, memory, disk)
- [ ] Monitoring dashboards configured
- [ ] Alert rules updated
- [ ] Rollback plan prepared

#### **During Deployment**
- [ ] Zero-downtime deployment strategy executed
- [ ] Health checks passing
- [ ] Metrics collecting properly
- [ ] No error spikes detected
- [ ] Consumer lag within acceptable limits
- [ ] Message throughput stable

#### **Post-Deployment**
- [ ] Smoke tests executed successfully
- [ ] Integration tests passed
- [ ] Performance benchmarks met
- [ ] Error rates within SLA limits
- [ ] Monitoring alerts configured
- [ ] Documentation updated

### 2. Incident Response Procedures

#### **High Error Rate Incident**
```bash
# 1. Immediate assessment
curl -s http://app-url/actuator/health | jq .

# 2. Check recent deployments
kubectl rollout history deployment/spring-kafka-pro

# 3. Check Kafka cluster status
kafka-topics.sh --bootstrap-server kafka:9092 --list

# 4. Review application logs
kubectl logs deployment/spring-kafka-pro --tail=100

# 5. Check consumer lag
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group my-group --describe

# 6. If needed, rollback deployment
kubectl rollout undo deployment/spring-kafka-pro
```

#### **Consumer Lag Incident**
```bash
# 1. Check consumer group status
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group my-group --describe

# 2. Scale up consumer instances
kubectl scale deployment spring-kafka-pro --replicas=6

# 3. Monitor lag reduction
watch "kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group my-group --describe"

# 4. If needed, reset consumer offsets (CAUTION!)
kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group my-group --reset-offsets --to-latest --execute --all-topics
```

### 3. Maintenance Procedures

#### **Kafka Cluster Upgrade**
1. **Preparation**
   - Review upgrade documentation
   - Schedule maintenance window
   - Prepare rollback plan
   - Notify stakeholders

2. **Execution**
   - Upgrade brokers one by one
   - Verify cluster health after each broker
   - Monitor application metrics
   - Test message flow

3. **Validation**
   - Run integration tests
   - Verify consumer group assignments
   - Check replication factors
   - Monitor for 24 hours

---

## Troubleshooting Guide

### Common Issues and Solutions

#### **Issue: Consumer Group Rebalancing**
```
Symptoms:
- Frequent rebalancing logs
- Processing delays
- Consumer lag spikes

Root Causes:
- session.timeout.ms too low
- max.poll.interval.ms exceeded
- Network connectivity issues

Solutions:
1. Increase session.timeout.ms to 30000ms
2. Increase max.poll.interval.ms to 300000ms
3. Optimize message processing time
4. Check network stability
```

#### **Issue: Producer Timeouts**
```
Symptoms:
- TimeoutException in producer logs
- Failed message sends
- High delivery.timeout.ms

Root Causes:
- Network latency
- Kafka broker overload
- Insufficient buffer memory

Solutions:
1. Increase request.timeout.ms
2. Increase delivery.timeout.ms
3. Increase buffer.memory
4. Check broker performance
```

#### **Issue: Memory Leaks**
```
Symptoms:
- OutOfMemoryError
- Gradual memory increase
- GC pressure

Root Causes:
- Accumulating metrics
- Connection leaks
- Large message payloads

Solutions:
1. Review metrics retention
2. Ensure proper connection cleanup
3. Implement message size limits
4. Tune JVM garbage collection
```

### Debugging Commands

```bash
# Check application health
curl -s http://localhost:8080/actuator/health | jq .

# Get detailed metrics
curl -s http://localhost:8080/actuator/metrics/kafka.producer.messages.sent

# Check JVM memory usage
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq .

# View thread dump
curl -s http://localhost:8080/actuator/threaddump > threaddump.json

# Check Kafka consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Describe specific consumer group
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --describe

# List topics and partitions
kafka-topics.sh --bootstrap-server localhost:9092 --list

# Check topic configuration
kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name my-topic --describe
```

---

## Best Practices Checklist

### ✅ Configuration
- [ ] Use immutable configuration with validation
- [ ] Environment-specific configuration files
- [ ] Sensitive data in environment variables/secrets
- [ ] Configuration validation on startup
- [ ] Proper defaults for all settings

### ✅ Producer Configuration
- [ ] Idempotence enabled (`enable.idempotence=true`)
- [ ] Acknowledgment set to all (`acks=all`)
- [ ] Infinite retries (`retries=Integer.MAX_VALUE`)
- [ ] Appropriate batch size and linger time
- [ ] Compression enabled (LZ4 or Snappy)
- [ ] Proper timeout settings

### ✅ Consumer Configuration
- [ ] Manual acknowledgment enabled
- [ ] Auto-commit disabled (`enable.auto.commit=false`)
- [ ] Appropriate session timeout settings
- [ ] Proper fetch size configuration
- [ ] Consumer group ID configured
- [ ] Offset reset strategy defined

### ✅ Error Handling
- [ ] Comprehensive exception hierarchy
- [ ] Retry logic with exponential backoff
- [ ] Dead letter topic configuration
- [ ] Error classification (retryable vs non-retryable)
- [ ] Proper logging with correlation IDs

### ✅ Monitoring
- [ ] Health indicators implemented
- [ ] Comprehensive metrics collection
- [ ] Prometheus integration
- [ ] Grafana dashboards configured
- [ ] Alert rules defined
- [ ] Log aggregation setup

### ✅ Security
- [ ] SSL/TLS encryption enabled
- [ ] SASL authentication configured
- [ ] Network security groups configured
- [ ] Secrets management implemented
- [ ] Access control implemented

### ✅ Performance
- [ ] JVM properly tuned
- [ ] Connection pooling optimized
- [ ] Batch processing enabled
- [ ] Async operations used
- [ ] Resource limits configured

### ✅ Deployment
- [ ] Zero-downtime deployment strategy
- [ ] Health checks configured
- [ ] Resource quotas set
- [ ] Monitoring integration
- [ ] Rollback procedures documented

### ✅ Operations
- [ ] Incident response procedures
- [ ] Maintenance procedures documented
- [ ] Monitoring and alerting operational
- [ ] Backup and recovery procedures
- [ ] Performance benchmarking completed

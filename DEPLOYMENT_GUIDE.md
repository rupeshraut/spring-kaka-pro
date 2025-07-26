# Spring Kafka Pro - Deployment & Operations Guide

## 📋 Table of Contents

- [Overview](#overview)
- [Infrastructure Requirements](#infrastructure-requirements)
- [Local Development Setup](#local-development-setup)
- [Container Deployment](#container-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Production Configuration](#production-configuration)
- [Security Setup](#security-setup)
- [Monitoring & Observability](#monitoring--observability)
- [Backup & Disaster Recovery](#backup--disaster-recovery)
- [Performance Tuning](#performance-tuning)
- [Operational Procedures](#operational-procedures)
- [Troubleshooting](#troubleshooting)
- [Maintenance & Updates](#maintenance--updates)

---

## Overview

This guide provides comprehensive instructions for deploying and operating Spring Kafka Pro in various environments, from local development to production-scale Kubernetes clusters.

### Deployment Options
- 🖥️ **Local Development**: Docker Compose setup
- 🐳 **Container**: Docker deployment
- ☸️ **Kubernetes**: Production-ready orchestration
- ☁️ **Cloud**: AWS, GCP, Azure deployment patterns

---

## Infrastructure Requirements

### Minimum Requirements

| Component | Local Dev | Staging | Production |
|-----------|-----------|---------|------------|
| **CPU** | 2 cores | 4 cores | 8+ cores |
| **Memory** | 4 GB | 8 GB | 16+ GB |
| **Storage** | 20 GB | 100 GB | 500+ GB |
| **Network** | 1 Gbps | 1 Gbps | 10+ Gbps |

### Kafka Cluster Requirements

| Environment | Brokers | Partitions | Replication Factor |
|-------------|---------|------------|-------------------|
| **Development** | 1 | 3-6 | 1 |
| **Staging** | 3 | 6-12 | 2 |
| **Production** | 3+ | 12+ | 3 |

### Dependencies

```yaml
Required Services:
  - Apache Kafka 3.5+
  - Apache Zookeeper 3.8+ (if not using KRaft)
  - Java 17+
  - Container Runtime (Docker/containerd)

Optional Services:
  - Prometheus (monitoring)
  - Grafana (dashboards)
  - Jaeger (distributed tracing)
  - Elasticsearch (log aggregation)
```

---

## Local Development Setup

### 1. Docker Compose Setup

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    hostname: zookeeper
    container_name: zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
      - zookeeper-logs:/var/lib/zookeeper/log

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    hostname: kafka
    container_name: kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "19092:19092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_JMX_PORT: 19092
      KAFKA_JMX_HOSTNAME: localhost
    volumes:
      - kafka-data:/var/lib/kafka/data

  schema-registry:
    image: confluentinc/cp-schema-registry:7.4.0
    hostname: schema-registry
    container_name: schema-registry
    depends_on:
      - kafka
    ports:
      - "8081:8081"
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: 'kafka:29092'
      SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: kafka-ui
    depends_on:
      - kafka
      - schema-registry
    ports:
      - "8080:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
      KAFKA_CLUSTERS_0_SCHEMAREGISTRY: http://schema-registry:8081

  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--storage.tsdb.retention.time=200h'
      - '--web.enable-lifecycle'

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana
      - ./docker/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./docker/grafana/datasources:/etc/grafana/provisioning/datasources

volumes:
  zookeeper-data:
  zookeeper-logs:
  kafka-data:
  prometheus-data:
  grafana-data:
```

### 2. Prometheus Configuration

Create `docker/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  # - "first_rules.yml"
  # - "second_rules.yml"

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'spring-kafka-pro'
    static_configs:
      - targets: ['host.docker.internal:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s

  - job_name: 'kafka'
    static_configs:
      - targets: ['kafka:19092']
```

### 3. Grafana Dashboard Configuration

Create `docker/grafana/datasources/prometheus.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
```

### 4. Start Development Environment

```bash
# Start all services
docker-compose up -d

# Verify services are running
docker-compose ps

# Check logs
docker-compose logs -f spring-kafka-pro

# Stop all services
docker-compose down
```

---

## Container Deployment

### 1. Application Dockerfile

```dockerfile
# Multi-stage build for Spring Kafka Pro
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

# Build the application
RUN gradle clean build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install necessary packages
RUN apk add --no-cache curl dumb-init

# Create application user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Create application directory
WORKDIR /app

# Copy built application
COPY --from=builder /app/build/libs/spring-kafka-pro-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

# JVM and application configuration
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Use dumb-init as PID 1
ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 2. Build and Run Container

```bash
# Build image
docker build -t spring-kafka-pro:latest .

# Run container with environment variables
docker run -d \
  --name spring-kafka-pro \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092 \
  -e KAFKA_SECURITY_ENABLED=true \
  -e KAFKA_USERNAME=app-user \
  -e KAFKA_PASSWORD=secure-password \
  -v /var/log/spring-kafka-pro:/app/logs \
  spring-kafka-pro:latest

# Check container health
docker ps
docker logs spring-kafka-pro
```

### 3. Docker Compose for Production

```yaml
version: '3.8'

services:
  spring-kafka-pro:
    image: spring-kafka-pro:latest
    container_name: spring-kafka-pro
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}
      - KAFKA_SECURITY_ENABLED=true
      - KAFKA_USERNAME=${KAFKA_USERNAME}
      - KAFKA_PASSWORD=${KAFKA_PASSWORD}
      - KAFKA_SSL_TRUSTSTORE_LOCATION=/app/security/kafka.client.truststore.jks
      - KAFKA_SSL_TRUSTSTORE_PASSWORD=${KAFKA_SSL_TRUSTSTORE_PASSWORD}
      - LOGGING_LEVEL_ROOT=INFO
    volumes:
      - ./security:/app/security:ro
      - /var/log/spring-kafka-pro:/app/logs
      - /tmp:/tmp
    networks:
      - kafka-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
        reservations:
          memory: 1G
          cpus: '0.5'

networks:
  kafka-network:
    external: true
```

---

## Kubernetes Deployment

### 1. Namespace and ConfigMap

```yaml
# namespace.yml
apiVersion: v1
kind: Namespace
metadata:
  name: spring-kafka-pro
  labels:
    name: spring-kafka-pro

---
# configmap.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-kafka-pro-config
  namespace: spring-kafka-pro
data:
  application.yml: |
    spring:
      application:
        name: spring-kafka-pro
      profiles:
        active: prod
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
      producer:
        acks: all
        retries: 2147483647
        enable-idempotence: true
        batch-size: 32768
        linger-ms: 20
        compression-type: snappy
      consumer:
        group-id: ${KAFKA_CONSUMER_GROUP:spring-kafka-pro-group}
        auto-offset-reset: earliest
        enable-auto-commit: false
        max-poll-records: 500
        concurrency: ${KAFKA_CONSUMER_CONCURRENCY:6}
      security:
        enabled: ${KAFKA_SECURITY_ENABLED:true}
        protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_SSL}
    management:
      endpoints:
        web:
          exposure:
            include: health,metrics,prometheus,info
      endpoint:
        health:
          show-details: when-authorized
    logging:
      level:
        com.company.kafka: INFO
        org.springframework.kafka: WARN
      file:
        name: /app/logs/spring-kafka-pro.log
```

### 2. Secret Management

```yaml
# secrets.yml
apiVersion: v1
kind: Secret
metadata:
  name: spring-kafka-pro-secrets
  namespace: spring-kafka-pro
type: Opaque
data:
  kafka-username: <base64-encoded-username>
  kafka-password: <base64-encoded-password>
  kafka-ssl-truststore-password: <base64-encoded-truststore-password>
  kafka-ssl-keystore-password: <base64-encoded-keystore-password>

---
# SSL certificates secret
apiVersion: v1
kind: Secret
metadata:
  name: kafka-ssl-certs
  namespace: spring-kafka-pro
type: Opaque
data:
  kafka.client.truststore.jks: <base64-encoded-truststore>
  kafka.client.keystore.jks: <base64-encoded-keystore>
```

### 3. Deployment

```yaml
# deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-kafka-pro
  namespace: spring-kafka-pro
  labels:
    app: spring-kafka-pro
    version: v1
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  selector:
    matchLabels:
      app: spring-kafka-pro
  template:
    metadata:
      labels:
        app: spring-kafka-pro
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        runAsGroup: 1001
        fsGroup: 1001
      containers:
      - name: spring-kafka-pro
        image: spring-kafka-pro:1.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-cluster-kafka-bootstrap.kafka.svc.cluster.local:9092"
        - name: KAFKA_CONSUMER_GROUP
          value: "spring-kafka-pro-group"
        - name: KAFKA_CONSUMER_CONCURRENCY
          value: "6"
        - name: KAFKA_SECURITY_ENABLED
          value: "true"
        - name: KAFKA_SECURITY_PROTOCOL
          value: "SASL_SSL"
        - name: KAFKA_USERNAME
          valueFrom:
            secretKeyRef:
              name: spring-kafka-pro-secrets
              key: kafka-username
        - name: KAFKA_PASSWORD
          valueFrom:
            secretKeyRef:
              name: spring-kafka-pro-secrets
              key: kafka-password
        - name: KAFKA_SSL_TRUSTSTORE_LOCATION
          value: "/app/security/kafka.client.truststore.jks"
        - name: KAFKA_SSL_TRUSTSTORE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: spring-kafka-pro-secrets
              key: kafka-ssl-truststore-password
        - name: JAVA_OPTS
          value: "-XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: ssl-certs
          mountPath: /app/security
          readOnly: true
        - name: logs-volume
          mountPath: /app/logs
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 10
      volumes:
      - name: config-volume
        configMap:
          name: spring-kafka-pro-config
      - name: ssl-certs
        secret:
          secretName: kafka-ssl-certs
      - name: logs-volume
        emptyDir: {}
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - spring-kafka-pro
              topologyKey: kubernetes.io/hostname
```

### 4. Service and Ingress

```yaml
# service.yml
apiVersion: v1
kind: Service
metadata:
  name: spring-kafka-pro-service
  namespace: spring-kafka-pro
  labels:
    app: spring-kafka-pro
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: spring-kafka-pro

---
# ingress.yml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring-kafka-pro-ingress
  namespace: spring-kafka-pro
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - kafka-pro.yourdomain.com
    secretName: spring-kafka-pro-tls
  rules:
  - host: kafka-pro.yourdomain.com
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

### 5. Horizontal Pod Autoscaler

```yaml
# hpa.yml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: spring-kafka-pro-hpa
  namespace: spring-kafka-pro
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: spring-kafka-pro
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: kafka_consumer_lag_sum
      target:
        type: AverageValue
        averageValue: "1000"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
```

### 6. Deployment Commands

```bash
# Apply all Kubernetes manifests
kubectl apply -f namespace.yml
kubectl apply -f configmap.yml
kubectl apply -f secrets.yml
kubectl apply -f deployment.yml
kubectl apply -f service.yml
kubectl apply -f ingress.yml
kubectl apply -f hpa.yml

# Check deployment status
kubectl get pods -n spring-kafka-pro
kubectl get svc -n spring-kafka-pro
kubectl get ingress -n spring-kafka-pro

# View logs
kubectl logs -f deployment/spring-kafka-pro -n spring-kafka-pro

# Scale deployment
kubectl scale deployment spring-kafka-pro --replicas=5 -n spring-kafka-pro

# Rolling update
kubectl set image deployment/spring-kafka-pro spring-kafka-pro=spring-kafka-pro:1.1.0 -n spring-kafka-pro

# Check rollout status
kubectl rollout status deployment/spring-kafka-pro -n spring-kafka-pro

# Rollback if needed
kubectl rollout undo deployment/spring-kafka-pro -n spring-kafka-pro
```

---

## Production Configuration

### 1. Environment Variables

```bash
# Kafka Configuration
export KAFKA_BOOTSTRAP_SERVERS="kafka1.prod.com:9092,kafka2.prod.com:9092,kafka3.prod.com:9092"
export KAFKA_SECURITY_ENABLED=true
export KAFKA_SECURITY_PROTOCOL=SASL_SSL
export KAFKA_USERNAME=spring-kafka-pro-user
export KAFKA_PASSWORD=secure-production-password
export KAFKA_CONSUMER_GROUP=spring-kafka-pro-prod-group
export KAFKA_CONSUMER_CONCURRENCY=12

# SSL Configuration
export KAFKA_SSL_TRUSTSTORE_LOCATION=/app/security/kafka.client.truststore.jks
export KAFKA_SSL_TRUSTSTORE_PASSWORD=truststore-password
export KAFKA_SSL_KEYSTORE_LOCATION=/app/security/kafka.client.keystore.jks
export KAFKA_SSL_KEYSTORE_PASSWORD=keystore-password

# Application Configuration
export SPRING_PROFILES_ACTIVE=prod
export LOGGING_LEVEL_ROOT=INFO
export LOGGING_FILE_NAME=/var/log/spring-kafka-pro/application.log

# JVM Tuning
export JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

### 2. Application Properties (Production)

```yaml
# application-prod.yml
spring:
  application:
    name: spring-kafka-pro
  profiles:
    active: prod

kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
  producer:
    acks: all
    retries: 2147483647
    enable-idempotence: true
    max-in-flight-requests-per-connection: 1
    batch-size: 65536  # 64KB for better throughput
    linger-ms: 50      # Wait longer for better batching
    compression-type: lz4
    buffer-memory: 134217728  # 128MB buffer
    request-timeout: PT60S
    delivery-timeout: PT5M
  consumer:
    group-id: ${KAFKA_CONSUMER_GROUP}
    auto-offset-reset: earliest
    enable-auto-commit: false
    max-poll-records: 1000     # Higher for batch processing
    max-poll-interval: PT10M   # Longer for complex processing
    session-timeout: PT45S
    heartbeat-interval: PT15S
    fetch-min-bytes: 100000    # 100KB minimum fetch
    fetch-max-wait: PT1S
    isolation-level: read_committed
    concurrency: ${KAFKA_CONSUMER_CONCURRENCY:12}
  topics:
    orders:
      name: orders-topic
      partitions: 24
      replication-factor: 3
    payments:
      name: payments-topic
      partitions: 24
      replication-factor: 3
    notifications:
      name: notifications-topic
      partitions: 12
      replication-factor: 3
  security:
    enabled: ${KAFKA_SECURITY_ENABLED:true}
    protocol: ${KAFKA_SECURITY_PROTOCOL:SASL_SSL}
    ssl:
      trust-store-location: ${KAFKA_SSL_TRUSTSTORE_LOCATION}
      trust-store-password: ${KAFKA_SSL_TRUSTSTORE_PASSWORD}
      key-store-location: ${KAFKA_SSL_KEYSTORE_LOCATION}
      key-store-password: ${KAFKA_SSL_KEYSTORE_PASSWORD}
    sasl:
      mechanism: SCRAM-SHA-512
      jaas-config: |
        org.apache.kafka.common.security.scram.ScramLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: when-authorized
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
        step: PT10S
    distribution:
      percentiles-histogram:
        "[http.server.requests]": true
        "[kafka.producer.send.time]": true
        "[kafka.consumer.processing.time]": true
      slo:
        "[http.server.requests]": "50ms,100ms,200ms,500ms,1s,2s"
        "[kafka.producer.send.time]": "10ms,50ms,100ms,200ms,500ms"

logging:
  level:
    com.company.kafka: INFO
    org.springframework.kafka: WARN
    org.apache.kafka: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{correlationId:-}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{correlationId:-}] %logger{36} - %msg%n"
  file:
    name: ${LOGGING_FILE_NAME:/var/log/spring-kafka-pro/application.log}
    max-size: 100MB
    max-history: 30
    total-size-cap: 1GB

server:
  port: 8080
  shutdown: graceful
  tomcat:
    max-threads: 200
    min-spare-threads: 50
```

---

## Security Setup

### 1. SSL/TLS Configuration

#### Generate SSL Certificates

```bash
# Create Certificate Authority (CA)
openssl req -new -x509 -keyout ca-key -out ca-cert -days 365 -subj "/CN=KafkaCA" -nodes

# Create server keystore
keytool -keystore kafka.server.keystore.jks -alias localhost -validity 365 -genkey -keyalg RSA -dname "CN=localhost" -storepass server-password -keypass server-password

# Create certificate request
keytool -keystore kafka.server.keystore.jks -alias localhost -certreq -file cert-file -storepass server-password

# Sign certificate with CA
openssl x509 -req -CA ca-cert -CAkey ca-key -in cert-file -out cert-signed -days 365 -CAcreateserial

# Import CA certificate into keystore
keytool -keystore kafka.server.keystore.jks -alias CARoot -import -file ca-cert -storepass server-password -noprompt

# Import signed certificate into keystore
keytool -keystore kafka.server.keystore.jks -alias localhost -import -file cert-signed -storepass server-password -noprompt

# Create client truststore
keytool -keystore kafka.client.truststore.jks -alias CARoot -import -file ca-cert -storepass client-password -noprompt

# Create client keystore (for mutual TLS)
keytool -keystore kafka.client.keystore.jks -alias client -validity 365 -genkey -keyalg RSA -dname "CN=client" -storepass client-password -keypass client-password
```

#### Kafka Server Configuration

```properties
# server.properties
listeners=PLAINTEXT://localhost:9092,SSL://localhost:9093,SASL_SSL://localhost:9094
advertised.listeners=PLAINTEXT://localhost:9092,SSL://localhost:9093,SASL_SSL://localhost:9094
listener.security.protocol.map=PLAINTEXT:PLAINTEXT,SSL:SSL,SASL_SSL:SASL_SSL

# SSL Configuration
ssl.keystore.location=/path/to/kafka.server.keystore.jks
ssl.keystore.password=server-password
ssl.key.password=server-password
ssl.truststore.location=/path/to/kafka.server.truststore.jks
ssl.truststore.password=server-password
ssl.client.auth=required
ssl.endpoint.identification.algorithm=HTTPS

# SASL Configuration
sasl.enabled.mechanisms=SCRAM-SHA-512
sasl.mechanism.inter.broker.protocol=SCRAM-SHA-512
listener.name.sasl_ssl.scram-sha-512.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="admin" password="admin-secret";

# Security protocols
security.inter.broker.protocol=SASL_SSL
```

### 2. SASL/SCRAM Setup

#### Create SASL Users

```bash
# Create admin user
kafka-configs.sh --zookeeper localhost:2181 --alter --add-config 'SCRAM-SHA-512=[password=admin-secret]' --entity-type users --entity-name admin

# Create application user
kafka-configs.sh --zookeeper localhost:2181 --alter --add-config 'SCRAM-SHA-512=[password=app-secret]' --entity-type users --entity-name spring-kafka-pro-user

# List users
kafka-configs.sh --zookeeper localhost:2181 --describe --entity-type users
```

#### Application JAAS Configuration

```java
// SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/api/kafka/management/**").hasRole("ADMIN")
                .requestMatchers("/api/kafka/dlt/**").hasRole("OPERATOR")
                .anyRequest().authenticated()
            )
            .httpBasic(withDefaults())
            .csrf(csrf -> csrf.disable());
            
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin-password"))
            .roles("ADMIN", "OPERATOR")
            .build();
            
        UserDetails operator = User.builder()
            .username("operator")
            .password(passwordEncoder().encode("operator-password"))
            .roles("OPERATOR")
            .build();
            
        return new InMemoryUserDetailsManager(admin, operator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 3. Network Security

#### Firewall Rules

```bash
# Allow Kafka broker ports
sudo ufw allow 9092/tcp  # PLAINTEXT
sudo ufw allow 9093/tcp  # SSL
sudo ufw allow 9094/tcp  # SASL_SSL

# Allow application port
sudo ufw allow 8080/tcp

# Allow monitoring ports
sudo ufw allow 9090/tcp  # Prometheus
sudo ufw allow 3000/tcp  # Grafana

# Block unnecessary ports
sudo ufw deny 2181/tcp   # ZooKeeper (internal only)
```

#### Network Policies (Kubernetes)

```yaml
# network-policy.yml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: spring-kafka-pro-network-policy
  namespace: spring-kafka-pro
spec:
  podSelector:
    matchLabels:
      app: spring-kafka-pro
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: monitoring
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: kafka
    ports:
    - protocol: TCP
      port: 9092
    - protocol: TCP
      port: 9094
  - to: []  # DNS resolution
    ports:
    - protocol: UDP
      port: 53
```

---

## Monitoring & Observability

### 1. Prometheus Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    cluster: 'production'
    replica: 'prometheus-1'

rule_files:
  - "/etc/prometheus/rules/*.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093

scrape_configs:
  - job_name: 'spring-kafka-pro'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - spring-kafka-pro
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__
      - action: labelmap
        regex: __meta_kubernetes_pod_label_(.+)
      - source_labels: [__meta_kubernetes_namespace]
        action: replace
        target_label: kubernetes_namespace
      - source_labels: [__meta_kubernetes_pod_name]
        action: replace
        target_label: kubernetes_pod_name

  - job_name: 'kafka-exporter'
    static_configs:
      - targets: ['kafka-exporter:9308']
```

### 2. Alerting Rules

```yaml
# alerts.yml
groups:
  - name: spring-kafka-pro
    rules:
      - alert: HighConsumerLag
        expr: kafka_consumer_lag_sum > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High consumer lag detected"
          description: "Consumer lag is {{ $value }} for group {{ $labels.group }}"

      - alert: ProducerErrors
        expr: rate(kafka_producer_messages_failed_total[5m]) > 10
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High producer error rate"
          description: "Producer error rate is {{ $value }} per second"

      - alert: DeadLetterTopicGrowth
        expr: increase(kafka_dlt_messages_total[1h]) > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Dead letter topic growing rapidly"
          description: "DLT has received {{ $value }} messages in the last hour"

      - alert: CircuitBreakerOpen
        expr: kafka_error_circuit_breaker_opens_total > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker opened"
          description: "Circuit breaker for {{ $labels.service }} is open"

      - alert: ApplicationDown
        expr: up{job="spring-kafka-pro"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Spring Kafka Pro application is down"
          description: "Instance {{ $labels.instance }} has been down for more than 1 minute"

      - alert: HighMemoryUsage
        expr: (kafka_jvm_memory_used_bytes / kafka_jvm_memory_max_bytes) * 100 > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage"
          description: "Memory usage is {{ $value }}% on {{ $labels.instance }}"

      - alert: PoisonPillDetected
        expr: increase(kafka_error_poison_pills_total[5m]) > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Poison pill messages detected"
          description: "{{ $value }} poison pill messages detected in the last 5 minutes"
```

### 3. Grafana Dashboards

#### Application Dashboard

```json
{
  "dashboard": {
    "id": null,
    "title": "Spring Kafka Pro Dashboard",
    "tags": ["kafka", "spring-boot"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
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
        "id": 2,
        "title": "Consumer Lag",
        "type": "singlestat",
        "targets": [
          {
            "expr": "kafka_consumer_lag_sum",
            "legendFormat": "Total Lag"
          }
        ]
      },
      {
        "id": 3,
        "title": "Error Rates",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(kafka_consumer_messages_failed_total[5m])",
            "legendFormat": "Consumer Errors/sec"
          },
          {
            "expr": "rate(kafka_producer_messages_failed_total[5m])",
            "legendFormat": "Producer Errors/sec"
          }
        ]
      },
      {
        "id": 4,
        "title": "DLT Messages",
        "type": "graph",
        "targets": [
          {
            "expr": "kafka_dlt_messages_total",
            "legendFormat": "Total DLT Messages"
          }
        ]
      }
    ]
  }
}
```

### 4. Log Aggregation

#### Fluentd Configuration

```yaml
# fluentd-configmap.yml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluentd-config
  namespace: kube-system
data:
  fluent.conf: |
    <source>
      @type tail
      path /var/log/containers/spring-kafka-pro*.log
      pos_file /var/log/fluentd-spring-kafka-pro.log.pos
      tag kubernetes.spring-kafka-pro
      format json
      time_key time
      time_format %Y-%m-%dT%H:%M:%S.%NZ
    </source>
    
    <filter kubernetes.spring-kafka-pro>
      @type kubernetes_metadata
    </filter>
    
    <match kubernetes.spring-kafka-pro>
      @type elasticsearch
      host elasticsearch.logging.svc.cluster.local
      port 9200
      index_name spring-kafka-pro
      type_name _doc
      flush_interval 10s
    </match>
```

---

## Performance Tuning

### 1. JVM Tuning

```bash
# Production JVM settings
JAVA_OPTS="
  # Heap settings
  -Xms4g
  -Xmx8g
  
  # GC settings
  -XX:+UseG1GC
  -XX:G1HeapRegionSize=16m
  -XX:G1ReservePercent=25
  -XX:G1NewSizePercent=30
  -XX:G1MaxNewSizePercent=40
  -XX:G1MixedGCLiveThresholdPercent=85
  -XX:+G1UseAdaptiveIHOP
  -XX:G1MixedGCCountTarget=8
  -XX:G1OldCSetRegionThresholdPercent=10
  
  # Container support
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  
  # Performance monitoring
  -XX:+FlightRecorder
  -XX:+UnlockCommercialFeatures
  
  # Error handling
  -XX:+ExitOnOutOfMemoryError
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/app/logs/heapdump.hprof
  
  # Security
  -Djava.security.egd=file:/dev/./urandom
  
  # Debugging (disable in production)
  # -XX:+PrintGC
  # -XX:+PrintGCDetails
  # -XX:+PrintGCTimeStamps
"
```

### 2. Kafka Configuration Tuning

#### Producer Optimization

```properties
# High throughput producer settings
acks=all
retries=2147483647
enable.idempotence=true
max.in.flight.requests.per.connection=1

# Batching for throughput
batch.size=65536
linger.ms=50
compression.type=lz4

# Buffer and memory
buffer.memory=134217728
send.buffer.bytes=131072
receive.buffer.bytes=131072

# Timeouts
request.timeout.ms=60000
delivery.timeout.ms=300000
```

#### Consumer Optimization

```properties
# Fetch optimization
fetch.min.bytes=100000
fetch.max.wait.ms=500
max.partition.fetch.bytes=1048576

# Poll settings
max.poll.records=1000
max.poll.interval.ms=600000

# Session management
session.timeout.ms=45000
heartbeat.interval.ms=15000

# Network buffers
send.buffer.bytes=131072
receive.buffer.bytes=131072
```

### 3. Application-Level Tuning

#### Thread Pool Configuration

```java
@Configuration
public class ThreadPoolConfig {

    @Bean("kafkaTaskExecutor")
    public TaskExecutor kafkaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kafka-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("dltProcessingExecutor")
    public TaskExecutor dltProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("dlt-processing-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
```

#### Connection Pool Tuning

```java
@Configuration
public class KafkaOptimizationConfig {

    @Bean
    public ProducerFactory<String, Object> optimizedProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Connection pooling
        props.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 540000);
        props.put(ProducerConfig.METADATA_MAX_AGE_CONFIG, 300000);
        
        // Partitioner for better distribution
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, 
            "org.apache.kafka.clients.producer.RoundRobinPartitioner");
        
        // Interceptors for monitoring
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
            "com.company.kafka.interceptor.KafkaProducerInterceptor");
        
        return new DefaultKafkaProducerFactory<>(props);
    }
}
```

---

## Operational Procedures

### 1. Deployment Procedures

#### Blue-Green Deployment

```bash
#!/bin/bash
# blue-green-deploy.sh

NAMESPACE="spring-kafka-pro"
NEW_VERSION=$1
CURRENT_COLOR=$(kubectl get deployment spring-kafka-pro -n $NAMESPACE -o jsonpath='{.metadata.labels.color}')

if [ "$CURRENT_COLOR" == "blue" ]; then
    NEW_COLOR="green"
else
    NEW_COLOR="blue"
fi

echo "Deploying version $NEW_VERSION to $NEW_COLOR environment"

# Update deployment with new version and color
kubectl patch deployment spring-kafka-pro -n $NAMESPACE -p '
{
  "metadata": {
    "labels": {
      "color": "'$NEW_COLOR'"
    }
  },
  "spec": {
    "selector": {
      "matchLabels": {
        "color": "'$NEW_COLOR'"
      }
    },
    "template": {
      "metadata": {
        "labels": {
          "color": "'$NEW_COLOR'"
        }
      },
      "spec": {
        "containers": [
          {
            "name": "spring-kafka-pro",
            "image": "spring-kafka-pro:'$NEW_VERSION'"
          }
        ]
      }
    }
  }
}'

# Wait for rollout to complete
kubectl rollout status deployment/spring-kafka-pro -n $NAMESPACE --timeout=600s

# Run health checks
echo "Running health checks..."
for i in {1..30}; do
    if kubectl exec -n $NAMESPACE deployment/spring-kafka-pro -- curl -f http://localhost:8080/actuator/health; then
        echo "Health check passed"
        break
    fi
    sleep 10
done

# Switch traffic to new version
kubectl patch service spring-kafka-pro-service -n $NAMESPACE -p '
{
  "spec": {
    "selector": {
      "color": "'$NEW_COLOR'"
    }
  }
}'

echo "Traffic switched to $NEW_COLOR ($NEW_VERSION)"
```

#### Rolling Update

```bash
#!/bin/bash
# rolling-update.sh

NAMESPACE="spring-kafka-pro"
NEW_VERSION=$1

echo "Starting rolling update to version $NEW_VERSION"

# Update image
kubectl set image deployment/spring-kafka-pro spring-kafka-pro=spring-kafka-pro:$NEW_VERSION -n $NAMESPACE

# Monitor rollout
kubectl rollout status deployment/spring-kafka-pro -n $NAMESPACE --timeout=900s

# Verify deployment
kubectl get pods -n $NAMESPACE -l app=spring-kafka-pro

echo "Rolling update completed"
```

### 2. Backup Procedures

#### Configuration Backup

```bash
#!/bin/bash
# backup-config.sh

BACKUP_DIR="/backup/spring-kafka-pro/$(date +%Y%m%d_%H%M%S)"
NAMESPACE="spring-kafka-pro"

mkdir -p $BACKUP_DIR

echo "Backing up Kubernetes resources..."

# Backup all resources
kubectl get all -n $NAMESPACE -o yaml > $BACKUP_DIR/all-resources.yaml
kubectl get configmap -n $NAMESPACE -o yaml > $BACKUP_DIR/configmaps.yaml
kubectl get secret -n $NAMESPACE -o yaml > $BACKUP_DIR/secrets.yaml
kubectl get pvc -n $NAMESPACE -o yaml > $BACKUP_DIR/persistent-volumes.yaml

# Backup Kafka topic configurations
echo "Backing up Kafka topic configurations..."
kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --list > $BACKUP_DIR/topics-list.txt

for topic in $(cat $BACKUP_DIR/topics-list.txt); do
    kafka-topics.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --describe --topic $topic > $BACKUP_DIR/topic-$topic.conf
done

# Backup consumer group offsets
echo "Backing up consumer group offsets..."
kafka-consumer-groups.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --list > $BACKUP_DIR/consumer-groups.txt

for group in $(cat $BACKUP_DIR/consumer-groups.txt); do
    kafka-consumer-groups.sh --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --group $group --describe > $BACKUP_DIR/offsets-$group.txt
done

echo "Backup completed: $BACKUP_DIR"
```

### 3. Disaster Recovery

#### Restore Procedure

```bash
#!/bin/bash
# restore.sh

BACKUP_DIR=$1
NAMESPACE="spring-kafka-pro"

if [ -z "$BACKUP_DIR" ]; then
    echo "Usage: $0 <backup-directory>"
    exit 1
fi

echo "Restoring from backup: $BACKUP_DIR"

# Restore Kubernetes resources
kubectl apply -f $BACKUP_DIR/configmaps.yaml
kubectl apply -f $BACKUP_DIR/secrets.yaml
kubectl apply -f $BACKUP_DIR/persistent-volumes.yaml
kubectl apply -f $BACKUP_DIR/all-resources.yaml

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=spring-kafka-pro -n $NAMESPACE --timeout=600s

# Restore Kafka topics
echo "Restoring Kafka topics..."
for conf in $BACKUP_DIR/topic-*.conf; do
    topic=$(basename $conf .conf | sed 's/topic-//')
    # Parse topic configuration and recreate
    # (Implementation depends on your topic configuration format)
done

echo "Restore completed"
```

### 4. Maintenance Procedures

#### Log Rotation

```bash
#!/bin/bash
# log-rotation.sh

LOG_DIR="/var/log/spring-kafka-pro"
RETENTION_DAYS=30

echo "Starting log rotation for Spring Kafka Pro"

# Compress logs older than 1 day
find $LOG_DIR -name "*.log" -mtime +1 -exec gzip {} \;

# Remove compressed logs older than retention period
find $LOG_DIR -name "*.gz" -mtime +$RETENTION_DAYS -delete

# Rotate current log
if [ -f "$LOG_DIR/application.log" ]; then
    mv "$LOG_DIR/application.log" "$LOG_DIR/application.log.$(date +%Y%m%d_%H%M%S)"
    touch "$LOG_DIR/application.log"
    chown spring-kafka-pro:spring-kafka-pro "$LOG_DIR/application.log"
fi

echo "Log rotation completed"
```

#### Health Check Script

```bash
#!/bin/bash
# health-check.sh

APP_URL="http://localhost:8080"
HEALTHY=true

echo "Running health checks for Spring Kafka Pro"

# Check application health
if ! curl -f $APP_URL/actuator/health > /dev/null 2>&1; then
    echo "ERROR: Application health check failed"
    HEALTHY=false
fi

# Check Kafka connectivity
if ! curl -f $APP_URL/actuator/health/kafka > /dev/null 2>&1; then
    echo "ERROR: Kafka connectivity check failed"
    HEALTHY=false
fi

# Check memory usage
MEMORY_USAGE=$(curl -s $APP_URL/actuator/metrics/jvm.memory.used | jq -r '.measurements[0].value')
MEMORY_MAX=$(curl -s $APP_URL/actuator/metrics/jvm.memory.max | jq -r '.measurements[0].value')
MEMORY_PERCENT=$(echo "scale=2; $MEMORY_USAGE / $MEMORY_MAX * 100" | bc)

if (( $(echo "$MEMORY_PERCENT > 85" | bc -l) )); then
    echo "WARNING: Memory usage is high: ${MEMORY_PERCENT}%"
fi

# Check consumer lag
LAG=$(curl -s $APP_URL/actuator/metrics/kafka.consumer.lag | jq -r '.measurements[0].value')
if (( $(echo "$LAG > 10000" | bc -l) )); then
    echo "WARNING: Consumer lag is high: $LAG"
fi

if [ "$HEALTHY" = true ]; then
    echo "All health checks passed"
    exit 0
else
    echo "Health check failed"
    exit 1
fi
```

---

This comprehensive deployment guide provides everything needed to successfully deploy and operate Spring Kafka Pro in production environments. Follow these procedures to ensure reliable, secure, and performant deployments.
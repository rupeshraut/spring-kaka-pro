# Kafka Partitioning Usage Examples and Configuration Guide

This guide provides practical examples and configuration patterns for implementing advanced Kafka partitioning strategies in your Spring Boot application.

## Table of Contents

- [Quick Start Examples](#quick-start-examples)
- [Producer Partitioning Examples](#producer-partitioning-examples)
- [Consumer Assignment Examples](#consumer-assignment-examples)
- [Complete Configuration Examples](#complete-configuration-examples)
- [Real-World Use Cases](#real-world-use-cases)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting Guide](#troubleshooting-guide)

## Quick Start Examples

### Basic Setup

1. **Add partitioning configuration to `application.yml`:**

```yaml
kafka:
  partitioning:
    producer:
      strategy: HASH  # Start with default behavior
    consumer:
      assignment-strategy: LOAD_BALANCED
      metadata:
        rack: default
        priority: MEDIUM
        capacity: 1000
        region: default
    monitoring:
      enabled: true
      metrics-interval: PT30S
```

2. **Verify setup with health check:**

```bash
curl http://localhost:8080/api/kafka/partitioning/health
```

3. **Monitor partition distribution:**

```bash
curl http://localhost:8080/api/kafka/partitioning/monitor
```

## Producer Partitioning Examples

### Example 1: E-commerce Order Processing

**Scenario:** Route customer orders to dedicated partitions for better customer data locality.

```yaml
kafka:
  partitioning:
    producer:
      strategy: CUSTOMER_AWARE
      customer:
        hash-seed: 12345
        customer-id-field: customerId
        enable-caching: true
        cache-size: 50000
```

**Message Format:**
```json
{
  "orderId": "ORD-001",
  "customerId": "CUST-12345",
  "amount": 99.99,
  "items": [...]
}
```

**Benefits:**
- All orders from a customer go to the same partition
- Enables customer-specific processing and analytics
- Improves cache locality for customer data

### Example 2: Multi-Region Deployment

**Scenario:** Distribute messages across regions for compliance and latency optimization.

```yaml
kafka:
  partitioning:
    producer:
      strategy: GEOGRAPHIC
      geographic:
        regions: ["us-east", "us-west", "eu-west", "asia-pacific"]
        region-field: region
        enable-cross-border-fallback: true
        default-region: us-east
```

**Message Format:**
```json
{
  "eventId": "EVT-001",
  "region": "eu-west",
  "userId": "user123",
  "data": {...}
}
```

**Producer Code:**
```java
@Service
public class EventPublisher {
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishEvent(Event event) {
        // The region field in the message will be used for geographic partitioning
        kafkaTemplate.send("events-topic", event.getUserId(), event);
    }
}
```

### Example 3: Priority-Based Message Processing

**Scenario:** Route high-priority messages to dedicated partitions for faster processing.

```yaml
kafka:
  partitioning:
    producer:
      strategy: PRIORITY
      priority:
        partition-ratio: 0.3  # 30% of partitions for high-priority messages
        priority-field: priority
        high-priority-values: ["HIGH", "CRITICAL", "URGENT"]
        enable-dynamic-adjustment: false
```

**Message Format:**
```json
{
  "messageId": "MSG-001",
  "priority": "HIGH",
  "content": "Critical system alert",
  "timestamp": "2023-12-01T10:00:00Z"
}
```

**Consumer Code:**
```java
@KafkaListener(
    topics = "alerts-topic",
    containerFactory = "kafkaListenerContainerFactory"
)
public void processAlert(
    @Payload AlertMessage message,
    @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition
) {
    if (isHighPriorityPartition(partition)) {
        // Process high-priority message immediately
        processHighPriorityAlert(message);
    } else {
        // Process normal priority message
        processNormalAlert(message);
    }
}
```

### Example 4: Time-Based Data Processing

**Scenario:** Organize time-series data into time windows for batch processing.

```yaml
kafka:
  partitioning:
    producer:
      strategy: TIME_BASED
      time-based:
        time-window: PT5M  # 5-minute windows
        use-utc-time: true
        time-field: timestamp
```

**Message Format:**
```json
{
  "sensorId": "SENSOR-001",
  "timestamp": "2023-12-01T10:05:00Z",
  "temperature": 23.5,
  "humidity": 45.2
}
```

**Use Case:** All sensor data within a 5-minute window goes to the same partition, enabling efficient batch processing.

### Example 5: Load-Aware Hot Partition Avoidance

**Scenario:** Dynamically avoid hot partitions in high-throughput scenarios.

```yaml
kafka:
  partitioning:
    producer:
      strategy: LOAD_AWARE
      load-aware:
        load-threshold: 5000  # Messages per monitoring window
        metrics-window: PT1M
        enable-hot-partition-avoidance: true
        load-balancing-factor: 2.0
```

**Monitoring Script:**
```bash
#!/bin/bash
# Monitor partition load distribution
while true; do
    echo "=== Partition Distribution at $(date) ==="
    curl -s http://localhost:8080/api/kafka/partitioning/distribution | \
        jq '.partitioners[].partitionDistribution'
    sleep 30
done
```

## Consumer Assignment Examples

### Example 1: Heterogeneous Consumer Deployment

**Scenario:** Different consumer instances with varying processing capabilities.

```yaml
# High-capacity consumer configuration
kafka:
  partitioning:
    consumer:
      assignment-strategy: LOAD_BALANCED
      metadata:
        rack: us-east-1a
        priority: HIGH
        capacity: 5000
        region: us-east
        capabilities: ["BATCH_PROCESSING", "REAL_TIME", "JSON", "AVRO"]
        preferences: ["HIGH_THROUGHPUT"]

# Standard consumer configuration  
kafka:
  partitioning:
    consumer:
      assignment-strategy: LOAD_BALANCED
      metadata:
        rack: us-east-1b
        priority: MEDIUM
        capacity: 2000
        region: us-east
        capabilities: ["JSON"]
        preferences: ["BATCH_PROCESSING"]
```

**Deployment:**
```yaml
# docker-compose.yml
version: '3.8'
services:
  high-capacity-consumer:
    image: my-app:latest
    environment:
      - CONSUMER_CAPACITY=5000
      - CONSUMER_PRIORITY=HIGH
      - CONSUMER_RACK=us-east-1a
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2.0'

  standard-consumer:
    image: my-app:latest
    environment:
      - CONSUMER_CAPACITY=2000
      - CONSUMER_PRIORITY=MEDIUM
      - CONSUMER_RACK=us-east-1b
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '1.0'
```

### Example 2: Multi-AZ Rack-Aware Deployment

**Scenario:** Deploy consumers across availability zones with rack awareness.

```yaml
# AZ-1 Consumer
kafka:
  partitioning:
    consumer:
      assignment-strategy: RACK_AWARE
      metadata:
        rack: us-east-1a
        region: us-east
        capacity: 2000

# AZ-2 Consumer
kafka:
  partitioning:
    consumer:
      assignment-strategy: RACK_AWARE
      metadata:
        rack: us-east-1b
        region: us-east
        capacity: 2000
```

**Kubernetes Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka-consumer-az1
spec:
  replicas: 3
  template:
    spec:
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
            - matchExpressions:
              - key: topology.kubernetes.io/zone
                operator: In
                values: ["us-east-1a"]
      containers:
      - name: consumer
        env:
        - name: CONSUMER_RACK
          value: "us-east-1a"
```

### Example 3: Workload-Aware Specialized Processing

**Scenario:** Different consumers optimized for different message types.

```yaml
# Batch processing consumer
kafka:
  partitioning:
    consumer:
      assignment-strategy: WORKLOAD_AWARE
      metadata:
        capabilities: ["BATCH_PROCESSING", "JSON", "AVRO"]
        preferences: ["HIGH_THROUGHPUT", "BULK_OPERATIONS"]
        capacity: 10000
        rack: batch-rack

# Real-time processing consumer
kafka:
  partitioning:
    consumer:
      assignment-strategy: WORKLOAD_AWARE
      metadata:
        capabilities: ["REAL_TIME", "STREAM_PROCESSING", "JSON"]
        preferences: ["LOW_LATENCY", "SINGLE_MESSAGE"]
        capacity: 1000
        rack: realtime-rack
```

**Consumer Implementation:**
```java
@Service
@Profile("batch-processor")
public class BatchMessageProcessor {
    
    @KafkaListener(
        topics = "data-topic",
        containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void processBatch(List<DataMessage> messages) {
        // Optimized for batch processing
        processBulkData(messages);
    }
}

@Service
@Profile("realtime-processor")
public class RealtimeMessageProcessor {
    
    @KafkaListener(
        topics = "data-topic",
        containerFactory = "realtimeKafkaListenerContainerFactory"
    )
    public void processMessage(DataMessage message) {
        // Optimized for low-latency processing
        processRealtimeData(message);
    }
}
```

## Complete Configuration Examples

### Production Multi-Tenant SaaS Configuration

```yaml
# application-prod.yml
kafka:
  bootstrap-servers: kafka1:9092,kafka2:9092,kafka3:9092
  
  partitioning:
    producer:
      strategy: CUSTOMER_AWARE
      customer:
        hash-seed: ${CUSTOMER_HASH_SEED:54321}
        customer-id-field: tenantId
        enable-caching: true
        cache-size: 100000
      
    consumer:
      assignment-strategy: WORKLOAD_AWARE
      metadata:
        rack: ${DEPLOYMENT_RACK:default}
        priority: ${CONSUMER_PRIORITY:MEDIUM}
        capacity: ${CONSUMER_CAPACITY:2000}
        region: ${DEPLOYMENT_REGION:us-east}
        capabilities: ${CONSUMER_CAPABILITIES:JSON,AVRO}
        preferences: ${CONSUMER_PREFERENCES:BATCH_PROCESSING}
      
      rebalancing:
        max-rebalance-time: PT10M
        max-rebalance-retries: 5
        enable-eager-rebalancing: false
        enable-incremental-rebalancing: true
        stabilization-period: PT1M
        
      affinity:
        enable-sticky-assignment: true
        stickiness-factor: 0.9
        affinity-timeout: PT15M
        enable-cross-topic-affinity: false
    
    monitoring:
      enabled: true
      metrics-interval: PT15S
      reporting-interval: PT5M
      
      alerts:
        enabled: true
        skew-threshold: 2.5
        hot-partition-threshold: 1000
        alert-cooldown: PT5M
        alert-channels: ["LOG", "METRICS", "WEBHOOK"]
        
      metrics:
        enable-detailed-metrics: true
        enable-partition-level-metrics: true
        enable-consumer-level-metrics: true
        history-retention-seconds: 3600
        export-formats: ["PROMETHEUS", "JMX"]
```

### Development Environment Configuration

```yaml
# application-dev.yml
kafka:
  bootstrap-servers: localhost:9092
  
  partitioning:
    producer:
      strategy: ROUND_ROBIN  # Simple for development
      
    consumer:
      assignment-strategy: LOAD_BALANCED
      metadata:
        rack: dev-rack
        priority: MEDIUM
        capacity: 500
        region: dev
        
    monitoring:
      enabled: true
      metrics-interval: PT30S
      alerts:
        enabled: false  # Disable alerts in development
```

### Microservices Configuration

```yaml
# Order Service
kafka:
  partitioning:
    producer:
      strategy: CUSTOMER_AWARE
      customer:
        customer-id-field: customerId
    consumer:
      assignment-strategy: AFFINITY_BASED

# Payment Service  
kafka:
  partitioning:
    producer:
      strategy: PRIORITY
      priority:
        partition-ratio: 0.4
        priority-field: urgency
    consumer:
      assignment-strategy: PRIORITY_BASED
      metadata:
        priority: HIGH

# Analytics Service
kafka:
  partitioning:
    producer:
      strategy: TIME_BASED
      time-based:
        time-window: PT1H
    consumer:
      assignment-strategy: WORKLOAD_AWARE
      metadata:
        capabilities: ["BATCH_PROCESSING", "ANALYTICS"]
```

## Real-World Use Cases

### Use Case 1: Financial Trading Platform

**Requirements:**
- Ultra-low latency for market data
- High-priority order processing
- Risk management with geographic isolation

**Configuration:**
```yaml
kafka:
  partitioning:
    producer:
      strategy: PRIORITY
      priority:
        partition-ratio: 0.5
        priority-field: orderType
        high-priority-values: ["MARKET", "STOP_LOSS", "TAKE_PROFIT"]
        
    consumer:
      assignment-strategy: PRIORITY_BASED
      metadata:
        priority: HIGH
        capacity: 50000
        capabilities: ["LOW_LATENCY", "REAL_TIME"]
        
    monitoring:
      metrics-interval: PT1S  # 1-second monitoring
      alerts:
        skew-threshold: 1.2  # Very tight skew tolerance
```

### Use Case 2: IoT Sensor Data Processing

**Requirements:**
- Time-series data organization
- Geographic data locality
- Scalable batch processing

**Configuration:**
```yaml
kafka:
  partitioning:
    producer:
      strategy: TIME_BASED
      time-based:
        time-window: PT15M  # 15-minute windows
        time-field: sensorTimestamp
        
    consumer:
      assignment-strategy: GEOGRAPHIC
      metadata:
        region: ${SENSOR_REGION}
        capabilities: ["BATCH_PROCESSING", "TIME_SERIES"]
```

### Use Case 3: Multi-Tenant E-commerce Platform

**Requirements:**
- Tenant isolation
- Variable tenant sizes
- SLA differentiation

**Configuration:**
```yaml
kafka:
  partitioning:
    producer:
      strategy: CUSTOMER_AWARE
      customer:
        customer-id-field: tenantId
        enable-caching: true
        cache-size: 10000
        
    consumer:
      assignment-strategy: LOAD_BALANCED
      metadata:
        capacity: ${TENANT_CAPACITY:2000}
        priority: ${TENANT_TIER:MEDIUM}
```

**Tenant-Specific Configuration:**
```java
@Component
public class TenantConfigurationManager {
    
    @Value("${tenant.tier:STANDARD}")
    private String tenantTier;
    
    @PostConstruct
    public void configureTenant() {
        switch (tenantTier) {
            case "PREMIUM":
                System.setProperty("consumer.capacity", "5000");
                System.setProperty("consumer.priority", "HIGH");
                break;
            case "STANDARD":
                System.setProperty("consumer.capacity", "2000");
                System.setProperty("consumer.priority", "MEDIUM");
                break;
            case "BASIC":
                System.setProperty("consumer.capacity", "1000");
                System.setProperty("consumer.priority", "LOW");
                break;
        }
    }
}
```

## Performance Tuning

### Optimizing Producer Partitioning

1. **Monitor Partition Distribution:**
```bash
# Check for partition skew
curl -s http://localhost:8080/api/kafka/partitioning/distribution | \
    jq '.partitioners | to_entries[] | {topic: .key, skew: (.value.partitionDistribution | to_entries | map(.value) | (max / (add / length)))}'
```

2. **Tune Cache Sizes:**
```yaml
kafka:
  partitioning:
    producer:
      customer:
        cache-size: 50000  # Adjust based on unique customers
        enable-caching: true
```

3. **Optimize Hash Seeds:**
```yaml
kafka:
  partitioning:
    producer:
      customer:
        hash-seed: 12345  # Use prime numbers for better distribution
```

### Optimizing Consumer Assignment

1. **Capacity Tuning:**
```bash
# Monitor consumer utilization
curl -s http://localhost:8080/api/kafka/partitioning/metrics | \
    jq '.metrics.partitioning.utilization'

# Adjust capacity based on actual throughput
export CONSUMER_CAPACITY=3000
```

2. **Rebalancing Optimization:**
```yaml
kafka:
  partitioning:
    consumer:
      rebalancing:
        max-rebalance-time: PT5M
        enable-incremental-rebalancing: true
        stabilization-period: PT30S
```

3. **Sticky Assignment:**
```yaml
kafka:
  partitioning:
    consumer:
      affinity:
        enable-sticky-assignment: true
        stickiness-factor: 0.8
        affinity-timeout: PT10M
```

### Performance Monitoring

1. **Key Metrics to Track:**
```bash
# Partition skew
curl -s http://localhost:8080/actuator/metrics/kafka.partition.distribution.skew

# Consumer utilization
curl -s http://localhost:8080/actuator/metrics/kafka.consumer.utilization

# Assignment efficiency
curl -s http://localhost:8080/actuator/metrics/kafka.partition.assignment.efficiency
```

2. **Grafana Dashboard Queries:**
```promql
# Partition skew over time
kafka_partition_distribution_skew

# Consumer utilization by instance
kafka_consumer_utilization{instance=~".*"}

# Rebalancing frequency
rate(kafka_partition_rebalance_frequency_total[5m])
```

## Troubleshooting Guide

### Common Issues and Solutions

#### 1. High Partition Skew

**Symptoms:**
- Uneven message distribution
- Some consumers idle while others overloaded
- Poor overall throughput

**Diagnosis:**
```bash
curl -s http://localhost:8080/api/kafka/partitioning/recommendations | \
    jq '.recommendations[] | select(.type == "PARTITION_DISTRIBUTION")'
```

**Solutions:**
```yaml
# Switch to load-aware partitioning
kafka:
  partitioning:
    producer:
      strategy: LOAD_AWARE
      load-aware:
        load-threshold: 1000
        enable-hot-partition-avoidance: true
```

#### 2. Frequent Rebalancing

**Symptoms:**
- High rebalancing frequency
- Processing interruptions
- Poor assignment efficiency

**Diagnosis:**
```bash
curl -s http://localhost:8080/api/kafka/partitioning/metrics | \
    jq '.metrics.consumers.totalRebalances'
```

**Solutions:**
```yaml
kafka:
  partitioning:
    consumer:
      rebalancing:
        max-rebalance-time: PT10M
        stabilization-period: PT2M
      affinity:
        enable-sticky-assignment: true
        stickiness-factor: 0.9
```

#### 3. Low Consumer Utilization

**Symptoms:**
- Low CPU/memory usage on consumers
- High consumer lag despite low load
- Inefficient resource usage

**Diagnosis:**
```bash
curl -s http://localhost:8080/api/kafka/partitioning/recommendations | \
    jq '.recommendations[] | select(.type == "CONSUMER_UTILIZATION")'
```

**Solutions:**
```yaml
# Reduce consumer count or increase capacity
kafka:
  partitioning:
    consumer:
      metadata:
        capacity: 3000  # Increase from 2000
```

#### 4. Hot Partitions

**Symptoms:**
- One or few partitions receiving most traffic
- Uneven processing load
- Bottlenecks in specific partitions

**Diagnosis:**
```bash
curl -s http://localhost:8080/api/kafka/partitioning/distribution | \
    jq '.partitioners[].partitionDistribution | to_entries | sort_by(.value) | reverse'
```

**Solutions:**
```yaml
kafka:
  partitioning:
    producer:
      strategy: LOAD_AWARE
      load-aware:
        load-threshold: 500  # Lower threshold
        enable-hot-partition-avoidance: true
```

### Debug Mode Configuration

```yaml
# Enable debug logging
logging:
  level:
    com.company.kafka.partition: DEBUG
    org.apache.kafka.clients.producer.internals.DefaultPartitioner: DEBUG
    org.apache.kafka.clients.consumer.internals: DEBUG

# Enhanced monitoring
kafka:
  partitioning:
    monitoring:
      metrics-interval: PT5S
      enable-detailed-metrics: true
      enable-partition-level-metrics: true
```

### Health Check Script

```bash
#!/bin/bash

echo "=== Kafka Partitioning Health Check ==="

# Check overall health
health=$(curl -s http://localhost:8080/api/kafka/partitioning/health | jq -r '.health.overall')
echo "Overall Health: $health"

if [ "$health" != "HEALTHY" ]; then
    echo "=== Getting Recommendations ==="
    curl -s http://localhost:8080/api/kafka/partitioning/recommendations | \
        jq '.recommendations[] | {type: .type, severity: .severity, title: .title}'
        
    echo "=== Getting Detailed Metrics ==="
    curl -s http://localhost:8080/api/kafka/partitioning/metrics | \
        jq '.metrics.partitioning'
fi

echo "=== Health Check Complete ==="
```

This comprehensive guide provides practical examples and real-world configurations for implementing advanced Kafka partitioning strategies. Use these examples as starting points and adjust the configurations based on your specific requirements and performance characteristics.
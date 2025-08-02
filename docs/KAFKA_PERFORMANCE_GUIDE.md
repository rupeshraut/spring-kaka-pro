# Kafka Performance Benchmarking and Monitoring Guide

## 🎯 Performance Benchmarking

### Expected Performance Targets

| System Configuration | Expected Producer TPS | Expected Consumer TPS | Latency (p99) |
|---------------------|----------------------|---------------------|---------------|
| 8 cores, 16GB RAM | 25,000-50,000 | 30,000-60,000 | <100ms |
| 16 cores, 32GB RAM | 75,000-150,000 | 100,000-200,000 | <50ms |
| 32 cores, 64GB RAM | 200,000-500,000 | 300,000-800,000 | <20ms |

### Benchmarking Commands

#### Producer Benchmark
```bash
# Test 1: Basic throughput test
kafka-producer-perf-test.sh \
  --topic benchmark-topic \
  --num-records 1000000 \
  --record-size 1024 \
  --throughput 100000 \
  --producer-props bootstrap.servers=localhost:9092

# Test 2: High-throughput with compression
kafka-producer-perf-test.sh \
  --topic benchmark-topic \
  --num-records 5000000 \
  --record-size 512 \
  --throughput -1 \
  --producer-props \
    bootstrap.servers=localhost:9092 \
    batch.size=65536 \
    linger.ms=100 \
    compression.type=snappy \
    buffer.memory=134217728

# Test 3: Low-latency test
kafka-producer-perf-test.sh \
  --topic benchmark-topic \
  --num-records 100000 \
  --record-size 256 \
  --throughput 50000 \
  --producer-props \
    bootstrap.servers=localhost:9092 \
    batch.size=1024 \
    linger.ms=0 \
    acks=1
```

#### Consumer Benchmark
```bash
# Test 1: Basic consumption test
kafka-consumer-perf-test.sh \
  --topic benchmark-topic \
  --messages 1000000 \
  --threads 1 \
  --consumer-props bootstrap.servers=localhost:9092

# Test 2: Multi-threaded consumption
kafka-consumer-perf-test.sh \
  --topic benchmark-topic \
  --messages 5000000 \
  --threads 8 \
  --consumer-props \
    bootstrap.servers=localhost:9092 \
    max.poll.records=1000 \
    fetch.min.bytes=100000
```

## 📊 Monitoring Configuration

### Prometheus Configuration

```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'kafka-producer'
    static_configs:
      - targets: ['localhost:8081']
    metrics_path: /actuator/prometheus
    scrape_interval: 5s

  - job_name: 'kafka-consumer'
    static_configs:
      - targets: ['localhost:8082']
    metrics_path: /actuator/prometheus
    scrape_interval: 5s

  - job_name: 'kafka-broker'
    static_configs:
      - targets: ['localhost:9999']
    scrape_interval: 10s

  - job_name: 'node-exporter'
    static_configs:
      - targets: ['localhost:9100']
    scrape_interval: 15s
```

### Key Metrics to Monitor

#### Producer Metrics
```yaml
producer_metrics:
  throughput:
    - kafka_producer_record_send_rate
    - kafka_producer_byte_rate
    - kafka_producer_batch_size_avg
  
  latency:
    - kafka_producer_record_queue_time_avg
    - kafka_producer_record_send_time_avg
    - kafka_producer_request_latency_avg
  
  errors:
    - kafka_producer_record_error_rate
    - kafka_producer_record_retry_rate
  
  resource_usage:
    - kafka_producer_buffer_available_bytes
    - kafka_producer_buffer_exhausted_rate
```

#### Consumer Metrics
```yaml
consumer_metrics:
  throughput:
    - kafka_consumer_records_consumed_rate
    - kafka_consumer_bytes_consumed_rate
    - kafka_consumer_fetch_rate
  
  lag:
    - kafka_consumer_lag_sum
    - kafka_consumer_lag_max
    - kafka_consumer_records_lag_max
  
  latency:
    - kafka_consumer_fetch_latency_avg
    - kafka_consumer_commit_latency_avg
  
  processing:
    - kafka_listener_seconds_sum
    - kafka_listener_seconds_count
```

### Grafana Dashboard Configuration

#### Producer Dashboard JSON
```json
{
  "dashboard": {
    "title": "Kafka Producer Performance",
    "panels": [
      {
        "title": "Producer Throughput",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(kafka_producer_record_send_total[5m])",
            "legendFormat": "Records/sec"
          }
        ]
      },
      {
        "title": "Producer Latency",
        "type": "graph",
        "targets": [
          {
            "expr": "kafka_producer_record_send_time_avg",
            "legendFormat": "Send Time (ms)"
          }
        ]
      },
      {
        "title": "Buffer Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "kafka_producer_buffer_available_bytes",
            "legendFormat": "Available Bytes"
          }
        ]
      }
    ]
  }
}
```

#### Consumer Dashboard JSON
```json
{
  "dashboard": {
    "title": "Kafka Consumer Performance",
    "panels": [
      {
        "title": "Consumer Lag",
        "type": "graph",
        "targets": [
          {
            "expr": "kafka_consumer_lag_sum",
            "legendFormat": "Total Lag"
          }
        ],
        "alert": {
          "conditions": [
            {
              "query": {"queryType": "A"},
              "reducer": {"type": "last"},
              "evaluator": {
                "params": [1000],
                "type": "gt"
              }
            }
          ]
        }
      },
      {
        "title": "Consumption Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(kafka_consumer_records_consumed_total[5m])",
            "legendFormat": "Records/sec"
          }
        ]
      }
    ]
  }
}
```

## 🚨 Alerting Rules

### Prometheus Alerting Rules

```yaml
# kafka-alerts.yml
groups:
  - name: kafka.rules
    rules:
      # Producer Alerts
      - alert: HighProducerLatency
        expr: kafka_producer_record_send_time_avg > 100
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High producer latency detected"
          description: "Producer latency is {{ $value }}ms"

      - alert: ProducerBufferExhausted
        expr: kafka_producer_buffer_exhausted_rate > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Producer buffer exhausted"
          description: "Producer buffer is running out of space"

      # Consumer Alerts
      - alert: HighConsumerLag
        expr: kafka_consumer_lag_sum > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High consumer lag detected"
          description: "Consumer lag is {{ $value }} messages"

      - alert: CriticalConsumerLag
        expr: kafka_consumer_lag_sum > 100000
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Critical consumer lag"
          description: "Consumer lag is critically high: {{ $value }} messages"

      # System Alerts
      - alert: HighCPUUsage
        expr: 100 - (avg by(instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100) > 80
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage"
          description: "CPU usage is {{ $value }}%"

      - alert: HighMemoryUsage
        expr: (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100 > 85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage"
          description: "Memory usage is {{ $value }}%"
```

## 🔧 Performance Tuning Recommendations

### Based on Monitoring Results

#### If Producer Throughput is Low
```yaml
symptoms:
  - kafka_producer_record_send_rate < expected
  - kafka_producer_batch_size_avg is small
  
solutions:
  - Increase batch.size (current * 2)
  - Increase linger.ms (5-100ms)
  - Add compression (snappy/lz4)
  - Increase buffer.memory
  - Scale horizontally
```

#### If Consumer Lag is High
```yaml
symptoms:
  - kafka_consumer_lag_sum growing
  - kafka_consumer_records_consumed_rate < producer rate
  
solutions:
  - Increase max.poll.records
  - Increase fetch.min.bytes
  - Add more consumer instances
  - Optimize processing logic
  - Increase concurrency
```

#### If Latency is High
```yaml
symptoms:
  - kafka_producer_record_send_time_avg > 50ms
  - kafka_consumer_fetch_latency_avg > 10ms
  
solutions:
  - Reduce batch.size
  - Reduce linger.ms
  - Reduce fetch.min.bytes
  - Optimize network settings
  - Use faster storage
```

## 📈 Capacity Planning

### Growth Projection Formula

```bash
# Calculate future requirements
current_tps = 50000
growth_rate = 0.3  # 30% yearly growth
years = 2

future_tps = current_tps * (1 + growth_rate) ^ years
safety_factor = 1.5  # 50% safety margin

required_capacity = future_tps * safety_factor

# Example: 50,000 TPS with 30% growth over 2 years
# future_tps = 50,000 * (1.3)^2 = 84,500
# required_capacity = 84,500 * 1.5 = 126,750 TPS
```

### Resource Scaling Guidelines

```yaml
scaling_thresholds:
  cpu_utilization: 
    scale_up: 70%
    scale_down: 30%
  
  memory_utilization:
    scale_up: 80%
    scale_down: 40%
  
  consumer_lag:
    scale_up: 5000 messages
    alert: 10000 messages
  
  producer_latency:
    concern: 50ms
    critical: 100ms
```

## 🎯 Performance Testing Checklist

### Pre-Production Testing

```yaml
load_testing:
  - [ ] Baseline performance established
  - [ ] Peak load testing (150% of expected)
  - [ ] Sustained load testing (24+ hours)
  - [ ] Failure scenario testing
  - [ ] Network partition testing
  - [ ] Resource exhaustion testing

monitoring_validation:
  - [ ] All metrics collecting correctly
  - [ ] Alerts triggering appropriately
  - [ ] Dashboards displaying accurate data
  - [ ] Log aggregation working
  - [ ] Tracing configured

capacity_planning:
  - [ ] Growth projections calculated
  - [ ] Scaling triggers defined
  - [ ] Resource limits tested
  - [ ] Cost projections completed
```

This comprehensive monitoring and benchmarking guide ensures you can measure, optimize, and scale your high-throughput Kafka deployment effectively! 📊

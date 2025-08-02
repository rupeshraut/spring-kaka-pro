# Kafka Property Sizing Guide

This guide provides comprehensive recommendations for sizing Kafka properties based on different use cases and throughput requirements.

## Quick Reference: Sizing Categories

| Use Case | Throughput | Messages/sec | Message Size | Partitions | Recommended Profile |
|----------|------------|--------------|--------------|------------|-------------------|
| **Development** | Low | < 1K | Any | 1-3 | [Development Profile](#development-profile) |
| **Small Production** | Medium | 1K-10K | < 1MB | 3-6 | [Small Production Profile](#small-production-profile) |
| **High Throughput** | High | 10K-100K | < 1MB | 6-12 | [High Throughput Profile](#high-throughput-profile) |
| **Large Scale** | Very High | > 100K | Any | 12+ | [Large Scale Profile](#large-scale-profile) |

## 🔧 Core Property Sizing Guidelines

### Producer Properties

#### **Batch Size (`batchSize`)**
```yaml
# Purpose: Number of bytes to collect before sending batch
# Sizing Formula: batchSize = (average_message_size * messages_per_batch)

# Development
batch-size: 1024        # 1KB - immediate sending for testing

# Small Production  
batch-size: 16384       # 16KB - balance latency vs throughput

# High Throughput
batch-size: 65536       # 64KB - maximize throughput

# Large Scale
batch-size: 131072      # 128KB - maximum batching efficiency
```

#### **Linger Time (`lingerMs`)**
```yaml
# Purpose: Time to wait for additional messages before sending batch
# Trade-off: Higher values = better throughput, higher latency

# Low Latency Requirements
linger-ms: 0            # Send immediately

# Balanced (Recommended)
linger-ms: 20           # 20ms - good balance

# High Throughput
linger-ms: 100          # 100ms - prioritize throughput

# Bulk Processing
linger-ms: 1000         # 1 second - maximum batching
```

#### **Buffer Memory (`bufferMemory`)**
```yaml
# Purpose: Total memory for buffering unsent messages
# Sizing Formula: bufferMemory = batchSize * partitions * buffer_multiplier

# Development
buffer-memory: 33554432    # 32MB

# Small Production
buffer-memory: 67108864    # 64MB (default)

# High Throughput  
buffer-memory: 134217728   # 128MB

# Large Scale
buffer-memory: 268435456   # 256MB or more
```

#### **Request Timeout (`requestTimeout`)**
```yaml
# Purpose: Maximum time to wait for response from broker
# Sizing: Based on network latency and broker load

# Local/Fast Network
request-timeout: 10s

# Standard Network (Recommended)
request-timeout: 30s

# Slow Network/High Load
request-timeout: 60s

# Cross-Region/Unreliable Network
request-timeout: 120s
```

### Consumer Properties

#### **Max Poll Records (`maxPollRecords`)**
```yaml
# Purpose: Maximum number of records returned in single poll
# Sizing Formula: maxPollRecords = processing_time_budget / avg_processing_time_per_message

# Fast Processing (< 1ms per message)
max-poll-records: 1000

# Standard Processing (1-10ms per message)  
max-poll-records: 500

# Slow Processing (10-100ms per message)
max-poll-records: 100

# Very Slow Processing (> 100ms per message)
max-poll-records: 10
```

#### **Max Poll Interval (`maxPollInterval`)**
```yaml
# Purpose: Maximum time between polls before consumer is kicked out
# Sizing Formula: maxPollInterval > maxPollRecords * avg_processing_time_per_message

# Fast Processing
max-poll-interval: 5m      # 5 minutes (default)

# Standard Processing
max-poll-interval: 10m     # 10 minutes

# Slow Processing (batch jobs, ML inference)
max-poll-interval: 30m     # 30 minutes

# Very Slow Processing
max-poll-interval: 60m     # 1 hour
```

#### **Fetch Min Bytes (`fetchMinBytes`)**
```yaml
# Purpose: Minimum bytes to fetch before returning response
# Trade-off: Higher values = better throughput, higher latency

# Low Latency
fetch-min-bytes: 1        # Return immediately with any data

# Balanced (Recommended)
fetch-min-bytes: 50000    # 50KB

# High Throughput
fetch-min-bytes: 100000   # 100KB

# Maximum Batching
fetch-min-bytes: 1048576  # 1MB
```

#### **Session Timeout (`sessionTimeout`)**
```yaml
# Purpose: Time before consumer is considered dead
# Constraint: Must be between 6s and 300s (broker defaults)

# Fast Failure Detection
session-timeout: 10s

# Standard (Recommended)
session-timeout: 30s

# Slow/Unreliable Network
session-timeout: 60s

# Very Unreliable Network  
session-timeout: 120s
```

### Topic Properties

#### **Partitions**
```yaml
# Purpose: Parallelism and scalability
# Sizing Factors: Consumer count, throughput, ordering requirements

# Sizing Formula: partitions = max(consumer_count, target_throughput / partition_throughput)

# Development
partitions: 1             # Simple testing

# Small Production
partitions: 3             # Basic parallelism

# Standard Production
partitions: 6             # Good balance

# High Throughput
partitions: 12            # High parallelism

# Very High Throughput
partitions: 24-48         # Maximum parallelism

# Guidelines:
# - More partitions = more parallelism but higher overhead
# - Consider consumer group size (consumers <= partitions for optimal usage)
# - Each partition can handle ~10-100 MB/s depending on message size
```

#### **Replication Factor**
```yaml
# Purpose: Data durability and availability
# Trade-off: Higher replication = better durability, more storage

# Development/Testing
replication-factor: 1     # No redundancy (not for production)

# Production (Minimum)
replication-factor: 2     # Can tolerate 1 broker failure

# Production (Recommended)
replication-factor: 3     # Can tolerate 2 broker failures

# Critical Systems
replication-factor: 5     # Maximum durability (rarely needed)
```

## 📊 Environment-Specific Profiles

### Development Profile
```yaml
kafka:
  producer:
    batch-size: 1024
    linger-ms: 0
    buffer-memory: 33554432
    request-timeout: 10s
    delivery-timeout: 30s
    
  consumer:
    max-poll-records: 100
    max-poll-interval: 5m
    session-timeout: 10s
    fetch-min-bytes: 1
    concurrency: 1
    
  topics:
    orders:
      partitions: 1
      replication-factor: 1
```

### Small Production Profile
```yaml
kafka:
  producer:
    batch-size: 16384
    linger-ms: 20
    buffer-memory: 67108864
    request-timeout: 30s
    delivery-timeout: 120s
    
  consumer:
    max-poll-records: 500
    max-poll-interval: 10m
    session-timeout: 30s
    fetch-min-bytes: 50000
    concurrency: 3
    
  topics:
    orders:
      partitions: 3
      replication-factor: 3
```

### High Throughput Profile
```yaml
kafka:
  producer:
    batch-size: 65536
    linger-ms: 100
    buffer-memory: 134217728
    request-timeout: 30s
    delivery-timeout: 300s
    
  consumer:
    max-poll-records: 1000
    max-poll-interval: 15m
    session-timeout: 45s
    fetch-min-bytes: 100000
    concurrency: 6
    
  topics:
    orders:
      partitions: 12
      replication-factor: 3
```

### Large Scale Profile
```yaml
kafka:
  producer:
    batch-size: 131072
    linger-ms: 200
    buffer-memory: 268435456
    request-timeout: 60s
    delivery-timeout: 600s
    
  consumer:
    max-poll-records: 2000
    max-poll-interval: 30m
    session-timeout: 60s
    fetch-min-bytes: 1048576
    concurrency: 12
    
  topics:
    orders:
      partitions: 24
      replication-factor: 3
```

## 🎯 Sizing Decision Framework

### Step 1: Determine Your Use Case
1. **Message Volume**: How many messages per second?
2. **Message Size**: Average and maximum message size?
3. **Latency Requirements**: Real-time vs batch processing?
4. **Durability Requirements**: Can you afford to lose messages?
5. **Consumer Count**: How many consumer instances?

### Step 2: Calculate Key Metrics
```
# Throughput Calculation
partition_throughput = 10MB/s (typical)
required_partitions = target_throughput / partition_throughput

# Buffer Size Calculation  
buffer_memory = batch_size * partitions * 2 (safety factor)

# Poll Configuration
max_poll_records = poll_interval_budget / avg_processing_time_per_message
```

### Step 3: Start with Profile and Tune
1. Start with the closest profile from above
2. Monitor key metrics (lag, throughput, latency)
3. Adjust based on actual performance
4. Load test with realistic data

## 📈 Monitoring for Right-Sizing

### Key Metrics to Watch
```yaml
# Producer Metrics
- producer.batch-size-avg          # Actual average batch size
- producer.batch-size-max          # Maximum batch size used
- producer.buffer-available-bytes  # Available buffer space
- producer.request-latency-avg     # Request latency

# Consumer Metrics  
- consumer.lag-sum                 # Total consumer lag
- consumer.records-consumed-rate   # Records per second
- consumer.fetch-latency-avg       # Fetch latency
- consumer.commit-latency-avg      # Commit latency

# Topic Metrics
- partition.size                   # Partition size on disk
- partition.offset-lag             # Offset lag per partition
```

### Tuning Indicators
```yaml
# Scale UP if:
- Consumer lag is consistently growing
- Producer buffer is frequently full
- High request latencies
- Low batch sizes despite high linger time

# Scale DOWN if:  
- Over-provisioned resources
- Very low utilization
- Unnecessary latency from large batches
- Too many idle partitions
```

## 🚨 Common Sizing Mistakes

### ❌ Avoid These Pitfalls

1. **Too Many Partitions**
   - More partitions ≠ always better performance
   - Each partition has overhead
   - Rebalancing takes longer with more partitions

2. **Buffer Memory Too Small**
   - Causes producer blocking
   - Reduces throughput significantly
   - Should be >> batch_size * partitions

3. **Poll Interval Too Short**
   - Causes frequent rebalancing
   - Increases consumer group instability
   - Should match actual processing time needs

4. **Fetch Min Bytes Too High**
   - Causes unnecessary latency
   - Should balance throughput vs latency needs
   - Consider message volume patterns

## 🔄 Performance Testing Approach

### 1. Baseline Testing
```bash
# Test with minimal settings first
kafka-producer-perf-test --topic test-topic \
  --num-records 100000 \
  --record-size 1024 \
  --throughput 1000

kafka-consumer-perf-test --topic test-topic \
  --messages 100000 \
  --threads 1
```

### 2. Incremental Scaling
- Start with development profile
- Gradually increase batch sizes and buffers
- Monitor impact on latency and throughput
- Find optimal balance for your use case

### 3. Load Testing
- Use realistic message patterns
- Test with multiple producers/consumers
- Include error scenarios and recovery
- Monitor resource utilization

Remember: **Start conservative, measure, then optimize!** 📊

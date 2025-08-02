# Kafka Message Size Measurement Guide

This comprehensive guide shows you how to measure and monitor Kafka message sizes in your Spring Boot application.

## Overview

Message size is a critical factor in Kafka performance. This guide provides:

- **Utility classes** for accurate size calculation
- **Monitoring services** with automatic size tracking
- **Metrics collection** with Micrometer integration
- **REST API** for testing and monitoring
- **Best practices** for size optimization

## Quick Start

### 1. Calculate Message Size

```java
@Autowired
private MessageSizeCalculator messageSizeCalculator;

// Calculate size of a producer record
ProducerRecord<String, Object> record = new ProducerRecord<>("topic", "key", message);
long size = messageSizeCalculator.calculateProducerRecordSize(record);

// Calculate size of a consumer record
long size = messageSizeCalculator.calculateConsumerRecordSize(consumerRecord);

// Calculate just the value size
long valueSize = messageSizeCalculator.calculateValueSize(messageObject);
```

### 2. Monitor Messages with Size Tracking

```java
@Autowired
private MessageSizeMonitoringService monitoringService;

// Send message with automatic size monitoring
monitoringService.sendMessageWithSizeMonitoring("topic", "key", message);

// Consumer automatically tracks sizes via @KafkaListener
```

### 3. Collect Metrics

```java
@Autowired
private MessageSizeMetrics metrics;

// Record sizes for metrics
metrics.recordProducerMessageSize(sizeBytes);
metrics.recordConsumerMessageSize(sizeBytes);

// Get statistics
MessageSizeMetrics.MessageSizeMetricsSummary summary = metrics.getSummary();
```

## Detailed Usage

### Message Size Calculator

The `MessageSizeCalculator` provides comprehensive size calculation:

```java
// Producer record with headers
ProducerRecord<String, Object> record = new ProducerRecord<>("user-events", "user-123", userEvent);
record.headers().add("correlation-id", correlationId.getBytes());
record.headers().add("source", "user-service".getBytes());

long totalSize = messageSizeCalculator.calculateProducerRecordSize(record);
// Returns: key size + value size + headers size + Kafka overhead

// Break down the size
long keySize = record.key().getBytes(StandardCharsets.UTF_8).length;
long valueSize = messageSizeCalculator.calculateValueSize(record.value());
long headersSize = messageSizeCalculator.calculateHeadersSize(record.headers());
long overhead = messageSizeCalculator.calculateKafkaOverhead();

System.out.printf("Total: %d bytes (key: %d, value: %d, headers: %d, overhead: %d)%n",
        totalSize, keySize, valueSize, headersSize, overhead);
```

### Size-Based Processing

```java
@KafkaListener(topics = "user-events")
public void processMessage(ConsumerRecord<String, Object> record, Acknowledgment ack) {
    long messageSize = messageSizeCalculator.calculateConsumerRecordSize(record);
    
    if (messageSize < 1024) {
        // Fast processing for small messages
        processSmallMessage(record.value());
    } else if (messageSize < 100 * 1024) {
        // Standard processing for medium messages
        processMediumMessage(record.value());
    } else {
        // Careful processing for large messages
        processLargeMessage(record.value(), messageSize);
    }
    
    ack.acknowledge();
}
```

### Batch Size Monitoring

```java
@KafkaListener(topics = "high-volume-events", containerFactory = "batchListenerFactory")
public void processBatch(List<ConsumerRecord<String, Object>> records, Acknowledgment ack) {
    long totalBatchSize = 0;
    
    for (ConsumerRecord<String, Object> record : records) {
        long messageSize = messageSizeCalculator.calculateConsumerRecordSize(record);
        totalBatchSize += messageSize;
    }
    
    // Record batch metrics
    messageSizeMetrics.recordBatchSize(totalBatchSize, records.size());
    
    log.info("Processing batch: {} messages, {} total size", 
            records.size(), 
            messageSizeCalculator.formatSize(totalBatchSize));
    
    // Process batch...
    ack.acknowledge();
}
```

## REST API Testing

### Send Test Messages

```bash
# Send a test message
curl -X POST http://localhost:8080/api/message-size/test/send \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "test-topic",
    "key": "test-key-1",
    "message": {"id": 1, "name": "Test User", "data": "sample data"}
  }'
```

### Generate Messages of Specific Size

```bash
# Generate 10 messages of 1KB each
curl -X POST "http://localhost:8080/api/message-size/test/generate/1024?count=10&topic=test-topic"
```

### Get Size Statistics

```bash
# Get formatted statistics
curl http://localhost:8080/api/message-size/stats/formatted
```

### Calculate Message Size

```bash
# Calculate size for a complex object
curl -X POST http://localhost:8080/api/message-size/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 12345,
    "profile": {
      "name": "John Doe",
      "email": "john@example.com",
      "preferences": ["email", "sms"],
      "metadata": {
        "created": "2024-01-01T00:00:00Z",
        "updated": "2024-01-15T10:30:00Z"
      }
    }
  }'
```

## Size Categories and Thresholds

### Message Size Categories

```java
public enum MessageSizeCategory {
    SMALL(0, 1024),           // 0 - 1KB: Fast processing
    MEDIUM(1024, 102400),     // 1KB - 100KB: Standard processing  
    LARGE(102400, 1048576),   // 100KB - 1MB: Careful processing
    OVERSIZED(1048576, Long.MAX_VALUE); // > 1MB: Special handling
    
    private final long minSize;
    private final long maxSize;
}
```

### Processing Recommendations

| Size Category | Recommendation | Processing Strategy |
|--------------|---------------|-------------------|
| **Small (< 1KB)** | Consider batching | In-memory processing |
| **Medium (1KB-100KB)** | Optimal size range | Standard processing |
| **Large (100KB-1MB)** | Enable compression | Stream processing |
| **Oversized (> 1MB)** | Split or use external storage | Chunked processing |

## Metrics and Monitoring

### Available Metrics

```yaml
# Message size distributions
kafka.message.size.producer: Size of outbound messages
kafka.message.size.consumer: Size of inbound messages
kafka.batch.size: Size of message batches

# Processing time by size
kafka.message.processing.time{size_category="small"}: Small message processing time
kafka.message.processing.time{size_category="medium"}: Medium message processing time
kafka.message.processing.time{size_category="large"}: Large message processing time

# Message counts by category
kafka.messages.count{size_category="small"}: Count of small messages
kafka.messages.count{size_category="medium"}: Count of medium messages
kafka.messages.count{size_category="large"}: Count of large messages
kafka.messages.count{size_category="oversized"}: Count of oversized messages

# Throughput metrics
kafka.throughput.bytes_per_second: Processing throughput
```

### Grafana Dashboard Queries

```promql
# Average message size trend
rate(kafka_message_size_producer_sum[5m]) / rate(kafka_message_size_producer_count[5m])

# Message size distribution
histogram_quantile(0.95, kafka_message_size_producer_bucket)

# Processing time by size category
kafka_message_processing_time{size_category="large"}

# Oversized message rate
rate(kafka_messages_count{size_category="oversized"}[5m])
```

## Size Optimization Strategies

### 1. Message Compression

```yaml
# Producer configuration
spring:
  kafka:
    producer:
      compression-type: snappy  # or lz4, gzip, zstd
      properties:
        compression.type: snappy
```

### 2. Field Selection

```java
// Use DTOs with only necessary fields
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserEventDto(
    @JsonProperty("id") Long userId,
    @JsonProperty("action") String action,
    @JsonProperty("ts") Instant timestamp
    // Exclude heavy fields like full profile data
) {}
```

### 3. External Storage Pattern

```java
public class LargeMessageHandler {
    
    public void sendLargeMessage(String topic, Object largeMessage) {
        long size = messageSizeCalculator.calculateValueSize(largeMessage);
        
        if (size > 1024 * 1024) { // > 1MB
            // Store in external storage (S3, etc.)
            String storageKey = storeInExternalStorage(largeMessage);
            
            // Send reference message
            MessageReference reference = new MessageReference(storageKey, size);
            kafkaTemplate.send(topic, reference);
        } else {
            // Send directly
            kafkaTemplate.send(topic, largeMessage);
        }
    }
}
```

### 4. Message Splitting

```java
public class MessageSplitter {
    
    public void sendLargeMessageInChunks(String topic, byte[] largePayload) {
        int chunkSize = 64 * 1024; // 64KB chunks
        String messageId = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil((double) largePayload.length / chunkSize);
        
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, largePayload.length);
            byte[] chunk = Arrays.copyOfRange(largePayload, start, end);
            
            MessageChunk chunkMessage = new MessageChunk(
                messageId, i, totalChunks, chunk
            );
            
            kafkaTemplate.send(topic, chunkMessage);
        }
    }
}
```

## Performance Impact

### Size vs Performance Characteristics

| Message Size | Throughput | Latency | Memory Usage | Network Usage |
|-------------|-----------|---------|--------------|---------------|
| **< 1KB** | Very High | Very Low | Low | Low |
| **1KB-10KB** | High | Low | Medium | Medium |
| **10KB-100KB** | Medium | Medium | Medium | High |
| **> 100KB** | Low | High | High | Very High |

### Optimization Guidelines

```java
// Pro Tips for message size optimization:

// 1. Measure before optimizing
long baseline = messageSizeCalculator.calculateValueSize(message);

// 2. Use appropriate serialization
// JSON: Good for debugging, larger size
// Avro: Compact, schema evolution
// Protobuf: Very compact, type safety

// 3. Consider compression trade-offs
// CPU overhead vs network bandwidth savings

// 4. Monitor size trends
messageSizeMetrics.recordProducerMessageSize(messageSize);

// 5. Set appropriate size limits
if (messageSize > kafkaProperties.getMaxMessageSize()) {
    throw new MessageTooLargeException("Message size exceeds limit");
}
```

## Troubleshooting

### Common Size-Related Issues

1. **Messages Too Large**
   ```
   Error: org.apache.kafka.common.errors.RecordTooLargeException
   Solution: Increase max.request.size or split messages
   ```

2. **Poor Performance with Large Messages**
   ```
   Symptoms: High latency, memory pressure
   Solution: Enable compression, use external storage
   ```

3. **Memory Issues with Batching**
   ```
   Symptoms: OutOfMemoryError in consumers
   Solution: Reduce batch size, process in streams
   ```

### Size Monitoring Alerts

```yaml
# Prometheus alerting rules
groups:
  - name: kafka_message_size
    rules:
      - alert: OversizedMessages
        expr: rate(kafka_messages_count{size_category="oversized"}[5m]) > 0.1
        for: 5m
        annotations:
          summary: "High rate of oversized messages detected"
          
      - alert: AverageMessageSizeTooLarge
        expr: kafka_message_size_producer > 102400  # 100KB
        for: 10m
        annotations:
          summary: "Average message size is too large"
```

## Best Practices

### 1. **Measure Early and Often**
- Calculate sizes during development
- Monitor size trends in production
- Set up alerts for size thresholds

### 2. **Choose Appropriate Limits**
```java
@ConfigurationProperties(prefix = "app.kafka.message-size")
public record MessageSizeLimits(
    @Min(1024) long smallThreshold,        // 1KB
    @Min(10240) long mediumThreshold,      // 10KB
    @Min(102400) long largeThreshold,      // 100KB
    @Min(1048576) long maxAllowedSize      // 1MB
) {}
```

### 3. **Implement Size-Based Routing**
```java
@Component
public class SizeBasedRouter {
    
    public String selectTopic(Object message, long size) {
        if (size < 1024) {
            return "small-messages";
        } else if (size < 100 * 1024) {
            return "medium-messages";
        } else {
            return "large-messages";
        }
    }
}
```

### 4. **Use Appropriate Serialization**
```java
// Size comparison for same data:
// JSON: ~500 bytes
// Avro: ~200 bytes  
// Protobuf: ~150 bytes
// MessagePack: ~180 bytes
```

This comprehensive guide provides everything you need to measure, monitor, and optimize Kafka message sizes in your Spring Boot application. The included utilities handle all the complexity while providing detailed insights into your message patterns.

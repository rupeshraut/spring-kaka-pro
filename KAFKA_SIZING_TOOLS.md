# Kafka Property Sizing Tools

This directory contains comprehensive tools to help you calculate optimal Kafka property values for your specific use case. Choose the method that works best for you:

## 🎯 Quick Start

### 1. **Web Interface** (Recommended for beginners)
The easiest way to get sizing recommendations with a user-friendly form:

```bash
# Enable the web calculator
echo "kafka.sizing.web.enabled=true" >> src/main/resources/application.yml

# Start the application
./gradlew bootRun

# Open your browser to
open http://localhost:8080/kafka-sizing
```

### 2. **Command Line Calculator**
For developers who prefer command-line tools:

```bash
# Interactive mode - guided questions
./kafka-sizing.sh -i

# Direct parameters
./kafka-sizing.sh --messages-per-second 5000 --message-size 2048 --low-latency true

# See all options
./kafka-sizing.sh --help
```

### 3. **Programmatic Calculator**
For integration into your build process or automation:

```bash
# Set environment variables
export KAFKA_SIZING_MESSAGES_PER_SECOND=5000
export KAFKA_SIZING_MESSAGE_SIZE=2048
export KAFKA_SIZING_LOW_LATENCY=true

# Run the calculator
./gradlew bootRun --args="--kafka.sizing.enabled=true"
```

## 📊 What You'll Get

All tools provide the same comprehensive recommendations:

### Producer Settings
- **Batch Size**: Optimal batching for your throughput
- **Linger Time**: Balance between latency and efficiency
- **Buffer Memory**: Memory allocation for producer buffering
- **Acknowledgments**: Durability vs. performance trade-off
- **Compression**: Best compression algorithm for your data

### Consumer Settings
- **Max Poll Records**: Optimal batch size for processing
- **Poll Intervals**: Timeout configuration for your processing time
- **Session Timeout**: Failover detection timing
- **Fetch Settings**: Network efficiency optimization
- **Concurrency**: Parallel processing recommendations

### Topic Configuration
- **Partitions**: Based on throughput and consumer count
- **Replication Factor**: Durability requirements

### Ready-to-Use YAML
```yaml
kafka:
  producer:
    batch-size: 32768
    linger-ms: 20
    buffer-memory: 134217728
    acks: "all"
    compression-type: "snappy"
  consumer:
    max-poll-records: 500
    max-poll-interval: 600000ms
    session-timeout: 30000ms
    fetch-min-bytes: 100000
    concurrency: 2
```

## 📚 Additional Resources

### Comprehensive Documentation
- **[KAFKA_SIZING_GUIDE.md](../KAFKA_SIZING_GUIDE.md)**: Complete guide with formulas and best practices
- **[application-sizing-examples.yml](../application-sizing-examples.yml)**: Pre-configured profiles for common scenarios

### Environment-Specific Profiles
The sizing guide includes ready-to-use configurations for:
- **Development**: Local testing and development
- **Small Production**: Low-volume production systems
- **High Throughput**: High-volume message processing
- **Large Scale**: Enterprise-level deployments
- **Low Latency**: Real-time applications
- **Bulk Processing**: Batch data processing
- **Resource Constrained**: Limited memory/CPU environments

## 🔧 Customization

### Input Parameters
All tools accept these key parameters:

| Parameter | Description | Default | Range |
|-----------|-------------|---------|--------|
| `messages-per-second` | Expected peak throughput | 1000 | 1 - 1,000,000 |
| `message-size` | Average message size in bytes | 1024 | 1 - 10MB |
| `processing-time` | Time to process each message (ms) | 10 | 1 - 60,000 |
| `consumer-count` | Number of consumer instances | 3 | 1 - 100 |
| `low-latency` | Optimize for low latency | false | true/false |
| `high-durability` | Require strong consistency | true | true/false |

### Optimization Profiles

**Low Latency Profile:**
- Minimizes batching
- Reduces linger time to 0
- Uses minimal compression
- Optimizes for sub-100ms response times

**High Throughput Profile:**
- Maximizes batching efficiency
- Uses compression (snappy)
- Optimizes buffer sizes
- Balances latency for maximum throughput

**High Durability Profile:**
- Requires all replicas to acknowledge
- Increases replication factor
- Enables idempotence
- Prioritizes data safety over speed

## ⚡ Quick Examples

### High-Volume E-commerce
```bash
./kafka-sizing.sh --messages-per-second 10000 --message-size 512 --consumer-count 6
```

### Real-time Trading
```bash
./kafka-sizing.sh --messages-per-second 50000 --message-size 256 --low-latency true
```

### Batch Analytics
```bash
./kafka-sizing.sh --messages-per-second 1000 --message-size 8192 --processing-time 100
```

### IoT Sensor Data
```bash
./kafka-sizing.sh --messages-per-second 5000 --message-size 128 --high-durability false
```

## 🎯 Best Practices

### 1. Start with Calculator Results
Use the tools to get baseline recommendations, then fine-tune based on monitoring.

### 2. Test with Real Data
Always validate recommendations with your actual data patterns and load.

### 3. Monitor Key Metrics
- Consumer lag
- Producer throughput
- Memory usage
- Error rates

### 4. Iterate and Optimize
Performance tuning is an iterative process. Start conservative and optimize based on metrics.

### 5. Environment-Specific Tuning
Development, staging, and production often need different configurations.

## 🚀 Integration Tips

### CI/CD Pipeline
```bash
# Generate configuration as part of deployment
./kafka-sizing.sh --messages-per-second $EXPECTED_TPS --message-size $AVG_MSG_SIZE > config/kafka-sizing.yml
```

### Docker Environment
```dockerfile
# Add calculator to your Docker image
COPY kafka-sizing.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/kafka-sizing.sh
```

### Kubernetes ConfigMap
```bash
# Generate ConfigMap from sizing results
kubectl create configmap kafka-config --from-file=kafka-sizing.yml
```

## 📞 Need Help?

1. **Check the complete sizing guide**: `KAFKA_SIZING_GUIDE.md`
2. **Review example configurations**: `application-sizing-examples.yml`
3. **Run the web interface**: Most user-friendly option
4. **Use interactive mode**: `./kafka-sizing.sh -i`

Remember: These tools provide starting points. Always test and monitor your specific use case!

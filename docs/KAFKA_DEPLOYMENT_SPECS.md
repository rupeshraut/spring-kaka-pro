# High-Throughput Kafka Deployment Configurations

This file contains specific server configurations for different throughput scenarios.

## 🚀 Ready-to-Deploy Server Specifications

### **Scenario 1: E-commerce Platform (50K messages/second)**

#### Server Configuration
```yaml
server_specs:
  cpu: "Intel Xeon Gold 6248 (20 cores, 2.5GHz)"
  memory: "64 GB DDR4-3200"
  storage: "2TB NVMe SSD (Samsung 980 PRO)"
  network: "10 Gbps Ethernet"
  os: "Ubuntu 22.04 LTS"

estimated_cost:
  aws_equivalent: "c5.5xlarge ($0.85/hour)"
  azure_equivalent: "F20s_v2 ($0.845/hour)"
  bare_metal: "$8,000-12,000 initial cost"
```

#### Application JVM Settings
```bash
# Producer JVM Configuration
JAVA_OPTS_PRODUCER="-Xms16g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:G1HeapRegionSize=16m -XX:+AlwaysPreTouch -XX:+DisableExplicitGC"

# Consumer JVM Configuration  
JAVA_OPTS_CONSUMER="-Xms24g -Xmx24g -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=16m -XX:+AlwaysPreTouch -XX:+DisableExplicitGC"
```

#### Kafka Configuration
```yaml
kafka_config:
  producer:
    batch_size: 65536
    linger_ms: 100
    buffer_memory: 134217728
    compression_type: "snappy"
    acks: "1"
    
  consumer:
    max_poll_records: 1000
    fetch_min_bytes: 100000
    max_poll_interval_ms: 900000
    concurrency: 12
```

---

### **Scenario 2: Financial Trading (200K messages/second)**

#### Server Configuration
```yaml
server_specs:
  cpu: "Intel Xeon Platinum 8280 (28 cores, 2.7GHz)"
  memory: "128 GB DDR4-3200"
  storage: "4TB NVMe SSD RAID 0 (Intel Optane)"
  network: "25 Gbps Ethernet with RDMA"
  os: "RHEL 8.6 with RT kernel"

estimated_cost:
  aws_equivalent: "c5n.9xlarge ($1.944/hour)"
  azure_equivalent: "F32s_v2 ($1.35/hour)"
  bare_metal: "$15,000-25,000 initial cost"
```

#### Application JVM Settings
```bash
# Producer JVM Configuration (Low Latency)
JAVA_OPTS_PRODUCER="-Xms32g -Xmx32g -XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+UseTransparentHugePages"

# Consumer JVM Configuration
JAVA_OPTS_CONSUMER="-Xms48g -Xmx48g -XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:G1HeapRegionSize=32m -XX:+AlwaysPreTouch -XX:+DisableExplicitGC"
```

#### Kafka Configuration
```yaml
kafka_config:
  producer:
    batch_size: 131072
    linger_ms: 10  # Lower for trading
    buffer_memory: 268435456
    compression_type: "lz4"
    acks: "1"
    
  consumer:
    max_poll_records: 2000
    fetch_min_bytes: 1048576
    max_poll_interval_ms: 600000
    concurrency: 20
```

---

### **Scenario 3: IoT Data Ingestion (1M messages/second)**

#### Server Configuration
```yaml
server_specs:
  cpu: "AMD EPYC 7742 (64 cores, 2.25GHz)"
  memory: "256 GB DDR4-3200"
  storage: "8TB NVMe SSD RAID 0 (Multiple drives)"
  network: "100 Gbps Ethernet"
  os: "Ubuntu 22.04 LTS with custom kernel"

estimated_cost:
  aws_equivalent: "c5n.18xlarge ($3.888/hour)"
  azure_equivalent: "HB120rs_v2 ($3.60/hour)"
  bare_metal: "$30,000-50,000 initial cost"
```

#### Application JVM Settings
```bash
# Producer JVM Configuration (Ultra High Throughput)
JAVA_OPTS_PRODUCER="-Xms64g -Xmx64g -XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+UseTransparentHugePages -XX:+UseLargePages"

# Consumer JVM Configuration
JAVA_OPTS_CONSUMER="-Xms96g -Xmx96g -XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+UseTransparentHugePages"
```

#### Kafka Configuration
```yaml
kafka_config:
  producer:
    batch_size: 262144  # 256KB
    linger_ms: 50
    buffer_memory: 536870912  # 512MB
    compression_type: "lz4"
    acks: "1"
    
  consumer:
    max_poll_records: 5000
    fetch_min_bytes: 2097152  # 2MB
    max_poll_interval_ms: 1800000  # 30 minutes
    concurrency: 32
```

## 🖥️ Container Deployment Configurations

### **Docker Compose for High Throughput**

```yaml
version: '3.8'
services:
  kafka-producer:
    image: your-producer:latest
    deploy:
      resources:
        limits:
          cpus: '16.0'
          memory: 32G
        reservations:
          cpus: '8.0'
          memory: 16G
    environment:
      - JAVA_OPTS=-Xms16g -Xmx32g -XX:+UseG1GC -XX:MaxGCPauseMillis=20
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    volumes:
      - producer-data:/app/data
    ulimits:
      nofile: 100000
      memlock: -1
    sysctls:
      - net.core.rmem_max=134217728
      - net.core.wmem_max=134217728

  kafka-consumer:
    image: your-consumer:latest
    deploy:
      resources:
        limits:
          cpus: '20.0'
          memory: 48G
        reservations:
          cpus: '12.0'
          memory: 24G
    environment:
      - JAVA_OPTS=-Xms24g -Xmx48g -XX:+UseG1GC -XX:MaxGCPauseMillis=50
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    volumes:
      - consumer-data:/app/data
    ulimits:
      nofile: 100000
      memlock: -1

volumes:
  producer-data:
  consumer-data:
```

### **Kubernetes Deployment for Production**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka-producer-high-throughput
spec:
  replicas: 3
  selector:
    matchLabels:
      app: kafka-producer
  template:
    metadata:
      labels:
        app: kafka-producer
    spec:
      nodeSelector:
        workload-type: compute-optimized
      containers:
      - name: producer
        image: kafka-producer:latest
        resources:
          requests:
            memory: "16Gi"
            cpu: "8000m"
          limits:
            memory: "32Gi"
            cpu: "16000m"
        env:
        - name: JAVA_OPTS
          value: "-Xms16g -Xmx32g -XX:+UseG1GC -XX:MaxGCPauseMillis=20"
        - name: KAFKA_PRODUCER_BATCH_SIZE
          value: "65536"
        - name: KAFKA_PRODUCER_LINGER_MS
          value: "100"
        - name: KAFKA_PRODUCER_BUFFER_MEMORY
          value: "134217728"
        ports:
        - containerPort: 8080
        - containerPort: 8081  # Metrics port
        volumeMounts:
        - name: producer-storage
          mountPath: /app/data
      volumes:
      - name: producer-storage
        persistentVolumeClaim:
          claimName: producer-pvc

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: producer-pvc
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: fast-ssd
  resources:
    requests:
      storage: 1Ti
```

## ⚙️ System Tuning Scripts

### **Complete System Optimization Script**

```bash
#!/bin/bash
# optimize-kafka-system.sh - Complete system optimization for Kafka

set -e

echo "🔧 Starting Kafka system optimization..."

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root" 
   exit 1
fi

# 1. Network Optimization
echo "📡 Optimizing network settings..."
cat >> /etc/sysctl.conf << EOF
# Kafka Network Optimization
net.core.rmem_max = 134217728
net.core.wmem_max = 134217728
net.core.rmem_default = 65536
net.core.wmem_default = 65536
net.ipv4.tcp_rmem = 4096 65536 134217728
net.ipv4.tcp_wmem = 4096 65536 134217728
net.core.netdev_max_backlog = 30000
net.core.netdev_budget = 600
net.netfilter.nf_conntrack_max = 1048576
EOF

# 2. Memory and VM Optimization
echo "💾 Optimizing memory settings..."
cat >> /etc/sysctl.conf << EOF
# Kafka Memory Optimization
vm.swappiness = 1
vm.dirty_ratio = 80
vm.dirty_background_ratio = 5
vm.dirty_expire_centisecs = 12000
vm.dirty_writeback_centisecs = 1500
vm.overcommit_memory = 1
EOF

# 3. File System Optimization
echo "📁 Optimizing file system settings..."
cat >> /etc/sysctl.conf << EOF
# File System Optimization
fs.file-max = 1000000
fs.nr_open = 1000000
EOF

# 4. Process Limits
echo "⚡ Setting process limits..."
cat >> /etc/security/limits.conf << EOF
# Kafka Process Limits
* soft nofile 100000
* hard nofile 100000
* soft nproc 32768
* hard nproc 32768
* soft memlock unlimited
* hard memlock unlimited
EOF

# 5. CPU Optimization
echo "🔥 Optimizing CPU settings..."
# Disable CPU frequency scaling for consistent performance
echo performance | tee /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

# 6. Disk I/O Optimization
echo "💽 Optimizing disk I/O..."
# Set I/O scheduler to deadline for SSDs
for disk in /sys/block/sd*/queue/scheduler; do
    echo deadline > "$disk" 2>/dev/null || true
done

# Set readahead for better sequential I/O
for disk in /sys/block/sd*/queue/read_ahead_kb; do
    echo 128 > "$disk" 2>/dev/null || true
done

# 7. Apply all sysctl changes
echo "✅ Applying all system changes..."
sysctl -p

# 8. Create Kafka user with optimized settings
echo "👤 Creating Kafka user..."
useradd -r -s /bin/false kafka 2>/dev/null || true

# 9. Java Optimization
echo "☕ Setting Java optimizations..."
cat > /etc/environment << EOF
# Java Optimizations for Kafka
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
JVM_PERFORMANCE_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+AlwaysPreTouch"
EOF

echo ""
echo "🎉 System optimization complete!"
echo ""
echo "📋 Next steps:"
echo "1. Reboot the system to apply all changes"
echo "2. Verify settings with: ./verify-optimization.sh"
echo "3. Deploy your Kafka applications"
echo "4. Monitor performance with your monitoring stack"
echo ""
echo "⚠️  Remember to tune JVM settings based on your specific workload!"
```

### **Performance Verification Script**

```bash
#!/bin/bash
# verify-optimization.sh - Verify system optimization

echo "🔍 Verifying Kafka system optimization..."

# Check network settings
echo "📡 Network Settings:"
echo "  rmem_max: $(sysctl -n net.core.rmem_max)"
echo "  wmem_max: $(sysctl -n net.core.wmem_max)"
echo "  netdev_max_backlog: $(sysctl -n net.core.netdev_max_backlog)"

# Check memory settings
echo "💾 Memory Settings:"
echo "  swappiness: $(sysctl -n vm.swappiness)"
echo "  dirty_ratio: $(sysctl -n vm.dirty_ratio)"

# Check file limits
echo "📁 File Limits:"
echo "  file-max: $(sysctl -n fs.file-max)"
echo "  Current nofile limit: $(ulimit -n)"

# Check CPU governor
echo "🔥 CPU Settings:"
echo "  CPU Governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor)"

# Check disk scheduler
echo "💽 Disk I/O:"
for disk in /sys/block/sd*/queue/scheduler; do
    if [[ -f "$disk" ]]; then
        diskname=$(echo "$disk" | cut -d'/' -f4)
        scheduler=$(cat "$disk" | grep -o '\[.*\]' | tr -d '[]')
        echo "  $diskname scheduler: $scheduler"
    fi
done

echo ""
echo "✅ Verification complete!"
```

## 📊 Performance Testing Commands

### **Producer Performance Test**

```bash
#!/bin/bash
# test-producer-performance.sh

echo "🚀 Testing Kafka Producer Performance..."

# High throughput test
kafka-producer-perf-test.sh \
  --topic performance-test \
  --num-records 1000000 \
  --record-size 1024 \
  --throughput 100000 \
  --producer-props \
    bootstrap.servers=localhost:9092 \
    batch.size=65536 \
    linger.ms=100 \
    compression.type=snappy \
    buffer.memory=134217728

echo "📊 Producer test complete!"
```

### **Consumer Performance Test**

```bash
#!/bin/bash
# test-consumer-performance.sh

echo "📥 Testing Kafka Consumer Performance..."

# High throughput test
kafka-consumer-perf-test.sh \
  --topic performance-test \
  --messages 1000000 \
  --threads 8 \
  --consumer-props \
    bootstrap.servers=localhost:9092 \
    max.poll.records=1000 \
    fetch.min.bytes=100000 \
    group.id=perf-test-group

echo "📊 Consumer test complete!"
```

## 🎯 Quick Deployment Checklist

```yaml
pre_deployment:
  - [ ] Server specs meet throughput requirements
  - [ ] Operating system optimized
  - [ ] JVM settings configured
  - [ ] Network tuning applied
  - [ ] Storage performance verified
  - [ ] Monitoring stack ready
  
during_deployment:
  - [ ] Application deployed with correct resource limits
  - [ ] JVM heap sizes appropriate
  - [ ] Kafka configurations applied
  - [ ] Health checks passing
  
post_deployment:
  - [ ] Performance tests executed
  - [ ] Metrics collection verified
  - [ ] Alerting configured
  - [ ] Load testing completed
```

This configuration guide provides everything you need to deploy high-throughput Kafka applications on properly sized hardware! 🚀

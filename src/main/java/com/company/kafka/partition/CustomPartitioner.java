package com.company.kafka.partition;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced custom partitioner with multiple partitioning strategies.
 * 
 * Supported Strategies:
 * 1. ROUND_ROBIN - Distributes messages evenly across partitions
 * 2. HASH - Uses key hash for consistent partitioning (default Kafka behavior)
 * 3. STICKY - Assigns messages to specific partitions for better batching
 * 4. CUSTOMER_AWARE - Routes messages based on customer ID for locality
 * 5. GEOGRAPHIC - Routes messages based on geographic region
 * 6. PRIORITY - Routes high-priority messages to dedicated partitions
 * 7. TIME_BASED - Routes messages based on time windows
 * 8. LOAD_AWARE - Routes messages based on partition load metrics
 * 
 * Configuration:
 * - partitioner.strategy: Strategy type (default: HASH)
 * - partitioner.sticky.partition.count: Number of sticky partitions (default: 1)
 * - partitioner.priority.partition.ratio: Ratio of partitions for priority messages (default: 0.2)
 * - partitioner.geographic.regions: Comma-separated list of regions
 * - partitioner.customer.hash.seed: Seed for customer hash function
 * 
 * Performance Features:
 * - Caching of partition calculations
 * - Efficient round-robin implementation
 * - Load balancing with metrics integration
 * - Hot partition detection and avoidance
 * 
 * Pro Tips:
 * 1. Use CUSTOMER_AWARE for applications requiring customer data locality
 * 2. Use STICKY for maximum batching efficiency in high-throughput scenarios
 * 3. Use PRIORITY for systems with different SLA requirements
 * 4. Use GEOGRAPHIC for multi-region deployments
 * 5. Monitor partition distribution with the built-in metrics
 */
@Slf4j
public class CustomPartitioner implements Partitioner {
    
    public enum PartitioningStrategy {
        ROUND_ROBIN,
        HASH,
        STICKY,
        CUSTOMER_AWARE,
        GEOGRAPHIC,
        PRIORITY,
        TIME_BASED,
        LOAD_AWARE
    }
    
    private PartitioningStrategy strategy = PartitioningStrategy.HASH;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    private final Map<String, Integer> stickyPartitionCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> customerPartitionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> partitionLoadMetrics = new ConcurrentHashMap<>();
    
    // Configuration
    private int stickyPartitionCount = 1;
    private double priorityPartitionRatio = 0.2;
    private String[] geographicRegions = {"us-east", "us-west", "eu", "asia"};
    private long customerHashSeed = 12345L;
    private long timeWindowMs = 60000L; // 1 minute
    private int loadAwareThreshold = 1000;
    
    @Override
    public void configure(Map<String, ?> configs) {
        log.info("Configuring custom partitioner with configs: {}", configs);
        
        // Parse strategy
        String strategyConfig = (String) configs.get("partitioner.strategy");
        if (strategyConfig != null) {
            try {
                strategy = PartitioningStrategy.valueOf(strategyConfig.toUpperCase());
                log.info("Using partitioning strategy: {}", strategy);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid partitioning strategy: {}, falling back to HASH", strategyConfig);
                strategy = PartitioningStrategy.HASH;
            }
        }
        
        // Parse sticky partition count
        Object stickyCountConfig = configs.get("partitioner.sticky.partition.count");
        if (stickyCountConfig != null) {
            stickyPartitionCount = Integer.parseInt(stickyCountConfig.toString());
        }
        
        // Parse priority partition ratio
        Object priorityRatioConfig = configs.get("partitioner.priority.partition.ratio");
        if (priorityRatioConfig != null) {
            priorityPartitionRatio = Double.parseDouble(priorityRatioConfig.toString());
        }
        
        // Parse geographic regions
        String regionsConfig = (String) configs.get("partitioner.geographic.regions");
        if (regionsConfig != null) {
            geographicRegions = regionsConfig.split(",");
        }
        
        // Parse customer hash seed
        Object hashSeedConfig = configs.get("partitioner.customer.hash.seed");
        if (hashSeedConfig != null) {
            customerHashSeed = Long.parseLong(hashSeedConfig.toString());
        }
        
        // Parse time window
        Object timeWindowConfig = configs.get("partitioner.time.window.ms");
        if (timeWindowConfig != null) {
            timeWindowMs = Long.parseLong(timeWindowConfig.toString());
        }
        
        // Parse load aware threshold
        Object loadThresholdConfig = configs.get("partitioner.load.aware.threshold");
        if (loadThresholdConfig != null) {
            loadAwareThreshold = Integer.parseInt(loadThresholdConfig.toString());
        }
        
        log.info("Custom partitioner configured - strategy: {}, stickyCount: {}, priorityRatio: {}", 
            strategy, stickyPartitionCount, priorityPartitionRatio);
    }

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, 
                        byte[] valueBytes, Cluster cluster) {
        
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        
        if (numPartitions == 0) {
            throw new IllegalStateException("No partitions available for topic: " + topic);
        }
        
        int partition = switch (strategy) {
            case ROUND_ROBIN -> roundRobinPartition(numPartitions);
            case HASH -> hashPartition(key, keyBytes, numPartitions);
            case STICKY -> stickyPartition(topic, numPartitions);
            case CUSTOMER_AWARE -> customerAwarePartition(key, value, numPartitions);
            case GEOGRAPHIC -> geographicPartition(key, value, numPartitions);
            case PRIORITY -> priorityPartition(key, value, numPartitions);
            case TIME_BASED -> timeBasedPartition(numPartitions);
            case LOAD_AWARE -> loadAwarePartition(topic, numPartitions);
        };
        
        // Update metrics
        updatePartitionMetrics(topic, partition);
        
        log.debug("Partitioned message - topic: {}, key: {}, strategy: {}, partition: {}", 
            topic, key, strategy, partition);
        
        return partition;
    }
    
    /**
     * Round-robin partitioning for even distribution.
     */
    private int roundRobinPartition(int numPartitions) {
        return roundRobinCounter.getAndIncrement() % numPartitions;
    }
    
    /**
     * Hash-based partitioning (default Kafka behavior).
     */
    private int hashPartition(Object key, byte[] keyBytes, int numPartitions) {
        if (keyBytes == null) {
            // If no key, use round-robin
            return roundRobinPartition(numPartitions);
        }
        
        // Use Kafka's default hash function
        return Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions;
    }
    
    /**
     * Sticky partitioning for better batching efficiency.
     */
    private int stickyPartition(String topic, int numPartitions) {
        String threadKey = topic + "-" + Thread.currentThread().getId();
        
        return stickyPartitionCache.computeIfAbsent(threadKey, k -> {
            int partition = roundRobinCounter.getAndIncrement() % Math.min(stickyPartitionCount, numPartitions);
            log.debug("Assigned sticky partition {} for thread key: {}", partition, threadKey);
            return partition;
        });
    }
    
    /**
     * Customer-aware partitioning for data locality.
     */
    private int customerAwarePartition(Object key, Object value, int numPartitions) {
        String customerId = extractCustomerId(key, value);
        if (customerId == null) {
            return hashPartition(key, key != null ? key.toString().getBytes() : null, numPartitions);
        }
        
        return customerPartitionCache.computeIfAbsent(customerId, cid -> {
            // Use a hash function with custom seed for consistent customer distribution
            long hash = customerId.hashCode() ^ customerHashSeed;
            int partition = (int) (Math.abs(hash) % numPartitions);
            log.debug("Assigned customer {} to partition {}", customerId, partition);
            return partition;
        });
    }
    
    /**
     * Geographic partitioning for multi-region deployments.
     */
    private int geographicPartition(Object key, Object value, int numPartitions) {
        String region = extractRegion(key, value);
        if (region == null) {
            return hashPartition(key, key != null ? key.toString().getBytes() : null, numPartitions);
        }
        
        // Map regions to partition ranges
        int partitionsPerRegion = Math.max(1, numPartitions / geographicRegions.length);
        
        for (int i = 0; i < geographicRegions.length; i++) {
            if (geographicRegions[i].equalsIgnoreCase(region)) {
                int basePartition = i * partitionsPerRegion;
                int regionPartition = Math.abs(key != null ? key.hashCode() : 0) % partitionsPerRegion;
                return Math.min(basePartition + regionPartition, numPartitions - 1);
            }
        }
        
        // Unknown region, use hash partitioning
        return hashPartition(key, key != null ? key.toString().getBytes() : null, numPartitions);
    }
    
    /**
     * Priority-based partitioning for different SLA requirements.
     */
    private int priorityPartition(Object key, Object value, int numPartitions) {
        String priority = extractPriority(key, value);
        boolean isHighPriority = "HIGH".equalsIgnoreCase(priority) || "CRITICAL".equalsIgnoreCase(priority);
        
        int priorityPartitions = Math.max(1, (int) (numPartitions * priorityPartitionRatio));
        
        if (isHighPriority) {
            // Route high-priority messages to dedicated partitions
            int priorityPartition = Math.abs(key != null ? key.hashCode() : 0) % priorityPartitions;
            log.debug("Routing high-priority message to partition {}", priorityPartition);
            return priorityPartition;
        } else {
            // Route normal messages to remaining partitions
            int normalPartitions = numPartitions - priorityPartitions;
            if (normalPartitions <= 0) {
                return hashPartition(key, key != null ? key.toString().getBytes() : null, numPartitions);
            }
            
            int normalPartition = priorityPartitions + 
                (Math.abs(key != null ? key.hashCode() : 0) % normalPartitions);
            return normalPartition;
        }
    }
    
    /**
     * Time-based partitioning for temporal data organization.
     */
    private int timeBasedPartition(int numPartitions) {
        long currentTimeWindow = System.currentTimeMillis() / timeWindowMs;
        return (int) (currentTimeWindow % numPartitions);
    }
    
    /**
     * Load-aware partitioning to avoid hot partitions.
     */
    private int loadAwarePartition(String topic, int numPartitions) {
        // Find partition with lowest load
        int bestPartition = 0;
        long minLoad = Long.MAX_VALUE;
        
        for (int i = 0; i < numPartitions; i++) {
            String partitionKey = topic + "-" + i;
            long load = partitionLoadMetrics.getOrDefault(partitionKey, 0L);
            
            if (load < minLoad) {
                minLoad = load;
                bestPartition = i;
            }
        }
        
        // If all partitions are above threshold, use round-robin
        if (minLoad > loadAwareThreshold) {
            return roundRobinPartition(numPartitions);
        }
        
        return bestPartition;
    }
    
    /**
     * Extract customer ID from key or value.
     */
    private String extractCustomerId(Object key, Object value) {
        // Try to extract from key first
        if (key != null) {
            String keyStr = key.toString();
            if (keyStr.startsWith("CUSTOMER-") || keyStr.startsWith("customer-")) {
                return keyStr;
            }
        }
        
        // Try to extract from value (assuming it's a Map or has customerId field)
        if (value instanceof Map) {
            Map<?, ?> valueMap = (Map<?, ?>) value;
            Object customerId = valueMap.get("customerId");
            if (customerId != null) {
                return customerId.toString();
            }
        }
        
        // Try reflection for objects with customerId field
        try {
            if (value != null && value.getClass().getDeclaredField("customerId") != null) {
                var field = value.getClass().getDeclaredField("customerId");
                field.setAccessible(true);
                Object customerId = field.get(value);
                if (customerId != null) {
                    return customerId.toString();
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        
        return null;
    }
    
    /**
     * Extract geographic region from key or value.
     */
    private String extractRegion(Object key, Object value) {
        // Check headers or message content for region information
        if (value instanceof Map) {
            Map<?, ?> valueMap = (Map<?, ?>) value;
            Object region = valueMap.get("region");
            if (region != null) {
                return region.toString();
            }
        }
        
        // Try reflection for objects with region field
        try {
            if (value != null && value.getClass().getDeclaredField("region") != null) {
                var field = value.getClass().getDeclaredField("region");
                field.setAccessible(true);
                Object region = field.get(value);
                if (region != null) {
                    return region.toString();
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        
        return null;
    }
    
    /**
     * Extract priority from key or value.
     */
    private String extractPriority(Object key, Object value) {
        // Check for priority in value
        if (value instanceof Map) {
            Map<?, ?> valueMap = (Map<?, ?>) value;
            Object priority = valueMap.get("priority");
            if (priority != null) {
                return priority.toString();
            }
        }
        
        // Try reflection for objects with priority field
        try {
            if (value != null && value.getClass().getDeclaredField("priority") != null) {
                var field = value.getClass().getDeclaredField("priority");
                field.setAccessible(true);
                Object priority = field.get(value);
                if (priority != null) {
                    return priority.toString();
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }
        
        return "NORMAL";
    }
    
    /**
     * Update partition load metrics.
     */
    private void updatePartitionMetrics(String topic, int partition) {
        String partitionKey = topic + "-" + partition;
        partitionLoadMetrics.merge(partitionKey, 1L, Long::sum);
    }
    
    /**
     * Get partition distribution statistics.
     */
    public Map<String, Long> getPartitionDistribution() {
        return new ConcurrentHashMap<>(partitionLoadMetrics);
    }
    
    /**
     * Reset partition metrics.
     */
    public void resetMetrics() {
        partitionLoadMetrics.clear();
        log.info("Partition metrics reset for custom partitioner");
    }

    @Override
    public void close() {
        log.info("Closing custom partitioner - strategy: {}, metrics: {}", 
            strategy, partitionLoadMetrics.size());
        
        stickyPartitionCache.clear();
        customerPartitionCache.clear();
        partitionLoadMetrics.clear();
    }
    
    // Getters for testing and monitoring
    public PartitioningStrategy getStrategy() {
        return strategy;
    }
    
    public int getStickyPartitionCount() {
        return stickyPartitionCount;
    }
    
    public double getPriorityPartitionRatio() {
        return priorityPartitionRatio;
    }
}
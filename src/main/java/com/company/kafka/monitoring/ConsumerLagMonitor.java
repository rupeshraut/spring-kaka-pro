package com.company.kafka.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Consumer lag monitoring for Kafka consumers.
 * 
 * Features:
 * - Real-time consumer lag tracking per partition
 * - Consumer group health monitoring
 * - Lag threshold alerting
 * - Trend analysis and metrics
 * - Partition-level lag visibility
 * - Consumer group assignment monitoring
 * 
 * Metrics:
 * - Consumer lag per topic/partition
 * - Consumer group assignments
 * - Lag trend indicators
 * - Consumer group health status
 * 
 * Use Cases:
 * - Production monitoring and alerting
 * - Performance optimization
 * - Capacity planning
 * - Troubleshooting consumer issues
 * - SLA monitoring
 * 
 * Pro Tips:
 * 1. Monitor lag trends, not just absolute values
 * 2. Set appropriate lag thresholds for your use case
 * 3. Consider consumer group rebalancing impact on lag
 * 4. Monitor both lag and processing rate
 * 5. Implement alerting for sustained high lag
 */
@Component
@Slf4j
public class ConsumerLagMonitor {

    private final KafkaAdmin kafkaAdmin;
    private final MeterRegistry meterRegistry;
    
    // Lag tracking
    private final Map<TopicPartition, Long> currentLag = new ConcurrentHashMap<>();
    private final Map<TopicPartition, Long> previousLag = new ConcurrentHashMap<>();
    private final Map<String, Set<TopicPartition>> consumerGroupAssignments = new ConcurrentHashMap<>();
    
    // Configuration
    private final List<String> monitoredConsumerGroups;
    private final long lagWarningThreshold;
    private final long lagCriticalThreshold;
    
    public ConsumerLagMonitor(KafkaAdmin kafkaAdmin, MeterRegistry meterRegistry) {
        this.kafkaAdmin = kafkaAdmin;
        this.meterRegistry = meterRegistry;
        
        // Configuration - in production, these should come from properties
        this.monitoredConsumerGroups = List.of(
            "spring-kafka-pro-group",
            "spring-kafka-pro-group-batch"
        );
        this.lagWarningThreshold = 1000;  // 1000 messages
        this.lagCriticalThreshold = 5000; // 5000 messages
        
        // Register lag gauges
        Gauge.builder("kafka.consumer.lag.total", this, ConsumerLagMonitor::getTotalLag)
                .description("Total consumer lag across all partitions")
                .register(meterRegistry);
                
        Gauge.builder("kafka.consumer.lag.max", this, ConsumerLagMonitor::getMaxLag)
                .description("Maximum consumer lag across all partitions")
                .register(meterRegistry);
                
        Gauge.builder("kafka.consumer.lag.partitions.warning", this, 
                monitor -> monitor.getPartitionsAboveThreshold(lagWarningThreshold))
                .description("Number of partitions with lag above warning threshold")
                .register(meterRegistry);
                
        Gauge.builder("kafka.consumer.lag.partitions.critical", this,
                monitor -> monitor.getPartitionsAboveThreshold(lagCriticalThreshold))
                .description("Number of partitions with lag above critical threshold")
                .register(meterRegistry);
        
        log.info("ConsumerLagMonitor initialized - monitoring groups: {}, thresholds: warning={}, critical={}", 
            monitoredConsumerGroups, lagWarningThreshold, lagCriticalThreshold);
    }

    /**
     * Scheduled task to monitor consumer lag.
     * Runs every 30 seconds to update lag metrics.
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void monitorConsumerLag() {
        log.debug("Starting consumer lag monitoring cycle");
        
        try {
            AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
            
            for (String consumerGroup : monitoredConsumerGroups) {
                try {
                    updateConsumerGroupLag(adminClient, consumerGroup);
                } catch (Exception e) {
                    log.error("Error monitoring consumer group: {}, error: {}", consumerGroup, e.getMessage());
                }
            }
            
            adminClient.close();
            
        } catch (Exception e) {
            log.error("Error in consumer lag monitoring: {}", e.getMessage(), e);
        }
        
        log.debug("Consumer lag monitoring cycle completed");
    }

    /**
     * Updates lag information for a specific consumer group.
     *
     * @param adminClient the Kafka admin client
     * @param consumerGroup the consumer group to monitor
     */
    private void updateConsumerGroupLag(AdminClient adminClient, String consumerGroup) 
            throws ExecutionException, InterruptedException {
        
        log.debug("Updating lag for consumer group: {}", consumerGroup);
        
        // Get consumer group offsets
        ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroup);
        Map<TopicPartition, OffsetAndMetadata> consumerOffsets = offsetsResult.partitionsToOffsetAndMetadata().get();
        
        if (consumerOffsets.isEmpty()) {
            log.debug("No consumer offsets found for group: {}", consumerGroup);
            return;
        }
        
        // Get latest offsets for the same partitions
        Map<TopicPartition, OffsetSpec> latestOffsetSpec = consumerOffsets.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        
        ListOffsetsResult latestOffsetsResult = adminClient.listOffsets(latestOffsetSpec);
        Map<TopicPartition, Long> latestOffsets = new HashMap<>();
        
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : consumerOffsets.entrySet()) {
            TopicPartition partition = entry.getKey();
            try {
                long latestOffset = latestOffsetsResult.partitionResult(partition).get().offset();
                latestOffsets.put(partition, latestOffset);
            } catch (Exception e) {
                log.warn("Failed to get latest offset for partition: {}, error: {}", partition, e.getMessage());
            }
        }
        
        // Calculate lag for each partition
        Map<TopicPartition, Long> newLag = new HashMap<>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : consumerOffsets.entrySet()) {
            TopicPartition partition = entry.getKey();
            long consumerOffset = entry.getValue().offset();
            Long latestOffset = latestOffsets.get(partition);
            
            if (latestOffset != null) {
                long lag = latestOffset - consumerOffset;
                newLag.put(partition, Math.max(0, lag)); // Ensure non-negative lag
                
                // Register individual partition lag gauge
                Gauge.builder("kafka.consumer.lag.by.partition", () -> Math.max(0, lag))
                        .description("Consumer lag for specific partition")
                        .tag("topic", partition.topic())
                        .tag("partition", String.valueOf(partition.partition()))
                        .tag("consumer_group", consumerGroup)
                        .register(meterRegistry);
            }
        }
        
        // Update lag tracking
        previousLag.putAll(currentLag);
        currentLag.putAll(newLag);
        consumerGroupAssignments.put(consumerGroup, consumerOffsets.keySet());
        
        // Log lag summary
        logLagSummary(consumerGroup, newLag);
        
        // Check for alerts
        checkLagAlerts(consumerGroup, newLag);
    }

    /**
     * Logs a summary of consumer lag for a consumer group.
     *
     * @param consumerGroup the consumer group
     * @param lagMap the lag map
     */
    private void logLagSummary(String consumerGroup, Map<TopicPartition, Long> lagMap) {
        if (lagMap.isEmpty()) {
            return;
        }
        
        long totalLag = lagMap.values().stream().mapToLong(Long::longValue).sum();
        long maxLag = lagMap.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long avgLag = totalLag / lagMap.size();
        
        log.info("Consumer lag summary - group: {}, partitions: {}, total: {}, max: {}, avg: {}", 
            consumerGroup, lagMap.size(), totalLag, maxLag, avgLag);
        
        // Log details for high-lag partitions
        lagMap.entrySet().stream()
                .filter(entry -> entry.getValue() > lagWarningThreshold)
                .forEach(entry -> log.warn("High lag detected - group: {}, partition: {}, lag: {}", 
                    consumerGroup, entry.getKey(), entry.getValue()));
    }

    /**
     * Checks for lag-based alerts.
     *
     * @param consumerGroup the consumer group
     * @param lagMap the lag map
     */
    private void checkLagAlerts(String consumerGroup, Map<TopicPartition, Long> lagMap) {
        for (Map.Entry<TopicPartition, Long> entry : lagMap.entrySet()) {
            TopicPartition partition = entry.getKey();
            long lag = entry.getValue();
            
            if (lag > lagCriticalThreshold) {
                log.error("CRITICAL LAG ALERT - group: {}, partition: {}, lag: {} (threshold: {})", 
                    consumerGroup, partition, lag, lagCriticalThreshold);
                    
                // Record critical lag metric
                meterRegistry.counter("kafka.consumer.lag.alerts.critical",
                    "consumer_group", consumerGroup,
                    "topic", partition.topic(),
                    "partition", String.valueOf(partition.partition()))
                    .increment();
                    
            } else if (lag > lagWarningThreshold) {
                log.warn("WARNING LAG ALERT - group: {}, partition: {}, lag: {} (threshold: {})", 
                    consumerGroup, partition, lag, lagWarningThreshold);
                    
                // Record warning lag metric
                meterRegistry.counter("kafka.consumer.lag.alerts.warning",
                    "consumer_group", consumerGroup,
                    "topic", partition.topic(),
                    "partition", String.valueOf(partition.partition()))
                    .increment();
            }
        }
    }

    /**
     * Gets the total lag across all monitored partitions.
     *
     * @return total lag
     */
    public long getTotalLag() {
        return currentLag.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Gets the maximum lag across all monitored partitions.
     *
     * @return maximum lag
     */
    public long getMaxLag() {
        return currentLag.values().stream().mapToLong(Long::longValue).max().orElse(0);
    }

    /**
     * Gets the number of partitions with lag above the specified threshold.
     *
     * @param threshold the lag threshold
     * @return number of partitions above threshold
     */
    public long getPartitionsAboveThreshold(long threshold) {
        return currentLag.values().stream().mapToLong(Long::longValue).filter(lag -> lag > threshold).count();
    }

    /**
     * Gets lag information for a specific consumer group.
     *
     * @param consumerGroup the consumer group
     * @return lag information map
     */
    public Map<String, Object> getConsumerGroupLagInfo(String consumerGroup) {
        Set<TopicPartition> partitions = consumerGroupAssignments.get(consumerGroup);
        if (partitions == null) {
            return Map.of("status", "not_found");
        }
        
        Map<String, Long> lagByPartition = new HashMap<>();
        long totalLag = 0;
        long maxLag = 0;
        
        for (TopicPartition partition : partitions) {
            Long lag = currentLag.get(partition);
            if (lag != null) {
                lagByPartition.put(partition.toString(), lag);
                totalLag += lag;
                maxLag = Math.max(maxLag, lag);
            }
        }
        
        return Map.of(
            "consumerGroup", consumerGroup,
            "partitionCount", partitions.size(),
            "totalLag", totalLag,
            "maxLag", maxLag,
            "averageLag", partitions.isEmpty() ? 0 : totalLag / partitions.size(),
            "lagByPartition", lagByPartition,
            "warningThreshold", lagWarningThreshold,
            "criticalThreshold", lagCriticalThreshold,
            "partitionsAboveWarning", getPartitionsAboveThreshold(lagWarningThreshold),
            "partitionsAboveCritical", getPartitionsAboveThreshold(lagCriticalThreshold)
        );
    }

    /**
     * Gets lag information for all monitored consumer groups.
     *
     * @return comprehensive lag information
     */
    public Map<String, Object> getAllLagInfo() {
        Map<String, Object> allGroupsInfo = new HashMap<>();
        
        for (String consumerGroup : monitoredConsumerGroups) {
            allGroupsInfo.put(consumerGroup, getConsumerGroupLagInfo(consumerGroup));
        }
        
        return Map.of(
            "timestamp", System.currentTimeMillis(),
            "totalLag", getTotalLag(),
            "maxLag", getMaxLag(),
            "monitoredGroups", monitoredConsumerGroups.size(),
            "consumerGroups", allGroupsInfo,
            "thresholds", Map.of(
                "warning", lagWarningThreshold,
                "critical", lagCriticalThreshold
            )
        );
    }

    /**
     * Gets lag trend information by comparing current and previous measurements.
     *
     * @return lag trend information
     */
    public Map<String, Object> getLagTrends() {
        Map<String, String> trendByPartition = new HashMap<>();
        
        for (TopicPartition partition : currentLag.keySet()) {
            Long current = currentLag.get(partition);
            Long previous = previousLag.get(partition);
            
            if (current != null && previous != null) {
                if (current > previous) {
                    trendByPartition.put(partition.toString(), "INCREASING");
                } else if (current < previous) {
                    trendByPartition.put(partition.toString(), "DECREASING");
                } else {
                    trendByPartition.put(partition.toString(), "STABLE");
                }
            } else {
                trendByPartition.put(partition.toString(), "NEW");
            }
        }
        
        return Map.of(
            "timestamp", System.currentTimeMillis(),
            "trendByPartition", trendByPartition
        );
    }
}
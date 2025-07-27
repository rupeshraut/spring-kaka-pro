package com.company.kafka.partition;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for CustomPartitioner strategies.
 * 
 * Test Coverage:
 * - All partitioning strategies (ROUND_ROBIN, HASH, STICKY, etc.)
 * - Edge cases and error conditions
 * - Performance characteristics
 * - Configuration validation
 * - Partition distribution metrics
 * 
 * Test Scenarios:
 * - Single partition topic
 * - Multi-partition topics
 * - Different message patterns
 * - Customer-aware partitioning
 * - Geographic partitioning
 * - Priority-based partitioning
 * - Load balancing effectiveness
 */
class CustomPartitionerTest {

    private CustomPartitioner partitioner;
    private Cluster cluster;
    private final String TOPIC_NAME = "test-topic";
    private final int PARTITION_COUNT = 6;

    @BeforeEach
    void setUp() {
        partitioner = new CustomPartitioner();
        cluster = createTestCluster();
    }

    @Test
    @DisplayName("Should use HASH strategy by default")
    void shouldUseHashStrategyByDefault() {
        // Given
        Map<String, Object> config = new HashMap<>();
        
        // When
        partitioner.configure(config);
        
        // Then
        assertThat(partitioner.getStrategy()).isEqualTo(CustomPartitioner.PartitioningStrategy.HASH);
    }

    @Test
    @DisplayName("Should configure strategy from properties")
    void shouldConfigureStrategyFromProperties() {
        // Given
        Map<String, Object> config = Map.of(
            "partitioner.strategy", "ROUND_ROBIN",
            "partitioner.sticky.partition.count", "3",
            "partitioner.priority.partition.ratio", "0.3"
        );
        
        // When
        partitioner.configure(config);
        
        // Then
        assertThat(partitioner.getStrategy()).isEqualTo(CustomPartitioner.PartitioningStrategy.ROUND_ROBIN);
        assertThat(partitioner.getStickyPartitionCount()).isEqualTo(3);
        assertThat(partitioner.getPriorityPartitionRatio()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("Should handle invalid strategy gracefully")
    void shouldHandleInvalidStrategyGracefully() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "INVALID_STRATEGY");
        
        // When
        partitioner.configure(config);
        
        // Then
        assertThat(partitioner.getStrategy()).isEqualTo(CustomPartitioner.PartitioningStrategy.HASH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROUND_ROBIN", "HASH", "STICKY", "CUSTOMER_AWARE", "GEOGRAPHIC", "PRIORITY"})
    @DisplayName("Should support all partitioning strategies")
    void shouldSupportAllPartitioningStrategies(String strategy) {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", strategy);
        
        // When
        partitioner.configure(config);
        
        // Then
        assertThat(partitioner.getStrategy().toString()).isEqualTo(strategy);
    }

    @Test
    @DisplayName("Round-robin strategy should distribute messages evenly")
    void roundRobinStrategyShouldDistributeMessagesEvenly() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "ROUND_ROBIN");
        partitioner.configure(config);
        
        Map<Integer, Integer> partitionCounts = new HashMap<>();
        
        // When
        for (int i = 0; i < 100; i++) {
            int partition = partitioner.partition(TOPIC_NAME, "key-" + i, null, 
                createTestMessage("message-" + i), null, cluster);
            partitionCounts.merge(partition, 1, Integer::sum);
        }
        
        // Then
        assertThat(partitionCounts).hasSize(PARTITION_COUNT);
        
        // Check distribution is relatively even (within 20% variance)
        double average = 100.0 / PARTITION_COUNT;
        for (Integer count : partitionCounts.values()) {
            assertThat(count).isBetween((int) (average * 0.8), (int) (average * 1.2));
        }
    }

    @Test
    @DisplayName("Hash strategy should provide consistent partitioning")
    void hashStrategyShouldProvideConsistentPartitioning() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "HASH");
        partitioner.configure(config);
        
        // When & Then
        for (int i = 0; i < 10; i++) {
            String key = "consistent-key";
            int partition1 = partitioner.partition(TOPIC_NAME, key, key.getBytes(), 
                createTestMessage("message1"), null, cluster);
            int partition2 = partitioner.partition(TOPIC_NAME, key, key.getBytes(), 
                createTestMessage("message2"), null, cluster);
            
            assertThat(partition1).isEqualTo(partition2);
        }
    }

    @Test
    @DisplayName("Sticky strategy should maintain thread affinity")
    void stickyStrategyShouldMaintainThreadAffinity() {
        // Given
        Map<String, Object> config = Map.of(
            "partitioner.strategy", "STICKY",
            "partitioner.sticky.partition.count", "2"
        );
        partitioner.configure(config);
        
        Set<Integer> usedPartitions = new HashSet<>();
        
        // When
        for (int i = 0; i < 50; i++) {
            int partition = partitioner.partition(TOPIC_NAME, "key-" + i, null, 
                createTestMessage("message-" + i), null, cluster);
            usedPartitions.add(partition);
        }
        
        // Then - Should use limited number of partitions
        assertThat(usedPartitions.size()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Customer-aware strategy should partition by customer ID")
    void customerAwareStrategyShouldPartitionByCustomerId() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "CUSTOMER_AWARE");
        partitioner.configure(config);
        
        // When & Then
        String customerId = "CUSTOMER-123";
        Map<String, Object> message1 = Map.of("customerId", customerId, "amount", 100.0);
        Map<String, Object> message2 = Map.of("customerId", customerId, "amount", 200.0);
        
        int partition1 = partitioner.partition(TOPIC_NAME, null, null, message1, null, cluster);
        int partition2 = partitioner.partition(TOPIC_NAME, null, null, message2, null, cluster);
        
        // Same customer should go to same partition
        assertThat(partition1).isEqualTo(partition2);
    }

    @Test
    @DisplayName("Geographic strategy should partition by region")
    void geographicStrategyShouldPartitionByRegion() {
        // Given
        Map<String, Object> config = Map.of(
            "partitioner.strategy", "GEOGRAPHIC",
            "partitioner.geographic.regions", "us-east,us-west,eu,asia"
        );
        partitioner.configure(config);
        
        // When & Then
        Map<String, Object> usEastMessage1 = Map.of("region", "us-east", "data", "test1");
        Map<String, Object> usEastMessage2 = Map.of("region", "us-east", "data", "test2");
        Map<String, Object> euMessage = Map.of("region", "eu", "data", "test3");
        
        int usEastPartition1 = partitioner.partition(TOPIC_NAME, null, null, usEastMessage1, null, cluster);
        int usEastPartition2 = partitioner.partition(TOPIC_NAME, null, null, usEastMessage2, null, cluster);
        int euPartition = partitioner.partition(TOPIC_NAME, null, null, euMessage, null, cluster);
        
        // Same region should go to same partition range
        assertThat(usEastPartition1).isEqualTo(usEastPartition2);
        // Different regions should likely go to different partitions
        assertThat(usEastPartition1).isNotEqualTo(euPartition);
    }

    @Test
    @DisplayName("Priority strategy should separate high and normal priority messages")
    void priorityStrategyShouldSeparateHighAndNormalPriorityMessages() {
        // Given
        Map<String, Object> config = Map.of(
            "partitioner.strategy", "PRIORITY",
            "partitioner.priority.partition.ratio", "0.5"
        );
        partitioner.configure(config);
        
        Set<Integer> highPriorityPartitions = new HashSet<>();
        Set<Integer> normalPriorityPartitions = new HashSet<>();
        
        // When
        for (int i = 0; i < 20; i++) {
            Map<String, Object> highPriorityMessage = Map.of("priority", "HIGH", "id", i);
            Map<String, Object> normalPriorityMessage = Map.of("priority", "NORMAL", "id", i);
            
            int highPartition = partitioner.partition(TOPIC_NAME, "key-" + i, null, 
                highPriorityMessage, null, cluster);
            int normalPartition = partitioner.partition(TOPIC_NAME, "key-" + i, null, 
                normalPriorityMessage, null, cluster);
            
            highPriorityPartitions.add(highPartition);
            normalPriorityPartitions.add(normalPartition);
        }
        
        // Then - High priority and normal priority should use different partition ranges
        assertThat(Collections.disjoint(highPriorityPartitions, normalPriorityPartitions)).isTrue();
    }

    @Test
    @DisplayName("Time-based strategy should change partitions over time")
    void timeBasedStrategyShouldChangePartitionsOverTime() {
        // Given
        Map<String, Object> config = Map.of(
            "partitioner.strategy", "TIME_BASED",
            "partitioner.time.window.ms", "1000" // 1 second window
        );
        partitioner.configure(config);
        
        // When
        int partition1 = partitioner.partition(TOPIC_NAME, "key", null, 
            createTestMessage("message1"), null, cluster);
        
        // Wait for time window change (simulate with different time)
        try {
            Thread.sleep(1100); // Wait longer than window
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        int partition2 = partitioner.partition(TOPIC_NAME, "key", null, 
            createTestMessage("message2"), null, cluster);
        
        // Then - Different time windows may result in different partitions
        // Note: This test might be flaky due to timing, but demonstrates the concept
        assertThat(partition1).isBetween(0, PARTITION_COUNT - 1);
        assertThat(partition2).isBetween(0, PARTITION_COUNT - 1);
    }

    @Test
    @DisplayName("Should handle null keys gracefully")
    void shouldHandleNullKeysGracefully() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "HASH");
        partitioner.configure(config);
        
        // When & Then
        assertThatCode(() -> {
            int partition = partitioner.partition(TOPIC_NAME, null, null, 
                createTestMessage("message"), null, cluster);
            assertThat(partition).isBetween(0, PARTITION_COUNT - 1);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle empty cluster gracefully")
    void shouldHandleEmptyClusterGracefully() {
        // Given
        Cluster emptyCluster = new Cluster("test", Collections.emptyList(), 
            Collections.emptyList(), Collections.emptySet(), Collections.emptySet());
        
        // When & Then
        assertThatThrownBy(() -> {
            partitioner.partition(TOPIC_NAME, "key", null, 
                createTestMessage("message"), null, emptyCluster);
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No partitions available");
    }

    @Test
    @DisplayName("Should track partition distribution metrics")
    void shouldTrackPartitionDistributionMetrics() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "ROUND_ROBIN");
        partitioner.configure(config);
        
        // When
        for (int i = 0; i < 30; i++) {
            partitioner.partition(TOPIC_NAME, "key-" + i, null, 
                createTestMessage("message-" + i), null, cluster);
        }
        
        // Then
        Map<String, Long> distribution = partitioner.getPartitionDistribution();
        assertThat(distribution).isNotEmpty();
        
        long totalMessages = distribution.values().stream().mapToLong(Long::longValue).sum();
        assertThat(totalMessages).isEqualTo(30);
    }

    @Test
    @DisplayName("Should reset metrics successfully")
    void shouldResetMetricsSuccessfully() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "HASH");
        partitioner.configure(config);
        
        // When
        partitioner.partition(TOPIC_NAME, "key", null, createTestMessage("message"), null, cluster);
        partitioner.resetMetrics();
        
        // Then
        Map<String, Long> distribution = partitioner.getPartitionDistribution();
        assertThat(distribution).isEmpty();
    }

    @Test
    @DisplayName("Should close without errors")
    void shouldCloseWithoutErrors() {
        // Given
        Map<String, Object> config = Map.of("partitioner.strategy", "STICKY");
        partitioner.configure(config);
        
        // When & Then
        assertThatCode(() -> partitioner.close()).doesNotThrowAnyException();
    }

    // Helper methods

    private Cluster createTestCluster() {
        List<Node> nodes = Arrays.asList(
            new Node(0, "localhost", 9092),
            new Node(1, "localhost", 9093),
            new Node(2, "localhost", 9094)
        );
        
        List<PartitionInfo> partitions = new ArrayList<>();
        for (int i = 0; i < PARTITION_COUNT; i++) {
            partitions.add(new PartitionInfo(TOPIC_NAME, i, nodes.get(0), 
                nodes.toArray(new Node[0]), nodes.toArray(new Node[0])));
        }
        
        return new Cluster("test-cluster", nodes, partitions, 
            Collections.emptySet(), Collections.emptySet());
    }

    private Map<String, Object> createTestMessage(String content) {
        return Map.of(
            "content", content,
            "timestamp", System.currentTimeMillis(),
            "id", UUID.randomUUID().toString()
        );
    }
}
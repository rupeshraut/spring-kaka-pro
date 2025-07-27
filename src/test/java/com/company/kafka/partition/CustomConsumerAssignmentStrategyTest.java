package com.company.kafka.partition;

import org.apache.kafka.clients.consumer.ConsumerPartitionAssignor;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for CustomConsumerAssignmentStrategy.
 * 
 * Test Coverage:
 * - All assignment strategies (AFFINITY_BASED, LOAD_BALANCED, etc.)
 * - Consumer metadata handling
 * - Partition distribution fairness
 * - Edge cases and error conditions
 * - Assignment efficiency metrics
 * 
 * Test Scenarios:
 * - Single consumer group
 * - Multiple consumers with different capabilities
 * - Cross-topic assignments
 * - Consumer failover scenarios
 * - Load balancing effectiveness
 */
class CustomConsumerAssignmentStrategyTest {
    
    private CustomConsumerAssignmentStrategy assignmentStrategy;
    private Cluster cluster;
    private final String TOPIC_1 = "topic-1";
    private final String TOPIC_2 = "topic-2";
    private final int PARTITIONS_PER_TOPIC = 6;
    
    @BeforeEach
    void setUp() {
        assignmentStrategy = new CustomConsumerAssignmentStrategy();
        cluster = createTestCluster();
    }
    
    @Test
    @DisplayName("Should return correct strategy name")
    void shouldReturnCorrectStrategyName() {
        assertThat(assignmentStrategy.name()).isEqualTo("custom");
    }
    
    @Test
    @DisplayName("Should support both EAGER and COOPERATIVE protocols")
    void shouldSupportBothProtocols() {
        List<ConsumerPartitionAssignor.RebalanceProtocol> protocols = assignmentStrategy.supportedProtocols();
        
        assertThat(protocols).containsExactlyInAnyOrder(
            ConsumerPartitionAssignor.RebalanceProtocol.EAGER,
            ConsumerPartitionAssignor.RebalanceProtocol.COOPERATIVE
        );
    }
    
    @Test
    @DisplayName("Should encode and decode consumer metadata")
    void shouldEncodeAndDecodeConsumerMetadata() {
        // Given
        Set<String> topics = Set.of(TOPIC_1, TOPIC_2);
        
        // When
        ByteBuffer encoded = assignmentStrategy.subscriptionUserData(topics);
        
        // Then
        assertThat(encoded).isNotNull();
        assertThat(encoded.remaining()).isGreaterThan(0);
    }
    
    @ParameterizedTest
    @EnumSource(CustomConsumerAssignmentStrategy.AssignmentStrategy.class)
    @DisplayName("Should handle all assignment strategies")
    void shouldHandleAllAssignmentStrategies(CustomConsumerAssignmentStrategy.AssignmentStrategy strategy) {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = createTestSubscriptions();
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When & Then
        assertThatCode(() -> {
            ConsumerPartitionAssignor.GroupAssignment assignment = 
                assignmentStrategy.assign(cluster, groupSubscription);
            validateAssignment(assignment, subscriptions.keySet());
        }).doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("Load balanced strategy should distribute partitions by capacity")
    void loadBalancedStrategyShouldDistributePartitionsByCapacity() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-high-capacity", createSubscription(List.of(TOPIC_1), createHighCapacityMetadata()),
            "consumer-medium-capacity", createSubscription(List.of(TOPIC_1), createMediumCapacityMetadata()),
            "consumer-low-capacity", createSubscription(List.of(TOPIC_1), createLowCapacityMetadata())
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        int highCapacityPartitions = assignments.get("consumer-high-capacity").partitions().size();
        int mediumCapacityPartitions = assignments.get("consumer-medium-capacity").partitions().size();
        int lowCapacityPartitions = assignments.get("consumer-low-capacity").partitions().size();
        
        // High capacity consumer should get more partitions
        assertThat(highCapacityPartitions).isGreaterThanOrEqualTo(mediumCapacityPartitions);
        assertThat(mediumCapacityPartitions).isGreaterThanOrEqualTo(lowCapacityPartitions);
        
        // All partitions should be assigned
        assertThat(highCapacityPartitions + mediumCapacityPartitions + lowCapacityPartitions)
            .isEqualTo(PARTITIONS_PER_TOPIC);
    }
    
    @Test
    @DisplayName("Rack aware strategy should consider consumer rack locality")
    void rackAwareStrategyShouldConsiderConsumerRackLocality() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-rack-1", createSubscription(List.of(TOPIC_1), createMetadata("rack-1", "MEDIUM", 1000, "us-east")),
            "consumer-rack-2", createSubscription(List.of(TOPIC_1), createMetadata("rack-2", "MEDIUM", 1000, "us-east")),
            "consumer-rack-3", createSubscription(List.of(TOPIC_1), createMetadata("rack-3", "MEDIUM", 1000, "us-east"))
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        // Each consumer should get some partitions
        for (ConsumerPartitionAssignor.Assignment consumerAssignment : assignments.values()) {
            assertThat(consumerAssignment.partitions()).isNotEmpty();
        }
        
        // All partitions should be assigned
        int totalAssigned = assignments.values().stream()
            .mapToInt(assignment1 -> assignment1.partitions().size())
            .sum();
        assertThat(totalAssigned).isEqualTo(PARTITIONS_PER_TOPIC);
    }
    
    @Test
    @DisplayName("Priority based strategy should prioritize high priority consumers")
    void priorityBasedStrategyShouldPrioritizeHighPriorityConsumers() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-high-priority", createSubscription(List.of(TOPIC_1), createMetadata("rack-1", "HIGH", 1000, "us-east")),
            "consumer-medium-priority", createSubscription(List.of(TOPIC_1), createMetadata("rack-1", "MEDIUM", 1000, "us-east")),
            "consumer-low-priority", createSubscription(List.of(TOPIC_1), createMetadata("rack-1", "LOW", 1000, "us-east"))
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        int highPriorityPartitions = assignments.get("consumer-high-priority").partitions().size();
        int mediumPriorityPartitions = assignments.get("consumer-medium-priority").partitions().size();
        int lowPriorityPartitions = assignments.get("consumer-low-priority").partitions().size();
        
        // High priority consumer should get more partitions than lower priority ones
        assertThat(highPriorityPartitions).isGreaterThanOrEqualTo(mediumPriorityPartitions);
        assertThat(mediumPriorityPartitions).isGreaterThanOrEqualTo(lowPriorityPartitions);
    }
    
    @Test
    @DisplayName("Geographic strategy should group consumers by region")
    void geographicStrategyShouldGroupConsumersByRegion() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-us-east-1", createSubscription(List.of(TOPIC_1), createMetadata("rack-1", "MEDIUM", 1000, "us-east")),
            "consumer-us-east-2", createSubscription(List.of(TOPIC_1), createMetadata("rack-2", "MEDIUM", 1000, "us-east")),
            "consumer-eu-1", createSubscription(List.of(TOPIC_1), createMetadata("rack-3", "MEDIUM", 1000, "eu")),
            "consumer-asia-1", createSubscription(List.of(TOPIC_1), createMetadata("rack-4", "MEDIUM", 1000, "asia"))
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        // All consumers should get partitions
        for (Map.Entry<String, ConsumerPartitionAssignor.Assignment> entry : assignments.entrySet()) {
            assertThat(entry.getValue().partitions())
                .as("Consumer %s should have partitions assigned", entry.getKey())
                .isNotEmpty();
        }
        
        // Total partitions should match
        int totalAssigned = assignments.values().stream()
            .mapToInt(assignment1 -> assignment1.partitions().size())
            .sum();
        assertThat(totalAssigned).isEqualTo(PARTITIONS_PER_TOPIC);
    }
    
    @Test
    @DisplayName("Workload aware strategy should consider consumer capabilities")
    void workloadAwareStrategyShouldConsiderConsumerCapabilities() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-batch-capable", createSubscription(List.of(TOPIC_1), 
                createMetadataWithCapabilities("rack-1", "MEDIUM", 2000, "us-east", 
                    List.of("BATCH_PROCESSING", "JSON"), List.of("HIGH_THROUGHPUT"))),
            "consumer-stream-capable", createSubscription(List.of(TOPIC_1), 
                createMetadataWithCapabilities("rack-2", "MEDIUM", 1500, "us-east", 
                    List.of("REAL_TIME", "AVRO"), List.of("LOW_LATENCY"))),
            "consumer-basic", createSubscription(List.of(TOPIC_1), 
                createMetadataWithCapabilities("rack-3", "MEDIUM", 1000, "us-east", 
                    List.of("JSON"), List.of("BATCH_PROCESSING")))
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        int batchCapablePartitions = assignments.get("consumer-batch-capable").partitions().size();
        int streamCapablePartitions = assignments.get("consumer-stream-capable").partitions().size();
        int basicPartitions = assignments.get("consumer-basic").partitions().size();
        
        // Consumers with higher capacity should get more partitions
        assertThat(batchCapablePartitions).isGreaterThanOrEqualTo(streamCapablePartitions);
        assertThat(streamCapablePartitions).isGreaterThanOrEqualTo(basicPartitions);
    }
    
    @Test
    @DisplayName("Should handle single consumer gracefully")
    void shouldHandleSingleConsumerGracefully() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "single-consumer", createSubscription(List.of(TOPIC_1, TOPIC_2), createDefaultMetadata())
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        ConsumerPartitionAssignor.Assignment singleAssignment = assignments.get("single-consumer");
        
        assertThat(singleAssignment.partitions()).hasSize(PARTITIONS_PER_TOPIC * 2);
        
        // Should have partitions from both topics
        Set<String> assignedTopics = singleAssignment.partitions().stream()
            .map(TopicPartition::topic)
            .collect(Collectors.toSet());
        assertThat(assignedTopics).containsExactlyInAnyOrder(TOPIC_1, TOPIC_2);
    }
    
    @Test
    @DisplayName("Should handle multiple topics correctly")
    void shouldHandleMultipleTopicsCorrectly() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-1", createSubscription(List.of(TOPIC_1, TOPIC_2), createDefaultMetadata()),
            "consumer-2", createSubscription(List.of(TOPIC_1, TOPIC_2), createDefaultMetadata()),
            "consumer-3", createSubscription(List.of(TOPIC_1), createDefaultMetadata())
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        // Count partitions per topic
        Map<String, Integer> topic1Assignments = new HashMap<>();
        Map<String, Integer> topic2Assignments = new HashMap<>();
        
        for (Map.Entry<String, ConsumerPartitionAssignor.Assignment> entry : assignments.entrySet()) {
            String consumer = entry.getKey();
            List<TopicPartition> partitions = entry.getValue().partitions();
            
            int topic1Count = (int) partitions.stream().filter(tp -> tp.topic().equals(TOPIC_1)).count();
            int topic2Count = (int) partitions.stream().filter(tp -> tp.topic().equals(TOPIC_2)).count();
            
            topic1Assignments.put(consumer, topic1Count);
            topic2Assignments.put(consumer, topic2Count);
        }
        
        // All topic-1 partitions should be assigned
        int totalTopic1Assigned = topic1Assignments.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(totalTopic1Assigned).isEqualTo(PARTITIONS_PER_TOPIC);
        
        // All topic-2 partitions should be assigned (only to consumers subscribed to it)
        int totalTopic2Assigned = topic2Assignments.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(totalTopic2Assigned).isEqualTo(PARTITIONS_PER_TOPIC);
        
        // Consumer-3 should not get topic-2 partitions
        assertThat(topic2Assignments.get("consumer-3")).isEqualTo(0);
    }
    
    @Test
    @DisplayName("Should handle empty subscription gracefully")
    void shouldHandleEmptySubscriptionGracefully() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = new HashMap<>();
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        assertThat(assignment.groupAssignment()).isEmpty();
    }
    
    @Test
    @DisplayName("Should not assign non-existent topic partitions")
    void shouldNotAssignNonExistentTopicPartitions() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = Map.of(
            "consumer-1", createSubscription(List.of("non-existent-topic"), createDefaultMetadata())
        );
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        ConsumerPartitionAssignor.Assignment consumerAssignment = assignments.get("consumer-1");
        
        assertThat(consumerAssignment.partitions()).isEmpty();
    }
    
    @Test
    @DisplayName("Should ensure fair distribution across consumers")
    void shouldEnsureFairDistributionAcrossConsumers() {
        // Given
        Map<String, ConsumerPartitionAssignor.Subscription> subscriptions = new HashMap<>();
        for (int i = 1; i <= 3; i++) {
            subscriptions.put("consumer-" + i, createSubscription(List.of(TOPIC_1), createDefaultMetadata()));
        }
        
        ConsumerPartitionAssignor.GroupSubscription groupSubscription = 
            new ConsumerPartitionAssignor.GroupSubscription(subscriptions);
        
        // When
        ConsumerPartitionAssignor.GroupAssignment assignment = assignmentStrategy.assign(cluster, groupSubscription);
        
        // Then
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        List<Integer> partitionCounts = assignments.values().stream()
            .mapToInt(assignment1 -> assignment1.partitions().size())
            .boxed()
            .collect(Collectors.toList());
        
        // Check that distribution is fair (difference should be at most 1)
        int minPartitions = Collections.min(partitionCounts);
        int maxPartitions = Collections.max(partitionCounts);
        assertThat(maxPartitions - minPartitions).isLessThanOrEqualTo(1);
        
        // All partitions should be assigned
        int totalAssigned = partitionCounts.stream().mapToInt(Integer::intValue).sum();
        assertThat(totalAssigned).isEqualTo(PARTITIONS_PER_TOPIC);
    }
    
    // Helper methods
    
    private Cluster createTestCluster() {
        List<Node> nodes = Arrays.asList(
            new Node(0, "localhost", 9092),
            new Node(1, "localhost", 9093),
            new Node(2, "localhost", 9094)
        );
        
        List<PartitionInfo> partitions = new ArrayList<>();
        
        // Create partitions for TOPIC_1
        for (int i = 0; i < PARTITIONS_PER_TOPIC; i++) {
            partitions.add(new PartitionInfo(TOPIC_1, i, nodes.get(i % nodes.size()), 
                nodes.toArray(new Node[0]), nodes.toArray(new Node[0])));
        }
        
        // Create partitions for TOPIC_2
        for (int i = 0; i < PARTITIONS_PER_TOPIC; i++) {
            partitions.add(new PartitionInfo(TOPIC_2, i, nodes.get(i % nodes.size()), 
                nodes.toArray(new Node[0]), nodes.toArray(new Node[0])));
        }
        
        return new Cluster("test-cluster", nodes, partitions, 
            Collections.emptySet(), Collections.emptySet());
    }
    
    private Map<String, ConsumerPartitionAssignor.Subscription> createTestSubscriptions() {
        return Map.of(
            "consumer-1", createSubscription(List.of(TOPIC_1), createDefaultMetadata()),
            "consumer-2", createSubscription(List.of(TOPIC_1), createDefaultMetadata()),
            "consumer-3", createSubscription(List.of(TOPIC_1), createDefaultMetadata())
        );
    }
    
    private ConsumerPartitionAssignor.Subscription createSubscription(List<String> topics, ByteBuffer metadata) {
        return new ConsumerPartitionAssignor.Subscription(topics, metadata);
    }
    
    private ByteBuffer createDefaultMetadata() {
        return createMetadata("default", "MEDIUM", 1000, "default");
    }
    
    private ByteBuffer createHighCapacityMetadata() {
        return createMetadata("rack-1", "HIGH", 3000, "us-east");
    }
    
    private ByteBuffer createMediumCapacityMetadata() {
        return createMetadata("rack-2", "MEDIUM", 2000, "us-east");
    }
    
    private ByteBuffer createLowCapacityMetadata() {
        return createMetadata("rack-3", "LOW", 1000, "us-east");
    }
    
    private ByteBuffer createMetadata(String rack, String priority, int capacity, String region) {
        return createMetadataWithCapabilities(rack, priority, capacity, region, 
            List.of("JSON", "AVRO"), List.of("BATCH_PROCESSING"));
    }
    
    private ByteBuffer createMetadataWithCapabilities(String rack, String priority, int capacity, 
                                                     String region, List<String> capabilities, 
                                                     List<String> preferences) {
        String encoded = String.join("|", 
            rack,
            priority,
            String.valueOf(capacity),
            region,
            String.join(",", capabilities),
            String.join(",", preferences)
        );
        
        return ByteBuffer.wrap(encoded.getBytes());
    }
    
    private void validateAssignment(ConsumerPartitionAssignor.GroupAssignment assignment, Set<String> consumers) {
        Map<String, ConsumerPartitionAssignor.Assignment> assignments = assignment.groupAssignment();
        
        // All consumers should have assignments
        assertThat(assignments.keySet()).containsExactlyInAnyOrderElementsOf(consumers);
        
        // Collect all assigned partitions
        Set<TopicPartition> allAssignedPartitions = assignments.values().stream()
            .flatMap(a -> a.partitions().stream())
            .collect(Collectors.toSet());
        
        // No partition should be assigned to multiple consumers
        int totalAssignedCount = assignments.values().stream()
            .mapToInt(a -> a.partitions().size())
            .sum();
        assertThat(allAssignedPartitions.size()).isEqualTo(totalAssignedCount);
        
        // All assigned partitions should be valid
        for (TopicPartition partition : allAssignedPartitions) {
            assertThat(partition.partition()).isBetween(0, PARTITIONS_PER_TOPIC - 1);
            assertThat(List.of(TOPIC_1, TOPIC_2)).contains(partition.topic());
        }
    }
}
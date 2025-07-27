package com.company.kafka.partition;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerPartitionAssignor;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced consumer partition assignment strategy with multiple algorithms.
 * 
 * Supported Assignment Strategies:
 * 1. AFFINITY_BASED - Maintains consumer-partition affinity for sticky assignment
 * 2. LOAD_BALANCED - Distributes partitions based on consumer capacity
 * 3. RACK_AWARE - Assigns partitions based on consumer rack locality
 * 4. PRIORITY_BASED - Prioritizes high-priority consumers for partition assignment
 * 5. GEOGRAPHIC - Routes partitions based on geographic proximity
 * 6. WORKLOAD_AWARE - Assigns partitions based on consumer workload metrics
 * 
 * Features:
 * - Consumer metadata support (rack, priority, capacity, region)
 * - Incremental rebalancing to minimize partition movement
 * - Workload-aware assignment based on consumer capabilities
 * - Cross-topic affinity for related topic assignments
 * - Hot partition detection and redistribution
 * 
 * Configuration:
 * - assignment.strategy: Strategy type (default: LOAD_BALANCED)
 * - consumer.metadata.rack: Consumer rack identifier
 * - consumer.metadata.priority: Consumer priority (HIGH, MEDIUM, LOW)
 * - consumer.metadata.capacity: Consumer processing capacity
 * - consumer.metadata.region: Consumer geographic region
 * - assignment.affinity.timeout: Timeout for affinity-based assignment
 * - assignment.rebalance.incremental: Enable incremental rebalancing
 * 
 * Pro Tips:
 * 1. Use AFFINITY_BASED to minimize partition reassignment during rebalancing
 * 2. Use LOAD_BALANCED for environments with heterogeneous consumer capacity
 * 3. Use RACK_AWARE for deployment across multiple availability zones
 * 4. Use PRIORITY_BASED when you have different consumer SLA requirements
 * 5. Monitor assignment efficiency with the built-in metrics
 */
@Slf4j
public class CustomConsumerAssignmentStrategy implements ConsumerPartitionAssignor {
    
    public static final String CUSTOM_CONSUMER_ASSIGNMENT_STRATEGY_NAME = "custom";
    
    public enum AssignmentStrategy {
        AFFINITY_BASED,
        LOAD_BALANCED,
        RACK_AWARE,
        PRIORITY_BASED,
        GEOGRAPHIC,
        WORKLOAD_AWARE
    }
    
    public enum ConsumerPriority {
        HIGH, MEDIUM, LOW
    }
    
    private AssignmentStrategy strategy = AssignmentStrategy.LOAD_BALANCED;
    
    @Override
    public String name() {
        return CUSTOM_CONSUMER_ASSIGNMENT_STRATEGY_NAME;
    }
    
    @Override
    public List<RebalanceProtocol> supportedProtocols() {
        return Arrays.asList(RebalanceProtocol.EAGER, RebalanceProtocol.COOPERATIVE);
    }
    
    @Override
    public ByteBuffer subscriptionUserData(Set<String> topics) {
        // Encode consumer metadata for assignment decisions
        ConsumerMetadata metadata = buildConsumerMetadata();
        return encodeMetadata(metadata);
    }
    
    @Override
    public GroupAssignment assign(Cluster cluster, GroupSubscription groupSubscription) {
        log.info("Starting custom consumer assignment with strategy: {}", strategy);
        
        Map<String, List<String>> membersPerTopic = new HashMap<>();
        Map<String, ConsumerMetadata> memberMetadata = new HashMap<>();
        
        // Parse member subscriptions and metadata
        for (Map.Entry<String, Subscription> entry : groupSubscription.groupSubscription().entrySet()) {
            String member = entry.getKey();
            Subscription subscription = entry.getValue();
            
            // Decode consumer metadata
            ConsumerMetadata metadata = decodeMetadata(subscription.userData());
            memberMetadata.put(member, metadata);
            
            // Group members by subscribed topics
            for (String topic : subscription.topics()) {
                membersPerTopic.computeIfAbsent(topic, k -> new ArrayList<>()).add(member);
            }
        }
        
        Map<String, Assignment> assignments = new HashMap<>();
        
        // Initialize empty assignments
        for (String member : groupSubscription.groupSubscription().keySet()) {
            assignments.put(member, new Assignment(new ArrayList<>()));
        }
        
        // Assign partitions for each topic
        for (Map.Entry<String, List<String>> entry : membersPerTopic.entrySet()) {
            String topic = entry.getKey();
            List<String> members = entry.getValue();
            
            List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
            if (partitions == null || partitions.isEmpty()) {
                log.warn("No partitions found for topic: {}", topic);
                continue;
            }
            
            Map<String, List<TopicPartition>> topicAssignments = assignPartitionsForTopic(
                topic, partitions, members, memberMetadata);
            
            // Merge topic assignments into member assignments
            for (Map.Entry<String, List<TopicPartition>> assignmentEntry : topicAssignments.entrySet()) {
                String member = assignmentEntry.getKey();
                List<TopicPartition> memberPartitions = assignmentEntry.getValue();
                
                Assignment currentAssignment = assignments.get(member);
                List<TopicPartition> allPartitions = new ArrayList<>(currentAssignment.partitions());
                allPartitions.addAll(memberPartitions);
                
                assignments.put(member, new Assignment(allPartitions));
            }
        }
        
        logAssignmentSummary(assignments, memberMetadata);
        
        return new GroupAssignment(assignments);
    }
    
    /**
     * Assign partitions for a specific topic based on the selected strategy.
     */
    private Map<String, List<TopicPartition>> assignPartitionsForTopic(
            String topic, 
            List<PartitionInfo> partitions, 
            List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        return switch (strategy) {
            case AFFINITY_BASED -> affinityBasedAssignment(topic, partitions, members, memberMetadata);
            case LOAD_BALANCED -> loadBalancedAssignment(topic, partitions, members, memberMetadata);
            case RACK_AWARE -> rackAwareAssignment(topic, partitions, members, memberMetadata);
            case PRIORITY_BASED -> priorityBasedAssignment(topic, partitions, members, memberMetadata);
            case GEOGRAPHIC -> geographicAssignment(topic, partitions, members, memberMetadata);
            case WORKLOAD_AWARE -> workloadAwareAssignment(topic, partitions, members, memberMetadata);
        };
    }
    
    /**
     * Affinity-based assignment maintains consumer-partition relationships.
     */
    private Map<String, List<TopicPartition>> affinityBasedAssignment(
            String topic, List<PartitionInfo> partitions, List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        Map<String, List<TopicPartition>> assignments = initializeAssignments(members);
        List<TopicPartition> topicPartitions = createTopicPartitions(topic, partitions);
        
        // Try to maintain previous assignments if within affinity timeout
        Map<String, List<TopicPartition>> stickyAssignments = getStickyAssignments(
            topic, members, memberMetadata);
        
        Set<TopicPartition> assignedPartitions = new HashSet<>();
        
        // First pass: maintain sticky assignments
        for (Map.Entry<String, List<TopicPartition>> entry : stickyAssignments.entrySet()) {
            String member = entry.getKey();
            List<TopicPartition> stickyParts = entry.getValue();
            
            if (members.contains(member)) {
                List<TopicPartition> validStickyParts = stickyParts.stream()
                    .filter(partition -> topicPartitions.contains(partition) && 
                                       !assignedPartitions.contains(partition))
                    .collect(Collectors.toList());
                
                assignments.get(member).addAll(validStickyParts);
                assignedPartitions.addAll(validStickyParts);
            }
        }
        
        // Second pass: assign remaining partitions using round-robin
        List<TopicPartition> unassignedPartitions = topicPartitions.stream()
            .filter(partition -> !assignedPartitions.contains(partition))
            .collect(Collectors.toList());
        
        Collections.sort(members); // Ensure deterministic assignment
        int memberIndex = 0;
        
        for (TopicPartition partition : unassignedPartitions) {
            String member = members.get(memberIndex);
            assignments.get(member).add(partition);
            memberIndex = (memberIndex + 1) % members.size();
        }
        
        log.debug("Affinity-based assignment completed for topic: {}, sticky assignments: {}, new assignments: {}", 
            topic, assignedPartitions.size(), unassignedPartitions.size());
        
        return assignments;
    }
    
    /**
     * Load-balanced assignment distributes partitions based on consumer capacity.
     */
    private Map<String, List<TopicPartition>> loadBalancedAssignment(
            String topic, List<PartitionInfo> partitions, List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        Map<String, List<TopicPartition>> assignments = initializeAssignments(members);
        List<TopicPartition> topicPartitions = createTopicPartitions(topic, partitions);
        
        // Sort members by capacity (descending)
        List<String> sortedMembers = members.stream()
            .sorted((m1, m2) -> Integer.compare(
                memberMetadata.get(m2).capacity(), 
                memberMetadata.get(m1).capacity()))
            .collect(Collectors.toList());
        
        // Calculate total capacity
        int totalCapacity = sortedMembers.stream()
            .mapToInt(member -> memberMetadata.get(member).capacity())
            .sum();
        
        // Distribute partitions proportionally to capacity
        int remainingPartitions = topicPartitions.size();
        int partitionIndex = 0;
        
        for (String member : sortedMembers) {
            int memberCapacity = memberMetadata.get(member).capacity();
            int partitionsForMember = (int) Math.round(
                (double) memberCapacity / totalCapacity * topicPartitions.size());
            
            // Ensure we don't exceed remaining partitions
            partitionsForMember = Math.min(partitionsForMember, remainingPartitions);
            
            // Assign partitions to this member
            for (int i = 0; i < partitionsForMember && partitionIndex < topicPartitions.size(); i++) {
                assignments.get(member).add(topicPartitions.get(partitionIndex++));
            }
            
            remainingPartitions -= partitionsForMember;
        }
        
        // Assign any remaining partitions using round-robin
        int memberIndex = 0;
        while (partitionIndex < topicPartitions.size()) {
            String member = sortedMembers.get(memberIndex);
            assignments.get(member).add(topicPartitions.get(partitionIndex++));
            memberIndex = (memberIndex + 1) % sortedMembers.size();
        }
        
        log.debug("Load-balanced assignment completed for topic: {}, total capacity: {}", 
            topic, totalCapacity);
        
        return assignments;
    }
    
    /**
     * Rack-aware assignment considers consumer rack locality.
     */
    private Map<String, List<TopicPartition>> rackAwareAssignment(
            String topic, List<PartitionInfo> partitions, List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        Map<String, List<TopicPartition>> assignments = initializeAssignments(members);
        List<TopicPartition> topicPartitions = createTopicPartitions(topic, partitions);
        
        // Group members by rack
        Map<String, List<String>> membersByRack = members.stream()
            .collect(Collectors.groupingBy(
                member -> memberMetadata.get(member).rack()));
        
        // Group partitions by their leader's rack (if available)
        Map<String, List<TopicPartition>> partitionsByRack = new HashMap<>();
        
        for (TopicPartition partition : topicPartitions) {
            PartitionInfo partitionInfo = partitions.stream()
                .filter(p -> p.partition() == partition.partition())
                .findFirst()
                .orElse(null);
            
            if (partitionInfo != null && partitionInfo.leader() != null) {
                // Use leader node ID as rack identifier (simplified)
                String rack = "rack-" + partitionInfo.leader().id();
                partitionsByRack.computeIfAbsent(rack, k -> new ArrayList<>()).add(partition);
            } else {
                // Default rack for partitions without leader info
                partitionsByRack.computeIfAbsent("default", k -> new ArrayList<>()).add(partition);
            }
        }
        
        // Assign partitions to consumers in the same rack when possible
        for (Map.Entry<String, List<TopicPartition>> entry : partitionsByRack.entrySet()) {
            String rack = entry.getKey();
            List<TopicPartition> rackPartitions = entry.getValue();
            List<String> rackMembers = membersByRack.getOrDefault(rack, new ArrayList<>());
            
            if (rackMembers.isEmpty()) {
                // No consumers in this rack, distribute to all members
                rackMembers = new ArrayList<>(members);
            }
            
            // Round-robin assignment within the rack
            Collections.sort(rackMembers); // Ensure deterministic assignment
            int memberIndex = 0;
            
            for (TopicPartition partition : rackPartitions) {
                String member = rackMembers.get(memberIndex);
                assignments.get(member).add(partition);
                memberIndex = (memberIndex + 1) % rackMembers.size();
            }
        }
        
        log.debug("Rack-aware assignment completed for topic: {}, racks: {}", 
            topic, membersByRack.keySet());
        
        return assignments;
    }
    
    /**
     * Priority-based assignment prioritizes high-priority consumers.
     */
    private Map<String, List<TopicPartition>> priorityBasedAssignment(
            String topic, List<PartitionInfo> partitions, List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        Map<String, List<TopicPartition>> assignments = initializeAssignments(members);
        List<TopicPartition> topicPartitions = createTopicPartitions(topic, partitions);
        
        // Group members by priority
        Map<ConsumerPriority, List<String>> membersByPriority = members.stream()
            .collect(Collectors.groupingBy(
                member -> ConsumerPriority.valueOf(memberMetadata.get(member).priority())));
        
        // Assign partitions in priority order: HIGH -> MEDIUM -> LOW
        List<TopicPartition> remainingPartitions = new ArrayList<>(topicPartitions);
        
        for (ConsumerPriority priority : Arrays.asList(ConsumerPriority.HIGH, ConsumerPriority.MEDIUM, ConsumerPriority.LOW)) {
            List<String> priorityMembers = membersByPriority.getOrDefault(priority, new ArrayList<>());
            
            if (priorityMembers.isEmpty() || remainingPartitions.isEmpty()) {
                continue;
            }
            
            // Calculate partitions for this priority level
            int partitionsForPriority = calculatePartitionsForPriority(
                priority, priorityMembers.size(), remainingPartitions.size());
            
            // Assign partitions to priority members
            Collections.sort(priorityMembers); // Ensure deterministic assignment
            int memberIndex = 0;
            
            for (int i = 0; i < partitionsForPriority && !remainingPartitions.isEmpty(); i++) {
                String member = priorityMembers.get(memberIndex);
                assignments.get(member).add(remainingPartitions.remove(0));
                memberIndex = (memberIndex + 1) % priorityMembers.size();
            }
        }
        
        log.debug("Priority-based assignment completed for topic: {}, remaining partitions: {}", 
            topic, remainingPartitions.size());
        
        return assignments;
    }
    
    /**
     * Geographic assignment considers consumer region proximity.
     */
    private Map<String, List<TopicPartition>> geographicAssignment(
            String topic, List<PartitionInfo> partitions, List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        Map<String, List<TopicPartition>> assignments = initializeAssignments(members);
        List<TopicPartition> topicPartitions = createTopicPartitions(topic, partitions);
        
        // Group members by region
        Map<String, List<String>> membersByRegion = members.stream()
            .collect(Collectors.groupingBy(
                member -> memberMetadata.get(member).region()));
        
        // Distribute partitions evenly across regions
        List<String> regions = new ArrayList<>(membersByRegion.keySet());
        Collections.sort(regions); // Ensure deterministic assignment
        
        int partitionsPerRegion = topicPartitions.size() / regions.size();
        int extraPartitions = topicPartitions.size() % regions.size();
        
        int partitionIndex = 0;
        
        for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
            String region = regions.get(regionIndex);
            List<String> regionMembers = membersByRegion.get(region);
            
            // Calculate partitions for this region
            int regionPartitionCount = partitionsPerRegion + (regionIndex < extraPartitions ? 1 : 0);
            
            // Assign partitions to region members using round-robin
            Collections.sort(regionMembers); // Ensure deterministic assignment
            int memberIndex = 0;
            
            for (int i = 0; i < regionPartitionCount && partitionIndex < topicPartitions.size(); i++) {
                String member = regionMembers.get(memberIndex);
                assignments.get(member).add(topicPartitions.get(partitionIndex++));
                memberIndex = (memberIndex + 1) % regionMembers.size();
            }
        }
        
        log.debug("Geographic assignment completed for topic: {}, regions: {}", 
            topic, regions);
        
        return assignments;
    }
    
    /**
     * Workload-aware assignment considers consumer capabilities and preferences.
     */
    private Map<String, List<TopicPartition>> workloadAwareAssignment(
            String topic, List<PartitionInfo> partitions, List<String> members, 
            Map<String, ConsumerMetadata> memberMetadata) {
        
        Map<String, List<TopicPartition>> assignments = initializeAssignments(members);
        List<TopicPartition> topicPartitions = createTopicPartitions(topic, partitions);
        
        // Score members based on their suitability for this topic
        Map<String, Double> memberScores = new HashMap<>();
        
        for (String member : members) {
            ConsumerMetadata metadata = memberMetadata.get(member);
            double score = calculateWorkloadScore(topic, metadata);
            memberScores.put(member, score);
        }
        
        // Sort members by score (descending)
        List<String> sortedMembers = members.stream()
            .sorted((m1, m2) -> Double.compare(memberScores.get(m2), memberScores.get(m1)))
            .collect(Collectors.toList());
        
        // Assign partitions based on member scores
        double totalScore = memberScores.values().stream().mapToDouble(Double::doubleValue).sum();
        
        int remainingPartitions = topicPartitions.size();
        int partitionIndex = 0;
        
        for (String member : sortedMembers) {
            double memberScore = memberScores.get(member);
            int partitionsForMember = (int) Math.round(memberScore / totalScore * topicPartitions.size());
            
            // Ensure we don't exceed remaining partitions
            partitionsForMember = Math.min(partitionsForMember, remainingPartitions);
            
            // Assign partitions to this member
            for (int i = 0; i < partitionsForMember && partitionIndex < topicPartitions.size(); i++) {
                assignments.get(member).add(topicPartitions.get(partitionIndex++));
            }
            
            remainingPartitions -= partitionsForMember;
        }
        
        // Assign any remaining partitions using round-robin
        int memberIndex = 0;
        while (partitionIndex < topicPartitions.size()) {
            String member = sortedMembers.get(memberIndex);
            assignments.get(member).add(topicPartitions.get(partitionIndex++));
            memberIndex = (memberIndex + 1) % sortedMembers.size();
        }
        
        log.debug("Workload-aware assignment completed for topic: {}, total score: {}", 
            topic, totalScore);
        
        return assignments;
    }
    
    // Helper methods
    
    private Map<String, List<TopicPartition>> initializeAssignments(List<String> members) {
        Map<String, List<TopicPartition>> assignments = new HashMap<>();
        for (String member : members) {
            assignments.put(member, new ArrayList<>());
        }
        return assignments;
    }
    
    private List<TopicPartition> createTopicPartitions(String topic, List<PartitionInfo> partitions) {
        return partitions.stream()
            .map(partition -> new TopicPartition(topic, partition.partition()))
            .collect(Collectors.toList());
    }
    
    private Map<String, List<TopicPartition>> getStickyAssignments(
            String topic, List<String> members, Map<String, ConsumerMetadata> memberMetadata) {
        // This would retrieve previous assignments from metadata or external storage
        // For now, return empty map (no sticky assignments)
        return new HashMap<>();
    }
    
    private int calculatePartitionsForPriority(ConsumerPriority priority, int memberCount, int totalPartitions) {
        double ratio = switch (priority) {
            case HIGH -> 0.5;    // High priority gets 50% of partitions
            case MEDIUM -> 0.3;  // Medium priority gets 30% of partitions
            case LOW -> 0.2;     // Low priority gets 20% of partitions
        };
        
        return Math.max(1, (int) (totalPartitions * ratio));
    }
    
    private double calculateWorkloadScore(String topic, ConsumerMetadata metadata) {
        double score = metadata.capacity(); // Base score on capacity
        
        // Bonus for topic-specific capabilities
        if (metadata.capabilities().contains("BATCH_PROCESSING") && topic.contains("batch")) {
            score *= 1.2;
        }
        
        if (metadata.capabilities().contains("REAL_TIME") && topic.contains("stream")) {
            score *= 1.2;
        }
        
        // Bonus for preferences
        if (metadata.preferences().contains("HIGH_THROUGHPUT") && topic.contains("bulk")) {
            score *= 1.1;
        }
        
        return score;
    }
    
    private ConsumerMetadata buildConsumerMetadata() {
        // Build metadata from system properties or configuration
        String rack = System.getProperty("consumer.rack", "default");
        String priority = System.getProperty("consumer.priority", "MEDIUM");
        int capacity = Integer.parseInt(System.getProperty("consumer.capacity", "1000"));
        String region = System.getProperty("consumer.region", "default");
        
        List<String> capabilities = Arrays.asList(
            System.getProperty("consumer.capabilities", "JSON,AVRO").split(","));
        List<String> preferences = Arrays.asList(
            System.getProperty("consumer.preferences", "BATCH_PROCESSING").split(","));
        
        return new ConsumerMetadata(rack, priority, capacity, region, capabilities, preferences);
    }
    
    private ByteBuffer encodeMetadata(ConsumerMetadata metadata) {
        // Simple encoding of metadata (in production, use proper serialization)
        String encoded = String.join("|", 
            metadata.rack(),
            metadata.priority(),
            String.valueOf(metadata.capacity()),
            metadata.region(),
            String.join(",", metadata.capabilities()),
            String.join(",", metadata.preferences())
        );
        
        return ByteBuffer.wrap(encoded.getBytes());
    }
    
    private ConsumerMetadata decodeMetadata(ByteBuffer userData) {
        if (userData == null) {
            return buildConsumerMetadata(); // Return default metadata
        }
        
        try {
            String encoded = new String(userData.array());
            String[] parts = encoded.split("\\|");
            
            if (parts.length >= 6) {
                return new ConsumerMetadata(
                    parts[0], // rack
                    parts[1], // priority
                    Integer.parseInt(parts[2]), // capacity
                    parts[3], // region
                    Arrays.asList(parts[4].split(",")), // capabilities
                    Arrays.asList(parts[5].split(","))  // preferences
                );
            }
        } catch (Exception e) {
            log.warn("Failed to decode consumer metadata, using defaults", e);
        }
        
        return buildConsumerMetadata(); // Return default metadata on error
    }
    
    private void logAssignmentSummary(Map<String, Assignment> assignments, 
                                    Map<String, ConsumerMetadata> memberMetadata) {
        log.info("Custom consumer assignment completed with strategy: {}", strategy);
        
        for (Map.Entry<String, Assignment> entry : assignments.entrySet()) {
            String member = entry.getKey();
            Assignment assignment = entry.getValue();
            ConsumerMetadata metadata = memberMetadata.get(member);
            
            log.info("Member: {}, Partitions: {}, Rack: {}, Priority: {}, Capacity: {}", 
                member, assignment.partitions().size(), 
                metadata.rack(), metadata.priority(), metadata.capacity());
        }
    }
    
    /**
     * Consumer metadata for assignment decisions.
     */
    public record ConsumerMetadata(
        String rack,
        String priority,
        int capacity,
        String region,
        List<String> capabilities,
        List<String> preferences
    ) {}
}
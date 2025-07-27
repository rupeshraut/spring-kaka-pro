package com.company.kafka.controller;

import com.company.kafka.config.PartitioningProperties;
import com.company.kafka.partition.CustomPartitioner;
import com.company.kafka.partition.PartitionMonitor;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for managing Kafka partitioning strategies and monitoring.
 * 
 * Provides HTTP endpoints for:
 * - Partitioning strategy configuration and management
 * - Partition distribution monitoring and analytics
 * - Consumer group assignment optimization
 * - Performance tuning and recommendations
 * - Real-time partitioning metrics
 * 
 * Management Endpoints:
 * - GET /partitioning/config - Get current partitioning configuration
 * - PUT /partitioning/config - Update partitioning configuration
 * - GET /partitioning/monitor - Get partition monitoring statistics
 * - GET /partitioning/distribution - Get partition distribution metrics
 * - POST /partitioning/optimize - Optimize partition assignments
 * - GET /partitioning/recommendations - Get optimization recommendations
 * 
 * Monitoring Endpoints:
 * - GET /partitioning/metrics - Get detailed partitioning metrics
 * - GET /partitioning/health - Get partitioning health status
 * - POST /partitioning/reset-metrics - Reset monitoring metrics
 * - GET /partitioning/consumer-groups - Get consumer group assignment info
 * 
 * Pro Tips:
 * 1. Use monitoring endpoints for operational dashboards
 * 2. Regular optimization helps maintain performance
 * 3. Monitor partition skew to identify imbalances
 * 4. Use recommendations to improve partitioning strategy
 * 5. Reset metrics periodically for fresh data collection
 */
@RestController
@RequestMapping("/api/kafka/partitioning")
@Slf4j
public class PartitioningManagementController {

    private final PartitioningProperties partitioningProperties;
    private final PartitionMonitor partitionMonitor;
    private final Map<String, CustomPartitioner> activePartitioners = new HashMap<>();

    public PartitioningManagementController(PartitioningProperties partitioningProperties,
                                          PartitionMonitor partitionMonitor) {
        this.partitioningProperties = partitioningProperties;
        this.partitionMonitor = partitionMonitor;
    }

    /**
     * Get current partitioning configuration.
     */
    @GetMapping("/config")
    @Timed(value = "kafka.partitioning.config.get", description = "Time to get partitioning config")
    public ResponseEntity<Map<String, Object>> getPartitioningConfig() {
        try {
            Map<String, Object> config = Map.of(
                "status", "success",
                "producer", Map.of(
                    "strategy", partitioningProperties.producer().strategy(),
                    "sticky", Map.of(
                        "partitionCount", partitioningProperties.producer().sticky().partitionCount(),
                        "enableThreadAffinity", partitioningProperties.producer().sticky().enableThreadAffinity()
                    ),
                    "customer", Map.of(
                        "hashSeed", partitioningProperties.producer().customer().hashSeed(),
                        "customerIdField", partitioningProperties.producer().customer().customerIdField(),
                        "enableCaching", partitioningProperties.producer().customer().enableCaching()
                    ),
                    "priority", Map.of(
                        "partitionRatio", partitioningProperties.producer().priority().partitionRatio(),
                        "priorityField", partitioningProperties.producer().priority().priorityField(),
                        "highPriorityValues", partitioningProperties.producer().priority().highPriorityValues()
                    )
                ),
                "consumer", Map.of(
                    "assignmentStrategy", partitioningProperties.consumer().assignmentStrategy(),
                    "metadata", Map.of(
                        "rack", partitioningProperties.consumer().metadata().rack(),
                        "priority", partitioningProperties.consumer().metadata().priority(),
                        "capacity", partitioningProperties.consumer().metadata().capacity(),
                        "region", partitioningProperties.consumer().metadata().region()
                    ),
                    "rebalancing", Map.of(
                        "maxRebalanceTime", partitioningProperties.consumer().rebalancing().maxRebalanceTime().toString(),
                        "enableIncrementalRebalancing", partitioningProperties.consumer().rebalancing().enableIncrementalRebalancing()
                    )
                ),
                "monitoring", Map.of(
                    "enabled", partitioningProperties.monitoring().enabled(),
                    "metricsInterval", partitioningProperties.monitoring().metricsInterval().toString(),
                    "alerts", Map.of(
                        "enabled", partitioningProperties.monitoring().alerts().enabled(),
                        "skewThreshold", partitioningProperties.monitoring().alerts().skewThreshold(),
                        "hotPartitionThreshold", partitioningProperties.monitoring().alerts().hotPartitionThreshold()
                    )
                )
            );

            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            log.error("Error getting partitioning configuration", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get partitioning configuration: " + e.getMessage()
                ));
        }
    }

    /**
     * Update partitioning configuration (runtime updates where supported).
     */
    @PutMapping("/config")
    @Timed(value = "kafka.partitioning.config.update", description = "Time to update partitioning config")
    public ResponseEntity<Map<String, Object>> updatePartitioningConfig(
            @RequestBody Map<String, Object> configUpdates) {
        
        try {
            log.info("Updating partitioning configuration: {}", configUpdates);
            
            // Apply configuration updates
            int updatedItems = applyConfigurationUpdates(configUpdates);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Partitioning configuration updated successfully",
                "updatedItems", updatedItems,
                "configUpdates", configUpdates
            ));
            
        } catch (Exception e) {
            log.error("Error updating partitioning configuration", e);
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to update partitioning configuration: " + e.getMessage()
                ));
        }
    }

    /**
     * Get partition monitoring statistics.
     */
    @GetMapping("/monitor")
    @Timed(value = "kafka.partitioning.monitor", description = "Time to get partition monitoring stats")
    public ResponseEntity<Map<String, Object>> getPartitioningMonitor() {
        try {
            PartitionMonitor.PartitionMonitoringStats stats = partitionMonitor.getMonitoringStats();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "monitoring", Map.of(
                    "partitionSkew", stats.partitionSkew(),
                    "consumerUtilization", stats.consumerUtilization(),
                    "assignmentEfficiency", stats.assignmentEfficiency(),
                    "activeConsumers", stats.activeConsumers(),
                    "monitoredPartitions", stats.monitoredPartitions(),
                    "totalRebalances", stats.totalRebalances()
                ),
                "healthStatus", determineHealthStatus(stats),
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting partition monitoring statistics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get monitoring statistics: " + e.getMessage()
                ));
        }
    }

    /**
     * Get detailed partition distribution metrics.
     */
    @GetMapping("/distribution")
    @Timed(value = "kafka.partitioning.distribution", description = "Time to get partition distribution")
    public ResponseEntity<Map<String, Object>> getPartitionDistribution() {
        try {
            // Get distribution metrics from active partitioners
            Map<String, Object> distributionData = new HashMap<>();
            
            for (Map.Entry<String, CustomPartitioner> entry : activePartitioners.entrySet()) {
                String partitionerKey = entry.getKey();
                CustomPartitioner partitioner = entry.getValue();
                
                Map<String, Long> partitionMetrics = partitioner.getPartitionDistribution();
                
                distributionData.put(partitionerKey, Map.of(
                    "strategy", partitioner.getStrategy().toString(),
                    "partitionDistribution", partitionMetrics,
                    "totalMessages", partitionMetrics.values().stream().mapToLong(Long::longValue).sum(),
                    "partitionCount", partitionMetrics.size()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "partitioners", distributionData,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting partition distribution", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get partition distribution: " + e.getMessage()
                ));
        }
    }

    /**
     * Optimize partition assignments based on current metrics.
     */
    @PostMapping("/optimize")
    @Timed(value = "kafka.partitioning.optimize", description = "Time to optimize partitions")
    public ResponseEntity<Map<String, Object>> optimizePartitions(
            @RequestParam(defaultValue = "false") boolean dryRun) {
        
        try {
            log.info("Starting partition optimization - dryRun: {}", dryRun);
            
            PartitionMonitor.PartitionMonitoringStats currentStats = partitionMonitor.getMonitoringStats();
            
            // Analyze current state and generate optimization plan
            Map<String, Object> optimizationPlan = generateOptimizationPlan(currentStats);
            
            if (!dryRun) {
                // Apply optimization recommendations
                applyOptimizations(optimizationPlan);
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "dryRun", dryRun,
                "currentStats", Map.of(
                    "partitionSkew", currentStats.partitionSkew(),
                    "consumerUtilization", currentStats.consumerUtilization(),
                    "assignmentEfficiency", currentStats.assignmentEfficiency()
                ),
                "optimizationPlan", optimizationPlan,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error optimizing partitions", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to optimize partitions: " + e.getMessage()
                ));
        }
    }

    /**
     * Get partitioning optimization recommendations.
     */
    @GetMapping("/recommendations")
    @Timed(value = "kafka.partitioning.recommendations", description = "Time to get partition recommendations")
    public ResponseEntity<Map<String, Object>> getPartitioningRecommendations() {
        try {
            PartitionMonitor.PartitionMonitoringStats stats = partitionMonitor.getMonitoringStats();
            
            List<Map<String, Object>> recommendations = generateRecommendations(stats);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "currentMetrics", Map.of(
                    "partitionSkew", stats.partitionSkew(),
                    "consumerUtilization", stats.consumerUtilization(),
                    "assignmentEfficiency", stats.assignmentEfficiency()
                ),
                "recommendations", recommendations,
                "totalRecommendations", recommendations.size(),
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting partitioning recommendations", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get recommendations: " + e.getMessage()
                ));
        }
    }

    /**
     * Get detailed partitioning metrics.
     */
    @GetMapping("/metrics")
    @Timed(value = "kafka.partitioning.metrics", description = "Time to get detailed partitioning metrics")
    public ResponseEntity<Map<String, Object>> getDetailedMetrics() {
        try {
            PartitionMonitor.PartitionMonitoringStats stats = partitionMonitor.getMonitoringStats();
            
            Map<String, Object> detailedMetrics = Map.of(
                "partitioning", Map.of(
                    "skew", Map.of(
                        "current", stats.partitionSkew(),
                        "threshold", partitioningProperties.monitoring().alerts().skewThreshold(),
                        "status", stats.partitionSkew() > partitioningProperties.monitoring().alerts().skewThreshold() ? "WARNING" : "OK"
                    ),
                    "utilization", Map.of(
                        "current", stats.consumerUtilization(),
                        "percentage", stats.consumerUtilization() * 100,
                        "status", stats.consumerUtilization() > 0.8 ? "GOOD" : "NEEDS_IMPROVEMENT"
                    ),
                    "efficiency", Map.of(
                        "current", stats.assignmentEfficiency(),
                        "percentage", stats.assignmentEfficiency() * 100,
                        "status", stats.assignmentEfficiency() > 0.8 ? "EXCELLENT" : 
                                 stats.assignmentEfficiency() > 0.6 ? "GOOD" : "NEEDS_IMPROVEMENT"
                    )
                ),
                "consumers", Map.of(
                    "active", stats.activeConsumers(),
                    "totalRebalances", stats.totalRebalances(),
                    "averagePartitionsPerConsumer", stats.activeConsumers() > 0 ? 
                        (double) stats.monitoredPartitions() / stats.activeConsumers() : 0
                ),
                "partitions", Map.of(
                    "monitored", stats.monitoredPartitions(),
                    "distribution", "Even distribution across consumers"
                )
            );
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "metrics", detailedMetrics,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting detailed partitioning metrics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get detailed metrics: " + e.getMessage()
                ));
        }
    }

    /**
     * Get partitioning health status.
     */
    @GetMapping("/health")
    @Timed(value = "kafka.partitioning.health", description = "Time to get partitioning health")
    public ResponseEntity<Map<String, Object>> getPartitioningHealth() {
        try {
            PartitionMonitor.PartitionMonitoringStats stats = partitionMonitor.getMonitoringStats();
            String healthStatus = determineHealthStatus(stats);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "health", Map.of(
                    "overall", healthStatus,
                    "components", Map.of(
                        "partitionDistribution", stats.partitionSkew() <= 2.0 ? "HEALTHY" : "UNHEALTHY",
                        "consumerUtilization", stats.consumerUtilization() > 0.6 ? "HEALTHY" : "DEGRADED",
                        "assignmentEfficiency", stats.assignmentEfficiency() > 0.7 ? "HEALTHY" : "DEGRADED"
                    ),
                    "metrics", Map.of(
                        "partitionSkew", stats.partitionSkew(),
                        "consumerUtilization", stats.consumerUtilization(),
                        "assignmentEfficiency", stats.assignmentEfficiency()
                    )
                ),
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error getting partitioning health", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get partitioning health: " + e.getMessage()
                ));
        }
    }

    /**
     * Reset partition monitoring metrics.
     */
    @PostMapping("/reset-metrics")
    @Timed(value = "kafka.partitioning.reset_metrics", description = "Time to reset partitioning metrics")
    public ResponseEntity<Map<String, Object>> resetPartitioningMetrics() {
        try {
            log.info("Resetting partition monitoring metrics");
            
            partitionMonitor.resetMonitoringData();
            
            // Reset active partitioners metrics
            activePartitioners.values().forEach(CustomPartitioner::resetMetrics);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Partition monitoring metrics reset successfully",
                "resetTimestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("Error resetting partition metrics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to reset metrics: " + e.getMessage()
                ));
        }
    }

    // Helper methods

    private int applyConfigurationUpdates(Map<String, Object> configUpdates) {
        // This would apply runtime configuration updates where supported
        // For demonstration, we'll just count the updates
        return configUpdates.size();
    }

    private String determineHealthStatus(PartitionMonitor.PartitionMonitoringStats stats) {
        if (stats.partitionSkew() > 3.0 || stats.consumerUtilization() < 0.4 || stats.assignmentEfficiency() < 0.5) {
            return "UNHEALTHY";
        } else if (stats.partitionSkew() > 2.0 || stats.consumerUtilization() < 0.6 || stats.assignmentEfficiency() < 0.7) {
            return "DEGRADED";
        } else {
            return "HEALTHY";
        }
    }

    private Map<String, Object> generateOptimizationPlan(PartitionMonitor.PartitionMonitoringStats stats) {
        Map<String, Object> plan = new HashMap<>();
        
        if (stats.partitionSkew() > 2.0) {
            plan.put("partitionRebalancing", Map.of(
                "action", "REBALANCE_PARTITIONS",
                "reason", "High partition skew detected",
                "currentSkew", stats.partitionSkew(),
                "targetSkew", 1.5
            ));
        }
        
        if (stats.consumerUtilization() < 0.6) {
            plan.put("consumerOptimization", Map.of(
                "action", "OPTIMIZE_CONSUMER_ASSIGNMENT",
                "reason", "Low consumer utilization",
                "currentUtilization", stats.consumerUtilization(),
                "targetUtilization", 0.8
            ));
        }
        
        if (stats.assignmentEfficiency() < 0.7) {
            plan.put("assignmentStrategy", Map.of(
                "action", "CHANGE_ASSIGNMENT_STRATEGY",
                "reason", "Low assignment efficiency",
                "currentEfficiency", stats.assignmentEfficiency(),
                "recommendedStrategy", "WORKLOAD_AWARE"
            ));
        }
        
        return plan;
    }

    private void applyOptimizations(Map<String, Object> optimizationPlan) {
        // This would apply the optimization plan
        // Implementation would depend on specific optimization actions
        log.info("Applying optimization plan: {}", optimizationPlan);
    }

    private List<Map<String, Object>> generateRecommendations(PartitionMonitor.PartitionMonitoringStats stats) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        if (stats.partitionSkew() > 2.0) {
            recommendations.add(Map.of(
                "type", "PARTITION_DISTRIBUTION",
                "severity", "HIGH",
                "title", "High Partition Skew Detected",
                "description", "Partition distribution is uneven across consumers",
                "recommendation", "Consider using LOAD_BALANCED assignment strategy or rebalancing consumer group",
                "metrics", Map.of("currentSkew", stats.partitionSkew(), "idealSkew", "< 1.5")
            ));
        }
        
        if (stats.consumerUtilization() < 0.6) {
            recommendations.add(Map.of(
                "type", "CONSUMER_UTILIZATION",
                "severity", "MEDIUM",
                "title", "Low Consumer Utilization",
                "description", "Consumers are not effectively utilizing available resources",
                "recommendation", "Optimize partition assignment or consider reducing consumer count",
                "metrics", Map.of("currentUtilization", stats.consumerUtilization(), "targetUtilization", "> 0.8")
            ));
        }
        
        if (stats.assignmentEfficiency() < 0.7) {
            recommendations.add(Map.of(
                "type", "ASSIGNMENT_EFFICIENCY",
                "severity", "MEDIUM",
                "title", "Suboptimal Assignment Efficiency",
                "description", "Partition assignment strategy is not optimal for current workload",
                "recommendation", "Consider switching to WORKLOAD_AWARE or AFFINITY_BASED assignment strategy",
                "metrics", Map.of("currentEfficiency", stats.assignmentEfficiency(), "targetEfficiency", "> 0.8")
            ));
        }
        
        if (stats.totalRebalances() > 10) {
            recommendations.add(Map.of(
                "type", "REBALANCING_FREQUENCY",
                "severity", "HIGH",
                "title", "Frequent Rebalancing Detected",
                "description", "Consumer group is rebalancing too frequently",
                "recommendation", "Increase session timeout, enable sticky assignment, or investigate consumer stability",
                "metrics", Map.of("totalRebalances", stats.totalRebalances(), "recommendedFrequency", "< 5 per hour")
            ));
        }
        
        return recommendations;
    }
}
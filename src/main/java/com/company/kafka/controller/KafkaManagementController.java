package com.company.kafka.controller;

import com.company.kafka.management.KafkaContainerManager;
import com.company.kafka.monitoring.ConsumerLagMonitor;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Kafka container lifecycle management.
 * 
 * Provides HTTP endpoints for:
 * - Container start/stop/pause/resume operations
 * - Container status monitoring
 * - Bulk container operations
 * - Health and metrics information
 * 
 * Pro Tips:
 * 1. Use these endpoints for operational control
 * 2. Implement proper authentication for management endpoints
 * 3. Monitor container operations with metrics
 * 4. Use pause/resume for maintenance windows
 * 5. Implement circuit breaker patterns with container control
 */
@RestController
@RequestMapping("/api/kafka/management")
@Slf4j
public class KafkaManagementController {

    private final KafkaContainerManager containerManager;
    private final ConsumerLagMonitor lagMonitor;

    public KafkaManagementController(KafkaContainerManager containerManager, ConsumerLagMonitor lagMonitor) {
        this.containerManager = containerManager;
        this.lagMonitor = lagMonitor;
    }

    /**
     * Gets the status of all Kafka containers.
     *
     * @return container status information
     */
    @GetMapping("/containers/status")
    @Timed(value = "kafka.management.containers.status", description = "Time to get container status")
    public ResponseEntity<Map<String, Object>> getContainerStatuses() {
        try {
            Map<String, KafkaContainerManager.ContainerStatus> statuses = 
                containerManager.getAllContainerStatuses();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "containers", statuses,
                "totalContainers", statuses.size()
            ));
            
        } catch (Exception e) {
            log.error("Error getting container statuses", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get container statuses: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets the status of a specific container.
     *
     * @param listenerId the listener container ID
     * @return container status information
     */
    @GetMapping("/containers/{listenerId}/status")
    @Timed(value = "kafka.management.container.status", description = "Time to get single container status")
    public ResponseEntity<Map<String, Object>> getContainerStatus(@PathVariable String listenerId) {
        try {
            KafkaContainerManager.ContainerStatus status = 
                containerManager.getContainerStatus(listenerId);
            
            if ("NOT_FOUND".equals(status.getState())) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "container", status
            ));
            
        } catch (Exception e) {
            log.error("Error getting container status for: {}", listenerId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get container status: " + e.getMessage()
                ));
        }
    }

    /**
     * Starts a specific container.
     *
     * @param listenerId the listener container ID
     * @return operation result
     */
    @PostMapping("/containers/{listenerId}/start")
    @Timed(value = "kafka.management.container.start", description = "Time to start a container")
    public ResponseEntity<Map<String, Object>> startContainer(@PathVariable String listenerId) {
        try {
            log.info("Starting container: {}", listenerId);
            boolean result = containerManager.startContainer(listenerId);
            
            return ResponseEntity.ok(Map.of(
                "status", result ? "success" : "failed",
                "message", result ? "Container started successfully" : "Failed to start container",
                "listenerId", listenerId
            ));
            
        } catch (Exception e) {
            log.error("Error starting container: {}", listenerId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to start container: " + e.getMessage(),
                    "listenerId", listenerId
                ));
        }
    }

    /**
     * Stops a specific container.
     *
     * @param listenerId the listener container ID
     * @return operation result
     */
    @PostMapping("/containers/{listenerId}/stop")
    @Timed(value = "kafka.management.container.stop", description = "Time to stop a container")
    public ResponseEntity<Map<String, Object>> stopContainer(@PathVariable String listenerId) {
        try {
            log.info("Stopping container: {}", listenerId);
            boolean result = containerManager.stopContainer(listenerId);
            
            return ResponseEntity.ok(Map.of(
                "status", result ? "success" : "failed",
                "message", result ? "Container stopped successfully" : "Failed to stop container",
                "listenerId", listenerId
            ));
            
        } catch (Exception e) {
            log.error("Error stopping container: {}", listenerId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to stop container: " + e.getMessage(),
                    "listenerId", listenerId
                ));
        }
    }

    /**
     * Pauses a specific container.
     *
     * @param listenerId the listener container ID
     * @return operation result
     */
    @PostMapping("/containers/{listenerId}/pause")
    @Timed(value = "kafka.management.container.pause", description = "Time to pause a container")
    public ResponseEntity<Map<String, Object>> pauseContainer(@PathVariable String listenerId) {
        try {
            log.info("Pausing container: {}", listenerId);
            boolean result = containerManager.pauseContainer(listenerId);
            
            return ResponseEntity.ok(Map.of(
                "status", result ? "success" : "failed",
                "message", result ? "Container paused successfully" : "Failed to pause container",
                "listenerId", listenerId
            ));
            
        } catch (Exception e) {
            log.error("Error pausing container: {}", listenerId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to pause container: " + e.getMessage(),
                    "listenerId", listenerId
                ));
        }
    }

    /**
     * Resumes a specific container.
     *
     * @param listenerId the listener container ID
     * @return operation result
     */
    @PostMapping("/containers/{listenerId}/resume")
    @Timed(value = "kafka.management.container.resume", description = "Time to resume a container")
    public ResponseEntity<Map<String, Object>> resumeContainer(@PathVariable String listenerId) {
        try {
            log.info("Resuming container: {}", listenerId);
            boolean result = containerManager.resumeContainer(listenerId);
            
            return ResponseEntity.ok(Map.of(
                "status", result ? "success" : "failed",
                "message", result ? "Container resumed successfully" : "Failed to resume container",
                "listenerId", listenerId
            ));
            
        } catch (Exception e) {
            log.error("Error resuming container: {}", listenerId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to resume container: " + e.getMessage(),
                    "listenerId", listenerId
                ));
        }
    }

    /**
     * Starts all containers.
     *
     * @return operation results for all containers
     */
    @PostMapping("/containers/start-all")
    @Timed(value = "kafka.management.containers.start_all", description = "Time to start all containers")
    public ResponseEntity<Map<String, Object>> startAllContainers() {
        try {
            log.info("Starting all containers");
            Map<String, Boolean> results = containerManager.startAllContainers();
            
            long successCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();
            
            return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", String.format("Started %d out of %d containers", successCount, results.size()),
                "results", results,
                "successCount", successCount,
                "totalCount", results.size()
            ));
            
        } catch (Exception e) {
            log.error("Error starting all containers", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to start all containers: " + e.getMessage()
                ));
        }
    }

    /**
     * Stops all containers.
     *
     * @return operation results for all containers
     */
    @PostMapping("/containers/stop-all")
    @Timed(value = "kafka.management.containers.stop_all", description = "Time to stop all containers")
    public ResponseEntity<Map<String, Object>> stopAllContainers() {
        try {
            log.info("Stopping all containers");
            Map<String, Boolean> results = containerManager.stopAllContainers();
            
            long successCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();
            
            return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", String.format("Stopped %d out of %d containers", successCount, results.size()),
                "results", results,
                "successCount", successCount,
                "totalCount", results.size()
            ));
            
        } catch (Exception e) {
            log.error("Error stopping all containers", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to stop all containers: " + e.getMessage()
                ));
        }
    }

    /**
     * Pauses all containers.
     *
     * @return operation results for all containers
     */
    @PostMapping("/containers/pause-all")
    @Timed(value = "kafka.management.containers.pause_all", description = "Time to pause all containers")
    public ResponseEntity<Map<String, Object>> pauseAllContainers() {
        try {
            log.info("Pausing all containers");
            Map<String, Boolean> results = containerManager.pauseAllContainers();
            
            long successCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();
            
            return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", String.format("Paused %d out of %d containers", successCount, results.size()),
                "results", results,
                "successCount", successCount,
                "totalCount", results.size()
            ));
            
        } catch (Exception e) {
            log.error("Error pausing all containers", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to pause all containers: " + e.getMessage()
                ));
        }
    }

    /**
     * Resumes all containers.
     *
     * @return operation results for all containers
     */
    @PostMapping("/containers/resume-all")
    @Timed(value = "kafka.management.containers.resume_all", description = "Time to resume all containers")
    public ResponseEntity<Map<String, Object>> resumeAllContainers() {
        try {
            log.info("Resuming all containers");
            Map<String, Boolean> results = containerManager.resumeAllContainers();
            
            long successCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();
            
            return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", String.format("Resumed %d out of %d containers", successCount, results.size()),
                "results", results,
                "successCount", successCount,
                "totalCount", results.size()
            ));
            
        } catch (Exception e) {
            log.error("Error resuming all containers", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to resume all containers: " + e.getMessage()
                ));
        }
    }

    /**
     * Performs graceful shutdown of all containers.
     *
     * @param timeoutMs optional timeout in milliseconds (default: 30 seconds)
     * @return shutdown result
     */
    @PostMapping("/containers/graceful-shutdown")
    @Timed(value = "kafka.management.containers.graceful_shutdown", description = "Time for graceful shutdown")
    public ResponseEntity<Map<String, Object>> gracefulShutdown(
            @RequestParam(defaultValue = "30000") long timeoutMs) {
        try {
            log.info("Performing graceful shutdown with timeout: {}ms", timeoutMs);
            boolean result = containerManager.gracefulShutdown(timeoutMs);
            
            return ResponseEntity.ok(Map.of(
                "status", result ? "success" : "timeout",
                "message", result ? "All containers shut down gracefully" : 
                          "Graceful shutdown timeout exceeded",
                "timeoutMs", timeoutMs
            ));
            
        } catch (Exception e) {
            log.error("Error during graceful shutdown", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to perform graceful shutdown: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets container management health information.
     *
     * @return health and metrics data
     */
    @GetMapping("/health")
    @Timed(value = "kafka.management.health", description = "Time to get management health info")
    public ResponseEntity<Map<String, Object>> getManagementHealth() {
        try {
            Map<String, Object> health = containerManager.getHealthInfo();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "management", health
            ));
            
        } catch (Exception e) {
            log.error("Error getting management health", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get management health: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets consumer lag information for all monitored consumer groups.
     *
     * @return lag information
     */
    @GetMapping("/lag")
    @Timed(value = "kafka.management.lag.all", description = "Time to get all consumer lag info")
    public ResponseEntity<Map<String, Object>> getAllLagInfo() {
        try {
            Map<String, Object> lagInfo = lagMonitor.getAllLagInfo();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "lag", lagInfo
            ));
            
        } catch (Exception e) {
            log.error("Error getting consumer lag information", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get consumer lag information: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets consumer lag information for a specific consumer group.
     *
     * @param consumerGroup the consumer group name
     * @return lag information for the consumer group
     */
    @GetMapping("/lag/{consumerGroup}")
    @Timed(value = "kafka.management.lag.group", description = "Time to get consumer group lag info")
    public ResponseEntity<Map<String, Object>> getConsumerGroupLag(@PathVariable String consumerGroup) {
        try {
            Map<String, Object> lagInfo = lagMonitor.getConsumerGroupLagInfo(consumerGroup);
            
            if ("not_found".equals(lagInfo.get("status"))) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "lag", lagInfo
            ));
            
        } catch (Exception e) {
            log.error("Error getting consumer lag information for group: {}", consumerGroup, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get consumer lag information: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets consumer lag trend information.
     *
     * @return lag trend information
     */
    @GetMapping("/lag/trends")
    @Timed(value = "kafka.management.lag.trends", description = "Time to get lag trends")
    public ResponseEntity<Map<String, Object>> getLagTrends() {
        try {
            Map<String, Object> trends = lagMonitor.getLagTrends();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "trends", trends
            ));
            
        } catch (Exception e) {
            log.error("Error getting consumer lag trends", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get consumer lag trends: " + e.getMessage()
                ));
        }
    }
}
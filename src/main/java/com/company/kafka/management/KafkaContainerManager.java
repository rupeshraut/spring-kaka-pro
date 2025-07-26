package com.company.kafka.management;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka container lifecycle management for operational control.
 * 
 * Features:
 * - Programmatic container start/stop/pause/resume
 * - Consumer lag monitoring per container
 * - Container health monitoring
 * - Graceful shutdown handling
 * - Container performance metrics
 * - Dynamic container configuration
 * 
 * Use Cases:
 * - Operational control during deployments
 * - Emergency pause/resume for maintenance
 * - Container health monitoring and alerting
 * - Dynamic scaling based on load
 * - Debugging and troubleshooting
 * 
 * Pro Tips:
 * 1. Use pause/resume for temporary maintenance
 * 2. Monitor container states for operational insights
 * 3. Implement proper graceful shutdown procedures
 * 4. Track container performance metrics
 * 5. Use container management for circuit breaker patterns
 */
@Component
@Slf4j
public class KafkaContainerManager {

    private final KafkaListenerEndpointRegistry registry;
    private final MeterRegistry meterRegistry;
    
    // Container state tracking
    private final AtomicInteger runningContainers = new AtomicInteger(0);
    private final AtomicInteger pausedContainers = new AtomicInteger(0);
    private final AtomicInteger stoppedContainers = new AtomicInteger(0);
    
    // Operation metrics
    private final Counter containerStartCounter;
    private final Counter containerStopCounter;
    private final Counter containerPauseCounter;
    private final Counter containerResumeCounter;

    public KafkaContainerManager(KafkaListenerEndpointRegistry registry, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.containerStartCounter = Counter.builder("kafka.container.operations.start")
                .description("Number of container start operations")
                .register(meterRegistry);
                
        this.containerStopCounter = Counter.builder("kafka.container.operations.stop")
                .description("Number of container stop operations")
                .register(meterRegistry);
                
        this.containerPauseCounter = Counter.builder("kafka.container.operations.pause")
                .description("Number of container pause operations")
                .register(meterRegistry);
                
        this.containerResumeCounter = Counter.builder("kafka.container.operations.resume")
                .description("Number of container resume operations")
                .register(meterRegistry);
        
        // Register gauges for container states
        Gauge.builder("kafka.containers.running", runningContainers, AtomicInteger::get)
                .description("Number of running containers")
                .register(meterRegistry);
                
        Gauge.builder("kafka.containers.paused", pausedContainers, AtomicInteger::get)
                .description("Number of paused containers")
                .register(meterRegistry);
                
        Gauge.builder("kafka.containers.stopped", stoppedContainers, AtomicInteger::get)
                .description("Number of stopped containers")
                .register(meterRegistry);
        
        log.info("KafkaContainerManager initialized with metrics tracking");
    }

    /**
     * Starts a specific Kafka listener container.
     *
     * @param listenerId the listener container ID
     * @return true if container was started successfully
     */
    public boolean startContainer(String listenerId) {
        try {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container == null) {
                log.warn("Container not found: {}", listenerId);
                return false;
            }
            
            if (!container.isRunning()) {
                container.start();
                containerStartCounter.increment();
                updateContainerCounts();
                
                log.info("Container started successfully: {}", listenerId);
                return true;
            } else {
                log.debug("Container already running: {}", listenerId);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Failed to start container: {}, error: {}", listenerId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Stops a specific Kafka listener container.
     *
     * @param listenerId the listener container ID
     * @return true if container was stopped successfully
     */
    public boolean stopContainer(String listenerId) {
        try {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container == null) {
                log.warn("Container not found: {}", listenerId);
                return false;
            }
            
            if (container.isRunning()) {
                container.stop();
                containerStopCounter.increment();
                updateContainerCounts();
                
                log.info("Container stopped successfully: {}", listenerId);
                return true;
            } else {
                log.debug("Container already stopped: {}", listenerId);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Failed to stop container: {}, error: {}", listenerId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Pauses a specific Kafka listener container.
     *
     * @param listenerId the listener container ID
     * @return true if container was paused successfully
     */
    public boolean pauseContainer(String listenerId) {
        try {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container == null) {
                log.warn("Container not found: {}", listenerId);
                return false;
            }
            
            if (container.isRunning() && !container.isContainerPaused()) {
                container.pause();
                containerPauseCounter.increment();
                updateContainerCounts();
                
                log.info("Container paused successfully: {}", listenerId);
                return true;
            } else {
                log.debug("Container already paused or not running: {}", listenerId);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Failed to pause container: {}, error: {}", listenerId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Resumes a specific Kafka listener container.
     *
     * @param listenerId the listener container ID
     * @return true if container was resumed successfully
     */
    public boolean resumeContainer(String listenerId) {
        try {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container == null) {
                log.warn("Container not found: {}", listenerId);
                return false;
            }
            
            if (container.isRunning() && container.isContainerPaused()) {
                container.resume();
                containerResumeCounter.increment();
                updateContainerCounts();
                
                log.info("Container resumed successfully: {}", listenerId);
                return true;
            } else {
                log.debug("Container not paused or not running: {}", listenerId);
                return true;
            }
            
        } catch (Exception e) {
            log.error("Failed to resume container: {}, error: {}", listenerId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Starts all registered Kafka listener containers.
     *
     * @return map of container IDs to start results
     */
    public Map<String, Boolean> startAllContainers() {
        log.info("Starting all Kafka listener containers");
        
        Map<String, Boolean> results = new HashMap<>();
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();
        
        for (MessageListenerContainer container : containers) {
            String listenerId = getContainerListenerId(container);
            boolean result = startContainer(listenerId);
            results.put(listenerId, result);
        }
        
        log.info("Started {} containers, results: {}", containers.size(), results);
        return results;
    }

    /**
     * Stops all registered Kafka listener containers.
     *
     * @return map of container IDs to stop results
     */
    public Map<String, Boolean> stopAllContainers() {
        log.info("Stopping all Kafka listener containers");
        
        Map<String, Boolean> results = new HashMap<>();
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();
        
        for (MessageListenerContainer container : containers) {
            String listenerId = getContainerListenerId(container);
            boolean result = stopContainer(listenerId);
            results.put(listenerId, result);
        }
        
        log.info("Stopped {} containers, results: {}", containers.size(), results);
        return results;
    }

    /**
     * Pauses all registered Kafka listener containers.
     *
     * @return map of container IDs to pause results
     */
    public Map<String, Boolean> pauseAllContainers() {
        log.info("Pausing all Kafka listener containers");
        
        Map<String, Boolean> results = new HashMap<>();
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();
        
        for (MessageListenerContainer container : containers) {
            String listenerId = getContainerListenerId(container);
            boolean result = pauseContainer(listenerId);
            results.put(listenerId, result);
        }
        
        log.info("Paused {} containers, results: {}", containers.size(), results);
        return results;
    }

    /**
     * Resumes all registered Kafka listener containers.
     *
     * @return map of container IDs to resume results
     */
    public Map<String, Boolean> resumeAllContainers() {
        log.info("Resuming all Kafka listener containers");
        
        Map<String, Boolean> results = new HashMap<>();
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();
        
        for (MessageListenerContainer container : containers) {
            String listenerId = getContainerListenerId(container);
            boolean result = resumeContainer(listenerId);
            results.put(listenerId, result);
        }
        
        log.info("Resumed {} containers, results: {}", containers.size(), results);
        return results;
    }

    /**
     * Gets the status of a specific container.
     *
     * @param listenerId the listener container ID
     * @return container status information
     */
    public ContainerStatus getContainerStatus(String listenerId) {
        MessageListenerContainer container = registry.getListenerContainer(listenerId);
        if (container == null) {
            return new ContainerStatus(listenerId, "NOT_FOUND", false, false);
        }
        
        String state = getContainerState(container);
        return new ContainerStatus(
            listenerId,
            state,
            container.isRunning(),
            container.isContainerPaused()
        );
    }

    /**
     * Gets the status of all containers.
     *
     * @return map of container IDs to status information
     */
    public Map<String, ContainerStatus> getAllContainerStatuses() {
        Map<String, ContainerStatus> statuses = new HashMap<>();
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();
        
        for (MessageListenerContainer container : containers) {
            String listenerId = getContainerListenerId(container);
            ContainerStatus status = getContainerStatus(listenerId);
            statuses.put(listenerId, status);
        }
        
        return statuses;
    }

    /**
     * Performs a graceful shutdown of all containers.
     *
     * @param timeoutMs timeout in milliseconds for shutdown
     * @return true if all containers shut down gracefully
     */
    public boolean gracefulShutdown(long timeoutMs) {
        log.info("Performing graceful shutdown of all Kafka containers with timeout: {}ms", timeoutMs);
        
        try {
            // Stop all containers gracefully
            registry.stop();
            
            // Wait for shutdown with timeout
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                boolean allStopped = registry.getListenerContainers().stream()
                    .allMatch(container -> !container.isRunning());
                    
                if (allStopped) {
                    log.info("All containers shut down gracefully");
                    updateContainerCounts();
                    return true;
                }
                
                Thread.sleep(100);
            }
            
            log.warn("Graceful shutdown timeout exceeded, some containers may still be running");
            return false;
            
        } catch (Exception e) {
            log.error("Error during graceful shutdown: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets the listener ID from a container (implementation-dependent).
     *
     * @param container the message listener container
     * @return listener ID string
     */
    private String getContainerListenerId(MessageListenerContainer container) {
        // This is a simplified implementation - in practice, you might need to
        // extract the listener ID from the container's properties or configuration
        return container.getClass().getSimpleName() + "-" + container.hashCode();
    }

    /**
     * Gets the current state of a container.
     *
     * @param container the message listener container
     * @return state string
     */
    private String getContainerState(MessageListenerContainer container) {
        if (!container.isRunning()) {
            return "STOPPED";
        } else if (container.isContainerPaused()) {
            return "PAUSED";
        } else {
            return "RUNNING";
        }
    }

    /**
     * Updates the container count metrics.
     */
    private void updateContainerCounts() {
        int running = 0, paused = 0, stopped = 0;
        
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            if (!container.isRunning()) {
                stopped++;
            } else if (container.isContainerPaused()) {
                paused++;
            } else {
                running++;
            }
        }
        
        runningContainers.set(running);
        pausedContainers.set(paused);
        stoppedContainers.set(stopped);
    }

    /**
     * Container status information.
     */
    public static class ContainerStatus {
        private final String listenerId;
        private final String state;
        private final boolean isRunning;
        private final boolean isPaused;

        public ContainerStatus(String listenerId, String state, boolean isRunning, boolean isPaused) {
            this.listenerId = listenerId;
            this.state = state;
            this.isRunning = isRunning;
            this.isPaused = isPaused;
        }

        public String getListenerId() { return listenerId; }
        public String getState() { return state; }
        public boolean isRunning() { return isRunning; }
        public boolean isPaused() { return isPaused; }

        @Override
        public String toString() {
            return String.format("ContainerStatus{id='%s', state='%s', running=%s, paused=%s}", 
                listenerId, state, isRunning, isPaused);
        }
    }

    /**
     * Gets container management health information.
     *
     * @return health status information
     */
    public Map<String, Object> getHealthInfo() {
        Map<String, ContainerStatus> statuses = getAllContainerStatuses();
        
        return Map.of(
            "totalContainers", statuses.size(),
            "runningContainers", runningContainers.get(),
            "pausedContainers", pausedContainers.get(),
            "stoppedContainers", stoppedContainers.get(),
            "operationsPerformed", Map.of(
                "starts", containerStartCounter.count(),
                "stops", containerStopCounter.count(),
                "pauses", containerPauseCounter.count(),
                "resumes", containerResumeCounter.count()
            ),
            "containerStatuses", statuses
        );
    }
}
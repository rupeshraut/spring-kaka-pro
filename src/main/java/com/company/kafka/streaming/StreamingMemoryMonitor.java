package com.company.kafka.streaming;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors memory usage specifically for streaming Kafka processing to ensure
 * memory efficiency and detect potential memory leaks or excessive usage.
 * 
 * Key Monitoring Areas:
 * 1. Heap memory usage trends
 * 2. Buffer utilization in streaming processors
 * 3. Garbage collection frequency and duration
 * 4. Memory allocation rates
 * 
 * Alerts when:
 * - Memory usage exceeds safe thresholds
 * - Memory growth indicates potential leaks
 * - Buffer utilization suggests backpressure issues
 * 
 * Pro Tips:
 * 1. Set alerts at 70% heap usage for early warning
 * 2. Monitor memory allocation rate vs processing rate
 * 3. Track buffer sizes to detect bottlenecks
 * 4. Use memory profiling tools for deep analysis
 */
@Component
@Slf4j
public class StreamingMemoryMonitor {

    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    private final AtomicLong lastGcCount = new AtomicLong(0);
    private final AtomicLong lastGcTime = new AtomicLong(0);
    
    // Memory thresholds (configurable)
    private static final double MEMORY_WARNING_THRESHOLD = 0.70; // 70%
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.85; // 85%
    
    private final double[] memoryUsageHistory = new double[10];
    private int historyIndex = 0;

    public StreamingMemoryMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMemoryGauges();
    }

    /**
     * Initializes memory monitoring gauges.
     */
    private void initializeMemoryGauges() {
        // Heap memory usage
        Gauge.builder("kafka.streaming.memory.heap.used", this, monitor -> {
                MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
                return heapUsage.getUsed();
            })
            .description("Heap memory used by streaming processors")
            .register(meterRegistry);

        Gauge.builder("kafka.streaming.memory.heap.max", this, monitor -> {
                MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
                return heapUsage.getMax();
            })
            .description("Maximum heap memory available")
            .register(meterRegistry);

        Gauge.builder("kafka.streaming.memory.heap.utilization", this, monitor -> monitor.getHeapUtilization())
            .description("Heap memory utilization percentage")
            .register(meterRegistry);

        // Non-heap memory usage
        Gauge.builder("kafka.streaming.memory.nonheap.used", this, monitor -> {
                MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
                return nonHeapUsage.getUsed();
            })
            .description("Non-heap memory used")
            .register(meterRegistry);

        // Memory pressure indicator
        Gauge.builder("kafka.streaming.memory.pressure", this, monitor -> monitor.getMemoryPressure())
            .description("Memory pressure indicator (0=low, 1=high)")
            .register(meterRegistry);
    }

    /**
     * Scheduled method to monitor memory usage and detect issues.
     */
    @Scheduled(fixedRate = 10000) // Every 10 seconds
    public void monitorMemoryUsage() {
        double currentUtilization = getHeapUtilization();
        
        // Update history
        memoryUsageHistory[historyIndex] = currentUtilization;
        historyIndex = (historyIndex + 1) % memoryUsageHistory.length;
        
        // Check thresholds
        if (currentUtilization > MEMORY_CRITICAL_THRESHOLD) {
            log.error("CRITICAL: Memory usage is {}% - immediate action required", 
                String.format("%.1f", currentUtilization * 100));
            meterRegistry.counter("kafka.streaming.memory.alerts.critical").increment();
            
        } else if (currentUtilization > MEMORY_WARNING_THRESHOLD) {
            log.warn("WARNING: Memory usage is {}% - monitor closely", 
                String.format("%.1f", currentUtilization * 100));
            meterRegistry.counter("kafka.streaming.memory.alerts.warning").increment();
        }
        
        // Check for memory leaks
        detectMemoryLeak();
        
        // Monitor GC activity
        monitorGarbageCollection();
        
        log.debug("Memory monitoring - Heap: {}%, Non-heap: {} MB", 
            String.format("%.1f", currentUtilization * 100),
            memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
    }

    /**
     * Calculates current heap memory utilization as a percentage.
     */
    private double getHeapUtilization() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        if (heapUsage.getMax() == -1) {
            // Max is undefined, use committed instead
            return (double) heapUsage.getUsed() / heapUsage.getCommitted();
        }
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }

    /**
     * Calculates memory pressure indicator.
     */
    private double getMemoryPressure() {
        double utilization = getHeapUtilization();
        
        if (utilization > MEMORY_CRITICAL_THRESHOLD) {
            return 1.0; // High pressure
        } else if (utilization > MEMORY_WARNING_THRESHOLD) {
            return 0.5; // Medium pressure
        } else {
            return 0.0; // Low pressure
        }
    }

    /**
     * Detects potential memory leaks by analyzing memory usage trends.
     */
    private void detectMemoryLeak() {
        if (historyIndex == 0) { // Full cycle completed
            double trend = calculateMemoryTrend();
            
            if (trend > 0.05) { // 5% increase trend
                log.warn("Potential memory leak detected - memory usage trending upward by {}%", 
                    String.format("%.2f", trend * 100));
                meterRegistry.counter("kafka.streaming.memory.leak.warnings").increment();
            }
        }
    }

    /**
     * Calculates memory usage trend over the history window.
     */
    private double calculateMemoryTrend() {
        if (memoryUsageHistory.length < 2) {
            return 0.0;
        }
        
        double first = memoryUsageHistory[0];
        double last = memoryUsageHistory[memoryUsageHistory.length - 1];
        
        return (last - first) / first;
    }

    /**
     * Monitors garbage collection activity to detect memory pressure.
     */
    private void monitorGarbageCollection() {
        try {
            long currentGcCount = ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(gc -> gc.getCollectionCount())
                .sum();
                
            long currentGcTime = ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(gc -> gc.getCollectionTime())
                .sum();
            
            long gcCountDelta = currentGcCount - lastGcCount.getAndSet(currentGcCount);
            long gcTimeDelta = currentGcTime - lastGcTime.getAndSet(currentGcTime);
            
            if (gcCountDelta > 0) {
                meterRegistry.counter("kafka.streaming.gc.collections").increment(gcCountDelta);
                meterRegistry.timer("kafka.streaming.gc.time")
                    .record(gcTimeDelta, java.util.concurrent.TimeUnit.MILLISECONDS);
                
                // High GC activity warning
                if (gcCountDelta > 5) { // More than 5 GC cycles in 10 seconds
                    log.warn("High GC activity detected - {} collections in 10 seconds, {}ms total time", 
                        gcCountDelta, gcTimeDelta);
                    meterRegistry.counter("kafka.streaming.gc.high.activity").increment();
                }
            }
            
        } catch (Exception e) {
            log.error("Error monitoring garbage collection: {}", e.getMessage());
        }
    }

    /**
     * Provides memory optimization recommendations based on current usage.
     */
    public MemoryOptimizationRecommendations getOptimizationRecommendations() {
        double utilization = getHeapUtilization();
        double pressure = getMemoryPressure();
        double trend = calculateMemoryTrend();
        
        return new MemoryOptimizationRecommendations(
            utilization,
            pressure,
            trend,
            generateRecommendations(utilization, pressure, trend)
        );
    }

    private java.util.List<String> generateRecommendations(double utilization, double pressure, double trend) {
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        
        if (utilization > MEMORY_CRITICAL_THRESHOLD) {
            recommendations.add("CRITICAL: Increase heap size immediately (-Xmx)");
            recommendations.add("Reduce buffer sizes in streaming processors");
            recommendations.add("Implement more aggressive backpressure");
        } else if (utilization > MEMORY_WARNING_THRESHOLD) {
            recommendations.add("Consider increasing heap size");
            recommendations.add("Monitor buffer utilization in streaming processors");
        }
        
        if (trend > 0.03) {
            recommendations.add("Investigate potential memory leak");
            recommendations.add("Enable memory profiling for detailed analysis");
        }
        
        if (pressure > 0.5) {
            recommendations.add("Reduce concurrent processing threads");
            recommendations.add("Implement smaller batch sizes");
        }
        
        return recommendations;
    }

    /**
     * Memory optimization recommendations.
     */
    public record MemoryOptimizationRecommendations(
        double currentUtilization,
        double memoryPressure,
        double memoryTrend,
        java.util.List<String> recommendations
    ) {
        public boolean requiresImmediateAction() {
            return currentUtilization > MEMORY_CRITICAL_THRESHOLD || memoryTrend > 0.05;
        }
        
        public String getSeverity() {
            if (requiresImmediateAction()) {
                return "CRITICAL";
            } else if (currentUtilization > MEMORY_WARNING_THRESHOLD) {
                return "WARNING";
            } else {
                return "OK";
            }
        }
    }
}

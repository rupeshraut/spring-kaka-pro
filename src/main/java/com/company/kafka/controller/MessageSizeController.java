package com.company.kafka.controller;

import com.company.kafka.metrics.MessageSizeMetrics;
import com.company.kafka.service.MessageSizeMonitoringService;
import com.company.kafka.util.MessageSizeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for message size monitoring and testing
 * 
 * Provides endpoints to:
 * - Send test messages of various sizes
 * - Get message size statistics
 * - Monitor performance by message size
 * - Reset metrics
 */
@RestController
@RequestMapping("/api/message-size")
@RequiredArgsConstructor
@Slf4j
public class MessageSizeController {
    
    private final MessageSizeMonitoringService messageSizeService;
    private final MessageSizeMetrics messageSizeMetrics;
    private final MessageSizeCalculator messageSizeCalculator;
    
    /**
     * Send a test message and return its size information
     * 
     * @param request Test message request
     * @return Message size details
     */
    @PostMapping("/test/send")
    public ResponseEntity<MessageSizeResponse> sendTestMessage(@RequestBody TestMessageRequest request) {
        try {
            // Calculate size before sending
            long estimatedSize = messageSizeCalculator.calculateValueSize(request.message());
            
            // Send message with monitoring
            messageSizeService.sendMessageWithSizeMonitoring(
                    request.topic(), 
                    request.key(), 
                    request.message()
            );
            
            // Record metrics
            messageSizeMetrics.recordProducerMessageSize(estimatedSize);
            
            MessageSizeResponse response = new MessageSizeResponse(
                    request.topic(),
                    request.key(),
                    estimatedSize,
                    messageSizeCalculator.formatSize(estimatedSize),
                    getSizeCategory(estimatedSize),
                    System.currentTimeMillis()
            );
            
            log.info("Test message sent - Topic: {}, Size: {} ({})", 
                    request.topic(), estimatedSize, messageSizeCalculator.formatSize(estimatedSize));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to send test message to topic: {}", request.topic(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate test messages of specific sizes
     * 
     * @param sizeBytes Target size in bytes
     * @param count Number of messages to generate
     * @param topic Target topic
     * @return Generation result
     */
    @PostMapping("/test/generate/{sizeBytes}")
    public ResponseEntity<GenerationResponse> generateTestMessages(
            @PathVariable long sizeBytes,
            @RequestParam(defaultValue = "1") int count,
            @RequestParam(defaultValue = "test-topic") String topic) {
        
        try {
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < count; i++) {
                // Generate message of target size
                String message = generateMessageOfSize(sizeBytes, i);
                String key = "test-key-" + i;
                
                // Send with monitoring
                messageSizeService.sendMessageWithSizeMonitoring(topic, key, message);
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            long totalBytes = sizeBytes * count;
            
            GenerationResponse response = new GenerationResponse(
                    count,
                    sizeBytes,
                    totalBytes,
                    messageSizeCalculator.formatSize(totalBytes),
                    totalTime,
                    calculateThroughput(totalBytes, totalTime)
            );
            
            log.info("Generated {} test messages of {} bytes each in {}ms", 
                    count, sizeBytes, totalTime);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to generate test messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get current message size statistics
     * 
     * @return Statistics summary
     */
    @GetMapping("/stats")
    public ResponseEntity<MessageSizeMetrics.MessageSizeMetricsSummary> getStatistics() {
        try {
            MessageSizeMetrics.MessageSizeMetricsSummary summary = messageSizeMetrics.getSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to get message size statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get detailed statistics with formatting
     * 
     * @return Formatted statistics
     */
    @GetMapping("/stats/formatted")
    public ResponseEntity<Map<String, Object>> getFormattedStatistics() {
        try {
            MessageSizeMetrics.MessageSizeMetricsSummary summary = messageSizeMetrics.getSummary();
            MessageSizeCalculator.MessageSizeStats calculatorStats = messageSizeService.getCurrentStats();
            
            Map<String, Object> response = Map.of(
                    "summary", summary.getFormattedSummary(),
                    "calculatorStats", calculatorStats.getFormattedStats(),
                    "recommendations", generateRecommendations(summary)
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get formatted statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Reset all message size statistics
     * 
     * @return Reset confirmation
     */
    @PostMapping("/stats/reset")
    public ResponseEntity<Map<String, String>> resetStatistics() {
        try {
            messageSizeService.resetStats();
            
            Map<String, String> response = Map.of(
                    "status", "success",
                    "message", "Message size statistics reset successfully",
                    "timestamp", String.valueOf(System.currentTimeMillis())
            );
            
            log.info("Message size statistics reset");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to reset statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Calculate message size for a given object
     * 
     * @param request Object to calculate size for
     * @return Size calculation result
     */
    @PostMapping("/calculate")
    public ResponseEntity<SizeCalculationResponse> calculateMessageSize(@RequestBody Object request) {
        try {
            long sizeBytes = messageSizeCalculator.calculateValueSize(request);
            
            SizeCalculationResponse response = new SizeCalculationResponse(
                    sizeBytes,
                    messageSizeCalculator.formatSize(sizeBytes),
                    getSizeCategory(sizeBytes),
                    getOptimizationSuggestions(sizeBytes),
                    System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to calculate message size", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate a message of specific size
     */
    private String generateMessageOfSize(long targetSize, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":").append(index)
          .append(",\"timestamp\":").append(System.currentTimeMillis())
          .append(",\"data\":\"");
        
        // Fill with data to reach target size
        int currentSize = sb.toString().getBytes().length + 2; // +2 for closing quotes
        int remainingSize = (int) Math.max(0, targetSize - currentSize);
        
        for (int i = 0; i < remainingSize; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        
        sb.append("\"}");
        return sb.toString();
    }
    
    /**
     * Determine size category
     */
    private String getSizeCategory(long sizeBytes) {
        if (sizeBytes < 1024) return "SMALL";
        if (sizeBytes < 100 * 1024) return "MEDIUM";
        if (sizeBytes < 1024 * 1024) return "LARGE";
        return "OVERSIZED";
    }
    
    /**
     * Calculate throughput
     */
    private double calculateThroughput(long totalBytes, long timeMs) {
        if (timeMs == 0) return 0;
        return (double) totalBytes / timeMs * 1000; // bytes per second
    }
    
    /**
     * Generate optimization recommendations
     */
    private java.util.List<String> getOptimizationSuggestions(long sizeBytes) {
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        
        if (sizeBytes > 1024 * 1024) { // > 1MB
            suggestions.add("Consider message compression");
            suggestions.add("Split large messages into smaller chunks");
            suggestions.add("Use external storage with message references");
        } else if (sizeBytes > 100 * 1024) { // > 100KB
            suggestions.add("Consider enabling compression");
            suggestions.add("Review if all fields are necessary");
        } else if (sizeBytes < 100) { // < 100 bytes
            suggestions.add("Consider batching small messages");
        }
        
        return suggestions;
    }
    
    /**
     * Generate recommendations based on statistics
     */
    private java.util.List<String> generateRecommendations(MessageSizeMetrics.MessageSizeMetricsSummary summary) {
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        
        if (summary.oversizedMessagesCount() > 0) {
            recommendations.add("High number of oversized messages detected - consider optimization");
        }
        
        if (summary.avgProducerMessageSize() > 100 * 1024) {
            recommendations.add("Large average message size - enable compression");
        }
        
        if (summary.smallMessagesCount() > summary.totalMessages() * 0.8) {
            recommendations.add("Many small messages - consider batching");
        }
        
        return recommendations;
    }
    
    // Request/Response DTOs
    public record TestMessageRequest(String topic, String key, Object message) {}
    
    public record MessageSizeResponse(
            String topic,
            String key,
            long sizeBytes,
            String formattedSize,
            String category,
            long timestamp
    ) {}
    
    public record GenerationResponse(
            int messageCount,
            long messageSizeBytes,
            long totalBytes,
            String formattedTotalSize,
            long generationTimeMs,
            double throughputBytesPerSecond
    ) {}
    
    public record SizeCalculationResponse(
            long sizeBytes,
            String formattedSize,
            String category,
            java.util.List<String> optimizationSuggestions,
            long timestamp
    ) {}
}

package com.company.kafka.controller;

import com.company.kafka.dlt.DeadLetterTopicManager;
import com.company.kafka.error.EnhancedKafkaErrorHandler;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Dead Letter Topic management and error handling operations.
 * 
 * Provides HTTP endpoints for:
 * - DLT monitoring and analytics
 * - Message reprocessing from DLT to original topics
 * - Error pattern analysis and reporting
 * - DLT retention policy management
 * - Enhanced error handler status and configuration
 * 
 * CRITICAL Production Endpoints:
 * - GET /dlt/analytics - Comprehensive DLT health and statistics
 * - POST /dlt/{topic}/reprocess - Reprocess failed messages
 * - GET /dlt/{topic}/browse - Browse DLT messages for analysis
 * - POST /dlt/retention/apply - Apply retention policies
 * - GET /error-handler/status - Enhanced error handler status
 * 
 * Pro Tips:
 * 1. Use analytics endpoint for operational dashboards
 * 2. Implement proper authentication for management endpoints
 * 3. Monitor reprocessing operations with metrics
 * 4. Use message browsing for root cause analysis
 * 5. Automate retention policy application
 */
@RestController
@RequestMapping("/api/kafka/dlt")
@Slf4j
public class DltManagementController {

    private final DeadLetterTopicManager dltManager;
    private final EnhancedKafkaErrorHandler enhancedErrorHandler;

    public DltManagementController(DeadLetterTopicManager dltManager, 
                                 EnhancedKafkaErrorHandler enhancedErrorHandler) {
        this.dltManager = dltManager;
        this.enhancedErrorHandler = enhancedErrorHandler;
    }

    /**
     * Gets comprehensive DLT analytics and health information.
     *
     * @return DLT analytics data
     */
    @GetMapping("/analytics")
    @Timed(value = "kafka.dlt.analytics", description = "Time to get DLT analytics")
    public ResponseEntity<Map<String, Object>> getDltAnalytics() {
        try {
            Map<String, Object> analytics = dltManager.getDltAnalytics();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "analytics", analytics
            ));
            
        } catch (Exception e) {
            log.error("Error getting DLT analytics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get DLT analytics: " + e.getMessage()
                ));
        }
    }

    /**
     * Reprocesses messages from a DLT topic back to the original topic.
     *
     * @param dltTopic the DLT topic name
     * @param maxMessages maximum number of messages to reprocess
     * @param filterType optional filter type for message selection
     * @return reprocessing result
     */
    @PostMapping("/{dltTopic}/reprocess")
    @Timed(value = "kafka.dlt.reprocess", description = "Time to reprocess DLT messages")
    public ResponseEntity<Map<String, Object>> reprocessMessages(
            @PathVariable String dltTopic,
            @RequestParam(defaultValue = "100") int maxMessages,
            @RequestParam(required = false) String filterType) {
        
        try {
            log.info("Reprocessing DLT messages - topic: {}, maxMessages: {}, filter: {}", 
                dltTopic, maxMessages, filterType);
            
            // Create message filter based on filter type
            DeadLetterTopicManager.MessageFilter filter = createMessageFilter(filterType);
            
            // Perform reprocessing
            DeadLetterTopicManager.DltReprocessingResult result = 
                dltManager.reprocessMessages(dltTopic, maxMessages, filter);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "dltTopic", dltTopic,
                "maxMessages", maxMessages,
                "processedCount", result.getProcessedCount(),
                "errorCount", result.getErrorCount(),
                "summary", result.getSummary()
            ));
            
        } catch (Exception e) {
            log.error("Error reprocessing DLT messages - topic: {}", dltTopic, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to reprocess DLT messages: " + e.getMessage(),
                    "dltTopic", dltTopic
                ));
        }
    }

    /**
     * Browses messages in a DLT topic for analysis.
     *
     * @param dltTopic the DLT topic name
     * @param maxMessages maximum number of messages to retrieve
     * @param filterType optional filter type for message selection
     * @return list of DLT messages
     */
    @GetMapping("/{dltTopic}/browse")
    @Timed(value = "kafka.dlt.browse", description = "Time to browse DLT messages")
    public ResponseEntity<Map<String, Object>> browseDltMessages(
            @PathVariable String dltTopic,
            @RequestParam(defaultValue = "50") int maxMessages,
            @RequestParam(required = false) String filterType) {
        
        try {
            log.debug("Browsing DLT messages - topic: {}, maxMessages: {}, filter: {}", 
                dltTopic, maxMessages, filterType);
            
            // Create message filter based on filter type
            DeadLetterTopicManager.MessageFilter filter = createMessageFilter(filterType);
            
            // Browse messages
            List<DeadLetterTopicManager.DltMessage> messages = 
                dltManager.browseDltMessages(dltTopic, maxMessages, filter);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "dltTopic", dltTopic,
                "messageCount", messages.size(),
                "maxMessages", maxMessages,
                "messages", messages
            ));
            
        } catch (Exception e) {
            log.error("Error browsing DLT messages - topic: {}", dltTopic, e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to browse DLT messages: " + e.getMessage(),
                    "dltTopic", dltTopic
                ));
        }
    }

    /**
     * Applies retention policy to all DLT topics.
     *
     * @return retention policy application result
     */
    @PostMapping("/retention/apply")
    @Timed(value = "kafka.dlt.retention", description = "Time to apply DLT retention policy")
    public ResponseEntity<Map<String, Object>> applyRetentionPolicy() {
        try {
            log.info("Applying DLT retention policy");
            
            Map<String, Object> result = dltManager.applyRetentionPolicy();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "retentionResult", result
            ));
            
        } catch (Exception e) {
            log.error("Error applying DLT retention policy", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to apply retention policy: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets enhanced error handler status and statistics.
     *
     * @return enhanced error handler information
     */
    @GetMapping("/error-handler/status")
    @Timed(value = "kafka.dlt.error_handler_status", description = "Time to get error handler status")
    public ResponseEntity<Map<String, Object>> getEnhancedErrorHandlerStatus() {
        try {
            Map<String, Object> status = enhancedErrorHandler.getEnhancedHealthInfo();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "errorHandler", status
            ));
            
        } catch (Exception e) {
            log.error("Error getting enhanced error handler status", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get error handler status: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets DLT topics with high error rates for investigation.
     *
     * @param threshold minimum message count threshold
     * @return list of problematic DLT topics
     */
    @GetMapping("/problematic-topics")
    @Timed(value = "kafka.dlt.problematic_topics", description = "Time to get problematic DLT topics")
    public ResponseEntity<Map<String, Object>> getProblematicTopics(
            @RequestParam(defaultValue = "100") int threshold) {
        
        try {
            Map<String, Object> analytics = dltManager.getDltAnalytics();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topTopics = 
                (List<Map<String, Object>>) analytics.get("topProblematicTopics");
            
            // Filter by threshold
            List<Map<String, Object>> problematicTopics = topTopics.stream()
                .filter(topic -> {
                    Object messageCount = topic.get("messageCount");
                    return messageCount instanceof Number && 
                           ((Number) messageCount).intValue() >= threshold;
                })
                .toList();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "threshold", threshold,
                "problematicTopics", problematicTopics,
                "count", problematicTopics.size()
            ));
            
        } catch (Exception e) {
            log.error("Error getting problematic DLT topics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get problematic topics: " + e.getMessage()
                ));
        }
    }

    /**
     * Gets error pattern analysis across all DLT topics.
     *
     * @return error pattern analysis
     */
    @GetMapping("/error-patterns")
    @Timed(value = "kafka.dlt.error_patterns", description = "Time to analyze error patterns")
    public ResponseEntity<Map<String, Object>> getErrorPatterns() {
        try {
            Map<String, Object> analytics = dltManager.getDltAnalytics();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topErrors = 
                (List<Map<String, Object>>) analytics.get("topErrorTypes");
            
            // Get enhanced error handler statistics
            Map<String, Object> errorHandlerStatus = enhancedErrorHandler.getEnhancedHealthInfo();
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "topErrorTypes", topErrors,
                "enhancedErrorStats", Map.of(
                    "poisonPillsDetected", errorHandlerStatus.get("poisonPillsDetected"),
                    "circuitBreakerOpens", errorHandlerStatus.get("circuitBreakerOpens"),
                    "rateLimitedErrors", errorHandlerStatus.get("rateLimitedErrors"),
                    "correlatedErrors", errorHandlerStatus.get("correlatedErrors")
                ),
                "circuitBreakerStates", errorHandlerStatus.get("circuitBreakerStates")
            ));
            
        } catch (Exception e) {
            log.error("Error getting error patterns", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get error patterns: " + e.getMessage()
                ));
        }
    }

    /**
     * Creates a message filter based on the filter type.
     *
     * @param filterType the filter type string
     * @return message filter implementation
     */
    private DeadLetterTopicManager.MessageFilter createMessageFilter(String filterType) {
        if (filterType == null) {
            return null;
        }
        
        return switch (filterType.toLowerCase()) {
            case "validation" -> record -> {
                String exceptionType = getHeaderValue(record.headers(), "dlt.exception-class");
                return exceptionType != null && exceptionType.contains("ValidationException");
            };
            
            case "recoverable" -> record -> {
                String exceptionType = getHeaderValue(record.headers(), "dlt.exception-class");
                return exceptionType != null && exceptionType.contains("RecoverableException");
            };
            
            case "poison-pill" -> record -> {
                String isPoisonPill = getHeaderValue(record.headers(), "dlt.enhanced.poison.pill");
                return "true".equals(isPoisonPill);
            };
            
            case "circuit-breaker" -> record -> {
                String circuitBreakerService = getHeaderValue(record.headers(), "dlt.enhanced.circuit.breaker.service");
                return circuitBreakerService != null;
            };
            
            case "recent" -> record -> {
                String timestamp = getHeaderValue(record.headers(), "dlt.enhanced.timestamp");
                if (timestamp != null) {
                    try {
                        long messageTime = java.time.Instant.parse(timestamp).toEpochMilli();
                        long hourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
                        return messageTime > hourAgo;
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            };
            
            default -> null; // No filter
        };
    }

    /**
     * Helper method to get header value from Kafka headers.
     *
     * @param headers the Kafka headers
     * @param key the header key
     * @return header value as string, or null if not found
     */
    private String getHeaderValue(org.apache.kafka.common.header.Headers headers, String key) {
        org.apache.kafka.common.header.Header header = headers.lastHeader(key);
        if (header != null) {
            return new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }
}
package com.company.kafka.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
// Note: RecordFilter is available in older Spring Kafka versions
// For newer versions, we'll use configuration-based approach
import org.springframework.stereotype.Component;

/**
 * Kafka record filters for message preprocessing and filtering.
 * 
 * Features:
 * - Message content filtering based on business rules
 * - Performance optimization by skipping unnecessary processing
 * - Metrics tracking for filtered messages
 * - Configurable filter rules
 * - Support for multiple filter strategies
 * 
 * Use Cases:
 * - Skip test/development messages in production
 * - Filter messages based on content type
 * - Implement message routing logic
 * - Skip corrupted or invalid messages
 * - Apply business-specific filtering rules
 * 
 * Pro Tips:
 * 1. Keep filters lightweight to avoid performance impact
 * 2. Monitor filter metrics to understand message patterns
 * 3. Use filters to prevent unnecessary processing
 * 4. Implement configurable filter rules for flexibility
 * 5. Consider filter order and dependencies
 */
@Component
@Slf4j
public class KafkaRecordFilters {

    private final MeterRegistry meterRegistry;
    
    // Filter metrics
    private final Counter filteredMessagesCounter;
    private final Counter testMessageFilterCounter;
    private final Counter invalidMessageFilterCounter;
    private final Counter businessRuleFilterCounter;

    public KafkaRecordFilters(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize filter metrics
        this.filteredMessagesCounter = Counter.builder("kafka.consumer.messages.filtered")
                .description("Total number of messages filtered out")
                .register(meterRegistry);
                
        this.testMessageFilterCounter = Counter.builder("kafka.consumer.messages.filtered.test")
                .description("Number of test messages filtered out")
                .register(meterRegistry);
                
        this.invalidMessageFilterCounter = Counter.builder("kafka.consumer.messages.filtered.invalid")
                .description("Number of invalid messages filtered out")
                .register(meterRegistry);
                
        this.businessRuleFilterCounter = Counter.builder("kafka.consumer.messages.filtered.business_rule")
                .description("Number of messages filtered by business rules")
                .register(meterRegistry);
    }

    /**
     * Filter for test and development messages.
     * Skips messages that are meant for testing or development environments.
     *
     * @return Filter function for test messages
     */
    public java.util.function.Predicate<ConsumerRecord<String, Object>> testMessageFilter() {
        return record -> {
            String value = record.value() != null ? record.value().toString() : "";
            
            // Filter out test messages
            if (value.contains("TEST") || 
                value.contains("DEVELOPMENT") || 
                value.contains("DEBUG") ||
                record.key() != null && record.key().startsWith("test-")) {
                
                testMessageFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.debug("Filtered test message - topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset());
                
                return true; // Filter out (skip processing)
            }
            
            return false; // Process the message
        };
    }

    /**
     * Filter for invalid or corrupted messages.
     * Skips messages that don't meet basic validation criteria.
     *
     * @return Filter function for invalid messages
     */
    public java.util.function.Predicate<ConsumerRecord<String, Object>> invalidMessageFilter() {
        return record -> {
            // Check for null or empty values
            if (record.value() == null) {
                invalidMessageFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.warn("Filtered null message - topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset());
                
                return true; // Filter out
            }
            
            String value = record.value().toString();
            
            // Check for empty messages
            if (value.trim().isEmpty()) {
                invalidMessageFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.warn("Filtered empty message - topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset());
                
                return true; // Filter out
            }
            
            // Check for message size limits
            if (value.length() > 1024 * 1024) { // 1MB limit
                invalidMessageFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.warn("Filtered oversized message - topic: {}, partition: {}, offset: {}, size: {} bytes", 
                    record.topic(), record.partition(), record.offset(), value.length());
                
                return true; // Filter out
            }
            
            // Check for basic JSON structure (for JSON messages)
            if (!isValidJsonStructure(value)) {
                invalidMessageFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.warn("Filtered invalid JSON message - topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset());
                
                return true; // Filter out
            }
            
            return false; // Process the message
        };
    }

    /**
     * Filter for business rule-based message filtering.
     * Applies business-specific rules to determine if messages should be processed.
     *
     * @return Filter function for business rules
     */
    public java.util.function.Predicate<ConsumerRecord<String, Object>> businessRuleFilter() {
        return record -> {
            String value = record.value() != null ? record.value().toString() : "";
            
            // Skip cancelled orders
            if (record.topic().contains("orders") && value.contains("\"status\":\"CANCELLED\"")) {
                businessRuleFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.debug("Filtered cancelled order - topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset());
                
                return true; // Filter out
            }
            
            // Skip zero-amount payments
            if (record.topic().contains("payments") && 
                (value.contains("\"amount\":0") || value.contains("\"amount\":\"0\""))) {
                businessRuleFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.debug("Filtered zero-amount payment - topic: {}, partition: {}, offset: {}", 
                    record.topic(), record.partition(), record.offset());
                
                return true; // Filter out
            }
            
            // Skip old messages (older than 24 hours)
            if (isOldMessage(record, 24 * 60 * 60 * 1000L)) { // 24 hours in milliseconds
                businessRuleFilterCounter.increment();
                filteredMessagesCounter.increment();
                
                log.debug("Filtered old message - topic: {}, partition: {}, offset: {}, timestamp: {}", 
                    record.topic(), record.partition(), record.offset(), record.timestamp());
                
                return true; // Filter out
            }
            
            return false; // Process the message
        };
    }

    /**
     * Composite filter that combines multiple filtering strategies.
     * Applies all filters in sequence for comprehensive message filtering.
     *
     * @return Combined filter function
     */
    public java.util.function.Predicate<ConsumerRecord<String, Object>> compositeFilter() {
        return record -> {
            // Apply all filters in order
            if (testMessageFilter().test(record)) {
                return true;
            }
            
            if (invalidMessageFilter().test(record)) {
                return true;
            }
            
            if (businessRuleFilter().test(record)) {
                return true;
            }
            
            return false; // Process the message if it passes all filters
        };
    }

    /**
     * Topic-specific filter that applies different rules based on the topic.
     *
     * @return Filter function for topic-specific filtering
     */
    public java.util.function.Predicate<ConsumerRecord<String, Object>> topicSpecificFilter() {
        return record -> {
            String topic = record.topic();
            
            // Orders topic specific filtering
            if (topic.contains("orders")) {
                return filterOrderMessage(record);
            }
            
            // Payments topic specific filtering
            if (topic.contains("payments")) {
                return filterPaymentMessage(record);
            }
            
            // Notifications topic specific filtering
            if (topic.contains("notifications")) {
                return filterNotificationMessage(record);
            }
            
            return false; // Default: process the message
        };
    }

    /**
     * Filters order-specific messages.
     *
     * @param record the consumer record
     * @return true if message should be filtered out
     */
    private boolean filterOrderMessage(ConsumerRecord<String, Object> record) {
        String value = record.value() != null ? record.value().toString() : "";
        
        // Skip draft orders
        if (value.contains("\"status\":\"DRAFT\"")) {
            businessRuleFilterCounter.increment();
            filteredMessagesCounter.increment();
            return true;
        }
        
        // Skip orders without customer ID
        if (!value.contains("customerId") || value.contains("\"customerId\":null")) {
            businessRuleFilterCounter.increment();
            filteredMessagesCounter.increment();
            return true;
        }
        
        return false;
    }

    /**
     * Filters payment-specific messages.
     *
     * @param record the consumer record
     * @return true if message should be filtered out
     */
    private boolean filterPaymentMessage(ConsumerRecord<String, Object> record) {
        String value = record.value() != null ? record.value().toString() : "";
        
        // Skip failed payments older than 1 hour
        if (value.contains("\"status\":\"FAILED\"") && 
            isOldMessage(record, 60 * 60 * 1000L)) { // 1 hour
            businessRuleFilterCounter.increment();
            filteredMessagesCounter.increment();
            return true;
        }
        
        return false;
    }

    /**
     * Filters notification-specific messages.
     *
     * @param record the consumer record
     * @return true if message should be filtered out
     */
    private boolean filterNotificationMessage(ConsumerRecord<String, Object> record) {
        String value = record.value() != null ? record.value().toString() : "";
        
        // Skip notifications without recipient
        if (!value.contains("recipient") || value.contains("\"recipient\":null")) {
            businessRuleFilterCounter.increment();
            filteredMessagesCounter.increment();
            return true;
        }
        
        return false;
    }

    /**
     * Checks if a message is older than the specified age.
     *
     * @param record the consumer record
     * @param maxAgeMillis maximum age in milliseconds
     * @return true if message is older than maxAge
     */
    private boolean isOldMessage(ConsumerRecord<String, Object> record, long maxAgeMillis) {
        long messageTimestamp = record.timestamp();
        long currentTime = System.currentTimeMillis();
        
        return (currentTime - messageTimestamp) > maxAgeMillis;
    }

    /**
     * Basic validation for JSON structure.
     *
     * @param value the message value to validate
     * @return true if the value appears to be valid JSON
     */
    private boolean isValidJsonStructure(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = value.trim();
        
        // Basic JSON structure check
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * Gets filter health information and statistics.
     *
     * @return filter statistics
     */
    public java.util.Map<String, Object> getFilterStats() {
        return java.util.Map.of(
            "totalFiltered", filteredMessagesCounter.count(),
            "testMessagesFiltered", testMessageFilterCounter.count(),
            "invalidMessagesFiltered", invalidMessageFilterCounter.count(),
            "businessRuleFiltered", businessRuleFilterCounter.count()
        );
    }
}
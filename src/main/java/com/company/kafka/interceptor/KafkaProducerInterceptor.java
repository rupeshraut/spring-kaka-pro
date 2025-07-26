package com.company.kafka.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka producer interceptor for message enrichment and monitoring.
 * 
 * Features:
 * - Automatic header injection (timestamps, correlation IDs, etc.)
 * - Message tracking and metrics collection
 * - Security context propagation
 * - Message transformation and enrichment
 * - Performance monitoring
 * 
 * Use Cases:
 * - Add standard headers to all messages
 * - Implement distributed tracing
 * - Monitor message production patterns
 * - Add security context information
 * - Implement message auditing
 * 
 * Pro Tips:
 * 1. Keep interceptors lightweight to avoid performance impact
 * 2. Use interceptors for cross-cutting concerns
 * 3. Monitor interceptor performance with metrics
 * 4. Avoid heavy transformations in interceptors
 * 5. Handle exceptions gracefully to prevent message loss
 */
@Slf4j
public class KafkaProducerInterceptor implements ProducerInterceptor<String, Object> {

    private static final String TIMESTAMP_HEADER = "x-timestamp";
    private static final String CORRELATION_ID_HEADER = "x-correlation-id";
    private static final String APPLICATION_HEADER = "x-application";
    private static final String VERSION_HEADER = "x-version";
    private static final String ENVIRONMENT_HEADER = "x-environment";
    
    private final AtomicLong messageCounter = new AtomicLong(0);
    private final AtomicLong errorCounter = new AtomicLong(0);
    
    private String applicationName;
    private String applicationVersion;
    private String environment;

    @Override
    public void configure(Map<String, ?> configs) {
        // Extract configuration from producer properties
        Object appName = configs.get("interceptor.application.name");
        this.applicationName = appName != null ? appName.toString() : "spring-kafka-pro";
        
        Object appVersion = configs.get("interceptor.application.version");
        this.applicationVersion = appVersion != null ? appVersion.toString() : "1.0.0";
        
        Object env = configs.get("interceptor.environment");
        this.environment = env != null ? env.toString() : "unknown";
        
        log.info("KafkaProducerInterceptor configured - app: {}, version: {}, env: {}", 
            applicationName, applicationVersion, environment);
    }

    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        try {
            // Increment message counter
            long messageId = messageCounter.incrementAndGet();
            
            log.debug("Intercepting producer message - topic: {}, key: {}, messageId: {}", 
                record.topic(), record.key(), messageId);
            
            // Create new headers from existing ones
            Headers headers = record.headers();
            
            // Add timestamp if not present
            if (headers.lastHeader(TIMESTAMP_HEADER) == null) {
                headers.add(TIMESTAMP_HEADER, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            }
            
            // Add correlation ID if not present
            if (headers.lastHeader(CORRELATION_ID_HEADER) == null) {
                String correlationId = generateCorrelationId(messageId);
                headers.add(CORRELATION_ID_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
            }
            
            // Add application metadata
            headers.add(APPLICATION_HEADER, applicationName.getBytes(StandardCharsets.UTF_8));
            headers.add(VERSION_HEADER, applicationVersion.getBytes(StandardCharsets.UTF_8));
            headers.add(ENVIRONMENT_HEADER, environment.getBytes(StandardCharsets.UTF_8));
            
            // Add message sequence number
            headers.add("x-message-sequence", String.valueOf(messageId).getBytes(StandardCharsets.UTF_8));
            
            // Add topic-specific headers
            addTopicSpecificHeaders(record, headers);
            
            // Create new record with enriched headers
            ProducerRecord<String, Object> enrichedRecord = new ProducerRecord<>(
                record.topic(),
                record.partition(),
                record.timestamp(),
                record.key(),
                record.value(),
                headers
            );
            
            log.debug("Message enriched with headers - topic: {}, messageId: {}, correlationId: {}", 
                record.topic(), messageId, getHeaderValue(headers, CORRELATION_ID_HEADER));
            
            return enrichedRecord;
            
        } catch (Exception e) {
            errorCounter.incrementAndGet();
            log.error("Error in producer interceptor - topic: {}, error: {}", 
                record.topic(), e.getMessage(), e);
            
            // Return original record on error to prevent message loss
            return record;
        }
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (exception == null) {
            log.debug("Message acknowledged - topic: {}, partition: {}, offset: {}", 
                metadata.topic(), metadata.partition(), metadata.offset());
                
            // Record success metrics
            recordSuccessMetrics(metadata);
            
        } else {
            errorCounter.incrementAndGet();
            log.error("Message send failed - topic: {}, error: {}", 
                metadata != null ? metadata.topic() : "unknown", exception.getMessage());
                
            // Record failure metrics
            recordFailureMetrics(metadata, exception);
        }
    }

    @Override
    public void close() {
        log.info("KafkaProducerInterceptor closing - processed: {}, errors: {}", 
            messageCounter.get(), errorCounter.get());
    }

    /**
     * Generates a correlation ID for message tracking.
     *
     * @param messageId the message sequence ID
     * @return correlation ID string
     */
    private String generateCorrelationId(long messageId) {
        return String.format("%s-%d-%d", applicationName, System.currentTimeMillis(), messageId);
    }

    /**
     * Adds topic-specific headers based on the message topic.
     *
     * @param record the producer record
     * @param headers the headers to enrich
     */
    private void addTopicSpecificHeaders(ProducerRecord<String, Object> record, Headers headers) {
        String topic = record.topic();
        
        if (topic.contains("orders")) {
            headers.add("x-message-type", "order".getBytes(StandardCharsets.UTF_8));
            headers.add("x-business-domain", "commerce".getBytes(StandardCharsets.UTF_8));
            
        } else if (topic.contains("payments")) {
            headers.add("x-message-type", "payment".getBytes(StandardCharsets.UTF_8));
            headers.add("x-business-domain", "finance".getBytes(StandardCharsets.UTF_8));
            headers.add("x-requires-encryption", "true".getBytes(StandardCharsets.UTF_8));
            
        } else if (topic.contains("notifications")) {
            headers.add("x-message-type", "notification".getBytes(StandardCharsets.UTF_8));
            headers.add("x-business-domain", "communication".getBytes(StandardCharsets.UTF_8));
            headers.add("x-priority", "normal".getBytes(StandardCharsets.UTF_8));
        }
        
        // Add schema version (useful for schema evolution)
        headers.add("x-schema-version", "1.0".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets header value as string.
     *
     * @param headers the headers
     * @param headerName the header name
     * @return header value or null
     */
    private String getHeaderValue(Headers headers, String headerName) {
        if (headers.lastHeader(headerName) != null) {
            return new String(headers.lastHeader(headerName).value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Records success metrics (placeholder for actual metrics implementation).
     *
     * @param metadata the record metadata
     */
    private void recordSuccessMetrics(RecordMetadata metadata) {
        // In a real implementation, you would record metrics to your metrics system
        // Example: meterRegistry.counter("kafka.producer.messages.success", "topic", metadata.topic()).increment();
        log.trace("Recording success metrics for topic: {}", metadata.topic());
    }

    /**
     * Records failure metrics (placeholder for actual metrics implementation).
     *
     * @param metadata the record metadata (may be null)
     * @param exception the exception that occurred
     */
    private void recordFailureMetrics(RecordMetadata metadata, Exception exception) {
        // In a real implementation, you would record metrics to your metrics system
        String topic = metadata != null ? metadata.topic() : "unknown";
        log.trace("Recording failure metrics for topic: {}, error: {}", topic, exception.getClass().getSimpleName());
    }

    /**
     * Gets interceptor statistics.
     *
     * @return interceptor statistics
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "messagesProcessed", messageCounter.get(),
            "errorsEncountered", errorCounter.get(),
            "applicationName", applicationName,
            "applicationVersion", applicationVersion,
            "environment", environment
        );
    }
}
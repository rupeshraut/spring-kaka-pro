package com.company.kafka.service;

import com.company.kafka.config.KafkaProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready Kafka producer service with comprehensive error handling and monitoring.
 * 
 * Features:
 * - Async message publishing with callbacks
 * - Comprehensive error handling and retry logic
 * - Metrics integration with Micrometer
 * - Correlation ID support for tracing
 * - Idempotent producer configuration
 * 
 * Pro Tips:
 * 1. Always use async sends for better performance
 * 2. Handle send failures appropriately with proper error classification
 * 3. Include correlation IDs for distributed tracing
 * 4. Monitor producer metrics for performance optimization
 * 5. Use idempotence to prevent duplicate messages
 */
@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties kafkaProperties;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter messagesSentCounter;
    private final Counter messagesFailedCounter;
    private final Timer sendTimer;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate, 
                              KafkaProperties kafkaProperties,
                              MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.messagesSentCounter = Counter.builder("kafka.producer.messages.sent")
                .description("Total number of messages sent to Kafka")
                .register(meterRegistry);
                
        this.messagesFailedCounter = Counter.builder("kafka.producer.messages.failed")
                .description("Total number of failed message sends")
                .register(meterRegistry);
                
        this.sendTimer = Timer.builder("kafka.producer.send.time")
                .description("Time taken to send messages to Kafka")
                .register(meterRegistry);
    }

    /**
     * Send a message to the specified topic asynchronously with full monitoring.
     *
     * @param topic the Kafka topic
     * @param key the message key (for partitioning)
     * @param message the message payload
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> sendMessage(String topic, String key, Object message) {
        String correlationId = generateCorrelationId();
        
        log.info("Sending message to topic: {}, key: {}, correlationId: {}", topic, key, correlationId);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, message);
        
        future.whenComplete((result, exception) -> {
            sample.stop(sendTimer);
            
            if (exception == null) {
                messagesSentCounter.increment();
                log.info("Message sent successfully - topic: {}, partition: {}, offset: {}, correlationId: {}", 
                    topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), correlationId);
                    
                // Record success metrics
                meterRegistry.counter("kafka.producer.messages.sent.by.topic", "topic", topic).increment();
                
            } else {
                messagesFailedCounter.increment();
                log.error("Failed to send message - topic: {}, correlationId: {}, error: {}", 
                    topic, correlationId, exception.getMessage(), exception);
                    
                // Record failure metrics
                meterRegistry.counter("kafka.producer.messages.failed.by.topic", "topic", topic).increment();
                meterRegistry.counter("kafka.producer.messages.failed.by.error", 
                    "error", exception.getClass().getSimpleName()).increment();
            }
        });
        
        return future;
    }

    /**
     * Send a message with only a value (no key).
     *
     * @param topic the Kafka topic
     * @param message the message payload
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> sendMessage(String topic, Object message) {
        return sendMessage(topic, null, message);
    }

    /**
     * Send a message to the orders topic.
     *
     * @param key the message key
     * @param orderEvent the order event
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> sendOrderEvent(String key, Object orderEvent) {
        String topic = kafkaProperties.topics().orders().name();
        return sendMessage(topic, key, orderEvent);
    }

    /**
     * Send a message to the payments topic.
     *
     * @param key the message key
     * @param paymentEvent the payment event
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> sendPaymentEvent(String key, Object paymentEvent) {
        String topic = kafkaProperties.topics().payments().name();
        return sendMessage(topic, key, paymentEvent);
    }

    /**
     * Send a notification message.
     *
     * @param key the message key
     * @param notification the notification
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, Object>> sendNotification(String key, Object notification) {
        String topic = kafkaProperties.topics().notifications().name();
        return sendMessage(topic, key, notification);
    }

    /**
     * Send multiple messages in batch for better performance.
     *
     * @param topic the Kafka topic
     * @param messages the messages to send (key-value pairs)
     * @return CompletableFuture with all send results
     */
    public CompletableFuture<Void> sendBatch(String topic, java.util.Map<String, Object> messages) {
        String correlationId = generateCorrelationId();
        log.info("Sending batch of {} messages to topic: {}, correlationId: {}", 
            messages.size(), topic, correlationId);

        CompletableFuture<?>[] futures = messages.entrySet().stream()
            .map(entry -> sendMessage(topic, entry.getKey(), entry.getValue()))
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
            .whenComplete((result, exception) -> {
                if (exception == null) {
                    log.info("Batch send completed successfully - topic: {}, count: {}, correlationId: {}", 
                        topic, messages.size(), correlationId);
                } else {
                    log.error("Batch send failed - topic: {}, correlationId: {}, error: {}", 
                        topic, correlationId, exception.getMessage(), exception);
                }
            });
    }

    /**
     * Generate a unique correlation ID for message tracking.
     *
     * @return correlation ID string
     */
    private String generateCorrelationId() {
        return "kafka-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get producer health information.
     *
     * @return health status information
     */
    public java.util.Map<String, Object> getHealthInfo() {
        return java.util.Map.of(
            "messagesSent", messagesSentCounter.count(),
            "messagesFailed", messagesFailedCounter.count(),
            "successRate", messagesSentCounter.count() / (messagesSentCounter.count() + messagesFailedCounter.count() + 0.001),
            "averageSendTime", sendTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS)
        );
    }
}

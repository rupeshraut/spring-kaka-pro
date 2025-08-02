package com.company.kafka.streaming;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Improved Kafka consumer using the unified streaming processor architecture.
 * 
 * Key Improvements:
 * 1. Uses UnifiedStreamingProcessor for better resource management
 * 2. Simplified message flow with direct processing
 * 3. Built-in health monitoring and circuit breaker protection
 * 4. Automatic acknowledgment handling
 * 5. Comprehensive metrics and error handling
 * 
 * Benefits:
 * - Single processor for all message types
 * - Eliminates complex adapter chains  
 * - Better error recovery and resilience
 * - Memory-efficient with proper lifecycle management
 * - Real-time health monitoring and alerting
 */
@Service
@Slf4j
public class StreamingKafkaConsumer {

    private final UnifiedStreamingProcessor processor;
    private final MeterRegistry meterRegistry;

    public StreamingKafkaConsumer(UnifiedStreamingProcessor processor, MeterRegistry meterRegistry) {
        this.processor = processor;
        this.meterRegistry = meterRegistry;
    }


    /**
     * Kafka listener for orders using the unified streaming processor.
     */
    @KafkaListener(
        topics = "${kafka.topics.orders.name:orders-topic}",
        groupId = "${kafka.consumer.group-id:streaming-consumer-group}",
        concurrency = "2" // Limited concurrency to control memory usage
    )
    public void consumeOrdersWithStreaming(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        processMessage(message, topic, partition, offset, acknowledgment);
    }

    /**
     * Kafka listener for payments using the unified streaming processor.
     */
    @KafkaListener(
        topics = "${kafka.topics.payments.name:payments-topic}",
        groupId = "${kafka.consumer.group-id:streaming-bulk-group}",
        concurrency = "1" // Single thread for ordered processing
    )
    public void consumePaymentsWithStreaming(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        processMessage(message, topic, partition, offset, acknowledgment);
    }

    /**
     * Common message processing method using the unified streaming processor.
     */
    private void processMessage(String message, String topic, int partition, long offset, Acknowledgment acknowledgment) {
        String correlationId = java.util.UUID.randomUUID().toString();
        
        log.debug("Received message for streaming processing - topic: {}, partition: {}, offset: {}, correlationId: {}", 
            topic, partition, offset, correlationId);

        // Create message envelope
        UnifiedStreamingProcessor.MessageEnvelope envelope = new UnifiedStreamingProcessor.MessageEnvelope(
            message, acknowledgment, correlationId, topic, partition, offset);
        
        try {
            // Process message using unified processor (async)
            processor.processMessage(envelope)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Error processing message - correlationId: {}, error: {}", 
                            correlationId, throwable.getMessage(), throwable);
                        meterRegistry.counter("kafka.streaming.unified.processing.errors",
                            "topic", topic).increment();
                    } else {
                        handleProcessingResult(result, topic);
                    }
                });
            
            meterRegistry.counter("kafka.streaming.unified.messages.submitted", "topic", topic).increment();
            
        } catch (Exception e) {
            log.error("Error submitting message to unified processor - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
            
            // Acknowledge even on error to prevent reprocessing
            acknowledgment.acknowledge();
            meterRegistry.counter("kafka.streaming.unified.messages.submit.failed", "topic", topic).increment();
        }
    }

    /**
     * Handle processing results from the unified processor.
     */
    private void handleProcessingResult(UnifiedStreamingProcessor.ProcessingResult result, String topic) {
        String status = result.getStatus();
        
        if (result.isSuccess()) {
            log.debug("Successfully processed message - correlationId: {}, processingTime: {}ns", 
                result.getCorrelationId(), result.getProcessingTimeNanos());
            
            meterRegistry.counter("kafka.streaming.unified.messages.success", "topic", topic).increment();
            meterRegistry.timer("kafka.streaming.unified.processing.time", "topic", topic)
                .record(result.getProcessingTimeNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
            
        } else {
            log.error("Failed to process message - correlationId: {}, status: {}, error: {}", 
                result.getCorrelationId(), status, 
                result.getError() != null ? result.getError().getMessage() : "Unknown error");
            
            meterRegistry.counter("kafka.streaming.unified.messages.failed", 
                "topic", topic, "status", status).increment();
        }
    }

}

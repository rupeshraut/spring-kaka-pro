package com.company.kafka.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive Kafka Metrics Collection
 * 
 * Pro Tips:
 * 1. Monitor both business and technical metrics
 * 2. Use proper metric types (Counter, Gauge, Timer, Histogram)
 * 3. Add meaningful tags for filtering and aggregation
 * 4. Set up alerts based on metric thresholds
 * 5. Monitor consumer lag regularly
 * 6. Track error rates and success rates
 * 
 * @author Development Team
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaMetrics {

    private final MeterRegistry meterRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Atomic counters for real-time metrics
    private final AtomicLong messagesProduced = new AtomicLong(0);
    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong producerErrors = new AtomicLong(0);
    private final AtomicLong consumerErrors = new AtomicLong(0);
    
    // Metric instances
    private Timer producerSendTimer;
    private Timer consumerProcessingTimer;
    private Counter producerSuccessCounter;
    private Counter producerErrorCounter;
    private Counter consumerSuccessCounter;
    private Counter consumerErrorCounter;

    @PostConstruct
    public void initializeMetrics() {
        // Producer metrics
        producerSendTimer = Timer.builder("kafka.producer.send.time")
            .description("Time taken to send messages to Kafka")
            .register(meterRegistry);
            
        producerSuccessCounter = Counter.builder("kafka.producer.messages.success")
            .description("Number of successfully sent messages")
            .register(meterRegistry);
            
        producerErrorCounter = Counter.builder("kafka.producer.messages.error")
            .description("Number of producer errors")
            .register(meterRegistry);
        
        // Consumer metrics
        consumerProcessingTimer = Timer.builder("kafka.consumer.processing.time")
            .description("Time taken to process consumed messages")
            .register(meterRegistry);
            
        consumerSuccessCounter = Counter.builder("kafka.consumer.messages.success")
            .description("Number of successfully processed messages")
            .register(meterRegistry);
            
        consumerErrorCounter = Counter.builder("kafka.consumer.messages.error")
            .description("Number of consumer processing errors")
            .register(meterRegistry);
        
        // Gauge metrics for current state
        Gauge.builder("kafka.producer.messages.produced.total", messagesProduced, AtomicLong::get)
            .description("Total number of messages produced")
            .register(meterRegistry);
            
        Gauge.builder("kafka.consumer.messages.consumed.total", messagesConsumed, AtomicLong::get)
            .description("Total number of messages consumed")
            .register(meterRegistry);
            
        Gauge.builder("kafka.producer.errors.total", producerErrors, AtomicLong::get)
            .description("Total number of producer errors")
            .register(meterRegistry);
            
        Gauge.builder("kafka.consumer.errors.total", consumerErrors, AtomicLong::get)
            .description("Total number of consumer errors")
            .register(meterRegistry);
        
        // Connection metrics
        registerConnectionMetrics();
        
        log.info("Kafka metrics initialized successfully");
    }

    private void registerConnectionMetrics() {
        // Producer connection metrics
        Gauge.builder("kafka.producer.connections.active", this, metrics -> metrics.getActiveProducerConnections(metrics))
            .description("Number of active producer connections")
            .register(meterRegistry);
            
        // Add JVM and Kafka-specific metrics
        Gauge.builder("kafka.producer.buffer.memory.available", this, metrics -> metrics.getAvailableBufferMemory(metrics))
            .description("Available buffer memory for producer")
            .register(meterRegistry);
    }

    // Producer metrics methods
    public void recordProducerSuccess(String topic, String eventType) {
        messagesProduced.incrementAndGet();
        Counter.builder("kafka.producer.messages.success")
            .tag("topic", topic)
            .tag("event_type", eventType)
            .register(meterRegistry)
            .increment();
    }

    public void recordProducerError(String topic, String eventType, Exception error) {
        producerErrors.incrementAndGet();
        Counter.builder("kafka.producer.messages.error")
            .tag("topic", topic)
            .tag("event_type", eventType)
            .tag("error_type", error.getClass().getSimpleName())
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startProducerTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopProducerTimer(Timer.Sample sample, String topic, String eventType, boolean success) {
        sample.stop(Timer.builder("kafka.producer.send.time")
            .tag("topic", topic)
            .tag("event_type", eventType)
            .tag("status", success ? "success" : "failure")
            .register(meterRegistry));
    }

    // Consumer metrics methods
    public void recordConsumerSuccess(String topic, String eventType) {
        messagesConsumed.incrementAndGet();
        Counter.builder("kafka.consumer.messages.success")
            .tag("topic", topic)
            .tag("event_type", eventType)
            .register(meterRegistry)
            .increment();
    }

    public void recordConsumerError(String topic, String eventType, Exception error) {
        consumerErrors.incrementAndGet();
        Counter.builder("kafka.consumer.messages.error")
            .tag("topic", topic)
            .tag("event_type", eventType)
            .tag("error_type", error.getClass().getSimpleName())
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startConsumerTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopConsumerTimer(Timer.Sample sample, String topic, String eventType, boolean success) {
        sample.stop(Timer.builder("kafka.consumer.processing.time")
            .tag("topic", topic)
            .tag("event_type", eventType)
            .tag("status", success ? "success" : "failure")
            .register(meterRegistry));
    }

    // Connection monitoring methods
    private double getActiveProducerConnections(KafkaMetrics metrics) {
        try {
            return kafkaTemplate.getProducerFactory()
                .createProducer()
                .metrics()
                .entrySet().stream()
                .filter(entry -> "connection-count".equals(entry.getKey().name()))
                .mapToDouble(entry -> ((Number) entry.getValue().metricValue()).doubleValue())
                .sum();
        } catch (Exception e) {
            log.warn("Failed to get producer connection count: {}", e.getMessage());
            return 0.0;
        }
    }

    private double getAvailableBufferMemory(KafkaMetrics metrics) {
        try {
            return kafkaTemplate.getProducerFactory()
                .createProducer()
                .metrics()
                .entrySet().stream()
                .filter(entry -> "buffer-available-bytes".equals(entry.getKey().name()))
                .mapToDouble(entry -> ((Number) entry.getValue().metricValue()).doubleValue())
                .findFirst()
                .orElse(0.0);
        } catch (Exception e) {
            log.warn("Failed to get available buffer memory: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Producer Listener for automatic metrics collection
     */
    public static class ProducerMetricsListener implements ProducerListener<String, Object> {
        
        @Override
        public void onSuccess(ProducerRecord<String, Object> producerRecord, RecordMetadata recordMetadata) {
            log.debug("Message sent successfully to topic: {}, partition: {}, offset: {}", 
                recordMetadata.topic(), recordMetadata.partition(), recordMetadata.offset());
        }

        @Override
        public void onError(ProducerRecord<String, Object> producerRecord, RecordMetadata recordMetadata, Exception exception) {
            log.error("Failed to send message to topic: {}", producerRecord.topic(), exception);
        }
    }

    /**
     * Custom Task Executor for consumer metrics
     */
    public static class ConsumerTaskExecutor extends ThreadPoolTaskExecutor {
        
        public ConsumerTaskExecutor() {
            super();
            setCorePoolSize(5);
            setMaxPoolSize(10);
            setQueueCapacity(100);
            setThreadNamePrefix("kafka-consumer-");
            setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
            initialize();
        }
    }

    /**
     * Get current metrics summary for monitoring dashboards
     */
    public MetricsSummary getMetricsSummary() {
        return MetricsSummary.builder()
            .messagesProduced(messagesProduced.get())
            .messagesConsumed(messagesConsumed.get())
            .producerErrors(producerErrors.get())
            .consumerErrors(consumerErrors.get())
            .activeConnections(getActiveProducerConnections(this))
            .availableBufferMemory(getAvailableBufferMemory(this))
            .timestamp(System.currentTimeMillis())
            .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class MetricsSummary {
        private final long messagesProduced;
        private final long messagesConsumed; 
        private final long producerErrors;
        private final long consumerErrors;
        private final double activeConnections;
        private final double availableBufferMemory;
        private final long timestamp;
    }
}

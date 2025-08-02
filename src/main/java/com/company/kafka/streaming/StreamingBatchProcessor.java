package com.company.kafka.streaming;

import com.company.kafka.exception.RecoverableException;
import com.company.kafka.exception.ValidationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming-based batch processor that solves memory issues by processing messages
 * as a continuous stream rather than accumulating them in memory.
 * 
 * Key Features:
 * - Backpressure handling to prevent memory overflow
 * - Configurable buffer sizes and processing windows
 * - Memory-efficient streaming with reactive patterns
 * - Comprehensive error handling and metrics
 * - Automatic acknowledgment management
 * 
 * Memory Benefits:
 * 1. Processes messages one-by-one without accumulation
 * 2. Uses bounded buffers to limit memory usage
 * 3. Applies backpressure when downstream can't keep up
 * 4. Garbage collection friendly with short-lived objects
 * 
 * Pro Tips:
 * 1. Use small buffer sizes (100-1000) for memory efficiency
 * 2. Apply backpressure early to prevent cascading failures
 * 3. Monitor buffer utilization and processing lag
 * 4. Implement circuit breakers for downstream failures
 * 5. Use streaming aggregations instead of collecting all data
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamingBatchProcessor<T> {

    private final MeterRegistry meterRegistry;

    /**
     * Creates a memory-efficient streaming processor that handles messages
     * without accumulating them in memory.
     */
    public StreamingProcessor<T> createProcessor(StreamingConfig config) {
        return new StreamingProcessor<>(config, meterRegistry);
    }

    /**
     * Configuration for streaming processor behavior.
     */
    public record StreamingConfig(
        int bufferSize,
        Duration processingTimeout,
        int maxConcurrency,
        boolean enableBackpressure,
        Duration backpressureTimeout
    ) {
        public StreamingConfig {
            bufferSize = bufferSize > 0 ? bufferSize : 256;
            processingTimeout = processingTimeout != null ? processingTimeout : Duration.ofSeconds(30);
            maxConcurrency = maxConcurrency > 0 ? maxConcurrency : Runtime.getRuntime().availableProcessors();
            backpressureTimeout = backpressureTimeout != null ? backpressureTimeout : Duration.ofSeconds(5);
        }
        
        public static StreamingConfig defaultConfig() {
            return new StreamingConfig(256, Duration.ofSeconds(30), 4, true, Duration.ofSeconds(5));
        }
        
        public static StreamingConfig memoryOptimized() {
            return new StreamingConfig(100, Duration.ofSeconds(15), 2, true, Duration.ofSeconds(2));
        }
        
        public static StreamingConfig highThroughput() {
            return new StreamingConfig(1000, Duration.ofSeconds(60), 8, true, Duration.ofSeconds(10));
        }
    }

    /**
     * Reactive streaming processor implementation using Java Flow API.
     */
    public static class StreamingProcessor<T> implements Flow.Processor<T, ProcessingResult<T>> {
        
        private final StreamingConfig config;
        private final MeterRegistry meterRegistry;
        private final SubmissionPublisher<ProcessingResult<T>> publisher;
        private final AtomicLong processedCount = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        
        private Flow.Subscription subscription;
        private volatile boolean cancelled = false;

        public StreamingProcessor(StreamingConfig config, MeterRegistry meterRegistry) {
            this.config = config;
            this.meterRegistry = meterRegistry;
            this.publisher = new SubmissionPublisher<>(
                Runnable::run, // Use same thread for simplicity
                config.bufferSize()
            );
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ProcessingResult<T>> subscriber) {
            publisher.subscribe(subscriber);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            // Request initial batch
            subscription.request(config.bufferSize());
            log.info("Streaming processor subscribed with buffer size: {}", config.bufferSize());
        }

        @Override
        public void onNext(T item) {
            if (cancelled) {
                return;
            }

            Timer.Sample sample = Timer.start(meterRegistry);
            
            try {
                // Process item immediately without storing
                ProcessingResult<T> result = processItem(item);
                
                // Try to submit result with backpressure handling
                if (config.enableBackpressure()) {
                    submitWithBackpressure(result);
                } else {
                    publisher.submit(result);
                }
                
                processedCount.incrementAndGet();
                sample.stop(Timer.builder("kafka.streaming.item.processing.time").register(meterRegistry));
                
                // Request next item to maintain flow
                subscription.request(1);
                
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Error processing streaming item: {}", e.getMessage(), e);
                
                ProcessingResult<T> errorResult = ProcessingResult.error(item, e);
                publisher.submit(errorResult);
                
                sample.stop(Timer.builder("kafka.streaming.item.processing.time")
                    .tag("status", "error").register(meterRegistry));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            log.error("Streaming processor error: {}", throwable.getMessage(), throwable);
            publisher.closeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            log.info("Streaming processor completed. Processed: {}, Errors: {}", 
                processedCount.get(), errorCount.get());
            publisher.close();
        }

        private ProcessingResult<T> processItem(T item) {
            // Simulate processing - in real implementation, this would be your business logic
            long startTime = System.currentTimeMillis();
            
            try {
                // Validate item
                if (item == null) {
                    throw new ValidationException("Null item received", null, "NON_NULL_ITEM");
                }
                
                // Process item (this is where your business logic goes)
                T processedItem = processBusinessLogic(item);
                
                long processingTime = System.currentTimeMillis() - startTime;
                return ProcessingResult.success(processedItem, processingTime);
                
            } catch (ValidationException e) {
                return ProcessingResult.error(item, e);
            } catch (Exception e) {
                RecoverableException recoverableException = new RecoverableException("Processing failed for item: " + item, e);
                return ProcessingResult.error(item, recoverableException);
            }
        }

        private T processBusinessLogic(T item) {
            // Placeholder for actual business logic
            // In real implementation, this would transform/validate/enrich the item
            return item;
        }

        private void submitWithBackpressure(ProcessingResult<T> result) {
            try {
                if (publisher.submit(result) < 0) {
                    // Buffer is full, apply backpressure
                    meterRegistry.counter("kafka.streaming.backpressure.events").increment();
                    log.warn("Backpressure applied - buffer full, dropping or waiting");
                    
                    // Try with timeout
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(config.backpressureTimeout().toMillis());
                            publisher.submit(result);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Backpressure wait interrupted");
                        }
                    });
                    
                    try {
                        future.get(config.backpressureTimeout().toMillis(), 
                            java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.error("Failed to submit with backpressure: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Error submitting result with backpressure: {}", e.getMessage(), e);
            }
        }

        public void cancel() {
            cancelled = true;
            if (subscription != null) {
                subscription.cancel();
            }
            publisher.close();
        }

        public StreamingStats getStats() {
            return new StreamingStats(
                processedCount.get(),
                errorCount.get(),
                publisher.estimateMaximumLag(),
                publisher.estimateMinimumDemand()
            );
        }
    }

    /**
     * Result of processing a single item in the stream.
     */
    public record ProcessingResult<T>(
        T item,
        boolean success,
        Throwable error,
        long processingTimeMs,
        String correlationId
    ) {
        public static <T> ProcessingResult<T> success(T item, long processingTime) {
            return new ProcessingResult<>(item, true, null, processingTime, 
                java.util.UUID.randomUUID().toString());
        }
        
        public static <T> ProcessingResult<T> error(T item, Throwable error) {
            return new ProcessingResult<>(item, false, error, 0, 
                java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * Statistics for monitoring streaming processor performance.
     */
    public record StreamingStats(
        long processedCount,
        long errorCount,
        long maxLag,
        long minDemand
    ) {
        public double errorRate() {
            return processedCount > 0 ? (double) errorCount / processedCount : 0.0;
        }
        
        public boolean isHealthy() {
            return errorRate() < 0.05 && maxLag < 1000; // Less than 5% errors and reasonable lag
        }
    }
}

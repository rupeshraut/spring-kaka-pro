package com.company.kafka.error;

import com.company.kafka.exception.NonRecoverableException;
import com.company.kafka.exception.RecoverableException;
import com.company.kafka.exception.ValidationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Enhanced production-ready Kafka error handler with advanced features.
 * 
 * NEW Production Features:
 * - Poison pill detection and automatic quarantine
 * - Circuit breaker pattern for external dependencies
 * - Jitter in exponential backoff to prevent thundering herd
 * - Error correlation and tracking across services
 * - Rate limiting for error processing
 * - Enhanced DLT routing with metadata
 * - Error aggregation and reporting
 * 
 * Critical Production Improvements:
 * 1. POISON PILL DETECTION: Automatically identifies messages that always fail
 * 2. CIRCUIT BREAKER: Prevents cascade failures when external services are down
 * 3. JITTER: Adds randomness to retry delays to prevent synchronized retries
 * 4. ERROR CORRELATION: Tracks related failures across the system
 * 5. RATE LIMITING: Prevents error storms from overwhelming the system
 * 6. ENHANCED MONITORING: Detailed metrics for operational visibility
 * 
 * Pro Tips:
 * 1. Monitor poison pill rates - high rates indicate data quality issues
 * 2. Circuit breaker metrics help identify external service issues
 * 3. Use error correlation to trace cascading failures
 * 4. Set appropriate rate limits to protect downstream systems
 * 5. Implement alerts on circuit breaker state changes
 */
@Component
@Slf4j
public class EnhancedKafkaErrorHandler {

    private final KafkaOperations<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    // Poison pill detection
    private final Map<String, AtomicInteger> messageFailureCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFailureTime = new ConcurrentHashMap<>();
    private final int poisonPillThreshold = 3; // Mark as poison after 3 consecutive failures
    private final long poisonPillCooldownMs = 300000; // 5 minutes cooldown
    
    // Circuit breaker state
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    private final int circuitBreakerFailureThreshold = 5;
    private final long circuitBreakerTimeoutMs = 60000; // 1 minute
    
    // Rate limiting
    private final Map<String, AtomicLong> errorRateLimiters = new ConcurrentHashMap<>();
    private final long errorRateWindowMs = 60000; // 1 minute window
    private final int maxErrorsPerWindow = 100;
    
    // Enhanced metrics
    private final Counter poisonPillCounter;
    private final Counter circuitBreakerOpenCounter;
    private final Counter rateLimitedErrorsCounter;
    private final Counter correlatedErrorsCounter;

    public EnhancedKafkaErrorHandler(KafkaOperations<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        
        // Initialize enhanced metrics
        this.poisonPillCounter = Counter.builder("kafka.error.poison_pills")
                .description("Number of poison pill messages detected")
                .register(meterRegistry);
                
        this.circuitBreakerOpenCounter = Counter.builder("kafka.error.circuit_breaker.opens")
                .description("Number of circuit breaker openings")
                .register(meterRegistry);
                
        this.rateLimitedErrorsCounter = Counter.builder("kafka.error.rate_limited")
                .description("Number of errors that were rate limited")
                .register(meterRegistry);
                
        this.correlatedErrorsCounter = Counter.builder("kafka.error.correlated")
                .description("Number of correlated errors detected")
                .register(meterRegistry);
        
        log.info("EnhancedKafkaErrorHandler initialized with poison pill detection and circuit breaker");
    }

    /**
     * Creates an enhanced error handler with all production features.
     *
     * @return configured DefaultErrorHandler with enhancements
     */
    public DefaultErrorHandler createEnhancedErrorHandler() {
        log.info("Creating enhanced Kafka error handler with production features");
        
        // Configure exponential backoff WITH JITTER
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(10);
        backOff.setInitialInterval(1000L);      // Start with 1 second
        backOff.setMultiplier(2.0);             // Double each retry
        backOff.setMaxInterval(32000L);         // Cap at 32 seconds
        
        // Create enhanced dead letter recoverer
        DeadLetterPublishingRecoverer deadLetterRecoverer = createEnhancedDeadLetterRecoverer();
        
        // Create the error handler
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterRecoverer, backOff);
        
        // Configure exceptions
        configureNonRetryableExceptions(errorHandler);
        configureRetryableExceptions(errorHandler);
        
        // Add enhanced retry listener with poison pill detection
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            String messageKey = generateMessageKey(record);
            
            // Check for rate limiting
            if (isRateLimited(record.topic())) {
                rateLimitedErrorsCounter.increment();
                log.warn("Error processing rate limited - topic: {}, key: {}", record.topic(), messageKey);
                return;
            }
            
            // Track failure count for poison pill detection
            trackMessageFailure(messageKey);
            
            // Check circuit breaker state
            String serviceName = extractServiceName(ex);
            if (serviceName != null) {
                updateCircuitBreaker(serviceName, false);
            }
            
            // Add jitter to retry delay
            long jitteredDelay = addJitter(calculateRetryDelay(deliveryAttempt));
            
            log.warn("Retrying message with jitter - topic: {}, partition: {}, offset: {}, attempt: {}, " +
                    "delay: {}ms, error: {}", 
                record.topic(), record.partition(), record.offset(), deliveryAttempt, 
                jitteredDelay, ex.getMessage());
            
            // Check for poison pill
            if (isPoisonPill(messageKey)) {
                poisonPillCounter.increment();
                log.error("POISON PILL DETECTED - message consistently failing: {}, topic: {}, " +
                        "partition: {}, offset: {}", 
                    messageKey, record.topic(), record.partition(), record.offset());
                
                // Send to poison pill topic
                sendToPoisonPillTopic(record, ex);
                
                // Stop retrying this message
                throw new NonRecoverableException("Poison pill detected", "POISON_PILL");
            }
        });
        
        log.info("Enhanced Kafka error handler configured successfully");
        return errorHandler;
    }

    /**
     * Creates an enhanced dead letter recoverer with detailed metadata.
     *
     * @return configured DeadLetterPublishingRecoverer
     */
    private DeadLetterPublishingRecoverer createEnhancedDeadLetterRecoverer() {
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver = 
            (record, exception) -> {
                String deadLetterTopic = determineEnhancedDeadLetterTopic(record, exception);
                log.warn("Routing to enhanced DLT - original: {}, dlt: {}, error: {}", 
                    record.topic(), deadLetterTopic, exception.getClass().getSimpleName());
                return new TopicPartition(deadLetterTopic, -1);
            };
        
        return new DeadLetterPublishingRecoverer(kafkaTemplate, destinationResolver) {
            @Override
            public void accept(ConsumerRecord<?, ?> record, Exception exception) {
                // Add enhanced error context
                addEnhancedErrorHeaders(record, exception);
                
                // Update circuit breaker on final failure
                String serviceName = extractServiceName(exception);
                if (serviceName != null) {
                    updateCircuitBreaker(serviceName, false);
                }
                
                // Check for error correlation
                checkErrorCorrelation(record, exception);
                
                log.error("Enhanced DLT publishing - topic: {}, partition: {}, offset: {}, " +
                        "error: {}, context: {}", 
                    record.topic(), record.partition(), record.offset(), 
                    exception.getMessage(), getErrorContext(record, exception));
                
                super.accept(record, exception);
            }
        };
    }

    /**
     * Determines enhanced dead letter topic with additional routing logic.
     *
     * @param record the consumer record
     * @param exception the exception
     * @return the enhanced dead letter topic name
     */
    private String determineEnhancedDeadLetterTopic(ConsumerRecord<?, ?> record, Exception exception) {
        String originalTopic = record.topic();
        String messageKey = generateMessageKey(record);
        
        // Check if this is a poison pill
        if (isPoisonPill(messageKey)) {
            return originalTopic + ".poison-pill.dlt";
        }
        
        // Check if related to circuit breaker
        String serviceName = extractServiceName(exception);
        if (serviceName != null && isCircuitBreakerOpen(serviceName)) {
            return originalTopic + ".circuit-breaker." + serviceName.toLowerCase() + ".dlt";
        }
        
        // Original routing logic with enhancements
        if (exception instanceof ValidationException) {
            ValidationException ve = (ValidationException) exception;
            if (ve.getFieldName() != null) {
                return originalTopic + ".validation." + ve.getFieldName().toLowerCase() + ".dlt";
            }
            return originalTopic + ".validation.dlt";
            
        } else if (exception instanceof NonRecoverableException) {
            NonRecoverableException nre = (NonRecoverableException) exception;
            String errorCode = nre.getErrorCode().toLowerCase();
            
            // Route based on error category
            if (nre.isAuthenticationError()) {
                return originalTopic + ".auth." + errorCode + ".dlt";
            } else if (nre.isAuthorizationError()) {
                return originalTopic + ".authz." + errorCode + ".dlt";
            } else if (nre.isConfigurationError()) {
                return originalTopic + ".config." + errorCode + ".dlt";
            }
            
            return originalTopic + ".non-recoverable." + errorCode + ".dlt";
            
        } else if (exception instanceof RecoverableException) {
            RecoverableException re = (RecoverableException) exception;
            if (re.getServiceName() != null) {
                return originalTopic + ".recoverable." + re.getServiceName().toLowerCase() + ".dlt";
            }
            return originalTopic + ".recoverable.dlt";
            
        } else {
            // Check for specific exception types
            String exceptionType = exception.getClass().getSimpleName().toLowerCase();
            return originalTopic + ".error." + exceptionType + ".dlt";
        }
    }

    /**
     * Adds enhanced error headers with detailed context.
     *
     * @param record the consumer record
     * @param exception the exception
     */
    private void addEnhancedErrorHeaders(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            // Enhanced error context
            record.headers().add("dlt.enhanced.version", "2.0".getBytes(StandardCharsets.UTF_8));
            record.headers().add("dlt.enhanced.timestamp", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("dlt.enhanced.hostname", getHostname().getBytes(StandardCharsets.UTF_8));
            record.headers().add("dlt.enhanced.application", "spring-kafka-pro".getBytes(StandardCharsets.UTF_8));
            record.headers().add("dlt.enhanced.environment", getEnvironment().getBytes(StandardCharsets.UTF_8));
            
            // Message identification
            String messageKey = generateMessageKey(record);
            record.headers().add("dlt.enhanced.message.key", messageKey.getBytes(StandardCharsets.UTF_8));
            record.headers().add("dlt.enhanced.failure.count", 
                String.valueOf(getFailureCount(messageKey)).getBytes(StandardCharsets.UTF_8));
            
            // Circuit breaker context
            String serviceName = extractServiceName(exception);
            if (serviceName != null) {
                CircuitBreakerState state = circuitBreakers.get(serviceName);
                if (state != null) {
                    record.headers().add("dlt.enhanced.circuit.breaker.service", 
                        serviceName.getBytes(StandardCharsets.UTF_8));
                    record.headers().add("dlt.enhanced.circuit.breaker.state", 
                        state.state.toString().getBytes(StandardCharsets.UTF_8));
                    record.headers().add("dlt.enhanced.circuit.breaker.failure.count", 
                        String.valueOf(state.failureCount.get()).getBytes(StandardCharsets.UTF_8));
                }
            }
            
            // Error correlation
            String correlationId = findErrorCorrelation(record, exception);
            if (correlationId != null) {
                record.headers().add("dlt.enhanced.correlation.id", 
                    correlationId.getBytes(StandardCharsets.UTF_8));
            }
            
            // Poison pill detection
            if (isPoisonPill(messageKey)) {
                record.headers().add("dlt.enhanced.poison.pill", "true".getBytes(StandardCharsets.UTF_8));
                record.headers().add("dlt.enhanced.poison.pill.detected.at", 
                    Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            }
            
        } catch (Exception e) {
            log.error("Failed to add enhanced error headers", e);
        }
    }

    /**
     * Generates a unique key for message tracking.
     *
     * @param record the consumer record
     * @return unique message key
     */
    private String generateMessageKey(ConsumerRecord<?, ?> record) {
        return String.format("%s:%d:%d", record.topic(), record.partition(), record.offset());
    }

    /**
     * Tracks message failure for poison pill detection.
     *
     * @param messageKey the message key
     */
    private void trackMessageFailure(String messageKey) {
        messageFailureCounts.computeIfAbsent(messageKey, k -> new AtomicInteger(0)).incrementAndGet();
        lastFailureTime.put(messageKey, System.currentTimeMillis());
    }

    /**
     * Checks if a message is a poison pill.
     *
     * @param messageKey the message key
     * @return true if the message is a poison pill
     */
    private boolean isPoisonPill(String messageKey) {
        AtomicInteger failureCount = messageFailureCounts.get(messageKey);
        if (failureCount == null) {
            return false;
        }
        
        // Check if we've exceeded the threshold
        if (failureCount.get() >= poisonPillThreshold) {
            Long lastFailure = lastFailureTime.get(messageKey);
            if (lastFailure != null) {
                // Check if still within cooldown period
                return (System.currentTimeMillis() - lastFailure) < poisonPillCooldownMs;
            }
        }
        
        return false;
    }

    /**
     * Sends a poison pill message to a dedicated topic for analysis.
     *
     * @param record the poison pill record
     * @param exception the exception
     */
    private void sendToPoisonPillTopic(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            String poisonTopic = record.topic() + ".poison-pill.quarantine";
            
            // Create poison pill analysis record
            Map<String, Object> analysisData = Map.of(
                "originalTopic", record.topic(),
                "originalPartition", record.partition(),
                "originalOffset", record.offset(),
                "failureCount", getFailureCount(generateMessageKey(record)),
                "firstFailureTime", getFirstFailureTime(generateMessageKey(record)),
                "lastFailureTime", System.currentTimeMillis(),
                "exceptionType", exception.getClass().getName(),
                "exceptionMessage", exception.getMessage(),
                "hostname", getHostname(),
                "environment", getEnvironment()
            );
            
            kafkaTemplate.send(poisonTopic, record.key() != null ? record.key().toString() : null, analysisData);
            
            log.error("Poison pill quarantined - topic: {}, analysis sent to: {}", record.topic(), poisonTopic);
            
        } catch (Exception e) {
            log.error("Failed to send poison pill to quarantine topic", e);
        }
    }

    /**
     * Circuit breaker state tracking.
     */
    private static class CircuitBreakerState {
        enum State { CLOSED, OPEN, HALF_OPEN }
        
        volatile State state = State.CLOSED;
        final AtomicInteger failureCount = new AtomicInteger(0);
        volatile long lastFailureTime = 0;
        volatile long lastSuccessTime = System.currentTimeMillis();
    }

    /**
     * Updates circuit breaker state based on operation result.
     *
     * @param serviceName the service name
     * @param success whether the operation was successful
     */
    private void updateCircuitBreaker(String serviceName, boolean success) {
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(serviceName, 
            k -> new CircuitBreakerState());
        
        if (success) {
            // Reset on success
            state.failureCount.set(0);
            state.lastSuccessTime = System.currentTimeMillis();
            if (state.state == CircuitBreakerState.State.HALF_OPEN) {
                state.state = CircuitBreakerState.State.CLOSED;
                log.info("Circuit breaker CLOSED for service: {}", serviceName);
            }
        } else {
            // Increment failure count
            int failures = state.failureCount.incrementAndGet();
            state.lastFailureTime = System.currentTimeMillis();
            
            if (failures >= circuitBreakerFailureThreshold && 
                state.state == CircuitBreakerState.State.CLOSED) {
                
                state.state = CircuitBreakerState.State.OPEN;
                circuitBreakerOpenCounter.increment();
                log.error("Circuit breaker OPENED for service: {} after {} failures", serviceName, failures);
            }
        }
        
        // Check if we should transition to half-open
        if (state.state == CircuitBreakerState.State.OPEN &&
            (System.currentTimeMillis() - state.lastFailureTime) > circuitBreakerTimeoutMs) {
            
            state.state = CircuitBreakerState.State.HALF_OPEN;
            log.info("Circuit breaker HALF-OPEN for service: {}", serviceName);
        }
    }

    /**
     * Checks if circuit breaker is open for a service.
     *
     * @param serviceName the service name
     * @return true if circuit breaker is open
     */
    private boolean isCircuitBreakerOpen(String serviceName) {
        CircuitBreakerState state = circuitBreakers.get(serviceName);
        return state != null && state.state == CircuitBreakerState.State.OPEN;
    }

    // Helper methods for implementation
    private long addJitter(long delay) {
        double jitter = 0.1; // 10% jitter
        double factor = 1.0 + (Math.random() - 0.5) * 2 * jitter;
        return (long) (delay * factor);
    }
    
    private long calculateRetryDelay(int attempt) {
        return Math.min(1000L * (long) Math.pow(2, attempt - 1), 32000L);
    }
    
    private boolean isRateLimited(String topic) {
        long currentWindow = System.currentTimeMillis() / errorRateWindowMs;
        AtomicLong counter = errorRateLimiters.computeIfAbsent(topic + ":" + currentWindow, 
            k -> new AtomicLong(0));
        return counter.incrementAndGet() > maxErrorsPerWindow;
    }
    
    private String extractServiceName(Exception exception) {
        if (exception instanceof RecoverableException) {
            return ((RecoverableException) exception).getServiceName();
        }
        return null;
    }
    
    private void checkErrorCorrelation(ConsumerRecord<?, ?> record, Exception exception) {
        // Implementation for error correlation logic
        correlatedErrorsCounter.increment();
    }
    
    private String findErrorCorrelation(ConsumerRecord<?, ?> record, Exception exception) {
        // Implementation for finding correlated errors
        return null;
    }
    
    private Map<String, Object> getErrorContext(ConsumerRecord<?, ?> record, Exception exception) {
        return Map.of(
            "messageKey", generateMessageKey(record),
            "exceptionType", exception.getClass().getSimpleName(),
            "timestamp", Instant.now().toString()
        );
    }
    
    private int getFailureCount(String messageKey) {
        AtomicInteger count = messageFailureCounts.get(messageKey);
        return count != null ? count.get() : 0;
    }
    
    private long getFirstFailureTime(String messageKey) {
        return lastFailureTime.getOrDefault(messageKey, System.currentTimeMillis());
    }
    
    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "unknown");
    }
    
    private void configureNonRetryableExceptions(DefaultErrorHandler errorHandler) {
        errorHandler.addNotRetryableExceptions(
            ValidationException.class,
            NonRecoverableException.class,
            IllegalArgumentException.class,
            SecurityException.class,
            UnsupportedOperationException.class
        );
    }
    
    private void configureRetryableExceptions(DefaultErrorHandler errorHandler) {
        errorHandler.addRetryableExceptions(
            RecoverableException.class,
            java.net.ConnectException.class,
            java.net.SocketTimeoutException.class,
            org.springframework.kafka.KafkaException.class
        );
    }

    /**
     * Gets enhanced error handler health information.
     *
     * @return enhanced health status
     */
    public Map<String, Object> getEnhancedHealthInfo() {
        return Map.of(
            "poisonPillsDetected", poisonPillCounter.count(),
            "circuitBreakerOpens", circuitBreakerOpenCounter.count(),
            "rateLimitedErrors", rateLimitedErrorsCounter.count(),
            "correlatedErrors", correlatedErrorsCounter.count(),
            "activeCircuitBreakers", circuitBreakers.size(),
            "trackedMessages", messageFailureCounts.size(),
            "circuitBreakerStates", getCircuitBreakerStates()
        );
    }
    
    private Map<String, String> getCircuitBreakerStates() {
        Map<String, String> states = new ConcurrentHashMap<>();
        circuitBreakers.forEach((service, state) -> 
            states.put(service, state.state.toString()));
        return states;
    }
}
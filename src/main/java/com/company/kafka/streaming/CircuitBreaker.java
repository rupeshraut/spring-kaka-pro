package com.company.kafka.streaming;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker implementation for protecting the streaming processor
 * from cascading failures and providing automatic recovery.
 * 
 * States:
 * - CLOSED: Normal operation, failures are counted
 * - OPEN: Circuit is open, all requests are rejected
 * - HALF_OPEN: Testing if the service has recovered
 */
@Slf4j
public class CircuitBreaker {

    private final StreamingConfiguration.CircuitBreakerConfig config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong stateChangeTime = new AtomicLong(System.currentTimeMillis());

    public CircuitBreaker(StreamingConfiguration.CircuitBreakerConfig config) {
        this.config = config;
    }

    /**
     * Check if processing can proceed.
     */
    public boolean canProcess() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                return shouldAttemptReset();
                
            case HALF_OPEN:
                return true;
                
            default:
                return false;
        }
    }

    /**
     * Record a successful operation.
     */
    public void recordSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            
            if (successes >= config.getSuccessThreshold()) {
                reset();
                log.info("Circuit breaker reset to CLOSED after {} successful operations", successes);
            }
        } else if (currentState == State.CLOSED) {
            // Reset failure count on successful operation
            failureCount.set(0);
        }
    }

    /**
     * Record a failed operation.
     */
    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        
        State currentState = state.get();
        
        if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            
            if (failures >= config.getFailureThreshold()) {
                openCircuit();
                log.warn("Circuit breaker opened after {} failures", failures);
            }
            
        } else if (currentState == State.HALF_OPEN) {
            openCircuit();
            log.warn("Circuit breaker reopened due to failure in HALF_OPEN state");
        }
    }

    /**
     * Get current circuit breaker state.
     */
    public String getState() {
        return state.get().name();
    }

    /**
     * Check if circuit breaker is open.
     */
    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    /**
     * Get current statistics.
     */
    public CircuitBreakerStats getStats() {
        return new CircuitBreakerStats(
            state.get().name(),
            failureCount.get(),
            successCount.get(),
            lastFailureTime.get(),
            stateChangeTime.get()
        );
    }

    /**
     * Manually reset the circuit breaker (for testing/admin purposes).
     */
    public void forceReset() {
        reset();
        log.info("Circuit breaker manually reset");
    }

    private boolean shouldAttemptReset() {
        long timeSinceStateChange = System.currentTimeMillis() - stateChangeTime.get();
        
        if (timeSinceStateChange >= config.getResetTimeout().toMillis()) {
            if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                stateChangeTime.set(System.currentTimeMillis());
                successCount.set(0);
                log.info("Circuit breaker transitioned to HALF_OPEN for testing");
                return true;
            }
        }
        
        return false;
    }

    private void openCircuit() {
        if (state.compareAndSet(State.CLOSED, State.OPEN) || 
            state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            stateChangeTime.set(System.currentTimeMillis());
        }
    }

    private void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        stateChangeTime.set(System.currentTimeMillis());
    }

    /**
     * Circuit breaker states.
     */
    private enum State {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, rejecting requests
        HALF_OPEN  // Testing if service has recovered
    }

    /**
     * Circuit breaker statistics.
     */
    public static class CircuitBreakerStats {
        private final String state;
        private final int failureCount;
        private final int successCount;
        private final long lastFailureTime;
        private final long stateChangeTime;

        public CircuitBreakerStats(String state, int failureCount, int successCount, 
                long lastFailureTime, long stateChangeTime) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.lastFailureTime = lastFailureTime;
            this.stateChangeTime = stateChangeTime;
        }

        public String getState() { return state; }
        public int getFailureCount() { return failureCount; }
        public int getSuccessCount() { return successCount; }
        public long getLastFailureTime() { return lastFailureTime; }
        public long getStateChangeTime() { return stateChangeTime; }
        
        public Instant getLastFailureInstant() { 
            return lastFailureTime > 0 ? Instant.ofEpochMilli(lastFailureTime) : null; 
        }
        
        public Instant getStateChangeInstant() { 
            return Instant.ofEpochMilli(stateChangeTime); 
        }
    }
}
package com.company.kafka.service;

import com.company.kafka.exception.NonRecoverableException;
import com.company.kafka.exception.RecoverableException;
import com.company.kafka.model.OrderEvent;
import com.company.kafka.model.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Message Processing Service
 * 
 * Pro Tips:
 * 1. Keep processing logic idempotent
 * 2. Implement proper error handling with context
 * 3. Use correlation IDs for tracing
 * 4. Make operations atomic where possible
 * 5. Consider implementing saga patterns for distributed transactions
 * 
 * @author Development Team
 */
@Service
@Slf4j
public class MessageProcessor {

    private final Random random = new Random();

    /**
     * Process order events with business logic
     */
    public void processOrderEvent(OrderEvent orderEvent, String correlationId) {
        log.info("Processing order event: correlationId={}, orderId={}, status={}, amount={}", 
            correlationId, orderEvent.getOrderId(), orderEvent.getStatus(), orderEvent.getTotalAmount());
        
        try {
            // Simulate business logic processing
            simulateProcessing();
            
            // Example business logic based on order status
            switch (orderEvent.getStatus()) {
                case PENDING -> handleOrderCreated(orderEvent, correlationId);
                case CONFIRMED -> handleOrderConfirmed(orderEvent, correlationId);
                case PROCESSING -> handleOrderProcessing(orderEvent, correlationId);
                case SHIPPED -> handleOrderShipped(orderEvent, correlationId);
                case DELIVERED -> handleOrderDelivered(orderEvent, correlationId);
                case CANCELLED -> handleOrderCancelled(orderEvent, correlationId);
                case REFUNDED -> handleOrderRefunded(orderEvent, correlationId);
                default -> throw new NonRecoverableException(
                    "Unknown order status: " + orderEvent.getStatus() + " for correlationId: " + correlationId);
            }
            
            log.info("Order event processed successfully: correlationId={}, orderId={}", 
                correlationId, orderEvent.getOrderId());
                
        } catch (RecoverableException | NonRecoverableException e) {
            // Re-throw known exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing order event: correlationId={}, orderId={}", 
                correlationId, orderEvent.getOrderId(), e);
            throw new NonRecoverableException("Unexpected error processing order: " + e.getMessage(), e);
        }
    }

    /**
     * Process payment events with business logic
     */
    public void processPaymentEvent(PaymentEvent paymentEvent, String correlationId) {
        log.info("Processing payment event: correlationId={}, paymentId={}, orderId={}, status={}, amount={}", 
            correlationId, paymentEvent.getPaymentId(), paymentEvent.getOrderId(), 
            paymentEvent.getStatus(), paymentEvent.getAmount());
        
        try {
            // Simulate business logic processing
            simulateProcessing();
            
            // Example business logic based on payment status
            switch (paymentEvent.getStatus()) {
                case PENDING -> handlePaymentPending(paymentEvent, correlationId);
                case PROCESSING -> handlePaymentProcessing(paymentEvent, correlationId);
                case COMPLETED -> handlePaymentCompleted(paymentEvent, correlationId);
                case FAILED -> handlePaymentFailed(paymentEvent, correlationId);
                case CANCELLED -> handlePaymentCancelled(paymentEvent, correlationId);
                case REFUNDED -> handlePaymentRefunded(paymentEvent, correlationId);
                default -> throw new NonRecoverableException(
                    "Unknown payment status: " + paymentEvent.getStatus() + " for correlationId: " + correlationId);
            }
            
            log.info("Payment event processed successfully: correlationId={}, paymentId={}, orderId={}", 
                correlationId, paymentEvent.getPaymentId(), paymentEvent.getOrderId());
                
        } catch (RecoverableException | NonRecoverableException e) {
            // Re-throw known exceptions
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing payment event: correlationId={}, paymentId={}, orderId={}", 
                correlationId, paymentEvent.getPaymentId(), paymentEvent.getOrderId(), e);
            throw new NonRecoverableException("Unexpected error processing payment: " + e.getMessage(), e);
        }
    }

    // Order event handlers
    private void handleOrderCreated(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order created: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        
        // Example: Validate inventory, check customer credit, etc.
        if (orderEvent.getTotalAmount().doubleValue() > 10000) {
            throw new NonRecoverableException("Order amount exceeds limit: " + orderEvent.getTotalAmount());
        }
        
        // Simulate potential recoverable error (e.g., external service timeout)
        if (random.nextDouble() < 0.1) { // 10% chance
            throw new RecoverableException("External inventory service timeout for correlationId: " + correlationId);
        }
    }

    private void handleOrderConfirmed(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order confirmed: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        // Example: Reserve inventory, send confirmation email
    }

    private void handleOrderProcessing(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order processing: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        // Example: Update warehouse management system
    }

    private void handleOrderShipped(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order shipped: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        // Example: Send tracking information to customer
    }

    private void handleOrderDelivered(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order delivered: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        // Example: Update customer loyalty points
    }

    private void handleOrderCancelled(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order cancelled: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        // Example: Release reserved inventory, process refund
    }

    private void handleOrderRefunded(OrderEvent orderEvent, String correlationId) {
        log.debug("Handling order refunded: correlationId={}, orderId={}", correlationId, orderEvent.getOrderId());
        // Example: Update accounting system
    }

    // Payment event handlers
    private void handlePaymentPending(PaymentEvent paymentEvent, String correlationId) {
        log.debug("Handling payment pending: correlationId={}, paymentId={}", correlationId, paymentEvent.getPaymentId());
        // Example: Initialize payment processing
    }

    private void handlePaymentProcessing(PaymentEvent paymentEvent, String correlationId) {
        log.debug("Handling payment processing: correlationId={}, paymentId={}", correlationId, paymentEvent.getPaymentId());
        
        // Simulate potential recoverable error (e.g., payment gateway timeout)
        if (random.nextDouble() < 0.05) { // 5% chance
            throw new RecoverableException("Payment gateway timeout for correlationId: " + correlationId);
        }
    }

    private void handlePaymentCompleted(PaymentEvent paymentEvent, String correlationId) {
        log.debug("Handling payment completed: correlationId={}, paymentId={}", correlationId, paymentEvent.getPaymentId());
        // Example: Update order status, send receipt
    }

    private void handlePaymentFailed(PaymentEvent paymentEvent, String correlationId) {
        log.debug("Handling payment failed: correlationId={}, paymentId={}", correlationId, paymentEvent.getPaymentId());
        // Example: Cancel order, notify customer
    }

    private void handlePaymentCancelled(PaymentEvent paymentEvent, String correlationId) {
        log.debug("Handling payment cancelled: correlationId={}, paymentId={}", correlationId, paymentEvent.getPaymentId());
        // Example: Update order status
    }

    private void handlePaymentRefunded(PaymentEvent paymentEvent, String correlationId) {
        log.debug("Handling payment refunded: correlationId={}, paymentId={}", correlationId, paymentEvent.getPaymentId());
        // Example: Update accounting system, notify customer
    }

    /**
     * Simulate processing time (remove in production)
     */
    private void simulateProcessing() {
        try {
            // Simulate processing time between 10-100ms
            Thread.sleep(10 + random.nextInt(90));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RecoverableException("Processing interrupted", e);
        }
    }
}

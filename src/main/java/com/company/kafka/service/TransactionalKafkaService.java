package com.company.kafka.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional Kafka service providing exactly-once semantics across multiple topics.
 * 
 * Features:
 * - Atomic operations across multiple Kafka topics
 * - Automatic rollback on any send failure
 * - Exactly-once delivery guarantees
 * - Transaction timeout handling
 * - Correlation ID tracking across transactions
 * 
 * Use Cases:
 * - Order processing with payment and notification
 * - Multi-step business workflows
 * - Saga pattern implementation
 * - Event sourcing with consistency
 * 
 * Pro Tips:
 * 1. Keep transactions short to avoid timeouts
 * 2. Handle transaction exceptions appropriately
 * 3. Monitor transaction metrics for performance
 * 4. Use correlation IDs for transaction tracing
 * 5. Avoid long-running operations inside transactions
 */
@Service
@Slf4j
public class TransactionalKafkaService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransactionalKafkaService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Processes an order with payment and notification in a single transaction.
     * All messages are sent atomically - if any fails, all are rolled back.
     *
     * @param orderId the order ID
     * @param orderData the order event data
     * @param paymentData the payment event data
     * @param notificationData the notification data
     * @return correlation ID for transaction tracking
     */
    @Transactional("kafkaTransactionManager")
    public String processOrderTransaction(String orderId, Object orderData, 
                                        Object paymentData, Object notificationData) {
        String correlationId = "tx-" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("Starting order transaction - orderId: {}, correlationId: {}", orderId, correlationId);
        
        try {
            // Send order event
            kafkaTemplate.send("orders-topic", orderId, orderData).get();
            log.debug("Order event sent - correlationId: {}", correlationId);
            
            // Send payment event
            kafkaTemplate.send("payments-topic", orderId, paymentData).get();
            log.debug("Payment event sent - correlationId: {}", correlationId);
            
            // Send notification
            kafkaTemplate.send("notifications-topic", orderId, notificationData).get();
            log.debug("Notification sent - correlationId: {}", correlationId);
            
            log.info("Order transaction completed successfully - correlationId: {}", correlationId);
            return correlationId;
            
        } catch (Exception e) {
            log.error("Order transaction failed - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
            throw new RuntimeException("Transaction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Processes a payment refund with order update and notification.
     *
     * @param orderId the order ID
     * @param refundData the refund event data
     * @param orderUpdateData the order update data
     * @param notificationData the notification data
     * @return correlation ID for transaction tracking
     */
    @Transactional("kafkaTransactionManager")
    public String processRefundTransaction(String orderId, Object refundData,
                                         Object orderUpdateData, Object notificationData) {
        String correlationId = "refund-tx-" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("Starting refund transaction - orderId: {}, correlationId: {}", orderId, correlationId);
        
        try {
            // Send refund event
            kafkaTemplate.send("payments-topic", orderId, refundData).get();
            
            // Update order status
            kafkaTemplate.send("orders-topic", orderId, orderUpdateData).get();
            
            // Send refund notification
            kafkaTemplate.send("notifications-topic", orderId, notificationData).get();
            
            log.info("Refund transaction completed successfully - correlationId: {}", correlationId);
            return correlationId;
            
        } catch (Exception e) {
            log.error("Refund transaction failed - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
            throw new RuntimeException("Refund transaction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a custom transaction with multiple operations.
     *
     * @param operations the operations to execute
     * @return correlation ID for transaction tracking
     */
    @Transactional("kafkaTransactionManager")
    public String executeTransaction(TransactionOperation... operations) {
        String correlationId = "custom-tx-" + UUID.randomUUID().toString().substring(0, 8);
        
        log.info("Starting custom transaction - correlationId: {}, operations: {}", 
            correlationId, operations.length);
        
        try {
            for (TransactionOperation operation : operations) {
                kafkaTemplate.send(operation.getTopic(), operation.getKey(), operation.getValue()).get();
                log.debug("Operation executed - topic: {}, key: {}, correlationId: {}", 
                    operation.getTopic(), operation.getKey(), correlationId);
            }
            
            log.info("Custom transaction completed successfully - correlationId: {}", correlationId);
            return correlationId;
            
        } catch (Exception e) {
            log.error("Custom transaction failed - correlationId: {}, error: {}", 
                correlationId, e.getMessage(), e);
            throw new RuntimeException("Custom transaction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Represents a single operation within a transaction.
     */
    public static class TransactionOperation {
        private final String topic;
        private final String key;
        private final Object value;

        public TransactionOperation(String topic, String key, Object value) {
            this.topic = topic;
            this.key = key;
            this.value = value;
        }

        public String getTopic() { return topic; }
        public String getKey() { return key; }
        public Object getValue() { return value; }
        
        public static TransactionOperation of(String topic, String key, Object value) {
            return new TransactionOperation(topic, key, value);
        }
    }
}
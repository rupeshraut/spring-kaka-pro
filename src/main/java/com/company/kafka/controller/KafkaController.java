package com.company.kafka.controller;

import com.company.kafka.service.KafkaProducerService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Production-ready REST controller for Kafka operations.
 * 
 * This controller provides HTTP endpoints for:
 * - Sending messages to different topics
 * - Monitoring producer health and metrics
 * - Batch message operations
 * - Message validation and error handling
 * 
 * Pro Tips:
 * 1. Include proper validation for incoming requests
 * 2. Use async responses for better performance
 * 3. Add correlation IDs for distributed tracing
 * 4. Include comprehensive error handling
 * 5. Monitor endpoint performance with metrics
 * 6. Use proper HTTP status codes
 */
@RestController
@RequestMapping("/api/kafka")
@Slf4j
public class KafkaController {

    private final KafkaProducerService kafkaProducerService;

    public KafkaController(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    /**
     * Send an order event message.
     *
     * @param request the order request
     * @return response with message details
     */
    @PostMapping("/orders")
    @Timed(value = "kafka.controller.orders", description = "Time taken to send order messages")
    public ResponseEntity<Map<String, Object>> sendOrderEvent(@RequestBody OrderRequest request) {
        try {
            String correlationId = "order-" + UUID.randomUUID().toString().substring(0, 8);
            
            log.info("Received order event request - correlationId: {}, orderId: {}", 
                correlationId, request.getOrderId());

            // Validate request
            if (request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "status", "error",
                        "message", "Order ID is required",
                        "correlationId", correlationId
                    ));
            }

            // Create message payload
            String message = String.format(
                "{\"orderId\":\"%s\",\"customerId\":\"%s\",\"amount\":%.2f,\"status\":\"%s\",\"timestamp\":\"%s\"}",
                request.getOrderId(),
                request.getCustomerId(),
                request.getAmount(),
                request.getStatus(),
                java.time.Instant.now()
            );

            // Send message
            kafkaProducerService.sendOrderEvent(correlationId, message);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Order event sent successfully",
                "correlationId", correlationId,
                "orderId", request.getOrderId()
            ));

        } catch (Exception e) {
            log.error("Error sending order event", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to send order event: " + e.getMessage()
                ));
        }
    }

    /**
     * Send a payment event message.
     *
     * @param request the payment request
     * @return response with message details
     */
    @PostMapping("/payments")
    @Timed(value = "kafka.controller.payments", description = "Time taken to send payment messages")
    public ResponseEntity<Map<String, Object>> sendPaymentEvent(@RequestBody PaymentRequest request) {
        try {
            String correlationId = "payment-" + UUID.randomUUID().toString().substring(0, 8);
            
            log.info("Received payment event request - correlationId: {}, paymentId: {}", 
                correlationId, request.getPaymentId());

            // Validate request
            if (request.getPaymentId() == null || request.getPaymentId().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "status", "error",
                        "message", "Payment ID is required",
                        "correlationId", correlationId
                    ));
            }

            // Create message payload
            String message = String.format(
                "{\"paymentId\":\"%s\",\"orderId\":\"%s\",\"amount\":%.2f,\"method\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}",
                request.getPaymentId(),
                request.getOrderId(),
                request.getAmount(),
                request.getMethod(),
                request.getStatus(),
                java.time.Instant.now()
            );

            // Send message
            kafkaProducerService.sendPaymentEvent(correlationId, message);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Payment event sent successfully",
                "correlationId", correlationId,
                "paymentId", request.getPaymentId()
            ));

        } catch (Exception e) {
            log.error("Error sending payment event", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to send payment event: " + e.getMessage()
                ));
        }
    }

    /**
     * Send a notification event message.
     *
     * @param request the notification request
     * @return response with message details
     */
    @PostMapping("/notifications")
    @Timed(value = "kafka.controller.notifications", description = "Time taken to send notification messages")
    public ResponseEntity<Map<String, Object>> sendNotificationEvent(@RequestBody NotificationRequest request) {
        try {
            String correlationId = "notification-" + UUID.randomUUID().toString().substring(0, 8);
            
            log.info("Received notification event request - correlationId: {}, type: {}", 
                correlationId, request.getType());

            // Create message payload
            String message = String.format(
                "{\"type\":\"%s\",\"recipient\":\"%s\",\"subject\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                request.getType(),
                request.getRecipient(),
                request.getSubject(),
                request.getContent(),
                java.time.Instant.now()
            );

            // Send message
            kafkaProducerService.sendNotification(correlationId, message);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Notification event sent successfully",
                "correlationId", correlationId,
                "type", request.getType()
            ));

        } catch (Exception e) {
            log.error("Error sending notification event", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to send notification event: " + e.getMessage()
                ));
        }
    }

    /**
     * Get producer health and metrics information.
     *
     * @return producer health data
     */
    @GetMapping("/producer/health")
    public ResponseEntity<Map<String, Object>> getProducerHealth() {
        try {
            Map<String, Object> health = kafkaProducerService.getHealthInfo();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "producer", health
            ));
        } catch (Exception e) {
            log.error("Error getting producer health", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to get producer health: " + e.getMessage()
                ));
        }
    }

    // Request DTOs
    public static class OrderRequest {
        private String orderId;
        private String customerId;
        private Double amount;
        private String status;

        // Constructors
        public OrderRequest() {}

        // Getters and setters
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PaymentRequest {
        private String paymentId;
        private String orderId;
        private Double amount;
        private String method;
        private String status;

        // Constructors
        public PaymentRequest() {}

        // Getters and setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
        
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class NotificationRequest {
        private String type;
        private String recipient;
        private String subject;
        private String content;

        // Constructors
        public NotificationRequest() {}

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }
        
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}

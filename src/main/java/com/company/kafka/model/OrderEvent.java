package com.company.kafka.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Order event model for Kafka messaging.
 * 
 * Demonstrates:
 * - Immutable data model using Lombok
 * - JSON serialization configuration
 * - Bean validation annotations
 * - Builder pattern for easy construction
 * 
 * Pro Tips:
 * 1. Use @Jacksonized with @Builder for immutable objects
 * 2. Always validate critical business fields
 * 3. Use appropriate JSON formatting for dates and numbers
 * 4. Include correlation IDs for tracing
 */
@Data
@Builder
@Jacksonized
public class OrderEvent {

    @NotBlank(message = "Order ID cannot be blank")
    @JsonProperty("order_id")
    private final String orderId;

    @NotBlank(message = "Customer ID cannot be blank")
    @JsonProperty("customer_id")
    private final String customerId;

    @NotNull(message = "Order status cannot be null")
    @JsonProperty("status")
    private final OrderStatus status;

    @NotNull(message = "Order items cannot be null")
    @Size(min = 1, message = "Order must have at least one item")
    @JsonProperty("items")
    private final List<OrderItem> items;

    @NotNull(message = "Total amount cannot be null")
    @DecimalMin(value = "0.01", message = "Total amount must be positive")
    @JsonProperty("total_amount")
    private final BigDecimal totalAmount;

    @JsonProperty("shipping_address")
    private final Address shippingAddress;

    @JsonProperty("billing_address")
    private final Address billingAddress;

    @NotNull(message = "Order timestamp cannot be null")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @JsonProperty("order_timestamp")
    private final LocalDateTime orderTimestamp;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    /**
     * Order status enumeration
     */
    public enum OrderStatus {
        PENDING, 
        CONFIRMED, 
        PROCESSING, 
        SHIPPED, 
        DELIVERED, 
        CANCELLED, 
        REFUNDED
    }

    /**
     * Order item nested class
     */
    @Data
    @Builder
    @Jacksonized
    public static class OrderItem {
        @NotBlank(message = "Product ID cannot be blank")
        @JsonProperty("product_id")
        private final String productId;

        @NotBlank(message = "Product name cannot be blank")
        @JsonProperty("product_name")
        private final String productName;

        @NotNull(message = "Quantity cannot be null")
        @DecimalMin(value = "1", message = "Quantity must be at least 1")
        @JsonProperty("quantity")
        private final Integer quantity;

        @NotNull(message = "Unit price cannot be null")
        @DecimalMin(value = "0.01", message = "Unit price must be positive")
        @JsonProperty("unit_price")
        private final BigDecimal unitPrice;

        @NotNull(message = "Total item price cannot be null")
        @DecimalMin(value = "0.01", message = "Total item price must be positive")
        @JsonProperty("total_price")
        private final BigDecimal totalPrice;
    }

    /**
     * Address nested class
     */
    @Data
    @Builder
    @Jacksonized
    public static class Address {
        @JsonProperty("street")
        private final String street;

        @JsonProperty("city")
        private final String city;

        @JsonProperty("state")
        private final String state;

        @JsonProperty("postal_code")
        private final String postalCode;

        @JsonProperty("country")
        private final String country;
    }

    /**
     * Create a sample order event for testing
     */
    public static OrderEvent createSample() {
        return OrderEvent.builder()
                .orderId("ORD-12345")
                .customerId("CUST-67890")
                .status(OrderStatus.PENDING)
                .items(List.of(
                        OrderItem.builder()
                                .productId("PROD-001")
                                .productName("Sample Product")
                                .quantity(2)
                                .unitPrice(new BigDecimal("29.99"))
                                .totalPrice(new BigDecimal("59.98"))
                                .build()
                ))
                .totalAmount(new BigDecimal("59.98"))
                .orderTimestamp(LocalDateTime.now())
                .metadata(Map.of("source", "web"))
                .build();
    }

    /**
     * Create a cancelled order event for testing error scenarios
     */
    public static OrderEvent createCancelled() {
        return OrderEvent.builder()
                .orderId("ORD-CANCEL-001")
                .customerId("CUST-TEST")
                .status(OrderStatus.CANCELLED)
                .items(List.of(
                        OrderItem.builder()
                                .productId("PROD-002")
                                .productName("Cancelled Product")
                                .quantity(1)
                                .unitPrice(new BigDecimal("19.99"))
                                .totalPrice(new BigDecimal("19.99"))
                                .build()
                ))
                .totalAmount(new BigDecimal("19.99"))
                .orderTimestamp(LocalDateTime.now())
                .metadata(Map.of("source", "api", "reason", "customer_request"))
                .build();
    }
}

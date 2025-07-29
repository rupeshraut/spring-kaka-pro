package com.company.kafka.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payment event model for Kafka messaging.
 * 
 * Demonstrates:
 * - Immutable data model using Lombok
 * - JSON serialization configuration
 * - Bean validation annotations
 * - Builder pattern for easy construction
 * 
 * Pro Tips:
 * 1. Include correlation IDs to link related events
 * 2. Always validate monetary amounts
 * 3. Use appropriate JSON formatting for dates
 * 4. Include payment method details for auditing
 */
@Data
@Builder
@Jacksonized
public class PaymentEvent {

    @NotBlank(message = "Payment ID cannot be blank")
    @JsonProperty("payment_id")
    private final String paymentId;

    @NotBlank(message = "Order ID cannot be blank")
    @JsonProperty("order_id")
    private final String orderId;

    @NotBlank(message = "Customer ID cannot be blank")
    @JsonProperty("customer_id")
    private final String customerId;

    @NotNull(message = "Payment status cannot be null")
    @JsonProperty("status")
    private final PaymentStatus status;

    @NotNull(message = "Payment amount cannot be null")
    @DecimalMin(value = "0.01", message = "Payment amount must be positive")
    @JsonProperty("amount")
    private final BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    @JsonProperty("currency")
    private final String currency;

    @NotNull(message = "Payment method cannot be null")
    @JsonProperty("payment_method")
    private final PaymentMethod paymentMethod;

    @JsonProperty("transaction_id")
    private final String transactionId;

    @JsonProperty("gateway_response")
    private final String gatewayResponse;

    @NotNull(message = "Payment timestamp cannot be null")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @JsonProperty("payment_timestamp")
    private final LocalDateTime paymentTimestamp;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    /**
     * Payment status enumeration
     */
    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED,
        PARTIALLY_REFUNDED
    }

    /**
     * Payment method nested class
     */
    @Data
    @Builder
    @Jacksonized
    public static class PaymentMethod {
        @NotNull(message = "Payment type cannot be null")
        @JsonProperty("type")
        private final PaymentType type;

        @JsonProperty("card_last_four")
        private final String cardLastFour;

        @JsonProperty("card_brand")
        private final String cardBrand;

        @JsonProperty("bank_name")
        private final String bankName;

        @JsonProperty("wallet_provider")
        private final String walletProvider;

        /**
         * Payment type enumeration
         */
        public enum PaymentType {
            CREDIT_CARD,
            DEBIT_CARD,
            BANK_TRANSFER,
            DIGITAL_WALLET,
            CRYPTOCURRENCY,
            BUY_NOW_PAY_LATER
        }
    }

    /**
     * Create a sample successful payment event for testing
     */
    public static PaymentEvent createSuccessful() {
        return PaymentEvent.builder()
                .paymentId("PAY-12345")
                .orderId("ORD-12345")
                .customerId("CUST-67890")
                .status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("59.98"))
                .currency("USD")
                .paymentMethod(PaymentMethod.builder()
                        .type(PaymentMethod.PaymentType.CREDIT_CARD)
                        .cardLastFour("1234")
                        .cardBrand("VISA")
                        .build())
                .transactionId("TXN-987654321")
                .gatewayResponse("SUCCESS")
                .paymentTimestamp(LocalDateTime.now())
                .metadata(Map.of("gateway", "stripe", "processor", "visa"))
                .build();
    }

    /**
     * Create a failed payment event for testing error scenarios
     */
    public static PaymentEvent createFailed() {
        return PaymentEvent.builder()
                .paymentId("PAY-FAIL-001")
                .orderId("ORD-FAIL-001")
                .customerId("CUST-FAIL")
                .status(PaymentStatus.FAILED)
                .amount(new BigDecimal("199.99"))
                .currency("USD")
                .paymentMethod(PaymentMethod.builder()
                        .type(PaymentMethod.PaymentType.CREDIT_CARD)
                        .cardLastFour("9999")
                        .cardBrand("MASTERCARD")
                        .build())
                .transactionId("TXN-FAIL-123")
                .gatewayResponse("INSUFFICIENT_FUNDS")
                .paymentTimestamp(LocalDateTime.now())
                .metadata(Map.of("gateway", "stripe", "error_code", "card_declined"))
                .build();
    }
}

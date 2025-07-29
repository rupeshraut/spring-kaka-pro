package com.company.kafka.service;

import com.company.kafka.config.KafkaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for KafkaConsumerService.
 * 
 * Tests:
 * - Order event consumption
 * - Payment event consumption
 * - Error handling scenarios
 * - Message validation
 * - Acknowledgment behavior
 * 
 * Pro Tips:
 * 1. Mock acknowledgment for unit testing
 * 2. Test validation edge cases
 * 3. Verify acknowledgment is called only on success
 * 4. Test exception propagation for retry logic
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private Acknowledgment acknowledgment;
    
    @Mock
    private KafkaProperties kafkaProperties;
    
    @Mock
    private MeterRegistry meterRegistry;

    private KafkaConsumerService consumerService;

    @BeforeEach
    void setUp() {
        consumerService = new KafkaConsumerService(kafkaProperties, meterRegistry);
    }

    @Test
    void consumeOrderEvent_WithValidMessage_ShouldProcessSuccessfully() {
        // Arrange
        String message = "valid-order-message";
        String topic = "orders-topic";
        int partition = 0;
        long offset = 123L;

        // Act
        consumerService.consumeOrderEvent(message, topic, partition, offset, acknowledgment);

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeOrderEvent_WithNullMessage_ShouldThrowException() {
        // Arrange
        String topic = "orders-topic";
        int partition = 0;
        long offset = 123L;

        // Act & Assert
        assertThatThrownBy(() -> 
            consumerService.consumeOrderEvent(null, topic, partition, offset, acknowledgment)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Order message cannot be null or blank");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumeOrderEvent_WithBlankMessage_ShouldThrowException() {
        // Arrange
        String message = "   ";
        String topic = "orders-topic";
        int partition = 0;
        long offset = 123L;

        // Act & Assert
        assertThatThrownBy(() -> 
            consumerService.consumeOrderEvent(message, topic, partition, offset, acknowledgment)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Order message cannot be null or blank");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumeOrderEvent_WithEmptyMessage_ShouldThrowException() {
        // Arrange
        String message = "";
        String topic = "orders-topic";
        int partition = 0;
        long offset = 123L;

        // Act & Assert
        assertThatThrownBy(() -> 
            consumerService.consumeOrderEvent(message, topic, partition, offset, acknowledgment)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Order message cannot be null or blank");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumePaymentEvent_WithValidMessage_ShouldProcessSuccessfully() {
        // Arrange
        String message = "valid-payment-message";
        String topic = "payments-topic";
        int partition = 0;
        long offset = 456L;

        // Act
        consumerService.consumePaymentEvent(message, topic, partition, offset, acknowledgment);

        // Assert
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumePaymentEvent_WithNullMessage_ShouldThrowException() {
        // Arrange
        String topic = "payments-topic";
        int partition = 0;
        long offset = 456L;

        // Act & Assert
        assertThatThrownBy(() -> 
            consumerService.consumePaymentEvent(null, topic, partition, offset, acknowledgment)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Payment message cannot be null or blank");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumePaymentEvent_WithBlankMessage_ShouldThrowException() {
        // Arrange
        String message = "   ";
        String topic = "payments-topic";
        int partition = 0;
        long offset = 456L;

        // Act & Assert
        assertThatThrownBy(() -> 
            consumerService.consumePaymentEvent(message, topic, partition, offset, acknowledgment)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Payment message cannot be null or blank");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void consumePaymentEvent_WithEmptyMessage_ShouldThrowException() {
        // Arrange
        String message = "";
        String topic = "payments-topic";
        int partition = 0;
        long offset = 456L;

        // Act & Assert
        assertThatThrownBy(() -> 
            consumerService.consumePaymentEvent(message, topic, partition, offset, acknowledgment)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessage("Payment message cannot be null or blank");

        verify(acknowledgment, never()).acknowledge();
    }
}

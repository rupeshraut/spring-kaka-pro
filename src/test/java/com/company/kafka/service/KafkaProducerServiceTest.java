package com.company.kafka.service;

import com.company.kafka.config.KafkaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KafkaProducerService.
 * 
 * Tests:
 * - Message sending with key and value
 * - Message sending with value only
 * - Success and failure scenarios
 * - Logging behavior
 * 
 * Pro Tips:
 * 1. Mock KafkaTemplate for unit testing
 * 2. Test both success and failure scenarios
 * 3. Verify proper callback handling
 * 4. Use CompletableFuture utilities for async testing
 */
@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Mock
    private KafkaProperties kafkaProperties;
    
    private MeterRegistry meterRegistry;

    private KafkaProducerService producerService;

    @BeforeEach
    void setUp() {
        // Use a real SimpleMeterRegistry instead of mock
        meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        
        // Setup mock kafkaProperties with nested structure
        KafkaProperties.TopicsProperties topics = org.mockito.Mockito.mock(KafkaProperties.TopicsProperties.class);
        KafkaProperties.TopicConfig ordersConfig = org.mockito.Mockito.mock(KafkaProperties.TopicConfig.class);
        KafkaProperties.TopicConfig paymentsConfig = org.mockito.Mockito.mock(KafkaProperties.TopicConfig.class);
        KafkaProperties.TopicConfig notificationsConfig = org.mockito.Mockito.mock(KafkaProperties.TopicConfig.class);
        
        org.mockito.Mockito.when(ordersConfig.name()).thenReturn("orders-topic");
        org.mockito.Mockito.when(paymentsConfig.name()).thenReturn("payments-topic");
        org.mockito.Mockito.when(notificationsConfig.name()).thenReturn("notifications-topic");
        
        org.mockito.Mockito.when(topics.orders()).thenReturn(ordersConfig);
        org.mockito.Mockito.when(topics.payments()).thenReturn(paymentsConfig);
        org.mockito.Mockito.when(topics.notifications()).thenReturn(notificationsConfig);
        
        org.mockito.Mockito.when(kafkaProperties.topics()).thenReturn(topics);
        
        producerService = new KafkaProducerService(kafkaTemplate, kafkaProperties, meterRegistry);
    }

    @Test
    void sendMessage_WithKeyAndValue_ShouldSendSuccessfully() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String message = "test-message";
        
        RecordMetadata metadata = org.mockito.Mockito.mock(RecordMetadata.class);
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, key, message);
        SendResult<String, Object> sendResult = new SendResult<>(producerRecord, metadata);
        
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(topic, key, message)).thenReturn(future);

        // Act
        CompletableFuture<SendResult<String, Object>> result = producerService.sendMessage(topic, key, message);

        // Assert
        assertThat(result).isCompleted();
        assertThat(result.join()).isEqualTo(sendResult);
        verify(kafkaTemplate).send(topic, key, message);
    }

    @Test
    void sendMessage_WithValueOnly_ShouldSendSuccessfully() {
        // Arrange
        String topic = "test-topic";
        String message = "test-message";
        
        RecordMetadata metadata = org.mockito.Mockito.mock(RecordMetadata.class);
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, null, message);
        SendResult<String, Object> sendResult = new SendResult<>(producerRecord, metadata);
        
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(topic, null, message)).thenReturn(future);

        // Act
        CompletableFuture<SendResult<String, Object>> result = producerService.sendMessage(topic, message);

        // Assert
        assertThat(result).isCompleted();
        assertThat(result.join()).isEqualTo(sendResult);
        verify(kafkaTemplate).send(topic, null, message);
    }

    @Test
    void sendMessage_WhenSendFails_ShouldReturnFailedFuture() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String message = "test-message";
        
        RuntimeException exception = new RuntimeException("Send failed");
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        
        when(kafkaTemplate.send(topic, key, message)).thenReturn(future);

        // Act
        CompletableFuture<SendResult<String, Object>> result = producerService.sendMessage(topic, key, message);

        // Assert
        assertThat(result).isCompletedExceptionally();
        verify(kafkaTemplate).send(topic, key, message);
    }

    @Test
    void sendMessage_ShouldCallKafkaTemplate() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        Object message = new Object();
        
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // Act
        producerService.sendMessage(topic, key, message);

        // Assert
        verify(kafkaTemplate).send(topic, key, message);
    }
}

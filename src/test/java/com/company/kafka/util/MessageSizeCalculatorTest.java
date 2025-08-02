package com.company.kafka.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MessageSizeCalculator
 * 
 * Tests various scenarios for message size calculation:
 * - Different message types and sizes
 * - Headers and metadata overhead
 * - Batch calculations
 * - Statistics tracking
 */
@ExtendWith(MockitoExtension.class)
class MessageSizeCalculatorTest {
    
    @Mock
    private ObjectMapper objectMapper;
    
    private MessageSizeCalculator messageSizeCalculator;
    
    @BeforeEach
    void setUp() {
        messageSizeCalculator = new MessageSizeCalculator(new ObjectMapper());
    }
    
    @Test
    void shouldCalculateStringMessageSize() {
        // Given
        String message = "Hello, Kafka!";
        
        // When
        long size = messageSizeCalculator.calculateValueSize(message);
        
        // Then
        assertThat(size).isEqualTo(message.getBytes(StandardCharsets.UTF_8).length);
    }
    
    @Test
    void shouldCalculateByteArraySize() {
        // Given
        byte[] message = "Hello, Kafka!".getBytes(StandardCharsets.UTF_8);
        
        // When
        long size = messageSizeCalculator.calculateValueSize(message);
        
        // Then
        assertThat(size).isEqualTo(message.length);
    }
    
    @Test
    void shouldCalculateComplexObjectSize() {
        // Given
        Map<String, Object> message = new HashMap<>();
        message.put("id", 123);
        message.put("name", "Test User");
        message.put("active", true);
        
        // When
        long size = messageSizeCalculator.calculateValueSize(message);
        
        // Then
        assertThat(size).isGreaterThan(0);
        // Size should be reasonable for a simple JSON object
        assertThat(size).isBetween(30L, 100L);
    }
    
    @Test
    void shouldCalculateNullValueSize() {
        // When
        long size = messageSizeCalculator.calculateValueSize(null);
        
        // Then
        assertThat(size).isZero();
    }
    
    @Test
    void shouldCalculateProducerRecordSizeWithKeyAndValue() {
        // Given
        String key = "test-key";
        String value = "test-value";
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", key, value);
        
        // When
        long totalSize = messageSizeCalculator.calculateProducerRecordSize(record);
        
        // Then
        long expectedSize = key.getBytes(StandardCharsets.UTF_8).length
                + value.getBytes(StandardCharsets.UTF_8).length
                + messageSizeCalculator.calculateKafkaOverhead();
        
        assertThat(totalSize).isEqualTo(expectedSize);
    }
    
    @Test
    void shouldCalculateProducerRecordSizeWithHeaders() {
        // Given
        String key = "test-key";
        String value = "test-value";
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", key, value);
        
        // Add headers
        record.headers().add("correlation-id", "12345".getBytes());
        record.headers().add("source", "test-service".getBytes());
        
        // When
        long totalSize = messageSizeCalculator.calculateProducerRecordSize(record);
        
        // Then
        long expectedHeadersSize = messageSizeCalculator.calculateHeadersSize(record.headers());
        assertThat(totalSize).isGreaterThan(key.length() + value.length());
        assertThat(expectedHeadersSize).isGreaterThan(0);
    }
    
    @Test
    void shouldCalculateConsumerRecordSize() {
        // Given
        String key = "test-key";
        String value = "test-value";
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "test-topic", 0, 100L, key, value
        );
        
        // When
        long totalSize = messageSizeCalculator.calculateConsumerRecordSize(record);
        
        // Then
        assertThat(totalSize).isGreaterThan(key.length() + value.length());
    }
    
    @Test
    void shouldCalculateHeadersSize() {
        // Given
        RecordHeaders headers = new RecordHeaders();
        headers.add("header1", "value1".getBytes());
        headers.add("header2", "value2".getBytes());
        
        // When
        long headersSize = messageSizeCalculator.calculateHeadersSize(headers);
        
        // Then
        // Each header has key, value, and 8 bytes overhead
        long expectedSize = "header1".length() + "value1".length() + 8
                + "header2".length() + "value2".length() + 8;
        
        assertThat(headersSize).isEqualTo(expectedSize);
    }
    
    @Test
    void shouldCalculateKafkaOverhead() {
        // When
        long overhead = messageSizeCalculator.calculateKafkaOverhead();
        
        // Then
        assertThat(overhead).isEqualTo(30L); // As documented in the implementation
    }
    
    @Test
    void shouldCalculateBatchSize() {
        // Given
        String message1 = "Hello";
        String message2 = "World";
        String message3 = "Kafka";
        
        // When
        long batchSize = messageSizeCalculator.calculateBatchSize(message1, message2, message3);
        
        // Then
        long expectedSize = messageSizeCalculator.calculateValueSize(message1)
                + messageSizeCalculator.calculateValueSize(message2)
                + messageSizeCalculator.calculateValueSize(message3)
                + (3 * messageSizeCalculator.calculateKafkaOverhead()) // Per message overhead
                + 50; // Batch overhead
        
        assertThat(batchSize).isEqualTo(expectedSize);
    }
    
    @Test
    void shouldUpdateStatistics() {
        // Given
        String message1 = "Small message";
        String message2 = "This is a larger message with more content";
        
        // When
        messageSizeCalculator.calculateValueSize(message1);
        messageSizeCalculator.calculateValueSize(message2);
        
        // Then
        MessageSizeCalculator.MessageSizeStats stats = messageSizeCalculator.getStatistics();
        assertThat(stats.messageCount()).isEqualTo(2);
        assertThat(stats.totalBytes()).isGreaterThan(0);
        assertThat(stats.averageSize()).isGreaterThan(0);
        assertThat(stats.maxSize()).isGreaterThanOrEqualTo(stats.minSize());
    }
    
    @Test
    void shouldResetStatistics() {
        // Given
        messageSizeCalculator.calculateValueSize("Test message");
        MessageSizeCalculator.MessageSizeStats initialStats = messageSizeCalculator.getStatistics();
        assertThat(initialStats.messageCount()).isEqualTo(1);
        
        // When
        messageSizeCalculator.resetStatistics();
        
        // Then
        MessageSizeCalculator.MessageSizeStats resetStats = messageSizeCalculator.getStatistics();
        assertThat(resetStats.messageCount()).isZero();
        assertThat(resetStats.totalBytes()).isZero();
        assertThat(resetStats.averageSize()).isZero();
    }
    
    @Test
    void shouldFormatSizeCorrectly() {
        // Test different size ranges
        assertThat(messageSizeCalculator.formatSize(500)).isEqualTo("500 B");
        assertThat(messageSizeCalculator.formatSize(1536)).isEqualTo("1.5 KB");
        assertThat(messageSizeCalculator.formatSize(2097152)).isEqualTo("2.0 MB");
        assertThat(messageSizeCalculator.formatSize(1073741824L)).isEqualTo("1.0 GB");
    }
    
    @Test
    void shouldHandleLargeMessages() {
        // Given - Create a large message (1MB)
        StringBuilder largeMessage = new StringBuilder();
        for (int i = 0; i < 1024 * 1024; i++) {
            largeMessage.append("A");
        }
        
        // When
        long size = messageSizeCalculator.calculateValueSize(largeMessage.toString());
        
        // Then
        assertThat(size).isGreaterThanOrEqualTo(1024 * 1024); // Should be at least 1MB
        assertThat(messageSizeCalculator.formatSize(size)).contains("MB");
    }
    
    @Test
    void shouldProvideFormattedStats() {
        // Given
        messageSizeCalculator.calculateValueSize("Message 1");
        messageSizeCalculator.calculateValueSize("This is a longer message for testing");
        
        // When
        MessageSizeCalculator.MessageSizeStats stats = messageSizeCalculator.getStatistics();
        String formatted = stats.getFormattedStats();
        
        // Then
        assertThat(formatted).contains("Messages: 2");
        assertThat(formatted).contains("Total:");
        assertThat(formatted).contains("Avg:");
        assertThat(formatted).contains("Max:");
        assertThat(formatted).contains("Min:");
    }
    
    @Test
    void shouldCalculateSizeForEmptyMessage() {
        // Given
        String emptyMessage = "";
        
        // When
        long size = messageSizeCalculator.calculateValueSize(emptyMessage);
        
        // Then
        assertThat(size).isZero();
    }
    
    @Test
    void shouldCalculateSizeForRecordWithNullKey() {
        // Given
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", null, "test-value");
        
        // When
        long size = messageSizeCalculator.calculateProducerRecordSize(record);
        
        // Then
        long expectedSize = "test-value".getBytes(StandardCharsets.UTF_8).length
                + messageSizeCalculator.calculateKafkaOverhead();
        
        assertThat(size).isEqualTo(expectedSize);
    }
    
    @Test
    void shouldCalculateSizeForRecordWithNullValue() {
        // Given
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", "test-key", null);
        
        // When
        long size = messageSizeCalculator.calculateProducerRecordSize(record);
        
        // Then
        long expectedSize = "test-key".getBytes(StandardCharsets.UTF_8).length
                + messageSizeCalculator.calculateKafkaOverhead();
        
        assertThat(size).isEqualTo(expectedSize);
    }
}

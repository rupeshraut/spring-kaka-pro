package com.company.kafka.config;

import com.company.kafka.exception.NonRecoverableException;
import com.company.kafka.exception.RecoverableException;
import com.company.kafka.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

import java.util.function.BiFunction;

/**
 * Kafka Error Handling Configuration
 * 
 * Pro Tips:
 * 1. Distinguish between recoverable and non-recoverable errors
 * 2. Use dead letter topics for poison messages
 * 3. Implement exponential backoff for retries
 * 4. Log errors with sufficient context for debugging
 * 5. Monitor error rates and dead letter topics
 * 6. Consider circuit breaker patterns for external dependencies
 * 
 * Error Handling Strategy:
 * - Recoverable errors: Retry with exponential backoff
 * - Non-recoverable errors: Send to dead letter topic immediately
 * - Validation errors: Send to validation-specific dead letter topic
 * - Deserialization errors: Send to deserialization dead letter topic
 * 
 * @author Development Team
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaErrorHandlingConfig {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // Create dead letter publishing recoverer with topic routing logic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate, 
            deadLetterTopicResolver()
        );
        
        // Configure recoverer to include error information in headers
        recoverer.setHeadersFunction((consumerRecord, exception) -> {
            RecordHeaders headers = new RecordHeaders();
            headers.add("dlt-exception-message", exception.getMessage().getBytes());
            headers.add("dlt-exception-class", exception.getClass().getName().getBytes());
            headers.add("dlt-original-topic", consumerRecord.topic().getBytes());
            headers.add("dlt-original-partition", String.valueOf(consumerRecord.partition()).getBytes());
            headers.add("dlt-original-offset", String.valueOf(consumerRecord.offset()).getBytes());
            headers.add("dlt-timestamp", String.valueOf(System.currentTimeMillis()).getBytes());
            return headers;
        });
        
        // Configure retry policy with exponential backoff
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);  // Start with 1 second
        backOff.setMultiplier(2.0);         // Double each time
        backOff.setMaxInterval(10000L);     // Max 10 seconds between retries
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // Configure which exceptions should NOT be retried (non-recoverable)
        errorHandler.addNotRetryableExceptions(
            ValidationException.class,
            NonRecoverableException.class,
            DeserializationException.class,
            MessageConversionException.class,
            IllegalArgumentException.class,
            NullPointerException.class
        );
        
        // Configure which exceptions SHOULD be retried (recoverable)
        errorHandler.addRetryableExceptions(
            RecoverableException.class,
            org.springframework.dao.TransientDataAccessException.class,
            org.springframework.web.client.ResourceAccessException.class,
            java.net.SocketTimeoutException.class,
            java.util.concurrent.TimeoutException.class
        );
        
        // Set commit recovery behavior
        errorHandler.setCommitRecovered(true);
        
        log.info("Kafka error handler configured with exponential backoff: initial=1s, max=10s, maxRetries=3");
        
        return errorHandler;
    }
    
    /**
     * Dead letter topic resolver that routes messages to appropriate DLT based on error type
     */
    private BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> deadLetterTopicResolver() {
        return (record, exception) -> {
            String originalTopic = record.topic();
            String dltTopicName;
            
            // Route to specific DLT based on exception type
            if (exception instanceof ValidationException) {
                dltTopicName = originalTopic + ".validation.dlt";
                log.warn("Validation error for record from topic {}, routing to {}: {}", 
                    originalTopic, dltTopicName, exception.getMessage());
            } else if (exception instanceof DeserializationException) {
                dltTopicName = originalTopic + ".deserialization.dlt";
                log.error("Deserialization error for record from topic {}, routing to {}: {}", 
                    originalTopic, dltTopicName, exception.getMessage());
            } else if (exception instanceof NonRecoverableException) {
                dltTopicName = originalTopic + ".business.dlt";
                log.error("Business logic error for record from topic {}, routing to {}: {}", 
                    originalTopic, dltTopicName, exception.getMessage());
            } else {
                dltTopicName = originalTopic + ".dlt";
                log.error("General error for record from topic {}, routing to {}: {}", 
                    originalTopic, dltTopicName, exception.getMessage());
            }
            
            // Use partition -1 to let Kafka determine the partition
            return new TopicPartition(dltTopicName, -1);
        };
    }
    
    /**
     * Alternative error handler for specific use cases with fixed backoff
     */
    @Bean("fixedBackoffErrorHandler")
    public CommonErrorHandler fixedBackoffErrorHandler() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        
        // Fixed backoff: 3 retries with 5 seconds between each retry
        FixedBackOff fixedBackOff = new FixedBackOff(5000L, 3L);
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, fixedBackOff);
        
        // Only retry specific exceptions
        errorHandler.addRetryableExceptions(
            RecoverableException.class,
            java.net.SocketTimeoutException.class
        );
        
        // Don't retry these exceptions at all
        errorHandler.addNotRetryableExceptions(
            ValidationException.class,
            DeserializationException.class
        );
        
        log.info("Fixed backoff error handler configured: 3 retries with 5s interval");
        
        return errorHandler;
    }
}

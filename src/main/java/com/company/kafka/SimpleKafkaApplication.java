package com.company.kafka;

import com.company.kafka.config.KafkaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Spring Boot application with production-ready Kafka integration.
 * 
 * Features:
 * - Comprehensive Kafka configuration
 * - Production-ready error handling
 * - Metrics and monitoring
 * - Security support
 * 
 * Pro Tips:
 * 1. Enable configuration properties for type-safe configuration
 * 2. Use @EnableKafka for Kafka listener support
 * 3. Configure proper profiles for different environments
 * 4. Include comprehensive monitoring and health checks
 */
@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class SimpleKafkaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleKafkaApplication.class, args);
    }
}

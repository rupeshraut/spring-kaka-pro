package com.company.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Kafka Production Application
 * 
 * This application demonstrates production-ready Kafka integration with:
 * - Comprehensive error handling and retry mechanisms
 * - Advanced monitoring and health checks
 * - Security configurations for production environments
 * - Performance optimizations and best practices
 * 
 * Pro Tips:
 * 1. Always enable idempotence for producers in production
 * 2. Use manual acknowledgment for consumers for better control
 * 3. Implement proper dead letter topic handling
 * 4. Monitor consumer lag and throughput metrics
 * 5. Configure appropriate timeouts and batch sizes
 * 
 * @author Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class KafkaApplication {

    public static void main(String[] args) {
        // Set system properties for optimal JVM performance
        System.setProperty("java.awt.headless", "true");
        System.setProperty("spring.backgroundpreinitializer.ignore", "true");
        
        SpringApplication application = new SpringApplication(KafkaApplication.class);
        
        // Configure additional profiles programmatically if needed
        // application.setAdditionalProfiles("monitoring", "security");
        
        application.run(args);
    }
}

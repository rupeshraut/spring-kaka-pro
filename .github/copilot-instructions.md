<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

# Spring Kafka Production Project Instructions

This project is a comprehensive Spring Boot application demonstrating production-ready Apache Kafka integration with advanced error handling, monitoring, and best practices.

## Project Context

### Technology Stack
- **Java 17**: Modern Java with latest features
- **Spring Boot 3.2.x**: Latest stable Spring Boot version
- **Spring Kafka**: Production-ready Kafka integration
- **Gradle**: Build tool with Kotlin DSL
- **TestContainers**: Integration testing with real Kafka instances
- **Micrometer**: Metrics and observability
- **Docker**: Containerization and local development

### Architecture Patterns
- **Configuration Properties**: Use `@ConfigurationProperties` with immutable records
- **Error Handling**: Comprehensive error classification and dead letter topics
- **Metrics & Monitoring**: Detailed metrics collection and health checks
- **Security First**: SSL/TLS and SASL authentication support
- **Testing Excellence**: Unit, integration, and performance tests

## Code Generation Guidelines

### Configuration Classes
- Use `@ConstructorBinding` with records for immutable configuration
- Validate properties with Bean Validation annotations
- Provide sensible defaults in constructors
- Group related properties into nested records
- Use `Duration` and `DataSize` for time and size properties

### Service Classes
- Implement comprehensive error handling with custom exceptions
- Add detailed logging with correlation IDs
- Include metrics collection using Micrometer
- Use async operations where appropriate
- Implement proper resource cleanup

### Exception Handling
- Distinguish between `RecoverableException` and `NonRecoverableException`
- Use `ValidationException` for data validation errors
- Include detailed context in exception messages
- Route exceptions to appropriate dead letter topics

### Kafka Components
- **Producers**: Enable idempotence, use appropriate batching and compression
- **Consumers**: Use manual acknowledgment, implement proper error handling
- **Topics**: Follow naming conventions with environment prefixes
- **Serialization**: Use JSON with proper type handling

### Testing Approach
- Use TestContainers for integration tests
- Mock external dependencies appropriately
- Test error scenarios thoroughly
- Include performance and load tests
- Validate configuration in tests

## Best Practices to Follow

### Production Readiness
1. Always enable idempotence for producers
2. Use manual acknowledgment for consumers
3. Implement exponential backoff for retries
4. Monitor consumer lag and throughput
5. Configure appropriate timeouts and batch sizes

### Code Quality
1. Use meaningful variable and method names
2. Add comprehensive JavaDoc for public APIs
3. Include Pro Tips comments for complex configurations
4. Follow SOLID principles
5. Implement proper separation of concerns

### Security
1. Never hardcode credentials or sensitive data
2. Use environment variables for configuration
3. Implement proper SSL/TLS configuration
4. Validate and sanitize all inputs
5. Use SASL authentication in production

### Monitoring & Observability
1. Add metrics for all critical operations
2. Implement comprehensive health checks
3. Include distributed tracing support
4. Log with appropriate levels and context
5. Expose metrics for Prometheus scraping

## Code Examples to Reference

### Configuration Pattern
```java
@ConfigurationProperties(prefix = "kafka")
@ConstructorBinding
public record KafkaProperties(
    @NotBlank String bootstrapServers,
    @Valid ProducerProperties producer
) {
    public KafkaProperties {
        bootstrapServers = bootstrapServers != null ? bootstrapServers : "localhost:9092";
        producer = producer != null ? producer : new ProducerProperties();
    }
}
```

### Service Pattern
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        // Implementation with metrics and error handling
    }
}
```

### Error Handling Pattern
```java
try {
    // Process message
    messageProcessor.process(message, correlationId);
    acknowledgment.acknowledge();
} catch (ValidationException e) {
    // Don't retry validation errors
    acknowledgment.acknowledge();
} catch (RecoverableException e) {
    // Let error handler retry
    throw e;
}
```

## When Suggesting Code

1. **Always include comprehensive error handling**
2. **Add metrics and logging where appropriate**
3. **Use the established patterns in the codebase**
4. **Include relevant Pro Tips as comments**
5. **Consider production implications**
6. **Suggest appropriate tests**
7. **Follow the established naming conventions**
8. **Include proper documentation**

## File Organization

- `config/`: Configuration classes and beans
- `service/`: Business logic and Kafka operations
- `model/`: Data transfer objects and events
- `exception/`: Custom exception classes
- `health/`: Health check implementations
- `metrics/`: Metrics collection and monitoring
- `test/`: Comprehensive test suite

Remember: This is a production-ready example, so prioritize reliability, observability, and maintainability in all code suggestions.

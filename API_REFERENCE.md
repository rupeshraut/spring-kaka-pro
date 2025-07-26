# Spring Kafka Pro - API Reference

## 📋 Table of Contents

- [Overview](#overview)
- [Authentication](#authentication)
- [Core Kafka APIs](#core-kafka-apis)
- [Dead Letter Topic Management](#dead-letter-topic-management)
- [Container Management](#container-management)
- [Monitoring & Health APIs](#monitoring--health-apis)
- [Error Handling APIs](#error-handling-apis)
- [Configuration APIs](#configuration-apis)
- [Response Format](#response-format)
- [Error Codes](#error-codes)
- [Rate Limiting](#rate-limiting)
- [Examples](#examples)

---

## Overview

The Spring Kafka Pro application provides comprehensive REST APIs for managing Kafka operations, monitoring system health, and handling dead letter topics. All APIs follow REST conventions and return JSON responses.

**Base URL**: `http://localhost:8080/api`

### Key Features
- Complete Kafka producer and consumer management
- Dead Letter Topic (DLT) operations and analytics
- Real-time monitoring and health checks
- Container lifecycle management
- Error handling and circuit breaker status
- Comprehensive metrics and observability

---

## Authentication

Currently, the APIs use basic security. In production, implement proper authentication:

```http
Authorization: Bearer <jwt-token>
# or
Authorization: Basic <base64-credentials>
```

---

## Core Kafka APIs

### Send Message

Send a message to a Kafka topic.

```http
POST /api/kafka/send
Content-Type: application/json
```

**Request Body:**
```json
{
  "topic": "orders-topic",
  "key": "ORDER-123",
  "message": {
    "orderId": "ORDER-123",
    "customerId": "CUSTOMER-456",
    "amount": 99.99,
    "timestamp": "2024-01-15T10:30:00Z"
  },
  "headers": {
    "correlation-id": "corr-123",
    "event-type": "ORDER_CREATED"
  }
}
```

**Response:**
```json
{
  "status": "success",
  "messageId": "msg-uuid-123",
  "topic": "orders-topic",
  "partition": 2,
  "offset": 12345,
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

### Batch Send Messages

Send multiple messages in a single request.

```http
POST /api/kafka/send/batch
Content-Type: application/json
```

**Request Body:**
```json
{
  "messages": [
    {
      "topic": "orders-topic",
      "key": "ORDER-123",
      "message": { "orderId": "ORDER-123", "amount": 99.99 }
    },
    {
      "topic": "payments-topic", 
      "key": "PAYMENT-456",
      "message": { "paymentId": "PAYMENT-456", "amount": 99.99 }
    }
  ]
}
```

**Response:**
```json
{
  "status": "success",
  "totalMessages": 2,
  "successful": 2,
  "failed": 0,
  "results": [
    {
      "messageId": "msg-1",
      "topic": "orders-topic",
      "partition": 1,
      "offset": 12346,
      "status": "success"
    },
    {
      "messageId": "msg-2", 
      "topic": "payments-topic",
      "partition": 0,
      "offset": 8901,
      "status": "success"
    }
  ]
}
```

### Get Topic Information

Retrieve information about Kafka topics.

```http
GET /api/kafka/topics
```

**Response:**
```json
{
  "status": "success",
  "topics": [
    {
      "name": "orders-topic",
      "partitions": 6,
      "replicationFactor": 3,
      "configEntries": {
        "retention.ms": "604800000",
        "compression.type": "snappy"
      }
    }
  ]
}
```

---

## Dead Letter Topic Management

### Get DLT Analytics

Comprehensive analytics for all dead letter topics.

```http
GET /api/kafka/dlt/analytics
```

**Response:**
```json
{
  "status": "success",
  "analytics": {
    "totalDltTopics": 12,
    "totalDltMessages": 347,
    "totalDltSizeBytes": 2847392,
    "topProblematicTopics": [
      {
        "topic": "orders-topic.validation.dlt",
        "messageCount": 156,
        "sizeBytes": 1204893,
        "lastActivity": "2024-01-15T10:25:00Z"
      },
      {
        "topic": "payments-topic.non-recoverable.dlt",
        "messageCount": 89,
        "sizeBytes": 756432,
        "lastActivity": "2024-01-15T09:15:00Z"
      }
    ],
    "topErrorTypes": [
      {
        "errorType": "ValidationException",
        "count": 234
      },
      {
        "errorType": "NonRecoverableException", 
        "count": 113
      }
    ],
    "healthIndicators": {
      "criticalTopics": 2,
      "averageMessagesPerTopic": 28.9,
      "oldestDltActivity": "2024-01-14T08:30:00Z",
      "newestDltActivity": "2024-01-15T10:25:00Z"
    },
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### Reprocess DLT Messages

Reprocess failed messages from a dead letter topic.

```http
POST /api/kafka/dlt/{dltTopic}/reprocess?maxMessages=100&filterType=validation
```

**Path Parameters:**
- `dltTopic`: Name of the dead letter topic

**Query Parameters:**
- `maxMessages` (optional): Maximum number of messages to reprocess (default: 100)
- `filterType` (optional): Filter type for message selection
  - `validation`: Only validation error messages
  - `recoverable`: Only recoverable error messages
  - `poison-pill`: Only poison pill messages
  - `circuit-breaker`: Only circuit breaker related messages
  - `recent`: Only messages from the last hour

**Response:**
```json
{
  "status": "success",
  "dltTopic": "orders-topic.validation.dlt",
  "maxMessages": 100,
  "processedCount": 87,
  "errorCount": 3,
  "summary": "Reprocessed 87 messages, skipped 10, errors 3"
}
```

### Browse DLT Messages

Browse messages in a dead letter topic for analysis.

```http
GET /api/kafka/dlt/{dltTopic}/browse?maxMessages=50&filterType=recent
```

**Response:**
```json
{
  "status": "success",
  "dltTopic": "orders-topic.validation.dlt",
  "messageCount": 25,
  "maxMessages": 50,
  "messages": [
    {
      "topic": "orders-topic.validation.dlt",
      "partition": 0,
      "offset": 123,
      "key": "ORDER-789",
      "value": {
        "orderId": "ORDER-789",
        "customerId": "",
        "amount": -10.0
      },
      "headers": {
        "dlt.exception-class": "ValidationException",
        "dlt.exception-message": "Customer ID is required",
        "dlt.original-topic": "orders-topic",
        "dlt.enhanced.version": "2.0",
        "correlation-id": "corr-789"
      },
      "timestamp": "2024-01-15T09:45:00Z"
    }
  ]
}
```

### Apply DLT Retention Policy

Apply retention policies to clean up old DLT messages.

```http
POST /api/kafka/dlt/retention/apply
```

**Response:**
```json
{
  "status": "success",
  "retentionResult": {
    "retentionDays": 7,
    "cutoffTime": "2024-01-08T10:30:00Z",
    "topicsProcessed": 8,
    "totalMessagesDeleted": 156,
    "detailedResults": {
      "orders-topic.validation.dlt": 45,
      "payments-topic.dlt": 67,
      "notifications-topic.non-recoverable.dlt": 44
    }
  }
}
```

### Get Problematic Topics

Get DLT topics with high error rates.

```http
GET /api/kafka/dlt/problematic-topics?threshold=100
```

**Response:**
```json
{
  "status": "success",
  "threshold": 100,
  "problematicTopics": [
    {
      "topic": "orders-topic.validation.dlt",
      "messageCount": 234,
      "sizeBytes": 1847392,
      "lastActivity": "2024-01-15T10:25:00Z"
    },
    {
      "topic": "payments-topic.non-recoverable.dlt",
      "messageCount": 156,
      "sizeBytes": 1204893,
      "lastActivity": "2024-01-15T09:15:00Z"
    }
  ],
  "count": 2
}
```

### Get Error Patterns

Analyze error patterns across all DLT topics.

```http
GET /api/kafka/dlt/error-patterns
```

**Response:**
```json
{
  "status": "success",
  "topErrorTypes": [
    {
      "errorType": "ValidationException",
      "count": 234
    },
    {
      "errorType": "NonRecoverableException",
      "count": 113
    },
    {
      "errorType": "RecoverableException",
      "count": 67
    }
  ],
  "enhancedErrorStats": {
    "poisonPillsDetected": 23,
    "circuitBreakerOpens": 8,
    "rateLimitedErrors": 156,
    "correlatedErrors": 45
  },
  "circuitBreakerStates": {
    "payment-service": "OPEN",
    "inventory-service": "CLOSED",
    "notification-service": "HALF_OPEN"
  }
}
```

---

## Container Management

### Start Container

Start a Kafka listener container.

```http
POST /api/kafka/management/containers/{listenerId}/start
```

**Response:**
```json
{
  "status": "success",
  "listenerId": "orderConsumerContainer",
  "action": "started",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Stop Container

Stop a Kafka listener container.

```http
POST /api/kafka/management/containers/{listenerId}/stop
```

### Pause Container

Pause a Kafka listener container.

```http
POST /api/kafka/management/containers/{listenerId}/pause
```

### Resume Container

Resume a paused Kafka listener container.

```http
POST /api/kafka/management/containers/{listenerId}/resume
```

### Get Container Status

Get the status of all or specific containers.

```http
GET /api/kafka/management/containers
GET /api/kafka/management/containers/{listenerId}
```

**Response:**
```json
{
  "status": "success",
  "containers": [
    {
      "listenerId": "orderConsumerContainer",
      "state": "RUNNING",
      "assignedPartitions": [
        { "topic": "orders-topic", "partition": 0 },
        { "topic": "orders-topic", "partition": 1 }
      ],
      "lastStartTime": "2024-01-15T08:00:00Z",
      "messagesProcessed": 12456,
      "errorCount": 23
    }
  ]
}
```

---

## Monitoring & Health APIs

### Application Health

Get overall application health status.

```http
GET /actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "cluster": {
          "clusterId": "kafka-cluster-1",
          "nodeCount": 3
        },
        "producer": {
          "connections": 2
        },
        "consumer": {
          "availableTopics": 15
        }
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 91909484544,
        "threshold": 10485760,
        "exists": true
      }
    }
  }
}
```

### Kafka-Specific Health

Get detailed Kafka health information.

```http
GET /actuator/health/kafka
```

### Consumer Lag Monitoring

Monitor consumer lag across all consumer groups.

```http
GET /api/kafka/monitoring/consumer-lag
```

**Response:**
```json
{
  "status": "success",
  "consumerGroups": [
    {
      "groupId": "spring-kafka-pro-group",
      "topics": [
        {
          "topic": "orders-topic",
          "partitions": [
            {
              "partition": 0,
              "currentOffset": 12345,
              "logEndOffset": 12350,
              "lag": 5,
              "status": "OK"
            },
            {
              "partition": 1,
              "currentOffset": 8901,
              "logEndOffset": 8920,
              "lag": 19,
              "status": "WARNING"
            }
          ],
          "totalLag": 24
        }
      ],
      "overallStatus": "WARNING"
    }
  ],
  "alerts": [
    {
      "severity": "WARNING",
      "message": "Consumer lag exceeds warning threshold",
      "details": {
        "group": "spring-kafka-pro-group",
        "topic": "orders-topic",
        "partition": 1,
        "lag": 19,
        "threshold": 10
      }
    }
  ]
}
```

### Metrics

Get application metrics in Prometheus format.

```http
GET /actuator/prometheus
```

Get specific metrics in JSON format.

```http
GET /actuator/metrics/kafka.producer.messages.sent
GET /actuator/metrics/kafka.consumer.messages.processed
GET /actuator/metrics/kafka.consumer.lag
```

**Response:**
```json
{
  "name": "kafka.producer.messages.sent",
  "description": "Number of messages sent",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 12456.0
    }
  ],
  "availableTags": [
    {
      "tag": "topic",
      "values": ["orders-topic", "payments-topic", "notifications-topic"]
    }
  ]
}
```

---

## Error Handling APIs

### Enhanced Error Handler Status

Get status and statistics of the enhanced error handler.

```http
GET /api/kafka/dlt/error-handler/status
```

**Response:**
```json
{
  "status": "success",
  "errorHandler": {
    "poisonPillsDetected": 23,
    "circuitBreakerOpens": 8,
    "rateLimitedErrors": 156,
    "correlatedErrors": 45,
    "activeCircuitBreakers": 3,
    "trackedMessages": 1247,
    "circuitBreakerStates": {
      "payment-service": "OPEN",
      "inventory-service": "CLOSED",
      "notification-service": "HALF_OPEN"
    }
  }
}
```

### Circuit Breaker Management

Get circuit breaker status for all services.

```http
GET /api/kafka/management/circuit-breakers
```

**Response:**
```json
{
  "status": "success",
  "circuitBreakers": [
    {
      "serviceName": "payment-service",
      "state": "OPEN",
      "failureCount": 12,
      "lastFailureTime": "2024-01-15T10:25:00Z",
      "lastSuccessTime": "2024-01-15T09:45:00Z",
      "nextRetryTime": "2024-01-15T10:26:00Z"
    },
    {
      "serviceName": "inventory-service",
      "state": "CLOSED", 
      "failureCount": 0,
      "lastSuccessTime": "2024-01-15T10:29:00Z"
    }
  ]
}
```

### Reset Circuit Breaker

Force reset a circuit breaker to closed state.

```http
POST /api/kafka/management/circuit-breakers/{serviceName}/reset
```

---

## Configuration APIs

### Get Current Configuration

Get the current Kafka configuration.

```http
GET /api/kafka/configuration
```

**Response:**
```json
{
  "status": "success",
  "configuration": {
    "bootstrapServers": "localhost:9092",
    "producer": {
      "acks": "all",
      "retries": 2147483647,
      "enableIdempotence": true,
      "batchSize": 32768,
      "lingerMs": 20,
      "compressionType": "snappy"
    },
    "consumer": {
      "groupId": "spring-kafka-pro-group",
      "autoOffsetReset": "earliest",
      "enableAutoCommit": false,
      "maxPollRecords": 500,
      "concurrency": 3
    },
    "topics": {
      "orders": {
        "name": "orders-topic",
        "partitions": 6,
        "replicationFactor": 3
      }
    },
    "security": {
      "enabled": false,
      "protocol": "PLAINTEXT"
    }
  }
}
```

### Update Configuration (Runtime)

Update certain runtime configuration parameters.

```http
PUT /api/kafka/configuration
Content-Type: application/json
```

**Request Body:**
```json
{
  "consumer": {
    "concurrency": 6,
    "maxPollRecords": 1000
  },
  "errorHandling": {
    "maxRetries": 5,
    "retryBackoffMs": 2000
  }
}
```

---

## Response Format

### Success Response
```json
{
  "status": "success",
  "data": { /* response data */ },
  "timestamp": "2024-01-15T10:30:00Z",
  "requestId": "req-uuid-123"
}
```

### Error Response
```json
{
  "status": "error",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid request parameters",
    "details": {
      "field": "topic",
      "issue": "Topic name cannot be empty"
    }
  },
  "timestamp": "2024-01-15T10:30:00Z",
  "requestId": "req-uuid-456"
}
```

---

## Error Codes

| Code | Description | HTTP Status |
|------|-------------|-------------|
| `VALIDATION_ERROR` | Request validation failed | 400 |
| `TOPIC_NOT_FOUND` | Specified topic does not exist | 404 |
| `PRODUCER_ERROR` | Message production failed | 500 |
| `CONSUMER_ERROR` | Message consumption failed | 500 |
| `DLT_PROCESSING_ERROR` | DLT operation failed | 500 |
| `CONTAINER_ERROR` | Container operation failed | 500 |
| `CIRCUIT_BREAKER_OPEN` | Circuit breaker is open | 503 |
| `RATE_LIMIT_EXCEEDED` | Rate limit exceeded | 429 |
| `AUTHENTICATION_ERROR` | Authentication failed | 401 |
| `AUTHORIZATION_ERROR` | Authorization failed | 403 |

---

## Rate Limiting

APIs are rate-limited to prevent abuse:

- **General APIs**: 1000 requests per minute per IP
- **DLT Operations**: 100 requests per minute per IP  
- **Bulk Operations**: 10 requests per minute per IP

Rate limit headers are included in responses:
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642248600
```

---

## Examples

### Complete Order Processing Flow

1. **Send Order Event**
```bash
curl -X POST http://localhost:8080/api/kafka/send \
  -H "Content-Type: application/json" \
  -d '{
    "topic": "orders-topic",
    "key": "ORDER-123",
    "message": {
      "orderId": "ORDER-123",
      "customerId": "CUSTOMER-456", 
      "amount": 99.99,
      "timestamp": "2024-01-15T10:30:00Z"
    }
  }'
```

2. **Monitor Processing**
```bash
curl http://localhost:8080/api/kafka/monitoring/consumer-lag
```

3. **Check for Errors**
```bash
curl http://localhost:8080/api/kafka/dlt/analytics
```

4. **Reprocess Failed Messages**
```bash
curl -X POST http://localhost:8080/api/kafka/dlt/orders-topic.validation.dlt/reprocess?maxMessages=10
```

### DLT Management Workflow

1. **Get DLT Analytics**
```bash
curl http://localhost:8080/api/kafka/dlt/analytics
```

2. **Browse Problematic Messages**
```bash
curl http://localhost:8080/api/kafka/dlt/orders-topic.validation.dlt/browse?maxMessages=5
```

3. **Reprocess Valid Messages**
```bash
curl -X POST http://localhost:8080/api/kafka/dlt/orders-topic.validation.dlt/reprocess?filterType=validation&maxMessages=50
```

4. **Apply Retention Policy**
```bash
curl -X POST http://localhost:8080/api/kafka/dlt/retention/apply
```

### Container Management

1. **Check Container Status**
```bash
curl http://localhost:8080/api/kafka/management/containers
```

2. **Pause High-Load Container**
```bash
curl -X POST http://localhost:8080/api/kafka/management/containers/orderConsumerContainer/pause
```

3. **Resume After Load Reduction**
```bash
curl -X POST http://localhost:8080/api/kafka/management/containers/orderConsumerContainer/resume
```

---

This API reference provides comprehensive documentation for all available endpoints in the Spring Kafka Pro application. Use these APIs to build robust operational dashboards and automation tools for your Kafka-based systems.
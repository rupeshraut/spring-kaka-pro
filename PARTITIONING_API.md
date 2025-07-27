# Partitioning Management API Reference

This document provides comprehensive API documentation for the Kafka partitioning management endpoints.

## Base URL

```
http://localhost:8080/api/kafka/partitioning
```

## Authentication

Currently, the APIs are open access. In production environments, implement appropriate authentication and authorization mechanisms.

## Endpoints Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/config` | Get current partitioning configuration |
| PUT | `/config` | Update partitioning configuration |
| GET | `/monitor` | Get partition monitoring statistics |
| GET | `/distribution` | Get partition distribution metrics |
| POST | `/optimize` | Optimize partition assignments |
| GET | `/recommendations` | Get optimization recommendations |
| GET | `/metrics` | Get detailed partitioning metrics |
| GET | `/health` | Get partitioning health status |
| POST | `/reset-metrics` | Reset monitoring metrics |

## API Documentation

### 1. Get Partitioning Configuration

#### `GET /config`

Retrieves the current partitioning configuration for both producer and consumer.

**Response Format:**
```json
{
  "status": "success",
  "producer": {
    "strategy": "CUSTOMER_AWARE",
    "sticky": {
      "partitionCount": 2,
      "enableThreadAffinity": true
    },
    "customer": {
      "hashSeed": 54321,
      "customerIdField": "customerId",
      "enableCaching": true
    },
    "priority": {
      "partitionRatio": 0.3,
      "priorityField": "priority",
      "highPriorityValues": ["HIGH", "CRITICAL", "URGENT"]
    }
  },
  "consumer": {
    "assignmentStrategy": "LOAD_BALANCED",
    "metadata": {
      "rack": "us-east-1a",
      "priority": "MEDIUM",
      "capacity": 2000,
      "region": "us-east"
    },
    "rebalancing": {
      "maxRebalanceTime": "PT5M",
      "enableIncrementalRebalancing": true
    }
  },
  "monitoring": {
    "enabled": true,
    "metricsInterval": "PT30S",
    "alerts": {
      "enabled": true,
      "skewThreshold": 2.0,
      "hotPartitionThreshold": 100
    }
  }
}
```

**Response Codes:**
- `200 OK` - Configuration retrieved successfully
- `500 Internal Server Error` - Failed to retrieve configuration

**Example:**
```bash
curl -X GET "http://localhost:8080/api/kafka/partitioning/config" \
  -H "Accept: application/json"
```

### 2. Update Partitioning Configuration

#### `PUT /config`

Updates partitioning configuration at runtime (where supported).

**Request Format:**
```json
{
  "producer": {
    "strategy": "PRIORITY",
    "priority": {
      "partitionRatio": 0.4
    }
  },
  "consumer": {
    "assignmentStrategy": "WORKLOAD_AWARE"
  }
}
```

**Response Format:**
```json
{
  "status": "success",
  "message": "Partitioning configuration updated successfully",
  "updatedItems": 2,
  "configUpdates": {
    "producer.strategy": "PRIORITY",
    "consumer.assignmentStrategy": "WORKLOAD_AWARE"
  }
}
```

**Response Codes:**
- `200 OK` - Configuration updated successfully
- `400 Bad Request` - Invalid configuration parameters
- `500 Internal Server Error` - Failed to update configuration

**Example:**
```bash
curl -X PUT "http://localhost:8080/api/kafka/partitioning/config" \
  -H "Content-Type: application/json" \
  -d '{
    "producer": {
      "strategy": "GEOGRAPHIC"
    }
  }'
```

### 3. Get Monitoring Statistics

#### `GET /monitor`

Retrieves current partition monitoring statistics and health metrics.

**Response Format:**
```json
{
  "status": "success",
  "monitoring": {
    "partitionSkew": 1.2,
    "consumerUtilization": 0.85,
    "assignmentEfficiency": 0.92,
    "activeConsumers": 6,
    "monitoredPartitions": 24,
    "totalRebalances": 3
  },
  "healthStatus": "HEALTHY",
  "timestamp": 1703123456789
}
```

**Health Status Values:**
- `HEALTHY` - All metrics within acceptable ranges
- `DEGRADED` - Some metrics below optimal levels
- `UNHEALTHY` - Critical metrics indicate problems

**Response Codes:**
- `200 OK` - Statistics retrieved successfully
- `500 Internal Server Error` - Failed to retrieve statistics

**Example:**
```bash
curl -X GET "http://localhost:8080/api/kafka/partitioning/monitor" \
  -H "Accept: application/json"
```

### 4. Get Partition Distribution

#### `GET /distribution`

Retrieves detailed partition distribution metrics across all active partitioners.

**Response Format:**
```json
{
  "status": "success",
  "partitioners": {
    "orders-topic": {
      "strategy": "CUSTOMER_AWARE",
      "partitionDistribution": {
        "orders-topic-0": 1250,
        "orders-topic-1": 1180,
        "orders-topic-2": 1320,
        "orders-topic-3": 1150
      },
      "totalMessages": 4900,
      "partitionCount": 4
    },
    "events-topic": {
      "strategy": "ROUND_ROBIN",
      "partitionDistribution": {
        "events-topic-0": 850,
        "events-topic-1": 832,
        "events-topic-2": 847,
        "events-topic-3": 838
      },
      "totalMessages": 3367,
      "partitionCount": 4
    }
  },
  "timestamp": 1703123456789
}
```

**Response Codes:**
- `200 OK` - Distribution metrics retrieved successfully
- `500 Internal Server Error` - Failed to retrieve metrics

**Example:**
```bash
curl -X GET "http://localhost:8080/api/kafka/partitioning/distribution" \
  -H "Accept: application/json"
```

### 5. Optimize Partitions

#### `POST /optimize`

Analyzes current partition performance and optionally applies optimizations.

**Query Parameters:**
- `dryRun` (boolean, default: false) - If true, only analyzes without applying changes

**Response Format:**
```json
{
  "status": "success",
  "dryRun": false,
  "currentStats": {
    "partitionSkew": 2.1,
    "consumerUtilization": 0.65,
    "assignmentEfficiency": 0.72
  },
  "optimizationPlan": {
    "partitionRebalancing": {
      "action": "REBALANCE_PARTITIONS",
      "reason": "High partition skew detected",
      "currentSkew": 2.1,
      "targetSkew": 1.5
    },
    "consumerOptimization": {
      "action": "OPTIMIZE_CONSUMER_ASSIGNMENT",
      "reason": "Low consumer utilization",
      "currentUtilization": 0.65,
      "targetUtilization": 0.8
    }
  },
  "timestamp": 1703123456789
}
```

**Response Codes:**
- `200 OK` - Optimization completed successfully
- `500 Internal Server Error` - Failed to perform optimization

**Examples:**
```bash
# Dry run analysis
curl -X POST "http://localhost:8080/api/kafka/partitioning/optimize?dryRun=true" \
  -H "Accept: application/json"

# Apply optimizations
curl -X POST "http://localhost:8080/api/kafka/partitioning/optimize" \
  -H "Accept: application/json"
```

### 6. Get Recommendations

#### `GET /recommendations`

Retrieves optimization recommendations based on current performance metrics.

**Response Format:**
```json
{
  "status": "success",
  "currentMetrics": {
    "partitionSkew": 2.3,
    "consumerUtilization": 0.58,
    "assignmentEfficiency": 0.67
  },
  "recommendations": [
    {
      "type": "PARTITION_DISTRIBUTION",
      "severity": "HIGH",
      "title": "High Partition Skew Detected",
      "description": "Partition distribution is uneven across consumers",
      "recommendation": "Consider using LOAD_BALANCED assignment strategy or rebalancing consumer group",
      "metrics": {
        "currentSkew": 2.3,
        "idealSkew": "< 1.5"
      }
    },
    {
      "type": "CONSUMER_UTILIZATION",
      "severity": "MEDIUM",
      "title": "Low Consumer Utilization",
      "description": "Consumers are not effectively utilizing available resources",
      "recommendation": "Optimize partition assignment or consider reducing consumer count",
      "metrics": {
        "currentUtilization": 0.58,
        "targetUtilization": "> 0.8"
      }
    }
  ],
  "totalRecommendations": 2,
  "timestamp": 1703123456789
}
```

**Recommendation Types:**
- `PARTITION_DISTRIBUTION` - Issues with partition distribution
- `CONSUMER_UTILIZATION` - Consumer resource utilization problems
- `ASSIGNMENT_EFFICIENCY` - Partition assignment optimization opportunities
- `REBALANCING_FREQUENCY` - Consumer group rebalancing issues

**Severity Levels:**
- `HIGH` - Critical issues requiring immediate attention
- `MEDIUM` - Performance improvements recommended
- `LOW` - Minor optimizations available

**Response Codes:**
- `200 OK` - Recommendations retrieved successfully
- `500 Internal Server Error` - Failed to generate recommendations

**Example:**
```bash
curl -X GET "http://localhost:8080/api/kafka/partitioning/recommendations" \
  -H "Accept: application/json"
```

### 7. Get Detailed Metrics

#### `GET /metrics`

Retrieves comprehensive partitioning metrics with status assessments.

**Response Format:**
```json
{
  "status": "success",
  "metrics": {
    "partitioning": {
      "skew": {
        "current": 1.8,
        "threshold": 2.0,
        "status": "OK"
      },
      "utilization": {
        "current": 0.82,
        "percentage": 82.0,
        "status": "GOOD"
      },
      "efficiency": {
        "current": 0.89,
        "percentage": 89.0,
        "status": "EXCELLENT"
      }
    },
    "consumers": {
      "active": 6,
      "totalRebalances": 4,
      "averagePartitionsPerConsumer": 4.0
    },
    "partitions": {
      "monitored": 24,
      "distribution": "Even distribution across consumers"
    }
  },
  "timestamp": 1703123456789
}
```

**Status Values:**
- **Skew:** `OK`, `WARNING`, `CRITICAL`
- **Utilization:** `POOR`, `NEEDS_IMPROVEMENT`, `GOOD`, `EXCELLENT`
- **Efficiency:** `POOR`, `NEEDS_IMPROVEMENT`, `GOOD`, `EXCELLENT`

**Response Codes:**
- `200 OK` - Metrics retrieved successfully
- `500 Internal Server Error` - Failed to retrieve metrics

**Example:**
```bash
curl -X GET "http://localhost:8080/api/kafka/partitioning/metrics" \
  -H "Accept: application/json"
```

### 8. Get Health Status

#### `GET /health`

Retrieves partitioning system health status with component-level details.

**Response Format:**
```json
{
  "status": "success",
  "health": {
    "overall": "HEALTHY",
    "components": {
      "partitionDistribution": "HEALTHY",
      "consumerUtilization": "HEALTHY",
      "assignmentEfficiency": "HEALTHY"
    },
    "metrics": {
      "partitionSkew": 1.4,
      "consumerUtilization": 0.85,
      "assignmentEfficiency": 0.91
    }
  },
  "timestamp": 1703123456789
}
```

**Health Values:**
- `HEALTHY` - Component functioning optimally
- `DEGRADED` - Component functioning but suboptimal
- `UNHEALTHY` - Component has critical issues

**Response Codes:**
- `200 OK` - Health status retrieved successfully
- `500 Internal Server Error` - Failed to retrieve health status

**Example:**
```bash
curl -X GET "http://localhost:8080/api/kafka/partitioning/health" \
  -H "Accept: application/json"
```

### 9. Reset Metrics

#### `POST /reset-metrics`

Resets all partition monitoring metrics and statistics.

**Response Format:**
```json
{
  "status": "success",
  "message": "Partition monitoring metrics reset successfully",
  "resetTimestamp": 1703123456789
}
```

**Response Codes:**
- `200 OK` - Metrics reset successfully
- `500 Internal Server Error` - Failed to reset metrics

**Example:**
```bash
curl -X POST "http://localhost:8080/api/kafka/partitioning/reset-metrics" \
  -H "Accept: application/json"
```

## Error Responses

All endpoints return consistent error response format:

```json
{
  "status": "error",
  "message": "Detailed error description",
  "timestamp": 1703123456789,
  "path": "/api/kafka/partitioning/config"
}
```

**Common Error Codes:**
- `400 Bad Request` - Invalid request parameters
- `404 Not Found` - Endpoint not found
- `500 Internal Server Error` - Server-side error
- `503 Service Unavailable` - Service temporarily unavailable

## Rate Limiting

Currently, no rate limiting is implemented. In production environments, consider implementing rate limiting based on:
- IP address
- API key
- User authentication

## Monitoring and Alerting

### Recommended Metrics to Monitor

1. **API Response Times**
   ```promql
   histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{uri=~"/api/kafka/partitioning/.*"}[5m]))
   ```

2. **API Error Rates**
   ```promql
   rate(http_requests_total{uri=~"/api/kafka/partitioning/.*", status=~"5.*"}[5m])
   ```

3. **Partition Skew Alerts**
   ```promql
   kafka_partition_skew > 2.0
   ```

4. **Consumer Utilization Alerts**
   ```promql
   kafka_consumer_utilization < 0.6
   ```

### Alerting Rules

Create alerts for:
- Partition skew > 2.0 (Warning) or > 3.0 (Critical)
- Consumer utilization < 0.6 (Warning) or < 0.4 (Critical)
- Assignment efficiency < 0.7 (Warning) or < 0.5 (Critical)
- API error rate > 5% (Warning) or > 10% (Critical)

## Usage Examples

### Complete Workflow Example

```bash
#!/bin/bash

BASE_URL="http://localhost:8080/api/kafka/partitioning"

echo "=== Getting Current Configuration ==="
curl -s "$BASE_URL/config" | jq .

echo -e "\n=== Checking Health Status ==="
curl -s "$BASE_URL/health" | jq .

echo -e "\n=== Getting Monitoring Statistics ==="
curl -s "$BASE_URL/monitor" | jq .

echo -e "\n=== Getting Recommendations ==="
curl -s "$BASE_URL/recommendations" | jq .

echo -e "\n=== Running Optimization (Dry Run) ==="
curl -s -X POST "$BASE_URL/optimize?dryRun=true" | jq .

echo -e "\n=== Getting Detailed Metrics ==="
curl -s "$BASE_URL/metrics" | jq .
```

### Automated Monitoring Script

```bash
#!/bin/bash

# Monitor partitioning health and send alerts
check_partitioning_health() {
  local health_response=$(curl -s "http://localhost:8080/api/kafka/partitioning/health")
  local overall_health=$(echo "$health_response" | jq -r '.health.overall')
  
  if [[ "$overall_health" != "HEALTHY" ]]; then
    echo "ALERT: Partitioning health is $overall_health"
    # Send alert to monitoring system
    curl -X POST "$ALERT_WEBHOOK_URL" -d "{\"message\": \"Kafka partitioning health: $overall_health\"}"
  fi
}

# Run health check
check_partitioning_health
```

## Best Practices

### API Usage
1. **Use appropriate HTTP methods** - GET for retrieval, POST for actions, PUT for updates
2. **Handle errors gracefully** - Always check response status and handle error cases
3. **Implement retry logic** - Use exponential backoff for retries on transient failures
4. **Cache responses** - Cache configuration and metrics responses appropriately

### Security
1. **Implement authentication** - Use API keys or OAuth for production environments
2. **Use HTTPS** - Always use HTTPS in production
3. **Validate inputs** - Validate all input parameters before processing
4. **Rate limiting** - Implement rate limiting to prevent abuse

### Performance
1. **Monitor API performance** - Track response times and error rates
2. **Use appropriate timeouts** - Set reasonable timeouts for API calls
3. **Batch operations** - Use batch operations where possible
4. **Cache frequently accessed data** - Cache configuration and metrics data

## Troubleshooting

### Common Issues

1. **Configuration Updates Not Applied**
   - Check application logs for configuration validation errors
   - Verify configuration format and values
   - Some configurations may require application restart

2. **Metrics Not Available**
   - Ensure monitoring is enabled in configuration
   - Check if partition monitor is running
   - Verify Kafka connectivity

3. **Health Status Shows UNHEALTHY**
   - Check partition skew metrics
   - Verify consumer utilization
   - Review assignment efficiency
   - Check for frequent rebalancing

4. **API Timeouts**
   - Check Kafka cluster connectivity
   - Monitor system resources
   - Review application logs for errors

For additional troubleshooting, check the application logs and Kafka cluster health.
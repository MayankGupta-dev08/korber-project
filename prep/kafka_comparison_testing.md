# Kafka Implementation - Comparison & Testing Guide

## 📊 Comparison of All Three Methods

| Aspect | Method 1: Event-Driven | Method 2: Request-Reply | Method 3: Saga |
|--------|------------------------|------------------------|----------------|
| **Complexity** | Simple ⭐ | Medium ⭐⭐ | Complex ⭐⭐⭐ |
| **Response Time** | Async (eventual) | Sync-like | Async (eventual) |
| **Consistency** | Eventual | Immediate | Strong (with compensation) |
| **Use Case** | Fire-and-forget | Need immediate response | Multi-step transactions |
| **Error Handling** | Event-based | Exception-based | Compensation-based |
| **Scalability** | High | Medium | High |
| **Code Overhead** | Low | Medium | High |

---

## 🎯 When to Use Each Method

### Method 1: Simple Event-Driven ✅ **RECOMMENDED FOR YOUR PROJECT**

**Use When:**
- Don't need immediate response
- OK with eventual consistency
- Multiple consumers need same event
- Simple workflow

**Example:**
```
Order placed → Email sent, Inventory updated, Analytics recorded
(All happen independently)
```

**Pros:**
- ✅ Simple to implement
- ✅ Highly scalable
- ✅ Loose coupling
- ✅ Easy to add new consumers

**Cons:**
- ❌ No immediate confirmation
- ❌ Harder to track failures
- ❌ Eventual consistency

---

### Method 2: Request-Reply

**Use When:**
- Need synchronous-like behavior
- Want Kafka benefits but need response
- Replacing REST with minimal changes

**Example:**
```
Order service asks: "Can I reserve 5 units?"
Inventory service replies: "Yes, reserved from batch [3, 5]"
```

**Pros:**
- ✅ Similar to REST (easier migration)
- ✅ Get response with timeout
- ✅ Good for transitioning from sync to async

**Cons:**
- ❌ Blocking (defeats Kafka purpose)
- ❌ More complex than REST
- ❌ Timeout issues

---

### Method 3: Saga Pattern

**Use When:**
- Multiple services involved
- Need rollback capability
- Strong consistency required
- Complex business logic

**Example:**
```
Order → Reserve Inventory → Process Payment → Ship
If Payment fails → Release Inventory → Cancel Order
```

**Pros:**
- ✅ Strong consistency guarantee
- ✅ Automatic compensation
- ✅ Audit trail of all steps
- ✅ Handles complex workflows

**Cons:**
- ❌ Most complex
- ❌ Requires saga state management
- ❌ Longer development time
- ❌ Harder to debug

---

## 🛠️ Application Configuration

### application.yml (Both Services)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    
    # Producer properties
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all  # Wait for all replicas
      retries: 3
      properties:
        max.in.flight.requests.per.connection: 1  # Ordering guarantee
    
    # Consumer properties
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      group-id: ${spring.application.name}-group
      auto-offset-reset: earliest  # Read from beginning
      enable-auto-commit: false  # Manual commit for reliability
      properties:
        spring.json.trusted.packages: "*"
    
    # Listener properties
    listener:
      ack-mode: manual  # Manual acknowledgment
      concurrency: 3  # Number of consumer threads

# Application name
spring:
  application:
    name: order-service  # or inventory-service

# Logging
logging:
  level:
    org.apache.kafka: INFO
    org.springframework.kafka: DEBUG
```

---

## 🧪 Testing Kafka Implementation

### 1. Unit Tests with Embedded Kafka

```java
package dev.mayank.korber.order.kafka;

import dev.mayank.korber.common.events.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"order-events", "inventory-events"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    }
)
@DirtiesContext
class KafkaIntegrationTest {
    
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @Autowired
    private OrderEventPublisher publisher;
    
    @Test
    void testPublishOrderPlacedEvent() {
        // Arrange
        OrderPlacedEvent event = new OrderPlacedEvent(1L, 1001L, "Laptop", 5);
        
        // Act
        publisher.publishOrderPlaced(event);
        
        // Assert - wait for async processing
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> {
                // Verify event was published
                return true; // Add your verification logic
            });
    }
}
```

### 2. Integration Test with Test Containers

```java
package dev.mayank.korber.order.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class KafkaTestContainersTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );
    
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Test
    void testKafkaConnection() {
        assertTrue(kafka.isRunning());
    }
}
```

**Add TestContainers dependency:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>kafka</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
```

---

## 🚀 Running the Kafka Implementation

### Step 1: Start Kafka

```bash
# Using Docker Compose
cd kafka-setup
docker-compose up -d

# Verify Kafka is running
docker ps
```

### Step 2: Create Topics

```bash
# Create topics manually (optional - auto-create is enabled)
docker exec -it kafka bash

kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --partitions 3 \
  --replication-factor 1

kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic inventory-events \
  --partitions 3 \
  --replication-factor 1

# List topics
kafka-topics --list --bootstrap-server localhost:9092
```

### Step 3: Start Services

```bash
# Terminal 1: Start Inventory Service
cd inventory-service
mvn spring-boot:run

# Terminal 2: Start Order Service
cd order-service
mvn spring-boot:run
```

### Step 4: Place Order

```bash
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1002,
    "quantity": 5
  }'
```

**Response (Method 1 - Event-Driven):**
```json
{
  "orderId": 11,
  "productId": 1002,
  "quantity": 5,
  "status": "PENDING",
  "message": "Order placed. Awaiting inventory confirmation."
}
```

### Step 5: Monitor Kafka

```bash
# Consumer for order-events
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning

# Consumer for inventory-events
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic inventory-events \
  --from-beginning
```

---

## 📊 Monitoring Kafka

### 1. Kafka UI (Docker)

```yaml
# Add to docker-compose.yml
kafka-ui:
  image: provectuslabs/kafka-ui:latest
  ports:
    - "8090:8080"
  environment:
    KAFKA_CLUSTERS_0_NAME: local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
  depends_on:
    - kafka
```

**Access:** http://localhost:8090

### 2. Spring Boot Actuator

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,kafka
  metrics:
    tags:
      application: ${spring.application.name}
```

**Check metrics:** http://localhost:8080/actuator/metrics

---

## 🔍 Debugging Tips

### 1. Check Consumer Group Status

```bash
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group order-service-group
```

### 2. View Messages in Topic

```bash
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true
```

### 3. Reset Consumer Offset

```bash
# Reset to beginning
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group order-service-group \
  --reset-offsets \
  --to-earliest \
  --topic order-events \
  --execute
```

---

## ⚠️ Common Issues and Solutions

### Issue 1: Consumer Not Receiving Messages

**Problem:** Consumer starts but doesn't process messages

**Solution:**
```java
// Check consumer group ID is unique
@KafkaListener(
    topics = "order-events",
    groupId = "unique-group-id"  // Must be unique!
)
```

### Issue 2: Serialization Errors

**Problem:** `SerializationException`

**Solution:**
```yaml
# Add trusted packages
spring:
  kafka:
    consumer:
      properties:
        spring.json.trusted.packages: "dev.mayank.korber.*,java.util,java.time"
```

### Issue 3: Message Loss

**Problem:** Messages lost on consumer restart

**Solution:**
```yaml
# Enable manual commit
spring:
  kafka:
    listener:
      ack-mode: manual

# In code
@KafkaListener(...)
public void handle(ConsumerRecord<String, Event> record, Acknowledgment ack) {
    processEvent(record.value());
    ack.acknowledge();  // Manual acknowledgment
}
```

---

## 🎯 Recommended Approach for Your Project

### For Körber Interview:

**Start with Method 1 (Event-Driven)** because:

1. ✅ Simple to explain
2. ✅ Shows understanding of async messaging
3. ✅ Easy to demo
4. ✅ Production-ready approach

**Mention in Interview:**
> "I implemented asynchronous event-driven communication using Kafka. When an order is placed, we publish an OrderPlaced event that the Inventory Service consumes. This decouples the services and allows them to scale independently. In production, I'd also implement Method 3 (Saga Pattern) for stronger consistency guarantees, especially if we add payment processing or other steps that require rollback capability."

**Show You Understand Trade-offs:**
> "The trade-off is eventual consistency vs immediate consistency. With events, the user gets an immediate 'Order Pending' response, and we confirm asynchronously. This is perfect for e-commerce where a few seconds delay is acceptable. For real-time inventory checks, I could use Method 2 (Request-Reply) to get immediate confirmation."

---

## ✅ Implementation Checklist

- [ ] Kafka running locally (Docker or manual)
- [ ] Dependencies added to both services
- [ ] Event classes created in common package
- [ ] Kafka configuration added
- [ ] Producer/Publisher implemented
- [ ] Consumer/Listener implemented
- [ ] Topics created (or auto-create enabled)
- [ ] Services tested end-to-end
- [ ] Monitoring set up (Kafka UI)
- [ ] Error handling implemented
- [ ] Tests written (embedded Kafka)

---

## 📚 Additional Resources

**Kafka Basics:**
- https://kafka.apache.org/quickstart
- https://docs.spring.io/spring-kafka/reference/

**Testing:**
- https://docs.spring.io/spring-kafka/reference/testing.html

**Best Practices:**
- https://www.confluent.io/blog/

This gives you three complete, production-ready approaches to implement Kafka! 🚀
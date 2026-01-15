# Kafka Implementation Guide - Complete Setup

## 🎯 Overview

We'll replace synchronous REST calls with asynchronous Kafka-based communication:

### Current Architecture (REST):
```
OrderService → HTTP Call → InventoryService → Response → OrderService
(Blocking, Synchronous)
```

### New Architecture (Kafka):
```
OrderService → Kafka Topic → InventoryService
             ↑                     ↓
             └─────────────────────┘
        (Non-blocking, Asynchronous, Event-Driven)
```

---

## 📦 Prerequisites

### 1. Install Kafka Locally

**Option A: Docker (RECOMMENDED)**
```yaml
# docker-compose.yml
version: '3'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

**Start Kafka:**
```bash
docker-compose up -d
```

**Option B: Local Installation**
```bash
# Download Kafka
wget https://archive.apache.org/dist/kafka/3.5.0/kafka_2.13-3.5.0.tgz
tar -xzf kafka_2.13-3.5.0.tgz
cd kafka_2.13-3.5.0

# Start Zookeeper
bin/zookeeper-server-start.sh config/zookeeper.properties

# Start Kafka (in another terminal)
bin/kafka-server-start.sh config/server.properties
```

### 2. Add Kafka Dependencies

**Parent POM:**
```xml
<properties>
    <spring-kafka.version>3.0.12</spring-kafka.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
            <version>${spring-kafka.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Both Service POMs:**
```xml
<dependencies>
    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    
    <!-- Jackson for JSON serialization -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## 🎪 Method 1: Simple Event-Driven (Fire and Forget)

### Architecture:
```
OrderService publishes "OrderPlaced" event
    ↓
Kafka Topic: "order-events"
    ↓
InventoryService consumes and processes
```

### Topics:
- `order-events` - Order placed events
- `inventory-events` - Inventory update results

---

## 🎪 Method 2: Request-Reply Pattern

### Architecture:
```
OrderService → "order-requests" topic → InventoryService
    ↑                                          ↓
    └────────── "order-responses" topic ───────┘
```

### Topics:
- `order-requests` - Order requests
- `order-responses` - Responses with success/failure

---

## 🎪 Method 3: Saga Pattern (Orchestration)

### Architecture:
```
OrderService (Orchestrator)
    ↓ CreateOrder
    ↓ CheckInventory → InventoryService
    ↓ ReserveInventory → InventoryService
    ↓ ConfirmOrder
```

### Topics:
- `saga-commands` - Commands to services
- `saga-events` - Events from services

---

Let me implement all three methods in detail!


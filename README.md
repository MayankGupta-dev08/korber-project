# E-Commerce Microservices System

## Overview
This project implements two Spring Boot microservices for managing inventory and orders in an e-commerce system.

## Project Structure
```
microservices-assignment/
├── inventory-service/
│   ├── src/main/java/dev/mayank/korber/inventory/
│   │   ├── InventoryServiceApplication.java
│   │   ├── config/
│   │   │   └── OpenApiConfig.java
│   │   ├── controller/
│   │   │   └── InventoryController.java
│   │   ├── service/
│   │   │   ├── InventoryService.java
│   │   │   └── factory/
│   │   │       ├── InventoryHandlerFactory.java
│   │   │       ├── InventoryHandler.java
│   │   │       └── DefaultInventoryHandler.java
│   │   ├── repository/
│   │   │   └── InventoryBatchRepository.java
│   │   ├── model/
│   │   │   └── InventoryBatch.java
│   │   └── dto/
│   │       ├── InventoryResponse.java
│   │       ├── BatchDTO.java
│   │       └── UpdateInventoryRequest.java
│   │       └── UpdateInventoryResponse.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── data/
│   │   │   └── inventory.csv
│   │   └── db/changelog/
│   │       ├── db.changelog-master.xml
│   │       ├── 01-create-inventory-table.xml
│   │       └── 02-load-inventory-data.xml
│   ├── src/test/java/dev/mayank/korber/inventory/
│   │   ├── InventoryServiceIntegrationTest.java
│   │   ├── service/
│   │       ├── DefaultInventoryHandlerTest.java
│   │       ├── InventoryServiceTest.java
│   └── pom.xml
├── order-service/
│   ├── src/main/java/dev/mayank/korber/order/
│   │   ├── OrderServiceApplication.java
│   │   ├── client/
│   │   │   └── InventoryClient.java
│   │   ├── config/
│   │   │   └── OpenApiConfig.java
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── service/
│   │   │   └── OrderService.java
│   │   ├── repository/
│   │   │   └── OrderRepository.java
│   │   ├── model/
│   │   │   └── Order.java
│   │   ├── dto/
│   │   │   ├── InventoryResponse.java
│   │   │   ├── InventoryUpdateRequest.java
│   │   │   ├── InventoryUpdateResponse.java
│   │   │   ├── OrderRequest.java
│   │   │   └── OrderResponse.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── data/
│   │   │   └── orders.csv
│   │   └── db/changelog/
│   │       ├── db.changelog-master.xml
│   │       ├── 01-create-order-table.xml
│   │       └── 02-load-order-data.xml
│   ├── src/test/java/dev/mayank/korber/order/
│   │   ├── OrderServiceIntegrationTest.java
│   │   ├── service/
│   │       ├── OrderServiceTest.java
│   └── pom.xml
└── README.md
```

## Prerequisites
- Java 17 or higher
- Maven 3.6+
- Git

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/MayankGupta-dev08/korber-project.git
cd korber-project
```

### 2. Build the Projects
```bash
# Build Inventory Service
cd inventory-service
mvn clean install
cd ..

# Build Order Service
cd order-service
mvn clean install
cd ..
```

### 3. Run the Services

#### Start Inventory Service (Port 8081)
```bash
cd inventory-service
mvn spring-boot:run
```

#### Start Order Service (Port 8080)
```bash
cd order-service
mvn spring-boot:run
```

## API Documentation

### Inventory Service (http://localhost:8081)

- swagger url: http://localhost:8081/swagger-ui.html

#### GET /inventory/{productId}
Returns inventory batches sorted by expiry date for a given product.

**Request:**
```bash
curl http://localhost:8081/inventory/1005
```

**Response:**
```json
{
  "productId": 1005,
  "productName": "Smartwatch",
  "batches": [
    {
      "batchId": 5,
      "quantity": 39,
      "expiryDate": "2026-03-31"
    },
    {
      "batchId": 7,
      "quantity": 40,
      "expiryDate": "2026-04-24"
    },
    {
      "batchId": 2,
      "quantity": 52,
      "expiryDate": "2026-05-30"
    }
  ]
}
```

#### POST /inventory/update
Updates inventory after an order is placed.

**Request:**
```bash
curl -X POST http://localhost:8081/inventory/update \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1005,
    "quantity": 10
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Inventory updated successfully",
  "reservedBatchIds": [5]
}
```

### Order Service (http://localhost:8080)

- swagger url: http://localhost:8080/swagger-ui.html

#### POST /order
Places an order and updates inventory.

**Request:**
```bash
curl -X POST http://localhost:8080/order \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1002,
    "quantity": 3
  }'
```

**Response:**
```json
{
  "orderId": 11,
  "productId": 1002,
  "productName": "Smartphone",
  "quantity": 3,
  "status": "PLACED",
  "reservedFromBatchIds": [9],
  "message": "Order placed. Inventory reserved."
}
```

## Testing Instructions

### Run Unit Tests
```bash
# Inventory Service
cd inventory-service
mvn test

# Order Service
cd order-service
mvn test
```

### Run Integration Tests
```bash
# Both services
mvn verify
```

## Database Access

### H2 Console Access

**Inventory Service:**
- URL: http://localhost:8081/h2-console
- JDBC URL: jdbc:h2:mem:inventorydb
- Username: sa
- Password: (leave empty)

**Order Service:**
- URL: http://localhost:8080/h2-console
- JDBC URL: jdbc:h2:mem:orderdb
- Username: sa
- Password: (leave empty)

## Design Patterns

### Factory Pattern Implementation
The Inventory Service uses the Factory Design Pattern for inventory handling logic:

- `InventoryHandlerFactory`: Creates appropriate handlers
- `InventoryHandler`: Interface for different handling strategies
- `DefaultInventoryHandler`: Default FIFO implementation (earliest expiry first)

This allows easy extension for different inventory management strategies (e.g., LIFO, priority-based, warehouse-specific).

## Technologies Used
- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database
- Liquibase
- Lombok
- JUnit 5
- Mockito
- RestTemplate
- Maven

## Architecture Highlights
- Microservices architecture with REST communication
- Layered architecture (Controller → Service → Repository)
- Factory pattern for extensibility
- Liquibase for database versioning
- Comprehensive unit and integration tests
- H2 in-memory database for development

## Future Enhancements
- Add Swagger/OpenAPI documentation
- Implement WebClient for reactive communication
- Add circuit breaker pattern (Resilience4j)
- Implement distributed tracing
- Add API Gateway
- Implement proper exception handling with custom error responses
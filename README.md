# E-Commerce Microservices System

## Overview
This project implements two Spring Boot microservices for managing inventory and orders in an e-commerce system.

## Project Structure
```
microservices-assignment/
├── inventory-service/
│   ├── src/main/java/dev/mayank/koerber/inventory/
│   │   ├── InventoryServiceApplication.java
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
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── data/
│   │   │   └── inventory.csv
│   │   └── db/changelog/
│   │       ├── db.changelog-master.xml
│   │       ├── 01-create-inventory-table.xml
│   │       └── 02-load-inventory-data.xml
│   └── pom.xml
├── order-service/
│   ├── src/main/java/dev/mayank/koerber/order/
│   │   ├── OrderServiceApplication.java
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── service/
│   │   │   └── OrderService.java
│   │   ├── repository/
│   │   │   └── OrderRepository.java
│   │   ├── model/
│   │   │   └── Order.java
│   │   ├── dto/
│   │   │   ├── OrderRequest.java
│   │   │   └── OrderResponse.java
│   │   └── client/
│   │       └── InventoryClient.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── data/
│   │   │   └── orders.csv
│   │   └── db/changelog/
│   │       ├── db.changelog-master.xml
│   │       ├── 01-create-order-table.xml
│   │       └── 02-load-order-data.xml
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
cd microservices-assignment
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
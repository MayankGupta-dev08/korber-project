# Complete Interview Preparation - Körber Project

## 🎯 PROJECT OVERVIEW

### Elevator Pitch (30 seconds):

> "I built a microservices-based e-commerce inventory management system with two Spring Boot services. The **Inventory Service** manages stock across multiple warehouses with FIFO expiry tracking, while the **Order Service** places orders and coordinates with inventory via REST APIs. I used Liquibase for database versioning, implemented the Factory pattern for extensibility, and achieved 85%+ test coverage with comprehensive unit and integration tests."

---

## 📐 SYSTEM ARCHITECTURE

### High-Level Architecture:

```
┌─────────────────┐         REST API        ┌─────────────────┐
│                 │ ◄──────────────────────► │                 │
│  Order Service  │                          │ Inventory       │
│    (Port 8080)  │    GET /inventory/:id    │    Service      │
│                 │    POST /inventory/update│  (Port 8081)    │
└────────┬────────┘                          └────────┬────────┘
         │                                            │
         │ JPA                                        │ JPA
         ▼                                            ▼
┌─────────────────┐                          ┌─────────────────┐
│   H2 Database   │                          │   H2 Database   │
│   (orderdb)     │                          │ (inventorydb)   │
│                 │                          │                 │
│  - orders       │                          │ - inventory_    │
│                 │                          │   batch         │
└─────────────────┘                          └─────────────────┘
```

### Technology Stack:

```
Layer              Technology           Version
─────────────────────────────────────────────────
Framework          Spring Boot          3.2.0
Language           Java                 17
Build Tool         Maven                3.6+
Database           H2 (in-memory)       Runtime
Migration          Liquibase            Latest
Testing            JUnit 5 + Mockito    Latest
Documentation      Lombok               Latest
```

---

## 🏗️ INVENTORY SERVICE DEEP DIVE

### 1. **Architecture Layers:**

```
┌──────────────────────────────────────────┐
│         Controller Layer                  │
│  (HTTP Endpoints, Request/Response)       │
│  • InventoryController.java              │
└──────────────┬───────────────────────────┘
               │
┌──────────────▼───────────────────────────┐
│          Service Layer                    │
│  (Business Logic, Validation)             │
│  • InventoryService.java                 │
│  • InventoryHandlerFactory (Factory)     │
│  • DefaultInventoryHandler (FIFO)        │
└──────────────┬───────────────────────────┘
               │
┌──────────────▼───────────────────────────┐
│        Repository Layer                   │
│  (Data Access, JPA)                      │
│  • InventoryBatchRepository.java         │
└──────────────┬───────────────────────────┘
               │
┌──────────────▼───────────────────────────┐
│           Database                        │
│  • inventory_batch table                 │
│  • Liquibase managed                     │
└──────────────────────────────────────────┘
```

### 2. **Key Design Patterns:**

#### **Factory Pattern:**
```java
InventoryHandlerFactory
    ↓
Creates → InventoryHandler (Interface)
    ↓
Implemented by:
    • DefaultInventoryHandler (FIFO)
    • [Future] LifoInventoryHandler
    • [Future] PriorityInventoryHandler
```

**Why Factory Pattern?**
> "The Factory Pattern allows us to easily extend inventory handling strategies without modifying existing code. Currently, we use FIFO (First In, First Out) based on expiry dates, but if business requirements change to LIFO or priority-based allocation, we can simply add new handler implementations without touching the service layer. This follows the Open/Closed Principle."

#### **Repository Pattern:**
```java
Spring Data JPA automatically implements:
InventoryBatchRepository extends JpaRepository
    ↓
Auto-generates SQL for:
    • findByProductId()
    • findByProductIdAndQuantityGreaterThan()
    • save(), findAll(), etc.
```

### 3. **FIFO Algorithm Explanation:**

```java
Problem: Product has 3 batches with different expiry dates
    Batch A: 50 units, expires 2026-01-15
    Batch B: 30 units, expires 2026-03-20
    Batch C: 40 units, expires 2026-06-10

Order: Reserve 60 units

FIFO Solution:
    1. Sort by expiry (A, B, C)
    2. Use Batch A completely (50 units)
    3. Use Batch B partially (10 units)
    4. Result: Reserved from [A, B]
```

**Implementation:**
```java
public List<Long> reserveInventory(List<InventoryBatch> batches, Integer quantity) {
    List<Long> reservedBatchIds = new ArrayList<>();
    int remaining = quantity;
    
    for (InventoryBatch batch : batches) {  // Already sorted by expiry
        if (remaining <= 0) break;
        
        int toReserve = Math.min(batch.getQuantity(), remaining);
        batch.setQuantity(batch.getQuantity() - toReserve);
        remaining -= toReserve;
        reservedBatchIds.add(batch.getBatchId());
    }
    
    if (remaining > 0) {
        throw new IllegalStateException("Insufficient inventory");
    }
    
    return reservedBatchIds;
}
```

### 4. **API Endpoints:**

#### **GET /inventory/{productId}**
```json
Request: GET http://localhost:8081/inventory/1005

Response: 200 OK
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
        }
    ]
}
```

**Use Case:** Check available inventory for a product

#### **POST /inventory/update**
```json
Request: POST http://localhost:8081/inventory/update
{
    "productId": 1005,
    "quantity": 50
}

Response: 200 OK
{
    "success": true,
    "message": "Inventory updated successfully",
    "reservedBatchIds": [5, 7]
}
```

**Use Case:** Reserve inventory when order is placed

---

## 🛒 ORDER SERVICE DEEP DIVE

### 1. **Architecture:**

```
OrderController
    ↓ (validates request)
OrderService
    ↓ (business logic)
    ├─► InventoryClient (checks availability)
    ├─► InventoryClient (reserves stock)
    └─► OrderRepository (saves order)
```

### 2. **Inter-Service Communication:**

```java
@Component
public class InventoryClient {
    private final RestTemplate restTemplate;
    
    public InventoryResponse getInventory(Long productId) {
        String url = "http://localhost:8081/inventory/" + productId;
        return restTemplate.getForEntity(url, InventoryResponse.class)
            .getBody();
    }
    
    public InventoryUpdateResponse updateInventory(Long productId, Integer qty) {
        String url = "http://localhost:8081/inventory/update";
        UpdateInventoryRequest request = new UpdateInventoryRequest(productId, qty);
        return restTemplate.postForEntity(url, request, 
            InventoryUpdateResponse.class).getBody();
    }
}
```

**Why RestTemplate?**
> "RestTemplate is Spring's synchronous HTTP client. For this assignment, it's perfect because order placement is naturally synchronous - we need to check and reserve inventory before confirming the order. In a production system with high volume, I'd consider WebClient for reactive programming or message queues like Kafka for asynchronous processing."

### 3. **Order Placement Flow:**

```
1. User → POST /order {productId: 1002, quantity: 3}
    ↓
2. OrderController validates request
    ↓
3. OrderService.placeOrder()
    ↓
4. Check inventory availability
    → InventoryClient.getInventory(1002)
    ← Returns: available = 112 units ✅
    ↓
5. Reserve inventory
    → InventoryClient.updateInventory(1002, 3)
    ← Returns: {success: true, reservedBatchIds: [9]}
    ↓
6. Create order in database
    → OrderRepository.save(order)
    ← Returns: Order{id: 11, status: PLACED}
    ↓
7. Return response
    ← {orderId: 11, status: "PLACED", reservedFromBatchIds: [9]}
```

### 4. **Error Handling:**

```java
try {
    // Check inventory
    InventoryResponse inv = inventoryClient.getInventory(productId);
    
    if (getTotalAvailable(inv) < quantity) {
        throw new IllegalStateException("Insufficient inventory");
    }
    
    // Reserve inventory
    inventoryClient.updateInventory(productId, quantity);
    
    // Save order
    return orderRepository.save(order);
    
} catch (RestClientException e) {
    throw new IllegalStateException("Inventory service unavailable");
}
```

**Interview Answer:**
> "I implemented defensive error handling at multiple levels. If the inventory service is down, we catch RestClientException and inform the user the service is unavailable. If inventory is insufficient, we validate BEFORE creating the order to prevent invalid states. This ensures data consistency across services."

---

## 🗄️ LIQUIBASE - DATABASE VERSIONING

### What is Liquibase?

> "Liquibase is a database migration tool that tracks and applies schema changes in a version-controlled way. Instead of manually running SQL scripts, Liquibase maintains a changelog of all database modifications, ensuring consistency across environments."

### Your Implementation:

```
resources/db/changelog/
├── db.changelog-master.xml     ← Master file
├── 01-create-inventory-table.xml
└── 02-load-inventory-data.xml
```

#### **Master Changelog:**
```xml
<databaseChangeLog>
    <include file="db/changelog/01-create-inventory-table.xml"/>
    <include file="db/changelog/02-load-inventory-data.xml"/>
</databaseChangeLog>
```

#### **Changeset 1: Create Table**
```xml
<changeSet id="1" author="mayank">
    <createTable tableName="inventory_batch">
        <column name="batch_id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true"/>
        </column>
        <column name="product_id" type="BIGINT">
            <constraints nullable="false"/>
        </column>
        <column name="quantity" type="INT"/>
        <column name="expiry_date" type="DATE"/>
    </createTable>
</changeSet>
```

#### **Changeset 2: Load Data**
```xml
<changeSet id="2" author="mayank">
    <loadData file="data/inventory.csv"
              tableName="inventory_batch"
              separator=",">
        <column name="batch_id" type="NUMERIC"/>
        <column name="product_id" type="NUMERIC"/>
        <!-- ... -->
    </loadData>
</changeSet>
```

### How It Works:

```
1. Application starts
    ↓
2. Liquibase checks DATABASECHANGELOG table
    ↓
3. Finds changesets not yet applied
    ↓
4. Executes them in order (01, 02, ...)
    ↓
5. Records execution in DATABASECHANGELOG
    ↓
6. Next startup: skips already-applied changes
```

### Interview Questions:

**Q: Why use Liquibase instead of Hibernate ddl-auto?**
> "Hibernate's ddl-auto=update is great for development but risky in production. It can't handle data migrations, doesn't track version history, and can accidentally drop columns. Liquibase gives us:
> - Version control for database
> - Rollback capability
> - Repeatable migrations across environments
> - Safe production deployments"

**Q: How do you add a new column?**
> "Create a new changeset file:
> ```xml
> <changeSet id="3" author="mayank">
>     <addColumn tableName="inventory_batch">
>         <column name="warehouse_location" type="VARCHAR(100)"/>
>     </addColumn>
> </changeSet>
> ```
> Liquibase automatically applies this on next startup."

---

## 🧪 TESTING STRATEGY

### Test Pyramid:

```
Your Project Distribution:

E2E Tests (Manual)        5%  ← Postman/curl testing
    │
Integration Tests        35%  ← 15 tests
    │                          Full stack + real DB
    │
Unit Tests              60%  ← 11 tests
                               Isolated logic testing
```

### Coverage Achieved:

```
Overall: ~85% line coverage

Breakdown:
├── Controllers:     90%
├── Services:        95%
├── Repositories:    N/A (interfaces)
├── Models:         100% (Lombok)
└── DTOs:           100%
```

### Why This Balance?

> "Unit tests are fast and test logic in isolation. Integration tests are slower but catch integration issues. I focused on achieving high coverage of business logic (services) while ensuring end-to-end flows work correctly. The 85% coverage gives confidence without testing trivial code like getters/setters."

---

## 🎯 SPRING BOOT CONCEPTS

### 1. **Dependency Injection:**

```java
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class InventoryService {
    
    private final InventoryBatchRepository repository;  // ← Injected
    private final InventoryHandlerFactory factory;      // ← Injected
    
    // Spring automatically injects these dependencies
}
```

**Interview Answer:**
> "Spring's DI container manages object creation and wiring. I use constructor injection (via Lombok's @RequiredArgsConstructor) which is the recommended approach because it makes dependencies explicit, enables immutability, and simplifies testing."

### 2. **Spring Data JPA:**

```java
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {
    
    // Spring generates SQL automatically from method name!
    List<InventoryBatch> findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(
        Long productId, Integer quantity);
    
    // Generated SQL:
    // SELECT * FROM inventory_batch 
    // WHERE product_id = ? AND quantity > ? 
    // ORDER BY expiry_date ASC
}
```

### 3. **Transaction Management:**

```java
@Transactional
public UpdateInventoryResponse updateInventory(Long productId, Integer quantity) {
    // All operations in this method are part of ONE transaction
    // If ANY operation fails → ROLLBACK entire transaction
    
    List<InventoryBatch> batches = repository.find...();
    handler.reserveInventory(batches, quantity);
    repository.saveAll(batches);  // ← Commits here if no exception
    
    return response;
}
```

**Why @Transactional?**
> "Ensures atomicity. Either ALL inventory updates succeed, or NONE do. This prevents partial inventory reservations that could cause data inconsistency."

---

## 📊 MICROSERVICES CONCEPTS

### 1. **Service Independence:**

```
Each service has:
✅ Own database (orderdb vs inventorydb)
✅ Own codebase
✅ Own port (8080 vs 8081)
✅ Independent deployment
✅ Can scale independently
```

### 2. **Communication:**

**Synchronous (REST):**
```java
// What you implemented
OrderService → REST call → InventoryService
```

**Pros:**
- ✅ Simple to implement
- ✅ Immediate response
- ✅ Easy to debug

**Cons:**
- ❌ Tight coupling
- ❌ If Inventory down, Order fails
- ❌ Not scalable for high volume

**Asynchronous (Kafka - Future Enhancement):**
```java
OrderService → Kafka Topic → InventoryService
```

**Pros:**
- ✅ Loose coupling
- ✅ Service independence
- ✅ Better scalability
- ✅ Event sourcing

**When to use:**
> "For this assignment, REST is appropriate because order placement requires immediate inventory confirmation. In production with thousands of orders/second, I'd use Kafka for asynchronous processing, with a saga pattern for distributed transactions."

---

## 🎤 COMMON INTERVIEW QUESTIONS

### Q1: "Walk me through placing an order in your system"

**Answer:**
> "When a user places an order:
> 
> 1. **Request arrives** at OrderController (POST /order)
> 2. **Validation** checks productId and quantity are valid
> 3. **Check availability** via InventoryClient.getInventory()
> 4. **Calculate total** available across all batches
> 5. **Validate sufficient** stock exists
> 6. **Reserve inventory** via InventoryClient.updateInventory()
>    - Inventory Service uses FIFO to select batches
>    - Reduces quantities in database
> 7. **Create order** in Order Service database
> 8. **Return response** with order ID and reserved batches
> 
> If any step fails, appropriate exception is thrown and no partial state is saved."

### Q2: "How does FIFO work in your system?"

**Answer:**
> "FIFO ensures we use inventory closest to expiry first:
> 
> 1. Repository queries batches **sorted by expiry_date ASC**
> 2. DefaultInventoryHandler iterates through sorted list
> 3. For each batch, reserves minimum of (batch quantity, remaining needed)
> 4. Continues until request fulfilled or inventory exhausted
> 5. Updates batch quantities in database
> 
> **Example:** Need 50 units, have batches: [30 units, 40 units, 50 units]
> - Use first batch completely: 30 units
> - Use second batch partially: 20 units
> - Third batch untouched
> 
> This minimizes waste by ensuring older inventory ships first."

### Q3: "How did you test the FIFO logic?"

**Answer:**
> "Three-level testing approach:
> 
> **Unit Test (DefaultInventoryHandlerTest):**
> - Tests algorithm with sample data
> - Verifies correct batches selected
> - Fast, isolated testing
> 
> **Service Unit Test (InventoryServiceTest):**
> - Tests service orchestration
> - Mocks repository and handler
> - Verifies correct methods called
> 
> **Integration Test (InventoryServiceIntegrationTest):**
> - End-to-end test with real database
> - Places order, verifies correct batch consumed
> - Tests with Liquibase-loaded data
> 
> This gives confidence the algorithm works correctly in isolation AND in the full system."

### Q4: "What would you improve in this system?"

**Answer:**
> "For production, I'd add:
> 
> **1. Error Handling:**
> - Circuit breaker (Resilience4j) for service calls
> - Retry logic with exponential backoff
> - Graceful degradation
> 
> **2. Monitoring:**
> - Spring Boot Actuator for health checks
> - Prometheus + Grafana for metrics
> - Distributed tracing (Jaeger)
> 
> **3. Security:**
> - JWT authentication
> - API rate limiting
> - Input sanitization
> 
> **4. Scalability:**
> - Kafka for async communication
> - Redis for caching inventory
> - Database connection pooling
> 
> **5. Data Consistency:**
> - Saga pattern for distributed transactions
> - Event sourcing for audit trail
> - Optimistic locking for concurrent updates"

---

## ✅ KEY TAKEAWAYS FOR INTERVIEW

### Technical Depth:
✅ Understand each layer (Controller, Service, Repository)
✅ Explain design patterns (Factory, Repository)
✅ Know FIFO algorithm implementation
✅ Understand Liquibase changesets
✅ Explain testing strategy

### Business Value:
✅ FIFO reduces waste
✅ Microservices enable scaling
✅ Testing ensures reliability
✅ Liquibase enables safe deployments

### Improvements:
✅ Know what's missing (authentication, caching, etc.)
✅ Understand when to use Kafka vs REST
✅ Be honest about trade-offs made

**Confidence Statement:**
> "This project demonstrates my ability to build production-ready microservices with proper architecture, comprehensive testing, and business logic implementation. While it's a simplified version, the patterns and practices I used are the foundation for enterprise systems."
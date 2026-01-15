// ==================== RESILIENCE4J - ERROR HANDLING ====================

/**
 * Circuit Breaker: Prevents cascading failures by stopping requests to failing services
 * Retry: Automatically retries failed requests with exponential backoff
 * Rate Limiter: Limits number of requests per time period
 * Bulkhead: Limits concurrent requests to prevent resource exhaustion
 */

// ==================== STEP 1: ADD DEPENDENCIES ====================

// In parent pom.xml:
/*
```xml
<properties>
    <resilience4j.version>2.1.0</resilience4j.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```
*/

// In order-service pom.xml:
/*
```xml
<dependencies>
    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-circuitbreaker</artifactId>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-retry</artifactId>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-ratelimiter</artifactId>
    </dependency>
    
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-bulkhead</artifactId>
    </dependency>
    
    <!-- AOP for annotations -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
</dependencies>
```
*/

// ==================== STEP 2: CONFIGURATION ====================

// application.yml (order-service)
/*
```yaml
resilience4j:
  # Circuit Breaker Configuration
  circuitbreaker:
    instances:
      inventoryService:
        registerHealthIndicator: true
        slidingWindowSize: 10                    # Number of calls to evaluate
        minimumNumberOfCalls: 5                  # Min calls before evaluating
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 10s             # Wait before trying again
        failureRateThreshold: 50                 # 50% failure opens circuit
        slowCallRateThreshold: 50                # 50% slow calls opens circuit
        slowCallDurationThreshold: 2s            # Calls > 2s are "slow"
        recordExceptions:
          - org.springframework.web.client.RestClientException
          - java.net.ConnectException
        ignoreExceptions:
          - java.lang.IllegalArgumentException
  
  # Retry Configuration
  retry:
    instances:
      inventoryService:
        maxAttempts: 3                           # Retry 3 times
        waitDuration: 1s                         # Wait 1s between retries
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2          # 1s, 2s, 4s
        retryExceptions:
          - org.springframework.web.client.RestClientException
        ignoreExceptions:
          - java.lang.IllegalArgumentException
  
  # Rate Limiter Configuration
  ratelimiter:
    instances:
      inventoryService:
        limitForPeriod: 10                       # 10 calls
        limitRefreshPeriod: 1s                   # Per second
        timeoutDuration: 0                       # Don't wait, fail immediately
  
  # Bulkhead Configuration (Limit concurrent calls)
  bulkhead:
    instances:
      inventoryService:
        maxConcurrentCalls: 5                    # Max 5 concurrent calls
        maxWaitDuration: 1s

  # Time Limiter (Overall timeout)
  timelimiter:
    instances:
      inventoryService:
        timeoutDuration: 5s                      # Max 5s per call
        cancelRunningFuture: true

# Actuator endpoints to view Resilience4j metrics
management:
  endpoints:
    web:
      exposure:
        include: health,circuitbreakers,ratelimiters,retries,bulkheads
  health:
    circuitbreakers:
      enabled: true
  endpoint:
    health:
      show-details: always
```

*/

// ==================== STEP 3: ENHANCED INVENTORY CLIENT ====================

```java
package dev.mayank.korber.order.client;

import dev.mayank.korber.order.dto.InventoryResponse;
import dev.mayank.korber.order.dto.InventoryUpdateRequest;
import dev.mayank.korber.order.dto.InventoryUpdateResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResilientInventoryClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${inventory.service.url:http://localhost:8081}")
    private String inventoryServiceUrl;
    
    /**
     * Get inventory with Circuit Breaker, Retry, and Rate Limiter
     * 
     * Circuit Breaker: Opens if 50% of last 10 calls fail
     * Retry: Retries 3 times with exponential backoff (1s, 2s, 4s)
     * Rate Limiter: Max 10 calls per second
     * Bulkhead: Max 5 concurrent calls
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "getInventoryFallback")
    @Retry(name = "inventoryService")
    @RateLimiter(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public InventoryResponse getInventory(Long productId) {
        String url = inventoryServiceUrl + "/inventory/" + productId;
        log.info("Calling Inventory Service: {}", url);
        
        return restTemplate.getForObject(url, InventoryResponse.class);
    }
    
    /**
     * Fallback method when circuit is OPEN or all retries exhausted
     * This prevents complete failure and provides graceful degradation
     */
    private InventoryResponse getInventoryFallback(Long productId, Exception ex) {
        log.error("Inventory Service unavailable. Using fallback. ProductId: {}", productId, ex);
        
        // Return cached data or default response
        InventoryResponse fallbackResponse = new InventoryResponse();
        fallbackResponse.setProductId(productId);
        fallbackResponse.setProductName("Unknown Product");
        fallbackResponse.setBatches(java.util.Collections.emptyList());
        
        return fallbackResponse;
    }
    
    /**
     * Update inventory with resilience patterns
     */
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "updateInventoryFallback")
    @Retry(name = "inventoryService")
    @RateLimiter(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public InventoryUpdateResponse updateInventory(Long productId, Integer quantity) {
        String url = inventoryServiceUrl + "/inventory/update";
        log.info("Updating inventory: productId={}, quantity={}", productId, quantity);
        
        InventoryUpdateRequest request = new InventoryUpdateRequest(productId, quantity);
        
        return restTemplate.postForObject(url, request, InventoryUpdateResponse.class);
    }
    
    /**
     * Fallback for update - throw exception to prevent order creation
     */
    private InventoryUpdateResponse updateInventoryFallback(
            Long productId, Integer quantity, Exception ex) {
        
        log.error("Failed to update inventory after retries. ProductId: {}, Quantity: {}", 
            productId, quantity, ex);
        
        // Don't create order if we can't reserve inventory
        throw new ServiceUnavailableException(
            "Inventory service is currently unavailable. Please try again later.", ex);
    }
}
```

// ==================== STEP 4: CUSTOM EXCEPTIONS ====================

```java
package dev.mayank.korber.order.exception;

public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String message) {
        super(message);
    }
    
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

// ==================== STEP 5: GLOBAL EXCEPTION HANDLER ====================

package dev.mayank.korber.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(
            ServiceUnavailableException ex) {
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        errorResponse.put("error", "Service Unavailable");
        errorResponse.put("message", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(errorResponse);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
        errorResponse.put("error", "Bad Request");
        errorResponse.put("message", ex.getMessage());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex) {
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        errorResponse.put("error", "Unprocessable Entity");
        errorResponse.put("message", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(errorResponse);
    }
}
```

// ==================== STEP 6: CIRCUIT BREAKER STATE LISTENER ====================
        
        
```java
package dev.mayank.korber.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class CircuitBreakerConfiguration {
    
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        
        // Register event listeners for all circuit breakers
        registry.circuitBreaker("inventoryService").getEventPublisher()
            .onStateTransition(this::logStateTransition)
            .onError(event -> 
                log.error("Circuit breaker error: {}", event.getThrowable().getMessage()))
            .onSuccess(event -> 
                log.debug("Circuit breaker success"))
            .onCallNotPermitted(event -> 
                log.warn("Circuit breaker call not permitted - Circuit is OPEN"));
        
        return registry;
    }
    
    private void logStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("Circuit Breaker State Transition: {} -> {}", 
            event.getStateTransition().getFromState(),
            event.getStateTransition().getToState());
        
        if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
            log.error("⚠️ CIRCUIT BREAKER OPENED for inventoryService! " +
                     "Requests will be blocked for configured wait duration.");
        } else if (event.getStateTransition().getToState() == CircuitBreaker.State.HALF_OPEN) {
            log.info("Circuit breaker entered HALF_OPEN state. Testing with limited requests.");
        } else if (event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED) {
            log.info("✅ Circuit breaker CLOSED. Normal operation resumed.");
        }
    }
}
```

// ==================== STEP 7: TESTING ====================

```java
package dev.mayank.korber.order.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class ResilientInventoryClientTest {
    
    @Autowired
    private ResilientInventoryClient client;
    
    @MockBean
    private RestTemplate restTemplate;
    
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @BeforeEach
    void setUp() {
        // Reset circuit breaker before each test
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("inventoryService");
        circuitBreaker.transitionToClosedState();
    }
    
    @Test
    void testRetryOnFailure() {
        // Arrange: First 2 calls fail, 3rd succeeds
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
            .thenThrow(new RestClientException("Service down"))
            .thenThrow(new RestClientException("Service down"))
            .thenReturn(new InventoryResponse(1001L, "Laptop", null));
        
        // Act
        InventoryResponse response = client.getInventory(1001L);
        
        // Assert: Should retry and eventually succeed
        assertNotNull(response);
        assertEquals(1001L, response.getProductId());
        verify(restTemplate, times(3)).getForObject(anyString(), eq(InventoryResponse.class));
    }
    
    @Test
    void testCircuitBreakerOpens() {
        // Arrange: All calls fail
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
            .thenThrow(new RestClientException("Service down"));
        
        // Act: Make enough calls to open circuit (10 calls with 50% failure rate)
        for (int i = 0; i < 10; i++) {
            try {
                client.getInventory(1001L);
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Assert: Circuit should be open
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("inventoryService");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
    
    @Test
    void testFallbackCalled() {
        // Arrange: Service always fails
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
            .thenThrow(new RestClientException("Service down"));
        
        // Act: Circuit eventually opens and fallback is called
        InventoryResponse response = null;
        for (int i = 0; i < 15; i++) {
            response = client.getInventory(1001L);
        }
        
        // Assert: Fallback response returned
        assertNotNull(response);
        assertEquals("Unknown Product", response.getProductName());
    }
}
```
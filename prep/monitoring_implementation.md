# ==================== MONITORING SETUP ====================

# ==================== STEP 1: ADD DEPENDENCIES ====================

# In parent pom.xml:
```xml
<dependencies>
     <!-- Spring Boot Actuator -->
     <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-actuator</artifactId>
     </dependency>
     
     <!-- Micrometer Prometheus Registry -->
     <dependency>
         <groupId>io.micrometer</groupId>
         <artifactId>micrometer-registry-prometheus</artifactId>
     </dependency>
     
     <!-- Distributed Tracing (Optional) -->
     <dependency>
         <groupId>io.micrometer</groupId>
         <artifactId>micrometer-tracing-bridge-brave</artifactId>
     </dependency>
     
     <dependency>
         <groupId>io.zipkin.reporter2</groupId>
         <artifactId>zipkin-reporter-brave</artifactId>
     </dependency>
 </dependencies>
```

# ==================== STEP 2: APPLICATION.YML CONFIGURATION ====================

```yaml
# application.yml (Both services)
spring:
  application:
    name: order-service  # or inventory-service

# Actuator Configuration
management:
  # Expose all actuator endpoints
  endpoints:
    web:
      exposure:
        include: "*"  # In production, be selective: health,info,prometheus,metrics
      base-path: /actuator
  
  # Health endpoint configuration
  endpoint:
    health:
      show-details: always
      show-components: always
      probes:
        enabled: true  # For Kubernetes liveness/readiness probes
  
  # Metrics configuration
  metrics:
    tags:
      application: ${spring.application.name}
      environment: dev
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms,500ms,1s,2s,5s
    export:
      prometheus:
        enabled: true
  
  # Health indicators
  health:
    circuitbreakers:
      enabled: true
    ratelimiters:
      enabled: true
    retries:
      enabled: true
    db:
      enabled: true
    diskspace:
      enabled: true
  
  # Info endpoint
  info:
    env:
      enabled: true
    java:
      enabled: true
    os:
      enabled: true
    git:
      mode: full

# Distributed Tracing (Optional)
management:
  tracing:
    sampling:
      probability: 1.0  # Sample 100% of requests (reduce in production)
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

# Logging
logging:
  level:
    root: INFO
    dev.mayank.korber: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/${spring.application.name}.log
```

# ==================== STEP 3: CUSTOM METRICS ====================

# CustomMetricsConfig.java
/*
```java
package dev.mayank.korber.order.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomMetricsConfig {
    
    @Bean
    public Counter orderPlacedCounter(MeterRegistry registry) {
        return Counter.builder("orders.placed")
            .description("Total number of orders placed")
            .tag("service", "order-service")
            .register(registry);
    }
    
    @Bean
    public Counter orderFailedCounter(MeterRegistry registry) {
        return Counter.builder("orders.failed")
            .description("Total number of failed orders")
            .tag("service", "order-service")
            .register(registry);
    }
    
    @Bean
    public Timer inventoryCheckTimer(MeterRegistry registry) {
        return Timer.builder("inventory.check.duration")
            .description("Time taken to check inventory")
            .tag("service", "order-service")
            .register(registry);
    }
}
```
*/

# ==================== STEP 4: ENHANCED SERVICE WITH METRICS ====================

# OrderService.java (with metrics)
/*
```java
package dev.mayank.korber.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Create order
            Order order = createOrder(request);
            
            // Check and reserve inventory (timed)
            Timer.Sample inventoryCheck = Timer.start(meterRegistry);
            InventoryUpdateResponse inventoryResponse = 
                inventoryClient.updateInventory(request.getProductId(), request.getQuantity());
            inventoryCheck.stop(Timer.builder("inventory.check.duration")
                .register(meterRegistry));
            
            // Update order status
            order.setStatus(Order.OrderStatus.CONFIRMED);
            orderRepository.save(order);
            
            // Increment success counter
            meterRegistry.counter("orders.placed", "status", "success").increment();
            
            sample.stop(Timer.builder("orders.processing.time")
                .tag("status", "success")
                .register(meterRegistry));
            
            return buildResponse(order, inventoryResponse);
            
        } catch (Exception e) {
            log.error("Failed to place order", e);
            
            // Increment failure counter
            meterRegistry.counter("orders.placed", "status", "failed").increment();
            
            sample.stop(Timer.builder("orders.processing.time")
                .tag("status", "failed")
                .register(meterRegistry));
            
            throw e;
        }
    }
}
```
*/

# ==================== STEP 5: PROMETHEUS CONFIGURATION ====================

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Order Service
  - job_name: 'order-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
        labels:
          application: 'order-service'
          environment: 'dev'
  
  # Inventory Service
  - job_name: 'inventory-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8081']
        labels:
          application: 'inventory-service'
          environment: 'dev'
```

# ==================== STEP 6: DOCKER COMPOSE FOR MONITORING STACK ====================

```yaml
# docker-compose-monitoring.yml
version: '3.8'

services:
  # Prometheus
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    networks:
      - monitoring

  # Grafana
  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
    depends_on:
      - prometheus
    networks:
      - monitoring

  # Zipkin (for distributed tracing)
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    networks:
      - monitoring

networks:
  monitoring:
    driver: bridge

volumes:
  prometheus-data:
  grafana-data:
```

# ==================== STEP 7: GRAFANA DATASOURCE CONFIGURATION ====================

# grafana/datasources/prometheus.yml
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

# ==================== STEP 8: GRAFANA DASHBOARD CONFIGURATION ====================

# grafana/dashboards/dashboard.yml
```yaml
apiVersion: 1

providers:
  - name: 'Order Service Dashboard'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards
```

# ==================== STEP 9: HEALTH CHECK ENDPOINT ====================

# Custom Health Indicator
/*
```java
package dev.mayank.korber.order.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class InventoryServiceHealthIndicator implements HealthIndicator {
    
    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;
    
    @Override
    public Health health() {
        try {
            // Check if inventory service is reachable
            restTemplate.getForObject(
                inventoryServiceUrl + "/actuator/health", 
                String.class
            );
            
            return Health.up()
                .withDetail("inventoryService", "Available")
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("inventoryService", "Unavailable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```
*/

# ==================== STEP 10: KEY METRICS TO MONITOR ====================

# Metrics exposed at /actuator/prometheus:

# JVM Metrics:
# - jvm_memory_used_bytes
# - jvm_memory_max_bytes
# - jvm_gc_pause_seconds
# - jvm_threads_live

# HTTP Metrics:
# - http_server_requests_seconds_count
# - http_server_requests_seconds_sum
# - http_server_requests_seconds_max

# Database Metrics:
# - hikaricp_connections_active
# - hikaricp_connections_idle
# - hikaricp_connections_pending

# Custom Metrics:
# - orders_placed_total
# - orders_failed_total
# - inventory_check_duration_seconds
# - orders_processing_time_seconds

# Circuit Breaker Metrics:
# - resilience4j_circuitbreaker_state
# - resilience4j_circuitbreaker_failure_rate
# - resilience4j_circuitbreaker_slow_call_rate

# ==================== USEFUL ACTUATOR ENDPOINTS ====================

# Health Check:
# GET http://localhost:8080/actuator/health
# Response:
```json
{
   "status": "UP",
   "components": {
     "circuitBreakers": {
       "status": "UP",
       "details": {
         "inventoryService": {
           "status": "UP",
           "state": "CLOSED"
         }
       }
     },
     "db": {
       "status": "UP"
     }
   }
 }
```

# Metrics:
# GET http://localhost:8080/actuator/metrics
# GET http://localhost:8080/actuator/metrics/orders.placed

# Prometheus Format:
# GET http://localhost:8080/actuator/prometheus

# Info:
# GET http://localhost:8080/actuator/info

# Environment:
# GET http://localhost:8080/actuator/env
// ==================== SCALABILITY IMPROVEMENTS ====================

// ==================== STEP 1: REDIS CACHING ====================

// Add dependencies to parent pom.xml:
/*
```xml
<dependencies>
    <!-- Redis Cache -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    
    <!-- Connection Pool (HikariCP - already included in Spring Boot) -->
    <!-- Lettuce Redis Client (default with spring-data-redis) -->
</dependencies>
```
*/

// application.yml configuration:
/*
```yaml
spring:
  # Redis Configuration
  redis:
    host: localhost
    port: 6379
    password:  # Leave empty if no password
    timeout: 60000
    jedis:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: -1ms
  
  # Cache Configuration
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutes
      cache-null-values: false
      
  # Database Connection Pool (HikariCP)
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: HikariPool-OrderService
```
*/

// ==================== STEP 2: REDIS CONFIGURATION ====================

```java
package dev.mayank.korber.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        
        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))  // Default TTL: 10 minutes
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            );
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .withCacheConfiguration("inventory", 
                config.entryTtl(Duration.ofMinutes(5)))  // Inventory cache: 5 min
            .withCacheConfiguration("orders", 
                config.entryTtl(Duration.ofMinutes(15))) // Orders cache: 15 min
            .build();
    }
}
```

// ==================== STEP 3: CACHED INVENTORY SERVICE ====================

```java
package dev.mayank.korber.inventory.service;

import dev.mayank.korber.inventory.dto.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedInventoryService {
    
    private final InventoryBatchRepository repository;
    private final InventoryHandlerFactory handlerFactory;
    
    /**
     * Cache inventory lookups
     * Key: "inventory::productId"
     * TTL: 5 minutes (from config)
     */
    @Cacheable(value = "inventory", key = "#productId", unless = "#result == null")
    public InventoryResponse getInventoryByProductId(Long productId) {
        log.info("Cache MISS - Fetching from database: productId={}", productId);
        
        List<InventoryBatch> batches = repository
            .findByProductIdOrderByExpiryDateAsc(productId);
        
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }
        
        InventoryHandler handler = handlerFactory.getDefaultHandler();
        List<InventoryBatch> sortedBatches = handler.getBatchesSorted(batches);
        
        InventoryResponse response = new InventoryResponse();
        response.setProductId(productId);
        response.setProductName(batches.get(0).getProductName());
        response.setBatches(sortedBatches.stream()
            .map(batch -> new BatchDTO(
                batch.getBatchId(),
                batch.getQuantity(),
                batch.getExpiryDate()
            ))
            .collect(Collectors.toList()));
        
        return response;
    }
    
    /**
     * Update inventory and evict cache
     * Cache is invalidated after update
     */
    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public UpdateInventoryResponse updateInventory(Long productId, Integer quantity) {
        log.info("Updating inventory and evicting cache: productId={}", productId);
        
        List<InventoryBatch> batches = repository
            .findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(productId, 0);
        
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("Product not found or out of stock");
        }
        
        // Check total availability
        int totalAvailable = batches.stream()
            .mapToInt(InventoryBatch::getQuantity)
            .sum();
        
        if (totalAvailable < quantity) {
            throw new IllegalStateException(
                String.format("Insufficient inventory. Requested: %d, Available: %d", 
                    quantity, totalAvailable)
            );
        }
        
        InventoryHandler handler = handlerFactory.getDefaultHandler();
        List<Long> reservedBatchIds = handler.reserveInventory(batches, quantity);
        
        repository.saveAll(batches);
        
        UpdateInventoryResponse response = new UpdateInventoryResponse();
        response.setSuccess(true);
        response.setMessage("Inventory updated successfully");
        response.setReservedBatchIds(reservedBatchIds);
        
        return response;
    }
    
    /**
     * Manually refresh cache entry
     */
    @CachePut(value = "inventory", key = "#productId")
    public InventoryResponse refreshInventoryCache(Long productId) {
        log.info("Manually refreshing cache: productId={}", productId);
        return getInventoryByProductId(productId);
    }
    
    /**
     * Clear all inventory cache
     */
    @CacheEvict(value = "inventory", allEntries = true)
    public void clearInventoryCache() {
        log.info("Clearing all inventory cache");
    }
}
```

// ==================== STEP 4: CACHE WARMING ====================

```java
package dev.mayank.korber.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@EnableAsync
public class CacheWarmer {
    
    private final CachedInventoryService inventoryService;
    private final InventoryBatchRepository repository;
    
    /**
     * Warm cache on application startup with popular products
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmCache() {
        log.info("Starting cache warming...");
        
        try {
            // Get all distinct product IDs
            List<Long> productIds = repository.findAll().stream()
                .map(InventoryBatch::getProductId)
                .distinct()
                .toList();
            
            // Load each product into cache
            for (Long productId : productIds) {
                try {
                    inventoryService.getInventoryByProductId(productId);
                    log.debug("Warmed cache for product: {}", productId);
                } catch (Exception e) {
                    log.warn("Failed to warm cache for product: {}", productId, e);
                }
            }
            
            log.info("Cache warming completed. {} products loaded.", productIds.size());
            
        } catch (Exception e) {
            log.error("Cache warming failed", e);
        }
    }
}
```

// ==================== STEP 5: CACHE METRICS ====================

```java
package dev.mayank.korber.inventory.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CacheMetrics {
    
    private final CacheManager cacheManager;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void recordCacheMetrics() {
        Cache inventoryCache = cacheManager.getCache("inventory");
        
        if (inventoryCache != null) {
            // Record cache statistics
            meterRegistry.gauge("cache.size", 
                Tags.of("cache", "inventory"), 
                inventoryCache, 
                c -> getCacheSize(c));
        }
    }
    
    private double getCacheSize(Cache cache) {
        // Implementation depends on cache provider
        // For Redis, you'd query the key count
        return 0.0;
    }
}
```

// ==================== STEP 6: REDIS DOCKER COMPOSE ====================

/*
```yaml
# docker-compose.yml - Add Redis service

version: '3.8'

services:
  redis:
    image: redis:7-alpine
    container_name: redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes
    networks:
      - app-network

  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: redis-commander
    environment:
      - REDIS_HOSTS=local:redis:6379
    ports:
      - "8082:8081"
    depends_on:
      - redis
    networks:
      - app-network

volumes:
  redis-data:

networks:
  app-network:
    driver: bridge
```
*/

// ==================== STEP 7: CONNECTION POOLING MONITORING ====================

```java
package dev.mayank.korber.order.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.db.MetricsDSLContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class DataSourceMetricsConfig {
    
    private final MeterRegistry meterRegistry;
    
    @Bean
    public HikariDataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        
        // Connection pool configuration
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        
        // Enable metrics
        ds.setMetricRegistry(null); // Use Micrometer
        ds.setPoolName("HikariPool-OrderService");
        
        return ds;
    }
}
```

// ==================== STEP 8: TESTING REDIS CACHE ====================

```java
package dev.mayank.korber.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.redis.host=localhost",
    "spring.redis.port=6379"
})
class CachedInventoryServiceTest {
    
    @Autowired
    private CachedInventoryService inventoryService;
    
    @Autowired
    private CacheManager cacheManager;
    
    @Test
    void testCacheHit() {
        Long productId = 1001L;
        
        // First call - cache miss
        InventoryResponse response1 = inventoryService.getInventoryByProductId(productId);
        assertNotNull(response1);
        
        // Second call - cache hit (should be faster)
        long startTime = System.currentTimeMillis();
        InventoryResponse response2 = inventoryService.getInventoryByProductId(productId);
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull(response2);
        assertEquals(response1.getProductId(), response2.getProductId());
        assertTrue(duration < 10); // Should be very fast from cache
    }
    
    @Test
    void testCacheEviction() {
        Long productId = 1001L;
        
        // Load into cache
        inventoryService.getInventoryByProductId(productId);
        
        // Verify cached
        assertNotNull(cacheManager.getCache("inventory").get(productId));
        
        // Update inventory - should evict cache
        inventoryService.updateInventory(productId, 5);
        
        // Verify cache evicted
        assertNull(cacheManager.getCache("inventory").get(productId));
    }
}
```
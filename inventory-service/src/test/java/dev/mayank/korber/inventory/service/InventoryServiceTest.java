package dev.mayank.korber.inventory.service;

import dev.mayank.korber.inventory.dto.InventoryResponse;
import dev.mayank.korber.inventory.dto.UpdateInventoryResponse;
import dev.mayank.korber.inventory.model.InventoryBatch;
import dev.mayank.korber.inventory.repository.InventoryBatchRepository;
import dev.mayank.korber.inventory.service.factory.InventoryHandler;
import dev.mayank.korber.inventory.service.factory.InventoryHandlerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryService
 * Tests service logic in isolation with mocked dependencies
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Inventory Service Unit Tests")
class InventoryServiceTest {

    @Mock
    private InventoryBatchRepository repository;

    @Mock
    private InventoryHandlerFactory handlerFactory;

    @Mock
    private InventoryHandler handler;

    @InjectMocks
    private InventoryService inventoryService;

    private List<InventoryBatch> sampleBatches;

    @BeforeEach
    void setUp() {
        // Create sample test data
        sampleBatches = new ArrayList<>();
        sampleBatches.add(new InventoryBatch(
                1L, 1001L, "Laptop", 50, LocalDate.of(2025, 12, 31)
        ));
        sampleBatches.add(new InventoryBatch(
                2L, 1001L, "Laptop", 30, LocalDate.of(2026, 3, 15)
        ));
    }

    @Test
    @DisplayName("Get inventory by product ID - Success")
    void getInventoryByProductId_Success() {
        // Arrange
        Long productId = 1001L;

        when(repository.findByProductIdOrderByExpiryDateAsc(anyLong()))
                .thenReturn(sampleBatches);
        when(handlerFactory.getDefaultHandler())
                .thenReturn(handler);
        when(handler.getBatchesSorted(any()))
                .thenReturn(sampleBatches);

        // Act
        InventoryResponse response = inventoryService.getInventoryByProductId(productId);

        // Assert
        assertNotNull(response);
        assertEquals(productId, response.getProductId());
        assertEquals("Laptop", response.getProductName());
        assertEquals(2, response.getBatches().size());
        assertEquals(1L, response.getBatches().get(0).getBatchId());
        assertEquals(50, response.getBatches().get(0).getQuantity());

        // Verify interactions
        verify(repository, times(1)).findByProductIdOrderByExpiryDateAsc(productId);
        verify(handlerFactory, times(1)).getDefaultHandler();
        verify(handler, times(1)).getBatchesSorted(any());
    }

    @Test
    @DisplayName("Get inventory by product ID - Product not found")
    void getInventoryByProductId_ProductNotFound() {
        // Arrange
        Long productId = 9999L;
        when(repository.findByProductIdOrderByExpiryDateAsc(anyLong()))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.getInventoryByProductId(productId)
        );

        assertTrue(exception.getMessage().contains("No inventory found for"));
        verify(repository, times(1)).findByProductIdOrderByExpiryDateAsc(productId);
        verify(handlerFactory, never()).getDefaultHandler();
    }

    @Test
    @DisplayName("Update inventory - Success")
    void updateInventory_Success() {
        // Arrange
        Long productId = 1001L;
        Integer quantity = 20;
        List<Long> reservedBatchIds = List.of(1L);

        // The service always queries with 0 as the minimum quantity
        when(repository.findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(
                productId, 0))
                .thenReturn(sampleBatches);

        when(handlerFactory.getDefaultHandler())
                .thenReturn(handler);

        when(handler.reserveInventory(any(), anyInt()))
                .thenReturn(reservedBatchIds);

        when(repository.saveAll(any()))
                .thenReturn(sampleBatches);

        // Act
        UpdateInventoryResponse response = inventoryService.updateInventory(productId, quantity);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertEquals("Successfully updated inventory", response.getMessage());
        assertEquals(reservedBatchIds, response.getReservedBatchIds());

        // Verify interactions
        verify(repository, times(1))
                .findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(productId, 0);
        verify(handlerFactory, times(1)).getDefaultHandler();
        verify(handler, times(1)).reserveInventory(any(), anyInt());
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("Update inventory - Insufficient stock")
    void updateInventory_InsufficientStock() {
        // Arrange
        Long productId = 1001L;
        Integer quantity = 100; // More than available (50 + 30 = 80)

        when(repository.findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(
                productId, 0))
                .thenReturn(sampleBatches);

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> inventoryService.updateInventory(productId, quantity)
        );

        assertTrue(exception.getMessage().contains("Insufficient inventory"));

        verify(repository, times(1))
                .findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(productId, 0);
        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Update inventory - Product not found")
    void updateInventory_ProductNotFound() {
        // Arrange
        Long productId = 9999L;
        Integer quantity = 10;

        when(repository.findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(
                productId, 0))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.updateInventory(productId, quantity)
        );

        assertTrue(exception.getMessage().contains("Product not found or out of stock"));
        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Update inventory - Zero quantity request")
    void updateInventory_ZeroQuantity() {
        // Arrange
        Long productId = 1001L;
        Integer quantity = 0;

        when(repository.findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(
                productId, 0))
                .thenReturn(sampleBatches);

        when(handlerFactory.getDefaultHandler())
                .thenReturn(handler);

        when(handler.reserveInventory(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        when(repository.saveAll(any()))
                .thenReturn(sampleBatches);

        // Act
        UpdateInventoryResponse response = inventoryService.updateInventory(productId, quantity);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertTrue(response.getReservedBatchIds().isEmpty());
    }
}

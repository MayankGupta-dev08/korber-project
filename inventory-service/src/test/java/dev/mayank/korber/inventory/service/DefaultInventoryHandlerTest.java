package dev.mayank.korber.inventory.service;

import dev.mayank.korber.inventory.model.InventoryBatch;
import dev.mayank.korber.inventory.service.factory.DefaultInventoryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultInventoryHandler
 * Tests the FIFO inventory reservation logic
 */
@DisplayName("Default Inventory Handler Unit Tests")
class DefaultInventoryHandlerTest {

    private DefaultInventoryHandler handler;
    private List<InventoryBatch> batches;

    @BeforeEach
    void setUp() {
        handler = new DefaultInventoryHandler();

        // Create test batches with different quantities and dates
        batches = new ArrayList<>();
        batches.add(new InventoryBatch(
                1L, 1001L, "Laptop", 10, LocalDate.of(2025, 12, 31)
        ));
        batches.add(new InventoryBatch(
                2L, 1001L, "Laptop", 20, LocalDate.of(2026, 3, 15)
        ));
        batches.add(new InventoryBatch(
                3L, 1001L, "Laptop", 30, LocalDate.of(2026, 6, 20)
        ));
    }

    @Test
    @DisplayName("Reserve inventory - Single batch")
    void reserveInventory_SingleBatch() {
        // Act
        List<Long> reservedIds = handler.reserveInventory(batches, 5);

        // Assert
        assertEquals(1, reservedIds.size());
        assertEquals(1L, reservedIds.get(0));
        assertEquals(5, batches.get(0).getQuantity()); // 10 - 5 = 5
        assertEquals(20, batches.get(1).getQuantity()); // Unchanged
    }

    @Test
    @DisplayName("Reserve inventory - Multiple batches (FIFO)")
    void reserveInventory_MultipleBatches() {
        // Act - Request 25 units (will use batch 1: 10 + batch 2: 15)
        List<Long> reservedIds = handler.reserveInventory(batches, 25);

        // Assert
        assertEquals(2, reservedIds.size());
        assertEquals(1L, reservedIds.get(0)); // First batch
        assertEquals(2L, reservedIds.get(1)); // Second batch
        assertEquals(0, batches.get(0).getQuantity()); // Fully consumed
        assertEquals(5, batches.get(1).getQuantity()); // 20 - 15 = 5
        assertEquals(30, batches.get(2).getQuantity()); // Unchanged
    }

    @Test
    @DisplayName("Reserve inventory - Exact quantity match")
    void reserveInventory_ExactMatch() {
        // Act - Request exactly 10 units (exact match with first batch)
        List<Long> reservedIds = handler.reserveInventory(batches, 10);

        // Assert
        assertEquals(1, reservedIds.size());
        assertEquals(1L, reservedIds.get(0));
        assertEquals(0, batches.get(0).getQuantity());
    }

    @Test
    @DisplayName("Reserve inventory - Insufficient stock throws exception")
    void reserveInventory_InsufficientStock() {
        // Act & Assert - Request 100 units (only 60 available)
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> handler.reserveInventory(batches, 100)
        );

        assertEquals("Insufficient inventory available", exception.getMessage());
    }

    @Test
    @DisplayName("Get batches sorted - Returns input list")
    void getBatchesSorted_ReturnsInput() {
        // Act
        List<InventoryBatch> sorted = handler.getBatchesSorted(batches);

        // Assert
        assertEquals(batches, sorted);
        assertEquals(3, sorted.size());
    }

    @Test
    @DisplayName("Reserve inventory - All batches consumed")
    void reserveInventory_AllBatchesConsumed() {
        // Act - Request all 60 units
        List<Long> reservedIds = handler.reserveInventory(batches, 60);

        // Assert
        assertEquals(3, reservedIds.size());
        assertEquals(0, batches.get(0).getQuantity());
        assertEquals(0, batches.get(1).getQuantity());
        assertEquals(0, batches.get(2).getQuantity());
    }
}

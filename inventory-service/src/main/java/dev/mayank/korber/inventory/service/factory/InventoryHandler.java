package dev.mayank.korber.inventory.service.factory;

import dev.mayank.korber.inventory.model.InventoryBatch;

import java.util.List;

/**
 * Interface for different inventory handling strategies
 * This allows extensibility for different inventory management approaches:
 * - FIFO (First In First Out)
 * - LIFO (Last In First Out)
 * - Priority-based
 * - Warehouse-specific
 */
public interface InventoryHandler {

    List<InventoryBatch> getBatchesSorted(List<InventoryBatch> batches);

    List<Long> reserveInventory(List<InventoryBatch> batches, Integer quantity);
}

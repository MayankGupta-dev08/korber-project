package dev.mayank.korber.inventory.service.factory;

import dev.mayank.korber.inventory.model.InventoryBatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default FIFO implementation - uses earliest expiry date first
 */
@Component
public class LIFOInventoryHandler implements InventoryHandler {
    @Override
    public List<InventoryBatch> getBatchesSorted(List<InventoryBatch> batches) {
        Collections.reverse(batches);   // already sorted by expiry date from repository query so reverse will be LIFO
        return batches;
    }

    @Override
    public List<Long> reserveInventory(List<InventoryBatch> batches, Integer quantityNeeded) {
        List<Long> reservedBatchIds = new ArrayList<>();
        int remainingQuantity = quantityNeeded;

        for (InventoryBatch inventoryBatch : batches) {
            if (remainingQuantity <= 0) break;

            if (inventoryBatch.getQuantity() > 0) {
                int quantityToReserve = Math.min(remainingQuantity, inventoryBatch.getQuantity());
                inventoryBatch.setQuantity(inventoryBatch.getQuantity() - quantityToReserve);
                remainingQuantity -= quantityToReserve;
                reservedBatchIds.add(inventoryBatch.getBatchId());
            }
        }

        if (remainingQuantity > 0) throw new IllegalStateException("Insufficient inventory available");
        return reservedBatchIds;
    }
}

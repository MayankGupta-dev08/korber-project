package dev.mayank.korber.inventory.service;

import dev.mayank.korber.inventory.dto.BatchDTO;
import dev.mayank.korber.inventory.dto.InventoryResponse;
import dev.mayank.korber.inventory.dto.UpdateInventoryResponse;
import dev.mayank.korber.inventory.model.InventoryBatch;
import dev.mayank.korber.inventory.repository.InventoryBatchRepository;
import dev.mayank.korber.inventory.service.factory.InventoryHandler;
import dev.mayank.korber.inventory.service.factory.InventoryHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryBatchRepository inventoryBatchRepository;
    private final InventoryHandlerFactory inventoryHandlerFactory;

    public InventoryResponse getInventoryByProductId(Long productId) {
        log.info("Fetching inventory for  product id {}", productId);
        List<InventoryBatch> batches = inventoryBatchRepository.findByProductIdOrderByExpiryDateAsc(productId);

        if (batches.isEmpty()) {
            log.error("No inventory found for  product id {}", productId);
            throw new IllegalArgumentException("No inventory found for  product id " + productId);
        }

        InventoryHandler inventoryHandler = inventoryHandlerFactory.getDefaultHandler();
        List<InventoryBatch> sortedBatches = inventoryHandler.getBatchesSorted(batches);

        InventoryResponse inventoryResponse = new InventoryResponse();
        inventoryResponse.setProductId(batches.get(0).getProductId());
        inventoryResponse.setProductName(batches.get(0).getProductName());
        inventoryResponse.setBatches(
                sortedBatches.stream().map(batch -> new BatchDTO(
                        batch.getBatchId(), batch.getQuantity(), batch.getExpiryDate()
                )).toList()
        );
        return inventoryResponse;
    }

    @Transactional
    public UpdateInventoryResponse updateInventory(Long productId, Integer quantity) {
        log.info("Updating inventory for  product id {} and quantity={}", productId, quantity);
        List<InventoryBatch> batches = inventoryBatchRepository.findByProductIdAndQuantityGreaterThanOrderByExpiryDateAsc(productId, quantity);

        if (batches.isEmpty()) {
            log.error("Product not found or out of stock: {}", productId);
            throw new IllegalArgumentException("Product not found or out of stock: " + productId);
        }

        int totalAvailableQuantity = batches.stream().mapToInt(InventoryBatch::getQuantity).sum();
        if (totalAvailableQuantity < quantity) {
            log.error("Insufficient inventory. Requested={} and Available={}", quantity, totalAvailableQuantity);
            throw new IllegalStateException(String.format("Insufficient inventory. Requested: %d, Available: %d", quantity, totalAvailableQuantity));
        }

        InventoryHandler inventoryHandler = inventoryHandlerFactory.getDefaultHandler();
        List<Long> reservedBatchIds = inventoryHandler.reserveInventory(batches, quantity);
        inventoryBatchRepository.saveAll(batches);  //save updated batches
        log.info("Successfully updated inventory from batches: {}", reservedBatchIds);

        UpdateInventoryResponse response = new UpdateInventoryResponse();
        response.setSuccess(Boolean.TRUE);
        response.setMessage("Successfully updated inventory");
        response.setReservedBatchIds(reservedBatchIds);
        return response;
    }
}

package dev.mayank.korber.inventory.controller;

import dev.mayank.korber.inventory.dto.InventoryResponse;
import dev.mayank.korber.inventory.dto.UpdateInventoryRequest;
import dev.mayank.korber.inventory.dto.UpdateInventoryResponse;
import dev.mayank.korber.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@SuppressWarnings("unused")
@Tag(name = "Inventory Management", description = "APIs for managing product inventory batches with FIFO strategy")
public class InventoryController {
    private final InventoryService inventoryService;

    @Operation(
            summary = "Get inventory batches by product ID",
            description = "Retrieves all inventory batches for a specific product, sorted by expiry date (FIFO - earliest first). " +
                    "This helps in implementing First-In-First-Out inventory management strategy."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved inventory batches",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = InventoryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found",
                    content = @Content
            )
    })
    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable Long productId) {
        try {
            InventoryResponse response = inventoryService.getInventoryByProductId(productId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Update inventory after order placement",
            description = "Reserves inventory from available batches using FIFO strategy. " +
                    "Automatically deducts quantity from batches with earliest expiry dates first. " +
                    "This endpoint is typically called by the Order Service when an order is placed."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Inventory successfully updated and reserved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateInventoryResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request - Product not found or invalid quantity",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Insufficient inventory available",
                    content = @Content
            )
    })
    @PostMapping("/update")
    public ResponseEntity<UpdateInventoryResponse> updateInventory(@RequestBody UpdateInventoryRequest inventoryRequest) {
        try {
            UpdateInventoryResponse response = inventoryService.updateInventory(inventoryRequest.getProductId(), inventoryRequest.getQuantity());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }
}

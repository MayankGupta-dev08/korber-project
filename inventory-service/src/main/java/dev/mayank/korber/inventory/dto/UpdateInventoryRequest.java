package dev.mayank.korber.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update/reserve inventory for a product")
public class UpdateInventoryRequest {
    @Schema(description = "Product identifier for which inventory needs to be updated", example = "1002")
    private Long productId;

    @Schema(description = "Quantity to reserve from inventory", example = "3", minimum = "1")
    private Integer quantity;
}

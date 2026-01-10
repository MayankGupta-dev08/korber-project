package dev.mayank.korber.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing inventory information for a product")
public class InventoryResponse {
    @Schema(description = "Product identifier", example = "1005")
    private Long productId;

    @Schema(description = "Name of the product", example = "Smartwatch")
    private String productName;

    @Schema(description = "List of inventory batches sorted by expiry date (earliest first)")
    private List<BatchDTO> batches;
}

package dev.mayank.korber.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Inventory batch information containing batch details")
public class BatchDTO {
    @Schema(description = "Unique identifier for the batch", example = "1")
    private Long  batchId;

    @Schema(description = "Available quantity in this batch", example = "50")
    private Integer quantity;

    @Schema(description = "Expiry date of the batch", example = "2026-06-25")
    private LocalDate expiryDate;
}

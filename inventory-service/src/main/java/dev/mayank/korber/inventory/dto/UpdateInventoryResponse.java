package dev.mayank.korber.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response after updating/reserving inventory")
public class UpdateInventoryResponse {
    @Schema(description = "Indicates if the operation was successful", example = "true")
    private Boolean success;

    @Schema(description = "Descriptive message about the operation", example = "Inventory updated successfully")
    private String message;

    @Schema(description = "List of batch IDs from which inventory was reserved", example = "[9, 10]")
    private List<Long> reservedBatchIds;
}

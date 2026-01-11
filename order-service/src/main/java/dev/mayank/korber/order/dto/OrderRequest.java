package dev.mayank.korber.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to place a new order")
public class OrderRequest {
    @Schema(description = "Product identifier to order", example = "1002", required = true)
    private Long productId;

    @Schema(description = "Quantity to order", example = "3", required = true, minimum = "1")
    private Integer quantity;
}

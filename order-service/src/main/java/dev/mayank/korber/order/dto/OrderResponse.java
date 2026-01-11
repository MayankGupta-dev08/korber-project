package dev.mayank.korber.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response after placing an order")
public class OrderResponse {
    @Schema(description = "Generated order identifier", example = "11")
    private Long orderId;

    @Schema(description = "Product identifier that was ordered", example = "1002")
    private Long productId;

    @Schema(description = "Name of the product", example = "Smartphone")
    private String productName;

    @Schema(description = "Quantity ordered", example = "3")
    private Integer quantity;

    @Schema(description = "Current order status", example = "PLACED", allowableValues = {"PLACED", "SHIPPED", "DELIVERED"})
    private String status;

    @Schema(description = "List of inventory batch IDs from which stock was reserved")
    private List<Long> reservedFromBatchIds;

    @Schema(description = "Confirmation message", example = "Order placed. Inventory reserved.")
    private String message;
}

package dev.mayank.korber.order.controller;

import dev.mayank.korber.order.dto.OrderRequest;
import dev.mayank.korber.order.dto.OrderResponse;
import dev.mayank.korber.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@SuppressWarnings("unused")
@Tag(name = "Order Management", description = "APIs for managing customer orders and inventory reservation")
public class OrderController {
    private final OrderService orderService;

    @Operation(
            summary = "Place a new order",
            description = "Creates a new order and reserves inventory from available batches. " +
                    "This endpoint communicates with the Inventory Service to: " +
                    "1. Check product availability " +
                    "2. Reserve inventory using FIFO strategy (earliest expiry first) " +
                    "3. Create the order record if inventory is successfully reserved. " +
                    "The order will fail if insufficient inventory is available."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Order placed successfully and inventory reserved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OrderResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found in inventory",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "Insufficient inventory available or inventory service unavailable",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content
            )
    })
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Order details including product ID and quantity to order",
                    required = true,
                    content = @Content(schema = @Schema(implementation = OrderRequest.class))
            )
            @RequestBody OrderRequest orderRequest) {
        log.info("POST /order - Placing order for product: {}, quantity: {}", orderRequest.getProductId(), orderRequest.getQuantity());
        try {
            OrderResponse response = orderService.placeOrder(orderRequest);
            log.info("Order placed successfully: orderId={}", response.getOrderId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Product not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            log.error("Cannot place order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception e) {
            log.error("Unexpected error while placing order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

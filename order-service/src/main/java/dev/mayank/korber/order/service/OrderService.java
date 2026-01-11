package dev.mayank.korber.order.service;

import dev.mayank.korber.order.client.InventoryClient;
import dev.mayank.korber.order.dto.InventoryResponse;
import dev.mayank.korber.order.dto.InventoryUpdateResponse;
import dev.mayank.korber.order.dto.OrderRequest;
import dev.mayank.korber.order.dto.OrderResponse;
import dev.mayank.korber.order.model.Order;
import dev.mayank.korber.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    @Transactional
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        log.info("Processing order: productId={}, quantity={}", orderRequest.getProductId(), orderRequest.getQuantity());
        // Step 1: Check inventory availability
        InventoryResponse inventoryResponse;
        try {
            inventoryResponse = inventoryClient.getInventory(orderRequest.getProductId());
        } catch (RestClientException e) {
            log.error("Failed to check inventory", e);
            throw new IllegalStateException("Inventory service unavailable");
        }

        if (inventoryResponse == null) {
            throw new IllegalArgumentException("Product not found: " + orderRequest.getProductId());
        }

        // Calculate total available quantity
        int totalAvailable = inventoryResponse.getBatches().stream().mapToInt(InventoryResponse.BatchInfo::getQuantity).sum();
        if (totalAvailable < orderRequest.getQuantity()) {
            throw new IllegalStateException(
                    String.format("Insufficient inventory. Requested: %d, Available: %d", orderRequest.getQuantity(), totalAvailable)
            );
        }

        // Step 2: Reserve inventory
        InventoryUpdateResponse updateResponse;
        try {
            updateResponse = inventoryClient.updateInventory(orderRequest.getProductId(), orderRequest.getQuantity());
        } catch (RestClientException e) {
            log.error("Failed to update inventory", e);
            throw new IllegalStateException("Failed to reserve inventory");
        }

        if (updateResponse == null || !updateResponse.getSuccess())
            throw new IllegalStateException("Failed to reserve inventory");

        // Step 3: Create order
        Order order = new Order();
        order.setProductId(orderRequest.getProductId());
        order.setProductName(inventoryResponse.getProductName());
        order.setQuantity(orderRequest.getQuantity());
        order.setOrderStatus(Order.OrderStatus.PLACED);
        order.setOrderDate(LocalDate.now());

        Order savedOrder = orderRepository.save(order);

        // Step 4: Build response
        OrderResponse response = new OrderResponse();
        response.setOrderId(savedOrder.getOrderId());
        response.setProductId(savedOrder.getProductId());
        response.setProductName(savedOrder.getProductName());
        response.setQuantity(savedOrder.getQuantity());
        response.setStatus(savedOrder.getOrderStatus().name());
        response.setReservedFromBatchIds(updateResponse.getReservedBatchIds());
        response.setMessage("Order placed. Inventory reserved.");

        log.info("Order placed successfully: orderId={}", savedOrder.getOrderId());
        return response;
    }
}

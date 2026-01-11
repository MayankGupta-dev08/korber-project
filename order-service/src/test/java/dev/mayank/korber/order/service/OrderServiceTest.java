package dev.mayank.korber.order.service;

import dev.mayank.korber.order.client.InventoryClient;
import dev.mayank.korber.order.dto.InventoryResponse;
import dev.mayank.korber.order.dto.InventoryUpdateResponse;
import dev.mayank.korber.order.dto.OrderRequest;
import dev.mayank.korber.order.dto.OrderResponse;
import dev.mayank.korber.order.model.Order;
import dev.mayank.korber.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest orderRequest;
    private InventoryResponse inventoryResponse;
    private InventoryUpdateResponse updateResponse;

    @BeforeEach
    void setUp() {
        orderRequest = new OrderRequest(1002L, 3);

        List<InventoryResponse.BatchInfo> batches = Arrays.asList(
                new InventoryResponse.BatchInfo(9L, 29, "2026-05-31"),
                new InventoryResponse.BatchInfo(10L, 83, "2026-11-15")
        );

        inventoryResponse = new InventoryResponse(1002L, "Smartphone", batches);

        updateResponse = new InventoryUpdateResponse(
                true,
                "Inventory updated",
                Arrays.asList(9L)
        );
    }

    @Test
    void placeOrder_Success() {
        // Arrange
        when(inventoryClient.getInventory(1002L)).thenReturn(inventoryResponse);
        when(inventoryClient.updateInventory(1002L, 3)).thenReturn(updateResponse);

        Order savedOrder = new Order();
        savedOrder.setOrderId(11L);
        savedOrder.setProductId(1002L);
        savedOrder.setProductName("Smartphone");
        savedOrder.setQuantity(3);
        savedOrder.setOrderStatus(Order.OrderStatus.PLACED);

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse response = orderService.placeOrder(orderRequest);

        // Assert
        assertNotNull(response);
        assertEquals(11L, response.getOrderId());
        assertEquals(1002L, response.getProductId());
        assertEquals("Smartphone", response.getProductName());
        assertEquals(3, response.getQuantity());
        assertEquals("PLACED", response.getStatus());
        assertEquals(Arrays.asList(9L), response.getReservedFromBatchIds());
        assertEquals("Order placed. Inventory reserved.", response.getMessage());

        verify(inventoryClient).getInventory(1002L);
        verify(inventoryClient).updateInventory(1002L, 3);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void placeOrder_InsufficientInventory() {
        // Arrange
        orderRequest.setQuantity(200);
        when(inventoryClient.getInventory(1002L)).thenReturn(inventoryResponse);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder(orderRequest);
        });

        verify(inventoryClient).getInventory(1002L);
        verify(inventoryClient, never()).updateInventory(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_ProductNotFound() {
        // Arrange
        when(inventoryClient.getInventory(9999L)).thenReturn(null);
        orderRequest.setProductId(9999L);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.placeOrder(orderRequest);
        });

        verify(inventoryClient).getInventory(9999L);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_InventoryServiceUnavailable() {
        // Arrange
        when(inventoryClient.getInventory(1002L))
                .thenThrow(new RestClientException("Service unavailable"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder(orderRequest);
        });

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_InventoryUpdateFails() {
        // Arrange
        when(inventoryClient.getInventory(1002L)).thenReturn(inventoryResponse);
        when(inventoryClient.updateInventory(1002L, 3))
                .thenThrow(new RestClientException("Update failed"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            orderService.placeOrder(orderRequest);
        });

        verify(inventoryClient).getInventory(1002L);
        verify(inventoryClient).updateInventory(1002L, 3);
        verify(orderRepository, never()).save(any());
    }
}

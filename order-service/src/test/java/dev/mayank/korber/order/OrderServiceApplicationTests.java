package dev.mayank.korber.order;

import dev.mayank.korber.order.client.InventoryClient;
import dev.mayank.korber.order.dto.InventoryResponse;
import dev.mayank.korber.order.dto.InventoryUpdateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryClient inventoryClient;

    @Test
    void placeOrder_Success() throws Exception {
        // Mock inventory responses
        List<InventoryResponse.BatchInfo> batches = Arrays.asList(
                new InventoryResponse.BatchInfo(9L, 29, "2026-05-31")
        );
        InventoryResponse inventoryResponse = new InventoryResponse(
                1002L, "Smartphone", batches
        );

        InventoryUpdateResponse updateResponse = new InventoryUpdateResponse(
                true,
                "Updated",
                Arrays.asList(9L)
        );

        when(inventoryClient.getInventory(1002L)).thenReturn(inventoryResponse);
        when(inventoryClient.updateInventory(1002L, 3)).thenReturn(updateResponse);

        String requestBody = """
                {
                    "productId": 1002,
                    "quantity": 3
                }
                """;

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.productId").value(1002))
                .andExpect(jsonPath("$.productName").value("Smartphone"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.reservedFromBatchIds").isArray())
                .andExpect(jsonPath("$.message").value("Order placed. Inventory reserved."));

        verify(inventoryClient).getInventory(1002L);
        verify(inventoryClient).updateInventory(1002L, 3);
    }

    @Test
    void placeOrder_InsufficientStock() throws Exception {
        List<InventoryResponse.BatchInfo> batches = Arrays.asList(
                new InventoryResponse.BatchInfo(9L, 5, "2026-05-31")
        );
        InventoryResponse inventoryResponse = new InventoryResponse(
                1002L, "Smartphone", batches
        );

        when(inventoryClient.getInventory(1002L)).thenReturn(inventoryResponse);

        String requestBody = """
                {
                    "productId": 1002,
                    "quantity": 100
                }
                """;

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity());

        verify(inventoryClient).getInventory(1002L);
        verify(inventoryClient, never()).updateInventory(anyLong(), anyInt());
    }

    @Test
    void placeOrder_ProductNotFound() throws Exception {
        when(inventoryClient.getInventory(9999L)).thenReturn(null);

        String requestBody = """
                {
                    "productId": 9999,
                    "quantity": 3
                }
                """;

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }
}

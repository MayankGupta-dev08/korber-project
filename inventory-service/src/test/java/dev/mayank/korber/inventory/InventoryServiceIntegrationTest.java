package dev.mayank.korber.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Inventory Service
 * Tests the entire stack: Controller → Service → Repository → Database
 * Uses real H2 database with Liquibase data
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Inventory Service Integration Tests")
class InventoryServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== GET /inventory/{productId} Tests ====================

    @Test
    @DisplayName("GET /inventory/{productId} - Success with multiple batches")
    void getInventory_Success() throws Exception {
        mockMvc.perform(get("/inventory/1001"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.productId").value(1001))
                .andExpect(jsonPath("$.productName").value("Laptop"))
                .andExpect(jsonPath("$.batches").isArray())
                .andExpect(jsonPath("$.batches", hasSize(1)))
                .andExpect(jsonPath("$.batches[0].batchId").value(1))
                .andExpect(jsonPath("$.batches[0].quantity").value(68))
                .andExpect(jsonPath("$.batches[0].expiryDate").value("2026-06-25"));
    }

    @Test
    @DisplayName("GET /inventory/{productId} - Multiple batches sorted by expiry")
    void getInventory_MultipleBatches_SortedByExpiry() throws Exception {
        // Product 1005 (Smartwatch) has 3 batches
        mockMvc.perform(get("/inventory/1005"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1005))
                .andExpect(jsonPath("$.productName").value("Smartwatch"))
                .andExpect(jsonPath("$.batches", hasSize(3)))
                // Verify sorted by expiry date (ascending)
                .andExpect(jsonPath("$.batches[0].expiryDate").value("2026-03-31"))
                .andExpect(jsonPath("$.batches[1].expiryDate").value("2026-04-24"))
                .andExpect(jsonPath("$.batches[2].expiryDate").value("2026-05-30"));
    }

    @Test
    @DisplayName("GET /inventory/{productId} - Product not found")
    void getInventory_ProductNotFound() throws Exception {
        mockMvc.perform(get("/inventory/9999"))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /inventory/{productId} - Verify all products exist")
    void getInventory_AllProductsExist() throws Exception {
        // Test all products from CSV data
        Long[] productIds = {1001L, 1002L, 1003L, 1004L, 1005L};

        for (Long productId : productIds) {
            mockMvc.perform(get("/inventory/" + productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId))
                    .andExpect(jsonPath("$.productName").exists())
                    .andExpect(jsonPath("$.batches").isArray())
                    .andExpect(jsonPath("$.batches", hasSize(greaterThan(0))));
        }
    }

    // ==================== POST /inventory/update Tests ====================

    @Test
    @Transactional
    @DisplayName("POST /inventory/update - Success with single batch")
    void updateInventory_Success_SingleBatch() throws Exception {
        String requestBody = """
                {
                    "productId": 1002,
                    "quantity": 5
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Inventory updated successfully"))
                .andExpect(jsonPath("$.reservedBatchIds").isArray())
                .andExpect(jsonPath("$.reservedBatchIds", hasSize(1)))
                .andExpect(jsonPath("$.reservedBatchIds[0]").value(9));

        // Verify inventory was actually reduced
        mockMvc.perform(get("/inventory/1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches[0].quantity").value(24)); // 29 - 5 = 24
    }

    @Test
    @Transactional
    @DisplayName("POST /inventory/update - Success with multiple batches (FIFO)")
    void updateInventory_Success_MultipleBatches() throws Exception {
        // Product 1005 has batches: 39 (2026-03-31), 40 (2026-04-24), 52 (2026-05-30)
        String requestBody = """
                {
                    "productId": 1005,
                    "quantity": 50
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reservedBatchIds").isArray())
                .andExpect(jsonPath("$.reservedBatchIds", hasSize(2))) // Should use 2 batches
                .andExpect(jsonPath("$.reservedBatchIds[0]").value(5)) // First batch (earliest expiry)
                .andExpect(jsonPath("$.reservedBatchIds[1]").value(7)); // Second batch

        // Verify FIFO: first batch depleted, second batch partially used
        mockMvc.perform(get("/inventory/1005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches[0].quantity").value(0))  // 39 - 39 = 0
                .andExpect(jsonPath("$.batches[1].quantity").value(29)) // 40 - 11 = 29
                .andExpect(jsonPath("$.batches[2].quantity").value(52)); // Unchanged
    }

    @Test
    @Transactional
    @DisplayName("POST /inventory/update - Insufficient stock")
    void updateInventory_InsufficientStock() throws Exception {
        String requestBody = """
                {
                    "productId": 1002,
                    "quantity": 500
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /inventory/update - Product not found")
    void updateInventory_ProductNotFound() throws Exception {
        String requestBody = """
                {
                    "productId": 9999,
                    "quantity": 10
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /inventory/update - Invalid JSON")
    void updateInventory_InvalidJson() throws Exception {
        String requestBody = """
                {
                    "productId": "invalid",
                    "quantity": "not a number"
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    @DisplayName("POST /inventory/update - Zero quantity")
    void updateInventory_ZeroQuantity() throws Exception {
        String requestBody = """
                {
                    "productId": 1002,
                    "quantity": 0
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reservedBatchIds").isEmpty());
    }

    // ==================== Business Logic Tests ====================

    @Test
    @Transactional
    @DisplayName("Verify FIFO logic - Oldest expiry consumed first")
    void verifyFifoLogic() throws Exception {
        // Get initial state
        MvcResult initialResult = mockMvc.perform(get("/inventory/1005"))
                .andExpect(status().isOk())
                .andReturn();

        // Update inventory
        String requestBody = """
                {
                    "productId": 1005,
                    "quantity": 30
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedBatchIds[0]").value(5)); // Batch with earliest expiry

        // Verify first batch was consumed first
        mockMvc.perform(get("/inventory/1005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches[0].quantity").value(9)) // 39 - 30 = 9
                .andExpect(jsonPath("$.batches[1].quantity").value(40)) // Unchanged
                .andExpect(jsonPath("$.batches[2].quantity").value(52)); // Unchanged
    }

    @Test
    @Transactional
    @DisplayName("Sequential updates reduce inventory correctly")
    void sequentialUpdates() throws Exception {
        // First update
        String request1 = """
                {
                    "productId": 1003,
                    "quantity": 10
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request1))
                .andExpect(status().isOk());

        // Second update
        String request2 = """
                {
                    "productId": 1003,
                    "quantity": 15
                }
                """;

        mockMvc.perform(post("/inventory/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request2))
                .andExpect(status().isOk());

        // Verify total reduction (10 + 15 = 25)
        mockMvc.perform(get("/inventory/1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches[0].quantity").value(10)) // 35 - 25 = 10
                .andExpect(jsonPath("$.batches[1].quantity").value(21)); // Unchanged
    }
}

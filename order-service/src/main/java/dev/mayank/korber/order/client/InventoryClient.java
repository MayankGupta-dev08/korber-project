package dev.mayank.korber.order.client;

import dev.mayank.korber.order.dto.InventoryResponse;
import dev.mayank.korber.order.dto.InventoryUpdateRequest;
import dev.mayank.korber.order.dto.InventoryUpdateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryClient {
    private final RestTemplate restTemplate;

    @Value("${inventory.service.url:http://localhost:8081}")
    private String inventoryServiceUrl;

    public InventoryResponse getInventory(Long productId) {
        String url = inventoryServiceUrl + "/inventory/" + productId;
        log.info("Calling Inventory Service: {}", url);
        ResponseEntity<InventoryResponse> response = restTemplate.getForEntity(url, InventoryResponse.class);
        return response.getBody();
    }

    public InventoryUpdateResponse updateInventory(Long productId, Integer quantity) {
        String url = inventoryServiceUrl + "/inventory/update";
        log.info("Updating inventory: productId={}, quantity={}", productId, quantity);
        InventoryUpdateRequest request = new InventoryUpdateRequest(productId, quantity);
        ResponseEntity<InventoryUpdateResponse> response = restTemplate.postForEntity(url, request, InventoryUpdateResponse.class);
        return response.getBody();
    }
}


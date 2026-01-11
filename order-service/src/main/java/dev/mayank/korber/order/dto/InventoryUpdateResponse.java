package dev.mayank.korber.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUpdateResponse {
    private Boolean success;
    private String message;
    private List<Long> reservedBatchIds;
}

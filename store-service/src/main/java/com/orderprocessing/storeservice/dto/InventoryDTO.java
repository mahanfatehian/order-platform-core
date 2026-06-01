package com.orderprocessing.storeservice.dto;

import java.util.UUID;
import java.time.Instant;

import lombok.Data;

@Data
public class InventoryDTO {
    private UUID productId;
    private int quantity;
    private int reservedQuantity;
    private Instant lastUpdated;
}
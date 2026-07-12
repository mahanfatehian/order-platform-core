package com.orderprocessing.webui.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryView(
        UUID productId,
        String productName,
        String sku,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant lastUpdated
) { }

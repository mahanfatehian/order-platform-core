package com.orderprocessing.storeservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Data Transfer Object representing inventory stock levels for a product")
public class InventoryDTO {

    @Schema(description = "Unique identifier of the product", example = "a1111111-1111-1111-1111-111111111111")
    private UUID productId;

    private String productName;

    private String sku;

    @Schema(description = "Total available quantity in stock", example = "100")
    private int quantity;

    @Schema(description = "Quantity currently reserved by pending orders", example = "10")
    private int reservedQuantity;

    @Schema(description = "Quantity currently available to new orders", example = "90")
    private int availableQuantity;

    @Schema(description = "Timestamp of the last inventory update")
    private Instant lastUpdated;
}

package com.orderprocessing.storeservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Data Transfer Object representing a Product in the store catalog")
public class ProductDTO {

    @Schema(description = "Unique identifier of the product", example = "a1111111-1111-1111-1111-111111111111")
    private UUID id;

    @Schema(description = "Name of the product", example = "Wireless Headphones")
    private String name;

    @Schema(description = "Detailed description of the product", example = "Noise-cancelling over-ear headphones")
    private String description;

    @Schema(description = "Unique stock keeping unit", example = "ELEC-HEADPHONE-001")
    private String sku;

    @Schema(description = "Price of the product", example = "149.99")
    private BigDecimal price;

    @Schema(description = "Category of the product", example = "ELECTRONICS", allowableValues = {"ELECTRONICS", "CLOTHING", "HOME", "BOOKS", "OTHER"})
    private String category;

    private boolean active;

    private int availableQuantity;

    @Schema(description = "Timestamp when the product was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the product was last updated")
    private Instant updatedAt;
}

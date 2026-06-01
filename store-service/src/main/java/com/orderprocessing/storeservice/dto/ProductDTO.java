package com.orderprocessing.storeservice.dto;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductDTO {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;
}
package com.orderprocessing.storeservice.dto;

import java.util.UUID;
import java.time.Instant;

import lombok.Data;

@Data
public class ProductDTO {
    private UUID id;
    private String name;
    private String description;
    private double price;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;
}
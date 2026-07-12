package com.orderprocessing.storeservice.dto;

import com.orderprocessing.storeservice.model.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 4000)
    private String description;

    @Size(max = 100)
    private String sku;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull(message = "Category is required")
    private Product.Category category;
}

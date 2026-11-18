package com.orderprocessing.storeservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

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
    private UUID categoryId;

    /**
     * Nullable on purpose: a caller that does not mention the flag leaves it to the service, which treats a new
     * product as active and leaves an existing one as it was. Active is rejected until a picture is stored.
     */
    private Boolean active;
}

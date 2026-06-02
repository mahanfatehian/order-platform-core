package com.orderprocessing.storeservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Request payload for updating, reserving, or releasing inventory quantities")
public class UpdateInventoryDTO {

    @Min(value = 0, message = "Quantity cannot be negative")
    @Schema(description = "Quantity to add, reserve, or release", example = "5")
    private int quantity;
}
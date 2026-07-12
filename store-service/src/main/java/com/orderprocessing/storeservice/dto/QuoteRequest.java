package com.orderprocessing.storeservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuoteRequest(
        @NotEmpty(message = "At least one item is required")
        @Size(max = 100, message = "A quote cannot contain more than 100 products")
        List<@Valid QuoteItemRequest> items
) {
}

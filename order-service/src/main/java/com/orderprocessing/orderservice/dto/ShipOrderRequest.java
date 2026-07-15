package com.orderprocessing.orderservice.dto;

import jakarta.validation.constraints.Size;

/** Optional shipment details supplied by a delivery operator. */
public record ShipOrderRequest(
        @Size(max = 100, message = "Tracking reference cannot exceed 100 characters")
        String trackingReference) {
}

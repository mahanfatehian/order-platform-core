package com.orderprocessing.storeservice.dto;

import jakarta.validation.constraints.NotNull;

public record ActivationRequest(@NotNull(message = "Active state is required") Boolean active) {
}

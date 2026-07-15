package com.orderprocessing.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(description = "Immutable audit entry for one order status transition")
public record OrderStatusHistoryResponse(
        UUID id,
        UUID orderId,
        UUID eventId,
        String fromStatus,
        String toStatus,
        UUID actorUserId,
        String actorRole,
        String reason,
        String correlationId,
        Instant occurredAt,
        Instant recordedAt) {
}

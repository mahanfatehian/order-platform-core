package com.orderprocessing.webui.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderHistoryView(
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
        Instant recordedAt
) { }

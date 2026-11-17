package com.orderprocessing.webui.dto;

import java.time.Instant;
import java.util.UUID;

public record WalletEntryView(
        UUID id,
        String type,
        long amount,
        String currency,
        String reason,
        String actorRole,
        Instant createdAt
) {
}

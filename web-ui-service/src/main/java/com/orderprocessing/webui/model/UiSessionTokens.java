package com.orderprocessing.webui.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record UiSessionTokens(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt
) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public boolean accessExpiresWithin(Instant threshold) {
        return accessExpiresAt == null || !accessExpiresAt.isAfter(threshold);
    }
}

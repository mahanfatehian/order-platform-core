package com.orderprocessing.webui.captcha;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;

/**
 * A single issued captcha challenge. Instances live in the Redis-backed HTTP session, so the type has to stay
 * serializable and free of framework references.
 */
public record CaptchaChallenge(String answer, Instant expiresAt) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Answers are compared case-insensitively: the rendered alphabet is upper case only, and forcing readers to
     * match the casing adds friction without adding protection.
     */
    public boolean matches(String candidate) {
        if (candidate == null) return false;
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        return !normalized.isEmpty() && answer.equals(normalized);
    }
}

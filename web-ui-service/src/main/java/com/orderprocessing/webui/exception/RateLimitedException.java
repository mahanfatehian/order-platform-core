package com.orderprocessing.webui.exception;

import java.time.Duration;

/** Raised when a caller exceeds the request ceiling for an unauthenticated endpoint. */
public class RateLimitedException extends RuntimeException {
    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super("Too many requests from this address");
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return Math.max(1L, retryAfter.toSeconds());
    }
}

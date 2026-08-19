package com.orderprocessing.webui.support;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the caller address once, so every abuse control keys off the same value. */
public final class ClientAddress {
    private ClientAddress() { }

    /**
     * {@code server.forward-headers-strategy: framework} already applies the trusted proxy headers, so the
     * remote address is the real client. Reading {@code X-Forwarded-For} here as well would let any caller
     * choose its own key by forging the header.
     */
    public static String of(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}

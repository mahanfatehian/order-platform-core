package com.orderprocessing.security.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        String timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            String correlationId
    ) {
        return new ApiErrorResponse(java.time.Instant.now().toString(), status, code, message, path, correlationId, Map.of());
    }

    public static ApiErrorResponse validation(
            String message,
            String path,
            String correlationId,
            Map<String, String> fieldErrors
    ) {
        return new ApiErrorResponse(java.time.Instant.now().toString(), 400, "VALIDATION_ERROR", message, path,
                correlationId, Map.copyOf(fieldErrors));
    }
}

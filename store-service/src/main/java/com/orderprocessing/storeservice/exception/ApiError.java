package com.orderprocessing.storeservice.exception;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors
) {
}

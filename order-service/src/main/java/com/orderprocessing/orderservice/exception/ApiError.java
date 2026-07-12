package com.orderprocessing.orderservice.exception;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(OffsetDateTime timestamp, int status, String code, String message, String path,
                       String correlationId, Map<String, String> fieldErrors) {
}

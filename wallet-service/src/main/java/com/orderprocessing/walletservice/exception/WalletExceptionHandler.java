package com.orderprocessing.walletservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class WalletExceptionHandler {

    @ExceptionHandler(WalletException.class)
    public ResponseEntity<Map<String, Object>> handle(WalletException exception, HttpServletRequest request) {
        return body(exception.getStatus().value(), exception.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> forbidden(AuthorizationDeniedException exception, HttpServletRequest request) {
        return body(403, "FORBIDDEN", "You do not have permission to perform this operation", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception,
                                                                HttpServletRequest request) {
        return body(400, "VALIDATION_ERROR", "Request is not valid", request);
    }

    private ResponseEntity<Map<String, Object>> body(int status, String code, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status);
        body.put("code", code);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        String correlationId = request.getHeader("X-Correlation-Id");
        body.put("correlationId", correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId);
        body.put("fieldErrors", Map.of());
        return ResponseEntity.status(status).body(body);
    }
}

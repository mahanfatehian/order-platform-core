package com.orderprocessing.authservice.exception;

import com.orderprocessing.security.web.ApiErrorResponse;
import com.orderprocessing.security.web.CorrelationId;
import feign.FeignException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({AuthenticationFailedException.class, JwtException.class})
    public ResponseEntity<ApiErrorResponse> handleAuthentication(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Invalid credentials or token", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(
                "Request validation failed",
                request.getRequestURI(),
                CorrelationId.resolve(request),
                errors
        ));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", request);
    }

    @ExceptionHandler({ServiceUnavailableException.class, DataAccessException.class, FeignException.class})
    public ResponseEntity<ApiErrorResponse> handleUnavailable(Exception exception, HttpServletRequest request) {
        String correlationId = CorrelationId.resolve(request);
        log.warn("Authentication dependency unavailable correlationId={} type={}",
                correlationId, exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiErrorResponse.of(
                503,
                "SERVICE_UNAVAILABLE",
                "Authentication service is temporarily unavailable",
                request.getRequestURI(),
                correlationId
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception, HttpServletRequest request) {
        String correlationId = CorrelationId.resolve(request);
        log.error("Unexpected auth-service failure correlationId={}", correlationId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                500,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                correlationId
        ));
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status.value(), code, message, request.getRequestURI(), CorrelationId.resolve(request)));
    }
}

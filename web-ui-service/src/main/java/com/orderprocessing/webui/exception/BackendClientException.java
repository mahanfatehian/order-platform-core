package com.orderprocessing.webui.exception;

import org.springframework.http.HttpStatusCode;

import java.util.Map;

public class BackendClientException extends RuntimeException {
    private final HttpStatusCode status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public BackendClientException(HttpStatusCode status, String code, String message, Map<String, String> fieldErrors) {
        super(message == null || message.isBlank() ? "The requested service could not complete the operation" : message);
        this.status = status;
        this.code = code == null ? "BACKEND_ERROR" : code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public HttpStatusCode getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}

package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends DomainException {
    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    /** Keeps the upstream failure attached; the response still carries only the generic message. */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
        initCause(cause);
    }
}

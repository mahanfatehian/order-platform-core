package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends DomainException {
    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }
}

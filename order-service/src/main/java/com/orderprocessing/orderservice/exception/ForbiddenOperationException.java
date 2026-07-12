package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends DomainException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN_OPERATION", message);
    }
}

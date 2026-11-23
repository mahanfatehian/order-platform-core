package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class ReturnNotAllowedException extends DomainException {
    public ReturnNotAllowedException(String message) {
        super(HttpStatus.CONFLICT, "RETURN_NOT_ALLOWED", message);
    }
}

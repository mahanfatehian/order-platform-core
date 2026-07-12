package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class InvalidOrderStateException extends DomainException {
    public InvalidOrderStateException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_ORDER_STATE", message);
    }
}

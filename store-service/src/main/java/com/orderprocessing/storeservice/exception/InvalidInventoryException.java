package com.orderprocessing.storeservice.exception;

import org.springframework.http.HttpStatus;

public class InvalidInventoryException extends DomainException {
    public InvalidInventoryException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_INVENTORY", message);
    }
}

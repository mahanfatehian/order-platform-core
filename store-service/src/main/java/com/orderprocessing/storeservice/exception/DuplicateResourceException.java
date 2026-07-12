package com.orderprocessing.storeservice.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends DomainException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", message);
    }
}

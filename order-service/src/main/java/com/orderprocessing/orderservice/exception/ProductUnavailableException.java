package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class ProductUnavailableException extends DomainException {
    public ProductUnavailableException(String message) {
        super(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", message);
    }
}

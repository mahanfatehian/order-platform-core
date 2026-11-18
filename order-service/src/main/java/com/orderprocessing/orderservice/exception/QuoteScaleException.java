package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class QuoteScaleException extends DomainException {
    public QuoteScaleException(String message) {
        super(HttpStatus.BAD_REQUEST, "SCALE_MISMATCH", message);
    }
}

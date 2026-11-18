package com.orderprocessing.storeservice.exception;

import org.springframework.http.HttpStatus;

public class CatalogRuleException extends DomainException {
    public CatalogRuleException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}

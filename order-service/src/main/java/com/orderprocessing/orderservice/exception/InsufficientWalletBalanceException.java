package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class InsufficientWalletBalanceException extends DomainException {
    public InsufficientWalletBalanceException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}

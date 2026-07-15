package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

/** A replay attempted to change data that was fixed by the original command. */
public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String message) {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", message);
    }
}

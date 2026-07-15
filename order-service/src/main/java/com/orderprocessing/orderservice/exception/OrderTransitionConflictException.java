package com.orderprocessing.orderservice.exception;

import com.orderprocessing.orderservice.model.Order;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** A human command was valid, but not for the order's current state. */
public class OrderTransitionConflictException extends DomainException {
    public OrderTransitionConflictException(UUID orderId,
                                            String action,
                                            Order.Status expected,
                                            Order.Status actual) {
        super(HttpStatus.CONFLICT, "ORDER_TRANSITION_CONFLICT",
                "Cannot " + action + " order " + orderId + ": expected status " + expected
                        + " but current status is " + actual);
    }
}

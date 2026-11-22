package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class BlankTrackingReferenceException extends DomainException {
    public BlankTrackingReferenceException() {
        super(HttpStatus.BAD_REQUEST, "TRACKING_REFERENCE_REQUIRED",
                "Ship requires a tracking reference");
    }
}

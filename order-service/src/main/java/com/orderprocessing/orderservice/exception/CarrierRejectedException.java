package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class CarrierRejectedException extends DomainException {
    public CarrierRejectedException() {
        super(HttpStatus.BAD_REQUEST, "CARRIER_REJECTED",
                "The carrier did not accept this tracking reference");
    }
}

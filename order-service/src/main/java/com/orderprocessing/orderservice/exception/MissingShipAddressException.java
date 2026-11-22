package com.orderprocessing.orderservice.exception;

import org.springframework.http.HttpStatus;

public class MissingShipAddressException extends DomainException {
    public MissingShipAddressException() {
        super(HttpStatus.CONFLICT, "SHIP_ADDRESS_REQUIRED",
                "Ship requires the address stored on the order");
    }
}

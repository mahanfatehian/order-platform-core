package com.orderprocessing.orderservice.carrier;

import java.util.UUID;

public class FakeCarrier implements CarrierPort {
    private boolean reject;

    public void rejectNext() {
        reject = true;
    }

    @Override
    public String accept(UUID orderId, String trackingReference) {
        if (reject || trackingReference == null || trackingReference.isBlank()) {
            return null;
        }
        return trackingReference;
    }
}

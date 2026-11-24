package com.orderprocessing.orderservice.carrier;

import java.util.UUID;

public interface CarrierPort {
    String accept(UUID orderId, String trackingReference);
}

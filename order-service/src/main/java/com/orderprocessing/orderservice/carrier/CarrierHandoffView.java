package com.orderprocessing.orderservice.carrier;

import java.util.UUID;

public record CarrierHandoffView(UUID orderId, String trackingReference) {
}

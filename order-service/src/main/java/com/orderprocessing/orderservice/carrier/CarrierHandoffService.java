package com.orderprocessing.orderservice.carrier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CarrierHandoffService {
    private final CarrierHandoffRepository handoffs;

    public CarrierHandoffService(CarrierHandoffRepository handoffs) {
        this.handoffs = handoffs;
    }

    @Transactional
    public CarrierHandoff record(UUID orderId, String trackingReference) {
        return handoffs.findByOrderId(orderId).orElseGet(() -> {
            CarrierHandoff row = new CarrierHandoff();
            row.setId(UUID.randomUUID());
            row.setOrderId(orderId);
            row.setTrackingReference(trackingReference);
            row.setAcceptedAt(Instant.now());
            return handoffs.save(row);
        });
    }

    @Transactional(readOnly = true)
    public Optional<CarrierHandoffView> view(UUID orderId) {
        return handoffs.findByOrderId(orderId)
                .map(row -> new CarrierHandoffView(row.getOrderId(), row.getTrackingReference()));
    }
}

package com.orderprocessing.orderservice.carrier;

import com.orderprocessing.orderservice.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/delivery/handoffs")
public class CarrierHandoffController {
    private final CarrierHandoffService handoffs;

    public CarrierHandoffController(CarrierHandoffService handoffs) {
        this.handoffs = handoffs;
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('DELIVERY')")
    public CarrierHandoffView read(@PathVariable UUID orderId) {
        return handoffs.view(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Handoff does not exist"));
    }
}

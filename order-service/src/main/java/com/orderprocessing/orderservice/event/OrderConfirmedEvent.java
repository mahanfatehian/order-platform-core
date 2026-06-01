package com.orderprocessing.orderservice.event;

import java.util.UUID;

import lombok.Data;

@Data
public class OrderConfirmedEvent {
    private UUID orderId;
    private boolean success;
}
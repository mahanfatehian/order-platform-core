package com.orderprocessing.common.events;

import java.util.UUID;

import lombok.Data;

@Data
public class OrderFailedEvent {
    private UUID orderId;
    private boolean success;
    private String reason;
}
package com.orderprocessing.common.events;

import java.util.UUID;
import java.util.Map;

import lombok.Data;

@Data
public class OrderPlacedEvent {
    private UUID orderId;
    private UUID userId;
    private Map<UUID, Integer> items;
}
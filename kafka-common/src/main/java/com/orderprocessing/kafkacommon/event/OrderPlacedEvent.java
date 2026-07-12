package com.orderprocessing.kafkacommon.event;

import java.util.UUID;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPlacedEvent extends DomainEvent {
    private UUID orderId;
    private UUID userId;
    private Map<UUID, Integer> items;
}

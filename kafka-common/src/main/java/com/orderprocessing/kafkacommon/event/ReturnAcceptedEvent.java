package com.orderprocessing.kafkacommon.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReturnAcceptedEvent extends DomainEvent {
    private UUID orderId;
    private Map<UUID, Integer> items;
    private UUID actorUserId;
}

package com.orderprocessing.kafkacommon.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPackagedEvent extends DomainEvent {
    private UUID orderId;
}

package com.orderprocessing.kafkacommon.event;

import java.util.UUID;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StockInsufficientEvent extends DomainEvent {
    private UUID orderId;
    private boolean success;
    private String reason;
}

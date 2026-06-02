package com.orderprocessing.kafkacommon.event;

import java.util.UUID;

import lombok.Data;

@Data
public class StockReservedEvent {
    private UUID orderId;
    private boolean success;
}
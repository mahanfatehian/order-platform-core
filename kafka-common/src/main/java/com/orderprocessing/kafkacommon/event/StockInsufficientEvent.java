package com.orderprocessing.kafkacommon.event;

import java.util.UUID;

import lombok.Data;

@Data
public class StockInsufficientEvent {
    private UUID orderId;
    private boolean success;
    private String reason;
}
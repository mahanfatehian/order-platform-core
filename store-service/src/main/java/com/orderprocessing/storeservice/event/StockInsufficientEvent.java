package com.orderprocessing.storeservice.event;

import java.util.UUID;

import lombok.Data;

@Data
public class StockInsufficientEvent {
    private UUID orderId;
    private boolean success;
    private String reason;
}
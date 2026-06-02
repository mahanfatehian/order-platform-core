package com.orderprocessing.kafkacommon.event;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class OrderCancelledEvent {
    private UUID orderId;
    private Map<UUID, Integer> items;
    private String reason;
}
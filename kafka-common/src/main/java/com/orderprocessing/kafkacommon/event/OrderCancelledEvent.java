package com.orderprocessing.kafkacommon.event;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderCancelledEvent extends DomainEvent {
    private UUID orderId;
    private Map<UUID, Integer> items;
    private String reason;
}

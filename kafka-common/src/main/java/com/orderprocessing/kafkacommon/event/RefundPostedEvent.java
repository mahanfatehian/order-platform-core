package com.orderprocessing.kafkacommon.event;

import java.util.UUID;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RefundPostedEvent extends DomainEvent {
    private UUID refundEntryId;
    private UUID orderId;
    private UUID userId;
    private long amount;
}

package com.orderprocessing.kafkacommon.event;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Common metadata for past-tense order fulfillment facts.
 *
 * <p>The additional fields are nullable so consumers can continue to
 * deserialize version-one messages that only contained {@code orderId}.</p>
 */
@Getter
@Setter
public abstract class OrderFulfillmentEvent extends DomainEvent {
    private UUID orderId;
    private UUID actorUserId;
    private String actorRole;
    private String fromStatus;
    private String toStatus;
    private String reason;
}

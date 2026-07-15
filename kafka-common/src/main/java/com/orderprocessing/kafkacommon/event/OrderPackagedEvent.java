package com.orderprocessing.kafkacommon.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPackagedEvent extends OrderFulfillmentEvent {
}

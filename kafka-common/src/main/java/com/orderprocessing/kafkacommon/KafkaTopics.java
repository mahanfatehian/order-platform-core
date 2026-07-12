package com.orderprocessing.kafkacommon;

public final class KafkaTopics {
    public static final String ORDER_EVENTS = "order.events";
    public static final String STORE_EVENTS = "store.events";
    public static final String ORDER_EVENTS_DLT = ORDER_EVENTS + ".dlt";
    public static final String STORE_EVENTS_DLT = STORE_EVENTS + ".dlt";

    private KafkaTopics() {
    }
}

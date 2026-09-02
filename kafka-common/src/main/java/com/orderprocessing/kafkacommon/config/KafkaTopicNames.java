package com.orderprocessing.kafkacommon.config;

import com.orderprocessing.kafkacommon.KafkaTopics;

public record KafkaTopicNames(String orderEvents, String storeEvents) {
    public KafkaTopicNames {
        orderEvents = KafkaTopics.requireValidTopic(orderEvents, "orderEvents");
        storeEvents = KafkaTopics.requireValidTopic(storeEvents, "storeEvents");
    }
}

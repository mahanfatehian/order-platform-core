package com.orderprocessing.kafkacommon;

import java.util.regex.Pattern;

public final class KafkaTopics {
    private static final Pattern VALID_TOPIC_COMPONENT = Pattern.compile("[A-Za-z0-9._-]+");

    public static final String ORDER_EVENTS = "order.events";
    public static final String STORE_EVENTS = "store.events";
    public static final String ORDER_SERVICE = "order-service";
    public static final String STORE_SERVICE = "store-service";
    public static final String ORDER_EVENTS_DLT = ORDER_EVENTS + ".dlt";
    public static final String STORE_EVENTS_DLT = STORE_EVENTS + ".dlt";
    public static final String ORDER_EVENTS_ORDER_SERVICE_DLT =
            deadLetterTopic(ORDER_EVENTS, ORDER_SERVICE);
    public static final String ORDER_EVENTS_STORE_SERVICE_DLT =
            deadLetterTopic(ORDER_EVENTS, STORE_SERVICE);
    public static final String STORE_EVENTS_ORDER_SERVICE_DLT =
            deadLetterTopic(STORE_EVENTS, ORDER_SERVICE);

    private KafkaTopics() {
    }

    public static String deadLetterTopic(String sourceTopic, String consumerOwner) {
        requireTopicComponent(sourceTopic, "sourceTopic");
        requireTopicComponent(consumerOwner, "consumerOwner");
        String destination = sourceTopic + "." + consumerOwner + ".dlt";
        if (destination.length() > 249) {
            throw new IllegalArgumentException("dead-letter topic exceeds Kafka's 249-character limit");
        }
        return destination;
    }

    private static void requireTopicComponent(String value, String argument) {
        if (value == null || !VALID_TOPIC_COMPONENT.matcher(value).matches()) {
            throw new IllegalArgumentException(argument + " is not a valid Kafka topic component");
        }
    }
}

package com.orderprocessing.kafkacommon.config;

import com.orderprocessing.kafkacommon.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.order-events:" + KafkaTopics.ORDER_EVENTS + "}")
    private String orderEventsTopic;

    @Value("${kafka.topics.order-events-partitions:3}")
    private int orderEventsPartitions;

    @Value("${kafka.topics.order-events-replicas:1}")
    private int orderEventsReplicas;

    @Value("${kafka.topics.store-events:" + KafkaTopics.STORE_EVENTS + "}")
    private String storeEventsTopic;

    @Value("${kafka.topics.store-events-partitions:3}")
    private int storeEventsPartitions;

    @Value("${kafka.topics.store-events-replicas:1}")
    private int storeEventsReplicas;

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(orderEventsTopic)
                .partitions(orderEventsPartitions)
                .replicas(orderEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name(storeEventsTopic)
                .partitions(storeEventsPartitions)
                .replicas(storeEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS_DLT)
                .partitions(orderEventsPartitions)
                .replicas(orderEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic storeEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.STORE_EVENTS_DLT)
                .partitions(storeEventsPartitions)
                .replicas(storeEventsReplicas)
                .build();
    }
}

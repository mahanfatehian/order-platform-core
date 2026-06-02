package com.orderprocessing.kafkacommon.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.order-events:order.events}")
    private String orderEventsTopic;

    @Value("${kafka.topics.order-events-partitions:3}")
    private int orderEventsPartitions;

    @Value("${kafka.topics.order-events-replicas:1}")
    private int orderEventsReplicas;

    @Value("${kafka.topics.store-events:store.events}")
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
}
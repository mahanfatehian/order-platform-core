package com.orderprocessing.kafkacommon.config;

import com.orderprocessing.kafkacommon.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    private final KafkaTopicNames topicNames;
    private final int orderEventsPartitions;
    private final int orderEventsReplicas;
    private final int storeEventsPartitions;
    private final int storeEventsReplicas;

    public KafkaTopicConfig(
            @Value("${kafka.topics.order-events:order.events}") String orderEventsTopic,
            @Value("${kafka.topics.order-events-partitions:3}") int orderEventsPartitions,
            @Value("${kafka.topics.order-events-replicas:1}") int orderEventsReplicas,
            @Value("${kafka.topics.store-events:store.events}") String storeEventsTopic,
            @Value("${kafka.topics.store-events-partitions:3}") int storeEventsPartitions,
            @Value("${kafka.topics.store-events-replicas:1}") int storeEventsReplicas) {
        this.topicNames = new KafkaTopicNames(orderEventsTopic, storeEventsTopic);
        this.orderEventsPartitions = orderEventsPartitions;
        this.orderEventsReplicas = orderEventsReplicas;
        this.storeEventsPartitions = storeEventsPartitions;
        this.storeEventsReplicas = storeEventsReplicas;
    }

    @Bean
    public KafkaTopicNames kafkaTopicNames() {
        return topicNames;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(topicNames.orderEvents())
                .partitions(orderEventsPartitions)
                .replicas(orderEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name(topicNames.storeEvents())
                .partitions(storeEventsPartitions)
                .replicas(storeEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic orderEventsOrderServiceDltTopic() {
        return TopicBuilder.name(KafkaTopics.deadLetterTopic(topicNames.orderEvents(), KafkaTopics.ORDER_SERVICE))
                .partitions(orderEventsPartitions)
                .replicas(orderEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic orderEventsStoreServiceDltTopic() {
        return TopicBuilder.name(KafkaTopics.deadLetterTopic(topicNames.orderEvents(), KafkaTopics.STORE_SERVICE))
                .partitions(orderEventsPartitions)
                .replicas(orderEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic storeEventsOrderServiceDltTopic() {
        return TopicBuilder.name(KafkaTopics.deadLetterTopic(topicNames.storeEvents(), KafkaTopics.ORDER_SERVICE))
                .partitions(storeEventsPartitions)
                .replicas(storeEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.deadLetterTopic(topicNames.orderEvents()))
                .partitions(orderEventsPartitions)
                .replicas(orderEventsReplicas)
                .build();
    }

    @Bean
    public NewTopic storeEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.deadLetterTopic(topicNames.storeEvents()))
                .partitions(storeEventsPartitions)
                .replicas(storeEventsReplicas)
                .build();
    }
}

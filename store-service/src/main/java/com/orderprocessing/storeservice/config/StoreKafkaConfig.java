package com.orderprocessing.storeservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class StoreKafkaConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic storeEventsTopic() {
        return TopicBuilder.name("store.events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
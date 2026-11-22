package com.orderprocessing.notificationservice.config;

import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.config.KafkaTopicNames;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class NotificationKafkaTopics {

    @Bean
    public NewTopic notificationOrderEventsDlt(KafkaTopicNames names) {
        return TopicBuilder.name(KafkaTopics.deadLetterTopic(names.orderEvents(), "notification-service"))
                .partitions(3)
                .replicas(1)
                .build();
    }
}

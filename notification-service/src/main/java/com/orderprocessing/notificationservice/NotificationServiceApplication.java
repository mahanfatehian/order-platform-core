package com.orderprocessing.notificationservice;

import com.orderprocessing.kafkacommon.config.KafkaConfig;
import com.orderprocessing.kafkacommon.config.KafkaTopicConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@Import({KafkaConfig.class, KafkaTopicConfig.class})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

package com.orderprocessing.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.context.annotation.Import;
import com.orderprocessing.kafkacommon.config.KafkaConfig;
import com.orderprocessing.kafkacommon.config.KafkaTopicConfig;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableMethodSecurity
@Import({KafkaConfig.class, KafkaTopicConfig.class})
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

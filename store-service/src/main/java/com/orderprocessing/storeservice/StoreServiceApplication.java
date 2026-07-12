package com.orderprocessing.storeservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.orderprocessing.kafkacommon.config.KafkaConfig;
import com.orderprocessing.kafkacommon.config.KafkaTopicConfig;

@SpringBootApplication
@EnableScheduling
@Import({KafkaConfig.class, KafkaTopicConfig.class})
public class StoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreServiceApplication.class, args);
    }
}

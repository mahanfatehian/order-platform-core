package com.orderprocessing.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.orderprocessing.authservice.config.UserServiceClientProperties;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(UserServiceClientProperties.class)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

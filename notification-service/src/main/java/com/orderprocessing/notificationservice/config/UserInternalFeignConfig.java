package com.orderprocessing.notificationservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

public class UserInternalFeignConfig {

    @Bean
    public RequestInterceptor userInternalApiKeyRequestInterceptor(
            @Value("${services.user-service.internal-api-key}") String apiKey) {
        return requestTemplate -> {
            requestTemplate.header("X-Internal-Api-Key", apiKey);
            requestTemplate.header("X-Correlation-Id", UUID.randomUUID().toString());
        };
    }
}

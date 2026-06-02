package com.orderprocessing.orderservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoreServiceFeignConfig {
    @Bean
    public RequestInterceptor storeInternalApiKeyRequestInterceptor(
            @Value("${services.store-service.internal-api-key}") String apiKey) {
        return requestTemplate -> requestTemplate.header("X-Store-Internal-Api-Key", apiKey);
    }
}
package com.orderprocessing.orderservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public class StoreServiceFeignConfig {
    @Bean
    public RequestInterceptor storeInternalApiKeyRequestInterceptor(
            @Value("${services.store-service.internal-api-key}") String apiKey) {
        return requestTemplate -> {
            requestTemplate.header("X-Store-Internal-Api-Key", apiKey);
            String correlationId = null;
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                correlationId = attributes.getRequest().getHeader("X-Correlation-Id");
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            requestTemplate.header("X-Correlation-Id", correlationId);
        };
    }
}

package com.orderprocessing.authservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignInternalAuthConfig {

    @Bean
    public RequestInterceptor internalApiKeyRequestInterceptor(
            @Value("${services.user-service.internal-api-key}") String apiKey
    ) {
        return requestTemplate -> requestTemplate.header("X-Internal-Api-Key", apiKey);
    }
}

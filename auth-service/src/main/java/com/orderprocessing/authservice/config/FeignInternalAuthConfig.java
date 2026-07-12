package com.orderprocessing.authservice.config;

import com.orderprocessing.security.web.CorrelationId;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignInternalAuthConfig {

    @Bean
    public RequestInterceptor internalApiKeyRequestInterceptor(
            UserServiceClientProperties properties
    ) {
        return requestTemplate -> {
            requestTemplate.header("X-Internal-Api-Key", properties.getInternalApiKey());
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                requestTemplate.header(CorrelationId.HEADER, CorrelationId.resolve(attributes.getRequest()));
            }
        };
    }
}

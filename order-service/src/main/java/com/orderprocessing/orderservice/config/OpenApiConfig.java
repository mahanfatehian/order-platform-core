package com.orderprocessing.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderOpenAPI() {
        return new OpenAPI().info(new Info().title("Order Service API").version("1.0.0")
                .description("Processes orders, manages outbox events, and handles synchronous inventory reservations."));
    }

    @Bean
    public GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder().group("1. Order Management").pathsToMatch("/api/orders/**").build();
    }
}
package com.orderprocessing.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderOpenAPI() {
        // Relative, so "Try it out" calls the origin that served the page; the gateway routes /api/** at its root.
        return new OpenAPI().info(new Info().title("Order Service API").version("1.0.0")
                .description("Owns the order lifecycle and publishes its events through a transactional outbox; "
                        + "store-service reserves inventory asynchronously in response."))
                .servers(List.of(new Server().url("/")));
    }

    @Bean
    public GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder().group("1. Order Management").pathsToMatch("/api/orders/**").build();
    }
}
package com.orderprocessing.authservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI authOpenAPI() {
        // Relative, so "Try it out" calls the origin that served the page; the gateway routes /api/** at its root.
        return new OpenAPI().info(new Info()
                .title("Auth Service API")
                .version("1.0.0")
                .description("Handles JWT issuance, refresh, and logout.")
                .contact(new Contact().name("Order Platform Team")))
                .servers(List.of(new Server().url("/")));
    }
}
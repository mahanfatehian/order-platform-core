package com.orderprocessing.authservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI authOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Auth Service API")
                .version("1.0.0")
                .description("Handles JWT issuance, refresh, and logout.")
                .contact(new Contact().name("Order Platform Team")));
    }
}
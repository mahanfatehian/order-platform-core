package com.orderprocessing.storeservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storeOpenAPI() {
        // Relative, so "Try it out" calls the origin that served the page; the gateway routes /api/** at its root.
        return new OpenAPI().info(new Info().title("Store Service API").version("1.0.0")
                        .description("Manages product catalog and inventory."))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("internalApiKey",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Store-Internal-Api-Key")));
    }

    @Bean
    public GroupedOpenApi publicStoreApi() {
        // The group name becomes a URL path segment, so it must not contain a slash.
        return GroupedOpenApi.builder().group("1. Store Catalog (Public and JWT)").pathsToMatch("/api/store/**").pathsToExclude("/api/store/internal/**").build();
    }

    @Bean
    public GroupedOpenApi internalStoreApi() {
        return GroupedOpenApi.builder().group("2. Store Inventory (Internal)").pathsToMatch("/api/store/internal/**").build();
    }
}
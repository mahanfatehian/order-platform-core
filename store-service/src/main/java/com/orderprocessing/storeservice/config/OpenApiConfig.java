package com.orderprocessing.storeservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storeOpenAPI() {
        return new OpenAPI().info(new Info().title("Store Service API").version("1.0.0")
                        .description("Manages product catalog and inventory."))
                .components(new Components().addSecuritySchemes("internalApiKey",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Store-Internal-Api-Key")));
    }

    @Bean
    public GroupedOpenApi publicStoreApi() {
        return GroupedOpenApi.builder().group("1. Store Catalog (Public/JWT)").pathsToMatch("/api/store/**").pathsToExclude("/api/store/internal/**").build();
    }

    @Bean
    public GroupedOpenApi internalStoreApi() {
        return GroupedOpenApi.builder().group("2. Store Inventory (Internal)").pathsToMatch("/api/store/internal/**").build();
    }
}
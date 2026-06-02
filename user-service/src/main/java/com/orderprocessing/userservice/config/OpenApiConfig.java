package com.orderprocessing.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .version("1.0.0")
                        .description("REST API for managing users in the order platform")
                        .contact(new Contact().name("Order Platform Team")))
                .externalDocs(new ExternalDocumentation()
                        .description("Project documentation"))
                .components(new Components()
                        .addSecuritySchemes("internalApiKey",
                                new SecurityScheme()
                                        .name("X-Internal-Api-Key")
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("Internal API Key for service-to-service communication")));
    }
}
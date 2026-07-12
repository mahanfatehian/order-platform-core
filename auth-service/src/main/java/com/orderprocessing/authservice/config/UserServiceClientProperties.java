package com.orderprocessing.authservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "services.user-service")
public class UserServiceClientProperties {

    @NotBlank(message = "User-service internal API key is required")
    @Size(min = 32, message = "User-service internal API key must contain at least 32 characters")
    private String internalApiKey;
}

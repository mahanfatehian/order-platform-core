package com.orderprocessing.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@ConfigurationProperties(prefix = "app.security.internal")
@Validated
@Getter
@Setter
public class InternalSecurityProperties {

    @NotBlank(message = "User-service internal API key is required")
    @Size(min = 32, message = "User-service internal API key must contain at least 32 characters")
    private String apiKey;
}

package com.orderprocessing.gateway.config;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.ArrayList;
import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtSecurityProperties {
    private List<String> publicPaths = new ArrayList<>();
    @NotBlank(message = "JWT secret is required")
    @Size(min = 32, message = "JWT secret must contain at least 32 characters")
    private String secret;
}

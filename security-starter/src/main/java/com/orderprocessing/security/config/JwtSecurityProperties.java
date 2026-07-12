package com.orderprocessing.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.jwt")
@Validated
public class JwtSecurityProperties {

    private List<String> publicPaths = new ArrayList<>();
    @NotBlank(message = "JWT secret is required")
    @Size(min = 32, message = "JWT secret must contain at least 32 characters")
    private String secret;
    @Positive
    private long expiration = 3600000; // 1 hour access token
    @Positive
    private long refreshExpiration = 604800000; // 7 days refresh token

    public JwtSecurityProperties() {
        this.publicPaths.add("/swagger-ui.html");
        this.publicPaths.add("/swagger-ui/**");
        this.publicPaths.add("/v3/api-docs/**");
        this.publicPaths.add("/api/auth/login");
        this.publicPaths.add("/api/auth/refresh");
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }
}

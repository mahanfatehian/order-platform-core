package com.orderprocessing.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtSecurityProperties {

    private String secret;

    private long expiration = 3600000;

    private List<String> publicPaths = new ArrayList<>();

    public JwtSecurityProperties() {

        publicPaths.add("/swagger-ui.html");
        publicPaths.add("/swagger-ui/**");
        publicPaths.add("/v3/api-docs/**");
        publicPaths.add("/actuator/health");

        publicPaths.add("/api/v1/auth/login");
        publicPaths.add("/api/v1/auth/logout");
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

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}

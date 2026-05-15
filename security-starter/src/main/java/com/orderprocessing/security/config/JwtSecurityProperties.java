package com.orderprocessing.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtSecurityProperties {

    private List<String> publicPaths = new ArrayList<>();
    private String secret = "defaultsecretdefaultsecretdefaultsecret12";
    private long expiration = 86400000; // 24h access token
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

package com.orderprocessing.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Configuration
public class RateLimitConfig {

    /**
     * Resolves the rate limit key based on the authenticated user's principal (username).
     * Falls back to the client's IP address if the user is not authenticated.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            return exchange.getPrincipal()
                    .map(principal -> principal.getName()) // Gets the 'sub' claim from JWT
                    .switchIfEmpty(Mono.justOrEmpty(getClientIp(exchange)));
        };
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(getClientIp(exchange));
    }

    @Bean
    public KeyResolver uiSessionKeyResolver() {
        return exchange -> {
            String sessionId = exchange.getRequest().getCookies().getFirst("ORDER_PLATFORM_SESSION") != null
                    ? exchange.getRequest().getCookies().getFirst("ORDER_PLATFORM_SESSION").getValue()
                    : null;
            if (sessionId == null && exchange.getRequest().getCookies().getFirst("SESSION") != null) {
                sessionId = exchange.getRequest().getCookies().getFirst("SESSION").getValue();
            }
            if (sessionId == null && exchange.getRequest().getCookies().getFirst("JSESSIONID") != null) {
                sessionId = exchange.getRequest().getCookies().getFirst("JSESSIONID").getValue();
            }
            return Mono.just(sessionId == null || sessionId.isBlank()
                    ? "ip:" + getClientIp(exchange)
                    : "session:" + sha256(sessionId));
        };
    }

    private String getClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

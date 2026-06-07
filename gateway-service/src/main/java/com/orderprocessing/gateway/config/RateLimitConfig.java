package com.orderprocessing.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

    /**
     * Resolves the rate limit key based on the authenticated user's principal (username).
     * Falls back to the client's IP address if the user is not authenticated.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            return exchange.getPrincipal()
                    .map(principal -> principal.getName()) // Gets the 'sub' claim from JWT
                    .switchIfEmpty(Mono.justOrEmpty(getClientIp(exchange)));
        };
    }

    /**
     * Alternative: Strict IP-based rate limiting (uncomment to use instead of userKeyResolver)
     */
    /*
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.justOrEmpty(getClientIp(exchange));
    }
    */

    private String getClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }
}
package com.orderprocessing.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName();
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String provided = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = provided != null && SAFE_VALUE.matcher(provided).matches()
                ? provided
                : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(HEADER);
            headers.add(HEADER, correlationId);
        }).build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getAttributes().put(ATTRIBUTE, correlationId);
        mutated.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(mutated);
    }

    public static String get(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(ATTRIBUTE);
        return value instanceof String text ? text : UUID.randomUUID().toString();
    }
}

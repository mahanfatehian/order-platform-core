package com.orderprocessing.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class RateLimitResponseFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        // Must run with higher precedence (lower order value) than the RequestRateLimiter
        // to successfully wrap the response before the rate limiter sets it to 429 and completes it.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> setComplete() {
                if (getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    originalResponse.getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    String jsonResponse = "{" +
                            "\"status\": 429," +
                            "\"error\": \"Too Many Requests\"," +
                            "\"message\": \"Rate limit exceeded. Please slow down and try again later.\"," +
                            "\"path\": \"" + exchange.getRequest().getPath().value() + "\"" +
                            "}";

                    DataBuffer buffer = bufferFactory.wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
                    return originalResponse.writeWith(Mono.just(buffer));
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
}
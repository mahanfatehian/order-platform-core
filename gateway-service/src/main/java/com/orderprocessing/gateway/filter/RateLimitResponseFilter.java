package com.orderprocessing.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.Map;

@Component
public class RateLimitResponseFilter implements WebFilter, Ordered {

    private final ObjectMapper objectMapper;

    public RateLimitResponseFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        // Must run with higher precedence (lower order value) than the RequestRateLimiter
        // to successfully wrap the response before the rate limiter sets it to 429 and completes it.
        return Ordered.HIGHEST_PRECEDENCE + 1;
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

                    String correlationId = CorrelationIdWebFilter.get(exchange);
                    originalResponse.getHeaders().set(CorrelationIdWebFilter.HEADER, correlationId);
                    Map<String, Object> body = Map.of(
                            "timestamp", Instant.now().toString(),
                            "status", 429,
                            "code", "RATE_LIMIT_EXCEEDED",
                            "message", "Rate limit exceeded. Please slow down and try again later.",
                            "path", exchange.getRequest().getPath().value(),
                            "correlationId", correlationId
                    );
                    try {
                        DataBuffer buffer = bufferFactory.wrap(objectMapper.writeValueAsBytes(body));
                        return originalResponse.writeWith(Mono.just(buffer));
                    } catch (JsonProcessingException exception) {
                        return Mono.error(exception);
                    }
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
}

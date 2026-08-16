package com.orderprocessing.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.gateway.security.GatewayErrorWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiBoundaryFilterTest {

    private final InternalApiBoundaryFilter filter = new InternalApiBoundaryFilter(new GatewayErrorWriter(new ObjectMapper()));

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/users/internal/authenticate",
            "/api/store/internal/quote"
    })
    void rejectsInternalPathsBeforeTheDownstreamChain(String path) {
        MockServerWebExchange exchange = exchange(path);
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertThat(chainCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER)).isEqualTo("correlation-123");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"INTERNAL_API_FORBIDDEN\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X-Internal-Api-Key",
            "x-store-internal-api-key"
    })
    void removesInternalCredentialsFromForwardedPublicRequests(String internalHeader) {
        MockServerWebExchange exchange = exchange("/api/store/products");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        WebFilterChain chain = forwardedExchange -> {
            forwarded.set(forwardedExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded).hasValueSatisfying(value -> {
            HttpHeaders headers = value.getRequest().getHeaders();
            assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("X-Internal-Api-Key"));
            assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("X-Store-Internal-Api-Key"));
            assertThat(headers.getFirst(CorrelationIdWebFilter.HEADER)).isEqualTo("correlation-123");
        });
    }

    private static MockServerWebExchange exchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header(CorrelationIdWebFilter.HEADER, "correlation-123")
                .header("X-Internal-Api-Key", "user-internal-secret")
                .header("x-store-internal-api-key", "store-internal-secret")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdWebFilter.ATTRIBUTE, "correlation-123");
        return exchange;
    }
}

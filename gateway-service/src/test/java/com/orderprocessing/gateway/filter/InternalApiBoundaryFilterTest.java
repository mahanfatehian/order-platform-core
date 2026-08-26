package com.orderprocessing.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.gateway.security.GatewayErrorWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiBoundaryFilterTest {

    private final InternalApiBoundaryFilter filter = edgeFilter("none");

    private static InternalApiBoundaryFilter edgeFilter(String forwardHeadersStrategy) {
        return new InternalApiBoundaryFilter(new GatewayErrorWriter(new ObjectMapper()), forwardHeadersStrategy);
    }

    private static HttpHeaders forwardThrough(InternalApiBoundaryFilter filter, MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, downstream -> {
            forwarded.set(downstream);
            return Mono.empty();
        }).block();
        return forwarded.get().getRequest().getHeaders();
    }

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
    @MethodSource("internalHeaderCasings")
    void removesInternalCredentialsFromForwardedPublicRequests(String userHeader, String storeHeader) {
        MockServerWebExchange exchange = exchange("/api/store/products", userHeader, storeHeader);
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

    private static Stream<Arguments> internalHeaderCasings() {
        return Stream.of(
                Arguments.of("x-internal-api-key", "X-Store-Internal-Api-Key"),
                Arguments.of("X-Internal-Api-Key", "x-store-internal-api-key")
        );
    }

    private static MockServerWebExchange exchange(String path) {
        return exchange(path, "X-Internal-Api-Key", "x-store-internal-api-key");
    }

    private static MockServerWebExchange exchange(String path, String userHeader, String storeHeader) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header(CorrelationIdWebFilter.HEADER, "correlation-123")
                .header(userHeader, "user-internal-secret")
                .header(storeHeader, "store-internal-secret")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(CorrelationIdWebFilter.ATTRIBUTE, "correlation-123");
        return exchange;
    }

    @Test
    void stripsForwardedHeadersAClientTriedToSetWhenTheGatewayIsTheEdge() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/store/products")
                        .header("X-Forwarded-For", "203.0.113.9")
                        .header("x-forwarded-host", "evil.example.com")
                        .header("Forwarded", "for=203.0.113.9;host=evil.example.com;proto=https")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Prefix", "/admin"));

        HttpHeaders headers = forwardThrough(edgeFilter("none"), exchange);

        // Every one of these decides a rate-limit bucket or an absolute URL downstream.
        assertThat(headers.keySet()).noneSatisfy(name ->
                assertThat(name.toLowerCase(java.util.Locale.ROOT)).startsWith("x-forwarded-"));
        assertThat(headers.getFirst("Forwarded")).isNull();
    }

    @Test
    void keepsForwardedHeadersWhenAnOperatorDeclaresATrustedProxy() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/store/products")
                        .header("X-Forwarded-For", "203.0.113.9"));

        HttpHeaders headers = forwardThrough(edgeFilter("framework"), exchange);

        // A load balancer in front owns the chain; stripping it there would hide every real client address.
        assertThat(headers.getFirst("X-Forwarded-For")).isEqualTo("203.0.113.9");
    }

    @Test
    void stillRemovesInternalCredentialsWhenAProxyIsTrusted() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/store/products")
                        .header(InternalApiBoundaryFilter.USER_INTERNAL_HEADER, "forged")
                        .header(InternalApiBoundaryFilter.STORE_INTERNAL_HEADER, "forged"));

        HttpHeaders headers = forwardThrough(edgeFilter("framework"), exchange);

        assertThat(headers.getFirst(InternalApiBoundaryFilter.USER_INTERNAL_HEADER)).isNull();
        assertThat(headers.getFirst(InternalApiBoundaryFilter.STORE_INTERNAL_HEADER)).isNull();
    }
}

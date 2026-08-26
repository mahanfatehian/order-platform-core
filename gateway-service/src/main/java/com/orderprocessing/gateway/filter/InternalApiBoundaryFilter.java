package com.orderprocessing.gateway.filter;

import com.orderprocessing.gateway.security.GatewayErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class InternalApiBoundaryFilter implements WebFilter, Ordered {

    static final String USER_INTERNAL_HEADER = "X-Internal-Api-Key";
    static final String STORE_INTERNAL_HEADER = "X-Store-Internal-Api-Key";

    /**
     * Headers that describe who the caller is and how they reached us. Only a proxy in front of this service may
     * set them; a client that sets them is claiming an identity it does not have.
     */
    static final List<String> FORWARDED_HEADERS = List.of(
            "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Port",
            "X-Forwarded-Proto", "X-Forwarded-Prefix", "X-Forwarded-Ssl");

    private static final PathPattern USER_INTERNAL_PATH = PathPatternParser.defaultInstance
            .parse("/api/users/internal/**");
    private static final PathPattern STORE_INTERNAL_PATH = PathPatternParser.defaultInstance
            .parse("/api/store/internal/**");

    private final GatewayErrorWriter errorWriter;
    private final boolean trustsProxyHeaders;

    public InternalApiBoundaryFilter(
            GatewayErrorWriter errorWriter,
            @Value("${server.forward-headers-strategy:none}") String forwardHeadersStrategy) {
        this.errorWriter = errorWriter;
        // The same switch that decides whether Spring rewrites the remote address from these headers decides
        // whether we let them through: either a trusted proxy owns them, or nobody does.
        this.trustsProxyHeaders = !"none".equalsIgnoreCase(forwardHeadersStrategy);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        if (USER_INTERNAL_PATH.matches(path) || STORE_INTERNAL_PATH.matches(path)) {
            return errorWriter.write(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "INTERNAL_API_FORBIDDEN",
                    "Internal service APIs are not available through the public gateway"
            );
        }

        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(USER_INTERNAL_HEADER);
            headers.remove(STORE_INTERNAL_HEADER);
            if (!trustsProxyHeaders) {
                // Stop a forged chain reaching the services behind us. The routing filter appends the real peer
                // address afterwards, so downstream still receives an accurate X-Forwarded-For.
                FORWARDED_HEADERS.forEach(headers::remove);
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}

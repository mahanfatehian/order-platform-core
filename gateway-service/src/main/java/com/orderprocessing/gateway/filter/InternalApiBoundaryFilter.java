package com.orderprocessing.gateway.filter;

import com.orderprocessing.gateway.security.GatewayErrorWriter;
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

@Component
public class InternalApiBoundaryFilter implements WebFilter, Ordered {

    static final String USER_INTERNAL_HEADER = "X-Internal-Api-Key";
    static final String STORE_INTERNAL_HEADER = "X-Store-Internal-Api-Key";

    private static final PathPattern USER_INTERNAL_PATH = PathPatternParser.defaultInstance
            .parse("/api/users/internal/**");
    private static final PathPattern STORE_INTERNAL_PATH = PathPatternParser.defaultInstance
            .parse("/api/store/internal/**");

    private final GatewayErrorWriter errorWriter;

    public InternalApiBoundaryFilter(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
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
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}

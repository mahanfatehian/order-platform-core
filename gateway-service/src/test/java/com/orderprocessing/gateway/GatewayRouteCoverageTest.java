package com.orderprocessing.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.orderprocessing.gateway.config.JwtSecurityProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.server.PathContainer;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is the only entry point the browser is given, and discovery.locator.enabled is false, so a path
 * with no explicit route is a 404 no matter how healthy the service behind it is. Every path the rendered UI
 * asks for therefore has to appear here.
 */
@SpringBootTest(properties = {
        "JWT_SECRET=test-only-gateway-jwt-secret-that-is-long-enough",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "management.health.redis.enabled=false"
})
class GatewayRouteCoverageTest {
    @Autowired
    RouteLocator routeLocator;

    @Autowired
    JwtSecurityProperties jwtSecurityProperties;

    private boolean isRouted(HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path));
        return Boolean.TRUE.equals(routeLocator.getRoutes()
                .filterWhen(route -> Mono.from(route.getPredicate().apply(exchange)))
                .hasElements()
                .block());
    }

    /**
     * Being routed is only half of reachable. Anything outside public-paths falls through to
     * anyExchange().authenticated(), so the gateway answers 401 before the route is ever used.
     */
    private boolean isAnonymouslyAllowed(String path) {
        PathPatternParser parser = new PathPatternParser();
        return jwtSecurityProperties.getPublicPaths().stream()
                .anyMatch(pattern -> parser.parse(pattern).matches(PathContainer.parsePath(path)));
    }

    @Test
    void servesTheCaptchaChallengeToAnAnonymousVisitor() {
        // The whole point of the challenge is that it is shown to someone who has not signed in, so requiring a
        // token to fetch it makes the sign-in page unusable exactly when the threshold has been crossed.
        assertThat(isAnonymouslyAllowed("/captcha/image")).isTrue();
    }

    @Test
    void leavesAnAuthenticatedPathOutOfThePublicSet() {
        // Guards the helper: if every path were public, the assertion above would prove nothing.
        assertThat(isAnonymouslyAllowed("/api/orders/summary")).isFalse();
    }

    @Test
    void routesTheCaptchaChallengeTheSignInPageEmbeds() {
        // auth/login.html renders <img src="/captcha/image"> once the attempt threshold is crossed. Without a
        // route the browser gets a 404 image and the challenge can never be solved.
        assertThat(isRouted(HttpMethod.GET, "/captcha/image")).isTrue();
    }

    @Test
    void routesEveryPathTheSignInAndRegistrationPagesRequest() {
        assertThat(isRouted(HttpMethod.GET, "/login")).isTrue();
        assertThat(isRouted(HttpMethod.POST, "/login")).isTrue();
        assertThat(isRouted(HttpMethod.GET, "/register")).isTrue();
        assertThat(isRouted(HttpMethod.POST, "/register")).isTrue();
        assertThat(isRouted(HttpMethod.GET, "/assets/css/app.css")).isTrue();
        assertThat(isRouted(HttpMethod.GET, "/webjars/bootstrap/css/bootstrap.min.css")).isTrue();
    }

    @Test
    void routesTheAuthenticatedWorkspaceAndTheBackendApis() {
        assertThat(isRouted(HttpMethod.GET, "/app/orders")).isTrue();
        assertThat(isRouted(HttpMethod.POST, "/app/checkout")).isTrue();
        assertThat(isRouted(HttpMethod.GET, "/admin/users")).isTrue();
        assertThat(isRouted(HttpMethod.POST, "/api/auth/login")).isTrue();
        assertThat(isRouted(HttpMethod.GET, "/api/orders/summary")).isTrue();
    }

    @Test
    void leavesAnUnknownPathUnrouted() {
        // Guards the helper itself: if everything matched, the assertions above would prove nothing.
        assertThat(isRouted(HttpMethod.GET, "/definitely-not-a-route")).isFalse();
    }
}

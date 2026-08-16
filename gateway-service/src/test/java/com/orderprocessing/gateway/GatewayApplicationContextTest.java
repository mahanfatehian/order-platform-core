package com.orderprocessing.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.LogoutWebFilter;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest(properties = {
        "JWT_SECRET=test-only-gateway-jwt-secret-that-is-long-enough",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "management.health.redis.enabled=false"
})
class GatewayApplicationContextTest {
    @Autowired
    SecurityWebFilterChain securityWebFilterChain;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void contextLoadsWithAllRateLimitResolvers() {
    }

    @Test
    void uiLogoutIsForwardedToTheBff() {
        assertThat(securityWebFilterChain.getWebFilters().collectList().block())
                .noneMatch(LogoutWebFilter.class::isInstance);
    }

    @Test
    void authenticatedRequestsCannotRouteToInternalServiceApis() {
        WebTestClient client = WebTestClient.bindToApplicationContext(applicationContext)
                .apply(springSecurity())
                .build()
                .mutateWith(mockJwt());

        assertInternalApiIsForbidden(client, "/api/users/internal/authenticate");
        assertInternalApiIsForbidden(client, "/api/store/internal/quote");
    }

    private static void assertInternalApiIsForbidden(WebTestClient client, String path) {
        client.get().uri(path)
                .header("X-Correlation-Id", "context-test-correlation")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().valueEquals("X-Correlation-Id", "context-test-correlation")
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_API_FORBIDDEN");
    }
}

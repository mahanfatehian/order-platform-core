package com.orderprocessing.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.LogoutWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void contextLoadsWithAllRateLimitResolvers() {
    }

    @Test
    void uiLogoutIsForwardedToTheBff() {
        assertThat(securityWebFilterChain.getWebFilters().collectList().block())
                .noneMatch(LogoutWebFilter.class::isInstance);
    }
}

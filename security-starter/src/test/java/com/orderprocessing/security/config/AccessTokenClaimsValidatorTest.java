package com.orderprocessing.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenClaimsValidatorTest {

    private final AccessTokenClaimsValidator validator = new AccessTokenClaimsValidator();

    @Test
    void acceptsCompleteAccessToken() {
        assertThat(validator.validate(jwt("access", true)).hasErrors()).isFalse();
    }

    @Test
    void rejectsRefreshTokenAtResourceBoundary() {
        assertThat(validator.validate(jwt("refresh", true)).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingVersion() {
        assertThat(validator.validate(jwt("access", false)).hasErrors()).isTrue();
    }

    private Jwt jwt(String type, boolean includeVersion) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("customer")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("type", type)
                .claim("userId", UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_USER"));
        if (includeVersion) {
            builder.claim("tokenVersion", 42L);
        }
        return builder.build();
    }
}

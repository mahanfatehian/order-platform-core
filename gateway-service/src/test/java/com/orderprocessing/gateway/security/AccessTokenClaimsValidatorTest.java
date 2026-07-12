package com.orderprocessing.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenClaimsValidatorTest {

    @Test
    void acceptsOnlyCompleteAccessTokens() {
        Instant now = Instant.now();
        Jwt access = Jwt.withTokenValue("access")
                .header("alg", "HS256")
                .subject("user")
                .claim("jti", "jti")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("type", "access")
                .claim("userId", UUID.randomUUID().toString())
                .claim("tokenVersion", 7L)
                .claim("roles", List.of("ROLE_USER"))
                .build();
        Jwt refresh = Jwt.withTokenValue("refresh")
                .headers(headers -> headers.putAll(access.getHeaders()))
                .claims(claims -> claims.putAll(access.getClaims()))
                .claim("type", "refresh")
                .build();

        AccessTokenClaimsValidator validator = new AccessTokenClaimsValidator();
        assertThat(validator.validate(access).hasErrors()).isFalse();
        assertThat(validator.validate(refresh).hasErrors()).isTrue();
    }
}

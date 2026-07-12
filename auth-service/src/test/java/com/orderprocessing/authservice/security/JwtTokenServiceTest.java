package com.orderprocessing.authservice.security;

import com.orderprocessing.security.config.JwtSecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void refreshUserIdRoundTripsAsCanonicalString() {
        JwtTokenService service = new JwtTokenService(properties());
        UUID userId = UUID.randomUUID();

        JwtTokenService.TokenPair pair = service.generateTokenPair(
                "customer", Set.of("ROLE_USER"), userId, 1234L);
        JwtTokenService.RefreshTokenClaims claims = service.parseRefreshToken(pair.refreshToken());

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.tokenVersion()).isEqualTo(1234L);
        assertThat(claims.linkedAccessJti()).isNotBlank();
    }

    private JwtSecurityProperties properties() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        properties.setExpiration(60_000);
        properties.setRefreshExpiration(120_000);
        return properties;
    }
}

package com.orderprocessing.security.config;

import com.orderprocessing.security.service.TokenRevocationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAutoConfigurationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void servletDecoderUsesCombinedValidationInsteadOfSplitBlacklistAndVersionReads() {
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UUID userId = UUID.randomUUID();
        String jti = "jti";
        when(revocation.isAccessTokenValid(jti, userId, 42L)).thenReturn(true);

        Jwt decoded = decoder(revocation).decode(signedAccessToken(jti, userId));

        assertThat(decoded.getId()).isEqualTo(jti);
        verify(revocation).isAccessTokenValid(jti, userId, 42L);
        verify(revocation, never()).isAccessTokenBlacklisted(anyString());
        verify(revocation, never()).getTokenVersion(any());
    }

    @Test
    void servletDecoderRejectsAccessTokenWhenCombinedValidationReturnsFalse() {
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UUID userId = UUID.randomUUID();
        when(revocation.isAccessTokenValid("jti", userId, 42L)).thenReturn(false);

        assertThatThrownBy(() -> decoder(revocation).decode(signedAccessToken("jti", userId)))
                .isInstanceOf(JwtException.class)
                .hasMessage("Access token has been revoked");
    }

    @Test
    void servletDecoderFailsClosedWhenCombinedValidationCannotReadRedisState() {
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UUID userId = UUID.randomUUID();
        when(revocation.isAccessTokenValid("jti", userId, 42L))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> decoder(revocation).decode(signedAccessToken("jti", userId)))
                .isInstanceOf(JwtException.class)
                .hasMessage("Token revocation state could not be verified")
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    private JwtDecoder decoder(TokenRevocationService revocation) {
        return new SecurityAutoConfiguration().jwtDecoder(properties(), revocation);
    }

    private String signedAccessToken(String jti, UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .claim("type", "access")
                .claim("userId", userId.toString())
                .claim("tokenVersion", 42L)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private JwtSecurityProperties properties() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret(SECRET);
        return properties;
    }
}

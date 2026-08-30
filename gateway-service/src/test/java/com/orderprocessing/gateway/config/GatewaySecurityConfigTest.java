package com.orderprocessing.gateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewaySecurityConfigTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void gatewayValidationUsesOneScriptInsteadOfSequentialBlacklistAndVersionReads() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        GatewaySecurityConfig config = new GatewaySecurityConfig();
        UUID userId = UUID.randomUUID();
        Jwt jwt = accessJwt(userId);
        List<String> keys = List.of(
                "blacklist:access:jti",
                "user:token-version:" + userId);
        when(redis.execute(any(RedisScript.class), eq(keys), eq(List.of("42"))))
                .thenReturn(Flux.just(1L));

        assertThat(config.validateRevocation(jwt, redis).block()).isSameAs(jwt);

        verify(redis).execute(any(RedisScript.class), eq(keys), eq(List.of("42")));
        verify(redis, never()).hasKey(any());
        verify(redis, never()).opsForValue();
    }

    @Test
    void gatewayValidationRejectsScriptResultThatSignalsRevokedOrMismatchedState() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        UUID userId = UUID.randomUUID();
        when(redis.execute(any(RedisScript.class), any(List.class), eq(List.of("42"))))
                .thenReturn(Flux.just(0L));

        assertThatThrownBy(() -> new GatewaySecurityConfig()
                .validateRevocation(accessJwt(userId), redis)
                .block())
                .isInstanceOf(JwtException.class)
                .hasMessage("Access token has been revoked");
    }

    @Test
    void gatewayValidationFailsClosedWhenRedisScriptReturnsNoState() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        UUID userId = UUID.randomUUID();
        when(redis.execute(any(RedisScript.class), any(List.class), eq(List.of("42"))))
                .thenReturn(Flux.empty());

        assertThatThrownBy(() -> new GatewaySecurityConfig()
                .validateRevocation(accessJwt(userId), redis)
                .block())
                .isInstanceOf(JwtException.class)
                .hasMessage("Token revocation state could not be verified");
    }

    @Test
    void gatewayDecoderMapsRedisScriptFailureToJwtExceptionInsteadOfAllowingAccess() throws Exception {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        UUID userId = UUID.randomUUID();
        when(redis.execute(any(RedisScript.class), any(List.class), eq(List.of("42"))))
                .thenReturn(Flux.error(new RedisConnectionFailureException("down")));

        assertThatThrownBy(() -> new GatewaySecurityConfig()
                .reactiveJwtDecoder(properties(), redis)
                .decode(signedAccessToken(userId))
                .block())
                .isInstanceOf(JwtException.class)
                .hasMessage("Token revocation state could not be verified")
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    private Jwt accessJwt(UUID userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("jti", "jti")
                .claim("userId", userId.toString())
                .claim("tokenVersion", 42L)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private String signedAccessToken(UUID userId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID("jti")
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("type", "access")
                .claim("userId", userId.toString())
                .claim("tokenVersion", 42L)
                .claim("roles", List.of("ROLE_USER"))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return token.serialize();
    }

    private JwtSecurityProperties properties() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setSecret(SECRET);
        return properties;
    }
}

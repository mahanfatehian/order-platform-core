package com.orderprocessing.security.config;

import com.orderprocessing.security.service.TokenRevocationService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
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
import static org.mockito.ArgumentMatchers.anyLong;
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

    private static ListAppender<ILoggingEvent> captureLogs() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SecurityAutoConfiguration.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Test
    void anUnreadableRevocationStoreIsReportedOnceRatherThanPerRequest() {
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UUID userId = UUID.randomUUID();
        when(revocation.isAccessTokenValid(anyString(), any(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("redis is unreachable"));
        ListAppender<ILoggingEvent> logs = captureLogs();
        JwtDecoder decoder = decoder(revocation);

        for (int request = 0; request < 5; request++) {
            assertThatThrownBy(() -> decoder.decode(signedAccessToken("jti", userId)))
                    .isInstanceOf(JwtException.class);
        }

        // Five rejected requests, one incident. Logging per request would bury the cause in its own noise.
        assertThat(logs.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains("revocation state cannot be read"));
    }

    @Test
    void anOrdinaryRevokedTokenIsNotReportedAsAnIncident() {
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UUID userId = UUID.randomUUID();
        when(revocation.isAccessTokenValid(anyString(), any(), anyLong())).thenReturn(false);
        ListAppender<ILoggingEvent> logs = captureLogs();

        assertThatThrownBy(() -> decoder(revocation).decode(signedAccessToken("jti", userId)))
                .isInstanceOf(JwtException.class);

        // A revoked token is the control working, not an outage.
        assertThat(logs.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    void recoveryIsReportedSoTheIncidentHasAnEnd() {
        TokenRevocationService revocation = mock(TokenRevocationService.class);
        UUID userId = UUID.randomUUID();
        when(revocation.isAccessTokenValid(anyString(), any(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("redis is unreachable"))
                .thenReturn(true);
        ListAppender<ILoggingEvent> logs = captureLogs();
        JwtDecoder decoder = decoder(revocation);

        assertThatThrownBy(() -> decoder.decode(signedAccessToken("jti", userId)))
                .isInstanceOf(JwtException.class);
        decoder.decode(signedAccessToken("jti", userId));

        assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.INFO
                && event.getFormattedMessage().contains("readable again"));
    }
}

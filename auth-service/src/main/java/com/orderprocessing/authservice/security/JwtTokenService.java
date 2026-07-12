package com.orderprocessing.authservice.security;

import com.orderprocessing.authservice.exception.AuthenticationFailedException;
import com.orderprocessing.security.config.JwtSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtSecurityProperties properties;
    private final SecretKey secretKey;
    private final JwtParser parser;

    public JwtTokenService(JwtSecurityProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(secretKey).build();
    }

    public TokenPair generateTokenPair(
            String username,
            Set<String> roles,
            UUID userId,
            long tokenVersion
    ) {
        if (!StringUtils.hasText(username) || userId == null || tokenVersion <= 0) {
            throw new IllegalArgumentException("Complete current user identity is required for token issuance");
        }
        Set<String> currentRoles = roles == null ? Set.of() : Set.copyOf(new TreeSet<>(roles));
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plusMillis(properties.getExpiration());
        String accessJti = UUID.randomUUID().toString();
        String accessToken = Jwts.builder()
                .subject(username)
                .id(accessJti)
                .claim("roles", currentRoles)
                .claim("type", "access")
                .claim("tokenVersion", tokenVersion)
                .claim("userId", userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(accessExpiresAt))
                .signWith(secretKey)
                .compact();

        Instant refreshExpiresAt = now.plusMillis(properties.getRefreshExpiration());
        String refreshToken = Jwts.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .claim("roles", currentRoles)
                .claim("type", "refresh")
                .claim("accessJti", accessJti)
                .claim("accessExp", accessExpiresAt.toString())
                .claim("tokenVersion", tokenVersion)
                .claim("userId", userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(refreshExpiresAt))
                .signWith(secretKey)
                .compact();

        return new TokenPair(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    public RefreshTokenClaims parseRefreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new AuthenticationFailedException("Refresh token is required");
        }
        Claims claims = parser.parseSignedClaims(token).getPayload();
        try {
            String type = claims.get("type", String.class);
            String username = claims.getSubject();
            String jti = claims.getId();
            String userIdValue = claims.get("userId", String.class);
            Object versionValue = claims.get("tokenVersion");
            String accessJti = claims.get("accessJti", String.class);
            String accessExpiration = claims.get("accessExp", String.class);
            if (!"refresh".equals(type)
                    || !StringUtils.hasText(username)
                    || !StringUtils.hasText(jti)
                    || !StringUtils.hasText(userIdValue)
                    || !(versionValue instanceof Number number)
                    || number.longValue() <= 0
                    || !StringUtils.hasText(accessJti)
                    || !StringUtils.hasText(accessExpiration)
                    || claims.getIssuedAt() == null
                    || claims.getExpiration() == null) {
                throw new AuthenticationFailedException("Invalid refresh token");
            }
            return new RefreshTokenClaims(
                    username,
                    UUID.fromString(userIdValue),
                    jti,
                    number.longValue(),
                    claims.getExpiration().toInstant(),
                    accessJti,
                    Instant.parse(accessExpiration)
            );
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new AuthenticationFailedException("Invalid refresh token", exception);
        }
    }

    public record TokenPair(
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt
    ) {
    }

    public record RefreshTokenClaims(
            String username,
            UUID userId,
            String jti,
            long tokenVersion,
            Instant expiresAt,
            String linkedAccessJti,
            Instant linkedAccessExpiresAt
    ) {
    }
}

package com.orderprocessing.authservice.security;

import com.orderprocessing.security.config.JwtSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtSecurityProperties properties;
    private final SecretKey secretKey;

    public JwtTokenService(JwtSecurityProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String username, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(properties.getExpiration());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(username)
                .id(jti)
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate a refresh token that carries the JTI and expiration of the
     * access token it was originally issued together with.
     */
    public String generateRefreshToken(String username, String accessJti, Instant accessExpiresAt) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(properties.getRefreshExpiration());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(username)
                .id(jti)
                .claim("type", "refresh")
                .claim("accessJti", accessJti)          // <-- new claim
                .claim("accessExp", accessExpiresAt.toString())  // <-- store as ISO string
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public String extractJti(String token) {
        return parse(token).getId();
    }

    public Instant extractExpiration(String token) {
        Date expiration = parse(token).getExpiration();
        return expiration == null ? null : expiration.toInstant();
    }

    public String extractType(String token) {
        return parse(token).get("type", String.class);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(extractType(token));
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractType(token));
    }

    /**
     * Extract the JTI of the access token that was embedded in the refresh token.
     */
    public String extractAccessJti(String refreshToken) {
        return parse(refreshToken).get("accessJti", String.class);
    }

    /**
     * Extract the expiration of the access token that was embedded in the refresh token.
     */
    public Instant extractAccessExpiration(String refreshToken) {
        String expStr = parse(refreshToken).get("accessExp", String.class);
        return expStr != null ? Instant.parse(expStr) : null;
    }
}
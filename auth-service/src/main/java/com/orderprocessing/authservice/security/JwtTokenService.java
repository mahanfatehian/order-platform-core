package com.orderprocessing.authservice.security;

import com.orderprocessing.security.config.JwtSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redisTemplate;

    public JwtTokenService(JwtSecurityProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    // Get or initialize the token version for a user
    private long getTokenVersion(String username) {
        String key = "user:token-version:" + username;
        String current = redisTemplate.opsForValue().get(key);
        if (current == null) {
            redisTemplate.opsForValue().set(key, "1");
            return 1;
        }
        return Long.parseLong(current);
    }

    // Increment token version on logout
    public void incrementTokenVersion(String username) {
        String key = "user:token-version:" + username;
        redisTemplate.opsForValue().increment(key);
    }

    // Extract the token version claim from any JWT
    public Long extractTokenVersion(String token) {
        return parse(token).get("tokenVersion", Long.class);
    }

    // Check if the token's version matches the current version for its user
    public boolean isTokenVersionValid(String token) {
        String username = extractUsername(token);
        Long tokenVersion = extractTokenVersion(token);
        if (tokenVersion == null) {
            return false;
        }
        long currentVersion = getTokenVersion(username);
        return tokenVersion == currentVersion;
    }

    public String generateAccessToken(String username, Set<String> roles, UUID userId) {
        long version = getTokenVersion(username);
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(properties.getExpiration());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(username)
                .id(jti)
                .claim("roles", roles)
                .claim("type", "access")
                .claim("tokenVersion", version)
                .claim("userId", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String username, String accessJti, Instant accessExpiresAt, UUID userId) {
        long version = getTokenVersion(username);
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(properties.getRefreshExpiration());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(username)
                .id(jti)
                .claim("type", "refresh")
                .claim("accessJti", accessJti)
                .claim("accessExp", accessExpiresAt.toString())
                .claim("tokenVersion", version)
                .claim("userId", userId)
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

    public UUID extractUserId(String token) {
        return parse(token).get("userId", UUID.class);
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

    public String extractAccessJti(String refreshToken) {
        return parse(refreshToken).get("accessJti", String.class);
    }

    public Instant extractAccessExpiration(String refreshToken) {
        String expStr = parse(refreshToken).get("accessExp", String.class);
        return expStr != null ? Instant.parse(expStr) : null;
    }
}
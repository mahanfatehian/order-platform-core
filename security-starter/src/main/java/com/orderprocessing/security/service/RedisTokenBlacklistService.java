package com.orderprocessing.security.service;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final String ACCESS_PREFIX = "blacklist:access:";
    private static final String REFRESH_PREFIX = "blacklist:refresh:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistAccessToken(String jti, Instant expiresAt) {
        saveWithTtl(ACCESS_PREFIX + jti, expiresAt);
    }

    @Override
    public void blacklistRefreshToken(String jti, Instant expiresAt) {
        saveWithTtl(REFRESH_PREFIX + jti, expiresAt);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACCESS_PREFIX + jti));
    }

    @Override
    public boolean isRefreshTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_PREFIX + jti));
    }

    private void saveWithTtl(String key, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(key, "true", ttl);
        }
    }
}

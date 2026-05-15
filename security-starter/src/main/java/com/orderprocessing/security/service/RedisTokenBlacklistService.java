package com.orderprocessing.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);
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
        boolean blacklisted = redisTemplate.hasKey(ACCESS_PREFIX + jti);
        log.debug("Checking blacklist for access token jti={} -> {}", jti, blacklisted);
        return blacklisted;
    }

    @Override
    public boolean isRefreshTokenBlacklisted(String jti) {
        boolean blacklisted = redisTemplate.hasKey(REFRESH_PREFIX + jti);
        log.debug("Checking blacklist for refresh token jti={} -> {}", jti, blacklisted);
        return blacklisted;
    }

    private void saveWithTtl(String key, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        log.debug("Attempting to save blacklist key={} with TTL={}", key, ttl);
        if (!ttl.isNegative() && !ttl.isZero()) {
            try {
                redisTemplate.opsForValue().set(key, "true", ttl);
                log.info("Successfully saved blacklist key={}", key);
            } catch (Exception e) {
                log.error("Failed to save blacklist key={} -> {}", key, e.getMessage());
            }
        } else {
            log.warn("Skipping save of blacklist key={} because TTL={} is not positive", key, ttl);
        }
    }
}
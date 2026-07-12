package com.orderprocessing.security.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/** Redis-backed, fail-closed token revocation state. */
public class RedisTokenBlacklistService implements TokenRevocationService {

    public static final String ACCESS_PREFIX = "blacklist:access:";
    public static final String REFRESH_PREFIX = "blacklist:refresh:";
    public static final String VERSION_PREFIX = "user:token-version:";

    private static final long MAX_SAFE_REDIS_INTEGER = 9_000_000_000_000_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
              redis.call('SET', KEYS[1], ARGV[1])
              return tonumber(ARGV[1])
            end
            return redis.call('INCR', KEYS[1])
            """, Long.class);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
              return -1
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            if tonumber(ARGV[3]) > 0 then
              redis.call('PSETEX', KEYS[3], ARGV[3], 'true')
            end
            redis.call('PSETEX', KEYS[2], ARGV[2], 'true')
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> LOGOUT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current or current ~= ARGV[1] then
              return 0
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 0
            end
            redis.call('PSETEX', KEYS[2], ARGV[2], 'true')
            redis.call('INCR', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistAccessToken(String jti, Instant expiresAt) {
        saveWithTtl(ACCESS_PREFIX + requiredText(jti, "access token jti"), expiresAt);
    }

    @Override
    public void blacklistRefreshToken(String jti, Instant expiresAt) {
        saveWithTtl(REFRESH_PREFIX + requiredText(jti, "refresh token jti"), expiresAt);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACCESS_PREFIX + requiredText(jti, "access token jti")));
    }

    @Override
    public boolean isRefreshTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_PREFIX + requiredText(jti, "refresh token jti")));
    }

    @Override
    public long getOrCreateTokenVersion(UUID userId) {
        String key = versionKey(userId);
        String existing = redisTemplate.opsForValue().get(key);
        if (existing != null) {
            return parseVersion(existing);
        }

        long candidate = RANDOM.nextLong(1, MAX_SAFE_REDIS_INTEGER);
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, Long.toString(candidate)))) {
            return candidate;
        }
        String winner = redisTemplate.opsForValue().get(key);
        if (winner == null) {
            throw new IllegalStateException("Token-version initialization did not produce a value");
        }
        return parseVersion(winner);
    }

    @Override
    public OptionalLong getTokenVersion(UUID userId) {
        String value = redisTemplate.opsForValue().get(versionKey(userId));
        return value == null ? OptionalLong.empty() : OptionalLong.of(parseVersion(value));
    }

    @Override
    public long incrementTokenVersion(UUID userId) {
        long initial = RANDOM.nextLong(1, MAX_SAFE_REDIS_INTEGER);
        Long result = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(versionKey(userId)),
                Long.toString(initial)
        );
        if (result == null || result <= 0) {
            throw new IllegalStateException("Token-version increment failed");
        }
        return result;
    }

    @Override
    public boolean rotateRefreshToken(
            String refreshJti,
            Instant refreshExpiresAt,
            String linkedAccessJti,
            Instant linkedAccessExpiresAt,
            UUID userId,
            long expectedTokenVersion
    ) {
        long refreshTtl = positiveTtlMillis(refreshExpiresAt);
        long accessTtl = remainingTtlMillis(linkedAccessExpiresAt);
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        versionKey(userId),
                        REFRESH_PREFIX + requiredText(refreshJti, "refresh token jti"),
                        ACCESS_PREFIX + requiredText(linkedAccessJti, "linked access token jti")
                ),
                Long.toString(expectedTokenVersion),
                Long.toString(refreshTtl),
                Long.toString(accessTtl)
        );
        return Objects.equals(result, 1L);
    }

    @Override
    public boolean revokeAccessTokenAndIncrementVersion(
            String accessJti,
            Instant accessExpiresAt,
            UUID userId,
            long expectedTokenVersion
    ) {
        long ttl = positiveTtlMillis(accessExpiresAt);
        Long result = redisTemplate.execute(
                LOGOUT_SCRIPT,
                List.of(
                        versionKey(userId),
                        ACCESS_PREFIX + requiredText(accessJti, "access token jti")
                ),
                Long.toString(expectedTokenVersion),
                Long.toString(ttl)
        );
        return Objects.equals(result, 1L);
    }

    private void saveWithTtl(String key, Instant expiresAt) {
        redisTemplate.opsForValue().set(key, "true", Duration.ofMillis(positiveTtlMillis(expiresAt)));
    }

    private long positiveTtlMillis(Instant expiresAt) {
        long ttl = remainingTtlMillis(expiresAt);
        if (ttl <= 0) {
            throw new IllegalArgumentException("Cannot revoke an expired token");
        }
        return ttl;
    }

    private long remainingTtlMillis(Instant expiresAt) {
        return Math.max(0, Duration.between(
                Instant.now(), Objects.requireNonNull(expiresAt, "token expiration")).toMillis());
    }

    private String versionKey(UUID userId) {
        return VERSION_PREFIX + Objects.requireNonNull(userId, "userId");
    }

    private long parseVersion(String value) {
        try {
            long version = Long.parseLong(value);
            if (version <= 0) {
                throw new NumberFormatException("not positive");
            }
            return version;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Stored token version is invalid", exception);
        }
    }

    private String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}

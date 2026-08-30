package com.orderprocessing.security.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisTokenValidationIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        service = new RedisTokenBlacklistService(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void redisScriptAcceptsMatchingUnblacklistedAccessTokenState() {
        UUID userId = UUID.randomUUID();
        redisTemplate.opsForValue().set(versionKey(userId), "42");

        assertThat(service.isAccessTokenValid("jti", userId, 42L)).isTrue();
    }

    @Test
    void redisScriptRejectsMismatchedStoredTokenVersion() {
        UUID userId = UUID.randomUUID();
        redisTemplate.opsForValue().set(versionKey(userId), "41");

        assertThat(service.isAccessTokenValid("jti", userId, 42L)).isFalse();
    }

    @Test
    void redisScriptRejectsMissingTokenVersionState() {
        assertThat(service.isAccessTokenValid("jti", UUID.randomUUID(), 42L)).isFalse();
    }

    @Test
    void redisScriptRejectsBlacklistedAccessTokenEvenWhenVersionMatches() {
        UUID userId = UUID.randomUUID();
        redisTemplate.opsForValue().set(versionKey(userId), "42");
        redisTemplate.opsForValue().set(RedisTokenBlacklistService.ACCESS_PREFIX + "jti", "true");

        assertThat(service.isAccessTokenValid("jti", userId, 42L)).isFalse();
    }

    @Test
    void redisScriptRejectsMalformedStoredVersionInsteadOfFailingOpen() {
        UUID userId = UUID.randomUUID();
        redisTemplate.opsForValue().set(versionKey(userId), "not-a-number");

        assertThat(service.isAccessTokenValid("jti", userId, 42L)).isFalse();
    }

    private String versionKey(UUID userId) {
        return RedisTokenBlacklistService.VERSION_PREFIX + userId;
    }
}

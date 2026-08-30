package com.orderprocessing.webui.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AttemptCounterStoreRedisIntegrationTest {
    private static final String KEY = "attempts:login:ip:203.0.113.7";
    private static final Duration WINDOW = Duration.ofSeconds(2);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private AttemptCounterStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        store = new AttemptCounterStore(redis, new ExpiringCounterMap(1000, Clock.systemUTC()));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void createsAndRenewsTheSlidingTtl() throws InterruptedException {
        assertThat(store.increment(KEY, WINDOW)).isEqualTo(1L);
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isBetween(1L, 2_000L);

        Thread.sleep(1_100L);
        long beforeRenewal = redis.getExpire(KEY, TimeUnit.MILLISECONDS);

        assertThat(store.increment(KEY, WINDOW)).isEqualTo(2L);
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isGreaterThan(beforeRenewal);
    }

    @Test
    void repairsAStaleKeyWithoutAnExpiry() {
        redis.opsForValue().set(KEY, "7");
        assertThat(redis.getExpire(KEY)).isEqualTo(-1L);

        assertThat(store.increment(KEY, WINDOW)).isEqualTo(8L);
        assertThat(redis.getExpire(KEY, TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    void expiresTheCounterAfterTheWindow() throws InterruptedException {
        assertThat(store.increment(KEY, WINDOW)).isEqualTo(1L);

        long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (!Boolean.FALSE.equals(redis.hasKey(KEY)) && System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }

        assertThat(redis.hasKey(KEY)).isFalse();
    }
}

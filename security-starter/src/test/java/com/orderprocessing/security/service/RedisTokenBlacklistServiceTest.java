package com.orderprocessing.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisTokenBlacklistServiceTest {

    @Test
    void blacklistWriteFailureIsNotSuppressed() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        doThrow(new RedisConnectionFailureException("down"))
                .when(operations).set(anyString(), anyString(), any(Duration.class));

        RedisTokenBlacklistService service = new RedisTokenBlacklistService(template);

        assertThatThrownBy(() -> service.blacklistAccessToken("jti", Instant.now().plusSeconds(60)))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}

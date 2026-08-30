package com.orderprocessing.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTokenBlacklistServiceTest {

    @Test
    void accessValidationUsesOneScriptInsteadOfSeparateBlacklistAndVersionReads() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(template);
        UUID userId = UUID.randomUUID();
        List<String> keys = List.of(
                RedisTokenBlacklistService.ACCESS_PREFIX + "jti",
                RedisTokenBlacklistService.VERSION_PREFIX + userId);
        when(template.execute(any(RedisScript.class), eq(keys), eq("42"))).thenReturn(1L);

        assertThat(service.isAccessTokenValid("jti", userId, 42L)).isTrue();

        verify(template).execute(any(RedisScript.class), eq(keys), eq("42"));
        verify(template, never()).hasKey(anyString());
        verify(template, never()).opsForValue();
    }

    @Test
    void accessValidationRejectsScriptResultThatSignalsRevokedOrMismatchedState() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(template);
        when(template.execute(any(RedisScript.class), any(List.class), eq("42"))).thenReturn(0L);

        assertThat(service.isAccessTokenValid("jti", UUID.randomUUID(), 42L)).isFalse();
    }

    @Test
    void accessValidationFailsClosedWhenRedisScriptReturnsNoState() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(template);
        when(template.execute(any(RedisScript.class), any(List.class), eq("42"))).thenReturn(null);

        assertThatThrownBy(() -> service.isAccessTokenValid("jti", UUID.randomUUID(), 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Access-token validation failed");
    }

    @Test
    void accessValidationPropagatesRedisConnectionFailureInsteadOfAcceptingUnknownState() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisTokenBlacklistService service = new RedisTokenBlacklistService(template);
        when(template.execute(any(RedisScript.class), any(List.class), eq("42")))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> service.isAccessTokenValid("jti", UUID.randomUUID(), 42L))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

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

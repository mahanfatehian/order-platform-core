package com.orderprocessing.webui.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttemptCounterStoreTest {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY = "attempts:login:ip:203.0.113.7";

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    private AttemptCounterStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        store = new AttemptCounterStore(redis, new ExpiringCounterMap(1000, Clock.systemUTC()));
    }

    @Test
    void prefersTheSharedTallySoEveryInstanceSeesTheSameCount() {
        when(values.increment(KEY)).thenReturn(4L);
        when(values.get(KEY)).thenReturn("4");

        assertThat(store.increment(KEY, WINDOW)).isEqualTo(4L);
        assertThat(store.count(KEY)).isEqualTo(4L);
        verify(redis).expire(KEY, WINDOW);
    }

    @Test
    void keepsCountingLocallyWhenTheSharedStoreIsUnreachable() {
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("redis is down"));
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("redis is down"));

        assertThat(store.increment(KEY, WINDOW)).isEqualTo(1L);
        assertThat(store.increment(KEY, WINDOW)).isEqualTo(2L);
        // The whole point of the fallback: an outage must not silently switch abuse counting off.
        assertThat(store.count(KEY)).isEqualTo(2L);
    }

    @Test
    void carriesOutageCountsForwardOnceTheSharedStoreReturns() {
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("redis is down"));
        store.increment(KEY, WINDOW);
        store.increment(KEY, WINDOW);
        store.increment(KEY, WINDOW);

        // Redis recovers but never saw those attempts; the higher local tally must still hold.
        when(values.get(KEY)).thenReturn("1");
        assertThat(store.count(KEY)).isEqualTo(3L);
    }

    @Test
    void treatsAMalformedSharedValueAsAbsentRatherThanFailingTheRequest() {
        when(values.get(KEY)).thenReturn("not-a-number");

        assertThat(store.count(KEY)).isZero();
    }

    @Test
    void clearingWipesBothTheSharedAndTheLocalTally() {
        when(values.increment(anyString())).thenThrow(new QueryTimeoutException("redis is down"));
        store.increment(KEY, WINDOW);

        store.clear(KEY);

        when(values.get(KEY)).thenReturn(null);
        assertThat(store.count(KEY)).isZero();
        verify(redis).delete(KEY);
    }

    @Test
    void survivesASharedStoreThatAcceptsWritesButReturnsNothing() {
        when(values.increment(KEY)).thenReturn(null);

        assertThat(store.increment(KEY, WINDOW)).isEqualTo(1L);
    }
}

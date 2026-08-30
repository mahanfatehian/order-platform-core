package com.orderprocessing.webui.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared counters for the sign-in abuse controls. Redis is authoritative so every instance sees the same tally,
 * and an in-process map takes over when Redis cannot be reached.
 *
 * <p>Losing Redis therefore degrades the controls to per-instance accounting rather than disabling them: a client
 * spread across N instances gets N times the allowance, which is a far smaller hole than counting nothing at all.
 */
@Component
public class AttemptCounterStore {
    private static final Logger log = LoggerFactory.getLogger(AttemptCounterStore.class);
    private static final int MAX_LOCAL_KEYS = 50_000;
    private static final RedisScript<Long> INCREMENT_AND_EXPIRE_SCRIPT =
            new DefaultRedisScript<>("""
                    local total = redis.call('INCR', KEYS[1])
                    redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    return total
                    """, Long.class);

    private final StringRedisTemplate redis;
    private final ExpiringCounterMap local;
    private final AtomicBoolean degraded = new AtomicBoolean();

    @Autowired
    public AttemptCounterStore(StringRedisTemplate redis) {
        this(redis, new ExpiringCounterMap(MAX_LOCAL_KEYS, Clock.systemUTC()));
    }

    /** Visible for tests, which supply a clock-driven fallback map. */
    AttemptCounterStore(StringRedisTemplate redis, ExpiringCounterMap local) {
        this.redis = redis;
        this.local = local;
    }

    /** Records one event and returns the running total, re-arming the expiry so the window slides. */
    public long increment(String key, Duration window) {
        try {
            Long total = redis.execute(
                    INCREMENT_AND_EXPIRE_SCRIPT,
                    List.of(key),
                    Long.toString(window.toMillis()));
            if (total != null) {
                markHealthy();
                return Math.max(total, local.count(key));
            }
        } catch (RuntimeException exception) {
            markDegraded(exception);
        }
        return local.increment(key, window);
    }

    /**
     * Reads the running total. The larger of the shared and local tallies wins, so counts recorded while Redis was
     * unavailable still hold once it returns rather than resetting an attacker to zero.
     */
    public long count(String key) {
        long localCount = local.count(key);
        try {
            String stored = redis.opsForValue().get(key);
            markHealthy();
            return stored == null ? localCount : Math.max(localCount, Long.parseLong(stored));
        } catch (NumberFormatException exception) {
            log.warn("Discarding a malformed counter value at {}", key);
            return localCount;
        } catch (RuntimeException exception) {
            markDegraded(exception);
            return localCount;
        }
    }

    public void clear(String key) {
        local.clear(key);
        try {
            redis.delete(key);
            markHealthy();
        } catch (RuntimeException exception) {
            markDegraded(exception);
        }
    }

    /** Logs the transition only, so a sustained outage does not bury the rest of the log. */
    private void markDegraded(RuntimeException exception) {
        if (degraded.compareAndSet(false, true)) {
            log.warn("Shared attempt counters are unreachable; falling back to per-instance counting", exception);
        }
    }

    private void markHealthy() {
        if (degraded.compareAndSet(true, false)) {
            log.info("Shared attempt counters are reachable again; per-instance fallback is no longer in use");
        }
    }
}

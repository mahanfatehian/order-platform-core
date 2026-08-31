package com.orderprocessing.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This service performs a Redis call on the path of every request, so the command timeout decides how long a
 * request thread can be held by a Redis that has stopped answering. Lettuce defaults to 60 seconds, which is
 * long enough for a slow Redis to exhaust the thread pool while the service itself is perfectly healthy.
 */
class RedisTimeoutConfigurationTest {
    @Test
    void redisCommandAndConnectTimeoutsAreBounded() throws IOException {
        assertThat(configured("spring.data.redis.timeout"))
                .describedAs("unset leaves Lettuce's 60s default on a per-request call")
                .isLessThanOrEqualTo(Duration.ofSeconds(5));
        assertThat(configured("spring.data.redis.connect-timeout"))
                .describedAs("unset leaves an unbounded wait for a Redis that is not accepting connections")
                .isLessThanOrEqualTo(Duration.ofSeconds(5));
    }

    private static Duration configured(String property) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Object value = sources.get(0).getProperty(property);
        assertThat(value).describedAs("%s is not configured", property).isNotNull();
        String literal = String.valueOf(value).trim();
        if (literal.startsWith("${") && literal.endsWith("}")) {
            literal = literal.substring(literal.indexOf(':') + 1, literal.length() - 1);
        }
        return DurationStyle.detectAndParse(literal.trim());
    }
}

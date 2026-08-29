package com.orderprocessing.storeservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring schedules every {@code @Scheduled} method onto one shared pool, sized 1 by default. A job that runs
 * long therefore delays every other job in the service rather than just itself, which matters here because the
 * outbox publisher is supposed to poll once a second while the retention sweep can run for minutes.
 *
 * <p>This asserts the pool is at least as wide as the number of jobs, so adding a job without widening the pool
 * fails here rather than silently reintroducing the queueing.
 */
class ScheduledTaskCapacityTest {
    private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";

    @Test
    void schedulerPoolIsWideEnoughForEveryScheduledJobInTheService() throws IOException {
        int jobs = countScheduledMethods("com/orderprocessing/storeservice");

        assertThat(jobs)
                .describedAs("scanner found no @Scheduled methods, so the assertion below would prove nothing")
                .isPositive();
        assertThat(configuredPoolSize())
                .describedAs("spring.task.scheduling.pool.size must cover all %d scheduled jobs", jobs)
                .isGreaterThanOrEqualTo(jobs);
    }

    private static int countScheduledMethods(String basePackagePath) throws IOException {
        MetadataReaderFactory factory = new CachingMetadataReaderFactory();
        Resource[] classes = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + basePackagePath + "/**/*.class");
        int total = 0;
        for (Resource candidate : classes) {
            total += factory.getMetadataReader(candidate).getAnnotationMetadata()
                    .getAnnotatedMethods(SCHEDULED).size();
        }
        return total;
    }

    private static int configuredPoolSize() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Object value = sources.get(0).getProperty("spring.task.scheduling.pool.size");
        assertThat(value)
                .describedAs("spring.task.scheduling.pool.size is unset, so the pool is Spring's default of 1")
                .isNotNull();
        // The value carries an env placeholder in the file; the fallback after the colon is what ships.
        String literal = String.valueOf(value).trim();
        if (literal.startsWith("${") && literal.endsWith("}")) {
            literal = literal.substring(literal.indexOf(':') + 1, literal.length() - 1);
        }
        return Integer.parseInt(literal.trim());
    }
}

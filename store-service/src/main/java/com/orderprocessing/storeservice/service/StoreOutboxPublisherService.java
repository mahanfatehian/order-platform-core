package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StoreOutboxPublisherService {
    private final StoreOutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseRetryDelayMillis;
    private final long maxRetryDelayMillis;

    public StoreOutboxPublisherService(StoreOutboxEventRepository repository,
                                       KafkaTemplate<String, Object> kafkaTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${outbox.batch-size:50}") int batchSize,
                                       @Value("${outbox.max-attempts:5}") int maxAttempts,
                                       @Value("${outbox.retry.base-delay:1000}") long baseRetryDelayMillis,
                                       @Value("${outbox.retry.max-delay:60000}") long maxRetryDelayMillis) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseRetryDelayMillis = Math.max(1, baseRetryDelayMillis);
        this.maxRetryDelayMillis = Math.max(this.baseRetryDelayMillis, maxRetryDelayMillis);
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval:1000}")
    @Transactional
    public void publishReadyEvents() {
        List<StoreOutboxEvent> events = repository.lockReadyBatch(batchSize);
        for (StoreOutboxEvent event : events) {
            try {
                DomainEvent payload = KafkaEventRegistry.deserialize(event.getEventType(), event.getPayload(), objectMapper);
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload).get(10, TimeUnit.SECONDS);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                event.setNextAttemptAt(null);
            } catch (Exception ex) {
                int attempts = event.getAttemptCount() + 1;
                event.setAttemptCount(attempts);
                event.setLastError(truncate(ex.getMessage()));
                boolean deadLettered = attempts >= maxAttempts;
                event.setDeadLettered(deadLettered);
                event.setNextAttemptAt(deadLettered ? null : retryAt(event.getId(), attempts));
                log.error("Store outbox publish failed for event {} (attempt {}/{})",
                        event.getId(), attempts, maxAttempts, ex);
            }
            repository.save(event);
        }
    }

    private Instant retryAt(java.util.UUID eventId, int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);
        long exponential;
        try {
            exponential = Math.multiplyExact(baseRetryDelayMillis, 1L << exponent);
        } catch (ArithmeticException ignored) {
            exponential = maxRetryDelayMillis;
        }
        long capped = Math.min(exponential, maxRetryDelayMillis);
        long spread = Math.max(1, capped / 5);
        long offset = Math.floorMod(eventId.getLeastSignificantBits(), spread * 2 + 1) - spread;
        return Instant.now().plusMillis(Math.max(1, capped + offset));
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown publisher failure";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

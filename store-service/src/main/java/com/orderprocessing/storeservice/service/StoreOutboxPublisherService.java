package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    private final Duration batchTimeout;

    public StoreOutboxPublisherService(StoreOutboxEventRepository repository,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${outbox.batch-size:50}") int batchSize,
                                  @Value("${outbox.max-attempts:5}") int maxAttempts,
                                  @Value("${outbox.retry.base-delay:1000}") long baseRetryDelayMillis,
                                  @Value("${outbox.retry.max-delay:60000}") long maxRetryDelayMillis,
                                  @Value("${outbox.publish-timeout:15s}") Duration batchTimeout) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseRetryDelayMillis = Math.max(1, baseRetryDelayMillis);
        this.maxRetryDelayMillis = Math.max(this.baseRetryDelayMillis, maxRetryDelayMillis);
        this.batchTimeout = batchTimeout;
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval:1000}")
    @Transactional
    public void publishReadyEvents() {
        List<StoreOutboxEvent> events = repository.lockReadyBatch(batchSize);
        if (events.isEmpty()) {
            return;
        }
        awaitWithinBudget(dispatch(events));
        repository.saveAll(events);
    }

    /**
     * Hands the whole batch to the producer before waiting on any of it. lockReadyBatch never returns two rows for
     * the same aggregate, so this cannot reorder two facts about one aggregate, and the idempotent producer preserves
     * order per partition in any case.
     */
    private Map<StoreOutboxEvent, CompletableFuture<SendResult<String, Object>>> dispatch(List<StoreOutboxEvent> events) {
        Map<StoreOutboxEvent, CompletableFuture<SendResult<String, Object>>> inFlight = new LinkedHashMap<>();
        for (StoreOutboxEvent event : events) {
            try {
                DomainEvent payload =
                        KafkaEventRegistry.deserialize(event.getEventType(), event.getPayload(), objectMapper);
                inFlight.put(event, kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload));
            } catch (Exception exception) {
                recordFailure(event, exception);
            }
        }
        return inFlight;
    }

    /**
     * Spends one budget across the batch rather than one per event, so an unreachable broker cannot hold the
     * transaction, and the row locks it carries, for the batch size multiplied by the timeout.
     */
    private void awaitWithinBudget(Map<StoreOutboxEvent, CompletableFuture<SendResult<String, Object>>> inFlight) {
        long deadline = System.nanoTime() + batchTimeout.toNanos();
        for (Map.Entry<StoreOutboxEvent, CompletableFuture<SendResult<String, Object>>> entry : inFlight.entrySet()) {
            StoreOutboxEvent event = entry.getKey();
            try {
                entry.getValue().get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                markPublished(event);
            } catch (InterruptedException exception) {
                // Shutdown. Leave the remaining rows untouched so the next run picks them up unchanged.
                Thread.currentThread().interrupt();
                recordFailure(event, exception);
                return;
            } catch (Exception exception) {
                recordFailure(event, exception);
            }
        }
    }

    private void markPublished(StoreOutboxEvent event) {
        event.setPublished(true);
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
        event.setNextAttemptAt(null);
    }

    private void recordFailure(StoreOutboxEvent event, Exception exception) {
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setLastError(truncate(exception.getMessage()));
        boolean deadLettered = attempts >= maxAttempts;
        event.setDeadLettered(deadLettered);
        event.setNextAttemptAt(deadLettered ? null : retryAt(event.getId(), attempts));
        log.error("Store outbox publish failed for event {} (attempt {}/{})",
                event.getId(), attempts, maxAttempts, exception);
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

package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
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
public class OutboxPublisherService {
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseRetryDelayMillis;
    private final long maxRetryDelayMillis;
    private final Duration batchTimeout;

    public OutboxPublisherService(OutboxEventRepository repository,
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
    public void publishUnpublishedEvents() {
        List<OutboxEvent> events = repository.lockReadyBatch(batchSize);
        if (events.isEmpty()) {
            return;
        }
        // One deadline covers both halves. send() blocks before it returns a future, so a budget applied only
        // to the futures would leave the dispatch loop unbounded.
        long deadline = System.nanoTime() + batchTimeout.toNanos();
        awaitWithinBudget(dispatch(events, deadline), deadline);
        repository.saveAll(events);
    }

    /**
     * Hands the whole batch to the producer before waiting on any of it. lockReadyBatch never returns two rows for
     * the same aggregate, so this cannot reorder two facts about one order, and the idempotent producer preserves
     * order per partition in any case.
     */
    private Map<OutboxEvent, CompletableFuture<SendResult<String, Object>>> dispatch(List<OutboxEvent> events, long deadline) {
        Map<OutboxEvent, CompletableFuture<SendResult<String, Object>>> inFlight = new LinkedHashMap<>();
        for (OutboxEvent event : events) {
            if (System.nanoTime() >= deadline) {
                // Out of budget. Leave the rest of the batch untouched, with no attempt recorded, so the next
                // run retries them as though they had never been picked up.
                break;
            }
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
    private void awaitWithinBudget(Map<OutboxEvent, CompletableFuture<SendResult<String, Object>>> inFlight, long deadline) {
        for (Map.Entry<OutboxEvent, CompletableFuture<SendResult<String, Object>>> entry : inFlight.entrySet()) {
            OutboxEvent event = entry.getKey();
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

    private void markPublished(OutboxEvent event) {
        event.setPublished(true);
        event.setPublishedAt(Instant.now());
        event.setLastError(null);
        event.setNextAttemptAt(null);
    }

    private void recordFailure(OutboxEvent event, Exception exception) {
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setLastError(truncate(exception.getMessage()));
        boolean deadLettered = attempts >= maxAttempts;
        event.setDeadLettered(deadLettered);
        event.setNextAttemptAt(deadLettered ? null : retryAt(event.getId(), attempts));
        log.error("Order outbox publish failed for event {} (attempt {}/{})",
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

package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoreOutboxPublisherServiceTest {
    private static final String PAYLOAD =
            "{\"orderId\":\"11111111-1111-1111-1111-111111111111\",\"success\":true}";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final StoreOutboxEventRepository repository = mock(StoreOutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private StoreOutboxPublisherService publisher(Duration budget) {
        return new StoreOutboxPublisherService(repository, kafkaTemplate, objectMapper, 50, 5, 1000, 60000, budget);
    }

    private StoreOutboxEvent event(String eventType) {
        StoreOutboxEvent event = new StoreOutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setPayload(PAYLOAD);
        event.setTopic("store.events");
        event.setCreatedAt(Instant.now());
        return event;
    }

    /** Behaves like a send the broker never acknowledges: it consumes exactly the time it was given. */
    private static final class StalledSend extends CompletableFuture<SendResult<String, Object>> {
        @Override
        public SendResult<String, Object> get(long timeout, TimeUnit unit)
                throws InterruptedException, TimeoutException {
            Thread.sleep(Math.max(0L, unit.toMillis(timeout)));
            throw new TimeoutException("broker unreachable");
        }
    }

    @Test
    void spendsOneTimeoutBudgetAcrossTheBatchRatherThanOnePerEvent() {
        List<StoreOutboxEvent> events = List.of(event("StockReservedEvent"), event("StockReservedEvent"),
                event("StockReservedEvent"), event("StockReservedEvent"));
        when(repository.lockReadyBatch(anyInt())).thenReturn(events);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> new StalledSend());

        long startedAt = System.nanoTime();
        publisher(Duration.ofMillis(300)).publishReadyEvents();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        // One shared deadline caps the whole batch near 300ms. A per-event timeout would spend 300ms four times
        // over, and would hold the transaction, and its row locks, for the whole of it.
        assertThat(elapsedMillis).isLessThan(900L);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.isPublished()).isFalse();
            assertThat(event.getAttemptCount()).isEqualTo(1);
            assertThat(event.getNextAttemptAt()).isNotNull();
        });
    }

    @Test
    void handsTheWholeBatchToTheProducerBeforeWaitingOnAnyOfIt() {
        List<StoreOutboxEvent> events = List.of(event("StockReservedEvent"), event("StockReservedEvent"),
                event("StockReservedEvent"));
        when(repository.lockReadyBatch(anyInt())).thenReturn(events);
        List<String> order = new ArrayList<>();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> {
            order.add("send");
            return new CompletableFuture<SendResult<String, Object>>() {
                @Override
                public SendResult<String, Object> get(long timeout, TimeUnit unit) {
                    order.add("await");
                    return null;
                }
            };
        });

        publisher(Duration.ofSeconds(5)).publishReadyEvents();

        assertThat(order).containsExactly("send", "send", "send", "await", "await", "await");
    }

    @Test
    void marksEverySuccessfulSendPublishedAndPersistsTheBatchOnce() {
        List<StoreOutboxEvent> events = List.of(event("StockReservedEvent"), event("StockReservedEvent"));
        when(repository.lockReadyBatch(anyInt())).thenReturn(events);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(null));

        publisher(Duration.ofSeconds(5)).publishReadyEvents();

        assertThat(events).allSatisfy(event -> {
            assertThat(event.isPublished()).isTrue();
            assertThat(event.getPublishedAt()).isNotNull();
            assertThat(event.getNextAttemptAt()).isNull();
            assertThat(event.getLastError()).isNull();
        });
        verify(repository).saveAll(events);
    }

    @Test
    void recordsAFailureWithoutSendingWhenThePayloadTypeIsNotOnTheAllowList() {
        List<StoreOutboxEvent> events = List.of(event("SomethingRemovedFromTheRegistry"));
        when(repository.lockReadyBatch(anyInt())).thenReturn(events);

        publisher(Duration.ofSeconds(5)).publishReadyEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertThat(events.get(0).getAttemptCount()).isEqualTo(1);
        assertThat(events.get(0).getLastError()).contains("SomethingRemovedFromTheRegistry");
    }

    @Test
    void touchesNothingWhenThereIsNoReadyWork() {
        when(repository.lockReadyBatch(anyInt())).thenReturn(List.of());

        publisher(Duration.ofSeconds(5)).publishReadyEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void deadLettersAnEventOnceItsAttemptsAreExhausted() {
        StoreOutboxEvent event = event("StockReservedEvent");
        event.setAttemptCount(4);
        when(repository.lockReadyBatch(anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> new StalledSend());

        publisher(Duration.ofMillis(50)).publishReadyEvents();

        assertThat(event.getAttemptCount()).isEqualTo(5);
        assertThat(event.isDeadLettered()).isTrue();
        assertThat(event.getNextAttemptAt()).isNull();
    }
}

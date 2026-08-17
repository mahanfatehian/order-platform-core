package com.orderprocessing.kafkacommon;

import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventCorrelationContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void bindsEventCorrelationIdForTheDurationOfTheHandler() {
        AtomicReference<String> observed = new AtomicReference<>();

        EventCorrelationContext.run(record(event("checkout-42")),
                () -> observed.set(MDC.get(EventCorrelationContext.MDC_KEY)));

        assertThat(observed).hasValue("checkout-42");
        assertThat(MDC.get(EventCorrelationContext.MDC_KEY)).isNull();
    }

    @Test
    void fallsBackToRecordCoordinatesWhenTheEventCarriesNoCorrelationId() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order.events", 3, 77L, "key", event(null));

        assertThat(EventCorrelationContext.resolve(record)).isEqualTo("order.events:3:77");
    }

    @Test
    void rejectsCorrelationIdsThatWouldForgeLogLines() {
        // The correlation id arrives inside a producer-controlled payload, so a
        // value that could inject a newline must never reach the log stream.
        assertThat(EventCorrelationContext.resolve(record(event("valid\ninjected INFO fake"))))
                .isEqualTo("order.events:0:1");
        assertThat(EventCorrelationContext.resolve(record(event(" "))))
                .isEqualTo("order.events:0:1");
        assertThat(EventCorrelationContext.resolve(record(event("x".repeat(129)))))
                .isEqualTo("order.events:0:1");
    }

    @Test
    void handlesRecordsCarryingSomethingOtherThanADomainEvent() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>("order.events", 1, 5L, "key", "not-an-event");

        assertThat(EventCorrelationContext.resolve(record)).isEqualTo("order.events:1:5");
    }

    @Test
    void restoresThePreviousCorrelationIdEvenWhenTheHandlerThrows() {
        MDC.put(EventCorrelationContext.MDC_KEY, "outer");

        assertThatThrownBy(() -> EventCorrelationContext.run(record(event("inner")), () -> {
            throw new IllegalStateException("handler failed");
        })).isInstanceOf(IllegalStateException.class);

        // A listener failure is retried by the container; leaking the failed
        // record's id would mislabel every later record on this thread.
        assertThat(MDC.get(EventCorrelationContext.MDC_KEY)).isEqualTo("outer");
    }

    private OrderPlacedEvent event(String correlationId) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setCorrelationId(correlationId);
        return event;
    }

    private ConsumerRecord<String, Object> record(Object value) {
        return new ConsumerRecord<>("order.events", 0, 1L, "key", value);
    }
}

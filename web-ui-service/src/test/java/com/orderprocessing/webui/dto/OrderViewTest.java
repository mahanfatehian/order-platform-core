package com.orderprocessing.webui.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderViewTest {

    @Test
    void fulfillmentStatesRemainInProgressUntilDelivery() {
        for (String status : List.of("PENDING", "CONFIRMED", "PACKAGED", "SHIPPED")) {
            OrderView order = order(status);
            assertThat(order.inProgress()).as(status).isTrue();
            assertThat(order.terminal()).as(status).isFalse();
        }

        assertThat(order("DELIVERED").inProgress()).isFalse();
        assertThat(order("DELIVERED").terminal()).isTrue();
    }

    @Test
    void failedAndCancelledAreTerminalWithoutCompletingTheSuccessTimeline() {
        for (String status : List.of("FAILED", "CANCELLED")) {
            OrderView order = order(status);
            assertThat(order.terminal()).as(status).isTrue();
            assertThat(order.inProgress()).as(status).isFalse();
            assertThat(order.lifecycleStage()).as(status).isZero();
            assertThat(order.reachedStage(1)).as(status).isFalse();
        }
    }

    @Test
    void lifecycleStagesFollowTheFiveStepFulfillmentSequence() {
        assertThat(order("PENDING").lifecycleStage()).isEqualTo(1);
        assertThat(order("CONFIRMED").lifecycleStage()).isEqualTo(2);
        assertThat(order("PACKAGED").lifecycleStage()).isEqualTo(3);
        assertThat(order("SHIPPED").lifecycleStage()).isEqualTo(4);
        assertThat(order("DELIVERED").lifecycleStage()).isEqualTo(5);
        assertThat(order("SHIPPED").reachedStage(3)).isTrue();
        assertThat(order("SHIPPED").reachedStage(5)).isFalse();
    }

    private OrderView order(String status) {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
        return new OrderView(UUID.randomUUID(), UUID.randomUUID(), status, BigDecimal.TEN,
                null, List.of(), now, now, "PENDING".equals(status) || "CONFIRMED".equals(status));
    }
}

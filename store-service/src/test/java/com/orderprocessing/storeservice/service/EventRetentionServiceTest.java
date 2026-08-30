package com.orderprocessing.storeservice.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final Duration OUTBOX_RETENTION = Duration.ofDays(30);
    private static final Duration INBOX_RETENTION = Duration.ofDays(14);
    private static final Instant OUTBOX_CUTOFF = NOW.minus(OUTBOX_RETENTION);
    private static final Instant INBOX_CUTOFF = NOW.minus(INBOX_RETENTION);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void limitsEachSourceToConfiguredMaximumBatches() {
        EventRetentionBatchService batches = mock(EventRetentionBatchService.class);
        when(batches.deleteOutboxBatch(OUTBOX_CUTOFF, 2)).thenReturn(2, 2, 2);
        when(batches.deleteInboxBatch(INBOX_CUTOFF, 2)).thenReturn(2, 2, 2);

        service(batches, 2, 3).clean();

        verify(batches, times(3)).deleteOutboxBatch(OUTBOX_CUTOFF, 2);
        verify(batches, times(3)).deleteInboxBatch(INBOX_CUTOFF, 2);
    }

    @Test
    void stopsOnlyAfterBothSourcesReturnZero() {
        EventRetentionBatchService batches = mock(EventRetentionBatchService.class);
        when(batches.deleteOutboxBatch(OUTBOX_CUTOFF, 2)).thenReturn(1, 0);
        when(batches.deleteInboxBatch(INBOX_CUTOFF, 2)).thenReturn(1, 0);

        service(batches, 2, 3).clean();

        var order = inOrder(batches);
        order.verify(batches).deleteOutboxBatch(OUTBOX_CUTOFF, 2);
        order.verify(batches).deleteInboxBatch(INBOX_CUTOFF, 2);
        order.verify(batches).deleteOutboxBatch(OUTBOX_CUTOFF, 2);
        order.verify(batches).deleteInboxBatch(INBOX_CUTOFF, 2);
        order.verifyNoMoreInteractions();
    }

    @Test
    void continuesWhenOnlyInboxReturnsRows() {
        EventRetentionBatchService batches = mock(EventRetentionBatchService.class);
        when(batches.deleteOutboxBatch(OUTBOX_CUTOFF, 2)).thenReturn(0, 0);
        when(batches.deleteInboxBatch(INBOX_CUTOFF, 2)).thenReturn(1, 0);

        service(batches, 2, 3).clean();

        verify(batches, times(2)).deleteOutboxBatch(OUTBOX_CUTOFF, 2);
        verify(batches, times(2)).deleteInboxBatch(INBOX_CUTOFF, 2);
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        EventRetentionBatchService batches = mock(EventRetentionBatchService.class);

        assertThrows(IllegalArgumentException.class,
                () -> new EventRetentionService(batches, OUTBOX_RETENTION, INBOX_RETENTION, 0, 3, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new EventRetentionService(batches, OUTBOX_RETENTION, INBOX_RETENTION, -1, 3, CLOCK));
    }

    @Test
    void rejectsNonPositiveMaximumBatches() {
        EventRetentionBatchService batches = mock(EventRetentionBatchService.class);

        assertThrows(IllegalArgumentException.class,
                () -> new EventRetentionService(batches, OUTBOX_RETENTION, INBOX_RETENTION, 2, 0, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new EventRetentionService(batches, OUTBOX_RETENTION, INBOX_RETENTION, 2, -1, CLOCK));
    }

    private EventRetentionService service(EventRetentionBatchService batches, int batchSize, int maxBatches) {
        return new EventRetentionService(
                batches, OUTBOX_RETENTION, INBOX_RETENTION, batchSize, maxBatches, CLOCK);
    }
}

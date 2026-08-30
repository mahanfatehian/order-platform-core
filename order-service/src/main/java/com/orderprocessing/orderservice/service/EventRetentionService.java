package com.orderprocessing.orderservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class EventRetentionService {
    private final EventRetentionBatchService batches;
    private final Duration outboxRetention;
    private final Duration inboxRetention;
    private final int batchSize;
    private final int maxBatchesPerRun;
    private final Clock clock;

    public EventRetentionService(EventRetentionBatchService batches,
                                 @Value("${maintenance.outbox-retention:P30D}") Duration outboxRetention,
                                 @Value("${maintenance.inbox-retention:P30D}") Duration inboxRetention,
                                 @Value("${maintenance.cleanup-batch-size:500}") int batchSize,
                                 @Value("${maintenance.cleanup-max-batches-per-run:20}") int maxBatchesPerRun) {
        this(batches, outboxRetention, inboxRetention, batchSize, maxBatchesPerRun, Clock.systemUTC());
    }

    EventRetentionService(EventRetentionBatchService batches,
                          Duration outboxRetention,
                          Duration inboxRetention,
                          int batchSize,
                          int maxBatchesPerRun,
                          Clock clock) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("cleanup batch size must be positive");
        }
        if (maxBatchesPerRun <= 0) {
            throw new IllegalArgumentException("cleanup maximum batches per run must be positive");
        }
        this.batches = batches;
        this.outboxRetention = outboxRetention;
        this.inboxRetention = inboxRetention;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
        this.clock = clock;
    }

    @Scheduled(cron = "${maintenance.cleanup-cron:0 15 3 * * *}")
    public void clean() {
        Instant now = clock.instant();
        Instant outboxCutoff = now.minus(outboxRetention);
        Instant inboxCutoff = now.minus(inboxRetention);
        int outboxTotal = 0;
        int inboxTotal = 0;
        for (int batch = 0; batch < maxBatchesPerRun; batch++) {
            int outboxRows = batches.deleteOutboxBatch(outboxCutoff, batchSize);
            int inboxRows = batches.deleteInboxBatch(inboxCutoff, batchSize);
            outboxTotal += outboxRows;
            inboxTotal += inboxRows;
            if (outboxRows == 0 && inboxRows == 0) {
                break;
            }
        }
        if (outboxTotal + inboxTotal > 0) {
            log.info("Removed {} published outbox rows and {} expired inbox rows", outboxTotal, inboxTotal);
        }
    }
}

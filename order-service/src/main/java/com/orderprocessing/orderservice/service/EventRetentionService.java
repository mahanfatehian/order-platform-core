package com.orderprocessing.orderservice.service;

import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class EventRetentionService {
    private final OutboxEventRepository outbox;
    private final ProcessedKafkaEventRepository inbox;
    private final Duration outboxRetention;
    private final Duration inboxRetention;

    public EventRetentionService(OutboxEventRepository outbox,
                                 ProcessedKafkaEventRepository inbox,
                                 @Value("${maintenance.outbox-retention:P30D}") Duration outboxRetention,
                                 @Value("${maintenance.inbox-retention:P30D}") Duration inboxRetention) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.outboxRetention = outboxRetention;
        this.inboxRetention = inboxRetention;
    }

    @Scheduled(cron = "${maintenance.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void clean() {
        Instant now = Instant.now();
        int outboxRows = outbox.deletePublishedBefore(now.minus(outboxRetention));
        int inboxRows = inbox.deleteProcessedBefore(now.minus(inboxRetention));
        if (outboxRows + inboxRows > 0) {
            log.info("Removed {} published outbox rows and {} expired inbox rows", outboxRows, inboxRows);
        }
    }
}

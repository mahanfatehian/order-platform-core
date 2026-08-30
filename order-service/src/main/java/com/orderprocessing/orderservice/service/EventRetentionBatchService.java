package com.orderprocessing.orderservice.service;

import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import com.orderprocessing.orderservice.repository.ProcessedKafkaEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class EventRetentionBatchService {
    private final OutboxEventRepository outbox;
    private final ProcessedKafkaEventRepository inbox;

    public EventRetentionBatchService(OutboxEventRepository outbox,
                                      ProcessedKafkaEventRepository inbox) {
        this.outbox = outbox;
        this.inbox = inbox;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteOutboxBatch(Instant cutoff, int batchSize) {
        return outbox.deletePublishedBatchBefore(cutoff, batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteInboxBatch(Instant cutoff, int batchSize) {
        return inbox.deleteProcessedBatchBefore(cutoff, batchSize);
    }
}

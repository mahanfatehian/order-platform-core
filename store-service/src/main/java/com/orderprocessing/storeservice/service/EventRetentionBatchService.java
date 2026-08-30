package com.orderprocessing.storeservice.service;

import com.orderprocessing.storeservice.repository.ProcessedKafkaEventRepository;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class EventRetentionBatchService {
    private final StoreOutboxEventRepository outbox;
    private final ProcessedKafkaEventRepository inbox;

    public EventRetentionBatchService(StoreOutboxEventRepository outbox,
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

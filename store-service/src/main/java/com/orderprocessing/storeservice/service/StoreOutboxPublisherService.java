package com.orderprocessing.storeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.kafkacommon.KafkaEventRegistry;
import com.orderprocessing.kafkacommon.event.DomainEvent;
import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import com.orderprocessing.storeservice.repository.StoreOutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StoreOutboxPublisherService {
    private final StoreOutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;

    public StoreOutboxPublisherService(StoreOutboxEventRepository repository,
                                       KafkaTemplate<String, Object> kafkaTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${outbox.batch-size:50}") int batchSize,
                                       @Value("${outbox.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval:1000}")
    @Transactional
    public void publishReadyEvents() {
        List<StoreOutboxEvent> events = repository.lockReadyBatch(batchSize);
        for (StoreOutboxEvent event : events) {
            try {
                DomainEvent payload = KafkaEventRegistry.deserialize(event.getEventType(), event.getPayload(), objectMapper);
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload).get(10, TimeUnit.SECONDS);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
            } catch (Exception ex) {
                int attempts = event.getAttemptCount() + 1;
                event.setAttemptCount(attempts);
                event.setLastError(truncate(ex.getMessage()));
                event.setDeadLettered(attempts >= maxAttempts);
                log.error("Store outbox publish failed for event {} (attempt {}/{})",
                        event.getId(), attempts, maxAttempts, ex);
            }
            repository.save(event);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown publisher failure";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}

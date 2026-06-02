package com.orderprocessing.orderservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderprocessing.orderservice.model.OutboxEvent;
import com.orderprocessing.orderservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisherService(OutboxEventRepository outboxEventRepository,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval:5000}")
    @Transactional
    public void publishUnpublishedEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : events) {
            try {
                log.info("Publishing outbox event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());
                String topic = "order.events";

                // Extract orderId from payload for the Kafka key
                JsonNode jsonNode = objectMapper.readTree(event.getPayload());
                String orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asText() : event.getAggregateId();

                // Blocking send ensures we only mark as published if Kafka acknowledges it
                kafkaTemplate.send(topic, orderId, event.getPayload()).get();

                event.setPublished(true);
                outboxEventRepository.save(event);
                log.info("Successfully published and marked as published: {}", event.getId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                // Do not mark as published; it will be retried on the next schedule
            }
        }
    }
}
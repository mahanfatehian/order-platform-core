package com.orderprocessing.orderservice.kafka;

import com.orderprocessing.orderservice.event.OrderFailedEvent;
import com.orderprocessing.orderservice.event.OrderConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class OrderKafkaProducer {

    private static final String ORDER_EVENTS_TOPIC = "order.events";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Sending OrderConfirmed event to topic {}: {}", ORDER_EVENTS_TOPIC, event);
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.getOrderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send OrderConfirmed event {} to topic {}: {}", 
                            event, ORDER_EVENTS_TOPIC, ex.getMessage());
                } else {
                    log.info("Successfully sent OrderConfirmed event {} to partition {} with offset {}", 
                            event, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                }
            });
    }

    public void sendOrderFailed(OrderFailedEvent event) {
        log.info("Sending OrderFailed event to topic {}: {}", ORDER_EVENTS_TOPIC, event);
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.getOrderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send OrderFailed event {} to topic {}: {}", 
                            event, ORDER_EVENTS_TOPIC, ex.getMessage());
                } else {
                    log.info("Successfully sent OrderFailed event {} to partition {} with offset {}", 
                            event, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                }
            });
    }
}
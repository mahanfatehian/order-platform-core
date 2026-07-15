package com.orderprocessing.orderservice.kafka;

import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderPackagedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import com.orderprocessing.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderKafkaConsumer {
    private final OrderService orderService;

    @KafkaListener(topics = KafkaTopics.STORE_EVENTS, groupId = "order-service")
    public void handleStoreEvent(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        if (event instanceof StockReservedEvent reserved) {
            orderService.processStockReserved(reserved, record.topic(), record.partition(), record.offset());
            return;
        }
        if (event instanceof StockInsufficientEvent insufficient) {
            orderService.processStockInsufficient(insufficient, record.topic(), record.partition(), record.offset());
            return;
        }
        log.debug("Ignoring store event type not consumed by order-service: {}",
                event == null ? "null" : event.getClass().getSimpleName());
    }

    @KafkaListener(topics = KafkaTopics.ORDER_EVENTS, groupId = "order-service")
    public void handleOrderEvent(ConsumerRecord<String, Object> record) {
        // These are the service's own past-tense facts. Routing them through the
        // inbox provides retry-safe acknowledgement only; OrderService never
        // treats Kafka messages as authorization to perform a human action.
        Object event = record.value();
        if (event instanceof OrderPackagedEvent packaged) {
            orderService.processOrderPackaged(packaged, record.topic(), record.partition(), record.offset());
            return;
        }
        if (event instanceof OrderShippedEvent shipped) {
            orderService.processOrderShipped(shipped, record.topic(), record.partition(), record.offset());
            return;
        }
        if (event instanceof OrderDeliveredEvent delivered) {
            orderService.processOrderDelivered(delivered, record.topic(), record.partition(), record.offset());
            return;
        }
        log.debug("Ignoring order event type not consumed by order-service: {}",
                event == null ? "null" : event.getClass().getSimpleName());
    }
}

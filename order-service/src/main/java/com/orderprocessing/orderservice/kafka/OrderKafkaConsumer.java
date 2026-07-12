package com.orderprocessing.orderservice.kafka;

import com.orderprocessing.kafkacommon.KafkaTopics;
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
}

package com.orderprocessing.storeservice.kafka;

import com.orderprocessing.kafkacommon.event.StockInsufficientEvent;
import com.orderprocessing.kafkacommon.event.StockReservedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StoreKafkaProducer {

    private static final String STORE_EVENTS_TOPIC = "store.events";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StoreKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendStockReserved(StockReservedEvent event) {
        log.info("Sending StockReserved event to topic {}: {}", STORE_EVENTS_TOPIC, event);
        kafkaTemplate.send(STORE_EVENTS_TOPIC, event.getOrderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send StockReserved event {} to topic {}: {}", 
                            event, STORE_EVENTS_TOPIC, ex.getMessage());
                } else {
                    log.info("Successfully sent StockReserved event {} to partition {} with offset {}", 
                            event, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                }
            });
    }

    public void sendStockInsufficient(StockInsufficientEvent event) {
        log.info("Sending StockInsufficient event to topic {}: {}", STORE_EVENTS_TOPIC, event);
        kafkaTemplate.send(STORE_EVENTS_TOPIC, event.getOrderId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send StockInsufficient event {} to topic {}: {}", 
                            event, STORE_EVENTS_TOPIC, ex.getMessage());
                } else {
                    log.info("Successfully sent StockInsufficient event {} to partition {} with offset {}", 
                            event, result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                }
            });
    }
}
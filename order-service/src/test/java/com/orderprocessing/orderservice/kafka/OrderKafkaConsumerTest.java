package com.orderprocessing.orderservice.kafka;

import com.orderprocessing.kafkacommon.KafkaTopics;
import com.orderprocessing.kafkacommon.event.OrderDeliveredEvent;
import com.orderprocessing.kafkacommon.event.OrderPackagedEvent;
import com.orderprocessing.kafkacommon.event.OrderPlacedEvent;
import com.orderprocessing.kafkacommon.event.OrderShippedEvent;
import com.orderprocessing.orderservice.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OrderKafkaConsumerTest {
    @Mock
    private OrderService orderService;

    @Test
    void routesHumanLifecycleEventsWithKafkaPosition() {
        OrderKafkaConsumer consumer = new OrderKafkaConsumer(orderService);
        OrderPackagedEvent packaged = new OrderPackagedEvent();
        OrderShippedEvent shipped = new OrderShippedEvent();
        OrderDeliveredEvent delivered = new OrderDeliveredEvent();

        consumer.handleOrderEvent(record(packaged, 0, 10L));
        consumer.handleOrderEvent(record(shipped, 1, 20L));
        consumer.handleOrderEvent(record(delivered, 2, 30L));

        verify(orderService).processOrderPackaged(packaged, KafkaTopics.ORDER_EVENTS, 0, 10L);
        verify(orderService).processOrderShipped(shipped, KafkaTopics.ORDER_EVENTS, 1, 20L);
        verify(orderService).processOrderDelivered(delivered, KafkaTopics.ORDER_EVENTS, 2, 30L);
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void ignoresOtherOrderEvents() {
        OrderKafkaConsumer consumer = new OrderKafkaConsumer(orderService);

        consumer.handleOrderEvent(record(new OrderPlacedEvent(), 0, 40L));

        verifyNoMoreInteractions(orderService);
    }

    private ConsumerRecord<String, Object> record(Object event, int partition, long offset) {
        return new ConsumerRecord<>(KafkaTopics.ORDER_EVENTS, partition, offset, "order-id", event);
    }
}

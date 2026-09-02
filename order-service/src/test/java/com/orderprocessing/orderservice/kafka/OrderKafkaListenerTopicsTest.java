package com.orderprocessing.orderservice.kafka;

import com.orderprocessing.kafkacommon.config.KafkaTopicConfig;
import com.orderprocessing.orderservice.service.OrderService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OrderKafkaListenerTopicsTest {

    @Test
    void resolvesBothOrderListenerDestinationsFromConfiguredTopicNames() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "kafka.topics.order-events", "orders.v2",
                    "kafka.topics.store-events", "stores.v2")));
            context.register(KafkaTopicConfig.class, ListenerTestConfiguration.class);
            context.refresh();

            assertThat(context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainers())
                    .extracting(container -> container.getContainerProperties().getTopics())
                    .containsExactlyInAnyOrder(new String[] {"orders.v2"}, new String[] {"stores.v2"});
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableKafka
    static class ListenerTestConfiguration {
        @Bean
        OrderKafkaConsumer orderKafkaConsumer() {
            return new OrderKafkaConsumer(mock(OrderService.class));
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
            ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(Map.of(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                    ConsumerConfig.GROUP_ID_CONFIG, "listener-topic-test",
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer",
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")));
            factory.setAutoStartup(false);
            return factory;
        }
    }
}

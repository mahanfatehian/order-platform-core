package com.orderprocessing.kafkacommon.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 3, topics = {
        "order.events",
        "order.events.order-service.dlt",
        "order.events.store-service.dlt",
        "order.events.dlt"
})
class ConsumerOwnedDeadLetterRoutingIntegrationTest {
    private final List<KafkaMessageListenerContainer<String, String>> containers = new ArrayList<>();
    private final List<Consumer<String, String>> consumers = new ArrayList<>();
    private DefaultKafkaProducerFactory<String, String> producerFactory;

    @Test
    void exhaustedFailuresFromTwoConsumerGroupsUseSeparateOwnedDltsWithRecoveryHeadersAndNoLegacyRecord(
            EmbeddedKafkaBroker broker) throws Exception {
        KafkaTemplate<String, String> template = kafkaTemplate(broker);
        KafkaMessageListenerContainer<String, String> orderContainer = failingContainer(
                broker, template, "order-service");
        KafkaMessageListenerContainer<String, String> storeContainer = failingContainer(
                broker, template, "store-service");

        orderContainer.start();
        storeContainer.start();
        ContainerTestUtils.waitForAssignment(orderContainer, 3);
        ContainerTestUtils.waitForAssignment(storeContainer, 3);

        template.send("order.events", 1, "order-1", "poison").get();

        ConsumerRecord<String, String> orderDlt = pollOne(broker, "order.events.order-service.dlt");
        ConsumerRecord<String, String> storeDlt = pollOne(broker, "order.events.store-service.dlt");

        assertThat(orderDlt.partition()).isEqualTo(1);
        assertThat(storeDlt.partition()).isEqualTo(1);
        assertRecovererHeaders(orderDlt, "order-service");
        assertRecovererHeaders(storeDlt, "store-service");

        assertThat(poll(broker, "order.events.dlt", Duration.ofMillis(750))).isEmpty();
    }

    @AfterEach
    void closeKafkaClients() {
        containers.forEach(KafkaMessageListenerContainer::stop);
        consumers.forEach(Consumer::close);
        if (producerFactory != null) {
            producerFactory.destroy();
        }
    }

    private KafkaTemplate<String, String> kafkaTemplate(EmbeddedKafkaBroker broker) {
        Map<String, Object> producerProperties = KafkaTestUtils.producerProps(broker);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerFactory = new DefaultKafkaProducerFactory<>(producerProperties);
        return new KafkaTemplate<>(producerFactory);
    }

    private KafkaMessageListenerContainer<String, String> failingContainer(
            EmbeddedKafkaBroker broker, KafkaTemplate<String, String> template, String groupId) {
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(groupId, "false", broker);
        ContainerProperties containerProperties = new ContainerProperties("order.events");
        containerProperties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, String>)
                record -> {
                    throw new IllegalStateException("poison");
                });

        KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(consumerProperties, new StringDeserializer(), new StringDeserializer()),
                containerProperties);
        container.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template, new ConsumerOwnedDeadLetterResolver(groupId)),
                new FixedBackOff(0L, 0L)));
        containers.add(container);
        return container;
    }

    private ConsumerRecord<String, String> pollOne(EmbeddedKafkaBroker broker, String topic) {
        List<ConsumerRecord<String, String>> records = poll(broker, topic, Duration.ofSeconds(10));
        assertThat(records).hasSize(1);
        return records.getFirst();
    }

    private List<ConsumerRecord<String, String>> poll(
            EmbeddedKafkaBroker broker, String topic, Duration timeout) {
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps("assert-" + topic, "false", broker);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer = new KafkaConsumer<>(consumerProperties);
        consumers.add(consumer);
        consumer.assign(List.of(
                new TopicPartition(topic, 0),
                new TopicPartition(topic, 1),
                new TopicPartition(topic, 2)));
        ConsumerRecords<String, String> records = consumer.poll(timeout);
        List<ConsumerRecord<String, String>> topicRecords = new ArrayList<>();
        records.records(topic).forEach(topicRecords::add);
        return topicRecords;
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private void assertRecovererHeaders(ConsumerRecord<?, ?> record, String consumerGroup) {
        assertThat(header(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo("order.events");
        assertThat(header(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP)).isEqualTo(consumerGroup);
        assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .contains("ListenerExecutionFailedException");
        assertThat(header(record, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .contains("IllegalStateException");
    }
}

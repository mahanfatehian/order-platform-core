package com.orderprocessing.kafkacommon.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    @Test
    void consumerOwnedTopicBeansProvisionAllActiveRoutesWhileKeepingLegacyDestinationsAvailable() {
        KafkaTopicConfig config = new KafkaTopicConfig("order.events", 3, 1, "store.events", 3, 1);

        List<NewTopic> deadLetterTopics = List.of(
                config.orderEventsOrderServiceDltTopic(),
                config.orderEventsStoreServiceDltTopic(),
                config.storeEventsOrderServiceDltTopic(),
                config.orderEventsDltTopic(),
                config.storeEventsDltTopic());

        assertThat(deadLetterTopics)
                .extracting(NewTopic::name)
                .containsExactly(
                        "order.events.order-service.dlt",
                        "order.events.store-service.dlt",
                        "store.events.order-service.dlt",
                        "order.events.dlt",
                        "store.events.dlt");
        assertThat(deadLetterTopics)
                .allSatisfy(topic -> {
                    assertThat(topic.numPartitions()).isEqualTo(3);
                    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
                });
    }
}

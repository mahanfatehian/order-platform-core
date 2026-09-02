package com.orderprocessing.kafkacommon.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    @Test
    void provisionsConfiguredRoutesAndTheirConsumerAndLegacyDeadLetterTopics() {
        KafkaTopicConfig config = new KafkaTopicConfig("orders.v2", 3, 1, "stores.v2", 3, 1);

        List<NewTopic> topics = List.of(
                config.orderEventsTopic(),
                config.storeEventsTopic(),
                config.orderEventsOrderServiceDltTopic(),
                config.orderEventsStoreServiceDltTopic(),
                config.storeEventsOrderServiceDltTopic(),
                config.orderEventsDltTopic(),
                config.storeEventsDltTopic());

        assertThat(topics)
                .extracting(NewTopic::name)
                .containsExactly(
                        "orders.v2",
                        "stores.v2",
                        "orders.v2.order-service.dlt",
                        "orders.v2.store-service.dlt",
                        "stores.v2.order-service.dlt",
                        "orders.v2.dlt",
                        "stores.v2.dlt");
        assertThat(topics)
                .allSatisfy(topic -> {
                    assertThat(topic.numPartitions()).isEqualTo(3);
                    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
                });
    }
}

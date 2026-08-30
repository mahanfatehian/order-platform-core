package com.orderprocessing.kafkacommon.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ConsumerOwnedDeadLetterResolverTest {

    @Test
    void applySeparatesConsumerFailuresFromTheSameSourceTopicWhileRetainingSourcePartition() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.events", 2, 17L, "order-1", "poison");

        TopicPartition orderDestination = new ConsumerOwnedDeadLetterResolver("order-service")
                .apply(record, new IllegalStateException("poison"));
        TopicPartition storeDestination = new ConsumerOwnedDeadLetterResolver("store-service")
                .apply(record, new IllegalStateException("poison"));

        assertThat(orderDestination).isEqualTo(new TopicPartition("order.events.order-service.dlt", 2));
        assertThat(storeDestination).isEqualTo(new TopicPartition("order.events.store-service.dlt", 2));
    }

    @Test
    void constructorRejectsInvalidOwnerInsteadOfDeferringBadDeadLetterDestinationUntilRecovery() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConsumerOwnedDeadLetterResolver("invalid/owner"))
                .withMessageContaining("consumerOwner");
    }
}

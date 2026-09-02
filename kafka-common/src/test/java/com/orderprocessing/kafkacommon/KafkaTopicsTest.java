package com.orderprocessing.kafkacommon;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KafkaTopicsTest {

    @org.junit.jupiter.api.Test
    void acceptsTopicNamesAtThePersistedRouteBoundary() {
        String topic = "a".repeat(200);

        assertThat(KafkaTopics.requireValidTopic(topic, "sourceTopic")).isEqualTo(topic);
    }

    @ParameterizedTest
    @MethodSource("invalidConfiguredSourceTopics")
    void rejectsConfiguredSourceTopicsThatKafkaOrTheEventTablesCannotStore(String topic) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KafkaTopics.requireValidTopic(topic, "sourceTopic"))
                .withMessageContaining("sourceTopic");
    }

    @org.junit.jupiter.api.Test
    void deadLetterTopicDerivesDistinctConsumerOwnedDestinationsInsteadOfSharedSourceDlt() {
        assertThat(KafkaTopics.deadLetterTopic("order.events", "order-service"))
                .isEqualTo("order.events.order-service.dlt");
        assertThat(KafkaTopics.deadLetterTopic("order.events", "store-service"))
                .isEqualTo("order.events.store-service.dlt");
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void deadLetterTopicRejectsInvalidSourceOrOwnerInsteadOfCreatingUnroutableKafkaDestination(
            String sourceTopic, String consumerOwner, String invalidArgument) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> KafkaTopics.deadLetterTopic(sourceTopic, consumerOwner))
                .withMessageContaining(invalidArgument);
    }

    private static Stream<Arguments> invalidComponents() {
        return Stream.of(
                Arguments.of(null, "order-service", "sourceTopic"),
                Arguments.of("", "order-service", "sourceTopic"),
                Arguments.of("   ", "order-service", "sourceTopic"),
                Arguments.of("order/events", "order-service", "sourceTopic"),
                Arguments.of("order:events", "order-service", "sourceTopic"),
                Arguments.of("order.events", null, "consumerOwner"),
                Arguments.of("order.events", "", "consumerOwner"),
                Arguments.of("order.events", "   ", "consumerOwner"),
                Arguments.of("order.events", "order/service", "consumerOwner"),
                Arguments.of("order.events", "order:service", "consumerOwner"),
                Arguments.of("a".repeat(240), "owner", "200-character persistence limit"));
    }

    private static Stream<String> invalidConfiguredSourceTopics() {
        return Stream.of("a".repeat(201), ".", "..");
    }
}

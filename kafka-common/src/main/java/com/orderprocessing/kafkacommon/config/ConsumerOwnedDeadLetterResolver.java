package com.orderprocessing.kafkacommon.config;

import com.orderprocessing.kafkacommon.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

import java.util.Objects;
import java.util.function.BiFunction;

public final class ConsumerOwnedDeadLetterResolver
        implements BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {
    private final String consumerOwner;

    public ConsumerOwnedDeadLetterResolver(String consumerOwner) {
        KafkaTopics.deadLetterTopic("validation", consumerOwner);
        this.consumerOwner = consumerOwner;
    }

    @Override
    public TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception) {
        Objects.requireNonNull(record, "consumerRecord");
        return new TopicPartition(
                KafkaTopics.deadLetterTopic(record.topic(), consumerOwner),
                record.partition());
    }
}

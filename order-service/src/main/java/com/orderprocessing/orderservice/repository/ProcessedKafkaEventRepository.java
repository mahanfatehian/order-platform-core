package com.orderprocessing.orderservice.repository;

import com.orderprocessing.orderservice.model.ProcessedKafkaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, UUID> {
    @Modifying
    @Query(value = """
            insert into processed_kafka_events
                (event_id, event_type, topic, partition_number, record_offset, processed_at)
            values (:eventId, :eventType, :topic, :partitionNumber, :recordOffset, now())
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("eventType") String eventType,
                       @Param("topic") String topic,
                       @Param("partitionNumber") int partitionNumber,
                       @Param("recordOffset") long recordOffset);
}

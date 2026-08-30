package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.ProcessedKafkaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.time.Instant;

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

    @Modifying
    @Query(value = """
            WITH candidates AS (
                SELECT event_id
                FROM processed_kafka_events
                WHERE processed_at < :cutoff
                ORDER BY processed_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            DELETE FROM processed_kafka_events event
            USING candidates
            WHERE event.event_id = candidates.event_id
            """, nativeQuery = true)
    int deleteProcessedBatchBefore(@Param("cutoff") Instant cutoff,
                                   @Param("batchSize") int batchSize);
}

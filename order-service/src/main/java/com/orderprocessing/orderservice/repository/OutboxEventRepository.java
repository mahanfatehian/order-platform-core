package com.orderprocessing.orderservice.repository;

import com.orderprocessing.orderservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    long countByPublishedFalseAndDeadLetteredFalse();
    long countByDeadLetteredTrue();

    @Modifying
    @Query(value = "delete from outbox_events where published = true and published_at < :cutoff",
            nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

    @Query(value = """
            select e.* from outbox_events e
            where e.published = false
              and e.dead_lettered = false
              and (e.next_attempt_at is null or e.next_attempt_at <= now())
              and not exists (
                  select 1 from outbox_events older
                  where older.aggregate_id = e.aggregate_id
                    and older.published = false
                    and (older.created_at < e.created_at
                         or (older.created_at = e.created_at and older.id::text < e.id::text))
              )
            order by e.created_at, e.id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockReadyBatch(@Param("batchSize") int batchSize);
}

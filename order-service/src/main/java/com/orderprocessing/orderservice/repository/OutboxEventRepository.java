package com.orderprocessing.orderservice.repository;

import com.orderprocessing.orderservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            select e.* from outbox_events e
            where e.published = false
              and e.dead_lettered = false
              and not exists (
                  select 1 from outbox_events older
                  where older.aggregate_id = e.aggregate_id
                    and older.published = false
                    and older.dead_lettered = false
                    and (older.created_at < e.created_at
                         or (older.created_at = e.created_at and older.id::text < e.id::text))
              )
            order by e.created_at, e.id
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockReadyBatch(@Param("batchSize") int batchSize);
}

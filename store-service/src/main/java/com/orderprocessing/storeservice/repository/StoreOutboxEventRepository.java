package com.orderprocessing.storeservice.repository;

import com.orderprocessing.storeservice.model.StoreOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StoreOutboxEventRepository extends JpaRepository<StoreOutboxEvent, UUID> {
    @Query(value = """
            select e.* from store_outbox_events e
            where e.published = false
              and e.dead_lettered = false
              and not exists (
                  select 1 from store_outbox_events older
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
    List<StoreOutboxEvent> lockReadyBatch(@Param("batchSize") int batchSize);
}

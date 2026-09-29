package com.payflow.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            select * from outbox_events
            where published = false
            order by id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(int limit);
}

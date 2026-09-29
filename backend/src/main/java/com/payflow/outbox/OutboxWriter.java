package com.payflow.outbox;

import com.payflow.common.Jsons;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxWriter {

    private final OutboxEventRepository events;
    private final Jsons jsons;
    private final Clock clock;

    public OutboxWriter(OutboxEventRepository events, Jsons jsons, Clock clock) {
        this.events = events;
        this.jsons = jsons;
        this.clock = clock;
    }

    public void enqueue(UUID aggregateId, String eventType, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(jsons.write(payload));
        event.setPublished(false);
        event.setCreatedAt(clock.instant());
        events.save(event);
    }
}

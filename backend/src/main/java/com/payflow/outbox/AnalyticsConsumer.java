package com.payflow.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsConsumer implements OutboxConsumer {

    private final MeterRegistry meters;
    private final Set<Long> seen = ConcurrentHashMap.newKeySet();

    public AnalyticsConsumer(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    public void onEvent(OutboxEvent event) {
        if (event.getId() != null && !seen.add(event.getId())) {
            return;
        }
        meters.counter("outbox_events_consumed", "type", event.getEventType()).increment();
    }
}

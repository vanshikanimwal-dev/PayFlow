package com.payflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer implements OutboxConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @Override
    public void onEvent(OutboxEvent event) {
        log.info("push notification sent type={} aggregate={}", event.getEventType(), event.getAggregateId());
    }
}

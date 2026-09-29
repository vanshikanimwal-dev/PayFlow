package com.payflow.outbox;

import com.payflow.config.PayflowProperties;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final EntityManager entityManager;
    private final List<OutboxConsumer> consumers;
    private final ObjectProvider<RabbitTemplate> rabbit;
    private final PayflowProperties properties;
    private final Clock clock;

    public OutboxPublisher(
            EntityManager entityManager,
            List<OutboxConsumer> consumers,
            ObjectProvider<RabbitTemplate> rabbit,
            PayflowProperties properties,
            Clock clock) {
        this.entityManager = entityManager;
        this.consumers = consumers;
        this.rabbit = rabbit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int publishBatch() {
        @SuppressWarnings("unchecked")
        List<OutboxEvent> events = entityManager.createNativeQuery("""
                        select * from outbox_events
                        where published = false
                        order by id
                        limit 100
                        for update skip locked
                        """, OutboxEvent.class)
                .getResultList();
        for (OutboxEvent event : events) {
            dispatch(event);
            event.setPublished(true);
            event.setPublishedAt(clock.instant());
        }
        return events.size();
    }

    private void dispatch(OutboxEvent event) {
        if (properties.getOutbox().rabbit()) {
            RabbitTemplate template = rabbit.getIfAvailable();
            if (template == null) {
                throw new IllegalStateException("RabbitMQ broker is enabled but RabbitTemplate is missing");
            }
            template.convertAndSend("payflow.events", event.getEventType(), event.getPayload());
            return;
        }
        for (OutboxConsumer consumer : consumers) {
            try {
                consumer.onEvent(event);
            } catch (RuntimeException ex) {
                log.warn("Outbox consumer failed for event {}", event.getId(), ex);
                throw ex;
            }
        }
    }
}

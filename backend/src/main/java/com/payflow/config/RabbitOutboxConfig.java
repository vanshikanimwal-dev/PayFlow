package com.payflow.config;

import com.payflow.outbox.OutboxConsumer;
import com.payflow.outbox.OutboxEvent;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;

@Configuration
@ConditionalOnProperty(name = "payflow.outbox.broker", havingValue = "rabbit")
public class RabbitOutboxConfig {

    public static final String EXCHANGE = "payflow.events";

    @Bean
    TopicExchange payflowEvents() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue payflowEventQueue() {
        return new Queue("payflow.events.queue", true);
    }

    @Bean
    Binding payflowEventBinding(Queue payflowEventQueue, TopicExchange payflowEvents) {
        return BindingBuilder.bind(payflowEventQueue).to(payflowEvents).with("#");
    }

    @Bean
    RabbitOutboxListener rabbitOutboxListener(List<OutboxConsumer> consumers) {
        return new RabbitOutboxListener(consumers);
    }

    static final class RabbitOutboxListener {

        private final List<OutboxConsumer> consumers;

        RabbitOutboxListener(List<OutboxConsumer> consumers) {
            this.consumers = consumers;
        }

        @RabbitListener(queues = "payflow.events.queue")
        public void onMessage(String payload, @Header(name = "amqp_receivedRoutingKey", required = false) String eventType) {
            OutboxEvent event = new OutboxEvent();
            event.setEventType(eventType == null ? "unknown" : eventType);
            event.setPayload(payload);
            for (OutboxConsumer consumer : consumers) {
                consumer.onEvent(event);
            }
        }
    }
}

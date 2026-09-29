package com.payflow.outbox;

public interface OutboxConsumer {

    void onEvent(OutboxEvent event);
}

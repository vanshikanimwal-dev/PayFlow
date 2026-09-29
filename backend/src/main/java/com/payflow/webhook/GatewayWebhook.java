package com.payflow.webhook;

public record GatewayWebhook(
        String eventId, String type, String paymentId, long amountMinor, String reference, String createdAt) {
}

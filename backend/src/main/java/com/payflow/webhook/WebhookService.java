package com.payflow.webhook;

import com.payflow.common.ErrorCode;
import com.payflow.common.Jsons;
import com.payflow.common.PayflowException;
import com.payflow.payment.TopUpCompletionService;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {

    private final EntityManager entityManager;
    private final Jsons jsons;
    private final TopUpCompletionService completion;
    private final Clock clock;

    public WebhookService(EntityManager entityManager, Jsons jsons, TopUpCompletionService completion, Clock clock) {
        this.entityManager = entityManager;
        this.jsons = jsons;
        this.completion = completion;
        this.clock = clock;
    }

    @Transactional
    public void handle(byte[] raw) {
        String json = new String(raw, StandardCharsets.UTF_8);
        GatewayWebhook event = jsons.read(json, GatewayWebhook.class);
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, org.springframework.http.HttpStatus.BAD_REQUEST, "eventId is required");
        }
        int inserted = entityManager.createNativeQuery("""
                        insert into webhook_events (event_id, received_at, payload, processed)
                        values (:id, :now, cast(:payload as jsonb), true)
                        on conflict (event_id) do nothing
                        """)
                .setParameter("id", event.eventId())
                .setParameter("now", clock.instant())
                .setParameter("payload", json)
                .executeUpdate();
        if (inserted == 0) {
            return;
        }
        UUID txId;
        try {
            txId = UUID.fromString(event.reference());
        } catch (RuntimeException ex) {
            return;
        }
        try {
            switch (event.type() == null ? "" : event.type()) {
                case "payment.succeeded" -> completion.complete(txId, event.amountMinor(), event.paymentId());
                case "payment.failed" -> completion.fail(txId, "gateway payment.failed");
                default -> {
                    // Unknown or refund events are stored so the gateway does not retry forever.
                }
            }
        } catch (PayflowException ex) {
            if (ex.code() != ErrorCode.NOT_FOUND) {
                throw ex;
            }
        }
    }
}

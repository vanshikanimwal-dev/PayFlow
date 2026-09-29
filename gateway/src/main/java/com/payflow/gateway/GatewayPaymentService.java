package com.payflow.gateway;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GatewayPaymentService {

    private final GatewayPaymentRepository payments;
    private final WebhookDispatcher webhooks;

    public GatewayPaymentService(GatewayPaymentRepository payments, WebhookDispatcher webhooks) {
        this.payments = payments;
        this.webhooks = webhooks;
    }

    @Transactional
    public GatewayPaymentEntity create(String idempotencyKey, String reference, long amountMinor, String method, String callbackUrl) {
        Optional<GatewayPaymentEntity> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        GatewayPaymentEntity payment = new GatewayPaymentEntity();
        payment.setId("pi_" + UUID.randomUUID().toString().replace("-", ""));
        payment.setReference(reference);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmountMinor(amountMinor);
        payment.setMethod(method);
        payment.setStatus("PENDING");
        payment.setCallbackUrl(callbackUrl);
        payment.setCreatedAt(Instant.now());
        return payments.save(payment);
    }

    @Transactional
    public GatewayPaymentEntity complete(String id, boolean success) {
        GatewayPaymentEntity payment = payments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if ("PENDING".equals(payment.getStatus())) {
            payment.setStatus(success ? "CAPTURED" : "FAILED");
            payment.setCapturedAt(success ? Instant.now() : null);
            webhooks.send(payment.getCallbackUrl(), success ? "payment.succeeded" : "payment.failed", payment);
        }
        return payment;
    }

    @Transactional
    public GatewayPaymentEntity refund(String paymentId, long amountMinor) {
        GatewayPaymentEntity payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"CAPTURED".equals(payment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Payment is not captured");
        }
        if (payment.getRefundedMinor() + amountMinor > payment.getAmountMinor()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Refund exceeds the capture");
        }
        payment.setRefundedMinor(payment.getRefundedMinor() + amountMinor);
        webhooks.send(payment.getCallbackUrl(), "refund.succeeded", payment);
        return payment;
    }

    public String settlementCsv(LocalDate date, ChaosService chaos) {
        StringBuilder csv = new StringBuilder("payment_id,reference,amount_minor,status,captured_at\n");
        for (GatewayPaymentEntity payment : payments.findByStatus("CAPTURED")) {
            if (payment.getCapturedAt() == null) {
                continue;
            }
            LocalDate captured = payment.getCapturedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (!captured.equals(date)) {
                continue;
            }
            long amount = payment.getAmountMinor();
            String status = payment.getStatus();
            if (chaos.drift()) {
                amount = amount + 1;
            }
            csv.append(payment.getId()).append(',')
                    .append(payment.getReference()).append(',')
                    .append(amount).append(',')
                    .append(status).append(',')
                    .append(payment.getCapturedAt()).append('\n');
        }
        return csv.toString();
    }
}

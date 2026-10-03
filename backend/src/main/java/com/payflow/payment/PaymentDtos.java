package com.payflow.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CreatePaymentRequest(@Positive long amountMinor, @Size(max = 255) String description) {
    }

    public record PaymentRequestView(
            UUID id, String qrPayload, Instant expiresAt, String status, long amountMinor, String description) {
    }

    public record PayRequest(@NotNull UUID paymentRequestId) {
    }

    public record PaymentResponse(UUID transactionId, String status, long balanceAfterMinor) {
    }

    public record RefundRequest(@NotNull UUID transactionId, @Positive long amountMinor) {
    }

    public record RefundResponse(UUID transactionId, String status, long refundedMinor) {
    }

    public record Quote(long amountMinor, long feeMinor, long merchantMinor, long cashbackMinor) {
    }
}

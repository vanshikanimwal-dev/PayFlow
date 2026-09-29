package com.payflow.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public final class TopUpDtos {

    private TopUpDtos() {
    }

    public record TopUpRequest(@Positive long amountMinor, @NotNull PaymentMethod method) {
    }

    public record TopUpResponse(UUID transactionId, String status, String paymentUrl) {
    }
}

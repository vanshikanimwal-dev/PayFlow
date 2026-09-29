package com.payflow.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record TransferRequest(
            @NotBlank String toUserEmailOrPhone,
            @Positive long amountMinor,
            @Size(max = 255) String note) {
    }

    public record TransferResponse(UUID transactionId, String status, long balanceAfterMinor) {
    }
}

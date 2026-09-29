package com.payflow.transaction;

import com.payflow.ledger.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record WalletResponse(UUID accountId, long balanceMinor, String currency) {
    }

    public record TransactionSummary(
            UUID id,
            String type,
            String status,
            long amountMinor,
            String currency,
            Instant createdAt,
            UUID fromAccountId,
            UUID toAccountId) {
    }

    public record LedgerEntryView(UUID accountId, Direction direction, long amountMinor, long balanceAfter) {
    }

    public record TransactionDetail(
            UUID id,
            String type,
            String status,
            long amountMinor,
            String currency,
            Instant createdAt,
            UUID fromAccountId,
            UUID toAccountId,
            long refundedMinor,
            UUID reversalOf,
            String gatewayRef,
            String failureReason,
            String idempotencyKey,
            List<LedgerEntryView> entries) {
    }

    public record TransactionPage(List<TransactionSummary> items, String nextCursor) {
    }
}

package com.payflow.admin;

import com.payflow.audit.AuditLog;
import com.payflow.reconciliation.ReconciliationItem;
import com.payflow.reconciliation.ReconciliationKind;
import com.payflow.reconciliation.ReconciliationRun;
import com.payflow.reconciliation.Resolution;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AdminViews {

    private AdminViews() {
    }

    public record TransactionView(
            UUID id,
            TransactionType type,
            TransactionStatus status,
            long amountMinor,
            String currency,
            UUID initiatorId,
            UUID fromAccountId,
            UUID toAccountId,
            UUID reversalOf,
            long refundedMinor,
            String gatewayRef,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ReconciliationRunView(
            UUID id,
            LocalDate runDate,
            Instant startedAt,
            Instant finishedAt,
            Integer matched,
            Integer mismatched,
            String status) {
    }

    public record ReconciliationItemView(
            Long id,
            UUID runId,
            String gatewayRef,
            UUID transactionId,
            ReconciliationKind kind,
            Long gatewayAmount,
            Long ledgerAmount,
            Resolution resolution,
            String note) {
    }

    public record AuditView(
            Long id,
            UUID actorId,
            String actorRole,
            String action,
            String entity,
            String entityId,
            String beforeState,
            String afterState,
            String correlationId,
            String prevHash,
            String hash,
            Instant createdAt) {
    }

    public static TransactionView transaction(WalletTransaction tx) {
        return new TransactionView(
                tx.getId(),
                tx.getType(),
                tx.getStatus(),
                tx.getAmountMinor(),
                tx.getCurrency(),
                tx.getInitiatorId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getReversalOf(),
                tx.getRefundedMinor(),
                tx.getGatewayRef(),
                tx.getFailureReason(),
                tx.getCreatedAt(),
                tx.getUpdatedAt());
    }

    public static ReconciliationRunView run(ReconciliationRun run) {
        return new ReconciliationRunView(
                run.getId(),
                run.getRunDate(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getMatched(),
                run.getMismatched(),
                run.getStatus());
    }

    public static ReconciliationItemView item(ReconciliationItem item) {
        return new ReconciliationItemView(
                item.getId(),
                item.getRunId(),
                item.getGatewayRef(),
                item.getTransactionId(),
                item.getKind(),
                item.getGatewayAmount(),
                item.getLedgerAmount(),
                item.getResolution(),
                item.getNote());
    }

    public static AuditView audit(AuditLog row) {
        return new AuditView(
                row.getId(),
                row.getActorId(),
                row.getActorRole(),
                row.getAction(),
                row.getEntity(),
                row.getEntityId(),
                row.getBeforeState(),
                row.getAfterState(),
                row.getCorrelationId(),
                row.getPrevHash(),
                row.getHash(),
                row.getCreatedAt());
    }
}

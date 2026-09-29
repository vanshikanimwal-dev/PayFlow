package com.payflow.payment;

import com.payflow.account.AccountLocker;
import com.payflow.audit.AuditService;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.common.SystemAccounts;
import com.payflow.ledger.LedgerService;
import com.payflow.ledger.Leg;
import com.payflow.outbox.OutboxWriter;
import com.payflow.account.Account;
import com.payflow.reconciliation.MismatchRecorder;
import com.payflow.reconciliation.ReconciliationKind;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopUpCompletionService {

    private final WalletTransactionRepository transactions;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final MismatchRecorder mismatches;
    private final Clock clock;

    public TopUpCompletionService(
            WalletTransactionRepository transactions,
            AccountLocker locker,
            LedgerService ledger,
            AuditService audit,
            OutboxWriter outbox,
            MismatchRecorder mismatches,
            Clock clock) {
        this.transactions = transactions;
        this.locker = locker;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.mismatches = mismatches;
        this.clock = clock;
    }

    @Transactional
    public boolean complete(UUID txId, long gatewayAmount, String paymentId) {
        WalletTransaction peeked = transactions.findById(txId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        if (peeked.getType() != TransactionType.TOPUP) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Transaction is not a top-up");
        }
        if (peeked.getStatus() == TransactionStatus.COMPLETED || peeked.getStatus() == TransactionStatus.REVERSED) {
            return false;
        }
        if (peeked.getAmountMinor() != gatewayAmount) {
            mismatches.flag(peeked, ReconciliationKind.AMOUNT_MISMATCH, gatewayAmount, peeked.getAmountMinor(),
                    "Webhook amount does not match the top-up");
            return false;
        }
        List<Account> locked = locker.lock(List.of(peeked.getToAccountId(), SystemAccounts.GATEWAY));
        WalletTransaction tx = transactions.lockById(txId).orElseThrow();
        if (tx.getStatus() == TransactionStatus.COMPLETED || tx.getStatus() == TransactionStatus.REVERSED) {
            return false;
        }
        if (tx.getAmountMinor() != gatewayAmount) {
            mismatches.flag(tx, ReconciliationKind.AMOUNT_MISMATCH, gatewayAmount, tx.getAmountMinor(),
                    "Webhook amount does not match the top-up");
            return false;
        }
        ledger.post(tx, locked, List.of(
                Leg.debit(SystemAccounts.GATEWAY, gatewayAmount),
                Leg.credit(tx.getToAccountId(), gatewayAmount)));
        Instant now = clock.instant();
        if (tx.getStatus() == TransactionStatus.PENDING) {
            tx.transitionTo(TransactionStatus.PROCESSING, now);
        }
        if (tx.getStatus() == TransactionStatus.FAILED || tx.getStatus() == TransactionStatus.PROCESSING) {
            tx.transitionTo(TransactionStatus.COMPLETED, now);
        }
        if (paymentId != null && !paymentId.isBlank()) {
            tx.setGatewayRef(paymentId);
        }
        tx.setFailureReason(null);
        audit.record(tx.getInitiatorId(), null, "TOPUP_COMPLETED", "transaction", tx.getId().toString(), null,
                Map.of("amountMinor", gatewayAmount, "gatewayRef", tx.getGatewayRef() == null ? "" : tx.getGatewayRef()));
        outbox.enqueue(tx.getId(), "TOPUP_COMPLETED", Map.of("transactionId", tx.getId().toString(), "amountMinor", gatewayAmount));
        return true;
    }

    @Transactional
    public void fail(UUID txId, String reason) {
        WalletTransaction tx = transactions.lockById(txId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        if (tx.getStatus() == TransactionStatus.COMPLETED
                || tx.getStatus() == TransactionStatus.REVERSED
                || tx.getStatus() == TransactionStatus.FAILED) {
            return;
        }
        tx.transitionTo(TransactionStatus.FAILED, clock.instant());
        tx.setFailureReason(reason == null ? null : reason.substring(0, Math.min(255, reason.length())));
        audit.record(tx.getInitiatorId(), null, "TOPUP_FAILED", "transaction", tx.getId().toString(), null,
                Map.of("reason", tx.getFailureReason() == null ? "" : tx.getFailureReason()));
    }
}

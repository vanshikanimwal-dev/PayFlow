package com.payflow.payment;

import com.payflow.account.Account;
import com.payflow.account.AccountLocker;
import com.payflow.audit.AuditService;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.common.SystemAccounts;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.ledger.LedgerService;
import com.payflow.ledger.Leg;
import com.payflow.outbox.OutboxWriter;
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

/** Posts the wallet side of a card refund. Safe to call again after the webhook arrives. */
@Service
public class TopUpRefundCompletion {

    private final WalletTransactionRepository transactions;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final IdempotencyService idempotency;
    private final Clock clock;

    public TopUpRefundCompletion(
            WalletTransactionRepository transactions,
            AccountLocker locker,
            LedgerService ledger,
            AuditService audit,
            OutboxWriter outbox,
            IdempotencyService idempotency,
            Clock clock) {
        this.transactions = transactions;
        this.locker = locker;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional
    public PaymentDtos.RefundResponse complete(UUID refundId) {
        WalletTransaction peeked = transactions.findById(refundId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        if (peeked.getStatus() == TransactionStatus.COMPLETED) {
            return response(peeked);
        }
        List<Account> locked = locker.lock(List.of(peeked.getFromAccountId(), SystemAccounts.GATEWAY));
        WalletTransaction refund = transactions.lockById(refundId).orElseThrow();
        if (refund.getStatus() == TransactionStatus.COMPLETED) {
            return response(refund);
        }
        WalletTransaction original = transactions.lockById(refund.getReversalOf()).orElseThrow();
        long remaining = original.getAmountMinor() - original.getRefundedMinor();
        if (refund.getAmountMinor() > remaining) {
            throw new PayflowException(ErrorCode.ALREADY_REFUNDED, HttpStatus.UNPROCESSABLE_ENTITY, "Refund exceeds the remaining amount");
        }
        ledger.post(refund, locked, List.of(
                Leg.debit(refund.getFromAccountId(), refund.getAmountMinor()),
                Leg.credit(SystemAccounts.GATEWAY, refund.getAmountMinor())));
        Instant now = clock.instant();
        if (refund.getStatus() == TransactionStatus.PENDING) {
            refund.transitionTo(TransactionStatus.PROCESSING, now);
        }
        refund.transitionTo(TransactionStatus.COMPLETED, now);
        original.setRefundedMinor(original.getRefundedMinor() + refund.getAmountMinor());
        if (original.getRefundedMinor() == original.getAmountMinor()) {
            original.transitionTo(TransactionStatus.REVERSED, now);
        }
        PaymentDtos.RefundResponse body = response(refund);
        audit.record(refund.getInitiatorId(), null, "TOPUP_REFUND", "transaction", refund.getId().toString(), null,
                Map.of("originalId", original.getId().toString(), "amountMinor", refund.getAmountMinor()));
        outbox.enqueue(refund.getId(), "REFUND_COMPLETED", Map.of(
                "transactionId", refund.getId().toString(), "amountMinor", refund.getAmountMinor()));
        idempotency.complete(refund.getInitiatorId(), refund.getIdempotencyKey(), 201, body);
        return body;
    }

    @Transactional
    public void completeByOriginal(UUID topUpId) {
        transactions.findOpenRefund(topUpId).ifPresent(refund -> complete(refund.getId()));
    }

    private static PaymentDtos.RefundResponse response(WalletTransaction refund) {
        long reported = refund.getType() == TransactionType.REFUND ? refund.getAmountMinor() : 0;
        return new PaymentDtos.RefundResponse(refund.getId(), refund.getStatus().name(), reported);
    }
}

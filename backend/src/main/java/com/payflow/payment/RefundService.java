package com.payflow.payment;

import com.payflow.account.Account;
import com.payflow.account.AccountLocker;
import com.payflow.account.AccountRepository;
import com.payflow.audit.AuditService;
import com.payflow.auth.UserRole;
import com.payflow.common.ErrorCode;
import com.payflow.common.Fees;
import com.payflow.common.IdGenerator;
import com.payflow.common.PayflowException;
import com.payflow.common.SystemAccounts;
import com.payflow.idempotency.BeginResult;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.ledger.Direction;
import com.payflow.ledger.LedgerEntry;
import com.payflow.ledger.LedgerEntryRepository;
import com.payflow.ledger.LedgerService;
import com.payflow.ledger.Leg;
import com.payflow.outbox.OutboxWriter;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

    private final IdempotencyService idempotency;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;
    private final AccountLocker locker;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final IdGenerator ids;
    private final Clock clock;

    public RefundService(
            IdempotencyService idempotency,
            WalletTransactionRepository transactions,
            LedgerEntryRepository entries,
            AccountRepository accounts,
            AccountLocker locker,
            LedgerService ledger,
            AuditService audit,
            OutboxWriter outbox,
            IdGenerator ids,
            Clock clock) {
        this.idempotency = idempotency;
        this.transactions = transactions;
        this.entries = entries;
        this.accounts = accounts;
        this.locker = locker;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public PaymentDtos.RefundResponse refund(UUID userId, UserRole role, String key, String hash, PaymentDtos.RefundRequest request) {
        BeginResult<PaymentDtos.RefundResponse> begin =
                idempotency.begin(userId, key, hash, PaymentDtos.RefundResponse.class);
        if (begin instanceof BeginResult.Replay<PaymentDtos.RefundResponse> replay) {
            return replay.body();
        }
        WalletTransaction original = transactions.findById(request.transactionId())
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        if (original.getType() != TransactionType.PAYMENT && original.getType() != TransactionType.TRANSFER) {
            throw new PayflowException(
                    ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Only payments and transfers can be refunded");
        }
        if (original.getStatus() != TransactionStatus.COMPLETED) {
            throw new PayflowException(ErrorCode.ALREADY_REFUNDED, HttpStatus.UNPROCESSABLE_ENTITY, "Transaction cannot be refunded");
        }
        Account destination = accounts.findById(original.getToAccountId()).orElseThrow();
        boolean allowed = role == UserRole.ADMIN || (destination.getOwnerId() != null && destination.getOwnerId().equals(userId));
        if (!allowed) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You do not have access to this resource");
        }
        long remaining = original.getAmountMinor() - original.getRefundedMinor();
        if (request.amountMinor() > remaining) {
            throw new PayflowException(ErrorCode.ALREADY_REFUNDED, HttpStatus.UNPROCESSABLE_ENTITY, "Refund exceeds the remaining amount");
        }
        long originalFee = feeCollected(original.getId());
        long feeAlready = feeAlreadyRefunded(original.getId());
        long feePart = request.amountMinor() == remaining
                ? originalFee - feeAlready
                : Fees.proportionHalfUp(request.amountMinor(), originalFee, original.getAmountMinor());
        if (feePart < 0) {
            feePart = 0;
        }
        long destinationPart = request.amountMinor() - feePart;
        List<UUID> accountIds = new ArrayList<>();
        accountIds.add(original.getFromAccountId());
        accountIds.add(original.getToAccountId());
        if (feePart > 0) {
            accountIds.add(SystemAccounts.FEE);
        }
        List<Account> locked = locker.lock(accountIds);
        WalletTransaction lockedOriginal = transactions.lockById(original.getId()).orElseThrow();
        long stillRemaining = lockedOriginal.getAmountMinor() - lockedOriginal.getRefundedMinor();
        if (lockedOriginal.getStatus() != TransactionStatus.COMPLETED || request.amountMinor() > stillRemaining) {
            throw new PayflowException(ErrorCode.ALREADY_REFUNDED, HttpStatus.UNPROCESSABLE_ENTITY, "Refund exceeds the remaining amount");
        }
        Instant now = clock.instant();
        WalletTransaction refund = new WalletTransaction();
        refund.setId(ids.newId());
        refund.setType(TransactionType.REFUND);
        refund.setStatus(TransactionStatus.PENDING);
        refund.setAmountMinor(request.amountMinor());
        refund.setCurrency("INR");
        refund.setInitiatorId(userId);
        refund.setFromAccountId(lockedOriginal.getToAccountId());
        refund.setToAccountId(lockedOriginal.getFromAccountId());
        refund.setReversalOf(lockedOriginal.getId());
        refund.setIdempotencyKey(key);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        refund.transitionTo(TransactionStatus.COMPLETED, now);
        transactions.saveAndFlush(refund);
        List<Leg> legs = new ArrayList<>();
        if (destinationPart > 0) {
            legs.add(Leg.debit(lockedOriginal.getToAccountId(), destinationPart));
        }
        if (feePart > 0) {
            legs.add(Leg.debit(SystemAccounts.FEE, feePart));
        }
        legs.add(Leg.credit(lockedOriginal.getFromAccountId(), request.amountMinor()));
        if (legs.size() < 2) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount is too small");
        }
        ledger.post(refund, locked, legs);
        lockedOriginal.setRefundedMinor(lockedOriginal.getRefundedMinor() + request.amountMinor());
        if (lockedOriginal.getRefundedMinor() == lockedOriginal.getAmountMinor()) {
            lockedOriginal.transitionTo(TransactionStatus.REVERSED, now);
        }
        PaymentDtos.RefundResponse response =
                new PaymentDtos.RefundResponse(refund.getId(), refund.getStatus().name(), lockedOriginal.getRefundedMinor());
        audit.record(userId, role.name(), "REFUND", "transaction", refund.getId().toString(), null,
                Map.of("originalId", lockedOriginal.getId().toString(), "amountMinor", request.amountMinor(), "feeMinor", feePart));
        outbox.enqueue(refund.getId(), "REFUND_COMPLETED", Map.of("transactionId", refund.getId().toString(), "amountMinor", request.amountMinor()));
        idempotency.complete(userId, key, 201, response);
        return response;
    }

    private long feeCollected(UUID transactionId) {
        return entries.findByTransactionIdOrderByIdAsc(transactionId).stream()
                .filter(entry -> SystemAccounts.FEE.equals(entry.getAccountId()) && entry.getDirection() == Direction.CREDIT)
                .mapToLong(LedgerEntry::getAmountMinor)
                .sum();
    }

    private long feeAlreadyRefunded(UUID originalId) {
        long total = 0;
        for (WalletTransaction refund : transactions.findCompletedRefunds(originalId)) {
            total += entries.findByTransactionIdOrderByIdAsc(refund.getId()).stream()
                    .filter(entry -> SystemAccounts.FEE.equals(entry.getAccountId()) && entry.getDirection() == Direction.DEBIT)
                    .mapToLong(LedgerEntry::getAmountMinor)
                    .sum();
        }
        return total;
    }
}

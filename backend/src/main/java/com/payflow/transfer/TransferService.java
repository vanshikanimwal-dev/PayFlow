package com.payflow.transfer;

import com.payflow.account.Account;
import com.payflow.account.AccountLocker;
import com.payflow.account.AccountRepository;
import com.payflow.account.AccountStatus;
import com.payflow.account.WalletLookup;
import com.payflow.audit.AuditService;
import com.payflow.auth.AppUser;
import com.payflow.auth.UserRepository;
import com.payflow.auth.UserStatus;
import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.Money;
import com.payflow.common.PayflowException;
import com.payflow.config.PayflowProperties;
import com.payflow.idempotency.BeginResult;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.ledger.LedgerService;
import com.payflow.ledger.Leg;
import com.payflow.outbox.OutboxWriter;
import com.payflow.notify.FraudService;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final IdempotencyService idempotency;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final AccountLocker locker;
    private final WalletTransactionRepository transactions;
    private final LedgerService ledger;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final IdGenerator ids;
    private final Clock clock;
    private final PayflowProperties properties;
    private final MeterRegistry meters;
    private final WalletLookup wallets;
    private final FraudService fraud;

    public TransferService(
            IdempotencyService idempotency,
            UserRepository users,
            AccountRepository accounts,
            AccountLocker locker,
            WalletTransactionRepository transactions,
            LedgerService ledger,
            AuditService audit,
            OutboxWriter outbox,
            IdGenerator ids,
            Clock clock,
            PayflowProperties properties,
            MeterRegistry meters,
            WalletLookup wallets,
            FraudService fraud) {
        this.idempotency = idempotency;
        this.users = users;
        this.accounts = accounts;
        this.locker = locker;
        this.transactions = transactions;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.ids = ids;
        this.clock = clock;
        this.properties = properties;
        this.meters = meters;
        this.wallets = wallets;
        this.fraud = fraud;
    }

    @Transactional(readOnly = true)
    public TransferDtos.RecipientView preview(String emailOrPhone) {
        if (emailOrPhone == null || emailOrPhone.isBlank()) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Enter an email or phone");
        }
        AppUser recipient = resolve(emailOrPhone.trim());
        return new TransferDtos.RecipientView(RecipientNames.visible(recipient.getDisplayName(), recipient.getEmail()), recipient.getEmail());
    }

    @Transactional
    public TransferDtos.TransferResponse transfer(UUID userId, String idempotencyKey, String requestHash, TransferDtos.TransferRequest request) {
        BeginResult<TransferDtos.TransferResponse> begin =
                idempotency.begin(userId, idempotencyKey, requestHash, TransferDtos.TransferResponse.class);
        if (begin instanceof BeginResult.Replay<TransferDtos.TransferResponse> replay) {
            return replay.body();
        }
        if (begin instanceof BeginResult.Recover<TransferDtos.TransferResponse>) {
            return recover(userId, idempotencyKey);
        }
        Timer.Sample sample = Timer.start(meters);
        AppUser sender = users.findById(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required"));
        if (sender.getStatus() != UserStatus.ACTIVE) {
            throw inactive();
        }
        if (sender.isWalletLocked()) {
            throw new PayflowException(ErrorCode.WALLET_LOCKED, HttpStatus.UNPROCESSABLE_ENTITY, "Wallet is locked");
        }
        Account from = wallets.spending(userId);
        AppUser recipient = resolve(request.toUserEmailOrPhone().trim());
        Account to = wallets.spending(recipient.getId());
        if (from.getId().equals(to.getId())) {
            throw new PayflowException(ErrorCode.SELF_TRANSFER, HttpStatus.UNPROCESSABLE_ENTITY, "Cannot transfer to yourself");
        }
        Money amount = Money.inr(request.amountMinor());
        if (amount.minorUnits() > properties.getLimits().getMaxTransferMinor()) {
            throw new PayflowException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds the per-transfer limit");
        }
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
        long spent = transactions.sumCompletedTransfersSince(userId, startOfDay);
        if (Math.addExact(spent, amount.minorUnits()) > properties.getLimits().getDailyTransferMinor()) {
            throw new PayflowException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds the daily transfer limit");
        }
        Instant startOfMonth = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long monthSpent = transactions.sumCompletedTransfersSince(userId, startOfMonth);
        if (Math.addExact(monthSpent, amount.minorUnits()) > properties.getLimits().getMonthlyTransferMinor()) {
            throw new PayflowException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds the monthly transfer limit");
        }
        if (from.getStatus() != AccountStatus.ACTIVE || to.getStatus() != AccountStatus.ACTIVE || recipient.getStatus() != UserStatus.ACTIVE) {
            throw inactive();
        }
        List<Account> locked = locker.lock(List.of(from.getId(), to.getId()));
        Account lockedFrom = AccountLocker.require(locked, from.getId());
        Account lockedTo = AccountLocker.require(locked, to.getId());
        Instant now = clock.instant();
        WalletTransaction tx = new WalletTransaction();
        tx.setId(ids.newId());
        tx.setType(TransactionType.TRANSFER);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setAmountMinor(amount.minorUnits());
        tx.setCurrency("INR");
        tx.setInitiatorId(userId);
        tx.setFromAccountId(lockedFrom.getId());
        tx.setToAccountId(lockedTo.getId());
        tx.setIdempotencyKey(idempotencyKey);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        tx.transitionTo(TransactionStatus.COMPLETED, now);
        transactions.saveAndFlush(tx);
        ledger.post(tx, locked, List.of(
                Leg.debit(lockedFrom.getId(), amount.minorUnits()),
                Leg.credit(lockedTo.getId(), amount.minorUnits())));
        TransferDtos.TransferResponse response =
                new TransferDtos.TransferResponse(tx.getId(), tx.getStatus().name(), lockedFrom.getBalanceMinor());
        audit.record(userId, sender.getRole().name(), "TRANSFER", "transaction", tx.getId().toString(), null,
                Map.of("amountMinor", amount.minorUnits(), "to", recipient.getEmail(), "note", request.note() == null ? "" : request.note(),
                        "balanceAfterMinor", response.balanceAfterMinor()));
        outbox.enqueue(tx.getId(), "TRANSFER_COMPLETED", Map.of(
                "transactionId", tx.getId().toString(),
                "amountMinor", amount.minorUnits()));
        idempotency.complete(userId, idempotencyKey, 201, response);
        fraud.largeTransfer(userId, amount.minorUnits());
        meters.counter("transfers").increment();
        sample.stop(meters.timer("transfer_duration"));
        return response;
    }

    private TransferDtos.TransferResponse recover(UUID userId, String idempotencyKey) {
        WalletTransaction tx = transactions.findByInitiatorIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new PayflowException(
                        ErrorCode.IDEMPOTENCY_IN_PROGRESS, HttpStatus.CONFLICT, "A request with this idempotency key is still running"));
        Account from = accounts.findById(tx.getFromAccountId()).orElseThrow();
        return new TransferDtos.TransferResponse(tx.getId(), tx.getStatus().name(), from.getBalanceMinor());
    }

    private AppUser resolve(String emailOrPhone) {
        if (emailOrPhone.contains("@")) {
            return users.findByEmail(emailOrPhone.toLowerCase())
                    .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Recipient not found"));
        }
        return users.findByPhone(emailOrPhone)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Recipient not found"));
    }

    private static PayflowException inactive() {
        return new PayflowException(ErrorCode.ACCOUNT_INACTIVE, HttpStatus.UNPROCESSABLE_ENTITY, "Account is not active");
    }
}

package com.payflow.account;

import com.payflow.auth.AppUser;
import com.payflow.auth.UserRepository;
import com.payflow.auth.UserRole;
import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.Money;
import com.payflow.common.PayflowException;
import com.payflow.idempotency.BeginResult;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.ledger.LedgerService;
import com.payflow.ledger.Leg;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavingsService {

    public record MoveResponse(UUID transactionId, long spendingMinor, long savingsMinor) {
    }

    private final AccountRepository accounts;
    private final WalletLookup wallets;
    private final UserRepository users;
    private final AccountLocker locker;
    private final WalletTransactionRepository transactions;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final IdGenerator ids;
    private final Clock clock;

    public SavingsService(
            AccountRepository accounts,
            WalletLookup wallets,
            UserRepository users,
            AccountLocker locker,
            WalletTransactionRepository transactions,
            LedgerService ledger,
            IdempotencyService idempotency,
            IdGenerator ids,
            Clock clock) {
        this.accounts = accounts;
        this.wallets = wallets;
        this.users = users;
        this.locker = locker;
        this.transactions = transactions;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public Account open(UUID userId) {
        return wallets.savings(userId).orElseGet(() -> {
            AppUser user = users.findById(userId)
                    .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required"));
            if (user.getRole() == UserRole.MERCHANT) {
                throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Merchant accounts cannot open savings");
            }
            Instant now = clock.instant();
            Account savings = new Account();
            savings.setId(ids.newId());
            savings.setOwnerId(userId);
            savings.setType(AccountType.SAVINGS);
            savings.setCurrency("INR");
            savings.setBalanceMinor(0);
            savings.setStatus(AccountStatus.ACTIVE);
            savings.setCreatedAt(now);
            return accounts.save(savings);
        });
    }

    @Transactional
    public MoveResponse move(UUID userId, String key, String hash, long amountMinor, boolean toSavings) {
        BeginResult<MoveResponse> begin = idempotency.begin(userId, key, hash, MoveResponse.class);
        if (begin instanceof BeginResult.Replay<MoveResponse> replay) {
            return replay.body();
        }
        if (begin instanceof BeginResult.Recover<MoveResponse>) {
            throw new PayflowException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, HttpStatus.CONFLICT, "A request with this idempotency key is still running");
        }
        Money amount = Money.inr(amountMinor);
        Account spending = wallets.spending(userId);
        Account savings = wallets.savings(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Open a savings wallet first"));
        Account from = toSavings ? spending : savings;
        Account to = toSavings ? savings : spending;
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
        tx.setIdempotencyKey(key);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        tx.transitionTo(TransactionStatus.COMPLETED, now);
        transactions.saveAndFlush(tx);
        ledger.post(tx, locked, List.of(
                Leg.debit(lockedFrom.getId(), amount.minorUnits()),
                Leg.credit(lockedTo.getId(), amount.minorUnits())));
        long spendingMinor = (lockedFrom.getId().equals(spending.getId()) ? lockedFrom : lockedTo).getBalanceMinor();
        long savingsMinor = (lockedFrom.getId().equals(savings.getId()) ? lockedFrom : lockedTo).getBalanceMinor();
        MoveResponse response = new MoveResponse(tx.getId(), spendingMinor, savingsMinor);
        idempotency.complete(userId, key, 201, response);
        return response;
    }
}

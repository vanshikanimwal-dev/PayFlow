package com.payflow.payment;

import com.payflow.account.Account;
import com.payflow.account.AccountRepository;
import com.payflow.audit.AuditService;
import com.payflow.auth.AppUser;
import com.payflow.auth.UserRepository;
import com.payflow.common.ErrorCode;
import com.payflow.common.IdGenerator;
import com.payflow.common.PayflowException;
import com.payflow.common.SystemAccounts;
import com.payflow.idempotency.BeginResult;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopUpStore {

    private final IdempotencyService idempotency;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final AuditService audit;
    private final IdGenerator ids;
    private final Clock clock;

    public TopUpStore(
            IdempotencyService idempotency,
            UserRepository users,
            AccountRepository accounts,
            WalletTransactionRepository transactions,
            AuditService audit,
            IdGenerator ids,
            Clock clock) {
        this.idempotency = idempotency;
        this.users = users;
        this.accounts = accounts;
        this.transactions = transactions;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public Started begin(UUID userId, String key, String hash, long amountMinor) {
        BeginResult<TopUpDtos.TopUpResponse> result =
                idempotency.begin(userId, key, hash, TopUpDtos.TopUpResponse.class);
        if (result instanceof BeginResult.Replay<TopUpDtos.TopUpResponse> replay) {
            return Started.replay(replay.body());
        }
        if (result instanceof BeginResult.Recover<TopUpDtos.TopUpResponse>) {
            WalletTransaction existing = transactions.findByInitiatorIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> new PayflowException(
                            ErrorCode.IDEMPOTENCY_IN_PROGRESS, HttpStatus.CONFLICT, "A request with this idempotency key is still running"));
            return Started.replay(new TopUpDtos.TopUpResponse(existing.getId(), existing.getStatus().name(), null));
        }
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required"));
        Account wallet = accounts.findByOwnerId(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found"));
        Instant now = clock.instant();
        WalletTransaction tx = new WalletTransaction();
        tx.setId(ids.newId());
        tx.setType(TransactionType.TOPUP);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setAmountMinor(amountMinor);
        tx.setCurrency("INR");
        tx.setInitiatorId(userId);
        tx.setFromAccountId(SystemAccounts.GATEWAY);
        tx.setToAccountId(wallet.getId());
        tx.setIdempotencyKey(key);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        transactions.saveAndFlush(tx);
        audit.record(userId, user.getRole().name(), "TOPUP_CREATED", "transaction", tx.getId().toString(), null,
                Map.of("amountMinor", amountMinor, "status", "PENDING"));
        return Started.created(tx);
    }

    @Transactional
    public void markProcessing(UUID id, String gatewayRef) {
        WalletTransaction tx = transactions.lockById(id).orElseThrow();
        if (tx.getStatus() == TransactionStatus.PENDING) {
            tx.transitionTo(TransactionStatus.PROCESSING, clock.instant());
        }
        if (gatewayRef != null && !gatewayRef.isBlank()) {
            tx.setGatewayRef(gatewayRef);
        }
    }

    @Transactional
    public void markFailed(UUID id, String reason) {
        WalletTransaction tx = transactions.lockById(id).orElseThrow();
        if (tx.getStatus() == TransactionStatus.COMPLETED || tx.getStatus() == TransactionStatus.REVERSED) {
            return;
        }
        if (tx.getStatus() != TransactionStatus.FAILED) {
            tx.transitionTo(TransactionStatus.FAILED, clock.instant());
        }
        tx.setFailureReason(trim(reason));
    }

    @Transactional
    public void finishIdempotency(UUID userId, String key, TopUpDtos.TopUpResponse response) {
        idempotency.complete(userId, key, 202, response);
    }

    @Transactional(readOnly = true)
    public TopUpDtos.TopUpResponse status(UUID userId, UUID transactionId) {
        WalletTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        if (!tx.getInitiatorId().equals(userId) || tx.getType() != TransactionType.TOPUP) {
            throw new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found");
        }
        return new TopUpDtos.TopUpResponse(tx.getId(), tx.getStatus().name(), null);
    }

    private static String trim(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }

    public record Started(WalletTransaction created, TopUpDtos.TopUpResponse replay) {
        static Started created(WalletTransaction tx) {
            return new Started(tx, null);
        }

        static Started replay(TopUpDtos.TopUpResponse response) {
            return new Started(null, response);
        }
    }
}

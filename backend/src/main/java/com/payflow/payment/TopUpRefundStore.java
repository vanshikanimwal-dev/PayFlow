package com.payflow.payment;

import com.payflow.auth.UserRole;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opens a refund row and commits it before anyone calls the gateway. */
@Service
public class TopUpRefundStore {

    private final IdempotencyService idempotency;
    private final WalletTransactionRepository transactions;
    private final IdGenerator ids;
    private final Clock clock;

    public TopUpRefundStore(
            IdempotencyService idempotency, WalletTransactionRepository transactions, IdGenerator ids, Clock clock) {
        this.idempotency = idempotency;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public Started begin(UUID userId, UserRole role, String key, String hash, PaymentDtos.RefundRequest request) {
        BeginResult<PaymentDtos.RefundResponse> result =
                idempotency.begin(userId, key, hash, PaymentDtos.RefundResponse.class);
        if (result instanceof BeginResult.Replay<PaymentDtos.RefundResponse> replay) {
            return Started.replay(replay.body());
        }
        if (result instanceof BeginResult.Recover<PaymentDtos.RefundResponse>) {
            WalletTransaction existing = transactions.findByInitiatorIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() -> new PayflowException(
                            ErrorCode.IDEMPOTENCY_IN_PROGRESS, HttpStatus.CONFLICT, "A request with this idempotency key is still running"));
            return Started.replay(new PaymentDtos.RefundResponse(
                    existing.getId(), existing.getStatus().name(), existing.getAmountMinor()));
        }
        WalletTransaction original = transactions.findById(request.transactionId())
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        if (original.getType() != TransactionType.TOPUP || original.getStatus() != TransactionStatus.COMPLETED) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Only a completed top-up can be sent back to the card");
        }
        boolean allowed = role == UserRole.ADMIN || userId.equals(original.getInitiatorId());
        if (!allowed) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You do not have access to this resource");
        }
        if (original.getGatewayRef() == null || original.getGatewayRef().isBlank()) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "This top-up has no card payment to refund");
        }
        long remaining = original.getAmountMinor() - original.getRefundedMinor();
        if (request.amountMinor() > remaining) {
            throw new PayflowException(ErrorCode.ALREADY_REFUNDED, HttpStatus.UNPROCESSABLE_ENTITY, "Refund exceeds the remaining amount");
        }
        if (transactions.findOpenRefund(original.getId()).isPresent()) {
            throw new PayflowException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, HttpStatus.CONFLICT, "A refund for this top-up is already running");
        }
        Instant now = clock.instant();
        WalletTransaction refund = new WalletTransaction();
        refund.setId(ids.newId());
        refund.setType(TransactionType.REFUND);
        refund.setStatus(TransactionStatus.PENDING);
        refund.setAmountMinor(request.amountMinor());
        refund.setCurrency("INR");
        refund.setInitiatorId(userId);
        refund.setFromAccountId(original.getToAccountId());
        refund.setToAccountId(SystemAccounts.GATEWAY);
        refund.setReversalOf(original.getId());
        refund.setGatewayRef(original.getGatewayRef());
        refund.setIdempotencyKey(key);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        transactions.saveAndFlush(refund);
        return Started.created(refund);
    }

    @Transactional
    public void markProcessing(UUID refundId) {
        WalletTransaction refund = transactions.lockById(refundId).orElseThrow();
        if (refund.getStatus() == TransactionStatus.PENDING) {
            refund.transitionTo(TransactionStatus.PROCESSING, clock.instant());
        }
    }

    @Transactional
    public void markFailed(UUID refundId, String reason) {
        WalletTransaction refund = transactions.lockById(refundId).orElseThrow();
        if (refund.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        if (refund.getStatus() != TransactionStatus.FAILED) {
            refund.transitionTo(TransactionStatus.FAILED, clock.instant());
        }
        refund.setFailureReason(reason == null ? null : reason.substring(0, Math.min(255, reason.length())));
    }

    public record Started(WalletTransaction created, PaymentDtos.RefundResponse replay) {
        static Started created(WalletTransaction refund) {
            return new Started(refund, null);
        }

        static Started replay(PaymentDtos.RefundResponse response) {
            return new Started(null, response);
        }
    }
}

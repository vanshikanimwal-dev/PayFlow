package com.payflow.payment;

import com.payflow.auth.UserRole;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.config.PayflowProperties;
import com.payflow.gatewayclient.GatewayClient;
import com.payflow.gatewayclient.GatewayPayment;
import com.payflow.gatewayclient.GatewayRejectedException;
import com.payflow.gatewayclient.GatewayUnknownException;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Card refund saga. The refund row is committed first. The gateway is called only after that
 * transaction ends, then the wallet is debited.
 */
@Service
public class TopUpRefundService {

    private final TopUpRefundStore store;
    private final TopUpRefundCompletion completion;
    private final GatewayClient gateway;
    private final WalletTransactionRepository transactions;
    private final PayflowProperties properties;
    private final Clock clock;

    public TopUpRefundService(
            TopUpRefundStore store,
            TopUpRefundCompletion completion,
            GatewayClient gateway,
            WalletTransactionRepository transactions,
            PayflowProperties properties,
            Clock clock) {
        this.store = store;
        this.completion = completion;
        this.gateway = gateway;
        this.transactions = transactions;
        this.properties = properties;
        this.clock = clock;
    }

    public PaymentDtos.RefundResponse refund(UUID userId, UserRole role, String key, String hash, PaymentDtos.RefundRequest request) {
        TopUpRefundStore.Started started = store.begin(userId, role, key, hash, request);
        if (started.replay() != null) {
            return started.replay();
        }
        WalletTransaction refund = started.created();
        try {
            gateway.refund(refund.getGatewayRef(), refund.getAmountMinor(), refund.getId().toString());
            return completion.complete(refund.getId());
        } catch (GatewayRejectedException ex) {
            store.markFailed(refund.getId(), ex.getMessage());
            throw new PayflowException(ErrorCode.GATEWAY_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "The card refund was rejected");
        } catch (GatewayUnknownException ex) {
            store.markProcessing(refund.getId());
            throw new PayflowException(
                    ErrorCode.GATEWAY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, "Card refund is still processing");
        }
    }

    /** If the gateway already accepted the refund and the webhook was missed, finish the wallet side. */
    public int recover() {
        Instant cutoff = clock.instant().minus(properties.getSaga().getStaleAfter());
        List<WalletTransaction> stale = transactions.findByTypeAndStatusInAndUpdatedAtBefore(
                TransactionType.REFUND, List.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING), cutoff);
        int acted = 0;
        for (WalletTransaction refund : stale) {
            if (refund.getReversalOf() == null) {
                continue;
            }
            Optional<GatewayPayment> remote;
            try {
                remote = gateway.findByReference(refund.getReversalOf());
            } catch (GatewayUnknownException ex) {
                continue;
            }
            WalletTransaction original = transactions.findById(refund.getReversalOf()).orElse(null);
            long already = original == null ? 0 : original.getRefundedMinor();
            if (remote.isPresent() && remote.get().refundedMinor() >= already + refund.getAmountMinor()) {
                completion.complete(refund.getId());
                acted++;
            }
        }
        return acted;
    }
}

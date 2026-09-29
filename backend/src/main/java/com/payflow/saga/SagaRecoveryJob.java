package com.payflow.saga;

import com.payflow.gatewayclient.GatewayClient;
import com.payflow.gatewayclient.GatewayPayment;
import com.payflow.gatewayclient.GatewayUnknownException;
import com.payflow.payment.TopUpCompletionService;
import com.payflow.reconciliation.MismatchRecorder;
import com.payflow.reconciliation.ReconciliationKind;
import com.payflow.config.PayflowProperties;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SagaRecoveryJob {

    private final WalletTransactionRepository transactions;
    private final GatewayClient gateway;
    private final TopUpCompletionService completion;
    private final MismatchRecorder mismatches;
    private final PayflowProperties properties;
    private final Clock clock;

    public SagaRecoveryJob(
            WalletTransactionRepository transactions,
            GatewayClient gateway,
            TopUpCompletionService completion,
            MismatchRecorder mismatches,
            PayflowProperties properties,
            Clock clock) {
        this.transactions = transactions;
        this.gateway = gateway;
        this.completion = completion;
        this.mismatches = mismatches;
        this.properties = properties;
        this.clock = clock;
    }

    public int recover() {
        Instant cutoff = clock.instant().minus(properties.getSaga().getStaleAfter());
        List<WalletTransaction> stale = transactions.findByTypeAndStatusInAndUpdatedAtBefore(
                TransactionType.TOPUP, List.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING), cutoff);
        int acted = 0;
        for (WalletTransaction tx : stale) {
            Optional<GatewayPayment> remote;
            try {
                remote = gateway.findByReference(tx.getId());
            } catch (GatewayUnknownException ex) {
                continue;
            }
            if (remote.isEmpty()) {
                if (tx.getCreatedAt().isBefore(clock.instant().minus(properties.getSaga().getGiveUpAfter()))) {
                    completion.fail(tx.getId(), "No gateway payment after the recovery window");
                    mismatches.flag(tx, ReconciliationKind.STATUS_MISMATCH, null, tx.getAmountMinor(),
                            "No gateway record after the recovery window");
                    acted++;
                }
                continue;
            }
            GatewayPayment payment = remote.get();
            String status = payment.status() == null ? "" : payment.status();
            if ("CAPTURED".equals(status) || "SUCCEEDED".equals(status)) {
                completion.complete(tx.getId(), payment.amountMinor(), payment.paymentId());
                acted++;
            } else if ("FAILED".equals(status)) {
                completion.fail(tx.getId(), "Gateway reported failure");
                acted++;
            }
        }
        return acted;
    }
}

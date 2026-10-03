package com.payflow.payment;

import com.payflow.auth.UserRole;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransactionRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Picks the local ledger refund or the card saga. Neither path calls the gateway while holding locks. */
@Service
public class RefundCoordinator {

    private final WalletTransactionRepository transactions;
    private final RefundService ledgerRefunds;
    private final TopUpRefundService cardRefunds;

    public RefundCoordinator(
            WalletTransactionRepository transactions, RefundService ledgerRefunds, TopUpRefundService cardRefunds) {
        this.transactions = transactions;
        this.ledgerRefunds = ledgerRefunds;
        this.cardRefunds = cardRefunds;
    }

    public PaymentDtos.RefundResponse refund(UUID userId, UserRole role, String key, String hash, PaymentDtos.RefundRequest request) {
        TransactionType type = transactions.findById(request.transactionId())
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"))
                .getType();
        if (type == TransactionType.TOPUP) {
            return cardRefunds.refund(userId, role, key, hash, request);
        }
        return ledgerRefunds.refund(userId, role, key, hash, request);
    }
}

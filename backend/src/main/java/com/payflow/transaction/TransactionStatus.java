package com.payflow.transaction;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Legal edges from SPEC section 6. TOPUP {@code FAILED -> COMPLETED} is the section 10 safe
 * recovery when the gateway captured money we had marked failed.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED;

    private static final Map<TransactionType, Map<TransactionStatus, Set<TransactionStatus>>> ALLOWED = Map.of(
            TransactionType.TOPUP, Map.of(
                    PENDING, EnumSet.of(PROCESSING, FAILED),
                    PROCESSING, EnumSet.of(COMPLETED, FAILED),
                    COMPLETED, EnumSet.of(REVERSED),
                    FAILED, EnumSet.of(COMPLETED)),
            TransactionType.TRANSFER, Map.of(
                    PENDING, EnumSet.of(COMPLETED, FAILED),
                    COMPLETED, EnumSet.of(REVERSED)),
            TransactionType.PAYMENT, Map.of(
                    PENDING, EnumSet.of(COMPLETED, FAILED),
                    COMPLETED, EnumSet.of(REVERSED)),
            TransactionType.REFUND, Map.of(
                    PENDING, EnumSet.of(COMPLETED, FAILED)));

    public static boolean allowed(TransactionType type, TransactionStatus from, TransactionStatus to) {
        Set<TransactionStatus> next = ALLOWED.getOrDefault(type, Map.of()).get(from);
        return next != null && next.contains(to);
    }
}

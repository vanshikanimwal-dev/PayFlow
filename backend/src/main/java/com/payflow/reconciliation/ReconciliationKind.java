package com.payflow.reconciliation;

public enum ReconciliationKind {
    MISSING_IN_LEDGER,
    MISSING_IN_GATEWAY,
    AMOUNT_MISMATCH,
    STATUS_MISMATCH
}

package com.payflow.transaction;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(TransactionType type, TransactionStatus from, TransactionStatus to) {
        super("Illegal transition for " + type + ": " + from + " -> " + to);
    }
}

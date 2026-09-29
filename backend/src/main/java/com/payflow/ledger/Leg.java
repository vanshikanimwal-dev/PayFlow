package com.payflow.ledger;

import java.util.UUID;

public record Leg(UUID accountId, Direction direction, long amountMinor) {

    public Leg {
        if (accountId == null) {
            throw new IllegalArgumentException("Account is required");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Direction is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Ledger amount must be positive");
        }
    }

    public static Leg debit(UUID accountId, long amountMinor) {
        return new Leg(accountId, Direction.DEBIT, amountMinor);
    }

    public static Leg credit(UUID accountId, long amountMinor) {
        return new Leg(accountId, Direction.CREDIT, amountMinor);
    }
}

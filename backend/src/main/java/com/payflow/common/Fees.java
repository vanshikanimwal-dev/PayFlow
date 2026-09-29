package com.payflow.common;

public final class Fees {

    private Fees() {
    }

    /** Half-up percentage in integer paise. 2% of 100000 is 2000. */
    public static long percentHalfUp(long amountMinor, int percent) {
        if (amountMinor < 0 || percent < 0) {
            throw new IllegalArgumentException("Fee inputs cannot be negative");
        }
        return (Math.multiplyExact(amountMinor, (long) percent) + 50) / 100;
    }

    /** {@code amount * numerator / denominator}, half-up, used so the final refund takes the remainder. */
    public static long proportionHalfUp(long amount, long numerator, long denominator) {
        if (amount < 0 || numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("Proportion inputs are invalid");
        }
        return (Math.multiplyExact(amount, numerator) + (denominator / 2)) / denominator;
    }
}

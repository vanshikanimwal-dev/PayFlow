package com.payflow.common;

/**
 * Amount in minor units (paise for INR). Never constructed from {@code double} or {@code float}.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    public static Money inr(long paise) {
        return new Money(paise, Currency.INR);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    /**
     * Major units for display only. Parsing user input belongs at the edge and must stay integer math.
     */
    public String toDisplay() {
        long major = minorUnits / 100;
        long fraction = Math.abs(minorUnits % 100);
        return major + "." + (fraction < 10 ? "0" + fraction : Long.toString(fraction));
    }

    private void requireSameCurrency(Money other) {
        if (other.currency != currency) {
            throw new IllegalArgumentException("Currency mismatch");
        }
    }
}

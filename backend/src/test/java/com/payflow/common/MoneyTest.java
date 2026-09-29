package com.payflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void storesPaiseWithoutFloatingPoint() {
        Money money = Money.inr(50050);
        assertThat(money.minorUnits()).isEqualTo(50050);
        assertThat(money.currency()).isEqualTo(Currency.INR);
        assertThat(money.toDisplay()).isEqualTo("500.50");
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.inr(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void feeIsHalfUpInPaise() {
        assertThat(Fees.percentHalfUp(100_000, 2)).isEqualTo(2_000);
        assertThat(Fees.percentHalfUp(25, 2)).isEqualTo(1);
        assertThat(Fees.percentHalfUp(1, 2)).isZero();
    }
}

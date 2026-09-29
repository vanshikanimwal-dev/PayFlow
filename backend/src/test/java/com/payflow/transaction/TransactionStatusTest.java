package com.payflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionStatusTest {

    @Test
    void allowsTheDiagramEdges() {
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.PENDING, TransactionStatus.PROCESSING)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.PENDING, TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.PROCESSING, TransactionStatus.COMPLETED)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.PROCESSING, TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.COMPLETED, TransactionStatus.REVERSED)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.TRANSFER, TransactionStatus.PENDING, TransactionStatus.COMPLETED)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.PAYMENT, TransactionStatus.PENDING, TransactionStatus.FAILED)).isTrue();
        assertThat(TransactionStatus.allowed(TransactionType.REFUND, TransactionStatus.PENDING, TransactionStatus.COMPLETED)).isTrue();
    }

    @Test
    void topUpFailedCanBeCompletedWhenTheGatewayCaptured() {
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.FAILED, TransactionStatus.COMPLETED)).isTrue();
    }

    @Test
    void rejectsIllegalEdges() {
        assertThat(TransactionStatus.allowed(TransactionType.TOPUP, TransactionStatus.PENDING, TransactionStatus.COMPLETED)).isFalse();
        assertThat(TransactionStatus.allowed(TransactionType.TRANSFER, TransactionStatus.PENDING, TransactionStatus.PROCESSING)).isFalse();
        assertThat(TransactionStatus.allowed(TransactionType.TRANSFER, TransactionStatus.FAILED, TransactionStatus.COMPLETED)).isFalse();
        assertThat(TransactionStatus.allowed(TransactionType.REFUND, TransactionStatus.COMPLETED, TransactionStatus.FAILED)).isFalse();
        assertThat(TransactionStatus.allowed(TransactionType.PAYMENT, TransactionStatus.REVERSED, TransactionStatus.COMPLETED)).isFalse();

        WalletTransaction tx = new WalletTransaction();
        tx.setId(UUID.randomUUID());
        tx.setType(TransactionType.TRANSFER);
        tx.setStatus(TransactionStatus.COMPLETED);
        assertThatThrownBy(() -> tx.transitionTo(TransactionStatus.PENDING, Instant.now()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }
}

package com.payflow.transaction;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository
        extends JpaRepository<WalletTransaction, UUID>, JpaSpecificationExecutor<WalletTransaction> {

    Optional<WalletTransaction> findByInitiatorIdAndIdempotencyKey(UUID initiatorId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WalletTransaction t where t.id = :id")
    Optional<WalletTransaction> lockById(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(t.amountMinor), 0) from WalletTransaction t
            where t.initiatorId = :userId and t.type = com.payflow.transaction.TransactionType.TRANSFER
              and t.status = com.payflow.transaction.TransactionStatus.COMPLETED
              and t.createdAt >= :from
            """)
    long sumCompletedTransfersSince(@Param("userId") UUID userId, @Param("from") Instant from);

    List<WalletTransaction> findByTypeAndStatusInAndUpdatedAtBefore(
            TransactionType type, List<TransactionStatus> statuses, Instant updatedBefore);

    List<WalletTransaction> findByTypeAndCreatedAtGreaterThanEqualAndCreatedAtBefore(
            TransactionType type, Instant from, Instant to);

    long countByTypeAndStatus(TransactionType type, TransactionStatus status);

    @Query("""
            select t from WalletTransaction t
            where t.reversalOf = :originalId and t.type = com.payflow.transaction.TransactionType.REFUND
              and t.status = com.payflow.transaction.TransactionStatus.COMPLETED
            """)
    List<WalletTransaction> findCompletedRefunds(@Param("originalId") UUID originalId);

    @Query("""
            select t from WalletTransaction t
            where t.reversalOf = :originalId and t.type = com.payflow.transaction.TransactionType.REFUND
              and t.status in (
                com.payflow.transaction.TransactionStatus.PENDING,
                com.payflow.transaction.TransactionStatus.PROCESSING)
            """)
    Optional<WalletTransaction> findOpenRefund(@Param("originalId") UUID originalId);
}

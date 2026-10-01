package com.payflow.account;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByOwnerId(UUID ownerId);

    Optional<Account> findByOwnerIdAndType(UUID ownerId, AccountType type);

    List<Account> findAllByOwnerId(UUID ownerId);

    /**
     * Locks a single row. Callers that need several accounts must call this in ascending id order;
     * one {@code IN} query can lock rows in plan order and deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);
}

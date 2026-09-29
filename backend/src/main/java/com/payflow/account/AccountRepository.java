package com.payflow.account;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByOwnerId(UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    List<Account> lockByIds(@Param("ids") Collection<UUID> ids);
}

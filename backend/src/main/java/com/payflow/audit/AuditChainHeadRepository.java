package com.payflow.audit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuditChainHeadRepository extends JpaRepository<AuditChainHead, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.id = 1")
    AuditChainHead lockHead();
}

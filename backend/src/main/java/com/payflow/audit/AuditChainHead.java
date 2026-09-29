package com.payflow.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    @Id
    private short id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String hash;

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public String getHash() {
        return hash == null ? null : hash.strip();
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}

package com.payflow.account;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.ledger.Direction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (currency == null) {
            currency = "INR";
        }
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
    }

    public long apply(Direction direction, long amountMinor) {
        long delta = direction == Direction.CREDIT ? amountMinor : -amountMinor;
        long next = Math.addExact(balanceMinor, delta);
        if (type != AccountType.SYSTEM_GATEWAY && next < 0) {
            throw new PayflowException(
                    ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.UNPROCESSABLE_ENTITY, "Wallet balance is too low");
        }
        this.balanceMinor = next;
        return next;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public void setBalanceMinor(long balanceMinor) {
        this.balanceMinor = balanceMinor;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.payflow.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_requests")
public class PaymentRequest {

    @Id
    private UUID id;

    @Column(name = "merchant_account_id", nullable = false)
    private UUID merchantAccountId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PaymentRequestStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMerchantAccountId() {
        return merchantAccountId;
    }

    public void setMerchantAccountId(UUID merchantAccountId) {
        this.merchantAccountId = merchantAccountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PaymentRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentRequestStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

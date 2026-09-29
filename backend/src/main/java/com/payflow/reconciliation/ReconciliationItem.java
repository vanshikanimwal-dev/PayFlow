package com.payflow.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_items")
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "gateway_ref", length = 100)
    private String gatewayRef;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReconciliationKind kind;

    @Column(name = "gateway_amount")
    private Long gatewayAmount;

    @Column(name = "ledger_amount")
    private Long ledgerAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Resolution resolution;

    private String note;

    public Long getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getGatewayRef() {
        return gatewayRef;
    }

    public void setGatewayRef(String gatewayRef) {
        this.gatewayRef = gatewayRef;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public ReconciliationKind getKind() {
        return kind;
    }

    public void setKind(ReconciliationKind kind) {
        this.kind = kind;
    }

    public Long getGatewayAmount() {
        return gatewayAmount;
    }

    public void setGatewayAmount(Long gatewayAmount) {
        this.gatewayAmount = gatewayAmount;
    }

    public Long getLedgerAmount() {
        return ledgerAmount;
    }

    public void setLedgerAmount(Long ledgerAmount) {
        this.ledgerAmount = ledgerAmount;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public void setResolution(Resolution resolution) {
        this.resolution = resolution;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

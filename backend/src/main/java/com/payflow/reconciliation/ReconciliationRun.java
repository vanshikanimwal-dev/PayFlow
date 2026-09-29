package com.payflow.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    private UUID id;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private Integer matched;

    private Integer mismatched;

    @Column(length = 12)
    private String status;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public void setRunDate(LocalDate runDate) {
        this.runDate = runDate;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getMatched() {
        return matched;
    }

    public void setMatched(Integer matched) {
        this.matched = matched;
    }

    public Integer getMismatched() {
        return mismatched;
    }

    public void setMismatched(Integer mismatched) {
        this.mismatched = mismatched;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

package com.payflow.reconciliation;

import com.payflow.common.IdGenerator;
import com.payflow.gatewayclient.GatewayClient;
import com.payflow.payment.TopUpCompletionService;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

    private final GatewayClient gateway;
    private final WalletTransactionRepository transactions;
    private final ReconciliationRunRepository runs;
    private final ReconciliationItemRepository items;
    private final TopUpCompletionService completion;
    private final IdGenerator ids;
    private final Clock clock;
    private final MeterRegistry meters;

    public ReconciliationService(
            GatewayClient gateway,
            WalletTransactionRepository transactions,
            ReconciliationRunRepository runs,
            ReconciliationItemRepository items,
            TopUpCompletionService completion,
            IdGenerator ids,
            Clock clock,
            MeterRegistry meters) {
        this.gateway = gateway;
        this.transactions = transactions;
        this.runs = runs;
        this.items = items;
        this.completion = completion;
        this.ids = ids;
        this.clock = clock;
        this.meters = meters;
    }

    public ReconciliationRun reconcile(LocalDate date) {
        String csv = gateway.settlementCsv(date);
        return apply(date, csv);
    }

    @Transactional
    public ReconciliationRun apply(LocalDate date, String csv) {
        List<SettlementFileParser.Row> rows = SettlementFileParser.parse(csv);
        var from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<WalletTransaction> local = transactions.findByTypeAndCreatedAtGreaterThanEqualAndCreatedAtBefore(
                TransactionType.TOPUP, from, to);
        Map<UUID, WalletTransaction> byId = new HashMap<>();
        Map<String, WalletTransaction> byGatewayRef = new HashMap<>();
        for (WalletTransaction tx : local) {
            byId.put(tx.getId(), tx);
            if (tx.getGatewayRef() != null) {
                byGatewayRef.put(tx.getGatewayRef(), tx);
            }
        }
        ReconciliationRun run = new ReconciliationRun();
        run.setId(ids.newId());
        run.setRunDate(date);
        run.setStartedAt(clock.instant());
        run.setStatus("RUNNING");
        runs.save(run);
        int matched = 0;
        int mismatched = 0;
        Set<UUID> seen = new HashSet<>();
        for (SettlementFileParser.Row row : rows) {
            WalletTransaction tx = resolve(row, byId, byGatewayRef);
            boolean captured = "CAPTURED".equalsIgnoreCase(row.status()) || "SUCCEEDED".equalsIgnoreCase(row.status());
            if (tx == null) {
                if (captured) {
                    mismatched++;
                    item(run, row, null, ReconciliationKind.MISSING_IN_LEDGER, row.amountMinor(), null, Resolution.MANUAL_REVIEW,
                            "Gateway capture has no local transaction");
                }
                continue;
            }
            seen.add(tx.getId());
            if (captured && tx.getStatus() != TransactionStatus.COMPLETED && tx.getAmountMinor() == row.amountMinor()) {
                TransactionStatus before = tx.getStatus();
                completion.complete(tx.getId(), row.amountMinor(), row.paymentId());
                mismatched++;
                ReconciliationKind kind = before == TransactionStatus.FAILED
                        ? ReconciliationKind.STATUS_MISMATCH
                        : ReconciliationKind.MISSING_IN_LEDGER;
                item(run, row, tx.getId(), kind, row.amountMinor(), tx.getAmountMinor(), Resolution.AUTO_FIXED,
                        "Credited from the settlement file");
                continue;
            }
            if (captured && tx.getStatus() == TransactionStatus.COMPLETED && tx.getAmountMinor() == row.amountMinor()) {
                matched++;
                continue;
            }
            if (tx.getAmountMinor() != row.amountMinor()) {
                mismatched++;
                item(run, row, tx.getId(), ReconciliationKind.AMOUNT_MISMATCH, row.amountMinor(), tx.getAmountMinor(),
                        Resolution.MANUAL_REVIEW, "Amounts differ");
                continue;
            }
            mismatched++;
            item(run, row, tx.getId(), ReconciliationKind.STATUS_MISMATCH, row.amountMinor(), tx.getAmountMinor(),
                    Resolution.MANUAL_REVIEW, "Status does not match; not auto-fixed");
        }
        for (WalletTransaction tx : local) {
            if (seen.contains(tx.getId())) {
                continue;
            }
            if (tx.getStatus() == TransactionStatus.COMPLETED) {
                mismatched++;
                ReconciliationItem orphan = new ReconciliationItem();
                orphan.setRunId(run.getId());
                orphan.setGatewayRef(tx.getGatewayRef());
                orphan.setTransactionId(tx.getId());
                orphan.setKind(ReconciliationKind.MISSING_IN_GATEWAY);
                orphan.setLedgerAmount(tx.getAmountMinor());
                orphan.setResolution(Resolution.MANUAL_REVIEW);
                orphan.setNote("Ledger credited a top-up the settlement file does not capture");
                items.save(orphan);
            }
        }
        run.setMatched(matched);
        run.setMismatched(mismatched);
        run.setFinishedAt(clock.instant());
        run.setStatus("DONE");
        if (mismatched > 0) {
            meters.counter("reconciliation_mismatches").increment(mismatched);
        }
        return run;
    }

    @Transactional
    public ReconciliationItem resolve(Long itemId, Resolution resolution, String note, UUID actorId) {
        if (resolution != Resolution.RESOLVED && resolution != Resolution.MANUAL_REVIEW) {
            throw new com.payflow.common.PayflowException(
                    com.payflow.common.ErrorCode.VALIDATION_ERROR,
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Resolution must be RESOLVED or MANUAL_REVIEW");
        }
        ReconciliationItem item = items.findById(itemId)
                .orElseThrow(() -> new com.payflow.common.PayflowException(
                        com.payflow.common.ErrorCode.NOT_FOUND, org.springframework.http.HttpStatus.NOT_FOUND, "Reconciliation item not found"));
        item.setResolution(resolution);
        item.setNote(note);
        return item;
    }

    private WalletTransaction resolve(SettlementFileParser.Row row, Map<UUID, WalletTransaction> byId, Map<String, WalletTransaction> byGatewayRef) {
        try {
            WalletTransaction byReference = byId.get(UUID.fromString(row.reference()));
            if (byReference != null) {
                return byReference;
            }
        } catch (IllegalArgumentException ignored) {
            // reference is not our transaction id
        }
        return byGatewayRef.get(row.paymentId());
    }

    private void item(
            ReconciliationRun run,
            SettlementFileParser.Row row,
            UUID txId,
            ReconciliationKind kind,
            Long gatewayAmount,
            Long ledgerAmount,
            Resolution resolution,
            String note) {
        ReconciliationItem item = new ReconciliationItem();
        item.setRunId(run.getId());
        item.setGatewayRef(row.paymentId());
        item.setTransactionId(txId);
        item.setKind(kind);
        item.setGatewayAmount(gatewayAmount);
        item.setLedgerAmount(ledgerAmount);
        item.setResolution(resolution);
        item.setNote(note);
        items.save(item);
    }
}

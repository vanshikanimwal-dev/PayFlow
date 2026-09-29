package com.payflow.reconciliation;

import com.payflow.common.IdGenerator;
import com.payflow.transaction.WalletTransaction;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MismatchRecorder {

    private final ReconciliationRunRepository runs;
    private final ReconciliationItemRepository items;
    private final IdGenerator ids;
    private final Clock clock;

    public MismatchRecorder(
            ReconciliationRunRepository runs, ReconciliationItemRepository items, IdGenerator ids, Clock clock) {
        this.runs = runs;
        this.items = items;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public ReconciliationItem flag(WalletTransaction tx, ReconciliationKind kind, Long gatewayAmount, Long ledgerAmount, String note) {
        ReconciliationRun run = new ReconciliationRun();
        run.setId(ids.newId());
        run.setRunDate(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        run.setStartedAt(clock.instant());
        run.setFinishedAt(clock.instant());
        run.setMatched(0);
        run.setMismatched(1);
        run.setStatus("DONE");
        runs.save(run);
        ReconciliationItem item = new ReconciliationItem();
        item.setRunId(run.getId());
        item.setGatewayRef(tx.getGatewayRef());
        item.setTransactionId(tx.getId());
        item.setKind(kind);
        item.setGatewayAmount(gatewayAmount);
        item.setLedgerAmount(ledgerAmount);
        item.setResolution(Resolution.MANUAL_REVIEW);
        item.setNote(note);
        return items.save(item);
    }
}

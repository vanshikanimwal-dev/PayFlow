package com.payflow.config;

import com.payflow.idempotency.IdempotencyService;
import com.payflow.ledger.LedgerInvariantChecker;
import com.payflow.outbox.OutboxPublisher;
import com.payflow.payment.MerchantPaymentService;
import com.payflow.reconciliation.ReconciliationService;
import com.payflow.saga.SagaRecoveryJob;
import com.payflow.schedule.ScheduleService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payflow.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformJobs {

    private final OutboxPublisher outbox;
    private final SagaRecoveryJob saga;
    private final ReconciliationService reconciliation;
    private final LedgerInvariantChecker integrity;
    private final IdempotencyService idempotency;
    private final MerchantPaymentService payments;
    private final ScheduleService schedules;
    private final Clock clock;

    public PlatformJobs(
            OutboxPublisher outbox,
            SagaRecoveryJob saga,
            ReconciliationService reconciliation,
            LedgerInvariantChecker integrity,
            IdempotencyService idempotency,
            MerchantPaymentService payments,
            ScheduleService schedules,
            Clock clock) {
        this.outbox = outbox;
        this.saga = saga;
        this.reconciliation = reconciliation;
        this.integrity = integrity;
        this.idempotency = idempotency;
        this.payments = payments;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 1000)
    @SchedulerLock(name = "outboxPublisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT0.5S")
    public void publishOutbox() {
        outbox.publishBatch();
    }

    @Scheduled(fixedDelay = 30_000)
    @SchedulerLock(name = "sagaRecovery", lockAtMostFor = "PT5M")
    public void recoverSagas() {
        saga.recover();
    }

    @Scheduled(cron = "0 15 2 * * *")
    @SchedulerLock(name = "reconciliation", lockAtMostFor = "PT30M")
    public void reconcileYesterday() {
        reconciliation.reconcile(LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1));
    }

    @Scheduled(cron = "0 45 2 * * *")
    @SchedulerLock(name = "ledgerIntegrity", lockAtMostFor = "PT10M")
    public void checkIntegrity() {
        integrity.check();
    }

    @Scheduled(fixedDelay = 3_600_000)
    @SchedulerLock(name = "idempotencyCleanup", lockAtMostFor = "PT10M")
    public void deleteExpiredIdempotencyKeys() {
        idempotency.deleteExpired();
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "expirePaymentRequests", lockAtMostFor = "PT1M")
    public void expirePaymentRequests() {
        payments.expireOpenRequests();
    }

    @Scheduled(cron = "0 10 0 * * *")
    @SchedulerLock(name = "scheduledTransfers", lockAtMostFor = "PT30M")
    public void runScheduledTransfers() {
        schedules.runDue();
    }
}

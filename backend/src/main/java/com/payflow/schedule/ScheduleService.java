package com.payflow.schedule;

import com.payflow.common.PayflowException;
import com.payflow.transfer.TransferDtos;
import com.payflow.transfer.TransferService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final ScheduledTransferRepository schedules;
    private final TransferService transfers;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ScheduleService(
            ScheduledTransferRepository schedules,
            TransferService transfers,
            Clock clock,
            PlatformTransactionManager transactions) {
        this.schedules = schedules;
        this.transfers = transfers;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    public int runDue() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        int ran = 0;
        for (ScheduledTransfer item : schedules.findByActiveTrueAndNextRunOnLessThanEqual(today)) {
            String key = "sched-" + item.getId() + "-" + today.format(MONTH);
            try {
                transfers.transfer(
                        item.getUserId(),
                        key,
                        key,
                        new TransferDtos.TransferRequest(item.getToEmail(), item.getAmountMinor(), item.getNote()));
                advance(item.getId(), today);
                ran++;
            } catch (PayflowException ex) {
                log.warn("scheduled transfer {} skipped: {}", item.getId(), ex.getMessage());
            }
        }
        return ran;
    }

    private void advance(UUID id, LocalDate today) {
        tx.executeWithoutResult(status -> {
            ScheduledTransfer fresh = schedules.findById(id).orElseThrow();
            LocalDate nextMonth = today.withDayOfMonth(1).plusMonths(1);
            int day = Math.min(fresh.getDayOfMonth(), nextMonth.lengthOfMonth());
            fresh.setNextRunOn(nextMonth.withDayOfMonth(day));
        });
    }
}

package com.payflow.ledger;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LedgerInvariantChecker {

    private static final Logger log = LoggerFactory.getLogger(LedgerInvariantChecker.class);

    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;

    public LedgerInvariantChecker(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
    }

    public IntegrityReport check() {
        List<String> violations = new ArrayList<>();
        List<String> unbalanced = jdbc.query("""
                select t.id::text
                from transactions t
                left join ledger_entries e on e.transaction_id = t.id
                group by t.id, t.status
                having coalesce(sum(case when e.direction = 'CREDIT' then e.amount_minor else -e.amount_minor end), 0) <> 0
                    or (t.status in ('COMPLETED', 'REVERSED') and count(e.id) < 2)
                """, (rs, row) -> rs.getString(1));
        unbalanced.forEach(id -> violations.add("transaction " + id + " breaks signed-sum or entry-count"));

        List<String> drifted = jdbc.query("""
                select a.id::text
                from accounts a
                left join (
                    select account_id,
                           sum(case when direction = 'CREDIT' then amount_minor else -amount_minor end) as signed
                    from ledger_entries
                    group by account_id
                ) e on e.account_id = a.id
                where a.balance_minor <> coalesce(e.signed, 0)
                """, (rs, row) -> rs.getString(1));
        drifted.forEach(id -> violations.add("account " + id + " balance does not match its entries"));

        Long global = jdbc.queryForObject("select coalesce(sum(balance_minor), 0) from accounts", Long.class);
        if (global != null && global != 0) {
            violations.add("sum of account balances is " + global);
        }
        List<String> negative = jdbc.query(
                "select id::text from accounts where type <> 'SYSTEM_GATEWAY' and balance_minor < 0",
                (rs, row) -> rs.getString(1));
        negative.forEach(id -> violations.add("account " + id + " is negative"));

        if (!violations.isEmpty()) {
            meters.counter("ledger_integrity_violations").increment(violations.size());
            log.error("CRITICAL ledger integrity violations: {}", violations);
        }
        return new IntegrityReport(violations.isEmpty(), violations);
    }

    public record IntegrityReport(boolean valid, List<String> violations) {
    }
}

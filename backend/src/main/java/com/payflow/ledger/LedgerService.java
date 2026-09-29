package com.payflow.ledger;

import com.payflow.account.Account;
import com.payflow.transaction.WalletTransaction;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {

    private final LedgerEntryRepository entries;
    private final Clock clock;

    public LedgerService(LedgerEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    /**
     * Posts append-only entries and updates cached balances. Caller must already hold the account
     * locks and an open transaction that also inserted {@code tx}.
     */
    public void post(WalletTransaction tx, List<Account> lockedAccounts, List<Leg> legs) {
        if (legs.size() < 2) {
            throw new IllegalStateException("A transaction needs at least two ledger entries");
        }
        Map<UUID, Account> byId = new HashMap<>();
        for (Account account : lockedAccounts) {
            byId.put(account.getId(), account);
        }
        long signed = 0;
        for (Leg leg : legs) {
            if (!byId.containsKey(leg.accountId())) {
                throw new IllegalStateException("Account " + leg.accountId() + " is not locked");
            }
            signed = Math.addExact(signed, leg.direction() == Direction.CREDIT ? leg.amountMinor() : -leg.amountMinor());
        }
        if (signed != 0) {
            throw new IllegalStateException("Ledger entries for a transaction must sum to zero");
        }
        Instant now = clock.instant();
        for (Leg leg : legs) {
            Account account = byId.get(leg.accountId());
            long after = account.apply(leg.direction(), leg.amountMinor());
            LedgerEntry entry = new LedgerEntry();
            entry.setTransactionId(tx.getId());
            entry.setAccountId(account.getId());
            entry.setDirection(leg.direction());
            entry.setAmountMinor(leg.amountMinor());
            entry.setBalanceAfter(after);
            entry.setCreatedAt(now);
            entries.save(entry);
        }
    }
}

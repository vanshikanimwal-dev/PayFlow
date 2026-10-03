package com.payflow.transaction;

import com.payflow.account.Account;
import com.payflow.account.AccountRepository;
import com.payflow.account.WalletLookup;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.config.PayflowProperties;
import com.payflow.ledger.LedgerEntry;
import com.payflow.ledger.LedgerEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionQueryService {

    private final AccountRepository accounts;
    private final WalletLookup wallets;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final EntityManager entityManager;
    private final PayflowProperties properties;

    public TransactionQueryService(
            AccountRepository accounts,
            WalletLookup wallets,
            WalletTransactionRepository transactions,
            LedgerEntryRepository entries,
            EntityManager entityManager,
            PayflowProperties properties) {
        this.accounts = accounts;
        this.wallets = wallets;
        this.transactions = transactions;
        this.entries = entries;
        this.entityManager = entityManager;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public TransactionDtos.WalletResponse wallet(UUID userId) {
        Account account = wallets.spending(userId);
        long savings = wallets.savings(userId).map(Account::getBalanceMinor).orElse(0L);
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TransactionDtos.WalletResponse(
                account.getId(),
                account.getBalanceMinor(),
                account.getCurrency().strip(),
                savings,
                transactions.sumCompletedTransfersSince(userId, startOfDay),
                transactions.sumCompletedTransfersSince(userId, startOfMonth),
                properties.getLimits().getDailyTransferMinor(),
                properties.getLimits().getMonthlyTransferMinor());
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionPage page(UUID userId, String cursor, int limit, TransactionType type, TransactionStatus status) {
        List<Account> owned = wallets.owned(userId);
        int size = Math.min(Math.max(limit <= 0 ? 20 : limit, 1), 100);
        Instant cursorTime = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            TransactionCursor.Decoded decoded = TransactionCursor.decode(cursor);
            cursorTime = decoded.createdAt();
            cursorId = decoded.id();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<WalletTransaction> query = cb.createQuery(WalletTransaction.class);
        Root<WalletTransaction> root = query.from(WalletTransaction.class);
        List<Predicate> predicates = new ArrayList<>();
        List<Predicate> accountMatch = new ArrayList<>();
        for (Account ownedAccount : owned) {
            accountMatch.add(cb.equal(root.get("fromAccountId"), ownedAccount.getId()));
            accountMatch.add(cb.equal(root.get("toAccountId"), ownedAccount.getId()));
        }
        predicates.add(cb.or(accountMatch.toArray(Predicate[]::new)));
        if (type != null) {
            predicates.add(cb.equal(root.get("type"), type));
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        if (cursorTime != null) {
            predicates.add(cb.or(
                    cb.lessThan(root.get("createdAt"), cursorTime),
                    cb.and(cb.equal(root.get("createdAt"), cursorTime), cb.lessThan(root.get("id"), cursorId))));
        }
        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        List<WalletTransaction> rows = entityManager.createQuery(query).setMaxResults(size + 1).getResultList();
        String next = null;
        if (rows.size() > size) {
            rows = new ArrayList<>(rows.subList(0, size));
            WalletTransaction last = rows.get(rows.size() - 1);
            next = TransactionCursor.encode(last.getCreatedAt(), last.getId());
        }
        List<TransactionDtos.TransactionSummary> items = rows.stream().map(this::summary).toList();
        return new TransactionDtos.TransactionPage(items, next);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionDetail detail(UUID userId, UUID transactionId) {
        WalletTransaction tx = transactions.findById(transactionId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        assertOwner(userId, tx);
        return toDetail(tx);
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionDetail byKey(UUID userId, String idempotencyKey) {
        WalletTransaction tx = transactions.findByInitiatorIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Transaction not found"));
        return toDetail(tx);
    }

    private void assertOwner(UUID userId, WalletTransaction tx) {
        List<Account> owned = accounts.findAllByOwnerId(userId);
        boolean owns = userId.equals(tx.getInitiatorId())
                || owned.stream().anyMatch(account -> account.getId().equals(tx.getFromAccountId()) || account.getId().equals(tx.getToAccountId()));
        if (!owns) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "You do not have access to this resource");
        }
    }

    private TransactionDtos.TransactionDetail toDetail(WalletTransaction tx) {
        List<TransactionDtos.LedgerEntryView> views = entries.findByTransactionIdOrderByIdAsc(tx.getId()).stream()
                .map(this::entry)
                .toList();
        return new TransactionDtos.TransactionDetail(
                tx.getId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getAmountMinor(),
                tx.getCurrency().strip(),
                tx.getCreatedAt(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getRefundedMinor(),
                tx.getReversalOf(),
                tx.getGatewayRef(),
                tx.getFailureReason(),
                tx.getIdempotencyKey(),
                views);
    }

    private TransactionDtos.LedgerEntryView entry(LedgerEntry entry) {
        return new TransactionDtos.LedgerEntryView(entry.getAccountId(), entry.getDirection(), entry.getAmountMinor(), entry.getBalanceAfter());
    }

    private TransactionDtos.TransactionSummary summary(WalletTransaction tx) {
        return new TransactionDtos.TransactionSummary(
                tx.getId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getAmountMinor(),
                tx.getCurrency().strip(),
                tx.getCreatedAt(),
                tx.getFromAccountId(),
                tx.getToAccountId());
    }
}

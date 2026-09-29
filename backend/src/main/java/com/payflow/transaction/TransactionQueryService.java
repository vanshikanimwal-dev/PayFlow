package com.payflow.transaction;

import com.payflow.account.Account;
import com.payflow.account.AccountRepository;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.ledger.LedgerEntry;
import com.payflow.ledger.LedgerEntryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionQueryService {

    private final AccountRepository accounts;
    private final WalletTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final EntityManager entityManager;

    public TransactionQueryService(
            AccountRepository accounts,
            WalletTransactionRepository transactions,
            LedgerEntryRepository entries,
            EntityManager entityManager) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public TransactionDtos.WalletResponse wallet(UUID userId) {
        Account account = accounts.findByOwnerId(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found"));
        return new TransactionDtos.WalletResponse(account.getId(), account.getBalanceMinor(), account.getCurrency().strip());
    }

    @Transactional(readOnly = true)
    public TransactionDtos.TransactionPage page(UUID userId, String cursor, int limit, TransactionType type, TransactionStatus status) {
        Account account = accounts.findByOwnerId(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found"));
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
        predicates.add(cb.or(
                cb.equal(root.get("fromAccountId"), account.getId()),
                cb.equal(root.get("toAccountId"), account.getId())));
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
        Account account = accounts.findByOwnerId(userId).orElse(null);
        boolean owns = userId.equals(tx.getInitiatorId())
                || (account != null && (account.getId().equals(tx.getFromAccountId()) || account.getId().equals(tx.getToAccountId())));
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

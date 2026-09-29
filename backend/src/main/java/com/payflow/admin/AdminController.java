package com.payflow.admin;

import com.payflow.audit.AuditService;
import com.payflow.auth.CurrentUser;
import com.payflow.ledger.LedgerInvariantChecker;
import com.payflow.reconciliation.ReconciliationItemRepository;
import com.payflow.reconciliation.ReconciliationRunRepository;
import com.payflow.reconciliation.ReconciliationService;
import com.payflow.reconciliation.Resolution;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final WalletTransactionRepository transactions;
    private final ReconciliationService reconciliation;
    private final ReconciliationRunRepository runs;
    private final ReconciliationItemRepository items;
    private final LedgerInvariantChecker integrity;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AdminController(
            WalletTransactionRepository transactions,
            ReconciliationService reconciliation,
            ReconciliationRunRepository runs,
            ReconciliationItemRepository items,
            LedgerInvariantChecker integrity,
            AuditService audit,
            CurrentUser currentUser) {
        this.transactions = transactions;
        this.reconciliation = reconciliation;
        this.runs = runs;
        this.items = items;
        this.integrity = integrity;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @GetMapping("/transactions")
    public List<AdminViews.TransactionView> transactions(
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Specification<WalletTransaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return transactions.findAll(spec, PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AdminViews::transaction)
                .getContent();
    }

    @GetMapping("/reconciliation/runs")
    public List<AdminViews.ReconciliationRunView> runs() {
        return runs.findAllByOrderByStartedAtDesc().stream().map(AdminViews::run).toList();
    }

    @GetMapping("/reconciliation/runs/{id}/items")
    public List<AdminViews.ReconciliationItemView> items(@PathVariable java.util.UUID id) {
        return items.findByRunIdOrderByIdAsc(id).stream().map(AdminViews::item).toList();
    }

    @PostMapping("/reconciliation/run")
    public AdminViews.ReconciliationRunView run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return AdminViews.run(reconciliation.reconcile(day));
    }

    @PostMapping("/reconciliation/items/{id}/resolve")
    public AdminViews.ReconciliationItemView resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request) {
        var actor = currentUser.require();
        audit.record(actor.id(), actor.role().name(), "RECONCILIATION_RESOLVE", "reconciliation_item", id.toString(), null,
                java.util.Map.of("resolution", request.resolution().name()));
        return AdminViews.item(reconciliation.resolve(id, request.resolution(), request.note(), actor.id()));
    }

    @GetMapping("/integrity")
    public LedgerInvariantChecker.IntegrityReport integrity() {
        return integrity.check();
    }

    @GetMapping("/audit")
    public List<AdminViews.AuditView> audit(
            @RequestParam(required = false) String entity, @RequestParam(required = false) String entityId) {
        return audit.find(entity, entityId).stream().map(AdminViews::audit).toList();
    }

    @GetMapping("/audit/verify")
    public AuditService.AuditVerifyReport verifyAudit() {
        return audit.verify();
    }

    public record ResolveRequest(@NotNull Resolution resolution, String note) {
    }
}

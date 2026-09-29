package com.payflow.transaction;

import com.payflow.auth.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WalletController {

    private final CurrentUser currentUser;
    private final TransactionQueryService queries;

    public WalletController(CurrentUser currentUser, TransactionQueryService queries) {
        this.currentUser = currentUser;
        this.queries = queries;
    }

    @GetMapping("/wallet")
    public TransactionDtos.WalletResponse wallet() {
        return queries.wallet(currentUser.require().id());
    }

    @GetMapping("/wallet/transactions")
    public TransactionDtos.TransactionPage transactions(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status) {
        return queries.page(currentUser.require().id(), cursor, limit, type, status);
    }

    @GetMapping("/transactions/{id}")
    public TransactionDtos.TransactionDetail detail(@PathVariable java.util.UUID id) {
        return queries.detail(currentUser.require().id(), id);
    }

    @GetMapping("/transactions/by-key/{idempotencyKey}")
    public TransactionDtos.TransactionDetail byKey(@PathVariable String idempotencyKey) {
        return queries.byKey(currentUser.require().id(), idempotencyKey);
    }
}

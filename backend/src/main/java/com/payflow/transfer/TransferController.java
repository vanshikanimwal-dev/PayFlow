package com.payflow.transfer;

import com.payflow.auth.CurrentUser;
import com.payflow.auth.PinGuard;
import com.payflow.common.RequestHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final CurrentUser currentUser;
    private final TransferCoordinator transfers;
    private final RequestHasher hasher;
    private final PinGuard pins;

    public TransferController(CurrentUser currentUser, TransferCoordinator transfers, RequestHasher hasher, PinGuard pins) {
        this.currentUser = currentUser;
        this.transfers = transfers;
        this.hasher = hasher;
        this.pins = pins;
    }

    @GetMapping("/recipient")
    public TransferDtos.RecipientView recipient(@RequestParam("q") String query) {
        currentUser.require();
        return transfers.preview(query);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferDtos.TransferResponse transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferDtos.TransferRequest request,
            HttpServletRequest http) {
        var user = currentUser.require();
        pins.require(user.id(), http.getHeader("X-Transaction-Pin"));
        return transfers.transfer(user.id(), idempotencyKey, hasher.hash("POST", http.getRequestURI(), request), request);
    }
}

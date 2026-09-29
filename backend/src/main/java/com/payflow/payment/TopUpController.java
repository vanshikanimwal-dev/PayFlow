package com.payflow.payment;

import com.payflow.auth.CurrentUser;
import com.payflow.common.RequestHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topups")
public class TopUpController {

    private final CurrentUser currentUser;
    private final TopUpService topUps;
    private final RequestHasher hasher;

    public TopUpController(CurrentUser currentUser, TopUpService topUps, RequestHasher hasher) {
        this.currentUser = currentUser;
        this.topUps = topUps;
        this.hasher = hasher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TopUpDtos.TopUpResponse start(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TopUpDtos.TopUpRequest request,
            HttpServletRequest http) {
        var user = currentUser.require();
        return topUps.start(user.id(), idempotencyKey, hasher.hash("POST", http.getRequestURI(), request), request);
    }

    @GetMapping("/{transactionId}")
    public TopUpDtos.TopUpResponse status(@PathVariable UUID transactionId) {
        return topUps.status(currentUser.require().id(), transactionId);
    }
}

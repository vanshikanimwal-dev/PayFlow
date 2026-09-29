package com.payflow.payment;

import com.payflow.auth.CurrentUser;
import com.payflow.common.OptimisticRetry;
import com.payflow.common.RateLimiter;
import com.payflow.common.RequestHasher;
import com.payflow.config.PayflowProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final CurrentUser currentUser;
    private final MerchantPaymentService payments;
    private final RefundService refunds;
    private final RequestHasher hasher;
    private final RateLimiter rateLimiter;
    private final OptimisticRetry optimisticRetry;
    private final PayflowProperties properties;

    public PaymentController(
            CurrentUser currentUser,
            MerchantPaymentService payments,
            RefundService refunds,
            RequestHasher hasher,
            RateLimiter rateLimiter,
            OptimisticRetry optimisticRetry,
            PayflowProperties properties) {
        this.currentUser = currentUser;
        this.payments = payments;
        this.refunds = refunds;
        this.hasher = hasher;
        this.rateLimiter = rateLimiter;
        this.optimisticRetry = optimisticRetry;
        this.properties = properties;
    }

    @PostMapping("/merchant/payment-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public PaymentDtos.PaymentRequestView create(@Valid @RequestBody PaymentDtos.CreatePaymentRequest request) {
        return payments.create(currentUser.require().id(), request);
    }

    @GetMapping("/merchant/payment-requests/{id}")
    public PaymentDtos.PaymentRequestView get(@PathVariable UUID id) {
        currentUser.require();
        return payments.get(id);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDtos.PaymentResponse pay(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentDtos.PayRequest request,
            HttpServletRequest http) {
        var user = currentUser.require();
        rateLimiter.money(user.id());
        String hash = hasher.hash("POST", http.getRequestURI(), request);
        if (properties.getLocking().optimistic()) {
            return optimisticRetry.run(() -> payments.pay(user.id(), idempotencyKey, hash, request));
        }
        return payments.pay(user.id(), idempotencyKey, hash, request);
    }

    @PostMapping("/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDtos.RefundResponse refund(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentDtos.RefundRequest request,
            HttpServletRequest http) {
        var user = currentUser.require();
        rateLimiter.money(user.id());
        String hash = hasher.hash("POST", http.getRequestURI(), request);
        if (properties.getLocking().optimistic()) {
            return optimisticRetry.run(() -> refunds.refund(user.id(), user.role(), idempotencyKey, hash, request));
        }
        return refunds.refund(user.id(), user.role(), idempotencyKey, hash, request);
    }
}

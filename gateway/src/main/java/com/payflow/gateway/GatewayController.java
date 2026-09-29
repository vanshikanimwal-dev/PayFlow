package com.payflow.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GatewayController {

    private final GatewayPaymentService payments;
    private final GatewayPaymentRepository repository;
    private final ChaosService chaos;

    public GatewayController(GatewayPaymentService payments, GatewayPaymentRepository repository, ChaosService chaos) {
        this.payments = payments;
        this.repository = repository;
        this.chaos = chaos;
    }

    @PostMapping("/v1/payments")
    public Object create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePayment request,
            HttpServletRequest http) {
        boolean replay = repository.findByIdempotencyKey(idempotencyKey).isPresent();
        if (!replay && chaos.error()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "chaos error");
        }
        if (!replay) {
            chaos.latency();
        }
        GatewayPaymentEntity payment = payments.create(
                idempotencyKey, request.reference(), request.amountMinor(), request.method(), request.callbackUrl());
        if (!replay && chaos.timeout()) {
            http.startAsync();
            return null;
        }
        return view(payment);
    }

    @GetMapping("/v1/payments/{id}")
    public Map<String, Object> byId(@PathVariable String id) {
        return view(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping(value = "/v1/payments", params = "reference")
    public Map<String, Object> byReference(@RequestParam String reference) {
        return view(repository.findByReference(reference).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping(value = "/pay/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public String page(@PathVariable String id) {
        repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return """
                <!doctype html>
                <title>PayFlow mock checkout</title>
                <h1>Simulated checkout</h1>
                <form method="post" action="/pay/%s/complete"><button>Pay</button></form>
                <form method="post" action="/pay/%s/fail"><button>Fail</button></form>
                """.formatted(id, id);
    }

    @PostMapping("/pay/{id}/complete")
    public String pay(@PathVariable String id) {
        payments.complete(id, true);
        return "captured";
    }

    @PostMapping("/pay/{id}/fail")
    public String fail(@PathVariable String id) {
        payments.complete(id, false);
        return "failed";
    }

    @PostMapping("/v1/refunds")
    public Map<String, Object> refund(@Valid @RequestBody RefundRequest request) {
        return view(payments.refund(request.paymentId(), request.amountMinor()));
    }

    @GetMapping(value = "/v1/settlements/{date}.csv", produces = "text/csv")
    public String settlement(@PathVariable LocalDate date) {
        return payments.settlementCsv(date, chaos);
    }

    @PostMapping("/admin/chaos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void chaos(@RequestBody ChaosConfig config) {
        chaos.replace(config);
    }

    private static Map<String, Object> view(GatewayPaymentEntity payment) {
        return Map.of(
                "paymentId", payment.getId(),
                "status", payment.getStatus(),
                "amountMinor", payment.getAmountMinor(),
                "payUrl", "http://localhost:8081/pay/" + payment.getId(),
                "reference", payment.getReference());
    }

    public record CreatePayment(@NotBlank String reference, @Positive long amountMinor, @NotBlank String method, String callbackUrl) {
    }

    public record RefundRequest(@NotBlank String paymentId, @Positive long amountMinor) {
    }
}

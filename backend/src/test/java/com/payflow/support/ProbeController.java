package com.payflow.support;

import com.payflow.common.CorrelationIds;
import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only routes used to exercise the error contract. */
@RestController
@RequestMapping("/api/v1/_probe")
public class ProbeController {

    @GetMapping("/correlation")
    Map<String, String> correlation() {
        return Map.of("correlationId", CorrelationIds.current());
    }

    @PostMapping("/echo")
    Map<String, String> echo(@Valid @RequestBody EchoRequest request) {
        return Map.of("name", request.name());
    }

    @GetMapping("/missing-funds")
    void missingFunds() {
        throw new PayflowException(
                ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.UNPROCESSABLE_ENTITY, "Wallet balance is too low");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("database password=secret");
    }

    public record EchoRequest(@NotBlank String name) {
    }
}

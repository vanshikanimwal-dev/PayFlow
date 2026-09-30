package com.payflow.gatewayclient;

import com.payflow.common.CorrelationIds;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GatewayHttpClient implements GatewayClient {

    private final RestClient http;
    private final io.github.resilience4j.retry.Retry retry;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;

    public GatewayHttpClient(RestClient gatewayRestClient, RetryRegistry retries, CircuitBreakerRegistry breakers) {
        this.http = gatewayRestClient;
        this.retry = retries.retry("gateway");
        this.circuitBreaker = breakers.circuitBreaker("gateway");
    }

    @Override
    public GatewayPayment createPayment(UUID reference, long amountMinor, String method, String callbackUrl) {
        return guard(() -> http.post()
                .uri("/v1/payments")
                .header("Idempotency-Key", reference.toString())
                .header(CorrelationIds.HEADER, CorrelationIds.current())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new CreateBody(reference.toString(), amountMinor, method, callbackUrl))
                .retrieve()
                .body(GatewayPayment.class));
    }

    @Override
    public Optional<GatewayPayment> findByReference(UUID reference) {
        try {
            GatewayPayment payment = guard(() -> http.get()
                    .uri("/v1/payments?reference={reference}", reference)
                    .header(CorrelationIds.HEADER, CorrelationIds.current())
                    .retrieve()
                    .body(GatewayPayment.class));
            return Optional.ofNullable(payment);
        } catch (GatewayRejectedException ex) {
            return Optional.empty();
        }
    }

    @Override
    public String settlementCsv(LocalDate date) {
        return guard(() -> http.get()
                .uri("/v1/settlements/{date}.csv", date)
                .retrieve()
                .body(String.class));
    }

    private <T> T guard(java.util.function.Supplier<T> call) {
        java.util.function.Supplier<T> wrapped = () -> {
            try {
                return call.get();
            } catch (RestClientResponseException ex) {
                throw mapStatus(ex);
            } catch (ResourceAccessException ex) {
                throw new GatewayUnknownException("Gateway timed out", ex);
            } catch (RestClientException ex) {
                throw new GatewayUnknownException("Gateway timed out", ex);
            }
        };
        try {
            return retry.executeSupplier(() -> circuitBreaker.executeSupplier(wrapped));
        } catch (CallNotPermittedException ex) {
            throw new GatewayRejectedException("Gateway circuit is open");
        }
    }

    private RuntimeException mapStatus(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        if (status.value() == 404) {
            return new GatewayRejectedException("Gateway payment was not found");
        }
        if (status.is4xxClientError()) {
            return new GatewayRejectedException("Gateway rejected the payment");
        }
        return new GatewayUnknownException("Gateway returned " + status.value());
    }

    private record CreateBody(String reference, long amountMinor, String method, String callbackUrl) {
    }
}

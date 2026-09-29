package com.payflow.payment;

import com.payflow.common.ErrorCode;
import com.payflow.common.PayflowException;
import com.payflow.common.RateLimiter;
import com.payflow.config.PayflowProperties;
import com.payflow.gatewayclient.GatewayClient;
import com.payflow.gatewayclient.GatewayPayment;
import com.payflow.gatewayclient.GatewayRejectedException;
import com.payflow.gatewayclient.GatewayUnknownException;
import com.payflow.transaction.TransactionStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TopUpService {

    private final TopUpStore store;
    private final GatewayClient gateway;
    private final PayflowProperties properties;
    private final RateLimiter rateLimiter;

    public TopUpService(TopUpStore store, GatewayClient gateway, PayflowProperties properties, RateLimiter rateLimiter) {
        this.store = store;
        this.gateway = gateway;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    public TopUpDtos.TopUpResponse start(UUID userId, String key, String hash, TopUpDtos.TopUpRequest request) {
        rateLimiter.money(userId);
        if (request.amountMinor() > properties.getLimits().getMaxTransferMinor()) {
            throw new PayflowException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds the per-transfer limit");
        }
        TopUpStore.Started started = store.begin(userId, key, hash, request.amountMinor());
        if (started.replay() != null) {
            return started.replay();
        }
        var tx = started.created();
        try {
            GatewayPayment payment = gateway.createPayment(
                    tx.getId(), tx.getAmountMinor(), request.method().name(), properties.getGateway().getCallbackUrl());
            store.markProcessing(tx.getId(), payment.paymentId());
            TopUpDtos.TopUpResponse response =
                    new TopUpDtos.TopUpResponse(tx.getId(), TransactionStatus.PROCESSING.name(), payment.payUrl());
            store.finishIdempotency(userId, key, response);
            return response;
        } catch (GatewayRejectedException ex) {
            store.markFailed(tx.getId(), ex.getMessage());
            TopUpDtos.TopUpResponse response = new TopUpDtos.TopUpResponse(tx.getId(), TransactionStatus.FAILED.name(), null);
            store.finishIdempotency(userId, key, response);
            return response;
        } catch (GatewayUnknownException ex) {
            store.markProcessing(tx.getId(), null);
            throw new PayflowException(
                    ErrorCode.GATEWAY_TIMEOUT,
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Gateway result is unknown; the top-up stays processing");
        }
    }

    public TopUpDtos.TopUpResponse status(UUID userId, UUID transactionId) {
        return store.status(userId, transactionId);
    }
}

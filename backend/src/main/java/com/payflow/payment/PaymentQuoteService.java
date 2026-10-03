package com.payflow.payment;

import com.payflow.common.Fees;
import com.payflow.config.PayflowProperties;
import org.springframework.stereotype.Service;

@Service
public class PaymentQuoteService {

    private final PayflowProperties properties;

    public PaymentQuoteService(PayflowProperties properties) {
        this.properties = properties;
    }

    public PaymentDtos.Quote quote(long amountMinor) {
        long fee = Fees.percentHalfUp(amountMinor, properties.getFees().getPaymentPercent());
        long cashback = Fees.percentHalfUp(amountMinor, properties.getLimits().getCashbackPercent());
        if (fee <= 0 || cashback <= 0 || cashback > fee) {
            cashback = 0;
        }
        return new PaymentDtos.Quote(amountMinor, fee, amountMinor - fee, cashback);
    }
}
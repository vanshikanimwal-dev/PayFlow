-- One payment request can be claimed by only one completed payment.
ALTER TABLE transactions ADD COLUMN payment_request_id UUID;

CREATE UNIQUE INDEX ux_tx_payment_request
    ON transactions (payment_request_id)
    WHERE payment_request_id IS NOT NULL;

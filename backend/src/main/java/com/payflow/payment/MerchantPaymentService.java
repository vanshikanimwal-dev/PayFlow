package com.payflow.payment;

import com.payflow.account.Account;
import com.payflow.account.AccountLocker;
import com.payflow.account.AccountRepository;
import com.payflow.account.AccountStatus;
import com.payflow.account.AccountType;
import com.payflow.audit.AuditService;
import com.payflow.auth.UserRepository;
import com.payflow.auth.UserRole;
import com.payflow.common.ErrorCode;
import com.payflow.common.Fees;
import com.payflow.common.IdGenerator;
import com.payflow.common.PayflowException;
import com.payflow.common.SystemAccounts;
import com.payflow.config.PayflowProperties;
import com.payflow.idempotency.BeginResult;
import com.payflow.idempotency.IdempotencyService;
import com.payflow.ledger.LedgerService;
import com.payflow.ledger.Leg;
import com.payflow.outbox.OutboxWriter;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantPaymentService {

    private final PaymentRequestRepository requests;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final AccountLocker locker;
    private final WalletTransactionRepository transactions;
    private final LedgerService ledger;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final IdGenerator ids;
    private final Clock clock;
    private final PayflowProperties properties;

    public MerchantPaymentService(
            PaymentRequestRepository requests,
            AccountRepository accounts,
            UserRepository users,
            AccountLocker locker,
            WalletTransactionRepository transactions,
            LedgerService ledger,
            IdempotencyService idempotency,
            AuditService audit,
            OutboxWriter outbox,
            IdGenerator ids,
            Clock clock,
            PayflowProperties properties) {
        this.requests = requests;
        this.accounts = accounts;
        this.users = users;
        this.locker = locker;
        this.transactions = transactions;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.audit = audit;
        this.outbox = outbox;
        this.ids = ids;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public PaymentDtos.PaymentRequestView create(UUID merchantUserId, PaymentDtos.CreatePaymentRequest request) {
        var user = users.findById(merchantUserId).orElseThrow();
        if (user.getRole() != UserRole.MERCHANT) {
            throw new PayflowException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "Only a merchant can create a payment request");
        }
        Account merchant = accounts.findByOwnerId(merchantUserId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Merchant account not found"));
        if (merchant.getType() != AccountType.MERCHANT || merchant.getStatus() != AccountStatus.ACTIVE) {
            throw new PayflowException(ErrorCode.ACCOUNT_INACTIVE, HttpStatus.UNPROCESSABLE_ENTITY, "Account is not active");
        }
        if (request.amountMinor() > properties.getLimits().getMaxTransferMinor()) {
            throw new PayflowException(ErrorCode.LIMIT_EXCEEDED, HttpStatus.UNPROCESSABLE_ENTITY, "Amount exceeds the per-transfer limit");
        }
        Instant now = clock.instant();
        PaymentRequest created = new PaymentRequest();
        created.setId(ids.newId());
        created.setMerchantAccountId(merchant.getId());
        created.setAmountMinor(request.amountMinor());
        created.setDescription(request.description());
        created.setStatus(PaymentRequestStatus.OPEN);
        created.setExpiresAt(now.plus(properties.getPayments().getRequestTtl()));
        created.setCreatedAt(now);
        requests.save(created);
        audit.record(merchantUserId, user.getRole().name(), "PAYMENT_REQUEST", "payment_request", created.getId().toString(), null,
                Map.of("amountMinor", created.getAmountMinor()));
        return view(created);
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentRequestView get(UUID id) {
        PaymentRequest request = requests.findById(id)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment request not found"));
        return view(request);
    }

    @Transactional
    public PaymentDtos.PaymentResponse pay(UUID userId, String key, String hash, PaymentDtos.PayRequest body) {
        BeginResult<PaymentDtos.PaymentResponse> begin =
                idempotency.begin(userId, key, hash, PaymentDtos.PaymentResponse.class);
        if (begin instanceof BeginResult.Replay<PaymentDtos.PaymentResponse> replay) {
            return replay.body();
        }
        PaymentRequest request = requests.findById(body.paymentRequestId())
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment request not found"));
        Account customer = accounts.findByOwnerId(userId)
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Wallet not found"));
        if (customer.getId().equals(request.getMerchantAccountId())) {
            throw new PayflowException(ErrorCode.SELF_TRANSFER, HttpStatus.UNPROCESSABLE_ENTITY, "Cannot pay your own request");
        }
        long fee = Fees.percentHalfUp(request.getAmountMinor(), properties.getFees().getPaymentPercent());
        long merchantAmount = request.getAmountMinor() - fee;
        List<UUID> accountIds = new ArrayList<>();
        accountIds.add(customer.getId());
        accountIds.add(request.getMerchantAccountId());
        if (fee > 0) {
            accountIds.add(SystemAccounts.FEE);
        }
        List<Account> locked = locker.lock(accountIds);
        PaymentRequest lockedRequest = requests.lockById(request.getId())
                .orElseThrow(() -> new PayflowException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "Payment request not found"));
        if (lockedRequest.getStatus() == PaymentRequestStatus.PAID) {
            throw new PayflowException(ErrorCode.ALREADY_PAID, HttpStatus.CONFLICT, "Payment request is already paid");
        }
        if (lockedRequest.getStatus() == PaymentRequestStatus.EXPIRED || lockedRequest.getExpiresAt().isBefore(clock.instant())) {
            throw new PayflowException(ErrorCode.VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_ENTITY, "Payment request has expired");
        }
        Instant now = clock.instant();
        WalletTransaction tx = new WalletTransaction();
        tx.setId(ids.newId());
        tx.setType(TransactionType.PAYMENT);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setAmountMinor(lockedRequest.getAmountMinor());
        tx.setCurrency("INR");
        tx.setInitiatorId(userId);
        tx.setFromAccountId(customer.getId());
        tx.setToAccountId(lockedRequest.getMerchantAccountId());
        tx.setIdempotencyKey(key);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        tx.transitionTo(TransactionStatus.COMPLETED, now);
        transactions.saveAndFlush(tx);
        List<Leg> legs = new ArrayList<>();
        legs.add(Leg.debit(customer.getId(), lockedRequest.getAmountMinor()));
        legs.add(Leg.credit(lockedRequest.getMerchantAccountId(), merchantAmount));
        if (fee > 0) {
            legs.add(Leg.credit(SystemAccounts.FEE, fee));
        }
        ledger.post(tx, locked, legs);
        lockedRequest.setStatus(PaymentRequestStatus.PAID);
        Account lockedCustomer = AccountLocker.require(locked, customer.getId());
        PaymentDtos.PaymentResponse response =
                new PaymentDtos.PaymentResponse(tx.getId(), tx.getStatus().name(), lockedCustomer.getBalanceMinor());
        audit.record(userId, null, "PAYMENT", "transaction", tx.getId().toString(), null,
                Map.of("amountMinor", tx.getAmountMinor(), "feeMinor", fee, "paymentRequestId", lockedRequest.getId().toString()));
        outbox.enqueue(tx.getId(), "PAYMENT_COMPLETED", Map.of("transactionId", tx.getId().toString(), "amountMinor", tx.getAmountMinor()));
        idempotency.complete(userId, key, 201, response);
        return response;
    }

    public int expireOpenRequests() {
        List<PaymentRequest> stale = requests.findByStatusAndExpiresAtBefore(PaymentRequestStatus.OPEN, clock.instant());
        for (PaymentRequest request : stale) {
            request.setStatus(PaymentRequestStatus.EXPIRED);
        }
        requests.saveAll(stale);
        return stale.size();
    }

    private PaymentDtos.PaymentRequestView view(PaymentRequest request) {
        String status = request.getStatus().name();
        if (request.getStatus() == PaymentRequestStatus.OPEN && request.getExpiresAt().isBefore(clock.instant())) {
            status = PaymentRequestStatus.EXPIRED.name();
        }
        return new PaymentDtos.PaymentRequestView(
                request.getId(),
                "payflow://pay?requestId=" + request.getId(),
                request.getExpiresAt(),
                status,
                request.getAmountMinor(),
                request.getDescription());
    }
}

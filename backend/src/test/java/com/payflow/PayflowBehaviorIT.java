package com.payflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.account.AccountRepository;
import com.payflow.common.Hashes;
import com.payflow.common.SystemAccounts;
import com.payflow.ledger.LedgerInvariantChecker;
import com.payflow.payment.TopUpCompletionService;
import com.payflow.saga.SagaRecoveryJob;
import com.payflow.transaction.TransactionStatus;
import com.payflow.transaction.TransactionType;
import com.payflow.transaction.WalletTransaction;
import com.payflow.transaction.WalletTransactionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class PayflowBehaviorIT extends AbstractIntegrationTest {

    private static final String HMAC = "test-gateway-hmac-secret";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TopUpCompletionService completion;

    @Autowired
    private WalletTransactionRepository transactions;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SagaRecoveryJob saga;

    @Autowired
    private LedgerInvariantChecker integrity;

    @Autowired
    private com.payflow.audit.AuditService audit;

    @Autowired
    private com.payflow.reconciliation.ReconciliationService reconciliation;

    @Autowired
    private com.payflow.common.RateLimiter rateLimiter;

    @Test
    void transferMovesMoneyOnceAndRejectsSelfTransfer() throws Exception {
        Session alice = register("USER");
        Session bob = register("USER");
        fund(alice, 50_000);

        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(alice))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(alice.email(), 100)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER"));

        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(alice))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(bob.email(), 1_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balanceAfterMinor").value(49_000));

        assertThat(balance(bob)).isEqualTo(1_000);
        assertThat(integrity.check().valid()).isTrue();
    }

    @Test
    void insufficientBalanceIsNotCachedSoTheSameKeyCanSucceedLater() throws Exception {
        Session alice = register("USER");
        Session bob = register("USER");
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(alice))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(bob.email(), 1_000)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        fund(alice, 5_000);
        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(alice))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(bob.email(), 1_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfterMinor").value(4_000));
    }

    @Test
    void parallelIdempotentTransfersCreateOneTransactionAndKeyReuseConflicts() throws Exception {
        Session alice = register("USER");
        Session bob = register("USER");
        fund(alice, 100_000);
        String key = UUID.randomUUID().toString();
        String body = transferBody(bob.email(), 10);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futures.add(pool.submit(() -> mvc.perform(post("/api/v1/transfers")
                                .header("Authorization", bearer(alice))
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn()));
            }
            List<String> bodies = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                bodies.add(result.getResponse().getContentAsString());
            }
            assertThat(bodies).allMatch(item -> item.equals(bodies.get(0)));
        } finally {
            pool.shutdownNow();
        }
        assertThat(transactions.findByInitiatorIdAndIdempotencyKey(alice.userId(), key)).isPresent();
        assertThat(jdbc.queryForObject(
                "select count(*) from transactions where initiator_id = ? and idempotency_key = ?",
                Integer.class, alice.userId(), key)).isEqualTo(1);

        mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", bearer(alice))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(bob.email(), 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void walletHistoryIsCursorPaginated() throws Exception {
        Session alice = register("USER");
        Session bob = register("USER");
        fund(alice, 10_000);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/transfers")
                            .header("Authorization", bearer(alice))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transferBody(bob.email(), 10)))
                    .andExpect(status().isCreated());
        }
        MvcResult page = mvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", bearer(alice))
                        .param("limit", "2")
                        .param("type", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        String cursor = com.jayway.jsonpath.JsonPath.read(page.getResponse().getContentAsString(), "$.nextCursor");
        mvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", bearer(alice))
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .param("type", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void concurrentTransfersConserveMoneyAndOrderedLocksAvoidDeadlock() throws Exception {
        List<Session> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Session user = register("USER");
            fund(user, 100_000);
            users.add(user);
        }
        ExecutorService pool = Executors.newFixedThreadPool(100);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> {
                    Session from = users.get(ThreadLocalRandom.current().nextInt(users.size()));
                    Session to = users.get(ThreadLocalRandom.current().nextInt(users.size()));
                    int status = mvc.perform(post("/api/v1/transfers")
                                    .header("Authorization", bearer(from))
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(transferBody(to.email(), 1 + ThreadLocalRandom.current().nextInt(20))))
                            .andReturn().getResponse().getStatus();
                    if (status >= 500) {
                        throw new IllegalStateException("transfer failed with " + status);
                    }
                    return status;
                }));
            }
            for (Future<Integer> future : futures) {
                future.get();
            }
            Session a = users.get(0);
            Session b = users.get(1);
            List<Future<Integer>> cross = List.of(
                    pool.submit(cross(a, b)),
                    pool.submit(cross(b, a)));
            for (Future<Integer> future : cross) {
                assertThat(future.get()).isBetween(200, 422);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(integrity.check().valid()).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from accounts where type <> 'SYSTEM_GATEWAY' and balance_minor < 0",
                Integer.class)).isZero();
    }

    @Test
    void duplicateAndOutOfOrderWebhooksCreditOnceAndBadSignaturesDoNot() throws Exception {
        Session alice = register("USER");
        WalletTransaction pending = processingTopUp(alice, 4_000, TransactionStatus.PENDING);
        String eventId = "evt-" + UUID.randomUUID();
        String body = webhook(eventId, "payment.succeeded", "pi_1", 4_000, pending.getId());
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(pool.submit(() -> postWebhook(body).getResponse().getStatus()));
            }
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(balance(alice)).isEqualTo(4_000);
        assertThat(transactions.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        long before = balance(alice);
        MvcResult rejected = postWebhookRaw(body, "t=1,v1=deadbeef");
        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
        long oldTs = Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond();
        String signedOld = sign(oldTs, body);
        assertThat(postWebhookRaw(body, signedOld).getResponse().getStatus()).isEqualTo(401);
        assertThat(balance(alice)).isEqualTo(before);
    }

    @Test
    void recoveryCompletesAProcessingTopUpExactlyOnce() throws Exception {
        Session alice = register("USER");
        WalletTransaction tx = processingTopUp(alice, 7_500, TransactionStatus.PROCESSING);
        jdbc.update("update transactions set updated_at = now() - interval '5 minutes' where id = ?", tx.getId());
        when(gateway.findByReference(tx.getId())).thenReturn(
                Optional.of(new com.payflow.gatewayclient.GatewayPayment("pi_recover", "CAPTURED", 7_500, "http://pay", tx.getId().toString())));
        saga.recover();
        saga.recover();
        assertThat(balance(alice)).isEqualTo(7_500);
        assertThat(jdbc.queryForObject(
                "select count(*) from ledger_entries where transaction_id = ?", Integer.class, tx.getId())).isEqualTo(2);
    }

    @Test
    void twoCustomersCannotPayTheSameRequest() throws Exception {
        Session merchant = register("MERCHANT");
        Session a = register("USER");
        Session b = register("USER");
        fund(a, 200_000);
        fund(b, 200_000);
        MvcResult created = mvc.perform(post("/api/v1/merchant/payment-requests")
                        .header("Authorization", bearer(merchant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":100000,\"description\":\"lunch\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qrPayload").exists())
                .andReturn();
        String requestId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String pay = "{\"paymentRequestId\":\"" + requestId + "\"}";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> pay(a, pay));
            Future<Integer> second = pool.submit(() -> pay(b, pay));
            List<Integer> statuses = List.of(first.get(), second.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(balance(merchant)).isEqualTo(98_000);
        long fee = accounts.findById(SystemAccounts.FEE).orElseThrow().getBalanceMinor();
        assertThat(fee).isGreaterThanOrEqualTo(2_000);
    }

    @Test
    void merchantRefundReturnsPrincipalAndFee() throws Exception {
        Session merchant = register("MERCHANT");
        Session alice = register("USER");
        fund(alice, 200_000);
        MvcResult created = mvc.perform(post("/api/v1/merchant/payment-requests")
                        .header("Authorization", bearer(merchant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":100000,\"description\":\"dinner\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        MvcResult paid = mvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(alice))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentRequestId\":\"" + requestId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String txId = com.jayway.jsonpath.JsonPath.read(paid.getResponse().getContentAsString(), "$.transactionId");
        mvc.perform(post("/api/v1/refunds")
                        .header("Authorization", bearer(merchant))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"" + txId + "\",\"amountMinor\":100000}"))
                .andExpect(status().isCreated());
        assertThat(balance(alice)).isEqualTo(200_000);
        assertThat(balance(merchant)).isZero();
    }

    @Test
    void reconciliationClassifiesAndAutoFixes() throws Exception {
        Session alice = register("USER");
        WalletTransaction missingLedger = processingTopUp(alice, 3_000, TransactionStatus.PROCESSING);
        missingLedger.setGatewayRef("pi_fix");
        transactions.save(missingLedger);
        WalletTransaction extra = processingTopUp(alice, 2_000, TransactionStatus.PROCESSING);
        completion.complete(extra.getId(), 2_000, "pi_extra");
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        String csv = """
                payment_id,reference,amount_minor,status,captured_at
                pi_fix,%s,3000,CAPTURED,2026-01-01T00:00:00Z
                pi_bad,%s,999,CAPTURED,2026-01-01T00:00:00Z
                """.formatted(missingLedger.getId(), extra.getId());
        var run = reconciliation.apply(today, csv);
        assertThat(run.getMismatched()).isGreaterThan(0);
        assertThat(transactions.findById(missingLedger.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(balance(alice)).isEqualTo(5_000);
    }

    @Test
    void integrityCheckerAndAuditChainDetectTamperingAndLedgerIsAppendOnly() throws Exception {
        Session alice = register("USER");
        fund(alice, 1_000);
        assertThat(audit.verify().valid()).isTrue();
        jdbc.update("update accounts set balance_minor = balance_minor + 5 where id = ?", alice.accountId());
        assertThat(integrity.check().valid()).isFalse();
        jdbc.update("update accounts set balance_minor = balance_minor - 5 where id = ?", alice.accountId());
        assertThat(integrity.check().valid()).isTrue();

        Long auditId = jdbc.queryForObject("select max(id) from audit_log", Long.class);
        String original = jdbc.queryForObject("select hash from audit_log where id = ?", String.class, auditId);
        jdbc.update("update audit_log set hash = ? where id = ?", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", auditId);
        assertThat(audit.verify().valid()).isFalse();
        jdbc.update("update audit_log set hash = ? where id = ?", original, auditId);

        Long entryId = jdbc.queryForObject("select max(id) from ledger_entries", Long.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jdbc.update("update ledger_entries set amount_minor = amount_minor where id = ?", entryId))
                .hasMessageContaining("append-only");
    }

    @Test
    void rateLimitAndAdminLogin() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            rateLimiter.hit("spec-bucket", 1, 60);
            rateLimiter.hit("spec-bucket", 1, 60);
        }).isInstanceOf(com.payflow.common.RateLimitedException.class);
    }

    @Test
    void seededAdminCanOpenIntegrity() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@payflow.local\",\"password\":\"admin-dev-change-me\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/admin/integrity").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").exists());
        mvc.perform(get("/api/v1/admin/audit/verify").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Callable<Integer> cross(Session from, Session to) {
        return () -> {
            int worst = 201;
            for (int i = 0; i < 1000; i++) {
                int status = mvc.perform(post("/api/v1/transfers")
                                .header("Authorization", bearer(from))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferBody(to.email(), 1)))
                        .andReturn().getResponse().getStatus();
                if (status >= 500) {
                    throw new IllegalStateException("deadlock or server error " + status);
                }
                worst = Math.max(worst, status);
            }
            return worst;
        };
    }

    private int pay(Session user, String body) throws Exception {
        return mvc.perform(post("/api/v1/payments")
                        .header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private MvcResult postWebhook(String body) throws Exception {
        long ts = Instant.now().getEpochSecond();
        return postWebhookRaw(body, sign(ts, body));
    }

    private MvcResult postWebhookRaw(String body, String signature) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/gateway")
                        .header("X-Gateway-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private static String sign(long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + Hashes.hmacSha256(HMAC, timestamp + "." + body);
    }

    private static String webhook(String eventId, String type, String paymentId, long amount, UUID txId) {
        return """
                {"eventId":"%s","type":"%s","paymentId":"%s","amountMinor":%d,"reference":"%s","createdAt":"2026-01-01T00:00:00Z"}
                """.formatted(eventId, type, paymentId, amount, txId);
    }

    private WalletTransaction processingTopUp(Session user, long amount, TransactionStatus status) {
        WalletTransaction tx = new WalletTransaction();
        tx.setId(UUID.randomUUID());
        tx.setType(TransactionType.TOPUP);
        tx.setStatus(status);
        tx.setAmountMinor(amount);
        tx.setCurrency("INR");
        tx.setInitiatorId(user.userId());
        tx.setFromAccountId(SystemAccounts.GATEWAY);
        tx.setToAccountId(user.accountId());
        tx.setIdempotencyKey("topup-" + tx.getId());
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        return transactions.saveAndFlush(tx);
    }

    private void fund(Session user, long amount) {
        WalletTransaction tx = processingTopUp(user, amount, TransactionStatus.PROCESSING);
        completion.complete(tx.getId(), amount, "pi_" + tx.getId());
    }

    private long balance(Session user) {
        return accounts.findById(user.accountId()).orElseThrow().getBalanceMinor();
    }

    private Session register(String role) throws Exception {
        String email = "u-" + UUID.randomUUID() + "@payflow.test";
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password-1\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        UUID userId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(payload, "$.sub"));
        MvcResult wallet = mvc.perform(get("/api/v1/wallet").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        UUID accountId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(wallet.getResponse().getContentAsString(), "$.accountId"));
        return new Session(token, userId, accountId, email);
    }

    private static String bearer(Session session) {
        return "Bearer " + session.token();
    }

    private static String transferBody(String email, long amount) {
        return "{\"toUserEmailOrPhone\":\"" + email + "\",\"amountMinor\":" + amount + ",\"note\":\"test\"}";
    }

    private record Session(String token, UUID userId, UUID accountId, String email) {
    }
}

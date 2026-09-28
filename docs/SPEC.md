# PayFlow: Payments & Wallet System (Simulation)

> Spec for building in Cursor. Put this file at `docs/SPEC.md` in the repo and reference it with `@docs/SPEC.md` in prompts.
> **This is a simulation. Never handle real card data or real money.**

---

## 1. Project goal

Build a wallet and payments backend (Java / Spring Boot) with a Flutter client, where the hard engineering goal is:

> **Money is never lost, duplicated, or created, even under concurrent requests, client retries, crashes mid-operation, and an unreliable external gateway.**

### What the system does
- Users register and get a wallet (INR, stored in paise).
- Users **top up** the wallet through a mock payment gateway (card/UPI simulation).
- Users **transfer** money to other users.
- Users **pay merchants** (QR-based), with a platform fee.
- Merchants and admins can **refund**.
- A **reconciliation** job compares the internal ledger with the gateway's settlement file.
- Every sensitive action is written to a **tamper-evident audit log**.

### Non-goals (do not build)
- Real KYC, real banking/UPI integration, multi-currency FX, microservice split of core modules, Kubernetes.

### Skills this project demonstrates
Double-entry ledger, ACID transactions, pessimistic/optimistic locking, deadlock avoidance, idempotency, saga + state machine, transactional outbox, webhook security, reconciliation, audit logging, concurrency testing.

---

## 2. Tech stack

### Backend (`/backend`)
| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.x (Web, Data JPA, Security, Validation, Actuator) |
| Build | Maven |
| DB | PostgreSQL 16 |
| Migrations | Flyway (never let Hibernate auto-create the schema; `ddl-auto=validate`) |
| Cache / locks / rate limit | Redis 7 (Bucket4j or a custom token bucket for rate limiting) |
| Messaging | RabbitMQ (simpler) or Kafka. **Start with an in-process outbox publisher, add the broker in phase 5** |
| Auth | Spring Security + JWT (access token 15 min, refresh token 7 days, stored hashed) |
| Mapping | MapStruct |
| Docs | springdoc-openapi (Swagger UI) |
| Scheduling | Spring `@Scheduled` + ShedLock (so jobs don't double-run across instances) |
| Testing | JUnit 5, Mockito, **Testcontainers (real PostgreSQL + Redis)**, AssertJ, Awaitility |
| Observability | Micrometer + Prometheus metrics, structured JSON logs with `correlationId` |

### Mock gateway (`/gateway`)
Separate Spring Boot service on its own port, own H2 or Postgres schema. It must be a **separate process** so failures are real.

### Client (`/mobile`)
Flutter 3.x: Riverpod, Dio, go_router, freezed + json_serializable, flutter_secure_storage, Drift, fl_chart, qr_flutter, mobile_scanner, mocktail.

### Infra
Docker Compose (postgres, redis, rabbitmq, backend, gateway), GitHub Actions CI, deploy backend on Render/Railway/EC2.

---

## 3. Repository layout (monorepo)

```
payflow/
├─ docs/
│  ├─ SPEC.md
│  ├─ DESIGN.md              (design decisions, tradeoffs, written as you build)
│  └─ architecture.png
├─ backend/
│  ├─ pom.xml
│  └─ src/main/java/com/payflow/
│     ├─ PayflowApplication.java
│     ├─ common/             (errors, ApiResponse, Money, IdGenerator, clock, correlation filter)
│     ├─ config/             (security, redis, scheduling, openapi)
│     ├─ auth/               (controller, service, jwt, dto, entity, repo)
│     ├─ account/            (Account entity, AccountService, AccountRepository)
│     ├─ ledger/             (LedgerEntry, LedgerService, LedgerInvariantChecker)
│     ├─ transaction/        (Transaction entity, state machine, TransactionQueryService)
│     ├─ transfer/           (TransferController, TransferService)
│     ├─ payment/            (TopUp, MerchantPayment, PaymentRequest, refund)
│     ├─ saga/               (TopUpSaga, SagaRecoveryJob)
│     ├─ gatewayclient/      (GatewayClient with timeouts, retries, circuit breaker)
│     ├─ webhook/            (WebhookController, signature verifier, dedupe)
│     ├─ idempotency/        (IdempotencyService, filter/aspect, entity)
│     ├─ outbox/             (OutboxEvent, OutboxPublisher)
│     ├─ reconciliation/     (SettlementFileParser, ReconciliationJob, report entities)
│     ├─ audit/              (AuditLog, AuditService, hash chain)
│     └─ admin/              (admin controllers)
│     src/main/resources/db/migration/   (V1__init.sql, V2__..., ...)
│     src/test/java/...
├─ gateway/                  (mock payment gateway service)
├─ mobile/                   (Flutter app)
├─ docker-compose.yml
└─ .github/workflows/ci.yml
```

Layering rule: `controller → service → repository`. Controllers never touch repositories. DTOs at the API boundary, entities never leak out.

---

## 4. Core domain rules (invariants)

These are non-negotiable. Encode them in code, tests, and DB constraints.

1. **Money is `long` paise.** Never `double`/`float`. Wrap in a `Money` value object (`long minorUnits`, `Currency`). `BigDecimal` only for display/parsing at the edges.
2. **Double-entry:** every transaction produces 2+ ledger entries. Sign convention: `CREDIT` increases an account balance, `DEBIT` decreases it. **The sum of signed amounts within a transaction is exactly 0.**
3. **Ledger is append-only.** No UPDATE or DELETE on `ledger_entries` (enforce with a DB trigger that raises an exception). Corrections are new reversing transactions.
4. **`accounts.balance_minor` is a cached value** updated in the same DB transaction as the ledger entries. A job verifies it equals the sum of the entries.
5. **User wallets can never go negative** (`CHECK (balance_minor >= 0)` for `USER_WALLET` and `MERCHANT`). System accounts (`SYSTEM_GATEWAY`) are allowed to be negative.
6. **Global zero-sum:** the sum of all account balances is always 0. This is the master integrity check.
7. **Never call an external system while holding a DB transaction with row locks.**
8. **Every state change of a transaction is validated by the state machine.** Illegal transitions throw.
9. **Every mutating API call requires an `Idempotency-Key`.**

### Account types
| Type | Purpose | May go negative |
|---|---|---|
| `USER_WALLET` | Normal user wallet | No |
| `MERCHANT` | Merchant balance | No |
| `SYSTEM_GATEWAY` | Counterparty for top-ups and refunds (money "outside" the system) | Yes |
| `SYSTEM_FEE` | Platform fee collection | No |

### Worked ledger examples (amounts in paise)
**Top-up of ₹500 (50000)**
| Account | Direction | Amount |
|---|---|---|
| SYSTEM_GATEWAY | DEBIT | 50000 |
| USER_WALLET(Alice) | CREDIT | 50000 |

**Transfer Alice → Bob ₹200**
| Account | Direction | Amount |
|---|---|---|
| Alice | DEBIT | 20000 |
| Bob | CREDIT | 20000 |

**Merchant payment ₹1000 with 2% fee (fee = 2000 paise, rounded half-up, computed in paise)**
| Account | Direction | Amount |
|---|---|---|
| Alice | DEBIT | 100000 |
| Merchant | CREDIT | 98000 |
| SYSTEM_FEE | CREDIT | 2000 |

**Refund** = a new transaction with `reversal_of = original_id` that posts the mirror-image entries. A transaction can only be refunded once (or partially, up to the original amount; track `refunded_minor`).

---

## 5. Database schema (Flyway `V1__init.sql`)

```sql
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         VARCHAR(255) UNIQUE NOT NULL,
  phone         VARCHAR(20)  UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL CHECK (role IN ('USER','MERCHANT','ADMIN')),
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
  token_hash VARCHAR(100) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE accounts (
  id            UUID PRIMARY KEY,
  owner_id      UUID REFERENCES users(id),        -- null for system accounts
  type          VARCHAR(20) NOT NULL,
  currency      CHAR(3)     NOT NULL DEFAULT 'INR',
  balance_minor BIGINT      NOT NULL DEFAULT 0,
  version       BIGINT      NOT NULL DEFAULT 0,   -- optimistic locking
  status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT non_negative_user CHECK (type IN ('SYSTEM_GATEWAY') OR balance_minor >= 0)
);

CREATE TABLE transactions (
  id              UUID PRIMARY KEY,
  type            VARCHAR(20) NOT NULL CHECK (type IN ('TOPUP','TRANSFER','PAYMENT','REFUND')),
  status          VARCHAR(20) NOT NULL,
  amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
  currency        CHAR(3)     NOT NULL DEFAULT 'INR',
  initiator_id    UUID        NOT NULL REFERENCES users(id),
  from_account_id UUID REFERENCES accounts(id),
  to_account_id   UUID REFERENCES accounts(id),
  reversal_of     UUID REFERENCES transactions(id),
  refunded_minor  BIGINT      NOT NULL DEFAULT 0,
  gateway_ref     VARCHAR(100),                   -- gateway payment id (top-ups)
  failure_reason  VARCHAR(255),
  idempotency_key VARCHAR(100) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT      NOT NULL DEFAULT 0,
  UNIQUE (initiator_id, idempotency_key)
);
CREATE INDEX idx_tx_from_created ON transactions (from_account_id, created_at DESC, id DESC);
CREATE INDEX idx_tx_to_created   ON transactions (to_account_id,   created_at DESC, id DESC);
CREATE INDEX idx_tx_status       ON transactions (status, updated_at);

CREATE TABLE ledger_entries (
  id             BIGSERIAL PRIMARY KEY,
  transaction_id UUID   NOT NULL REFERENCES transactions(id),
  account_id     UUID   NOT NULL REFERENCES accounts(id),
  direction      VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_minor   BIGINT NOT NULL CHECK (amount_minor > 0),
  balance_after  BIGINT NOT NULL,                 -- snapshot for easy statements
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_le_account ON ledger_entries (account_id, id DESC);
CREATE INDEX idx_le_tx      ON ledger_entries (transaction_id);

-- append-only enforcement
CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'ledger_entries is append-only'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER ledger_no_update BEFORE UPDATE OR DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();

CREATE TABLE idempotency_keys (
  user_id       UUID        NOT NULL,
  key           VARCHAR(100) NOT NULL,
  request_hash  CHAR(64)    NOT NULL,             -- SHA-256 of method+path+body
  status        VARCHAR(12) NOT NULL CHECK (status IN ('IN_PROGRESS','DONE')),
  response_code INT,
  response_body TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, key)
);

CREATE TABLE payment_requests (                    -- merchant QR
  id UUID PRIMARY KEY, merchant_account_id UUID NOT NULL REFERENCES accounts(id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0), description VARCHAR(255),
  status VARCHAR(12) NOT NULL DEFAULT 'OPEN',      -- OPEN, PAID, EXPIRED
  expires_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
  id           BIGSERIAL PRIMARY KEY,
  aggregate_id UUID NOT NULL,
  event_type   VARCHAR(60) NOT NULL,
  payload      JSONB NOT NULL,
  published    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published = FALSE;

CREATE TABLE webhook_events (                      -- dedupe inbound webhooks
  event_id VARCHAR(100) PRIMARY KEY, received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  payload JSONB NOT NULL, processed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE audit_log (
  id         BIGSERIAL PRIMARY KEY,
  actor_id   UUID, actor_role VARCHAR(20),
  action     VARCHAR(60) NOT NULL,
  entity     VARCHAR(40) NOT NULL, entity_id VARCHAR(64) NOT NULL,
  before_state JSONB, after_state JSONB,
  correlation_id VARCHAR(64),
  prev_hash  CHAR(64) NOT NULL, hash CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_runs (
  id UUID PRIMARY KEY, run_date DATE NOT NULL, started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ, matched INT, mismatched INT, status VARCHAR(12)
);
CREATE TABLE reconciliation_items (
  id BIGSERIAL PRIMARY KEY, run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
  gateway_ref VARCHAR(100), transaction_id UUID,
  kind VARCHAR(30) NOT NULL,   -- MISSING_IN_LEDGER, MISSING_IN_GATEWAY, AMOUNT_MISMATCH, STATUS_MISMATCH
  gateway_amount BIGINT, ledger_amount BIGINT,
  resolution VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN, AUTO_FIXED, MANUAL_REVIEW, RESOLVED
  note TEXT
);
```

Seed migration `V2__seed.sql`: create the `SYSTEM_GATEWAY` and `SYSTEM_FEE` accounts with fixed UUIDs, plus one ADMIN user.

---

## 6. Transaction state machine

```
TOPUP:     PENDING ──► PROCESSING ──► COMPLETED
              │            │
              └──► FAILED ◄┘          COMPLETED ──► REVERSED (refund)
TRANSFER / PAYMENT:  PENDING ──► COMPLETED   |   PENDING ──► FAILED
REFUND:    PENDING ──► COMPLETED | FAILED
```

- `PENDING`: created locally, nothing external happened yet.
- `PROCESSING`: gateway call made, result not known yet (also the state after a timeout).
- Terminal states: `COMPLETED`, `FAILED`, `REVERSED`.
- Implement as an enum with an `allowedTransitions` map and a single `Transaction.transitionTo(newStatus)` method that throws `IllegalStateTransitionException`. Unit test every legal and illegal transition.
- Transfers between two local wallets execute atomically in one DB transaction, so they go directly to `COMPLETED` or `FAILED`. Only top-ups (and gateway-backed refunds) use the multi-step saga.

---

## 7. API specification

Base path `/api/v1`. All bodies JSON. All amounts in **paise (integer)**. Auth via `Authorization: Bearer <jwt>`. All `POST` endpoints that move money require `Idempotency-Key: <uuid>`.

### Standard error format
```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "Wallet balance is too low",
  "correlationId": "c1f0...",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

| HTTP | code | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad input |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | Missing/invalid token |
| 403 | `FORBIDDEN` | Wrong role / not your account |
| 404 | `NOT_FOUND` | Unknown resource |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Same key still running |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Same key, different payload |
| 422 | `INSUFFICIENT_BALANCE` | Not enough funds |
| 422 | `ACCOUNT_INACTIVE`, `SELF_TRANSFER`, `LIMIT_EXCEEDED`, `ALREADY_REFUNDED` | Business rules |
| 429 | `RATE_LIMITED` | Too many requests (`Retry-After` header) |
| 502/504 | `GATEWAY_UNAVAILABLE` / `GATEWAY_TIMEOUT` | Gateway problem (transaction stays PENDING/PROCESSING) |

### Auth
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | `{email, phone, password, role}` → creates user + wallet account |
| POST | `/auth/login` | → `{accessToken, refreshToken, expiresIn}` |
| POST | `/auth/refresh` | Rotate refresh token (old one revoked) |
| POST | `/auth/logout` | Revoke refresh token |

### Wallet
| Method | Path | Notes |
|---|---|---|
| GET | `/wallet` | `{accountId, balanceMinor, currency}` |
| GET | `/wallet/transactions?cursor=&limit=20&type=&status=` | **Cursor pagination** (cursor = `createdAt,id` encoded). Returns `{items, nextCursor}` |
| GET | `/transactions/{id}` | Detail incl. ledger entries |
| GET | `/transactions/by-key/{idempotencyKey}` | Lets the client check the outcome after a timeout |

### Transfers
`POST /transfers`
```json
// request
{ "toUserEmailOrPhone": "bob@x.com", "amountMinor": 20000, "note": "lunch" }
// 201 response
{ "transactionId": "…", "status": "COMPLETED", "balanceAfterMinor": 30000 }
```
Rules: amount > 0, max per transaction (e.g. ₹50,000), daily limit, no self-transfer, both accounts active.

### Top-up
`POST /topups` → `{amountMinor, method: "CARD"|"UPI"}`
Response `202 Accepted`:
```json
{ "transactionId": "…", "status": "PROCESSING", "paymentUrl": "http://gateway/pay/pi_123" }
```
`GET /topups/{transactionId}` → current status (client polls every 2s, max ~60s, then shows "still processing").

### Webhook (called by the gateway)
`POST /webhooks/gateway` (no JWT; secured by HMAC signature header `X-Gateway-Signature: t=<ts>,v1=<hmac>`)
```json
{ "eventId": "evt_1", "type": "payment.succeeded|payment.failed|refund.succeeded",
  "paymentId": "pi_123", "amountMinor": 50000, "reference": "<transactionId>", "createdAt": "…" }
```
Always return `200` quickly for valid, already-seen events (dedupe by `eventId`).

### Merchant
| Method | Path | Notes |
|---|---|---|
| POST | `/merchant/payment-requests` | `{amountMinor, description}` → `{id, qrPayload, expiresAt}` |
| GET | `/merchant/payment-requests/{id}` | Status (OPEN/PAID/EXPIRED) |
| POST | `/payments` | Customer pays: `{paymentRequestId}` → 3 ledger entries (customer, merchant, fee) |
| POST | `/refunds` | `{transactionId, amountMinor}` (merchant or admin) |

### Admin (role ADMIN)
| Method | Path |
|---|---|
| GET | `/admin/transactions?status=&from=&to=` |
| GET | `/admin/reconciliation/runs` and `/runs/{id}/items` |
| POST | `/admin/reconciliation/run` (manual trigger) |
| POST | `/admin/reconciliation/items/{id}/resolve` |
| GET | `/admin/integrity` (runs invariant checks now) |
| GET | `/admin/audit?entity=&entityId=` and `/admin/audit/verify` (verifies hash chain) |

---

## 8. Detailed flows

### 8.1 Transfer (single local DB transaction)

```
POST /transfers  (Idempotency-Key: K)
 1. IdempotencyService.begin(userId, K, requestHash)
      → new key: continue | DONE: return stored response | IN_PROGRESS: 409 | hash differs: 409 KEY_REUSED
 2. @Transactional TransferService.transfer():
      a. resolve recipient account
      b. lock BOTH accounts in ascending UUID order:  SELECT ... WHERE id IN (a,b) ORDER BY id FOR UPDATE
      c. validate (active, balance >= amount, limits)
      d. insert transactions row (status COMPLETED)
      e. insert 2 ledger entries (DEBIT from, CREDIT to) with balance_after
      f. update both balance_minor
      g. write audit_log + outbox_event(TRANSFER_COMPLETED)
      h. mark idempotency key DONE with the response body   ← same DB transaction
 3. commit → return 201
```
Because step h is in the same DB transaction, a crash cannot leave a "moved money but key unfinished" state.

**Locking implementation notes**
- Use a repository method `@Lock(PESSIMISTIC_WRITE) @Query("select a from Account a where a.id in :ids order by a.id")`.
- Always sort IDs before locking. This prevents A→B / B→A deadlocks.
- Set a lock timeout (`javax.persistence.lock.timeout` / `SET LOCAL lock_timeout = '3s'`) and map failure to a retryable 409/503.
- **Also implement an optimistic-locking variant** (`@Version` on Account + `@Retryable`) behind a config flag and **benchmark both** for your design doc (hot-account contention vs low contention).

### 8.2 Idempotency (details)

- Key scope: `(userId, key)`. Keys valid 24h (`expires_at`); a cleanup job removes expired ones.
- `request_hash = SHA-256(method + path + canonical JSON body)`.
- Insert with `INSERT ... ON CONFLICT DO NOTHING`; rows-inserted = 1 means "we own this key".
- For **local transfers**: key completion is inside the business transaction (see 8.1), so `IN_PROGRESS` only exists while that transaction is open (other request either waits on the unique index or gets 409).
- For **gateway-backed flows** (top-up): the key row is committed as `IN_PROGRESS` first; recovery rule: if `IN_PROGRESS` for > 2 minutes, look up the transaction by `(initiator_id, idempotency_key)` and return its current state rather than re-executing.
- Implement as a servlet filter or Spring AOP `@Idempotent` annotation on controller methods, so business code stays clean.
- Only cache 2xx and deterministic 4xx (e.g. INSUFFICIENT_BALANCE may change, so **do not** cache it; validation errors are fine).
- The idempotency key you send to the gateway is the **transaction id**, so retrying gateway calls is also safe.

### 8.3 Top-up saga (orchestrated)

```
Step 1  TX-A (short DB tx):  create Transaction(TOPUP, PENDING) + idempotency; commit
Step 2  (no DB tx open)     :  GatewayClient.createPayment(reference=txId, amount)  [timeout 3s, 2 retries with backoff, circuit breaker]
                               success → TX-B: status=PROCESSING, store gateway_ref
                               failure (definite, e.g. 4xx) → status=FAILED
                               timeout / 5xx (unknown!) → status=PROCESSING, gateway_ref may be null → recovery job will resolve
Step 3  Webhook payment.succeeded arrives (or recovery job polls GET /payments?reference=txId)
        TX-C: dedupe by eventId; lock wallet + SYSTEM_GATEWAY (ordered);
              verify amount matches; post ledger (DEBIT gateway, CREDIT wallet); status=COMPLETED;
              audit + outbox(TOPUP_COMPLETED); commit
Step 4  Webhook payment.failed → status=FAILED (no ledger entries)
```

**The timeout rule:** on an unknown outcome, never assume success or failure. Leave `PROCESSING` and let recovery decide.

**SagaRecoveryJob** (every 30s, ShedLock): find TOPUPs in `PENDING`/`PROCESSING` older than 60s → ask gateway `GET /payments?reference=<txId>` → apply the result through the same `TopUpCompletionService` the webhook uses (single code path, idempotent). After 24h in PROCESSING with no gateway record, mark `FAILED` and raise a reconciliation item.

**Idempotent completion:** `TopUpCompletionService.complete(txId, gatewayAmount)` must be safe to call multiple times: lock the transaction row, if already `COMPLETED` return, else post the ledger.

**Transactional outbox**
- Any event to be published is inserted into `outbox_events` in the same DB transaction as the state change.
- `OutboxPublisher` (`@Scheduled` every 1s, `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100`) publishes to RabbitMQ/Kafka, then sets `published=true`. Delivery is at-least-once, so consumers must be idempotent.
- Consumers (in-process to start): notification stub (log "push notification sent"), analytics counter.

### 8.4 Merchant payment
1. Merchant creates a payment request (QR contains `payflow://pay?requestId=…`).
2. Customer scans → app calls `GET` request details → user confirms → `POST /payments` with `Idempotency-Key`.
3. One DB transaction: lock payment request row + 3 accounts in sorted order; ensure request is `OPEN` and not expired; post 3 ledger entries; mark request `PAID`; audit + outbox. Two customers paying the same request simultaneously: the row lock guarantees only one succeeds, the other gets `409 ALREADY_PAID`.

### 8.5 Refund
- Allowed for `COMPLETED` PAYMENT/TRANSFER by merchant or admin, up to `amount - refunded_minor`.
- Locks original transaction + accounts, posts mirrored entries (fee refunded proportionally), increments `refunded_minor`, sets original to `REVERSED` if fully refunded.
- Merchant balance must cover the refund, otherwise `INSUFFICIENT_BALANCE`.
- Top-up refunds to the card go through the gateway (`POST /refunds`), also saga-style. Treat as a stretch goal.

---

## 9. Mock payment gateway (`/gateway`)

A deliberately hostile external service.

### Endpoints
| Method | Path | Behavior |
|---|---|---|
| POST | `/v1/payments` | Body `{reference, amountMinor, method, callbackUrl}` + `Idempotency-Key` header. Returns `{paymentId, status: "PENDING", payUrl}` |
| GET | `/v1/payments/{id}` and `/v1/payments?reference=` | Current status |
| GET | `/pay/{paymentId}` | Simple HTML page with "Pay" / "Fail" buttons (simulates the user completing payment). Also an auto-complete mode |
| POST | `/v1/refunds` | Refund a payment |
| GET | `/v1/settlements/{yyyy-MM-dd}.csv` | Daily settlement file: `payment_id,reference,amount_minor,status,captured_at` |
| POST | `/admin/chaos` | Change chaos config at runtime |

### Chaos configuration (runtime adjustable)
```json
{
  "latencyMs": {"min": 50, "max": 2000},
  "timeoutRate": 0.10,          // request processed, but response never sent (the nasty case)
  "errorRate": 0.05,            // 500 before processing
  "duplicateWebhookRate": 0.20, // same event sent 2-3 times
  "delayedWebhookMs": {"min": 0, "max": 30000},
  "outOfOrderWebhooks": true,
  "dropWebhookRate": 0.05,      // webhook never sent (recovery job must catch it)
  "settlementDriftRate": 0.01   // settlement file contains a mismatch on purpose
}
```
The `timeoutRate` behavior is the key one: the gateway **does charge the payment** but never responds, so your system must recover.

### Webhook signing
`X-Gateway-Signature: t=<unix_ts>,v1=HMAC_SHA256(secret, t + "." + rawBody)`.
Backend verifies: constant-time compare, timestamp within 5 minutes (replay protection), then dedupe on `eventId`. Shared secret comes from env var.

---

## 10. Reconciliation

Daily `ReconciliationJob` (also triggerable via admin endpoint):
1. Download `settlements/{date}.csv` from the gateway.
2. Load internal TOPUP transactions for the same date window.
3. Match by `gateway_ref` / `reference`. Classify:

| Kind | Meaning | Auto-fix? |
|---|---|---|
| `MISSING_IN_LEDGER` | Gateway captured, we never credited | **Yes**: run `TopUpCompletionService.complete` (idempotent) |
| `MISSING_IN_GATEWAY` | We credited, gateway has no capture | No: `MANUAL_REVIEW` (possible fraud/bug) |
| `AMOUNT_MISMATCH` | Amounts differ | No: `MANUAL_REVIEW` |
| `STATUS_MISMATCH` | We FAILED, gateway CAPTURED (or reverse) | Fix the safe direction, flag the rest |

4. Store a `reconciliation_run` + `reconciliation_items`, expose to admin UI.
5. **Internal integrity checks** (`LedgerInvariantChecker`, also nightly):
   - For every transaction, signed sum of entries = 0.
   - For every account, `balance_minor == SUM(credits) - SUM(debits)`.
   - Sum of all account balances = 0.
   - No wallet negative.
   - Any violation → CRITICAL log + metric `ledger_integrity_violations_total` + admin alert.

---

## 11. Audit log with hash chain

- `AuditService.record(actor, action, entity, entityId, before, after)` is called inside the business transaction.
- `hash = SHA-256(prev_hash + actor + action + entity + entityId + before + after + createdAt)`; `prev_hash` is the previous row's hash (the first row uses a fixed genesis hash).
- To avoid two concurrent writers reading the same `prev_hash`, serialize appends: `SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1 FOR UPDATE` or use a single-row `audit_chain_head` table locked per write. (Document this as a throughput tradeoff in DESIGN.md.)
- `GET /admin/audit/verify` recomputes the chain and reports the first broken row.
- Log: registration, login failures, transfers, top-up state changes, refunds, admin actions, reconciliation resolutions. Never log passwords, tokens, or full card numbers.

---

## 12. Security

- BCrypt password hashing; refresh tokens stored hashed and rotated; JWT signed with a secret from env (HS256) or RSA keys.
- Role checks with `@PreAuthorize`; ownership checks in services (a user can only read their own wallet/transactions).
- Rate limiting on `/auth/login` (per IP + email) and money endpoints (per user) using Redis token buckets.
- Input validation with Bean Validation; reject amounts ≤ 0 or above limits.
- Webhook HMAC verification (section 9).
- No secrets in the repo; use `.env` + Docker secrets; commit `.env.example`.
- CORS restricted; HTTPS in deployment; security headers.
- Response bodies never include stack traces (global `@RestControllerAdvice`).

---

## 13. Flutter app specification

### Screens
1. **Auth:** register, login, biometric unlock (optional stretch).
2. **Home/Wallet:** balance card, quick actions (Send, Top up, Scan), recent transactions.
3. **Send money:** recipient input, amount, note, confirm sheet, result screen.
4. **Top-up:** amount, method, opens gateway pay page (WebView or external browser), then a "Processing" screen polling status.
5. **Transaction history:** infinite scroll (cursor), filters (type/status), detail page showing ledger entries.
6. **Merchant:** create payment request, show QR, live status of the request.
7. **Scan & pay:** camera scanner → confirm → pay.
8. **Admin (role-gated):** failed/pending transactions, reconciliation runs and items, integrity check button, audit verify.
9. **Settings:** profile, logout.

### Client-side correctness details (these are what to show off)
- **Idempotency key per attempt:** generate a UUID when the user taps "Confirm". A Dio interceptor attaches it. Retries of the *same attempt* reuse it; a new attempt generates a new one. Persist in-flight attempt keys (Drift/secure storage) so an app kill mid-request can resume safely.
- **Timeout handling:** on `DioException` timeout for a money call, do not show "Failed". Show "Checking status…", then call `GET /transactions/by-key/{key}` until resolved (max ~30s), then show a final or "still processing" state.
- **Token refresh interceptor:** on 401 `TOKEN_EXPIRED`, refresh once (single-flight, queue other requests), replay the original request.
- **Pending UI state** for top-ups with polling and cancel-safe navigation.
- **Error mapper:** backend `code` → friendly text and action (e.g. `INSUFFICIENT_BALANCE` → "Top up" button).
- **Offline:** cached balance and history viewable offline (Drift); money actions disabled with a clear banner when offline.
- **Money formatting:** always format from paise with `intl`; never parse user input into double (parse string → paise using integer math).

### Architecture
```
lib/
 ├─ core/ (dio_client.dart, interceptors/{auth,idempotency,logging}.dart, errors/, money/, theme/, router/)
 ├─ features/{auth,wallet,transfer,topup,merchant,history,admin}/
 │    ├─ data/ (api, dto, repository_impl, local (drift))
 │    ├─ domain/ (entities, repository interface, use cases)
 │    └─ presentation/ (screens, widgets, providers/notifiers)
 └─ main.dart
```
Riverpod `AsyncNotifier`s for each flow, `freezed` sealed states (`idle / submitting / checkingStatus / success / failure(code)`).

---

## 14. Testing strategy

**Backend (Testcontainers with real Postgres, not H2)**
| Test | Assertion |
|---|---|
| Concurrent transfers | 100 threads × random transfers among 10 accounts. After all finish: total money unchanged, no negative balance, each balance == sum of entries |
| Deadlock test | 2 threads doing A→B and B→A 1000 times, no deadlock exceptions |
| Idempotency | Same request 50× in parallel → exactly one transaction, 49 identical replayed responses |
| Key reuse | Same key + different body → `409 IDEMPOTENCY_KEY_REUSED` |
| Double payment of QR | 2 customers pay the same request concurrently → one succeeds |
| Saga recovery | Kill/interrupt after gateway call, before completion → recovery job completes it exactly once |
| Duplicate webhook | Same event 5× (and in parallel) → wallet credited once |
| Bad signature / old timestamp | 401, no state change |
| Out-of-order webhooks | `succeeded` before `PROCESSING` recorded → handled |
| Reconciliation | Seed known mismatches → correct classification and auto-fix |
| Integrity checker | Manually corrupt a balance in test → checker detects |
| Audit chain | Tamper with a row → verify endpoint reports it |
| State machine | All legal/illegal transitions |
| Ledger immutability | UPDATE/DELETE on ledger_entries throws |

**Flutter:** unit tests for money parsing/formatting and interceptors, widget tests for send-money success/timeout/insufficient balance, `integration_test` for the happy path against a local backend.

**Load test:** k6 or Gatling script: N virtual users doing transfers. Record RPS, p95 latency, and the bottleneck you found. Put real numbers in the README.

---

## 15. DevOps

- `docker-compose.yml`: postgres, redis, rabbitmq, backend, gateway (healthchecks + depends_on).
- Multi-stage `Dockerfile` for backend and gateway.
- GitHub Actions: build → unit tests → integration tests (Testcontainers) → Flutter analyze/test → Docker build.
- Profiles: `local`, `test`, `prod`. Config via env vars.
- Actuator health + Prometheus metrics. Custom metrics: `transfers_total`, `transfer_duration`, `topups_pending`, `idempotency_replays_total`, `reconciliation_mismatches_total`, `ledger_integrity_violations_total`.

---

## 16. Phased build plan (with Cursor prompts)

Build one phase at a time. Finish it and its tests before starting the next. Commit after each phase.

### Phase 0: Setup (½ day)
Create the monorepo, Spring Boot project, Docker Compose (postgres, redis), Flyway, Testcontainers base test class, global exception handler, correlation-id filter.
> **Cursor prompt:** "Using @docs/SPEC.md sections 2, 3 and 15, scaffold the backend Spring Boot 3 / Java 21 project with the package layout from section 3, Flyway, Testcontainers PostgreSQL base test class, docker-compose for postgres+redis, a global `@RestControllerAdvice` using the error format in section 7, and a correlation-id filter. No business logic yet."

### Phase 1: Accounts, ledger, transfers (week 1)
Auth (register/login/JWT), account creation, `Money`, Flyway V1/V2, `LedgerService.post(...)`, `TransferService` in one DB transaction, wallet + history endpoints.
> **Cursor prompt:** "Implement section 5 migrations exactly, then auth, accounts, `Money`, `LedgerService` (enforces signed-sum-zero and balance_after) and `TransferService` per section 8.1 without idempotency yet. Write unit tests and one Testcontainers integration test for a basic transfer. Explain any deviation."

### Phase 2: Concurrency (week 2)
Ordered pessimistic locking, optimistic variant behind a flag, lock timeouts, the 100-thread test and deadlock test.
> **Cursor prompt:** "Add ordered `FOR UPDATE` locking per section 8.1 and the concurrency tests from section 14 (concurrent transfers and deadlock). Then add the optimistic-locking variant behind `payflow.locking.strategy` and a JMH or simple timing comparison."

### Phase 3: Idempotency + pagination (week 3)
`IdempotencyService`, `@Idempotent` filter/aspect, by-key lookup endpoint, cursor pagination, rate limiting.
> **Cursor prompt:** "Implement section 8.2 idempotency exactly, including request hashing, IN_PROGRESS handling and completion inside the transfer transaction. Add the parallel 50-request test and key-reuse test. Then implement cursor pagination for wallet transactions."

### Phase 4: Mock gateway + top-up (week 4)
Gateway service with chaos config, `GatewayClient` (timeouts, retry, circuit breaker via Resilience4j), top-up flow, state machine.
> **Cursor prompt:** "Build the gateway service per section 9 (without settlement file yet), then the backend `GatewayClient` and top-up flow through Step 3 of section 8.3, using the state machine in section 6. Webhook endpoint with HMAC verification and dedupe."

### Phase 5: Saga recovery + outbox (week 5)
`SagaRecoveryJob`, idempotent `TopUpCompletionService`, outbox publisher and broker, tests for crash recovery and duplicate/out-of-order webhooks.

### Phase 6: Merchant payments, refunds (week 5-6)
Payment requests, QR payload, 3-leg payment, refund logic.

### Phase 7: Reconciliation, integrity, audit (week 6)
Settlement file in gateway, `ReconciliationJob`, `LedgerInvariantChecker`, hash-chained audit log, admin endpoints.

### Phase 8: Flutter app (weeks 4-7, in parallel after phase 3)
Start the Flutter app after Phase 3 (auth, wallet, send money, history), then add top-up/merchant/admin screens as backend phases finish.

### Phase 9: Polish (week 8)
Load test, README with architecture diagram, `DESIGN.md`, CI, deployment, demo video/GIF, chaos demo script (turn on high chaos, run traffic, show zero drift).

---

## 17. `.cursorrules` (copy into repo root)

```
You are helping build PayFlow, a payments/wallet simulation. Follow docs/SPEC.md strictly.

Rules:
- Java 21, Spring Boot 3, Maven. Layering: controller -> service -> repository. DTOs at API boundary; never expose entities.
- Money is long paise via the Money value object. Never use double/float for money.
- Schema changes only via new Flyway migrations. Hibernate ddl-auto=validate.
- ledger_entries is append-only. Every transaction's signed entries sum to zero.
- When locking multiple accounts, lock in ascending UUID order.
- Never call external services (gateway) inside a DB transaction holding locks.
- All money-moving endpoints require Idempotency-Key.
- Write tests with every feature. Integration tests use Testcontainers PostgreSQL (not H2).
- Prefer small, focused classes. Constructor injection. No field @Autowired.
- Do not add dependencies or change the architecture without saying why.
- When unsure about a requirement, quote the relevant SPEC section and ask before guessing.
- After each change, list what tests cover it and what remains untested.
```

---

## 18. Cursor workflow tips

1. Keep this file in `docs/SPEC.md` and always attach it (`@docs/SPEC.md`) plus only the files relevant to the current step.
2. Work **one phase at a time** in a fresh chat. Long chats drift.
3. Ask Cursor to write the **failing test first** for concurrency and idempotency, then the implementation.
4. Review every generated locking/transaction/idempotency line yourself. These are exactly what interviewers ask about, and AI tools often get them subtly wrong (e.g. `@Transactional` self-invocation, locking order, calling the gateway inside a transaction).
5. Keep `docs/DESIGN.md` as you go: for each hard problem write "problem → options → choice → tradeoff → what breaks at scale".
6. Commit small, with meaningful messages, since your GitHub history is part of the portfolio.

---

## 19. Definition of done

- [ ] All invariants in section 4 hold under the chaos test (gateway chaos at high settings, 1000+ mixed operations, integrity checker reports zero violations).
- [ ] Every test in section 14 exists and passes in CI.
- [ ] Swagger docs, Docker Compose one-command startup, live deployed demo.
- [ ] README: architecture diagram, ledger explanation, design tradeoffs, benchmark numbers, "what I would do at 10k TPS" (shard by account, hot account problem, read replicas, partitioned ledger).
- [ ] Flutter app demonstrates: idempotent retries, timeout → status check, pending top-up, pagination, admin reconciliation screen.

## 20. Interview cheat-sheet (you should be able to answer)

- How do you prevent double spending? → row locks in consistent order + CHECK constraint + idempotency.
- Server crashes after the gateway charged but before crediting? → PROCESSING state + webhook + recovery poll + reconciliation.
- Why ledger instead of just updating balance? → auditability, reconstructability, invariants.
- Pessimistic vs optimistic locking here? → hot accounts favor pessimistic; benchmark numbers from your test.
- How do you make webhooks safe? → HMAC, timestamp window, event-id dedupe, idempotent handler.
- Why the outbox? → atomically pair DB change with event publish; avoids dual-write inconsistency.
- What breaks at scale? → single Postgres write path, hot accounts, audit-chain serialization, outbox polling; mitigations: sharding, batching, partitioned tables, CDC (Debezium).

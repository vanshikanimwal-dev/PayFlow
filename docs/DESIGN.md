# Design notes

## Correlation id and errors

Every request gets an `X-Correlation-Id`. A client value is kept when it is 1–64 characters of letters, digits, `.`, `_`, or `-`. Anything else is replaced with a UUID. The filter writes the id into SLF4J MDC as `correlationId`. SPEC section 7 is the only error shape. 5xx responses use a fixed message; the stack stays in the log. `LOCK_TIMEOUT` (503) covers pessimistic lock timeouts and optimistic lock failures. `RateLimitedException` sets `Retry-After`.

## Money

**Problem:** floating point cannot represent paise.

**Choice:** `Money` is a `long` of minor units plus `Currency.INR`. Fees use integer half-up: `(amount * percent + 50) / 100`, so 2% of 100000 paise is 2000. No `double` or `float` on a money path.

## Schema

Flyway owns the schema. `ddl-auto=validate`.

- `V1__init.sql` is SPEC section 5, including the append-only trigger on `ledger_entries`.
- `V2__seed.sql` inserts the admin user and the `SYSTEM_GATEWAY` / `SYSTEM_FEE` accounts.
- `V3__ops.sql` adds ShedLock, the audit chain head, and a unique partial index so a user has one account.

CHAR columns (`currency`, hashes) are mapped with `SqlTypes.CHAR` so validation matches `bpchar`. JSON payloads are `jsonb`.

**Deviation:** the transfer note is audited and is not a column. V1 has no note column, and a later migration was not added because the note is not part of the ledger.

## Transfers and locking

**Problem:** two transfers can deadlock if they lock accounts in opposite orders, and a balance update without a ledger row cannot be reconstructed.

**Choice:** lock every involved account with `SELECT … WHERE id IN (…) ORDER BY id FOR UPDATE` (or the optimistic `@Version` path). Post at least two legs whose signed amounts sum to zero, then insert the transaction as `COMPLETED`. Local transfers are created in memory as `PENDING` and transition to `COMPLETED` before insert, because `PENDING → COMPLETED` is legal for `TRANSFER` and the row should not sit in `PENDING`.

`payflow.locking.strategy=pessimistic` (default) sets `lock_timeout`. A lock timeout or deadlock rolls the transaction back, and `LockRetry` runs the whole transfer again from outside that transaction, up to 4 attempts. Only the last failure becomes 503. `optimistic` loads the same rows in id order and retries `OptimisticLockingFailureException` from outside the `@Transactional` method, up to 8 attempts. Retrying inside the transaction would not see a fresh version.

## Idempotency

**Problem:** a client retry must not move money twice, and a failed attempt must not poison the key.

**Choice:** scope is `(userId, key)`. The request hash is SHA-256 of method, path, and canonical JSON. Insert uses `ON CONFLICT DO NOTHING`. Completion updates the same row inside the business transaction, so a rollback drops both the ledger post and the cached response. `IN_PROGRESS` younger than 2 minutes returns 409. Older in-progress keys return `Recover` so a crashed top-up can be resumed. Replays increment `idempotency_replays_total`. TTL is 24 hours.

A parallel duplicate blocks on the unique key until the first transaction commits, then reads `DONE` and returns the stored body. That is how 50 parallel calls become one transfer and 49 identical responses.

## Top-up saga

**Problem:** the gateway must not be called while account locks are held, and a crash after capture must still credit the wallet once.

**Choice:** `TopUpService` is not `@Transactional`. `TopUpStore` commits `PENDING` plus the idempotency key, the HTTP call runs, then a short transaction marks `PROCESSING` or `FAILED`. `GatewayUnknownException` leaves the transaction `PROCESSING` and the key `IN_PROGRESS` (504). A definite rejection marks `FAILED` and completes the key.

`TopUpCompletionService.complete` locks the wallet and `SYSTEM_GATEWAY` in id order, then the transaction row, and posts DEBIT gateway / CREDIT wallet. `PENDING` goes to `PROCESSING` and then `COMPLETED`, so a webhook that arrives before the PROCESSING update still credits once. `FAILED → COMPLETED` is allowed only for `TOPUP` (SPEC section 10). Amount mismatches are flagged and not credited. `payment.failed` does not un-credit a `COMPLETED` top-up.

`SagaRecoveryJob` polls top-ups stuck in `PENDING` or `PROCESSING` for 60 seconds. Captured or succeeded gateway payments complete; failed payments fail; an empty lookup after 24 hours fails and flags `STATUS_MISMATCH`. Jobs are off in the test profile and the test calls the job directly.

Webhook HMAC is `t=<unix>,v1=HMAC_SHA256(secret, t + "." + rawBody)`, compared in constant time, with a 5-minute window. The controller reads the raw body. Dedupe is `INSERT … ON CONFLICT DO NOTHING` on `event_id`.

## Merchant payments and refunds

A payment request lasts 15 minutes. `POST /payments` locks the request row, then the customer, merchant, and fee accounts in id order, then claims it with `UPDATE … WHERE status = 'OPEN'`. One row changed means this payer owns it. Zero rows means 409 `ALREADY_PAID`. The fee is 2% half-up. A zero fee (tiny amounts) posts two legs.

Refunds apply to `COMPLETED` `PAYMENT` or `TRANSFER` only. `TOPUP` card refunds from SPEC 8.5 are not implemented. The caller must be `ADMIN` or the owner of the destination account. The fee is proportional, and the final refund takes the remainder so fee paise do not stick. A full refund sets the original transaction to `REVERSED`.

Public registration rejects `ADMIN`.

## Reconciliation, integrity, audit

The settlement CSV is fetched outside the database transaction, then `apply` classifies `MISSING_IN_LEDGER` (auto-fix via `complete` when the amount matches and the top-up is not already `COMPLETED`), `MISSING_IN_GATEWAY`, `AMOUNT_MISMATCH`, and `STATUS_MISMATCH`. The local status is snapshotted before `complete`, so a `FAILED` row is not reported as `PROCESSING` after recovery.

The integrity checker compares signed entry sums, account balances, the global sum (zero), and negative balances on non-gateway accounts. Violations increment `ledger_integrity_violations_total`.

Audit rows hash `prev | actor | role | action | entity | entityId | before | after | createdAt`, with canonical JSON and `createdAt` as epoch milliseconds. Each append takes `pg_advisory_xact_lock` and then locks `audit_chain_head`, so two writers cannot share a `prev_hash`. `V4` adds nullable `transactions.payment_request_id` with a unique index so a payment request can be claimed only once.

## Outbox

**Choice:** default broker is in-process (`NotificationConsumer` logs, `AnalyticsConsumer` counts). `payflow.outbox.broker=rabbit` publishes the payload to `payflow.events` and a listener dispatches the same consumers. It does not also call them in the publisher, so events are not handled twice. The publisher claims unpublished rows with `FOR UPDATE SKIP LOCKED`.

## Dependencies added after phase 0

| Dependency | Why |
|---|---|
| spring-boot-starter-security, jjwt 0.12.6 | JWT access (15m) and refresh (7d) |
| spring-boot-starter-data-redis | Login and money token buckets. Login is keyed by remote address plus email, not `X-Forwarded-For` |
| spring-boot-starter-amqp | Optional outbox broker |
| spring-boot-starter-aop, spring-retry | Optimistic lock retry outside the transaction |
| springdoc 2.8.13 | Swagger |
| resilience4j 2.3.0 | Gateway retry and circuit breaker. Timeouts and 5xx are unknown; 4xx is a definite rejection and is not retried |
| shedlock 6.9.2 | One instance runs each scheduled job |

MapStruct was not added; API records are mapped by hand. Bucket4j was not added; the rate limiter is a Redis Lua token bucket. Lombok was not added.

## Flutter client

Flow states are Dart 3 sealed classes (`idle`, `submitting`, `checkingStatus`, `success`, `failure`) instead of freezed. The states match SPEC section 13, and the app does not need a second code generator for them. Drift still generates the local schema. The browser build keeps the last wallet in memory because the native sqlite library is not part of that build; Windows uses the Drift file. A confirm tap stores the idempotency key before the call, and a timeout polls `GET /transactions/by-key/{key}` instead of showing a hard failure. `401 TOKEN_EXPIRED` refreshes once and replays the original request.

Gateway chaos rates start at 0. Hostile behavior is `POST /admin/chaos` on the gateway, not the boot default. The gateway stores its own payments in H2 and is not part of the ledger.

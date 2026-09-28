# Design notes

## Phase 0 — Setup

### Correlation id
Every request gets an `X-Correlation-Id`. A client value is kept when it is 1–64 characters of letters, digits, `.`, `_`, or `-`. Anything else is replaced with a UUID so log injection and oversized headers cannot land in the audit trail later. The filter writes the id into SLF4J MDC as `correlationId` before the controller runs, and the error body reads it back from MDC. Spring Boot structured console logging (ECS JSON) includes MDC fields, so no extra log encoder dependency is required.

### Error body
SPEC section 7 is the only error shape. `GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler` so framework errors (404, bad JSON, validation) use that shape too. 5xx responses use a fixed message. The exception and stack stay in the log.

### Schema
Flyway is on and `ddl-auto=validate`. There is no `V1__init.sql` yet. Section 5's schema is phase 1, and using `V1` now would block that migration version.

### What is intentionally absent
- Spring Security and JWT (phase 1). Adding the starter now would lock every endpoint, including actuator, before there is an auth model.
- Redis client (phase 3 rate limits). Redis is in Compose so the process is available.
- RabbitMQ (phase 5 outbox).
- `Money` (phase 1). No money moves in this phase.

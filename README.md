# PayFlow

Wallet and payments **simulation**. It never handles real card data or real money.

The build follows [docs/SPEC.md](docs/SPEC.md) one phase at a time. This tree is **phase 0**: backend scaffold, Flyway, Postgres via Testcontainers, the section 7 error body, and a correlation-id filter. No money movement yet.

## Run locally

Docker is required for Postgres, Redis, and the integration test.

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd`. The API listens on port 8080. Profiles are `local` (default), `test`, and `prod`.

```bash
./mvnw test      # error-contract tests, no Docker
./mvnw verify    # also starts PostgreSQL 16 in Testcontainers
```

Actuator: `/actuator/health` and `/actuator/prometheus`.

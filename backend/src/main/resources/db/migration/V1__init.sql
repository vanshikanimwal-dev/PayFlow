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
  owner_id      UUID REFERENCES users(id),
  type          VARCHAR(20) NOT NULL,
  currency      CHAR(3)     NOT NULL DEFAULT 'INR',
  balance_minor BIGINT      NOT NULL DEFAULT 0,
  version       BIGINT      NOT NULL DEFAULT 0,
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
  gateway_ref     VARCHAR(100),
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
  balance_after  BIGINT NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_le_account ON ledger_entries (account_id, id DESC);
CREATE INDEX idx_le_tx      ON ledger_entries (transaction_id);

CREATE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'ledger_entries is append-only'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER ledger_no_update BEFORE UPDATE OR DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();

CREATE TABLE idempotency_keys (
  user_id       UUID        NOT NULL,
  key           VARCHAR(100) NOT NULL,
  request_hash  CHAR(64)    NOT NULL,
  status        VARCHAR(12) NOT NULL CHECK (status IN ('IN_PROGRESS','DONE')),
  response_code INT,
  response_body TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, key)
);

CREATE TABLE payment_requests (
  id UUID PRIMARY KEY, merchant_account_id UUID NOT NULL REFERENCES accounts(id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0), description VARCHAR(255),
  status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
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

CREATE TABLE webhook_events (
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
  kind VARCHAR(30) NOT NULL,
  gateway_amount BIGINT, ledger_amount BIGINT,
  resolution VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  note TEXT
);

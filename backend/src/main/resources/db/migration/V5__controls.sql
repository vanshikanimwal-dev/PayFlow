ALTER TABLE users ADD COLUMN display_name VARCHAR(80);
ALTER TABLE users ADD COLUMN pin_hash VARCHAR(100);
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(64);
ALTER TABLE users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN wallet_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN failed_logins INT NOT NULL DEFAULT 0;

ALTER TABLE refresh_tokens ADD COLUMN device_label VARCHAR(80);
ALTER TABLE refresh_tokens ADD COLUMN created_at TIMESTAMPTZ;

DROP INDEX uq_accounts_owner;
CREATE UNIQUE INDEX uq_accounts_owner_type ON accounts (owner_id, type) WHERE owner_id IS NOT NULL;

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  title VARCHAR(160) NOT NULL,
  body VARCHAR(500) NOT NULL,
  seen BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

CREATE TABLE scheduled_transfers (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  to_email VARCHAR(255) NOT NULL,
  amount_minor BIGINT NOT NULL,
  note VARCHAR(255),
  day_of_month INT NOT NULL CHECK (day_of_month BETWEEN 1 AND 28),
  next_run_on DATE NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE money_requests (
  id UUID PRIMARY KEY,
  requester_id UUID NOT NULL REFERENCES users(id),
  payer_email VARCHAR(255) NOT NULL,
  amount_minor BIGINT NOT NULL,
  note VARCHAR(255),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fraud_flags (
  id UUID PRIMARY KEY,
  user_id UUID,
  kind VARCHAR(40) NOT NULL,
  detail VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  open BOOLEAN NOT NULL DEFAULT TRUE
);

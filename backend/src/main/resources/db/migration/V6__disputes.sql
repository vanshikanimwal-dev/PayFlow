CREATE TABLE disputes (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  transaction_id UUID NOT NULL REFERENCES transactions(id),
  note VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_disputes_open ON disputes (user_id, transaction_id) WHERE status = 'OPEN';
CREATE INDEX idx_disputes_status ON disputes (status, created_at DESC);

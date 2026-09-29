-- ShedLock so scheduled jobs do not run twice when more than one instance is up.
CREATE TABLE shedlock (
  name VARCHAR(64) NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP NOT NULL,
  locked_at TIMESTAMP NOT NULL,
  locked_by VARCHAR(255) NOT NULL
);

-- Single row locked on every audit append so two writers cannot share a prev_hash.
CREATE TABLE audit_chain_head (
  id SMALLINT PRIMARY KEY,
  hash CHAR(64) NOT NULL,
  CONSTRAINT audit_chain_head_singleton CHECK (id = 1)
);

INSERT INTO audit_chain_head (id, hash)
VALUES (1, '0000000000000000000000000000000000000000000000000000000000000000');

CREATE UNIQUE INDEX uq_accounts_owner ON accounts (owner_id) WHERE owner_id IS NOT NULL;

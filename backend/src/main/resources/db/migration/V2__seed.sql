-- Local simulation admin. Password is admin-dev-change-me (bcrypt). Not a production secret.
INSERT INTO users (id, email, phone, password_hash, role, status)
VALUES (
  '00000000-0000-0000-0000-000000000010',
  'admin@payflow.local',
  NULL,
  '$2b$10$aOHGBYpfzWYwkWnoz.mkJOCml0XtmzvqIa52f.HijqvCI5R9YZ9k.',
  'ADMIN',
  'ACTIVE'
);

INSERT INTO accounts (id, owner_id, type, currency, balance_minor, version, status)
VALUES
  ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM_GATEWAY', 'INR', 0, 0, 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000002', NULL, 'SYSTEM_FEE', 'INR', 0, 0, 'ACTIVE');

CREATE TABLE IF NOT EXISTS nexora_accounts (
  id UUID PRIMARY KEY,
  username VARCHAR(20) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT nexora_accounts_username_format
    CHECK (username ~ '^[a-z0-9_]{3,20}$')
);
CREATE INDEX IF NOT EXISTS nexora_accounts_created_at_idx
  ON nexora_accounts (created_at DESC);

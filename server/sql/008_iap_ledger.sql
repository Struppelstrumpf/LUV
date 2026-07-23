-- Append-only style IAP records (also mirrored to /data/iap_ledger/*.jsonl).
CREATE TABLE IF NOT EXISTS iap_payments (
  id TEXT PRIMARY KEY,
  provider TEXT NOT NULL,
  user_id TEXT NOT NULL,
  pack_id TEXT,
  coins INTEGER NOT NULL DEFAULT 0,
  order_id TEXT,
  purchase_token TEXT,
  credited BOOLEAN NOT NULL DEFAULT TRUE,
  raw JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  appended_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS iap_payments_user_idx ON iap_payments (user_id);
CREATE INDEX IF NOT EXISTS iap_payments_token_idx ON iap_payments (purchase_token);

INSERT INTO meta(key, value) VALUES ('schema_version', '8')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

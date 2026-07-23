-- Phase 9: shop catalog / stats / rotation / change queue
-- Env: SHOP_BACKEND=table|blob

CREATE TABLE IF NOT EXISTS shop_domain (
  key TEXT PRIMARY KEY,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '12')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

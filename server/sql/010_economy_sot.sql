-- Phase 7: payments, ledger, vouchers, redeems, introOffers, economySettings
-- Env: ECONOMY_BACKEND=table|blob

CREATE TABLE IF NOT EXISTS economy_domain (
  key TEXT PRIMARY KEY,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '10')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

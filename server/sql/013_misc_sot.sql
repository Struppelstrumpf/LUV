-- Phase 10: layouts, reports, maintenance, item meta, achievements
-- Env: MISC_BACKEND=table|blob

CREATE TABLE IF NOT EXISTS misc_domain (
  key TEXT PRIMARY KEY,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '13')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

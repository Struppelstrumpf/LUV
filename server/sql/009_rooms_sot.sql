-- Phase 6: rooms + canvas memories + public canvases
-- Env: ROOMS_BACKEND=table|blob

CREATE TABLE IF NOT EXISTS rooms_domain (
  key TEXT PRIMARY KEY,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '9')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

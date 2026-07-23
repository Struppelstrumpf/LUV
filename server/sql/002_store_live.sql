-- Live primary document (one row). JSON file becomes hot mirror when STORE_BACKEND=postgres.
CREATE TABLE IF NOT EXISTS store_live (
  id SMALLINT PRIMARY KEY CHECK (id = 1),
  payload JSONB NOT NULL,
  bytes INTEGER NOT NULL DEFAULT 0,
  content_hash TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '2')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

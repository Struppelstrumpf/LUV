-- Phase 3: market listings + meta as source of truth.
-- Controlled by env MARKET_BACKEND=table|blob

CREATE TABLE IF NOT EXISTS market_listings (
  id TEXT PRIMARY KEY,
  seller_id TEXT,
  status TEXT,
  kind TEXT,
  item_id TEXT,
  price_coins INTEGER NOT NULL DEFAULT 0,
  raw JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS market_listings_seller_idx ON market_listings (seller_id);
CREATE INDEX IF NOT EXISTS market_listings_status_idx ON market_listings (status);
CREATE INDEX IF NOT EXISTS market_listings_kind_item_idx ON market_listings (kind, item_id);

CREATE TABLE IF NOT EXISTS market_meta (
  id SMALLINT PRIMARY KEY CHECK (id = 1),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '5')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

-- Phase 5: events / notices / feed / notify phrases as source of truth.
-- Controlled by env EVENTS_BACKEND=table|blob
-- Keys hydrate into getDb(): eventsConfig, eventContest, eventLobbies,
-- liveNotice, homeFeed, notifyPhrases

CREATE TABLE IF NOT EXISTS events_domain (
  key TEXT PRIMARY KEY,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO meta(key, value) VALUES ('schema_version', '7')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

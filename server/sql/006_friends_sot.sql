-- Phase 4: friendships + requests as source of truth (hydrate into user.friends).
-- Controlled by env FRIENDS_BACKEND=table|blob

CREATE TABLE IF NOT EXISTS friendships (
  user_a TEXT NOT NULL,
  user_b TEXT NOT NULL,
  level INTEGER NOT NULL DEFAULT 0,
  level_day TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_a, user_b),
  CHECK (user_a < user_b)
);

CREATE TABLE IF NOT EXISTS friend_requests (
  from_user_id TEXT NOT NULL,
  to_user_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (from_user_id, to_user_id),
  CHECK (from_user_id <> to_user_id)
);

CREATE INDEX IF NOT EXISTS friend_requests_to_idx ON friend_requests (to_user_id);

CREATE TABLE IF NOT EXISTS friend_list_meta (
  user_id TEXT NOT NULL,
  friend_id TEXT NOT NULL,
  sort_index INTEGER NOT NULL DEFAULT 0,
  level_reward_claimed INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, friend_id)
);

CREATE TABLE IF NOT EXISTS friend_pet_kraul (
  user_id TEXT NOT NULL,
  day_key TEXT NOT NULL,
  target_id TEXT NOT NULL,
  PRIMARY KEY (user_id, day_key, target_id)
);

INSERT INTO meta(key, value) VALUES ('schema_version', '6')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW();

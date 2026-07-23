/**
 * Phase 4: friendships/requests as SoT; hydrate into user.friends for getDb().
 * Env: FRIENDS_BACKEND=table|blob
 */
const crypto = require("crypto");
const path = require("path");
const { execFileSync } = require("child_process");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const FRIENDS_BACKEND = String(process.env.FRIENDS_BACKEND || "blob").toLowerCase();
const USE_FRIENDS_TABLE =
  FRIENDS_BACKEND === "table" || FRIENDS_BACKEND === "pg";

function contentHash(s) {
  return crypto.createHash("sha256").update(String(s)).digest("hex");
}

function pairKey(a, b) {
  const x = String(a);
  const y = String(b);
  return x < y ? [x, y] : [y, x];
}

function emptyFriends() {
  return {
    list: [],
    incoming: [],
    outgoing: [],
    levels: {},
    levelDays: {},
    levelRewardClaimed: {},
    petKraulTargets: [],
    petKraulDay: null,
  };
}

function extractFromUsers(usersMap) {
  const users = usersMap && typeof usersMap === "object" ? usersMap : {};
  const friendships = new Map(); // "a|b" -> {user_a,user_b,level,level_day}
  const requests = new Map(); // "from|to" -> {from,to}
  const listMeta = [];
  const petKraul = [];

  for (const [uid, u] of Object.entries(users)) {
    if (!u || typeof u !== "object") continue;
    const f = u.friends && typeof u.friends === "object" ? u.friends : {};
    const list = Array.isArray(f.list) ? f.list.map(String) : [];
    const incoming = Array.isArray(f.incoming) ? f.incoming.map(String) : [];
    const outgoing = Array.isArray(f.outgoing) ? f.outgoing.map(String) : [];
    const levels = f.levels && typeof f.levels === "object" ? f.levels : {};
    const levelDays =
      f.levelDays && typeof f.levelDays === "object" ? f.levelDays : {};
    const claimed =
      f.levelRewardClaimed && typeof f.levelRewardClaimed === "object"
        ? f.levelRewardClaimed
        : {};

    list.forEach((fid, idx) => {
      if (!fid || fid === uid) return;
      const [a, b] = pairKey(uid, fid);
      const key = `${a}|${b}`;
      const lv = Math.max(0, Math.min(100, Math.floor(Number(levels[fid]) || 0)));
      const day = levelDays[fid] ? String(levelDays[fid]) : null;
      const prev = friendships.get(key);
      if (!prev) {
        friendships.set(key, {
          user_a: a,
          user_b: b,
          level: lv,
          level_day: day,
        });
      } else {
        prev.level = Math.max(prev.level, lv);
        if (!prev.level_day && day) prev.level_day = day;
      }
      listMeta.push({
        user_id: String(uid),
        friend_id: String(fid),
        sort_index: idx,
        level_reward_claimed: Math.max(
          0,
          Math.floor(Number(claimed[fid]) || 0)
        ),
      });
    });

    for (const to of outgoing) {
      if (!to || to === uid) continue;
      requests.set(`${uid}|${to}`, {
        from_user_id: String(uid),
        to_user_id: String(to),
      });
    }
    for (const from of incoming) {
      if (!from || from === uid) continue;
      requests.set(`${from}|${uid}`, {
        from_user_id: String(from),
        to_user_id: String(uid),
      });
    }

    const dayKey = f.petKraulDay ? String(f.petKraulDay) : null;
    const targets = Array.isArray(f.petKraulTargets)
      ? f.petKraulTargets.map(String)
      : [];
    if (dayKey) {
      for (const t of targets) {
        if (!t) continue;
        petKraul.push({
          user_id: String(uid),
          day_key: dayKey,
          target_id: String(t),
        });
      }
    }
  }

  return {
    friendships: [...friendships.values()],
    requests: [...requests.values()],
    listMeta,
    petKraul,
  };
}

function applyToUsers(usersMap, data) {
  const users = usersMap && typeof usersMap === "object" ? usersMap : {};
  for (const u of Object.values(users)) {
    if (!u || typeof u !== "object") continue;
    u.friends = emptyFriends();
  }

  const friendships = data.friendships || [];
  const requests = data.requests || [];
  const listMeta = data.listMeta || [];
  const petKraul = data.petKraul || [];

  const metaByUser = new Map();
  for (const m of listMeta) {
    const uid = String(m.user_id);
    if (!metaByUser.has(uid)) metaByUser.set(uid, []);
    metaByUser.get(uid).push(m);
  }
  for (const arr of metaByUser.values()) {
    arr.sort((a, b) => (a.sort_index || 0) - (b.sort_index || 0));
  }

  for (const fr of friendships) {
    const a = String(fr.user_a);
    const b = String(fr.user_b);
    const level = Math.max(0, Math.min(100, Math.floor(Number(fr.level) || 0)));
    const levelDay = fr.level_day ? String(fr.level_day) : null;
    for (const [uid, other] of [
      [a, b],
      [b, a],
    ]) {
      const u = users[uid];
      if (!u) continue;
      if (!u.friends) u.friends = emptyFriends();
      if (!u.friends.list.includes(other)) u.friends.list.push(other);
      u.friends.levels[other] = level;
      if (levelDay) u.friends.levelDays[other] = levelDay;
    }
  }

  // Reorder lists from meta + claims
  for (const [uid, metas] of metaByUser.entries()) {
    const u = users[uid];
    if (!u?.friends) continue;
    const ordered = [];
    const seen = new Set();
    for (const m of metas) {
      const fid = String(m.friend_id);
      if (!u.friends.list.includes(fid) || seen.has(fid)) continue;
      ordered.push(fid);
      seen.add(fid);
      u.friends.levelRewardClaimed[fid] = Math.max(
        0,
        Math.floor(Number(m.level_reward_claimed) || 0)
      );
    }
    for (const fid of u.friends.list) {
      if (!seen.has(fid)) ordered.push(fid);
    }
    u.friends.list = ordered;
  }

  for (const r of requests) {
    const from = String(r.from_user_id);
    const to = String(r.to_user_id);
    if (users[from]?.friends && !users[from].friends.outgoing.includes(to)) {
      users[from].friends.outgoing.push(to);
    }
    if (users[to]?.friends && !users[to].friends.incoming.includes(from)) {
      users[to].friends.incoming.push(from);
    }
  }

  const kraulByUser = new Map();
  for (const p of petKraul) {
    const uid = String(p.user_id);
    if (!kraulByUser.has(uid)) {
      kraulByUser.set(uid, { day: String(p.day_key), targets: [] });
    }
    kraulByUser.get(uid).targets.push(String(p.target_id));
  }
  for (const [uid, k] of kraulByUser.entries()) {
    const u = users[uid];
    if (!u?.friends) continue;
    u.friends.petKraulDay = k.day;
    u.friends.petKraulTargets = [...new Set(k.targets)];
  }

  return users;
}

function summarizeFriends(usersMap) {
  const extracted = extractFromUsers(usersMap);
  const samples = extracted.friendships
    .slice()
    .sort((x, y) => `${x.user_a}|${x.user_b}`.localeCompare(`${y.user_a}|${y.user_b}`))
    .slice(0, 5)
    .map((f) => ({
      a: f.user_a,
      b: f.user_b,
      level: f.level,
      day: f.level_day || null,
    }));
  const reqSamples = extracted.requests
    .slice()
    .sort((x, y) =>
      `${x.from_user_id}|${x.to_user_id}`.localeCompare(
        `${y.from_user_id}|${y.to_user_id}`
      )
    )
    .slice(0, 5)
    .map((r) => ({ from: r.from_user_id, to: r.to_user_id }));
  return {
    friendships: extracted.friendships.length,
    requests: extracted.requests.length,
    listMeta: extracted.listMeta.length,
    petKraul: extracted.petKraul.length,
    samples,
    reqSamples,
    fingerprint: contentHash(
      JSON.stringify({
        f: extracted.friendships
          .map((x) => [x.user_a, x.user_b, x.level, x.level_day || ""])
          .sort(),
        r: extracted.requests
          .map((x) => [x.from_user_id, x.to_user_id])
          .sort(),
        m: extracted.listMeta
          .map((x) => [
            x.user_id,
            x.friend_id,
            x.sort_index,
            x.level_reward_claimed,
          ])
          .sort(),
        p: extracted.petKraul
          .map((x) => [x.user_id, x.day_key, x.target_id])
          .sort(),
      })
    ),
  };
}

async function withClient(fn) {
  if (!DATABASE_URL) throw new Error("DATABASE_URL missing");
  let pg;
  try {
    pg = require("pg");
  } catch {
    throw new Error("pg package not installed");
  }
  const client = new pg.Client({ connectionString: DATABASE_URL });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

async function ensureFriendsTables(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS friendships (
      user_a TEXT NOT NULL,
      user_b TEXT NOT NULL,
      level INTEGER NOT NULL DEFAULT 0,
      level_day TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (user_a, user_b),
      CHECK (user_a < user_b)
    )
  `);
  await client.query(`
    CREATE TABLE IF NOT EXISTS friend_requests (
      from_user_id TEXT NOT NULL,
      to_user_id TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (from_user_id, to_user_id),
      CHECK (from_user_id <> to_user_id)
    )
  `);
  await client.query(`
    CREATE TABLE IF NOT EXISTS friend_list_meta (
      user_id TEXT NOT NULL,
      friend_id TEXT NOT NULL,
      sort_index INTEGER NOT NULL DEFAULT 0,
      level_reward_claimed INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, friend_id)
    )
  `);
  await client.query(`
    CREATE TABLE IF NOT EXISTS friend_pet_kraul (
      user_id TEXT NOT NULL,
      day_key TEXT NOT NULL,
      target_id TEXT NOT NULL,
      PRIMARY KEY (user_id, day_key, target_id)
    )
  `);
}

async function replaceFriendsInClient(client, usersMap) {
  await ensureFriendsTables(client);
  const data = extractFromUsers(usersMap);
  await client.query(`DELETE FROM friend_pet_kraul`);
  await client.query(`DELETE FROM friend_list_meta`);
  await client.query(`DELETE FROM friend_requests`);
  await client.query(`DELETE FROM friendships`);

  for (const f of data.friendships) {
    await client.query(
      `INSERT INTO friendships(user_a, user_b, level, level_day)
       VALUES ($1,$2,$3,$4)`,
      [f.user_a, f.user_b, f.level, f.level_day]
    );
  }
  for (const r of data.requests) {
    await client.query(
      `INSERT INTO friend_requests(from_user_id, to_user_id)
       VALUES ($1,$2)`,
      [r.from_user_id, r.to_user_id]
    );
  }
  for (const m of data.listMeta) {
    await client.query(
      `INSERT INTO friend_list_meta(user_id, friend_id, sort_index, level_reward_claimed)
       VALUES ($1,$2,$3,$4)`,
      [m.user_id, m.friend_id, m.sort_index, m.level_reward_claimed]
    );
  }
  for (const p of data.petKraul) {
    await client.query(
      `INSERT INTO friend_pet_kraul(user_id, day_key, target_id)
       VALUES ($1,$2,$3)`,
      [p.user_id, p.day_key, p.target_id]
    );
  }
  return {
    friendships: data.friendships.length,
    requests: data.requests.length,
    listMeta: data.listMeta.length,
    petKraul: data.petKraul.length,
  };
}

async function replaceAllFriends(usersMap) {
  return withClient(async (client) => {
    await client.query("BEGIN");
    try {
      const counts = await replaceFriendsInClient(client, usersMap);
      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_friends_sot_at', $1)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
        [new Date().toISOString()]
      );
      await client.query("COMMIT");
      return counts;
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    }
  });
}

async function loadAllFriendsData() {
  return withClient(async (client) => {
    await ensureFriendsTables(client);
    const fr = await client.query(
      `SELECT user_a, user_b, level, level_day FROM friendships`
    );
    const rr = await client.query(
      `SELECT from_user_id, to_user_id FROM friend_requests`
    );
    const mr = await client.query(
      `SELECT user_id, friend_id, sort_index, level_reward_claimed FROM friend_list_meta`
    );
    const pr = await client.query(
      `SELECT user_id, day_key, target_id FROM friend_pet_kraul`
    );
    return {
      friendships: fr.rows.map((r) => ({
        user_a: r.user_a,
        user_b: r.user_b,
        level: Number(r.level) || 0,
        level_day: r.level_day || null,
      })),
      requests: rr.rows.map((r) => ({
        from_user_id: r.from_user_id,
        to_user_id: r.to_user_id,
      })),
      listMeta: mr.rows.map((r) => ({
        user_id: r.user_id,
        friend_id: r.friend_id,
        sort_index: Number(r.sort_index) || 0,
        level_reward_claimed: Number(r.level_reward_claimed) || 0,
      })),
      petKraul: pr.rows.map((r) => ({
        user_id: r.user_id,
        day_key: r.day_key,
        target_id: r.target_id,
      })),
    };
  });
}

function loadAllFriendsDataSync() {
  const helper = path.join(__dirname, "friends_pg_cli.js");
  const out = execFileSync(process.execPath, [helper, "load"], {
    encoding: "utf8",
    maxBuffer: 50 * 1024 * 1024,
    env: process.env,
  }).trim();
  if (!out || out === "null") {
    return { friendships: [], requests: [], listMeta: [], petKraul: [] };
  }
  return JSON.parse(out);
}

function hydrateUsersFriends(usersMap) {
  const data = loadAllFriendsDataSync();
  applyToUsers(usersMap, data);
  return {
    friendships: data.friendships.length,
    requests: data.requests.length,
  };
}

async function verifyFriends(usersMap) {
  const memSum = summarizeFriends(usersMap);
  const pgData = await loadAllFriendsData();
  // Build a synthetic users map from PG for fingerprint compare via apply+extract
  const synth = {};
  for (const id of Object.keys(usersMap || {})) {
    synth[id] = { id, friends: emptyFriends() };
  }
  // Ensure all referenced ids exist
  for (const f of pgData.friendships) {
    if (!synth[f.user_a]) synth[f.user_a] = { id: f.user_a, friends: emptyFriends() };
    if (!synth[f.user_b]) synth[f.user_b] = { id: f.user_b, friends: emptyFriends() };
  }
  for (const r of pgData.requests) {
    if (!synth[r.from_user_id])
      synth[r.from_user_id] = { id: r.from_user_id, friends: emptyFriends() };
    if (!synth[r.to_user_id])
      synth[r.to_user_id] = { id: r.to_user_id, friends: emptyFriends() };
  }
  applyToUsers(synth, pgData);
  const pgSum = summarizeFriends(synth);
  const ok =
    memSum.friendships === pgSum.friendships &&
    memSum.requests === pgSum.requests &&
    memSum.listMeta === pgSum.listMeta &&
    memSum.petKraul === pgSum.petKraul &&
    memSum.fingerprint === pgSum.fingerprint;
  return { ok, mem: memSum, postgres: pgSum };
}

module.exports = {
  FRIENDS_BACKEND: USE_FRIENDS_TABLE ? "table" : "blob",
  USE_FRIENDS_TABLE,
  emptyFriends,
  extractFromUsers,
  applyToUsers,
  summarizeFriends,
  ensureFriendsTables,
  replaceFriendsInClient,
  replaceAllFriends,
  loadAllFriendsData,
  loadAllFriendsDataSync,
  hydrateUsersFriends,
  verifyFriends,
};

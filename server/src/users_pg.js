/**
 * Phase 1: users table as source of truth (full user in raw + indexed columns).
 * Env: USERS_BACKEND=table|blob (default blob until cutover).
 */
const crypto = require("crypto");
const path = require("path");
const { execFileSync } = require("child_process");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const USERS_BACKEND = String(process.env.USERS_BACKEND || "blob").toLowerCase();
const USE_USERS_TABLE = USERS_BACKEND === "table" || USERS_BACKEND === "pg";

function userEmail(u) {
  if (!u || typeof u !== "object") return null;
  if (u.email) return String(u.email);
  if (u.googleEmail) return String(u.googleEmail);
  return null;
}

function stableStringify(value) {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) {
    return `[${value.map((v) => stableStringify(v)).join(",")}]`;
  }
  const keys = Object.keys(value).sort();
  return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(value[k])}`).join(",")}}`;
}

function contentHash(s) {
  return crypto.createHash("sha256").update(s).digest("hex");
}

function summarizeUsers(usersMap) {
  const users = usersMap && typeof usersMap === "object" ? usersMap : {};
  const ids = Object.keys(users);
  let coinsSum = 0;
  for (const id of ids) coinsSum += Number(users[id]?.coins) || 0;
  ids.sort();
  const samples = ids.slice(0, 5).map((id) => {
    const u = users[id] || {};
    return {
      id,
      coins: Number(u.coins) || 0,
      invHash: contentHash(stableStringify(u.inventory || null)),
    };
  });
  return { users: ids.length, coinsSum, samples };
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

async function ensureUsersSotColumns(client) {
  await client.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub TEXT`);
  await client.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS google_email TEXT`);
  await client.query(`ALTER TABLE users ADD COLUMN IF NOT EXISTS role TEXT`);
  await client.query(
    `ALTER TABLE users ADD COLUMN IF NOT EXISTS paid_coins INTEGER NOT NULL DEFAULT 0`
  );
  await client.query(
    `ALTER TABLE users ADD COLUMN IF NOT EXISTS daily_balance INTEGER NOT NULL DEFAULT 0`
  );
  await client.query(
    `ALTER TABLE users ADD COLUMN IF NOT EXISTS inventory JSONB NOT NULL DEFAULT '{}'::jsonb`
  );
}

function rowParams(id, u) {
  const email = userEmail(u);
  const inventory =
    u.inventory && typeof u.inventory === "object" ? u.inventory : {};
  return [
    String(id),
    email,
    u.nickname ? String(u.nickname) : null,
    Number(u.coins) || 0,
    JSON.stringify(u),
    u.googleSub ? String(u.googleSub) : null,
    u.googleEmail ? String(u.googleEmail) : email,
    u.role ? String(u.role) : null,
    Number(u.paidCoins) || 0,
    Number(u.dailyBalance) || 0,
    JSON.stringify(inventory),
  ];
}

const UPSERT_SQL = `
  INSERT INTO users(
    id, email, nickname, coins, raw, updated_at,
    google_sub, google_email, role, paid_coins, daily_balance, inventory
  )
  VALUES ($1,$2,$3,$4,$5::jsonb, NOW(), $6,$7,$8,$9,$10,$11::jsonb)
  ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    nickname = EXCLUDED.nickname,
    coins = EXCLUDED.coins,
    raw = EXCLUDED.raw,
    google_sub = EXCLUDED.google_sub,
    google_email = EXCLUDED.google_email,
    role = EXCLUDED.role,
    paid_coins = EXCLUDED.paid_coins,
    daily_balance = EXCLUDED.daily_balance,
    inventory = EXCLUDED.inventory,
    updated_at = NOW()
`;

async function upsertUsersInClient(client, usersMap) {
  await ensureUsersSotColumns(client);
  const users = usersMap && typeof usersMap === "object" ? usersMap : {};
  let n = 0;
  for (const [id, u] of Object.entries(users)) {
    if (!u || typeof u !== "object") continue;
    await client.query(UPSERT_SQL, rowParams(id, u));
    n += 1;
  }
  return n;
}

async function upsertAllUsers(usersMap) {
  return withClient(async (client) => {
    await client.query("BEGIN");
    try {
      const n = await upsertUsersInClient(client, usersMap);
      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_users_sot_at', $1)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
        [new Date().toISOString()]
      );
      await client.query("COMMIT");
      return n;
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    }
  });
}

async function loadAllUsers() {
  return withClient(async (client) => {
    await ensureUsersSotColumns(client);
    const r = await client.query(`SELECT id, raw FROM users`);
    const out = {};
    for (const row of r.rows) {
      const raw = row.raw;
      if (raw && typeof raw === "object") {
        out[String(row.id)] = { ...raw, id: raw.id || row.id };
      }
    }
    return out;
  });
}

function loadAllUsersSync() {
  const helper = path.join(__dirname, "users_pg_cli.js");
  const out = execFileSync(process.execPath, [helper, "load"], {
    encoding: "utf8",
    maxBuffer: 200 * 1024 * 1024,
    env: process.env,
  }).trim();
  if (!out || out === "null") return {};
  return JSON.parse(out);
}

function upsertAllUsersSync(usersMap) {
  const helper = path.join(__dirname, "users_pg_cli.js");
  execFileSync(process.execPath, [helper, "upsert"], {
    input: JSON.stringify(usersMap || {}),
    encoding: "utf8",
    maxBuffer: 200 * 1024 * 1024,
    env: process.env,
  });
}

async function verifyUsers(memUsers) {
  const memSum = summarizeUsers(memUsers);
  const pgUsers = await loadAllUsers();
  const pgSum = summarizeUsers(pgUsers);
  const ok =
    memSum.users === pgSum.users &&
    memSum.coinsSum === pgSum.coinsSum &&
    JSON.stringify(memSum.samples) === JSON.stringify(pgSum.samples);
  return { ok, mem: memSum, postgres: pgSum };
}

module.exports = {
  USERS_BACKEND: USE_USERS_TABLE ? "table" : "blob",
  USE_USERS_TABLE,
  summarizeUsers,
  ensureUsersSotColumns,
  upsertUsersInClient,
  upsertAllUsers,
  upsertAllUsersSync,
  loadAllUsers,
  loadAllUsersSync,
  verifyUsers,
};

/**
 * Phase 2: sessions table as source of truth.
 * Env: SESSIONS_BACKEND=table|blob
 */
const crypto = require("crypto");
const path = require("path");
const { execFileSync } = require("child_process");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const SESSIONS_BACKEND = String(process.env.SESSIONS_BACKEND || "blob").toLowerCase();
const USE_SESSIONS_TABLE =
  SESSIONS_BACKEND === "table" || SESSIONS_BACKEND === "pg";

function contentHash(s) {
  return crypto.createHash("sha256").update(String(s)).digest("hex");
}

function summarizeSessions(sessionsMap) {
  const sessions = sessionsMap && typeof sessionsMap === "object" ? sessionsMap : {};
  const tokens = Object.keys(sessions);
  tokens.sort();
  let active = 0;
  const now = Date.now();
  for (const t of tokens) {
    const s = sessions[t];
    if (s && Number(s.expiresAt) >= now) active += 1;
  }
  const samples = tokens.slice(0, 5).map((token) => {
    const s = sessions[token] || {};
    return {
      tokenHash: contentHash(token).slice(0, 16),
      userId: s.userId ? String(s.userId) : null,
      kind: s.kind || null,
      expiresAt: Number(s.expiresAt) || 0,
    };
  });
  return { sessions: tokens.length, active, samples };
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

async function ensureSessionsTable(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS sessions (
      token TEXT PRIMARY KEY,
      user_id TEXT,
      kind TEXT,
      expires_at TIMESTAMPTZ,
      raw JSONB NOT NULL DEFAULT '{}'::jsonb,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  await client.query(
    `CREATE INDEX IF NOT EXISTS sessions_user_id_idx ON sessions (user_id)`
  );
  await client.query(
    `CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions (expires_at)`
  );
}

async function replaceSessionsInClient(client, sessionsMap) {
  await ensureSessionsTable(client);
  const sessions = sessionsMap && typeof sessionsMap === "object" ? sessionsMap : {};
  await client.query(`DELETE FROM sessions`);
  let n = 0;
  for (const [token, s] of Object.entries(sessions)) {
    if (!s || typeof s !== "object") continue;
    const expiresMs = Number(s.expiresAt) || null;
    const expiresAt = expiresMs
      ? new Date(expiresMs).toISOString()
      : null;
    await client.query(
      `INSERT INTO sessions(token, user_id, kind, expires_at, raw, updated_at)
       VALUES ($1,$2,$3,$4,$5::jsonb, NOW())`,
      [
        String(token),
        s.userId != null ? String(s.userId) : null,
        s.kind ? String(s.kind) : null,
        expiresAt,
        JSON.stringify(s),
      ]
    );
    n += 1;
  }
  return n;
}

async function replaceAllSessions(sessionsMap) {
  return withClient(async (client) => {
    await client.query("BEGIN");
    try {
      const n = await replaceSessionsInClient(client, sessionsMap);
      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_sessions_sot_at', $1)
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

async function loadAllSessions() {
  return withClient(async (client) => {
    await ensureSessionsTable(client);
    const r = await client.query(`SELECT token, raw FROM sessions`);
    const out = {};
    for (const row of r.rows) {
      const raw = row.raw;
      if (raw && typeof raw === "object") {
        out[String(row.token)] = raw;
      }
    }
    return out;
  });
}

function loadAllSessionsSync() {
  const helper = path.join(__dirname, "sessions_pg_cli.js");
  const out = execFileSync(process.execPath, [helper, "load"], {
    encoding: "utf8",
    maxBuffer: 50 * 1024 * 1024,
    env: process.env,
  }).trim();
  if (!out || out === "null") return {};
  return JSON.parse(out);
}

async function verifySessions(memSessions) {
  const memSum = summarizeSessions(memSessions);
  const pgSessions = await loadAllSessions();
  const pgSum = summarizeSessions(pgSessions);
  const ok =
    memSum.sessions === pgSum.sessions &&
    memSum.active === pgSum.active &&
    JSON.stringify(memSum.samples) === JSON.stringify(pgSum.samples);
  return { ok, mem: memSum, postgres: pgSum };
}

module.exports = {
  SESSIONS_BACKEND: USE_SESSIONS_TABLE ? "table" : "blob",
  USE_SESSIONS_TABLE,
  summarizeSessions,
  ensureSessionsTable,
  replaceSessionsInClient,
  replaceAllSessions,
  loadAllSessions,
  loadAllSessionsSync,
  verifySessions,
};

/**
 * Postgres helpers for store_live (primary document) + users denormalization.
 */
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();

function userEmail(u) {
  if (!u || typeof u !== "object") return null;
  if (u.email) return String(u.email);
  if (u.googleEmail) return String(u.googleEmail);
  return null;
}

function contentHash(rawUtf8) {
  return crypto.createHash("sha256").update(rawUtf8).digest("hex");
}

/** Stable JSON for checksums (JSONB round-trip reorders object keys). */
function stableStringify(value) {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((v) => stableStringify(v)).join(",")}]`;
  }
  const keys = Object.keys(value).sort();
  return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(value[k])}`).join(",")}}`;
}

function summarize(db) {
  const users = db.users && typeof db.users === "object" ? db.users : {};
  const ids = Object.keys(users);
  let coinsSum = 0;
  for (const id of ids) {
    coinsSum += Number(users[id]?.coins) || 0;
  }
  ids.sort();
  const samples = ids.slice(0, 3).map((id) => {
    const u = users[id] || {};
    return {
      id,
      coins: Number(u.coins) || 0,
      invHash: contentHash(stableStringify(u.inventory || null)),
    };
  });
  return {
    users: ids.length,
    sessions: Object.keys(db.sessions || {}).length,
    marketListings: Object.keys(db.marketListings || {}).length,
    rooms: Object.keys(db.rooms || {}).length,
    coinsSum,
    samples,
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

async function ensureStoreLiveTable(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS store_live (
      id SMALLINT PRIMARY KEY CHECK (id = 1),
      payload JSONB NOT NULL,
      bytes INTEGER NOT NULL DEFAULT 0,
      content_hash TEXT,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}

async function loadLive() {
  return withClient(async (client) => {
    await ensureStoreLiveTable(client);
    const r = await client.query(
      `SELECT payload FROM store_live WHERE id = 1`
    );
    if (!r.rows.length) return null;
    return r.rows[0].payload;
  });
}

async function saveLive(db, { source = "save", alsoSnapshot = false } = {}) {
  const raw = JSON.stringify(db, null, 0);
  const bytes = Buffer.byteLength(raw);
  const hash = contentHash(raw);
  const users = db.users && typeof db.users === "object" ? db.users : {};

  await withClient(async (client) => {
    await ensureStoreLiveTable(client);
    await client.query("BEGIN");
    try {
      await client.query(
        `INSERT INTO store_live(id, payload, bytes, content_hash, updated_at)
         VALUES (1, $1::jsonb, $2, $3, NOW())
         ON CONFLICT (id) DO UPDATE SET
           payload = EXCLUDED.payload,
           bytes = EXCLUDED.bytes,
           content_hash = EXCLUDED.content_hash,
           updated_at = NOW()`,
        [raw, bytes, hash]
      );

      if (alsoSnapshot) {
        await client.query(
          `INSERT INTO store_snapshots(source, bytes, payload) VALUES ($1,$2,$3::jsonb)`,
          [source, bytes, raw]
        );
        await client.query(
          `DELETE FROM store_snapshots WHERE id NOT IN (
             SELECT id FROM store_snapshots ORDER BY id DESC LIMIT 20
           )`
        );
      }

      // Prefer SoT upsert (extra columns); fall back to legacy columns
      try {
        const { upsertUsersInClient } = require("./users_pg");
        await upsertUsersInClient(client, users);
      } catch {
        for (const [id, u] of Object.entries(users)) {
          if (!u || typeof u !== "object") continue;
          await client.query(
            `INSERT INTO users(id, email, nickname, coins, raw, updated_at)
             VALUES ($1,$2,$3,$4,$5::jsonb, NOW())
             ON CONFLICT (id) DO UPDATE SET
               email = EXCLUDED.email,
               nickname = EXCLUDED.nickname,
               coins = EXCLUDED.coins,
               raw = EXCLUDED.raw,
               updated_at = NOW()`,
            [
              String(id),
              userEmail(u),
              u.nickname ? String(u.nickname) : null,
              Number(u.coins) || 0,
              JSON.stringify(u),
            ]
          );
        }
      }

      try {
        const { replaceSessionsInClient } = require("./sessions_pg");
        const sessions =
          db.sessions && typeof db.sessions === "object" ? db.sessions : {};
        await replaceSessionsInClient(client, sessions);
      } catch {
        /* sessions table may not exist yet */
      }

      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_store_live_at', $1)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
        [new Date().toISOString()]
      );
      await client.query("COMMIT");
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    }
  });

  return { bytes, hash, summary: summarize(db) };
}

/** Sync load for module boot (spawns short-lived Node). */
function loadLiveSync() {
  const helper = path.join(__dirname, "pg_store_cli.js");
  const out = execFileSync(process.execPath, [helper, "load"], {
    encoding: "utf8",
    maxBuffer: 200 * 1024 * 1024,
    env: process.env,
  }).trim();
  if (!out || out === "null") return null;
  return JSON.parse(out);
}

/** Sync save for process exit. */
function saveLiveSync(db, opts = {}) {
  const helper = path.join(__dirname, "pg_store_cli.js");
  const raw = JSON.stringify(db, null, 0);
  const args = ["save"];
  if (opts.alsoSnapshot) args.push("--snapshot");
  if (opts.source) args.push("--source", String(opts.source));
  execFileSync(process.execPath, [helper, ...args], {
    input: raw,
    encoding: "utf8",
    maxBuffer: 200 * 1024 * 1024,
    env: process.env,
  });
}

function writeJsonMirror(dataFile, db) {
  const dir = path.dirname(dataFile);
  fs.mkdirSync(dir, { recursive: true });
  const tmp = dataFile + ".tmp";
  const raw = typeof db === "string" ? db : JSON.stringify(db, null, 0);
  fs.writeFileSync(tmp, raw, "utf8");
  fs.renameSync(tmp, dataFile);
}

function writeJsonMirrorAsync(dataFile, payloadUtf8) {
  return new Promise((resolve) => {
    const dir = path.dirname(dataFile);
    try {
      fs.mkdirSync(dir, { recursive: true });
    } catch {
      /* ignore */
    }
    const tmp = dataFile + ".tmp";
    fs.writeFile(tmp, payloadUtf8, "utf8", (err) => {
      if (err) {
        console.error("[store] json mirror write failed", err?.message || err);
        resolve(false);
        return;
      }
      fs.rename(tmp, dataFile, (err2) => {
        if (err2) {
          console.error("[store] json mirror rename failed", err2?.message || err2);
          resolve(false);
          return;
        }
        resolve(true);
      });
    });
  });
}

module.exports = {
  DATABASE_URL,
  contentHash,
  stableStringify,
  summarize,
  loadLive,
  saveLive,
  loadLiveSync,
  saveLiveSync,
  writeJsonMirror,
  writeJsonMirrorAsync,
  userEmail,
  ensureStoreLiveTable,
  withClient,
};

/**
 * Throttled Postgres snapshot history.
 * When STORE_BACKEND=postgres, pass { payloadUtf8 } from store.js (no JSON file).
 * When JSON is primary, may still read dataFile.
 */
const fs = require("fs");
const path = require("path");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const STORE_BACKEND = String(process.env.STORE_BACKEND || "json").toLowerCase();
const USE_PG = STORE_BACKEND === "postgres" || STORE_BACKEND === "pg";
const MIN_INTERVAL_MS = Math.max(
  5_000,
  Number(process.env.PG_DUAL_INTERVAL_MS) || 30_000
);

let lastAt = 0;
let running = false;

async function maybeDualWrite(dataFile, opts = {}) {
  if (!DATABASE_URL) return;
  const now = Date.now();
  if (running || now - lastAt < MIN_INTERVAL_MS) return;
  running = true;
  lastAt = now;
  try {
    let pg;
    try {
      pg = require("pg");
    } catch {
      return;
    }
    let raw = opts.payloadUtf8 ? String(opts.payloadUtf8) : null;
    if (!raw) {
      const file =
        dataFile || path.join(process.env.DATA_DIR || "/data", "luv-store.json");
      if (!fs.existsSync(file)) return;
      raw = fs.readFileSync(file, "utf8");
    }
    const db = JSON.parse(raw);
    const users = db.users && typeof db.users === "object" ? db.users : {};
    const client = new pg.Client({ connectionString: DATABASE_URL });
    await client.connect();
    try {
      await client.query("BEGIN");
      await client.query(
        `INSERT INTO store_snapshots(source, bytes, payload) VALUES ($1,$2,$3::jsonb)`,
        [
          opts.fromPrimary ? "primary_snapshot" : "dual_write",
          Buffer.byteLength(raw),
          raw,
        ]
      );
      await client.query(
        `DELETE FROM store_snapshots WHERE id NOT IN (
           SELECT id FROM store_snapshots ORDER BY id DESC LIMIT 20
         )`
      );

      if (!USE_PG) {
        await client.query(`
          CREATE TABLE IF NOT EXISTS store_live (
            id SMALLINT PRIMARY KEY CHECK (id = 1),
            payload JSONB NOT NULL,
            bytes INTEGER NOT NULL DEFAULT 0,
            content_hash TEXT,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
          )
        `);
        const crypto = require("crypto");
        const hash = crypto.createHash("sha256").update(raw).digest("hex");
        await client.query(
          `INSERT INTO store_live(id, payload, bytes, content_hash, updated_at)
           VALUES (1, $1::jsonb, $2, $3, NOW())
           ON CONFLICT (id) DO UPDATE SET
             payload = EXCLUDED.payload,
             bytes = EXCLUDED.bytes,
             content_hash = EXCLUDED.content_hash,
             updated_at = NOW()`,
          [raw, Buffer.byteLength(raw), hash]
        );
        for (const [id, u] of Object.entries(users)) {
          if (!u || typeof u !== "object") continue;
          const email = u.email
            ? String(u.email)
            : u.googleEmail
              ? String(u.googleEmail)
              : null;
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
              email,
              u.nickname ? String(u.nickname) : null,
              Number(u.coins) || 0,
              JSON.stringify(u),
            ]
          );
        }
      }

      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_dual_write_at', $1)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
        [new Date().toISOString()]
      );
      await client.query("COMMIT");
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    } finally {
      await client.end();
    }
  } catch (err) {
    console.warn("[pg_dual]", err?.message || err);
  } finally {
    running = false;
  }
}

module.exports = { maybeDualWrite };

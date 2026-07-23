/**
 * Dual-write: after JSON save, throttle a Postgres snapshot + users upsert.
 * Never blocks the request path. STORE_BACKEND stays json until explicit flip.
 */
const fs = require("fs");
const path = require("path");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const MIN_INTERVAL_MS = Math.max(
  5_000,
  Number(process.env.PG_DUAL_INTERVAL_MS) || 30_000
);

let lastAt = 0;
let running = false;

async function maybeDualWrite(dataFile) {
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
    const file = dataFile || path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    if (!fs.existsSync(file)) return;
    const raw = fs.readFileSync(file, "utf8");
    const db = JSON.parse(raw);
    const users = db.users && typeof db.users === "object" ? db.users : {};
    const client = new pg.Client({ connectionString: DATABASE_URL });
    await client.connect();
    try {
      await client.query("BEGIN");
      await client.query(
        `INSERT INTO store_snapshots(source, bytes, payload) VALUES ($1,$2,$3::jsonb)`,
        ["dual_write", Buffer.byteLength(raw), raw]
      );
      // Keep only last 20 snapshots
      await client.query(
        `DELETE FROM store_snapshots WHERE id NOT IN (
           SELECT id FROM store_snapshots ORDER BY id DESC LIMIT 20
         )`
      );
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
            u.email ? String(u.email) : null,
            u.nickname ? String(u.nickname) : null,
            Number(u.coins) || 0,
            JSON.stringify(u),
          ]
        );
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

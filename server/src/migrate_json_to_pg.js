#!/usr/bin/env node
/**
 * One-shot: import /data/luv-store.json into Postgres (snapshot + users table).
 * Does NOT switch STORE_BACKEND — JSON remains live until feature flag flip.
 *
 * Usage (inside luv-api / or with DATABASE_URL):
 *   node src/migrate_json_to_pg.js
 */
const fs = require("fs");
const path = require("path");

async function main() {
  const url = process.env.DATABASE_URL || "";
  if (!url) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  const dataDir = process.env.DATA_DIR || "/data";
  const storePath = path.join(dataDir, "luv-store.json");
  if (!fs.existsSync(storePath)) {
    console.error("missing", storePath);
    process.exit(1);
  }

  let pg;
  try {
    pg = require("pg");
  } catch {
    console.error("pg package not installed — run npm i pg");
    process.exit(1);
  }

  const raw = fs.readFileSync(storePath, "utf8");
  const db = JSON.parse(raw);
  const users = db.users && typeof db.users === "object" ? db.users : {};

  const client = new pg.Client({ connectionString: url });
  await client.connect();
  try {
    await client.query("BEGIN");
    await client.query(
      `INSERT INTO store_snapshots(source, bytes, payload) VALUES ($1, $2, $3::jsonb)`,
      ["migrate_json_to_pg", Buffer.byteLength(raw), raw]
    );

    let n = 0;
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
      n += 1;
    }

    await client.query(
      `INSERT INTO meta(key, value) VALUES ('last_json_migrate_at', $1)
       ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
      [new Date().toISOString()]
    );
    await client.query("COMMIT");
    console.log(JSON.stringify({ ok: true, usersImported: n, bytes: Buffer.byteLength(raw) }));
  } catch (e) {
    await client.query("ROLLBACK");
    throw e;
  } finally {
    await client.end();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

#!/usr/bin/env node
/**
 * One-shot: import /data/luv-store.json into Postgres store_live (+ snapshot + users).
 * Does NOT flip STORE_BACKEND — set that in .env after verify_store_pg.js passes.
 *
 * Usage:
 *   node src/migrate_json_to_pg.js
 */
const fs = require("fs");
const path = require("path");
const { saveLive, summarize, contentHash } = require("./pg_store");

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  const dataDir = process.env.DATA_DIR || "/data";
  const storePath = path.join(dataDir, "luv-store.json");
  if (!fs.existsSync(storePath)) {
    console.error("missing", storePath);
    process.exit(1);
  }

  const raw = fs.readFileSync(storePath, "utf8");
  const db = JSON.parse(raw);
  const result = await saveLive(db, {
    source: "migrate_json_to_pg",
    alsoSnapshot: true,
  });

  // meta marker
  const { withClient } = require("./pg_store");
  await withClient(async (client) => {
    await client.query(
      `INSERT INTO meta(key, value) VALUES ('last_json_migrate_at', $1)
       ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
      [new Date().toISOString()]
    );
  });

  console.log(
    JSON.stringify({
      ok: true,
      bytes: result.bytes,
      hash: result.hash,
      fileSha256: contentHash(raw),
      summary: summarize(db),
    })
  );
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

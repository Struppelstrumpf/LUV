#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const {
  replaceAllEvents,
  verifyEvents,
  summarizeEvents,
} = require("./events_pg");

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  let db = {};
  const payload = await loadLive();
  if (payload) db = payload;
  else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    db = JSON.parse(fs.readFileSync(p, "utf8"));
  }
  const n = await replaceAllEvents(db);
  const v = await verifyEvents(db);
  console.log(
    JSON.stringify(
      { ok: v.ok, replaced: n, summary: summarizeEvents(db), verify: v },
      null,
      2
    )
  );
  process.exit(v.ok ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

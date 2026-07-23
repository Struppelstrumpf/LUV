#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { verifyEvents, USE_EVENTS_TABLE } = require("./events_pg");

async function main() {
  let db = {};
  const payload = await loadLive();
  if (payload) db = payload;
  else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    db = JSON.parse(fs.readFileSync(p, "utf8"));
  }
  const v = await verifyEvents(db);
  console.log(
    JSON.stringify(
      {
        eventsBackendEnv: process.env.EVENTS_BACKEND || "blob",
        useTable: USE_EVENTS_TABLE,
        ...v,
      },
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

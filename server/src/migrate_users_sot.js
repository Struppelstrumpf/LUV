#!/usr/bin/env node
/**
 * Upsert all users from store_live (or JSON file) into users table (SoT columns).
 * Does not flip USERS_BACKEND — set USERS_BACKEND=table after verify.
 */
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { upsertAllUsers, verifyUsers, summarizeUsers } = require("./users_pg");

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }

  let users = null;
  const payload = await loadLive();
  if (payload && payload.users && typeof payload.users === "object") {
    users = payload.users;
  } else {
    const dataDir = process.env.DATA_DIR || "/data";
    const storePath = path.join(dataDir, "luv-store.json");
    const db = JSON.parse(fs.readFileSync(storePath, "utf8"));
    users = db.users || {};
  }

  const n = await upsertAllUsers(users);
  const v = await verifyUsers(users);
  console.log(
    JSON.stringify(
      {
        ok: v.ok,
        upserted: n,
        summary: summarizeUsers(users),
        verify: v,
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

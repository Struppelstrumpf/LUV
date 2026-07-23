#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { verifyUsers, USE_USERS_TABLE } = require("./users_pg");

async function main() {
  let users = {};
  const payload = await loadLive();
  if (payload?.users) users = payload.users;
  else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    users = JSON.parse(fs.readFileSync(p, "utf8")).users || {};
  }
  // When table is SoT, also compare against in-memory file after hydrate would match table
  const v = await verifyUsers(users);
  console.log(JSON.stringify({ usersBackendEnv: process.env.USERS_BACKEND || "blob", useTable: USE_USERS_TABLE, ...v }, null, 2));
  process.exit(v.ok ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

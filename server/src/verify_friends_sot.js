#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { loadAllUsers } = require("./users_pg");
const { verifyFriends, USE_FRIENDS_TABLE } = require("./friends_pg");

async function main() {
  let users = {};
  try {
    users = await loadAllUsers();
  } catch {
    /* ignore */
  }
  if (!Object.keys(users).length) {
    const payload = await loadLive();
    if (payload?.users) users = payload.users;
    else {
      const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
      users = JSON.parse(fs.readFileSync(p, "utf8")).users || {};
    }
  }
  const v = await verifyFriends(users);
  console.log(
    JSON.stringify(
      {
        friendsBackendEnv: process.env.FRIENDS_BACKEND || "blob",
        useTable: USE_FRIENDS_TABLE,
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

#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { loadAllUsers } = require("./users_pg");
const {
  replaceAllFriends,
  verifyFriends,
  summarizeFriends,
} = require("./friends_pg");

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  let users = {};
  try {
    users = await loadAllUsers();
  } catch {
    /* fall through */
  }
  if (!Object.keys(users).length) {
    const payload = await loadLive();
    if (payload?.users) users = payload.users;
    else {
      const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
      users = JSON.parse(fs.readFileSync(p, "utf8")).users || {};
    }
  }
  const counts = await replaceAllFriends(users);
  const v = await verifyFriends(users);
  console.log(
    JSON.stringify(
      {
        ok: v.ok,
        replaced: counts,
        summary: summarizeFriends(users),
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

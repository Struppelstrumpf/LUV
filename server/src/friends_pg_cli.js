#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const {
    loadAllFriendsData,
    replaceAllFriends,
    summarizeFriends,
  } = require("./friends_pg");

  if (cmd === "load") {
    process.stdout.write(JSON.stringify(await loadAllFriendsData()));
    return;
  }
  if (cmd === "replace") {
    const raw = fs.readFileSync(0, "utf8");
    const users = JSON.parse(raw || "{}");
    const counts = await replaceAllFriends(users);
    process.stdout.write(
      JSON.stringify({ ok: true, ...counts, ...summarizeFriends(users) })
    );
    return;
  }
  console.error("usage: friends_pg_cli.js load|replace");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const { loadAllUsers, upsertAllUsers, summarizeUsers } = require("./users_pg");

  if (cmd === "load") {
    const users = await loadAllUsers();
    process.stdout.write(JSON.stringify(users));
    return;
  }
  if (cmd === "upsert") {
    const raw = fs.readFileSync(0, "utf8");
    const users = JSON.parse(raw || "{}");
    const n = await upsertAllUsers(users);
    process.stdout.write(JSON.stringify({ ok: true, upserted: n, ...summarizeUsers(users) }));
    return;
  }
  console.error("usage: users_pg_cli.js load|upsert");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

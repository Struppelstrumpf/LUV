#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const { loadAllRooms, replaceAllRooms, summarizeRooms } = require("./rooms_pg");
  if (cmd === "load") {
    process.stdout.write(JSON.stringify(await loadAllRooms()));
    return;
  }
  if (cmd === "replace") {
    const db = JSON.parse(fs.readFileSync(0, "utf8") || "{}");
    const n = await replaceAllRooms(db);
    process.stdout.write(
      JSON.stringify({ ok: true, replaced: n, ...summarizeRooms(db) })
    );
    return;
  }
  console.error("usage: rooms_pg_cli.js load|replace");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const {
    loadAllEvents,
    replaceAllEvents,
    summarizeEvents,
  } = require("./events_pg");

  if (cmd === "load") {
    process.stdout.write(JSON.stringify(await loadAllEvents()));
    return;
  }
  if (cmd === "replace") {
    const raw = fs.readFileSync(0, "utf8");
    const db = JSON.parse(raw || "{}");
    const n = await replaceAllEvents(db);
    process.stdout.write(
      JSON.stringify({ ok: true, replaced: n, ...summarizeEvents(db) })
    );
    return;
  }
  console.error("usage: events_pg_cli.js load|replace");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

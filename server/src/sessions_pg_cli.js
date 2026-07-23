#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const {
    loadAllSessions,
    replaceAllSessions,
    summarizeSessions,
  } = require("./sessions_pg");

  if (cmd === "load") {
    process.stdout.write(JSON.stringify(await loadAllSessions()));
    return;
  }
  if (cmd === "replace") {
    const raw = fs.readFileSync(0, "utf8");
    const sessions = JSON.parse(raw || "{}");
    const n = await replaceAllSessions(sessions);
    process.stdout.write(
      JSON.stringify({ ok: true, replaced: n, ...summarizeSessions(sessions) })
    );
    return;
  }
  console.error("usage: sessions_pg_cli.js load|replace");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const {
    loadAllEconomy,
    replaceAllEconomy,
    summarizeEconomy,
  } = require("./economy_pg");
  if (cmd === "load") {
    process.stdout.write(JSON.stringify(await loadAllEconomy()));
    return;
  }
  if (cmd === "replace") {
    const db = JSON.parse(fs.readFileSync(0, "utf8") || "{}");
    const n = await replaceAllEconomy(db);
    process.stdout.write(
      JSON.stringify({ ok: true, replaced: n, ...summarizeEconomy(db) })
    );
    return;
  }
  console.error("usage: economy_pg_cli.js load|replace");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

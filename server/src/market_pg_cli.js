#!/usr/bin/env node
const fs = require("fs");

async function main() {
  const cmd = process.argv[2];
  const {
    loadAllMarket,
    replaceAllMarket,
    summarizeMarket,
  } = require("./market_pg");

  if (cmd === "load") {
    process.stdout.write(JSON.stringify(await loadAllMarket()));
    return;
  }
  if (cmd === "replace") {
    const raw = fs.readFileSync(0, "utf8");
    const data = JSON.parse(raw || "{}");
    const n = await replaceAllMarket(data.listings || {}, data.meta || {});
    process.stdout.write(
      JSON.stringify({
        ok: true,
        replaced: n,
        ...summarizeMarket(data.listings || {}, data.meta || {}),
      })
    );
    return;
  }
  console.error("usage: market_pg_cli.js load|replace");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

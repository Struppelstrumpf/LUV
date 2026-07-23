#!/usr/bin/env node
/**
 * Sync CLI used by store.js boot / exit when STORE_BACKEND=postgres.
 *   node pg_store_cli.js load
 *   node pg_store_cli.js save [--snapshot] [--source name]  < stdin JSON
 */
const fs = require("fs");

async function main() {
  const args = process.argv.slice(2);
  const cmd = args[0];
  const {
    loadLive,
    saveLive,
  } = require("./pg_store");

  if (cmd === "load") {
    const payload = await loadLive();
    if (payload == null) {
      process.stdout.write("null");
      return;
    }
    process.stdout.write(JSON.stringify(payload));
    return;
  }

  if (cmd === "save") {
    let alsoSnapshot = false;
    let source = "save_sync";
    for (let i = 1; i < args.length; i++) {
      if (args[i] === "--snapshot") alsoSnapshot = true;
      if (args[i] === "--source" && args[i + 1]) {
        source = args[++i];
      }
    }
    const raw = fs.readFileSync(0, "utf8");
    const db = JSON.parse(raw);
    const r = await saveLive(db, { source, alsoSnapshot });
    process.stdout.write(JSON.stringify({ ok: true, ...r }));
    return;
  }

  console.error("usage: pg_store_cli.js load|save");
  process.exit(2);
}

main().catch((e) => {
  console.error(e?.message || e);
  process.exit(1);
});

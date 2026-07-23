#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const {
  replaceAllSessions,
  verifySessions,
  summarizeSessions,
} = require("./sessions_pg");

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  let sessions = {};
  const payload = await loadLive();
  if (payload?.sessions && typeof payload.sessions === "object") {
    sessions = payload.sessions;
  } else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    sessions = JSON.parse(fs.readFileSync(p, "utf8")).sessions || {};
  }
  const n = await replaceAllSessions(sessions);
  const v = await verifySessions(sessions);
  console.log(
    JSON.stringify(
      { ok: v.ok, replaced: n, summary: summarizeSessions(sessions), verify: v },
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

#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { verifySessions, USE_SESSIONS_TABLE } = require("./sessions_pg");

async function main() {
  let sessions = {};
  const payload = await loadLive();
  if (payload?.sessions) sessions = payload.sessions;
  else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    sessions = JSON.parse(fs.readFileSync(p, "utf8")).sessions || {};
  }
  const v = await verifySessions(sessions);
  console.log(
    JSON.stringify(
      {
        sessionsBackendEnv: process.env.SESSIONS_BACKEND || "blob",
        useTable: USE_SESSIONS_TABLE,
        ...v,
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

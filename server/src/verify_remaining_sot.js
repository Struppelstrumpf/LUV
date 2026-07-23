#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { verifyRooms } = require("./rooms_pg");
const { verifyEconomy } = require("./economy_pg");
const { verifyMarriages } = require("./marriages_pg");
const { verifyShop } = require("./shop_pg");
const { verifyMisc } = require("./misc_pg");

async function loadDb() {
  const payload = await loadLive();
  if (payload) return payload;
  const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

async function main() {
  const db = await loadDb();
  const results = {
    rooms: await verifyRooms(db),
    economy: await verifyEconomy(db),
    marriages: await verifyMarriages(db),
    shop: await verifyShop(db),
    misc: await verifyMisc(db),
  };
  const ok = Object.values(results).every((r) => r.ok);
  console.log(JSON.stringify({ ok, results }, null, 2));
  process.exit(ok ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

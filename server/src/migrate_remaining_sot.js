#!/usr/bin/env node
/**
 * Migrate all remaining blob domains from store_live into domain tables.
 * Does NOT flip *_BACKEND flags.
 */
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { replaceAllRooms, verifyRooms, summarizeRooms } = require("./rooms_pg");
const {
  replaceAllEconomy,
  verifyEconomy,
  summarizeEconomy,
} = require("./economy_pg");
const {
  replaceAllMarriages,
  verifyMarriages,
  summarizeMarriages,
} = require("./marriages_pg");
const { replaceAllShop, verifyShop, summarizeShop } = require("./shop_pg");
const { replaceAllMisc, verifyMisc, summarizeMisc } = require("./misc_pg");

async function loadDb() {
  const payload = await loadLive();
  if (payload) return payload;
  const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  const db = await loadDb();
  const results = {};

  results.rooms = {
    replaced: await replaceAllRooms(db),
    verify: await verifyRooms(db),
    summary: summarizeRooms(db),
  };
  results.economy = {
    replaced: await replaceAllEconomy(db),
    verify: await verifyEconomy(db),
    summary: summarizeEconomy(db),
  };
  results.marriages = {
    replaced: await replaceAllMarriages(db),
    verify: await verifyMarriages(db),
    summary: summarizeMarriages(db),
  };
  results.shop = {
    replaced: await replaceAllShop(db),
    verify: await verifyShop(db),
    summary: summarizeShop(db),
  };
  results.misc = {
    replaced: await replaceAllMisc(db),
    verify: await verifyMisc(db),
    summary: summarizeMisc(db),
  };

  const ok = Object.values(results).every((r) => r.verify && r.verify.ok);
  console.log(JSON.stringify({ ok, results }, null, 2));
  process.exit(ok ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

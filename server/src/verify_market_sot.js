#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const { verifyMarket, USE_MARKET_TABLE } = require("./market_pg");

async function main() {
  let listings = {};
  let meta = {};
  const payload = await loadLive();
  if (payload) {
    listings = payload.marketListings || {};
    meta = payload.marketMeta || {};
  } else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    const db = JSON.parse(fs.readFileSync(p, "utf8"));
    listings = db.marketListings || {};
    meta = db.marketMeta || {};
  }
  const v = await verifyMarket(listings, meta);
  console.log(
    JSON.stringify(
      {
        marketBackendEnv: process.env.MARKET_BACKEND || "blob",
        useTable: USE_MARKET_TABLE,
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

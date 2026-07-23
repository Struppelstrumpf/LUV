#!/usr/bin/env node
const fs = require("fs");
const path = require("path");
const { loadLive } = require("./pg_store");
const {
  replaceAllMarket,
  verifyMarket,
  summarizeMarket,
} = require("./market_pg");

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error("DATABASE_URL missing");
    process.exit(1);
  }
  let listings = {};
  let meta = { priceHistory: {}, saleHistory: {} };
  const payload = await loadLive();
  if (payload) {
    listings = payload.marketListings || {};
    meta = payload.marketMeta || meta;
  } else {
    const p = path.join(process.env.DATA_DIR || "/data", "luv-store.json");
    const db = JSON.parse(fs.readFileSync(p, "utf8"));
    listings = db.marketListings || {};
    meta = db.marketMeta || meta;
  }
  const n = await replaceAllMarket(listings, meta);
  const v = await verifyMarket(listings, meta);
  console.log(
    JSON.stringify(
      {
        ok: v.ok,
        replaced: n,
        summary: summarizeMarket(listings, meta),
        verify: v,
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

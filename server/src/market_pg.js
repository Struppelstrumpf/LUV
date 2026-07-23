/**
 * Phase 3: market_listings + market_meta as source of truth.
 * Env: MARKET_BACKEND=table|blob
 */
const crypto = require("crypto");
const path = require("path");
const { execFileSync } = require("child_process");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const MARKET_BACKEND = String(process.env.MARKET_BACKEND || "blob").toLowerCase();
const USE_MARKET_TABLE = MARKET_BACKEND === "table" || MARKET_BACKEND === "pg";

function contentHash(s) {
  return crypto.createHash("sha256").update(String(s)).digest("hex");
}

function stableStringify(value) {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) {
    return `[${value.map((v) => stableStringify(v)).join(",")}]`;
  }
  const keys = Object.keys(value).sort();
  return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(value[k])}`).join(",")}}`;
}

function summarizeMarket(listingsMap, metaObj) {
  const listings = listingsMap && typeof listingsMap === "object" ? listingsMap : {};
  const ids = Object.keys(listings);
  ids.sort();
  let open = 0;
  let sold = 0;
  for (const id of ids) {
    const st = String(listings[id]?.status || "");
    if (st === "open" || st === "active" || st === "listed") open += 1;
    if (st === "sold") sold += 1;
  }
  const samples = ids.slice(0, 5).map((id) => {
    const e = listings[id] || {};
    return {
      id,
      status: e.status || null,
      sellerId: e.sellerId || null,
      priceCoins: Number(e.priceCoins) || 0,
      kind: e.kind || null,
      itemId: e.itemId || null,
    };
  });
  const meta = metaObj && typeof metaObj === "object" ? metaObj : {};
  return {
    listings: ids.length,
    open,
    sold,
    samples,
    metaHash: contentHash(stableStringify(meta)),
    priceHistoryKeys: Object.keys(meta.priceHistory || {}).length,
    saleHistoryKeys: Object.keys(meta.saleHistory || {}).length,
  };
}

async function withClient(fn) {
  if (!DATABASE_URL) throw new Error("DATABASE_URL missing");
  let pg;
  try {
    pg = require("pg");
  } catch {
    throw new Error("pg package not installed");
  }
  const client = new pg.Client({ connectionString: DATABASE_URL });
  await client.connect();
  try {
    return await fn(client);
  } finally {
    await client.end();
  }
}

async function ensureMarketTables(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS market_listings (
      id TEXT PRIMARY KEY,
      seller_id TEXT,
      status TEXT,
      kind TEXT,
      item_id TEXT,
      price_coins INTEGER NOT NULL DEFAULT 0,
      raw JSONB NOT NULL DEFAULT '{}'::jsonb,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
  await client.query(`
    CREATE TABLE IF NOT EXISTS market_meta (
      id SMALLINT PRIMARY KEY CHECK (id = 1),
      payload JSONB NOT NULL DEFAULT '{}'::jsonb,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}

async function replaceMarketInClient(client, listingsMap, metaObj) {
  await ensureMarketTables(client);
  const listings = listingsMap && typeof listingsMap === "object" ? listingsMap : {};
  const meta =
    metaObj && typeof metaObj === "object"
      ? metaObj
      : { priceHistory: {}, saleHistory: {} };

  await client.query(`DELETE FROM market_listings`);
  let n = 0;
  for (const [id, e] of Object.entries(listings)) {
    if (!e || typeof e !== "object") continue;
    await client.query(
      `INSERT INTO market_listings(
         id, seller_id, status, kind, item_id, price_coins, raw, updated_at
       ) VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb, NOW())`,
      [
        String(id),
        e.sellerId != null ? String(e.sellerId) : null,
        e.status != null ? String(e.status) : null,
        e.kind != null ? String(e.kind) : null,
        e.itemId != null ? String(e.itemId) : null,
        Number(e.priceCoins) || 0,
        JSON.stringify(e),
      ]
    );
    n += 1;
  }

  await client.query(
    `INSERT INTO market_meta(id, payload, updated_at)
     VALUES (1, $1::jsonb, NOW())
     ON CONFLICT (id) DO UPDATE SET
       payload = EXCLUDED.payload,
       updated_at = NOW()`,
    [JSON.stringify(meta)]
  );
  return n;
}

async function replaceAllMarket(listingsMap, metaObj) {
  return withClient(async (client) => {
    await client.query("BEGIN");
    try {
      const n = await replaceMarketInClient(client, listingsMap, metaObj);
      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_market_sot_at', $1)
         ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
        [new Date().toISOString()]
      );
      await client.query("COMMIT");
      return n;
    } catch (e) {
      await client.query("ROLLBACK");
      throw e;
    }
  });
}

async function loadAllMarket() {
  return withClient(async (client) => {
    await ensureMarketTables(client);
    const lr = await client.query(`SELECT id, raw FROM market_listings`);
    const listings = {};
    for (const row of lr.rows) {
      if (row.raw && typeof row.raw === "object") {
        listings[String(row.id)] = row.raw;
      }
    }
    const mr = await client.query(`SELECT payload FROM market_meta WHERE id = 1`);
    const meta =
      mr.rows[0]?.payload && typeof mr.rows[0].payload === "object"
        ? mr.rows[0].payload
        : { priceHistory: {}, saleHistory: {} };
    return { listings, meta };
  });
}

function loadAllMarketSync() {
  const helper = path.join(__dirname, "market_pg_cli.js");
  const out = execFileSync(process.execPath, [helper, "load"], {
    encoding: "utf8",
    maxBuffer: 100 * 1024 * 1024,
    env: process.env,
  }).trim();
  if (!out || out === "null") {
    return { listings: {}, meta: { priceHistory: {}, saleHistory: {} } };
  }
  return JSON.parse(out);
}

async function verifyMarket(listingsMap, metaObj) {
  const memSum = summarizeMarket(listingsMap, metaObj);
  const pg = await loadAllMarket();
  const pgSum = summarizeMarket(pg.listings, pg.meta);
  const ok =
    memSum.listings === pgSum.listings &&
    memSum.open === pgSum.open &&
    memSum.sold === pgSum.sold &&
    memSum.metaHash === pgSum.metaHash &&
    JSON.stringify(memSum.samples) === JSON.stringify(pgSum.samples);
  return { ok, mem: memSum, postgres: pgSum };
}

module.exports = {
  MARKET_BACKEND: USE_MARKET_TABLE ? "table" : "blob",
  USE_MARKET_TABLE,
  summarizeMarket,
  ensureMarketTables,
  replaceMarketInClient,
  replaceAllMarket,
  loadAllMarket,
  loadAllMarketSync,
  verifyMarket,
};

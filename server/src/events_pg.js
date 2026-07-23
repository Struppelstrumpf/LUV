/**
 * Phase 5: events/notices domain keys as SoT.
 * Env: EVENTS_BACKEND=table|blob
 */
const crypto = require("crypto");
const path = require("path");
const { execFileSync } = require("child_process");

const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();
const EVENTS_BACKEND = String(process.env.EVENTS_BACKEND || "blob").toLowerCase();
const USE_EVENTS_TABLE = EVENTS_BACKEND === "table" || EVENTS_BACKEND === "pg";

const DOMAIN_KEYS = [
  "eventsConfig",
  "eventContest",
  "eventLobbies",
  "liveNotice",
  "homeFeed",
  "notifyPhrases",
];

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

function extractFromDb(db) {
  const out = {};
  const src = db && typeof db === "object" ? db : {};
  for (const key of DOMAIN_KEYS) {
    if (Object.prototype.hasOwnProperty.call(src, key)) {
      out[key] = src[key];
    } else if (key === "liveNotice") {
      out[key] = null;
    } else if (key === "homeFeed") {
      out[key] = [];
    } else if (key === "notifyPhrases") {
      out[key] = { phrases: [], version: 1 };
    } else if (key === "eventsConfig") {
      out[key] = { events: [], updatedAt: null };
    } else {
      out[key] = {};
    }
  }
  return out;
}

function applyToDb(dbObj, data) {
  const db = dbObj && typeof dbObj === "object" ? dbObj : {};
  const src = data && typeof data === "object" ? data : {};
  for (const key of DOMAIN_KEYS) {
    if (Object.prototype.hasOwnProperty.call(src, key)) {
      db[key] = src[key];
    }
  }
  return db;
}

function summarizeEvents(dbOrExtract) {
  const data =
    dbOrExtract && typeof dbOrExtract === "object" && !dbOrExtract.eventsConfig
      ? dbOrExtract
      : extractFromDb(dbOrExtract);
  const contest = data.eventContest && typeof data.eventContest === "object"
    ? data.eventContest
    : {};
  let contestEvents = 0;
  let contestEntries = 0;
  for (const bucket of Object.values(contest)) {
    contestEvents += 1;
    if (bucket && Array.isArray(bucket.entries)) {
      contestEntries += bucket.entries.length;
    }
  }
  const cfg = data.eventsConfig && typeof data.eventsConfig === "object"
    ? data.eventsConfig
    : {};
  const eventsCount = Array.isArray(cfg.events) ? cfg.events.length : 0;
  const feed = Array.isArray(data.homeFeed) ? data.homeFeed : [];
  const phrases =
    data.notifyPhrases && Array.isArray(data.notifyPhrases.phrases)
      ? data.notifyPhrases.phrases.length
      : 0;
  return {
    eventsConfigCount: eventsCount,
    contestEvents,
    contestEntries,
    homeFeed: feed.length,
    notifyPhrases: phrases,
    hasLiveNotice: !!(data.liveNotice && typeof data.liveNotice === "object"),
    fingerprint: contentHash(stableStringify(data)),
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

async function ensureEventsTables(client) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS events_domain (
      key TEXT PRIMARY KEY,
      payload JSONB NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}

async function replaceEventsInClient(client, db) {
  await ensureEventsTables(client);
  const data = extractFromDb(db);
  await client.query(`DELETE FROM events_domain`);
  let n = 0;
  for (const key of DOMAIN_KEYS) {
    await client.query(
      `INSERT INTO events_domain(key, payload, updated_at)
       VALUES ($1, $2::jsonb, NOW())`,
      [key, JSON.stringify(data[key] === undefined ? null : data[key])]
    );
    n += 1;
  }
  return n;
}

async function replaceAllEvents(db) {
  return withClient(async (client) => {
    await client.query("BEGIN");
    try {
      const n = await replaceEventsInClient(client, db);
      await client.query(
        `INSERT INTO meta(key, value) VALUES ('last_events_sot_at', $1)
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

async function loadAllEvents() {
  return withClient(async (client) => {
    await ensureEventsTables(client);
    const r = await client.query(`SELECT key, payload FROM events_domain`);
    const out = extractFromDb({});
    for (const row of r.rows) {
      out[String(row.key)] = row.payload;
    }
    return out;
  });
}

function loadAllEventsSync() {
  const helper = path.join(__dirname, "events_pg_cli.js");
  const out = execFileSync(process.execPath, [helper, "load"], {
    encoding: "utf8",
    maxBuffer: 100 * 1024 * 1024,
    env: process.env,
  }).trim();
  if (!out || out === "null") return extractFromDb({});
  return JSON.parse(out);
}

async function verifyEvents(db) {
  const memSum = summarizeEvents(db);
  const pg = await loadAllEvents();
  const pgSum = summarizeEvents(pg);
  const ok =
    memSum.eventsConfigCount === pgSum.eventsConfigCount &&
    memSum.contestEvents === pgSum.contestEvents &&
    memSum.contestEntries === pgSum.contestEntries &&
    memSum.homeFeed === pgSum.homeFeed &&
    memSum.notifyPhrases === pgSum.notifyPhrases &&
    memSum.hasLiveNotice === pgSum.hasLiveNotice &&
    memSum.fingerprint === pgSum.fingerprint;
  return { ok, mem: memSum, postgres: pgSum };
}

module.exports = {
  EVENTS_BACKEND: USE_EVENTS_TABLE ? "table" : "blob",
  USE_EVENTS_TABLE,
  DOMAIN_KEYS,
  extractFromDb,
  applyToDb,
  summarizeEvents,
  ensureEventsTables,
  replaceEventsInClient,
  replaceAllEvents,
  loadAllEvents,
  loadAllEventsSync,
  verifyEvents,
};

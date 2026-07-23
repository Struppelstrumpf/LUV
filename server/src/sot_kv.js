/**
 * Shared helpers for key→JSONB domain SoT modules (events-style).
 */
const crypto = require("crypto");
const path = require("path");
const { execFileSync } = require("child_process");

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

function envUseTable(envName) {
  const v = String(process.env[envName] || "blob").toLowerCase();
  return v === "table" || v === "pg";
}

/**
 * @param {object} opts
 * @param {string} opts.envName e.g. ROOMS_BACKEND
 * @param {string} opts.table e.g. rooms_domain
 * @param {string[]} opts.keys domain keys on getDb()
 * @param {(key:string)=>any} opts.defaultForKey
 * @param {(data:object)=>object} [opts.summarize]
 * @param {string} opts.cliFile absolute or __dirname-relative cli helper name
 * @param {string} opts.metaStampKey e.g. last_rooms_sot_at
 */
function createKvDomain(opts) {
  const {
    envName,
    table,
    keys,
    defaultForKey,
    summarize: summarizeFn,
    cliFile,
    metaStampKey,
  } = opts;
  const USE_TABLE = envUseTable(envName);
  const DATABASE_URL = String(process.env.DATABASE_URL || "").trim();

  function extractFromDb(db) {
    const out = {};
    const src = db && typeof db === "object" ? db : {};
    for (const key of keys) {
      if (Object.prototype.hasOwnProperty.call(src, key)) {
        out[key] = src[key];
      } else {
        out[key] = defaultForKey(key);
      }
    }
    return out;
  }

  function applyToDb(dbObj, data) {
    const db = dbObj && typeof dbObj === "object" ? dbObj : {};
    const src = data && typeof data === "object" ? data : {};
    for (const key of keys) {
      if (Object.prototype.hasOwnProperty.call(src, key)) {
        db[key] = src[key];
      }
    }
    return db;
  }

  function summarize(dbOrExtract) {
    // Always project to domain keys — full getDb() also has these keys.
    const data = extractFromDb(dbOrExtract);
    if (typeof summarizeFn === "function") {
      return {
        ...summarizeFn(data),
        fingerprint: contentHash(stableStringify(data)),
      };
    }
    return { fingerprint: contentHash(stableStringify(data)) };
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

  async function ensureTables(client) {
    await client.query(`
      CREATE TABLE IF NOT EXISTS ${table} (
        key TEXT PRIMARY KEY,
        payload JSONB NOT NULL,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
    `);
  }

  async function replaceInClient(client, db) {
    await ensureTables(client);
    const data = extractFromDb(db);
    await client.query(`DELETE FROM ${table}`);
    let n = 0;
    for (const key of keys) {
      await client.query(
        `INSERT INTO ${table}(key, payload, updated_at)
         VALUES ($1, $2::jsonb, NOW())`,
        [key, JSON.stringify(data[key] === undefined ? null : data[key])]
      );
      n += 1;
    }
    return n;
  }

  async function replaceAll(db) {
    return withClient(async (client) => {
      await client.query("BEGIN");
      try {
        const n = await replaceInClient(client, db);
        if (metaStampKey) {
          await client.query(
            `INSERT INTO meta(key, value) VALUES ($1, $2)
             ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()`,
            [metaStampKey, new Date().toISOString()]
          );
        }
        await client.query("COMMIT");
        return n;
      } catch (e) {
        await client.query("ROLLBACK");
        throw e;
      }
    });
  }

  async function loadAll() {
    return withClient(async (client) => {
      await ensureTables(client);
      const r = await client.query(`SELECT key, payload FROM ${table}`);
      const out = extractFromDb({});
      for (const row of r.rows) {
        out[String(row.key)] = row.payload;
      }
      return out;
    });
  }

  function loadAllSync() {
    const helper = path.isAbsolute(cliFile)
      ? cliFile
      : path.join(__dirname, cliFile);
    const out = execFileSync(process.execPath, [helper, "load"], {
      encoding: "utf8",
      maxBuffer: 200 * 1024 * 1024,
      env: process.env,
    }).trim();
    if (!out || out === "null") return extractFromDb({});
    return JSON.parse(out);
  }

  async function verify(db) {
    // JSON round-trip matches JSONB storage (drops undefined, normalizes).
    const memNorm = JSON.parse(JSON.stringify(extractFromDb(db)));
    const memSum = summarize(memNorm);
    const pg = await loadAll();
    const pgSum = summarize(pg);
    return {
      ok: memSum.fingerprint === pgSum.fingerprint,
      mem: memSum,
      postgres: pgSum,
    };
  }

  function rowCountSync(data) {
    // helper for empty-table safety checks
    let n = 0;
    for (const key of keys) {
      const v = data[key];
      if (v == null) continue;
      if (Array.isArray(v)) n += v.length;
      else if (typeof v === "object") n += Object.keys(v).length;
      else n += 1;
    }
    return n;
  }

  return {
    envName,
    USE_TABLE,
    BACKEND: USE_TABLE ? "table" : "blob",
    DOMAIN_KEYS: keys,
    extractFromDb,
    applyToDb,
    summarize,
    ensureTables,
    replaceInClient,
    replaceAll,
    loadAll,
    loadAllSync,
    verify,
    rowCountSync,
    withClient,
  };
}

module.exports = {
  contentHash,
  stableStringify,
  envUseTable,
  createKvDomain,
};

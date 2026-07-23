/**
 * Durable append-only ledger for real-money (Play) purchases.
 * Survives store_live / pg_dump restore gaps: replayMissingCredits() on boot.
 *
 * Primary: /data/iap_ledger/play_purchases.jsonl (sync append)
 * Secondary: Postgres table iap_payments (best-effort)
 */
const fs = require("fs");
const path = require("path");

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "..", "data");
const LEDGER_DIR = path.join(DATA_DIR, "iap_ledger");
const LEDGER_FILE = path.join(LEDGER_DIR, "play_purchases.jsonl");

function ensureDir() {
  fs.mkdirSync(LEDGER_DIR, { recursive: true });
}

function ledgerPath() {
  return LEDGER_FILE;
}

/** Sync append — call immediately after crediting a Play purchase. */
function appendCreditedPurchase(rec) {
  if (!rec || !rec.id) throw new Error("iap_ledger: missing id");
  ensureDir();
  const row = {
    id: String(rec.id),
    provider: String(rec.provider || "google_play"),
    userId: String(rec.userId || ""),
    packId: rec.packId != null ? String(rec.packId) : null,
    coins: Math.max(0, Math.floor(Number(rec.coins) || 0)),
    orderId: rec.orderId != null ? String(rec.orderId) : null,
    purchaseToken: rec.purchaseToken != null ? String(rec.purchaseToken) : null,
    ip: rec.ip != null ? String(rec.ip) : "",
    status: "paid",
    credited: true,
    createdAt: Number(rec.createdAt) || Date.now(),
    appendedAt: Date.now(),
  };
  fs.appendFileSync(LEDGER_FILE, JSON.stringify(row) + "\n", "utf8");
  // Best-effort PG mirror (never block / throw out of purchase path)
  setImmediate(() => {
    upsertPg(row).catch((e) =>
      console.warn("[iap_ledger] pg upsert failed", e?.message || e)
    );
  });
  return row;
}

async function upsertPg(row) {
  const url = String(process.env.DATABASE_URL || "").trim();
  if (!url) return;
  let pg;
  try {
    pg = require("pg");
  } catch {
    return;
  }
  const client = new pg.Client({ connectionString: url });
  await client.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS iap_payments (
        id TEXT PRIMARY KEY,
        provider TEXT NOT NULL,
        user_id TEXT NOT NULL,
        pack_id TEXT,
        coins INTEGER NOT NULL DEFAULT 0,
        order_id TEXT,
        purchase_token TEXT,
        credited BOOLEAN NOT NULL DEFAULT TRUE,
        raw JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        appended_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )
    `);
    await client.query(
      `INSERT INTO iap_payments(
         id, provider, user_id, pack_id, coins, order_id, purchase_token,
         credited, raw, created_at, appended_at
       ) VALUES ($1,$2,$3,$4,$5,$6,$7,TRUE,$8::jsonb, to_timestamp($9/1000.0), NOW())
       ON CONFLICT (id) DO UPDATE SET
         credited = TRUE,
         raw = EXCLUDED.raw,
         appended_at = NOW()`,
      [
        row.id,
        row.provider,
        row.userId,
        row.packId,
        row.coins,
        row.orderId,
        row.purchaseToken,
        JSON.stringify(row),
        row.createdAt,
      ]
    );
  } finally {
    await client.end();
  }
}

function readAllRecords() {
  if (!fs.existsSync(LEDGER_FILE)) return [];
  const text = fs.readFileSync(LEDGER_FILE, "utf8");
  const byId = new Map();
  for (const line of text.split("\n")) {
    const t = line.trim();
    if (!t) continue;
    try {
      const row = JSON.parse(t);
      if (row && row.id) byId.set(String(row.id), row);
    } catch {
      /* skip bad line */
    }
  }
  return [...byId.values()];
}

/**
 * After restoring an older DB dump: re-apply credited purchases missing from db.payments.
 * Idempotent via payment id.
 * @param {object} db
 * @param {(userId:string, coins:number, paymentId:string) => void} [creditFn] e.g. applyLedger
 */
function replayMissingCredits(db, creditFn) {
  if (!db || typeof db !== "object") return { replayed: 0, skipped: 0, errors: 0 };
  if (!db.payments || typeof db.payments !== "object") db.payments = {};
  if (!db.users || typeof db.users !== "object") db.users = {};

  let replayed = 0;
  let skipped = 0;
  let errors = 0;
  for (const row of readAllRecords()) {
    try {
      if (!row.credited) {
        skipped += 1;
        continue;
      }
      const id = String(row.id);
      if (db.payments[id]?.credited) {
        skipped += 1;
        continue;
      }
      const user = db.users[row.userId];
      if (!user) {
        // Do not mark credited — next boot can retry once the user exists again.
        console.warn("[iap_ledger] replay skip unknown user", row.userId, id);
        skipped += 1;
        continue;
      }
      const coins = Math.max(0, Math.floor(Number(row.coins) || 0));
      db.payments[id] = {
        id,
        provider: row.provider || "google_play",
        userId: row.userId,
        packId: row.packId,
        coins,
        quantity: 1,
        orderId: row.orderId || null,
        purchaseToken: row.purchaseToken || null,
        ip: row.ip || "",
        status: "paid",
        createdAt: row.createdAt || Date.now(),
        credited: true,
        replayedAt: Date.now(),
      };
      if (coins > 0) {
        if (typeof creditFn === "function") {
          creditFn(row.userId, coins, id);
        } else {
          user.paidCoins = Math.max(0, Number(user.paidCoins) || 0) + coins;
          user.dailyBalance = Math.max(0, Number(user.dailyBalance) || 0);
          user.coins = user.paidCoins + user.dailyBalance;
        }
      }
      replayed += 1;
    } catch (err) {
      errors += 1;
      console.error("[iap_ledger] replay entry failed", err?.message || err);
    }
  }
  if (replayed > 0 || errors > 0) {
    console.log(`[iap_ledger] replayed ${replayed} missing purchase(s), skipped=${skipped}, errors=${errors}`);
  }
  return { replayed, skipped, errors };
}

function copyLedgerTo(destPath) {
  ensureDir();
  if (!fs.existsSync(LEDGER_FILE)) {
    fs.writeFileSync(destPath, "", "utf8");
    return { ok: true, bytes: 0 };
  }
  fs.copyFileSync(LEDGER_FILE, destPath);
  return { ok: true, bytes: fs.statSync(destPath).size };
}

module.exports = {
  LEDGER_DIR,
  LEDGER_FILE,
  ledgerPath,
  appendCreditedPurchase,
  readAllRecords,
  replayMissingCredits,
  copyLedgerTo,
};

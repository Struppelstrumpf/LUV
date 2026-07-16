const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "..", "data");
const DATA_FILE = path.join(DATA_DIR, "luv-store.json");

const DEFAULT = {
  users: {},
  sessions: {},
  vouchers: {},
  redeems: {},
  payments: {},
  ledger: [],
};

function ensureDir() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function load() {
  ensureDir();
  if (!fs.existsSync(DATA_FILE)) {
    save(DEFAULT);
    return structuredClone(DEFAULT);
  }
  try {
    const raw = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
    return {
      users: raw.users || {},
      sessions: raw.sessions || {},
      vouchers: raw.vouchers || {},
      redeems: raw.redeems || {},
      payments: raw.payments || {},
      ledger: Array.isArray(raw.ledger) ? raw.ledger : [],
    };
  } catch {
    return structuredClone(DEFAULT);
  }
}

let db = load();
let writeTimer = null;

function save(next = db) {
  ensureDir();
  const tmp = DATA_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(next, null, 0), "utf8");
  fs.renameSync(tmp, DATA_FILE);
  db = next;
}

function scheduleSave() {
  if (writeTimer) return;
  writeTimer = setTimeout(() => {
    writeTimer = null;
    save(db);
  }, 200);
}

function todayKey(tzOffsetMin = 0) {
  // Europe/Berlin approx via env; default UTC date is fine for fairness globally
  const d = new Date();
  if (process.env.TZ_OFFSET_MINUTES) {
    d.setMinutes(d.getMinutes() + Number(process.env.TZ_OFFSET_MINUTES));
  }
  void tzOffsetMin;
  return d.toISOString().slice(0, 10);
}

function hashSecret(secret) {
  return crypto.createHash("sha256").update(String(secret)).digest("hex");
}

function newId(prefix = "u") {
  return `${prefix}_${crypto.randomBytes(12).toString("hex")}`;
}

function getDb() {
  return db;
}

module.exports = {
  getDb,
  save,
  scheduleSave,
  todayKey,
  hashSecret,
  newId,
  DATA_DIR,
};

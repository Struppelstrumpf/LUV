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
  rooms: {},
  canvasMemories: {},
  publicCanvases: {},
  publicReports: {},
  peerReports: {},
  helpMessages: {},
  liveNotice: null,
  marketListings: {},
  marketMeta: { priceHistory: {} },
  marriages: {},
  guestbookReports: [],
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
    // Bekannte Felder normalisieren — unbekannte Top-Level-Keys behalten
    // (sonst gehen z. B. marriages bei jedem Restart verloren).
    return {
      ...raw,
      users: raw.users || {},
      sessions: raw.sessions || {},
      vouchers: raw.vouchers || {},
      redeems: raw.redeems || {},
      payments: raw.payments || {},
      ledger: Array.isArray(raw.ledger) ? raw.ledger : [],
      rooms: raw.rooms && typeof raw.rooms === "object" ? raw.rooms : {},
      canvasMemories:
        raw.canvasMemories && typeof raw.canvasMemories === "object"
          ? raw.canvasMemories
          : {},
      publicCanvases:
        raw.publicCanvases && typeof raw.publicCanvases === "object"
          ? raw.publicCanvases
          : {},
      publicReports:
        raw.publicReports && typeof raw.publicReports === "object"
          ? raw.publicReports
          : {},
      peerReports:
        raw.peerReports && typeof raw.peerReports === "object" ? raw.peerReports : {},
      helpMessages:
        raw.helpMessages && typeof raw.helpMessages === "object"
          ? raw.helpMessages
          : {},
      liveNotice:
        raw.liveNotice && typeof raw.liveNotice === "object" ? raw.liveNotice : null,
      marketListings:
        raw.marketListings && typeof raw.marketListings === "object"
          ? raw.marketListings
          : {},
      marketMeta:
        raw.marketMeta && typeof raw.marketMeta === "object"
          ? raw.marketMeta
          : { priceHistory: {} },
      marriages:
        raw.marriages && typeof raw.marriages === "object" ? raw.marriages : {},
      guestbookReports: Array.isArray(raw.guestbookReports)
        ? raw.guestbookReports
        : [],
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

/** Sofort speichern — für Coin-/Item-Transaktionen (Markt, Tip, …). */
function flushSave() {
  if (writeTimer) {
    clearTimeout(writeTimer);
    writeTimer = null;
  }
  save(db);
}

function todayKey(tzOffsetMin = 0) {
  // Tagesgrenze 0:00 Europe/Berlin (MEZ/MESZ)
  void tzOffsetMin;
  try {
    return new Intl.DateTimeFormat("en-CA", {
      timeZone: "Europe/Berlin",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  } catch {
    // Fallback: Europe/Berlin ≈ UTC+1/+2 — nie reines UTC (Mitternacht-Fehler)
    const d = new Date();
    const offsetMin = Number(process.env.TZ_OFFSET_MINUTES);
    if (Number.isFinite(offsetMin)) {
      d.setMinutes(d.getMinutes() + offsetMin);
    } else {
      // Grobe MESZ/MEZ-Näherung ohne Intl
      const m = d.getUTCMonth();
      const utcH = d.getUTCHours();
      const berlinOffset = m >= 2 && m <= 9 ? 2 : 1; // grob Sommer/Winter
      d.setUTCHours(utcH + berlinOffset);
    }
    const y = d.getUTCFullYear();
    const mo = String(d.getUTCMonth() + 1).padStart(2, "0");
    const day = String(d.getUTCDate()).padStart(2, "0");
    return `${y}-${mo}-${day}`;
  }
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
  flushSave,
  todayKey,
  hashSecret,
  newId,
  DATA_DIR,
};

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
  bugReports: {},
  liveNotice: null,
  maintenance: {
    nightKey: null,
    joke: "",
    jobDone: false,
    jobStartedAt: null,
    jobFinishedAt: null,
    lastReportId: null,
  },
  maintenanceReports: {},
  marketListings: {},
  marketMeta: { priceHistory: {} },
  economySettings: { achievementDailyCap: 12 },
  itemTradeFlags: {},
  itemDisplayLabels: {},
  achievementDefs: {},
  notifyPhrases: { phrases: [], version: 1 },
  marriages: {},
  guestbookReports: [],
  shopCatalog: { items: {}, version: 1 },
  roomLayouts: {},
};

function ensureDir() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function load() {
  ensureDir();
  if (!fs.existsSync(DATA_FILE)) {
    // Sync-Bootstrap wie zuvor — leere DB anlegen
    const empty = structuredClone(DEFAULT);
    const tmp = DATA_FILE + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(empty, null, 0), "utf8");
    fs.renameSync(tmp, DATA_FILE);
    return empty;
  }
  try {
    const raw = JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
    // WICHTIG: ...raw behalten (shopStats, staffAudit, …) + kritische Keys normalisieren
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
      bugReports:
        raw.bugReports && typeof raw.bugReports === "object" ? raw.bugReports : {},
      liveNotice:
        raw.liveNotice && typeof raw.liveNotice === "object" ? raw.liveNotice : null,
      maintenance:
        raw.maintenance && typeof raw.maintenance === "object"
          ? raw.maintenance
          : {
              nightKey: null,
              joke: "",
              jobDone: false,
              jobStartedAt: null,
              jobFinishedAt: null,
              lastReportId: null,
            },
      maintenanceReports:
        raw.maintenanceReports && typeof raw.maintenanceReports === "object"
          ? raw.maintenanceReports
          : {},
      marketListings:
        raw.marketListings && typeof raw.marketListings === "object"
          ? raw.marketListings
          : {},
      marketMeta:
        raw.marketMeta && typeof raw.marketMeta === "object"
          ? raw.marketMeta
          : { priceHistory: {} },
      economySettings:
        raw.economySettings && typeof raw.economySettings === "object"
          ? raw.economySettings
          : { achievementDailyCap: 12 },
      itemTradeFlags:
        raw.itemTradeFlags && typeof raw.itemTradeFlags === "object"
          ? raw.itemTradeFlags
          : {},
      itemDisplayLabels:
        raw.itemDisplayLabels && typeof raw.itemDisplayLabels === "object"
          ? raw.itemDisplayLabels
          : {},
      achievementDefs:
        raw.achievementDefs && typeof raw.achievementDefs === "object"
          ? raw.achievementDefs
          : {},
      notifyPhrases:
        raw.notifyPhrases && typeof raw.notifyPhrases === "object"
          ? raw.notifyPhrases
          : { phrases: [], version: 1 },
      marriages:
        raw.marriages && typeof raw.marriages === "object" ? raw.marriages : {},
      guestbookReports: Array.isArray(raw.guestbookReports)
        ? raw.guestbookReports
        : [],
      shopCatalog:
        raw.shopCatalog && typeof raw.shopCatalog === "object"
          ? raw.shopCatalog
          : { items: {}, version: 1 },
      roomLayouts:
        raw.roomLayouts && typeof raw.roomLayouts === "object"
          ? raw.roomLayouts
          : {},
    };
  } catch {
    return structuredClone(DEFAULT);
  }
}

let db = load();
let writeTimer = null;
/** Serialize async writes so HTTP handlers don't block on full-DB sync I/O. */
let writeChain = Promise.resolve();
let writeQueued = false;

function ensureDirExists() {
  ensureDir();
}

/** Sync write — nur Shutdown / Migration. */
function save(next = db) {
  ensureDirExists();
  if (next !== db) db = next;
  const tmp = DATA_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(db, null, 0), "utf8");
  fs.renameSync(tmp, DATA_FILE);
}

function enqueueSave() {
  if (writeQueued) return;
  writeQueued = true;
  writeChain = writeChain
    .then(
      () =>
        new Promise((resolve) => {
          setImmediate(() => {
            writeQueued = false;
            let payload;
            try {
              payload = JSON.stringify(db, null, 0);
            } catch (err) {
              console.error("[store] stringify failed", err?.message || err);
              resolve();
              return;
            }
            const tmp = DATA_FILE + ".tmp";
            ensureDirExists();
            fs.writeFile(tmp, payload, "utf8", (err) => {
              if (err) {
                console.error("[store] write failed", err?.message || err);
                resolve();
                return;
              }
              fs.rename(tmp, DATA_FILE, (err2) => {
                if (err2) {
                  console.error("[store] rename failed", err2?.message || err2);
                }
                resolve();
              });
            });
          });
        })
    )
    .catch((err) => {
      console.error("[store] write chain", err?.message || err);
    });
}

function scheduleSave() {
  if (writeTimer) return;
  writeTimer = setTimeout(() => {
    writeTimer = null;
    enqueueSave();
  }, 200);
}

/**
 * Persistenz anstoßen ohne den Event-Loop zu blockieren.
 * HTTP-Antworten können sofort danach gesendet werden.
 */
function flushSave() {
  if (writeTimer) {
    clearTimeout(writeTimer);
    writeTimer = null;
  }
  enqueueSave();
}

function flushSaveSync() {
  if (writeTimer) {
    clearTimeout(writeTimer);
    writeTimer = null;
  }
  save(db);
}

function todayKey(tzOffsetMin = 0) {
  void tzOffsetMin;
  try {
    return new Intl.DateTimeFormat("en-CA", {
      timeZone: "Europe/Berlin",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  } catch {
    const d = new Date();
    const offsetMin = Number(process.env.TZ_OFFSET_MINUTES);
    if (Number.isFinite(offsetMin)) {
      d.setMinutes(d.getMinutes() + offsetMin);
    } else {
      const m = d.getUTCMonth();
      const utcH = d.getUTCHours();
      const berlinOffset = m >= 2 && m <= 9 ? 2 : 1;
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

function onProcessExit() {
  try {
    flushSaveSync();
  } catch (err) {
    console.error("[store] exit flush failed", err?.message || err);
  }
}

process.once("SIGINT", () => {
  onProcessExit();
  process.exit(0);
});
process.once("SIGTERM", () => {
  onProcessExit();
  process.exit(0);
});

module.exports = {
  getDb,
  save,
  scheduleSave,
  flushSave,
  flushSaveSync,
  todayKey,
  hashSecret,
  newId,
  DATA_DIR,
  DATA_FILE,
};

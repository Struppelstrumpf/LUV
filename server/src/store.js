const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

// json = file primary | postgres = store_live primary + JSON hot mirror
const STORE_BACKEND = String(process.env.STORE_BACKEND || "json").toLowerCase();
const USE_PG = STORE_BACKEND === "postgres" || STORE_BACKEND === "pg";
const USERS_BACKEND = String(process.env.USERS_BACKEND || "blob").toLowerCase();
const USE_USERS_TABLE = USERS_BACKEND === "table" || USERS_BACKEND === "pg";

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
  homeFeed: [],
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

function normalize(raw) {
  if (!raw || typeof raw !== "object") return structuredClone(DEFAULT);
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
    homeFeed: Array.isArray(raw.homeFeed) ? raw.homeFeed : [],
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
}

function loadFromFile() {
  ensureDir();
  if (!fs.existsSync(DATA_FILE)) {
    const empty = structuredClone(DEFAULT);
    const tmp = DATA_FILE + ".tmp";
    fs.writeFileSync(tmp, JSON.stringify(empty, null, 0), "utf8");
    fs.renameSync(tmp, DATA_FILE);
    return empty;
  }
  try {
    return normalize(JSON.parse(fs.readFileSync(DATA_FILE, "utf8")));
  } catch {
    return structuredClone(DEFAULT);
  }
}

function hydrateUsersFromTable(dbObj) {
  if (!USE_USERS_TABLE || !process.env.DATABASE_URL) return dbObj;
  try {
    const { loadAllUsersSync } = require("./users_pg");
    const fromTable = loadAllUsersSync();
    const n = Object.keys(fromTable || {}).length;
    if (n === 0) {
      console.warn(
        "[store] USERS_BACKEND=table but users table empty — keeping blob users"
      );
      return dbObj;
    }
    dbObj.users = fromTable;
    console.log(`[store] users SoT=table (loaded ${n} users)`);
  } catch (err) {
    console.error(
      "[store] users table load failed — keeping blob users:",
      err?.message || err
    );
  }
  return dbObj;
}

function load() {
  if (!USE_PG) {
    const fromFile = loadFromFile();
    return hydrateUsersFromTable(fromFile);
  }
  if (!process.env.DATABASE_URL) {
    console.error(
      "[store] STORE_BACKEND=postgres but DATABASE_URL missing — falling back to JSON file"
    );
    return loadFromFile();
  }
  try {
    const { loadLiveSync, saveLiveSync, writeJsonMirror } = require("./pg_store");
    let payload = loadLiveSync();
    if (payload == null) {
      console.warn(
        "[store] store_live empty — bootstrapping from JSON file into Postgres"
      );
      const fromFile = loadFromFile();
      saveLiveSync(fromFile, { source: "bootstrap_from_json", alsoSnapshot: true });
      writeJsonMirror(DATA_FILE, fromFile);
      console.log("[store] primary=postgres (bootstrapped from JSON)");
      return hydrateUsersFromTable(normalize(fromFile));
    }
    const dbObj = hydrateUsersFromTable(normalize(payload));
    try {
      writeJsonMirror(DATA_FILE, dbObj);
    } catch (e) {
      console.warn("[store] json mirror on boot failed", e?.message || e);
    }
    console.log("[store] primary=postgres (loaded store_live)");
    return dbObj;
  } catch (err) {
    console.error(
      "[store] postgres load failed — falling back to JSON file:",
      err?.message || err
    );
    return hydrateUsersFromTable(loadFromFile());
  }
}

let db = load();
let writeTimer = null;
/** Serialize async writes so HTTP handlers don't block on full-DB sync I/O. */
let writeChain = Promise.resolve();
let writeQueued = false;
let pgSaveFailures = 0;

function ensureDirExists() {
  ensureDir();
}

function saveJsonFileSync() {
  ensureDirExists();
  const tmp = DATA_FILE + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(db, null, 0), "utf8");
  fs.renameSync(tmp, DATA_FILE);
}

/** Sync write — Shutdown / Migration. */
function save(next = db) {
  if (next !== db) db = next;
  if (USE_PG && process.env.DATABASE_URL) {
    try {
      const { saveLiveSync, writeJsonMirror } = require("./pg_store");
      saveLiveSync(db, { source: "flush_sync" });
      writeJsonMirror(DATA_FILE, db);
      return;
    } catch (err) {
      console.error("[store] pg sync save failed", err?.message || err);
      // Still mirror JSON so rollback data exists
      try {
        saveJsonFileSync();
      } catch (e2) {
        console.error("[store] json fallback save failed", e2?.message || e2);
      }
      throw err;
    }
  }
  saveJsonFileSync();
}

function enqueueSave() {
  if (writeQueued) return;
  writeQueued = true;
  writeChain = writeChain
    .then(
      () =>
        new Promise((resolve) => {
          setImmediate(async () => {
            writeQueued = false;
            let payload;
            try {
              payload = JSON.stringify(db, null, 0);
            } catch (err) {
              console.error("[store] stringify failed", err?.message || err);
              resolve();
              return;
            }

            if (USE_PG && process.env.DATABASE_URL) {
              try {
                const { saveLive, writeJsonMirrorAsync } = require("./pg_store");
                const parsed = JSON.parse(payload);
                await saveLive(parsed, { source: "enqueue_save", alsoSnapshot: false });
                pgSaveFailures = 0;
                await writeJsonMirrorAsync(DATA_FILE, payload);
                // Periodic history snapshot via pg_dual throttle
                try {
                  const { maybeDualWrite } = require("./pg_dual");
                  setImmediate(() => {
                    maybeDualWrite(DATA_FILE, { fromPrimary: true }).catch(() => {});
                  });
                } catch {
                  /* optional */
                }
              } catch (err) {
                pgSaveFailures += 1;
                console.error(
                  "[store] postgres save failed (#" + pgSaveFailures + ")",
                  err?.message || err
                );
                // Always keep JSON mirror current for emergency rollback
                try {
                  const { writeJsonMirrorAsync } = require("./pg_store");
                  await writeJsonMirrorAsync(DATA_FILE, payload);
                } catch {
                  /* ignore */
                }
              }
              resolve();
              return;
            }

            // JSON primary
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
                } else {
                  try {
                    const { maybeDualWrite } = require("./pg_dual");
                    setImmediate(() => {
                      maybeDualWrite(DATA_FILE).catch(() => {});
                    });
                  } catch {
                    /* optional */
                  }
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
  STORE_BACKEND: USE_PG ? "postgres" : "json",
  USERS_BACKEND: USE_USERS_TABLE ? "table" : "blob",
};

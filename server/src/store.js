const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

// json = file primary | postgres = store_live primary + JSON hot mirror
const STORE_BACKEND = String(process.env.STORE_BACKEND || "json").toLowerCase();
const USE_PG = STORE_BACKEND === "postgres" || STORE_BACKEND === "pg";
const USERS_BACKEND = String(process.env.USERS_BACKEND || "blob").toLowerCase();
const USE_USERS_TABLE = USERS_BACKEND === "table" || USERS_BACKEND === "pg";
const SESSIONS_BACKEND = String(process.env.SESSIONS_BACKEND || "blob").toLowerCase();
const USE_SESSIONS_TABLE =
  SESSIONS_BACKEND === "table" || SESSIONS_BACKEND === "pg";
const MARKET_BACKEND = String(process.env.MARKET_BACKEND || "blob").toLowerCase();
const USE_MARKET_TABLE = MARKET_BACKEND === "table" || MARKET_BACKEND === "pg";
const FRIENDS_BACKEND = String(process.env.FRIENDS_BACKEND || "blob").toLowerCase();
const USE_FRIENDS_TABLE =
  FRIENDS_BACKEND === "table" || FRIENDS_BACKEND === "pg";
const EVENTS_BACKEND = String(process.env.EVENTS_BACKEND || "blob").toLowerCase();
const USE_EVENTS_TABLE = EVENTS_BACKEND === "table" || EVENTS_BACKEND === "pg";

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

function hydrateSessionsFromTable(dbObj) {
  if (!USE_SESSIONS_TABLE || !process.env.DATABASE_URL) return dbObj;
  try {
    const { loadAllSessionsSync } = require("./sessions_pg");
    const fromTable = loadAllSessionsSync();
    const n = Object.keys(fromTable || {}).length;
    dbObj.sessions = fromTable || {};
    console.log(`[store] sessions SoT=table (loaded ${n} sessions)`);
  } catch (err) {
    console.error(
      "[store] sessions table load failed — keeping blob sessions:",
      err?.message || err
    );
  }
  return dbObj;
}

function hydrateMarketFromTable(dbObj) {
  if (!USE_MARKET_TABLE || !process.env.DATABASE_URL) return dbObj;
  try {
    const { loadAllMarketSync } = require("./market_pg");
    const fromTable = loadAllMarketSync();
    const n = Object.keys(fromTable.listings || {}).length;
    dbObj.marketListings = fromTable.listings || {};
    dbObj.marketMeta =
      fromTable.meta && typeof fromTable.meta === "object"
        ? fromTable.meta
        : { priceHistory: {}, saleHistory: {} };
    console.log(`[store] market SoT=table (loaded ${n} listings)`);
  } catch (err) {
    console.error(
      "[store] market table load failed — keeping blob market:",
      err?.message || err
    );
  }
  return dbObj;
}

function hydrateFriendsFromTable(dbObj) {
  if (!USE_FRIENDS_TABLE || !process.env.DATABASE_URL) return dbObj;
  try {
    const { hydrateUsersFriends, loadAllFriendsDataSync, extractFromUsers } =
      require("./friends_pg");
    const data = loadAllFriendsDataSync();
    const embedded = extractFromUsers(dbObj.users || {});
    const tableEmpty =
      (data.friendships || []).length === 0 &&
      (data.requests || []).length === 0;
    if (
      tableEmpty &&
      (embedded.friendships.length > 0 || embedded.requests.length > 0)
    ) {
      console.warn(
        "[store] FRIENDS_BACKEND=table but tables empty — keeping blob friends"
      );
      return dbObj;
    }
    const counts = hydrateUsersFriends(dbObj.users || {});
    console.log(
      `[store] friends SoT=table (friendships=${counts.friendships}, requests=${counts.requests})`
    );
  } catch (err) {
    console.error(
      "[store] friends table load failed — keeping blob friends:",
      err?.message || err
    );
  }
  return dbObj;
}

function hydrateEventsFromTable(dbObj) {
  if (!USE_EVENTS_TABLE || !process.env.DATABASE_URL) return dbObj;
  try {
    const { loadAllEventsSync, applyToDb, summarizeEvents } = require("./events_pg");
    const fromTable = loadAllEventsSync();
    const sum = summarizeEvents(fromTable);
    // Empty table + no config → keep blob (pre-migrate safety)
    if (
      sum.eventsConfigCount === 0 &&
      sum.contestEvents === 0 &&
      sum.homeFeed === 0 &&
      sum.notifyPhrases === 0 &&
      !sum.hasLiveNotice
    ) {
      const blobSum = summarizeEvents(dbObj);
      if (
        blobSum.eventsConfigCount > 0 ||
        blobSum.contestEvents > 0 ||
        blobSum.homeFeed > 0 ||
        blobSum.notifyPhrases > 0 ||
        blobSum.hasLiveNotice
      ) {
        console.warn(
          "[store] EVENTS_BACKEND=table but domain empty — keeping blob events"
        );
        return dbObj;
      }
    }
    applyToDb(dbObj, fromTable);
    console.log(
      `[store] events SoT=table (events=${sum.eventsConfigCount}, contestEntries=${sum.contestEntries}, feed=${sum.homeFeed})`
    );
  } catch (err) {
    console.error(
      "[store] events table load failed — keeping blob events:",
      err?.message || err
    );
  }
  return dbObj;
}

function hydrateDomainTables(dbObj) {
  return hydrateEventsFromTable(
    hydrateFriendsFromTable(
      hydrateMarketFromTable(
        hydrateSessionsFromTable(hydrateUsersFromTable(dbObj))
      )
    )
  );
}

function load() {
  if (!USE_PG) {
    const fromFile = loadFromFile();
    return hydrateDomainTables(fromFile);
  }
  if (!process.env.DATABASE_URL) {
    console.error(
      "[store] STORE_BACKEND=postgres but DATABASE_URL missing — cannot start"
    );
    process.exit(1);
  }
  try {
    const { loadLiveSync, saveLiveSync } = require("./pg_store");
    let payload = loadLiveSync();
    if (payload == null) {
      console.warn(
        "[store] store_live empty — seeding empty DEFAULT into Postgres"
      );
      const empty = structuredClone(DEFAULT);
      saveLiveSync(empty, { source: "bootstrap_empty", alsoSnapshot: true });
      console.log("[store] primary=postgres (seeded empty)");
      return hydrateDomainTables(normalize(empty));
    }
    const dbObj = hydrateDomainTables(normalize(payload));
    console.log("[store] primary=postgres (loaded store_live, no JSON mirror)");
    return dbObj;
  } catch (err) {
    console.error(
      "[store] postgres load failed — refusing JSON fallback:",
      err?.message || err
    );
    process.exit(1);
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
    const { saveLiveSync } = require("./pg_store");
    saveLiveSync(db, { source: "flush_sync" });
    return;
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
                const { saveLive } = require("./pg_store");
                const parsed = JSON.parse(payload);
                await saveLive(parsed, { source: "enqueue_save", alsoSnapshot: false });
                pgSaveFailures = 0;
                try {
                  const { maybeDualWrite } = require("./pg_dual");
                  setImmediate(() => {
                    maybeDualWrite(null, {
                      fromPrimary: true,
                      payloadUtf8: payload,
                    }).catch(() => {});
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
  SESSIONS_BACKEND: USE_SESSIONS_TABLE ? "table" : "blob",
  MARKET_BACKEND: USE_MARKET_TABLE ? "table" : "blob",
  FRIENDS_BACKEND: USE_FRIENDS_TABLE ? "table" : "blob",
  EVENTS_BACKEND: USE_EVENTS_TABLE ? "table" : "blob",
};

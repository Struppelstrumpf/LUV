/**
 * Nächtlicher Wartungsmodus 02:59–03:09 Europe/Berlin:
 * Shop-Zyklus, Store-Backup (max. 10), Gesundheitsbericht.
 */
const fs = require("fs");
const path = require("path");
const { berlinParts, berlinLocalMs } = require("./events");

const WINDOW_START_H = 2;
const WINDOW_START_M = 59;
const WINDOW_END_H = 3;
const WINDOW_END_M = 9;
const MAX_BACKUPS = 10;
const CLAIM_GRACE_MS = 30 * 60_000;

const JOKES = [
  "Die Server sortieren gerade Sticker nach Sternzeichen. Bitte leise tippen — außer zum Hüpfen.",
  "Wartung: Unsere Items machen Yoga. Einatmen… Shop raus. Ausatmen… Shop rein. Du darfst zuschauen.",
  "Gleich wieder online. Bis dahin: Du gegen den fiesen Pinsel. Wer verliert, darf trotzdem weiterlieben.",
  "Kurz Pause — die Coins putzen sich die Zähne. Dauer: so lange wie ein schlechtes First Date… okay, kürzer.",
  "Wir aktualisieren den Shop. Die Begleiter machen ein Nickerchen. Nicht wecken — außer mit einem Tap-Sprung.",
  "Fun Fact: Warten ist wie Zeichnen mit dem Partner — man tippt und hofft, dass die andere Seite auch was tut.",
  "Die Leinwand macht gerade Stretching. Bitte nicht mitfeiern, außer mit dem Herz-Hüpfer unten.",
];

function nightKeyFromParts(p) {
  return `${p.year}-${String(p.month).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`;
}

function windowBoundsMs(now = Date.now()) {
  const p = berlinParts(new Date(now));
  const start = berlinLocalMs(p.year, p.month, p.day, WINDOW_START_H, WINDOW_START_M);
  const end = berlinLocalMs(p.year, p.month, p.day, WINDOW_END_H, WINDOW_END_M);
  return { start, end, nightKey: nightKeyFromParts(p), parts: p };
}

function isMaintenanceActive(now = Date.now()) {
  const { start, end } = windowBoundsMs(now);
  return now >= start && now < end;
}

function ensureState(db) {
  if (!db.maintenance || typeof db.maintenance !== "object") {
    db.maintenance = {
      nightKey: null,
      joke: "",
      jobDone: false,
      jobStartedAt: null,
      jobFinishedAt: null,
      lastReportId: null,
    };
  }
  if (!db.maintenanceReports || typeof db.maintenanceReports !== "object") {
    db.maintenanceReports = {};
  }
  return db.maintenance;
}

function pickJoke(nightKey) {
  let h = 0;
  const s = String(nightKey || "x");
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return JOKES[h % JOKES.length];
}

function publicStatus(db, user = null, now = Date.now()) {
  ensureState(db);
  const { start, end, nightKey } = windowBoundsMs(now);
  const active = now >= start && now < end;
  const st = db.maintenance;
  // Wer während des Fensters Status abruft, gilt als „mitgewartet“
  if (user && active) {
    user.maintenanceWaitedNight = nightKey;
  }
  const highScore = Math.max(0, Math.floor(Number(user?.maintenanceHighScore) || 0));
  const claimed = Boolean(user && user.maintenanceClaimNight === nightKey);
  const waited = Boolean(user && user.maintenanceWaitedNight === nightKey);
  const canClaim =
    Boolean(user) &&
    waited &&
    !claimed &&
    now >= end &&
    now < end + CLAIM_GRACE_MS;
  const joke =
    active || canClaim
      ? st.joke || pickJoke(nightKey)
      : pickJoke(nightKey);
  return {
    active,
    nightKey,
    startsAt: start,
    endsAt: end,
    serverNow: now,
    remainingMs: active ? Math.max(0, end - now) : 0,
    joke,
    highScore,
    claimed,
    canClaim,
    rewardCoins: 2,
    claimGraceMs: CLAIM_GRACE_MS,
  };
}

function recordScore(db, user, score) {
  if (!user) return { ok: false, error: "auth" };
  const n = Math.max(0, Math.min(999999, Math.floor(Number(score) || 0)));
  const prev = Math.max(0, Math.floor(Number(user.maintenanceHighScore) || 0));
  if (n > prev) {
    user.maintenanceHighScore = n;
    user.maintenanceHighScoreAt = Date.now();
  }
  ensureState(db);
  if (!db.maintenance.topScores || typeof db.maintenance.topScores !== "object") {
    db.maintenance.topScores = {};
  }
  const uid = String(user.id || "");
  const cur = Math.max(0, Math.floor(Number(db.maintenance.topScores[uid]?.score) || 0));
  if (n > cur) {
    db.maintenance.topScores[uid] = {
      score: n,
      nickname: String(user.nickname || "Jemand").slice(0, 40),
      at: Date.now(),
    };
  }
  return {
    ok: true,
    highScore: Math.max(prev, n),
    personalBest: user.maintenanceHighScore,
  };
}

function claimReward(db, user, applyLedgerFn, now = Date.now()) {
  if (!user) return { ok: false, error: "auth", message: "Nicht angemeldet" };
  const { end, nightKey } = windowBoundsMs(now);
  // Claim erst nach Fensterende, max. 30 Min danach (kein Dauer-Exploit)
  if (now < end) {
    return { ok: false, error: "too_early", message: "Wartung läuft noch" };
  }
  if (now >= end + CLAIM_GRACE_MS) {
    return { ok: false, error: "too_late", message: "Belohnung nicht mehr verfügbar" };
  }
  if (user.maintenanceWaitedNight !== nightKey) {
    return { ok: false, error: "not_eligible", message: "Nur nach Mitwarten" };
  }
  if (user.maintenanceClaimNight === nightKey) {
    return { ok: true, already: true, coins: 0, nightKey };
  }
  user.maintenanceClaimNight = nightKey;
  if (typeof applyLedgerFn === "function") {
    applyLedgerFn(user.id, 2, "maintenance_reward", `maint:${nightKey}`);
  }
  ensureState(db);
  db.maintenance.claims = Math.max(0, Math.floor(Number(db.maintenance.claims) || 0)) + 1;
  return { ok: true, already: false, coins: 2, nightKey };
}

function backupDir(dataDir) {
  return path.join(dataDir, "backups");
}

function pruneBackups(dir, maxKeep = MAX_BACKUPS) {
  if (!fs.existsSync(dir)) return { kept: 0, deleted: [] };
  const files = fs
    .readdirSync(dir)
    .filter((f) => /^luv-store-.*\.json$/i.test(f))
    .map((f) => ({ name: f, full: path.join(dir, f), mtime: fs.statSync(path.join(dir, f)).mtimeMs }))
    .sort((a, b) => b.mtime - a.mtime);
  const deleted = [];
  while (files.length > maxKeep) {
    const old = files.pop();
    try {
      fs.unlinkSync(old.full);
      deleted.push(old.name);
    } catch (_) {
      /* ignore */
    }
  }
  return { kept: files.length, deleted };
}

function runBackup(dataFile, dataDir) {
  const entry = { level: "info", code: "BACKUP", message: "", recommendation: null };
  try {
    if (!fs.existsSync(dataFile)) {
      entry.level = "warn";
      entry.code = "BACKUP_MISSING_STORE";
      entry.message = "luv-store.json nicht gefunden — Backup übersprungen.";
      entry.recommendation = "Prüfen ob DATA_DIR korrekt ist und der Store geschrieben wird.";
      return { ok: false, entry, path: null };
    }
    const dir = backupDir(dataDir);
    fs.mkdirSync(dir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    const dest = path.join(dir, `luv-store-${stamp}.json`);
    fs.copyFileSync(dataFile, dest);
    const bytes = fs.statSync(dest).size;
    const pruned = pruneBackups(dir, MAX_BACKUPS);
    entry.message = `Backup ok (${bytes} Bytes) → ${path.basename(dest)}. Behalten: ${pruned.kept}.`;
    if (pruned.deleted.length) {
      entry.message += ` Gelöscht: ${pruned.deleted.join(", ")}.`;
    }
    return { ok: true, entry, path: dest, bytes, pruned };
  } catch (e) {
    entry.level = "error";
    entry.code = "BACKUP_FAILED";
    entry.message = e?.message || String(e);
    entry.recommendation =
      "Festplatte/Rechte prüfen; ggf. manuell luv-store.json sichern und Server-Logs lesen.";
    return { ok: false, entry, path: null };
  }
}

function runShopCycle(db, shopCalendar, shopCatalog) {
  const entries = [];
  try {
    const before = Date.now();
    const result = shopCalendar.applyAllRotationPlans(db);
    shopCatalog.deactivateExpired(db);
    const touched = Array.isArray(result)
      ? result.reduce((s, r) => s + (Number(r?.touched) || 0), 0)
      : Number(result?.touched) || 0;
    const stats = countShopLive(db);
    entries.push({
      level: "info",
      code: "SHOP_CYCLE_OK",
      message: `Shop-Zyklus angewendet (${Date.now() - before} ms). Touched≈${touched}. Live im Shop jetzt: ${stats.live} (Rotation live ${stats.rotLive}/${stats.rotatable}, Ziel ~50 %).`,
      recommendation: null,
    });
    if (stats.live < 80) {
      entries.push({
        level: "error",
        code: "SHOP_LIVE_CRITICAL",
        message: `Nur ${stats.live} Items aktuell im Shop — kritisch niedrig.`,
        recommendation:
          "Unter Itemshop → Kalender → Rotation „Jetzt neu mischen“. Wenn das nicht hilft: Bericht an die KI.",
      });
    } else if (
      stats.live < 200 ||
      (stats.rotatable > 100 && stats.rotLive / stats.rotatable < 0.25)
    ) {
      entries.push({
        level: "warn",
        code: "SHOP_LIVE_LOW",
        message: `Shop eher dünn: live=${stats.live}, Rotation ${stats.rotLive}/${stats.rotatable}.`,
        recommendation:
          "Einmal „Neu mischen“ prüfen. Ziel sind grob 40–60 % der rotierenden Items plus Starter.",
      });
    }
    return { ok: true, entries, touched, stats };
  } catch (e) {
    entries.push({
      level: "error",
      code: "SHOP_CYCLE_FAILED",
      message: e?.message || String(e),
      recommendation:
        "Rotation fehlgeschlagen — Items ggf. manuell unter Itemshop → Rotation „Neu mischen“. " +
        "Fehlerdetails an die KI schicken (Bericht kopieren).",
    });
    return { ok: false, entries, touched: 0 };
  }
}

/** Aktuell kaufbare Shop-Items (enabled + Zeitfenster jetzt). */
function countShopLive(db, now = Date.now()) {
  const items = Object.values(db?.shopCatalog?.items || {}).filter(Boolean);
  let live = 0;
  let rotatable = 0;
  let rotLive = 0;
  let always = 0;
  for (const it of items) {
    const from = it.availableFrom || 0;
    const until = it.availableUntil || Number.MAX_SAFE_INTEGER;
    const inWin = it.enabled !== false && now >= from && now < until;
    const alwaysOn =
      it.enabled !== false && it.availableFrom == null && it.availableUntil == null;
    if (alwaysOn) {
      always += 1;
      live += 1;
    } else if (inWin) {
      live += 1;
    }
    const rotates = Boolean(it.rotationPlanId) && it.rotationLocked !== true;
    if (rotates) {
      rotatable += 1;
      if (inWin) rotLive += 1;
    }
  }
  return {
    total: items.length,
    live,
    rotatable,
    rotLive,
    always,
    rotSharePct: rotatable ? Math.round((100 * rotLive) / rotatable) : 0,
  };
}

function healthSweep(db) {
  const entries = [];
  try {
    const users = Object.keys(db.users || {}).length;
    const rooms = Object.keys(db.rooms || {}).length;
    const shopItems = Object.keys(db.shopCatalog?.items || {}).length;
    entries.push({
      level: "info",
      code: "HEALTH_COUNTS",
      message: `Nutzer=${users}, Rooms=${rooms}, ShopItems=${shopItems}`,
      recommendation: null,
    });
    if (shopItems === 0) {
      entries.push({
        level: "warn",
        code: "SHOP_EMPTY",
        message: "Shop-Katalog ist leer.",
        recommendation: "Shop neu seeden / Katalog prüfen.",
      });
    }
    const live = countShopLive(db);
    entries.push({
      level: "info",
      code: "SHOP_LIVE_NOW",
      message: `Aktuell im Shop: ${live.live} (Rotation ${live.rotLive}/${live.rotatable} = ${live.rotSharePct} %, Starter/Fix≈${live.always}).`,
      recommendation: null,
    });
    if (live.live < 80) {
      entries.push({
        level: "error",
        code: "SHOP_LIVE_CRITICAL",
        message: `Nur ${live.live} Items live — Shop droht leer zu wirken.`,
        recommendation: "Rotation neu mischen; Bericht an die KI.",
      });
    } else if (live.live < 200) {
      entries.push({
        level: "warn",
        code: "SHOP_LIVE_LOW",
        message: `Nur ${live.live} Items live (unter Komfortzone ~200+).`,
        recommendation: "Rotation prüfen / neu mischen.",
      });
    }
    const broken = [];
    for (const [key, it] of Object.entries(db.shopCatalog?.items || {})) {
      if (!it || !it.kind || !it.itemId) broken.push(key);
      else if (
        it.availableFrom != null &&
        it.availableUntil != null &&
        Number(it.availableUntil) < Number(it.availableFrom)
      ) {
        broken.push(`${key}:until<from`);
      }
    }
    if (broken.length) {
      entries.push({
        level: "warn",
        code: "SHOP_WINDOW_ODD",
        message: `${broken.length} verdächtige Shop-Einträge: ${broken.slice(0, 8).join(", ")}`,
        recommendation: "Fenster im Itemshop-Kalender prüfen oder Rotation neu berechnen.",
      });
    } else {
      entries.push({
        level: "info",
        code: "SHOP_WINDOWS_OK",
        message: "Keine offensichtlich kaputten availableFrom/Until-Paare.",
        recommendation: null,
      });
    }
  } catch (e) {
    entries.push({
      level: "error",
      code: "HEALTH_SWEEP_FAILED",
      message: e?.message || String(e),
      recommendation: "Store-Integrität prüfen.",
    });
  }
  return entries;
}

function overallStatus(entries) {
  if (entries.some((e) => e.level === "error")) return "error";
  if (entries.some((e) => e.level === "warn")) return "warn";
  return "ok";
}

function saveReport(db, report) {
  ensureState(db);
  db.maintenanceReports[report.id] = report;
  // Max. 60 Berichte behalten
  const ids = Object.keys(db.maintenanceReports).sort();
  while (ids.length > 60) {
    const old = ids.shift();
    delete db.maintenanceReports[old];
  }
  db.maintenance.lastReportId = report.id;
  return report;
}

function buildReport({ nightKey, source, entries, backup, shop, now = Date.now() }) {
  const status = overallStatus(entries);
  const summary =
    status === "ok"
      ? "Wartung ohne kritische Fehler."
      : status === "warn"
        ? "Wartung mit Warnungen — bitte prüfen."
        : "Wartung mit Fehlern — bitte handeln.";
  return {
    id: `maint_${nightKey}_${source}_${now.toString(36)}`,
    nightKey,
    source, // auto | manual
    createdAt: now,
    status,
    summary,
    entries,
    backup: backup
      ? { path: backup.path, bytes: backup.bytes, ok: backup.ok }
      : null,
    shop: shop || null,
    copyText: formatReportCopy({
      nightKey,
      source,
      status,
      summary,
      entries,
      backup,
      shop,
      createdAt: now,
    }),
  };
}

function formatReportCopy(r) {
  const lines = [
    `LUV Wartungsbericht ${r.nightKey} (${r.source})`,
    `Status: ${r.status}`,
    `Zeit: ${new Date(r.createdAt).toISOString()}`,
    `Summary: ${r.summary}`,
    "",
  ];
  for (const e of r.entries || []) {
    lines.push(`[${String(e.level || "?").toUpperCase()}] ${e.code || "?"} — ${e.message || ""}`);
    if (e.recommendation) lines.push(`  → Empfehlung: ${e.recommendation}`);
  }
  if (r.backup) {
    lines.push("");
    lines.push(`Backup: ok=${r.backup.ok} bytes=${r.backup.bytes || 0} path=${r.backup.path || "—"}`);
  }
  if (r.shop) {
    lines.push(`Shop: touched≈${r.shop.touched || 0} ok=${r.shop.ok}`);
  }
  lines.push("");
  lines.push("(Diesen Textblock an die KI schicken.)");
  return lines.join("\n");
}

/**
 * Einmal pro Nacht im Fenster: Backup + Shop-Zyklus + Bericht.
 */
function runNightlyJob(db, {
  dataFile,
  dataDir,
  shopCalendar,
  shopCatalog,
  flushSaveSync,
  beforeShop,
  processShopQueue,
  now = Date.now(),
} = {}) {
  const st = ensureState(db);
  const { nightKey, start, end } = windowBoundsMs(now);
  if (now < start || now >= end) {
    return { ran: false, reason: "outside_window" };
  }
  if (st.nightKey === nightKey && st.jobDone) {
    return { ran: false, reason: "already_done", reportId: st.lastReportId };
  }

  st.nightKey = nightKey;
  st.joke = pickJoke(nightKey);
  st.jobStartedAt = now;
  st.jobDone = false;
  st.claims = 0;

  const entries = [
    {
      level: "info",
      code: "MAINT_START",
      message: `Wartungsfenster ${nightKey} 02:59–03:09 Berlin gestartet.`,
      recommendation: null,
    },
  ];

  try {
    if (typeof flushSaveSync === "function") flushSaveSync();
  } catch (e) {
    entries.push({
      level: "warn",
      code: "FLUSH_BEFORE_BACKUP",
      message: e?.message || String(e),
      recommendation: "Flush vor Backup fehlgeschlagen — Backup ggf. leicht veraltet.",
    });
  }

  const backup = runBackup(dataFile, dataDir);
  entries.push(backup.entry);

  try {
    if (typeof beforeShop === "function") beforeShop();
  } catch (e) {
    entries.push({
      level: "warn",
      code: "SHOP_SEED_FAILED",
      message: e?.message || String(e),
      recommendation: "Shop-Seed vor Zyklus fehlgeschlagen — Katalog ggf. prüfen.",
    });
  }

  // Zuerst Admin-Warteschlange (neue Items / Edits), dann Rotation neu schreiben
  if (typeof processShopQueue === "function") {
    try {
      const qResult = processShopQueue();
      const n = Number(qResult?.processed) || 0;
      const fail = Number(qResult?.failCount) || 0;
      entries.push({
        level: fail ? "warn" : "info",
        code: "SHOP_QUEUE",
        message: `Warteschlange: ${n} Job(s) verarbeitet, Fehler=${fail}.`,
        recommendation: fail
          ? "Fehlgeschlagene Jobs unter Itemshop → Warteschlange prüfen."
          : null,
      });
    } catch (e) {
      entries.push({
        level: "error",
        code: "SHOP_QUEUE_FAILED",
        message: e?.message || String(e),
        recommendation: "Warteschlange manuell prüfen / Jobs stornieren.",
      });
    }
  }

  const shop = runShopCycle(db, shopCalendar, shopCatalog);
  entries.push(...shop.entries);

  entries.push(...healthSweep(db));

  st.jobFinishedAt = Date.now();
  st.jobDone = true;

  const report = buildReport({
    nightKey,
    source: "auto",
    entries,
    backup,
    shop: { ok: shop.ok, touched: shop.touched },
    now: Date.now(),
  });
  saveReport(db, report);

  return { ran: true, report };
}

/** Manueller Bericht (ohne Shop umzubauen — optional mit Backup). */
function runManualReport(db, {
  dataFile,
  dataDir,
  shopCalendar,
  shopCatalog,
  flushSaveSync,
  withShopCycle = false,
} = {}) {
  const { nightKey } = windowBoundsMs();
  const entries = [
    {
      level: "info",
      code: "MANUAL_REPORT",
      message: "Manuell erfasster Wartungs-/Gesundheitsbericht.",
      recommendation: null,
    },
  ];
  try {
    if (typeof flushSaveSync === "function") flushSaveSync();
  } catch (e) {
    entries.push({
      level: "warn",
      code: "FLUSH_FAILED",
      message: e?.message || String(e),
      recommendation: null,
    });
  }
  const backup = runBackup(dataFile, dataDir);
  entries.push(backup.entry);
  let shop = { ok: true, touched: 0, entries: [] };
  if (withShopCycle && shopCalendar && shopCatalog) {
    shop = runShopCycle(db, shopCalendar, shopCatalog);
    entries.push(...shop.entries);
  }
  entries.push(...healthSweep(db));
  const report = buildReport({
    nightKey,
    source: "manual",
    entries,
    backup,
    shop: withShopCycle ? { ok: shop.ok, touched: shop.touched } : null,
  });
  saveReport(db, report);
  return report;
}

function listReports(db) {
  ensureState(db);
  return Object.values(db.maintenanceReports || {})
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    .slice(0, 40);
}

function getReport(db, id) {
  ensureState(db);
  return db.maintenanceReports?.[String(id || "")] || null;
}

/**
 * Tick alle ~15s: Job einmal starten; State pflegen.
 */
function tick(db, deps) {
  ensureState(db);
  const now = Date.now();
  if (!isMaintenanceActive(now)) return { active: false };
  const { nightKey } = windowBoundsMs(now);
  const st = db.maintenance;
  if (st.nightKey !== nightKey) {
    st.nightKey = nightKey;
    st.joke = pickJoke(nightKey);
    st.jobDone = false;
    st.jobStartedAt = null;
    st.jobFinishedAt = null;
  }
  if (!st.joke) st.joke = pickJoke(nightKey);
  if (!st.jobDone) {
    return { active: true, job: runNightlyJob(db, { ...deps, now }) };
  }
  return { active: true, job: { ran: false, reason: "already_done" } };
}

module.exports = {
  isMaintenanceActive,
  windowBoundsMs,
  publicStatus,
  recordScore,
  claimReward,
  tick,
  runNightlyJob,
  runManualReport,
  listReports,
  getReport,
  ensureState,
  WINDOW_START_H,
  WINDOW_START_M,
  WINDOW_END_H,
  WINDOW_END_M,
};

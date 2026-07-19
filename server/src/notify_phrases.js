/**
 * Admin-verwaltete Benachrichtigungs-/Share-Sprüche inkl. Tap-Ziel.
 */
const { SHARE_LINES } = require("./share_lines");
const MOOD_SEED = require("./mood_lines_seed");

const TARGETS = [
  { id: "home", label: "Home" },
  { id: "last_canvas", label: "Letzte Leinwand" },
  { id: "marketplace", label: "Marktplatz" },
  { id: "itemshop", label: "Itemshop" },
  { id: "coinshop", label: "Coinshop" },
  { id: "inventar", label: "Inventar (Menü)" },
  { id: "profile", label: "Profil gestalten" },
  { id: "sozial_friends", label: "Sozial · Freunde" },
  { id: "sozial_achievements", label: "Sozial · Erfolge" },
  { id: "none", label: "Nur öffnen (kein Ziel)" },
];

const TARGET_IDS = new Set(TARGETS.map((t) => t.id));

function ensureNotifyPhrases(db) {
  if (!db || typeof db !== "object") return { phrases: [], version: 1 };
  if (!db.notifyPhrases || typeof db.notifyPhrases !== "object") {
    db.notifyPhrases = { phrases: [], version: 1, seededAt: null };
  }
  if (!Array.isArray(db.notifyPhrases.phrases)) db.notifyPhrases.phrases = [];
  if (!db.notifyPhrases.seededAt) {
    seedDefaults(db);
  }
  return db.notifyPhrases;
}

function newId(prefix) {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function seedDefaults(db) {
  const store = db.notifyPhrases;
  const now = Date.now();
  const phrases = [];
  for (const text of MOOD_SEED) {
    const t = String(text || "").trim().slice(0, 200);
    if (!t) continue;
    phrases.push({
      id: newId("mood"),
      text: t,
      subtitle: "Tippen — und kurz vorbeischauen",
      pool: "mood",
      target: "home",
      enabled: true,
      createdAt: now,
      updatedAt: now,
    });
  }
  for (const text of SHARE_LINES) {
    const t = String(text || "").trim().slice(0, 200);
    if (!t) continue;
    phrases.push({
      id: newId("share"),
      text: t,
      subtitle: "",
      pool: "share",
      target: "none",
      enabled: true,
      createdAt: now,
      updatedAt: now,
    });
  }
  store.phrases = phrases;
  store.seededAt = now;
  store.version = 1;
}

function normalizeTarget(raw) {
  const t = String(raw || "home").trim().toLowerCase();
  return TARGET_IDS.has(t) ? t : "home";
}

function normalizePool(raw) {
  const p = String(raw || "mood").trim().toLowerCase();
  return p === "share" ? "share" : "mood";
}

function publicPhrase(p) {
  if (!p) return null;
  return {
    id: p.id,
    text: p.text,
    subtitle: p.subtitle || "",
    pool: p.pool,
    target: p.target,
    enabled: p.enabled !== false,
    createdAt: p.createdAt || null,
    updatedAt: p.updatedAt || null,
  };
}

function listPhrases(db, { pool = null, q = "" } = {}) {
  const store = ensureNotifyPhrases(db);
  let list = store.phrases.map(publicPhrase).filter(Boolean);
  if (pool) list = list.filter((p) => p.pool === pool);
  const qq = String(q || "").trim().toLowerCase();
  if (qq) {
    list = list.filter(
      (p) =>
        p.text.toLowerCase().includes(qq) ||
        p.id.toLowerCase().includes(qq) ||
        p.target.toLowerCase().includes(qq)
    );
  }
  list.sort((a, b) => String(b.updatedAt || 0) - String(a.updatedAt || 0));
  return list;
}

function upsertPhrase(db, patch, { create = false } = {}) {
  const store = ensureNotifyPhrases(db);
  const text = String(patch?.text || "").trim().slice(0, 200);
  if (text.length < 3) {
    return { ok: false, error: "bad_text", message: "Text zu kurz." };
  }
  const id = String(patch?.id || "").trim() || (create ? newId(normalizePool(patch?.pool)) : "");
  if (!id) return { ok: false, error: "bad_id", message: "ID fehlt." };
  const idx = store.phrases.findIndex((p) => p && p.id === id);
  if (create && idx >= 0) {
    return { ok: false, error: "exists", message: "Spruch existiert bereits." };
  }
  if (!create && idx < 0) {
    return { ok: false, error: "not_found", message: "Spruch nicht gefunden." };
  }
  const prev = idx >= 0 ? store.phrases[idx] : null;
  const next = {
    id,
    text,
    subtitle: String(patch?.subtitle ?? prev?.subtitle ?? "").trim().slice(0, 120),
    pool: normalizePool(patch?.pool ?? prev?.pool),
    target: normalizeTarget(patch?.target ?? prev?.target),
    enabled: patch?.enabled === undefined ? prev?.enabled !== false : Boolean(patch.enabled),
    createdAt: prev?.createdAt || Date.now(),
    updatedAt: Date.now(),
  };
  if (idx >= 0) store.phrases[idx] = next;
  else store.phrases.push(next);
  return { ok: true, phrase: publicPhrase(next) };
}

function deletePhrase(db, id) {
  const store = ensureNotifyPhrases(db);
  const want = String(id || "").trim();
  const before = store.phrases.length;
  store.phrases = store.phrases.filter((p) => p && p.id !== want);
  if (store.phrases.length === before) {
    return { ok: false, error: "not_found", message: "Spruch nicht gefunden." };
  }
  return { ok: true };
}

function pickPhrase(db, { pool = "mood", excludingId = null } = {}) {
  const store = ensureNotifyPhrases(db);
  const want = normalizePool(pool);
  let list = store.phrases.filter(
    (p) => p && p.enabled !== false && p.pool === want && String(p.text || "").trim()
  );
  if (excludingId) list = list.filter((p) => p.id !== excludingId);
  if (!list.length) {
    const fallback =
      want === "share"
        ? SHARE_LINES[0] || "Ein kleiner Moment nur für euch."
        : MOOD_SEED[0] || "Komm kurz vorbei.";
    return {
      id: "fallback",
      text: fallback,
      subtitle: want === "mood" ? "Tippen — und kurz vorbeischauen" : "",
      pool: want,
      target: want === "mood" ? "home" : "none",
    };
  }
  const pick = list[Math.floor(Math.random() * list.length)];
  return publicPhrase(pick);
}

/** Share-Line für OG/Landing — nutzt Admin-Pool wenn vorhanden. */
function pickShareLineFromDb(db) {
  return pickPhrase(db, { pool: "share" }).text;
}

module.exports = {
  TARGETS,
  ensureNotifyPhrases,
  listPhrases,
  upsertPhrase,
  deletePhrase,
  pickPhrase,
  pickShareLineFromDb,
  publicPhrase,
};

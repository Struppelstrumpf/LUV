/**
 * Event-Module: Quests, Lobby, Contest/Voting, Decor-Erweiterungen, Create/Update.
 * Arbeitet mit db.eventsConfig + db.eventContest.
 */

const path = require("path");
const fs = require("fs");
const crypto = require("crypto");

const QUEST_METRICS = [
  { id: "strokes", label: "Striche zeichnen" },
  { id: "draw_sessions", label: "Mal-Sessions" },
  { id: "lobby_opens", label: "Lobby öffnen" },
  { id: "krauls", label: "Begleiter kraulen" },
  { id: "social_opens", label: "Sozial öffnen" },
  { id: "gallery_opens", label: "Galerie öffnen" },
  { id: "reactions_sent", label: "Reaktion senden" },
  { id: "moments_saved", label: "Moment speichern" },
  { id: "event_lobby_strokes", label: "Striche in Event-Lobby" },
  { id: "event_lobby_opens", label: "Event-Lobby öffnen" },
];

const QUEST_METRIC_SET = new Set(QUEST_METRICS.map((m) => m.id));

function clampInt(n, min, max, fallback) {
  const v = Math.floor(Number(n));
  if (!Number.isFinite(v)) return fallback;
  return Math.max(min, Math.min(max, v));
}

function clampReward(n, fallback = 0) {
  return clampInt(n, 0, 200, fallback);
}

const DEFAULT_EVENT_PROMPTS = [
  "Herz",
  "Stern",
  "Sonne",
  "Mond",
  "Blume",
  "Katze",
  "Haus",
  "Baum",
  "Wolke",
  "Regenbogen",
];

function defaultContestPlaces() {
  const places = [
    {
      place: 1,
      coins: 100,
      rewardItem: {
        kind: "stickers",
        itemId: "🥇",
        emoji: "🥇",
        label: "Goldmedaille",
      },
    },
    { place: 2, coins: 50, rewardItem: null },
    { place: 3, coins: 25, rewardItem: null },
  ];
  for (let p = 4; p <= 10; p += 1) {
    places.push({ place: p, coins: 10, rewardItem: null });
  }
  return places;
}

function pickEventPrompt(lobbyCfg, eventTitle) {
  const picks = sampleEventPrompts(lobbyCfg, 1, eventTitle);
  return String(picks[0] || "Herz").slice(0, 80);
}

/** Bis zu [count] unterschiedliche Wörter aus der Event-Wortliste (Zufallsreihenfolge). */
function sampleEventPrompts(lobbyCfg, count = 3, eventTitle) {
  void eventTitle;
  const fromCfg = Array.isArray(lobbyCfg?.prompts)
    ? lobbyCfg.prompts.map((p) => String(p || "").trim()).filter(Boolean)
    : [];
  const pool = [...new Set(fromCfg.length ? fromCfg : DEFAULT_EVENT_PROMPTS)];
  for (let i = pool.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    const tmp = pool[i];
    pool[i] = pool[j];
    pool[j] = tmp;
  }
  const n = Math.max(1, Math.min(5, Math.floor(Number(count) || 3)));
  return pool.slice(0, Math.min(n, pool.length)).map((p) => String(p).slice(0, 80));
}

function slugId(raw) {
  const s = String(raw || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 40);
  return s || `ev_${crypto.randomBytes(4).toString("hex")}`;
}

function publicRewardItem(raw) {
  if (!raw || typeof raw !== "object") return null;
  const kind = String(raw.kind || "").trim();
  const itemId = String(raw.itemId || "").trim();
  if (!kind || !itemId) return null;
  const emoRaw = String(raw.emoji || itemId).trim();
  const emoMax = /^img_/i.test(emoRaw) || /^img_/i.test(itemId) ? 48 : 16;
  return {
    kind,
    itemId: itemId.slice(0, 48),
    emoji: emoRaw.slice(0, emoMax),
    label: String(raw.label || itemId).slice(0, 60),
  };
}

/** Bis zu 6 Item-Belohnungen; `single` als Fallback für ältere Events. */
function publicRewardItems(rawList, single) {
  const fromArr = Array.isArray(rawList) ? rawList : [];
  const merged = fromArr.length ? fromArr : single ? [single] : [];
  const out = [];
  const seen = new Set();
  for (const raw of merged) {
    const it = publicRewardItem(raw);
    if (!it) continue;
    const key = `${it.kind}:${it.itemId}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(it);
    if (out.length >= 6) break;
  }
  return out;
}

function normalizeDecor(d, fallback) {
  const base = fallback || {
    particles: "none",
    accentHex: "#E94E77",
    bannerText: "",
    intensity: 0.55,
    ornaments: "none",
    particleEmojis: [],
  };
  const src = d && typeof d === "object" ? d : {};
  const particles = String(src.particles || base.particles || "none").slice(0, 24);
  const allowed = new Set(["none", "snow", "hearts", "leaves", "sparkle", "custom"]);
  let emojis = Array.isArray(src.particleEmojis)
    ? src.particleEmojis.map((e) => String(e || "").trim().slice(0, 8)).filter(Boolean).slice(0, 8)
    : Array.isArray(base.particleEmojis)
      ? base.particleEmojis
      : [];
  if (particles === "custom" && !emojis.length) emojis = ["✨"];
  return {
    particles: allowed.has(particles) ? particles : "none",
    accentHex: String(src.accentHex || base.accentHex || "#E94E77").slice(0, 16),
    bannerText: String(src.bannerText || base.bannerText || "").slice(0, 48),
    intensity: Math.max(0, Math.min(1, Number(src.intensity ?? base.intensity) || 0.55)),
    ornaments: String(src.ornaments || base.ornaments || "none").slice(0, 24),
    particleEmojis: emojis,
  };
}

function normalizeCollect(raw, ev) {
  const src = raw && typeof raw === "object" ? raw : {};
  const enabled =
    src.enabled != null
      ? Boolean(src.enabled)
      : !(ev?.lobby?.enabled || ev?.contest?.enabled || (Array.isArray(ev?.quests) && ev.quests.length));
  const rewardItems = publicRewardItems(
    src.rewardItems ?? ev?.rewardItems,
    src.rewardItem ?? ev?.rewardItem
  );
  return {
    enabled: enabled !== false,
    rewardCoinsPerCollect: clampReward(
      src.rewardCoinsPerCollect ?? ev?.rewardCoinsPerCollect,
      2
    ),
    collectTarget: clampInt(src.collectTarget ?? ev?.collectTarget, 1, 31, 3),
    milestoneBonusCoins: clampReward(src.milestoneBonusCoins ?? ev?.milestoneBonusCoins, 0),
    rewardItems,
    rewardItem: rewardItems[0] || null,
  };
}

function normalizeQuests(raw) {
  if (!Array.isArray(raw)) return [];
  return raw
    .slice(0, 12)
    .map((q, i) => {
      if (!q || typeof q !== "object") return null;
      const metric = String(q.metric || "").trim();
      if (!QUEST_METRIC_SET.has(metric)) return null;
      return {
        id: String(q.id || `q_${i + 1}`).slice(0, 40),
        title: String(q.title || "Quest").slice(0, 60),
        hint: String(q.hint || "").slice(0, 200),
        metric,
        target: clampInt(q.target, 1, 9999, 1),
        rewardCoins: clampReward(q.rewardCoins, 1),
        rewardItem: publicRewardItem(q.rewardItem),
      };
    })
    .filter(Boolean);
}

function normalizeLobby(raw) {
  const src = raw && typeof raw === "object" ? raw : {};
  const enabled = Boolean(src.enabled);
  if (!enabled) {
    return { enabled: false };
  }
  const access = ["friends", "invite", "random"].includes(src.access) ? src.access : "friends";
  const createMode = src.createMode === "manual" ? "manual" : "auto";
  const palette = ["blue", "purple", "event"].includes(src.palette) ? src.palette : "event";
  const drawMode = src.drawMode === "promptList" ? "promptList" : "free";
  const prompts = Array.isArray(src.prompts)
    ? src.prompts.map((p) => String(p || "").trim().slice(0, 80)).filter(Boolean).slice(0, 40)
    : [];
  return {
    enabled: true,
    access,
    createMode,
    palette,
    invitesAllowed: src.invitesAllowed !== false,
    drawMode,
    // Wortliste auch speichern wenn drawMode free — Auswahl bleibt random pro Lobby
    prompts,
    minStrokesToQualify: clampInt(src.minStrokesToQualify, 0, 500, 5),
    sessionSeconds: clampInt(src.sessionSeconds, 30, 3600, 180),
  };
}

function normalizeContest(raw) {
  const src = raw && typeof raw === "object" ? raw : {};
  if (!src.enabled) return { enabled: false };
  const places = Array.isArray(src.places) && src.places.length
    ? src.places
        .slice(0, 10)
        .map((p) => ({
          place: clampInt(p.place, 1, 10, 1),
          coins: clampReward(p.coins, 10),
          rewardItem: publicRewardItem(p.rewardItem),
        }))
        .sort((a, b) => a.place - b.place)
    : defaultContestPlaces();
  const vr = src.voteRequire && typeof src.voteRequire === "object" ? src.voteRequire : {};
  return {
    enabled: true,
    voteFrom: src.voteFrom ? String(src.voteFrom).slice(0, 40) : null,
    voteUntil: src.voteUntil ? String(src.voteUntil).slice(0, 40) : null,
    places,
    voterRewardCoins: clampReward(src.voterRewardCoins, 0),
    voteRequire: {
      drewInEventLobby: vr.drewInEventLobby !== false,
      minStrokes: clampInt(vr.minStrokes, 0, 500, 5),
    },
    maxEntriesPerUser: clampInt(src.maxEntriesPerUser, 1, 5, 1),
  };
}

function normalizeSchedule(raw, ev) {
  const src = raw && typeof raw === "object" ? raw : {};
  const mode = src.mode === "absolute" ? "absolute" : "recurring";
  if (mode === "absolute") {
    let durationDays = clampInt(src.durationDays ?? ev?.durationDays, 1, 31, 1);
    const fromMs = src.absoluteFrom ? Date.parse(String(src.absoluteFrom)) : NaN;
    const untilMs = src.absoluteUntil ? Date.parse(String(src.absoluteUntil)) : NaN;
    if (Number.isFinite(fromMs) && Number.isFinite(untilMs) && untilMs >= fromMs) {
      durationDays = Math.max(1, Math.min(31, Math.floor((untilMs - fromMs) / 86400000) + 1));
    }
    return {
      mode: "absolute",
      absoluteFrom: src.absoluteFrom || null,
      absoluteUntil: src.absoluteUntil || null,
      recurrence: null,
      durationDays,
    };
  }
  return {
    mode: "recurring",
    absoluteFrom: null,
    absoluteUntil: null,
    recurrence: src.recurrence || ev?.recurrence || { type: "annual", month: 1, day: 1 },
    durationDays: clampInt(src.durationDays ?? ev?.durationDays, 1, 31, 3),
  };
}

function enrichEvent(ev) {
  if (!ev) return null;
  const schedule = normalizeSchedule(ev.schedule, ev);
  const lobby = normalizeLobby(ev.lobby);
  const contest = normalizeContest(ev.contest);
  // Contest braucht Lobby
  if (contest.enabled && !lobby.enabled) {
    contest.enabled = false;
  }
  const quests = normalizeQuests(ev.quests);
  const collect = normalizeCollect(ev.collect, {
    ...ev,
    lobby,
    contest,
    quests,
  });
  // Mindestens eine Aktivität
  if (!collect.enabled && !quests.length && !contest.enabled) {
    collect.enabled = true;
  }
  return {
    ...ev,
    schedule,
    collect,
    quests,
    lobby,
    contest,
    decor: normalizeDecor(ev.decor, ev.decor),
    // flat fields for backwards compat (absolute behält recurrence-Hinweis für Jahrkalender-Fallback)
    recurrence:
      schedule.mode === "recurring"
        ? schedule.recurrence
        : ev.recurrence || null,
    durationDays:
      schedule.mode === "recurring"
        ? schedule.durationDays
        : schedule.durationDays || ev.durationDays || 1,
    rewardCoinsPerCollect: collect.rewardCoinsPerCollect,
    collectTarget: collect.collectTarget,
    milestoneBonusCoins: collect.milestoneBonusCoins,
    rewardItems: collect.rewardItems || [],
    rewardItem: collect.rewardItem,
  };
}

function parseIsoMs(v) {
  if (!v) return null;
  const t = Date.parse(String(v));
  return Number.isFinite(t) ? t : null;
}

function isActiveEnriched(ev, now = new Date(), baseIsActive) {
  const e = enrichEvent(ev);
  if (!e || e.enabled === false) return false;
  if (e.schedule?.mode === "absolute") {
    const from = parseIsoMs(e.schedule.absoluteFrom);
    const until = parseIsoMs(e.schedule.absoluteUntil);
    const t = now.getTime();
    if (from == null && until == null) return false;
    if (from != null && t < from) return false;
    if (until != null && t > until) return false;
    return true;
  }
  return typeof baseIsActive === "function" ? baseIsActive(ev, now) : false;
}

function windowBounds(ev, now = new Date(), nextOccurrenceFn) {
  const e = enrichEvent(ev);
  if (e.schedule?.mode === "absolute") {
    const from = parseIsoMs(e.schedule.absoluteFrom);
    const until = parseIsoMs(e.schedule.absoluteUntil);
    return {
      start: from ? new Date(from).toISOString().slice(0, 10) : null,
      end: until ? new Date(until).toISOString().slice(0, 10) : null,
      active: isActiveEnriched(ev, now, () => false) || (from != null && until != null && now.getTime() >= from && now.getTime() <= until),
    };
  }
  const occ = typeof nextOccurrenceFn === "function" ? nextOccurrenceFn(ev, now) : null;
  return occ;
}

function ensureContestStore(db) {
  if (!db.eventContest || typeof db.eventContest !== "object") db.eventContest = {};
  return db.eventContest;
}

function contestBucket(db, eventId) {
  const all = ensureContestStore(db);
  const id = String(eventId || "").trim();
  if (!all[id] || typeof all[id] !== "object") {
    all[id] = { entries: [], prizesGranted: false, updatedAt: Date.now() };
  }
  if (!Array.isArray(all[id].entries)) all[id].entries = [];
  return all[id];
}

function ensureUserProgress(user, eventId) {
  if (!user.eventProgress || typeof user.eventProgress !== "object") user.eventProgress = {};
  const id = String(eventId || "").trim();
  if (!user.eventProgress[id] || typeof user.eventProgress[id] !== "object") {
    user.eventProgress[id] = {
      progress: 0,
      lastCollectDay: null,
      claimedMilestone: false,
      itemGranted: false,
      quests: {},
      votedEntryId: null,
      votedEntries: {},
      voterRewarded: false,
      qualifiedStrokes: 0,
      eventLobbyOpens: 0,
      lobbyCreated: false,
      lobbyCode: null,
      eventPrompt: null,
      eventPromptChoices: null,
      claimablePrize: null,
      prizeClaimed: false,
      claimedPlace: null,
    };
  }
  const p = user.eventProgress[id];
  if (!p.quests || typeof p.quests !== "object") p.quests = {};
  if (!p.votedEntries || typeof p.votedEntries !== "object") p.votedEntries = {};
  p.qualifiedStrokes = Math.max(0, Math.floor(Number(p.qualifiedStrokes) || 0));
  p.lobbyCreated = Boolean(p.lobbyCreated || p.lobbyCode);
  if (p.claimedPlace == null && p.prizeClaimed && p.claimablePrize?.place) {
    p.claimedPlace = Math.floor(Number(p.claimablePrize.place) || 0) || null;
  }
  return p;
}

function bumpQuest(user, eventId, metric, amount, applyLedgerFn, giveItemFn, dayKey) {
  const cfgEvent = arguments[7]; // optional full event
  void cfgEvent;
  return null;
}

function bumpEventQuest(db, user, eventObj, metric, amount, applyLedgerFn, giveItemFn) {
  const ev = enrichEvent(eventObj);
  if (!ev || !ev.quests?.length) return { updated: false };
  const add = Math.max(1, Math.floor(Number(amount) || 1));
  const prog = ensureUserProgress(user, ev.id);
  let any = false;
  const completed = [];
  for (const q of ev.quests) {
    if (q.metric !== metric) continue;
    const qp = prog.quests[q.id] || { progress: 0, done: false, claimed: false };
    if (qp.done && qp.claimed) continue;
    qp.progress = Math.min(q.target, (Number(qp.progress) || 0) + add);
    if (qp.progress >= q.target) qp.done = true;
    if (qp.done && !qp.claimed) {
      qp.claimed = true;
      let coins = q.rewardCoins || 0;
      let item = null;
      if (q.rewardItem && typeof giveItemFn === "function") {
        if (giveItemFn(user, q.rewardItem.kind, q.rewardItem.itemId)) {
          item = q.rewardItem;
        }
      }
      if (coins > 0 && typeof applyLedgerFn === "function") {
        applyLedgerFn(user.id, coins, "event_quest", `${ev.id}:${q.id}`);
      }
      completed.push({ questId: q.id, coins, item });
    }
    prog.quests[q.id] = qp;
    any = true;
  }
  return { updated: any, completed };
}

function createOrUpdateEvent(db, raw, { isCreate = false } = {}) {
  const cfg = db.eventsConfig;
  if (!cfg || !Array.isArray(cfg.events)) {
    return { ok: false, error: "no_config", message: "Events nicht geladen" };
  }
  let id = slugId(raw?.id);
  const existing = cfg.events.find((e) => e.id === id);
  if (isCreate && existing) {
    id = `${id}_${crypto.randomBytes(2).toString("hex")}`;
  }
  if (!isCreate && !existing && !raw?.forceCreate) {
    // allow create via upsert
  }
  const base = existing ? { ...existing } : { id, enabled: true, sort: 500, custom: true };
  base.id = id;
  if (raw.title != null) base.title = String(raw.title).slice(0, 60);
  if (raw.emoji != null) base.emoji = String(raw.emoji).slice(0, 8);
  if (raw.description != null) base.description = String(raw.description).slice(0, 240);
  if (raw.hint != null) base.hint = String(raw.hint).slice(0, 200);
  if (raw.enabled != null) base.enabled = Boolean(raw.enabled);
  if (raw.sort != null) base.sort = clampInt(raw.sort, 0, 9999, 500);
  if (raw.durationDays != null) base.durationDays = clampInt(raw.durationDays, 1, 31, 3);
  if (raw.recurrence != null && typeof raw.recurrence === "object") {
    base.recurrence = raw.recurrence;
  }
  if (raw.schedule != null) base.schedule = normalizeSchedule(raw.schedule, base);
  else if (raw.recurrence) {
    base.schedule = normalizeSchedule(
      { mode: "recurring", recurrence: raw.recurrence, durationDays: raw.durationDays },
      base
    );
  }
  if (raw.collect != null) base.collect = normalizeCollect(raw.collect, base);
  if (raw.rewardItems !== undefined || raw.rewardItem !== undefined) {
    const items = publicRewardItems(raw.rewardItems, raw.rewardItem);
    base.rewardItems = items;
    base.rewardItem = items[0] || null;
    base.collect = normalizeCollect(
      {
        ...(base.collect && typeof base.collect === "object" ? base.collect : {}),
        rewardItems: items,
        rewardItem: items[0] || null,
      },
      base
    );
  }
  if (raw.quests != null) base.quests = normalizeQuests(raw.quests);
  if (raw.lobby != null) base.lobby = normalizeLobby(raw.lobby);
  if (raw.contest != null) base.contest = normalizeContest(raw.contest);
  if (raw.decor != null) base.decor = normalizeDecor(raw.decor, base.decor);
  // flat compat from collect
  const enriched = enrichEvent(base);
  Object.assign(base, {
    schedule: enriched.schedule,
    collect: enriched.collect,
    quests: enriched.quests,
    lobby: enriched.lobby,
    contest: enriched.contest,
    decor: enriched.decor,
    recurrence: enriched.recurrence,
    durationDays: enriched.durationDays,
    rewardCoinsPerCollect: enriched.collect.rewardCoinsPerCollect,
    collectTarget: enriched.collect.collectTarget,
    milestoneBonusCoins: enriched.collect.milestoneBonusCoins,
    rewardItems: enriched.collect.rewardItems || [],
    rewardItem: enriched.collect.rewardItem,
    custom: base.custom !== false && !String(id).match(/^(new_year|valentine|carnival|easter|mothers_day|midsummer|autumn|halloween|full_moon|date_night|nikolaus|advent|christmas|silvester)$/),
  });
  if (!base.title) {
    return { ok: false, error: "title_required", message: "Titel fehlt" };
  }
  if (existing) {
    const idx = cfg.events.findIndex((e) => e.id === id);
    cfg.events[idx] = base;
  } else {
    base.custom = true;
    cfg.events.push(base);
  }
  cfg.updatedAt = new Date().toISOString();
  cfg.events.sort((a, b) => (a.sort || 0) - (b.sort || 0));
  return { ok: true, event: enrichEvent(base) };
}

function deleteCustomEvent(db, eventId) {
  const cfg = db.eventsConfig;
  const id = String(eventId || "").trim();
  const ev = cfg?.events?.find((e) => e.id === id);
  if (!ev) return { ok: false, error: "not_found" };
  if (ev.custom === false) {
    return { ok: false, error: "builtin", message: "Builtin-Events können nur deaktiviert werden." };
  }
  // builtins have no custom:true — treat known builtin ids
  const builtins = new Set([
    "new_year", "valentine", "carnival", "easter", "mothers_day", "midsummer",
    "autumn", "halloween", "full_moon", "date_night", "nikolaus", "advent", "christmas", "silvester",
  ]);
  if (builtins.has(id) && ev.custom !== true) {
    ev.enabled = false;
    return { ok: true, disabled: true };
  }
  cfg.events = cfg.events.filter((e) => e.id !== id);
  return { ok: true, deleted: true };
}

/** Einheitliches Event-Fenster-Ende (ISO), wie bei Event-Lobby-Create. */
function eventWindowEndIso(ev, now = new Date()) {
  const enriched = enrichEvent(ev);
  if (enriched.schedule?.mode === "absolute" && enriched.schedule.absoluteUntil) {
    return String(enriched.schedule.absoluteUntil);
  }
  // Caller kann nextOccurrence-end als YYYY-MM-DD liefern
  return null;
}

function eventWindowEndIsoFromOccEnd(occEnd) {
  if (!occEnd) return null;
  const s = String(occEnd);
  if (s.includes("T")) return s;
  const parts = s.split("-").map((x) => Number(x));
  if (parts.length === 3 && parts.every((n) => Number.isFinite(n))) {
    // Ende des Event-Tages (ca. 23:59 Berlin ≈ 21:59 UTC)
    return new Date(Date.UTC(parts[0], parts[1] - 1, parts[2], 21, 59, 59, 999)).toISOString();
  }
  return null;
}

/** Vote-Fenster: Event-Ende … +24h, sofern Admin nichts setzt. */
function resolveVoteBounds(contest, windowEndIso, now = Date.now()) {
  let from = parseIsoMs(contest?.voteFrom);
  let until = parseIsoMs(contest?.voteUntil);
  const endMs = parseIsoMs(windowEndIso);
  if (from == null && endMs != null) from = endMs;
  if (until == null && endMs != null) until = endMs + 24 * 60 * 60 * 1000;
  return { from, until };
}

function voteWindowOpen(contest, eventActive, now = Date.now(), windowEndIso = null) {
  if (!contest?.enabled) return false;
  // Während Event läuft: noch keine Abstimmung
  if (eventActive) return false;
  const endIso = windowEndIso && String(windowEndIso).includes("T")
    ? windowEndIso
    : eventWindowEndIsoFromOccEnd(windowEndIso);
  const { from, until } = resolveVoteBounds(contest, endIso, now);
  if (from == null && until == null) {
    return !eventActive;
  }
  if (from != null && now < from) return false;
  if (until != null && now > until) return false;
  return true;
}

/**
 * Gewinner-Phase: nach Abstimmungsende bis 24h vor dem nächsten Event
 * (irgendein kommendes Event im Kalender). Ohne nächstes Event: weiter sichtbar.
 */
function winnersShowcaseOpen(contest, windowEndIso, now = Date.now(), nextEventFromMs = null) {
  if (!contest?.enabled) return false;
  const endIso = windowEndIso && String(windowEndIso).includes("T")
    ? windowEndIso
    : eventWindowEndIsoFromOccEnd(windowEndIso);
  const { until: voteUntil } = resolveVoteBounds(contest, endIso, now);
  if (voteUntil == null || now <= voteUntil) return false;
  const nextFrom = Number(nextEventFromMs);
  if (Number.isFinite(nextFrom) && nextFrom > 0) {
    return now < nextFrom - 24 * 60 * 60 * 1000;
  }
  return true;
}

function canUserVote(user, eventObj, entryId) {
  const ev = enrichEvent(eventObj);
  const prog = ensureUserProgress(user, ev.id);
  const eid = String(entryId || "").trim();
  if (eid && prog.votedEntries[eid] != null) {
    return { ok: false, error: "already_voted", message: "Bereits abgestimmt." };
  }
  const req = ev.contest?.voteRequire || {};
  if (req.drewInEventLobby) {
    const need = clampInt(req.minStrokes, 0, 500, 5);
    if ((prog.qualifiedStrokes || 0) < need) {
      return {
        ok: false,
        error: "not_eligible",
        message: `Zuerst in der Event-Lobby zeichnen (mind. ${need} Striche).`,
      };
    }
  }
  return { ok: true };
}

function submitContestEntry(db, user, eventObj, payload) {
  const ev = enrichEvent(eventObj);
  if (!ev.contest?.enabled) {
    return { ok: false, error: "no_contest", message: "Kein Wettbewerb aktiv." };
  }
  const bucket = contestBucket(db, ev.id);
  const max = ev.contest.maxEntriesPerUser || 1;
  const mine = bucket.entries.filter((e) => e.userId === user.id);
  if (mine.length >= max) {
    return { ok: false, error: "max_entries", message: "Bereits eingereicht.", entry: mine[0] };
  }
  const strokes = Math.max(0, Math.floor(Number(payload.strokes) || 0));
  const minQ = ev.lobby?.minStrokesToQualify || 0;
  if (strokes < minQ) {
    return {
      ok: false,
      error: "too_few_strokes",
      message: `Mindestens ${minQ} Striche nötig.`,
    };
  }
  const entryId = `ent_${crypto.randomBytes(6).toString("hex")}`;
  const entry = {
    entryId,
    userId: user.id,
    nickname: String(user.nickname || "Jemand").slice(0, 18),
    lobbyCode: String(payload.lobbyCode || "").slice(0, 16),
    imagePath: payload.imagePath || null,
    prompt: String(payload.prompt || "").slice(0, 80) || null,
    strokes,
    createdAt: Date.now(),
    score: 0,
    votes: {},
  };
  bucket.entries.push(entry);
  bucket.updatedAt = Date.now();
  const prog = ensureUserProgress(user, ev.id);
  prog.qualifiedStrokes = Math.max(prog.qualifiedStrokes || 0, strokes);
  if (payload.prompt) prog.eventPrompt = String(payload.prompt).slice(0, 80);
  return { ok: true, entry };
}

function entryScore(entry) {
  if (entry && typeof entry.votes === "object" && entry.votes) {
    let s = 0;
    for (const v of Object.values(entry.votes)) {
      const n = Number(v);
      if (n === 1 || n === -1) s += n;
    }
    return s;
  }
  return Number(entry?.score ?? entry?.votes) || 0;
}

/** Anzahl abgegebener Stimmen auf einem Beitrag (für faire Verteilung). */
function entryVoteCount(entry) {
  if (!entry?.votes || typeof entry.votes !== "object") return 0;
  let n = 0;
  for (const v of Object.values(entry.votes)) {
    const x = Number(v);
    if (x === 1 || x === -1) n += 1;
  }
  return n;
}

/** Max. Bilder, die ein Nutzer pro Event bewerten / überspringen darf. */
const MAX_CONTEST_VOTES_PER_USER = 100;

function userContestVoteCount(prog) {
  if (!prog?.votedEntries || typeof prog.votedEntries !== "object") return 0;
  return Object.keys(prog.votedEntries).length;
}

function votesRemainingFor(prog) {
  return Math.max(0, MAX_CONTEST_VOTES_PER_USER - userContestVoteCount(prog));
}

/**
 * Beitrag als gesehen markieren (Vote ±1 oder Skip/Report 0), ohne Score-Änderung.
 * Zählt gegen das 100er-Limit.
 */
function markContestEntrySeen(user, eventId, entryId, value = 0) {
  const prog = ensureUserProgress(user, eventId);
  const eid = String(entryId || "").trim();
  if (!eid) return { ok: false };
  if (prog.votedEntries[eid] != null) return { ok: true, already: true };
  if (userContestVoteCount(prog) >= MAX_CONTEST_VOTES_PER_USER) {
    return { ok: false, error: "vote_limit", message: "Bewertungslimit erreicht (100)." };
  }
  const v = Number(value) === -1 ? -1 : Number(value) === 1 ? 1 : 0;
  prog.votedEntries[eid] = v;
  if (v === 1 || v === -1) prog.votedEntryId = eid;
  return { ok: true };
}

function castVote(db, user, eventObj, entryId, value, applyLedgerFn, eventActive, windowEndIso) {
  const ev = enrichEvent(eventObj);
  const voteVal = Number(value) === -1 ? -1 : Number(value) === 1 ? 1 : 0;
  if (!voteVal) {
    return { ok: false, error: "bad_value", message: "value muss +1 oder −1 sein." };
  }
  if (!voteWindowOpen(ev.contest, eventActive, Date.now(), windowEndIso)) {
    return { ok: false, error: "vote_closed", message: "Abstimmung ist nicht offen." };
  }
  const prog = ensureUserProgress(user, ev.id);
  if (userContestVoteCount(prog) >= MAX_CONTEST_VOTES_PER_USER) {
    return {
      ok: false,
      error: "vote_limit",
      message: "Du hast schon 100 Bilder bewertet.",
    };
  }
  const elig = canUserVote(user, ev, entryId);
  if (!elig.ok) return elig;
  const bucket = contestBucket(db, ev.id);
  const entry = bucket.entries.find((e) => e.entryId === entryId);
  if (!entry) return { ok: false, error: "not_found", message: "Beitrag nicht gefunden." };
  if (entry.userId === user.id) {
    return { ok: false, error: "self_vote", message: "Eigenes Bild nicht wählbar." };
  }
  if (!entry.votes || typeof entry.votes !== "object") entry.votes = {};
  entry.votes[user.id] = voteVal;
  entry.score = entryScore(entry);
  prog.votedEntries[entryId] = voteVal;
  prog.votedEntryId = entryId;
  let voterCoins = 0;
  if ((ev.contest.voterRewardCoins || 0) > 0 && !prog.voterRewarded) {
    voterCoins = ev.contest.voterRewardCoins;
    prog.voterRewarded = true;
    if (typeof applyLedgerFn === "function") {
      applyLedgerFn(user.id, voterCoins, "event_vote", `${ev.id}:${entryId}`);
    }
  }
  bucket.updatedAt = Date.now();
  return {
    ok: true,
    score: entry.score,
    voterCoins,
    votesRemaining: votesRemainingFor(prog),
    votesUsed: userContestVoteCount(prog),
    votesMax: MAX_CONTEST_VOTES_PER_USER,
  };
}

/**
 * Ranking speichern + Claim freischalten (ohne Auto-Gutschrift).
 */
function finalizeContestPrizes(db, eventObj, usersById) {
  const ev = enrichEvent(eventObj);
  if (!ev.contest?.enabled) return { ok: false, skipped: true };
  const bucket = contestBucket(db, ev.id);
  if (bucket.prizesReady || bucket.prizesGranted) {
    return { ok: true, already: true, ranking: bucket.ranking || [] };
  }
  const ranked = [...bucket.entries].sort(
    (a, b) => entryScore(b) - entryScore(a) || a.createdAt - b.createdAt
  );
  const ranking = ranked.map((e, i) => ({
    place: i + 1,
    entryId: e.entryId,
    userId: e.userId,
    nickname: e.nickname,
    score: entryScore(e),
    prompt: e.prompt || null,
    imagePath: e.imagePath || null,
  }));
  bucket.ranking = ranking;
  bucket.prizesReady = true;
  bucket.updatedAt = Date.now();
  for (const place of ev.contest.places || []) {
    const row = ranking[place.place - 1];
    if (!row) continue;
    const u = usersById?.[row.userId];
    if (!u) continue;
    const prog = ensureUserProgress(u, ev.id);
    if (prog.prizeClaimed) continue;
    prog.claimablePrize = {
      place: place.place,
      coins: place.coins || 0,
      rewardItem: place.rewardItem || null,
      grantMedal: place.place === 1,
    };
  }
  // Home-Feed: Top-3
  try {
    const homeFeed = require("./home_feed");
    const title = String(ev.title || ev.id || "Event").slice(0, 40);
    for (const row of ranking.slice(0, 3)) {
      const placeLabel =
        row.place === 1 ? "🥇" : row.place === 2 ? "🥈" : row.place === 3 ? "🥉" : `#${row.place}`;
      homeFeed.publish(db, {
        kind: "event_win",
        shortText: `${placeLabel} ${row.nickname} · ${title}`,
        title: `${row.nickname} hat Platz ${row.place} bei ${title}`,
        body:
          `${row.nickname} hat beim Event „${title}“ Platz ${row.place} erreicht` +
          (row.prompt ? ` mit „${String(row.prompt).slice(0, 60)}“.` : ".") +
          ` Tippe, um das Bild anzusehen.`,
        ttlMs: homeFeed.DEFAULT_TTL_MS,
        actionType: "contest_image",
        actionPayload: {
          eventId: ev.id,
          entryId: row.entryId,
          nickname: row.nickname,
          place: row.place,
        },
      });
    }
  } catch (e) {
    console.error("home feed contest publish failed", e);
  }
  return { ok: true, ranking };
}

function claimContestPrize(db, user, eventObj, applyLedgerFn, giveItemFn) {
  const ev = enrichEvent(eventObj);
  if (!ev.contest?.enabled) {
    return { ok: false, error: "no_contest", message: "Kein Wettbewerb." };
  }
  const bucket = contestBucket(db, ev.id);
  if (!bucket.prizesReady && !bucket.prizesGranted) {
    return { ok: false, error: "not_ready", message: "Ergebnisse noch nicht da." };
  }
  const prog = ensureUserProgress(user, ev.id);
  if (prog.prizeClaimed) {
    return { ok: false, error: "already_claimed", message: "Bereits abgeholt." };
  }
  const prize = prog.claimablePrize;
  if (!prize || !(prize.coins > 0 || prize.rewardItem || prize.grantMedal)) {
    return { ok: false, error: "not_winner", message: "Keine Belohnung für dich." };
  }
  let coins = Math.max(0, Math.floor(Number(prize.coins) || 0));
  const items = [];
  if (coins > 0 && typeof applyLedgerFn === "function") {
    applyLedgerFn(user.id, coins, "event_contest_prize", `${ev.id}:p${prize.place}`);
  }
  if (prize.rewardItem && typeof giveItemFn === "function") {
    if (giveItemFn(user, prize.rewardItem.kind, prize.rewardItem.itemId)) {
      items.push(prize.rewardItem);
    }
  }
  if (prize.grantMedal && typeof giveItemFn === "function") {
    const hasSticker = items.some((it) => it.kind === "stickers" && it.itemId === "🥇");
    if (!hasSticker && giveItemFn(user, "stickers", "🥇")) {
      items.push({ kind: "stickers", itemId: "🥇", emoji: "🥇", label: "Goldmedaille" });
    }
    if (giveItemFn(user, "emojis", "🥇")) {
      items.push({ kind: "emojis", itemId: "🥇", emoji: "🥇", label: "Goldmedaille" });
    }
  }
  prog.prizeClaimed = true;
  prog.claimedPlace = Math.floor(Number(prize.place) || 0) || null;
  prog.claimablePrize = null;
  return { ok: true, coinsGranted: coins, place: prize.place, items };
}

function nextFeedEntry(db, user, eventObj) {
  const ev = enrichEvent(eventObj);
  const prog = ensureUserProgress(user, ev.id);
  if (userContestVoteCount(prog) >= MAX_CONTEST_VOTES_PER_USER) return null;
  const bucket = contestBucket(db, ev.id);
  const candidates = bucket.entries.filter(
    (e) => e && e.userId !== user.id && prog.votedEntries[e.entryId] == null
  );
  if (!candidates.length) return null;
  // Fairness: Bilder mit den wenigsten Bewertungen zuerst
  let minVotes = Infinity;
  for (const e of candidates) {
    const c = entryVoteCount(e);
    if (c < minVotes) minVotes = c;
  }
  const pool = candidates.filter((e) => entryVoteCount(e) === minVotes);
  // Bei Gleichstand: älteres Bild zuerst, leichte Streuung
  pool.sort((a, b) => {
    const t = (a.createdAt || 0) - (b.createdAt || 0);
    if (t !== 0) return t;
    return String(a.entryId).localeCompare(String(b.entryId));
  });
  const pick =
    pool.length <= 1
      ? pool[0]
      : pool[Math.floor(Math.random() * Math.min(3, pool.length))];
  if (!pick) return null;
  return {
    entryId: pick.entryId,
    nickname: pick.nickname,
    prompt: pick.prompt || prog.eventPrompt || null,
    imageUrl: pick.imagePath
      ? `/v1/me/events/${ev.id}/contest/entries/${pick.entryId}/image`
      : null,
    strokes: pick.strokes || 0,
  };
}

function contestPublicForUser(db, user, eventObj, windowStart, windowEnd, active, now = Date.now()) {
  const ev = enrichEvent(eventObj);
  if (!ev.contest?.enabled) {
    return { enabled: false };
  }
  const prog = ensureUserProgress(user, ev.id);
  const bucket = contestBucket(db, ev.id);
  const votingOpen = voteWindowOpen(ev.contest, active, now, windowEnd);
  const { from, until } = resolveVoteBounds(ev.contest, windowEnd, now);
  const voteEnded = until != null && now > until;
  const req = ev.contest?.voteRequire || {};
  let canVoteBase = true;
  if (req.drewInEventLobby) {
    const need = clampInt(req.minStrokes, 0, 500, 5);
    canVoteBase = (prog.qualifiedStrokes || 0) >= need;
  }
  const votesUsed = userContestVoteCount(prog);
  const votesRemaining = votesRemainingFor(prog);
  const underLimit = votesRemaining > 0;
  const feed = votingOpen && underLimit ? nextFeedEntry(db, user, ev) : null;
  const myEntry = bucket.entries.find((e) => e.userId === user.id) || null;
  let winners = [];
  if ((bucket.prizesReady || bucket.prizesGranted || voteEnded) && Array.isArray(bucket.ranking)) {
    winners = bucket.ranking.slice(0, 3).map((w) => ({
      place: w.place,
      nickname: w.nickname,
      entryId: w.entryId,
      prompt: w.prompt,
      imageUrl: w.imagePath
        ? `/v1/me/events/${ev.id}/contest/entries/${w.entryId}/image`
        : null,
      score: w.score,
    }));
  }
  const claimable = Boolean(prog.claimablePrize) && !prog.prizeClaimed;
  let myPlace = null;
  if (Array.isArray(bucket.ranking)) {
    const mine = bucket.ranking.find((r) => r && r.userId === user.id);
    if (mine?.place) myPlace = Math.floor(Number(mine.place) || 0) || null;
  }
  if (myPlace == null && prog.claimablePrize?.place) {
    myPlace = Math.floor(Number(prog.claimablePrize.place) || 0) || null;
  }
  if (myPlace == null && prog.claimedPlace) {
    myPlace = Math.floor(Number(prog.claimedPlace) || 0) || null;
  }
  let phase = "live";
  if (!active) {
    if (votingOpen) phase = "voting";
    else if (voteEnded || bucket.prizesReady || bucket.prizesGranted) phase = "winners";
    else phase = "ended";
  }
  return {
    enabled: true,
    phase,
    votingOpen,
    voteFrom: from != null ? new Date(from).toISOString() : null,
    voteUntil: until != null ? new Date(until).toISOString() : null,
    canVote: Boolean(votingOpen && canVoteBase && feed && underLimit),
    feedItem: feed,
    votesUsed,
    votesRemaining,
    votesMax: MAX_CONTEST_VOTES_PER_USER,
    myEntry: myEntry
      ? {
          entryId: myEntry.entryId,
          prompt: myEntry.prompt,
          strokes: myEntry.strokes,
          score: entryScore(myEntry),
          imageUrl: myEntry.imagePath
            ? `/v1/me/events/${ev.id}/contest/entries/${myEntry.entryId}/image`
            : null,
        }
      : null,
    myPlace,
    winners,
    claimablePrize: claimable ? prog.claimablePrize : null,
    prizeClaimed: Boolean(prog.prizeClaimed),
    entryCount: bucket.entries.length,
    qualifiedStrokes: prog.qualifiedStrokes || 0,
    prizesReady: Boolean(bucket.prizesReady || bucket.prizesGranted),
    promptHint: prog.eventPrompt || null,
    lobbyCreated: Boolean(prog.lobbyCreated),
    lobbyCode: prog.lobbyCode || null,
  };
}

function noteEventLobbyStroke(user, eventId, amount = 1) {
  const prog = ensureUserProgress(user, eventId);
  const add = Math.max(1, Math.floor(Number(amount) || 1));
  prog.qualifiedStrokes = (prog.qualifiedStrokes || 0) + add;
  return prog.qualifiedStrokes;
}

function publicEventModules(ev, user, dayKey, now, helpers) {
  const e = enrichEvent(ev);
  const active = helpers.isActive(ev, now);
  const occ = helpers.nextOccurrence(ev, now);
  const prog = user ? ensureUserProgress(user, e.id) : null;
  const quests = (e.quests || []).map((q) => {
    const qp = prog?.quests?.[q.id] || { progress: 0, done: false, claimed: false };
    return {
      ...q,
      progress: Math.min(q.target, Number(qp.progress) || 0),
      done: Boolean(qp.done),
      claimed: Boolean(qp.claimed),
    };
  });
  const contestOpen = voteWindowOpen(e.contest, active, now.getTime(), occ?.end || e.schedule?.absoluteUntil || null);
  return {
    id: e.id,
    title: e.title,
    emoji: e.emoji,
    description: e.description,
    hint: e.hint,
    enabled: e.enabled !== false,
    sort: e.sort || 0,
    schedule: e.schedule,
    collect: {
      ...e.collect,
      progress: prog?.progress || 0,
      collectedToday: prog?.lastCollectDay === dayKey,
      claimedMilestone: Boolean(prog?.claimedMilestone),
      itemGranted: Boolean(prog?.itemGranted),
      canCollect: Boolean(active && e.collect.enabled && prog?.lastCollectDay !== dayKey),
    },
    quests,
    lobby: e.lobby,
    contest: e.contest.enabled
      ? {
          ...e.contest,
          votingOpen: contestOpen,
          entryCount: contestBucket(helpers.db, e.id).entries.length,
          hasVoted: Object.keys(prog?.votedEntries || {}).length > 0 || Boolean(prog?.votedEntryId),
          qualifiedStrokes: prog?.qualifiedStrokes || 0,
          prizesGranted: Boolean(contestBucket(helpers.db, e.id).prizesGranted || contestBucket(helpers.db, e.id).prizesReady),
          lobbyCreated: Boolean(prog?.lobbyCreated),
        }
      : { enabled: false },
    decor: e.decor,
    windowStart: occ?.start || e.schedule?.absoluteFrom || null,
    windowEnd: occ?.end || e.schedule?.absoluteUntil || null,
    active,
    custom: Boolean(e.custom),
  };
}

module.exports = {
  QUEST_METRICS,
  MAX_CONTEST_VOTES_PER_USER,
  enrichEvent,
  normalizeDecor,
  normalizeLobby,
  normalizeContest,
  normalizeQuests,
  normalizeCollect,
  normalizeSchedule,
  isActiveEnriched,
  createOrUpdateEvent,
  deleteCustomEvent,
  ensureUserProgress,
  bumpEventQuest,
  contestBucket,
  submitContestEntry,
  castVote,
  markContestEntrySeen,
  finalizeContestPrizes,
  claimContestPrize,
  contestPublicForUser,
  noteEventLobbyStroke,
  pickEventPrompt,
  sampleEventPrompts,
  defaultContestPlaces,
  voteWindowOpen,
  winnersShowcaseOpen,
  resolveVoteBounds,
  eventWindowEndIso,
  eventWindowEndIsoFromOccEnd,
  entryScore,
  entryVoteCount,
  publicEventModules,
  publicRewardItem,
  publicRewardItems,
  slugId,
};

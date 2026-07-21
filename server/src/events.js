/**
 * Jahres-Events: wiederkehrende Fenster, tägliches Sammeln, Decor-Presets.
 * Persistenz: db.eventsConfig + user.eventProgress
 */

const engine = require("./event_engine");

const DEFAULT_REWARD = 2;
const REWARD_MIN = 0;
const REWARD_MAX = 50;

function decor(particles, accentHex, bannerText, intensity = 0.55, ornaments = "none") {
  return { particles, accentHex, bannerText, intensity, ornaments };
}

function rewardItem(kind, itemId, emoji, label) {
  return { kind, itemId, emoji: emoji || itemId, label: label || itemId };
}

/** Items die nicht im Shop liegen, sondern über Events (weiterhin handelbar).
 *  Keys als "kind:itemId" — sonst würden Pets wie 🐰/🦌 fälschlich gesperrt. */
const EVENT_ONLY_KEYS = new Set([
  "emojis:💘",
  "emojis:🐰",
  "emojis:🎃",
  "emojis:🎅",
  "emojis:🎄",
  "emojis:💝",
  "stickers:💝",
  "stickers:🕯️",
  "stickers:🎄",
  "stickers:🎅",
  "stickers:🤶",
  "stickers:🎃",
  "stickers:🐰",
  "stickers:🥚",
  "stickers:🦌",
  "stickers:⛄",
  "stickers:❄️",
  "stickers:💘",
  "stickers:💒",
  "emojis:🥇",
  "stickers:🥇",
]);

/** @deprecated use EVENT_ONLY_KEYS — nur noch für Kompatibilität exportiert */
const EVENT_ONLY_ITEM_IDS = new Set(
  [...EVENT_ONLY_KEYS].map((k) => k.split(":").slice(1).join(":"))
);

function normalizeItemId(itemId) {
  return String(itemId || "").trim().replace(/\uFE0F/g, "");
}

function isEventOnlyItem(kind, itemId) {
  const k = String(kind || "").trim().toLowerCase();
  const id = normalizeItemId(itemId);
  if (!k || !id) return false;
  if (EVENT_ONLY_KEYS.has(`${k}:${id}`)) return true;
  // Mit VS16-Variante
  const raw = String(itemId || "").trim();
  if (raw !== id && EVENT_ONLY_KEYS.has(`${k}:${raw}`)) return true;
  return false;
}

/**
 * Shop-Items mit Event-Bindung (Begleiter/Sticker/Emoji/Hintergrund) —
 * im Event-Fenster kaufbar, aber nie in der Lootbox.
 */
function isEventShopBoundItem(db, kind, itemId) {
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim();
  if (!k || !id) return false;
  if (/^img_ev_/i.test(id) || /^img_event_/i.test(id)) return true;
  if (k === "themes" && /^theme_[a-z0-9_]+_t\d{4}$/i.test(id)) return true;
  if (!db) return false;
  try {
    const shopCatalog = require("./shop_catalog");
    const item = shopCatalog.getItem(db, k, id);
    if (item && String(item.eventId || "").trim()) return true;
  } catch {
    /* ignore */
  }
  return false;
}

/** Builtin-Events — Admin kann Felder überschreiben, fehlende IDs werden nachgezogen. */
const BUILTIN_EVENTS = [
  {
    id: "new_year",
    title: "Neujahr",
    emoji: "🎆",
    description: "Ein frischer Start zu zweit — hol dir heute einen Funken Glück.",
    hint: "Tippe auf Sammeln und feiert den ersten Tag des Jahres.",
    recurrence: { type: "annual", month: 1, day: 1 },
    durationDays: 2,
    rewardCoinsPerCollect: 3,
    collectTarget: 2,
    milestoneBonusCoins: 5,
    sort: 10,
    decor: decor("sparkle", "#F5C542", "Frohes neues Jahr!", 0.7, "spark"),
  },
  {
    id: "valentine",
    title: "Valentinstag",
    emoji: "💝",
    description: "Kleine Herzen, große Geste — sammelt Liebe für euren Tag.",
    hint: "Jeden Tag im Fenster einmal sammeln.",
    recurrence: { type: "annual", month: 2, day: 14 },
    durationDays: 3,
    rewardCoinsPerCollect: 3,
    collectTarget: 3,
    milestoneBonusCoins: 8,
    rewardItem: rewardItem("emojis", "💘", "💘", "Liebespfeil"),
    sort: 20,
    decor: decor("hearts", "#E94E77", "Happy Valentine's", 0.75, "hearts"),
  },
  {
    id: "carnival",
    title: "Fasching",
    emoji: "🎭",
    description: "Bunte Tage — verkleidet eure Stimmung mit einer kleinen Belohnung.",
    hint: "Sammeln während der Faschings-Tage.",
    recurrence: { type: "easter_offset", days: -48 },
    durationDays: 4,
    rewardCoinsPerCollect: 2,
    collectTarget: 4,
    milestoneBonusCoins: 6,
    sort: 30,
    decor: decor("sparkle", "#FF8A65", "Helau!", 0.6, "none"),
  },
  {
    id: "easter",
    title: "Ostern",
    emoji: "🐣",
    description: "Ostereier suchen — hier digital: täglich ein kleines Nest.",
    hint: "Über die Oster-Tage einmal pro Tag sammeln.",
    recurrence: { type: "easter_offset", days: -1 },
    durationDays: 4,
    rewardCoinsPerCollect: 3,
    collectTarget: 4,
    milestoneBonusCoins: 10,
    rewardItem: rewardItem("emojis", "🐰", "🐰", "Osterhase"),
    sort: 40,
    decor: decor("sparkle", "#A5D6A7", "Frohe Ostern", 0.55, "none"),
  },
  {
    id: "mothers_day",
    title: "Muttertag",
    emoji: "🌷",
    description: "Ein Stillmoment für Fürsorge — teilt Wärme in der Lobby.",
    hint: "Am Muttertags-Wochenende sammeln.",
    recurrence: { type: "nth_weekday", month: 5, weekday: 0, nth: 2 },
    durationDays: 2,
    rewardCoinsPerCollect: 2,
    collectTarget: 2,
    milestoneBonusCoins: 5,
    sort: 50,
    decor: decor("leaves", "#F48FB1", "Danke sagen", 0.5, "none"),
  },
  {
    id: "midsummer",
    title: "Sommerliebe",
    emoji: "☀️",
    description: "Lange Abende, warme Farben — sammelt Sonnenfunken.",
    hint: "Um den Mittsommer herum täglich sammeln.",
    recurrence: { type: "annual", month: 6, day: 21 },
    durationDays: 5,
    rewardCoinsPerCollect: 2,
    collectTarget: 5,
    milestoneBonusCoins: 8,
    sort: 60,
    decor: decor("sparkle", "#FFB74D", "Sommerliebe", 0.55, "none"),
  },
  {
    id: "autumn",
    title: "Herbstbeginn",
    emoji: "🍂",
    description: "Blätter fallen, Kuschelstimmung steigt — sammelt goldene Blätter.",
    hint: "In der ersten Herbstwoche sammeln.",
    recurrence: { type: "annual", month: 9, day: 22 },
    durationDays: 5,
    rewardCoinsPerCollect: 2,
    collectTarget: 5,
    milestoneBonusCoins: 7,
    sort: 70,
    decor: decor("leaves", "#D4A574", "Herbstzeit", 0.55, "none"),
  },
  {
    id: "halloween",
    title: "Halloween",
    emoji: "🎃",
    description: "Süßes oder Saures — hier nur Süßes: tägliche Leckereien.",
    hint: "Um Halloween herum einmal täglich sammeln.",
    recurrence: { type: "annual", month: 10, day: 31 },
    durationDays: 3,
    rewardCoinsPerCollect: 3,
    collectTarget: 3,
    milestoneBonusCoins: 8,
    rewardItem: rewardItem("emojis", "🎃", "🎃", "Kürbis"),
    sort: 80,
    decor: decor("sparkle", "#FF7043", "Buuh!", 0.65, "none"),
  },
  {
    id: "full_moon",
    title: "Vollmond-Nacht",
    emoji: "🌕",
    description: "Einmal im Monat: unter dem Mond etwas Besonderes teilen.",
    hint: "Am Vollmond-Abend einmal sammeln.",
    recurrence: { type: "full_moon" },
    durationDays: 1,
    rewardCoinsPerCollect: 4,
    collectTarget: 1,
    milestoneBonusCoins: 0,
    sort: 85,
    decor: decor("sparkle", "#B0BEC5", "Vollmond", 0.5, "spark"),
  },
  {
    id: "date_night",
    title: "Date-Night Freitag",
    emoji: "🍷",
    description: "Jeden Freitagabend: ein kleiner Anlass für euch beide.",
    hint: "Freitags ab 18 Uhr bis Samstag 1 Uhr einmal sammeln.",
    recurrence: {
      type: "weekly",
      weekday: 5,
      startHour: 18,
      startMinute: 0,
      endHour: 1,
      endMinute: 0,
      endDayOffset: 1,
    },
    durationDays: 1,
    rewardCoinsPerCollect: 2,
    collectTarget: 1,
    milestoneBonusCoins: 4,
    rewardItem: rewardItem("stickers", "💝", "💝", "Herz-Geschenk"),
    sort: 88,
    decor: decor("hearts", "#CE93D8", "Date Night", 0.4, "none"),
  },
  {
    id: "nikolaus",
    title: "Nikolaus",
    emoji: "🎅",
    description: "Stiefel raus — hier gibt's eine digitale Überraschung.",
    hint: "Am 6. Dezember sammeln.",
    recurrence: { type: "annual", month: 12, day: 6 },
    durationDays: 1,
    rewardCoinsPerCollect: 5,
    collectTarget: 1,
    milestoneBonusCoins: 0,
    rewardItem: rewardItem("emojis", "🎅", "🎅", "Nikolaus"),
    sort: 90,
    decor: decor("snow", "#90CAF9", "Nikolaus", 0.6, "none"),
  },
  {
    id: "advent",
    title: "Adventsfenster",
    emoji: "🕯️",
    description: "Jeden Tag ein Fenster öffnen — sammelt bis Weihnachten Sterne.",
    hint: "Vom 1. bis 24. Dezember täglich einmal sammeln.",
    recurrence: { type: "range", startMonth: 12, startDay: 1, endMonth: 12, endDay: 24 },
    durationDays: 1,
    rewardCoinsPerCollect: 2,
    collectTarget: 12,
    milestoneBonusCoins: 15,
    rewardItem: rewardItem("stickers", "🕯️", "🕯️", "Adventskerze"),
    sort: 100,
    decor: decor("snow", "#E94E77", "Advent", 0.7, "wreath"),
  },
  {
    id: "christmas",
    title: "Weihnachten",
    emoji: "🎄",
    description: "Besinnliche Tage — sammelt Weihnachtssterne zu zweit.",
    hint: "An Heiligabend und den Feiertagen sammeln.",
    recurrence: { type: "annual", month: 12, day: 24 },
    durationDays: 3,
    rewardCoinsPerCollect: 4,
    collectTarget: 3,
    milestoneBonusCoins: 12,
    rewardItem: rewardItem("emojis", "🎄", "🎄", "Weihnachtsbaum"),
    sort: 110,
    decor: decor("snow", "#C8E6C9", "Frohe Weihnachten", 0.8, "wreath"),
  },
  {
    id: "silvester",
    title: "Silvester",
    emoji: "✨",
    description: "Das Jahr ausklingen lassen — ein letzter Funke für euch.",
    hint: "Am 31. Dezember sammeln.",
    recurrence: { type: "annual", month: 12, day: 31 },
    durationDays: 1,
    rewardCoinsPerCollect: 5,
    collectTarget: 1,
    milestoneBonusCoins: 0,
    sort: 120,
    decor: decor("sparkle", "#FFD54F", "Prost Neujahr!", 0.75, "spark"),
  },
];

function clampReward(n, fallback = DEFAULT_REWARD) {
  const v = Math.floor(Number(n));
  if (!Number.isFinite(v)) return fallback;
  return Math.max(REWARD_MIN, Math.min(REWARD_MAX, v));
}

function clampTarget(n) {
  const v = Math.floor(Number(n));
  if (!Number.isFinite(v)) return 3;
  return Math.max(1, Math.min(31, v));
}

function clampIntensity(n) {
  const v = Number(n);
  if (!Number.isFinite(v)) return 0.55;
  return Math.max(0, Math.min(1, v));
}

const TZ_BERLIN = "Europe/Berlin";

function berlinParts(date = new Date()) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", {
      timeZone: TZ_BERLIN,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      weekday: "short",
      hour: "2-digit",
      minute: "2-digit",
      hourCycle: "h23",
    })
      .formatToParts(date)
      .filter((p) => p.type !== "literal")
      .map((p) => [p.type, p.value])
  );
  const wdMap = { Sun: 0, Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6 };
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    weekday: wdMap[parts.weekday] ?? 0,
  };
}

function berlinYmd(date = new Date()) {
  const p = berlinParts(date);
  return `${p.year}-${String(p.month).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`;
}

/** UTC-Instant für Mitternacht Europe/Berlin an einem Kalendertag. */
function berlinDayStartMs(y, m, d) {
  const desired = `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  let utc = Date.UTC(y, m - 1, d, 0, 0, 0);
  for (let i = 0; i < 4; i++) {
    const got = berlinYmd(new Date(utc));
    if (got === desired) {
      // Feintuning auf 00:00 Berlin
      const p = berlinParts(new Date(utc));
      const hour = Number(
        new Intl.DateTimeFormat("en-US", {
          timeZone: TZ_BERLIN,
          hour: "2-digit",
          hourCycle: "h23",
        }).format(new Date(utc))
      );
      utc -= hour * 3600000;
      const min = Number(
        new Intl.DateTimeFormat("en-US", {
          timeZone: TZ_BERLIN,
          minute: "2-digit",
        }).format(new Date(utc))
      );
      utc -= min * 60000;
      if (berlinYmd(new Date(utc)) === desired) return utc;
    }
    const [gy, gm, gd] = got.split("-").map(Number);
    const deltaDays =
      Math.round((Date.UTC(y, m - 1, d) - Date.UTC(gy, gm - 1, gd)) / 86400000) || 0;
    utc += deltaDays * 86400000;
  }
  return Date.UTC(y, m - 1, d, 0, 0, 0);
}

function utcDay(y, m, d) {
  // Kalendertag als Berlin-Mittag (Anzeige/Arithmetik), Fenster-Starts via berlinDayStartMs
  return new Date(berlinDayStartMs(y, m, d) + 12 * 3600000);
}

function ymd(date) {
  if (date instanceof Date) return berlinYmd(date);
  return berlinYmd(new Date(date));
}

function addDays(date, days) {
  const p = berlinParts(date);
  const base = Date.UTC(p.year, p.month - 1, p.day + Number(days || 0), 12, 0, 0);
  return new Date(base);
}

/** Anonymous Gregorian algorithm — Kalendertag (Berlin-Mittag). */
function easterSunday(year) {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return utcDay(year, month, day);
}

/** Approx. full moon day-of-month (simple synodic cycle). */
function approxFullMoonDay(year, month) {
  const known = Date.UTC(2000, 0, 21, 4, 40, 0);
  const syn = 29.530588853;
  const start = Date.UTC(year, month - 1, 1);
  const end = Date.UTC(year, month, 0);
  let best = 15;
  let bestDiff = Infinity;
  for (let d = 1; d <= 31; d++) {
    const t = Date.UTC(year, month - 1, d, 12, 0, 0);
    if (t < start || t > end) continue;
    const cycles = (t - known) / (syn * 86400000);
    const phase = Math.abs(cycles - Math.round(cycles));
    if (phase < bestDiff) {
      bestDiff = phase;
      best = d;
    }
  }
  return best;
}

function nthWeekdayOfMonth(year, month, weekday, nth) {
  // weekday: 0=Sun … 6=Sat (wie JS getDay, aber Europe/Berlin)
  let count = 0;
  for (let d = 1; d <= 31; d++) {
    const dt = utcDay(year, month, d);
    if (berlinParts(dt).month !== month) break;
    if (berlinParts(dt).weekday === weekday) {
      count += 1;
      if (count === nth) return dt;
    }
  }
  return utcDay(year, month, 1);
}

function windowBoundsFromDay(startDate, durationDays) {
  const p = berlinParts(startDate);
  const startMs = berlinDayStartMs(p.year, p.month, p.day);
  const endMs = startMs + Math.max(1, durationDays) * 86400000;
  return { start: new Date(startMs), end: new Date(endMs) };
}

function windowForYear(ev, year) {
  const r = ev.recurrence || { type: "annual", month: 1, day: 1 };
  const dur = Math.max(1, Math.min(31, Math.floor(Number(ev.durationDays) || 1)));

  if (r.type === "range") {
    const startMs = berlinDayStartMs(year, r.startMonth, r.startDay);
    // endDay inklusiv → exclusive = nächster Tag nach endDay
    const endMs = berlinDayStartMs(year, r.endMonth, r.endDay) + 86400000;
    return { start: new Date(startMs), end: new Date(endMs) };
  }
  if (r.type === "easter_offset") {
    return windowBoundsFromDay(addDays(easterSunday(year), Number(r.days) || 0), dur);
  }
  if (r.type === "nth_weekday") {
    return windowBoundsFromDay(nthWeekdayOfMonth(year, r.month, r.weekday, r.nth || 1), dur);
  }
  if (r.type === "full_moon") {
    return null;
  }
  if (r.type === "weekly") {
    return null;
  }
  return windowBoundsFromDay(utcDay(year, r.month || 1, r.day || 1), dur);
}

function berlinLocalMs(y, m, d, hour = 0, minute = 0) {
  const base = berlinDayStartMs(y, m, d);
  return base + Math.max(0, Number(hour) || 0) * 3600000 + Math.max(0, Number(minute) || 0) * 60000;
}

function weeklyHasClock(r) {
  return Number.isFinite(Number(r?.startHour));
}

/** Wochen-Fenster (z. B. Fr 18:00 → Sa 01:00). Enthält now oder null. */
function weeklyWindowContaining(ev, now = new Date()) {
  const r = ev.recurrence || {};
  if (r.type !== "weekly") return null;
  const wantWd = Number(r.weekday);
  if (!Number.isFinite(wantWd)) return null;
  const startH = weeklyHasClock(r) ? Math.max(0, Math.min(23, Math.floor(Number(r.startHour)))) : null;
  const startM = Math.max(0, Math.min(59, Math.floor(Number(r.startMinute) || 0)));
  let endH = Number.isFinite(Number(r.endHour))
    ? Math.max(0, Math.min(23, Math.floor(Number(r.endHour))))
    : null;
  let endM = Math.max(0, Math.min(59, Math.floor(Number(r.endMinute) || 0)));
  let endDayOffset = Math.max(0, Math.min(6, Math.floor(Number(r.endDayOffset) || 0)));
  if (startH != null && endH == null) {
    endH = 23;
    endM = 59;
  }
  if (
    startH != null &&
    endH != null &&
    !Number(r.endDayOffset) &&
    (endH < startH || (endH === startH && endM <= startM))
  ) {
    endDayOffset = 1;
  }
  const t = now.getTime();
  const bp = berlinParts(now);
  // Prüfe aktuelle und vorherige Woche (Fenster kann über Mitternacht gehen)
  for (const weekBack of [0, 1]) {
    let add = (wantWd - bp.weekday + 7) % 7;
    if (weekBack === 1) add -= 7;
    else if (add === 0 && startH != null) {
      /* same day — check below */
    }
    const startDay = addDays(utcDay(bp.year, bp.month, bp.day), add);
    const sp = berlinParts(startDay);
    let fromMs;
    let untilMs;
    if (startH == null) {
      fromMs = berlinDayStartMs(sp.year, sp.month, sp.day);
      untilMs = fromMs + 86400000;
    } else {
      fromMs = berlinLocalMs(sp.year, sp.month, sp.day, startH, startM);
      const endDay = addDays(startDay, endDayOffset);
      const ep = berlinParts(endDay);
      untilMs = berlinLocalMs(ep.year, ep.month, ep.day, endH, endM);
      if (untilMs <= fromMs) untilMs = fromMs + 3600000;
    }
    if (t >= fromMs && t < untilMs) {
      return { fromMs, untilMs, start: new Date(fromMs), end: new Date(untilMs) };
    }
  }
  return null;
}

/** Nächstes Wochen-Fenster ab now (auch wenn gerade aktiv → aktuelles). */
function nextWeeklyWindow(ev, now = new Date()) {
  const containing = weeklyWindowContaining(ev, now);
  if (containing) return containing;
  const r = ev.recurrence || {};
  const wantWd = Number(r.weekday);
  const bp = berlinParts(now);
  let add = (wantWd - bp.weekday + 7) % 7;
  if (add === 0) add = 7;
  const startH = weeklyHasClock(r) ? Math.max(0, Math.min(23, Math.floor(Number(r.startHour)))) : null;
  const startM = Math.max(0, Math.min(59, Math.floor(Number(r.startMinute) || 0)));
  let endH = Number.isFinite(Number(r.endHour))
    ? Math.max(0, Math.min(23, Math.floor(Number(r.endHour))))
    : null;
  let endM = Math.max(0, Math.min(59, Math.floor(Number(r.endMinute) || 0)));
  let endDayOffset = Math.max(0, Math.min(6, Math.floor(Number(r.endDayOffset) || 0)));
  if (startH != null && endH == null) {
    endH = 23;
    endM = 59;
  }
  if (
    startH != null &&
    endH != null &&
    !Number(r.endDayOffset) &&
    (endH < startH || (endH === startH && endM <= startM))
  ) {
    endDayOffset = 1;
  }
  const startDay = addDays(utcDay(bp.year, bp.month, bp.day), add);
  const sp = berlinParts(startDay);
  let fromMs;
  let untilMs;
  if (startH == null) {
    fromMs = berlinDayStartMs(sp.year, sp.month, sp.day);
    untilMs = fromMs + 86400000;
  } else {
    fromMs = berlinLocalMs(sp.year, sp.month, sp.day, startH, startM);
    const endDay = addDays(startDay, endDayOffset);
    const ep = berlinParts(endDay);
    untilMs = berlinLocalMs(ep.year, ep.month, ep.day, endH, endM);
  }
  return { fromMs, untilMs, start: new Date(fromMs), end: new Date(untilMs) };
}

function formatBerlinClock(date) {
  return new Intl.DateTimeFormat("de-DE", {
    timeZone: TZ_BERLIN,
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
}

function isActiveAt(ev, now = new Date()) {
  if (ev.enabled === false) return false;
  const r = ev.recurrence || {};
  const t = now.getTime();
  const bp = berlinParts(now);

  if (r.type === "weekly") {
    if (weeklyHasClock(r)) {
      return Boolean(weeklyWindowContaining(ev, now));
    }
    return bp.weekday === Number(r.weekday);
  }
  if (r.type === "full_moon") {
    return bp.day === approxFullMoonDay(bp.year, bp.month);
  }
  if (r.type === "range") {
    const w = windowForYear(ev, bp.year);
    if (!w) return false;
    return t >= w.start.getTime() && t < w.end.getTime();
  }

  for (const y of [bp.year - 1, bp.year, bp.year + 1]) {
    const w = windowForYear(ev, y);
    if (!w) continue;
    if (t >= w.start.getTime() && t < w.end.getTime()) return true;
  }
  return false;
}

function nextOccurrence(ev, now = new Date()) {
  if (ev.enabled === false) return null;
  const enriched = engine.enrichEvent(ev);
  if (enriched.schedule?.mode === "absolute") {
    const fromMs = Date.parse(enriched.schedule.absoluteFrom || "");
    const untilMs = Date.parse(enriched.schedule.absoluteUntil || "");
    if (!Number.isFinite(fromMs) && !Number.isFinite(untilMs)) return null;
    const active = isActiveAtPatched(ev, now);
    if (Number.isFinite(fromMs) && Number.isFinite(untilMs)) {
      const fromD = new Date(fromMs);
      const untilD = new Date(untilMs);
      const hasTime =
        fromD.getUTCHours() !== 0 ||
        fromD.getUTCMinutes() !== 0 ||
        untilD.getUTCHours() !== 0 ||
        untilD.getUTCMinutes() !== 0 ||
        String(enriched.schedule.absoluteFrom || "").includes("T");
      if (hasTime) {
        return {
          start: formatBerlinClock(fromD),
          end: formatBerlinClock(untilD),
          label: `${formatBerlinClock(fromD)} – ${formatBerlinClock(untilD)}`,
          active,
          fromMs,
          untilMs,
        };
      }
    }
    const start = Number.isFinite(fromMs) ? berlinYmd(new Date(fromMs)) : null;
    const end = Number.isFinite(untilMs) ? berlinYmd(new Date(untilMs)) : start;
    return { start, end, active, fromMs: Number.isFinite(fromMs) ? fromMs : null, untilMs: Number.isFinite(untilMs) ? untilMs : null };
  }
  const r = ev.recurrence || {};
  const bp = berlinParts(now);

  if (r.type === "weekly") {
    const win = nextWeeklyWindow(ev, now);
    const active = Boolean(weeklyWindowContaining(ev, now));
    if (weeklyHasClock(r)) {
      return {
        start: formatBerlinClock(win.start),
        end: formatBerlinClock(win.end),
        label: `${formatBerlinClock(win.start)} – ${formatBerlinClock(win.end)}`,
        active,
        fromMs: win.fromMs,
        untilMs: win.untilMs,
      };
    }
    const today = berlinYmd(win.start);
    return { start: today, end: today, active, fromMs: win.fromMs, untilMs: win.untilMs };
  }
  if (r.type === "full_moon") {
    for (let i = 0; i < 40; i++) {
      const probe = addDays(utcDay(bp.year, bp.month, bp.day), i);
      const pp = berlinParts(probe);
      const day = approxFullMoonDay(pp.year, pp.month);
      if (pp.day === day) {
        const start = ymd(utcDay(pp.year, pp.month, day));
        return { start, end: start, active: i === 0 };
      }
    }
    return null;
  }

  let best = null;
  for (const y of [bp.year - 1, bp.year, bp.year + 1]) {
    const w = windowForYear(ev, y);
    if (!w) continue;
    const active = now.getTime() >= w.start.getTime() && now.getTime() < w.end.getTime();
    if (active || w.end.getTime() > now.getTime()) {
      const cand = {
        start: ymd(w.start),
        end: ymd(addDays(w.end, -1)),
        active,
        t: active ? 0 : w.start.getTime(),
      };
      if (!best || cand.t < best.t) best = cand;
    }
  }
  if (!best) return null;
  return { start: best.start, end: best.end, active: best.active };
}

function normalizeDecor(d, fallback) {
  return engine.normalizeDecor(d, fallback || decor("none", "#E94E77", "", 0.5, "none"));
}

function publicRewardItem(raw) {
  if (!raw || typeof raw !== "object") return null;
  const kind = String(raw.kind || "").trim();
  const itemId = String(raw.itemId || "").trim();
  if (!kind || !itemId) return null;
  return {
    kind,
    itemId,
    emoji: String(raw.emoji || itemId).slice(0, 16),
    label: String(raw.label || itemId).slice(0, 60),
  };
}

function publicEvent(ev) {
  if (!ev) return null;
  const e = engine.enrichEvent(ev);
  return {
    id: String(e.id || ""),
    title: String(e.title || "").slice(0, 60),
    emoji: String(e.emoji || "🎉").slice(0, 8),
    description: String(e.description || "").slice(0, 240),
    hint: String(e.hint || "").slice(0, 200),
    recurrence: e.recurrence || { type: "annual", month: 1, day: 1 },
    durationDays: Math.max(1, Math.min(31, Math.floor(Number(e.durationDays) || 1))),
    enabled: e.enabled !== false,
    sort: Math.floor(Number(e.sort) || 0),
    rewardCoinsPerCollect: clampReward(e.rewardCoinsPerCollect, DEFAULT_REWARD),
    collectTarget: clampTarget(e.collectTarget),
    milestoneBonusCoins: clampReward(e.milestoneBonusCoins, 0),
    rewardItem: publicRewardItem(e.rewardItem),
    decor: normalizeDecor(e.decor),
    schedule: e.schedule,
    collect: e.collect,
    quests: e.quests,
    lobby: e.lobby,
    contest: e.contest,
    custom: Boolean(e.custom),
  };
}

const _isActiveAtBase = isActiveAt;
function isActiveAtPatched(ev, now = new Date()) {
  return engine.isActiveEnriched(ev, now, _isActiveAtBase);
}

function defaultConfig() {
  return {
    events: BUILTIN_EVENTS.map((e) => ({ ...e, enabled: true, decor: { ...e.decor } })),
    updatedAt: null,
  };
}

function ensureEventsConfig(db) {
  if (!db || typeof db !== "object") return defaultConfig();
  if (!db.eventsConfig || typeof db.eventsConfig !== "object") {
    db.eventsConfig = defaultConfig();
  }
  const cfg = db.eventsConfig;
  if (!Array.isArray(cfg.events)) cfg.events = [];
  const byId = new Map(cfg.events.map((e) => [e.id, e]));
  for (const b of BUILTIN_EVENTS) {
    if (!byId.has(b.id)) {
      cfg.events.push({ ...b, enabled: true, decor: { ...b.decor } });
    } else {
      const cur = byId.get(b.id);
      if (!cur.title) cur.title = b.title;
      if (!cur.emoji) cur.emoji = b.emoji;
      if (!cur.description) cur.description = b.description;
      if (!cur.hint) cur.hint = b.hint;
      if (!cur.recurrence) cur.recurrence = b.recurrence;
      // Builtin-Uhrzeiten nachziehen (z. B. Date-Night Fr 18–Sa 1)
      if (
        b.recurrence &&
        Number.isFinite(Number(b.recurrence.startHour)) &&
        !Number.isFinite(Number(cur.recurrence?.startHour))
      ) {
        cur.recurrence = { ...(cur.recurrence || {}), ...b.recurrence };
        if (b.id === "date_night") {
          cur.collectTarget = b.collectTarget;
          cur.hint = b.hint;
          cur.description = b.description;
          if (b.milestoneBonusCoins != null) cur.milestoneBonusCoins = b.milestoneBonusCoins;
        }
      }
      if (cur.durationDays == null) cur.durationDays = b.durationDays;
      if (cur.rewardCoinsPerCollect == null) cur.rewardCoinsPerCollect = b.rewardCoinsPerCollect;
      if (cur.collectTarget == null) cur.collectTarget = b.collectTarget;
      if (cur.milestoneBonusCoins == null) cur.milestoneBonusCoins = b.milestoneBonusCoins;
      if (!cur.rewardItem && b.rewardItem) cur.rewardItem = { ...b.rewardItem };
      if (cur.sort == null) cur.sort = b.sort;
      cur.decor = normalizeDecor(cur.decor, b.decor);
    }
  }
  cfg.events.sort((a, b) => (a.sort || 0) - (b.sort || 0));
  return cfg;
}

function listAdminEvents(db) {
  const cfg = ensureEventsConfig(db);
  const now = new Date();
  return cfg.events.map((e) => {
    const pub = publicEvent(e);
    const occ = nextOccurrence(e, now);
    return {
      ...pub,
      active: isActiveAtPatched(e, now),
      next: occ,
    };
  });
}

function putAdminEvents(db, body) {
  const cfg = ensureEventsConfig(db);
  const incoming = Array.isArray(body?.events) ? body.events : null;
  if (!incoming) {
    return { ok: false, error: "bad_body", message: "events[] erwartet" };
  }
  const byId = new Map(cfg.events.map((e) => [e.id, e]));
  for (const raw of incoming) {
    const id = String(raw?.id || "").trim();
    if (!id || !byId.has(id)) continue;
    const cur = byId.get(id);
    if (raw.title != null) cur.title = String(raw.title).slice(0, 60);
    if (raw.emoji != null) cur.emoji = String(raw.emoji).slice(0, 8);
    if (raw.description != null) cur.description = String(raw.description).slice(0, 240);
    if (raw.hint != null) cur.hint = String(raw.hint).slice(0, 200);
    if (raw.enabled != null) cur.enabled = Boolean(raw.enabled);
    if (raw.durationDays != null) cur.durationDays = Math.max(1, Math.min(31, Math.floor(Number(raw.durationDays) || 1)));
    if (raw.rewardCoinsPerCollect != null) cur.rewardCoinsPerCollect = clampReward(raw.rewardCoinsPerCollect);
    if (raw.collectTarget != null) cur.collectTarget = clampTarget(raw.collectTarget);
    if (raw.milestoneBonusCoins != null) cur.milestoneBonusCoins = clampReward(raw.milestoneBonusCoins, 0);
    if (raw.sort != null) cur.sort = Math.floor(Number(raw.sort) || 0);
    if (raw.decor != null) cur.decor = normalizeDecor(raw.decor, cur.decor);
    if (raw.rewardItem !== undefined) {
      cur.rewardItem = publicRewardItem(raw.rewardItem);
      if (cur.collect) cur.collect.rewardItem = cur.rewardItem;
    }
    if (raw.schedule != null) cur.schedule = engine.normalizeSchedule(raw.schedule, cur);
    if (raw.quests != null) cur.quests = engine.normalizeQuests(raw.quests);
    if (raw.lobby != null) cur.lobby = engine.normalizeLobby(raw.lobby);
    if (raw.contest != null) cur.contest = engine.normalizeContest(raw.contest);
    // recurrence nur eingeschränkt überschreiben (struktur behalten)
    if (raw.recurrence && typeof raw.recurrence === "object") {
      cur.recurrence = raw.recurrence;
    }
  }
  cfg.updatedAt = new Date().toISOString();
  cfg.events.sort((a, b) => (a.sort || 0) - (b.sort || 0));
  return { ok: true, events: listAdminEvents(db) };
}

/** Berlin-Fenster ab heute für N Tage (absolut). */
function absoluteWindowBerlin(durationDays = 2, fromDate = new Date()) {
  const days = Math.max(1, Math.min(31, Math.floor(Number(durationDays) || 2)));
  const bp = berlinParts(fromDate);
  const fromMs = berlinDayStartMs(bp.year, bp.month, bp.day);
  const untilExclusive = fromMs + days * 24 * 60 * 60 * 1000;
  return {
    mode: "absolute",
    absoluteFrom: new Date(fromMs).toISOString(),
    absoluteUntil: new Date(untilExclusive - 1).toISOString(),
  };
}

/** Neues Custom-Event anlegen (Admin) — inkl. absoluter Fenster, Quests, Lobby, Contest. */
function createAdminEvent(db, body = {}) {
  const cfg = ensureEventsConfig(db);
  let id = String(body.id || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_")
    .replace(/^_|_$/g, "")
    .slice(0, 40);
  if (!id) {
    const base = String(body.title || "event")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "_")
      .replace(/^_|_$/g, "")
      .slice(0, 24);
    id = `custom_${base || "event"}_${Date.now().toString(36).slice(-4)}`;
  }
  if (cfg.events.some((e) => e.id === id)) {
    return { ok: false, error: "exists", message: "Event-ID existiert bereits." };
  }
  const month = Math.max(1, Math.min(12, Math.floor(Number(body.month) || new Date().getMonth() + 1)));
  const day = Math.max(1, Math.min(31, Math.floor(Number(body.day) || 1)));
  const durationDays = Math.max(1, Math.min(31, Math.floor(Number(body.durationDays) || 3)));
  const wantAbsolute =
    body.absolute === true ||
    body.mode === "absolute" ||
    (body.schedule && body.schedule.mode === "absolute") ||
    Boolean(body.absoluteFrom);

  let schedule = null;
  if (wantAbsolute) {
    schedule =
      body.schedule?.mode === "absolute"
        ? engine.normalizeSchedule(body.schedule, {})
        : absoluteWindowBerlin(durationDays);
    if (body.absoluteFrom) schedule.absoluteFrom = String(body.absoluteFrom);
    if (body.absoluteUntil) schedule.absoluteUntil = String(body.absoluteUntil);
  }

  const rewardItem =
    body.rewardItem && body.rewardItem.kind && body.rewardItem.itemId
      ? {
          kind: String(body.rewardItem.kind).trim(),
          itemId: String(body.rewardItem.itemId).trim(),
          emoji: String(body.rewardItem.emoji || body.rewardItem.itemId).trim(),
          label: String(body.rewardItem.label || body.rewardItem.itemId).trim(),
        }
      : null;

  const payload = {
    id,
    title: String(body.title || "Neues Event").trim().slice(0, 60) || "Neues Event",
    emoji: String(body.emoji || "🎉").trim().slice(0, 8) || "🎉",
    description: String(body.description || "").trim().slice(0, 240),
    hint: String(body.hint || "Während des Fensters einmal täglich sammeln.").trim().slice(0, 200),
    enabled: body.enabled !== false,
    sort: Math.floor(Number(body.sort) || 200),
    custom: true,
    durationDays,
    recurrence:
      body.recurrence && typeof body.recurrence === "object"
        ? body.recurrence
        : { type: "annual", month, day },
    schedule: schedule || undefined,
    collect: body.collect || {
      enabled: true,
      rewardCoinsPerCollect: clampReward(body.rewardCoinsPerCollect ?? 2),
      collectTarget: clampTarget(body.collectTarget ?? 3),
      milestoneBonusCoins: clampReward(body.milestoneBonusCoins ?? 5, 0),
      rewardItem,
    },
    rewardItem,
    rewardCoinsPerCollect: clampReward(body.rewardCoinsPerCollect ?? 2),
    collectTarget: clampTarget(body.collectTarget ?? 3),
    milestoneBonusCoins: clampReward(body.milestoneBonusCoins ?? 5, 0),
    quests: Array.isArray(body.quests) ? body.quests : undefined,
    lobby: body.lobby && typeof body.lobby === "object" ? body.lobby : undefined,
    contest: body.contest && typeof body.contest === "object" ? body.contest : undefined,
    decor: body.decor || decor("sparkle", "#E94E77", String(body.title || "Event").slice(0, 24), 0.55, "none"),
  };

  const result = engine.createOrUpdateEvent(db, payload, { isCreate: true });
  if (!result.ok) return result;
  const stored = cfg.events.find((e) => e.id === result.event?.id) || result.event;
  if (stored) stored.custom = true;
  return { ok: true, event: publicEvent(stored), events: listAdminEvents(db) };
}

const BUILTIN_IDS = new Set([
  "new_year",
  "valentine",
  "carnival",
  "easter",
  "mothers_day",
  "midsummer",
  "autumn",
  "halloween",
  "full_moon",
  "date_night",
  "nikolaus",
  "advent",
  "christmas",
  "silvester",
]);

/**
 * Event löschen (Custom) oder deaktivieren (Builtin).
 * Räumt Schmuck-Quelle, Progress, Contest und Event-Lobbys auf.
 */
function deleteAdminEvent(db, eventId, { liveRooms = null } = {}) {
  const cfg = ensureEventsConfig(db);
  const id = String(eventId || "").trim();
  if (!id) return { ok: false, error: "bad_id", message: "Event-ID fehlt." };
  const ev = cfg.events.find((e) => e.id === id);
  if (!ev) return { ok: false, error: "not_found", message: "Event nicht gefunden." };

  let deleted = false;
  let disabled = false;
  if (BUILTIN_IDS.has(id) && ev.custom !== true) {
    ev.enabled = false;
    disabled = true;
  } else {
    cfg.events = cfg.events.filter((e) => e.id !== id);
    deleted = true;
  }
  cfg.updatedAt = new Date().toISOString();

  if (db.eventContest && typeof db.eventContest === "object" && db.eventContest[id]) {
    delete db.eventContest[id];
  }
  if (db.eventLobbies && typeof db.eventLobbies === "object" && db.eventLobbies[id]) {
    delete db.eventLobbies[id];
  }

  let progressCleared = 0;
  for (const u of Object.values(db.users || {})) {
    if (!u?.eventProgress || typeof u.eventProgress !== "object") continue;
    for (const key of Object.keys(u.eventProgress)) {
      if (key === id || key.startsWith(`${id}:`)) {
        delete u.eventProgress[key];
        progressCleared += 1;
      }
    }
  }

  let roomsCleared = 0;
  const roomCodes = new Set();
  for (const [code, room] of Object.entries(db.rooms || {})) {
    if (room && (room.eventId === id || room.eventLobbyId === id)) {
      roomCodes.add(code);
      delete db.rooms[code];
      roomsCleared += 1;
    }
  }
  if (liveRooms && typeof liveRooms.forEach === "function") {
    for (const code of [...liveRooms.keys()]) {
      const room = liveRooms.get(code);
      if (room && (room.eventId === id || room.eventLobbyId === id || roomCodes.has(code))) {
        try {
          for (const sock of room.sockets || []) {
            try {
              sock.send(JSON.stringify({ type: "lobby_closed", reason: "event_deleted" }));
              sock.close();
            } catch (_) {
              /* ignore */
            }
          }
        } catch (_) {
          /* ignore */
        }
        liveRooms.delete(code);
        roomCodes.add(code);
      }
    }
  }

  return {
    ok: true,
    deleted,
    disabled,
    progressCleared,
    roomsCleared,
    roomCodes: [...roomCodes],
    events: listAdminEvents(db),
  };
}

/** Aktive Event-Quests für eine Metrik erhöhen (Streiche, Sessions, …). */
function bumpQuestsForUser(db, user, metric, amount, applyLedgerFn, giveItemFn) {
  if (!user || !metric) return [];
  const cfg = ensureEventsConfig(db);
  const now = new Date();
  const done = [];
  for (const ev of cfg.events) {
    if (!isActiveAtPatched(ev, now)) continue;
    if (!Array.isArray(ev.quests) || !ev.quests.length) continue;
    const r = engine.bumpEventQuest(db, user, ev, metric, amount, applyLedgerFn, giveItemFn);
    if (r?.completed?.length) {
      for (const c of r.completed) done.push({ eventId: ev.id, ...c });
    }
  }
  return done;
}

function ensureUserProgress(user) {
  if (!user.eventProgress || typeof user.eventProgress !== "object") {
    user.eventProgress = {};
  }
  return user.eventProgress;
}

function progressSeasonKey(ev, now = new Date()) {
  const enriched = engine.enrichEvent(ev);
  if (enriched.schedule?.mode === "absolute") {
    const from = String(enriched.schedule.absoluteFrom || berlinYmd(now)).slice(0, 10);
    return `${ev.id}:${from}`;
  }
  const r = ev.recurrence || {};
  const bp = berlinParts(now);
  if (r.type === "weekly") {
    const win = weeklyWindowContaining(ev, now) || nextWeeklyWindow(ev, now);
    // Pro Vorkommen (Freitagabend), nicht nur pro Jahr
    return `${ev.id}:${berlinYmd(win.start)}`;
  }
  if (r.type === "full_moon") return `${ev.id}:${berlinYmd(now)}`;
  for (const y of [bp.year - 1, bp.year, bp.year + 1]) {
    const w = windowForYear(ev, y);
    if (w && now.getTime() >= w.start.getTime() && now.getTime() < w.end.getTime()) {
      return `${ev.id}:${ymd(w.start)}`;
    }
  }
  const occ = nextOccurrence(ev, now);
  return `${ev.id}:${occ?.start || bp.year}`;
}

function progressFor(user, eventId, seasonKey = null) {
  const all = ensureUserProgress(user);
  const key = seasonKey || String(eventId || "").trim();
  if (!all[key] || typeof all[key] !== "object") {
    all[key] = {
      progress: 0,
      lastCollectDay: null,
      claimedMilestone: false,
      itemGranted: false,
    };
  }
  const p = all[key];
  p.progress = Math.max(0, Math.floor(Number(p.progress) || 0));
  p.claimedMilestone = Boolean(p.claimedMilestone);
  p.itemGranted = Boolean(p.itemGranted);
  return p;
}

function eventShopItemId(eventId, year, kind) {
  const id = String(eventId || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .slice(0, 14);
  const y = Math.floor(Number(year) || new Date().getFullYear());
  const k = String(kind || "pets").trim();
  if (k === "themes") return `theme_${id}_t${y}`.slice(0, 32);
  const tag = k === "stickers" ? "s" : k === "emojis" ? "e" : "p";
  return `img_ev_${id}_${tag}${y}`.slice(0, 32);
}

/** @deprecated alias */
function eventShopPetId(eventId, year) {
  return eventShopItemId(eventId, year, "pets");
}

function shopPetYearForEvent(ev, now = new Date()) {
  const bp = berlinParts(now);
  if (isActiveAtPatched(ev, now)) {
    for (const y of [bp.year - 1, bp.year, bp.year + 1]) {
      const w = windowForYear(ev, y);
      if (w && now.getTime() >= w.start.getTime() && now.getTime() < w.end.getTime()) {
        return y;
      }
    }
    const enriched = engine.enrichEvent(ev);
    if (enriched.schedule?.mode === "absolute") {
      const fromMs = Date.parse(enriched.schedule.absoluteFrom || "");
      if (Number.isFinite(fromMs)) return berlinParts(new Date(fromMs)).year;
    }
  }
  const occ = nextOccurrence(ev, now);
  if (occ?.start) {
    const m = String(occ.start).match(/^(\d{4})/);
    if (m) return Number(m[1]);
  }
  return bp.year;
}

function activeEventWindowMs(ev, now = new Date()) {
  const bp = berlinParts(now);
  for (const y of [bp.year - 1, bp.year, bp.year + 1]) {
    const w = windowForYear(ev, y);
    if (w && now.getTime() >= w.start.getTime() && now.getTime() < w.end.getTime()) {
      return { from: w.start.getTime(), until: w.end.getTime(), year: y };
    }
  }
  const enriched = engine.enrichEvent(ev);
  if (enriched.schedule?.mode === "absolute") {
    const fromMs = Date.parse(enriched.schedule.absoluteFrom || "");
    const untilMs = Date.parse(enriched.schedule.absoluteUntil || "");
    if (
      Number.isFinite(fromMs) &&
      Number.isFinite(untilMs) &&
      now.getTime() >= fromMs &&
      now.getTime() < untilMs
    ) {
      return { from: fromMs, until: untilMs, year: berlinParts(new Date(fromMs)).year };
    }
  }
  return null;
}

/** Fenster für ein bestimmtes Event-Jahr (Admin-Anlage / Sync). */
function windowMsForEventYear(ev, year) {
  if (!ev) return null;
  const y = Math.floor(Number(year) || 0);
  if (y < 2020 || y > 2100) return null;
  const enriched = engine.enrichEvent(ev);
  if (enriched.schedule?.mode === "absolute") {
    const fromMs = Date.parse(enriched.schedule.absoluteFrom || "");
    const untilMs = Date.parse(enriched.schedule.absoluteUntil || "");
    if (Number.isFinite(fromMs) && Number.isFinite(untilMs)) {
      return { from: fromMs, until: untilMs, year: berlinParts(new Date(fromMs)).year };
    }
  }
  const w = windowForYear(ev, y);
  if (!w) return null;
  return { from: w.start.getTime(), until: w.end.getTime(), year: y };
}

function findCatalogEventItem(db, eventId, year, kind) {
  if (!db) return null;
  let shopCatalog;
  try {
    shopCatalog = require("./shop_catalog");
  } catch {
    return null;
  }
  const cat = shopCatalog.ensureShopCatalog(db);
  const eid = String(eventId || "").trim();
  const k = String(kind || "").trim();
  if (!eid || !k) return null;
  const y = Math.floor(Number(year) || 0);
  const hits = Object.values(cat.items || {}).filter(
    (i) =>
      i &&
      i.kind === k &&
      String(i.eventId || "") === eid &&
      (!y || Number(i.eventYear) === y || String(i.itemId || "").includes(String(y)))
  );
  if (!hits.length) return null;
  let hit = y ? hits.find((i) => Number(i.eventYear) === y) : null;
  if (!hit) hit = hits.slice().sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))[0];
  return hit || null;
}

function findCatalogEventPet(db, eventId, year) {
  return findCatalogEventItem(db, eventId, year, "pets");
}

/** Konflikt: schon ein Item derselben Kategorie für Event+Jahr. */
function findEventKindConflict(db, eventId, kind, eventYear, excludeItemId = null) {
  let shopCatalog;
  try {
    shopCatalog = require("./shop_catalog");
  } catch {
    return null;
  }
  const cat = shopCatalog.ensureShopCatalog(db);
  const eid = String(eventId || "").trim();
  const k = String(kind || "").trim();
  const y = Math.floor(Number(eventYear) || 0);
  const ex = String(excludeItemId || "").trim();
  for (const item of Object.values(cat.items || {})) {
    if (!item || item.kind !== k) continue;
    if (String(item.eventId || "") !== eid) continue;
    if (y && Number(item.eventYear) !== y) continue;
    if (ex && String(item.itemId) === ex) continue;
    return item;
  }
  return null;
}

function catalogItemToShopPublic(item, ev, year) {
  if (!item) return null;
  let shopCatalog;
  try {
    shopCatalog = require("./shop_catalog");
  } catch {
    shopCatalog = null;
  }
  const pub = publicEvent(ev);
  const price =
    shopCatalog && typeof shopCatalog.effectivePrice === "function"
      ? shopCatalog.effectivePrice(item)
      : Math.max(0, Number(item.priceCoins) || 0);
  const hasImage = Boolean(item.hasImage);
  const kind = String(item.kind || "");
  const emoji =
    item.previewEmoji ||
    (kind === "themes" ? item.visualConfig?.emojis?.[0] : null) ||
    pub.emoji ||
    "🎁";
  return {
    kind,
    itemId: item.itemId,
    emoji,
    label: String(item.label || item.itemId).slice(0, 40),
    priceCoins: price > 0 ? price : kind === "themes" ? 0 : 200,
    year: year || Number(item.eventYear) || 0,
    hasImage,
    imageUrl: hasImage
      ? `/luv/v1/shop/pet-image/${encodeURIComponent(item.itemId)}`
      : null,
    visualConfig: kind === "themes" && item.visualConfig ? item.visualConfig : null,
  };
}

function shopItemsPublic(db, ev, now = new Date()) {
  if (!ev) return [];
  const year = shopPetYearForEvent(ev, now);
  const kinds = ["pets", "stickers", "emojis", "themes"];
  const out = [];
  for (const kind of kinds) {
    const item = findCatalogEventItem(db, ev.id, year, kind);
    if (!item) continue;
    const pub = catalogItemToShopPublic(item, ev, year);
    if (pub) out.push(pub);
  }
  return out;
}

function shopPetPublic(db, ev, now = new Date()) {
  const items = shopItemsPublic(db, ev, now);
  return items.find((i) => i.kind === "pets") || null;
}

/**
 * Event-Shop-Items: Fenster an Event-Jahr anpassen (alle Kategorien mit eventId).
 */
function syncEventShopPets(db, now = new Date()) {
  if (!db) return;
  let shopCatalog;
  try {
    shopCatalog = require("./shop_catalog");
  } catch {
    return;
  }
  const cfg = ensureEventsConfig(db);
  const cat = shopCatalog.ensureShopCatalog(db);
  const byId = new Map((cfg.events || []).map((e) => [String(e.id), e]));
  for (const item of Object.values(cat.items || {})) {
    if (!item || !item.eventId) continue;
    if (!["pets", "stickers", "emojis", "themes"].includes(item.kind)) continue;
    const ev = byId.get(String(item.eventId));
    if (!ev || ev.enabled === false) continue;
    const year =
      Number(item.eventYear) ||
      shopPetYearForEvent(ev, now) ||
      berlinParts(now).year;
    if (!item.eventYear) item.eventYear = year;
    const win = windowMsForEventYear(ev, year);
    if (!win) continue;
    const same =
      Number(item.availableFrom) === win.from &&
      Number(item.availableUntil) === win.until &&
      item.rotationLocked === true;
    if (same) continue;
    shopCatalog.upsertItem(db, {
      ...item,
      kind: item.kind,
      itemId: item.itemId,
      availableFrom: win.from,
      availableUntil: win.until,
      rotationLocked: true,
      eventId: item.eventId,
      eventYear: year,
      enabled: item.enabled !== false,
    });
  }
}

/** Admin: Event-Liste inkl. belegte Kategorien für Shop-Wizard. */
function shopEventOptions(db, now = new Date()) {
  const cfg = ensureEventsConfig(db);
  return (cfg.events || [])
    .filter((e) => e && e.enabled !== false)
    .map((e) => {
      const pub = publicEvent(e);
      const occ = nextOccurrence(e, now);
      const year = shopPetYearForEvent(e, now);
      const win = windowMsForEventYear(e, year);
      const occupied = {};
      const existingByKind = {};
      for (const kind of ["pets", "stickers", "emojis", "themes"]) {
        const hit = findCatalogEventItem(db, e.id, year, kind);
        if (hit) {
          occupied[kind] = true;
          existingByKind[kind] = hit.itemId;
        }
      }
      return {
        id: pub.id,
        title: pub.title,
        emoji: pub.emoji,
        year,
        windowStart: occ?.start || null,
        windowEnd: occ?.end || null,
        availableFrom: win?.from || null,
        availableUntil: win?.until || null,
        suggestedItemId: eventShopItemId(e.id, year, "pets"),
        suggestedLabel: `${pub.title}-Begleiter`.slice(0, 40),
        suggestedByKind: {
          pets: {
            itemId: eventShopItemId(e.id, year, "pets"),
            label: `${pub.title}-Begleiter`.slice(0, 40),
          },
          stickers: {
            itemId: eventShopItemId(e.id, year, "stickers"),
            label: `${pub.title}-Sticker`.slice(0, 40),
          },
          emojis: {
            itemId: eventShopItemId(e.id, year, "emojis"),
            label: `${pub.title}-Emoji`.slice(0, 40),
          },
          themes: {
            itemId: eventShopItemId(e.id, year, "themes"),
            label: `${pub.title}-Hintergrund`.slice(0, 40),
          },
        },
        hasPetForYear: Boolean(occupied.pets),
        existingItemId: existingByKind.pets || null,
        occupiedKinds: occupied,
        existingByKind,
        active: isActiveAtPatched(e, now),
      };
    })
    .sort((a, b) => String(a.windowStart || "").localeCompare(String(b.windowStart || "")));
}

function meEventsPayload(db, user, dayKey, now = new Date()) {
  try {
    syncEventShopPets(db, now);
  } catch {
    /* ignore */
  }
  const cfg = ensureEventsConfig(db);
  const active = [];
  const upcoming = [];
  for (const e of cfg.events) {
    if (e.enabled === false) continue;
    const pub = publicEvent(e);
    const occ = nextOccurrence(e, now);
    const season = progressSeasonKey(e, now);
    const prog = progressFor(user, e.id, season);
    let quests = [];
    if (Array.isArray(pub.quests) && pub.quests.length) {
      const engineProg = engine.ensureUserProgress(user, e.id);
      quests = pub.quests.map((q) => {
        const qp = engineProg.quests?.[q.id] || { progress: 0, done: false, claimed: false };
        return {
          ...q,
          progress: Math.min(q.target, Number(qp.progress) || 0),
          done: Boolean(qp.done),
          claimed: Boolean(qp.claimed),
        };
      });
    }
    const shopItems = shopItemsPublic(db, e, now);
    const row = {
      ...pub,
      quests,
      lobby: pub.lobby || { enabled: false },
      contest: pub.contest || { enabled: false },
      windowStart: occ?.label || occ?.start || null,
      windowEnd: occ?.label ? null : occ?.end || null,
      progress: prog.progress,
      collectTarget: pub.collectTarget,
      collectedToday: prog.lastCollectDay === dayKey,
      claimedMilestone: prog.claimedMilestone,
      itemGranted: prog.itemGranted,
      canCollect: false,
      shopItems,
      shopPet: shopItems.find((i) => i.kind === "pets") || null,
    };
    const activeNow = isActiveAtPatched(e, now);
    if (user && pub.contest?.enabled) {
      const windowEnd =
        engine.eventWindowEndIso(e, now) ||
        engine.eventWindowEndIsoFromOccEnd(row.windowEnd) ||
        row.windowEnd;
      row.contest = engine.contestPublicForUser(
        db,
        user,
        e,
        row.windowStart,
        windowEnd,
        activeNow,
        now.getTime()
      );
    }
    if (user) {
      const ep = engine.ensureUserProgress(user, e.id);
      row.eventPrompt = ep.eventPrompt || null;
      row.lobbyCreated = Boolean(ep.lobbyCreated);
      row.lobbyCode = ep.lobbyCode || null;
      row.canCreateLobby = Boolean(
        activeNow && pub.lobby?.enabled && !ep.lobbyCreated
      );
    }
    if (activeNow) {
      row.canCollect = prog.lastCollectDay !== dayKey;
      active.push(row);
    } else if (occ && !occ.active) {
      upcoming.push({ ...row, canCollect: false, canCreateLobby: false });
    }
  }
  upcoming.sort((a, b) => String(a.windowStart || "").localeCompare(String(b.windowStart || "")));
  // Bei Überlappung (Advent ∩ Weihnachten) Decor vom Event mit höchstem sort
  const primary =
    active.slice().sort((a, b) => (b.sort || 0) - (a.sort || 0))[0] || null;
  return {
    ok: true,
    dayKey,
    active,
    upcoming: upcoming.slice(0, 8),
    primaryDecor: primary?.decor || null,
    primaryEventId: primary?.id || null,
  };
}

function collectEvent(db, user, eventId, dayKey, applyLedgerFn, giveItemFn, now = new Date()) {
  const cfg = ensureEventsConfig(db);
  const ev = cfg.events.find((e) => e.id === String(eventId || "").trim());
  if (!ev || ev.enabled === false) {
    return { ok: false, error: "not_found", message: "Event nicht gefunden" };
  }
  if (!isActiveAtPatched(ev, now)) {
    return { ok: false, error: "inactive", message: "Event ist gerade nicht aktiv" };
  }
  const pub = publicEvent(ev);
  const season = progressSeasonKey(ev, now);
  const prog = progressFor(user, ev.id, season);
  if (prog.lastCollectDay === dayKey) {
    return { ok: false, error: "already", message: "Heute schon gesammelt" };
  }
  const grant = pub.rewardCoinsPerCollect;
  prog.lastCollectDay = dayKey;
  prog.progress = Math.min(pub.collectTarget, prog.progress + 1);
  let milestoneBonus = 0;
  let itemGranted = null;
  const hitMilestone = prog.progress >= pub.collectTarget;
  if (hitMilestone && !prog.claimedMilestone) {
    prog.claimedMilestone = true;
    if (pub.milestoneBonusCoins > 0) milestoneBonus = pub.milestoneBonusCoins;
    if (pub.rewardItem && !prog.itemGranted && typeof giveItemFn === "function") {
      if (giveItemFn(user, pub.rewardItem.kind, pub.rewardItem.itemId)) {
        prog.itemGranted = true;
        itemGranted = pub.rewardItem;
      }
    }
  } else if (
    hitMilestone &&
    pub.rewardItem &&
    !prog.itemGranted &&
    typeof giveItemFn === "function"
  ) {
    if (giveItemFn(user, pub.rewardItem.kind, pub.rewardItem.itemId)) {
      prog.itemGranted = true;
      itemGranted = pub.rewardItem;
    }
  }
  const total = grant + milestoneBonus;
  if (total > 0 && typeof applyLedgerFn === "function") {
    applyLedgerFn(user.id, total, "event_collect", `${ev.id}:${dayKey}`);
  }
  return {
    ok: true,
    eventId: ev.id,
    coinsGranted: total,
    rewardCoins: grant,
    milestoneBonus,
    itemGranted,
    progress: prog.progress,
    collectTarget: pub.collectTarget,
    claimedMilestone: prog.claimedMilestone,
    state: meEventsPayload(db, user, dayKey, now),
  };
}

function yearOverview(db, year) {
  const cfg = ensureEventsConfig(db);
  const y = Math.floor(Number(year) || berlinParts().year);
  const months = Array.from({ length: 12 }, (_, i) => ({ month: i + 1, events: [] }));
  for (const e of cfg.events) {
    if (e.enabled === false) continue;
    const enriched = engine.enrichEvent(e);
    // Absolute Events: direkt aus absoluteFrom/Until (nicht recurrence → 1.1.)
    if (enriched?.schedule?.mode === "absolute") {
      const fromMs = Date.parse(enriched.schedule.absoluteFrom || "");
      const untilMs = Date.parse(enriched.schedule.absoluteUntil || "");
      if (!Number.isFinite(fromMs)) continue;
      const sp = berlinParts(new Date(fromMs));
      const ep = Number.isFinite(untilMs) ? berlinParts(new Date(untilMs)) : sp;
      if (sp.year !== y && ep.year !== y) continue;
      if (sp.year === y) {
        const startLabel = sp.day;
        const endLabel = ep.year === y && ep.month === sp.month ? ep.day : ep.day;
        const label =
          sp.month === ep.month && sp.year === ep.year && startLabel === endLabel
            ? `${startLabel}.`
            : `${startLabel}.–${endLabel}.`;
        months[sp.month - 1].events.push({
          id: e.id,
          title: e.title,
          emoji: e.emoji,
          label,
          start: berlinYmd(new Date(fromMs)),
          end: Number.isFinite(untilMs) ? berlinYmd(new Date(untilMs)) : berlinYmd(new Date(fromMs)),
          absolute: true,
        });
      }
      continue;
    }
    const r = e.recurrence || enriched?.recurrence || {};
    if (r.type === "full_moon") {
      for (const mo of months) {
        mo.events.push({
          id: e.id,
          title: e.title,
          emoji: e.emoji,
          label: `~${approxFullMoonDay(y, mo.month)}.`,
          recurring: true,
        });
      }
      continue;
    }
    if (r.type === "weekly") {
      for (const mo of months) {
        mo.events.push({
          id: e.id,
          title: e.title,
          emoji: e.emoji,
          label: "jeden Fr",
          recurring: true,
        });
      }
      continue;
    }
    const w = windowForYear(e, y);
    if (!w) continue;
    const startM = berlinParts(w.start).month - 1;
    months[startM].events.push({
      id: e.id,
      title: e.title,
      emoji: e.emoji,
      label: `${berlinParts(w.start).day}.–${berlinParts(addDays(w.end, -1)).day}.`,
      start: ymd(w.start),
      end: ymd(addDays(w.end, -1)),
    });
  }
  return { year: y, months };
}

module.exports = {
  BUILTIN_EVENTS,
  EVENT_ONLY_ITEM_IDS,
  EVENT_ONLY_KEYS,
  ensureEventsConfig,
  listAdminEvents,
  putAdminEvents,
  createAdminEvent,
  deleteAdminEvent,
  bumpQuestsForUser,
  absoluteWindowBerlin,
  meEventsPayload,
  collectEvent,
  yearOverview,
  isActiveAt,
  isActiveAtPatched,
  isEventOnlyItem,
  isEventShopBoundItem,
  publicEvent,
  nextOccurrence,
  syncEventShopPets,
  shopPetPublic,
  shopItemsPublic,
  eventShopPetId,
  eventShopItemId,
  shopPetYearForEvent,
  shopEventOptions,
  windowMsForEventYear,
  findCatalogEventPet,
  findCatalogEventItem,
  findEventKindConflict,
};

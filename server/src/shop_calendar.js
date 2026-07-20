/**
 * Itemshop-Kalender: Verfügbarkeitsfenster, Rotationspläne, Monatsansicht.
 */
const shopCatalog = require("./shop_catalog");
const market = require("./market");
const itemLabels = require("./item_labels");
const { displayNameForEmoji } = require("./emoji_display_names");
const seasonEvents = require("./events");

const LOG_MAX = 2500;
const LOG_PER_ITEM = 60;
const DAY_MS = 24 * 60 * 60 * 1000;
const WEEK_MS = 7 * DAY_MS;
const MONTH_MS = 30 * DAY_MS; // Planungsmonat ≈ 30 Tage

function ensureAvailabilityLog(db) {
  const cat = shopCatalog.ensureShopCatalog(db);
  if (!Array.isArray(cat.availabilityLog)) cat.availabilityLog = [];
  return cat.availabilityLog;
}

function ensureRotationPlans(db) {
  if (!db.shopRotationPlans || typeof db.shopRotationPlans !== "object") {
    db.shopRotationPlans = { plans: {} };
  }
  if (!db.shopRotationPlans.plans || typeof db.shopRotationPlans.plans !== "object") {
    db.shopRotationPlans.plans = {};
  }
  return db.shopRotationPlans.plans;
}

function snapWindow(item) {
  if (!item) return { availableFrom: null, availableUntil: null, enabled: false };
  return {
    availableFrom: item.availableFrom || null,
    availableUntil: item.availableUntil || null,
    enabled: item.enabled !== false,
  };
}

function windowChanged(a, b) {
  return (
    (a?.availableFrom || null) !== (b?.availableFrom || null) ||
    (a?.availableUntil || null) !== (b?.availableUntil || null) ||
    Boolean(a?.enabled) !== Boolean(b?.enabled)
  );
}

function appendAvailabilityLog(db, { kind, itemId, before, after, reason, byUserId }) {
  if (!windowChanged(before, after)) return null;
  const log = ensureAvailabilityLog(db);
  const entry = {
    id: `avl_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`,
    kind: String(kind || "").trim(),
    itemId: String(itemId || "").trim(),
    at: Date.now(),
    reason: String(reason || "admin_edit"),
    byUserId: byUserId || null,
    before: snapWindow(before),
    after: snapWindow(after),
  };
  log.push(entry);
  if (log.length > LOG_MAX) {
    db.shopCatalog.availabilityLog = log.slice(-LOG_MAX);
  }
  return entry;
}

function logForItem(db, kind, itemId) {
  const wantK = String(kind || "").trim();
  const wantId = String(itemId || "").trim();
  return ensureAvailabilityLog(db)
    .filter((e) => e && e.kind === wantK && e.itemId === wantId)
    .slice(-LOG_PER_ITEM)
    .reverse();
}

function summarizePresence(item, history) {
  const now = Date.now();
  const segments = [];
  const chron = [...(history || [])].reverse();
  let openFrom = null;
  for (const e of chron) {
    if (e.after?.enabled && !e.before?.enabled) {
      openFrom = e.after.availableFrom || e.at;
    }
    if (!e.after?.enabled && e.before?.enabled) {
      const start = openFrom || e.before.availableFrom || e.at;
      const end = e.at;
      if (start && end > start) segments.push({ from: start, until: end, ms: end - start });
      openFrom = null;
    }
  }
  if (item?.enabled !== false) {
    const start = openFrom || item.availableFrom || item.createdAt || now;
    const end = item.availableUntil && item.availableUntil < now ? item.availableUntil : now;
    if (end > start) segments.push({ from: start, until: end, ms: end - start, active: true });
  }
  const totalMs = segments.reduce((s, x) => s + (x.ms || 0), 0);
  return { segments: segments.slice(-12), totalMs };
}

function multiBuyStats(db, kind, itemId) {
  const key = shopCatalog.itemKey(kind, itemId);
  let multiBuyUsers = 0;
  let multiBuyExtra = 0;
  let maxPerUser = 0;
  const users = db.users || {};
  for (const u of Object.values(users)) {
    const n = Math.max(0, Math.floor(Number(u?.shopPurchases?.[key]) || 0));
    if (n > maxPerUser) maxPerUser = n;
    if (n > 1) {
      multiBuyUsers += 1;
      multiBuyExtra += n - 1;
    }
  }
  return { multiBuyUsers, multiBuyExtra, maxPerUserBuys: maxPerUser };
}

function marketStats(db, kind, itemId, shopPrice) {
  const sales = market.allSales(db, kind, itemId);
  const prices = sales.map((s) => Math.max(0, Math.floor(Number(s.price) || 0))).filter((p) => p > 0);
  const shop = Math.max(0, Math.floor(Number(shopPrice) || 0));
  const above = prices.filter((p) => shop > 0 && p > shop);
  const lastSoldAt = sales.length ? Math.max(...sales.map((s) => Number(s.at) || 0)) : null;
  const salesTotal = prices.length;
  const salesAboveShop = above.length;
  const aboveRatio = salesTotal > 0 ? salesAboveShop / salesTotal : 0;
  const hot = salesAboveShop >= 3 && aboveRatio >= 0.4;
  const premium = salesAboveShop >= 1 && Math.max(0, ...above) >= shop * 1.5 && shop > 0;
  return {
    salesTotal,
    salesAboveShop,
    minPrice: prices.length ? Math.min(...prices) : null,
    maxPrice: prices.length ? Math.max(...prices) : null,
    avgPrice: prices.length
      ? Math.round(prices.reduce((a, b) => a + b, 0) / prices.length)
      : null,
    lastSoldAt,
    hot,
    premium,
    aboveRatio: Math.round(aboveRatio * 100),
  };
}

function statusOf(item, now = Date.now()) {
  if (!item) return "missing";
  if (item.enabled === false) {
    if (item.availableUntil && now > item.availableUntil) return "expired";
    return "off";
  }
  if (item.availableFrom && now < item.availableFrom) return "scheduled";
  if (item.availableUntil && now > item.availableUntil) return "expired";
  if (item.availableFrom || item.availableUntil) return "window";
  return "always";
}

function unitToMs(unit, length = 1) {
  const n = Math.max(1, Math.floor(Number(length) || 1));
  const u = String(unit || "day").toLowerCase();
  if (u === "week" || u === "weeks" || u === "woche" || u === "wochen") return n * WEEK_MS;
  if (u === "month" || u === "months" || u === "monat" || u === "monate") return n * MONTH_MS;
  return n * DAY_MS;
}

function parseDateInput(raw) {
  if (raw === null || raw === undefined || raw === "") return null;
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  const s = String(raw).trim();
  if (!s) return null;
  const t = Date.parse(s);
  if (!Number.isFinite(t)) return null;
  return t;
}

function stableHash(str) {
  let h = 0;
  const s = String(str || "");
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h >>> 0;
}

function normalizePlan(raw = {}) {
  const id =
    String(raw.id || "").trim() ||
    `rot_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
  const cycleUnit = ["day", "week", "month"].includes(raw.cycleUnit) ? raw.cycleUnit : "month";
  const activeUnit = ["day", "week", "month"].includes(raw.activeUnit) ? raw.activeUnit : "week";
  const mode = raw.mode === "manual" ? "manual" : "price";
  const itemKeys = Array.isArray(raw.itemKeys)
    ? raw.itemKeys.map((k) => String(k || "").trim()).filter(Boolean).slice(0, 500)
    : [];
  // independent = jedes Item eigenständig (Standard). queue = altes Slot-Modell.
  const model = raw.model === "queue" ? "queue" : "independent";
  return {
    id,
    label: String(raw.label || "Rotation").trim().slice(0, 60) || "Rotation",
    enabled: raw.enabled !== false,
    model,
    cycleUnit,
    cycleLength: Math.max(1, Math.min(36, Math.floor(Number(raw.cycleLength) || 3))),
    activeUnit,
    activeLength: Math.max(1, Math.min(90, Math.floor(Number(raw.activeLength) || 1))),
    shortDays: Math.max(1, Math.min(14, Math.floor(Number(raw.shortDays) || 3))),
    longDays: Math.max(1, Math.min(30, Math.floor(Number(raw.longDays) || 7))),
    concurrent: Math.max(1, Math.min(50, Math.floor(Number(raw.concurrent) || 1))),
    mode,
    priceMin:
      raw.priceMin == null || raw.priceMin === ""
        ? null
        : Math.max(0, Math.floor(Number(raw.priceMin) || 0)),
    priceMax:
      raw.priceMax == null || raw.priceMax === ""
        ? null
        : Math.max(0, Math.floor(Number(raw.priceMax) || 0)),
    itemKeys,
    anchorAt: Number(raw.anchorAt) || Date.now(),
    createdAt: Number(raw.createdAt) || Date.now(),
    updatedAt: Date.now(),
    seeded: Boolean(raw.seeded),
  };
}

function isRotationEligible(item) {
  if (!item) return false;
  if (item.rotationLocked === true) return false;
  if (seasonEvents.isEventOnlyItem(item.kind, item.itemId)) return false;
  return true;
}

function resolvePlanItems(db, plan) {
  const cat = shopCatalog.ensureShopCatalog(db);
  let items = Object.values(cat.items || {}).filter(Boolean).filter(isRotationEligible);
  if (plan.mode === "manual") {
    const want = new Set(plan.itemKeys || []);
    items = items.filter((i) => want.has(shopCatalog.itemKey(i.kind, i.itemId)));
  } else {
    const min = plan.priceMin != null ? plan.priceMin : 100;
    const max = plan.priceMax != null ? plan.priceMax : null;
    items = items.filter((i) => {
      if (i.rotationPlanId != null && i.rotationPlanId !== plan.id) return false;
      const p = shopCatalog.effectivePrice(i);
      if (p < min) return false;
      if (max != null && p > max) return false;
      return true;
    });
  }
  items.sort(
    (a, b) =>
      shopCatalog.effectivePrice(b) - shopCatalog.effectivePrice(a) ||
      String(a.itemId).localeCompare(String(b.itemId))
  );
  return items;
}

/** Items die dem Plan zugeordnet waren, aber nicht mehr im Pool sind. */
function detachOrphanPlanItems(db, plan, keepKeys, { now = Date.now(), byUserId = null } = {}) {
  const cat = shopCatalog.ensureShopCatalog(db);
  const keep = keepKeys instanceof Set ? keepKeys : new Set(keepKeys || []);
  let cleared = 0;
  for (const item of Object.values(cat.items || {})) {
    if (!item || item.rotationPlanId !== plan.id) continue;
    const key = shopCatalog.itemKey(item.kind, item.itemId);
    if (keep.has(key)) continue;
    const before = snapWindow(item);
    item.rotationPlanId = null;
    item.availableFrom = null;
    item.availableUntil = null;
    item.enabled = true;
    item.updatedAt = now;
    appendAvailabilityLog(db, {
      kind: item.kind,
      itemId: item.itemId,
      before,
      after: snapWindow(item),
      reason: "rotation_orphan_clear",
      byUserId,
    });
    cleared += 1;
  }
  return cleared;
}

function planPeriodMs(plan) {
  return unitToMs(plan.cycleUnit, plan.cycleLength);
}

/** Shop-Dauer für ein Item: ~40 % kurz (3 Tage), sonst lang (7 Tage). */
function itemOnMs(plan, itemId) {
  const shortD = Math.max(1, Number(plan.shortDays) || 3);
  const longD = Math.max(shortD, Number(plan.longDays) || 7);
  const h = stableHash(`${plan.id}:${itemId}`);
  return (h % 5 < 2 ? shortD : longD) * DAY_MS;
}

/** Phasen-Offset 0…period — Items starten versetzt. */
function itemPhaseOffset(plan, itemId) {
  const period = planPeriodMs(plan);
  const h = stableHash(`phase:${plan.id}:${itemId}`);
  return (h % 100000) / 100000 * period;
}

/**
 * Unabhängiges Fenster eines Items.
 * intoPeriod < onMs → gerade im Shop; sonst Pause bis zum nächsten Auftritt.
 * Max. Wartezeit ≈ Zykluslänge (z. B. 3 Monate) — nie Jahre.
 */
function itemWindowAt(plan, itemId, now = Date.now()) {
  const period = Math.max(DAY_MS, planPeriodMs(plan));
  const onMs = Math.min(itemOnMs(plan, itemId), period);
  const offset = itemPhaseOffset(plan, itemId);
  const anchor = Number(plan.anchorAt) || 0;
  const into = ((now - anchor - offset) % period + period) % period;
  if (into < onMs) {
    const from = now - into;
    return { active: true, from, until: from + onMs, onMs, periodMs: period };
  }
  const from = now + (period - into);
  return { active: false, from, until: from + onMs, onMs, periodMs: period };
}

function itemActiveOnDayForPlan(plan, itemId, dayStart, dayEnd) {
  const period = Math.max(DAY_MS, planPeriodMs(plan));
  const onMs = Math.min(itemOnMs(plan, itemId), period);
  const offset = itemPhaseOffset(plan, itemId);
  const anchor = Number(plan.anchorAt) || 0;
  const into0 = ((dayStart - anchor - offset) % period + period) % period;
  const winStart = dayStart - into0;
  const winEnd = winStart + onMs;
  if (winStart < dayEnd && winEnd > dayStart) return true;
  const nextStart = winStart + period;
  return nextStart < dayEnd && nextStart + onMs > dayStart;
}

function planTiming(plan, now = Date.now()) {
  const cycleMs = planPeriodMs(plan);
  const activeMs = unitToMs(plan.activeUnit, plan.activeLength);
  const anchor = Number(plan.anchorAt) || now;
  const elapsed = Math.max(0, now - anchor);
  const cycleIndex = Math.floor(elapsed / cycleMs);
  const cycleStart = anchor + cycleIndex * cycleMs;
  const intoCycle = now - cycleStart;
  const slotsPerCycle = Math.max(1, Math.floor(cycleMs / Math.max(1, activeMs)));
  const slotIndex = Math.min(slotsPerCycle - 1, Math.floor(intoCycle / Math.max(1, activeMs)));
  const slotStart = cycleStart + slotStartOffset(slotIndex, activeMs);
  const slotEnd = Math.min(cycleStart + cycleMs, slotStart + activeMs);
  return {
    cycleMs,
    activeMs,
    cycleIndex,
    cycleStart,
    slotsPerCycle,
    slotIndex,
    slotStart,
    slotEnd,
    cycleEnd: cycleStart + cycleMs,
    model: plan.model || "independent",
    shortDays: plan.shortDays || 3,
    longDays: plan.longDays || 7,
  };
}

function slotStartOffset(slotIndex, activeMs) {
  return Math.max(0, Math.floor(Number(slotIndex) || 0) * activeMs);
}

function nextActiveWindow(plan, itemIndex, n, now = Date.now()) {
  const t = planTiming(plan, now);
  if (n <= 0) return { from: t.slotStart, until: t.slotEnd };
  const concurrent = Math.max(1, plan.concurrent || 1);
  for (let s = t.slotIndex; s < t.slotIndex + n + t.slotsPerCycle + 2; s++) {
    for (let c = 0; c < concurrent; c++) {
      if ((s + c) % n === itemIndex % n) {
        const cycleOffset = Math.floor(s / t.slotsPerCycle);
        const cycleStart = t.cycleStart + cycleOffset * t.cycleMs;
        const localSlot = ((s % t.slotsPerCycle) + t.slotsPerCycle) % t.slotsPerCycle;
        const from = cycleStart + slotStartOffset(localSlot, t.activeMs);
        const until = Math.min(cycleStart + t.cycleMs, from + t.activeMs);
        if (until > now || s === t.slotIndex) return { from, until };
      }
    }
  }
  return { from: t.slotEnd, until: t.slotEnd + t.activeMs };
}

function applyRotationPlan(db, plan, { now = Date.now(), byUserId = null } = {}) {
  if (!plan || plan.enabled === false) return { ok: true, touched: 0, active: [] };
  const items = resolvePlanItems(db, plan);
  const keepKeys = new Set(items.map((i) => shopCatalog.itemKey(i.kind, i.itemId)));
  detachOrphanPlanItems(db, plan, keepKeys, { now, byUserId });
  const n = items.length;
  if (!n) return { ok: true, touched: 0, active: [], message: "Keine Items für diesen Plan." };

  const independent = (plan.model || "independent") !== "queue";
  let touched = 0;
  const active = [];
  const t = planTiming(plan, now);

  if (independent) {
    for (const item of items) {
      if (item.rotationLocked) continue;
      const before = snapWindow(item);
      const w = itemWindowAt(plan, item.itemId, now);
      item.enabled = true;
      item.availableFrom = w.from;
      item.availableUntil = w.until;
      item.rotationPlanId = plan.id;
      item.rotationLocked = false;
      item.updatedAt = now;
      if (w.active) {
        active.push({
          kind: item.kind,
          itemId: item.itemId,
          label: item.label,
          emoji: calendarGlyph(item),
          availableFrom: w.from,
          availableUntil: w.until,
        });
      }
      const after = snapWindow(item);
      if (windowChanged(before, after)) {
        appendAvailabilityLog(db, {
          kind: item.kind,
          itemId: item.itemId,
          before,
          after,
          reason: "rotation_plan",
          byUserId,
        });
        touched += 1;
      }
    }
  } else {
    // Legacy queue-Modell (nur falls explizit gesetzt)
    const concurrent = Math.min(n, Math.max(1, plan.concurrent || 1));
    const activeIdx = new Set();
    for (let c = 0; c < concurrent; c++) activeIdx.add((t.slotIndex + c) % n);
    for (let i = 0; i < n; i++) {
      const item = items[i];
      if (item.rotationLocked) continue;
      const before = snapWindow(item);
      const on = activeIdx.has(i);
      let from;
      let until;
      if (on) {
        from = t.slotStart;
        until = t.slotEnd;
        active.push({
          kind: item.kind,
          itemId: item.itemId,
          label: item.label,
          emoji: calendarGlyph(item),
          availableFrom: from,
          availableUntil: until,
        });
      } else {
        const nxt = nextActiveWindow(plan, i, n, now);
        from = nxt.from;
        until = nxt.until;
      }
      item.enabled = true;
      item.availableFrom = from;
      item.availableUntil = until;
      item.rotationPlanId = plan.id;
      item.rotationLocked = false;
      item.updatedAt = now;
      const after = snapWindow(item);
      if (windowChanged(before, after)) {
        appendAvailabilityLog(db, {
          kind: item.kind,
          itemId: item.itemId,
          before,
          after,
          reason: "rotation_plan",
          byUserId,
        });
        touched += 1;
      }
    }
  }

  return {
    ok: true,
    touched,
    active,
    timing: t,
    itemCount: n,
    activeCount: active.length,
  };
}

function applyAllRotationPlans(db, { now = Date.now(), byUserId = null } = {}) {
  const plans = Object.values(ensureRotationPlans(db)).filter((p) => p && p.enabled !== false);
  let touched = 0;
  const results = [];
  for (const plan of plans) {
    const r = applyRotationPlan(db, plan, { now, byUserId });
    touched += r.touched || 0;
    results.push({ id: plan.id, label: plan.label, ...r });
  }
  return { ok: true, touched, plans: results };
}

/** Preview active items without persisting (pure). */
function previewPlanActive(db, plan, now = Date.now()) {
  const items = resolvePlanItems(db, plan);
  const n = items.length;
  const t = planTiming(plan, now);
  if (!n) return { items: [], timing: t, itemCount: 0, members: [] };
  const independent = (plan.model || "independent") !== "queue";
  const active = [];
  const members = [];
  if (independent) {
    for (const it of items) {
      const w = itemWindowAt(plan, it.itemId, now);
      const row = {
        kind: it.kind,
        itemId: it.itemId,
        label: it.label,
        emoji: calendarGlyph(it),
        priceCoins: shopCatalog.effectivePrice(it),
        availableFrom: w.from,
        availableUntil: w.until,
        active: w.active,
        onDays: Math.round(w.onMs / DAY_MS),
        periodDays: Math.round(w.periodMs / DAY_MS),
      };
      members.push(row);
      if (w.active) active.push(row);
    }
  } else {
    const concurrent = Math.min(n, Math.max(1, plan.concurrent || 1));
    for (let c = 0; c < concurrent; c++) {
      const i = (t.slotIndex + c) % n;
      const it = items[i];
      active.push({
        kind: it.kind,
        itemId: it.itemId,
        label: it.label,
        emoji: calendarGlyph(it),
        priceCoins: shopCatalog.effectivePrice(it),
        availableFrom: t.slotStart,
        availableUntil: t.slotEnd,
        active: true,
      });
    }
    for (let i = 0; i < n; i++) {
      const it = items[i];
      const on = active.some((a) => a.itemId === it.itemId && a.kind === it.kind);
      const nxt = on
        ? { from: t.slotStart, until: t.slotEnd }
        : nextActiveWindow(plan, i, n, now);
      members.push({
        kind: it.kind,
        itemId: it.itemId,
        label: it.label,
        emoji: calendarGlyph(it),
        priceCoins: shopCatalog.effectivePrice(it),
        availableFrom: nxt.from,
        availableUntil: nxt.until,
        active: on,
      });
    }
  }
  return { items: active, timing: t, itemCount: n, members, activeCount: active.length };
}

function listRotationPlans(db) {
  // Reine Vorschau — kein Apply (GET darf keine Fenster überschreiben)
  return listRotationPlansSafe(db);
}

function listRotationPlansSafe(db) {
  const plans = Object.values(ensureRotationPlans(db)).filter(Boolean);
  plans.sort((a, b) => String(a.label).localeCompare(String(b.label), "de"));
  const now = Date.now();
  return plans.map((p) => {
    const prev = previewPlanActive(db, p, now);
    const share =
      prev.itemCount > 0 ? Math.round((100 * (prev.activeCount || 0)) / prev.itemCount) : 0;
    return {
      ...p,
      itemCount: prev.itemCount || 0,
      activeCount: prev.activeCount || 0,
      activeSharePct: share,
      timing: prev.timing,
      activeNow: prev.items,
      members: prev.members || [],
      cycleMs: prev.timing.cycleMs,
      activeMs: prev.timing.activeMs,
      explain:
        (p.model || "independent") === "queue"
          ? `Warteschlange: ${p.concurrent || 1} gleichzeitig, Slot ${p.activeLength} ${p.activeUnit}.`
          : `Jedes Item unabhängig: ${p.shortDays || 3} oder ${p.longDays || 7} Tage im Shop, dann Pause bis zum nächsten Zyklus (~${p.cycleLength} ${p.cycleUnit}). Starts sind versetzt, damit immer etwas im Shop ist.`,
    };
  });
}

function clearPlanWindows(db, planId, { now = Date.now(), byUserId = null, keepEnabled = true } = {}) {
  const cat = shopCatalog.ensureShopCatalog(db);
  const id = String(planId || "").trim();
  let cleared = 0;
  for (const item of Object.values(cat.items || {})) {
    if (!item || item.rotationPlanId !== id) continue;
    const before = snapWindow(item);
    item.rotationPlanId = null;
    item.availableFrom = null;
    item.availableUntil = null;
    if (!keepEnabled) item.enabled = false;
    item.updatedAt = now;
    appendAvailabilityLog(db, {
      kind: item.kind,
      itemId: item.itemId,
      before,
      after: snapWindow(item),
      reason: "rotation_plan_clear",
      byUserId,
    });
    cleared += 1;
  }
  return cleared;
}

function upsertRotationPlan(db, patch, { byUserId = null } = {}) {
  const plans = ensureRotationPlans(db);
  const id = String(patch?.id || "").trim();
  const prev = id && plans[id] ? plans[id] : null;
  const next = normalizePlan({ ...(prev || {}), ...(patch || {}), id: id || undefined });
  if (prev?.seeded) next.seeded = true;
  plans[next.id] = next;
  if (next.enabled) {
    applyRotationPlan(db, next, { byUserId });
  } else if (prev && prev.enabled !== false) {
    // Gerade deaktiviert → Fenster freigeben
    clearPlanWindows(db, next.id, { byUserId, keepEnabled: true });
  }
  return { ok: true, plan: next };
}

function deleteRotationPlan(db, planId) {
  const plans = ensureRotationPlans(db);
  const id = String(planId || "").trim();
  if (!plans[id]) return { ok: false, error: "not_found", message: "Plan nicht gefunden." };
  clearPlanWindows(db, id, { keepEnabled: true });
  delete plans[id];
  return { ok: true };
}

/** Standard: teure Items (≥100) — unabhängig alle ~3 Monate für 3 oder 7 Tage. */
function ensureDefaultExpensiveRotation(db) {
  const plans = ensureRotationPlans(db);
  const existing =
    plans.expensive_3m_week ||
    Object.values(plans).find((p) => p && p.seeded === true) ||
    null;

  const desired = {
    id: "expensive_3m_week",
    label: "Teure Items · alle ~3 Monate",
    enabled: true,
    model: "independent",
    cycleUnit: "month",
    cycleLength: 3,
    activeUnit: "week",
    activeLength: 1,
    shortDays: 3,
    longDays: 7,
    concurrent: 1,
    mode: "price",
    priceMin: 100,
    priceMax: null,
    itemKeys: [],
    anchorAt: existing?.anchorAt || Date.UTC(2026, 0, 5, 0, 0, 0),
  };

  if (existing) {
    const needsMigrate =
      existing.model !== "independent" ||
      !existing.shortDays ||
      !existing.longDays ||
      existing.id !== "expensive_3m_week";
    const plan = normalizePlan({ ...existing, ...desired, id: "expensive_3m_week" });
    plan.seeded = true;
    if (existing.id !== plan.id && plans[existing.id]) delete plans[existing.id];
    plans[plan.id] = plan;
    if (needsMigrate) applyRotationPlan(db, plan);
    return { ok: true, created: false, migrated: needsMigrate, plan };
  }

  const plan = normalizePlan(desired);
  plan.seeded = true;
  plans[plan.id] = plan;
  applyRotationPlan(db, plan);
  return { ok: true, created: true, plan };
}

/** Item aus Rotation nehmen → dauerhaft im Shop (Lock). */
function removeItemFromRotation(db, kind, itemId, { byUserId = null } = {}) {
  const item = shopCatalog.getItem(db, kind, itemId);
  if (!item) {
    return { ok: false, error: "not_found", message: "Item nicht im Shop-Katalog." };
  }
  const before = snapWindow(item);
  const planId = item.rotationPlanId || null;
  const plans = ensureRotationPlans(db);
  if (planId && plans[planId]?.mode === "manual") {
    plans[planId].itemKeys = (plans[planId].itemKeys || []).filter(
      (k) => k !== shopCatalog.itemKey(kind, itemId)
    );
    plans[planId].updatedAt = Date.now();
  }
  item.rotationPlanId = null;
  item.rotationLocked = true;
  item.availableFrom = null;
  item.availableUntil = null;
  item.enabled = true;
  item.updatedAt = Date.now();
  appendAvailabilityLog(db, {
    kind,
    itemId,
    before,
    after: snapWindow(item),
    reason: "rotation_remove",
    byUserId,
  });
  return {
    ok: true,
    item: shopCatalog.publicItem(item, Date.now(), { admin: true, db }),
    removedFromPlanId: planId,
  };
}

/** Item wieder in Preis-Rotation erlauben (Lock weg). */
function rejoinRotationPool(db, kind, itemId, { byUserId = null } = {}) {
  const item = shopCatalog.getItem(db, kind, itemId);
  if (!item) {
    return { ok: false, error: "not_found", message: "Item nicht im Shop-Katalog." };
  }
  item.rotationLocked = false;
  item.updatedAt = Date.now();
  applyAllRotationPlans(db, { byUserId });
  return {
    ok: true,
    item: shopCatalog.publicItem(item, Date.now(), { admin: true, db }),
  };
}

function dayKey(ts) {
  const d = new Date(ts);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function itemActiveOnDay(item, dayStart, dayEnd) {
  if (!item || item.enabled === false) return false;
  // Nur Items mit Zeitfenster — „immer an“ würde jeden Tag fluten
  if (!item.availableFrom && !item.availableUntil) return false;
  const from = item.availableFrom || 0;
  const until = item.availableUntil || Number.MAX_SAFE_INTEGER;
  return from < dayEnd && until > dayStart;
}

/** Anzeige-Glyph für Kalenderzellen — nie raw img_*-IDs (werden zu □). */
function calendarGlyph(it) {
  if (!it) return "·";
  if (it.kind === "themes") return "🖼️";
  const id = String(it.itemId || "").trim();
  if (!id) return "·";
  if (/^img_/i.test(id) || id.startsWith("theme_")) {
    if (it.kind === "pets") return "🐾";
    if (it.kind === "stickers") return "🏷️";
    if (it.kind === "emojis") return "😊";
    return "🖼️";
  }
  // Emoji-artig (kurz, kein ASCII-ID)
  if (id.length <= 8 && !/^[a-z0-9_]+$/i.test(id)) return id;
  if (it.kind === "pets") return "🐾";
  if (it.kind === "stickers") return "🏷️";
  if (it.kind === "emojis") return "😊";
  return "·";
}

function monthGrid(db, year, month /* 1-12 */) {
  // Kein Apply hier — sonst überschreibt jeder Admin-Reload manuelle Fenster
  const y = Math.max(2000, Math.min(2100, Math.floor(Number(year) || new Date().getFullYear())));
  const m = Math.max(1, Math.min(12, Math.floor(Number(month) || new Date().getMonth() + 1)));
  const first = new Date(y, m - 1, 1);
  const startPad = (first.getDay() + 6) % 7; // Mo=0
  const daysInMonth = new Date(y, m, 0).getDate();
  const cat = shopCatalog.ensureShopCatalog(db);
  const all = Object.values(cat.items || {}).filter(Boolean);
  const plansMap = ensureRotationPlans(db);
  const days = [];
  for (let d = 1; d <= daysInMonth; d++) {
    const dayStart = new Date(y, m - 1, d).getTime();
    const dayEnd = dayStart + DAY_MS;
    const active = all
      .filter((it) => {
        const plan = it.rotationPlanId ? plansMap[it.rotationPlanId] : null;
        if (plan && plan.enabled !== false && (plan.model || "independent") !== "queue") {
          return itemActiveOnDayForPlan(plan, it.itemId, dayStart, dayEnd);
        }
        return itemActiveOnDay(it, dayStart, dayEnd);
      })
      .map((it) => ({
        kind: it.kind,
        itemId: it.itemId,
        label: it.label,
        emoji: calendarGlyph(it),
        priceCoins: shopCatalog.effectivePrice(it),
        rotationPlanId: it.rotationPlanId || null,
        availableFrom: it.availableFrom || null,
        availableUntil: it.availableUntil || null,
      }));
    const byKind = { stickers: 0, themes: 0, pets: 0, emojis: 0, other: 0 };
    for (const it of active) {
      if (byKind[it.kind] != null) byKind[it.kind] += 1;
      else byKind.other += 1;
    }
    days.push({
      date: dayKey(dayStart),
      day: d,
      weekday: (new Date(y, m - 1, d).getDay() + 6) % 7,
      items: active,
      count: active.length,
      byKind,
    });
  }
  return {
    year: y,
    month: m,
    startPad,
    daysInMonth,
    days,
    today: dayKey(Date.now()),
    plans: listRotationPlansSafe(db).map((p) => ({
      id: p.id,
      label: p.label,
      enabled: p.enabled,
      model: p.model || "independent",
      itemCount: p.itemCount,
      activeCount: p.activeCount || 0,
      activeSharePct: p.activeSharePct || 0,
      activeNow: p.activeNow,
      explain: p.explain,
      cycleUnit: p.cycleUnit,
      cycleLength: p.cycleLength,
      activeUnit: p.activeUnit,
      activeLength: p.activeLength,
      shortDays: p.shortDays || 3,
      longDays: p.longDays || 7,
      concurrent: p.concurrent,
      mode: p.mode,
      priceMin: p.priceMin,
      priceMax: p.priceMax,
      itemKeys: p.itemKeys || [],
      members: (p.members || []).slice(0, 80),
      anchorAt: p.anchorAt,
      seeded: Boolean(p.seeded),
    })),
  };
}

function listCalendar(db, { kind = null, q = "", status = "", mark = "" } = {}) {
  shopCatalog.deactivateExpired(db);
  const cat = shopCatalog.ensureShopCatalog(db);
  const now = Date.now();
  const stats = db.shopStats && typeof db.shopStats === "object" ? db.shopStats : {};
  let items = Object.values(cat.items || {}).filter(Boolean);
  if (kind) items = items.filter((i) => i.kind === kind);
  const qq = String(q || "").trim().toLowerCase();
  if (qq) {
    items = items.filter((i) => {
      const label = itemLabels.resolveDisplayLabel(
        db,
        i.kind,
        i.itemId,
        displayNameForEmoji,
        i.label
      );
      const hay = `${i.itemId} ${i.label} ${label} ${i.kind}`.toLowerCase();
      return hay.includes(qq);
    });
  }

  const rows = items.map((raw) => {
    const pub = shopCatalog.publicItem(raw, now, { admin: true, db });
    const key = shopCatalog.itemKey(raw.kind, raw.itemId);
    const shopBuys = Math.max(Number(stats[key]) || 0, Number(raw.soldTotal) || 0);
    const multi = multiBuyStats(db, raw.kind, raw.itemId);
    const mkt = marketStats(db, raw.kind, raw.itemId, pub.listPrice || pub.priceCoins);
    const history = logForItem(db, raw.kind, raw.itemId);
    const presence = summarizePresence(raw, history);
    const st = statusOf(raw, now);
    return {
      ...pub,
      status: st,
      shopBuys,
      ...multi,
      market: mkt,
      presenceMs: presence.totalMs,
      presenceSegments: presence.segments,
      availabilityLog: history,
      rotationPlanId: raw.rotationPlanId || null,
      rotationLocked: raw.rotationLocked === true,
    };
  });

  let filtered = rows;
  if (status) filtered = filtered.filter((r) => r.status === status);
  if (mark === "hot") filtered = filtered.filter((r) => r.market?.hot);
  if (mark === "premium") filtered = filtered.filter((r) => r.market?.premium);
  if (mark === "multibuy") filtered = filtered.filter((r) => r.multiBuyUsers > 0);
  if (mark === "market") filtered = filtered.filter((r) => (r.market?.salesTotal || 0) > 0);
  if (mark === "rotation") filtered = filtered.filter((r) => r.rotationPlanId);

  filtered.sort((a, b) => {
    const rank = { window: 0, always: 1, scheduled: 2, off: 3, expired: 4, missing: 5 };
    const d = (rank[a.status] ?? 9) - (rank[b.status] ?? 9);
    if (d !== 0) return d;
    return String(a.label || "").localeCompare(String(b.label || ""), "de");
  });

  return {
    items: filtered,
    count: filtered.length,
    now,
    marks: {
      hot: rows.filter((r) => r.market?.hot).length,
      premium: rows.filter((r) => r.market?.premium).length,
      multibuy: rows.filter((r) => r.multiBuyUsers > 0).length,
      market: rows.filter((r) => (r.market?.salesTotal || 0) > 0).length,
      rotation: rows.filter((r) => r.rotationPlanId).length,
    },
    plans: listRotationPlansSafe(db),
  };
}

function updateAvailability(db, kind, itemId, patch, { byUserId = null } = {}) {
  const item = shopCatalog.getItem(db, kind, itemId);
  if (!item) {
    return { ok: false, error: "not_found", message: "Item nicht im Shop-Katalog." };
  }
  const before = snapWindow(item);
  if (patch.availableFrom !== undefined) {
    item.availableFrom = parseDateInput(patch.availableFrom);
  }
  if (patch.availableUntil !== undefined) {
    item.availableUntil = parseDateInput(patch.availableUntil);
  }
  if (
    item.availableFrom != null &&
    item.availableUntil != null &&
    item.availableUntil < item.availableFrom
  ) {
    return { ok: false, error: "bad_window", message: "Bis-Datum liegt vor Von-Datum." }; // validated
  }
  if (patch.enabled !== undefined) {
    item.enabled = Boolean(patch.enabled);
  }
  if (patch.rotationPlanId !== undefined) {
    item.rotationPlanId = patch.rotationPlanId
      ? String(patch.rotationPlanId).trim().slice(0, 64)
      : null;
  }
  // Manuelles Fenster löst Item aus Rotation
  if (
    (patch.availableFrom !== undefined || patch.availableUntil !== undefined) &&
    patch.rotationPlanId === undefined
  ) {
    item.rotationPlanId = null;
    item.rotationLocked = true;
  }
  if (patch.rotationLocked !== undefined) {
    item.rotationLocked = Boolean(patch.rotationLocked);
  }
  // Explizit wieder in Rotation: Lock aufheben
  if (patch.rotationPlanId) {
    item.rotationLocked = false;
  }
  item.updatedAt = Date.now();
  const after = snapWindow(item);
  appendAvailabilityLog(db, {
    kind,
    itemId,
    before,
    after,
    reason: "admin_calendar",
    byUserId,
  });
  return {
    ok: true,
    item: shopCatalog.publicItem(item, Date.now(), { admin: true, db }),
  };
}

function batchAvailability(db, body, { byUserId = null } = {}) {
  const keys = Array.isArray(body?.itemKeys) ? body.itemKeys : [];
  const items = Array.isArray(body?.items) ? body.items : [];
  const targets = [];
  for (const k of keys) {
    const [kind, ...rest] = String(k).split(":");
    const itemId = rest.join(":");
    if (kind && itemId) targets.push({ kind, itemId });
  }
  for (const it of items) {
    if (it?.kind && it?.itemId) targets.push({ kind: it.kind, itemId: it.itemId });
  }
  const uniq = new Map();
  for (const t of targets) uniq.set(`${t.kind}:${t.itemId}`, t);
  const patch = {
    availableFrom: body?.availableFrom,
    availableUntil: body?.availableUntil,
    enabled: body?.enabled,
    rotationPlanId: body?.rotationPlanId,
  };
  const updated = [];
  const skipped = [];
  for (const t of uniq.values()) {
    const r = updateAvailability(db, t.kind, t.itemId, patch, { byUserId });
    if (r.ok) updated.push(r.item);
    else skipped.push({ kind: t.kind, itemId: t.itemId, error: r.error || "failed" });
  }
  return { ok: true, count: updated.length, items: updated, skipped };
}

module.exports = {
  ensureAvailabilityLog,
  appendAvailabilityLog,
  listCalendar,
  updateAvailability,
  batchAvailability,
  parseDateInput,
  statusOf,
  snapWindow,
  windowChanged,
  unitToMs,
  ensureRotationPlans,
  removeItemFromRotation,
  rejoinRotationPool,
  normalizePlan,
  listRotationPlans: listRotationPlansSafe,
  upsertRotationPlan,
  deleteRotationPlan,
  applyRotationPlan,
  applyAllRotationPlans,
  ensureDefaultExpensiveRotation,
  monthGrid,
  previewPlanActive,
  DAY_MS,
  WEEK_MS,
  MONTH_MS,
};

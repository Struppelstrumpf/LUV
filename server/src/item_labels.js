/**
 * Admin-Anzeigenamen: wirken nur auf die Namensanzeige (Shop, Inventar, Universe).
 * Keine Preise, Limits oder Shop-Aktivierung.
 */

function itemKey(kind, itemId) {
  return `${String(kind || "").trim()}:${String(itemId || "").trim()}`;
}

function ensureItemLabels(db) {
  if (!db || typeof db !== "object") return {};
  if (!db.itemDisplayLabels || typeof db.itemDisplayLabels !== "object") {
    db.itemDisplayLabels = {};
  }
  return db.itemDisplayLabels;
}

function getDisplayLabel(db, kind, itemId) {
  const map = ensureItemLabels(db);
  const key = itemKey(kind, itemId);
  const v = map[key];
  if (typeof v === "string" && v.trim()) return v.trim().slice(0, 40);
  return null;
}

/**
 * Setzt oder entfernt den Anzeigenamen. label null/"" → Override löschen.
 */
function setDisplayLabel(db, kind, itemId, label) {
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim().slice(0, 32);
  if (!["emojis", "stickers", "themes", "pets"].includes(k) || !id) {
    return { ok: false, error: "bad_item", message: "Kategorie oder ID ungültig." };
  }
  const map = ensureItemLabels(db);
  const key = itemKey(k, id);
  const clean = String(label ?? "")
    .trim()
    .slice(0, 40);
  if (!clean) {
    delete map[key];
    return { ok: true, kind: k, itemId: id, label: null, cleared: true };
  }
  map[key] = clean;
  return { ok: true, kind: k, itemId: id, label: clean, cleared: false };
}

/** Bester Anzeigename: Override → Shop-Label (wenn ≠ ID) → Fallback-Fn */
function resolveDisplayLabel(db, kind, itemId, fallbackFn, shopLabel) {
  const override = getDisplayLabel(db, kind, itemId);
  if (override) return override;
  const shop = String(shopLabel || "").trim();
  const id = String(itemId || "").trim();
  if (shop && shop !== id) return shop.slice(0, 40);
  if (typeof fallbackFn === "function") {
    return String(fallbackFn(id) || id).slice(0, 40) || id;
  }
  return id;
}

module.exports = {
  itemKey,
  ensureItemLabels,
  getDisplayLabel,
  setDisplayLabel,
  resolveDisplayLabel,
};

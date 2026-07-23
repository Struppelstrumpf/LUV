/**
 * Spieler-Marktplatz (Nasebär-/Tauschplatz-Stil):
 * Öffentlicher Markt, private Angebote, Kauf mit Coins, Tausch.
 */

const crypto = require("crypto");
const { isAchievementSticker } = require("./sticker_catalog");
const shopCatalog = require("./shop_catalog");

// Gleiche Reihenfolge/Namen wie Itemshop & Inventar
const CATEGORIES = [
  { id: "all", label: "Alle", emoji: "📦" },
  { id: "stickers", label: "Sticker", emoji: "🎀" },
  { id: "themes", label: "Hintergründe", emoji: "🖼️" },
  { id: "pets", label: "Begleiter", emoji: "🐣" },
  { id: "emojis", label: "Emojis", emoji: "😊" },
];

function newListingId() {
  return `ml_${crypto.randomBytes(10).toString("hex")}`;
}

const PRICE_WINDOW_OPTIONS = [7, 14, 30, 60, 90, 180];
const DEFAULT_PRICE_WINDOW_DAYS = 90;
const SALE_HISTORY_MAX_DAYS = 180;
const SALE_HISTORY_MAX_LEN = 200;

function ensureMarket(db) {
  if (!db.marketListings || typeof db.marketListings !== "object") {
    db.marketListings = {};
  }
  if (!db.marketMeta || typeof db.marketMeta !== "object") {
    db.marketMeta = { priceHistory: {}, saleHistory: {} };
  }
  if (!db.marketMeta.priceHistory || typeof db.marketMeta.priceHistory !== "object") {
    db.marketMeta.priceHistory = {};
  }
  if (!db.marketMeta.saleHistory || typeof db.marketMeta.saleHistory !== "object") {
    db.marketMeta.saleHistory = {};
  }
  const w = Math.floor(Number(db.marketMeta.priceWindowDays) || 0);
  if (!PRICE_WINDOW_OPTIONS.includes(w)) {
    db.marketMeta.priceWindowDays = DEFAULT_PRICE_WINDOW_DAYS;
  }
  return db.marketListings;
}

function priceWindowDays(db) {
  ensureMarket(db);
  const w = Math.floor(Number(db.marketMeta.priceWindowDays) || 0);
  return PRICE_WINDOW_OPTIONS.includes(w) ? w : DEFAULT_PRICE_WINDOW_DAYS;
}

function setPriceWindowDays(db, days) {
  ensureMarket(db);
  const w = Math.floor(Number(days) || 0);
  if (!PRICE_WINDOW_OPTIONS.includes(w)) {
    return { ok: false, error: "bad_window" };
  }
  db.marketMeta.priceWindowDays = w;
  return { ok: true, priceWindowDays: w, options: PRICE_WINDOW_OPTIONS };
}

function itemKey(kind, itemId) {
  return `${kind}:${itemId}`;
}

/** @deprecated Listing-Preis — nur noch für alte Trends; Verkäufe → recordSale */
function recordPrice(db, kind, itemId, price) {
  ensureMarket(db);
  const key = itemKey(kind, itemId);
  if (!db.marketMeta.priceHistory[key]) db.marketMeta.priceHistory[key] = [];
  const hist = db.marketMeta.priceHistory[key];
  hist.push({ price: Number(price) || 0, at: Date.now() });
  if (hist.length > 40) db.marketMeta.priceHistory[key] = hist.slice(-40);
}

function pruneSaleHistory(hist) {
  const cutoff = Date.now() - SALE_HISTORY_MAX_DAYS * 24 * 60 * 60 * 1000;
  return (hist || [])
    .filter((h) => h && (Number(h.at) || 0) >= cutoff && (Number(h.price) || 0) > 0)
    .slice(-SALE_HISTORY_MAX_LEN);
}

/** Echten Verkaufspreis merken (nur bei Kauf). */
function recordSale(db, kind, itemId, price) {
  ensureMarket(db);
  const key = itemKey(kind, itemId);
  if (!db.marketMeta.saleHistory[key]) db.marketMeta.saleHistory[key] = [];
  const p = Math.max(0, Math.floor(Number(price) || 0));
  if (p < 1) return;
  db.marketMeta.saleHistory[key].push({ price: p, at: Date.now() });
  db.marketMeta.saleHistory[key] = pruneSaleHistory(db.marketMeta.saleHistory[key]);
}

/** Einmalig: sold-Listings → saleHistory (falls leer). */
function backfillSaleHistory(db) {
  ensureMarket(db);
  if (db.marketMeta.saleHistoryBackfilled) return;
  const listings = db.marketListings || {};
  for (const e of Object.values(listings)) {
    if (!e || e.status !== "sold") continue;
    const p = Math.max(0, Math.floor(Number(e.priceCoins) || 0));
    const at = Math.max(0, Number(e.soldAt) || Number(e.createdAt) || 0);
    if (p < 1 || !e.kind || !e.itemId || !at) continue;
    const key = itemKey(e.kind, e.itemId);
    if (!db.marketMeta.saleHistory[key]) db.marketMeta.saleHistory[key] = [];
    db.marketMeta.saleHistory[key].push({ price: p, at });
  }
  for (const key of Object.keys(db.marketMeta.saleHistory)) {
    const sorted = pruneSaleHistory(db.marketMeta.saleHistory[key]).sort(
      (a, b) => (a.at || 0) - (b.at || 0)
    );
    db.marketMeta.saleHistory[key] = sorted;
  }
  db.marketMeta.saleHistoryBackfilled = true;
}

function salesInWindow(db, kind, itemId, windowDays) {
  ensureMarket(db);
  backfillSaleHistory(db);
  const hist = db.marketMeta.saleHistory[itemKey(kind, itemId)] || [];
  const days = Math.max(1, Math.floor(Number(windowDays) || DEFAULT_PRICE_WINDOW_DAYS));
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  return hist.filter((h) => (Number(h.at) || 0) >= cutoff);
}

function allSales(db, kind, itemId) {
  ensureMarket(db);
  backfillSaleHistory(db);
  return db.marketMeta.saleHistory[itemKey(kind, itemId)] || [];
}

function trendFromSales(sales) {
  if (!sales || sales.length < 2) return "=";
  const last = Number(sales[sales.length - 1].price) || 0;
  const prev = Number(sales[sales.length - 2].price) || 0;
  if (last > prev) return "↑";
  if (last < prev) return "↓";
  return "=";
}

function rangeFromPrices(prices) {
  const nums = (prices || [])
    .map((p) => Math.max(0, Math.floor(Number(p) || 0)))
    .filter((p) => p > 0);
  if (!nums.length) return null;
  return {
    min: Math.min(...nums),
    max: Math.max(...nums),
    count: nums.length,
  };
}

function openListingPrices(db, kind, itemId) {
  const listings = ensureMarket(db);
  const now = Date.now();
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim();
  return Object.values(listings)
    .filter((e) => {
      if (!e || e.status !== "open" || e.private) return false;
      if (e.kind !== k || e.itemId !== id) return false;
      if (e.expiresAt && e.expiresAt < now) return false;
      return (Number(e.priceCoins) || 0) > 0;
    })
    .map((e) => Number(e.priceCoins) || 0);
}

/**
 * Preis-Infos für UI:
 * - shopPrice: nur wenn Itemshop-Preis > 0
 * - sales: min/max Verkäufe im Fenster (nur wenn verkauft)
 * - listings: aktuelle Angebotsspanne, nur wenn noch nie verkauft
 */
function priceInsight(db, kind, itemId, shopPriceRaw) {
  ensureMarket(db);
  backfillSaleHistory(db);
  const windowDays = priceWindowDays(db);
  const shopN = Math.max(0, Math.floor(Number(shopPriceRaw) || 0));
  const shopPrice = shopN > 0 ? shopN : null;

  const ever = allSales(db, kind, itemId);
  const inWin = salesInWindow(db, kind, itemId, windowDays);
  let sales = null;
  let listings = null;
  let source = "none";

  if (ever.length > 0) {
    const range = rangeFromPrices(inWin.map((h) => h.price));
    if (range) {
      sales = { ...range, trend: trendFromSales(inWin) };
      source = "sales";
    }
  } else {
    const listingRange = rangeFromPrices(openListingPrices(db, kind, itemId));
    if (listingRange) {
      listings = { ...listingRange, trend: "=" };
      source = "listings";
    }
  }

  const trend =
    sales?.trend ||
    listings?.trend ||
    "=";

  const lastSaleRaw = ever.length
    ? Math.max(0, Math.floor(Number(ever[ever.length - 1].price) || 0))
    : 0;
  return {
    shopPrice,
    windowDays,
    sales,
    listings,
    source,
    trend,
    /** Letzter Marktplatz-Verkaufspreis (alle Zeiten), sonst null */
    lastSalePrice: lastSaleRaw > 0 ? lastSaleRaw : null,
  };
}

function trendFor(db, kind, itemId, currentPrice) {
  ensureMarket(db);
  backfillSaleHistory(db);
  const insight = priceInsight(db, kind, itemId, null);
  if (insight.sales) return insight.sales.trend;
  // Fallback: alte Listing-Historie
  const hist = db.marketMeta.priceHistory[itemKey(kind, itemId)] || [];
  if (hist.length < 2) return "=";
  const prev = hist[hist.length - 2]?.price ?? currentPrice;
  if (currentPrice > prev) return "↑";
  if (currentPrice < prev) return "↓";
  return "=";
}

function listingPublic(entry, viewerId, db) {
  if (!entry) return null;
  const mine = entry.sellerId === viewerId;
  return {
    id: entry.id,
    kind: entry.kind,
    itemId: entry.itemId,
    label: entry.label,
    emoji: entry.emoji,
    category: entry.category,
    priceCoins: entry.priceCoins,
    allowTrade: Boolean(entry.allowTrade),
    tradeWantKind: entry.tradeWantKind || null,
    tradeWantItemId: entry.tradeWantItemId || null,
    tradeWantLabel: entry.tradeWantLabel || null,
    private: Boolean(entry.private),
    targetUserId: entry.targetUserId || null,
    sellerId: entry.sellerId,
    sellerNickname: entry.sellerNickname,
    createdAt: entry.createdAt,
    stock: 1,
    offerCount: 1,
    trend: trendFor(db, entry.kind, entry.itemId, entry.priceCoins),
    isMine: mine,
    ownedByViewer: mine,
  };
}

function aggregateMarket(db, viewerId, { category, q, mode }) {
  const listings = ensureMarket(db);
  const now = Date.now();
  const active = Object.values(listings).filter((e) => {
    if (!e || e.status !== "open") return false;
    if (e.expiresAt && e.expiresAt < now) return false;
    if (mode === "private") {
      return (
        e.private &&
        (e.sellerId === viewerId || e.targetUserId === viewerId)
      );
    }
    // public market
    if (e.private) return false;
    return true;
  });

  // Ein Eintrag pro Item (Nasebär): günstigster Preis, Anzahl Angebote
  const groups = new Map();
  for (const e of active) {
    if (category && category !== "all" && e.category !== category) continue;
    if (q) {
      const itemMatch = shopCatalog.matchesSearchQuery(
        {
          itemId: e.itemId,
          label: e.label,
          kind: e.kind,
          searchText: `${e.emoji || ""} ${e.sellerNickname || ""}`,
        },
        q
      );
      if (!itemMatch) continue;
    }
    const key = `${e.kind}|${e.itemId}`;
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        kind: e.kind,
        itemId: e.itemId,
        label: e.label,
        emoji: e.emoji,
        category: e.category,
        listings: [],
      });
    }
    groups.get(key).listings.push(e);
  }

  const items = [...groups.values()]
    .map((g) => {
      const sorted = [...g.listings].sort((a, b) => {
        const pa = Number(a.priceCoins) || 0;
        const pb = Number(b.priceCoins) || 0;
        if (pa !== pb) return pa - pb;
        return (a.createdAt || 0) - (b.createdAt || 0);
      });
      // Preis/Listing nur aus fremden Angeboten — eigene nicht mit Minimum vermischen
      const buyableList = sorted.filter((l) => l.sellerId !== viewerId);
      const coinBuyable = buyableList.filter((l) => (Number(l.priceCoins) || 0) > 0);
      const ownCoin = sorted.filter(
        (l) => l.sellerId === viewerId && (Number(l.priceCoins) || 0) > 0
      );
      const minPrice = coinBuyable.length
        ? Math.min(...coinBuyable.map((l) => Number(l.priceCoins) || 0))
        : ownCoin.length
          ? Math.min(...ownCoin.map((l) => Number(l.priceCoins) || 0))
          : Math.min(...sorted.map((l) => Number(l.priceCoins) || 0));
      const best = buyableList[0] || sorted[0];
      const hasMine = sorted.some((l) => l.sellerId === viewerId);
      const allMine = !buyableList.length;
      const anyTrade = sorted.some((l) => l.allowTrade);
      return {
        listingId: best.id,
        kind: g.kind,
        itemId: g.itemId,
        label: g.label,
        emoji: g.emoji,
        category: g.category,
        priceCoins: Number.isFinite(minPrice) ? minPrice : 0,
        allowTrade: anyTrade,
        trend: trendFor(
          db,
          g.kind,
          g.itemId,
          coinBuyable.length ? minPrice : minPrice
        ),
        stock: g.listings.length,
        offerCount: g.listings.length,
        isMine: allMine,
        ownedByViewer: hasMine,
        sellerNickname: best.sellerNickname,
      };
    })
    .sort((a, b) => a.label.localeCompare(b.label, "de"));

  return {
    categories: CATEGORIES,
    items,
    count: items.length,
    mode: mode || "market",
  };
}

/** Alle offenen Angebote zu einem Item (für Drill-down). */
function listOffersForItem(db, viewerId, { kind, itemId, mode }) {
  const listings = ensureMarket(db);
  const now = Date.now();
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim();
  if (!k || !id) return { offers: [], count: 0 };

  const offers = Object.values(listings)
    .filter((e) => {
      if (!e || e.status !== "open") return false;
      if (e.kind !== k || e.itemId !== id) return false;
      if (e.expiresAt && e.expiresAt < now) return false;
      if (mode === "private") {
        return (
          e.private &&
          (e.sellerId === viewerId || e.targetUserId === viewerId)
        );
      }
      return !e.private;
    })
    .map((e) => listingPublic(e, viewerId, db))
    .filter(Boolean)
    .sort((a, b) => {
      const pa = Number(a.priceCoins) || 0;
      const pb = Number(b.priceCoins) || 0;
      if (pa !== pb) return pa - pb;
      return (a.createdAt || 0) - (b.createdAt || 0);
    });

  return {
    kind: k,
    itemId: id,
    label: offers[0]?.label || id,
    emoji: offers[0]?.emoji || "?",
    category: offers[0]?.category || k,
    offers,
    count: offers.length,
  };
}

/** Begleiter als Zähl-Map (wie Sticker) — Array-Altbestand → Map. */
function asPetMap(inv) {
  if (!inv) return {};
  if (Array.isArray(inv.pets)) {
    const next = {};
    for (const p of inv.pets) {
      const id = String(p || "").trim();
      if (!id) continue;
      next[id] = Math.min(999, (Number(next[id]) || 0) + 1);
    }
    inv.pets = next;
  } else if (!inv.pets || typeof inv.pets !== "object") {
    inv.pets = {};
  }
  return inv.pets;
}

function petCountOf(inv, itemId) {
  const id = String(itemId || "").trim();
  if (!id) return 0;
  return Math.max(0, Math.floor(Number(asPetMap(inv)[id]) || 0));
}

function petReserved(user, inv, itemId) {
  const id = String(itemId || "").trim();
  if (!id) return 0;
  let reserved = 0;
  if (String(inv.equippedPet || "") === id) reserved = 1;
  const companion = String(user?.profileCanvas?.companionEmoji || "").trim();
  if (companion === id) reserved = Math.max(reserved, 1);
  return reserved;
}

function userOwnsItem(user, ensureInventory, kind, itemId) {
  const inv = ensureInventory(user);
  if (kind === "pets") return petCountOf(inv, itemId) > 0;
  if (kind === "themes") return (inv.themes || []).includes(itemId);
  if (kind === "stickers") return (Number(inv.stickers?.[itemId]) || 0) > 0;
  if (kind === "emojis") return (Number(inv.emojis?.[itemId]) || 0) > 0;
  return false;
}

function takeItemFromUser(user, ensureInventory, kind, itemId) {
  const inv = ensureInventory(user);
  if (kind === "pets") {
    if (itemId === "🐣") return false; // Starter nicht verkaufen
    const pets = asPetMap(inv);
    const owned = petCountOf(inv, itemId);
    if (owned < 1) return false;
    // Freier Bestand = owned − 1 wenn ausgerüstet/Profil (wie Sticker)
    if (owned - petReserved(user, inv, itemId) < 1) return false;
    pets[itemId] = owned - 1;
    if (pets[itemId] <= 0) delete pets[itemId];
    return true;
  }
  if (kind === "themes") {
    if (itemId === "meadow") return false;
    const i = inv.themes.indexOf(itemId);
    if (i < 0) return false;
    inv.themes.splice(i, 1);
    return true;
  }
  if (kind === "stickers") {
    const owned = Math.max(0, Math.floor(Number(inv.stickers[itemId]) || 0));
    if (owned < 1) return false;
    // Nur freien Bestand nehmen (nicht Profil-Platzierungen)
    let placed = 0;
    const layout = user?.profileCanvas?.layout;
    if (Array.isArray(layout)) {
      for (const el of layout) {
        if (!el || String(el.type || "").toLowerCase() !== "sticker") continue;
        const e = String(el.emoji || el.text || "").trim();
        if (e === itemId) placed += 1;
      }
    }
    if (owned - placed < 1) return false;
    inv.stickers[itemId] = owned - 1;
    if (inv.stickers[itemId] <= 0) delete inv.stickers[itemId];
    return true;
  }
  if (kind === "emojis") {
    const owned = Math.max(0, Math.floor(Number(inv.emojis[itemId]) || 0));
    if (owned < 1) return false;
    // Starter-Emojis nicht unter 1
    const starters = ["👍", "❌", "❤️", "😂", "😱", "😡", "😭"];
    if (starters.includes(itemId) && owned <= 1) return false;
    // Letztes Exemplar in der Reaktionsleiste nicht entnehmen
    const bar = Array.isArray(user?.settings?.emojiBar) ? user.settings.emojiBar : [];
    const inBar = bar.some((e) => String(e) === itemId);
    if (inBar && owned <= 1) return false;
    inv.emojis[itemId] = owned - 1;
    if (inv.emojis[itemId] <= 0) delete inv.emojis[itemId];
    return true;
  }
  return false;
}

function giveItemToUser(user, ensureInventory, kind, itemId) {
  const inv = ensureInventory(user);
  if (kind === "pets") {
    const pets = asPetMap(inv);
    const id = String(itemId || "").trim();
    if (!id) return false;
    pets[id] = Math.min(999, (Number(pets[id]) || 0) + 1);
    return true;
  }
  if (kind === "themes") {
    if (!inv.themes.includes(itemId)) inv.themes.push(itemId);
    return true;
  }
  if (kind === "stickers") {
    // Kapelle & andere Erfolgs-Sticker: nie unter 1, nie absenken
    if (isAchievementSticker(itemId)) {
      const cur = Math.max(0, Math.floor(Number(inv.stickers[itemId]) || 0));
      inv.stickers[itemId] = Math.max(1, cur);
      return true;
    }
    inv.stickers[itemId] = (Number(inv.stickers[itemId]) || 0) + 1;
    return true;
  }
  if (kind === "emojis") {
    inv.emojis[itemId] = (Number(inv.emojis[itemId]) || 0) + 1;
    return true;
  }
  return false;
}

function resolveLabel(kind, itemId, shopCatalogHints) {
  if (shopCatalogHints?.label) return shopCatalogHints.label;
  return itemId;
}

module.exports = {
  CATEGORIES,
  PRICE_WINDOW_OPTIONS,
  DEFAULT_PRICE_WINDOW_DAYS,
  ensureMarket,
  aggregateMarket,
  listOffersForItem,
  listingPublic,
  newListingId,
  recordPrice,
  recordSale,
  allSales,
  salesInWindow,
  priceInsight,
  priceWindowDays,
  setPriceWindowDays,
  backfillSaleHistory,
  userOwnsItem,
  takeItemFromUser,
  giveItemToUser,
  resolveLabel,
  itemKey,
};

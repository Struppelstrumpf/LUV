/**
 * Spieler-Marktplatz (Nasebär-/Tauschplatz-Stil):
 * Öffentlicher Markt, private Angebote, Kauf mit Coins, Tausch.
 */

const crypto = require("crypto");

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

function ensureMarket(db) {
  if (!db.marketListings || typeof db.marketListings !== "object") {
    db.marketListings = {};
  }
  if (!db.marketMeta || typeof db.marketMeta !== "object") {
    db.marketMeta = { priceHistory: {} };
  }
  return db.marketListings;
}

function itemKey(kind, itemId) {
  return `${kind}:${itemId}`;
}

function recordPrice(db, kind, itemId, price) {
  ensureMarket(db);
  const key = itemKey(kind, itemId);
  if (!db.marketMeta.priceHistory[key]) db.marketMeta.priceHistory[key] = [];
  const hist = db.marketMeta.priceHistory[key];
  hist.push({ price: Number(price) || 0, at: Date.now() });
  if (hist.length > 40) db.marketMeta.priceHistory[key] = hist.slice(-40);
}

function trendFor(db, kind, itemId, currentPrice) {
  ensureMarket(db);
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
      const hay = `${e.label} ${e.emoji} ${e.sellerNickname}`.toLowerCase();
      if (!hay.includes(q.toLowerCase())) continue;
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
      const coinOffers = sorted.filter((l) => (Number(l.priceCoins) || 0) > 0);
      const minPrice = coinOffers.length
        ? Math.min(...coinOffers.map((l) => Number(l.priceCoins) || 0))
        : Math.min(...sorted.map((l) => Number(l.priceCoins) || 0));
      const buyable = sorted.find((l) => l.sellerId !== viewerId);
      const best = buyable || sorted[0];
      const hasMine = sorted.some((l) => l.sellerId === viewerId);
      const allMine = !buyable;
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
        trend: trendFor(db, g.kind, g.itemId, minPrice),
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

function userOwnsItem(user, ensureInventory, kind, itemId) {
  const inv = ensureInventory(user);
  if (kind === "pets") return (inv.pets || []).includes(itemId);
  if (kind === "themes") return (inv.themes || []).includes(itemId);
  if (kind === "stickers") return (Number(inv.stickers?.[itemId]) || 0) > 0;
  if (kind === "emojis") return (Number(inv.emojis?.[itemId]) || 0) > 0;
  return false;
}

function takeItemFromUser(user, ensureInventory, kind, itemId) {
  const inv = ensureInventory(user);
  if (kind === "pets") {
    if (itemId === "🐣") return false; // Starter nicht verkaufen
    // Ausgerüstet → nicht entnehmen (Caller muss vorher prüfen)
    if (inv.equippedPet === itemId) return false;
    const i = inv.pets.indexOf(itemId);
    if (i < 0) return false;
    inv.pets.splice(i, 1);
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
    if ((Number(inv.stickers[itemId]) || 0) < 1) return false;
    inv.stickers[itemId] -= 1;
    if (inv.stickers[itemId] <= 0) delete inv.stickers[itemId];
    return true;
  }
  if (kind === "emojis") {
    if ((Number(inv.emojis[itemId]) || 0) < 1) return false;
    // Starter-Emojis nicht unter 1
    const starters = ["👍", "❌", "❤️", "😂", "😱", "😡", "😭"];
    if (starters.includes(itemId) && inv.emojis[itemId] <= 1) return false;
    inv.emojis[itemId] -= 1;
    if (inv.emojis[itemId] <= 0) delete inv.emojis[itemId];
    return true;
  }
  return false;
}

function giveItemToUser(user, ensureInventory, kind, itemId) {
  const inv = ensureInventory(user);
  if (kind === "pets") {
    if (!inv.pets.includes(itemId)) inv.pets.push(itemId);
    return;
  }
  if (kind === "themes") {
    if (!inv.themes.includes(itemId)) inv.themes.push(itemId);
    return;
  }
  if (kind === "stickers") {
    inv.stickers[itemId] = (Number(inv.stickers[itemId]) || 0) + 1;
    return;
  }
  if (kind === "emojis") {
    inv.emojis[itemId] = (Number(inv.emojis[itemId]) || 0) + 1;
  }
}

function resolveLabel(kind, itemId, shopCatalogHints) {
  if (shopCatalogHints?.label) return shopCatalogHints.label;
  return itemId;
}

module.exports = {
  CATEGORIES,
  ensureMarket,
  aggregateMarket,
  listOffersForItem,
  listingPublic,
  newListingId,
  recordPrice,
  userOwnsItem,
  takeItemFromUser,
  giveItemToUser,
  resolveLabel,
  itemKey,
};

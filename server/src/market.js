/**
 * Spieler-Marktplatz (Nasebär-/Tauschplatz-Stil):
 * Öffentlicher Markt, private Angebote, Kauf mit Coins, Tausch.
 */

const crypto = require("crypto");

const CATEGORIES = [
  { id: "all", label: "Alle Artikel", emoji: "📦" },
  { id: "pets", label: "Begleiter", emoji: "🐣" },
  { id: "stickers", label: "Sticker", emoji: "🎀" },
  { id: "themes", label: "Profil", emoji: "🖼️" },
  { id: "emojis", label: "Reaktionen", emoji: "😊" },
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

  // Gruppieren nach kind+itemId für Markt-Ansicht (wie Nasebär Artikelzeilen)
  const groups = new Map();
  for (const e of active) {
    if (category && category !== "all" && e.category !== category) continue;
    if (q) {
      const hay = `${e.label} ${e.emoji} ${e.sellerNickname}`.toLowerCase();
      if (!hay.includes(q.toLowerCase())) continue;
    }
    const key = `${e.kind}|${e.itemId}|${e.allowTrade ? "t" : "c"}|${e.priceCoins}`;
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        kind: e.kind,
        itemId: e.itemId,
        label: e.label,
        emoji: e.emoji,
        category: e.category,
        priceCoins: e.priceCoins,
        allowTrade: Boolean(e.allowTrade),
        listings: [],
        minPrice: e.priceCoins,
      });
    }
    const g = groups.get(key);
    g.listings.push(e);
    g.minPrice = Math.min(g.minPrice, e.priceCoins);
  }

  const items = [...groups.values()]
    .map((g) => {
      const best = g.listings.sort((a, b) => a.priceCoins - b.priceCoins)[0];
      const owned = g.listings.some((l) => l.sellerId === viewerId);
      return {
        listingId: best.id,
        kind: g.kind,
        itemId: g.itemId,
        label: g.label,
        emoji: g.emoji,
        category: g.category,
        priceCoins: g.minPrice,
        allowTrade: g.allowTrade,
        trend: trendFor(db, g.kind, g.itemId, g.minPrice),
        stock: g.listings.length,
        offerCount: g.listings.length,
        isMine: owned,
        ownedByViewer: owned,
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
    const i = inv.pets.indexOf(itemId);
    if (i < 0) return false;
    inv.pets.splice(i, 1);
    if (inv.equippedPet === itemId) inv.equippedPet = "🐣";
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
  listingPublic,
  newListingId,
  recordPrice,
  userOwnsItem,
  takeItemFromUser,
  giveItemToUser,
  resolveLabel,
  itemKey,
};

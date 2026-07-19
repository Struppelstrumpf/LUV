/**
 * Einheitliches Item-Universe für Admin + Marktplatz-Handelbarkeit.
 * Quellen: Shop, Erfolg, Code, Starter, Ehe — Flags überschreibbar.
 */

const ach = require("./achievements");
const { ACHIEVEMENT_STICKERS, STICKER_SHOP_PRICES } = require("./sticker_catalog");
const { displayNameForEmoji } = require("./emoji_display_names");
const itemLabels = require("./item_labels");
const marriage = require("./marriage");

function isWeakLabel(label, itemId) {
  const l = String(label || "").trim();
  const id = String(itemId || "").trim();
  if (!l) return true;
  if (l === id) return true;
  return false;
}

const KINDS = ["emojis", "stickers", "themes", "pets"];

function itemKey(kind, itemId) {
  return `${String(kind || "").trim()}:${String(itemId || "").trim()}`;
}

function ensureTradeFlags(db) {
  if (!db.itemTradeFlags || typeof db.itemTradeFlags !== "object") {
    db.itemTradeFlags = {};
  }
  return db.itemTradeFlags;
}

/** Starter — nie handelbar, nicht umschaltbar. */
function isLockedStarter(kind, itemId, ctx) {
  const id = String(itemId || "").trim();
  if (kind === "pets" && id === (ctx.defaultPet || "🐣")) return true;
  if (kind === "themes" && id === "meadow") return true;
  if (kind === "emojis" && Array.isArray(ctx.starterEmojis) && ctx.starterEmojis.includes(id)) {
    return true;
  }
  // Ehe-Pet bleibt systemgebunden (fällt bei Scheidung weg)
  if (kind === "pets" && id === marriage.MARRIAGE_PET) return true;
  return false;
}

function defaultMarketSellable(kind, itemId, sources) {
  const src = sources || [];
  // Starter & Ehe-Pet: nie. Alles andere (Shop, Erfolg, Code) standardmäßig handelbar.
  if (src.includes("starter") || src.includes("marriage")) return false;
  return true;
}

function getMarketSellable(db, kind, itemId, sources, ctx) {
  if (isLockedStarter(kind, itemId, ctx || {})) return false;
  const flags = ensureTradeFlags(db);
  const key = itemKey(kind, itemId);
  const entry = flags[key];
  if (entry && typeof entry.marketSellable === "boolean") {
    return entry.marketSellable;
  }
  return defaultMarketSellable(kind, itemId, sources);
}

function setMarketSellable(db, kind, itemId, sellable, ctx) {
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim();
  if (!KINDS.includes(k) || !id) {
    return { ok: false, error: "bad_item", message: "Item ungültig." };
  }
  if (isLockedStarter(k, id, ctx || {})) {
    return {
      ok: false,
      error: "locked",
      message: "Starter-/Ehe-Items sind dauerhaft nicht handelbar.",
    };
  }
  const flags = ensureTradeFlags(db);
  const key = itemKey(k, id);
  flags[key] = {
    marketSellable: Boolean(sellable),
    updatedAt: Date.now(),
  };
  return { ok: true, kind: k, itemId: id, marketSellable: Boolean(sellable) };
}

function addSource(map, kind, itemId, source, extra = {}) {
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim();
  if (!KINDS.includes(k) || !id) return;
  const key = itemKey(k, id);
  if (!map.has(key)) {
    map.set(key, {
      kind: k,
      itemId: id,
      sources: new Set(),
      label: extra.label || "",
      emoji: extra.emoji || (k === "themes" ? "🖼️" : id),
      shopEnabled: null,
      priceCoins: null,
      inShopCatalog: false,
      achievementIds: [],
    });
  }
  const row = map.get(key);
  row.sources.add(source);
  if (extra.label && (!row.label || isWeakLabel(row.label, id))) {
    row.label = extra.label;
  }
  if (extra.emoji) row.emoji = extra.emoji;
  if (extra.shopEnabled != null) row.shopEnabled = extra.shopEnabled;
  if (extra.priceCoins != null) row.priceCoins = extra.priceCoins;
  if (extra.inShopCatalog) row.inShopCatalog = true;
  if (extra.achievementId) {
    if (!row.achievementIds.includes(extra.achievementId)) {
      row.achievementIds.push(extra.achievementId);
    }
  }
}

/**
 * @param {object} db
 * @param {object} ctx
 * @param {object} ctx.shopCatalog - shop_catalog module
 * @param {object} ctx.prices - { emojiPrices, themePrices, petPrices, stickerPrices }
 * @param {string} ctx.defaultPet
 * @param {string[]} ctx.starterEmojis
 * @param {string} [ctx.q]
 * @param {string} [ctx.kind]
 * @param {string} [ctx.source] shop|achievement|code|starter|marriage|tradeable|locked
 */
function listItemUniverse(db, ctx) {
  const shopCatalog = ctx.shopCatalog;
  const prices = ctx.prices || {};
  const map = new Map();

  // Shop-Katalog (Admin-Sicht inkl. disabled)
  if (shopCatalog) {
    shopCatalog.ensureShopCatalog(db);
    const items = shopCatalog.listPublicCatalog(db, {
      admin: true,
      kind: null,
      q: "",
    });
    for (const it of items) {
      addSource(map, it.kind, it.itemId, "shop", {
        label: it.label,
        emoji: it.emoji,
        shopEnabled: it.enabled !== false,
        priceCoins: it.priceCoins,
        inShopCatalog: true,
      });
    }
  }

  // Static-Preislisten (falls noch nicht im Katalog)
  for (const [id, price] of Object.entries(prices.emojiPrices || {})) {
    addSource(map, "emojis", id, "shop", {
      label: displayNameForEmoji(id),
      emoji: id,
      priceCoins: price,
      shopEnabled: true,
    });
  }
  for (const [id, price] of Object.entries(prices.stickerPrices || STICKER_SHOP_PRICES)) {
    addSource(map, "stickers", id, "shop", {
      label: displayNameForEmoji(id),
      emoji: id,
      priceCoins: price,
      shopEnabled: true,
    });
  }
  for (const [id, price] of Object.entries(prices.petPrices || {})) {
    addSource(map, "pets", id, "shop", {
      label: displayNameForEmoji(id),
      emoji: id,
      priceCoins: price,
      shopEnabled: true,
    });
  }
  for (const [id, price] of Object.entries(prices.themePrices || {})) {
    addSource(map, "themes", id, "shop", {
      label: id,
      emoji: "🖼️",
      priceCoins: price,
      shopEnabled: true,
    });
  }

  // Erfolgs-Belohnungen
  for (const def of ach.listAchievements({ includeDisabled: true })) {
    const reward = ach.publicRewardItem(def);
    if (!reward) continue;
    addSource(map, reward.kind, reward.itemId, "achievement", {
      label: reward.label,
      emoji: reward.emoji,
      achievementId: def.id,
    });
  }
  for (const id of ACHIEVEMENT_STICKERS) {
    addSource(map, "stickers", id, "achievement", {
      label: displayNameForEmoji(id),
      emoji: id,
    });
  }

  // Codes / Voucher
  const vouchers = db.vouchers && typeof db.vouchers === "object" ? db.vouchers : {};
  for (const v of Object.values(vouchers)) {
    if (!v || !Array.isArray(v.items)) continue;
    for (const it of v.items) {
      const kind = String(it?.kind || "").trim();
      const itemId = String(it?.itemId || "").trim();
      if (!kind || !itemId) continue;
      addSource(map, kind, itemId, "code", {
        label: kind === "themes" ? itemId : displayNameForEmoji(itemId),
        emoji: kind === "themes" ? "🖼️" : itemId,
      });
    }
  }

  // Starter
  for (const id of ctx.starterEmojis || []) {
    addSource(map, "emojis", id, "starter", {
      label: displayNameForEmoji(id),
      emoji: id,
    });
  }
  addSource(map, "pets", ctx.defaultPet || "🐣", "starter", {
    label: "Küken",
    emoji: ctx.defaultPet || "🐣",
  });
  addSource(map, "themes", "meadow", "starter", {
    label: "Wiese",
    emoji: "🌿",
  });

  // Ehe-Pet (dauerhaft gesperrt). Kapelle nur als Erfolg (handelbar freischaltbar).
  addSource(map, "pets", marriage.MARRIAGE_PET, "marriage", {
    label: marriage.MARRIAGE_PET_LABEL,
    emoji: marriage.MARRIAGE_PET,
  });
  addSource(map, "stickers", marriage.MARRIAGE_CHAPEL_STICKER, "achievement", {
    label: "Kapelle",
    emoji: marriage.MARRIAGE_CHAPEL_STICKER,
  });

  const qNorm = String(ctx.q || "")
    .toLowerCase()
    .replace(/ä/g, "ae")
    .replace(/ö/g, "oe")
    .replace(/ü/g, "ue")
    .replace(/ß/g, "ss")
    .trim();
  const kindFilter = String(ctx.kind || "").trim();
  const sourceFilter = String(ctx.source || "").trim();

  const lockCtx = {
    defaultPet: ctx.defaultPet || "🐣",
    starterEmojis: ctx.starterEmojis || [],
  };

  const achMeta = ach.achievementMetaMap();
  let rows = [...map.values()].map((row) => {
    const sources = [...row.sources].sort();
    const locked = isLockedStarter(row.kind, row.itemId, lockCtx);
    const marketSellable = getMarketSellable(db, row.kind, row.itemId, sources, lockCtx);
    const label = itemLabels.resolveDisplayLabel(
      db,
      row.kind,
      row.itemId,
      (id) =>
        row.kind === "themes" ? id : displayNameForEmoji(id),
      row.label
    );
    const achievements = (row.achievementIds || [])
      .map((aid) => achMeta[aid])
      .filter(Boolean);
    return {
      kind: row.kind,
      itemId: row.itemId,
      label,
      displayLabelOverride: itemLabels.getDisplayLabel(db, row.kind, row.itemId),
      emoji: row.emoji || (row.kind === "themes" ? "🖼️" : row.itemId),
      sources,
      shopEnabled: row.shopEnabled,
      inShopCatalog: Boolean(row.inShopCatalog),
      priceCoins: row.priceCoins,
      achievementIds: row.achievementIds,
      achievements,
      marketSellable,
      marketLocked: locked,
    };
  });

  if (kindFilter && KINDS.includes(kindFilter)) {
    rows = rows.filter((r) => r.kind === kindFilter);
  }
  if (sourceFilter === "tradeable") {
    rows = rows.filter((r) => r.marketSellable);
  } else if (sourceFilter === "locked") {
    rows = rows.filter((r) => !r.marketSellable);
  } else if (sourceFilter && sourceFilter !== "all") {
    rows = rows.filter((r) => r.sources.includes(sourceFilter));
  }
  if (qNorm) {
    rows = rows.filter((r) => {
      const hay = `${r.label} ${r.itemId} ${r.emoji} ${r.sources.join(" ")} ${r.achievementIds.join(" ")}`
        .toLowerCase()
        .replace(/ä/g, "ae")
        .replace(/ö/g, "oe")
        .replace(/ü/g, "ue")
        .replace(/ß/g, "ss");
      return hay.includes(qNorm);
    });
  }

  rows.sort(
    (a, b) =>
      a.kind.localeCompare(b.kind) ||
      String(a.label).localeCompare(String(b.label), "de") ||
      a.itemId.localeCompare(b.itemId)
  );

  return {
    items: rows,
    total: rows.length,
    sources: [
      { id: "shop", label: "Itemshop", color: "shop" },
      { id: "achievement", label: "Erfolg", color: "achievement" },
      { id: "code", label: "Code", color: "code" },
      { id: "starter", label: "Starter", color: "starter" },
      { id: "marriage", label: "Ehe", color: "marriage" },
    ],
  };
}

module.exports = {
  KINDS,
  itemKey,
  ensureTradeFlags,
  isLockedStarter,
  defaultMarketSellable,
  getMarketSellable,
  setMarketSellable,
  listItemUniverse,
};

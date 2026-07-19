/**
 * Dynamischer Itemshop-Katalog mit Overrides, Angeboten, Timern und Kauf-Limits.
 * Seed = statische Maps; Admin speichert Overrides in db.shopCatalog.items.
 */

const EXTRA_EMOJI_PRICES = {
  // Premium / teuer
  "💋": 22,
  "🫦": 28,
  "😻": 24,
  "💐": 30,
  "🥂": 32,
  "🍾": 36,
  "💍": 80,
  "🏰": 120,
  "🪽": 90,
  "🕊️": 100,
  "🦢": 70,
  "🦚": 85,
  "🪷": 55,
  "🪻": 48,
  "🫧": 40,
  "🪄": 95,
  "🧿": 110,
  "🪬": 105,
  "🪞": 60,
  "🕯️": 45,
  "📿": 75,
  "⚔️": 130,
  "🛡️": 125,
  "🗡️": 115,
  "🏹": 90,
  "🪄": 95,
  // Extrem teuer
  "💠": 220,
  "🔮": 280,
  "🧿": 300,
  "💫": 180,
  "🌌": 450,
  "🪐": 520,
  "☄️": 380,
  "🌠": 400,
  "💎": 650,
  "👑": 800,
  "🏰": 900,
  "🦄": 1200,
  "🐉": 1500,
  "🐲": 1400,
  "🦋": 200,
  "🦚": 1100,
  "🦩": 350,
  "🐆": 600,
  "🐅": 700,
  "🦁": 750,
  "🦅": 550,
  "🦉": 420,
  "🐺": 480,
  "🦊": 500,
  "🐯": 850,
  "🦓": 400,
  "🦒": 450,
  "🐘": 900,
  "🐋": 1000,
  "🦈": 950,
  "🦑": 700,
  "🧬": 1600,
  "🛸": 2000,
  "🚀": 1800,
  "🛰️": 1700,
  "🌙": 300,
  "☀️": 320,
  "⭐": 250,
  "🌟": 350,
  "✨": 280,
  "💖": 400,
  "💕": 380,
  "💗": 420,
  "💘": 500,
  "💝": 550,
  "💞": 480,
  "💟": 300,
  "❤️‍🔥": 900,
  "🖤": 200,
  "🤍": 220,
  "💛": 200,
  "💚": 200,
  "💙": 200,
  "💜": 240,
  "🧡": 200,
  "🤎": 180,
  "💯": 600,
  "🏆": 1100,
  "🥇": 1300,
  "🎖️": 800,
  "🏅": 700,
  "🎗️": 650,
  "🎟️": 400,
  "🃏": 1500,
  "♠️": 900,
  "♥️": 900,
  "♦️": 900,
  "♣️": 900,
  "🎭": 1200,
  "🖼️": 1000,
  "🎨": 800,
  "🎹": 700,
  "🎻": 750,
  "🎷": 720,
  "🎸": 680,
  "🥁": 500,
  "🎤": 450,
  "🎧": 400,
  "🎬": 900,
  "📷": 600,
  "📸": 650,
  "🎥": 800,
  "📹": 550,
  "📱": 300,
  "💻": 700,
  "🖥️": 900,
  "⌨️": 400,
  "🖱️": 350,
  "🖨️": 500,
  "📡": 1100,
  "🔭": 1400,
  "🔬": 1000,
  "⚗️": 1200,
  "🧪": 800,
  "💊": 600,
  "💉": 700,
  "🩸": 500,
  "🧠": 1600,
  "🫀": 1500,
  "🫁": 1300,
  "👁️": 900,
  "👀": 400,
  "👅": 350,
  "👄": 400,
  "🦷": 300,
  "🦴": 280,
  "🦵": 250,
  "🦶": 220,
  "🦾": 1800,
  "🦿": 1700,
  "🤖": 2000,
  "👾": 1500,
  "👽": 1600,
  "👻": 900,
  "💀": 800,
  "☠️": 1000,
  "🤡": 700,
  "👹": 1100,
  "👺": 1050,
  "👿": 900,
  "😈": 950,
  "😇": 800,
  "🫠": 600,
  "😶‍🌫️": 2000,
  "🗯️": 1800,
  "💭": 500,
  "💬": 400,
  "🗨️": 450,
  "💤": 200,
  "💢": 250,
  "♨️": 700,
  "🌀": 800,
  "🌈": 900,
  "🔥": 750,
  "💧": 300,
  "🌊": 850,
  "❄️": 700,
  "⛄": 600,
  "☃️": 650,
  "🌪️": 1000,
  "🌫️": 500,
  "🌩️": 900,
  "⛈️": 950,
  "🌧️": 600,
  "🌦️": 550,
  "🌤️": 400,
  "⛅": 350,
  "🌥️": 380,
  "☁️": 300,
  "🌝": 700,
  "🌞": 750,
  "🌛": 650,
  "🌜": 650,
  "🌚": 800,
  "🌕": 900,
  "🌖": 700,
  "🌗": 700,
  "🌘": 700,
  "🌑": 850,
  "🌒": 700,
  "🌓": 700,
  "🌔": 700,
  "🌏": 1200,
  "🌍": 1200,
  "🌎": 1200,
  "🗺️": 1000,
  "🧭": 800,
  "🏔️": 900,
  "⛰️": 700,
  "🌋": 1400,
  "🗻": 1000,
  "🏕️": 600,
  "🏖️": 700,
  "🏝️": 1100,
  "🏜️": 800,
  "🏟️": 1500,
  "🏛️": 1600,
  "🏗️": 900,
  "🧱": 400,
  "🏘️": 800,
  "🏚️": 500,
  "🏠": 600,
  "🏡": 650,
  "🏢": 700,
  "🏣": 500,
  "🏤": 500,
  "🏥": 900,
  "🏦": 1100,
  "🏨": 800,
  "🏩": 1000,
  "🏪": 550,
  "🏫": 700,
  "🏬": 750,
  "🏭": 650,
  "🏯": 2000,
  "🗼": 1800,
  "🗽": 2200,
  "⛪": 1500,
  "🕌": 1600,
  "🛕": 1500,
  "🕍": 1500,
  "⛩️": 1400,
  "🕋": 1700,
  "⛲": 900,
  "⛺": 500,
  "🌁": 800,
  "🌃": 1000,
  "🏙️": 1200,
  "🌄": 900,
  "🌅": 950,
  "🌆": 1000,
  "🌇": 1000,
  "🌉": 1100,
  "♨️": 700,
  "🎠": 800,
  "🎡": 900,
  "🎢": 950,
  "💈": 400,
  "🎪": 850,
  // Ultra-legendär
  "⚜️": 5000,
  "🔱": 4500,
  "⚛️": 4000,
  "♾️": 6000,
  "🃏": 3500,
  "🀄️": 3000,
  "🎴": 2800,
  "🧿": 3200,
  "🪬": 3000,
  "💠": 2500,
  "🔮": 4000,
  "💎": 5500,
  "👑": 8000,
  "🦄": 9000,
  "🐉": 10000,
};

function itemKey(kind, itemId) {
  return `${kind}:${itemId}`;
}

function ensureShopCatalog(db) {
  if (!db.shopCatalog || typeof db.shopCatalog !== "object") {
    db.shopCatalog = { items: {}, version: 1 };
  }
  if (!db.shopCatalog.items || typeof db.shopCatalog.items !== "object") {
    db.shopCatalog.items = {};
  }
  return db.shopCatalog;
}

function seedFromStatic(db, staticMaps) {
  const cat = ensureShopCatalog(db);
  const { emojiPrices = {}, themePrices = {}, petPrices = {}, stickerPrices = {} } = staticMaps;
  const all = [
    ...Object.entries(emojiPrices).map(([id, price]) => ["emojis", id, price]),
    ...Object.entries(EXTRA_EMOJI_PRICES).map(([id, price]) => ["emojis", id, price]),
    ...Object.entries(themePrices).map(([id, price]) => ["themes", id, price]),
    ...Object.entries(petPrices).map(([id, price]) => ["pets", id, price]),
    ...Object.entries(stickerPrices).map(([id, price]) => ["stickers", id, price]),
  ];
  for (const [kind, itemId, price] of all) {
    const key = itemKey(kind, itemId);
    if (cat.items[key]) continue;
    const p = Math.max(0, Math.floor(Number(price) || 0));
    if (kind === "themes" && itemId === "meadow") continue;
    if (kind === "pets" && p <= 0 && itemId === "🐣") continue;
    if (p <= 0 && kind !== "themes") continue;
    cat.items[key] = normalizeItem({
      kind,
      itemId,
      label: itemId,
      priceCoins: p,
      enabled: true,
      searchText: defaultSearchText(kind, itemId),
      soldTotal: 0,
      seeded: true,
    });
  }
  return cat;
}

function defaultSearchText(kind, itemId) {
  const id = String(itemId || "");
  const map = {
    "❤️": "herz heart liebe",
    "🧡": "herz orange",
    "💛": "herz gelb",
    "💚": "herz gruen",
    "💙": "herz blau",
    "💜": "herz lila",
    "🖤": "herz schwarz",
    "🤍": "herz weiss",
    "🤎": "herz braun",
    "💕": "herz herzen liebe",
    "💖": "herz sparkle",
    "💗": "herz wachsend",
    "💘": "herz pfeil",
    "💝": "herz geschenk",
    "💞": "herz kreisend",
    "💟": "herz dekoration",
    "❣️": "herz ausrufezeichen",
    "💔": "herz gebrochen",
    "❤️‍🔥": "herz feuer",
    "❤️‍🩹": "herz heilen",
    "💋": "kuss lippen",
    "👑": "krone king queen",
    "💎": "diamant juwel",
    "🦄": "einhorn unicorn",
    "🐉": "drache dragon",
    "⭐": "stern star",
    "🌟": "stern glanz",
    "✨": "funken sparkle",
    "🔥": "feuer fire",
    "🌙": "mond moon",
    "☀️": "sonne sun",
    "🌹": "rose blume",
    "🌸": "blume sakura",
    "🍀": "klee glueck",
    "🌈": "regenbogen rainbow",
    "🐱": "katze cat",
    "🐶": "hund dog",
    "🦊": "fuchs fox",
    "🐻": "baer bear",
    "🐼": "panda",
    "🐸": "frosch frog",
    "🐧": "pinguin",
    "🦋": "schmetterling butterfly",
    "😂": "lachen lachend",
    "😍": "verliebt eyes",
    "🥰": "liebe herzen",
    "😊": "laecheln smile",
    "👍": "daumen gut ok",
    "👎": "daumen schlecht",
    "🎉": "party feier",
    "🎁": "geschenk gift",
    "🏆": "pokal trophy",
    "🥇": "medaille gold",
  };
  return map[id] || `${kind} ${id}`;
}

function normalizeItem(raw) {
  const kind = String(raw.kind || "").trim();
  const itemId = String(raw.itemId || "").trim().slice(0, 32);
  const priceCoins = Math.max(0, Math.min(100_000, Math.floor(Number(raw.priceCoins) || 0)));
  const compareAt = raw.compareAtPrice != null ? Math.max(0, Math.floor(Number(raw.compareAtPrice) || 0)) : null;
  const salePrice = raw.salePrice != null ? Math.max(0, Math.floor(Number(raw.salePrice) || 0)) : null;
  let availableFrom = raw.availableFrom != null ? Number(raw.availableFrom) || null : null;
  let availableUntil = raw.availableUntil != null ? Number(raw.availableUntil) || null : null;
  const maxTotalSales =
    raw.maxTotalSales === null || raw.maxTotalSales === undefined || raw.maxTotalSales === ""
      ? null
      : Math.max(0, Math.floor(Number(raw.maxTotalSales) || 0));
  const maxPerUser =
    raw.maxPerUser === null || raw.maxPerUser === undefined || raw.maxPerUser === ""
      ? null
      : Math.max(0, Math.floor(Number(raw.maxPerUser) || 0));
  return {
    kind,
    itemId,
    label: String(raw.label || itemId).trim().slice(0, 40) || itemId,
    priceCoins,
    compareAtPrice: compareAt && compareAt > 0 ? compareAt : null,
    salePrice: salePrice && salePrice > 0 ? salePrice : null,
    enabled: raw.enabled !== false,
    availableFrom,
    availableUntil,
    maxTotalSales,
    maxPerUser,
    soldTotal: Math.max(0, Math.floor(Number(raw.soldTotal) || 0)),
    searchText: String(raw.searchText || defaultSearchText(kind, itemId)).trim().slice(0, 120),
    seeded: Boolean(raw.seeded),
    createdAt: Number(raw.createdAt) || Date.now(),
    updatedAt: Date.now(),
  };
}

function effectivePrice(item) {
  if (!item) return 0;
  if (item.salePrice != null && item.salePrice > 0) return item.salePrice;
  return Math.max(0, Number(item.priceCoins) || 0);
}

function isWithinWindow(item, now = Date.now()) {
  if (!item) return false;
  if (item.availableFrom && now < item.availableFrom) return false;
  if (item.availableUntil && now > item.availableUntil) return false;
  return true;
}

/** Abgelaufene Items deaktivieren (nicht löschen). */
function deactivateExpired(db, now = Date.now()) {
  const cat = ensureShopCatalog(db);
  let n = 0;
  for (const item of Object.values(cat.items)) {
    if (!item || !item.enabled) continue;
    if (item.availableUntil && now > item.availableUntil) {
      item.enabled = false;
      item.updatedAt = now;
      n++;
    }
  }
  return n;
}

function getItem(db, kind, itemId) {
  const cat = ensureShopCatalog(db);
  return cat.items[itemKey(kind, itemId)] || null;
}

function isShopKnown(db, kind, itemId) {
  const item = getItem(db, kind, itemId);
  return Boolean(item);
}

function userPurchaseCount(user, kind, itemId) {
  if (!user.shopPurchases || typeof user.shopPurchases !== "object") return 0;
  return Math.max(0, Math.floor(Number(user.shopPurchases[itemKey(kind, itemId)]) || 0));
}

function bumpUserPurchase(user, kind, itemId) {
  if (!user.shopPurchases || typeof user.shopPurchases !== "object") user.shopPurchases = {};
  const k = itemKey(kind, itemId);
  user.shopPurchases[k] = userPurchaseCount(user, kind, itemId) + 1;
}

function canBuy(db, user, kind, itemId, now = Date.now()) {
  const item = getItem(db, kind, itemId);
  if (!item) return { ok: false, error: "unknown_item", message: "Artikel unbekannt." };
  if (!item.enabled) {
    return { ok: false, error: "disabled", message: "Dieser Artikel ist gerade nicht im Shop." };
  }
  if (!isWithinWindow(item, now)) {
    return { ok: false, error: "not_available", message: "Artikel derzeit nicht verfügbar." };
  }
  if (item.maxTotalSales != null && item.soldTotal >= item.maxTotalSales) {
    return { ok: false, error: "sold_out", message: "Ausverkauft." };
  }
  if (item.maxPerUser != null) {
    const ownedBuys = userPurchaseCount(user, kind, itemId);
    if (ownedBuys >= item.maxPerUser) {
      return {
        ok: false,
        error: "per_user_limit",
        message: `Maximal ${item.maxPerUser}× pro Person.`,
      };
    }
  }
  const price = effectivePrice(item);
  return { ok: true, item, price };
}

function recordSale(db, user, kind, itemId) {
  const item = getItem(db, kind, itemId);
  if (item) {
    item.soldTotal = Math.max(0, Math.floor(Number(item.soldTotal) || 0)) + 1;
    if (item.maxTotalSales != null && item.soldTotal >= item.maxTotalSales) {
      item.enabled = false;
    }
    item.updatedAt = Date.now();
  }
  bumpUserPurchase(user, kind, itemId);
}

function remainingMs(item, now = Date.now()) {
  if (!item?.availableUntil) return null;
  return Math.max(0, item.availableUntil - now);
}

function publicItem(item, now = Date.now(), { admin = false } = {}) {
  if (!item) return null;
  const price = effectivePrice(item);
  const compareAt =
    item.compareAtPrice && item.compareAtPrice > price
      ? item.compareAtPrice
      : item.salePrice && item.priceCoins > price
        ? item.priceCoins
        : null;
  const rem = remainingMs(item, now);
  const base = {
    kind: item.kind,
    itemId: item.itemId,
    label: item.label,
    emoji: item.kind === "themes" ? "🖼️" : item.itemId,
    priceCoins: price,
    compareAtPrice: compareAt,
    onSale: Boolean(compareAt && compareAt > price),
    searchText: item.searchText || "",
    availableUntil: item.availableUntil || null,
    remainingMs: rem,
    maxPerUser: item.maxPerUser,
    maxTotalSales: item.maxTotalSales,
    soldTotal: item.soldTotal || 0,
  };
  if (admin) {
    return {
      ...base,
      enabled: item.enabled !== false,
      availableFrom: item.availableFrom || null,
      listPrice: item.priceCoins,
      salePrice: item.salePrice,
      seeded: Boolean(item.seeded),
      createdAt: item.createdAt,
      updatedAt: item.updatedAt,
    };
  }
  return base;
}

function listPublicCatalog(db, { admin = false, kind = null, q = "" } = {}) {
  deactivateExpired(db);
  const cat = ensureShopCatalog(db);
  const now = Date.now();
  const query = String(q || "")
    .trim()
    .toLowerCase();
  let items = Object.values(cat.items).filter(Boolean);
  if (kind) items = items.filter((i) => i.kind === kind);
  if (!admin) {
    items = items.filter((i) => i.enabled !== false && isWithinWindow(i, now));
    items = items.filter((i) => {
      if (i.maxTotalSales != null && i.soldTotal >= i.maxTotalSales) return false;
      return effectivePrice(i) > 0 || i.kind === "themes";
    });
  }
  if (query) {
    items = items.filter((i) => {
      const hay = `${i.itemId} ${i.label} ${i.searchText || ""} ${i.kind}`.toLowerCase();
      return hay.includes(query);
    });
  }
  items.sort((a, b) => effectivePrice(a) - effectivePrice(b) || a.itemId.localeCompare(b.itemId));
  return items.map((i) => publicItem(i, now, { admin }));
}

function upsertItem(db, patch) {
  const cat = ensureShopCatalog(db);
  const kind = String(patch.kind || "").trim();
  const itemId = String(patch.itemId || "").trim().slice(0, 32);
  if (!["emojis", "themes", "pets", "stickers"].includes(kind) || !itemId) {
    return { ok: false, error: "bad_item", message: "Kategorie oder ID ungültig." };
  }
  const key = itemKey(kind, itemId);
  const prev = cat.items[key] || {};
  const next = normalizeItem({
    ...prev,
    ...patch,
    kind,
    itemId,
    soldTotal: prev.soldTotal || 0,
    seeded: prev.seeded || false,
    createdAt: prev.createdAt || Date.now(),
  });
  cat.items[key] = next;
  return { ok: true, item: publicItem(next, Date.now(), { admin: true }) };
}

function setEnabled(db, kind, itemId, enabled) {
  const item = getItem(db, kind, itemId);
  if (!item) return { ok: false, error: "not_found" };
  item.enabled = Boolean(enabled);
  item.updatedAt = Date.now();
  return { ok: true, item: publicItem(item, Date.now(), { admin: true }) };
}

function priceOf(db, kind, itemId) {
  const item = getItem(db, kind, itemId);
  if (!item || !item.enabled) return null;
  if (!isWithinWindow(item)) return null;
  return effectivePrice(item);
}

module.exports = {
  EXTRA_EMOJI_PRICES,
  ensureShopCatalog,
  seedFromStatic,
  getItem,
  isShopKnown,
  canBuy,
  recordSale,
  listPublicCatalog,
  upsertItem,
  setEnabled,
  priceOf,
  effectivePrice,
  deactivateExpired,
  itemKey,
  publicItem,
};

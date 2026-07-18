const http = require("http");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const express = require("express");
const { WebSocketServer } = require("ws");
const {
  getDb,
  scheduleSave,
  flushSave,
  todayKey,
  hashSecret,
  newId,
  DATA_DIR,
} = require("./store");
const { pickShareLine } = require("./share_lines");
const ach = require("./achievements");
const market = require("./market");
const lootbox = require("./lootbox");
const {
  STICKER_SHOP_PRICES,
  isKnownSticker,
  isAchievementSticker,
} = require("./sticker_catalog");

const PORT = Number(process.env.PORT || 8080);
const ROOM_TTL_MS = Number(process.env.ROOM_TTL_MS || 24 * 60 * 60 * 1000);
const PUBLIC_JOIN_BASE =
  process.env.PUBLIC_JOIN_BASE || "https://reineke.pro/luv/j";
const SESSION_TTL_MS = Number(process.env.SESSION_TTL_MS || 30 * 24 * 60 * 60 * 1000);
const MOLLIE_API_KEY = process.env.MOLLIE_API_KEY || "";
/** Feste Super-Admins per Google-E-Mail (nicht Spitzname). Komma-getrennt via Env erweiterbar. */
const SUPER_ADMIN_EMAILS = new Set(
  String(process.env.SUPER_ADMIN_EMAILS || "xstruppelstrumpf@gmail.com")
    .split(",")
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean)
);

const ALL_MOD_PERMISSION_IDS = [
  "reports.view",
  "reports.act",
  "codes.view",
  "codes.edit",
  "codes.revoke",
  "gm.search",
  "gm.editCoins",
  "gm.editNick",
  "gm.block",
  "gm.delete",
  "live.notify",
];

const DEFAULT_MOD_PERMISSIONS = {
  "reports.view": true,
  "reports.act": true,
  "gm.search": true,
  "gm.editCoins": false,
  "gm.block": true,
  "live.notify": true,
};

const MOD_PERMISSION_GROUPS = [
  {
    id: "reports",
    icon: "🚩",
    label: "Meldungen",
    description: "Öffentliche Bilder & Lobby-Meldungen",
    permissions: [
      { id: "reports.view", label: "Meldungen sehen" },
      { id: "reports.act", label: "Behalten / Löschen" },
    ],
  },
  {
    id: "codes",
    icon: "🎟️",
    label: "Codes",
    description: "Gutscheincodes verwalten",
    permissions: [
      { id: "codes.view", label: "Codes sehen" },
      { id: "codes.edit", label: "Codes erstellen" },
      { id: "codes.revoke", label: "Codes widerrufen" },
    ],
  },
  {
    id: "gamemaster",
    icon: "🎮",
    label: "Nutzer",
    description: "Spieler suchen und Konten verwalten",
    permissions: [
      { id: "gm.search", label: "Nutzer suchen" },
      { id: "gm.editCoins", label: "Coins anpassen" },
      { id: "gm.editNick", label: "Spitzname ändern" },
      { id: "gm.block", label: "Sperren / Entsperren" },
      { id: "gm.delete", label: "Konto löschen" },
    ],
  },
  {
    id: "live",
    icon: "📣",
    label: "Live-Hinweis",
    description: "Kurze In-App-Nachricht an alle",
    permissions: [{ id: "live.notify", label: "Live-Hinweis senden" }],
  },
];
const MOLLIE_WEBHOOK_URL =
  process.env.MOLLIE_WEBHOOK_URL || "https://reineke.pro/luv/v1/webhooks/mollie";
const PUBLIC_SHOP_REDIRECT =
  process.env.PUBLIC_SHOP_REDIRECT || "https://reineke.pro/luv/shop/return";
/** OAuth 2.0 Web-Client-ID (Google Cloud) — für Sign-In + Token-Verify */
const GOOGLE_CLIENT_ID = String(process.env.GOOGLE_CLIENT_ID || "").trim();
const WEB_DIR = String(process.env.WEB_DIR || "/opt/luv-web").trim();

const DAILY_COINS = 12;
/** Max. Coins, die ein Profil pro Tag (0:00 Europe/Berlin) ins Münzglas bekommen kann (alle Spender zusammen). */
const GLASS_TIP_DAILY_MAX = 10;
const STARTING_COINS = 25;
const FREE_SESSIONS_PER_DAY = 5;
const SESSION_COST = 1;
const CLEAR_COST = 1;
const GAME_COST = 1;
const LOBBY_CREATE_COST = 4;
const SLOT_COST = 5;
const LOOTBOX_PRICE = 10;
const LOOTBOX_MAX_QTY = 50;
const WORDS_DE = require("./words_de");
const Games = require("./games");
const MAX_LOBBIES = 10;
const MAX_LOBBY_NAME_LENGTH = 16;
const MAX_PEERS = Number(process.env.MAX_PEERS || 10);
const FREE_LOBBY_START_CAPACITY = 2; // Host + 1 Einladung
const PAID_LOBBY_START_CAPACITY = 4; // Host + 3 Einladungen
/** Lange offen, damit Offline-Mitglieder noch zustimmen können */
const CLEAR_VOTE_MS = 24 * 60 * 60 * 1000;

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const INTRO_PACK_ID = "pack_intro_100";
/** Angebot endet automatisch am 01.01.2027 00:00 UTC (Ende 2026) — nicht in der UI erwähnen */
const INTRO_EXPIRES_AT = Date.parse("2027-01-01T00:00:00.000Z");

const PACKS = {
  [INTRO_PACK_ID]: {
    id: INTRO_PACK_ID,
    coins: 100,
    amountEur: "0.99",
    compareAtEur: "4.99",
    label: "Säckchen Glück",
    oncePerUserAndIp: true,
    expiresAt: INTRO_EXPIRES_AT,
  },
  pack_50: { id: "pack_50", coins: 60, amountEur: "2.99", label: "Handvoll Coins" },
  pack_150: { id: "pack_150", coins: 175, amountEur: "6.99", label: "Beutel voll Coins" },
  pack_400: { id: "pack_400", coins: 450, amountEur: "14.99", label: "Schatztruhe" },
  pack_900: { id: "pack_900", coins: 1000, amountEur: "29.99", label: "Münzhaufen" },
  pack_2000: { id: "pack_2000", coins: 2200, amountEur: "49.99", label: "Goldschatz" },
  pack_5000: { id: "pack_5000", coins: 5600, amountEur: "99.99", label: "Schatzkammer" },
};

/** Faire Coin-Preise für Itemshop-Emojis (Client-Katalog muss passen). */
const EMOJI_SHOP_PRICES = {
  "👍": 5,
  "👎": 5,
  "❌": 5,
  "❤️": 8,
  "🧡": 8,
  "💛": 8,
  "💚": 8,
  "💙": 8,
  "💜": 8,
  "🖤": 8,
  "🤍": 8,
  "🤎": 8,
  "💔": 10,
  "❣️": 10,
  "💕": 12,
  "💖": 12,
  "💗": 12,
  "💘": 14,
  "💝": 14,
  "💞": 12,
  "💟": 10,
  "❤️‍🔥": 16,
  "❤️‍🩹": 14,
  "😂": 8,
  "🤣": 10,
  "😊": 6,
  "🙂": 5,
  "😉": 6,
  "😍": 10,
  "🥰": 12,
  "😘": 10,
  "😗": 8,
  "😙": 8,
  "😚": 8,
  "☺️": 8,
  "😜": 8,
  "🤪": 10,
  "😝": 8,
  "😛": 6,
  "😎": 12,
  "🤩": 12,
  "🥳": 12,
  "😏": 8,
  "😱": 8,
  "😨": 8,
  "😰": 8,
  "😥": 8,
  "😢": 8,
  "😭": 10,
  "😡": 8,
  "🤬": 12,
  "😤": 8,
  "😠": 8,
  "😳": 8,
  "🥺": 12,
  "🥹": 12,
  "😴": 6,
  "🥱": 8,
  "🤤": 8,
  "🤔": 8,
  "🙄": 8,
  "😬": 8,
  "🤐": 8,
  "🤫": 8,
  "🤭": 8,
  "🫡": 10,
  "🤝": 10,
  "🙏": 8,
  "💪": 10,
  "👀": 8,
  "🙈": 10,
  "🙉": 10,
  "🙊": 10,
  "😺": 10,
  "😸": 10,
  "😹": 10,
  "😻": 12,
  "😼": 10,
  "😽": 10,
  "🔥": 10,
  "✨": 8,
  "⭐": 8,
  "🌟": 10,
  "💫": 10,
  "⚡": 8,
  "💥": 10,
  "💯": 12,
  "🎉": 10,
  "🎊": 10,
  "🎈": 8,
  "🎁": 12,
  "🌹": 12,
  "🌸": 8,
  "🍀": 8,
  "🌈": 10,
  "☀️": 6,
  "🌙": 6,
  "☄️": 12,
  "🎯": 10,
  "🏆": 14,
  "👑": 16,
  "💎": 18,
  "🥇": 16,
  "🐱": 12,
  "🐶": 12,
  "🐻": 12,
  "🐼": 14,
  "🦊": 14,
  "🐰": 12,
  "🦄": 18,
  "🐸": 10,
  "🐧": 12,
  "🦋": 10,
  "🐝": 8,
  "🐢": 10,
  "🍕": 8,
  "🍩": 8,
  "☕": 6,
  "🍷": 10,
  "🍺": 8,
  "🧁": 8,
  "🍓": 6,
  "🍑": 10,
  "🍪": 8,
  "🍫": 8,
  "🍿": 8,
  "🧋": 10,
  "🎵": 8,
  "🎶": 8,
  "📱": 6,
  "💡": 6,
  "📎": 5,
  "✏️": 5,
  "📌": 5,
  "🔔": 8,
  "💀": 12,
  "☠️": 14,
  "👻": 12,
  "🤖": 14,
  "👽": 14,
  "👾": 14,
  "💩": 10,
  "🤡": 12,
  "😈": 14,
  "👿": 12,
  "😇": 12,
  "🫠": 12,
  "🫢": 10,
  "🫣": 10,
  "🥶": 10,
  "🥵": 10,
  "🥴": 10,
  "😵": 10,
  "🤯": 12,
  "🫶": 14,
  "🫰": 12,
  "✌️": 8,
  "🤞": 8,
  "🤟": 8,
  "🤘": 8,
  "👏": 8,
  "🙌": 10,
  "👋": 6,
  "💅": 10,
  "🤳": 8,
  "🫂": 12,
  "💤": 6,
  "💢": 8,
  "💬": 6,
};

const DEFAULT_PET = "🐣";

const THEME_SHOP_PRICES = {
  meadow: 0,
  forest: 18,
  sunset: 20,
  night: 20,
  snow: 18,
  blossom: 22,
  ocean: 22,
  rain: 18,
  autumn: 20,
  stars: 24,
  cabin: 22,
  lake: 22,
  lavender: 24,
  hearth: 24,
  dawn: 22,
  desert: 22,
  bamboo: 24,
  mist: 24,
  golden: 26,
  iceberg: 26,
  sakura: 28,
  coral: 28,
  vineyard: 28,
  storm: 30,
  candy: 30,
  ember: 32,
  volcano: 34,
  aurora: 36,
  meteor: 38,
  galaxy: 40,
};

const PET_SHOP_PRICES = {
  "🐣": 0,
  "🐤": 12,
  "🐥": 12,
  "🐦": 14,
  "🐔": 16,
  "🐓": 16,
  "🦆": 16,
  "🐸": 16,
  "🐹": 18,
  "🐭": 18,
  "🦋": 18,
  "🐝": 18,
  "🐛": 16,
  "🐞": 16,
  "🐜": 14,
  "🐌": 16,
  "🐶": 20,
  "🐕": 20,
  "🐩": 22,
  "🦮": 24,
  "🐕‍🦺": 24,
  "🐱": 20,
  "🐈": 20,
  "🐈‍⬛": 26,
  "🐰": 22,
  "🐇": 22,
  "🐮": 20,
  "🐂": 22,
  "🐃": 22,
  "🐄": 20,
  "🐷": 20,
  "🐖": 20,
  "🐗": 24,
  "🐏": 20,
  "🐑": 20,
  "🐐": 20,
  "🦙": 24,
  "🦌": 24,
  "🐴": 22,
  "🐎": 24,
  "🐧": 22,
  "🐢": 22,
  "🦎": 20,
  "🐍": 24,
  "🐊": 28,
  "🦉": 24,
  "🦇": 22,
  "🐺": 28,
  "🐻": 24,
  "🐻‍❄️": 32,
  "🦊": 26,
  "🐼": 28,
  "🐨": 26,
  "🐯": 30,
  "🐅": 32,
  "🐆": 30,
  "🦁": 32,
  "🦓": 28,
  "🦒": 30,
  "🦘": 28,
  "🐘": 30,
  "🦣": 36,
  "🦏": 30,
  "🦛": 28,
  "🐪": 24,
  "🐫": 24,
  "🦬": 28,
  "🦍": 34,
  "🦧": 32,
  "🐵": 22,
  "🙈": 20,
  "🙉": 20,
  "🙊": 20,
  "🦝": 26,
  "🦨": 22,
  "🦡": 22,
  "🦫": 24,
  "🦦": 26,
  "🦥": 28,
  "🐁": 16,
  "🐀": 16,
  "🐿️": 20,
  "🦔": 24,
  "🦅": 28,
  "🕊️": 20,
  "🦢": 26,
  "🦩": 28,
  "🦚": 32,
  "🦜": 28,
  "🦤": 34,
  "🦃": 20,
  "🐟": 16,
  "🐠": 18,
  "🐡": 20,
  "🐬": 28,
  "🐳": 30,
  "🐋": 30,
  "🦈": 32,
  "🦭": 26,
  "🐙": 28,
  "🦑": 24,
  "🦐": 16,
  "🦞": 22,
  "🦀": 18,
  "🦟": 12,
  "🦗": 14,
  "🕷️": 18,
  "🦂": 22,
  "🦖": 38,
  "🦕": 36,
  "🐉": 42,
  "🐲": 40,
  "🦄": 44,
  "🐾": 14,
};

const STARTER_EMOJIS = ["👍", "❌", "❤️", "😂", "😱", "😡", "😭"];

/** Nur Katalog-/Starter-Items — verhindert eingeschmuggelte Fake-Items. */
function isKnownInventoryItem(kind, itemId) {
  const id = String(itemId || "").trim();
  if (!id) return false;
  if (kind === "pets") return id === DEFAULT_PET || PET_SHOP_PRICES[id] != null;
  if (kind === "themes") return id === "meadow" || THEME_SHOP_PRICES[id] != null;
  if (kind === "stickers") return isKnownSticker(id);
  if (kind === "emojis") {
    return STARTER_EMOJIS.includes(id) || EMOJI_SHOP_PRICES[id] != null;
  }
  return false;
}

function scrubInventoryCatalog(inv) {
  inv.pets = (inv.pets || []).filter((p) => isKnownInventoryItem("pets", p));
  inv.themes = (inv.themes || []).filter((t) => isKnownInventoryItem("themes", t));
  for (const e of Object.keys(inv.stickers || {})) {
    if (!isKnownInventoryItem("stickers", e)) delete inv.stickers[e];
    else inv.stickers[e] = Math.min(999, Math.max(0, Math.floor(Number(inv.stickers[e]) || 0)));
    if (inv.stickers[e] <= 0) delete inv.stickers[e];
  }
  for (const e of Object.keys(inv.emojis || {})) {
    if (!isKnownInventoryItem("emojis", e)) delete inv.emojis[e];
    else inv.emojis[e] = Math.min(999, Math.max(0, Math.floor(Number(inv.emojis[e]) || 0)));
    if (inv.emojis[e] <= 0) delete inv.emojis[e];
  }
}

/** Item nur gutschreiben, wenn es im Shop-Katalog existiert. */
function safeGiveItem(user, kind, itemId) {
  if (!isKnownInventoryItem(kind, itemId)) return false;
  market.giveItemToUser(user, ensureInventory, kind, itemId);
  return true;
}

function ensureInventory(user) {
  if (!user.inventory || typeof user.inventory !== "object") {
    user.inventory = {};
  }
  const inv = user.inventory;
  if (!inv.emojis || typeof inv.emojis !== "object") inv.emojis = {};
  if (!Array.isArray(inv.themes)) inv.themes = [];
  if (!inv.stickers || typeof inv.stickers !== "object") inv.stickers = {};
  if (!Array.isArray(inv.pets)) inv.pets = [];
  // Fremde/Fake-Items aus Mods oder alten Bugs entfernen
  scrubInventoryCatalog(inv);
  // Starter
  for (const e of STARTER_EMOJIS) {
    if (!inv.emojis[e] || inv.emojis[e] < 1) inv.emojis[e] = 1;
  }
  if (!inv.themes.includes("meadow")) inv.themes.push("meadow");
  if (!inv.pets.includes(DEFAULT_PET)) inv.pets.push(DEFAULT_PET);
  // WICHTIG: Keine Shop-Items aus profileCanvas „schenken“ — sonst Inventar-Bypass per PUT /profile
  if (!inv.equippedPet || !inv.pets.includes(inv.equippedPet)) {
    inv.equippedPet = DEFAULT_PET;
  }
  user.inventory = inv;
  return inv;
}

/** Theme/Begleiter nur wenn im Inventar (oder kostenlos). */
function clampProfileToInventory(user, profile) {
  if (!profile || typeof profile !== "object") return profile;
  const inv = ensureInventory(user);
  let themeId = String(profile.themeId || "meadow").trim().slice(0, 32) || "meadow";
  if (!inv.themes.includes(themeId)) themeId = "meadow";
  let companionEmoji =
    String(profile.companionEmoji || DEFAULT_PET).trim().slice(0, 8) || DEFAULT_PET;
  if (!inv.pets.includes(companionEmoji)) companionEmoji = DEFAULT_PET;
  // Sticker-Bestand: syncStickersOnProfileSave (Inventar = freier Stock, nicht auf der Leinwand)
  const layout = Array.isArray(profile.layout)
    ? profile.layout.filter((el) => {
        if (!el || el.type !== "sticker") return true;
        return Boolean(String(el.emoji || el.text || "").trim());
      })
    : [];
  return { ...profile, themeId, companionEmoji, layout };
}

function userOwnsCanvasEmoji(user, emoji) {
  const e = String(emoji || "").trim().slice(0, 8);
  if (!e) return false;
  const inv = ensureInventory(user);
  if ((Number(inv.emojis[e]) || 0) > 0) return true;
  if ((Number(inv.stickers[e]) || 0) > 0) return true;
  // Nur explizit kostenlose Katalog-Items ohne Inventar-Eintrag
  if (EMOJI_SHOP_PRICES[e] === 0) return true;
  if (STICKER_SHOP_PRICES[e] === 0) return true;
  return false;
}

/** Unique Shop-Items (Theme/Pet): schon im Besitz → kein zweiter Kauf/Tausch. */
function userAlreadyOwnsUnique(user, kind, itemId) {
  if (!user) return false;
  if (kind !== "pets" && kind !== "themes") return false;
  return market.userOwnsItem(user, ensureInventory, kind, itemId);
}

/** Abgelaufenes Angebot: Item an Seller zurück, Status expired. */
function expireOpenListing(entry) {
  if (!entry || entry.status !== "open") return false;
  if (!entry.expiresAt || entry.expiresAt >= Date.now()) return false;
  const seller = getDb().users?.[entry.sellerId];
  if (seller) {
    safeGiveItem(seller, entry.kind, entry.itemId);
  }
  entry.status = "expired";
  entry.expiredAt = Date.now();
  return true;
}

/** Alle abgelaufenen Markt-Angebote auflösen (Items zurück). */
function sweepExpiredMarketListings() {
  const listings = market.ensureMarket(getDb());
  let n = 0;
  for (const entry of Object.values(listings)) {
    if (expireOpenListing(entry)) n += 1;
  }
  if (n > 0) scheduleSave();
  return n;
}

/**
 * Ausgerüstete Items nicht verkaufen (Profil / Reaktionsleiste).
 * @returns {string|null} Fehlermeldung oder null wenn ok
 */
function marketItemEquippedLock(user, kind, itemId) {
  if (!user || !kind || !itemId) return null;
  const inv = ensureInventory(user);
  const id = String(itemId).trim();
  if (kind === "pets") {
    if (String(inv.equippedPet || "") === id) {
      return "Begleiter ist ausgerüstet — zuerst einen anderen wählen.";
    }
    const companion = String(user.profileCanvas?.companionEmoji || "").trim();
    if (companion && companion === id) {
      return "Begleiter ist auf dem Profil — zuerst wechseln.";
    }
    return null;
  }
  if (kind === "themes") {
    const themeId = String(user.profileCanvas?.themeId || "").trim();
    if (themeId && themeId === id) {
      return "Hintergrund ist auf dem Profil aktiv — zuerst wechseln.";
    }
    return null;
  }
  if (kind === "emojis") {
    const settings = ensureSettings(user);
    const bar = Array.isArray(settings.emojiBar) ? settings.emojiBar : [];
    const inBar = bar.some((e) => String(e) === id);
    const count = Number(inv.emojis?.[id]) || 0;
    if (inBar && count <= 1) {
      return "Emoji ist in der Reaktionsleiste — zuerst entfernen.";
    }
    return null;
  }
  // Stickers: freier Bestand ist schon ohne Profil-Platzierungen
  return null;
}

/** Nach Verkauf/Entnahme: Profil-Ghosts bereinigen. */
function cleanupProfileAfterItemRemoved(user, kind, itemId) {
  if (!user || !kind || !itemId) return;
  const id = String(itemId).trim();
  const inv = ensureInventory(user);
  if (kind === "pets") {
    if (String(inv.equippedPet || "") === id || !inv.pets.includes(inv.equippedPet)) {
      inv.equippedPet = DEFAULT_PET;
    }
    if (user.profileCanvas && String(user.profileCanvas.companionEmoji || "") === id) {
      user.profileCanvas.companionEmoji = inv.equippedPet || DEFAULT_PET;
    }
  }
  if (kind === "themes" && user.profileCanvas) {
    if (String(user.profileCanvas.themeId || "") === id) {
      user.profileCanvas.themeId = "meadow";
    }
  }
  if (kind === "emojis") {
    const settings = ensureSettings(user);
    if (Array.isArray(settings.emojiBar)) {
      const count = Number(inv.emojis?.[id]) || 0;
      if (count <= 0) {
        settings.emojiBar = settings.emojiBar.filter((e) => String(e) !== id);
      }
    }
  }
}

/** Einfaches IP-Rate-Limit (Anti-Account-Farming). */
const rateBuckets = new Map();
function rateLimit(key, max, windowMs) {
  const now = Date.now();
  let b = rateBuckets.get(key);
  if (!b || now - b.start >= windowMs) {
    b = { start: now, count: 0 };
    rateBuckets.set(key, b);
  }
  b.count += 1;
  if (rateBuckets.size > 5000) {
    for (const [k, v] of rateBuckets) {
      if (now - v.start >= windowMs) rateBuckets.delete(k);
    }
  }
  return b.count <= max;
}

function userPetEmoji(user) {
  const inv = ensureInventory(user);
  return String(inv.equippedPet || DEFAULT_PET).slice(0, 8) || DEFAULT_PET;
}

function ensureSettings(user) {
  if (!user.settings || typeof user.settings !== "object") {
    user.settings = {};
  }
  return user.settings;
}

function sanitizeSettings(raw) {
  const src = raw && typeof raw === "object" ? raw : {};
  const quietHours =
    src.quietHours && typeof src.quietHours === "object" ? src.quietHours : {};
  const quietOut = {};
  for (let day = 1; day <= 7; day++) {
    const arr = quietHours[String(day)];
    if (!Array.isArray(arr)) continue;
    const windows = [];
    for (const w of arr) {
      if (!w || typeof w !== "object") continue;
      const s = Math.floor(Number(w.s ?? w.startMinutes));
      const e = Math.floor(Number(w.e ?? w.endMinutes));
      if (
        Number.isFinite(s) &&
        Number.isFinite(e) &&
        s >= 0 &&
        s < 1440 &&
        e >= 0 &&
        e < 1440 &&
        s !== e
      ) {
        windows.push({ s, e });
      }
    }
    if (windows.length) quietOut[String(day)] = windows.slice(0, 12);
  }
  let emojiBar = [];
  if (Array.isArray(src.emojiBar)) {
    emojiBar = src.emojiBar
      .map((e) => String(e || "").trim().slice(0, 8))
      .filter(Boolean)
      .filter((e, i, a) => a.indexOf(e) === i)
      .slice(0, 8);
  }
  // Pro Lobby-Code (nicht lokale UUID) — sonst bricht Multi-Gerät-Sync die Glocke
  const lobbyProximity = {};
  if (src.lobbyProximity && typeof src.lobbyProximity === "object") {
    for (const [k, v] of Object.entries(src.lobbyProximity)) {
      if (v !== true) continue;
      const code = String(k || "")
        .trim()
        .toUpperCase()
        .replace(/^LUV-/, "")
        .slice(0, 16);
      // Nur stabile Invite-Codes; UUIDs (mit Bindestrichen) verwerfen
      if (code.length >= 3 && code.length <= 16 && /^[A-Z0-9]+$/.test(code)) {
        lobbyProximity[code] = true;
      }
    }
  }
  const brush = Number(src.brushWidth);
  const updatedAt = Number(src.updatedAt);
  return {
    quietHours: quietOut,
    emojiBar,
    partnerDrawNotify: src.partnerDrawNotify !== false,
    partnerHaptic: src.partnerHaptic !== false,
    liveProximityRich: src.liveProximityRich !== false,
    liveProximityWake: Boolean(src.liveProximityWake),
    lobbyProximity,
    brushWidth: Number.isFinite(brush)
      ? Math.min(40, Math.max(6, brush))
      : 18,
    updatedAt: Number.isFinite(updatedAt) && updatedAt > 0 ? updatedAt : 0,
  };
}

function publicSettings(user) {
  return sanitizeSettings(ensureSettings(user));
}

function mergeInventory(target, source) {
  const a = ensureInventory(target);
  const b = ensureInventory(source);
  for (const [e, n] of Object.entries(b.emojis || {})) {
    if (!isKnownInventoryItem("emojis", e)) continue;
    a.emojis[e] = Math.min(999, Math.max(Number(a.emojis[e]) || 0, Number(n) || 0));
  }
  for (const [e, n] of Object.entries(b.stickers || {})) {
    if (!isKnownInventoryItem("stickers", e)) continue;
    a.stickers[e] = Math.min(999, Math.max(Number(a.stickers[e]) || 0, Number(n) || 0));
  }
  for (const t of b.themes || []) {
    if (t && isKnownInventoryItem("themes", t) && !a.themes.includes(t)) a.themes.push(t);
  }
  for (const p of b.pets || []) {
    if (p && isKnownInventoryItem("pets", p) && !a.pets.includes(p)) a.pets.push(p);
  }
  if (!a.equippedPet || !a.pets.includes(a.equippedPet)) {
    a.equippedPet = b.equippedPet && a.pets.includes(b.equippedPet)
      ? b.equippedPet
      : DEFAULT_PET;
  }
}

function mergeFriends(target, source) {
  const a = ensureFriends(target);
  const b = ensureFriends(source);
  for (const id of b.list || []) {
    if (id && id !== target.id && !a.list.includes(id)) a.list.push(id);
  }
  for (const id of b.incoming || []) {
    if (id && id !== target.id && !a.incoming.includes(id) && !a.list.includes(id)) {
      a.incoming.push(id);
    }
  }
  for (const id of b.outgoing || []) {
    if (id && id !== target.id && !a.outgoing.includes(id) && !a.list.includes(id)) {
      a.outgoing.push(id);
    }
  }
}

function profileRichness(p) {
  if (!p || typeof p !== "object") return 0;
  const layout = Array.isArray(p.layout) ? p.layout.length : 0;
  return layout + (String(p.bio || "").trim() ? 2 : 0) + (p.themeId ? 1 : 0);
}

function mergeSettings(target, source) {
  const a = ensureSettings(target);
  const b = ensureSettings(source);
  const aAt = Number(a.updatedAt) || 0;
  const bAt = Number(b.updatedAt) || 0;
  if (bAt > aAt || Object.keys(a).length === 0) {
    target.settings = sanitizeSettings({ ...a, ...b, updatedAt: Math.max(aAt, bAt) });
  }
}

function ensureFriends(user) {
  if (!user.friends || typeof user.friends !== "object") {
    user.friends = {};
  }
  const f = user.friends;
  if (!Array.isArray(f.list)) f.list = [];
  if (!Array.isArray(f.incoming)) f.incoming = [];
  if (!Array.isArray(f.outgoing)) f.outgoing = [];
  if (!Array.isArray(f.petKraulTargets)) f.petKraulTargets = [];
  f.list = f.list.map((id) => String(id || "").trim()).filter(Boolean);
  f.incoming = f.incoming.map((id) => String(id || "").trim()).filter(Boolean);
  f.outgoing = f.outgoing.map((id) => String(id || "").trim()).filter(Boolean);
  user.friends = f;
  return f;
}

function friendPublicCard(user) {
  if (!user) return null;
  ensureCoinBuckets(user);
  return {
    userId: user.id,
    nickname: String(user.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
    petEmoji: userPetEmoji(user),
  };
}

function friendRelation(me, otherId) {
  const f = ensureFriends(me);
  if (f.list.includes(otherId)) return "friends";
  if (f.outgoing.includes(otherId)) return "outgoing";
  if (f.incoming.includes(otherId)) return "incoming";
  return "none";
}

function canPetKraulToday(me, targetId) {
  const f = ensureFriends(me);
  const day = todayKey();
  if (f.petKraulDay !== day) {
    f.petKraulDay = day;
    f.petKraulTargets = [];
  }
  return !f.petKraulTargets.includes(targetId);
}

function markPetKraul(me, targetId) {
  const f = ensureFriends(me);
  const day = todayKey();
  if (f.petKraulDay !== day) {
    f.petKraulDay = day;
    f.petKraulTargets = [];
  }
  if (!f.petKraulTargets.includes(targetId)) {
    f.petKraulTargets.push(targetId);
  }
}

/** @type {Map<string, any>} */
const rooms = new Map();

function serializeRoom(code, room) {
  return {
    token: room.token,
    createdAt: room.createdAt || Date.now(),
    lastActiveAt: room.lastActiveAt || room.createdAt || Date.now(),
    hostUserId: room.hostUserId || null,
    name: room.name || "Lobby",
    hostNickname: room.hostNickname || "Host",
    isFree: Boolean(room.isFree),
    isRandom: Boolean(room.isRandom),
    capacity: roomCapacity(room),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    colorByUserId:
      room.colorByUserId && typeof room.colorByUserId === "object"
        ? room.colorByUserId
        : {},
    peakPeers: Math.max(1, Number(room.peakPeers) || 1),
    lastCanvasAt: Number(room.lastCanvasAt) || 0,
    memberUserIds: Array.isArray(room.memberUserIds)
      ? room.memberUserIds.filter((id) => typeof id === "string")
      : [],
    joinAnnouncedUserIds: Array.isArray(room.joinAnnouncedUserIds)
      ? room.joinAnnouncedUserIds.filter((id) => typeof id === "string")
      : [],
    publicShare:
      room.publicShare && typeof room.publicShare === "object"
        ? {
            day: String(room.publicShare.day || ""),
            count: Math.max(0, Number(room.publicShare.count) || 0),
            nextAt: Number(room.publicShare.nextAt) || 0,
          }
        : { day: "", count: 0, nextAt: 0 },
  };
}

function persistRooms() {
  const db = getDb();
  const out = {};
  for (const [code, room] of rooms.entries()) {
    out[code] = serializeRoom(code, room);
  }
  db.rooms = out;
  scheduleSave();
}

function touchRoom(room) {
  if (!room) return;
  room.lastActiveAt = Date.now();
}

function healRoomCapacity(room) {
  if (!room) return false;
  const need = roomCapacity(room);
  if ((Number(room.capacity) || 0) < need) {
    room.capacity = need;
    return true;
  }
  return false;
}

function restoreRoomsFromDisk() {
  const db = getDb();
  const saved = db.rooms && typeof db.rooms === "object" ? db.rooms : {};
  let n = 0;
  for (const [rawCode, data] of Object.entries(saved)) {
    const code = String(rawCode || "")
      .toUpperCase()
      .replace(/^LUV-/, "");
    if (!code || !data || typeof data !== "object") continue;
    if (rooms.has(code)) continue;
    rooms.set(code, {
      token: data.token || randomToken().slice(0, 32),
      createdAt: Number(data.createdAt) || Date.now(),
      lastActiveAt: Number(data.lastActiveAt) || Number(data.createdAt) || Date.now(),
      sockets: new Map(),
      clearProposal: null,
      game: null,
      gameTimer: null,
      hostUserId: data.hostUserId || null,
      name: String(data.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
      hostNickname: String(data.hostNickname || "Host").slice(0, 18),
      isFree: Boolean(data.isFree),
      isRandom: Boolean(data.isRandom),
      capacity: Math.min(
        MAX_PEERS,
        Math.max(
          1,
          Number(data.capacity) || defaultLobbyCapacity(Boolean(data.isFree))
        )
      ),
      hostColorSide: normalizeHostColorSide(data.hostColorSide),
      colorByUserId:
        data.colorByUserId && typeof data.colorByUserId === "object"
          ? data.colorByUserId
          : {},
      peakPeers: Math.max(1, Number(data.peakPeers) || 1),
      lastCanvasAt: Number(data.lastCanvasAt) || 0,
      memberUserIds: Array.isArray(data.memberUserIds)
        ? data.memberUserIds.filter((id) => typeof id === "string")
        : [],
      joinAnnouncedUserIds: Array.isArray(data.joinAnnouncedUserIds)
        ? data.joinAnnouncedUserIds.filter((id) => typeof id === "string")
        : Array.isArray(data.memberUserIds)
          ? data.memberUserIds.filter((id) => typeof id === "string")
          : [],
      publicShare:
        data.publicShare && typeof data.publicShare === "object"
          ? {
              day: String(data.publicShare.day || ""),
              count: Math.max(0, Number(data.publicShare.count) || 0),
              nextAt: Number(data.publicShare.nextAt) || 0,
            }
          : { day: "", count: 0, nextAt: 0 },
      strokes: loadRoomStrokes(code),
      stickers: loadRoomStickers(code),
    });
    const room = rooms.get(code);
    healRoomMembership(room);
    n += 1;
  }
  if (n > 0) {
    console.log(`restored ${n} lobby room(s) from disk`);
    persistRooms();
  }
}

const SNAPSHOT_DIR = path.join(DATA_DIR, "snapshots");
const STROKES_DIR = path.join(DATA_DIR, "strokes");
const MEMORY_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_STROKES_PER_ROOM = 2500;
const MAX_STICKERS_PER_ROOM = 80;
const MAX_POINTS_PER_STROKE = 420;
const STROKE_HISTORY_CHUNK = 35;
const strokeSaveTimers = new Map();
const stickerSaveTimers = new Map();

function ensureStrokesDir() {
  fs.mkdirSync(STROKES_DIR, { recursive: true });
}

function strokesFilePath(code) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  return path.join(STROKES_DIR, `${clean}.json`);
}

function stickersFilePath(code) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  return path.join(STROKES_DIR, `${clean}.stickers.json`);
}

function loadRoomStrokes(code) {
  try {
    const raw = fs.readFileSync(strokesFilePath(code), "utf8");
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((s) => sanitizeStoredStroke(s))
      .filter(Boolean)
      .slice(-MAX_STROKES_PER_ROOM);
  } catch {
    return [];
  }
}

function saveRoomStrokes(code, strokes) {
  ensureStrokesDir();
  const file = strokesFilePath(code);
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(Array.isArray(strokes) ? strokes : []));
  fs.renameSync(tmp, file);
}

function scheduleStrokeSave(code, room) {
  const key = String(code || "").toUpperCase();
  const prev = strokeSaveTimers.get(key);
  if (prev) clearTimeout(prev);
  strokeSaveTimers.set(
    key,
    setTimeout(() => {
      strokeSaveTimers.delete(key);
      try {
        saveRoomStrokes(key, room?.strokes || []);
      } catch (err) {
        console.warn("stroke save failed", key, err?.message || err);
      }
    }, 700)
  );
}

function loadRoomStickers(code) {
  try {
    const raw = fs.readFileSync(stickersFilePath(code), "utf8");
    const arr = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((s) => sanitizeStoredSticker(s))
      .filter(Boolean)
      .slice(-MAX_STICKERS_PER_ROOM);
  } catch {
    return [];
  }
}

function saveRoomStickers(code, stickers) {
  ensureStrokesDir();
  const file = stickersFilePath(code);
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(Array.isArray(stickers) ? stickers : []));
  fs.renameSync(tmp, file);
}

function scheduleStickerSave(code, room) {
  const key = String(code || "").toUpperCase();
  const prev = stickerSaveTimers.get(key);
  if (prev) clearTimeout(prev);
  stickerSaveTimers.set(
    key,
    setTimeout(() => {
      stickerSaveTimers.delete(key);
      try {
        saveRoomStickers(key, room?.stickers || []);
      } catch (err) {
        console.warn("sticker save failed", key, err?.message || err);
      }
    }, 400)
  );
}

function sanitizeStoredSticker(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = String(raw.id || "").trim();
  const emoji = String(raw.emoji || "").trim().slice(0, 8);
  if (!id || id.length > 80 || !emoji) return null;
  const x = Math.min(1, Math.max(0, Number(raw.x)));
  const y = Math.min(1, Math.max(0, Number(raw.y)));
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  const nickname = String(raw.nickname || "").trim().slice(0, 18) || null;
  return { id, emoji, x, y, nickname };
}

function ensureRoomStickers(room, code) {
  if (!room) return [];
  if (!Array.isArray(room.stickers)) {
    room.stickers = loadRoomStickers(code);
  }
  return room.stickers;
}

function appendRoomSticker(room, code, sticker) {
  const list = ensureRoomStickers(room, code);
  const idx = list.findIndex((s) => s.id === sticker.id);
  if (idx >= 0) list[idx] = sticker;
  else list.push(sticker);
  while (list.length > MAX_STICKERS_PER_ROOM) list.shift();
  room.stickers = list;
  scheduleStickerSave(code, room);
  return true;
}

function clearRoomStickers(room, code) {
  if (!room) return;
  room.stickers = [];
  scheduleStickerSave(code, room);
}

function removeRoomSticker(room, code, stickerId) {
  const id = String(stickerId || "").trim();
  if (!id) return false;
  const list = ensureRoomStickers(room, code);
  const next = list.filter((s) => s.id !== id);
  if (next.length === list.length) return false;
  room.stickers = next;
  scheduleStickerSave(code, room);
  return true;
}

function sanitizeTemplateParts(rawParts) {
  if (!Array.isArray(rawParts)) return null;
  const out = [];
  for (const part of rawParts.slice(0, 48)) {
    if (!part || typeof part !== "object") continue;
    const pointsIn = Array.isArray(part.points) ? part.points : [];
    const points = [];
    for (const p of pointsIn.slice(0, 240)) {
      if (!p || typeof p !== "object") continue;
      const x = Number(p.x);
      const y = Number(p.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
      points.push({
        x: Math.min(1, Math.max(0, x)),
        y: Math.min(1, Math.max(0, y)),
      });
    }
    if (points.length < 2) continue;
    out.push({
      points,
      width: Math.min(48, Math.max(3, Number(part.width) || 18)),
      colorIndex: Math.max(0, Math.min(31, Number(part.colorIndex) || 0)),
    });
  }
  return out.length ? out : null;
}

function sanitizeStoredStroke(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = String(raw.id || "").trim();
  if (!id || id.length > 80) return null;
  const emoji = String(raw.emoji || "").trim().slice(0, 8) || null;
  const templateParts = sanitizeTemplateParts(raw.templateParts);
  const pointsIn = Array.isArray(raw.points) ? raw.points : [];
  const points = [];
  for (const p of pointsIn) {
    if (!p || typeof p !== "object") continue;
    const x = Number(p.x);
    const y = Number(p.y);
    if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
    points.push({
      x: Math.min(1, Math.max(0, x)),
      y: Math.min(1, Math.max(0, y)),
    });
    if (points.length >= MAX_POINTS_PER_STROKE) break;
  }
  // Emoji/Vorlage: 1 Punkt reicht; Linien brauchen ≥2
  if (emoji || templateParts) {
    if (!points.length) return null;
  } else if (points.length < 2) {
    return null;
  }
  const out = {
    id,
    points,
    // Dicke: Referenz-Pixel (kurze Seite ≈ 1000) — Float beibehalten für feine Pinsel
    width: emoji || templateParts
      ? Math.min(48, Math.max(0, Number(raw.width) || 0))
      : Math.min(48, Math.max(3, Number(raw.width) || 18)),
    nickname: String(raw.nickname || "").trim().slice(0, 18) || null,
    colorIndex: Math.max(0, Math.min(31, Number(raw.colorIndex) || 0)),
    authorId: String(raw.authorId || "").trim().slice(0, 64) || null,
    colorLocked: Boolean(raw.colorLocked),
  };
  if (emoji) out.emoji = emoji;
  if (templateParts) {
    out.templateParts = templateParts;
    const sc = Number(raw.templateScale);
    out.templateScale = Number.isFinite(sc)
      ? Math.min(4, Math.max(0.2, sc))
      : 1;
    const rot = Number(raw.templateRotation);
    out.templateRotation = Number.isFinite(rot) ? rot : 0;
  }
  return out;
}

function migrateLegacyStickersIntoStrokes(room, code) {
  if (!room || room._stickersMigrated) return;
  room._stickersMigrated = true;
  const legacy = loadRoomStickers(code);
  const memory = Array.isArray(room.stickers) ? room.stickers : [];
  if (!legacy.length && !memory.length) return;
  if (!Array.isArray(room.strokes)) {
    room.strokes = loadRoomStrokes(code);
  }
  const list = room.strokes;
  const ids = new Set(list.map((s) => s.id));
  let added = 0;
  for (const s of [...memory, ...legacy]) {
    const sticker = sanitizeStoredSticker(s);
    if (!sticker || ids.has(sticker.id)) continue;
    const stroke = sanitizeStoredStroke({
      id: sticker.id,
      points: [{ x: sticker.x, y: sticker.y }],
      width: 0,
      emoji: sticker.emoji,
      nickname: sticker.nickname,
      colorIndex: 0,
    });
    if (!stroke) continue;
    list.push(stroke);
    ids.add(stroke.id);
    added += 1;
  }
  if (added > 0) {
    while (list.length > MAX_STROKES_PER_ROOM) list.shift();
    room.strokes = list;
    scheduleStrokeSave(code, room);
  }
  // Legacy-Datei leeren — Emojis leben in strokes.json
  room.stickers = [];
  scheduleStickerSave(code, room);
}

function ensureRoomStrokes(room, code) {
  if (!room) return [];
  if (!Array.isArray(room.strokes)) {
    room.strokes = loadRoomStrokes(code);
  }
  migrateLegacyStickersIntoStrokes(room, code);
  return room.strokes;
}

function appendRoomStroke(room, code, stroke) {
  const list = ensureRoomStrokes(room, code);
  if (list.some((s) => s.id === stroke.id)) return false;
  list.push(stroke);
  while (list.length > MAX_STROKES_PER_ROOM) list.shift();
  room.strokes = list;
  scheduleStrokeSave(code, room);
  return true;
}

function removeRoomStroke(room, code, strokeId) {
  const id = String(strokeId || "").trim();
  if (!id) return false;
  const list = ensureRoomStrokes(room, code);
  const next = list.filter((s) => s.id !== id);
  if (next.length === list.length) return false;
  room.strokes = next;
  scheduleStrokeSave(code, room);
  return true;
}

function clearRoomStrokes(room, code) {
  if (!room) return;
  room.strokes = [];
  scheduleStrokeSave(code, room);
  clearRoomStickers(room, code);
}

function strokeFromSocketMessage(json, socket) {
  // "local" von alten Clients ignorieren — sonst kollabieren alle Ghost-Avatare
  const rawAuthor = String(json?.authorId || "").trim();
  const authorId =
    rawAuthor && rawAuthor.toLowerCase() !== "local" && rawAuthor !== "null"
      ? rawAuthor
      : socket?.luvUserId || socket?.luvPeerId || null;
  const base = sanitizeStoredStroke({
    id: json?.id,
    points: json?.points,
    width: json?.width,
    nickname: json?.nickname || socket?.luvNickname || null,
    colorIndex:
      json?.colorIndex != null ? json.colorIndex : socket?.luvColorIndex || 0,
    authorId,
    emoji: json?.emoji,
    colorLocked: json?.colorLocked,
    templateParts: json?.templateParts,
    templateScale: json?.templateScale,
    templateRotation: json?.templateRotation,
  });
  return base;
}

function stickersFromEmojiStrokes(strokes) {
  // Für ältere Clients: Emoji-Striche auch als stickers[] spiegeln
  const out = [];
  for (const s of strokes || []) {
    if (!s || !s.emoji || !Array.isArray(s.points) || !s.points.length) continue;
    out.push({
      id: s.id,
      emoji: s.emoji,
      x: s.points[0].x,
      y: s.points[0].y,
      nickname: s.nickname || null,
    });
  }
  return out;
}

function sendCanvasHistory(socket, room, code) {
  if (!socket || socket.readyState !== 1) return;
  const strokes = ensureRoomStrokes(room, code);
  const stickers = stickersFromEmojiStrokes(strokes);
  const sendChunk = (payload) => {
    try {
      if (socket.readyState === 1) socket.send(JSON.stringify(payload));
    } catch (err) {
      console.warn("sendCanvasHistory failed:", err?.message || err);
    }
  };
  if (!strokes.length) {
    sendChunk({
      type: "canvas_history",
      strokes: [],
      stickers,
      done: true,
      replace: true,
    });
    socket.luvHistoryAt = Date.now();
    return;
  }
  for (let i = 0; i < strokes.length; i += STROKE_HISTORY_CHUNK) {
    const chunk = strokes.slice(i, i + STROKE_HISTORY_CHUNK);
    const done = i + STROKE_HISTORY_CHUNK >= strokes.length;
    sendChunk({
      type: "canvas_history",
      strokes: chunk,
      // Stickers immer im letzten Chunk — leeres Array löscht Client-State korrekt
      ...(done ? { stickers } : {}),
      done,
      replace: i === 0,
    });
  }
  socket.luvHistoryAt = Date.now();
}

function ensureSnapshotDir() {
  fs.mkdirSync(SNAPSHOT_DIR, { recursive: true });
}

function canvasMemories() {
  const db = getDb();
  if (!db.canvasMemories || typeof db.canvasMemories !== "object") {
    db.canvasMemories = {};
  }
  return db.canvasMemories;
}

function touchCanvasActivity(room, peerCountHint) {
  if (!room) return;
  const now = Date.now();
  const prev = Number(room.lastCanvasAt) || 0;
  room.lastCanvasAt = now;
  const n = Math.max(
    Number(peerCountHint) || 0,
    room.sockets ? room.sockets.size : 0
  );
  room.peakPeers = Math.max(Number(room.peakPeers) || 1, n, 1);
  touchRoom(room);
  // Disk nicht bei jedem Strich — alle ~30s reicht
  if (now - prev > 30_000) persistRooms();
}

function cleanupCanvasMemories() {
  ensureSnapshotDir();
  const mem = canvasMemories();
  const now = Date.now();
  let dirty = false;
  for (const [code, meta] of Object.entries(mem)) {
    const released = Number(meta?.releasedAt) || 0;
    const expires = released > 0 ? released + MEMORY_TTL_MS : 0;
    if (released > 0 && expires > 0 && now > expires) {
      const file = meta?.file ? path.join(SNAPSHOT_DIR, path.basename(meta.file)) : null;
      if (file && fs.existsSync(file)) {
        try {
          fs.unlinkSync(file);
        } catch {
          /* ignore */
        }
      }
      delete mem[code];
      dirty = true;
    }
  }
  // Orphan files
  try {
    for (const name of fs.readdirSync(SNAPSHOT_DIR)) {
      const full = path.join(SNAPSHOT_DIR, name);
      const st = fs.statSync(full);
      if (now - st.mtimeMs > MEMORY_TTL_MS * 2) {
        try {
          fs.unlinkSync(full);
        } catch {
          /* ignore */
        }
      }
    }
  } catch {
    /* ignore */
  }
  if (dirty) scheduleSave();
}

function maybeReleaseMemory(code, room) {
  const clean = String(code || "").toUpperCase();
  if (!clean || !room) return null;
  const mem = canvasMemories();
  const last = Number(room.lastCanvasAt) || 0;
  if (!last || Date.now() - last < MEMORY_TTL_MS) return mem[clean] || null;
  const entry = mem[clean];
  if (!entry?.file) return null;
  if (!entry.releasedAt) {
    entry.releasedAt = Date.now();
    entry.lobbyName = room.name || "Lobby";
    scheduleSave();
  }
  return entry;
}

function randomCode(length = 6, alphabet = CODE_ALPHABET) {
  let out = "";
  for (let i = 0; i < length; i++) out += alphabet[crypto.randomInt(0, alphabet.length)];
  return out;
}

function randomToken() {
  return crypto.randomBytes(24).toString("hex");
}

function inviteFor(code) {
  return {
    code,
    invite: `LUV-${code}`,
    joinUrl: `${PUBLIC_JOIN_BASE.replace(/\/$/, "")}/${code}`,
  };
}

function ensureHostedRooms(user) {
  if (!user.hostedRooms || typeof user.hostedRooms !== "object") {
    user.hostedRooms = {};
  }
}

/**
 * Früher: hat hostedRooms gelöscht, sobald der Raum nicht im RAM war —
 * nach Restart/Deploy weg. Nie mehr so: Lobbys bleiben bis explizites Verlassen.
 * Geister nach Leave (Ownership ohne Mitgliedschaft) räumt reconcileAbandonedLobbyOwnership.
 */
function pruneHostedRooms(user) {
  ensureHostedRooms(user);
  // No-op (bewusst): fehlende rooms-Map-Einträge dürfen Ownership nicht killen.
  return false;
}

/**
 * Ownership ohne Mitgliedschaft = bewusst verlassen → aus Cloud-Liste entfernen.
 * Leere Geisterräume (bezahlt wie gratis) wirklich löschen, damit Sync sie nicht zurückbringt.
 */
function reconcileAbandonedLobbyOwnership(user) {
  if (!user?.id) return false;
  ensureHostedRooms(user);
  let dirty = false;
  for (const code of Object.keys(user.hostedRooms || {})) {
    let room = rooms.get(code);
    if (!room) {
      const saved = getDb().rooms?.[code];
      if (saved && typeof saved === "object") {
        const members = Array.isArray(saved.memberUserIds) ? saved.memberUserIds : [];
        if (!members.includes(user.id)) {
          delete user.hostedRooms[code];
          dirty = true;
          if (members.length === 0) {
            delete getDb().rooms[code];
          } else if (saved.hostUserId === user.id) {
            room = hydrateRoom(code, saved);
            rooms.set(code, room);
            if (!promoteNextHostFromMembers(room, code)) {
              dissolveEmptyLobby(room, code);
            }
          }
        }
        continue;
      }
      delete user.hostedRooms[code];
      dirty = true;
      continue;
    }
    const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
    if (members.includes(user.id)) continue;
    delete user.hostedRooms[code];
    dirty = true;
    if (room.hostUserId === user.id) {
      if ((room.sockets?.size || 0) > 0) {
        if (!promoteNextHost(room, code)) promoteNextHostFromMembers(room, code);
      } else if (!promoteNextHostFromMembers(room, code)) {
        dissolveEmptyLobby(room, code);
      }
    }
  }
  healJoinedRoomsFromMembership(user);
  if (dirty) {
    persistRooms();
    scheduleSave();
  }
  return dirty;
}

function hydrateRoom(code, data, tokenOverride) {
  return {
    token: tokenOverride || data.token || randomToken().slice(0, 32),
    createdAt: Number(data.createdAt) || Date.now(),
    lastActiveAt: Date.now(),
    sockets: new Map(),
    clearProposal: null,
    game: null,
    gameTimer: null,
    hostUserId: data.hostUserId || null,
    name: String(data.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    hostNickname: String(data.hostNickname || "Host").slice(0, 18),
    isFree: Boolean(data.isFree),
    isRandom: Boolean(data.isRandom),
    capacity: Math.min(
      MAX_PEERS,
      Math.max(
        1,
        Number(data.capacity) || defaultLobbyCapacity(Boolean(data.isFree))
      )
    ),
    hostColorSide: normalizeHostColorSide(data.hostColorSide),
    colorByUserId:
      data.colorByUserId && typeof data.colorByUserId === "object"
        ? { ...data.colorByUserId }
        : {},
    peakPeers: Math.max(1, Number(data.peakPeers) || 1),
    lastCanvasAt: Number(data.lastCanvasAt) || 0,
    memberUserIds: Array.isArray(data.memberUserIds)
      ? data.memberUserIds.filter((id) => typeof id === "string")
      : [],
    joinAnnouncedUserIds: Array.isArray(data.joinAnnouncedUserIds)
      ? data.joinAnnouncedUserIds.filter((id) => typeof id === "string")
      : Array.isArray(data.memberUserIds)
        ? data.memberUserIds.filter((id) => typeof id === "string")
        : [],
    publicShare:
      data.publicShare && typeof data.publicShare === "object"
        ? {
            day: String(data.publicShare.day || ""),
            count: Math.max(0, Number(data.publicShare.count) || 0),
            nextAt: Number(data.publicShare.nextAt) || 0,
          }
        : { day: "", count: 0, nextAt: 0 },
    strokes: loadRoomStrokes(code),
    stickers: loadRoomStickers(code),
  };
}

function ensureJoinedRooms(user) {
  if (!user.joinedRooms || typeof user.joinedRooms !== "object") {
    user.joinedRooms = {};
  }
  return user.joinedRooms;
}

function findRoomCode(room) {
  if (!room) return null;
  for (const [code, r] of rooms.entries()) {
    if (r === room) return code;
  }
  return null;
}

function rememberJoinedLobby(user, code, room) {
  if (!user || !code || !room) return;
  const j = ensureJoinedRooms(user);
  if (room.hostUserId && room.hostUserId === user.id) {
    delete j[code];
    return;
  }
  j[code] = {
    name: String(room.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    token: room.token || null,
    capacity: roomCapacity(room),
    isFree: Boolean(room.isFree),
    isRandom: Boolean(room.isRandom),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    hostNickname: String(room.hostNickname || "Host").slice(0, 18),
    joinedAt: Date.now(),
  };
}

function forgetJoinedLobby(user, code) {
  if (!user || !code) return;
  ensureJoinedRooms(user);
  delete user.joinedRooms[code];
}

function healJoinedRoomsFromMembership(user) {
  if (!user?.id) return;
  ensureJoinedRooms(user);
  const db = getDb();
  const seen = new Set();
  for (const [code, room] of rooms.entries()) {
    if (
      Array.isArray(room.memberUserIds) &&
      room.memberUserIds.includes(user.id) &&
      room.hostUserId !== user.id
    ) {
      rememberJoinedLobby(user, code, room);
      seen.add(code);
    }
  }
  for (const [code, data] of Object.entries(db.rooms || {})) {
    if (seen.has(code)) continue;
    const ids = Array.isArray(data.memberUserIds) ? data.memberUserIds : [];
    if (ids.includes(user.id) && data.hostUserId !== user.id) {
      rememberJoinedLobby(user, code, data);
      seen.add(code);
    }
  }
  // Verwaiste Einträge entfernen (Lobby weg / nicht mehr Mitglied)
  for (const code of Object.keys(user.joinedRooms)) {
    if (seen.has(code)) continue;
    const live = rooms.get(code);
    const stored = db.rooms?.[code];
    const still =
      (live &&
        Array.isArray(live.memberUserIds) &&
        live.memberUserIds.includes(user.id) &&
        live.hostUserId !== user.id) ||
      (stored &&
        Array.isArray(stored.memberUserIds) &&
        stored.memberUserIds.includes(user.id) &&
        stored.hostUserId !== user.id);
    if (!still) delete user.joinedRooms[code];
  }
}

function publicJoinedLobbies(user) {
  healJoinedRoomsFromMembership(user);
  return Object.entries(user.joinedRooms || {}).map(([code, meta]) => ({
    code,
    name: String(meta?.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    token: meta?.token || null,
    capacity: Math.min(
      MAX_PEERS,
      Math.max(
        1,
        Number(meta?.capacity) || defaultLobbyCapacity(Boolean(meta?.isFree))
      )
    ),
    isFree: Boolean(meta?.isFree),
    isRandom: Boolean(meta?.isRandom),
    lastCanvasAt: Number(rooms.get(code)?.lastCanvasAt || meta?.lastCanvasAt || 0),
    hostColorSide: normalizeHostColorSide(meta?.hostColorSide),
    invite: `${PUBLIC_JOIN_BASE}/${code}`,
    hostNickname: String(meta?.hostNickname || "Host").slice(0, 18),
    role: "join",
  }));
}

function rememberMember(room, userId) {
  if (!room || !userId) return false;
  if (!Array.isArray(room.memberUserIds)) room.memberUserIds = [];
  let added = false;
  if (!room.memberUserIds.includes(userId)) {
    room.memberUserIds.push(userId);
    if (room.memberUserIds.length > 40) {
      room.memberUserIds = room.memberUserIds.slice(-40);
    }
    added = true;
  }
  const code = findRoomCode(room);
  const user = getDb().users?.[userId];
  if (user && code) {
    rememberJoinedLobby(user, code, room);
    scheduleSave();
  }
  return added;
}

/** Bewusstes Verlassen — nicht nur offline, sondern raus aus Kachel/Roster. */
function forgetMember(room, userId) {
  if (!room || !userId) return false;
  let changed = false;
  if (Array.isArray(room.memberUserIds)) {
    const next = room.memberUserIds.filter((id) => id !== userId);
    if (next.length !== room.memberUserIds.length) {
      room.memberUserIds = next;
      changed = true;
    }
  }
  // Sonst holt healRoomMembership die Person über Farben sofort wieder rein
  if (room.colorByUserId && typeof room.colorByUserId === "object" && room.colorByUserId[userId] != null) {
    delete room.colorByUserId[userId];
    changed = true;
  }
  if (Array.isArray(room.joinAnnouncedUserIds)) {
    const nextAnn = room.joinAnnouncedUserIds.filter((id) => id !== userId);
    if (nextAnn.length !== room.joinAnnouncedUserIds.length) {
      room.joinAnnouncedUserIds = nextAnn;
      changed = true;
    }
  }
  const code = findRoomCode(room);
  const user = getDb().users?.[userId];
  if (user && code) {
    forgetJoinedLobby(user, code);
    scheduleSave();
  }
  return changed;
}

/** Einmalige Beitritts-Meldung — nicht bei jedem Reconnect / App-Wiederöffnen */
function claimJoinAnnouncement(room, userId) {
  if (!room || !userId) return false;
  if (!Array.isArray(room.joinAnnouncedUserIds)) room.joinAnnouncedUserIds = [];
  if (room.joinAnnouncedUserIds.includes(userId)) return false;
  room.joinAnnouncedUserIds.push(userId);
  if (room.joinAnnouncedUserIds.length > 40) {
    room.joinAnnouncedUserIds = room.joinAnnouncedUserIds.slice(-40);
  }
  return true;
}

/**
 * Raum laden / wiederherstellen — Lobbys dürfen Updates überleben.
 * Host mit Session darf mit seinem gespeicherten Token reclaimen.
 */
function resolveRoom(code, token, user) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  if (!clean) return null;

  let room = rooms.get(clean);
  if (!room) {
    const saved = getDb().rooms?.[clean];
    if (saved && typeof saved === "object") {
      room = hydrateRoom(clean, saved);
      rooms.set(clean, room);
      console.log(`rehydrated room ${clean} from disk`);
    }
  }

  if (room) {
    // Join/Preview ohne Token: bestehenden Live-Raum nutzen (nie ersetzen).
    if (!token || room.token === token) return room;

    // Bekanntes Mitglied mit veraltetem Token — trotzdem rein (nie Split-Lobby).
    // Früher: return null → 4401 → jeder hing allein in „seiner“ Sicht.
    if (user && Array.isArray(room.memberUserIds) && room.memberUserIds.includes(user.id)) {
      return room;
    }

    // Host: immer rein. Token nur übernehmen, wenn niemand sonst verbunden ist
    // (sonst verlieren Joiner den Match und sehen sich allein).
    if (user && room.hostUserId === user.id) {
      if ((room.sockets?.size || 0) === 0) {
        room.token = token;
        touchRoom(room);
        persistRooms();
      }
      return room;
    }
    return null;
  }

  // Komplett weg aus RAM+Disk — aus hostedRooms des Hosts neu aufbauen
  if (user) {
    ensureHostedRooms(user);
    const meta = user.hostedRooms[clean];
    if (meta && typeof meta === "object") {
      room = hydrateRoom(
        clean,
        {
          token: meta.token || token,
          createdAt: meta.createdAt,
          hostUserId: user.id,
          name: meta.name || "Lobby",
          hostNickname: user.nickname || "Host",
          isFree: Boolean(meta.isFree),
          capacity: meta.capacity,
          hostColorSide: meta.hostColorSide,
          colorByUserId: meta.colorByUserId || { [user.id]: hostColorIndex(meta.hostColorSide) },
        },
        token || meta.token
      );
      rooms.set(clean, room);
      persistRooms();
      console.log(`recreated room ${clean} from hostedRooms for ${user.id}`);
      return room;
    }
  }

  return null;
}

function releaseHostedRoom(code, hostUserId) {
  if (!hostUserId) return;
  const user = getDb().users[hostUserId];
  if (!user) return;
  ensureHostedRooms(user);
  if (user.hostedRooms[code]) {
    delete user.hostedRooms[code];
    scheduleSave();
  }
}

function roomExistsAnywhere(code) {
  if (rooms.has(code)) return true;
  const saved = getDb().rooms?.[code];
  return Boolean(saved && typeof saved === "object");
}

function hasActiveFreeLobby(user) {
  ensureHostedRooms(user);
  let dirty = false;
  let active = false;
  for (const [code, meta] of Object.entries(user.hostedRooms)) {
    if (!meta?.isFree) continue;
    // Nicht löschen nur weil 0 Sockets — Host verbindet oft erst nach Create.
    if (roomExistsAnywhere(code)) {
      active = true;
    } else {
      delete user.hostedRooms[code];
      dirty = true;
    }
  }
  if (dirty) {
    if (user.freeLobbyCreateDay === todayKey() && !active) {
      user.freeLobbyCreateDay = null;
    }
    scheduleSave();
  }
  return active;
}

function canCreateFreeLobbyToday(user) {
  // 1 Gratis-Lobby gleichzeitig (nicht „1× am Tag und fertig“).
  if (hasActiveFreeLobby(user)) return false;
  if (user.freeLobbyCreateDay === todayKey()) {
    user.freeLobbyCreateDay = null;
    scheduleSave();
  }
  return true;
}

function evaluateCanCreateFreeLobby(user) {
  // canCreateFreeLobbyToday ruft hasActiveFreeLobby bereits auf
  return canCreateFreeLobbyToday(user);
}

function defaultLobbyCapacity(isFree) {
  return isFree ? FREE_LOBBY_START_CAPACITY : PAID_LOBBY_START_CAPACITY;
}

function roomCapacity(room) {
  const fallback = defaultLobbyCapacity(Boolean(room?.isFree));
  let n = Number(room?.capacity);
  if (!Number.isFinite(n) || n < 1) n = fallback;
  // Nach Restart/Bug nie kleiner als bekannte Mitglieder — sonst room_full-Flapping
  // (verbunden → sofort wieder raus) und jeder sieht nur sich selbst.
  const members = Array.isArray(room?.memberUserIds) ? room.memberUserIds.length : 0;
  const colors =
    room?.colorByUserId && typeof room.colorByUserId === "object"
      ? Object.keys(room.colorByUserId).length
      : 0;
  const peak = Math.max(1, Number(room?.peakPeers) || 1);
  n = Math.max(n, fallback, members, colors, peak);
  return Math.min(MAX_PEERS, Math.floor(n));
}

/**
 * Nur Kapazität heilen.
 * Früher: colorByUserId → memberUserIds — das hat Leave sofort rückgängig gemacht
 * (Slot blieb belegt, Leinwand zeigte „kurz offline“ statt ausgegraut).
 */
function healRoomMembership(room) {
  if (!room) return false;
  return healRoomCapacity(room);
}

/**
 * Verbundene + sonst bekannte Mitglieder — fuer Kachel/Avatare.
 * Reihenfolge = Beitrittsreihenfolge (memberUserIds).
 */
function roomRosterAll(room) {
  const connectedById = new Map();
  for (const m of roomConnectedMembers(room)) {
    if (m?.userId) {
      connectedById.set(m.userId, {
        ...m,
        online: true,
        active: Boolean(m.active),
      });
    }
  }
  const memberIds = Array.isArray(room?.memberUserIds) ? room.memberUserIds : [];
  const out = [];
  const seen = new Set();
  for (const uid of memberIds) {
    if (!uid || seen.has(uid)) continue;
    seen.add(uid);
    const live = connectedById.get(uid);
    if (live) {
      out.push(live);
      continue;
    }
    const u = getDb().users?.[uid];
    const nick =
      String(u?.nickname || "")
        .trim()
        .slice(0, 18) || "Jemand";
    const rawColor = Number(room.colorByUserId?.[uid]);
    const colorIndex = Number.isFinite(rawColor)
      ? Math.max(0, Math.floor(rawColor))
      : 0;
    out.push({
      userId: uid,
      nickname: nick,
      colorIndex,
      active: false,
      online: false,
      petEmoji: u ? userPetEmoji(u) : DEFAULT_PET,
    });
  }
  for (const [uid, m] of connectedById) {
    if (seen.has(uid)) continue;
    out.push(m);
  }
  return out;
}

function publicRoom(room, code) {
  healRoomMembership(room);
  const roster = roomRosterAll(room);
  const online = roster.filter((m) => m.online).length;
  const peak = Math.max(1, Number(room.peakPeers) || 1, online);
  return {
    code,
    name: room.name || "Lobby",
    hostNickname: room.hostNickname || "Host",
    peers: online,
    capacity: roomCapacity(room),
    maxPeers: MAX_PEERS,
    isFree: Boolean(room.isFree),
    isRandom: Boolean(room.isRandom),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    peakPeers: peak,
    coupleMode: peak <= 2,
    lastCanvasAt: Number(room.lastCanvasAt) || 0,
    members: roster.map((m) => m.nickname),
    memberList: roster,
    ...inviteFor(code),
  };
}

const COLOR_BLUE = 0;
const COLOR_PURPLE = 1;
const COLOR_COUNT = 16;

function normalizeHostColorSide(side) {
  const s = String(side || "blue").toLowerCase();
  return s === "purple" || s === "lila" ? "purple" : "blue";
}

function hostColorIndex(side) {
  return normalizeHostColorSide(side) === "purple" ? COLOR_PURPLE : COLOR_BLUE;
}

function oppositeHostColor(hostIdx) {
  return hostIdx === COLOR_PURPLE ? COLOR_BLUE : COLOR_PURPLE;
}

function takenRoomColors(room, exceptUserId) {
  const taken = new Set();
  if (!room.colorByUserId || typeof room.colorByUserId !== "object") {
    room.colorByUserId = {};
  }
  for (const [uid, idx] of Object.entries(room.colorByUserId)) {
    if (exceptUserId && uid === exceptUserId) continue;
    if (Number.isInteger(idx)) taken.add(idx);
  }
  for (const socket of room.sockets.values()) {
    if (exceptUserId && socket.luvUserId === exceptUserId) continue;
    if (Number.isInteger(socket.luvColorIndex)) taken.add(socket.luvColorIndex);
  }
  return taken;
}

/** Host: Blau/Lila · 2. Person: Gegenseite · ab 3.: weitere Farben */
function assignRoomColor(room, userId, isHost) {
  if (!room.colorByUserId || typeof room.colorByUserId !== "object") {
    room.colorByUserId = {};
  }
  if (userId && Number.isInteger(room.colorByUserId[userId])) {
    return room.colorByUserId[userId];
  }
  const hostIdx = hostColorIndex(room.hostColorSide);
  let assigned;
  if (isHost) {
    assigned = hostIdx;
  } else {
    const taken = takenRoomColors(room, userId);
    // Host-Farbe ist reserviert, auch wenn Host noch nicht verbunden ist
    taken.add(hostIdx);
    const other = oppositeHostColor(hostIdx);
    if (!taken.has(other)) {
      assigned = other;
    } else {
      assigned = 2;
      while (taken.has(assigned) && assigned < COLOR_COUNT) assigned += 1;
      if (assigned >= COLOR_COUNT) assigned = 2;
    }
  }
  if (userId) room.colorByUserId[userId] = assigned;
  return assigned;
}

function rememberSocketColor(room, socket, user, colorIndex) {
  const idx = Number(colorIndex);
  if (!Number.isInteger(idx) || idx < 0 || idx >= COLOR_COUNT) return;
  socket.luvColorIndex = idx;
  if (!room.colorByUserId || typeof room.colorByUserId !== "object") {
    room.colorByUserId = {};
  }
  if (user?.id) room.colorByUserId[user.id] = idx;
}

function cleanupRooms() {
  const now = Date.now();
  let removed = false;
  for (const [code, room] of rooms.entries()) {
    if (room.sockets.size > 0) continue;
    // Bezahlte Lobbys nie per TTL löschen (Updates/Restarts & Geldschutz)
    if (!room.isFree) continue;
    const idleSince = room.lastActiveAt || room.createdAt || 0;
    if (now - idleSince > ROOM_TTL_MS) {
      const hostUserId = room.hostUserId;
      rooms.delete(code);
      releaseHostedRoom(code, hostUserId);
      removed = true;
    }
  }
  if (removed) persistRooms();
}

/** paidCoins = Käufe/Gutscheine (stackbar). dailyBalance = Tagesbonus (nicht stackbar). */
function ensureCoinBuckets(user) {
  if (!user) return;
  if (user.paidCoins == null || user.dailyBalance == null) {
    const total = Math.max(0, Number(user.coins) || 0);
    const day = todayKey();
    if (user.lastDailyGrantDate === day) {
      // Heutiger Tagesbonus bleibt im Tages-Topf, Rest als bezahlt
      user.dailyBalance = Math.min(DAILY_COINS, total);
      user.paidCoins = Math.max(0, total - user.dailyBalance);
    } else {
      // Alles bisherige bleibt erhalten (als bezahlt), Tagesbonus kommt beim Grant
      user.paidCoins = total;
      user.dailyBalance = 0;
    }
  }
  user.paidCoins = Math.max(0, Number(user.paidCoins) || 0);
  user.dailyBalance = Math.max(0, Number(user.dailyBalance) || 0);
}

function syncCoinTotal(user) {
  ensureCoinBuckets(user);
  user.coins = user.paidCoins + user.dailyBalance;
}

/**
 * Tagesbonus automatisch: setzt dailyBalance auf DAILY_COINS (kein Aufaddieren über Tage).
 * Verpasste Tage gibt es nicht nach.
 */
function ensureDailyGrant(user) {
  if (!user) return false;
  ensureCoinBuckets(user);
  const day = todayKey();
  if (user.lastDailyGrantDate === day) {
    syncCoinTotal(user);
    return false;
  }
  user.dailyBalance = DAILY_COINS;
  user.lastDailyGrantDate = day;
  syncCoinTotal(user);
  const db = getDb();
  db.ledger.push({
    id: newId("led"),
    userId: user.id,
    delta: DAILY_COINS,
    reason: "daily_grant",
    refId: day,
    at: Date.now(),
    balance: user.coins,
    note: "topup_not_stack",
  });
  if (db.ledger.length > 5000) db.ledger.splice(0, db.ledger.length - 5000);
  scheduleSave();
  return true;
}

const PAID_CREDIT_REASONS = new Set([
  "mollie_purchase",
  "voucher",
  "clear_refund",
  "signup_grant",
  "admin_grant",
  "public_share_reward",
  "glass_tip_recv",
  "pet_kraul_recv",
  "market_sell",
]);

const PUBLIC_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const PUBLIC_DIR = path.join(DATA_DIR, "public-canvases");
const PUBLIC_COOLDOWN_FIRST_MS = 60 * 60 * 1000;
const PUBLIC_COOLDOWN_NEXT_MS = 120 * 60 * 1000;
/** Lange offen, damit Offline-Mitglieder noch zustimmen können */
const PUBLIC_VOTE_MS = 24 * 60 * 60 * 1000;

function ensurePublicDir() {
  fs.mkdirSync(PUBLIC_DIR, { recursive: true });
}

function publicCanvases() {
  const db = getDb();
  if (!db.publicCanvases || typeof db.publicCanvases !== "object") {
    db.publicCanvases = {};
  }
  return db.publicCanvases;
}

function publicReports() {
  const db = getDb();
  if (!db.publicReports || typeof db.publicReports !== "object") {
    db.publicReports = {};
  }
  return db.publicReports;
}

function peerReports() {
  const db = getDb();
  if (!db.peerReports || typeof db.peerReports !== "object") {
    db.peerReports = {};
  }
  return db.peerReports;
}

function helpMessages() {
  const db = getDb();
  if (!db.helpMessages || typeof db.helpMessages !== "object") {
    db.helpMessages = {};
  }
  return db.helpMessages;
}

function helpMessageView(msg) {
  return {
    id: msg.id,
    message: msg.message || "",
    nickname: msg.nickname || "Jemand",
    userId: msg.userId || null,
    createdAt: msg.createdAt || 0,
    status: msg.status || "open",
  };
}

const PEER_REPORT_DIR = path.join(DATA_DIR, "peer-reports");

function ensurePeerReportDir() {
  fs.mkdirSync(PEER_REPORT_DIR, { recursive: true });
}

function peerReportView(report) {
  return {
    id: report.id,
    status: report.status || "open",
    reportedAt: report.reportedAt,
    reporterNickname: report.reporterNickname || "Jemand",
    targetNickname: report.targetNickname || "Jemand",
    targetUserId: report.targetUserId || null,
    lobbyCode: report.lobbyCode || null,
    lobbyName: report.lobbyName || "Lobby",
    hasImage: Boolean(report.file),
    imageUrl: report.file
      ? `/v1/admin/peer-reports/${encodeURIComponent(report.id)}/image`
      : null,
  };
}

const PUBLIC_DELETE_BAN_THRESHOLD = 10;

function deletePublicCanvasFile(entry) {
  if (!entry?.file) return;
  const filePath = path.join(PUBLIC_DIR, path.basename(entry.file));
  if (fs.existsSync(filePath)) {
    try {
      fs.unlinkSync(filePath);
    } catch {
      /* ignore */
    }
  }
}

function banUserForPublicDeletes(user) {
  if (!user || user.role === "admin") return;
  user.banned = true;
  user.bannedAt = Date.now();
  user.bannedReason = "public_canvas_violations";
  const db = getDb();
  for (const [token, session] of Object.entries(db.sessions || {})) {
    if (session?.userId === user.id) delete db.sessions[token];
  }
}

function publicReportView(report) {
  return {
    id: report.id,
    publicId: report.publicId,
    status: report.status || "open",
    reportedAt: report.reportedAt,
    reporterNickname: report.reporterNickname || "Jemand",
    lobbyName: report.lobbyName || "Lobby",
    hostNickname: report.hostNickname || "Jemand",
    nameLine: report.nameLine || report.hostNickname || "Jemand",
    hostUserId: report.hostUserId || null,
    imageUrl: report.publicId
      ? `/v1/public-canvases/${encodeURIComponent(report.publicId)}/image`
      : null,
  };
}

function cleanupPublicCanvases() {
  ensurePublicDir();
  const all = publicCanvases();
  const openReportIds = new Set(
    Object.values(publicReports())
      .filter((r) => (r.status || "open") === "open")
      .map((r) => r.publicId)
  );
  const now = Date.now();
  let dirty = false;
  for (const [id, meta] of Object.entries(all)) {
    const created = Number(meta?.createdAt) || 0;
    if (created > 0 && now - created > PUBLIC_TTL_MS) {
      // Gemeldete Bilder für Admin-Review behalten
      if (openReportIds.has(id)) continue;
      deletePublicCanvasFile(meta);
      delete all[id];
      dirty = true;
    }
  }
  if (dirty) scheduleSave();
}

function listPublicCanvasEntries() {
  cleanupPublicCanvases();
  return Object.entries(publicCanvases())
    .map(([id, meta]) => ({ id, ...meta }))
    .filter((e) => e.file && Number(e.createdAt) > 0)
    .sort((a, b) => Number(b.createdAt) - Number(a.createdAt));
}

/**
 * Zufällige öffentliche Leinwand.
 * @param {Set<string>|string[]} [excludeIds] bereits gesehene IDs
 * @returns {{ entry: object, cycled: boolean }|null}
 */
function pickRandomPublicCanvas(excludeIds) {
  const list = listPublicCanvasEntries();
  if (!list.length) return null;
  const exclude = excludeIds instanceof Set
    ? excludeIds
    : new Set(
        (Array.isArray(excludeIds) ? excludeIds : [])
          .map((x) => String(x || "").trim())
          .filter(Boolean)
      );
  const fresh = exclude.size
    ? list.filter((e) => e && e.id && !exclude.has(e.id))
    : list;
  if (fresh.length) {
    return {
      entry: fresh[crypto.randomInt(0, fresh.length)],
      cycled: false,
    };
  }
  // Alle schon gesehen → Zyklus neu
  return {
    entry: list[crypto.randomInt(0, list.length)],
    cycled: true,
  };
}

function publicCanvasImageUrl(id) {
  if (!id) return "https://reineke.pro/downloads/luv/og.jpg?v=1813";
  return `https://reineke.pro/luv/v1/public-canvases/${encodeURIComponent(id)}/image`;
}

function roomMemberNicknames(room) {
  const names = [];
  const seen = new Set();
  const push = (raw) => {
    const n = String(raw || "").trim().slice(0, 18);
    if (!n) return;
    const key = n.toLowerCase();
    if (seen.has(key)) return;
    seen.add(key);
    names.push(n);
  };
  if (room?.hostNickname) push(room.hostNickname);
  if (room?.sockets) {
    for (const sock of room.sockets.values()) {
      push(sock.luvNickname);
    }
  }
  if (Array.isArray(room?.memberUserIds)) {
    const db = getDb();
    for (const uid of room.memberUserIds) {
      push(db.users?.[uid]?.nickname);
    }
  }
  return names;
}

function invitePublicBlurb(room, hostNickname) {
  const host = String(hostNickname || room?.hostNickname || "Jemand").trim() || "Jemand";
  return `${host} lädt dich zu einer gemeinsamen Leinwand ein`;
}

/** Klar: Vorschaubild stammt von einer anderen Gruppe, die veröffentlicht hat. */
function publicCanvasCredit(entry) {
  if (!entry) return "Öffentliche Community-Leinwand — von anderen in LUV geteilt";
  const who = String(entry.nameLine || splashNameLine(entry) || "").trim();
  if (who) {
    return `Öffentliche Leinwand von ${who} — von einer anderen Gruppe veröffentlicht`;
  }
  return "Öffentliche Community-Leinwand — von anderen in LUV geteilt";
}

function inviteShareDescription(hostNickname, picked) {
  const host = String(hostNickname || "Jemand").trim() || "Jemand";
  const invite = `${host} lädt dich zu LUV ein`;
  if (!picked) return invite;
  return `${invite}. Vorschaubild: ${publicCanvasCredit(picked)}`;
}

function landingShareDescription(line, picked) {
  const base = String(line || "LUV — Nähe, die mitgeht").trim();
  if (!picked) return base;
  return `${base} · Vorschaubild: ${publicCanvasCredit(picked)}`;
}

function splashNameLine(entry) {
  const host = String(entry?.hostNickname || "Jemand").trim() || "Jemand";
  const others = Array.isArray(entry?.memberNicknames)
    ? entry.memberNicknames.map((n) => String(n || "").trim()).filter(Boolean)
    : [];
  const ordered = [];
  const seen = new Set();
  const push = (n) => {
    const t = String(n || "").trim();
    if (!t) return;
    const k = t.toLowerCase();
    if (seen.has(k)) return;
    seen.add(k);
    ordered.push(t);
  };
  push(host);
  for (const n of others) push(n);
  if (ordered.length <= 1) return ordered[0] || host;
  if (ordered.length === 2) return `${ordered[0]} & ${ordered[1]}`;
  return `${ordered[0]} & ${ordered.length - 1} weiteren`;
}

function getPublicShareState(room) {
  const day = todayKey();
  if (!room.publicShare || room.publicShare.day !== day) {
    room.publicShare = { day, count: 0, nextAt: 0 };
  }
  return room.publicShare;
}

function publicShareRewardForCount(countBefore) {
  if (countBefore <= 0) {
    return { coins: 1, cooldownMs: PUBLIC_COOLDOWN_FIRST_MS };
  }
  if (countBefore === 1) {
    return { coins: 2, cooldownMs: PUBLIC_COOLDOWN_NEXT_MS };
  }
  return { coins: 3, cooldownMs: PUBLIC_COOLDOWN_NEXT_MS };
}

function rewardPublicShare(room, code, proposalId) {
  const state = getPublicShareState(room);
  const { coins, cooldownMs } = publicShareRewardForCount(state.count);
  const userIds = new Set();
  if (Array.isArray(room.memberUserIds)) {
    for (const id of room.memberUserIds) if (id) userIds.add(id);
  }
  if (room.hostUserId) userIds.add(room.hostUserId);
  for (const sock of room.sockets.values()) {
    if (sock.luvUserId) userIds.add(sock.luvUserId);
  }
  const rewarded = [];
  for (const uid of userIds) {
    const u = applyLedger(uid, coins, "public_share_reward", proposalId);
    if (u) rewarded.push(u);
  }
  state.count += 1;
  state.nextAt = Date.now() + cooldownMs;
  persistRooms();
  return { coins, cooldownMs, rewarded };
}

function publicUser(user) {
  const dailyGrantedJustNow = ensureDailyGrant(user);
  ensureStaffFields(user);
  const day = todayKey();
  const freeLeft = Math.max(0, FREE_SESSIONS_PER_DAY - (user.sessionsByDay?.[day] || 0));
  const canCreateFree = evaluateCanCreateFreeLobby(user);
  const role = user.role || "user";
  return {
    id: user.id,
    nickname: user.nickname,
    coins: user.coins,
    paidCoins: user.paidCoins || 0,
    dailyBalance: user.dailyBalance || 0,
    role,
    isStaff: role === "admin" || role === "mod",
    permissions: staffPermissions(user),
    googleEmail: user.googleEmail || null,
    banned: Boolean(user.banned),
    publicDeletedCount: Math.max(0, Number(user.publicDeletedCount) || 0),
    freeSessionsLeft: freeLeft,
    freeSessionsPerDay: FREE_SESSIONS_PER_DAY,
    dailyCoins: DAILY_COINS,
    sessionCost: SESSION_COST,
    clearCost: CLEAR_COST,
    gameCost: GAME_COST,
    lobbyCreateCost: LOBBY_CREATE_COST,
    slotCost: SLOT_COST,
    maxLobbies: MAX_LOBBIES,
    canCreateFreeLobby: canCreateFree,
    lastDailyGrantDate: user.lastDailyGrantDate || null,
    canClaimDaily: false,
    dailyGrantedJustNow: Boolean(dailyGrantedJustNow),
    googleLinked: Boolean(user.googleSub),
    pendingLootboxes: pendingLootboxPublic(user).length,
  };
}

function ensurePendingLootboxes(user) {
  if (!Array.isArray(user.pendingLootboxes)) user.pendingLootboxes = [];
  return user.pendingLootboxes;
}

function pendingLootboxPublic(user) {
  return ensurePendingLootboxes(user).map((p) => ({
    id: p.id,
    kind: p.kind,
    itemId: p.itemId,
    emoji: p.emoji,
    label: p.label,
    shopPrice: p.shopPrice,
    chancePercent: p.chancePercent,
    purchasedAt: p.purchasedAt || 0,
  }));
}

function buildLootboxPoolForUser(user) {
  ensureInventory(user);
  const pool = lootbox.buildPool({
    emojiPrices: EMOJI_SHOP_PRICES,
    themePrices: THEME_SHOP_PRICES,
    petPrices: PET_SHOP_PRICES,
    stickerPrices: STICKER_SHOP_PRICES,
    isKnown: isKnownInventoryItem,
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
  });
  const filtered = pool.filter((x) => {
    if (x.kind === "emojis" || x.kind === "stickers") return true;
    return !market.userOwnsItem(user, ensureInventory, x.kind, x.itemId);
  });
  return filtered.length ? filtered : pool;
}

function applyLedger(userId, delta, reason, refId) {
  const db = getDb();
  const user = db.users[userId];
  if (!user) return null;
  ensureCoinBuckets(user);
  const amount = Number(delta) || 0;
  if (amount > 0) {
    if (reason === "daily_grant") {
      // Nie aufaddieren — immer auf Tageswert setzen
      user.dailyBalance = DAILY_COINS;
      user.lastDailyGrantDate = todayKey();
    } else if (PAID_CREDIT_REASONS.has(reason)) {
      user.paidCoins += amount;
    } else {
      // Unbekannte Gutschriften stacken als bezahlt
      user.paidCoins += amount;
    }
  } else if (amount < 0) {
    let need = -amount;
    syncCoinTotal(user);
    if ((user.coins || 0) < need) {
      // Nie ins Minus / Teilabbuchung — Aufrufer muss ablehnen
      return null;
    }
    const fromDaily = Math.min(user.dailyBalance, need);
    user.dailyBalance -= fromDaily;
    need -= fromDaily;
    if (need > 0) {
      user.paidCoins -= need;
    }
  }
  syncCoinTotal(user);
  db.ledger.push({
    id: newId("led"),
    userId,
    delta: amount,
    reason,
    refId: refId || null,
    at: Date.now(),
    balance: user.coins,
  });
  if (db.ledger.length > 5000) db.ledger.splice(0, db.ledger.length - 5000);
  scheduleSave();
  return user;
}

/** Erfolgs-/Daily-Fortschritt — Coins über applyLedger, Cap 25/Tag. */
function trackAch(user, metric, amount = 1) {
  if (!user || !metric) return null;
  try {
    return ach.bumpMetric(user, metric, amount, todayKey(), (uid, delta, reason, ref) => {
      applyLedger(uid, delta, reason, ref);
    });
  } catch (err) {
    console.warn("trackAch", err?.message || err);
    return null;
  }
}

function syncAchInventoryMetrics(user) {
  if (!user) return;
  const ledger = (uid, d, r, ref) => applyLedger(uid, d, r, ref);
  const day = todayKey();
  const inv = ensureInventory(user);
  ach.setMetricAtLeast(user, "pets_owned", (inv.pets || []).length, day, ledger);
  ach.setMetricAtLeast(user, "friends", (ensureFriends(user).list || []).length, day, ledger);
  if ((inv.pets || []).includes("🦉")) ach.setMetricAtLeast(user, "pet_owl", 1, day, ledger);
  if ((inv.pets || []).includes("🐯")) ach.setMetricAtLeast(user, "pet_tiger", 1, day, ledger);
  if (user.googleSub) ach.setMetricAtLeast(user, "google_linked", 1, day, ledger);
  if (user.nickname && String(user.nickname).toLowerCase() !== "luv") {
    ach.setMetricAtLeast(user, "nickname_set", 1, day, ledger);
  }
}

function createSession(userId) {
  const db = getDb();
  const token = randomToken();
  db.sessions[token] = {
    userId,
    createdAt: Date.now(),
    expiresAt: Date.now() + SESSION_TTL_MS,
  };
  scheduleSave();
  return token;
}

function destroySession(token) {
  if (!token) return;
  const db = getDb();
  if (db.sessions[token]) {
    delete db.sessions[token];
    scheduleSave();
  }
}

function destroyAllSessionsForUser(userId) {
  if (!userId) return;
  const db = getDb();
  let dirty = false;
  for (const [token, session] of Object.entries(db.sessions)) {
    if (session?.userId === userId) {
      delete db.sessions[token];
      dirty = true;
    }
  }
  if (dirty) scheduleSave();
}

function isChosenNickname(nick) {
  const n = String(nick || "").trim();
  return n.length >= 2 && n.toLowerCase() !== "luv";
}

function nicknameKey(nick) {
  return String(nick || "").trim().toLowerCase();
}

/** Exakter Treffer (case-insensitive). Placeholder „Luv“ zählt nicht als einzigartig. */
function findUserByNickname(nick, exceptUserId = null) {
  const key = nicknameKey(nick);
  if (key.length < 2 || key === "luv") return null;
  return (
    Object.values(getDb().users || {}).find(
      (u) =>
        u &&
        u.id !== exceptUserId &&
        nicknameKey(u.nickname) === key
    ) || null
  );
}

function assertNicknameAvailable(nick, exceptUserId, res) {
  if (!isChosenNickname(nick)) {
    res.status(400).json({
      error: "bad_nick",
      message: "Spitzname zu kurz oder ungültig.",
    });
    return false;
  }
  if (findUserByNickname(nick, exceptUserId)) {
    res.status(409).json({
      error: "nickname_taken",
      message: "Dieser Spitzname ist schon vergeben.",
    });
    return false;
  }
  return true;
}

/**
 * Quelle in Ziel zusammenführen (Coins, Lobbys, Google, Admin).
 * Danach wird die Quelle gelöscht — ohne deren Lobbys zu zerstören.
 */
function absorbUserInto(target, source) {
  if (!target?.id || !source?.id || target.id === source.id) return target;
  const db = getDb();
  ensureCoinBuckets(target);
  ensureCoinBuckets(source);
  target.paidCoins = (target.paidCoins || 0) + (source.paidCoins || 0);
  target.dailyBalance = Math.max(target.dailyBalance || 0, source.dailyBalance || 0);
  syncCoinTotal(target);

  if (!isChosenNickname(target.nickname) && isChosenNickname(source.nickname)) {
    target.nickname = source.nickname;
  }
  if (source.role === "admin") target.role = "admin";

  ensureHostedRooms(target);
  ensureHostedRooms(source);
  for (const [code, meta] of Object.entries(source.hostedRooms || {})) {
    if (!target.hostedRooms[code]) {
      target.hostedRooms[code] = { ...meta };
    } else {
      const a = Number(target.hostedRooms[code].capacity) || 0;
      const b = Number(meta?.capacity) || 0;
      target.hostedRooms[code].capacity = Math.max(a, b);
    }
    const live = rooms.get(code);
    if (live && live.hostUserId === source.id) {
      live.hostUserId = target.id;
      live.hostNickname = target.nickname || live.hostNickname;
      touchRoom(live);
    }
    if (db.rooms?.[code] && db.rooms[code].hostUserId === source.id) {
      db.rooms[code].hostUserId = target.id;
      db.rooms[code].hostNickname = target.nickname || db.rooms[code].hostNickname;
    }
  }
  source.hostedRooms = {};

  if (source.googleSub && !target.googleSub) {
    target.googleSub = source.googleSub;
    target.googleEmail = source.googleEmail || null;
  }
  source.googleSub = null;
  source.googleEmail = null;

  // Profil / Inventar / Freunde / Settings / Joins zusammenführen
  if (profileRichness(source.profileCanvas) > profileRichness(target.profileCanvas)) {
    target.profileCanvas = source.profileCanvas;
  } else if (!target.profileCanvas && source.profileCanvas) {
    target.profileCanvas = source.profileCanvas;
  }
  mergeInventory(target, source);
  mergeFriends(target, source);
  mergeSettings(target, source);
  ensureJoinedRooms(target);
  ensureJoinedRooms(source);
  for (const [code, meta] of Object.entries(source.joinedRooms || {})) {
    if (!target.joinedRooms[code]) target.joinedRooms[code] = { ...meta };
  }
  source.joinedRooms = {};

  destroyAllSessionsForUser(source.id);
  if (Array.isArray(db.ledger)) {
    for (const e of db.ledger) {
      if (e?.userId === source.id) e.userId = target.id;
    }
  }
  delete db.users[source.id];
  persistRooms();
  scheduleSave();
  console.log(`merged user ${source.id} into ${target.id}`);
  return target;
}

/** Konto + Google-Link + Hosted Rooms endgültig entfernen. */
function deleteUserAccount(user) {
  if (!user?.id) return;
  const db = getDb();
  const userId = user.id;
  ensureHostedRooms(user);
  ensureJoinedRooms(user);
  const hostedCodes = Object.keys(user.hostedRooms || {});
  for (const code of hostedCodes) {
    const room = rooms.get(code);
    if (room && room.hostUserId === userId) {
      for (const sock of room.sockets.values()) {
        try {
          sock.close(4000, "account_deleted");
        } catch {
          /* ignore */
        }
      }
      rooms.delete(code);
    }
    if (db.rooms?.[code]) {
      delete db.rooms[code];
    }
    delete user.hostedRooms[code];
  }
  for (const code of Object.keys(user.joinedRooms || {})) {
    const room = rooms.get(code) || null;
    if (room) forgetMember(room, userId);
    delete user.joinedRooms[code];
  }
  // Aus Freundeslisten anderer Nutzer streichen
  for (const other of Object.values(db.users || {})) {
    if (!other?.id || other.id === userId) continue;
    ensureFriends(other);
    const f = other.friends;
    const before =
      f.list.length + f.incoming.length + f.outgoing.length + (f.petKraulTargets?.length || 0);
    f.list = f.list.filter((id) => id !== userId);
    f.incoming = f.incoming.filter((id) => id !== userId);
    f.outgoing = f.outgoing.filter((id) => id !== userId);
    if (Array.isArray(f.petKraulTargets)) {
      f.petKraulTargets = f.petKraulTargets.filter((id) => id !== userId);
    }
    const after =
      f.list.length + f.incoming.length + f.outgoing.length + (f.petKraulTargets?.length || 0);
    if (after !== before) scheduleSave();
  }
  destroyAllSessionsForUser(userId);
  if (Array.isArray(db.ledger)) {
    db.ledger = db.ledger.filter((e) => e?.userId !== userId);
  }
  delete db.users[userId];
  persistRooms();
  scheduleSave();
}

function findUserByGoogleSub(sub) {
  if (!sub) return null;
  return Object.values(getDb().users).find((u) => u.googleSub === sub) || null;
}

async function verifyGoogleIdToken(idToken) {
  if (!GOOGLE_CLIENT_ID || !idToken) return null;
  try {
    const url =
      "https://oauth2.googleapis.com/tokeninfo?id_token=" +
      encodeURIComponent(idToken);
    const res = await fetch(url);
    if (!res.ok) return null;
    const payload = await res.json();
    if (payload.aud !== GOOGLE_CLIENT_ID) return null;
    const iss = String(payload.iss || "");
    if (iss !== "accounts.google.com" && iss !== "https://accounts.google.com") {
      return null;
    }
    if (Number(payload.exp) * 1000 < Date.now()) return null;
    const sub = String(payload.sub || "");
    if (!sub) return null;
    return {
      sub,
      email: String(payload.email || "").slice(0, 120) || null,
      name: String(payload.name || "").trim().slice(0, 18) || null,
    };
  } catch {
    return null;
  }
}

function publicHostedLobbies(user) {
  reconcileAbandonedLobbyOwnership(user);
  ensureHostedRooms(user);
  return Object.entries(user.hostedRooms).map(([code, meta]) => ({
    code,
    name: String(meta?.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    token: meta?.token || null,
    capacity: Math.min(
      MAX_PEERS,
      Math.max(
        1,
        Number(meta?.capacity) || defaultLobbyCapacity(Boolean(meta?.isFree))
      )
    ),
    isFree: Boolean(meta?.isFree),
    isRandom: Boolean(meta?.isRandom),
    lastCanvasAt: Number(
      rooms.get(code)?.lastCanvasAt ||
        meta?.lastCanvasAt ||
        getDb().rooms?.[code]?.lastCanvasAt ||
        0
    ),
    hostColorSide: normalizeHostColorSide(meta?.hostColorSide),
    invite: `${PUBLIC_JOIN_BASE}/${code}`,
    hostNickname: user.nickname || "Host",
  }));
}

function authUser(req) {
  const header = String(req.headers.authorization || "");
  const token = header.startsWith("Bearer ")
    ? header.slice(7).trim()
    : String(req.headers["x-luv-session"] || "").trim();
  if (!token) return null;
  const db = getDb();
  const session = db.sessions[token];
  if (!session || session.expiresAt < Date.now()) return null;
  const user = db.users[session.userId];
  if (!user) return null;
  return { user, token };
}

function requireAuth(req, res) {
  const ctx = authUser(req);
  if (!ctx) {
    res.status(401).json({ error: "unauthorized" });
    return null;
  }
  if (ctx.user.banned) {
    res.status(403).json({
      error: "banned",
      message: "Dieses Konto ist gesperrt.",
    });
    return null;
  }
  return ctx;
}

function clientIp(req) {
  const xf = String(req.headers["x-forwarded-for"] || "")
    .split(",")[0]
    .trim();
  const raw = xf || req.socket?.remoteAddress || "";
  return String(raw).replace(/^::ffff:/, "").slice(0, 64);
}

function ensureIntroDb(db) {
  if (!db.introOffersByIp || typeof db.introOffersByIp !== "object") {
    db.introOffersByIp = {};
  }
}

function introOfferActive() {
  return Date.now() < INTRO_EXPIRES_AT;
}

function canClaimIntroOffer(user, ip) {
  if (!introOfferActive()) return false;
  if (user?.introOfferUsed) return false;
  const db = getDb();
  ensureIntroDb(db);
  if (ip && db.introOffersByIp[ip]) return false;
  return true;
}

function packPurchaseStats() {
  const stats = ensureShopStats(getDb());
  if (!stats.packs || typeof stats.packs !== "object") stats.packs = {};
  return stats.packs;
}

function bumpPackPurchase(packId, qty = 1) {
  const id = String(packId || "").trim();
  if (!id || !PACKS[id]) return;
  const packs = packPurchaseStats();
  const n = Math.max(1, Math.floor(Number(qty) || 1));
  packs[id] = Math.max(0, Number(packs[id]) || 0) + n;
}

/** Meistgekauftes Normal-Paket (ohne Aktions-/Once-Angebote). Fallback: pack_400 (14,99). */
function mostPurchasedNormalPackId() {
  const packs = packPurchaseStats();
  let bestId = "pack_400";
  let best = Number(packs[bestId]) || 0;
  for (const pack of Object.values(PACKS)) {
    if (pack.oncePerUserAndIp) continue;
    if (pack.id === bestId) continue;
    const n = Number(packs[pack.id]) || 0;
    if (n > best) {
      best = n;
      bestId = pack.id;
    }
  }
  return bestId;
}

function publicPack(pack, { mostPurchasedId } = {}) {
  const out = {
    id: pack.id,
    label: pack.label,
    coins: pack.coins,
    amountEur: pack.amountEur,
    onceOnly: Boolean(pack.oncePerUserAndIp),
    isOffer: Boolean(pack.oncePerUserAndIp || pack.compareAtEur),
    mostPurchased: mostPurchasedId ? pack.id === mostPurchasedId : false,
  };
  if (pack.compareAtEur) out.compareAtEur = pack.compareAtEur;
  return out;
}

function listShopPacks(user, ip) {
  const mostPurchasedId = mostPurchasedNormalPackId();
  const packs = [];
  for (const pack of Object.values(PACKS)) {
    if (pack.expiresAt && Date.now() >= pack.expiresAt) continue;
    if (pack.oncePerUserAndIp && !canClaimIntroOffer(user, ip)) continue;
    packs.push(publicPack(pack, { mostPurchasedId }));
  }
  // Angebote zuerst, dann nach Preis aufsteigend
  packs.sort((a, b) => {
    const ao = a.isOffer ? 0 : 1;
    const bo = b.isOffer ? 0 : 1;
    if (ao !== bo) return ao - bo;
    return Number(a.amountEur) - Number(b.amountEur);
  });
  return packs;
}

/** Globale Itemshop-Kaufzähler (für Markt-Hub „meistgekauft“). */
function ensureShopStats(db) {
  if (!db.shopStats || typeof db.shopStats !== "object") db.shopStats = {};
  return db.shopStats;
}

function bumpShopPurchase(kind, itemId) {
  const id = String(itemId || "").trim();
  const k = String(kind || "").trim();
  if (!k || !id) return;
  const stats = ensureShopStats(getDb());
  const key = `${k}:${id}`;
  stats[key] = Math.min(1_000_000, (Number(stats[key]) || 0) + 1);
}

function shopItemPrice(kind, itemId) {
  if (kind === "emojis") return Number(EMOJI_SHOP_PRICES[itemId]) || 0;
  if (kind === "stickers") return Number(STICKER_SHOP_PRICES[itemId]) || 0;
  if (kind === "pets") return Number(PET_SHOP_PRICES[itemId]) || 0;
  if (kind === "themes") return Number(THEME_SHOP_PRICES[itemId]) || 0;
  return 0;
}

/** Einmalig Ledger → Stats (ältere Käufe zählen auch). */
function backfillShopStatsFromLedger(db) {
  if (db.shopStatsBackfilled) return;
  const stats = ensureShopStats(db);
  const reasonKind = {
    buy_emoji: "emojis",
    buy_sticker: "stickers",
    buy_pet: "pets",
    buy_theme: "themes",
  };
  if (Array.isArray(db.ledger)) {
    for (const e of db.ledger) {
      const kind = reasonKind[e?.reason];
      const itemId = String(e?.ref || "").trim();
      if (!kind || !itemId) continue;
      const key = `${kind}:${itemId}`;
      stats[key] = (Number(stats[key]) || 0) + 1;
    }
  }
  db.shopStatsBackfilled = true;
}

function topShopPurchases(limit = 2) {
  const db = getDb();
  backfillShopStatsFromLedger(db);
  const stats = ensureShopStats(db);
  const ranked = Object.entries(stats)
    .map(([key, n]) => {
      const i = key.indexOf(":");
      if (i < 1) return null;
      const kind = key.slice(0, i);
      const itemId = key.slice(i + 1);
      if (!["emojis", "stickers", "pets", "themes"].includes(kind)) return null;
      if (!isKnownInventoryItem(kind, itemId)) return null;
      const meta = marketItemMeta(kind, itemId);
      if (!meta) return null;
      return {
        kind,
        itemId,
        emoji: meta.emoji || itemId,
        label: meta.label || itemId,
        priceCoins: shopItemPrice(kind, itemId),
        bought: Number(n) || 0,
      };
    })
    .filter(Boolean)
    .sort((a, b) => b.bought - a.bought || a.label.localeCompare(b.label, "de"));

  const out = ranked.slice(0, limit);
  // Fallback wenn noch niemand gekauft hat
  const fallbacks = [
    { kind: "stickers", itemId: "🦋", emoji: "🦋", label: "🦋", priceCoins: 8, bought: 0 },
    { kind: "pets", itemId: "🦊", emoji: "🦊", label: "🦊", priceCoins: 26, bought: 0 },
    { kind: "emojis", itemId: "🥰", emoji: "🥰", label: "🥰", priceCoins: 12, bought: 0 },
  ];
  for (const f of fallbacks) {
    if (out.length >= limit) break;
    if (out.some((x) => x.kind === f.kind && x.itemId === f.itemId)) continue;
    out.push(f);
  }
  return out.slice(0, limit);
}

function newestMarketListings(limit = 2) {
  const db = getDb();
  const listings = market.ensureMarket(db);
  const now = Date.now();
  return Object.values(listings)
    .filter((e) => {
      if (!e || e.status !== "open") return false;
      if (e.private) return false;
      if (e.expiresAt && e.expiresAt < now) return false;
      return true;
    })
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    .slice(0, limit)
    .map((e) => ({
      id: e.id,
      kind: e.kind,
      itemId: e.itemId,
      emoji: e.emoji,
      label: e.label,
      priceCoins: Number(e.priceCoins) || 0,
      allowTrade: Boolean(e.allowTrade),
      sellerNickname: e.sellerNickname || "",
      createdAt: e.createdAt || 0,
    }));
}

function applySuperAdmin(user) {
  if (!user) return user;
  const email = String(user.googleEmail || "")
    .trim()
    .toLowerCase();
  if (email && SUPER_ADMIN_EMAILS.has(email)) {
    user.role = "admin";
  }
  return user;
}

function ensureStaffFields(user) {
  if (!user) return user;
  applySuperAdmin(user);
  if (user.role === "mod") {
    if (!user.modPermissions || typeof user.modPermissions !== "object") {
      user.modPermissions = { ...DEFAULT_MOD_PERMISSIONS };
    }
  } else if (user.role !== "admin") {
    user.modPermissions = {};
  }
  return user;
}

function staffPermissions(user) {
  ensureStaffFields(user);
  if (user.role === "admin") {
    const all = {};
    for (const id of ALL_MOD_PERMISSION_IDS) all[id] = true;
    return all;
  }
  if (user.role === "mod") {
    const out = {};
    for (const id of ALL_MOD_PERMISSION_IDS) {
      out[id] = Boolean(user.modPermissions?.[id]);
    }
    return out;
  }
  return {};
}

function isStaff(user) {
  ensureStaffFields(user);
  return user.role === "admin" || user.role === "mod";
}

function hasStaffPerm(user, perm) {
  if (!perm) return isStaff(user);
  ensureStaffFields(user);
  if (user.role === "admin") return true;
  if (user.role !== "mod") return false;
  return Boolean(user.modPermissions?.[perm]);
}

function requireAdmin(req, res) {
  const ctx = requireAuth(req, res);
  if (!ctx) return null;
  ensureStaffFields(ctx.user);
  if (ctx.user.role !== "admin") {
    res.status(403).json({ error: "forbidden", message: "Nur für Admins." });
    return null;
  }
  return ctx;
}

function requireStaff(req, res, perm) {
  const ctx = requireAuth(req, res);
  if (!ctx) return null;
  ensureStaffFields(ctx.user);
  if (!isStaff(ctx.user)) {
    res.status(403).json({ error: "forbidden", message: "Kein Staff-Zugang." });
    return null;
  }
  if (perm && !hasStaffPerm(ctx.user, perm)) {
    res.status(403).json({
      error: "forbidden",
      message: "Keine Berechtigung für diese Aktion.",
    });
    return null;
  }
  return ctx;
}

function staffUserCard(user) {
  if (!user) return null;
  ensureStaffFields(user);
  return {
    userId: user.id,
    nickname: String(user.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
    email: user.googleEmail || null,
    role: user.role || "user",
    coins: user.coins || 0,
    banned: Boolean(user.banned),
    googleLinked: Boolean(user.googleSub),
    createdAt: user.createdAt || null,
    publicDeletedCount: Math.max(0, Number(user.publicDeletedCount) || 0),
    petEmoji: userPetEmoji(user),
    permissions: user.role === "mod" ? staffPermissions(user) : undefined,
    modSince: user.modSince || null,
  };
}

/** Live-Hinweise bleiben für Nachzügler sichtbar (nicht nur „gerade online“). */
const LIVE_NOTICE_TTL_MS = 24 * 60 * 60 * 1000;

function staffDisplayName(user) {
  const nick = String(user?.nickname || "")
    .trim()
    .slice(0, 18);
  // Platzhalter-Spitzname „Luv“ nicht als Absender nutzen
  if (nick && nick.toLowerCase() !== "luv") return nick;
  const emailLocal = String(user?.googleEmail || "")
    .split("@")[0]
    .trim()
    .slice(0, 18);
  if (emailLocal) return emailLocal;
  return nick || "Team";
}

function getLiveNotice() {
  const db = getDb();
  const n = db.liveNotice;
  if (!n || typeof n !== "object" || !n.message) return null;
  if ((Number(n.expiresAt) || 0) < Date.now()) {
    db.liveNotice = null;
    scheduleSave();
    return null;
  }
  let authorNickname = String(n.authorNickname || "").trim().slice(0, 18);
  if (!authorNickname || authorNickname.toLowerCase() === "luv") {
    const author = n.authorId ? db.users?.[n.authorId] : null;
    authorNickname = author ? staffDisplayName(author) : "Team";
  }
  return {
    id: n.id,
    message: String(n.message).slice(0, 160),
    authorNickname,
    authorId: n.authorId || null,
    createdAt: n.createdAt || Date.now(),
    expiresAt: n.expiresAt,
  };
}

function broadcastLiveNotice(notice) {
  if (!notice) return;
  const payload = JSON.stringify({
    type: "live_notice",
    id: notice.id,
    message: notice.message,
    authorNickname: notice.authorNickname,
    createdAt: notice.createdAt,
  });
  for (const room of rooms.values()) {
    for (const sock of room.sockets.values()) {
      try {
        if (sock.readyState === 1) sock.send(payload);
      } catch {
        /* ignore */
      }
    }
  }
}

function userFromSessionToken(sessionToken) {
  if (!sessionToken) return null;
  const db = getDb();
  const session = db.sessions[sessionToken];
  if (!session || session.expiresAt < Date.now()) return null;
  return db.users[session.userId] || null;
}

/** First draw in a lobby today: free slot or 1 coin. */
function consumeDrawSession(user, lobbyCode) {
  const day = todayKey();
  if (!user.drawLocks) user.drawLocks = {};
  if (!user.sessionsByDay) user.sessionsByDay = {};
  const key = `${day}:${lobbyCode}`;
  if (user.drawLocks[key]) {
    return { ok: true, charged: false, already: true };
  }
  const used = user.sessionsByDay[day] || 0;
  if (used < FREE_SESSIONS_PER_DAY) {
    user.sessionsByDay[day] = used + 1;
    user.drawLocks[key] = "free";
    scheduleSave();
    return { ok: true, charged: false, free: true };
  }
  ensureDailyGrant(user);
  if ((user.coins || 0) < SESSION_COST) {
    return { ok: false, error: "no_coins" };
  }
  applyLedger(user.id, -SESSION_COST, "session", key);
  user.drawLocks[key] = "paid";
  scheduleSave();
  return { ok: true, charged: true, free: false };
}

/** WS-Send ohne den Relay-Loop bei einem toten Socket abzubrechen. */
function safeSend(sock, data, room = null, peerId = null) {
  if (!sock || sock.readyState !== 1) {
    if (room && peerId && room.sockets.get(peerId) === sock) {
      room.sockets.delete(peerId);
    }
    return false;
  }
  try {
    sock.send(data);
    return true;
  } catch (err) {
    console.warn("ws safeSend failed:", err?.message || err);
    try {
      sock.close();
    } catch {
      /* ignore */
    }
    if (room && peerId && room.sockets.get(peerId) === sock) {
      room.sockets.delete(peerId);
    }
    return false;
  }
}

/** Tote / halb-offene Sockets aus der Map werfen — verhindert „B sieht, C nicht“. */
function pruneDeadSockets(room) {
  if (!room?.sockets) return;
  for (const [id, sock] of [...room.sockets.entries()]) {
    if (!sock || sock.readyState !== 1) {
      room.sockets.delete(id);
    }
  }
}

/** An alle Peers außer Sender — jeder Send isoliert. */
function relayToPeers(room, payload, exceptPeerId = null) {
  if (!room) return;
  pruneDeadSockets(room);
  const text = typeof payload === "string" ? payload : JSON.stringify(payload);
  for (const [id, peer] of room.sockets.entries()) {
    if (exceptPeerId && id === exceptPeerId) continue;
    safeSend(peer, text, room, id);
  }
}

function broadcastRoom(room, payload, exceptSocket = null) {
  pruneDeadSockets(room);
  const text = typeof payload === "string" ? payload : JSON.stringify(payload);
  for (const [id, sock] of room.sockets.entries()) {
    if (exceptSocket && sock === exceptSocket) continue;
    safeSend(sock, text, room, id);
  }
}

/**
 * Jede verbundene Person genau einmal — immer mit Anzeigenamen.
 * Früher: leere Nicknames wurden übersprungen → fehlten in Kachel/Avataren.
 */
function roomConnectedMembers(room) {
  const out = [];
  const seenUsers = new Set();
  for (const [peerId, sock] of room.sockets.entries()) {
    const userId = sock.luvUserId || null;
    if (userId) {
      if (seenUsers.has(userId)) continue;
      seenUsers.add(userId);
    }
    let nick = String(sock.luvNickname || "").trim().slice(0, 18);
    if (!nick && userId) {
      const u = getDb().users[userId];
      nick = String(u?.nickname || "").trim().slice(0, 18);
      if (nick) sock.luvNickname = nick;
    }
    if (!nick) nick = "Jemand";
    const colorIndex = Number.isFinite(Number(sock.luvColorIndex))
      ? Math.max(0, Math.floor(Number(sock.luvColorIndex)))
      : 0;
    const u = userId ? getDb().users?.[userId] : null;
    // Mehrere Geräte desselben Kontos: aktiv, wenn irgendein Socket auf der Leinwand ist
    let active = Boolean(sock.luvCanvasActive);
    if (userId) {
      for (const other of room.sockets.values()) {
        if (other.luvUserId === userId && other.luvCanvasActive) {
          active = true;
          break;
        }
      }
    }
    out.push({
      userId: userId || peerId,
      nickname: nick,
      colorIndex,
      active,
      petEmoji: u ? userPetEmoji(u) : DEFAULT_PET,
    });
  }
  return out;
}

function roomConnectedNicknames(room) {
  return roomConnectedMembers(room).map((m) => m.nickname);
}

/** Eindeutige verbundene Personen (pro userId ein Slot; anonyme Sockets einzeln). */
function uniqueConnectedCount(room) {
  return roomConnectedMembers(room).length;
}

/**
 * Ein Konto, eine Leinwand: nur Geräte die wirklich auf der Leinwand sind
 * (luvCanvasActive) bekommen canvas_taken — Lobby-WS / Hintergrund bleibt still.
 */
function kickOtherCanvasSockets(room, userId, exceptSocket) {
  if (!userId || !room) return 0;
  let n = 0;
  const payload = JSON.stringify({
    type: "canvas_taken",
    message: "Ein anderes Gerät hat die Leinwand betreten.",
  });
  for (const sock of room.sockets.values()) {
    if (sock === exceptSocket) continue;
    if (sock.luvUserId !== userId) continue;
    // Nur wer die Leinwand offen hat — nicht jedes Gerät mit Lobby-Verbindung
    if (!sock.luvCanvasActive) continue;
    sock.luvCanvasActive = false;
    if (sock.readyState === 1) {
      try {
        sock.send(payload);
      } catch {
        /* ignore */
      }
    }
    n += 1;
    const presenceOff = JSON.stringify({
      type: "presence",
      active: false,
      userId,
      peerKey: userId,
      nickname: sock.luvNickname || "Jemand",
      colorIndex: Number.isFinite(Number(sock.luvColorIndex)) ? sock.luvColorIndex : 0,
    });
    for (const peer of room.sockets.values()) {
      if (peer === sock || peer === exceptSocket) continue;
      if (peer.readyState === 1) {
        try {
          peer.send(presenceOff);
        } catch {
          /* ignore */
        }
      }
    }
  }
  return n;
}

/** Verzögerte Auflösung — kurze Offline/Reconnect-Fenster dürfen Lobby nicht killen. */
const EMPTY_FREE_LOBBY_GRACE_MS = 90_000;
const emptyFreeLobbyTimers = new Map();
/** Host nicht bei jedem MASK-/Reconnect-Drop sofort umhängen (1.8.80-Regression). */
const HOST_FAILOVER_GRACE_MS = 60_000;
const hostFailoverTimers = new Map();

function cancelEmptyFreeLobbyTimer(code) {
  const t = emptyFreeLobbyTimers.get(code);
  if (t) {
    clearTimeout(t);
    emptyFreeLobbyTimers.delete(code);
  }
}

function cancelHostFailoverTimer(code) {
  const t = hostFailoverTimers.get(code);
  if (t) {
    clearTimeout(t);
    hostFailoverTimers.delete(code);
  }
}

/** Leere Lobby (gratis oder bezahlt) nach bewusstem Leave / ohne Mitglieder auflösen. */
function dissolveEmptyLobby(room, code) {
  if (!room) return false;
  if ((room.sockets?.size || 0) > 0) return false;
  cancelEmptyFreeLobbyTimer(code);
  cancelHostFailoverTimer(code);
  const hostId = room.hostUserId;
  const memberIds = Array.isArray(room.memberUserIds) ? [...room.memberUserIds] : [];
  const wasFree = Boolean(room.isFree);
  rooms.delete(code);
  if (getDb().rooms?.[code]) {
    delete getDb().rooms[code];
  }
  if (hostId) {
    releaseHostedRoom(code, hostId);
    const host = getDb().users[hostId];
    if (host && wasFree && host.freeLobbyCreateDay === todayKey()) {
      const stillFree = Object.entries(host.hostedRooms || {}).some(
        ([c, m]) => m?.isFree && c !== code && roomExistsAnywhere(c)
      );
      if (!stillFree) host.freeLobbyCreateDay = null;
    }
  }
  for (const uid of memberIds) {
    const u = getDb().users?.[uid];
    if (u) forgetJoinedLobby(u, code);
  }
  persistRooms();
  scheduleSave();
  console.log(`dissolved empty lobby ${code} free=${wasFree}`);
  return true;
}

/**
 * Admin/Staff: Lobby sofort auflösen — alle raus, Ownership weg, RAM+Disk weg.
 */
function forceDissolveLobby(codeRaw) {
  const code = String(codeRaw || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  if (!code) return { ok: false, reason: "bad_code" };
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && typeof saved === "object") {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  cancelEmptyFreeLobbyTimer(code);
  cancelHostFailoverTimer(code);
  if (room) {
    for (const sock of [...room.sockets.values()]) {
      try {
        sock.close(4000, "room_closed");
      } catch {
        /* ignore */
      }
    }
    room.sockets.clear();
    const memberIds = Array.isArray(room.memberUserIds) ? [...room.memberUserIds] : [];
    const hostId = room.hostUserId;
    rooms.delete(code);
    if (getDb().rooms?.[code]) delete getDb().rooms[code];
    if (hostId) releaseHostedRoom(code, hostId);
    for (const uid of memberIds) {
      const u = getDb().users?.[uid];
      if (u) {
        forgetJoinedLobby(u, code);
        releaseHostedRoom(code, uid);
      }
    }
  } else {
    // Nur Ownership bereinigen (Geister-Eintrag)
    for (const u of Object.values(getDb().users || {})) {
      if (!u) continue;
      ensureHostedRooms(u);
      ensureJoinedRooms(u);
      if (u.hostedRooms?.[code]) delete u.hostedRooms[code];
      if (u.joinedRooms?.[code]) delete u.joinedRooms[code];
    }
    if (getDb().rooms?.[code]) delete getDb().rooms[code];
  }
  persistRooms();
  scheduleSave();
  console.log(`force-dissolved lobby ${code}`);
  return { ok: true, code };
}

function staffLobbyCard(code, meta, hostUser) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const live = rooms.get(clean);
  const saved = !live ? getDb().rooms?.[clean] : null;
  const src = live || saved || meta || {};
  const memberIds = Array.isArray(src.memberUserIds) ? src.memberUserIds : [];
  const members = memberIds.map((id) => {
    const u = getDb().users?.[id];
    return {
      userId: id,
      nickname: String(u?.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
      online: Boolean(live && [...(live.sockets?.values() || [])].some((s) => s.luvUserId === id)),
    };
  });
  const online = live
    ? [...live.sockets.values()].filter((s) => s.luvUserId).length
    : 0;
  const capacity = Math.min(
    MAX_PEERS,
    Math.max(
      1,
      Number(src.capacity) ||
        Number(meta?.capacity) ||
        defaultLobbyCapacity(Boolean(src.isFree ?? meta?.isFree))
    )
  );
  return {
    code: clean,
    name: String(src.name || meta?.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    capacity,
    isFree: Boolean(src.isFree ?? meta?.isFree),
    online,
    memberCount: members.length || online,
    members,
    active: Boolean(live) || Boolean(saved),
    live: Boolean(live),
    createdAt: Number(meta?.createdAt || src.createdAt) || null,
    lastActiveAt: Number(src.lastActiveAt) || null,
    invite: `${PUBLIC_JOIN_BASE}/${clean}`,
    hostUserId: hostUser?.id || src.hostUserId || null,
    hostNickname:
      String(hostUser?.nickname || src.hostNickname || "Host").trim().slice(0, 18) || "Host",
  };
}

function dissolveEmptyFreeLobby(room, code) {
  if (!room?.isFree) return false;
  return dissolveEmptyLobby(room, code);
}

/** Host-Nachfolger auch offline (Mitgliedsliste), z. B. nach Leave des Hosts. */
function promoteNextHostFromMembers(room, code) {
  if (!room || !rooms.has(code)) return false;
  if (promoteNextHost(room, code)) return true;
  const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
  const nextId = members.find((id) => id && id !== room.hostUserId) || null;
  if (!nextId) return false;
  const prevHost = room.hostUserId;
  if (prevHost) releaseHostedRoom(code, prevHost);
  room.hostUserId = nextId;
  const newHost = getDb().users[nextId];
  if (newHost) {
    room.hostNickname = String(newHost.nickname || room.hostNickname || "Host").slice(0, 18);
    ensureHostedRooms(newHost);
    newHost.hostedRooms[code] = {
      createdAt: Date.now(),
      isFree: Boolean(room.isFree),
      name: room.name,
      capacity: roomCapacity(room),
      token: room.token,
      hostColorSide: room.hostColorSide,
    };
    forgetJoinedLobby(newHost, code);
    scheduleSave();
  }
  touchRoom(room);
  persistRooms();
  console.log(
    `host failover (offline) code=${code} -> ${room.hostNickname || "?"} (${room.hostUserId})`
  );
  return true;
}

function scheduleEmptyFreeLobbyDissolve(room, code) {
  if (!room?.isFree) return;
  if ((room.sockets?.size || 0) > 0) {
    cancelEmptyFreeLobbyTimer(code);
    return;
  }
  if (emptyFreeLobbyTimers.has(code)) return;
  const timer = setTimeout(() => {
    emptyFreeLobbyTimers.delete(code);
    const live = rooms.get(code);
    if (!live || live !== room) return;
    if ((live.sockets?.size || 0) > 0) return;
    dissolveEmptyFreeLobby(live, code);
  }, EMPTY_FREE_LOBBY_GRACE_MS);
  emptyFreeLobbyTimers.set(code, timer);
}

function promoteNextHost(room, code) {
  if (!room || !rooms.has(code)) return false;
  let next = null;
  for (const sock of room.sockets.values()) {
    if (sock.luvUserId) {
      next = sock;
      break;
    }
  }
  if (!next) return false;

  const prevHost = room.hostUserId;
  if (prevHost) releaseHostedRoom(code, prevHost);
  room.hostUserId = next.luvUserId;
  if (next.luvNickname) room.hostNickname = next.luvNickname;
  const newHost = getDb().users[next.luvUserId];
  if (newHost) {
    ensureHostedRooms(newHost);
    newHost.hostedRooms[code] = {
      createdAt: Date.now(),
      isFree: Boolean(room.isFree),
      name: room.name,
      capacity: roomCapacity(room),
      token: room.token,
      hostColorSide: room.hostColorSide,
    };
    scheduleSave();
  }
  broadcastRoom(room, {
    type: "host_changed",
    hostNickname: room.hostNickname || "Host",
    hostUserId: room.hostUserId,
  });
  touchRoom(room);
  persistRooms();
  console.log(
    `host failover code=${code} -> ${room.hostNickname || "?"} (${room.hostUserId})`
  );
  return true;
}

function scheduleHostFailover(room, code) {
  if (hostFailoverTimers.has(code)) return;
  const timer = setTimeout(() => {
    hostFailoverTimers.delete(code);
    const live = rooms.get(code);
    if (!live || live !== room) return;
    if ((live.sockets?.size || 0) === 0) return;
    const hostHere = [...live.sockets.values()].some(
      (s) => s.luvUserId && s.luvUserId === live.hostUserId
    );
    if (hostHere) return;
    if (!promoteNextHost(live, code)) {
      scheduleEmptyFreeLobbyDissolve(live, code);
    }
  }, HOST_FAILOVER_GRACE_MS);
  hostFailoverTimers.set(code, timer);
}

/**
 * Host weg (Leave oder Disconnect) → nächsten User zum Host machen oder Lobby auflösen.
 * @param immediateEmpty true nur bei bewusstem Leave-API — sonst Grace für Reconnect.
 * Bewusstes Leave: auch bezahlte leere Lobbys weg (sonst bringt Cloud-Sync sie zurück).
 */
function ensureHostOrDissolve(room, code, { immediateEmpty = false } = {}) {
  if (!room) return;
  if ((room.sockets?.size || 0) === 0) {
    cancelHostFailoverTimer(code);
    if (immediateEmpty) {
      if (!promoteNextHostFromMembers(room, code)) {
        dissolveEmptyLobby(room, code);
      }
    } else {
      scheduleEmptyFreeLobbyDissolve(room, code);
    }
    return;
  }
  cancelEmptyFreeLobbyTimer(code);
  const hostHere = [...room.sockets.values()].some(
    (s) => s.luvUserId && s.luvUserId === room.hostUserId
  );
  if (hostHere) {
    cancelHostFailoverTimer(code);
    return;
  }

  // Kurzer Drop (MASK/Reconnect): Host NICHT sofort umhängen — sonst Split-Lobby
  if (!immediateEmpty) {
    scheduleHostFailover(room, code);
    return;
  }

  cancelHostFailoverTimer(code);
  if (!promoteNextHost(room, code) && !promoteNextHostFromMembers(room, code)) {
    dissolveEmptyLobby(room, code);
  }
}

function broadcastPeerCount(room) {
  healRoomMembership(room);
  const memberList = roomRosterAll(room);
  const online = memberList.filter((m) => m.online).length;
  broadcastRoom(room, {
    type: "peers",
    count: online,
    peers: online,
    capacity: roomCapacity(room),
    maxPeers: MAX_PEERS,
    members: memberList.map((m) => m.nickname),
    memberList,
  });
}

function refundClearCharge(proposal) {
  if (!proposal?.charged || !proposal.chargedUserId) return null;
  const user = applyLedger(proposal.chargedUserId, CLEAR_COST, "clear_refund", proposal.id);
  proposal.charged = false;
  return user;
}

function normalizeGuess(text) {
  return String(text || "")
    .toLowerCase()
    .replace(/ä/g, "ae")
    .replace(/ö/g, "oe")
    .replace(/ü/g, "ue")
    .replace(/ß/g, "ss")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

function pickWordOptions(count = 3) {
  const pool = WORDS_DE;
  const picks = new Set();
  let guard = 0;
  while (picks.size < count && guard < 40) {
    picks.add(pool[Math.floor(Math.random() * pool.length)]);
    guard += 1;
  }
  return [...picks];
}

const WORDS_ROUND_MS = 100_000;

function clearGameTimer(room) {
  if (room.gameTimer) {
    clearTimeout(room.gameTimer);
    room.gameTimer = null;
  }
}

function stopRoomGame(room) {
  if (!room.game && !room.gameTimer) return;
  clearGameTimer(room);
  room.game = null;
  broadcastRoom(room, { type: "game_stop" });
  broadcastRoom(room, { type: "game_board", game: "ttt", visible: false });
}

function broadcastPlayState(room) {
  if (!room.game) return;
  if (Games.isInteractive(room.game.type)) {
    broadcastRoom(room, {
      type: "game_play",
      game: Games.publicState(room.game),
    });
  } else {
    broadcastRoom(room, {
      type: "game_state",
      game: publicGameState(room.game),
    });
  }
}

function publicGameState(game) {
  if (!game) return null;
  return {
    type: game.type,
    status: game.status,
    drawerPeerId: game.drawerPeerId,
    drawerNickname: game.drawerNickname,
    overlay: Boolean(game.overlay),
    endsAt: game.endsAt || 0,
  };
}

function startWordsRoundTimer(room) {
  clearGameTimer(room);
  const game = room.game;
  if (!game || game.type !== "words" || game.status !== "draw") return;
  const endsAt = game.endsAt || Date.now() + WORDS_ROUND_MS;
  game.endsAt = endsAt;
  const wait = Math.max(0, endsAt - Date.now());
  room.gameTimer = setTimeout(() => {
    room.gameTimer = null;
    const g = room.game;
    if (!g || g.type !== "words" || g.status !== "draw") return;
    const word = g.word;
    g.status = "timeout";
    g.endsAt = 0;
    broadcastRoom(room, {
      type: "game_words_timeout",
      word,
      drawerNickname: g.drawerNickname,
    });
    broadcastRoom(room, {
      type: "game_state",
      game: publicGameState(g),
    });
  }, wait);
}

/** Leinwand leeren: immer max. 2 Ja nötig (allein reicht 1). */
function clearVotesNeeded(room) {
  return Math.min(2, Math.max(1, room.sockets.size));
}

function clearProposalTotals(proposal) {
  const yes = new Set(proposal.yesUserIds || []).size;
  const no = new Set(proposal.noUserIds || []).size;
  const needed = Math.max(1, Number(proposal.neededYes) || 2);
  return { yes, no, total: needed };
}

function clearYesEnough(proposal) {
  const t = clearProposalTotals(proposal);
  return t.yes >= t.total;
}

function clearVoteOpenPayload(room, proposal, forUserId = null) {
  const t = clearProposalTotals(proposal);
  const alreadyVoted =
    Boolean(forUserId) &&
    ((proposal.yesUserIds || []).includes(forUserId) ||
      (proposal.noUserIds || []).includes(forUserId));
  return {
    type: "clear_vote_open",
    proposalId: proposal.id,
    by: proposal.byNickname,
    byPeerId: proposal.byPeerId,
    byUserId: proposal.byUserId || null,
    endsAt: proposal.endsAt,
    yes: t.yes,
    no: t.no,
    total: t.total,
    alreadyVoted,
    isInitiator: Boolean(forUserId) && forUserId === proposal.byUserId,
  };
}

function sendClearVoteOpen(socket, room) {
  const proposal = room.clearProposal;
  if (!proposal || proposal.status !== "open") return;
  socket.send(JSON.stringify(clearVoteOpenPayload(room, proposal, socket.luvUserId || null)));
}

function rejectClear(room, code, reason = "rejected") {
  const proposal = room.clearProposal;
  if (!proposal) return;
  const t = clearProposalTotals(proposal);
  proposal.status = "rejected";
  const refundedUser = refundClearCharge(proposal);
  broadcastRoom(room, {
    type: "clear_result",
    proposalId: proposal.id,
    approved: false,
    yes: t.yes,
    total: t.total,
    reason,
  });
  if (refundedUser) {
    for (const sock of room.sockets.values()) {
      if (sock.luvUserId === proposal.chargedUserId && sock.readyState === 1) {
        sock.send(
          JSON.stringify({
            type: "economy_ok",
            charged: false,
            refunded: true,
            user: publicUser(refundedUser),
          })
        );
      }
    }
  }
  room.clearProposal = null;
  void code;
}

function approveClear(room, code) {
  const proposal = room.clearProposal;
  if (!proposal || proposal.status !== "open") return;
  if (!clearYesEnough(proposal)) return;
  const t = clearProposalTotals(proposal);
  proposal.status = "approved";
  broadcastRoom(room, {
    type: "clear_result",
    proposalId: proposal.id,
    approved: true,
    yes: t.yes,
    total: t.total,
  });
  stopRoomGame(room);
  clearRoomStrokes(room, code);
  broadcastRoom(room, { type: "clear" });
  room.clearProposal = null;
}

/** Timeout — leeren nur mit genug Ja-Stimmen (2, oder 1 allein) */
function resolveClear(room, code) {
  const proposal = room.clearProposal;
  if (!proposal || proposal.status !== "open") return;
  if (clearYesEnough(proposal)) {
    approveClear(room, code);
    return;
  }
  rejectClear(room, code, "timeout");
}

/** User-IDs, die gerade auf der Leinwand aktiv sind (presence active). */
function onlineCanvasUserIds(room) {
  const ids = [];
  const seen = new Set();
  for (const sock of room.sockets.values()) {
    if (sock.readyState !== 1) continue;
    if (!sock.luvCanvasActive) continue;
    const id = sock.luvUserId;
    if (!id || seen.has(id)) continue;
    seen.add(id);
    ids.push(id);
  }
  return ids;
}

/**
 * Nur wer gerade in der Leinwand online ist, muss zustimmen —
 * Offline-Mitglieder der Lobby zählen nicht.
 */
function buildPublicRequiredVoters(room, proposerUserId) {
  const ids = [];
  const seen = new Set();
  const add = (id) => {
    if (!id || seen.has(id)) return;
    seen.add(id);
    ids.push(id);
  };
  add(proposerUserId);
  for (const id of onlineCanvasUserIds(room)) add(id);
  return ids;
}

function syncPublicRequiredVoters(room) {
  const proposal = room.publicProposal;
  if (!proposal || proposal.status !== "open") return false;
  const next = buildPublicRequiredVoters(room, proposal.byUserId);
  const prev = Array.isArray(proposal.requiredUserIds) ? proposal.requiredUserIds : [];
  const prevSet = new Set(prev);
  const nextSet = new Set(next);
  const same =
    prevSet.size === nextSet.size && [...prevSet].every((id) => nextSet.has(id));
  if (same) return false;
  proposal.requiredUserIds = next;
  return true;
}

function broadcastPublicVoteOpen(room) {
  const proposal = room.publicProposal;
  if (!proposal || proposal.status !== "open") return;
  for (const sock of room.sockets.values()) {
    if (sock.readyState !== 1) continue;
    // Strikt: nur aktive Leinwand — Lobby-Online ohne Leinwand wird nicht gefragt
    if (!sock.luvCanvasActive) continue;
    sock.send(
      JSON.stringify(publicVoteOpenPayload(room, proposal, sock.luvUserId || null))
    );
  }
}

function publicProposalTotals(proposal) {
  const required = Array.isArray(proposal.requiredUserIds) ? proposal.requiredUserIds : [];
  const yes = new Set(proposal.yesUserIds || []);
  const no = new Set(proposal.noUserIds || []);
  // Fortschritt nur anhand der aktuell erforderlichen Online-Stimmen
  let yesRequired = 0;
  for (const id of required) if (yes.has(id)) yesRequired += 1;
  return {
    yes: yesRequired,
    no: no.size,
    total: Math.max(1, required.length),
    required,
  };
}

function allRequiredYes(proposal) {
  const { required } = publicProposalTotals(proposal);
  if (required.length === 0) return false;
  const yes = new Set(proposal.yesUserIds || []);
  return required.every((id) => yes.has(id));
}

function publicVoteOpenPayload(room, proposal, forUserId = null) {
  const t = publicProposalTotals(proposal);
  const alreadyVoted =
    Boolean(forUserId) &&
    ((proposal.yesUserIds || []).includes(forUserId) ||
      (proposal.noUserIds || []).includes(forUserId));
  return {
    type: "public_vote_open",
    proposalId: proposal.id,
    by: proposal.byNickname,
    byPeerId: proposal.byPeerId,
    byUserId: proposal.byUserId || null,
    endsAt: proposal.endsAt,
    yes: t.yes,
    no: t.no,
    total: t.total,
    rewardCoins: proposal.rewardCoins || 1,
    alreadyVoted,
    isInitiator: Boolean(forUserId) && forUserId === proposal.byUserId,
  };
}

function sendPublicVoteOpen(socket, room, code) {
  const proposal = room.publicProposal;
  if (!proposal) return;
  if (proposal.status === "capturing") {
    maybeRequestPublicCapture(room, code || proposal.lobbyCode);
    return;
  }
  if (proposal.status !== "open") return;
  socket.send(JSON.stringify(publicVoteOpenPayload(room, proposal, socket.luvUserId || null)));
}

function maybeRequestPublicCapture(room, code) {
  const proposal = room.publicProposal;
  if (!proposal) return;
  if (proposal.status === "open" && allRequiredYes(proposal)) {
    proposal.status = "capturing";
  }
  if (proposal.status !== "capturing") return;
  let target = null;
  for (const sock of room.sockets.values()) {
    if (sock.readyState !== 1) continue;
    if (sock.luvUserId && sock.luvUserId === proposal.byUserId) {
      target = sock;
      break;
    }
  }
  if (!target) {
    for (const sock of room.sockets.values()) {
      if (sock.readyState !== 1) continue;
      if (sock.luvUserId && (proposal.yesUserIds || []).includes(sock.luvUserId)) {
        target = sock;
        break;
      }
    }
  }
  if (!target) return;
  target.send(
    JSON.stringify({
      type: "public_capture_request",
      proposalId: proposal.id,
    })
  );
  void code;
}

function rejectPublicShare(room, code, reason = "rejected") {
  const proposal = room.publicProposal;
  if (!proposal) return;
  const t = publicProposalTotals(proposal);
  broadcastRoom(room, {
    type: "public_result",
    proposalId: proposal.id,
    approved: false,
    yes: t.yes,
    total: t.total,
    rewardCoins: 0,
    publicId: null,
    nameLine: "",
    imageUrl: null,
    reason,
  });
  room.publicProposal = null;
}

function finishPublicShare(room, code, imageBuf) {
  const proposal = room.publicProposal;
  if (!proposal) return;
  const t = publicProposalTotals(proposal);
  let rewardCoins = 0;
  let publicId = null;
  let nameLine = "";

  if (imageBuf && imageBuf.length >= 64) {
    ensurePublicDir();
    publicId = newId("pub");
    const fileName = `${publicId}.png`;
    const filePath = path.join(PUBLIC_DIR, fileName);
    fs.writeFileSync(filePath, imageBuf);
    const nicknames = roomMemberNicknames(room);
    const hostNick = room.hostNickname || proposal.byNickname || "Jemand";
    const entry = {
      file: fileName,
      createdAt: Date.now(),
      expiresAt: Date.now() + PUBLIC_TTL_MS,
      lobbyCode: code,
      lobbyName: room.name || "Lobby",
      hostNickname: hostNick,
      hostUserId: room.hostUserId || null,
      memberNicknames: nicknames,
      memberUserIds: Array.isArray(room.memberUserIds) ? [...room.memberUserIds] : [],
      nameLine: "",
    };
    entry.nameLine = splashNameLine(entry);
    nameLine = entry.nameLine;
    publicCanvases()[publicId] = entry;
    scheduleSave();

    const reward = rewardPublicShare(room, code, proposal.id);
    rewardCoins = reward.coins;
    for (const u of reward.rewarded) {
      for (const sock of room.sockets.values()) {
        if (sock.luvUserId === u.id && sock.readyState === 1) {
          sock.send(
            JSON.stringify({
              type: "economy_ok",
              charged: false,
              reason: "public_share_reward",
              rewardCoins,
              user: publicUser(u),
            })
          );
        }
      }
    }
  }

  broadcastRoom(room, {
    type: "public_result",
    proposalId: proposal.id,
    approved: Boolean(publicId),
    yes: t.yes,
    total: t.total,
    rewardCoins,
    publicId,
    nameLine,
    imageUrl: publicId ? `/v1/public-canvases/${publicId}/image` : null,
  });
  room.publicProposal = null;
}

/** Timeout / Abbruch — nie automatisch freigeben ohne volle Zustimmung */
function resolvePublicShare(room, code) {
  const proposal = room.publicProposal;
  if (!proposal) return;
  if (proposal.status === "capturing") {
    // Capture hängt — verwerfen
    rejectPublicShare(room, code, "timeout");
    return;
  }
  if (proposal.status !== "open") return;
  if (allRequiredYes(proposal)) {
    maybeRequestPublicCapture(room, code);
    return;
  }
  rejectPublicShare(room, code, "timeout");
}

setInterval(cleanupRooms, 60_000).unref();
setInterval(() => {
  sweepExpiredMarketListings();
}, 5 * 60_000).unref();
setInterval(() => {
  cleanupCanvasMemories();
  cleanupPublicCanvases();
  for (const [code, room] of rooms.entries()) {
    maybeReleaseMemory(code, room);
  }
  for (const [code, data] of Object.entries(getDb().rooms || {})) {
    if (rooms.has(code)) continue;
    const last = Number(data.lastCanvasAt) || 0;
    if (!last || Date.now() - last < MEMORY_TTL_MS) continue;
    const hydrated = hydrateRoom(code, data);
    rooms.set(code, hydrated);
    maybeReleaseMemory(code, hydrated);
  }
}, 60_000).unref();

// Interaktive Spiele: Timer / Phasen fortschreiben
setInterval(() => {
  for (const room of rooms.values()) {
    if (!room.game || !Games.isInteractive(room.game.type)) continue;
    const { changed, game } = Games.tickGame(room.game);
    if (changed) {
      room.game = game;
      broadcastPlayState(room);
    }
  }
}, 200).unref();
setInterval(() => {
  const db = getDb();
  const now = Date.now();
  let dirty = false;
  for (const [token, s] of Object.entries(db.sessions)) {
    if (s.expiresAt < now) {
      delete db.sessions[token];
      dirty = true;
    }
  }
  if (dirty) scheduleSave();
}, 3600_000).unref();

const app = express();
app.use(express.json({ limit: "8mb" }));
app.use(express.urlencoded({ extended: true }));

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "luv-api",
    rooms: rooms.size,
    maxPeers: MAX_PEERS,
    users: Object.keys(getDb().users).length,
    economy: {
      dailyCoins: DAILY_COINS,
      glassTipDailyMax: GLASS_TIP_DAILY_MAX,
      freeSessionsPerDay: FREE_SESSIONS_PER_DAY,
      sessionCost: SESSION_COST,
      startingCoins: STARTING_COINS,
      clearCost: CLEAR_COST,
      gameCost: GAME_COST,
      lobbyCreateCost: LOBBY_CREATE_COST,
      slotCost: SLOT_COST,
      maxLobbies: MAX_LOBBIES,
      maxPeers: MAX_PEERS,
      freeLobbyStartCapacity: FREE_LOBBY_START_CAPACITY,
      paidLobbyStartCapacity: PAID_LOBBY_START_CAPACITY,
    },
    shopEnabled: Boolean(MOLLIE_API_KEY),
    uptimeSec: Math.round(process.uptime()),
  });
});

app.get("/v1/auth/config", (_req, res) => {
  return res.json({
    googleEnabled: Boolean(GOOGLE_CLIENT_ID),
    googleWebClientId: GOOGLE_CLIENT_ID || null,
  });
});

app.post("/v1/auth/device", (req, res) => {
  const ip = clientIp(req);
  if (!rateLimit(`auth_device:${ip}`, 40, 60 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Anmeldeversuche. Bitte später erneut.",
    });
  }
  const installId = String(req.body?.installId || "").trim();
  const installSecret = String(req.body?.installSecret || "").trim();
  let nickname = String(req.body?.nickname || "").trim().slice(0, 18);
  if (!installId || installId.length < 8 || !installSecret || installSecret.length < 16) {
    return res.status(400).json({ error: "invalid_credentials" });
  }
  const secretHash = hashSecret(`${installId}:${installSecret}`);
  const db = getDb();
  let user = Object.values(db.users).find((u) => u.secretHash === secretHash);
  let created = false;
  if (!user) {
    // Mit Google-Login: keine stillen Gast-Konten (sonst überschreibt die App echte Konten).
    if (GOOGLE_CLIENT_ID) {
      return res.status(403).json({
        error: "google_required",
        message: "Bitte mit Google anmelden.",
      });
    }
    if (!rateLimit(`auth_signup:${ip}`, 3, 24 * 60 * 60 * 1000)) {
      return res.status(429).json({
        error: "rate_limited",
        message: "Zu viele neue Konten von diesem Netz. Bitte später oder mit Google anmelden.",
      });
    }
    if (!nickname || nickname.length < 2) nickname = "Luv";
    user = {
      id: newId("u"),
      secretHash,
      installIdHash: hashSecret(installId),
      nickname,
      paidCoins: 0,
      dailyBalance: STARTING_COINS,
      coins: STARTING_COINS,
      role: "user",
      lastDailyGrantDate: todayKey(),
      sessionsByDay: {},
      drawLocks: {},
      createdAt: Date.now(),
      googleSub: null,
      googleEmail: null,
      hostedRooms: {},
    };
    db.users[user.id] = user;
    db.ledger.push({
      id: newId("led"),
      userId: user.id,
      delta: STARTING_COINS,
      reason: "signup_grant",
      refId: null,
      at: Date.now(),
      balance: STARTING_COINS,
      note: "starter_daily_bucket",
    });
    scheduleSave();
    created = true;
  } else if (nickname && nickname.length >= 2) {
    // Spitzname nur einmal wählbar — danach gesperrt (Doppel-Namen werden manuell bereinigt)
    if (
      isChosenNickname(user.nickname) &&
      nicknameKey(nickname) !== nicknameKey(user.nickname)
    ) {
      return res.status(403).json({
        error: "nickname_locked",
        message: "Dein Spitzname kann nicht mehr geändert werden.",
      });
    }
    if (!isChosenNickname(user.nickname)) {
      user.nickname = nickname;
      scheduleSave();
    }
  }
  if (user.banned) {
    return res.status(403).json({
      error: "banned",
      message: "Dieses Konto ist gesperrt.",
    });
  }
  ensureDailyGrant(user);
  const token = createSession(user.id);
  return res.json({
    sessionToken: token,
    created,
    user: publicUser(user),
  });
});

/**
 * Mit Google anmelden / Konto verknüpfen.
 * - Mit Session: Google an aktuelles Konto hängen (Coins bleiben).
 * - Ohne Session: bestehendes Google-Konto laden oder neu anlegen.
 */
app.post("/v1/auth/google", async (req, res) => {
  if (!GOOGLE_CLIENT_ID) {
    return res.status(503).json({
      error: "google_disabled",
      message: "Google-Login ist noch nicht eingerichtet.",
    });
  }
  const idToken = String(req.body?.idToken || "").trim();
  if (!idToken) return res.status(400).json({ error: "missing_token" });
  const profile = await verifyGoogleIdToken(idToken);
  if (!profile) {
    return res.status(401).json({
      error: "invalid_google_token",
      message: "Google-Anmeldung fehlgeschlagen.",
    });
  }

  const db = getDb();
  const existingByGoogle = findUserByGoogleSub(profile.sub);
  const authed = authUser(req);
  let user = null;
  let linked = false;
  let created = false;

  if (authed?.user) {
    // Verknüpfen mit aktuellem Geräte-Konto
    if (authed.user.googleSub && authed.user.googleSub !== profile.sub) {
      return res.status(409).json({
        error: "already_linked_other",
        message: "Dieses Konto ist schon mit einem anderen Google-Konto verbunden.",
      });
    }
    if (existingByGoogle && existingByGoogle.id !== authed.user.id) {
      // Google hing an einem anderen LUV-Konto → zusammenführen in das aktuelle.
      // Coins, Lobbys und Spitzname bleiben erhalten (aktueller Nick hat Vorrang).
      absorbUserInto(authed.user, existingByGoogle);
    }
    authed.user.googleSub = profile.sub;
    authed.user.googleEmail = profile.email;
    applySuperAdmin(authed.user);
    // Nickname bewusst nicht aus Google übernehmen — Nutzer wählt selbst.
    user = authed.user;
    linked = true;
    scheduleSave();
    destroySession(authed.token);
  } else if (existingByGoogle) {
    user = existingByGoogle;
    if (profile.email) user.googleEmail = profile.email;
    applySuperAdmin(user);
    scheduleSave();
  } else {
    user = {
      id: newId("u"),
      secretHash: hashSecret(`google:${profile.sub}:${crypto.randomBytes(16).toString("hex")}`),
      installIdHash: null,
      nickname: "Luv",
      paidCoins: 0,
      dailyBalance: STARTING_COINS,
      coins: STARTING_COINS,
      role: "user",
      lastDailyGrantDate: todayKey(),
      sessionsByDay: {},
      drawLocks: {},
      createdAt: Date.now(),
      googleSub: profile.sub,
      googleEmail: profile.email,
      hostedRooms: {},
    };
    applySuperAdmin(user);
    db.users[user.id] = user;
    db.ledger.push({
      id: newId("led"),
      userId: user.id,
      delta: STARTING_COINS,
      reason: "signup_grant",
      refId: null,
      at: Date.now(),
      balance: STARTING_COINS,
      note: "google_signup",
    });
    scheduleSave();
    created = true;
  }

  if (user.banned) {
    return res.status(403).json({
      error: "banned",
      message: "Dieses Konto ist gesperrt.",
    });
  }
  ensureDailyGrant(user);
  const token = createSession(user.id);
  return res.json({
    sessionToken: token,
    created,
    linked,
    user: publicUser(user),
    lobbies: publicHostedLobbies(user),
    joined: publicJoinedLobbies(user),
    settings: publicSettings(user),
  });
});

app.post("/v1/auth/logout", (req, res) => {
  const ctx = authUser(req);
  if (ctx) destroySession(ctx.token);
  return res.json({ ok: true });
});

app.delete("/v1/me", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  try {
    deleteUserAccount(ctx.user);
    return res.json({ ok: true, deleted: true });
  } catch (err) {
    console.error("delete /v1/me failed", err);
    return res.status(500).json({
      error: "delete_failed",
      message: "Konto konnte nicht gelöscht werden.",
    });
  }
});

/** Fallback ohne DELETE-Body (manche Clients/Proxies mögen DELETE+JSON nicht). */
app.post("/v1/me/delete", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  try {
    deleteUserAccount(ctx.user);
    return res.json({ ok: true, deleted: true });
  } catch (err) {
    console.error("post /v1/me/delete failed", err);
    return res.status(500).json({
      error: "delete_failed",
      message: "Konto konnte nicht gelöscht werden.",
    });
  }
});

app.get("/v1/me/lobbies", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({
    lobbies: publicHostedLobbies(ctx.user),
    joined: publicJoinedLobbies(ctx.user),
  });
});

app.get("/v1/me/settings", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({ ok: true, settings: publicSettings(ctx.user) });
});

app.put("/v1/me/settings", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const next = sanitizeSettings({
    ...(req.body?.settings || req.body || {}),
    updatedAt: Date.now(),
  });
  ctx.user.settings = next;
  scheduleSave();
  return res.json({ ok: true, settings: next });
});

app.get("/v1/me", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({ user: publicUser(ctx.user) });
});

app.patch("/v1/me", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const nickname = String(req.body?.nickname || "").trim().slice(0, 18);
  if (nickname.length >= 2) {
    if (
      isChosenNickname(ctx.user.nickname) &&
      nicknameKey(nickname) !== nicknameKey(ctx.user.nickname)
    ) {
      return res.status(403).json({
        error: "nickname_locked",
        message: "Dein Spitzname kann nicht mehr geändert werden.",
      });
    }
    // Erste Vergabe (Placeholder „Luv“) — Doppel-Namen werden manuell bereinigt
    if (!isChosenNickname(ctx.user.nickname)) {
      ctx.user.nickname = nickname;
      scheduleSave();
    }
  }
  return res.json({ user: publicUser(ctx.user) });
});

/** Nutzer per Spitzname finden (für Freundesuche). */
app.get("/v1/users/lookup", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const q = String(req.query?.nickname || req.query?.q || "").trim().slice(0, 18);
  if (!isChosenNickname(q)) {
    return res.status(400).json({
      error: "bad_nick",
      message: "Bitte einen Spitznamen eingeben.",
    });
  }
  const user = findUserByNickname(q, ctx.user.id);
  if (!user) {
    return res.status(404).json({
      error: "not_found",
      message: "Niemand mit diesem Spitznamen gefunden.",
    });
  }
  return res.json({
    ok: true,
    user: friendPublicCard(user),
    friendStatus: friendRelation(ctx.user, user.id),
  });
});

function sanitizeProfileCanvas(raw) {
  if (!raw || typeof raw !== "object") return null;
  const themeId = String(raw.themeId || "meadow").trim().slice(0, 32) || "meadow";
  const statusEmoji = String(raw.statusEmoji || "😊").trim().slice(0, 8) || "😊";
  const companionEmoji = String(raw.companionEmoji || "💕").trim().slice(0, 8) || "💕";
  const bio = String(raw.bio || "").trim().slice(0, 500);
  const layoutIn = Array.isArray(raw.layout) ? raw.layout : [];
  const layout = [];
  const allowedTypes = new Set([
    "avatar",
    "name",
    "status",
    "bio",
    "glass",
    "pet",
    "sticker",
    "text",
  ]);
  for (const el of layoutIn.slice(0, 48)) {
    if (!el || typeof el !== "object") continue;
    const id = String(el.id || "").trim().slice(0, 64);
    let type = String(el.type || "sticker").trim().slice(0, 16).toLowerCase();
    if (!allowedTypes.has(type)) type = "sticker";
    if (!id) continue;
    const fontFamilyRaw = String(el.fontFamily || "").trim().toLowerCase();
    const fontFamily = ["cozy", "playful", "classic"].includes(fontFamilyRaw)
      ? fontFamilyRaw
      : null;
    const fontSizeNum = Number(el.fontSize);
    const fontSize = Number.isFinite(fontSizeNum)
      ? Math.min(32, Math.max(8, fontSizeNum))
      : null;
    layout.push({
      id,
      type,
      x: Math.min(100, Math.max(0, Number(el.x) || 50)),
      y: Math.min(100, Math.max(0, Number(el.y) || 50)),
      scale: Math.min(2.5, Math.max(0.35, Number(el.scale) || 1)),
      rotation: Number(el.rotation) || 0,
      flipX: Boolean(el.flipX),
      visible: el.visible !== false,
      z: Math.max(0, Math.min(100, Number(el.z) || 10)),
      emoji: String(el.emoji || "").trim().slice(0, 8) || null,
      text: String(el.text || "").trim().slice(0, 500) || null,
      color: String(el.color || "").trim().slice(0, 16) || null,
      fontSize,
      fontFamily,
    });
  }
  return { themeId, statusEmoji, companionEmoji, bio, layout };
}

app.get("/v1/me/profile", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const raw = sanitizeProfileCanvas(ctx.user.profileCanvas) || {
    themeId: "meadow",
    statusEmoji: "😊",
    companionEmoji: DEFAULT_PET,
    bio: "",
    layout: [],
  };
  const profile = clampProfileToInventory(ctx.user, raw);
  if (
    JSON.stringify(ctx.user.profileCanvas || {}) !== JSON.stringify(profile)
  ) {
    ctx.user.profileCanvas = profile;
    scheduleSave();
  }
  return res.json({
    nickname: ctx.user.nickname || "Du",
    userId: ctx.user.id,
    profile,
  });
});

/** Sticker auf dem Profil zählen (emoji → Anzahl). */
function countLayoutStickers(layout) {
  const out = {};
  if (!Array.isArray(layout)) return out;
  for (const el of layout) {
    if (!el || String(el.type).toLowerCase() !== "sticker") continue;
    const e = String(el.emoji || el.text || "").trim().slice(0, 8);
    if (!e) continue;
    out[e] = (out[e] || 0) + 1;
  }
  return out;
}

/**
 * Sticker-Inventar = freier Bestand (nicht auf Profil / Markt).
 * Beim Platzieren abziehen, beim Entfernen zurückgeben.
 * Einmalige Migration: Profil-Sticker vom Inventar abziehen.
 */
function migrateStickerInventoryIfNeeded(user) {
  if (user.stickerInvMode === 2) return;
  const inv = ensureInventory(user);
  const onProfile = countLayoutStickers(user.profileCanvas?.layout);
  for (const [e, n] of Object.entries(onProfile)) {
    const have = Number(inv.stickers[e]) || 0;
    if (have > 0 && n > 0) {
      inv.stickers[e] = Math.max(0, have - n);
      if (inv.stickers[e] <= 0) delete inv.stickers[e];
    }
  }
  user.stickerInvMode = 2;
}

/** Profil-Sticker vs. Inventar abgleichen (delta). Gibt false wenn nicht genug Bestand. */
function syncStickersOnProfileSave(user, newLayout) {
  migrateStickerInventoryIfNeeded(user);
  const inv = ensureInventory(user);
  const oldC = countLayoutStickers(user.profileCanvas?.layout);
  const newC = countLayoutStickers(newLayout);
  const keys = new Set([...Object.keys(oldC), ...Object.keys(newC)]);
  for (const e of keys) {
    const delta = (newC[e] || 0) - (oldC[e] || 0);
    if (delta > 0) {
      const have = Number(inv.stickers[e]) || 0;
      if (have < delta) return false;
      inv.stickers[e] = have - delta;
      if (inv.stickers[e] <= 0) delete inv.stickers[e];
    } else if (delta < 0) {
      inv.stickers[e] = (Number(inv.stickers[e]) || 0) + -delta;
    }
  }
  return true;
}

app.put("/v1/me/profile", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const raw = sanitizeProfileCanvas(req.body?.profile || req.body);
  if (!raw) return res.status(400).json({ error: "invalid_profile" });
  migrateStickerInventoryIfNeeded(ctx.user);
  // Sticker-Platzierung verbraucht Inventar (frei), Entfernen gibt zurück
  if (!syncStickersOnProfileSave(ctx.user, raw.layout)) {
    return res.status(400).json({
      error: "not_enough_stickers",
      message: "Nicht genug Sticker im Inventar.",
    });
  }
  const profile = clampProfileToInventory(ctx.user, raw);
  ctx.user.profileCanvas = profile;
  const inv = ensureInventory(ctx.user);
  scheduleSave();
  return res.json({
    ok: true,
    nickname: ctx.user.nickname || "Du",
    userId: ctx.user.id,
    profile,
    inventory: {
      stickers: inv.stickers,
      emojis: inv.emojis,
      themes: inv.themes,
      pets: inv.pets,
      equippedPet: inv.equippedPet || DEFAULT_PET,
    },
  });
});

app.get("/v1/users/:userId/profile", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const db = getDb();
  const user = db.users?.[uid];
  if (!user) return res.status(404).json({ error: "not_found" });
  ensureCoinBuckets(user);
  const raw = sanitizeProfileCanvas(user.profileCanvas) || {
    themeId: "meadow",
    statusEmoji: "😊",
    companionEmoji: DEFAULT_PET,
    bio: "",
    layout: [],
  };
  const profile = clampProfileToInventory(user, raw);
  const petEmoji = userPetEmoji(user);
  if (!profile.companionEmoji) profile.companionEmoji = petEmoji;
  const isSelf = uid === ctx.user.id;
  const areFriends = !isSelf && friendRelation(ctx.user, uid) === "friends";
  const tipRecv = glassTipRecvState(user);
  return res.json({
    nickname: user.nickname || "Jemand",
    userId: user.id,
    coins: user.coins || 0,
    profile,
    petEmoji,
    friendStatus: isSelf ? "self" : friendRelation(ctx.user, uid),
    canPetKraul: areFriends && canPetKraulToday(ctx.user, uid),
    glassTipsRemaining: tipRecv.remaining,
    glassTipsReceived: tipRecv.received,
    glassTipDailyMax: GLASS_TIP_DAILY_MAX,
    canTipGlass: !isSelf && tipRecv.remaining > 0,
  });
});

/** Freundesliste + Anfragen */
app.get("/v1/me/friends", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const f = ensureFriends(ctx.user);
  // Verwaiste IDs bereinigen
  f.list = f.list.filter((id) => db.users?.[id]);
  f.incoming = f.incoming.filter((id) => db.users?.[id]);
  f.outgoing = f.outgoing.filter((id) => db.users?.[id]);
  scheduleSave();
  return res.json({
    ok: true,
    friends: f.list.map((id) => friendPublicCard(db.users[id])).filter(Boolean),
    incoming: f.incoming.map((id) => friendPublicCard(db.users[id])).filter(Boolean),
    outgoing: f.outgoing.map((id) => friendPublicCard(db.users[id])).filter(Boolean),
  });
});

app.post("/v1/users/:userId/friend-request", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const toId = String(req.params.userId || "").trim();
  if (!toId || toId === ctx.user.id) {
    return res.status(400).json({ error: "invalid_user" });
  }
  const db = getDb();
  const target = db.users?.[toId];
  if (!target) return res.status(404).json({ error: "not_found" });
  const mine = ensureFriends(ctx.user);
  const theirs = ensureFriends(target);
  if (mine.list.includes(toId)) {
    return res.json({ ok: true, friendStatus: "friends", already: true });
  }
  // Gegenanfrage → sofort Freunde
  if (mine.incoming.includes(toId)) {
    mine.incoming = mine.incoming.filter((id) => id !== toId);
    theirs.outgoing = theirs.outgoing.filter((id) => id !== ctx.user.id);
    if (!mine.list.includes(toId)) mine.list.push(toId);
    if (!theirs.list.includes(ctx.user.id)) theirs.list.push(ctx.user.id);
    scheduleSave();
    return res.json({ ok: true, friendStatus: "friends" });
  }
  if (mine.outgoing.includes(toId)) {
    return res.json({ ok: true, friendStatus: "outgoing", already: true });
  }
  mine.outgoing.push(toId);
  if (!theirs.incoming.includes(ctx.user.id)) theirs.incoming.push(ctx.user.id);
  trackAch(ctx.user, "friend_requests_sent", 1);
  scheduleSave();
  return res.json({ ok: true, friendStatus: "outgoing" });
});

app.post("/v1/me/friends/accept", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const fromId = String(req.body?.userId || "").trim();
  if (!fromId) return res.status(400).json({ error: "invalid_user" });
  const db = getDb();
  const other = db.users?.[fromId];
  if (!other) return res.status(404).json({ error: "not_found" });
  const mine = ensureFriends(ctx.user);
  const theirs = ensureFriends(other);
  if (!mine.incoming.includes(fromId)) {
    return res.status(400).json({ error: "no_request" });
  }
  mine.incoming = mine.incoming.filter((id) => id !== fromId);
  theirs.outgoing = theirs.outgoing.filter((id) => id !== ctx.user.id);
  if (!mine.list.includes(fromId)) mine.list.push(fromId);
  if (!theirs.list.includes(ctx.user.id)) theirs.list.push(ctx.user.id);
  trackAch(ctx.user, "friend_accepts", 1);
  ach.setMetricAtLeast(ctx.user, "friends", mine.list.length, todayKey(), (uid, d, r, ref) =>
    applyLedger(uid, d, r, ref)
  );
  ach.setMetricAtLeast(other, "friends", theirs.list.length, todayKey(), (uid, d, r, ref) =>
    applyLedger(uid, d, r, ref)
  );
  scheduleSave();
  return res.json({ ok: true, friend: friendPublicCard(other) });
});

app.post("/v1/me/friends/decline", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const fromId = String(req.body?.userId || "").trim();
  if (!fromId) return res.status(400).json({ error: "invalid_user" });
  const db = getDb();
  const other = db.users?.[fromId];
  const mine = ensureFriends(ctx.user);
  mine.incoming = mine.incoming.filter((id) => id !== fromId);
  if (other) {
    const theirs = ensureFriends(other);
    theirs.outgoing = theirs.outgoing.filter((id) => id !== ctx.user.id);
  }
  scheduleSave();
  return res.json({ ok: true });
});

app.post("/v1/me/friends/remove", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const otherId = String(req.body?.userId || "").trim();
  if (!otherId) return res.status(400).json({ error: "invalid_user" });
  const db = getDb();
  const mine = ensureFriends(ctx.user);
  mine.list = mine.list.filter((id) => id !== otherId);
  mine.outgoing = mine.outgoing.filter((id) => id !== otherId);
  mine.incoming = mine.incoming.filter((id) => id !== otherId);
  const other = db.users?.[otherId];
  if (other) {
    const theirs = ensureFriends(other);
    theirs.list = theirs.list.filter((id) => id !== ctx.user.id);
    theirs.outgoing = theirs.outgoing.filter((id) => id !== ctx.user.id);
    theirs.incoming = theirs.incoming.filter((id) => id !== ctx.user.id);
  }
  scheduleSave();
  return res.json({ ok: true });
});

app.put("/v1/me/friends/order", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const raw = Array.isArray(req.body?.userIds) ? req.body.userIds : [];
  const f = ensureFriends(ctx.user);
  const want = raw.map((id) => String(id || "").trim()).filter(Boolean);
  const set = new Set(f.list);
  const next = [];
  for (const id of want) {
    if (set.has(id) && !next.includes(id)) next.push(id);
  }
  for (const id of f.list) {
    if (!next.includes(id)) next.push(id);
  }
  f.list = next;
  trackAch(ctx.user, "friend_reorders", 1);
  scheduleSave();
  return res.json({
    ok: true,
    friends: next
      .map((id) => friendPublicCard(getDb().users?.[id]))
      .filter(Boolean),
  });
});

/** Begleiter kraulen — nur Freunde, Empfänger +1 Coin, 1× pro Person / Tag. */
app.post("/v1/users/:userId/pet-kraul", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const toId = String(req.params.userId || "").trim();
  if (!toId) return res.status(400).json({ error: "invalid_user" });
  if (toId === ctx.user.id) {
    return res.status(400).json({ error: "self_kraul" });
  }
  const db = getDb();
  const target = db.users?.[toId];
  if (!target) return res.status(404).json({ error: "not_found" });
  if (friendRelation(ctx.user, toId) !== "friends") {
    return res.status(403).json({
      error: "friends_only",
      message: "Kraulen geht nur bei Freunden.",
    });
  }
  ensureCoinBuckets(ctx.user);
  ensureCoinBuckets(target);
  if (!canPetKraulToday(ctx.user, toId)) {
    return res.status(400).json({
      error: "already_krault",
      message: "Diesen Begleiter hast du heute schon gekrault.",
    });
  }
  // Empfänger: max. 25 Kraul-Coins / Tag (Farming-Deckel)
  const day = todayKey();
  if (target.petKraulRecvDay !== day) {
    target.petKraulRecvDay = day;
    target.petKraulRecvCount = 0;
  }
  if ((target.petKraulRecvCount || 0) >= 25) {
    return res.status(400).json({
      error: "recv_cap",
      message: "Heute schon genug Kraule bekommen.",
    });
  }
  applyLedger(toId, 1, "pet_kraul_recv", ctx.user.id);
  target.petKraulRecvCount = (target.petKraulRecvCount || 0) + 1;
  markPetKraul(ctx.user, toId);
  trackAch(ctx.user, "krauls", 1);
  const kraulSet = new Set(
    Array.isArray(ctx.user.friends?.petKraulTargets)
      ? ctx.user.friends.petKraulTargets
      : []
  );
  ach.setMetricAtLeast(ctx.user, "kraul_unique", kraulSet.size, todayKey(), (uid, d, r, ref) =>
    applyLedger(uid, d, r, ref)
  );
  scheduleSave();
  const petEmoji = userPetEmoji(target);
  return res.json({
    ok: true,
    petEmoji,
    toCoins: target.coins || 0,
    amount: 1,
    canPetKraul: false,
  });
});

/** Empfangenes Münzglas-Tageslimit (pro Profil, alle Spender, 0:00 Berlin). */
function glassTipRecvState(user) {
  const day = todayKey();
  if (user.glassTipsRecvDay !== day) {
    user.glassTipsRecvDay = day;
    user.glassTipsRecvCount = 0;
  }
  const received = Math.max(0, Number(user.glassTipsRecvCount) || 0);
  const remaining = Math.max(0, GLASS_TIP_DAILY_MAX - received);
  return { day, received, remaining, limit: GLASS_TIP_DAILY_MAX };
}

/** 1 Coin ins Münzglas — max. GLASS_TIP_DAILY_MAX Coins / Profil / Tag (0:00 Berlin). */
app.post("/v1/users/:userId/tip-glass", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!rateLimit(`tip:${ctx.user.id}`, 30, 60_000)) {
    return res.status(429).json({ error: "rate_limited", message: "Zu schnell — kurz warten." });
  }
  const toId = String(req.params.userId || "").trim();
  if (!toId) return res.status(400).json({ error: "invalid_user" });
  if (toId === ctx.user.id) {
    return res.status(400).json({ error: "self_tip" });
  }
  const db = getDb();
  const target = db.users?.[toId];
  if (!target) return res.status(404).json({ error: "not_found" });
  ensureCoinBuckets(ctx.user);
  ensureCoinBuckets(target);
  ensureDailyGrant(ctx.user);

  const layout = Array.isArray(target.profileCanvas?.layout)
    ? target.profileCanvas.layout
    : [];
  const hasGlass = layout.some((el) => el && String(el.type).toLowerCase() === "glass");
  if (!hasGlass) {
    return res.status(400).json({ error: "no_glass" });
  }

  const recv = glassTipRecvState(target);
  if (recv.remaining <= 0) {
    return res.status(400).json({
      error: "daily_tip_limit",
      message: "Dieses Münzglas ist heute voll (10 Coins). Ab 0 Uhr MEZ wieder möglich.",
      remaining: 0,
      limit: GLASS_TIP_DAILY_MAX,
      received: recv.received,
    });
  }
  if ((ctx.user.coins || 0) < 1) {
    return res.status(402).json({ error: "insufficient_coins" });
  }

  if (!applyLedger(ctx.user.id, -1, "glass_tip", toId)) {
    return res.status(402).json({ error: "insufficient_coins" });
  }
  applyLedger(toId, 1, "glass_tip_recv", ctx.user.id);
  target.glassTipsRecvCount = recv.received + 1;
  target.glassTipsRecvDay = recv.day;
  trackAch(ctx.user, "tips_given", 1);
  trackAch(target, "tips_received", 1);
  flushSave();

  const after = glassTipRecvState(target);
  return res.json({
    ok: true,
    amount: 1,
    remaining: after.remaining,
    limit: GLASS_TIP_DAILY_MAX,
    received: after.received,
    from: publicUser(ctx.user),
    toCoins: target.coins || 0,
  });
});

app.post("/v1/me/daily-claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  // Automatisch — kein manueller Claim mehr; Endpoint bleibt für alte Clients
  const claimed = ensureDailyGrant(ctx.user);
  if (claimed) trackAch(ctx.user, "daily_claims", 1);
  scheduleSave();
  return res.json({
    user: publicUser(ctx.user),
    claimed,
    amount: claimed ? DAILY_COINS : 0,
  });
});

app.post("/v1/economy/draw-session", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const lobbyCode = String(req.body?.lobbyCode || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  if (!lobbyCode) return res.status(400).json({ error: "invalid_lobby" });
  const result = consumeDrawSession(ctx.user, lobbyCode);
  if (!result.ok) {
    return res.status(402).json({
      error: "no_coins",
      message: "Keine freien Sessions und keine Coins mehr. Morgen gibt’s wieder 10 — oder Shop.",
      user: publicUser(ctx.user),
    });
  }
  return res.json({
    ok: true,
    charged: result.charged,
    free: Boolean(result.free),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/redeem", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.body?.code || "").trim();
  if (!code) return res.status(400).json({ error: "invalid_code" });

  const normalized = code.toUpperCase().replace(/[^A-Z0-9]/g, "");
  const db = getDb();
  const voucher =
    db.vouchers[normalized] ||
    Object.values(db.vouchers).find((v) => v.code === normalized);
  if (!voucher || voucher.revoked) {
    return res.status(404).json({ error: "code_not_found" });
  }
  if (voucher.expiresAt && voucher.expiresAt < Date.now()) {
    return res.status(410).json({ error: "code_expired" });
  }
  if (voucher.redeemCount >= voucher.maxRedeems) {
    return res.status(409).json({ error: "code_exhausted" });
  }
  const redeemKey = `${voucher.id}:${ctx.user.id}`;
  if (db.redeems[redeemKey]) {
    return res.status(409).json({ error: "already_redeemed" });
  }
  voucher.redeemCount += 1;
  db.redeems[redeemKey] = { at: Date.now(), userId: ctx.user.id, voucherId: voucher.id };
  applyLedger(ctx.user.id, voucher.coins, "voucher", voucher.id);
  scheduleSave();
  return res.json({
    type: "voucher",
    coins: voucher.coins,
    user: publicUser(ctx.user),
  });
});

app.get("/v1/admin/overview", (req, res) => {
  const ctx = requireStaff(req, res);
  if (!ctx) return;
  const db = getDb();
  const users = Object.values(db.users || {});
  const openPublic = Object.values(publicReports()).filter(
    (r) => (r.status || "open") === "open"
  ).length;
  const openPeer = Object.values(peerReports()).filter(
    (r) => (r.status || "open") === "open"
  ).length;
  const openHelp = Object.values(helpMessages()).filter(
    (r) => (r.status || "open") === "open"
  ).length;
  const mods = users.filter((u) => {
    ensureStaffFields(u);
    return u.role === "mod";
  }).length;
  return res.json({
    ok: true,
    users: users.length,
    rooms: rooms.size,
    openPublicReports: openPublic,
    openPeerReports: openPeer,
    openHelpMessages: openHelp,
    moderators: mods,
    vouchers: Object.keys(db.vouchers || {}).length,
    me: publicUser(ctx.user),
    permissionGroups: MOD_PERMISSION_GROUPS,
  });
});

app.get("/v1/admin/moderators", (req, res) => {
  const ctx = requireAdmin(req, res);
  if (!ctx) return;
  const list = Object.values(getDb().users || {})
    .filter((u) => {
      ensureStaffFields(u);
      return u.role === "mod";
    })
    .map((u) => staffUserCard(u))
    .sort((a, b) =>
      String(a.nickname).localeCompare(String(b.nickname), "de", {
        sensitivity: "base",
      })
    );
  return res.json({
    ok: true,
    moderators: list,
    permissionGroups: MOD_PERMISSION_GROUPS,
  });
});

app.get("/v1/admin/users/search", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const q = String(req.query?.q || "")
    .trim()
    .toLowerCase();
  if (q.length < 1) return res.json({ ok: true, users: [] });
  const list = Object.values(getDb().users || {})
    .filter((u) => {
      const nick = String(u.nickname || "").toLowerCase();
      const email = String(u.googleEmail || "").toLowerCase();
      const id = String(u.id || "").toLowerCase();
      return nick.includes(q) || email.includes(q) || id.includes(q);
    })
    .slice(0, 30)
    .map((u) => staffUserCard(u));
  return res.json({ ok: true, users: list });
});

/** Gehostete Lobbys eines Nutzers (Admin/Staff). */
app.get("/v1/admin/users/:userId/lobbies", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const user = getDb().users?.[uid];
  if (!user) {
    return res.status(404).json({ error: "not_found", message: "Nutzer nicht gefunden." });
  }
  reconcileAbandonedLobbyOwnership(user);
  ensureHostedRooms(user);
  const lobbies = Object.entries(user.hostedRooms || {}).map(([code, meta]) =>
    staffLobbyCard(code, meta, user)
  );
  lobbies.sort((a, b) => {
    if (a.live !== b.live) return a.live ? -1 : 1;
    if (a.active !== b.active) return a.active ? -1 : 1;
    return String(a.name).localeCompare(String(b.name), "de");
  });
  return res.json({
    ok: true,
    userId: uid,
    nickname: user.nickname || "Jemand",
    lobbies,
  });
});

/** Lobby-Detail für Staff. */
app.get("/v1/admin/rooms/:code", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let hostUser = null;
  for (const u of Object.values(getDb().users || {})) {
    if (u?.hostedRooms?.[code]) {
      hostUser = u;
      break;
    }
  }
  const meta = hostUser?.hostedRooms?.[code] || null;
  const live = rooms.get(code);
  const saved = getDb().rooms?.[code];
  if (!live && !saved && !meta) {
    return res.status(404).json({ error: "not_found", message: "Lobby nicht gefunden." });
  }
  return res.json({
    ok: true,
    lobby: staffLobbyCard(code, meta || {}, hostUser),
  });
});

/** Lobby sofort auflösen — alle raus. */
app.post("/v1/admin/rooms/:code/force-delete", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  if (!code) {
    return res.status(400).json({ error: "bad_code", message: "Ungültiger Lobby-Code." });
  }
  const result = forceDissolveLobby(code);
  if (!result.ok) {
    return res.status(400).json({
      error: result.reason || "failed",
      message: "Lobby konnte nicht gelöscht werden.",
    });
  }
  return res.json({ ok: true, code: result.code, deleted: true });
});

function findUserForStaffQuery(qRaw) {
  const q = String(qRaw || "")
    .trim()
    .toLowerCase();
  if (!q || q.length < 2) return null;
  const users = Object.values(getDb().users || {}).filter(Boolean);
  const exact = users.find((u) => {
    const nick = String(u.nickname || "").toLowerCase();
    const email = String(u.googleEmail || "").toLowerCase();
    return nick === q || email === q || String(u.id || "").toLowerCase() === q;
  });
  if (exact) return exact;
  // Teiltreffer nur bei eindeutigem Ergebnis (sonst falscher Admin-Match)
  const partial = users.filter((u) => {
    const nick = String(u.nickname || "").toLowerCase();
    const email = String(u.googleEmail || "").toLowerCase();
    return (nick.length >= 2 && nick.includes(q)) || (email.length >= 2 && email.includes(q));
  });
  return partial.length === 1 ? partial[0] : null;
}

app.post("/v1/admin/moderators/invite", (req, res) => {
  const ctx = requireAdmin(req, res);
  if (!ctx) return;
  const q = String(req.body?.query || req.body?.nickname || "").trim();
  if (q.length < 2) {
    return res.status(400).json({
      error: "invalid_query",
      message: "Bitte Spitzname oder E-Mail eingeben.",
    });
  }
  const target = findUserForStaffQuery(q);
  if (!target) {
    return res.status(404).json({
      error: "not_found",
      message: "Kein eindeutiger Nutzer gefunden. Bitte exakten Spitznamen oder E-Mail nutzen.",
    });
  }
  ensureStaffFields(target);
  if (
    target.role === "admin" ||
    SUPER_ADMIN_EMAILS.has(String(target.googleEmail || "").toLowerCase())
  ) {
    return res.status(400).json({
      error: "is_admin",
      message: "Admins brauchen keine Moderator-Rolle.",
    });
  }
  const already = target.role === "mod";
  target.role = "mod";
  if (!target.modPermissions || typeof target.modPermissions !== "object") {
    target.modPermissions = { ...DEFAULT_MOD_PERMISSIONS };
  }
  target.modSince = target.modSince || Date.now();
  scheduleSave();
  return res.json({
    ok: true,
    already,
    moderator: staffUserCard(target),
  });
});

function applyModeratorPermissions(req, res) {
  const ctx = requireAdmin(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!target) {
    return res.status(404).json({ error: "not_found", message: "Moderator nicht gefunden." });
  }
  ensureStaffFields(target);
  if (target.role !== "mod") {
    return res.status(400).json({
      error: "not_mod",
      message: "Dieser Nutzer ist kein Moderator — zuerst einladen.",
    });
  }
  const raw =
    req.body?.permissions && typeof req.body.permissions === "object"
      ? req.body.permissions
      : {};
  const next = {};
  for (const id of ALL_MOD_PERMISSION_IDS) {
    next[id] = Boolean(raw[id]);
  }
  target.modPermissions = next;
  scheduleSave();
  return res.json({ ok: true, moderator: staffUserCard(target) });
}

app.put("/v1/admin/moderators/:userId/permissions", applyModeratorPermissions);
app.post("/v1/admin/moderators/:userId/permissions", applyModeratorPermissions);

app.post("/v1/admin/moderators/:userId/remove", (req, res) => {
  const ctx = requireAdmin(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!target) return res.status(404).json({ error: "not_found" });
  if (target.role === "admin") {
    return res.status(400).json({ error: "is_admin" });
  }
  target.role = "user";
  target.modPermissions = {};
  scheduleSave();
  return res.json({ ok: true });
});

app.post("/v1/admin/users/:userId/coins", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editCoins");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!target) return res.status(404).json({ error: "not_found" });
  const delta = Math.trunc(Number(req.body?.delta) || 0);
  if (!delta || Math.abs(delta) > 5000) {
    return res.status(400).json({ error: "bad_delta" });
  }
  applyLedger(uid, delta, delta > 0 ? "admin_grant" : "admin_grant", ctx.user.id);
  // negative admin_grant still works via applyLedger
  scheduleSave();
  return res.json({ ok: true, user: staffUserCard(target) });
});

app.post("/v1/admin/users/:userId/nickname", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editNick");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!target) return res.status(404).json({ error: "not_found" });
  const nick = String(req.body?.nickname || "")
    .trim()
    .slice(0, 18);
  if (nick.length < 2) {
    return res.status(400).json({
      error: "bad_nick",
      message: "Spitzname zu kurz.",
    });
  }
  // Admin darf umbenennen; Doppel-Namen werden manuell bereinigt
  target.nickname = nick;
  scheduleSave();
  return res.json({ ok: true, user: staffUserCard(target) });
});

app.post("/v1/admin/users/:userId/ban", (req, res) => {
  const ctx = requireStaff(req, res, "gm.block");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!target) return res.status(404).json({ error: "not_found" });
  ensureStaffFields(target);
  if (target.role === "admin" || SUPER_ADMIN_EMAILS.has(String(target.googleEmail || "").toLowerCase())) {
    return res.status(400).json({ error: "cannot_ban_admin" });
  }
  const banned = req.body?.banned !== false;
  target.banned = banned;
  target.bannedAt = banned ? Date.now() : null;
  target.bannedReason = banned
    ? String(req.body?.reason || "staff_ban").slice(0, 80)
    : null;
  if (banned) {
    const db = getDb();
    for (const [token, session] of Object.entries(db.sessions || {})) {
      if (session?.userId === target.id) delete db.sessions[token];
    }
  }
  scheduleSave();
  return res.json({ ok: true, user: staffUserCard(target) });
});

app.post("/v1/admin/users/:userId/delete", (req, res) => {
  const ctx = requireStaff(req, res, "gm.delete");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  if (uid === ctx.user.id) {
    return res.status(400).json({ error: "self_delete" });
  }
  const db = getDb();
  const target = db.users?.[uid];
  if (!target) return res.status(404).json({ error: "not_found" });
  ensureStaffFields(target);
  if (target.role === "admin" || SUPER_ADMIN_EMAILS.has(String(target.googleEmail || "").toLowerCase())) {
    return res.status(400).json({ error: "cannot_delete_admin" });
  }
  for (const [token, session] of Object.entries(db.sessions || {})) {
    if (session?.userId === uid) delete db.sessions[token];
  }
  delete db.users[uid];
  scheduleSave();
  return res.json({ ok: true });
});

app.get("/v1/live-notice", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({ ok: true, notice: getLiveNotice() });
});

app.post("/v1/admin/live-notice", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const message = String(req.body?.message || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 160);
  if (message.length < 2) {
    return res.status(400).json({ error: "empty", message: "Nachricht zu kurz." });
  }
  const now = Date.now();
  const authorNickname = staffDisplayName(ctx.user);
  const notice = {
    id: newId("ln"),
    message,
    authorNickname,
    authorId: ctx.user.id,
    createdAt: now,
    // 24h — auch wer die App später öffnet, sieht den Hinweis einmal
    expiresAt: now + LIVE_NOTICE_TTL_MS,
  };
  getDb().liveNotice = notice;
  scheduleSave();
  broadcastLiveNotice(getLiveNotice());
  return res.json({ ok: true, notice: getLiveNotice() });
});

app.get("/v1/admin/vouchers", (req, res) => {
  const ctx = requireStaff(req, res, "codes.view");
  if (!ctx) return;
  const list = Object.values(getDb().vouchers)
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, 100)
    .map((v) => ({
      id: v.id,
      code: v.code,
      coins: v.coins,
      maxRedeems: v.maxRedeems,
      redeemCount: v.redeemCount,
      expiresAt: v.expiresAt,
      revoked: Boolean(v.revoked),
      createdAt: v.createdAt,
    }));
  return res.json({ vouchers: list });
});

app.post("/v1/admin/vouchers", (req, res) => {
  const ctx = requireStaff(req, res, "codes.edit");
  if (!ctx) return;
  const coins = Math.min(10000, Math.max(1, Number(req.body?.coins) || 0));
  // maxRedeems = how many different people may redeem; each person only once
  const maxRedeems = Math.min(10000, Math.max(1, Number(req.body?.maxRedeems) || 1));
  const forever = Boolean(req.body?.forever);
  const days = Math.min(365, Math.max(1, Number(req.body?.validDays) || 30));
  const custom = String(req.body?.code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, 24);
  if (custom.length > 0 && custom.length < 4) {
    return res.status(400).json({ error: "code_too_short" });
  }
  const code = custom.length >= 4 ? custom : randomCode(10);
  const db = getDb();
  if (db.vouchers[code]) return res.status(409).json({ error: "code_exists" });
  const voucher = {
    id: newId("v"),
    code,
    coins,
    maxRedeems,
    redeemCount: 0,
    expiresAt: forever ? null : Date.now() + days * 86400000,
    createdAt: Date.now(),
    createdBy: ctx.user.id,
    revoked: false,
  };
  db.vouchers[code] = voucher;
  scheduleSave();
  return res.status(201).json({ voucher });
});

app.post("/v1/admin/vouchers/:code/revoke", (req, res) => {
  const ctx = requireStaff(req, res, "codes.revoke");
  if (!ctx) return;
  const code = String(req.params.code || "").toUpperCase();
  const voucher = getDb().vouchers[code];
  if (!voucher) return res.status(404).json({ error: "not_found" });
  voucher.revoked = true;
  scheduleSave();
  return res.json({ ok: true });
});

app.get("/v1/shop/packs", (req, res) => {
  const ctx = authUser(req);
  const ip = clientIp(req);
  res.json({
    enabled: Boolean(MOLLIE_API_KEY),
    packs: listShopPacks(ctx?.user || null, ip),
  });
});

function requireCoins(ctx, price, res) {
  if ((ctx.user.coins || 0) < price) {
    res.status(402).json({
      error: "no_coins",
      message: "Nicht genug Coins.",
      need: price,
      coins: ctx.user.coins || 0,
    });
    return false;
  }
  return true;
}

/** Itemshop: Emoji für Coins kaufen (Mehrfachkauf erlaubt). */
app.post("/v1/shop/buy-emoji", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const emoji = String(req.body?.emoji || "").trim().slice(0, 8);
  if (!emoji || !EMOJI_SHOP_PRICES[emoji]) {
    return res.status(400).json({
      error: "unknown_item",
      message: "Dieses Emoji gibt es im Shop nicht.",
    });
  }
  const price = Number(EMOJI_SHOP_PRICES[emoji]) || 0;
  if (price < 1) return res.status(400).json({ error: "bad_price" });
  if (!isKnownInventoryItem("emojis", emoji)) {
    return res.status(400).json({ error: "unknown_item", message: "Dieses Emoji gibt es im Shop nicht." });
  }
  if (!requireCoins(ctx, price, res)) return;
  if (!applyLedger(ctx.user.id, -price, "buy_emoji", emoji)) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  const inv = ensureInventory(ctx.user);
  inv.emojis[emoji] = Math.min(999, (Number(inv.emojis[emoji]) || 0) + 1);
  bumpShopPurchase("emojis", emoji);
  flushSave();
  return res.json({
    ok: true,
    emoji,
    owned: inv.emojis[emoji],
    price,
    user: publicUser(ctx.user),
  });
});

app.post("/v1/shop/buy-theme", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const themeId = String(req.body?.themeId || "").trim().slice(0, 32);
  if (!themeId || THEME_SHOP_PRICES[themeId] == null) {
    return res.status(400).json({ error: "unknown_item", message: "Hintergrund unbekannt." });
  }
  const price = Number(THEME_SHOP_PRICES[themeId]) || 0;
  const inv = ensureInventory(ctx.user);
  if (inv.themes.includes(themeId)) {
    return res.json({ ok: true, themeId, alreadyOwned: true, user: publicUser(ctx.user) });
  }
  if (price > 0 && !requireCoins(ctx, price, res)) return;
  if (price > 0 && !applyLedger(ctx.user.id, -price, "buy_theme", themeId)) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  inv.themes.push(themeId);
  bumpShopPurchase("themes", themeId);
  flushSave();
  return res.json({ ok: true, themeId, price, user: publicUser(ctx.user) });
});

app.post("/v1/shop/buy-sticker", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const emoji = String(req.body?.emoji || "").trim().slice(0, 8);
  if (!emoji || STICKER_SHOP_PRICES[emoji] == null) {
    return res.status(400).json({ error: "unknown_item", message: "Sticker unbekannt." });
  }
  const price = Number(STICKER_SHOP_PRICES[emoji]) || 0;
  if (price < 1) return res.status(400).json({ error: "bad_price" });
  if (!isKnownInventoryItem("stickers", emoji)) {
    return res.status(400).json({ error: "unknown_item", message: "Sticker unbekannt." });
  }
  if (!requireCoins(ctx, price, res)) return;
  if (!applyLedger(ctx.user.id, -price, "buy_sticker", emoji)) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  const inv = ensureInventory(ctx.user);
  inv.stickers[emoji] = Math.min(999, (Number(inv.stickers[emoji]) || 0) + 1);
  bumpShopPurchase("stickers", emoji);
  flushSave();
  return res.json({
    ok: true,
    emoji,
    owned: inv.stickers[emoji],
    price,
    user: publicUser(ctx.user),
  });
});

app.post("/v1/shop/buy-pet", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const emoji = String(req.body?.emoji || "").trim().slice(0, 8);
  if (!emoji || PET_SHOP_PRICES[emoji] == null) {
    return res.status(400).json({ error: "unknown_item", message: "Begleiter unbekannt." });
  }
  const price = Number(PET_SHOP_PRICES[emoji]) || 0;
  const inv = ensureInventory(ctx.user);
  if (inv.pets.includes(emoji)) {
    return res.json({ ok: true, emoji, alreadyOwned: true, user: publicUser(ctx.user) });
  }
  if (price > 0 && !requireCoins(ctx, price, res)) return;
  if (price > 0) {
    if (!applyLedger(ctx.user.id, -price, "buy_pet", emoji)) {
      return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
    }
    trackAch(ctx.user, "coins_spent", price);
  }
  inv.pets.push(emoji);
  bumpShopPurchase("pets", emoji);
  trackAch(ctx.user, "pets_bought", 1);
  syncAchInventoryMetrics(ctx.user);
  flushSave();
  return res.json({ ok: true, emoji, price, user: publicUser(ctx.user) });
});

app.post("/v1/me/equip-pet", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const emoji = String(req.body?.emoji || "").trim().slice(0, 8);
  const inv = ensureInventory(ctx.user);
  if (!emoji || !inv.pets.includes(emoji)) {
    return res.status(400).json({ error: "not_owned" });
  }
  inv.equippedPet = emoji;
  trackAch(ctx.user, "pet_equips", 1);
  // Profil-Begleiter mitziehen
  if (!ctx.user.profileCanvas || typeof ctx.user.profileCanvas !== "object") {
    ctx.user.profileCanvas = {};
  }
  ctx.user.profileCanvas.companionEmoji = emoji;
  scheduleSave();
  // Avatare in aktiven Lobbys aktualisieren
  for (const room of rooms.values()) {
    let inRoom = false;
    for (const sock of room.sockets.values()) {
      if (sock.luvUserId === ctx.user.id) {
        inRoom = true;
        break;
      }
    }
    if (inRoom) broadcastPeerCount(room);
  }
  return res.json({
    ok: true,
    equippedPet: emoji,
    user: publicUser(ctx.user),
  });
});

app.get("/v1/me/inventory", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  migrateStickerInventoryIfNeeded(ctx.user);
  const inv = ensureInventory(ctx.user);
  scheduleSave();
  return res.json({
    ok: true,
    emojis: inv.emojis,
    themes: inv.themes,
    stickers: inv.stickers,
    pets: inv.pets,
    equippedPet: inv.equippedPet || DEFAULT_PET,
  });
});

const MAX_DRAW_TEMPLATES = 24;

function ensureDrawTemplates(user) {
  if (!Array.isArray(user.drawTemplates)) user.drawTemplates = [];
  return user.drawTemplates;
}

function publicDrawTemplate(t) {
  if (!t) return null;
  return {
    id: t.id,
    strokes: t.strokes,
    createdAt: t.createdAt || 0,
  };
}

app.get("/v1/me/templates", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const list = ensureDrawTemplates(ctx.user)
    .map(publicDrawTemplate)
    .filter(Boolean)
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  return res.json({ ok: true, templates: list, count: list.length });
});

app.post("/v1/me/templates", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const strokes = sanitizeTemplateParts(req.body?.strokes);
  if (!strokes || !strokes.length) {
    return res.status(400).json({ error: "empty", message: "Bitte etwas zeichnen." });
  }
  const list = ensureDrawTemplates(ctx.user);
  if (list.length >= MAX_DRAW_TEMPLATES) {
    return res.status(400).json({
      error: "limit",
      message: `Maximal ${MAX_DRAW_TEMPLATES} Vorlagen.`,
    });
  }
  const entry = {
    id: `tpl_${crypto.randomBytes(8).toString("hex")}`,
    strokes,
    createdAt: Date.now(),
  };
  list.push(entry);
  trackAch(ctx.user, "templates_created", 1);
  scheduleSave();
  return res.status(201).json({ ok: true, template: publicDrawTemplate(entry) });
});

app.delete("/v1/me/templates/:id", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const list = ensureDrawTemplates(ctx.user);
  const before = list.length;
  ctx.user.drawTemplates = list.filter((t) => t && t.id !== id);
  if (ctx.user.drawTemplates.length === before) {
    return res.status(404).json({ error: "not_found" });
  }
  scheduleSave();
  return res.json({ ok: true });
});

/** —— Erfolge / Daily / Streak —— */
app.get("/v1/me/achievements", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  syncAchInventoryMetrics(ctx.user);
  const a = ach.ensureDaily(ctx.user, todayKey());
  if (a.progress._activeDayMarked !== todayKey()) {
    a.progress._activeDayMarked = todayKey();
    trackAch(ctx.user, "active_days", 1);
  }
  scheduleSave();
  return res.json({ ok: true, ...ach.publicAchievementsState(ctx.user, todayKey()) });
});

app.post("/v1/me/achievements/ping", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const metric = String(req.body?.metric || "").trim().slice(0, 40);
  // Nur weiche UI-Metriken — Wirtschaft/Lobby/Tips nur serverseitig tracken.
  // amount immer 1 (Client darf nicht multiplizieren).
  const amount = 1;
  const allowed = new Set([
    "gallery_opens",
    "social_opens",
    "tutorial_done",
    "tutorial_draw",
    "memories_opened",
    "profile_views",
    "moments_saved",
    "moments_shared",
    "lobby_opens",
    "reactions_sent",
    "profile_saves",
    "quiet_hours_set",
    "emoji_bar_edits",
  ]);
  if (!allowed.has(metric)) {
    return res.status(400).json({ error: "bad_metric" });
  }
  // Anti-Spam: max. 30 Pings / Minute / User
  if (!rateLimit(`achping:${ctx.user.id}`, 30, 60_000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const result = trackAch(ctx.user, metric, amount);
  scheduleSave();
  return res.json({
    ok: true,
    unlocked: result?.unlocked || [],
    coinsGranted: result?.coinsGranted || 0,
    dailyJustCompleted: Boolean(result?.dailyJustCompleted),
    streak: result?.streak || 0,
    state: ach.publicAchievementsState(ctx.user, todayKey()),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/me/achievements/daily/claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const result = ach.claimDailyReward(
    ctx.user,
    todayKey(),
    (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref)
  );
  if (!result.ok) {
    return res.status(400).json({
      error: result.error,
      message: result.message,
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    coinsGranted: result.coinsGranted,
    state: ach.publicAchievementsState(ctx.user, todayKey()),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/me/achievements/:id/claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const result = ach.claimAchievement(
    ctx.user,
    req.params.id,
    todayKey(),
    (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
    (user, kind, itemId) => safeGiveItem(user, kind, itemId),
    (user, kind, itemId) => userAlreadyOwnsUnique(user, kind, itemId)
  );
  if (!result.ok) {
    return res.status(400).json({
      error: result.error,
      message: result.message,
    });
  }
  if (result.itemGranted) {
    syncAchInventoryMetrics(ctx.user);
  }
  scheduleSave();
  return res.json({
    ok: true,
    coinsGranted: result.coinsGranted || 0,
    itemGranted: result.itemGranted || null,
    achievementId: result.achievementId,
    state: ach.publicAchievementsState(ctx.user, todayKey()),
    user: publicUser(ctx.user),
  });
});

/** —— Spieler-Marktplatz (Nasebär-Stil) —— */
function marketItemMeta(kind, itemId) {
  const id = String(itemId || "").trim();
  if (kind === "pets") {
    return { category: "pets", emoji: id, label: id, sellable: id !== DEFAULT_PET && PET_SHOP_PRICES[id] != null };
  }
  if (kind === "themes") {
    const labels = {
      meadow: "Wiese",
      forest: "Wald",
      sunset: "Abendrot",
      night: "Nacht",
      snow: "Schnee",
      blossom: "Blüte",
      ocean: "Meer",
      rain: "Regen",
      autumn: "Herbst",
      stars: "Sterne",
      cabin: "Hütte",
      lake: "See",
      lavender: "Lavendel",
      hearth: "Kamin",
      dawn: "Morgengrauen",
      desert: "Wüste",
      bamboo: "Bambus",
      mist: "Nebel",
      golden: "Goldstunde",
      iceberg: "Eisberg",
      sakura: "Sakura",
      coral: "Koralle",
      vineyard: "Weinberg",
      storm: "Gewitter",
      candy: "Candy",
      ember: "Glut",
      volcano: "Vulkan",
      aurora: "Nordlicht",
      meteor: "Sternschnuppen",
      galaxy: "Galaxie"
    };
    return {
      category: "themes",
      emoji: "🖼️",
      label: labels[id] || id,
      sellable: id !== "meadow" && THEME_SHOP_PRICES[id] != null,
    };
  }
  if (kind === "stickers") {
    return {
      category: "stickers",
      emoji: id,
      label: id,
      // Achievement-Exclusives nicht verkaufen
      sellable: STICKER_SHOP_PRICES[id] != null && !isAchievementSticker(id),
    };
  }
  if (kind === "emojis") {
    return {
      category: "emojis",
      emoji: id,
      label: id,
      // Nur Katalog-Emojis (Starter-Minimum bleibt in takeItem geschützt)
      sellable: EMOJI_SHOP_PRICES[id] != null,
    };
  }
  return null;
}

/** Markt-Hub-Kacheln: neueste Angebote + meistgekaufte Shop-Items. */
app.get("/v1/market/hub", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const ip = clientIp(req);
  return res.json({
    ok: true,
    marketNewest: newestMarketListings(2),
    shopTop: topShopPurchases(2),
    coinNewest: listShopPacks(ctx.user, ip).slice(0, 2),
  });
});

app.get("/v1/market", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  sweepExpiredMarketListings();
  trackAch(ctx.user, "market_opens", 1);
  const mode = String(req.query.mode || "market") === "private" ? "private" : "market";
  const category = String(req.query.category || "all").trim() || "all";
  const q = String(req.query.q || "").trim().slice(0, 40);
  const data = market.aggregateMarket(getDb(), ctx.user.id, { category, q, mode });
  return res.json({ ok: true, ...data });
});

/** Angebote zu einem Item (Nasebär-Drill-down). */
app.get("/v1/market/offers", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const mode = String(req.query.mode || "market") === "private" ? "private" : "market";
  const kind = String(req.query.kind || "").trim();
  const itemId = String(req.query.itemId || "").trim().slice(0, 24);
  if (!["pets", "themes", "stickers", "emojis"].includes(kind) || !itemId) {
    return res.status(400).json({ error: "bad_item", message: "Item ungültig." });
  }
  const data = market.listOffersForItem(getDb(), ctx.user.id, { kind, itemId, mode });
  return res.json({ ok: true, ...data });
});

app.get("/v1/market/mine", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  sweepExpiredMarketListings();
  const listings = market.ensureMarket(getDb());
  const mine = Object.values(listings)
    .filter((e) => e && e.sellerId === ctx.user.id && e.status === "open")
    .map((e) => market.listingPublic(e, ctx.user.id, getDb()))
    .filter(Boolean)
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  return res.json({ ok: true, listings: mine, count: mine.length });
});

app.post("/v1/market/list", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const kind = String(req.body?.kind || "").trim();
  const itemId = String(req.body?.itemId || "").trim().slice(0, 24);
  const priceCoins = Math.max(0, Math.min(500, Math.floor(Number(req.body?.priceCoins) || 0)));
  const allowTrade = Boolean(req.body?.allowTrade);
  const isPrivate = Boolean(req.body?.private);
  const targetUserId = String(req.body?.targetUserId || "").trim() || null;
  if (!["pets", "themes", "stickers", "emojis"].includes(kind)) {
    return res.status(400).json({ error: "bad_kind", message: "Ungültige Kategorie." });
  }
  if (!allowTrade && priceCoins < 1) {
    return res.status(400).json({ error: "bad_price", message: "Preis mind. 1 Coin oder Tausch aktivieren." });
  }
  let tradeWantKind = null;
  let tradeWantItemId = null;
  let tradeWantLabel = null;
  if (allowTrade) {
    tradeWantKind = String(req.body?.tradeWantKind || "").trim();
    tradeWantItemId = String(req.body?.tradeWantItemId || "").trim().slice(0, 24);
    tradeWantLabel = String(req.body?.tradeWantLabel || "").trim().slice(0, 40) || null;
    if (!["pets", "themes", "stickers", "emojis"].includes(tradeWantKind) || !tradeWantItemId) {
      return res.status(400).json({
        error: "need_trade_want",
        message: "Beim Tausch musst du angeben, was du suchst.",
      });
    }
    const wantMeta = marketItemMeta(tradeWantKind, tradeWantItemId);
    if (!wantMeta?.sellable) {
      return res.status(400).json({
        error: "bad_trade_want",
        message: "Gesuchtes Tausch-Item ungültig.",
      });
    }
    if (!tradeWantLabel) tradeWantLabel = wantMeta.label || tradeWantItemId;
  }
  if (isPrivate && !targetUserId) {
    return res.status(400).json({ error: "need_target", message: "Privates Angebot braucht einen Empfänger." });
  }
  if (isPrivate && targetUserId === ctx.user.id) {
    return res.status(400).json({ error: "self", message: "Nicht an dich selbst." });
  }
  const meta = marketItemMeta(kind, itemId);
  if (!meta?.sellable || !isKnownInventoryItem(kind, itemId)) {
    return res.status(400).json({ error: "not_sellable", message: "Diesen Artikel kannst du nicht anbieten." });
  }
  if (!market.userOwnsItem(ctx.user, ensureInventory, kind, itemId)) {
    return res.status(400).json({ error: "not_owned", message: "Artikel nicht im Inventar." });
  }
  const equippedLock = marketItemEquippedLock(ctx.user, kind, itemId);
  if (equippedLock) {
    return res.status(400).json({ error: "equipped", message: equippedLock });
  }
  // Item aus Inventar nehmen (Reservierung)
  if (!market.takeItemFromUser(ctx.user, ensureInventory, kind, itemId)) {
    return res.status(400).json({ error: "not_owned", message: "Artikel nicht verfügbar." });
  }
  cleanupProfileAfterItemRemoved(ctx.user, kind, itemId);
  const listings = market.ensureMarket(getDb());
  const id = market.newListingId();
  const entry = {
    id,
    kind,
    itemId,
    label: meta.label,
    emoji: meta.emoji,
    category: meta.category,
    priceCoins,
    allowTrade,
    tradeWantKind,
    tradeWantItemId,
    tradeWantLabel,
    private: isPrivate,
    targetUserId: isPrivate ? targetUserId : null,
    sellerId: ctx.user.id,
    sellerNickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    status: "open",
    createdAt: Date.now(),
    expiresAt: Date.now() + 14 * 24 * 60 * 60 * 1000,
  };
  listings[id] = entry;
  market.recordPrice(getDb(), kind, itemId, priceCoins);
  trackAch(ctx.user, "market_listed", 1);
  if (isPrivate) trackAch(ctx.user, "market_private", 1);
  syncAchInventoryMetrics(ctx.user);
  flushSave();
  return res.status(201).json({
    ok: true,
    listing: market.listingPublic(entry, ctx.user.id, getDb()),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/market/:id/cancel", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const listings = market.ensureMarket(getDb());
  const entry = listings[id];
  if (!entry || entry.status !== "open") {
    return res.status(404).json({ error: "not_found" });
  }
  if (entry.sellerId !== ctx.user.id) {
    return res.status(403).json({ error: "forbidden" });
  }
  if (expireOpenListing(entry)) {
    // Item bereits an Seller zurück
    syncAchInventoryMetrics(ctx.user);
    scheduleSave();
    return res.json({ ok: true, expired: true, user: publicUser(ctx.user) });
  }
  safeGiveItem(ctx.user, entry.kind, entry.itemId);
  entry.status = "cancelled";
  syncAchInventoryMetrics(ctx.user);
  flushSave();
  return res.json({ ok: true, user: publicUser(ctx.user) });
});

app.post("/v1/market/:id/buy", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const listings = market.ensureMarket(getDb());
  const entry = listings[id];
  if (!entry || entry.status !== "open") {
    return res.status(404).json({ error: "not_found", message: "Angebot weg." });
  }
  if (expireOpenListing(entry)) {
    scheduleSave();
    return res.status(404).json({ error: "expired", message: "Angebot abgelaufen." });
  }
  if (entry.sellerId === ctx.user.id) {
    return res.status(400).json({ error: "self", message: "Eigenes Angebot." });
  }
  if (entry.private && entry.targetUserId !== ctx.user.id) {
    return res.status(403).json({ error: "forbidden", message: "Privates Angebot." });
  }
  if (entry.allowTrade && entry.priceCoins <= 0) {
    return res.status(400).json({ error: "trade_only", message: "Nur Tausch — nutze /trade." });
  }
  if (userAlreadyOwnsUnique(ctx.user, entry.kind, entry.itemId)) {
    return res.status(400).json({
      error: "already_owned",
      message: "Du hast diesen Artikel schon.",
    });
  }
  if (!isKnownInventoryItem(entry.kind, entry.itemId)) {
    // Ungültiges Listing → Item an Seller zurück, kein Kauf
    const sellerBad = getDb().users?.[entry.sellerId];
    if (sellerBad) safeGiveItem(sellerBad, entry.kind, entry.itemId);
    entry.status = "cancelled";
    scheduleSave();
    return res.status(400).json({ error: "invalid_item", message: "Artikel ungültig." });
  }
  const price = Math.max(1, Number(entry.priceCoins) || 0);
  ensureDailyGrant(ctx.user);
  if ((ctx.user.coins || 0) < price) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  const seller = getDb().users?.[entry.sellerId];
  if (!seller) {
    entry.status = "cancelled";
    scheduleSave();
    return res.status(404).json({ error: "seller_gone" });
  }
  // Status zuerst sperren (kein Doppelkauf)
  entry.status = "sold";
  entry.buyerId = ctx.user.id;
  entry.soldAt = Date.now();
  const debited = applyLedger(ctx.user.id, -price, "market_buy", id);
  if (!debited) {
    entry.status = "open";
    delete entry.buyerId;
    delete entry.soldAt;
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  // Verkaufserlös erst nach Abholen durch den Verkäufer
  if (!Array.isArray(seller.pendingMarketSales)) seller.pendingMarketSales = [];
  seller.pendingMarketSales.push({
    id: entry.id,
    kind: entry.kind,
    itemId: entry.itemId,
    emoji: entry.emoji || "📦",
    label: entry.label || entry.itemId,
    priceCoins: price,
    soldAt: Date.now(),
    buyerNickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
  });
  if (seller.pendingMarketSales.length > 80) {
    seller.pendingMarketSales = seller.pendingMarketSales.slice(-80);
  }
  safeGiveItem(ctx.user, entry.kind, entry.itemId);
  market.recordPrice(getDb(), entry.kind, entry.itemId, price);
  trackAch(ctx.user, "market_bought", 1);
  trackAch(ctx.user, "coins_spent", price);
  trackAch(seller, "market_sold", 1);
  syncAchInventoryMetrics(ctx.user);
  syncAchInventoryMetrics(seller);
  flushSave();
  return res.json({
    ok: true,
    user: publicUser(ctx.user),
    listing: market.listingPublic(entry, ctx.user.id, getDb()),
  });
});

app.get("/v1/market/pending-sales", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const pending = Array.isArray(ctx.user.pendingMarketSales)
    ? ctx.user.pendingMarketSales
    : [];
  const totalCoins = pending.reduce(
    (s, x) => s + Math.max(0, Number(x.priceCoins) || 0),
    0
  );
  return res.json({
    ok: true,
    sales: pending.map((x) => ({
      id: x.id,
      kind: x.kind,
      itemId: x.itemId,
      emoji: x.emoji || "📦",
      label: x.label || x.itemId,
      priceCoins: Math.max(0, Number(x.priceCoins) || 0),
      soldAt: Number(x.soldAt) || 0,
      buyerNickname: x.buyerNickname || "Jemand",
    })),
    totalCoins,
    count: pending.length,
  });
});

app.post("/v1/market/claim-sales", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const pending = Array.isArray(ctx.user.pendingMarketSales)
    ? ctx.user.pendingMarketSales
    : [];
  if (!pending.length) {
    return res.json({
      ok: true,
      claimed: 0,
      totalCoins: 0,
      user: publicUser(ctx.user),
    });
  }
  const totalCoins = pending.reduce(
    (s, x) => s + Math.max(0, Number(x.priceCoins) || 0),
    0
  );
  ctx.user.pendingMarketSales = [];
  if (totalCoins > 0) {
    applyLedger(ctx.user.id, totalCoins, "market_sell_claim", `n=${pending.length}`);
  }
  flushSave();
  return res.json({
    ok: true,
    claimed: pending.length,
    totalCoins,
    user: publicUser(ctx.user),
  });
});

app.post("/v1/market/:id/trade", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const offerKind = String(req.body?.kind || "").trim();
  const offerItemId = String(req.body?.itemId || "").trim().slice(0, 24);
  const listings = market.ensureMarket(getDb());
  const entry = listings[id];
  if (!entry || entry.status !== "open" || !entry.allowTrade) {
    return res.status(404).json({ error: "not_found", message: "Kein Tausch-Angebot." });
  }
  if (expireOpenListing(entry)) {
    scheduleSave();
    return res.status(404).json({ error: "expired", message: "Angebot abgelaufen." });
  }
  if (entry.sellerId === ctx.user.id) {
    return res.status(400).json({ error: "self" });
  }
  if (entry.private && entry.targetUserId !== ctx.user.id) {
    return res.status(403).json({ error: "forbidden" });
  }
  if (!entry.tradeWantKind || !entry.tradeWantItemId) {
    return res.status(400).json({
      error: "trade_want_missing",
      message: "Tausch-Angebot ungültig (kein Gesuch).",
    });
  }
  if (offerKind !== entry.tradeWantKind || offerItemId !== entry.tradeWantItemId) {
    return res.status(400).json({
      error: "wrong_offer",
      message: `Gesucht: ${entry.tradeWantLabel || entry.tradeWantItemId}`,
    });
  }
  if (userAlreadyOwnsUnique(ctx.user, entry.kind, entry.itemId)) {
    return res.status(400).json({
      error: "already_owned",
      message: "Du hast diesen Artikel schon.",
    });
  }
  if (userAlreadyOwnsUnique(getDb().users?.[entry.sellerId], offerKind, offerItemId)) {
    return res.status(400).json({
      error: "seller_already_owns",
      message: "Anbieter hat dein Tausch-Item schon.",
    });
  }
  const meta = marketItemMeta(offerKind, offerItemId);
  if (!meta?.sellable) {
    return res.status(400).json({ error: "not_sellable" });
  }
  if (!isKnownInventoryItem(entry.kind, entry.itemId) || !isKnownInventoryItem(offerKind, offerItemId)) {
    return res.status(400).json({ error: "invalid_item", message: "Artikel ungültig." });
  }
  const equippedLock = marketItemEquippedLock(ctx.user, offerKind, offerItemId);
  if (equippedLock) {
    return res.status(400).json({ error: "equipped", message: equippedLock });
  }
  if (!market.takeItemFromUser(ctx.user, ensureInventory, offerKind, offerItemId)) {
    return res.status(400).json({ error: "not_owned", message: "Dein Tauschartikel fehlt." });
  }
  cleanupProfileAfterItemRemoved(ctx.user, offerKind, offerItemId);
  const seller = getDb().users?.[entry.sellerId];
  if (!seller) {
    safeGiveItem(ctx.user, offerKind, offerItemId);
    entry.status = "cancelled";
    flushSave();
    return res.status(404).json({ error: "seller_gone" });
  }
  entry.status = "traded";
  entry.buyerId = ctx.user.id;
  entry.tradedAt = Date.now();
  entry.tradeReceivedKind = offerKind;
  entry.tradeReceivedItemId = offerItemId;
  // Buyer bekommt Listing-Item, Seller bekommt Offer-Item
  safeGiveItem(ctx.user, entry.kind, entry.itemId);
  safeGiveItem(seller, offerKind, offerItemId);
  trackAch(ctx.user, "market_trades", 1);
  trackAch(seller, "market_trades", 1);
  syncAchInventoryMetrics(ctx.user);
  syncAchInventoryMetrics(seller);
  flushSave();
  return res.json({ ok: true, user: publicUser(ctx.user) });
});

app.post("/v1/shop/checkout", async (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!MOLLIE_API_KEY) {
    return res.status(503).json({
      error: "shop_disabled",
      message: "Shop kommt bald — bis dahin Daily Coins & Gutscheine.",
    });
  }
  const pack = PACKS[String(req.body?.packId || "")];
  if (!pack) return res.status(400).json({ error: "invalid_pack" });
  if (pack.expiresAt && Date.now() >= pack.expiresAt) {
    return res.status(400).json({ error: "offer_gone", message: "Dieses Angebot ist nicht mehr verfügbar." });
  }
  const ip = clientIp(req);
  if (pack.oncePerUserAndIp && !canClaimIntroOffer(ctx.user, ip)) {
    return res.status(403).json({
      error: "offer_used",
      message: "Dieses Angebot hast du schon genutzt.",
    });
  }
  let quantity = Math.floor(Number(req.body?.quantity) || 1);
  if (!Number.isFinite(quantity) || quantity < 1) quantity = 1;
  if (pack.oncePerUserAndIp) quantity = 1;
  quantity = Math.min(20, quantity);
  const unitEur = Number(pack.amountEur);
  if (!Number.isFinite(unitEur) || unitEur <= 0) {
    return res.status(400).json({ error: "invalid_pack" });
  }
  const totalEur = (unitEur * quantity).toFixed(2);
  const totalCoins = pack.coins * quantity;
  try {
    const body = {
      amount: { currency: "EUR", value: totalEur },
      description:
        quantity > 1
          ? `LUV ${pack.label} ×${quantity}`
          : `LUV ${pack.label}`,
      redirectUrl: PUBLIC_SHOP_REDIRECT,
      webhookUrl: MOLLIE_WEBHOOK_URL,
      metadata: {
        userId: ctx.user.id,
        packId: pack.id,
        coins: String(totalCoins),
        quantity: String(quantity),
        ip: ip || "",
      },
    };
    const resp = await fetch("https://api.mollie.com/v2/payments", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${MOLLIE_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });
    const data = await resp.json();
    if (!resp.ok) {
      return res.status(502).json({ error: "mollie_error", detail: data });
    }
    const db = getDb();
    db.payments[data.id] = {
      id: data.id,
      userId: ctx.user.id,
      packId: pack.id,
      coins: totalCoins,
      quantity,
      ip: ip || "",
      status: data.status,
      createdAt: Date.now(),
      credited: false,
    };
    scheduleSave();
    const checkoutUrl = data._links?.checkout?.href;
    return res.json({ paymentId: data.id, checkoutUrl });
  } catch (e) {
    return res.status(502).json({ error: "mollie_unreachable", message: String(e.message || e) });
  }
});

app.post("/v1/webhooks/mollie", async (req, res) => {
  const id = String(req.body?.id || req.query?.id || "");
  if (!id || !MOLLIE_API_KEY) return res.status(200).send("ok");
  try {
    const resp = await fetch(`https://api.mollie.com/v2/payments/${id}`, {
      headers: { Authorization: `Bearer ${MOLLIE_API_KEY}` },
    });
    const data = await resp.json();
    if (!resp.ok) return res.status(200).send("ok");
    const db = getDb();
    let payment = db.payments[id];
    if (!payment) {
      // Ohne lokalen Checkout-Record: nur mit gültigem Pack aus PACKS (kein Metadata-Coins)
      const packId = String(data.metadata?.packId || "");
      const pack = PACKS[packId];
      const userId = String(data.metadata?.userId || "");
      if (!pack || !userId || !db.users[userId]) {
        return res.status(200).send("ok");
      }
      const qtyMeta = Math.min(
        20,
        Math.max(1, Math.floor(Number(data.metadata?.quantity) || 1))
      );
      payment = {
        id,
        userId,
        packId: pack.id,
        coins: (Number(pack.coins) || 0) * qtyMeta,
        quantity: qtyMeta,
        status: data.status,
        createdAt: Date.now(),
        credited: false,
        ip: String(data.metadata?.ip || ""),
      };
      db.payments[id] = payment;
    }
    payment.status = data.status;
    const packId = payment.packId || data.metadata?.packId;
    const pack = packId ? PACKS[packId] : null;
    const qty = Math.min(
      20,
      Math.max(
        1,
        Math.floor(
          Number(payment.quantity) ||
            Number(data.metadata?.quantity) ||
            1
        )
      )
    );
    payment.quantity = qty;
    const creditCoins = pack
      ? (Number(pack.coins) || 0) * qty
      : Number(payment.coins) || 0;
    if (creditCoins > 0) payment.coins = creditCoins;
    // credited sofort setzen (vor weiteren awaits) → kein Double-Credit bei Parallel-Webhooks
    if (
      data.status === "paid" &&
      !payment.credited &&
      payment.userId &&
      creditCoins > 0
    ) {
      payment.credited = true;
      applyLedger(payment.userId, creditCoins, "mollie_purchase", id);
      if (pack) bumpPackPurchase(pack.id, qty);
      if (pack?.oncePerUserAndIp) {
        const user = db.users[payment.userId];
        if (user) user.introOfferUsed = true;
        ensureIntroDb(db);
        const ip = payment.ip || data.metadata?.ip || "";
        if (ip) db.introOffersByIp[ip] = payment.userId;
      }
    }
    scheduleSave();
  } catch {
    // acknowledge to avoid retries storm; next webhook can retry
  }
  return res.status(200).send("ok");
});


app.post("/v1/rooms/random-match", (req, res) => {
  try {
  cleanupRooms();
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  healJoinedRoomsFromMembership(ctx.user);
  reconcileAbandonedLobbyOwnership(ctx.user);
  pruneHostedRooms(ctx.user);
  const hosted = ensureHostedRooms(ctx.user);
  const joined = ensureJoinedRooms(ctx.user);
  const findActiveRandom = () => {
    const check = (code) => {
      let room = rooms.get(code);
      if (!room) {
        const saved = getDb().rooms?.[code];
        if (saved && typeof saved === "object") {
          room = hydrateRoom(code, saved);
          rooms.set(code, room);
        }
      }
      if (!room?.isRandom) return null;
      if (!Array.isArray(room.memberUserIds) || !room.memberUserIds.includes(ctx.user.id)) {
        return null;
      }
      return code;
    };
    for (const code of Object.keys(hosted || {})) {
      const hit = check(code);
      if (hit) return hit;
    }
    for (const code of Object.keys(joined || {})) {
      const hit = check(code);
      if (hit) return hit;
    }
    for (const [code, room] of rooms.entries()) {
      if (
        room?.isRandom &&
        Array.isArray(room.memberUserIds) &&
        room.memberUserIds.includes(ctx.user.id)
      ) {
        return code;
      }
    }
    return null;
  };
  const existing = findActiveRandom();
  if (existing) {
    return res.status(409).json({
      error: "already_in_random",
      message: "Du bist schon in einer Random-Lobby. Bitte zuerst verlassen.",
      code: existing,
    });
  }

  const RANDOM_CAP = 5;
  let targetCode = null;
  let targetRoom = null;
  for (const [code, room] of rooms.entries()) {
    if (!room?.isRandom) continue;
    healRoomMembership(room);
    const cap = Math.min(MAX_PEERS, Math.max(1, Number(room.capacity) || RANDOM_CAP));
    const count = Array.isArray(room.memberUserIds) ? room.memberUserIds.length : 0;
    if (count >= cap) continue;
    if (room.memberUserIds?.includes(ctx.user.id)) {
      targetCode = code;
      targetRoom = room;
      break;
    }
    if (!targetRoom) {
      targetCode = code;
      targetRoom = room;
    }
  }

  if (!targetRoom) {
    const hostedCount = Object.keys(hosted).length;
    if (hostedCount >= MAX_LOBBIES) {
      return res.status(403).json({
        error: "max_lobbies",
        message: `Maximal ${MAX_LOBBIES} Lobbys. Bitte eine verlassen, bevor eine neue Random-Lobby startet.`,
      });
    }
    let code = randomCode();
    while (rooms.has(code)) code = randomCode();
    const token = randomToken().slice(0, 32);
    const hostColorSide = normalizeHostColorSide(ctx.user.colorSide || "blue");
    const hostIdx = hostColorIndex(hostColorSide);
    targetRoom = {
      token,
      createdAt: Date.now(),
      lastActiveAt: Date.now(),
      sockets: new Map(),
      clearProposal: null,
      hostUserId: ctx.user.id,
      name: "Random",
      hostNickname: ctx.user.nickname || "Host",
      isFree: true,
      isRandom: true,
      capacity: RANDOM_CAP,
      hostColorSide,
      colorByUserId: { [ctx.user.id]: hostIdx },
      peakPeers: 1,
      lastCanvasAt: 0,
      memberUserIds: [ctx.user.id],
      joinAnnouncedUserIds: [ctx.user.id],
      publicShare: { day: "", count: 0, nextAt: 0 },
      publicProposal: null,
      strokes: [],
      stickers: [],
    };
    rooms.set(code, targetRoom);
    hosted[code] = {
      createdAt: Date.now(),
      isFree: true,
      isRandom: true,
      name: "Random",
      capacity: RANDOM_CAP,
      token,
      hostColorSide,
      colorByUserId: { [ctx.user.id]: hostIdx },
    };
    persistRooms();
    scheduleSave();
    return res.status(201).json({
      token,
      maxPeers: MAX_PEERS,
      capacity: RANDOM_CAP,
      isFree: true,
      isRandom: true,
      name: "Random",
      hostNickname: ctx.user.nickname || "Host",
      hostColorSide,
      suggestedColorIndex: hostIdx,
      role: "HOST",
      user: publicUser(ctx.user),
      ...inviteFor(code),
    });
  }

  // Join existing open random lobby
  rememberMember(targetRoom, ctx.user.id);
  healRoomMembership(targetRoom);
  cancelEmptyFreeLobbyTimer(targetCode);
  touchRoom(targetRoom);
  persistRooms();
  const isHost = Boolean(targetRoom.hostUserId && ctx.user.id === targetRoom.hostUserId);
  const suggestedColorIndex = assignRoomColor(targetRoom, ctx.user.id, isHost);
  if (isHost) {
    hosted[targetCode] = {
      createdAt: targetRoom.createdAt || Date.now(),
      isFree: true,
      isRandom: true,
      name: "Random",
      capacity: RANDOM_CAP,
      token: targetRoom.token,
      hostColorSide: normalizeHostColorSide(targetRoom.hostColorSide),
      colorByUserId: targetRoom.colorByUserId || {},
    };
  } else {
    rememberJoinedLobby(ctx.user, targetCode, targetRoom);
  }
  scheduleSave();
  const roster = roomRosterAll(targetRoom);
  return res.json({
    token: targetRoom.token,
    peers: uniqueConnectedCount(targetRoom),
    capacity: roomCapacity(targetRoom),
    maxPeers: MAX_PEERS,
    name: "Random",
    hostNickname: targetRoom.hostNickname || "Host",
    hostUserId: targetRoom.hostUserId || null,
    isFree: true,
    isRandom: true,
    hostColorSide: normalizeHostColorSide(targetRoom.hostColorSide),
    suggestedColorIndex,
    role: isHost ? "HOST" : "JOIN",
    members: roster.map((m) => m.nickname),
    memberList: roster,
    ...inviteFor(targetCode),
  });
  } catch (e) {
    console.error("random-match failed", e);
    return res.status(500).json({
      error: "server_error",
      message: "Random-Lobby gerade nicht verfügbar. Bitte erneut versuchen.",
    });
  }
});

/**
 * Lootbox kaufen: Coins abziehen, Belohnung als pending speichern (noch nicht im Inventar).
 * So bleibt der Kauf erhalten, auch wenn der Shop geschlossen wird.
 * Body: { quantity?: number } — max. so viele wie man sich leisten kann / LOOTBOX_MAX_QTY.
 */
app.post("/v1/shop/lootbox", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const qtyRaw = Number(req.body?.quantity);
  const quantity = Math.max(1, Math.min(LOOTBOX_MAX_QTY, Number.isFinite(qtyRaw) ? Math.floor(qtyRaw) : 1));
  const total = quantity * LOOTBOX_PRICE;
  if (!requireCoins(ctx, total, res)) return;
  const usePool = buildLootboxPoolForUser(ctx.user);
  if (!usePool.length) {
    return res.status(500).json({ error: "empty_pool", message: "Keine Items verfügbar." });
  }
  const pending = ensurePendingLootboxes(ctx.user);
  const created = [];
  for (let i = 0; i < quantity; i++) {
    const pick = lootbox.pickFromPool(usePool);
    if (!pick) break;
    const entry = {
      id: newId("lb"),
      kind: pick.kind,
      itemId: pick.itemId,
      emoji: pick.emoji,
      label: pick.label,
      shopPrice: pick.shopPrice,
      chancePercent: pick.chancePercent,
      purchasedAt: Date.now(),
    };
    pending.push(entry);
    created.push(entry);
  }
  if (!created.length) {
    return res.status(500).json({ error: "empty_pool", message: "Keine Items verfügbar." });
  }
  const charged = created.length * LOOTBOX_PRICE;
  if (!applyLedger(ctx.user.id, -charged, "lootbox", `qty:${created.length}`)) {
    // Rollback pending entries if ledger fails
    for (const e of created) {
      const idx = pending.findIndex((p) => p.id === e.id);
      if (idx >= 0) pending.splice(idx, 1);
    }
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  trackAch(ctx.user, "coins_spent", charged);
  flushSave();
  return res.json({
    ok: true,
    price: LOOTBOX_PRICE,
    quantity: created.length,
    total: charged,
    pending: pendingLootboxPublic(ctx.user),
    purchased: created.map((e) => ({
      id: e.id,
      kind: e.kind,
      itemId: e.itemId,
      emoji: e.emoji,
      label: e.label,
      shopPrice: e.shopPrice,
      chancePercent: e.chancePercent,
    })),
    user: publicUser(ctx.user),
  });
});

/** Ungeöffnete Lootboxen (nach Kauf, vor Tippen/Öffnen). */
app.get("/v1/shop/lootbox/pending", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({
    ok: true,
    pending: pendingLootboxPublic(ctx.user),
    price: LOOTBOX_PRICE,
    user: publicUser(ctx.user),
  });
});

/**
 * Eine pending Lootbox öffnen → Item ins Inventar.
 * Body: { id?: string } — ohne id die älteste.
 */
app.post("/v1/shop/lootbox/open", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const pending = ensurePendingLootboxes(ctx.user);
  if (!pending.length) {
    return res.status(404).json({
      error: "none_pending",
      message: "Keine ungeöffnete Lootbox.",
    });
  }
  const wantId = String(req.body?.id || "").trim();
  let idx = wantId ? pending.findIndex((p) => p.id === wantId) : 0;
  if (idx < 0) idx = 0;
  const entry = pending[idx];
  pending.splice(idx, 1);
  safeGiveItem(ctx.user, entry.kind, entry.itemId);
  bumpShopPurchase(entry.kind, entry.itemId);
  flushSave();
  return res.json({
    ok: true,
    item: {
      kind: entry.kind,
      itemId: entry.itemId,
      emoji: entry.emoji,
      label: entry.label,
      shopPrice: entry.shopPrice,
      chancePercent: entry.chancePercent,
    },
    pending: pendingLootboxPublic(ctx.user),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/rooms", (req, res) => {
  cleanupRooms();
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  pruneHostedRooms(ctx.user);
  const hostedCount = Object.keys(ctx.user.hostedRooms).length;
  if (hostedCount >= MAX_LOBBIES) {
    return res.status(403).json({
      error: "max_lobbies",
      message: `Maximal ${MAX_LOBBIES} Lobbys.`,
    });
  }
  const name = String(req.body?.name || "Zusammen").trim().slice(0, MAX_LOBBY_NAME_LENGTH) || "Zusammen";
  const hostColorSide = normalizeHostColorSide(req.body?.hostColorSide);
  const hostIdx = hostColorIndex(hostColorSide);
  const eligibleFree = evaluateCanCreateFreeLobby(ctx.user);
  let charged = 0;
  let isFree = false;
  if (eligibleFree) {
    isFree = true;
    ctx.user.freeLobbyCreateDay = todayKey();
  } else {
    ensureDailyGrant(ctx.user);
    if ((ctx.user.coins || 0) < LOBBY_CREATE_COST) {
      return res.status(402).json({
        error: "no_coins",
        message: `Lobby kostet ${LOBBY_CREATE_COST} Coins (1 kostenlose Lobby/Tag, max. 1 aktiv).`,
      });
    }
    if (!applyLedger(ctx.user.id, -LOBBY_CREATE_COST, "lobby_create", null)) {
      return res.status(402).json({
        error: "no_coins",
        message: `Lobby kostet ${LOBBY_CREATE_COST} Coins (1 kostenlose Lobby/Tag, max. 1 aktiv).`,
      });
    }
    charged = LOBBY_CREATE_COST;
  }
  let code = randomCode();
  while (rooms.has(code)) code = randomCode();
  const token = randomToken().slice(0, 32);
  const capacity = defaultLobbyCapacity(isFree);
  rooms.set(code, {
    token,
    createdAt: Date.now(),
    lastActiveAt: Date.now(),
    sockets: new Map(),
    clearProposal: null,
    hostUserId: ctx.user.id,
    name,
    hostNickname: ctx.user.nickname || "Host",
    isFree,
    capacity,
    hostColorSide,
    colorByUserId: { [ctx.user.id]: hostIdx },
    peakPeers: 1,
    lastCanvasAt: 0,
    memberUserIds: [ctx.user.id],
    joinAnnouncedUserIds: [ctx.user.id],
    publicShare: { day: "", count: 0, nextAt: 0 },
    publicProposal: null,
    strokes: [],
    stickers: [],
  });
  ensureHostedRooms(ctx.user);
  ctx.user.hostedRooms[code] = {
    createdAt: Date.now(),
    isFree,
    name,
    capacity,
    token,
    hostColorSide,
    colorByUserId: { [ctx.user.id]: hostIdx },
  };
  persistRooms();
  trackAch(ctx.user, "lobbies_created", 1);
  if (isFree) trackAch(ctx.user, "free_lobbies", 1);
  else trackAch(ctx.user, "paid_lobbies", 1);
  if (charged > 0) trackAch(ctx.user, "coins_spent", charged);
  const hostedN = Object.keys(ctx.user.hostedRooms || {}).length;
  ach.setMetricAtLeast(ctx.user, "lobbies_active", hostedN, todayKey(), (uid, d, r, ref) =>
    applyLedger(uid, d, r, ref)
  );
  scheduleSave();
  res.status(201).json({
    token,
    maxPeers: MAX_PEERS,
    capacity,
    isFree,
    name,
    hostNickname: ctx.user.nickname || "Host",
    hostColorSide,
    suggestedColorIndex: hostIdx,
    charged,
    user: publicUser(ctx.user),
    ...inviteFor(code),
  });
});

app.post("/v1/rooms/:code/slots", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && typeof saved === "object" && !rooms.has(code)) {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    } else {
      room = rooms.get(code) || null;
    }
  }
  if (!room) {
    ensureHostedRooms(ctx.user);
    const meta = ctx.user.hostedRooms[code];
    // Nur neu aufbauen, wenn wirklich kein Live-Raum existiert
    if (meta && typeof meta === "object" && !rooms.has(code)) {
      room = hydrateRoom(code, {
        token: meta.token,
        createdAt: meta.createdAt,
        hostUserId: ctx.user.id,
        name: meta.name || "Lobby",
        hostNickname: ctx.user.nickname || "Host",
        isFree: Boolean(meta.isFree),
        capacity: meta.capacity,
        hostColorSide: meta.hostColorSide,
        colorByUserId: meta.colorByUserId || {
          [ctx.user.id]: hostColorIndex(meta.hostColorSide),
        },
      });
      rooms.set(code, room);
      persistRooms();
    } else {
      room = rooms.get(code) || null;
    }
  }
  if (!room) return res.status(404).json({ error: "room_not_found" });
  // Jedes Mitglied darf Plätze freischalten (nicht nur der Host)
  const capacity = roomCapacity(room);
  if (capacity >= MAX_PEERS) {
    return res.status(400).json({ error: "capacity_full", message: `Maximal ${MAX_PEERS} Personen.` });
  }
  ensureDailyGrant(ctx.user);
  if ((ctx.user.coins || 0) < SLOT_COST) {
    return res.status(402).json({
      error: "no_coins",
      message: `Weiterer Platz kostet ${SLOT_COST} Coins.`,
    });
  }
  if (!applyLedger(ctx.user.id, -SLOT_COST, "lobby_slot", code)) {
    return res.status(402).json({
      error: "no_coins",
      message: `Weiterer Platz kostet ${SLOT_COST} Coins.`,
    });
  }
  room.capacity = capacity + 1;
  touchRoom(room);
  if (room.hostUserId) {
    const host = getDb().users[room.hostUserId];
    if (host) {
      ensureHostedRooms(host);
      if (host.hostedRooms[code]) {
        host.hostedRooms[code].capacity = room.capacity;
        scheduleSave();
      }
    }
  }
  persistRooms();
  scheduleSave();
  broadcastPeerCount(room);
  return res.json({
    ok: true,
    capacity: room.capacity,
    maxPeers: MAX_PEERS,
    charged: SLOT_COST,
    user: publicUser(ctx.user),
    ...publicRoom(room, code),
  });
});

/** Verlassen ohne die Lobby für andere zu zerstören — Ownership des Leavers immer weg. */
app.post("/v1/rooms/:code/leave", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && typeof saved === "object") {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  if (!room) {
    releaseHostedRoom(code, ctx.user.id);
    forgetJoinedLobby(ctx.user, code);
    if (ctx.user.freeLobbyCreateDay === todayKey()) {
      const stillFree = Object.entries(ctx.user.hostedRooms || {}).some(
        ([c, m]) => m?.isFree && c !== code && roomExistsAnywhere(c)
      );
      if (!stillFree) {
        ctx.user.freeLobbyCreateDay = null;
      }
    }
    scheduleSave();
    return res.json({ ok: true, alreadyGone: true });
  }

  const leftNick =
    String(ctx.user.nickname || "")
      .trim()
      .slice(0, 18) || "Jemand";
  for (const [peerId, sock] of [...room.sockets.entries()]) {
    if (sock.luvUserId && sock.luvUserId === ctx.user.id) {
      sock.luvLeft = true;
      try {
        sock.close(4001, "left");
      } catch {
        /* ignore */
      }
      room.sockets.delete(peerId);
    }
  }
  forgetMember(room, ctx.user.id);
  const roster = roomRosterAll(room);
  const online = roster.filter((m) => m.online).length;
  broadcastRoom(room, {
    type: "peer_left",
    userId: ctx.user.id,
    nickname: leftNick,
    peers: online,
    count: online,
    capacity: roomCapacity(room),
    members: roster.map((m) => m.nickname),
    memberList: roster,
  });
  broadcastPeerCount(room);
  ensureHostOrDissolve(room, code, { immediateEmpty: true });
  // Belt & suspenders: Leaver erscheint nie wieder in /v1/me/lobbies
  releaseHostedRoom(code, ctx.user.id);
  forgetJoinedLobby(ctx.user, code);
  if (rooms.has(code)) {
    touchRoom(room);
    persistRooms();
  }
  scheduleSave();

  return res.json({ ok: true });
});

/** Person in einer Lobby melden (optional Screenshot aus Galerie). */
app.post("/v1/rooms/:code/report-peer", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const targetUserId = String(req.body?.userId || "").trim();
  const targetNickname = String(req.body?.nickname || "")
    .trim()
    .slice(0, 18);
  if (!targetUserId && !targetNickname) {
    return res.status(400).json({ error: "bad_target", message: "Wen möchtest du melden?" });
  }
  if (targetUserId && targetUserId === ctx.user.id) {
    return res.status(400).json({ error: "self", message: "Dich selbst kannst du nicht melden." });
  }
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved) {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  const b64 = String(req.body?.imageBase64 || "").replace(/^data:image\/\w+;base64,/, "");
  let fileName = null;
  if (b64) {
    try {
      const buf = Buffer.from(b64, "base64");
      if (buf.length > 0 && buf.length <= 8 * 1024 * 1024) {
        ensurePeerReportDir();
        fileName = `${newId("primg")}.png`;
        fs.writeFileSync(path.join(PEER_REPORT_DIR, fileName), buf);
      }
    } catch {
      /* optional attachment */
    }
  }
  const report = {
    id: newId("prep"),
    status: "open",
    reportedAt: Date.now(),
    reporterUserId: ctx.user.id,
    reporterNickname: ctx.user.nickname || "Jemand",
    targetUserId: targetUserId || null,
    targetNickname: targetNickname || "Jemand",
    lobbyCode: code || null,
    lobbyName: room?.name || "Lobby",
    file: fileName,
  };
  peerReports()[report.id] = report;
  scheduleSave();
  console.log(
    `peer report ${report.id} lobby=${code} target=${report.targetNickname} by=${report.reporterNickname}`
  );
  return res.status(201).json({ ok: true, report: peerReportView(report) });
});

app.get("/v1/admin/peer-reports", (req, res) => {
  const ctx = requireStaff(req, res, "reports.view");
  if (!ctx) return;
  const list = Object.values(peerReports())
    .filter((r) => (r.status || "open") === "open")
    .sort((a, b) => Number(b.reportedAt) - Number(a.reportedAt))
    .slice(0, 80)
    .map(peerReportView);
  return res.json({ reports: list });
});

app.get("/v1/admin/peer-reports/:id/image", (req, res) => {
  const ctx = requireStaff(req, res, "reports.view");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const report = peerReports()[id];
  if (!report?.file) return res.status(404).end();
  const filePath = path.join(PEER_REPORT_DIR, path.basename(report.file));
  if (!fs.existsSync(filePath)) return res.status(404).end();
  res.setHeader("Content-Type", "image/png");
  res.setHeader("Cache-Control", "private, max-age=60");
  return fs.createReadStream(filePath).pipe(res);
});

app.post("/v1/admin/peer-reports/:id/keep", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const report = peerReports()[id];
  if (!report) return res.status(404).json({ error: "not_found" });
  report.status = "kept";
  report.resolvedAt = Date.now();
  report.resolvedBy = ctx.user.id;
  scheduleSave();
  return res.json({ ok: true, report: peerReportView(report) });
});

app.post("/v1/admin/peer-reports/:id/delete", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const report = peerReports()[id];
  if (!report) return res.status(404).json({ error: "not_found" });
  if (report.file) {
    const filePath = path.join(PEER_REPORT_DIR, path.basename(report.file));
    try {
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    } catch {
      /* ignore */
    }
  }
  report.status = "deleted";
  report.resolvedAt = Date.now();
  report.resolvedBy = ctx.user.id;
  scheduleSave();
  return res.json({ ok: true, report: peerReportView(report) });
});

/** Hilfe-Anfragen aus Einstellungen → Admin/Mod unter Meldungen */
app.post("/v1/help-messages", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const message = String(req.body?.message || "")
    .trim()
    .replace(/\r\n/g, "\n")
    .slice(0, 800);
  if (message.length < 5) {
    return res.status(400).json({
      error: "empty",
      message: "Bitte schreib mindestens ein paar Worte.",
    });
  }
  const openMine = Object.values(helpMessages()).filter(
    (m) =>
      (m.status || "open") === "open" &&
      m.userId === ctx.user.id
  );
  if (openMine.length >= 5) {
    return res.status(429).json({
      error: "too_many",
      message: "Du hast schon offene Hilfe-Nachrichten. Bitte warte kurz.",
    });
  }
  const newest = openMine.sort(
    (a, b) => Number(b.createdAt) - Number(a.createdAt)
  )[0];
  if (newest && Date.now() - Number(newest.createdAt || 0) < 60_000) {
    return res.status(429).json({
      error: "cooldown",
      message: "Gleich nochmal — warte eine Minute.",
    });
  }
  const entry = {
    id: newId("help"),
    message,
    userId: ctx.user.id,
    nickname: ctx.user.nickname || "Jemand",
    createdAt: Date.now(),
    status: "open",
  };
  helpMessages()[entry.id] = entry;
  scheduleSave();
  console.log(
    `help message ${entry.id} by=${entry.nickname} len=${message.length}`
  );
  return res.status(201).json({ ok: true, message: helpMessageView(entry) });
});

app.get("/v1/admin/help-messages", (req, res) => {
  const ctx = requireStaff(req, res, "reports.view");
  if (!ctx) return;
  const list = Object.values(helpMessages())
    .filter((m) => (m.status || "open") === "open")
    .sort((a, b) => Number(b.createdAt) - Number(a.createdAt))
    .slice(0, 100)
    .map(helpMessageView);
  return res.json({ messages: list });
});

app.post("/v1/admin/help-messages/:id/delete", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const entry = helpMessages()[id];
  if (!entry) return res.status(404).json({ error: "not_found" });
  entry.status = "deleted";
  entry.resolvedAt = Date.now();
  entry.resolvedBy = ctx.user.id;
  scheduleSave();
  return res.json({ ok: true, message: helpMessageView(entry) });
});

app.delete("/v1/rooms/:code", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && typeof saved === "object") {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  if (!room) {
    releaseHostedRoom(code, ctx.user.id);
    forgetJoinedLobby(ctx.user, code);
    scheduleSave();
    return res.json({ ok: true, alreadyGone: true });
  }
  // Auflösen nur noch, wenn niemand sonst verbunden ist
  const others = [...room.sockets.values()].filter(
    (s) => s.luvUserId && s.luvUserId !== ctx.user.id
  );
  if (others.length > 0 && room.hostUserId && room.hostUserId !== ctx.user.id) {
    return res.status(403).json({
      error: "not_host",
      message: "Nur der Host kann die Lobby auflösen — oder einfach verlassen.",
    });
  }
  if (others.length > 0) {
    // Host mit anderen: lieber leave-Verhalten statt Kick
    return res.status(409).json({
      error: "others_present",
      message: "Andere sind noch in der Lobby — bitte verlassen statt auflösen.",
    });
  }
  for (const sock of room.sockets.values()) {
    try {
      sock.close(4000, "room_closed");
    } catch {
      /* ignore */
    }
  }
  room.sockets.clear();
  forgetMember(room, ctx.user.id);
  dissolveEmptyLobby(room, code);
  releaseHostedRoom(code, ctx.user.id);
  forgetJoinedLobby(ctx.user, code);
  scheduleSave();
  return res.json({ ok: true });
});

app.post("/v1/rooms/:code/join", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  // Token optional — Host kann Raum wiederbeleben; Join braucht existierenden Raum
  const tokenHint = String(req.body?.token || "").trim();
  // Live-Raum hat Vorrang — niemals über einen verbundenen Host hydraten.
  let room = rooms.get(code) || resolveRoom(code, tokenHint, ctx.user);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && !rooms.has(code)) {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    } else {
      room = rooms.get(code) || null;
    }
  }
  if (!room) return res.status(404).json({ error: "room_not_found" });
  healRoomMembership(room);
  let capacity = roomCapacity(room);
  // Reconnect desselben Users zählt nicht extra
  const alreadyHere = [...room.sockets.values()].some((s) => s.luvUserId === ctx.user.id);
  const isKnownMember =
    (Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id)) ||
    room.hostUserId === ctx.user.id;
  if (!alreadyHere && uniqueConnectedCount(room) >= capacity) {
    if (isKnownMember) {
      room.capacity = Math.min(MAX_PEERS, Math.max(capacity, uniqueConnectedCount(room) + 1));
      capacity = room.capacity;
    } else {
      return res.status(409).json({
        error: "room_full",
        message: "Lobby ist voll — jemand kann weitere Plätze freischalten.",
      });
    }
  }
  rememberMember(room, ctx.user.id);
  healRoomMembership(room);
  capacity = roomCapacity(room);
  cancelEmptyFreeLobbyTimer(code);
  touchRoom(room);
  persistRooms();
  const isHost = Boolean(room.hostUserId && ctx.user.id === room.hostUserId);
  const suggestedColorIndex = assignRoomColor(room, ctx.user.id, isHost);
  const roster = roomRosterAll(room);
  return res.json({
    token: room.token,
    peers: uniqueConnectedCount(room),
    capacity,
    maxPeers: MAX_PEERS,
    name: room.name || "Lobby",
    hostNickname: room.hostNickname || "Host",
    hostUserId: room.hostUserId || null,
    isFree: Boolean(room.isFree),
    isRandom: Boolean(room.isRandom),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    suggestedColorIndex,
    members: roster.map((m) => m.nickname),
    memberList: roster,
    ...inviteFor(code),
  });
});

app.get("/v1/rooms/:code/preview", (req, res) => {
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved) {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  if (!room) return res.status(404).json({ error: "room_not_found", message: "Lobby nicht gefunden." });
  return res.json(publicRoom(room, code));
});

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

app.get("/v1/share-line", (_req, res) => {
  return res.json({ line: pickShareLine() });
});

app.get("/v1/public-canvases/random", (req, res) => {
  const rawExclude = String(req.query.exclude || "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 80);
  const picked = pickRandomPublicCanvas(rawExclude);
  if (!picked?.entry) return res.json({ available: false });
  const entry = picked.entry;
  return res.json({
    available: true,
    cycled: Boolean(picked.cycled),
    id: entry.id,
    lobbyName: entry.lobbyName || "Lobby",
    hostNickname: entry.hostNickname || "Jemand",
    memberNicknames: Array.isArray(entry.memberNicknames) ? entry.memberNicknames : [],
    nameLine: entry.nameLine || splashNameLine(entry),
    imageUrl: `/v1/public-canvases/${entry.id}/image`,
    createdAt: entry.createdAt,
    expiresAt: entry.expiresAt || Number(entry.createdAt) + PUBLIC_TTL_MS,
  });
});

/** Eigene veröffentlichte Galerie-Bilder (für Geräte-Sync) */
app.get("/v1/public-canvases/mine", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  cleanupPublicCanvases();
  const uid = ctx.user.id;
  const items = listPublicCanvasEntries()
    .filter(
      (e) =>
        e.hostUserId === uid ||
        (Array.isArray(e.memberUserIds) && e.memberUserIds.includes(uid))
    )
    .map((e) => ({
      id: e.id,
      lobbyName: e.lobbyName || "Galerie",
      hostNickname: e.hostNickname || "Jemand",
      memberNicknames: Array.isArray(e.memberNicknames) ? e.memberNicknames : [],
      nameLine: e.nameLine || splashNameLine(e),
      imageUrl: `/v1/public-canvases/${e.id}/image`,
      createdAt: e.createdAt,
      expiresAt: e.expiresAt || Number(e.createdAt) + PUBLIC_TTL_MS,
      source: e.source || null,
    }));
  return res.json({ items, count: items.length });
});

/** Galerie → öffentlich veröffentlichen (ohne Lobby-Abstimmung) */
app.post("/v1/public-canvases/publish", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (ctx.user.banned) {
    return res.status(403).json({
      error: "banned",
      message: "Dein Konto darf keine Bilder veröffentlichen.",
    });
  }
  const b64 = String(req.body?.imageBase64 || "").replace(/^data:image\/\w+;base64,/, "");
  if (!b64 || b64.length < 80 || b64.length > 6_000_000) {
    return res.status(400).json({
      error: "bad_image",
      message: "Bild fehlt oder ist zu groß.",
    });
  }
  let imageBuf;
  try {
    imageBuf = Buffer.from(b64, "base64");
  } catch {
    return res.status(400).json({
      error: "bad_image",
      message: "Bild ungültig.",
    });
  }
  if (imageBuf.length < 64 || imageBuf.length > 4_500_000) {
    return res.status(400).json({
      error: "bad_image",
      message: "Bild ungültig.",
    });
  }
  ensurePublicDir();
  const publicId = newId("pub");
  const fileName = `${publicId}.png`;
  fs.writeFileSync(path.join(PUBLIC_DIR, fileName), imageBuf);
  const nicknames = Array.isArray(req.body?.memberNicknames)
    ? req.body.memberNicknames
        .map((n) => String(n || "").trim().slice(0, 18))
        .filter(Boolean)
        .slice(0, 12)
    : [];
  const hostNick =
    String(ctx.user.nickname || "").trim().slice(0, 18) || "Jemand";
  if (!nicknames.some((n) => n.toLowerCase() === hostNick.toLowerCase())) {
    nicknames.unshift(hostNick);
  }
  const entry = {
    file: fileName,
    createdAt: Date.now(),
    expiresAt: Date.now() + PUBLIC_TTL_MS,
    lobbyCode: null,
    lobbyName: String(req.body?.lobbyName || "Galerie").trim().slice(0, 40) || "Galerie",
    hostNickname: hostNick,
    hostUserId: ctx.user.id,
    memberNicknames: nicknames,
    memberUserIds: [ctx.user.id],
    nameLine: "",
    source: "gallery",
  };
  entry.nameLine = splashNameLine(entry);
  publicCanvases()[publicId] = entry;
  scheduleSave();
  return res.status(201).json({
    ok: true,
    id: publicId,
    nameLine: entry.nameLine,
    imageUrl: `/v1/public-canvases/${publicId}/image`,
    expiresAt: entry.expiresAt,
  });
});

/** Veröffentlichung zurücknehmen (nur eigener Upload) */
app.post("/v1/public-canvases/:id/unpublish", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const publicId = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  if (!publicId) return res.status(400).json({ error: "bad_id" });
  const entry = publicCanvases()[publicId];
  if (!entry) {
    return res.status(404).json({
      error: "not_found",
      message: "Veröffentlichung nicht gefunden.",
    });
  }
  if (entry.hostUserId && entry.hostUserId !== ctx.user.id) {
    return res.status(403).json({
      error: "forbidden",
      message: "Nur du kannst deine Veröffentlichung zurücknehmen.",
    });
  }
  try {
    const filePath = path.join(PUBLIC_DIR, path.basename(entry.file || ""));
    if (entry.file && fs.existsSync(filePath)) fs.unlinkSync(filePath);
  } catch {
    /* ignore */
  }
  delete publicCanvases()[publicId];
  // Offene Meldungen zu diesem Bild schließen
  const reports = publicReports();
  for (const [rid, report] of Object.entries(reports)) {
    if (report?.publicId === publicId && (report.status || "open") === "open") {
      report.status = "removed";
      report.resolvedAt = Date.now();
    }
  }
  scheduleSave();
  return res.json({ ok: true, id: publicId });
});

app.get("/v1/public-canvases/:id", (req, res) => {
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  if (!id) return res.status(404).json({ available: false });
  const entry = publicCanvases()[id];
  if (!entry?.file) return res.json({ available: false });
  const created = Number(entry.createdAt) || 0;
  if (created > 0 && Date.now() - created > PUBLIC_TTL_MS) {
    return res.json({ available: false });
  }
  return res.json({
    available: true,
    id,
    lobbyName: entry.lobbyName || "Lobby",
    hostNickname: entry.hostNickname || "Jemand",
    memberNicknames: Array.isArray(entry.memberNicknames) ? entry.memberNicknames : [],
    nameLine: entry.nameLine || splashNameLine(entry),
    imageUrl: `/v1/public-canvases/${id}/image`,
    createdAt: entry.createdAt,
    expiresAt: entry.expiresAt || created + PUBLIC_TTL_MS,
  });
});

app.get("/v1/public-canvases/:id/image", (req, res) => {
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  if (!id) return res.status(404).end();
  // Offene/gelöschte Meldungen: Datei ggf. noch für Admin-Review
  const entry = publicCanvases()[id];
  let fileName = entry?.file;
  if (!fileName) {
    const report = Object.values(publicReports()).find(
      (r) => r.publicId === id && r.file
    );
    fileName = report?.file;
  }
  if (!fileName) return res.status(404).end();
  const created = Number(entry?.createdAt) || 0;
  if (entry && created > 0 && Date.now() - created > PUBLIC_TTL_MS) {
    return res.status(404).end();
  }
  const filePath = path.join(PUBLIC_DIR, path.basename(fileName));
  if (!fs.existsSync(filePath)) return res.status(404).end();
  res.setHeader("Content-Type", "image/png");
  res.setHeader("Cache-Control", "public, max-age=600");
  return fs.createReadStream(filePath).pipe(res);
});

/** Nutzer meldet eine öffentliche Community-Leinwand */
app.post("/v1/public-canvases/:id/report", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const publicId = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  if (!publicId) return res.status(400).json({ error: "bad_id" });
  const entry = publicCanvases()[publicId];
  if (!entry?.file) {
    return res.status(404).json({
      error: "not_found",
      message: "Bild nicht mehr verfügbar.",
    });
  }
  const reports = publicReports();
  const already = Object.values(reports).find(
    (r) =>
      r.publicId === publicId &&
      r.reporterUserId === ctx.user.id &&
      (r.status || "open") === "open"
  );
  if (already) {
    return res.json({ ok: true, already: true, report: publicReportView(already) });
  }
  const report = {
    id: newId("rep"),
    publicId,
    status: "open",
    reportedAt: Date.now(),
    reporterUserId: ctx.user.id,
    reporterNickname: ctx.user.nickname || "Jemand",
    lobbyName: entry.lobbyName || "Lobby",
    hostNickname: entry.hostNickname || "Jemand",
    nameLine: entry.nameLine || splashNameLine(entry),
    hostUserId: entry.hostUserId || null,
    file: entry.file,
  };
  reports[report.id] = report;
  scheduleSave();
  return res.status(201).json({ ok: true, report: publicReportView(report) });
});

app.get("/v1/admin/public-reports", (req, res) => {
  const ctx = requireStaff(req, res, "reports.view");
  if (!ctx) return;
  const list = Object.values(publicReports())
    .filter((r) => (r.status || "open") === "open")
    .sort((a, b) => Number(b.reportedAt) - Number(a.reportedAt))
    .slice(0, 80)
    .map(publicReportView);
  return res.json({ reports: list });
});

app.post("/v1/admin/public-reports/:id/keep", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const report = publicReports()[id];
  if (!report) return res.status(404).json({ error: "not_found" });
  report.status = "kept";
  report.resolvedAt = Date.now();
  report.resolvedBy = ctx.user.id;
  scheduleSave();
  return res.json({ ok: true, report: publicReportView(report) });
});

app.post("/v1/admin/public-reports/:id/delete", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const report = publicReports()[id];
  if (!report) return res.status(404).json({ error: "not_found" });
  const publicId = report.publicId;
  const entry = publicCanvases()[publicId];
  const fileMeta = entry || report;
  deletePublicCanvasFile(fileMeta);
  if (entry) {
    delete publicCanvases()[publicId];
  }
  report.status = "deleted";
  report.resolvedAt = Date.now();
  report.resolvedBy = ctx.user.id;
  // Andere offene Meldungen zum selben Bild schließen
  for (const r of Object.values(publicReports())) {
    if (r.publicId === publicId && (r.status || "open") === "open") {
      r.status = "deleted";
      r.resolvedAt = Date.now();
      r.resolvedBy = ctx.user.id;
    }
  }
  let banned = false;
  const hostId = report.hostUserId || entry?.hostUserId;
  if (hostId) {
    const host = getDb().users[hostId];
    if (host && host.role !== "admin") {
      host.publicDeletedCount = Math.max(0, Number(host.publicDeletedCount) || 0) + 1;
      if (host.publicDeletedCount >= PUBLIC_DELETE_BAN_THRESHOLD) {
        banUserForPublicDeletes(host);
        banned = true;
      }
    }
  }
  scheduleSave();
  return res.json({
    ok: true,
    banned,
    publicDeletedCount: hostId
      ? Math.max(0, Number(getDb().users[hostId]?.publicDeletedCount) || 0)
      : 0,
    report: publicReportView(report),
  });
});

/** Landing /luv — OG-Preview mit zufälligem cozy Spruch (für WhatsApp & Co.). */
function serveLandingHtml(req, res) {
  const line = pickShareLine();
  const picked = pickRandomPublicCanvas()?.entry || null;
  const ogImage = picked
    ? publicCanvasImageUrl(picked.id)
    : "https://reineke.pro/downloads/luv/og.jpg?v=1813";
  const ogType = picked ? "image/png" : "image/jpeg";
  const description = landingShareDescription(line, picked);
  const imageAlt = picked
    ? publicCanvasCredit(picked)
    : "LUV — Nähe, die mitgeht";
  const title = "LUV — Nähe, die mitgeht";
  const indexPath = path.join(WEB_DIR, "index.html");
  let html = "";
  try {
    html = fs.readFileSync(indexPath, "utf8");
  } catch {
    html = `<!DOCTYPE html><html lang="de"><head><meta charset="UTF-8" /><title>${escapeHtml(
      title
    )}</title></head><body><p>LUV</p></body></html>`;
  }
  html = html
    .replace(/href="styles\.css"/g, 'href="/luv/styles.css"')
    .replace(/src="app\.js"/g, 'src="/luv/app.js"')
    .replace(
      /<meta name="description" content="[^"]*"\s*\/?>/i,
      `<meta name="description" content="${escapeHtml(description)}" />`
    );
  if (!/property="og:description"/i.test(html)) {
    const og = `
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="LUV" />
  <meta property="og:url" content="https://reineke.pro/luv/" />
  <meta property="og:title" content="${escapeHtml(title)}" />
  <meta property="og:description" content="${escapeHtml(description)}" />
  <meta property="og:image" content="${ogImage}" />
  <meta property="og:image:secure_url" content="${ogImage}" />
  <meta property="og:image:alt" content="${escapeHtml(imageAlt)}" />
  <meta property="og:image:type" content="${ogType}" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="1200" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="${escapeHtml(title)}" />
  <meta name="twitter:description" content="${escapeHtml(description)}" />
  <meta name="twitter:image" content="${ogImage}" />
  <meta name="twitter:image:alt" content="${escapeHtml(imageAlt)}" />`;
    html = html.replace(/<\/head>/i, `${og}\n</head>`);
  } else {
    html = html
      .replace(
        /property="og:description" content="[^"]*"/i,
        `property="og:description" content="${escapeHtml(description)}"`
      )
      .replace(
        /name="twitter:description" content="[^"]*"/i,
        `name="twitter:description" content="${escapeHtml(description)}"`
      )
      .replace(
        /property="og:image" content="[^"]*"/gi,
        `property="og:image" content="${ogImage}"`
      )
      .replace(
        /property="og:image:secure_url" content="[^"]*"/i,
        `property="og:image:secure_url" content="${ogImage}"`
      )
      .replace(
        /name="twitter:image" content="[^"]*"/i,
        `name="twitter:image" content="${ogImage}"`
      );
    if (/property="og:image:alt"/i.test(html)) {
      html = html.replace(
        /property="og:image:alt" content="[^"]*"/i,
        `property="og:image:alt" content="${escapeHtml(imageAlt)}"`
      );
    } else {
      html = html.replace(
        /(<meta property="og:image" content="[^"]*"\s*\/?>)/i,
        `$1\n  <meta property="og:image:alt" content="${escapeHtml(imageAlt)}" />`
      );
    }
  }
  res.status(200).type("html").send(html);
}

app.get("/", serveLandingHtml);
app.get("/landing", serveLandingHtml);

/** WhatsApp / Social Preview + Deep-Link Landing für /luv/j/:code */
app.get("/invite/:code", (req, res) => {
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  const room = rooms.get(code);
  const host = room?.hostNickname || "Jemand";
  const joinUrl = `https://reineke.pro/luv/j/${code}`;
  const deep = code ? `luv://join/${code}` : "https://reineke.pro/luv/";
  const picked = pickRandomPublicCanvas()?.entry || null;
  const ogImage = picked
    ? publicCanvasImageUrl(picked.id)
    : "https://reineke.pro/downloads/luv/og.jpg?v=1813";
  const ogType = picked ? "image/png" : "image/jpeg";
  const imageAlt = picked
    ? publicCanvasCredit(picked)
    : "LUV — Nähe, die mitgeht";
  const title = room
    ? `${host} will mit dir verbunden sein`
    : "LUV — Nähe, die mitgeht";
  const description = room
    ? inviteShareDescription(host, picked)
    : landingShareDescription(pickShareLine(), picked);
  const inviteLede = room
    ? invitePublicBlurb(room, host)
    : "Diese Verbindung ist gerade still. Der Host muss online sein — dann seid ihr wieder nah.";
  const found = Boolean(room);
  const previewBlock = picked
    ? `<figure class="public-preview">
      <img src="${escapeHtml(ogImage)}" alt="${escapeHtml(imageAlt)}" width="1200" height="1200" loading="lazy" />
      <figcaption>${escapeHtml(imageAlt)}</figcaption>
    </figure>`
    : "";

  // Immer 200 — WhatsApp/Link-Preview crawlen keine 404-Seiten für OG-Tags
  res
    .status(200)
    .type("html")
    .send(`<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
  <title>${escapeHtml(title)}</title>
  <meta name="description" content="${escapeHtml(description)}" />
  <meta name="theme-color" content="#0B0E14" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="LUV" />
  <meta property="og:url" content="${escapeHtml(joinUrl)}" />
  <meta property="og:title" content="${escapeHtml(title)}" />
  <meta property="og:description" content="${escapeHtml(description)}" />
  <meta property="og:image" content="${ogImage}" />
  <meta property="og:image:secure_url" content="${ogImage}" />
  <meta property="og:image:alt" content="${escapeHtml(imageAlt)}" />
  <meta property="og:image:type" content="${ogType}" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="1200" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="${escapeHtml(title)}" />
  <meta name="twitter:description" content="${escapeHtml(description)}" />
  <meta name="twitter:image" content="${ogImage}" />
  <meta name="twitter:image:alt" content="${escapeHtml(imageAlt)}" />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,700&family=Outfit:wght@400;500;600;700&display=swap" rel="stylesheet" />
  <link rel="stylesheet" href="/luv/styles.css" />
  <style>
    .join-stage { min-height: 100dvh; display: grid; place-content: center; padding: 2rem 1.25rem; text-align: center; }
    .join-stage .brand { font-family: Fraunces, serif; font-size: clamp(3rem, 10vw, 5rem); letter-spacing: 0.12em; margin: 0; color: #f4f1ec; }
    .join-stage .brand .c-l { color: #00b7e4; }
    .join-stage .brand .c-u {
      display: inline-block;
      background: linear-gradient(90deg, #00b7e4 0%, #c218a8 100%);
      -webkit-background-clip: text;
      background-clip: text;
      color: transparent;
      -webkit-text-fill-color: transparent;
    }
    .join-stage .brand .c-v { color: #c218a8; }
    .join-stage .headline { font-family: Fraunces, serif; font-weight: 500; font-size: clamp(1.4rem, 4vw, 2rem); margin: 1rem 0 0.5rem; }
    .join-stage .lede { opacity: 0.72; max-width: 28rem; margin: 0 auto 1.25rem; line-height: 1.5; }
    .join-stage .cta-row { display: flex; flex-direction: column; gap: 0.85rem; align-items: center; }
    .join-stage .download { text-decoration: none; }
    .public-preview {
      margin: 0 auto 1.5rem;
      max-width: min(22rem, 88vw);
      border-radius: 1.1rem;
      overflow: hidden;
      border: 1px solid rgba(255,255,255,0.12);
      background: rgba(0,0,0,0.28);
    }
    .public-preview img {
      display: block;
      width: 100%;
      height: auto;
      aspect-ratio: 1;
      object-fit: cover;
    }
    .public-preview figcaption {
      font-family: Outfit, system-ui, sans-serif;
      font-size: 0.78rem;
      line-height: 1.35;
      padding: 0.7rem 0.85rem 0.85rem;
      color: rgba(244,241,236,0.78);
      text-align: left;
    }
  </style>
</head>
<body>
  <div class="atmosphere" aria-hidden="true">
    <div class="wash wash-a"></div>
    <div class="wash wash-b"></div>
    <div class="grain"></div>
  </div>
  <main class="join-stage">
    <p class="brand" aria-label="LUV"><span class="c-l">L</span><span class="c-u">U</span><span class="c-v">V</span></p>
    <h1 class="headline">${escapeHtml(found ? `${host} will mit dir verbunden sein` : "Einladung")}</h1>
    <p class="lede">${escapeHtml(inviteLede)}</p>
    ${previewBlock}
    <div class="cta-row">
      <a class="download" id="openApp" href="${escapeHtml(deep)}">
        <span class="download-label">Jetzt verbinden</span>
        <span class="download-meta">Einladung öffnen</span>
      </a>
      <a class="download" href="https://reineke.pro/downloads/luv/LUV.apk" download="LUV.apk" style="opacity:.9">
        <span class="download-label">App herunterladen</span>
        <span class="download-meta">Android · APK</span>
      </a>
    </div>
  </main>
  <script>
    (function () {
      var deep = ${JSON.stringify(deep)};
      if (${found ? "true" : "false"} && deep.indexOf("luv://") === 0) {
        setTimeout(function () { window.location.href = deep; }, 450);
      }
    })();
  </script>
</body>
</html>`);
});

app.get("/v1/rooms/:code", (req, res) => {
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved) {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  if (!room) return res.status(404).json({ error: "room_not_found" });
  return res.json(publicRoom(room, code));
});

/** Host: Lobby umbenennen — sync an alle verbundenen Clients */
app.patch("/v1/rooms/:code", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved) {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    } else {
      const meta = ctx.user.hostedRooms?.[code];
      if (meta) {
        room = hydrateRoom(code, {
          token: meta.token,
          hostUserId: ctx.user.id,
          name: meta.name || "Lobby",
          hostNickname: ctx.user.nickname || "Host",
          isFree: Boolean(meta.isFree),
          capacity: meta.capacity,
          hostColorSide: meta.hostColorSide,
          colorByUserId: meta.colorByUserId,
        });
        rooms.set(code, room);
      }
    }
  }
  if (!room) {
    return res.status(404).json({ error: "room_not_found", message: "Lobby nicht gefunden." });
  }
  if (room.hostUserId !== ctx.user.id) {
    return res.status(403).json({ error: "not_host", message: "Nur der Host kann umbenennen." });
  }
  const name = String(req.body?.name || "").trim().slice(0, MAX_LOBBY_NAME_LENGTH);
  if (!name) {
    return res.status(400).json({ error: "bad_name", message: "Name fehlt." });
  }
  room.name = name;
  ensureHostedRooms(ctx.user);
  if (ctx.user.hostedRooms[code]) {
    ctx.user.hostedRooms[code].name = name;
  }
  touchRoom(room);
  persistRooms();
  scheduleSave();
  broadcastRoom(room, { type: "lobby_rename", name });
  return res.json({
    name,
    ...inviteFor(code),
  });
});

/** Host: Lobby bewusst wiederherstellen (z. B. nach Deploy) */
app.post("/v1/rooms/:code/ensure", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const token = String(req.body?.token || "").trim();
  const room = resolveRoom(code, token, ctx.user);
  if (!room) {
    return res.status(404).json({
      error: "room_not_found",
      message: "Lobby konnte nicht wiederhergestellt werden.",
    });
  }
  healRoomMembership(room);
  const roster = roomRosterAll(room);
  return res.json({
    token: room.token,
    peers: uniqueConnectedCount(room),
    capacity: roomCapacity(room),
    maxPeers: MAX_PEERS,
    name: room.name || "Lobby",
    hostNickname: room.hostNickname || "Host",
    isFree: Boolean(room.isFree),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    members: roster.map((m) => m.nickname),
    memberList: roster,
    ...inviteFor(code),
  });
});

function roomForAuth(code, token, user) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  let room = rooms.get(clean) || resolveRoom(clean, token, user);
  if (!room) {
    const saved = getDb().rooms?.[clean];
    if (saved) {
      room = hydrateRoom(clean, saved);
      rooms.set(clean, room);
    }
  }
  return room ? { code: clean, room } : null;
}

function memoryPublic(code, entry) {
  if (!entry?.file || !entry.releasedAt) return null;
  const expiresAt = Number(entry.releasedAt) + MEMORY_TTL_MS;
  if (Date.now() > expiresAt) return null;
  return {
    code,
    lobbyName: entry.lobbyName || "Lobby",
    releasedAt: entry.releasedAt,
    expiresAt,
    imageUrl: `/v1/rooms/${code}/memory/image`,
  };
}

/** Client lädt ein Abbild der Leinwand hoch (PNG base64) — bleibt bis Release, dann 24h. */
app.post("/v1/rooms/:code/canvas-snapshot", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const token = String(req.body?.token || "").trim();
  const found = roomForAuth(req.params.code, token, ctx.user);
  if (!found) return res.status(404).json({ error: "room_not_found" });
  const { code, room } = found;
  if (token && room.token !== token && room.hostUserId !== ctx.user.id) {
    return res.status(403).json({ error: "forbidden" });
  }
  rememberMember(room, ctx.user.id);
  const b64 = String(req.body?.imageBase64 || "").replace(/^data:image\/\w+;base64,/, "");
  if (!b64 || b64.length < 80 || b64.length > 6_000_000) {
    return res.status(400).json({ error: "bad_image" });
  }
  let buf;
  try {
    buf = Buffer.from(b64, "base64");
  } catch {
    return res.status(400).json({ error: "bad_image" });
  }
  if (buf.length < 64 || buf.length > 4_500_000) {
    return res.status(400).json({ error: "bad_image" });
  }
  ensureSnapshotDir();
  const fileName = `${code}.png`;
  const filePath = path.join(SNAPSHOT_DIR, fileName);
  fs.writeFileSync(filePath, buf);
  const mem = canvasMemories();
  const prev = mem[code] || {};
  mem[code] = {
    file: fileName,
    updatedAt: Date.now(),
    lobbyName: room.name || prev.lobbyName || "Lobby",
    // Neues Abbild vor Release überschreibt — releasedAt bleibt bis Cleanup
    releasedAt: prev.releasedAt || null,
  };
  // Frisches Bild vor Ablauf: wenn noch nicht released, nur speichern
  scheduleSave();
  persistRooms();
  return res.json({ ok: true, updatedAt: mem[code].updatedAt });
});

/** Aktivität auf der Leinwand melden (Presence / Öffnen). */
app.post("/v1/rooms/:code/canvas-touch", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const token = String(req.body?.token || "").trim();
  const found = roomForAuth(req.params.code, token, ctx.user);
  if (!found) return res.status(404).json({ error: "room_not_found" });
  const { code, room } = found;
  rememberMember(room, ctx.user.id);
  touchCanvasActivity(room, room.sockets.size);
  // Bei neuer Aktivität: pending Release zurücknehmen, wenn wieder jemand malt
  const mem = canvasMemories();
  if (mem[code]?.releasedAt) {
    // Nach Release bleibt das Abbild 24h zum Teilen — nicht löschen.
  }
  return res.json({
    ok: true,
    lastCanvasAt: room.lastCanvasAt,
    peakPeers: room.peakPeers || 1,
    coupleMode: (room.peakPeers || 1) <= 2,
  });
});

app.get("/v1/rooms/:code/memory", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const token = String(req.query?.token || req.headers["x-room-token"] || "").trim();
  const found = roomForAuth(req.params.code, token, ctx.user);
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const room = found?.room || rooms.get(code) || null;
  if (room) maybeReleaseMemory(code, room);
  const pub = memoryPublic(code, canvasMemories()[code]);
  if (!pub) return res.json({ available: false });
  return res.json({ available: true, ...pub });
});

app.get("/v1/rooms/:code/memory/image", (req, res) => {
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const entry = canvasMemories()[code];
  const pub = memoryPublic(code, entry);
  if (!pub || !entry?.file) return res.status(404).end();
  const filePath = path.join(SNAPSHOT_DIR, path.basename(entry.file));
  if (!fs.existsSync(filePath)) return res.status(404).end();
  res.setHeader("Content-Type", "image/png");
  res.setHeader("Cache-Control", "private, max-age=300");
  return fs.createReadStream(filePath).pipe(res);
});

app.get("/v1/me/memories", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  cleanupCanvasMemories();
  const codes = new Set();
  for (const code of Object.keys(ctx.user.hostedRooms || {})) codes.add(code);
  for (const [code, room] of rooms.entries()) {
    if (Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id)) {
      codes.add(code);
    }
  }
  for (const [code, data] of Object.entries(getDb().rooms || {})) {
    if (Array.isArray(data.memberUserIds) && data.memberUserIds.includes(ctx.user.id)) {
      codes.add(code);
    }
  }
  const list = [];
  for (const code of codes) {
    let room = rooms.get(code);
    if (!room) {
      const saved = getDb().rooms?.[code];
      if (saved) {
        room = hydrateRoom(code, saved);
        rooms.set(code, room);
      }
    }
    if (room) maybeReleaseMemory(code, room);
    const pub = memoryPublic(code, canvasMemories()[code]);
    if (pub) list.push(pub);
  }
  return res.json({ memories: list });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/v1/ws" });

// Ohne Listener crasht ein einzelner MASK-/Proxy-Fehler den ganzen API-Prozess —
// dann sehen alle nur noch sich selbst und Strokes kommen nicht an.
function attachWsErrorGuard(socket, label) {
  if (!socket || socket.luvErrorGuarded) return;
  socket.luvErrorGuarded = true;
  socket.on("error", (err) => {
    const msg = err?.message || String(err || "ws error");
    console.warn(`ws error ${label}:`, msg);
    // Nicht sofort terminate — Close reicht; sonst Reconnect-Sturm
    try {
      if (socket.readyState === 1) socket.close(1002, "protocol");
    } catch {
      try {
        socket.terminate();
      } catch {
        /* ignore */
      }
    }
  });
}

wss.on("error", (err) => {
  console.warn("wss error:", err?.message || err);
});

wss.on("connection", (socket, req) => {
  // SOFORT — vor close/send/history, sonst unhandled error → Prozess tot
  attachWsErrorGuard(socket, "pre-auth");

  const url = new URL(req.url || "", `http://${req.headers.host}`);
  const code = String(url.searchParams.get("code") || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const token = String(url.searchParams.get("token") || "");
  const role = String(url.searchParams.get("role") || "peer");
  const sessionToken = String(url.searchParams.get("session") || "");
  const user = userFromSessionToken(sessionToken);
  if (user?.banned) {
    try {
      socket.close(4403, "banned");
    } catch {
      /* ignore */
    }
    return;
  }

  const room = resolveRoom(code, token, user);
  if (!room) {
    try {
      socket.close(4401, "unauthorized");
    } catch {
      /* ignore */
    }
    return;
  }
  // Mehrere Geräte desselben Kontos dürfen in der Lobby verbunden bleiben.
  // Exklusivität gilt nur für die Leinwand (siehe presence → kickOtherCanvasSockets).
  healRoomMembership(room);
  let capacity = roomCapacity(room);
  if ((Number(room.capacity) || 0) < capacity) {
    room.capacity = capacity;
  }
  const others = uniqueConnectedCount(room);
  const isKnownMember = Boolean(
    user?.id &&
      ((Array.isArray(room.memberUserIds) && room.memberUserIds.includes(user.id)) ||
        room.hostUserId === user.id)
  );
  if (others >= capacity) {
    if (isKnownMember) {
      // Bekanntes Mitglied nie aussperren — Platz nachziehen
      room.capacity = Math.min(MAX_PEERS, Math.max(capacity, others + 1));
      capacity = room.capacity;
      persistRooms();
    } else {
      console.log(
        `ws room_full code=${code} others=${others} cap=${capacity} user=${user?.id || "?"}`
      );
      socket.close(4409, "room_full");
      return;
    }
  }
  if (user?.nickname && room.hostUserId && user.id === room.hostUserId) {
    room.hostNickname = user.nickname;
  }

  const peerId = `${role}-${crypto.randomBytes(4).toString("hex")}`;
  attachWsErrorGuard(socket, `code=${code} peer=${peerId}`);
  socket.luvPeerId = peerId;
  socket.luvUserId = user?.id || null;
  socket.luvNickname =
    String(user?.nickname || "")
      .trim()
      .slice(0, 18) || "Jemand";
  socket.luvCanDraw = true;
  socket.luvCanvasActive = false;
  const isHost = Boolean(user && room.hostUserId && user.id === room.hostUserId);
  const suggestedColorIndex = assignRoomColor(room, user?.id || null, isHost);
  socket.luvColorIndex = suggestedColorIndex;
  room.sockets.set(peerId, socket);
  cancelEmptyFreeLobbyTimer(code);
  rememberMember(room, user?.id);
  healRoomMembership(room);
  capacity = roomCapacity(room);
  const announceJoin = claimJoinAnnouncement(room, user?.id);
  const memberList = roomRosterAll(room);
  const connectedNow = memberList.filter((m) => m.online).length;
  room.peakPeers = Math.max(Number(room.peakPeers) || 1, connectedNow);
  touchRoom(room);
  persistRooms();

  socket.send(
    JSON.stringify({
      type: "welcome",
      code,
      peerId,
      peers: connectedNow,
      count: connectedNow,
      capacity,
      maxPeers: MAX_PEERS,
      name: room.name || "Lobby",
      hostNickname: room.hostNickname || "Host",
      hostUserId: room.hostUserId || null,
      isFree: Boolean(room.isFree),
      userId: user?.id || null,
      hostColorSide: normalizeHostColorSide(room.hostColorSide),
      suggestedColorIndex,
      peakPeers: room.peakPeers || 1,
      coupleMode: (room.peakPeers || 1) <= 2,
      members: memberList.map((m) => m.nickname),
      memberList,
      // Client kann veraltetes Token angleichen (verhindert Split-Lobbys)
      token: room.token || null,
    })
  );
  // Leinwand-Historie nach Welcome — überlebt App-/Server-Updates
  sendCanvasHistory(socket, room, code);
  // Offener Live-Hinweis nachreichen (auch wenn nicht live verbunden beim Senden)
  const pendingNotice = getLiveNotice();
  if (pendingNotice && socket.readyState === 1) {
    try {
      socket.send(
        JSON.stringify({
          type: "live_notice",
          id: pendingNotice.id,
          message: pendingNotice.message,
          authorNickname: pendingNotice.authorNickname,
          createdAt: pendingNotice.createdAt,
        })
      );
    } catch {
      /* ignore */
    }
  }
  // Offene Public-Abstimmung: erst relevant, wenn die Person die Leinwand öffnet (presence)
  if (room.publicProposal?.status === "capturing") {
    sendPublicVoteOpen(socket, room, code);
  }
  // Offene Clear-Abstimmung nachreichen (Quorum bleibt bei 2 Ja)
  if (room.clearProposal?.status === "open") {
    sendClearVoteOpen(socket, room);
  }
  // Nur beim allerersten Beitritt — nicht bei Offline/Reconnect
  if (announceJoin) {
    const joinedList = roomRosterAll(room);
    const joinedOnline = joinedList.filter((m) => m.online).length;
    broadcastRoom(
      room,
      {
        type: "peer_joined",
        nickname: socket.luvNickname || "Jemand",
        userId: user?.id || null,
        peers: joinedOnline,
        count: joinedOnline,
        capacity,
        members: joinedList.map((m) => m.nickname),
        memberList: joinedList,
        firstJoin: true,
      },
      socket
    );
  }
  broadcastPeerCount(room);
  console.log(
    `ws join code=${code} peer=${peerId} user=${user?.id || "?"} nick=${socket.luvNickname} members=${connectedNow}`
  );

  socket.on("message", (data) => {
    let text = typeof data === "string" ? data : data.toString("utf8");
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      return;
    }
    const type = json?.type;

    // Keepalive — Client-JSON-Ping (kein WS-Protokoll-Ping hinter Caddy)
    if (type === "ping") {
      try {
        if (socket.readyState === 1) socket.send(JSON.stringify({ type: "pong" }));
      } catch {
        /* ignore */
      }
      return;
    }
    if (type === "pong") return;

    if (type === "presence" || type === "recolor") {
      rememberSocketColor(room, socket, user, json.colorIndex);
    }

    if (type === "presence") {
      const wasActive = Boolean(socket.luvCanvasActive);
      socket.luvCanvasActive = json.active === true;
      if (socket.luvCanvasActive && user?.id) {
        kickOtherCanvasSockets(room, user.id, socket);
      }
      // Leinwand frisch geöffnet → History — aber nicht direkt nach Welcome-History
      // (sonst Clear/Replace-Blinken beim Reconnect mit offener Leinwand)
      if (socket.luvCanvasActive && !wasActive) {
        const sinceHistory = Date.now() - (Number(socket.luvHistoryAt) || 0);
        if (sinceHistory > 8_000) {
          sendCanvasHistory(socket, room, code);
        }
      }
      if (socket.luvNickname && !json.nickname) {
        json.nickname = socket.luvNickname;
      } else if (json.nickname) {
        socket.luvNickname = String(json.nickname).trim().slice(0, 18) || socket.luvNickname;
      }
      // Stabile IDs für Clients — verhindert doppelte/fehlende Avatare
      json.userId = socket.luvUserId || null;
      json.peerKey = socket.luvUserId || socket.luvNickname || peerId;
      if (Number.isFinite(Number(socket.luvColorIndex))) {
        json.colorIndex = socket.luvColorIndex;
      }
      text = JSON.stringify(json);
      if (room.publicProposal?.status === "open") {
        const changed = syncPublicRequiredVoters(room);
        if (socket.luvCanvasActive && user?.id) {
          sendPublicVoteOpen(socket, room, code);
        }
        if (changed) {
          const t = publicProposalTotals(room.publicProposal);
          broadcastRoom(room, {
            type: "public_vote_update",
            proposalId: room.publicProposal.id,
            yes: t.yes,
            no: t.no,
            total: t.total,
            rewardCoins: room.publicProposal.rewardCoins || 1,
          });
          if (allRequiredYes(room.publicProposal)) {
            maybeRequestPublicCapture(room, code);
          }
        } else if (wasActive !== socket.luvCanvasActive && allRequiredYes(room.publicProposal)) {
          maybeRequestPublicCapture(room, code);
        }
      }
    }

    if (type === "clear") {
      // Clients may not force-clear anymore — start proposal instead
      json = { type: "clear_propose", nickname: json.nickname };
    }

    if (json.type === "clear_propose") {
      if (room.clearProposal?.status === "open") {
        socket.send(JSON.stringify({ type: "clear_busy", proposalId: room.clearProposal.id }));
        return;
      }
      if (!user) {
        socket.send(
          JSON.stringify({
            type: "clear_blocked",
            error: "unauthorized",
            message: "Zum Löschen musst du angemeldet sein.",
          })
        );
        return;
      }
      ensureDailyGrant(user);
      if ((user.coins || 0) < CLEAR_COST) {
        socket.send(
          JSON.stringify({
            type: "clear_blocked",
            error: "no_coins",
            message: `Leinwand löschen kostet ${CLEAR_COST} Coin — keine freien Clears.`,
          })
        );
        return;
      }
      const proposalId = newId("clr");
      applyLedger(user.id, -CLEAR_COST, "clear_canvas", proposalId);
      const neededYes = clearVotesNeeded(room);
      room.clearProposal = {
        id: proposalId,
        lobbyCode: code,
        byPeerId: peerId,
        byUserId: user.id,
        byNickname: String(json.nickname || user.nickname || "Jemand").slice(0, 18),
        yesUserIds: [user.id],
        noUserIds: [],
        neededYes,
        status: "open",
        endsAt: Date.now() + CLEAR_VOTE_MS,
        charged: true,
        chargedUserId: user.id,
      };
      socket.send(
        JSON.stringify({
          type: "economy_ok",
          charged: true,
          reason: "clear_canvas",
          user: publicUser(user),
        })
      );
      for (const sock of room.sockets.values()) {
        if (sock.readyState === 1) {
          sock.send(JSON.stringify(clearVoteOpenPayload(room, room.clearProposal, sock.luvUserId || null)));
        }
      }
      setTimeout(() => {
        if (room.clearProposal?.id === proposalId && room.clearProposal.status === "open") {
          resolveClear(room, code);
        }
      }, CLEAR_VOTE_MS + 50);
      if (clearYesEnough(room.clearProposal)) {
        approveClear(room, code);
      }
      return;
    }

    if (type === "clear_vote") {
      const proposal = room.clearProposal;
      if (!proposal || proposal.status !== "open") return;
      if (proposal.id !== json.proposalId) return;
      if (!user?.id) return;
      proposal.yesUserIds = (proposal.yesUserIds || []).filter((id) => id !== user.id);
      proposal.noUserIds = (proposal.noUserIds || []).filter((id) => id !== user.id);
      if (json.yes === true || json.vote === "yes") proposal.yesUserIds.push(user.id);
      else proposal.noUserIds.push(user.id);
      const t = clearProposalTotals(proposal);
      broadcastRoom(room, {
        type: "clear_vote_update",
        proposalId: proposal.id,
        yes: t.yes,
        no: t.no,
        total: t.total,
      });
      if ((proposal.noUserIds || []).length > 0) {
        rejectClear(room, code, "rejected");
        return;
      }
      if (clearYesEnough(proposal)) {
        approveClear(room, code);
      }
      return;
    }

    if (type === "public_propose") {
      if (!user) {
        socket.send(
          JSON.stringify({
            type: "public_blocked",
            error: "unauthorized",
            message: "Zum Teilen musst du angemeldet sein.",
          })
        );
        return;
      }
      if (room.publicProposal?.status === "open" || room.publicProposal?.status === "capturing") {
        const canRestart =
          user.id === room.hostUserId || user.id === room.publicProposal.byUserId;
        if (!canRestart) {
          socket.send(
            JSON.stringify({
              type: "public_blocked",
              error: "busy",
              message: "Abstimmung läuft schon.",
            })
          );
          return;
        }
        // Festhängende / alte Abstimmung überschreiben — alle werden neu gefragt
        rejectPublicShare(room, code, "restarted");
      }
      const state = getPublicShareState(room);
      if (state.nextAt && Date.now() < state.nextAt) {
        const mins = Math.max(1, Math.ceil((state.nextAt - Date.now()) / 60000));
        socket.send(
          JSON.stringify({
            type: "public_blocked",
            error: "cooldown",
            message: `Öffentlich teilen geht in etwa ${mins} Min. wieder.`,
            nextAt: state.nextAt,
          })
        );
        return;
      }
      const rewardPreview = publicShareRewardForCount(state.count);
      const proposalId = newId("pubv");
      // Initiator ist gerade auf der Leinwand
      socket.luvCanvasActive = true;
      const requiredUserIds = buildPublicRequiredVoters(room, user.id);
      room.publicProposal = {
        id: proposalId,
        lobbyCode: code,
        byPeerId: peerId,
        byUserId: user.id,
        byNickname: String(json.nickname || user.nickname || "Jemand").slice(0, 18),
        yesUserIds: [user.id],
        noUserIds: [],
        requiredUserIds,
        status: "open",
        endsAt: Date.now() + PUBLIC_VOTE_MS,
        rewardCoins: rewardPreview.coins,
      };
      broadcastPublicVoteOpen(room);
      setTimeout(() => {
        if (
          room.publicProposal?.id === proposalId &&
          (room.publicProposal.status === "open" || room.publicProposal.status === "capturing")
        ) {
          resolvePublicShare(room, code);
        }
      }, PUBLIC_VOTE_MS + 50);
      if (allRequiredYes(room.publicProposal)) {
        maybeRequestPublicCapture(room, code);
      }
      return;
    }

    if (type === "public_vote") {
      const proposal = room.publicProposal;
      if (!proposal || proposal.status !== "open") return;
      if (proposal.id !== json.proposalId) return;
      if (!user?.id) return;
      // Wer abstimmt, ist auf der Leinwand
      socket.luvCanvasActive = true;
      syncPublicRequiredVoters(room);
      if (!proposal.requiredUserIds.includes(user.id)) {
        // Nicht (mehr) auf der Leinwand / nicht erforderlich
        return;
      }
      proposal.yesUserIds = (proposal.yesUserIds || []).filter((id) => id !== user.id);
      proposal.noUserIds = (proposal.noUserIds || []).filter((id) => id !== user.id);
      if (json.yes === true || json.vote === "yes") proposal.yesUserIds.push(user.id);
      else proposal.noUserIds.push(user.id);
      const t = publicProposalTotals(proposal);
      broadcastRoom(room, {
        type: "public_vote_update",
        proposalId: proposal.id,
        yes: t.yes,
        no: t.no,
        total: t.total,
        rewardCoins: proposal.rewardCoins || 1,
      });
      if ((proposal.noUserIds || []).length > 0) {
        rejectPublicShare(room, code, "rejected");
        return;
      }
      if (allRequiredYes(proposal)) {
        maybeRequestPublicCapture(room, code);
      }
      return;
    }

    if (type === "public_capture") {
      const proposal = room.publicProposal;
      if (!proposal || proposal.status !== "capturing") return;
      if (proposal.id !== json.proposalId) return;
      const b64 = String(json.imageBase64 || "").replace(/^data:image\/\w+;base64,/, "");
      if (!b64 || b64.length < 80 || b64.length > 6_000_000) {
        socket.send(
          JSON.stringify({
            type: "public_blocked",
            error: "bad_image",
            message: "Leinwand-Abbild fehlt oder ist zu groß.",
          })
        );
        return;
      }
      let imageBuf;
      try {
        imageBuf = Buffer.from(b64, "base64");
      } catch {
        socket.send(
          JSON.stringify({
            type: "public_blocked",
            error: "bad_image",
            message: "Leinwand-Abbild ungültig.",
          })
        );
        return;
      }
      if (imageBuf.length < 64 || imageBuf.length > 4_500_000) {
        socket.send(
          JSON.stringify({
            type: "public_blocked",
            error: "bad_image",
            message: "Leinwand-Abbild ungültig.",
          })
        );
        return;
      }
      finishPublicShare(room, code, imageBuf);
      return;
    }

    if (type === "game_start") {
      const gameType = String(json.game || "").toLowerCase();
      const allowed =
        gameType === "ttt" ||
        gameType === "words" ||
        Games.isInteractive(gameType);
      if (!allowed) return;
      if (!user) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "unauthorized",
            message: "Zum Spielen musst du angemeldet sein.",
          })
        );
        return;
      }
      ensureDailyGrant(user);
      if ((user.coins || 0) < GAME_COST) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "no_coins",
            message: `Spiel starten kostet ${GAME_COST} Coin.`,
          })
        );
        return;
      }
      applyLedger(user.id, -GAME_COST, "game_start", gameType);
      socket.send(
        JSON.stringify({
          type: "economy_ok",
          charged: true,
          reason: "game_start",
          user: publicUser(user),
        })
      );

      clearGameTimer(room);

      if (gameType === "words") {
        const options = pickWordOptions(3);
        room.game = {
          type: "words",
          status: "pick",
          overlay: false,
          drawerPeerId: peerId,
          drawerNickname: String(json.nickname || user.nickname || "Jemand").slice(0, 18),
          options,
          word: null,
          wordNorm: null,
          endsAt: 0,
          solvedBy: null,
        };
        broadcastPlayState(room);
        socket.send(
          JSON.stringify({
            type: "game_words_pick",
            options,
            drawerNickname: room.game.drawerNickname,
          })
        );
      } else if (Games.isInteractive(gameType)) {
        const peers = [...room.sockets.entries()].map(([id, sock]) => ({
          peerId: id,
          nickname: sock.luvNickname || "Jemand",
        }));
        if (!peers.some((p) => p.peerId === peerId)) {
          peers.unshift({
            peerId,
            nickname: String(json.nickname || user.nickname || "Jemand").slice(0, 18),
          });
        }
        room.game = Games.createGame(
          gameType,
          peerId,
          String(json.nickname || user.nickname || "Jemand").slice(0, 18),
          peers
        );
        broadcastPlayState(room);
      } else {
        // Tic-Tac-Toe Overlay
        room.game = {
          type: gameType,
          status: "active",
          overlay: true,
          drawerPeerId: peerId,
          drawerNickname: String(json.nickname || user.nickname || "Jemand").slice(0, 18),
        };
        broadcastPlayState(room);
        broadcastRoom(room, {
          type: "game_board",
          game: gameType,
          visible: true,
        });
      }
      return;
    }

    if (type === "game_stop") {
      stopRoomGame(room);
      return;
    }

    if (type === "game_action") {
      const game = room.game;
      if (!game || !Games.isInteractive(game.type)) return;
      const result = Games.applyAction(
        game,
        peerId,
        String(json.nickname || user?.nickname || socket.luvNickname || "Jemand"),
        json.action,
        json.payload || {}
      );
      room.game = result.game;
      if (result.chat) {
        broadcastRoom(room, {
          type: "game_guess_chat",
          nickname: result.chat.nickname,
          text: result.chat.text,
          ok: Boolean(result.chat.ok),
        });
      }
      if (result.error && result.error !== "early") {
        socket.send(
          JSON.stringify({
            type: "game_action_result",
            ok: false,
            error: result.error,
          })
        );
      }
      broadcastPlayState(room);
      return;
    }

    if (type === "game_pick") {
      const game = room.game;
      if (!game || game.type !== "words" || game.status !== "pick") return;
      if (game.drawerPeerId !== peerId) return;
      const choice = String(json.word || "");
      if (!game.options.includes(choice)) return;
      game.word = choice;
      game.wordNorm = normalizeGuess(choice);
      game.status = "draw";
      game.endsAt = Date.now() + WORDS_ROUND_MS;
      game.solvedBy = null;
      socket.send(
        JSON.stringify({
          type: "game_words_secret",
          word: choice,
          endsAt: game.endsAt,
        })
      );
      broadcastRoom(room, {
        type: "game_state",
        game: publicGameState(game),
      });
      startWordsRoundTimer(room);
      return;
    }

    if (type === "game_guess") {
      const game = room.game;
      if (!game || game.type !== "words" || game.status !== "draw") return;
      if (game.drawerPeerId === peerId) {
        socket.send(
          JSON.stringify({
            type: "game_guess_result",
            ok: false,
            message: "Du malst — die anderen raten.",
          })
        );
        return;
      }
      if (game.solvedBy) {
        socket.send(
          JSON.stringify({
            type: "game_guess_result",
            ok: false,
            message: "Schon erraten.",
          })
        );
        return;
      }
      const guess = String(json.text || "").trim().slice(0, 48);
      if (!guess) return;
      const nick = String(json.nickname || user?.nickname || "Jemand").slice(0, 18);
      const ok = normalizeGuess(guess) === game.wordNorm;

      // Jeder sieht alle Tipps als Blasen
      broadcastRoom(room, {
        type: "game_guess_chat",
        nickname: nick,
        text: guess,
        ok,
      });

      if (!ok) {
        socket.send(
          JSON.stringify({
            type: "game_guess_result",
            ok: false,
            message: "Leider nicht — weiter raten!",
          })
        );
        return;
      }

      // Erster Treffer beendet die Runde — Käufer bleibt Zeichner (kein Auto-Weiter)
      game.solvedBy = nick;
      game.status = "done";
      clearGameTimer(room);
      game.endsAt = 0;
      const secret = game.word;
      broadcastRoom(room, {
        type: "game_words_correct",
        winner: nick,
        word: secret,
        drawerNickname: game.drawerNickname,
      });
      broadcastRoom(room, {
        type: "game_state",
        game: publicGameState(game),
      });
      socket.send(
        JSON.stringify({
          type: "game_guess_result",
          ok: true,
          message: "Richtig!",
        })
      );
      return;
    }

    // Legacy free toggle — only hide; starting costs coins via game_start
    if (type === "game_board" && json.visible === false) {
      if (room.game && (room.game.type === json.game || !json.game)) {
        stopRoomGame(room);
      }
      relayToPeers(room, text, peerId);
      return;
    }

    if (type === "stroke") {
      // Zeichnen nur mit eingeloggt — kein Economy-/Inventar-Bypass ohne Session
      if (!user) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "auth_required",
            message: "Bitte einloggen zum Malen.",
          })
        );
        return;
      }
      let drawResult = null;
      drawResult = consumeDrawSession(user, code);
      if (!drawResult.ok) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "no_coins",
            message: "Keine freien Sessions/Coins — Zuschauen geht weiter.",
          })
        );
        return;
      }
      if (drawResult.charged || drawResult.free) {
        socket.send(
          JSON.stringify({
            type: "economy_ok",
            charged: Boolean(drawResult.charged),
            user: publicUser(user),
          })
        );
      }
      if (json.emoji && !userOwnsCanvasEmoji(user, json.emoji)) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "not_owned",
            message: "Dieses Emoji ist nicht in deinem Inventar.",
          })
        );
        return;
      }
      const stored = strokeFromSocketMessage(json, socket);
      if (!stored) return;
      appendRoomStroke(room, code, stored);
      touchCanvasActivity(room, room.sockets.size);
      if (user) {
        trackAch(user, "strokes", 1);
        if (stored.emoji) trackAch(user, "stickers_placed", 1);
        if (stored.templateParts) trackAch(user, "templates_placed", 1);
        if (drawResult && (drawResult.free || drawResult.charged) && !drawResult.already) {
          trackAch(user, "draw_sessions", 1);
        }
        const peers = uniqueConnectedCount(room);
        if (peers >= 2) trackAch(user, "draw_with_peers", 1);
        if (peers >= 4) trackAch(user, "draw_group4", 1);
        ach.setMetricAtLeast(user, "lobby_peak", room.peakPeers || peers, todayKey(), null);
      }
      // Relayed payload aus normalisiertem Stroke (stabile History)
      const relayPayload = {
        type: "stroke",
        id: stored.id,
        width: stored.width,
        nickname: stored.nickname,
        colorIndex: stored.colorIndex,
        authorId: stored.authorId,
        points: stored.points,
        colorLocked: Boolean(stored.colorLocked),
      };
      if (stored.emoji) relayPayload.emoji = stored.emoji;
      if (stored.templateParts) {
        relayPayload.templateParts = stored.templateParts;
        relayPayload.templateScale = stored.templateScale;
        relayPayload.templateRotation = stored.templateRotation;
      }
      const relay = JSON.stringify(relayPayload);
      relayToPeers(room, relay, peerId);
      // Legacy: Emoji-Strich zusätzlich als sticker_place für alte Clients
      if (stored.emoji && stored.points?.[0]) {
        const legacy = JSON.stringify({
          type: "sticker_place",
          id: stored.id,
          emoji: stored.emoji,
          x: stored.points[0].x,
          y: stored.points[0].y,
          nickname: stored.nickname,
        });
        relayToPeers(room, legacy, peerId);
      }
      return;
    }

    if (type === "undo") {
      removeRoomStroke(room, code, json.id);
    }

    if (type === "sticker_place") {
      if (!user) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "auth_required",
            message: "Bitte einloggen zum Platzieren.",
          })
        );
        return;
      }
      if (json.emoji && !userOwnsCanvasEmoji(user, json.emoji)) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "not_owned",
            message: "Dieser Sticker ist nicht in deinem Inventar.",
          })
        );
        return;
      }
      // Legacy → als Emoji-Strich in die gleiche History wie Linien
      const sticker = sanitizeStoredSticker({
        id: json.id,
        emoji: json.emoji,
        x: json.x,
        y: json.y,
        nickname: json.nickname || socket.luvNickname || null,
      });
      if (!sticker) return;
      const stored = sanitizeStoredStroke({
        id: sticker.id,
        points: [{ x: sticker.x, y: sticker.y }],
        width: 0,
        emoji: sticker.emoji,
        nickname: sticker.nickname,
        colorIndex: socket.luvColorIndex || 0,
        authorId: socket.luvUserId || socket.luvPeerId || null,
      });
      if (!stored) return;
      appendRoomStroke(room, code, stored);
      touchCanvasActivity(room, room.sockets.size);
      const strokeRelay = JSON.stringify({
        type: "stroke",
        id: stored.id,
        width: stored.width,
        nickname: stored.nickname,
        colorIndex: stored.colorIndex,
        authorId: stored.authorId,
        points: stored.points,
        emoji: stored.emoji,
        colorLocked: Boolean(stored.colorLocked),
      });
      const stickerRelay = JSON.stringify({
        type: "sticker_place",
        id: sticker.id,
        emoji: sticker.emoji,
        x: sticker.x,
        y: sticker.y,
        nickname: sticker.nickname,
      });
      relayToPeers(room, strokeRelay, peerId);
      relayToPeers(room, stickerRelay, peerId);
      return;
    }

    if (type === "sticker_remove") {
      const sid = String(json.id || "").trim();
      if (!sid) return;
      removeRoomStroke(room, code, sid);
      touchCanvasActivity(room, room.sockets.size);
      const undoRelay = JSON.stringify({ type: "undo", id: sid });
      const relay = JSON.stringify({ type: "sticker_remove", id: sid });
      relayToPeers(room, undoRelay, peerId);
      relayToPeers(room, relay, peerId);
      return;
    }

    if (type === "presence" && json.active === true) {
      touchCanvasActivity(room, room.sockets.size);
    }

    // Relay to others (clear, recolor, presence, …)
    relayToPeers(room, text, peerId);
  });

  socket.on("close", () => {
    const stillMapped = room.sockets.get(peerId) === socket;
    if (stillMapped) {
      room.sockets.delete(peerId);
    }
    // Ersetzt durch Reconnect oder schon per Leave entfernt — kein Extra-Broadcast
    if (socket.luvReplaced || socket.luvLeft) {
      return;
    }
    if (!stillMapped && !rooms.has(code)) {
      return;
    }
    if (!rooms.has(code)) return;

    const leftMembers = roomConnectedMembers(room).length;
    console.log(
      `ws close code=${code} peer=${peerId} leftMembers=${leftMembers} replaced=${!!socket.luvReplaced}`
    );
    broadcastPeerCount(room);
    ensureHostOrDissolve(room, code);
    if (!rooms.has(code)) return;

    // Clear-Abstimmung bleibt offen — Offline zählt nicht als Ablehnung
    if (room.clearProposal?.status === "open") {
      const t = clearProposalTotals(room.clearProposal);
      broadcastRoom(room, {
        type: "clear_vote_update",
        proposalId: room.clearProposal.id,
        yes: t.yes,
        no: t.no,
        total: t.total,
      });
    }
    // Public: wer die Leinwand verlässt, muss nicht mehr zustimmen
    if (room.publicProposal?.status === "open") {
      const changed = syncPublicRequiredVoters(room);
      if (changed) {
        const t = publicProposalTotals(room.publicProposal);
        broadcastRoom(room, {
          type: "public_vote_update",
          proposalId: room.publicProposal.id,
          yes: t.yes,
          no: t.no,
          total: t.total,
          rewardCoins: room.publicProposal.rewardCoins || 1,
        });
        if (allRequiredYes(room.publicProposal)) {
          maybeRequestPublicCapture(room, code);
        }
      }
    }
  });

});

restoreRoomsFromDisk();

// Letzter Sicherheitsnetz: ein WS-Protokollfehler darf die API nie killen.
process.on("uncaughtException", (err) => {
  const msg = String(err?.message || err || "");
  const code = err?.code || "";
  if (
    code === "WS_ERR_EXPECTED_MASK" ||
    code === "WS_ERR_INVALID_OPCODE" ||
    code === "WS_ERR_INVALID_UTF8" ||
    code === "WS_ERR_UNSUPPORTED_DATA_PAYLOAD_LENGTH" ||
    code === "WS_ERR_UNEXPECTED_RSV_1" ||
    code === "WS_ERR_UNEXPECTED_RSV_2_3" ||
    msg.includes("MASK must be set") ||
    msg.includes("Invalid WebSocket frame")
  ) {
    console.error("swallowed fatal ws frame error (kept process alive):", msg);
    return;
  }
  console.error("uncaughtException", err);
  process.exit(1);
});

process.on("unhandledRejection", (reason) => {
  console.error("unhandledRejection", reason);
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(
    `luv-api :${PORT} rooms=${rooms.size} maxPeers=${MAX_PEERS} maxLobbies=${MAX_LOBBIES} clearCost=${CLEAR_COST} lobbyCreate=${LOBBY_CREATE_COST} shop=${Boolean(MOLLIE_API_KEY)}`
  );
});

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
  flushSaveSync,
  DATA_FILE,
} = require("./store");
const { pickShareLine } = require("./share_lines");
const ach = require("./achievements");
const market = require("./market");
const lootbox = require("./lootbox");
const marriage = require("./marriage");
const weddingCeremony = require("./wedding_ceremony");
const weddingGifts = require("./wedding_gifts");
const roomLayouts = require("./room_layouts");
const homeFeed = require("./home_feed");
const shopCatalog = require("./shop_catalog");
const petImages = require("./pet_images");
const playBilling = require("./play_billing");
const playIntegrity = require("./play_integrity");
const inviteTrial = require("./invite_trial");
const weddingInviteCard = require("./wedding_invite_card");
const itemTrade = require("./item_trade");
const itemLabels = require("./item_labels");
const notifyPhrases = require("./notify_phrases");
const dailyTasks = require("./daily_tasks");
const shopCalendar = require("./shop_calendar");
const seasonEvents = require("./events");
const adminWebAuth = require("./admin_web_auth");
const maintenance = require("./maintenance");
const iapLedger = require("./iap_ledger");
const accountPush = require("./account_push");
const shopChangeQueue = require("./shop_change_queue");
const bugReports = require("./bug_reports");
const redisUtil = require("./redis_util");
ach.bindGetDb(getDb);
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
/** Web-Admin / Staff-Only-Login: kürzere Sessions (Standard 12h). */
const STAFF_SESSION_TTL_MS = Number(
  process.env.STAFF_SESSION_TTL_MS || 12 * 60 * 60 * 1000
);
/** @deprecated Mollie entfernt — nur noch Play Billing */
const MOLLIE_API_KEY = process.env.MOLLIE_API_KEY || "";
function shopPurchasesEnabled() {
  return playBilling.isConfigured();
}
/** Feste Super-Admins per Google-E-Mail (nicht Spitzname). Komma-getrennt via Env erweiterbar. */
const SUPER_ADMIN_EMAILS = new Set(
  String(process.env.SUPER_ADMIN_EMAILS || "xstruppelstrumpf@gmail.com")
    .split(",")
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean)
);

/**
 * Google-E-Mails, die sich auch über Sideload-APK anmelden dürfen
 * (ohne PLAY_RECOGNIZED). Alle anderen brauchen die Play-Store-App.
 * Default = Super-Admins. Env: SIDELOAD_LOGIN_EMAILS=a@x.de,b@y.de
 */
const SIDELOAD_LOGIN_EMAILS = new Set(
  String(
    process.env.SIDELOAD_LOGIN_EMAILS ||
      [
        // Du / Emrys (gleiche Google-Mail), Jane, Zaubermaus, Till
        "xstruppelstrumpf@gmail.com",
        "foxyspoint@gmail.com",
        "pinselpoint@gmail.com",
        "tihapoint@gmail.com",
      ].join(",")
  )
    .split(",")
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean)
);

function isSideloadLoginAllowed(email) {
  return SIDELOAD_LOGIN_EMAILS.has(String(email || "").trim().toLowerCase());
}

/**
 * Play-App-Pflicht für Google-Login. Sideload nur für Allowlist-E-Mails.
 * Geräte-Integrity (kein Emulator) gilt weiter für alle.
 */
async function gateGooglePlayLogin(req, email) {
  if (!playIntegrity.isEnforced()) return { ok: true };
  const integrityToken = String(req.body?.integrityToken || "").trim();
  const integrityNonce = String(req.body?.integrityNonce || "").trim();
  const allow = isSideloadLoginAllowed(email);

  if (!integrityToken || !integrityNonce) {
    if (allow) {
      console.warn("[play_integrity] sideload allowlist (missing token)", email);
      return { ok: true, sideloadBypass: true };
    }
    return {
      ok: false,
      error: "missing_integrity",
      message:
        "Bitte LUV aus dem Google Play Store installieren und aktualisieren.",
    };
  }

  const verdict = await playIntegrity.verifyIntegrityToken(
    integrityToken,
    integrityNonce
  );
  if (verdict.ok) return { ok: true, verdict };

  const sideloadApp =
    verdict.error === "app_untrusted" ||
    verdict.error === "missing_integrity" ||
    verdict.appLic === "UNRECOGNIZED_VERSION" ||
    verdict.appLic === "UNEVALUATED" ||
    (verdict.appLic && verdict.appLic !== "PLAY_RECOGNIZED");

  if (allow && sideloadApp && verdict.error !== "device_untrusted") {
    console.warn(
      "[play_integrity] sideload allowlist",
      email,
      verdict.error || "",
      verdict.appLic || ""
    );
    return { ok: true, verdict, sideloadBypass: true };
  }

  return {
    ok: false,
    error: verdict.error || "app_untrusted",
    message:
      verdict.message ||
      "Bitte LUV aus dem Google Play Store installieren.",
  };
}

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
  "market.settings",
  "mods.manage",
];

/** Rechte, die nur Admins haben dürfen (nie an Mods vergeben/speichern). */
const ADMIN_ONLY_PERMISSION_IDS = new Set(["mods.manage"]);

/**
 * Standard für neue Mods: nur Moderation, keine Wirtschaft/Shop/Rechte-Vergabe.
 * Admins können einzelne Rechte später gezielt freischalten.
 */
const DEFAULT_MOD_PERMISSIONS = Object.fromEntries(
  ALL_MOD_PERMISSION_IDS.map((id) => [
    id,
    [
      "reports.view",
      "reports.act",
      "gm.search",
      "gm.block",
      "codes.view",
      "live.notify",
    ].includes(id),
  ])
);

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
  {
    id: "market",
    icon: "🏪",
    label: "Marktplatz",
    description: "Preis-Anzeige und Markt-Einstellungen",
    permissions: [
      { id: "market.settings", label: "Markt- & Economy-Einstellungen" },
    ],
  },
  {
    id: "mods",
    icon: "🛡️",
    label: "Moderatoren",
    description: "Nur Admins: Moderatoren einladen und Rechte vergeben",
    permissions: [
      { id: "mods.manage", label: "Moderatoren verwalten (nur Admin)" },
    ],
  },
];
/** OAuth 2.0 Web-Client-ID (Google Cloud) — für Sign-In + Token-Verify */
const GOOGLE_CLIENT_ID = String(process.env.GOOGLE_CLIENT_ID || "").trim();
const WEB_DIR = String(process.env.WEB_DIR || "/opt/luv-web").trim();

const DAILY_COINS = 12;
/** Max. Coins, die ein Profil pro Tag (0:00 Europe/Berlin) ins Münzglas bekommen kann (alle Spender zusammen). */
const GLASS_TIP_DAILY_MAX = 10;
const STARTING_COINS = 25;
/** Legacy-API-Feld — Zeichnen ist in allen Lobbys frei (Lobby erstellen kostet schon). */
const FREE_SESSIONS_PER_DAY = 5;
/** Zeichnen kostet keine Coins mehr (früher 1 Coin nach freien Tages-Slots). */
const SESSION_COST = 0;
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
// Teure / extrem teure Emojis (Overrides überschreiben bei gleichem Key)
Object.assign(EMOJI_SHOP_PRICES, shopCatalog.EXTRA_EMOJI_PRICES);

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
  // Weitere Hintergründe (auch animiert) — inkl. hoch / extrem
  twilight: 45,
  neon: 55,
  abyss: 60,
  cherry: 48,
  moss: 42,
  sandstorm: 50,
  frostfire: 70,
  prism: 85,
  void: 120,
  royal: 150,
  celestial: 200,
  inferno: 280,
  paradise: 350,
  mythic: 500,
  eternity: 1200,
  cosmos: 2500,
  legend: 5000,
  divine: 10000,
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
  "🧙": 500,
  "🐾": 14,
};

const STARTER_EMOJIS = ["👍", "❌", "❤️", "😂", "😱", "😡", "😭"];

/** Nur Katalog-/Starter-/Erfolgs-Items — verhindert eingeschmuggelte Fake-Items. */
function isKnownInventoryItem(kind, itemId) {
  const id = String(itemId || "").trim();
  if (!id) return false;
  // Admin-gelöscht: nicht mehr im Shop — Besitz/Markt bleiben gültig
  if (shopCatalog.isDeleted(getDb(), kind, id)) {
    if (kind === "stickers" || kind === "emojis") return true;
    if (kind === "pets") return petImages.hasImage(id) || id === DEFAULT_PET || id === marriage.MARRIAGE_PET;
    if (kind === "themes") return true;
    return false;
  }
  if (ach.isAchievementRewardItem(kind, id)) return true;
  // Dynamischer Admin-Katalog (inkl. Bild-Begleiter)
  if (shopCatalog.isShopKnown(getDb(), kind, id)) return true;
  if (kind === "pets") {
    return (
      id === DEFAULT_PET ||
      id === marriage.MARRIAGE_PET ||
      PET_SHOP_PRICES[id] != null ||
      petImages.hasImage(id) ||
      isEventShopPetId(id)
    );
  }
  if (kind === "themes") return id === "meadow" || THEME_SHOP_PRICES[id] != null;
  if (kind === "stickers") return isKnownSticker(id);
  if (kind === "emojis") {
    return STARTER_EMOJIS.includes(id) || EMOJI_SHOP_PRICES[id] != null;
  }
  return false;
}

/** System-Items, die der Admin nicht löschen darf. */
function isProtectedShopItem(kind, itemId) {
  const id = String(itemId || "").trim();
  if (!id) return true;
  if (kind === "themes" && id === "meadow") return true;
  if (kind === "pets" && (id === DEFAULT_PET || id === marriage.MARRIAGE_PET)) return true;
  if (kind === "emojis" && STARTER_EMOJIS.includes(id)) return true;
  if (kind === "stickers" && isAchievementSticker(id)) return true;
  if (ach.isAchievementRewardItem(kind, id)) return true;
  return false;
}

/**
 * Genau EIN Item entfernen (kind + itemId exakt).
 * Andere Inventar-/Profil-/Leinwand-Inhalte bleiben unberührt.
 */
function purgeShopItemEverywhere(kind, itemId) {
  const id = String(itemId || "").trim();
  const k = String(kind || "").trim();
  // Ohne konkrete ID: nichts tun (nie „alles“ leeren)
  if (!id || !k || id.length > 32) {
    return { users: 0, rooms: 0, listings: 0, pendingRefunds: 0 };
  }
  const db = getDb();
  let usersTouched = 0;
  let roomsTouched = 0;
  let listingsClosed = 0;
  let pendingRefunds = 0;
  const purchaseKey = `${k}:${id}`;
  const sameId = (raw) => String(raw || "").trim() === id;

  for (const user of Object.values(db.users || {})) {
    if (!user) continue;
    let changed = false;
    // Inventar nur anfassen, wenn vorhanden — keine Voll-Reset
    const inv = user.inventory;
    if (inv && typeof inv === "object") {
      if (k === "emojis" && inv.emojis && typeof inv.emojis === "object" && inv.emojis[id] != null) {
        delete inv.emojis[id];
        changed = true;
      }
      if (k === "stickers" && inv.stickers && typeof inv.stickers === "object" && inv.stickers[id] != null) {
        delete inv.stickers[id];
        changed = true;
      }
      if (k === "pets") {
        normalizePetsMap(inv);
        if (inv.pets[id] != null) {
          delete inv.pets[id];
          changed = true;
        }
        if (sameId(inv.equippedPet)) {
          if (petCount(inv, DEFAULT_PET) < 1) addPetCount(inv, DEFAULT_PET, 1);
          inv.equippedPet = DEFAULT_PET;
          changed = true;
        }
      }
      if (k === "themes" && Array.isArray(inv.themes)) {
        const before = inv.themes.length;
        inv.themes = inv.themes.filter((t) => !sameId(t));
        if (inv.themes.length !== before) {
          changed = true;
          // Fallback behalten — nicht das ganze Themen-Inventar leeren
          if (!inv.themes.includes("meadow")) inv.themes.push("meadow");
        }
      }
    }

    if (user.shopPurchases && typeof user.shopPurchases === "object" && user.shopPurchases[purchaseKey] != null) {
      delete user.shopPurchases[purchaseKey];
      changed = true;
    }

    if (Array.isArray(user.pendingLootboxes) && user.pendingLootboxes.length) {
      const keep = [];
      for (const p of user.pendingLootboxes) {
        if (p && String(p.kind) === k && sameId(p.itemId)) {
          const refund = Math.max(1, Number(p.shopPrice) || LOOTBOX_PRICE);
          applyLedger(user.id, refund, "lootbox_item_deleted", purchaseKey);
          pendingRefunds += 1;
          changed = true;
        } else {
          keep.push(p);
        }
      }
      user.pendingLootboxes = keep;
    }

    if (user.profileCanvas && typeof user.profileCanvas === "object") {
      const pc = user.profileCanvas;
      if (k === "themes" && sameId(pc.themeId)) {
        pc.themeId = "meadow";
        changed = true;
      }
      if (k === "pets" && sameId(pc.companionEmoji)) {
        const invPets = user.inventory ? normalizePetsMap(user.inventory) : {};
        pc.companionEmoji = petOwned(user.inventory || { pets: invPets }, DEFAULT_PET)
          ? DEFAULT_PET
          : Object.keys(invPets)[0] || DEFAULT_PET;
        changed = true;
      }
      // Nur passende Layout-Elemente mit exakt dieser emoji-ID entfernen
      if (Array.isArray(pc.layout) && (k === "stickers" || k === "emojis" || k === "pets")) {
        const before = pc.layout.length;
        pc.layout = pc.layout.filter((el) => {
          if (!el || typeof el !== "object") return true;
          const t = String(el.type || "").toLowerCase();
          const e = String(el.emoji || "").trim();
          if (!sameId(e)) return true;
          // Nur passende Kind-Typen — Emoji-Delete entfernt keine Sticker-Platzierungen
          if (k === "stickers" && t === "sticker") return false;
          if (k === "pets" && t === "pet") return false;
          return true;
        });
        if (pc.layout.length !== before) changed = true;
      }
    }

    if (k === "emojis" && user.settings && Array.isArray(user.settings.emojiBar)) {
      const before = user.settings.emojiBar.length;
      user.settings.emojiBar = user.settings.emojiBar.filter((e) => !sameId(e));
      if (user.settings.emojiBar.length !== before) changed = true;
    }

    if (changed) {
      cleanupProfileAfterItemRemoved(user, k, id);
      usersTouched += 1;
    }
  }

  // Lobby-Leinwand: nur Emoji-/Sticker-Stempel mit exakt dieser ID (normale Striche bleiben)
  const touchesCanvas = k === "emojis" || k === "stickers";
  const purgeStrokeList = (list) => {
    if (!Array.isArray(list)) return list;
    return list.filter((s) => {
      if (!s || typeof s !== "object") return true;
      const e = String(s.emoji || "").trim();
      // Ohne emoji = normaler Strich/Vorlage → behalten
      if (!e) return true;
      return e !== id;
    });
  };

  if (touchesCanvas) {
    for (const [code, room] of rooms.entries()) {
      if (!room) continue;
      let changed = false;
      if (Array.isArray(room.strokes)) {
        const next = purgeStrokeList(room.strokes);
        if (next.length !== room.strokes.length) {
          room.strokes = next;
          scheduleStrokeSave(code, room);
          changed = true;
        }
      }
      if (Array.isArray(room.stickers)) {
        const next = purgeStrokeList(room.stickers);
        if (next.length !== room.stickers.length) {
          room.stickers = next;
          scheduleStickerSave(code, room);
          changed = true;
        }
      }
      if (changed) roomsTouched += 1;
    }

    try {
      ensureStrokesDir();
      const files = fs.readdirSync(STROKES_DIR);
      for (const name of files) {
        if (!name.endsWith(".json") || name.endsWith(".tmp")) continue;
        const full = path.join(STROKES_DIR, name);
        let arr;
        try {
          arr = JSON.parse(fs.readFileSync(full, "utf8"));
        } catch {
          continue;
        }
        if (!Array.isArray(arr)) continue;
        const next = purgeStrokeList(arr);
        if (next.length === arr.length) continue;
        const tmp = `${full}.tmp`;
        fs.writeFileSync(tmp, JSON.stringify(next));
        fs.renameSync(tmp, full);
        roomsTouched += 1;
      }
    } catch (err) {
      console.warn("purge strokes dir failed", err?.message || err);
    }
  }

  // Markt-Angebote: Escrow zuerst an Verkäufer zurück, dann cancel
  const listings = market.ensureMarket(db);
  for (const entry of Object.values(listings)) {
    if (!entry || entry.status !== "open") continue;
    if (String(entry.kind) === k && sameId(entry.itemId)) {
      const seller = db.users?.[entry.sellerId];
      if (seller) {
        try {
          safeGiveItem(seller, k, id);
        } catch (_) {
          /* ignore */
        }
      }
      entry.status = "cancelled";
      entry.cancelledAt = Date.now();
      entry.cancelReason = "item_deleted";
      listingsClosed += 1;
    }
  }

  // Nur die Bilddatei dieser ID
  if (id.toLowerCase().startsWith("img_")) {
    try {
      petImages.deleteImage(id);
    } catch (_) {
      /* ignore */
    }
  }

  return { users: usersTouched, rooms: roomsTouched, listings: listingsClosed, pendingRefunds };
}

/**
 * Katalog-Items für Lootbox — unabhängig vom Shop-Zeitfenster.
 * Nur ausblenden, wenn Admin lootboxEligible=false gesetzt hat (oder Starter/Ehe).
 */
function catalogLootboxExtras() {
  seedShopCatalogIfNeeded();
  const db = getDb();
  const cat = shopCatalog.ensureShopCatalog(db);
  const lockCtx = { defaultPet: DEFAULT_PET, starterEmojis: STARTER_EMOJIS };
  return Object.values(cat.items || {})
    .filter(Boolean)
    .filter((i) => {
      if (i.eventId) return false;
      if (seasonEvents.isEventOnlyItem(i.kind, i.itemId)) return false;
      if (seasonEvents.isEventShopBoundItem(db, i.kind, i.itemId)) return false;
      const price = shopCatalog.effectivePrice(i);
      if (price < 1 && i.kind !== "themes") return false;
      return itemTrade.getLootboxEligible(db, i.kind, i.itemId, ["shop"], lockCtx);
    })
    .map((i) => ({
      kind: i.kind,
      itemId: i.itemId,
      emoji: i.kind === "themes" ? "🖼️" : i.itemId,
      label: i.label || i.itemId,
      priceCoins: shopCatalog.effectivePrice(i),
    }));
}

/** System-gebunden (Starter/Ehe) — immer gesperrt. Erfolge über itemTrade-Flags. */
function isBoundInventoryItem(kind, itemId) {
  return itemTrade.isLockedStarter(kind, itemId, {
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
  });
}

function marketSellableFor(kind, itemId) {
  const id = String(itemId || "").trim();
  const sources = [];
  const db = getDb();
  if (shopCatalog.isShopKnown(db, kind, id)) sources.push("shop");
  if (seasonEvents.isEventOnlyItem(kind, id) || seasonEvents.isEventShopBoundItem(db, kind, id)) {
    sources.push("event");
  }
  if (ach.isAchievementRewardItem(kind, id) || (kind === "stickers" && isAchievementSticker(id))) {
    sources.push("achievement");
  }
  if (kind === "pets" && id === marriage.MARRIAGE_PET) sources.push("marriage");
  if (
    (kind === "pets" && id === DEFAULT_PET) ||
    (kind === "themes" && id === "meadow") ||
    (kind === "emojis" && STARTER_EMOJIS.includes(id))
  ) {
    sources.push("starter");
  }
  return itemTrade.getMarketSellable(getDb(), kind, id, sources, {
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
  });
}

function achDailyCap() {
  return ach.getAchievementDailyCap(getDb());
}

function achPublicState(user) {
  return ach.publicAchievementsState(user, todayKey(), achDailyCap());
}

function tryClaimAchievementReward(user, achievementId) {
  return ach.claimAchievement(
    user,
    achievementId,
    todayKey(),
    (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
    (u, k, itemId) => safeGiveItem(u, k, itemId),
    (u, k, itemId) => userAlreadyOwnsUnique(u, k, itemId),
    achDailyCap()
  );
}

function repairAchievementItemRewards(user) {
  return ach.repairMissingRewardItems(
    user,
    (u, k, itemId) => safeGiveItem(u, k, itemId),
    (u, k, itemId) => userAlreadyOwnsUnique(u, k, itemId)
  );
}

/** Begleiter-IDs: Emoji oder img_xxxxxxxx (nicht auf 8 Zeichen kürzen). */
const PET_ID_MAX = 32;

function normalizePetId(raw) {
  return String(raw || "").trim().slice(0, PET_ID_MAX);
}

/** Begleiter stapelbar (Map id→count), wie Sticker — Array-Altbestand wird migriert. */
function normalizePetsMap(inv) {
  if (!inv) return {};
  if (Array.isArray(inv.pets)) {
    const next = {};
    for (const p of inv.pets) {
      const id = expandTruncatedPetId(String(p || "").trim()).slice(0, PET_ID_MAX);
      if (!id) continue;
      next[id] = Math.min(999, (Number(next[id]) || 0) + 1);
    }
    inv.pets = next;
  } else if (!inv.pets || typeof inv.pets !== "object") {
    inv.pets = {};
  }
  return inv.pets;
}

function petCount(inv, id) {
  const key = expandTruncatedPetId(String(id || "").trim()).slice(0, PET_ID_MAX);
  if (!key) return 0;
  return Math.max(0, Math.floor(Number(normalizePetsMap(inv)[key]) || 0));
}

function petOwned(inv, id) {
  return petCount(inv, id) > 0;
}

function addPetCount(inv, id, n = 1) {
  const pets = normalizePetsMap(inv);
  const key = expandTruncatedPetId(String(id || "").trim()).slice(0, PET_ID_MAX);
  if (!key) return 0;
  const add = Math.max(0, Math.floor(Number(n) || 0));
  pets[key] = Math.min(999, (Number(pets[key]) || 0) + add);
  return pets[key];
}

function petReservedSlots(user, inv, id) {
  const key = expandTruncatedPetId(String(id || "").trim()).slice(0, PET_ID_MAX);
  if (!key) return 0;
  let reserved = 0;
  if (String(inv.equippedPet || "") === key) reserved = 1;
  const companion = String(user?.profileCanvas?.companionEmoji || "").trim();
  if (companion === key) reserved = Math.max(reserved, 1);
  return reserved;
}

function petFreeStock(user, id) {
  const inv = ensureInventory(user);
  return Math.max(0, petCount(inv, id) - petReservedSlots(user, inv, id));
}

function isEventShopPetId(id) {
  const s = String(id || "").trim();
  return (
    /^img_event_[a-z0-9_]+_\d{4}$/i.test(s) ||
    /^img_ev_[a-z0-9_]+_[pse]\d{4}$/i.test(s) ||
    /^theme_[a-z0-9_]+_t\d{4}$/i.test(s)
  );
}

/** Durch slice(0,8) gekürzte img_*-IDs anhand Katalog wiederherstellen. */
function expandTruncatedPetId(raw, kindHint = null) {
  const id = normalizePetId(raw);
  if (!id.startsWith("img_") || id.length >= 12) return id;
  try {
    seedShopCatalogIfNeeded();
    const kinds = kindHint
      ? [kindHint]
      : ["pets", "stickers", "emojis"];
    const hits = [];
    for (const kind of kinds) {
      const items =
        shopCatalog.listPublicCatalog(getDb(), { admin: true, kind }) || [];
      for (const i of items) {
        if (
          i &&
          String(i.itemId || "").startsWith(id) &&
          String(i.itemId).length >= 12
        ) {
          hits.push(String(i.itemId).slice(0, PET_ID_MAX));
        }
      }
    }
    const uniq = [...new Set(hits)];
    if (uniq.length === 1) return uniq[0];
  } catch (_) {
    /* ignore */
  }
  return id;
}

function isPlaceholderImageItemId(id) {
  const s = String(id || "")
    .trim()
    .toLowerCase();
  return !s || s === "img_new" || s === "new" || s === "img_" || s === "auto";
}

function isStableImageItemId(id) {
  const s = String(id || "").trim();
  return /^img_[a-z0-9]{6,28}$/i.test(s) && !isPlaceholderImageItemId(s);
}

/** Neue Bild-Item-ID — nie img_new, nie Kollision mit Katalog/Datei. */
function allocateImageItemId(db, kind) {
  for (let i = 0; i < 40; i++) {
    const id = `img_${crypto.randomBytes(4).toString("hex")}`;
    if (!shopCatalog.getItem(db, kind, id) && !petImages.hasImage(id)) return id;
  }
  return `img_${Date.now().toString(36)}${crypto.randomBytes(2).toString("hex")}`.slice(
    0,
    PET_ID_MAX
  );
}

/**
 * Besitz NIEMALS wegen Katalog-Änderungen löschen.
 * Nur IDs normalisieren / Zähler säubern — Ownership bleibt stabil.
 */
function scrubInventoryCatalog(inv) {
  const pets = normalizePetsMap(inv);
  const nextPets = {};
  for (const [k0, rawN] of Object.entries(pets)) {
    const n = Math.min(999, Math.max(0, Math.floor(Number(rawN) || 0)));
    if (n <= 0) continue;
    const k = expandTruncatedPetId(String(k0 || "").trim()).slice(0, PET_ID_MAX);
    if (!k) continue;
    nextPets[k] = Math.min(999, (Number(nextPets[k]) || 0) + n);
  }
  inv.pets = nextPets;
  if (inv.equippedPet) {
    inv.equippedPet = expandTruncatedPetId(inv.equippedPet);
  }
  inv.themes = (inv.themes || [])
    .map((t) => String(t || "").trim().slice(0, 32))
    .filter((t) => t.length > 0)
    .filter((t, i, a) => a.indexOf(t) === i);
  const nextStickers = {};
  for (const [e0, rawN] of Object.entries(inv.stickers || {})) {
    let n = Math.min(999, Math.max(0, Math.floor(Number(rawN) || 0)));
    if (n <= 0 || !String(e0 || "").trim()) continue;
    const e = expandTruncatedPetId(e0, "stickers");
    // Erfolgs-/Heirats-Sticker (z. B. Kapelle 💒) nie stapeln
    if (isAchievementSticker(e) && n > 1) n = 1;
    nextStickers[e] = Math.min(999, (Number(nextStickers[e]) || 0) + n);
    if (isAchievementSticker(e) && nextStickers[e] > 1) nextStickers[e] = 1;
  }
  inv.stickers = nextStickers;
  const nextEmojis = {};
  for (const [e0, rawN] of Object.entries(inv.emojis || {})) {
    const n = Math.min(999, Math.max(0, Math.floor(Number(rawN) || 0)));
    if (n <= 0 || !String(e0 || "").trim()) continue;
    const e = expandTruncatedPetId(e0, "emojis");
    nextEmojis[e] = Math.min(999, (Number(nextEmojis[e]) || 0) + n);
  }
  inv.emojis = nextEmojis;
}

/**
 * Item gutschreiben. Besitz vor Katalog — auch Legacy-/Admin-Items zurückgeben.
 * Kauf/Lootbox müssen separat mit isKnownInventoryItem prüfen.
 */
function safeGiveItem(user, kind, itemId) {
  const id = String(itemId || "").trim();
  if (!id || !kind) return false;
  market.giveItemToUser(user, ensureInventory, kind, id);
  return true;
}

/** Freier Sticker-Bestand = owned − auf dem Profil platziert. */
function stickerFreeStock(user, emoji) {
  const e = String(emoji || "").trim().slice(0, 32);
  if (!e) return 0;
  const inv = ensureInventory(user);
  const owned = Math.max(0, Math.floor(Number(inv.stickers[e]) || 0));
  const placed = countLayoutStickers(user.profileCanvas?.layout)[e] || 0;
  return Math.max(0, owned - placed);
}

function ensureInventory(user) {
  if (!user.inventory || typeof user.inventory !== "object") {
    user.inventory = {};
  }
  const inv = user.inventory;
  if (!inv.emojis || typeof inv.emojis !== "object") inv.emojis = {};
  if (!Array.isArray(inv.themes)) inv.themes = [];
  if (!inv.stickers || typeof inv.stickers !== "object") inv.stickers = {};
  // Pets: Map — Array wird in scrubInventoryCatalog migriert
  if (!inv.pets || (typeof inv.pets !== "object" && !Array.isArray(inv.pets))) {
    inv.pets = {};
  }
  // Nur normalisieren — niemals Besitz löschen
  scrubInventoryCatalog(inv);
  // Starter
  for (const e of STARTER_EMOJIS) {
    if (!inv.emojis[e] || inv.emojis[e] < 1) inv.emojis[e] = 1;
  }
  if (!inv.themes.includes("meadow")) inv.themes.push("meadow");
  if (petCount(inv, DEFAULT_PET) < 1) addPetCount(inv, DEFAULT_PET, 1);
  // Tutorial-Starter-Sticker (nicht equipped) — einmalig mindestens 1
  if (!inv.stickers["🐕"] || Number(inv.stickers["🐕"]) < 1) {
    inv.stickers["🐕"] = Math.max(1, Number(inv.stickers["🐕"]) || 0);
  }
  // WICHTIG: Keine Shop-Items aus profileCanvas „schenken“ — sonst Inventar-Bypass per PUT /profile
  if (!inv.equippedPet || !petOwned(inv, inv.equippedPet)) {
    inv.equippedPet = DEFAULT_PET;
  }
  user.inventory = inv;
  // Sticker-Mode-Migration + Unterzählungs-Heilung (owned ≥ placed)
  if (user) {
    restoreStickerTotalOwnership(user);
  }
  return inv;
}

/** Theme/Begleiter nur wenn im Inventar (oder kostenlos). */
function clampProfileToInventory(user, profile) {
  if (!profile || typeof profile !== "object") return profile;
  const inv = ensureInventory(user);
  let themeId = String(profile.themeId || "meadow").trim().slice(0, 32) || "meadow";
  if (!inv.themes.includes(themeId)) themeId = "meadow";
  let companionEmoji =
    expandTruncatedPetId(profile.companionEmoji || DEFAULT_PET) || DEFAULT_PET;
  if (!petOwned(inv, companionEmoji)) companionEmoji = DEFAULT_PET;
  const myM = marriage.findMarriageForUser(getDb(), user.id);
  const canSpouse = myM && myM.status === "married";
  const canEngaged =
    myM &&
    (myM.status === "engaged" ||
      myM.status === "wedding" ||
      myM.status === "ceremony_pending" ||
      myM.status === "ceremony_scheduled");
  const partnerNick = canSpouse || canEngaged
    ? (() => {
        const pid = marriage.partnerIdOf(myM, user.id);
        const p = pid ? getDb().users?.[pid] : null;
        return p ? String(p.nickname || "").trim().slice(0, 18) : "";
      })()
    : "";
  // Sticker-Bestand: syncStickersOnProfileSave (Inventar = Gesamtpbesitz; frei = owned − platziert)
  const layout = Array.isArray(profile.layout)
    ? profile.layout.filter((el) => {
        if (!el) return false;
        const t = String(el.type || "").toLowerCase();
        if (t === "spouse") return Boolean(canSpouse);
        if (t === "engaged") return Boolean(canEngaged);
        if (t !== "sticker") return true;
        return Boolean(String(el.emoji || el.text || "").trim());
      }).map((el) => {
        const t = String(el.type || "").toLowerCase();
        if ((t === "spouse" || t === "engaged") && partnerNick) {
          return { ...el, text: partnerNick, emoji: t === "spouse" ? "💍" : "💝" };
        }
        return el;
      })
    : [];
  return { ...profile, themeId, companionEmoji, layout };
}

function userOwnsCanvasEmoji(user, emoji) {
  const e = String(emoji || "").trim().slice(0, 32);
  if (!e) return false;
  const inv = ensureInventory(user);
  if ((Number(inv.emojis[e]) || 0) > 0) return true;
  if ((Number(inv.stickers[e]) || 0) > 0) return true;
  // Nur explizit kostenlose Katalog-Items ohne Inventar-Eintrag
  if (EMOJI_SHOP_PRICES[e] === 0) return true;
  if (STICKER_SHOP_PRICES[e] === 0) return true;
  return false;
}

/** Unique Shop-Items (nur Themes): schon im Besitz → kein zweiter Kauf/Tausch. Pets stapeln. */
function userAlreadyOwnsUnique(user, kind, itemId) {
  if (!user) return false;
  if (kind !== "themes") return false;
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
    if (petFreeStock(user, id) < 1) {
      return "Begleiter ist ausgerüstet — zuerst einen anderen wählen oder Extra verkaufen.";
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
  if (kind === "stickers") {
    if (stickerFreeStock(user, id) < 1) {
      return "Sticker ist auf dem Profil platziert — zuerst entfernen.";
    }
  }
  return null;
}

/** Nach Verkauf/Entnahme: Profil-Ghosts bereinigen. */
function cleanupProfileAfterItemRemoved(user, kind, itemId) {
  if (!user || !kind || !itemId) return;
  const id = String(itemId).trim();
  const inv = ensureInventory(user);
  if (kind === "pets") {
    if (petCount(inv, id) < 1) {
      if (String(inv.equippedPet || "") === id || !petOwned(inv, inv.equippedPet)) {
        inv.equippedPet = DEFAULT_PET;
      }
      if (user.profileCanvas && String(user.profileCanvas.companionEmoji || "") === id) {
        user.profileCanvas.companionEmoji = inv.equippedPet || DEFAULT_PET;
      }
    } else if (!petOwned(inv, inv.equippedPet)) {
      inv.equippedPet = DEFAULT_PET;
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

const BLOCKED_NICK_RE =
  /^(testuser|test\d*|user\d+|guest\d*|bot\d*|admin|moderator|null|undefined)$/i;

function looksLikeBotNickname(nick) {
  const n = String(nick || "").trim();
  if (!n) return false;
  if (BLOCKED_NICK_RE.test(n)) return true;
  if (/^(user|player|guest)[\s._-]*\d+$/i.test(n)) return true;
  return false;
}

/** Max. neue Google-Konten systemweit pro Stunde (zusätzlich zum IP-Limit). */
function globalSignupAllowed(maxPerHour = 20) {
  return rateLimit("auth_google_signup_global", maxPerHour, 60 * 60 * 1000);
}

function userPetEmoji(user) {
  const inv = ensureInventory(user);
  return normalizePetId(inv.equippedPet || DEFAULT_PET) || DEFAULT_PET;
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
      .map((e) => clipCanvasEmojiId(e))
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
  const lobbyOrder = [];
  if (Array.isArray(src.lobbyOrder)) {
    const seen = new Set();
    for (const raw of src.lobbyOrder) {
      const code = String(raw || "")
        .trim()
        .toUpperCase()
        .replace(/^LUV-/, "")
        .slice(0, 16);
      if (
        code.length >= 3 &&
        code.length <= 16 &&
        /^[A-Z0-9]+$/.test(code) &&
        !seen.has(code)
      ) {
        seen.add(code);
        lobbyOrder.push(code);
      }
      if (lobbyOrder.length >= 50) break;
    }
  }
  return {
    quietHours: quietOut,
    emojiBar,
    partnerDrawNotify: src.partnerDrawNotify !== false,
    partnerHaptic: src.partnerHaptic !== false,
    liveProximityRich: src.liveProximityRich !== false,
    liveProximityWake: Boolean(src.liveProximityWake),
    lobbyProximity,
    lobbyOrder,
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
  // Besitz immer übernehmen — Katalog-Status darf beim Merge nichts löschen
  for (const [e, n] of Object.entries(b.emojis || {})) {
    if (!String(e || "").trim()) continue;
    a.emojis[e] = Math.min(999, Math.max(Number(a.emojis[e]) || 0, Number(n) || 0));
  }
  for (const [e, n] of Object.entries(b.stickers || {})) {
    if (!String(e || "").trim()) continue;
    a.stickers[e] = Math.min(999, Math.max(Number(a.stickers[e]) || 0, Number(n) || 0));
  }
  for (const t of b.themes || []) {
    if (t && !a.themes.includes(t)) a.themes.push(t);
  }
  for (const [p, n] of Object.entries(b.pets || {})) {
    if (!p || !String(p).trim()) continue;
    const add = Math.max(0, Math.floor(Number(n) || 0));
    if (add < 1) continue;
    a.pets[p] = Math.min(999, Math.max(Number(a.pets[p]) || 0, add));
  }
  if (!a.equippedPet || !petOwned(a, a.equippedPet)) {
    a.equippedPet = b.equippedPet && petOwned(a, b.equippedPet)
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
  // Level / Claim-Fortschritt übernehmen
  marriage.ensureFriendLevels(a);
  marriage.ensureFriendLevels(b);
  for (const [oid, lv] of Object.entries(b.levels || {})) {
    if (!oid || oid === target.id) continue;
    a.levels[oid] = Math.max(Number(a.levels[oid]) || 0, Number(lv) || 0);
  }
  if (!a.levelRewardClaimed || typeof a.levelRewardClaimed !== "object") {
    a.levelRewardClaimed = {};
  }
  for (const [oid, n] of Object.entries(b.levelRewardClaimed || {})) {
    if (!oid || oid === target.id) continue;
    a.levelRewardClaimed[oid] = Math.max(
      Number(a.levelRewardClaimed[oid]) || 0,
      Number(n) || 0
    );
  }
}

/**
 * Nach Konto-Merge: Freundeslisten anderer Nutzer von fromId → toId umhängen.
 * Ohne das bricht die Mutual-Prüfung und alle Freunde verschwinden.
 */
function remapFriendRefs(db, fromId, toId) {
  if (!fromId || !toId || fromId === toId) return;
  for (const other of Object.values(db.users || {})) {
    if (!other?.id || other.id === fromId || other.id === toId) continue;
    const f = ensureFriends(other);
    for (const key of ["list", "incoming", "outgoing"]) {
      if (!Array.isArray(f[key])) continue;
      f[key] = [
        ...new Set(f[key].map((id) => (id === fromId ? toId : id)).filter(Boolean)),
      ];
    }
    if (Array.isArray(f.petKraulTargets)) {
      f.petKraulTargets = [
        ...new Set(
          f.petKraulTargets.map((id) => (id === fromId ? toId : id)).filter(Boolean)
        ),
      ];
    }
    marriage.ensureFriendLevels(f);
    if (f.levels && f.levels[fromId] != null) {
      f.levels[toId] = Math.max(Number(f.levels[toId]) || 0, Number(f.levels[fromId]) || 0);
      delete f.levels[fromId];
    }
    if (f.levelRewardClaimed && f.levelRewardClaimed[fromId] != null) {
      f.levelRewardClaimed[toId] = Math.max(
        Number(f.levelRewardClaimed[toId]) || 0,
        Number(f.levelRewardClaimed[fromId]) || 0
      );
      delete f.levelRewardClaimed[fromId];
    }
  }
}

/**
 * Nach Merge: Freunde zeigen oft noch auf die gelöschte Alt-ID.
 * Wenn genau ein lebender Ehepartner der Referenzen als Ziel in Frage kommt
 * (und selbst nicht auf die tote ID zeigt), IDs umhängen und Liste wiederherstellen.
 */
function repairDanglingFriendsViaSpouse(db) {
  const byMissing = new Map();
  for (const u of Object.values(db.users || {})) {
    if (!u?.id) continue;
    const f = ensureFriends(u);
    const ids = new Set([
      ...(f.list || []),
      ...(f.incoming || []),
      ...(f.outgoing || []),
      ...Object.keys(f.levels || {}),
    ]);
    for (const id of ids) {
      if (!id || db.users[id]) continue;
      if (!byMissing.has(id)) byMissing.set(id, new Set());
      byMissing.get(id).add(u.id);
    }
  }
  let repaired = 0;
  for (const [missingId, refSet] of byMissing.entries()) {
    const candidates = new Set();
    for (const rid of refSet) {
      const m = marriage.findMarriageForUser(db, rid);
      if (!m || !marriage.isBusyStatus(m.status)) continue;
      const partner = marriage.partnerIdOf(m, rid);
      if (partner && db.users[partner] && !refSet.has(partner)) {
        candidates.add(partner);
      }
    }
    if (candidates.size !== 1) continue;
    const toId = [...candidates][0];
    const target = db.users[toId];
    if (!target) continue;
    remapFriendRefs(db, missingId, toId);
    const tf = ensureFriends(target);
    for (const rid of refSet) {
      if (rid === toId || !db.users[rid]) continue;
      if (!tf.list.includes(rid)) tf.list.push(rid);
      const other = db.users[rid];
      const of = ensureFriends(other);
      // tote ID → toId (falls remap die levels schon umgehängt hat)
      of.list = of.list.filter((id) => id !== missingId);
      if (!of.list.includes(toId)) of.list.push(toId);
      const lv = Math.max(
        Number(of.levels?.[toId]) || 0,
        Number(of.levels?.[missingId]) || 0,
        Number(tf.levels?.[rid]) || 0
      );
      if (lv > 0) marriage.setLevelBoth(target, other, lv);
      else {
        // Mindestens Freundschaft ohne Level
        marriage.ensureFriendLevels(tf);
        marriage.ensureFriendLevels(of);
      }
    }
    repaired += 1;
    console.log(`repaired dangling friends ${missingId} → ${toId} (${refSet.size} refs)`);
  }
  return repaired;
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
  marriage.ensureFriendLevels(f);
  if (!f.levelRewardClaimed || typeof f.levelRewardClaimed !== "object") {
    f.levelRewardClaimed = {};
  }
  f.list = f.list.map((id) => String(id || "").trim()).filter(Boolean);
  f.incoming = f.incoming.map((id) => String(id || "").trim()).filter(Boolean);
  f.outgoing = f.outgoing.map((id) => String(id || "").trim()).filter(Boolean);
  user.friends = f;
  return f;
}

/** Abholbare Coins aus Freundschaftslevel (alle 10 Level · 1 Coin, summiert). */
function pendingFriendshipLevelCoins(user) {
  const f = ensureFriends(user);
  let pending = 0;
  for (const otherId of f.list) {
    const lv = marriage.getLevel(user, otherId);
    const last = Math.max(0, Math.floor(Number(f.levelRewardClaimed[otherId]) || 0));
    for (let m = 10; m <= lv; m += 10) {
      if (m > last) pending += 1;
    }
  }
  return pending;
}

function claimFriendshipLevelCoins(user) {
  const f = ensureFriends(user);
  let granted = 0;
  for (const otherId of f.list) {
    const lv = marriage.getLevel(user, otherId);
    let last = Math.max(0, Math.floor(Number(f.levelRewardClaimed[otherId]) || 0));
    for (let m = 10; m <= lv; m += 10) {
      if (m > last) {
        granted += 1;
        last = m;
      }
    }
    f.levelRewardClaimed[otherId] = last;
  }
  if (granted > 0) {
    applyLedger(user.id, granted, "friendship_level", "milestones");
  }
  return granted;
}

function friendPublicCard(user, viewer) {
  if (!user) return null;
  ensureCoinBuckets(user);
  const level = viewer ? marriage.getLevel(viewer, user.id) : 0;
  const m =
    viewer != null
      ? marriage.findMarriageBetween(getDb(), viewer.id, user.id)
      : null;
  const isSpouse = Boolean(m && m.status === "married");
  const isFiance = Boolean(
    m &&
      (m.status === "engaged" ||
        m.status === "wedding" ||
        m.status === "ceremony_pending" ||
        m.status === "ceremony_scheduled")
  );
  return {
    userId: user.id,
    nickname: String(user.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
    petEmoji: userPetEmoji(user),
    friendshipLevel: level,
    isSpouse,
    isFiance,
    marriage: viewer ? publicMarriageView(m, viewer.id) : null,
  };
}

const WEDDING_DIR = path.join(DATA_DIR, "weddings");
/** Vor dem Ja: Malbild hier — nie Boot-Recovery als „verheiratet“. */
const WEDDING_PENDING_DIR = path.join(DATA_DIR, "weddings_pending");

function ensureWeddingDir() {
  fs.mkdirSync(WEDDING_DIR, { recursive: true });
  fs.mkdirSync(WEDDING_PENDING_DIR, { recursive: true });
}

function weddingPairFileName(m) {
  return `${m.id || marriage.pairKey(m.a, m.b)}.png`;
}

function deleteWeddingImageFiles(m) {
  if (!m) return;
  ensureWeddingDir();
  const names = new Set();
  if (m.weddingImageFile) names.add(path.basename(String(m.weddingImageFile)));
  if (m.weddingImagePending) names.add(path.basename(String(m.weddingImagePending)));
  names.add(weddingPairFileName(m));
  for (const name of names) {
    if (!name || !/\.png$/i.test(name)) continue;
    for (const dir of [WEDDING_DIR, WEDDING_PENDING_DIR]) {
      try {
        const fp = path.join(dir, name);
        if (fs.existsSync(fp)) fs.unlinkSync(fp);
      } catch (e) {
        console.error("wedding image delete failed", e);
      }
    }
  }
  m.weddingImageFile = null;
  m.weddingImagePending = null;
}

/** Nach beiden Ja: Pending-Bild → finales Hochzeitsbild. */
function promotePendingWeddingImage(m) {
  if (!m) return false;
  ensureWeddingDir();
  const destName = weddingPairFileName(m);
  const pendingName = m.weddingImagePending
    ? path.basename(String(m.weddingImagePending))
    : destName;
  const pendingPath = path.join(WEDDING_PENDING_DIR, pendingName);
  const finalPath = path.join(WEDDING_DIR, destName);
  try {
    if (fs.existsSync(pendingPath)) {
      fs.copyFileSync(pendingPath, finalPath);
      try {
        fs.unlinkSync(pendingPath);
      } catch {
        /* ignore */
      }
      m.weddingImageFile = destName;
      m.weddingImagePending = null;
      return true;
    }
    // Alt: Bild lag schon final (vor dem Fix)
    if (fs.existsSync(finalPath)) {
      m.weddingImageFile = destName;
      m.weddingImagePending = null;
      return true;
    }
  } catch (e) {
    console.error("promote wedding image failed", e);
  }
  return false;
}

/**
 * Mal-Phase fertig: Snapshot nur als Pending (kein Ehe-Status, kein Boot-Married).
 */
function demoteFinalWeddingImageToPending(m) {
  if (!m?.weddingImageFile) return;
  ensureWeddingDir();
  const name = path.basename(String(m.weddingImageFile));
  const finalPath = path.join(WEDDING_DIR, name);
  const pendingPath = path.join(WEDDING_PENDING_DIR, name);
  try {
    if (fs.existsSync(finalPath)) {
      fs.copyFileSync(finalPath, pendingPath);
      fs.unlinkSync(finalPath);
    }
  } catch (e) {
    console.error("demote wedding image failed", e);
  }
  m.weddingImagePending = name;
  m.weddingImageFile = null;
}

/** Kurzzeit-Cache: Freunde-GET darf nicht jedes Mal 5000 Strokes von Disk lesen. */
const weddingStrokeCache = new Map(); // code -> { at, strokes }
const WEDDING_STROKE_CACHE_MS = 8_000;

function loadRoomStrokesCached(code) {
  const key = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  if (!key) return [];
  const hit = weddingStrokeCache.get(key);
  const now = Date.now();
  if (hit && now - hit.at < WEDDING_STROKE_CACHE_MS) return hit.strokes;
  let strokes = [];
  try {
    strokes = loadRoomStrokes(key);
  } catch {
    strokes = [];
  }
  weddingStrokeCache.set(key, { at: now, strokes });
  return strokes;
}

/** publicMarriage inkl. Strich-Fortschritt der Hochzeitsleinwand */
function publicMarriageView(m, viewerId) {
  if (!m) return null;
  const db = getDb();
  let strokes = [];
  if (m.status === "wedding" && m.weddingLobbyCode) {
    strokes = loadRoomStrokesCached(m.weddingLobbyCode);
  }
  return marriage.publicMarriage(m, viewerId, db.users, { strokes });
}

function weddingStrokesReadyFor(m) {
  if (!m || m.status !== "wedding") return true;
  if (!m.weddingLobbyCode) return false;
  try {
    return marriage.areWeddingStrokesReady(
      loadRoomStrokesCached(m.weddingLobbyCode),
      m.a,
      m.b
    );
  } catch {
    return false;
  }
}

function sortFriendsSpouseFirst(cards) {
  return [...cards].sort((a, b) => {
    if (a.isSpouse && !b.isSpouse) return -1;
    if (!a.isSpouse && b.isSpouse) return 1;
    if (a.isFiance && !b.isFiance) return -1;
    if (!a.isFiance && b.isFiance) return 1;
    return 0;
  });
}

function trackFriendshipLevelAch(user, level) {
  ach.setMetricAtLeast(user, "friendship_level_max", level, todayKey(), (uid, d, r, ref) =>
    applyLedger(uid, d, r, ref)
  );
  if (level >= 10) trackAch(user, "friendship_lvl_10", 1);
  if (level >= 25) trackAch(user, "friendship_lvl_25", 1);
  if (level >= 50) trackAch(user, "friendship_lvl_50", 1);
  if (level >= 75) trackAch(user, "friendship_lvl_75", 1);
  if (level >= 100) trackAch(user, "friendship_lvl_100", 1);
}

function isWeddingPaintMeta(meta) {
  return Boolean(meta?.isWedding) && !meta?.isWeddingCeremony;
}

function getWeddingPaintRoom(code) {
  const c = String(code || "").trim().toUpperCase();
  if (!c) return null;
  let room = rooms.get(c) || null;
  if (!room) {
    const saved = getDb().rooms?.[c];
    if (saved) {
      room = hydrateRoom(c, saved);
      if (room) rooms.set(c, room);
    }
  }
  if (room && isWeddingPaintMeta(room)) return room;
  return null;
}

/** Alle Hochzeitsbild-Codes (ohne Zeremonie) für ein Paar. */
function collectWeddingPaintCodesForCouple(userA, userB, marriageId) {
  const codes = new Set();
  const mid = String(marriageId || "").trim();
  const aId = userA?.id;
  const bId = userB?.id;
  function scanHosted(u) {
    if (!u?.hostedRooms || typeof u.hostedRooms !== "object") return;
    for (const [code, meta] of Object.entries(u.hostedRooms)) {
      if (isWeddingPaintMeta(meta)) codes.add(String(code).trim().toUpperCase());
    }
  }
  scanHosted(userA);
  scanHosted(userB);
  for (const [code, room] of rooms.entries()) {
    if (!isWeddingPaintMeta(room)) continue;
    const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
    if (mid && String(room.marriageId || "") === mid) {
      codes.add(String(code).trim().toUpperCase());
      continue;
    }
    if (aId && bId && members.includes(aId) && members.includes(bId)) {
      codes.add(String(code).trim().toUpperCase());
    }
  }
  const dbRooms = getDb().rooms || {};
  for (const [code, saved] of Object.entries(dbRooms)) {
    if (!isWeddingPaintMeta(saved)) continue;
    const members = Array.isArray(saved.memberUserIds) ? saved.memberUserIds : [];
    if (mid && String(saved.marriageId || "") === mid) {
      codes.add(String(code).trim().toUpperCase());
      continue;
    }
    if (aId && bId && members.includes(aId) && members.includes(bId)) {
      codes.add(String(code).trim().toUpperCase());
    }
  }
  return [...codes].filter(Boolean);
}

function syncWeddingPaintHostedMeta(userA, userB, code, room, { retake = false } = {}) {
  const c = String(code || "").trim().toUpperCase();
  if (!c || !room) return;
  const meta = {
    createdAt: room.createdAt || Date.now(),
    isFree: true,
    isRandom: false,
    isWedding: true,
    isWeddingCeremony: false,
    weddingRetake: Boolean(retake || room.weddingRetake),
    name: room.name || "Hochzeitsbild",
    capacity: 2,
    token: room.token,
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    colorByUserId: room.colorByUserId || {},
    lastCanvasAt: Number(room.lastCanvasAt) || 0,
    memberUserIds: [userA.id, userB.id],
    marriageId: room.marriageId || null,
  };
  ensureHostedRooms(userA);
  ensureHostedRooms(userB);
  userA.hostedRooms[c] = { ...meta };
  userB.hostedRooms[c] = { ...meta, coHost: true };
  forgetJoinedLobby(userA, c);
  forgetJoinedLobby(userB, c);
}

/** Überzählige Hochzeitsbild-Lobbys auflösen — keepCode behalten. */
function dissolveOrphanWeddingPaintLobbies(userA, userB, marriageRec, keepCode) {
  const keep = String(keepCode || "").trim().toUpperCase();
  const codes = collectWeddingPaintCodesForCouple(
    userA,
    userB,
    marriageRec?.id || marriage.pairKey(userA.id, userB.id)
  );
  let removed = 0;
  for (const code of codes) {
    if (keep && code === keep) continue;
    try {
      console.log(
        `dissolve orphan wedding-paint ${code} keep=${keep || "-"} marriage=${marriageRec?.id || "?"}`
      );
      dissolveWeddingLobby(code);
      removed += 1;
    } catch (e) {
      console.error("dissolve orphan wedding-paint", code, e);
    }
  }
  return removed;
}

function pickCanonicalWeddingPaintCode(userA, userB, marriageRec, { preferRetake = null } = {}) {
  const preferred = String(marriageRec?.weddingLobbyCode || "").trim().toUpperCase();
  if (preferred) {
    const room = getWeddingPaintRoom(preferred);
    if (room) {
      if (preferRetake == null || Boolean(room.weddingRetake) === Boolean(preferRetake)) {
        return preferred;
      }
    }
  }
  const codes = collectWeddingPaintCodesForCouple(
    userA,
    userB,
    marriageRec?.id || marriage.pairKey(userA.id, userB.id)
  );
  let best = null;
  let bestScore = -1;
  for (const code of codes) {
    const room = getWeddingPaintRoom(code);
    if (!room) continue;
    if (preferRetake != null && Boolean(room.weddingRetake) !== Boolean(preferRetake)) continue;
    let strokes = 0;
    try {
      strokes = ensureRoomStrokes(room, code).length;
    } catch {
      strokes = Array.isArray(room.strokes) ? room.strokes.length : 0;
    }
    const score =
      strokes * 1_000_000 +
      (Number(room.lastCanvasAt) || 0) +
      (Number(room.createdAt) || 0) / 1e13;
    if (score > bestScore) {
      bestScore = score;
      best = code;
    }
  }
  return best;
}

/** Einmalig: alle Ehen auf max. 1 Hochzeitsbild-Lobby bereinigen. */
function cleanupAllDuplicateWeddingPaintLobbies() {
  const db = getDb();
  let removed = 0;
  for (const m of Object.values(marriage.ensureMarriages(db))) {
    if (!m) continue;
    const a = db.users?.[m.a];
    const b = db.users?.[m.b];
    if (!a || !b) continue;
    const keep = pickCanonicalWeddingPaintCode(a, b, m);
    if (!keep) continue;
    if (String(m.weddingLobbyCode || "").trim().toUpperCase() !== keep) {
      m.weddingLobbyCode = keep;
    }
    const room = getWeddingPaintRoom(keep);
    if (room) syncWeddingPaintHostedMeta(a, b, keep, room, { retake: Boolean(room.weddingRetake) });
    removed += dissolveOrphanWeddingPaintLobbies(a, b, m, keep);
  }
  if (removed > 0) {
    persistRooms();
    scheduleSave();
    console.log(`cleanup duplicate wedding-paint: removed ${removed}`);
  }
  return removed;
}

function createWeddingLobby(userA, userB, marriageRec) {
  const existingCode = pickCanonicalWeddingPaintCode(userA, userB, marriageRec, {
    preferRetake: false,
  });
  if (existingCode) {
    const room = getWeddingPaintRoom(existingCode);
    if (room) {
      room.isWedding = true;
      room.isWeddingCeremony = false;
      room.weddingRetake = false;
      room.marriageId = marriageRec.id || room.marriageId;
      room.memberUserIds = [userA.id, userB.id];
      syncWeddingPaintHostedMeta(userA, userB, existingCode, room, { retake: false });
      marriageRec.weddingLobbyCode = existingCode;
      if (!marriageRec.weddingStartedAt) {
        marriageRec.weddingStartedAt = room.createdAt || Date.now();
      }
      if (!marriageRec.weddingEndsAt) {
        marriageRec.weddingEndsAt =
          (Number(marriageRec.weddingStartedAt) || Date.now()) + marriage.WEDDING_LOBBY_MS;
      }
      marriageRec.status = "wedding";
      dissolveOrphanWeddingPaintLobbies(userA, userB, marriageRec, existingCode);
      persistRooms();
      scheduleSave();
      emitMarriageLiveUpdate(
        marriageRec,
        "wedding",
        "Hochzeitsbild-Lobby ist offen"
      );
      return existingCode;
    }
  }

  let code = randomCode();
  while (rooms.has(code) || getDb().rooms?.[code]) code = randomCode();
  const token = randomToken().slice(0, 32);
  const hostColorSide = normalizeHostColorSide(userA.colorSide || "blue");
  const hostIdx = hostColorIndex(hostColorSide);
  const guestIdx = oppositeHostColor(hostIdx);
  const now = Date.now();
  const room = {
    token,
    createdAt: now,
    lastActiveAt: now,
    sockets: new Map(),
    clearProposal: null,
    game: null,
    gameTimer: null,
    hostUserId: userA.id,
    createdByUserId: userA.id,
    name: "Hochzeitsbild",
    hostNickname: userA.nickname || "Host",
    isFree: true,
    isRandom: false,
    isWedding: true,
    isWeddingCeremony: false,
    weddingRetake: false,
    capacity: 2,
    hostColorSide,
    colorByUserId: { [userA.id]: hostIdx, [userB.id]: guestIdx },
    peakPeers: 2,
    lastCanvasAt: 0,
    memberUserIds: [userA.id, userB.id],
    joinAnnouncedUserIds: [userA.id, userB.id],
    publicShare: { day: "", count: 0, nextAt: 0 },
    publicProposal: null,
    strokes: [],
    stickers: [],
    marriageId: marriageRec.id,
  };
  rooms.set(code, room);
  syncWeddingPaintHostedMeta(userA, userB, code, room, { retake: false });
  marriageRec.weddingLobbyCode = code;
  marriageRec.weddingStartedAt = now;
  marriageRec.weddingEndsAt = now + marriage.WEDDING_LOBBY_MS;
  marriageRec.status = "wedding";
  dissolveOrphanWeddingPaintLobbies(userA, userB, marriageRec, code);
  persistRooms();
  emitMarriageLiveUpdate(marriageRec, "wedding", "Hochzeitsbild-Lobby ist offen");
  return code;
}

/**
 * Fix: Ehe ohne Hochzeitsbild → nachträgliche Hochzeitsleinwand.
 * Status bleibt "married"; Speichern erst nach Bestätigung beider Partner.
 */
function createWeddingImageRetakeLobby(userA, userB, marriageRec) {
  const existingCode = pickCanonicalWeddingPaintCode(userA, userB, marriageRec, {
    preferRetake: true,
  }) || pickCanonicalWeddingPaintCode(userA, userB, marriageRec);
  if (existingCode) {
    const room = getWeddingPaintRoom(existingCode);
    if (room) {
      room.isWedding = true;
      room.weddingRetake = true;
      room.marriageId =
        marriageRec.id || marriage.pairKey(userA.id, userB.id) || room.marriageId;
      room.memberUserIds = [userA.id, userB.id];
      syncWeddingPaintHostedMeta(userA, userB, existingCode, room, { retake: true });
      marriageRec.weddingLobbyCode = existingCode;
      marriageRec.weddingImageRetake = true;
      if (!marriageRec.weddingConfirm || typeof marriageRec.weddingConfirm !== "object") {
        marriageRec.weddingConfirm = { [userA.id]: false, [userB.id]: false };
      }
      if (!marriageRec.weddingStartedAt) marriageRec.weddingStartedAt = Date.now();
      dissolveOrphanWeddingPaintLobbies(userA, userB, marriageRec, existingCode);
      persistRooms();
      scheduleSave();
      return existingCode;
    }
  }

  let code = randomCode();
  while (rooms.has(code) || getDb().rooms?.[code]) code = randomCode();
  const token = randomToken().slice(0, 32);
  const hostColorSide = normalizeHostColorSide(userA.colorSide || "blue");
  const hostIdx = hostColorIndex(hostColorSide);
  const guestIdx = oppositeHostColor(hostIdx);
  const now = Date.now();
  const room = {
    token,
    createdAt: now,
    lastActiveAt: now,
    sockets: new Map(),
    clearProposal: null,
    game: null,
    gameTimer: null,
    hostUserId: userA.id,
    createdByUserId: userA.id,
    name: "Hochzeitsbild",
    hostNickname: userA.nickname || "Host",
    isFree: true,
    isRandom: false,
    isWedding: true,
    weddingRetake: true,
    capacity: 2,
    hostColorSide,
    colorByUserId: { [userA.id]: hostIdx, [userB.id]: guestIdx },
    peakPeers: 2,
    lastCanvasAt: 0,
    memberUserIds: [userA.id, userB.id],
    joinAnnouncedUserIds: [userA.id, userB.id],
    publicShare: { day: "", count: 0, nextAt: 0 },
    publicProposal: null,
    strokes: [],
    stickers: [],
    marriageId: marriageRec.id || marriage.pairKey(userA.id, userB.id),
  };
  rooms.set(code, room);
  syncWeddingPaintHostedMeta(userA, userB, code, room, { retake: true });
  marriageRec.weddingLobbyCode = code;
  marriageRec.weddingImageRetake = true;
  marriageRec.weddingConfirm = { [userA.id]: false, [userB.id]: false };
  marriageRec.weddingStartedAt = now;
  marriageRec.weddingEndsAt = 0;
  dissolveOrphanWeddingPaintLobbies(userA, userB, marriageRec, code);
  persistRooms();
  scheduleSave();
  return code;
}

function ensureWeddingImageRetake(m) {
  if (!m || m.status !== "married") return null;
  if (m.weddingImageFile) return null;
  const db = getDb();
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (!a || !b) return null;
  const code = String(m.weddingLobbyCode || "").trim().toUpperCase();
  if (code) {
    let room = rooms.get(code);
    if (!room) {
      const saved = db.rooms?.[code];
      if (saved) {
        room = hydrateRoom(code, saved);
        if (room) rooms.set(code, room);
      }
    }
    if (room && room.isWedding) {
      room.weddingRetake = true;
      m.weddingImageRetake = true;
      if (!m.weddingConfirm || typeof m.weddingConfirm !== "object") {
        m.weddingConfirm = { [m.a]: false, [m.b]: false };
      }
      ensureHostedRooms(a);
      ensureHostedRooms(b);
      const meta = {
        createdAt: room.createdAt || Date.now(),
        isFree: true,
        isRandom: false,
        isWedding: true,
        weddingRetake: true,
        name: room.name || "Hochzeitsbild",
        capacity: 2,
        token: room.token,
        hostColorSide: normalizeHostColorSide(room.hostColorSide),
        colorByUserId: room.colorByUserId || {},
        lastCanvasAt: Number(room.lastCanvasAt) || 0,
      };
      a.hostedRooms[code] = { ...meta };
      b.hostedRooms[code] = { ...meta, coHost: true };
      return code;
    }
    m.weddingLobbyCode = null;
  }
  return createWeddingImageRetakeLobby(a, b, m);
}

function ensureAllWeddingImageRetakes() {
  const db = getDb();
  let n = 0;
  for (const m of Object.values(marriage.ensureMarriages(db))) {
    if (!m || m.status !== "married" || m.weddingImageFile) continue;
    if (ensureWeddingImageRetake(m)) n += 1;
  }
  if (n > 0) scheduleSave();
  return n;
}

function weddingConfirmState(m, viewerId) {
  const conf = m?.weddingConfirm && typeof m.weddingConfirm === "object" ? m.weddingConfirm : {};
  const partnerId = marriage.partnerIdOf(m, viewerId);
  return {
    weddingImageRetake: Boolean(m?.weddingImageRetake),
    weddingConfirmMine: Boolean(conf[viewerId]),
    weddingConfirmPartner: partnerId ? Boolean(conf[partnerId]) : false,
    weddingConfirmReady: Boolean(conf[m?.a]) && Boolean(conf[m?.b]),
  };
}

function completeWeddingImageRetake(m) {
  if (!m || m.status !== "married" || !m.weddingImageRetake) {
    return { ok: false, error: "no_retake" };
  }
  const conf = m.weddingConfirm && typeof m.weddingConfirm === "object" ? m.weddingConfirm : {};
  if (!conf[m.a] || !conf[m.b]) {
    return { ok: false, error: "need_both" };
  }
  const code = String(m.weddingLobbyCode || "").trim().toUpperCase();
  if (!code) return { ok: false, error: "no_lobby" };
  const snap = path.join(SNAPSHOT_DIR, `${code}.png`);
  if (!fs.existsSync(snap)) {
    return {
      ok: false,
      error: "need_snapshot",
      message: "Bitte erst etwas auf die Leinwand malen.",
    };
  }
  ensureWeddingDir();
  const destName = `${m.id || marriage.pairKey(m.a, m.b)}.png`;
  const dest = path.join(WEDDING_DIR, destName);
  try {
    fs.copyFileSync(snap, dest);
    m.weddingImageFile = destName;
  } catch (e) {
    console.error("wedding retake image copy failed", e);
    return { ok: false, error: "copy_failed", message: "Bild speichern fehlgeschlagen." };
  }
  dissolveWeddingLobby(code);
  m.weddingLobbyCode = null;
  m.weddingImageRetake = false;
  m.weddingConfirm = null;
  m.weddingStartedAt = 0;
  m.weddingEndsAt = 0;
  scheduleSave();
  return { ok: true };
}

function dissolveWeddingLobby(code) {
  if (!code) return;
  const room = rooms.get(code) || null;
  const saved = getDb().rooms?.[code];
  const members = room?.memberUserIds || saved?.memberUserIds || [];
  const db = getDb();
  for (const uid of members) {
    const u = db.users?.[uid];
    if (u) {
      releaseHostedRoom(code, uid);
      forgetJoinedLobby(u, code);
      if (u.hostedRooms?.[code]) delete u.hostedRooms[code];
    }
  }
  if (room) {
    for (const sock of [...(room.sockets?.values?.() || [])]) {
      try {
        sock.close(4001, "wedding_ended");
      } catch {
        /* ignore */
      }
    }
    rooms.delete(code);
  }
  if (db.rooms?.[code]) delete db.rooms[code];
  persistRooms();
}

const CONTEST_IMG_DIR = path.join(DATA_DIR, "contest");

function ensureContestImgDir() {
  fs.mkdirSync(CONTEST_IMG_DIR, { recursive: true });
}

/** Event-Lobby beenden: Snapshot → Contest-Entry → Room auflösen. */
function closeEventLobbyRoom(code, room) {
  if (!room?.eventId) return false;
  const db = getDb();
  const engine = require("./event_engine");
  const cfg = seasonEvents.ensureEventsConfig(db);
  const ev = (cfg.events || []).find((e) => e && e.id === room.eventId);
  const hostId = room.hostUserId || room.createdByUserId;
  const host = hostId ? db.users?.[hostId] : null;
  let imagePath = null;
  try {
    ensureContestImgDir();
    const mem = canvasMemories()?.[code];
    const snapName = mem?.file || `${code}.png`;
    const snapPath = path.join(SNAPSHOT_DIR, snapName);
    if (fs.existsSync(snapPath)) {
      const destRel = path.join("contest", `${room.eventId}_${code}.png`);
      const destAbs = path.join(DATA_DIR, destRel);
      fs.copyFileSync(snapPath, destAbs);
      imagePath = destRel.replace(/\\/g, "/");
      try {
        require("./contest_thumbs").warmContestThumb(destAbs);
      } catch (thumbErr) {
        console.error("closeEventLobby thumb", thumbErr);
      }
    }
  } catch (e) {
    console.error("closeEventLobby snapshot", e);
  }
  if (host && ev) {
    ensureRoomStrokes(room, code);
    const strokes = Array.isArray(room.strokes) ? room.strokes.length : 0;
    const prog = engine.ensureUserProgress(host, room.eventId);
    const strokeCount = Math.max(strokes, prog.qualifiedStrokes || 0);
    const submitted = engine.submitContestEntry(db, host, ev, {
      lobbyCode: code,
      imagePath,
      strokes: strokeCount,
      prompt: room.eventPrompt || prog.eventPrompt || null,
    });
    if (!submitted.ok && submitted.error !== "max_entries" && submitted.error !== "too_few_strokes") {
      console.error("closeEventLobby submit", submitted.error, submitted.message);
    }
  }
  for (const sock of [...(room.sockets?.values?.() || [])]) {
    try {
      sock.send(JSON.stringify({ type: "event_lobby_ended", eventId: room.eventId }));
    } catch {
      /* ignore */
    }
    try {
      sock.close(4002, "event_ended");
    } catch {
      /* ignore */
    }
  }
  forceDissolveLobby(code);
  scheduleSave();
  console.log(`closed event lobby ${code} event=${room.eventId}`);
  return true;
}

function tickEventLobbiesAndContests() {
  const db = getDb();
  const engine = require("./event_engine");
  const now = Date.now();
  const nowDate = new Date(now);
  // Event-Lobbys schließen
  for (const [code, room] of [...rooms.entries()]) {
    if (!room?.eventId || !room.eventEndsAt) continue;
    const ends = Date.parse(String(room.eventEndsAt));
    if (!Number.isFinite(ends) || now < ends) continue;
    closeEventLobbyRoom(code, room);
  }
  // Disk-only rooms
  for (const [code, data] of Object.entries(db.rooms || {})) {
    if (rooms.has(code)) continue;
    if (!data?.eventId || !data.eventEndsAt) continue;
    const ends = Date.parse(String(data.eventEndsAt));
    if (!Number.isFinite(ends) || now < ends) continue;
    const hydrated = hydrateRoom(code, data);
    rooms.set(code, hydrated);
    closeEventLobbyRoom(code, hydrated);
  }
  // Contests finalisieren
  const cfg = seasonEvents.ensureEventsConfig(db);
  let dirty = false;
  for (const ev of cfg.events || []) {
    const pub = seasonEvents.publicEvent(ev);
    if (!pub?.contest?.enabled) continue;
    const occ = seasonEvents.nextOccurrence(ev, nowDate);
    const active = seasonEvents.isActiveAtPatched(ev, nowDate);
    if (active) continue;
    const windowEnd =
      engine.eventWindowEndIso(ev, nowDate) ||
      engine.eventWindowEndIsoFromOccEnd(occ?.end) ||
      null;
    const { until } = engine.resolveVoteBounds(pub.contest, windowEnd, now);
    if (until == null || now <= until) continue;
    const r = engine.finalizeContestPrizes(db, ev, db.users);
    if (r?.ok && !r.already) dirty = true;
  }
  if (dirty) scheduleSave();
}

/** Snapshot der Mal-Lobby sichern und Mal-Lobby auflösen (noch keine Ehe). */
function captureWeddingImageAndClosePaintLobby(m) {
  if (!m) return false;
  const code = m.weddingLobbyCode;
  if (code) {
    const snap = path.join(SNAPSHOT_DIR, `${code}.png`);
    if (fs.existsSync(snap)) {
      ensureWeddingDir();
      const destName = weddingPairFileName(m);
      const dest = path.join(WEDDING_PENDING_DIR, destName);
      try {
        fs.copyFileSync(snap, dest);
        m.weddingImagePending = destName;
        // Finales Bild erst nach beiden Ja — sonst Boot-Recovery → fälschlich married
        if (m.weddingImageFile) {
          try {
            const old = path.join(WEDDING_DIR, path.basename(String(m.weddingImageFile)));
            if (fs.existsSync(old)) fs.unlinkSync(old);
          } catch {
            /* ignore */
          }
          m.weddingImageFile = null;
        }
      } catch (e) {
        console.error("wedding image copy failed", e);
      }
    }
    dissolveWeddingLobby(code);
  }
  m.weddingLobbyCode = null;
  return true;
}

/** Mal-Phase fertig → Warte auf Zeremonie (keine Auto-Ehe mehr). */
function markCeremonyPending(m, { force = false } = {}) {
  if (!m || m.status !== "wedding") return false;
  if (!force && !weddingStrokesReadyFor(m)) return false;
  captureWeddingImageAndClosePaintLobby(m);
  m.status = "ceremony_pending";
  m.weddingEndsAt = Date.now();
  const c = weddingCeremony.ensureCeremony(m);
  c.phase = "presence";
  c.startConfirm = {};
  emitMarriageLiveUpdate(
    m,
    "ceremony_pending",
    "Hochzeitsbild fertig — Zeremonie vorbereiten"
  );
  return true;
}

/** Nach dem Ja: Gäste raus, Kapellen-Lobby nur noch fürs Ehepaar (24h Geschenke). */
/** Nach dem Ja: Kapelle bleibt offen für Gäste (Empfang / Geschenke), kein Kick. */
function keepCeremonyLobbyForGifts(m) {
  const code = m?.ceremonyLobbyCode;
  if (!code) return;
  const db = getDb();
  const room = rooms.get(code) || db.rooms?.[code] || null;
  if (!room?.isWeddingCeremony && !db.rooms?.[code]?.isWeddingCeremony) return;
  const c = weddingCeremony.ensureCeremony(m);
  const inReception =
    c.pastorPhase === "reception" || c.phase === "reception";
  // Empfangsende nur setzen wenn Empfang läuft — nicht schon während Pastor-Rede
  if (inReception && !c.receptionEndsAt) {
    c.receptionEndsAt = Date.now() + weddingCeremony.RECEPTION_MS;
  }
  const ends =
    Number(c.receptionEndsAt) ||
    Number(m.giftWindowEndsAt) ||
    Date.now() + weddingCeremony.RECEPTION_MS;
  // Nach Ja / im Empfang: Phase immer "open" (nicht "none" stehen lassen)
  if (m.status === "married" || inReception) {
    if (!m.giftPhase || m.giftPhase === "none") m.giftPhase = "open";
    if (inReception) m.giftWindowEndsAt = ends;
  }
  room.capacity = Math.max(room.capacity || 10, weddingCeremony.CEREMONY_CAPACITY);
  room.giftWindowEndsAt = Number(m.giftWindowEndsAt) || ends;
  room.giftPhase =
    m.giftPhase && m.giftPhase !== "none" ? m.giftPhase : "open";
  m.ceremonyMemberIds = Array.isArray(room.memberUserIds)
    ? [...room.memberUserIds]
    : [m.a, m.b].filter(Boolean);
  if (db.rooms?.[code]) {
    db.rooms[code].capacity = room.capacity;
    db.rooms[code].giftWindowEndsAt = room.giftWindowEndsAt;
    db.rooms[code].giftPhase = room.giftPhase;
  }
  for (const uid of [m.a, m.b]) {
    const u = db.users?.[uid];
    if (!u) continue;
    ensureHostedRooms(u);
    const prev = u.hostedRooms[code] || {};
    u.hostedRooms[code] = {
      ...prev,
      isWeddingCeremony: true,
      isWedding: false,
      name: prev.name || room.name || "Hochzeit",
      token: prev.token || room.token,
      capacity: room.capacity,
      giftWindowEndsAt: room.giftWindowEndsAt,
      giftPhase: room.giftPhase,
      ceremonyAt: Number(m.ceremonyAt) || Number(prev.ceremonyAt) || 0,
      coupleNicknames:
        prev.coupleNicknames ||
        room.coupleNicknames || {
          a: String(db.users?.[m.a]?.nickname || "").slice(0, 18),
          b: String(db.users?.[m.b]?.nickname || "").slice(0, 18),
        },
    };
  }
  if (!rooms.has(code) && room) rooms.set(code, room);
  persistRooms();
}

function syncCeremonyGiftMeta(m) {
  const code = m?.ceremonyLobbyCode;
  if (!code) return;
  const db = getDb();
  const room = rooms.get(code) || db.rooms?.[code];
  if (!room) return;
  room.giftWindowEndsAt = Number(m.giftWindowEndsAt) || 0;
  room.giftPhase = m.giftPhase || "none";
  if (db.rooms?.[code]) {
    db.rooms[code].giftWindowEndsAt = room.giftWindowEndsAt;
    db.rooms[code].giftPhase = room.giftPhase;
  }
  for (const uid of [m.a, m.b]) {
    const u = db.users?.[uid];
    if (u?.hostedRooms?.[code]) {
      u.hostedRooms[code].giftWindowEndsAt = room.giftWindowEndsAt;
      u.hostedRooms[code].giftPhase = room.giftPhase;
    }
  }
}

/** Gäste aus der Kapelle werfen — Brautpaar bleibt für Geschenke-Claim. */
function kickCeremonyGuests(m) {
  const code = m?.ceremonyLobbyCode;
  if (!code) return 0;
  const db = getDb();
  const room = rooms.get(code) || db.rooms?.[code];
  if (!room) return 0;
  const couple = new Set([m.a, m.b].filter(Boolean));
  const members = Array.isArray(room.memberUserIds) ? [...room.memberUserIds] : [];
  let kicked = 0;
  for (const uid of members) {
    if (couple.has(uid)) continue;
    const u = db.users?.[uid];
    if (u) {
      forgetJoinedLobby(u, code);
      if (u.hostedRooms?.[code]) delete u.hostedRooms[code];
      releaseHostedRoom(code, uid);
    }
    kicked++;
  }
  room.memberUserIds = members.filter((id) => couple.has(id));
  m.ceremonyMemberIds = [...room.memberUserIds];
  if (room.sockets && typeof room.sockets.values === "function") {
    for (const sock of [...room.sockets.values()]) {
      const uid = sock?.luvUserId;
      if (uid && !couple.has(uid)) {
        try {
          sock.close(4001, "reception_over");
        } catch {
          /* ignore */
        }
      }
    }
  }
  if (db.rooms?.[code]) {
    db.rooms[code].memberUserIds = [...room.memberUserIds];
  }
  persistRooms();
  return kicked;
}

/** Ein Ehepartner nach Claim aus der Kapellen-Lobby nehmen. */
function removeCoupleMemberFromCeremonyLobby(m, userId) {
  const code = m?.ceremonyLobbyCode;
  if (!code || !userId) return;
  const db = getDb();
  const room = rooms.get(code) || db.rooms?.[code];
  const u = db.users?.[userId];
  if (u) {
    forgetJoinedLobby(u, code);
    if (u.hostedRooms?.[code]) delete u.hostedRooms[code];
    releaseHostedRoom(code, userId);
  }
  if (room) {
    room.memberUserIds = (room.memberUserIds || []).filter((id) => id !== userId);
    m.ceremonyMemberIds = [...room.memberUserIds];
    if (room.sockets && typeof room.sockets.values === "function") {
      for (const sock of [...room.sockets.values()]) {
        if (sock?.luvUserId === userId) {
          try {
            sock.close(4001, "gifts_claimed");
          } catch {
            /* ignore */
          }
        }
      }
    }
    if (db.rooms?.[code]) {
      db.rooms[code].memberUserIds = [...room.memberUserIds];
    }
  }
  persistRooms();
}

/** Empfang vorbei: Gäste raus, Geschenke würfeln, Paar kann abholen. */
function endCeremonyReception(m) {
  if (!m || m.status !== "married") return { ok: false };
  const c = weddingCeremony.ensureCeremony(m);
  if (c.receptionEnded) return { ok: true, already: true };
  kickCeremonyGuests(m);
  weddingGifts.ensureGiftState(m);
  if (m.giftPhase === "open" || m.giftPhase === "none") {
    m.giftWindowEndsAt = Date.now();
    weddingGifts.rollGiftPool(m);
  }
  c.receptionEnded = true;
  c.phase = "gifts_claim";
  c.pastorPhase = "gifts_claim";
  syncCeremonyGiftMeta(m);
  // Keine Gäste mehr → nur Paar in hostedRooms mit rolled
  return { ok: true, giftsRolled: m.giftPhase === "rolled" };
}

function finalizeWeddingMarriage(m, { force = false } = {}) {
  // Ehepartner erst nach beiderseitigem Ja am Altar (Staff: force + bothVowsOk)
  if (
    !m ||
    (m.status !== "ceremony_scheduled" &&
      m.status !== "ceremony_pending" &&
      m.status !== "wedding")
  ) {
    return false;
  }
  const c0 = weddingCeremony.ensureCeremony(m);
  const vowA = c0.vows?.[m.a];
  const vowB = c0.vows?.[m.b];
  const bothSaidYes =
    vowA?.choice === "yes" &&
    vowB?.choice === "yes" &&
    (Number(vowA.progress) || 0) >= 1 &&
    (Number(vowB.progress) || 0) >= 1;
  if (!bothSaidYes && !force) {
    return false;
  }
  // Mal-Phase allein macht niemanden zum Ehepartner
  if (m.status === "wedding" && !bothSaidYes && !force) {
    return false;
  }
  const db = getDb();
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (m.status === "wedding") {
    captureWeddingImageAndClosePaintLobby(m);
  }
  // Erst jetzt: Pending-Bild → echtes Hochzeitsbild (Ehepartner)
  promotePendingWeddingImage(m);
  m.status = "married";
  m.marriedAt = Date.now();
  m.weddingLobbyCode = null;
  const c = weddingCeremony.ensureCeremony(m);
  if (!Array.isArray(m.guestbook)) m.guestbook = [];
  weddingGifts.openGiftWindow(m, m.marriedAt);
  // Pastor-Rede zuerst; Empfangs-Timer (60 Min) startet erst bei reception
  weddingCeremony.startMarriedSpeech(m);
  c.receptionEnded = false;
  // Vorläufiges Geschenkfenster bis Empfang startet (dann auf Empfangsende gesetzt)
  if (!m.giftWindowEndsAt) {
    m.giftWindowEndsAt = m.marriedAt + weddingCeremony.RECEPTION_MS;
  }
  if (m.ceremonyLobbyCode) {
    keepCeremonyLobbyForGifts(m);
  }
  if (a) {
    marriage.grantMarriageItem(a);
    trackAch(a, "married", 1);
    tryClaimAchievementReward(a, "fs_married");
  }
  if (b) {
    marriage.grantMarriageItem(b);
    trackAch(b, "married", 1);
    tryClaimAchievementReward(b, "fs_married");
  }
  // Home-Feed (schmale Kachel) statt aufdringlicher Live-Banner
  try {
    const nameA = String(a?.nickname || "Jemand").trim().slice(0, 18) || "Jemand";
    const nameB = String(b?.nickname || "Jemand").trim().slice(0, 18) || "Jemand";
    const when = new Date(m.marriedAt).toLocaleString("de-DE", {
      timeZone: "Europe/Berlin",
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
    homeFeed.publish(db, {
      kind: "wedding",
      shortText: `${nameA} & ${nameB} haben geheiratet!`,
      title: `${nameA} & ${nameB} haben geheiratet!`,
      body:
        `${nameA} und ${nameB} haben sich am ${when} das Ja-Wort gegeben. ` +
        `Tippe hier, um das Hochzeitsbild zu öffnen — Gästebuch und Geschenke sind eine Stunde lang möglich.`,
      ttlMs: weddingCeremony.RECEPTION_MS,
      actionType: "wedding_image",
      actionPayload: {
        marriageId: m.id || marriage.pairKey(m.a, m.b),
        userIdA: m.a,
        userIdB: m.b,
        nicknameA: nameA,
        nicknameB: nameB,
      },
    });
  } catch (e) {
    console.error("wedding home feed failed", e);
  }
  emitMarriageLiveUpdate(m, "married", "Ihr seid verheiratet");
  return true;
}

function createWeddingCeremonyLobby(
  userA,
  userB,
  marriageRec,
  ceremonyAt,
  opts = {}
) {
  const booking = weddingCeremony.booking;
  const layoutId =
    String(opts.layoutId || marriageRec.ceremonyLayoutId || "wedding").trim() ||
    "wedding";
  const roomDef = booking.getRoomDef(layoutId) || booking.getRoomDef("wedding");
  const capacity = Math.min(
    booking.MAX_CAPACITY,
    Math.max(
      booking.COUPLE_SEATS + 1,
      Number(opts.capacity) || roomDef?.capacity || 8
    )
  );
  const moneyTreesEnabled =
    opts.moneyTreesEnabled != null
      ? Boolean(opts.moneyTreesEnabled)
      : marriageRec.moneyTreesEnabled === true;
  let code = randomCode();
  while (rooms.has(code) || getDb().rooms?.[code]) code = randomCode();
  const token = randomToken().slice(0, 32);
  const hostColorSide = normalizeHostColorSide(userA.colorSide || "blue");
  const hostIdx = hostColorIndex(hostColorSide);
  const guestIdx = oppositeHostColor(hostIdx);
  const now = Date.now();
  const nickA = String(userA.nickname || "").trim().slice(0, 18) || "Partner";
  const nickB = String(userB.nickname || "").trim().slice(0, 18) || "Partner";
  const lobbyName = roomDef?.name ? `Hochzeit · ${roomDef.name}` : "Hochzeit";
  const room = {
    token,
    createdAt: now,
    lastActiveAt: now,
    sockets: new Map(),
    clearProposal: null,
    game: null,
    gameTimer: null,
    hostUserId: userA.id,
    createdByUserId: userA.id,
    name: lobbyName,
    hostNickname: nickA,
    isFree: true,
    isRandom: false,
    isWedding: false,
    isWeddingCeremony: true,
    weddingRetake: false,
    capacity,
    ceremonyLayoutId: layoutId,
    moneyTreesEnabled,
    hostColorSide,
    colorByUserId: { [userA.id]: hostIdx, [userB.id]: guestIdx },
    peakPeers: 2,
    lastCanvasAt: 0,
    memberUserIds: [userA.id, userB.id],
    joinAnnouncedUserIds: [userA.id, userB.id],
    publicShare: { day: "", count: 0, nextAt: 0 },
    publicProposal: null,
    strokes: [],
    stickers: [],
    marriageId: marriageRec.id || marriage.pairKey(userA.id, userB.id),
    ceremonyAt: Number(ceremonyAt) || 0,
    coupleNicknames: { a: nickA, b: nickB },
    coupleUserIds: [userA.id, userB.id],
  };
  rooms.set(code, room);
  const meta = {
    createdAt: now,
    isFree: true,
    isRandom: false,
    isWedding: false,
    isWeddingCeremony: true,
    weddingRetake: false,
    name: lobbyName,
    capacity,
    ceremonyLayoutId: layoutId,
    moneyTreesEnabled,
    token,
    hostColorSide,
    colorByUserId: room.colorByUserId,
    lastCanvasAt: 0,
    memberUserIds: [userA.id, userB.id],
    marriageId: room.marriageId,
    ceremonyAt: room.ceremonyAt,
    coupleNicknames: room.coupleNicknames,
  };
  ensureHostedRooms(userA);
  ensureHostedRooms(userB);
  userA.hostedRooms[code] = { ...meta };
  userB.hostedRooms[code] = { ...meta, coHost: true };
  forgetJoinedLobby(userA, code);
  forgetJoinedLobby(userB, code);
  marriageRec.ceremonyLobbyCode = code;
  marriageRec.ceremonyAt = room.ceremonyAt;
  marriageRec.ceremonyMemberIds = [userA.id, userB.id];
  marriageRec.ceremonyLayoutId = layoutId;
  marriageRec.ceremonyCapacity = capacity;
  marriageRec.moneyTreesEnabled = moneyTreesEnabled;
  marriageRec.status = "ceremony_scheduled";
  const c = weddingCeremony.ensureCeremony(marriageRec);
  c.phase = "scheduled";
  c.liveWindowCleared = false;
  weddingCeremony.ensureLiveWindow(marriageRec);
  persistRooms();
  emitMarriageLiveUpdate(
    marriageRec,
    "ceremony_scheduled",
    "Hochzeit-Lobby ist bereit"
  );
  return code;
}

function dissolveWeddingCeremonyLobby(code) {
  if (!code) return;
  const room = rooms.get(code) || null;
  const saved = getDb().rooms?.[code];
  if (!room?.isWeddingCeremony && !saved?.isWeddingCeremony) {
    // Sicherheit: nie Mal-/Normal-Lobbys hier auflösen
    if (room?.isWedding || saved?.isWedding) return;
  }
  const members = room?.memberUserIds || saved?.memberUserIds || [];
  const db = getDb();
  for (const uid of members) {
    const u = db.users?.[uid];
    if (u) {
      releaseHostedRoom(code, uid);
      forgetJoinedLobby(u, code);
      if (u.hostedRooms?.[code]) delete u.hostedRooms[code];
    }
  }
  if (room) {
    for (const sock of [...(room.sockets?.values?.() || [])]) {
      try {
        sock.close(4001, "ceremony_ended");
      } catch {
        /* ignore */
      }
    }
    rooms.delete(code);
  }
  if (db.rooms?.[code]) delete db.rooms[code];
  persistRooms();
}

function findMarriageForCeremonyMember(db, userId) {
  const direct = marriage.findMarriageForUser(db, userId);
  if (direct) return direct;
  const all = marriage.ensureMarriages(db);
  for (const rec of Object.values(all)) {
    if (!rec) continue;
    if (Array.isArray(rec.ceremonyMemberIds) && rec.ceremonyMemberIds.includes(userId)) {
      return rec;
    }
    const code = rec.ceremonyLobbyCode;
    if (!code) continue;
    const room = rooms.get(code) || db.rooms?.[code];
    if (Array.isArray(room?.memberUserIds) && room.memberUserIds.includes(userId)) {
      return rec;
    }
  }
  return null;
}

function abortWeddingCeremony(
  m,
  leftByUserId,
  { divorceCooldown = true, noticeText = null, notifyBoth = false } = {}
) {
  if (!m) return;
  const db = getDb();
  const leftUser = leftByUserId ? db.users?.[leftByUserId] : null;
  const leftNick = leftUser
    ? String(leftUser.nickname || "").trim().slice(0, 18) || "Partner"
    : "Partner";
  const partnerId = weddingCeremony.partnerOf(m, leftByUserId);
  if (m.ceremonyLobbyCode) {
    dissolveWeddingCeremonyLobby(m.ceremonyLobbyCode);
    m.ceremonyLobbyCode = null;
  }
  // Gäste aus Memberliste droppen (Lobby weg)
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (divorceCooldown) {
    if (a) marriage.setDivorceCooldown(a);
    if (b) marriage.setDivorceCooldown(b);
  }
  const defaultText = divorceCooldown
    ? "Deine Hochzeit… Tippe für mehr"
    : "Die Trauung wurde abgebrochen — ihr könnt euch erneut verloben.";
  const text = String(noticeText || defaultText).trim().slice(0, 160) || defaultText;
  const pushNotice = (user, fromId, fromNick) => {
    if (!user) return;
    if (!Array.isArray(user.socialNotices)) user.socialNotices = [];
    user.socialNotices.push({
      id: newId(),
      type: "wedding_left",
      fromUserId: fromId || null,
      fromNickname: fromNick || "System",
      text,
      createdAt: Date.now(),
    });
    if (user.socialNotices.length > 30) {
      user.socialNotices = user.socialNotices.slice(-30);
    }
  };
  if (notifyBoth) {
    pushNotice(a, null, "Trauung");
    pushNotice(b, null, "Trauung");
  } else {
    const partner = partnerId ? db.users?.[partnerId] : null;
    pushNotice(partner, leftByUserId || null, leftNick);
  }
  const all = marriage.ensureMarriages(db);
  const key = m.id || marriage.pairKey(m.a, m.b);
  emitMarriageLiveUpdate(
    m,
    "cancelled",
    text || "Die Trauung wurde abgebrochen",
    leftByUserId || null
  );
  if (a) {
    marriage.clearMarriageItem(a);
    marriage.stripSpouseFromProfile(a);
  }
  if (b) {
    marriage.clearMarriageItem(b);
    marriage.stripSpouseFromProfile(b);
  }
  deleteWeddingImageFiles(m);
  delete all[key];
}

/** Nein am Altar: kein Scheidungs-/Antrags-Cooldown; beide Partner informieren. */
function abortWeddingCeremonyRejected(m, leftByUserId) {
  abortWeddingCeremony(m, leftByUserId, {
    divorceCooldown: false,
    notifyBoth: true,
    noticeText: "Die Trauung wurde abgebrochen — ihr könnt euch erneut verloben.",
  });
}

/** Coins aus Buchung zurück (beide Partner). */
function refundWeddingBooking(m) {
  if (!m) return 0;
  const b = weddingCeremony.booking.ensureBooking(m);
  if (!b.charged || b.refunded) return 0;
  const stored = Number(b.chargedBillPerPerson);
  const bill = Number.isFinite(stored) && stored >= 0
    ? stored
    : weddingCeremony.booking.billPerPerson(b);
  const roomId = b.chargedRoomId || b.roomId || null;
  if (bill > 0) {
    applyLedger(m.a, bill, "wedding_booking_refund", roomId);
    applyLedger(m.b, bill, "wedding_booking_refund", roomId);
  }
  b.refunded = true;
  return bill;
}

/**
 * 60-Min-Fenster ohne beiderseitiges Ja: Refund, Kick, Lobby weg, neu verloben möglich.
 */
function expireLiveWindowWithoutVows(m) {
  if (!m || m.status !== "ceremony_scheduled") return false;
  const c = weddingCeremony.ensureCeremony(m);
  if (c.liveWindowCleared) return false;
  weddingCeremony.ensureLiveWindow(m);
  const ends = Number(c.liveWindowEndsAt) || 0;
  if (!ends || Date.now() < ends) return false;
  refundWeddingBooking(m);
  abortWeddingCeremony(m, null, {
    divorceCooldown: false,
    notifyBoth: true,
    noticeText:
      "Die Trauungszeit ist abgelaufen — Coins zurück, kein Ja-Wort. Ihr könnt euch erneut verloben.",
  });
  return true;
}

function ceremonyLayoutIdOf(m) {
  const id = String(m?.ceremonyLayoutId || "wedding").trim();
  return roomLayouts.isWeddingBuiltin(id) ? id : "wedding";
}

function ceremonyLayoutOf(m, db) {
  return roomLayouts.getLayout(db, ceremonyLayoutIdOf(m));
}

/**
 * Sitzstatus aus Position in Admin-Rechtecken (blau=Gast, gelb=Brautpaar).
 * Mehrere Personen dürfen dieselbe Zone teilen.
 */
function syncWeddingZoneSeating(m, db, onlyUserId = null) {
  if (!m) return false;
  const c = weddingCeremony.ensureCeremony(m);
  const layout = ceremonyLayoutOf(m, db);
  let changed = false;
  const ids = onlyUserId
    ? [String(onlyUserId)]
    : [
        ...new Set([
          ...Object.keys(c.positions || {}),
          ...Object.keys(c.gatheringPresence || {}),
          m.a,
          m.b,
        ]),
      ].filter(Boolean);

  for (const uid of ids) {
    // Offline / kein Gathering-Heartbeat → nicht aus alter Position reseaten
    if (!weddingCeremony.isPresent(m, uid, "gathering")) {
      if (c.seated[uid] && weddingCeremony.canStand(m, uid) && !c.seatingLocked) {
        delete c.seated[uid];
        changed = true;
      }
      continue;
    }
    if (c.seated[uid] && !weddingCeremony.canStand(m, uid)) continue;
    const pos = c.positions[uid];
    if (!pos || !Number.isFinite(Number(pos.x)) || !Number.isFinite(Number(pos.y))) {
      if (c.seated[uid] && weddingCeremony.canStand(m, uid)) {
        delete c.seated[uid];
        changed = true;
      }
      continue;
    }
    const couple = weddingCeremony.isCouple(m, uid);
    const zone = roomLayouts.findMatchingSeatZone(layout, Number(pos.x), Number(pos.y), couple);
    const next = zone ? zone.id : null;
    const prev = c.seated[uid] || null;
    if (prev === next) continue;
    if (next) {
      c.seated[uid] = next;
      // Phase altar nur wenn Brautpaar auf gelbem Sitz — nicht bei Gast-Blau
      if (
        couple &&
        roomLayouts.isCoupleSeat(zone) &&
        (c.phase === "gathering" || c.phase === "none")
      ) {
        c.phase = "altar";
      }
    } else if (weddingCeremony.canStand(m, uid)) {
      delete c.seated[uid];
    }
    changed = true;
  }
  return changed;
}

function tickCeremonyRitual(m, db) {
  if (!m) return null;
  if (m.status === "ceremony_scheduled" && expireLiveWindowWithoutVows(m)) {
    return { dissolved: true, liveWindowExpired: true };
  }
  const cBefore = weddingCeremony.ensureCeremony(m);
  const phaseBefore = `${cBefore.phase || ""}|${cBefore.pastorPhase || ""}`;
  // Zuerst Ghosts weg, dann Sitze, dann Altar-Hold — sonst Musik mit einer Person
  weddingCeremony.pruneStaleGathering(m);
  syncWeddingZoneSeating(m, db);
  const isCoupleSeat = (seatId) => {
    const z = roomLayouts.findSitZone(db, ceremonyLayoutIdOf(m), seatId);
    return Boolean(z && roomLayouts.isCoupleSeat(z));
  };
  weddingCeremony.syncAltarHold(m, isCoupleSeat);
  const adv = weddingCeremony.advancePastor(m, db.users);
  if (adv?.receptionStarted) {
    keepCeremonyLobbyForGifts(m);
  }
  // Empfang abgelaufen → Gäste raus, Geschenke würfeln, Paar Claim (Lobby bleibt)
  const c = weddingCeremony.ensureCeremony(m);
  // Nach Nein-Rede: kurz phase=ended halten, damit beide Clients das Ende sehen
  const rejectDoneAt = Number(c.rejectFinishedAt) || 0;
  if (
    rejectDoneAt > 0 &&
    (c.pastorPhase === "ended" || c.phase === "ended") &&
    Date.now() - rejectDoneAt >= 8_000
  ) {
    abortWeddingCeremonyRejected(m, c.rejectUserId || null);
    return { dissolved: true };
  }
  const ends = Number(c.receptionEndsAt) || 0;
  if (
    ends > 0 &&
    Date.now() >= ends &&
    !c.receptionEnded &&
    m.status === "married" &&
    (c.pastorPhase === "reception" || c.phase === "reception")
  ) {
    endCeremonyReception(m);
    emitMarriageLiveUpdate(m, m.status || "married", "Empfang beendet");
    return { receptionOver: true, giftsRolled: true };
  }
  // Nur echte Phasenwechsel pushen (nicht bei jedem Sitz-/Move-Tick)
  const phaseAfter = `${c.phase || ""}|${c.pastorPhase || ""}`;
  if (phaseBefore !== phaseAfter) {
    emitMarriageLiveUpdate(
      m,
      m.status || "ceremony_scheduled",
      "Zeremonie aktualisiert"
    );
  }
  return adv || null;
}

function startEngagement(m) {
  const now = Date.now();
  m.status = "engaged";
  m.engagedAt = now;
  m.engageReadyAt = now + marriage.ENGAGE_WAIT_MS;
  const db = getDb();
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (a) marriage.clearDivorceCooldown(a);
  if (b) marriage.clearDivorceCooldown(b);
  emitMarriageLiveUpdate(m, "engaged", "Verlobt — Wartezeit läuft");
}

/** Sofort Verlobung → Hochzeitsbild-Lobby oder Wedding → ceremony_pending. */
function advanceMarriageNextStep(m, { force = false, freeEngageSkip = false } = {}) {
  if (!m) return { ok: false, error: "no_marriage" };
  const db = getDb();
  if (m.status === "engaged") {
    const a = db.users?.[m.a];
    const b = db.users?.[m.b];
    if (!a || !b) return { ok: false, error: "partner_missing" };
    if (freeEngageSkip) {
      if (m.engageFreeSkipUsed === true) {
        return {
          ok: false,
          error: "free_skip_used",
          message: "Gratis-Öffnen wurde bereits genutzt — bitte warten.",
        };
      }
      m.engageFreeSkipUsed = true;
    }
    m.engageReadyAt = Date.now();
    createWeddingLobby(a, b, m);
    trackAch(a, "wedding_started", 1);
    trackAch(b, "wedding_started", 1);
    return { ok: true, marriage: m };
  }
  if (m.status === "wedding") {
    if (!force && !weddingStrokesReadyFor(m)) {
      return {
        ok: false,
        error: "wedding_strokes",
        message: `Beide Partner brauchen je ${marriage.WEDDING_MIN_STROKES} Striche auf der Hochzeitsleinwand.`,
      };
    }
    markCeremonyPending(m, { force: true });
    return { ok: true, marriage: m };
  }
  return { ok: false, error: "wrong_phase", message: "Kein aktiver Warte-Schritt." };
}

/** Restzeit verkürzen (ms). remainingMs=0 → nächster Schritt. */
function shortenMarriageWait(m, remainingMs) {
  if (!m) return { ok: false, error: "no_marriage" };
  const rem = Math.max(0, Math.floor(Number(remainingMs) || 0));
  const now = Date.now();
  if (m.status === "engaged") {
    if (rem <= 0) return advanceMarriageNextStep(m);
    m.engageReadyAt = now + rem;
    return { ok: true, marriage: m };
  }
  if (m.status === "wedding") {
    if (rem <= 0) return advanceMarriageNextStep(m);
    m.weddingEndsAt = now + rem;
    return { ok: true, marriage: m };
  }
  return { ok: false, error: "wrong_phase" };
}

function finishWeddingGiftsAndDissolve(m) {
  weddingGifts.ensureGiftState(m);
  m.giftPhase = "done";
  const code = m.ceremonyLobbyCode;
  if (code) {
    dissolveWeddingCeremonyLobby(code);
    m.ceremonyLobbyCode = null;
  }
  syncCeremonyGiftMeta(m);
}

function claimWeddingGiftsForUser(m, user) {
  weddingGifts.ensureGiftState(m);
  if (m.giftPhase !== "rolled") {
    return { ok: false, error: "not_ready", message: "Geschenke noch nicht verteilt." };
  }
  if (!weddingCeremony.isCouple(m, user.id)) {
    return { ok: false, error: "couple_only" };
  }
  if (m.giftClaimed[user.id]) {
    return { ok: true, already: true, items: [] };
  }
  const items = Array.isArray(m.giftSplit?.[user.id]) ? m.giftSplit[user.id] : [];
  ensureInventory(user);
  for (const g of items) {
    if (!g?.kind || !g?.itemId) continue;
    market.giveItemToUser(user, ensureInventory, g.kind, g.itemId);
  }
  m.giftClaimed[user.id] = true;
  removeCoupleMemberFromCeremonyLobby(m, user.id);
  const both =
    Boolean(m.giftClaimed[m.a]) && Boolean(m.giftClaimed[m.b]);
  const membersLeft = Array.isArray(m.ceremonyMemberIds)
    ? m.ceremonyMemberIds.length
    : 0;
  if (both || membersLeft === 0) finishWeddingGiftsAndDissolve(m);
  return {
    ok: true,
    items: items.map((g) => ({
      kind: g.kind,
      itemId: g.itemId,
      fromNickname: g.fromNickname || "Jemand",
    })),
    bothClaimed: both,
  };
}

function tickWeddingGifts(now = Date.now()) {
  const db = getDb();
  const all = marriage.ensureMarriages(db);
  let dirty = false;
  for (const m of Object.values(all)) {
    if (!m || m.status !== "married") continue;
    weddingGifts.ensureGiftState(m);
    const c = weddingCeremony.ensureCeremony(m);
    const receptionEnds =
      Number(c.receptionEndsAt) || Number(m.giftWindowEndsAt) || 0;
    // Empfang vorbei (auch ohne Ceremony-Poll): Gäste raus + würfeln + Claim
    if (
      !c.receptionEnded &&
      receptionEnds > 0 &&
      now >= receptionEnds &&
      (m.giftPhase === "open" || m.giftPhase === "none" || m.ceremonyLobbyCode)
    ) {
      endCeremonyReception(m);
      dirty = true;
      continue;
    }
    if (
      m.giftPhase === "open" &&
      (Number(m.giftWindowEndsAt) || 0) > 0 &&
      now >= (Number(m.giftWindowEndsAt) || 0)
    ) {
      // Ohne Kapellen-Lobby: nur würfeln
      weddingGifts.rollGiftPool(m);
      syncCeremonyGiftMeta(m);
      dirty = true;
      continue;
    }
    if (m.giftPhase === "rolled") {
      const rolledAt = Number(m.giftWindowEndsAt) || 0;
      if (
        rolledAt > 0 &&
        now >= rolledAt + weddingGifts.GIFT_CLAIM_GRACE_MS
      ) {
        // Auto-Claim für beide, dann Lobby weg
        for (const uid of [m.a, m.b]) {
          const u = db.users?.[uid];
          if (!u || m.giftClaimed[uid]) continue;
          claimWeddingGiftsForUser(m, u);
        }
        if (m.giftPhase !== "done") finishWeddingGiftsAndDissolve(m);
        dirty = true;
      }
    }
  }
  return dirty;
}

function tickMarriages() {
  const db = getDb();
  const all = marriage.ensureMarriages(db);
  const now = Date.now();
  let dirty = false;
  for (const m of Object.values(all)) {
    if (!m) continue;
    if (m.status === "engaged" && now >= (Number(m.engageReadyAt) || 0)) {
      const a = db.users?.[m.a];
      const b = db.users?.[m.b];
      if (a && b) {
        createWeddingLobby(a, b, m);
        trackAch(a, "wedding_started", 1);
        trackAch(b, "wedding_started", 1);
        dirty = true;
      }
    } else if (m.status === "wedding" && now >= (Number(m.weddingEndsAt) || 0)) {
      // Timer + Striche → Zeremonie freischalten (keine Auto-Ehe)
      if (weddingStrokesReadyFor(m) && markCeremonyPending(m)) dirty = true;
    } else if (m.status === "ceremony_scheduled") {
      if (expireLiveWindowWithoutVows(m)) dirty = true;
      else {
        const tick = tickCeremonyRitual(m, db);
        if (tick) dirty = true;
      }
    } else if (m.status === "married") {
      const tick = tickCeremonyRitual(m, db);
      if (tick) dirty = true;
    }
  }
  if (tickWeddingGifts(now)) dirty = true;
  if (dirty) scheduleSave();
}

function endMarriageRecord(m, reason) {
  if (!m) return;
  const db = getDb();
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (m.weddingLobbyCode) {
    dissolveWeddingLobby(m.weddingLobbyCode);
  }
  // Bild entfernen, sonst würde Boot-Recovery die Ehe nach Scheidung neu anlegen
  if (m.weddingImageFile || m.weddingImagePending) {
    try {
      const names = [
        m.weddingImageFile && path.basename(String(m.weddingImageFile)),
        m.weddingImagePending && path.basename(String(m.weddingImagePending)),
        `${m.id || marriage.pairKey(m.a, m.b)}.png`,
      ].filter(Boolean);
      for (const name of names) {
        for (const dir of [
          path.join(DATA_DIR, "weddings"),
          path.join(DATA_DIR, "weddings_pending"),
        ]) {
          const fp = path.join(dir, name);
          if (fs.existsSync(fp)) fs.unlinkSync(fp);
        }
      }
    } catch (e) {
      console.error("wedding image delete failed", e);
    }
    m.weddingImageFile = null;
    m.weddingImagePending = null;
  }
  if (a) {
    marriage.clearMarriageItem(a);
    marriage.stripSpouseFromProfile(a);
  }
  if (b) {
    marriage.clearMarriageItem(b);
    marriage.stripSpouseFromProfile(b);
  }
  const key = m.id || marriage.pairKey(m.a, m.b);
  delete marriage.ensureMarriages(db)[key];
  // Auch unter pairKey löschen falls id anders
  delete marriage.ensureMarriages(db)[marriage.pairKey(m.a, m.b)];
  if (reason === "divorce") {
    if (a) {
      marriage.setDivorceCooldown(a);
      trackAch(a, "divorces", 1);
    }
    if (b) {
      marriage.setDivorceCooldown(b);
      trackAch(b, "divorces", 1);
    }
  }
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

/**
 * Merkt Striche pro Lobby/Tag und erhöht Freundschaftslevel um 1,
 * sobald zwei Freunde an dem Tag beide auf derselben Lobby gemalt haben.
 */
function noteFriendshipCanvasStroke(room, user) {
  if (!room || !user?.id) return;
  const day = todayKey();
  if (room.drawDay !== day) {
    room.drawDay = day;
    room.drawersToday = [];
  }
  if (!Array.isArray(room.drawersToday)) room.drawersToday = [];
  if (!room.drawersToday.includes(user.id)) {
    room.drawersToday.push(user.id);
  }
  const db = getDb();
  for (const otherId of room.drawersToday) {
    if (!otherId || otherId === user.id) continue;
    if (friendRelation(user, otherId) !== "friends") continue;
    const other = db.users?.[otherId];
    if (!other) continue;
    const bump = marriage.bumpLevelFromSharedCanvas(user, other, day);
    if (bump.bumped) {
      trackFriendshipLevelAch(user, bump.level);
      trackFriendshipLevelAch(other, bump.level);
      scheduleSave();
    }
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
    createdByUserId: room.createdByUserId || null,
    name: room.name || "Lobby",
    hostNickname: room.hostNickname || "Host",
    isFree: Boolean(room.isFree),
    isRandom: Boolean(room.isRandom),
    isWedding: Boolean(room.isWedding),
    isWeddingCeremony: Boolean(room.isWeddingCeremony),
    weddingRetake: Boolean(room.weddingRetake),
    capacity: roomCapacity(room),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    colorByUserId:
      room.colorByUserId && typeof room.colorByUserId === "object"
        ? room.colorByUserId
        : {},
    peakPeers: Math.max(1, Number(room.peakPeers) || 1),
    lastCanvasAt: Number(room.lastCanvasAt) || 0,
    lastCanvasActorId: room.lastCanvasActorId || null,
    memberUserIds: Array.isArray(room.memberUserIds)
      ? room.memberUserIds.filter((id) => typeof id === "string")
      : [],
    joinAnnouncedUserIds: Array.isArray(room.joinAnnouncedUserIds)
      ? room.joinAnnouncedUserIds.filter((id) => typeof id === "string")
      : [],
    marriageId: room.marriageId || null,
    ceremonyAt: Number(room.ceremonyAt) || 0,
    giftWindowEndsAt: Number(room.giftWindowEndsAt) || 0,
    giftPhase: room.giftPhase ? String(room.giftPhase).slice(0, 16) : "none",
    coupleNicknames:
      room.coupleNicknames && typeof room.coupleNicknames === "object"
        ? room.coupleNicknames
        : null,
    eventId: room.eventId ? String(room.eventId).slice(0, 64) : null,
    eventPrompt: room.eventPrompt ? String(room.eventPrompt).slice(0, 80) : null,
    eventPromptChoices: Array.isArray(room.eventPromptChoices)
      ? room.eventPromptChoices.map((p) => String(p || "").trim().slice(0, 80)).filter(Boolean).slice(0, 5)
      : null,
    eventEndsAt: room.eventEndsAt ? String(room.eventEndsAt).slice(0, 40) : null,
    invitesAllowed: room.invitesAllowed !== false,
    isCustomRoom: Boolean(room.isCustomRoom),
    customRoomId: room.customRoomId ? String(room.customRoomId).slice(0, 64) : null,
    customRoomImageUrl: room.customRoomImageUrl
      ? String(room.customRoomImageUrl).slice(0, 300)
      : null,
    space:
      room.space && typeof room.space === "object"
        ? {
            positions: room.space.positions || {},
            seated: room.space.seated || {},
            reactions: room.space.reactions || {},
            lastMoveAt: Number(room.space.lastMoveAt) || 0,
            lastMoveBy: room.space.lastMoveBy || null,
          }
        : null,
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
      createdByUserId: data.createdByUserId || data.hostUserId || null,
      name: String(data.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
      hostNickname: String(data.hostNickname || "Host").slice(0, 18),
      isFree: Boolean(data.isFree),
      isRandom: Boolean(data.isRandom),
      isWedding: Boolean(data.isWedding),
      isWeddingCeremony: Boolean(data.isWeddingCeremony),
      weddingRetake: Boolean(data.weddingRetake),
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
      lastCanvasActorId: data.lastCanvasActorId || null,
      memberUserIds: Array.isArray(data.memberUserIds)
        ? data.memberUserIds.filter((id) => typeof id === "string")
        : [],
      joinAnnouncedUserIds: Array.isArray(data.joinAnnouncedUserIds)
        ? data.joinAnnouncedUserIds.filter((id) => typeof id === "string")
        : Array.isArray(data.memberUserIds)
          ? data.memberUserIds.filter((id) => typeof id === "string")
          : [],
      marriageId: data.marriageId || null,
      ceremonyAt: Number(data.ceremonyAt) || 0,
      giftWindowEndsAt: Number(data.giftWindowEndsAt) || 0,
      giftPhase: data.giftPhase ? String(data.giftPhase).slice(0, 16) : "none",
      coupleNicknames:
        data.coupleNicknames && typeof data.coupleNicknames === "object"
          ? data.coupleNicknames
          : null,
      eventId: data.eventId ? String(data.eventId).slice(0, 64) : null,
      eventPrompt: data.eventPrompt ? String(data.eventPrompt).slice(0, 80) : null,
      eventPromptChoices: Array.isArray(data.eventPromptChoices)
        ? data.eventPromptChoices
            .map((p) => String(p || "").trim().slice(0, 80))
            .filter(Boolean)
            .slice(0, 5)
        : null,
      eventEndsAt: data.eventEndsAt ? String(data.eventEndsAt).slice(0, 40) : null,
      invitesAllowed: data.invitesAllowed !== false,
      isCustomRoom: Boolean(data.isCustomRoom),
      customRoomId: data.customRoomId ? String(data.customRoomId).slice(0, 64) : null,
      customRoomImageUrl: data.customRoomImageUrl
        ? String(data.customRoomImageUrl).slice(0, 300)
        : null,
      space:
        data.space && typeof data.space === "object"
          ? {
              positions: data.space.positions || {},
              seated: data.space.seated || {},
              reactions: data.space.reactions || {},
              lastMoveAt: Number(data.space.lastMoveAt) || 0,
              lastMoveBy: data.space.lastMoveBy || null,
            }
          : null,
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
const MAX_STROKES_PER_ROOM = 5000;
const MAX_STICKERS_PER_ROOM = 80;
/** Lange Füllstriche (z. B. Wolke ausmalen) brauchen deutlich mehr als kurze Linien. */
const MAX_POINTS_PER_STROKE = 2000;
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

function clipCanvasEmojiId(raw) {
  const e = String(raw || "").trim();
  if (!e) return "";
  // Bild-Begleiter img_xxxxxxxx brauchen mehr als 8 Zeichen
  if (e.toLowerCase().startsWith("img_")) return e.slice(0, PET_ID_MAX);
  return e.slice(0, 16);
}

function sanitizeStoredSticker(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = String(raw.id || "").trim();
  const emoji = clipCanvasEmojiId(raw.emoji);
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

const TEMPLATE_MAX_PARTS = 600;
const TEMPLATE_MAX_POINTS = 2400;
const TEMPLATE_MIN_POINT_DIST = 0.004; // ~0.4% der Fläche — lange Striche ohne Riesen-Payload

/** Abstand-Downsampling: behält Form, reduziert Punktdichte. */
function downsampleTemplatePoints(points, maxPoints, minDist) {
  if (!Array.isArray(points) || points.length <= 2) return points || [];
  const minD2 = minDist * minDist;
  const kept = [points[0]];
  for (let i = 1; i < points.length - 1; i++) {
    const prev = kept[kept.length - 1];
    const p = points[i];
    const dx = p.x - prev.x;
    const dy = p.y - prev.y;
    if (dx * dx + dy * dy >= minD2) kept.push(p);
  }
  const last = points[points.length - 1];
  const prev = kept[kept.length - 1];
  if (prev.x !== last.x || prev.y !== last.y) kept.push(last);
  if (kept.length <= maxPoints) return kept;
  // Gleichmäßig auf maxPoints ausdünnen (Start+Ende bleiben)
  const out = [kept[0]];
  const step = (kept.length - 1) / (maxPoints - 1);
  for (let i = 1; i < maxPoints - 1; i++) {
    out.push(kept[Math.round(i * step)]);
  }
  out.push(kept[kept.length - 1]);
  return out;
}

function sanitizeTemplateParts(rawParts) {
  if (!Array.isArray(rawParts)) return null;
  const out = [];
  for (const part of rawParts.slice(0, TEMPLATE_MAX_PARTS)) {
    if (!part || typeof part !== "object") continue;
    const pointsIn = Array.isArray(part.points) ? part.points : [];
    const rawPts = [];
    for (const p of pointsIn) {
      if (!p || typeof p !== "object") continue;
      const x = Number(p.x);
      const y = Number(p.y);
      if (!Number.isFinite(x) || !Number.isFinite(y)) continue;
      rawPts.push({
        x: Math.min(1, Math.max(0, x)),
        y: Math.min(1, Math.max(0, y)),
      });
    }
    const points = downsampleTemplatePoints(
      rawPts,
      TEMPLATE_MAX_POINTS,
      TEMPLATE_MIN_POINT_DIST
    );
    if (points.length < 2) continue;
    out.push({
      points,
      width: Math.min(48, Math.max(3, Number(part.width) || 18)),
      colorIndex: Math.max(0, Math.min(35, Number(part.colorIndex) || 0)),
    });
  }
  return out.length ? out : null;
}

function sanitizeStoredStroke(raw) {
  if (!raw || typeof raw !== "object") return null;
  const id = String(raw.id || "").trim();
  if (!id || id.length > 80) return null;
  const emoji = clipCanvasEmojiId(raw.emoji) || null;
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
    colorIndex: Math.max(0, Math.min(35, Number(raw.colorIndex) || 0)),
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
    const space = String(raw.templateCoordSpace || "").trim().toLowerCase();
    out.templateCoordSpace = space === "square" ? "square" : "canvas";
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
    templateCoordSpace: json?.templateCoordSpace,
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

/**
 * Zeichnen / Sticker / Vorlage — setzt lastCanvasAt + optional lastCanvasActorId
 * (für Home-Glow: nur fremde Aktivität in Abwesenheit).
 */
function touchCanvasActivity(room, peerCountHint, actorUserId) {
  if (!room) return;
  const now = Date.now();
  const prev = Number(room.lastCanvasAt) || 0;
  room.lastCanvasAt = now;
  const actor = String(actorUserId || "").trim();
  if (actor) room.lastCanvasActorId = actor;
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
  return user.hostedRooms;
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
        room = hydrateRoom(code, saved);
        rooms.set(code, room);
        healRoomMembership(room);
      }
    }
    if (!room) {
      // Raum wirklich weg (aufgelöst) → Ownership weg
      delete user.hostedRooms[code];
      dirty = true;
      continue;
    }
    // Event-Lobby: Ownership nur für Host/Ersteller; nie Host-Promote an Dritte
    if (room.eventId) {
      const isOwner =
        room.hostUserId === user.id || room.createdByUserId === user.id;
      if (!isOwner) {
        delete user.hostedRooms[code];
        dirty = true;
        continue;
      }
      healRoomMembership(room);
      if (!Array.isArray(room.memberUserIds) || !room.memberUserIds.includes(user.id)) {
        rememberMember(room, user.id);
        dirty = true;
      }
      const meta = user.hostedRooms[code];
      if (meta && typeof meta === "object" && !meta.token && room.token) {
        meta.token = room.token;
        dirty = true;
      }
      continue;
    }
    healRoomMembership(room);
    const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
    // Host/Ersteller zählt immer als Mitglied — nie Ownership nur wegen leerer Liste killen
    if (
      members.includes(user.id) ||
      room.hostUserId === user.id ||
      room.createdByUserId === user.id
    ) {
      if (!members.includes(user.id)) {
        rememberMember(room, user.id);
        dirty = true;
      }
      // Token in hostedRooms nachziehen
      const meta = user.hostedRooms[code];
      if (meta && typeof meta === "object" && !meta.token && room.token) {
        meta.token = room.token;
        dirty = true;
      }
      continue;
    }
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
    createdByUserId: data.createdByUserId || data.hostUserId || null,
    name: String(data.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    hostNickname: String(data.hostNickname || "Host").slice(0, 18),
    isFree: Boolean(data.isFree),
    isRandom: Boolean(data.isRandom),
    isWedding: Boolean(data.isWedding),
    isWeddingCeremony: Boolean(data.isWeddingCeremony),
    weddingRetake: Boolean(data.weddingRetake),
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
    lastCanvasActorId: data.lastCanvasActorId || null,
    memberUserIds: Array.isArray(data.memberUserIds)
      ? data.memberUserIds.filter((id) => typeof id === "string")
      : [],
    joinAnnouncedUserIds: Array.isArray(data.joinAnnouncedUserIds)
      ? data.joinAnnouncedUserIds.filter((id) => typeof id === "string")
      : Array.isArray(data.memberUserIds)
        ? data.memberUserIds.filter((id) => typeof id === "string")
        : [],
    marriageId: data.marriageId || null,
    ceremonyAt: Number(data.ceremonyAt) || 0,
    giftWindowEndsAt: Number(data.giftWindowEndsAt) || 0,
    giftPhase: data.giftPhase ? String(data.giftPhase).slice(0, 16) : "none",
    coupleNicknames:
      data.coupleNicknames && typeof data.coupleNicknames === "object"
        ? data.coupleNicknames
        : null,
    eventId: data.eventId ? String(data.eventId).slice(0, 64) : null,
    eventPrompt: data.eventPrompt ? String(data.eventPrompt).slice(0, 80) : null,
    eventPromptChoices: Array.isArray(data.eventPromptChoices)
      ? data.eventPromptChoices
          .map((p) => String(p || "").trim().slice(0, 80))
          .filter(Boolean)
          .slice(0, 5)
      : null,
    eventEndsAt: data.eventEndsAt ? String(data.eventEndsAt).slice(0, 40) : null,
    invitesAllowed: data.invitesAllowed !== false,
    isCustomRoom: Boolean(data.isCustomRoom),
    customRoomId: data.customRoomId ? String(data.customRoomId).slice(0, 64) : null,
    customRoomImageUrl: data.customRoomImageUrl
      ? String(data.customRoomImageUrl).slice(0, 300)
      : null,
    space:
      data.space && typeof data.space === "object"
        ? {
            positions: data.space.positions || {},
            seated: data.space.seated || {},
            reactions: data.space.reactions || {},
            lastMoveAt: Number(data.space.lastMoveAt) || 0,
            lastMoveBy: data.space.lastMoveBy || null,
          }
        : null,
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
  // Event-Lobbys sind Solo — nie in joinedRooms anderer Nutzer
  if (room.eventId) {
    const j = ensureJoinedRooms(user);
    delete j[code];
    return;
  }
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
    isWedding: Boolean(room.isWedding),
    isWeddingCeremony: Boolean(room.isWeddingCeremony),
    ceremonyAt: Number(room.ceremonyAt) || 0,
    coupleNicknames: room.coupleNicknames || null,
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
  const consider = (code, roomLike) => {
    if (!roomLike || roomLike.eventId) return; // Event = Solo, nie in joined
    const ids = Array.isArray(roomLike.memberUserIds) ? roomLike.memberUserIds : [];
    if (ids.includes(user.id) && roomLike.hostUserId !== user.id) {
      rememberJoinedLobby(user, code, roomLike);
      seen.add(code);
    }
  };
  for (const [code, room] of rooms.entries()) {
    consider(code, room);
  }
  for (const [code, data] of Object.entries(db.rooms || {})) {
    if (seen.has(code)) continue;
    consider(code, data);
  }
  // Verwaiste Einträge entfernen (Lobby weg / nicht mehr Mitglied / Event-Geister)
  for (const code of Object.keys(user.joinedRooms)) {
    if (seen.has(code)) continue;
    const live = rooms.get(code);
    const stored = db.rooms?.[code];
    if ((live && live.eventId) || (stored && stored.eventId)) {
      delete user.joinedRooms[code];
      continue;
    }
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

function ensureRoomCreator(room) {
  if (!room) return null;
  if (room.createdByUserId) return room.createdByUserId;
  const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
  // Beim Erstellen ist der Ersteller immer erstes Mitglied
  const guess = members.find((id) => id && typeof id === "string") || room.hostUserId || null;
  if (guess) room.createdByUserId = guess;
  return room.createdByUserId || null;
}

function isRoomCreator(userId, room) {
  if (!userId || !room) return false;
  const creator = ensureRoomCreator(room);
  return Boolean(creator && creator === userId);
}

function publicJoinedLobbies(user) {
  healJoinedRoomsFromMembership(user);
  return Object.entries(user.joinedRooms || {})
    .filter(([code]) => {
      const room = rooms.get(code) || getDb().rooms?.[code] || null;
      return !room?.eventId; // Event nie als Join listen
    })
    .map(([code, meta]) => {
    const room = rooms.get(code) || getDb().rooms?.[code] || null;
    return {
    code,
    name: String(meta?.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    token: meta?.token || room?.token || null,
    capacity: Math.min(
      MAX_PEERS,
      Math.max(
        1,
        Number(meta?.capacity) || defaultLobbyCapacity(Boolean(meta?.isFree))
      )
    ),
    isFree: Boolean(meta?.isFree),
    isRandom: Boolean(meta?.isRandom),
    isWedding: Boolean(meta?.isWedding),
    isWeddingCeremony: Boolean(
      meta?.isWeddingCeremony || room?.isWeddingCeremony
    ),
    isCustomRoom: Boolean(meta?.isCustomRoom || room?.isCustomRoom),
    customRoomId: meta?.customRoomId || room?.customRoomId || null,
    customRoomImageUrl:
      meta?.customRoomImageUrl || room?.customRoomImageUrl || null,
    weddingRetake: Boolean(
      meta?.weddingRetake || rooms.get(code)?.weddingRetake
    ),
    ceremonyAt: Number(meta?.ceremonyAt || room?.ceremonyAt || 0),
    coupleNicknames: meta?.coupleNicknames || room?.coupleNicknames || null,
    giftWindowEndsAt: Number(
      meta?.giftWindowEndsAt || room?.giftWindowEndsAt || 0
    ),
    giftPhase: String(meta?.giftPhase || room?.giftPhase || "none"),
    lastCanvasAt: Number(rooms.get(code)?.lastCanvasAt || meta?.lastCanvasAt || 0),
    lastCanvasActorId:
      rooms.get(code)?.lastCanvasActorId || meta?.lastCanvasActorId || null,
    hostColorSide: normalizeHostColorSide(meta?.hostColorSide),
    invite: `${PUBLIC_JOIN_BASE}/${code}`,
    hostNickname: String(meta?.hostNickname || "Host").slice(0, 18),
    role: "join",
    createdByMe: isRoomCreator(user.id, room),
    eventId: null,
    eventPrompt: null,
    eventEndsAt: null,
  };
  });
}

function rememberMember(room, userId) {
  if (!room || !userId) return false;
  // Event-Lobby: nur Host/Ersteller als Mitglied
  if (room.eventId) {
    const allowed =
      room.hostUserId === userId || room.createdByUserId === userId;
    if (!allowed) return false;
  }
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
    // Token muss passen — leerer Token erlaubt keinen Fremd-Join mehr
    if (token && room.token === token) return room;

    // Bekanntes Mitglied mit veraltetem Token — trotzdem rein (nie Split-Lobby).
    if (user && Array.isArray(room.memberUserIds) && room.memberUserIds.includes(user.id)) {
      return room;
    }

    // Host: immer rein. Token nur übernehmen, wenn niemand sonst verbunden ist
    if (user && room.hostUserId === user.id) {
      if ((room.sockets?.size || 0) === 0 && token) {
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
          isRandom: Boolean(meta.isRandom),
          isWedding: Boolean(meta.isWedding),
          weddingRetake: Boolean(meta.weddingRetake),
          capacity: meta.capacity,
          hostColorSide: meta.hostColorSide,
          colorByUserId: meta.colorByUserId || { [user.id]: hostColorIndex(meta.hostColorSide) },
          memberUserIds: Array.isArray(meta.memberUserIds) ? meta.memberUserIds : [user.id],
          marriageId: meta.marriageId || null,
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
  // Cap nur über Create-Default + bezahlte Slots — nie über Members/Peak aufblasen
  // (sonst lässt sich Kapazität ohne SLOT_COST umgehen).
  return Math.min(MAX_PEERS, Math.max(fallback, Math.floor(n)));
}

function roomMemberCount(room) {
  if (!room) return 0;
  const ids = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
  const set = new Set(ids.filter(Boolean));
  if (room.hostUserId) set.add(room.hostUserId);
  return set.size;
}

/**
 * Nur Kapazität heilen.
 * Früher: colorByUserId → memberUserIds — das hat Leave sofort rückgängig gemacht
 * (Slot blieb belegt, Leinwand zeigte „kurz offline“ statt ausgegraut).
 */
/** Anzeigename gültig? Unbekannt/„Jemand“/leer → nicht in Kachel/Leinwand. */
function isKnownDisplayNickname(nick) {
  const n = String(nick || "").trim();
  if (n.length < 2) return false;
  if (/^jemand$/i.test(n)) return false;
  return true;
}

/**
 * Gelöschte/zusammengeführte Konten aus memberUserIds entfernen
 * (sonst erscheinen überall „Jemand“-Geister).
 */
function pruneUnknownMembers(room) {
  if (!room) return false;
  const db = getDb();
  let changed = false;
  if (Array.isArray(room.memberUserIds)) {
    const next = [];
    const seen = new Set();
    for (const id of room.memberUserIds) {
      if (!id || typeof id !== "string" || seen.has(id)) continue;
      const u = db.users?.[id];
      if (!u) {
        if (room.colorByUserId?.[id] != null) delete room.colorByUserId[id];
        changed = true;
        continue;
      }
      if (!isKnownDisplayNickname(u.nickname)) {
        changed = true;
        continue;
      }
      seen.add(id);
      next.push(id);
    }
    if (next.length !== room.memberUserIds.length) {
      room.memberUserIds = next;
      changed = true;
    }
  }
  if (room.colorByUserId && typeof room.colorByUserId === "object") {
    for (const id of Object.keys(room.colorByUserId)) {
      if (!db.users?.[id]) {
        delete room.colorByUserId[id];
        changed = true;
      }
    }
  }
  return changed;
}

function healRoomMembership(room) {
  if (!room) return false;
  const pruned = pruneUnknownMembers(room);
  const cap = healRoomCapacity(room);
  let fixed = false;
  if (!Array.isArray(room.memberUserIds)) room.memberUserIds = [];
  // Event-Lobby: strikt Solo — nur Host/Ersteller, Cap 1
  if (room.eventId) {
    const owner = room.hostUserId || room.createdByUserId || null;
    const next = owner ? [owner] : [];
    if (
      room.memberUserIds.length !== next.length ||
      room.memberUserIds[0] !== next[0]
    ) {
      room.memberUserIds = next;
      fixed = true;
    }
    if ((Number(room.capacity) || 0) !== 1) {
      room.capacity = 1;
      fixed = true;
    }
    room.invitesAllowed = false;
    return pruned || cap || fixed;
  }
  const hostId = room.hostUserId || room.createdByUserId;
  if (hostId && typeof hostId === "string" && !room.memberUserIds.includes(hostId)) {
    room.memberUserIds.unshift(hostId);
    fixed = true;
  }
  return pruned || cap || fixed;
}

/**
 * Verbundene + sonst bekannte Mitglieder — fuer Kachel/Avatare.
 * Reihenfolge = Beitrittsreihenfolge (memberUserIds).
 * Unbekannte Profile (gelöschte User / „Jemand“) werden nicht angezeigt.
 */
function roomRosterAll(room) {
  pruneUnknownMembers(room);
  const connectedById = new Map();
  for (const m of roomConnectedMembers(room)) {
    if (m?.userId && isKnownDisplayNickname(m.nickname)) {
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
    if (!u) continue;
    const nick = String(u.nickname || "").trim().slice(0, 18);
    if (!isKnownDisplayNickname(nick)) continue;
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
      petEmoji: userPetEmoji(u),
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
  const hasSnap = inviteTrial.hasInviteSnapshot(SNAPSHOT_DIR, code);
  const isCeremony = Boolean(room.isWeddingCeremony);
  const hasDrawing =
    isCeremony ||
    hasSnap ||
    (Array.isArray(room.strokes) && room.strokes.length > 0) ||
    Number(room.lastCanvasAt) > 0;
  const inviteImageUrl = isCeremony
    ? weddingInviteCard.publicWeddingCardUrl(code, Number(room.ceremonyAt) || 0)
    : hasSnap
      ? `/v1/rooms/${code}/invite-image`
      : null;
  return {
    code,
    name: room.name || "Lobby",
    hostNickname: room.hostNickname || "Host",
    peers: online,
    capacity: roomCapacity(room),
    maxPeers: MAX_PEERS,
    isFree: Boolean(room.isFree),
    isRandom: Boolean(room.isRandom),
    isWedding: Boolean(room.isWedding),
    isWeddingCeremony: isCeremony,
    weddingRetake: Boolean(room.weddingRetake),
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    peakPeers: peak,
    coupleMode: peak <= 2,
    lastCanvasAt: Number(room.lastCanvasAt) || 0,
    lastCanvasActorId: room.lastCanvasActorId || null,
    members: roster.map((m) => m.nickname),
    memberList: roster,
    eventId: room.eventId || null,
    eventPrompt: room.eventPrompt || null,
    eventPromptChoices: Array.isArray(room.eventPromptChoices)
      ? room.eventPromptChoices
      : null,
    eventEndsAt: room.eventEndsAt || null,
    invitesAllowed: room.invitesAllowed !== false,
    hasDrawing: Boolean(hasDrawing),
    inviteImageUrl,
    inviteBlurb: invitePublicBlurb(room, room.hostNickname || "Host"),
    ceremonyAt: Number(room.ceremonyAt) || 0,
    coupleNicknames:
      room.coupleNicknames && typeof room.coupleNicknames === "object"
        ? {
            a: String(room.coupleNicknames.a || "").trim().slice(0, 18) || null,
            b: String(room.coupleNicknames.b || "").trim().slice(0, 18) || null,
          }
        : null,
    ...inviteFor(code),
  };
}

const COLOR_BLUE = 0;
const COLOR_PURPLE = 1;
const COLOR_COUNT = 36;

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
  if (user.isTrial) return false;
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
  "play_purchase",
  "play_purchase_replay",
  "voucher",
  "clear_refund",
  "signup_grant",
  "admin_grant",
  "public_share_reward",
  "glass_tip_recv",
  "pet_kraul_recv",
  "pet_kraul_give",
  "market_sell",
  "friendship_level",
  "event_collect",
  "event_quest",
  "event_contest_prize",
  "event_vote",
  "maintenance_reward",
  "bug_report_reward",
]);

/** Soft-Earn (nicht als IAP-Äquivalent / Tip-fähig stapeln). */
const SOFT_CREDIT_REASONS = new Set([
  "achievement",
  "daily_tasks",
  "lootbox_duplicate",
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

function ceremonyCoupleLine(room) {
  const cn = room?.coupleNicknames;
  const a = String(cn?.a || "").trim().slice(0, 18);
  const b = String(cn?.b || "").trim().slice(0, 18);
  if (a && b) return `${a} & ${b}`;
  const host = String(room?.hostNickname || "").trim().slice(0, 18);
  return host || "Ein Paar";
}

/** Nach Restart oft verloren — Flags aus Marriage / HostedRooms zurückholen. */
function healCeremonyRoomFlags(room, code) {
  if (!room || !code) return false;
  const clean = String(code || "")
    .toUpperCase()
    .replace(/^LUV-/, "")
    .replace(/[^A-Z0-9]/g, "");
  if (!clean) return false;
  const db = getDb();
  let dirty = false;

  let m = null;
  const mid = room.marriageId;
  if (mid) m = marriage.ensureMarriages(db)[mid] || null;
  if (!m) {
    for (const rec of Object.values(marriage.ensureMarriages(db))) {
      if (String(rec?.ceremonyLobbyCode || "").toUpperCase() === clean) {
        m = rec;
        break;
      }
    }
  }

  if (m && String(m.ceremonyLobbyCode || "").toUpperCase() === clean) {
    if (!room.isWeddingCeremony) {
      room.isWeddingCeremony = true;
      dirty = true;
    }
    room.isWedding = false;
    const at = Number(m.ceremonyAt) || 0;
    if (at > 0 && Number(room.ceremonyAt) !== at) {
      room.ceremonyAt = at;
      dirty = true;
    }
    const wantId = m.id || marriage.pairKey(m.a, m.b);
    if (!room.marriageId || room.marriageId !== wantId) {
      room.marriageId = wantId;
      dirty = true;
    }
    if (!room.coupleNicknames || typeof room.coupleNicknames !== "object") {
      const ua = db.users?.[m.a];
      const ub = db.users?.[m.b];
      room.coupleNicknames = {
        a: String(ua?.nickname || "").trim().slice(0, 18) || "Partner",
        b: String(ub?.nickname || "").trim().slice(0, 18) || "Partner",
      };
      dirty = true;
    }
  }

  if (!room.isWeddingCeremony) {
    const memberIds = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
    for (const uid of memberIds) {
      const meta = db.users?.[uid]?.hostedRooms?.[clean];
      if (!meta?.isWeddingCeremony) continue;
      room.isWeddingCeremony = true;
      room.isWedding = false;
      if (Number(meta.ceremonyAt) > 0) room.ceremonyAt = Number(meta.ceremonyAt);
      if (meta.coupleNicknames && typeof meta.coupleNicknames === "object") {
        room.coupleNicknames = meta.coupleNicknames;
      }
      if (meta.marriageId) room.marriageId = meta.marriageId;
      dirty = true;
      break;
    }
  }

  if (dirty) {
    const saved = db.rooms?.[clean];
    if (saved && typeof saved === "object") {
      saved.isWeddingCeremony = Boolean(room.isWeddingCeremony);
      saved.isWedding = Boolean(room.isWedding);
      saved.ceremonyAt = Number(room.ceremonyAt) || 0;
      saved.coupleNicknames = room.coupleNicknames || null;
      saved.marriageId = room.marriageId || null;
    }
    scheduleSave();
  }
  return dirty;
}

function invitePublicBlurb(room, hostNickname) {
  if (room?.isWeddingCeremony) {
    return `${ceremonyCoupleLine(room)} laden dich zur Hochzeit ein`;
  }
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
    // Legacy: Malen ist frei — Felder bleiben für alte Clients
    freeSessionsLeft: FREE_SESSIONS_PER_DAY,
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
    isTrial: Boolean(user.isTrial),
    trialRoomCode: user.isTrial ? user.trialRoomCode || null : null,
    trialDrawUntil: user.isTrial ? Number(user.trialDrawUntil) || 0 : null,
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

let lootboxPoolCache = { at: 0, pool: null };
const LOOTBOX_POOL_CACHE_MS = 60_000;

function buildLootboxPoolForUser(user) {
  ensureInventory(user);
  const now = Date.now();
  if (
    Array.isArray(lootboxPoolCache.pool) &&
    lootboxPoolCache.pool.length > 0 &&
    now - lootboxPoolCache.at < LOOTBOX_POOL_CACHE_MS
  ) {
    return lootboxPoolCache.pool;
  }
  // Volle Pool — Duplikate sind erlaubt (Emojis/Sticker stapeln;
  // Themes/Begleiter bei Besitz → Coin-Ausgleich beim Öffnen).
  // Nachträglich angelegte Katalog-Items landen automatisch mit Preis-Rarity drin.
  const pool = lootbox.buildPool({
    emojiPrices: EMOJI_SHOP_PRICES,
    themePrices: THEME_SHOP_PRICES,
    petPrices: PET_SHOP_PRICES,
    stickerPrices: STICKER_SHOP_PRICES,
    isKnown: (kind, id) =>
      isKnownInventoryItem(kind, id) &&
      !seasonEvents.isEventOnlyItem(kind, id) &&
      !seasonEvents.isEventShopBoundItem(getDb(), kind, id),
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
    extraItems: catalogLootboxExtras(),
  });
  lootboxPoolCache = { at: now, pool };
  return pool;
}

function invalidateLootboxPoolCache() {
  lootboxPoolCache = { at: 0, pool: null };
}

/** Lootbox-Belohnung gutschreiben. Duplikat-Theme/Pet → Coins statt leerem Treffer. */
function grantLootboxReward(user, entry) {
  const kind = String(entry.kind || "");
  const itemId = String(entry.itemId || "");
  const shopPrice = Math.max(1, Number(entry.shopPrice) || LOOTBOX_PRICE);
  const unique = kind === "themes";
  if (unique && market.userOwnsItem(user, ensureInventory, kind, itemId)) {
    const refund = Math.max(1, Math.min(shopPrice, Math.floor(shopPrice * 0.5)));
    applyLedger(user.id, refund, "lootbox_duplicate", `${kind}:${itemId}`);
    return { duplicate: true, coinsRefund: refund };
  }
  safeGiveItem(user, kind, itemId);
  bumpShopPurchase(kind, itemId);
  return { duplicate: false, coinsRefund: 0 };
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
    } else if (SOFT_CREDIT_REASONS.has(reason)) {
      user.dailyBalance += amount;
    } else {
      // Unbekannte Gutschriften: soft (nicht als paid stacken)
      user.dailyBalance += amount;
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

// Nach Store-Load: fehlende Play-Käufe aus durable JSONL nachziehen (Restore-Schutz).
try {
  const iapReplay = iapLedger.replayMissingCredits(getDb(), (userId, coins, paymentId) => {
    applyLedger(userId, coins, "play_purchase_replay", paymentId);
  });
  if (iapReplay.replayed > 0) {
    console.warn("[iap_ledger] boot replay", iapReplay);
    scheduleSave();
  }
} catch (e) {
  console.error("[iap_ledger] boot replay failed", e?.message || e);
}

/** Erfolgs-/Daily-Fortschritt — Coins über applyLedger, Cap 25/Tag. */
function trackAch(user, metric, amount = 1) {
  if (!user || !metric) return null;
  try {
    const result = ach.bumpMetric(user, metric, amount, todayKey(), (uid, delta, reason, ref) => {
      applyLedger(uid, delta, reason, ref);
    });
    notifyAchievementUnlocks(user, result);
    return result;
  } catch (err) {
    console.warn("trackAch", err?.message || err);
    return null;
  }
}

function notifyAchievementUnlocks(user, result) {
  if (!user?.id || !result) return;
  const unlocked = Array.isArray(result.unlocked) ? result.unlocked : [];
  for (const u of unlocked) {
    const title = String(u?.title || u?.id || "Erfolg").trim();
    accountPush.emitUserEvent(getDb(), user.id, "achievement_unlocked", {
      achievementId: u?.id || "",
      title,
      message: `${title} — Belohnung abholen`,
      claimable: true,
    });
  }
  if (result.dailyJustCompleted) {
    accountPush.emitUserEvent(getDb(), user.id, "achievement_claimable", {
      message: "Tagesaufgabe fertig — Belohnung abholen",
      daily: true,
    });
  }
}

/** Beide Partner: Sozial-Kacheln / Freunde-Liste sofort per Account-WS. */
function emitMarriageLiveUpdate(m, status, message, actorId = null) {
  if (!m) return;
  const db = getDb();
  const st = String(status || m.status || "").trim();
  const actor = actorId ? db.users?.[actorId] : null;
  const nick = String(actor?.nickname || "Hochzeit").trim().slice(0, 18) || "Hochzeit";
  const data = {
    status: st,
    message: String(message || "").trim().slice(0, 160),
    nickname: nick,
    fromUserId: actorId || "",
    weddingLobbyCode: m.weddingLobbyCode || "",
    ceremonyLobbyCode: m.ceremonyLobbyCode || "",
    ceremonyAt: Number(m.ceremonyAt) || 0,
  };
  for (const uid of [m.a, m.b]) {
    if (!uid) continue;
    accountPush.emitUserEvent(db, uid, "marriage_update", data);
  }
}

function syncAchInventoryMetrics(user) {
  if (!user) return;
  const ledger = (uid, d, r, ref) => applyLedger(uid, d, r, ref);
  const day = todayKey();
  const inv = ensureInventory(user);
  ach.setMetricAtLeast(
    user,
    "pets_owned",
    Object.keys(normalizePetsMap(inv)).filter((k) => petCount(inv, k) > 0).length,
    day,
    ledger
  );
  ach.setMetricAtLeast(user, "friends", (ensureFriends(user).list || []).length, day, ledger);
  if (petOwned(inv, "🦉")) ach.setMetricAtLeast(user, "pet_owl", 1, day, ledger);
  if (petOwned(inv, "🐯")) ach.setMetricAtLeast(user, "pet_tiger", 1, day, ledger);
  if (user.googleSub) ach.setMetricAtLeast(user, "google_linked", 1, day, ledger);
  if (user.nickname && String(user.nickname).toLowerCase() !== "luv") {
    ach.setMetricAtLeast(user, "nickname_set", 1, day, ledger);
  }
}

function createSession(userId, ttlMs = SESSION_TTL_MS, opts = {}) {
  const db = getDb();
  const token = randomToken();
  const ttl = Math.max(60_000, Number(ttlMs) || SESSION_TTL_MS);
  let kind = "app";
  if (opts.kind === "staff") kind = "staff";
  else if (opts.kind === "decoy") kind = "decoy";
  db.sessions[token] = {
    userId: kind === "decoy" ? String(userId || "decoy") : userId,
    kind,
    decoy: kind === "decoy",
    googleSub: opts.googleSub || null,
    createdAt: Date.now(),
    expiresAt: Date.now() + ttl,
  };
  scheduleSave();
  return token;
}

function staffAudit(actor, action, detail) {
  const db = getDb();
  if (!Array.isArray(db.staffAudit)) db.staffAudit = [];
  db.staffAudit.push({
    id: newId("aud"),
    at: Date.now(),
    actorId: actor?.id || null,
    actorNick: String(actor?.nickname || "").slice(0, 18) || null,
    actorRole: actor?.role || null,
    action: String(action || "").slice(0, 64),
    detail: detail && typeof detail === "object" ? detail : {},
  });
  if (db.staffAudit.length > 2500) {
    db.staffAudit = db.staffAudit.slice(-2000);
  }
  scheduleSave();
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
  // Default-Layout (Avatar+Name ≈ 2) nicht über ein bestehendes Profil stülpen
  {
    const tRich = profileRichness(target.profileCanvas);
    const sRich = profileRichness(source.profileCanvas);
    if (!target.profileCanvas && source.profileCanvas) {
      target.profileCanvas = source.profileCanvas;
    } else if (sRich > tRich && tRich <= 2 && sRich > 2) {
      target.profileCanvas = source.profileCanvas;
    } else if (sRich > tRich + 1) {
      target.profileCanvas = source.profileCanvas;
    }
  }
  mergeInventory(target, source);
  mergeFriends(target, source);
  remapFriendRefs(db, source.id, target.id);
  mergeSettings(target, source);
  ach.mergeAchievements(target, source);
  // Tagesbonus / Sessions: nicht durchs Zusammenführen neu claimbar machen
  const day = todayKey();
  if (source.lastDailyGrantDate === day || target.lastDailyGrantDate === day) {
    target.lastDailyGrantDate = day;
  } else {
    const dates = [target.lastDailyGrantDate, source.lastDailyGrantDate].filter(Boolean);
    if (dates.length) target.lastDailyGrantDate = dates.sort().pop();
  }
  if (!target.sessionsByDay || typeof target.sessionsByDay !== "object") {
    target.sessionsByDay = {};
  }
  for (const [d, n] of Object.entries(source.sessionsByDay || {})) {
    target.sessionsByDay[d] = Math.max(
      Number(target.sessionsByDay[d]) || 0,
      Number(n) || 0
    );
  }
  if (!target.drawLocks || typeof target.drawLocks !== "object") target.drawLocks = {};
  for (const [k, v] of Object.entries(source.drawLocks || {})) {
    if (!target.drawLocks[k]) target.drawLocks[k] = v;
  }
  ensurePendingLootboxes(target);
  ensurePendingLootboxes(source);
  for (const p of source.pendingLootboxes || []) {
    if (p?.id && !target.pendingLootboxes.some((x) => x.id === p.id)) {
      target.pendingLootboxes.push(p);
    }
  }
  source.pendingLootboxes = [];
  ensureJoinedRooms(target);
  ensureJoinedRooms(source);
  for (const [code, meta] of Object.entries(source.joinedRooms || {})) {
    if (!target.joinedRooms[code]) target.joinedRooms[code] = { ...meta };
  }
  source.joinedRooms = {};

  // Mitgliedschaften in allen Lobbys umhängen — sonst bleiben Geister-IDs → „Jemand“
  const remapRoomUser = (room) => {
    if (!room || typeof room !== "object") return;
    if (Array.isArray(room.memberUserIds)) {
      const next = [];
      const seen = new Set();
      for (const id of room.memberUserIds) {
        const mapped = id === source.id ? target.id : id;
        if (!mapped || seen.has(mapped)) continue;
        seen.add(mapped);
        next.push(mapped);
      }
      room.memberUserIds = next;
    }
    if (Array.isArray(room.joinAnnouncedUserIds)) {
      room.joinAnnouncedUserIds = [
        ...new Set(
          room.joinAnnouncedUserIds.map((id) => (id === source.id ? target.id : id))
        ),
      ];
    }
    if (room.colorByUserId && typeof room.colorByUserId === "object") {
      if (room.colorByUserId[source.id] != null) {
        if (room.colorByUserId[target.id] == null) {
          room.colorByUserId[target.id] = room.colorByUserId[source.id];
        }
        delete room.colorByUserId[source.id];
      }
    }
    if (room.hostUserId === source.id) room.hostUserId = target.id;
  };
  for (const room of rooms.values()) remapRoomUser(room);
  for (const room of Object.values(db.rooms || {})) remapRoomUser(room);

  // Ehe / Verlobung / Hochzeit an Ziel-Konto hängen
  marriage.remapUserIdInMarriages(db, source.id, target.id);

  // Marktplatz-Angebote + Verkäufe
  market.ensureMarket(db);
  for (const entry of Object.values(db.marketListings || {})) {
    if (!entry) continue;
    if (entry.sellerId === source.id) entry.sellerId = target.id;
    if (entry.buyerId === source.id) entry.buyerId = target.id;
    if (entry.targetUserId === source.id) entry.targetUserId = target.id;
  }
  if (!Array.isArray(target.pendingMarketSales)) target.pendingMarketSales = [];
  if (Array.isArray(source.pendingMarketSales)) {
    for (const sale of source.pendingMarketSales) {
      if (sale?.id && !target.pendingMarketSales.some((x) => x.id === sale.id)) {
        target.pendingMarketSales.push(sale);
      }
    }
  }
  source.pendingMarketSales = [];
  if (target.pendingMarketSales.length > 80) {
    target.pendingMarketSales = target.pendingMarketSales.slice(-80);
  }

  // Scheidungs-Cooldown: nur echte Scheidung übernehmen, nie bei aktiver Ehe
  const busyAfter = marriage.findMarriageForUser(db, target.id);
  if (busyAfter && marriage.isBusyStatus(busyAfter.status)) {
    marriage.clearDivorceCooldown(target);
  } else {
    const targetDivorced = Boolean(target.marriageDivorcedAt);
    const sourceDivorced = Boolean(source.marriageDivorcedAt);
    if (targetDivorced || sourceDivorced) {
      const aUntil = targetDivorced ? Number(target.marriageCooldownUntil) || 0 : 0;
      const bUntil = sourceDivorced ? Number(source.marriageCooldownUntil) || 0 : 0;
      target.marriageCooldownUntil = Math.max(aUntil, bUntil);
      target.marriageDivorcedAt = Math.max(
        Number(target.marriageDivorcedAt) || 0,
        Number(source.marriageDivorcedAt) || 0
      );
    } else {
      marriage.clearDivorceCooldown(target);
    }
  }
  marriage.clearDivorceCooldown(source);

  // Verwaiste Wedding-Lobbys / Hochzeitsbilder nochmal reparieren
  marriage.repairMarriageLinks(db, target, WEDDING_DIR);

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
  // Nur offene Anträge verwerfen — Ehe/Verlobung/Hochzeit + Bild/Gästebuch bleiben
  // für den Partner erhalten (Konto-Löschung ≠ Scheidung).
  const m = marriage.findMarriageForUser(db, userId);
  if (m && m.status === "proposed") {
    endMarriageRecord(m, "cancel");
  }
  // Offene Markt-Angebote zurückziehen (Items würden sonst hängen)
  market.ensureMarket(db);
  for (const entry of Object.values(db.marketListings || {})) {
    if (!entry || entry.sellerId !== userId || entry.status !== "open") continue;
    entry.status = "cancelled";
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
    // Unverifizierte Google-Adressen dürfen keinen Login erzeugen
    if (String(payload.email_verified) !== "true" && payload.email_verified !== true) {
      return null;
    }
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
  return Object.entries(user.hostedRooms).map(([code, meta]) => {
    const room = rooms.get(code) || getDb().rooms?.[code] || null;
    return {
    code,
    name: String(meta?.name || "Lobby").slice(0, MAX_LOBBY_NAME_LENGTH),
    token: meta?.token || room?.token || null,
    capacity: Math.min(
      MAX_PEERS,
      Math.max(
        1,
        Number(meta?.capacity) || defaultLobbyCapacity(Boolean(meta?.isFree))
      )
    ),
    isFree: Boolean(meta?.isFree),
    isRandom: Boolean(meta?.isRandom),
    isWedding: Boolean(meta?.isWedding),
    isWeddingCeremony: Boolean(
      meta?.isWeddingCeremony || room?.isWeddingCeremony
    ),
    isCustomRoom: Boolean(meta?.isCustomRoom || room?.isCustomRoom),
    customRoomId: meta?.customRoomId || room?.customRoomId || null,
    customRoomImageUrl:
      meta?.customRoomImageUrl || room?.customRoomImageUrl || null,
    weddingRetake: Boolean(
      meta?.weddingRetake || rooms.get(code)?.weddingRetake || getDb().rooms?.[code]?.weddingRetake
    ),
    ceremonyAt: Number(meta?.ceremonyAt || room?.ceremonyAt || 0),
    coupleNicknames:
      meta?.coupleNicknames || room?.coupleNicknames || null,
    giftWindowEndsAt: Number(
      meta?.giftWindowEndsAt || room?.giftWindowEndsAt || 0
    ),
    giftPhase: String(meta?.giftPhase || room?.giftPhase || "none"),
    lastCanvasAt: Number(
      rooms.get(code)?.lastCanvasAt ||
        meta?.lastCanvasAt ||
        getDb().rooms?.[code]?.lastCanvasAt ||
        0
    ),
    lastCanvasActorId:
      rooms.get(code)?.lastCanvasActorId ||
      meta?.lastCanvasActorId ||
      getDb().rooms?.[code]?.lastCanvasActorId ||
      null,
    hostColorSide: normalizeHostColorSide(meta?.hostColorSide),
    invite: `${PUBLIC_JOIN_BASE}/${code}`,
    hostNickname: user.nickname || "Host",
    createdByMe: room ? isRoomCreator(user.id, room) : true,
    eventId: meta?.eventId || room?.eventId || null,
    eventPrompt: meta?.eventPrompt || room?.eventPrompt || null,
    eventPromptChoices:
      (Array.isArray(meta?.eventPromptChoices) && meta.eventPromptChoices) ||
      (Array.isArray(room?.eventPromptChoices) && room.eventPromptChoices) ||
      null,
    eventEndsAt: meta?.eventEndsAt || room?.eventEndsAt || null,
  };
  });
}

/** userId → lastSeen (App im Vordergrund / API-Aktivität) */
const ACTIVE_APP_TTL_MS = 120_000;
const activeAppUsers = new Map();

function touchActiveAppUser(userId) {
  const id = String(userId || "").trim();
  if (!id) return;
  activeAppUsers.set(id, Date.now());
}

function countActiveAppUsers() {
  const now = Date.now();
  for (const [id, ts] of activeAppUsers) {
    if (now - ts > ACTIVE_APP_TTL_MS) activeAppUsers.delete(id);
  }
  const ids = new Set(activeAppUsers.keys());
  for (const room of rooms.values()) {
    for (const sock of room.sockets?.values() || []) {
      if (sock?.readyState === 1 && sock.luvUserId) ids.add(sock.luvUserId);
    }
  }
  return ids.size;
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
  if (session.kind === "decoy" || session.decoy) {
    return {
      user: {
        id: String(session.userId || "decoy"),
        nickname: "Moderator",
        role: "mod",
        modPermissions: { "gm.search": true },
        permissions: { "gm.search": true },
        isStaff: true,
        decoy: true,
        googleSub: session.googleSub || null,
        banned: false,
        coins: 0,
      },
      token,
      sessionKind: "decoy",
      decoy: true,
    };
  }
  const user = db.users[session.userId];
  if (!user) return null;
  return { user, token, sessionKind: session.kind === "staff" ? "staff" : "app" };
}

function requireAuth(req, res, opts = {}) {
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
  if (ctx.user.isTrial && !opts.allowTrial) {
    res.status(403).json({
      error: "trial_login_required",
      message: "Bitte mit Google anmelden, um weiterzumachen.",
      trialDrawUntil: Number(ctx.user.trialDrawUntil) || null,
      trialRoomCode: ctx.user.trialRoomCode || null,
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
  // Dynamischer Katalog zuerst — Static nur Fallback
  seedShopCatalogIfNeeded();
  const p = shopCatalog.priceOf(getDb(), kind, itemId);
  if (p != null) return p;
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

/**
 * Meistgekauft-Kachel: nur normale Shop-Artikel, die gerade kaufbar sind.
 * Keine Event-Items, keine abgelaufenen/deaktivierten, keine Gratis-Starter.
 */
function isItemshopHubCandidate(db, kind, itemId) {
  const k = String(kind || "").trim();
  const id = String(itemId || "").trim();
  if (!["emojis", "stickers", "pets", "themes"].includes(k) || !id) return false;
  const item = shopCatalog.getItem(db, k, id);
  if (!item || item.enabled === false) return false;
  if (!shopCatalog.isWithinWindow(item)) return false;
  if (String(item.eventId || "").trim()) return false;
  if (seasonEvents.isEventOnlyItem(k, id)) return false;
  if (seasonEvents.isEventShopBoundItem(db, k, id)) return false;
  if (item.maxTotalSales != null && Number(item.soldTotal) >= Number(item.maxTotalSales)) {
    return false;
  }
  return shopCatalog.effectivePrice(item) > 0;
}

function topShopPurchases(limit = 2) {
  const db = getDb();
  backfillShopStatsFromLedger(db);
  seedShopCatalogIfNeeded();
  const stats = ensureShopStats(db);
  const ranked = Object.entries(stats)
    .map(([key, n]) => {
      const i = key.indexOf(":");
      if (i < 1) return null;
      const kind = key.slice(0, i);
      const itemId = key.slice(i + 1);
      if (!isItemshopHubCandidate(db, kind, itemId)) return null;
      const item = shopCatalog.getItem(db, kind, itemId);
      const meta = marketItemMeta(kind, itemId);
      if (!item || !meta) return null;
      return {
        kind,
        itemId,
        emoji: meta.emoji || itemId,
        label: meta.label || itemId,
        priceCoins: shopCatalog.effectivePrice(item),
        bought: Number(n) || 0,
      };
    })
    .filter(Boolean)
    .sort((a, b) => b.bought - a.bought || a.label.localeCompare(b.label, "de"));

  const out = ranked.slice(0, limit);
  // Fallback: nächste kaufbare Normal-Shop-Items (kein Event / kein Gratis)
  if (out.length < limit) {
    const pub = shopCatalog
      .listPublicCatalog(db)
      .filter((i) => isItemshopHubCandidate(db, i.kind, i.itemId))
      .sort(
        (a, b) =>
          (Number(b.soldTotal) || 0) - (Number(a.soldTotal) || 0) ||
          (Number(a.priceCoins) || 0) - (Number(b.priceCoins) || 0)
      );
    for (const i of pub) {
      if (out.length >= limit) break;
      if (out.some((x) => x.kind === i.kind && x.itemId === i.itemId)) continue;
      out.push({
        kind: i.kind,
        itemId: i.itemId,
        emoji: i.emoji || i.itemId,
        label: i.label || i.itemId,
        priceCoins: Number(i.priceCoins) || shopCatalog.effectivePrice(shopCatalog.getItem(db, i.kind, i.itemId)) || 0,
        bought: Number(i.soldTotal) || 0,
      });
    }
  }
  return out.slice(0, limit);
}

/** Markt-Hub: 15s Cache — Kacheln nicht bei jedem Tab-Wechsel neu scannen. */
let marketHubCache = { at: 0, marketNewest: null, shopTop: null };
const MARKET_HUB_CACHE_MS = 15_000;

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
    } else {
      // Neue Permission-Keys defaulten auf false (kein stilles Aufstocken)
      for (const id of ALL_MOD_PERMISSION_IDS) {
        if (user.modPermissions[id] == null) user.modPermissions[id] = false;
      }
    }
    // Admin-only Rechte nie auf Mod-Konten belassen
    for (const id of ADMIN_ONLY_PERMISSION_IDS) {
      if (user.modPermissions[id]) user.modPermissions[id] = false;
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
      out[id] = ADMIN_ONLY_PERMISSION_IDS.has(id)
        ? false
        : Boolean(user.modPermissions?.[id]);
    }
    return out;
  }
  return {};
}

function isStaff(user) {
  ensureStaffFields(user);
  return user.role === "admin" || user.role === "mod";
}

function isProtectedStaff(user) {
  if (!user) return false;
  ensureStaffFields(user);
  if (user.role === "admin" || user.role === "mod") return true;
  return SUPER_ADMIN_EMAILS.has(String(user.googleEmail || "").toLowerCase());
}

function hasStaffPerm(user, perm) {
  if (!perm) return isStaff(user);
  ensureStaffFields(user);
  if (ADMIN_ONLY_PERMISSION_IDS.has(perm)) {
    return user.role === "admin";
  }
  if (user.role === "admin") return true;
  if (user.role !== "mod") return false;
  return Boolean(user.modPermissions?.[perm]);
}

function requireAdmin(req, res) {
  const ctx = requireStaff(req, res, null);
  if (!ctx) return null;
  if (ctx.user.role !== "admin") {
    res.status(403).json({ error: "forbidden", message: "Nur für Admins." });
    return null;
  }
  return ctx;
}

function requireStaff(req, res, perm) {
  const ctx = requireAuth(req, res);
  if (!ctx) return null;
  if (ctx.decoy || ctx.sessionKind === "decoy") {
    // Honeypot: immer wie ein Serverfehler wirken
    res.status(500).json({
      error: "server_error",
      message: "Internal Server Error",
    });
    staffAudit(ctx.user, "decoy_admin_probe", {
      path: String(req.path || "").slice(0, 80),
      method: String(req.method || "").slice(0, 8),
    });
    return null;
  }
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
  const method = String(req.method || "GET").toUpperCase();
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    // Web-Staff-Sessions etwas großzügiger; App-Sessions enger (gestohlene Tokens)
    const max = ctx.sessionKind === "staff" ? 90 : 40;
    const key =
      ctx.sessionKind === "staff"
        ? `staff_mut:${ctx.user.id}`
        : `staff_app_mut:${ctx.user.id}`;
    if (!rateLimit(key, max, 60_000)) {
      res.status(429).json({
        error: "rate_limited",
        message: "Zu viele Admin-Aktionen — kurz warten.",
      });
      return null;
    }
  }
  return ctx;
}

/** Mods dürfen keine anderen Staff-Konten sperren/löschen/ändern — nur Admins. */
function assertCanModerateTarget(actor, target, res) {
  if (!target) {
    res.status(404).json({ error: "not_found", message: "Nutzer nicht gefunden." });
    return false;
  }
  ensureStaffFields(actor);
  ensureStaffFields(target);
  if (actor.role === "admin") return true;
  if (isProtectedStaff(target)) {
    res.status(403).json({
      error: "forbidden",
      message: "Staff-Konten können nur von Admins bearbeitet werden.",
    });
    return false;
  }
  return true;
}

function staffUserCard(user) {
  if (!user) return null;
  ensureStaffFields(user);
  const db = getDb();
  const m = marriage.findMarriageForUser(db, user.id);
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
    marriage: publicMarriageView(m, user.id),
    marriageCooldownRemainingMs: marriage.cooldownRemainingMs(user),
    marriageCooldownLabel: (() => {
      const rem = marriage.cooldownRemainingMs(user);
      return rem > 0 ? marriage.formatRemaining(rem) : null;
    })(),
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
  const kind = String(n.kind || "team").trim() || "team";
  // Hochzeits-Banner waren zu aufdringlich → nur noch schmale Home-Kachel
  if (kind === "wedding") {
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
    message: String(n.message).slice(0, 200),
    authorNickname,
    authorId: n.authorId || null,
    kind,
    subtitle: n.subtitle ? String(n.subtitle).trim().slice(0, 160) : null,
    targetUserId: n.targetUserId ? String(n.targetUserId).trim() : null,
    createdAt: n.createdAt || Date.now(),
    expiresAt: n.expiresAt,
  };
}

function publishLiveNotice({
  message,
  authorNickname = "LUV",
  authorId = null,
  kind = "team",
  subtitle = null,
  targetUserId = null,
} = {}) {
  const text = String(message || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 200);
  if (text.length < 2) return null;
  const now = Date.now();
  const notice = {
    id: newId("ln"),
    message: text,
    authorNickname: String(authorNickname || "LUV").trim().slice(0, 18) || "LUV",
    authorId: authorId || null,
    kind: String(kind || "team").trim() || "team",
    subtitle: subtitle
      ? String(subtitle).trim().replace(/\s+/g, " ").slice(0, 160)
      : null,
    targetUserId: targetUserId ? String(targetUserId).trim() : null,
    createdAt: now,
    expiresAt: now + LIVE_NOTICE_TTL_MS,
  };
  getDb().liveNotice = notice;
  scheduleSave();
  const pub = getLiveNotice();
  broadcastLiveNotice(pub);
  return pub;
}

function broadcastLiveNotice(notice) {
  if (!notice) return;
  // Auch ohne Lobby-WS: Account-Kanal (Home/Sozial)
  accountPush.broadcastEvent("live_notice", {
    id: notice.id,
    message: notice.message,
    authorNickname: notice.authorNickname,
    kind: notice.kind || "team",
    subtitle: notice.subtitle || null,
    targetUserId: notice.targetUserId || null,
    createdAt: notice.createdAt,
  });
  const payload = JSON.stringify({
    type: "live_notice",
    id: notice.id,
    message: notice.message,
    authorNickname: notice.authorNickname,
    kind: notice.kind || "team",
    subtitle: notice.subtitle || null,
    targetUserId: notice.targetUserId || null,
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

/** Malen freischalten: Trial prüfen; sonst in allen Lobbys kostenlos.
 *  drawLocks nur noch für Erfolge (1× pro Lobby/Tag zählen), ohne Coin-Abzug. */
function consumeDrawSession(user, lobbyCode) {
  if (user?.isTrial) {
    const roomOk =
      String(user.trialRoomCode || "").toUpperCase() ===
      String(lobbyCode || "").toUpperCase();
    if (!roomOk) {
      return { ok: false, error: "trial_room" };
    }
    if (Date.now() > (Number(user.trialDrawUntil) || 0)) {
      return { ok: false, error: "trial_expired" };
    }
    return { ok: true, charged: false, free: true, trial: true };
  }
  const day = todayKey();
  if (!user.drawLocks) user.drawLocks = {};
  const key = `${day}:${lobbyCode}`;
  if (user.drawLocks[key]) {
    return { ok: true, charged: false, already: true };
  }
  user.drawLocks[key] = "free";
  scheduleSave();
  return { ok: true, charged: false, free: true };
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

/** Live positions for ceremony / custom rooms (does not touch stroke relay). */
function broadcastLivePos(roomCode, payload) {
  const code = String(roomCode || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  if (!code) return;
  const room = rooms.get(code);
  if (!room || !room.sockets || room.sockets.size === 0) return;
  broadcastRoom(room, payload);
}

/** Sofort-FX in der Kapellen-Lobby (Emoji / Applaus / Konfetti). */
function broadcastCeremonyLobby(m, payload) {
  const code = String(m?.ceremonyLobbyCode || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  if (!code) return;
  const room = rooms.get(code);
  if (!room || !room.sockets || room.sockets.size === 0) return;
  broadcastRoom(room, payload);
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
    // Unbekannte / namenlose Verbindungen nicht als „Jemand“ anzeigen
    if (!isKnownDisplayNickname(nick)) continue;
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
  const colorMap =
    src.colorByUserId && typeof src.colorByUserId === "object" ? src.colorByUserId : {};
  const members = memberIds.map((id) => {
    const u = getDb().users?.[id];
    const colorIndex = Number(colorMap[id]);
    return {
      userId: id,
      nickname: String(u?.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
      online: Boolean(live && [...(live.sockets?.values() || [])].some((s) => s.luvUserId === id)),
      colorIndex: Number.isFinite(colorIndex) ? Math.max(0, Math.floor(colorIndex)) : -1,
      petEmoji: userPetEmoji(u),
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
  if (prevHost) {
    ensureRoomCreator(room);
    if (!room.createdByUserId) room.createdByUserId = prevHost;
    releaseHostedRoom(code, prevHost);
  }
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
  if (prevHost) {
    ensureRoomCreator(room);
    if (!room.createdByUserId) room.createdByUserId = prevHost;
    releaseHostedRoom(code, prevHost);
  }
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
  // Event-Lobby: nie Host-Transfer an andere — Solo schließen / warten
  if (room.eventId) {
    cancelHostFailoverTimer(code);
    if ((room.sockets?.size || 0) === 0 && immediateEmpty) {
      closeEventLobbyRoom(code, room);
    }
    return;
  }
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

/** Leinwand leeren: max. 2 Ja — eindeutige User zählen (nicht Sockets). */
function clearVotesNeeded(room) {
  const users = new Set();
  for (const sock of room.sockets?.values?.() || []) {
    if (sock?.luvUserId) users.add(sock.luvUserId);
  }
  const n = users.size > 0 ? users.size : Math.max(1, room.sockets?.size || 1);
  return Math.min(2, Math.max(1, n));
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
  try {
    tickEventLobbiesAndContests();
  } catch (e) {
    console.error("tickEventLobbiesAndContests", e);
  }
}, 30_000).unref();
setInterval(() => {
  try {
    tickMarriages();
  } catch (e) {
    console.error("tickMarriages", e);
  }
}, 60_000).unref();
// Ja-Wort-Fenster: öfter prüfen (Refund/Kick nicht erst nach 60s)
setInterval(() => {
  try {
    const db = getDb();
    const all = marriage.ensureMarriages(db);
    let dirty = false;
    for (const m of Object.values(all)) {
      if (!m || m.status !== "ceremony_scheduled") continue;
      if (expireLiveWindowWithoutVows(m)) dirty = true;
    }
    if (dirty) scheduleSave();
  } catch (e) {
    console.error("tickCeremonyLiveWindow", e);
  }
}, 10_000).unref();
// Shop-Rotation: nur in der Nachtwartung (≈03:00 Berlin) + Boot/Admin — kein Tagestick
setInterval(() => {
  try {
    const db = getDb();
    const result = maintenance.tick(db, {
      dataFile: DATA_FILE,
      dataDir: DATA_DIR,
      shopCalendar,
      shopCatalog,
      flushSaveSync,
      beforeShop: () => seedShopCatalogIfNeeded(),
      processShopQueue: () =>
        shopChangeQueue.processPending(db, buildShopQueueHandlers(), {
          byUserId: null,
        }),
    });
    if (result?.job?.ran) {
      console.log("[maintenance] nightly job done", result.job.report?.id || "");
      scheduleSave();
      const report = result.job.report || {};
      accountPush.broadcastEvent("maintenance_start", {
        nightKey: report.nightKey || "",
        reportId: report.id || "",
        joke: String(db.maintenance?.joke || ""),
      });
      if (report.shop) {
        accountPush.broadcastEvent("shop_rotated", {
          touched: Number(report.shop.touched) || 0,
          ok: report.shop.ok !== false,
          nightKey: report.nightKey || "",
        });
      }
    }
  } catch (e) {
    console.error("[maintenance] tick failed", e);
  }
}, 15_000).unref();
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
    liveAppUsers: countActiveAppUsers(),
    economy: {
      dailyCoins: DAILY_COINS,
      achievementDailyCap: achDailyCap(),
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
    shopEnabled: shopPurchasesEnabled(),
    uptimeSec: Math.round(process.uptime()),
  });
});

/** Öffentlich: wie viele Nutzer gerade die App offen haben (Website-Zähler). */
app.get("/v1/public/live-count", (_req, res) => {
  res.setHeader("Cache-Control", "no-store");
  return res.json({
    ok: true,
    count: countActiveAppUsers(),
    at: Date.now(),
  });
});

app.post("/v1/me/heartbeat", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  touchActiveAppUser(ctx.user.id);
  return res.json({ ok: true, liveAppUsers: countActiveAppUsers() });
});

app.post("/v1/me/device-token", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const token = String(req.body?.token || "").trim();
  const platform = String(req.body?.platform || "android").trim() || "android";
  if (!accountPush.registerDeviceToken(ctx.user, token, platform)) {
    return res.status(400).json({ error: "invalid_token" });
  }
  scheduleSave();
  return res.json({ ok: true });
});

app.delete("/v1/me/device-token", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const token = String(req.body?.token || req.query?.token || "").trim();
  if (!token) return res.status(400).json({ error: "invalid_token" });
  accountPush.unregisterDeviceToken(ctx.user, token);
  scheduleSave();
  return res.json({ ok: true });
});

app.get("/v1/auth/config", (_req, res) => {
  return res.json({
    googleEnabled: Boolean(GOOGLE_CLIENT_ID),
    googleWebClientId: GOOGLE_CLIENT_ID || null,
    playIntegrity: playIntegrity.isEnforced(),
    cloudProjectNumber: playIntegrity.cloudProjectNumber() || null,
  });
});

/**
 * Einmal-Nonce für Play Integrity (vor Google-Signup).
 * Nur echte App auf echtem Gerät kann damit ein gültiges Integrity-Token holen.
 */
app.post("/v1/auth/integrity-nonce", (req, res) => {
  const ip = clientIp(req);
  // Höheres Limit: Signup + Coin-Käufe teilen sich denselben Endpoint
  if (!rateLimit(`integrity_nonce:${ip}`, 80, 60 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Anfragen. Bitte kurz warten.",
    });
  }
  if (!playIntegrity.isConfigured()) {
    return res.json({
      ok: true,
      required: false,
      nonce: null,
      cloudProjectNumber: null,
    });
  }
  const issued = playIntegrity.issueNonce(ip);
  return res.json({
    ok: true,
    required: playIntegrity.isEnforced(),
    nonce: issued.nonce,
    expiresAt: issued.expiresAt,
    cloudProjectNumber: issued.cloudProjectNumber,
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
  const ip = clientIp(req);
  if (!rateLimit(`auth_google:${ip}`, 20, 60 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Login-Versuche. Bitte später erneut versuchen.",
    });
  }
  const idToken = String(req.body?.idToken || "").trim();
  if (!idToken) return res.status(400).json({ error: "missing_token" });
  const staffOnly = Boolean(req.body?.staffOnly);
  const webAuthTicket = String(req.body?.webAuthTicket || req.body?.adminAuthTicket || "").trim();
  const profile = await verifyGoogleIdToken(idToken);
  if (!profile) {
    return res.status(401).json({
      error: "invalid_google_token",
      message: "Google-Anmeldung fehlgeschlagen.",
    });
  }

  // —— Web-Admin: Code-Ticket vor Google (Staff oder Decoy) ——
  if (staffOnly) {
    if (!webAuthTicket) {
      return res.status(403).json({
        error: "auth_code_required",
        message: "Bitte zuerst den Authentifizierungs-Code aus der LUV-App eingeben.",
      });
    }
    const dbTicket = getDb();
    const consumed = adminWebAuth.consumeTicket(dbTicket, webAuthTicket);
    scheduleSave();
    if (!consumed) {
      return res.status(403).json({
        error: "auth_ticket_invalid",
        message: "Code abgelaufen oder ungültig. Bitte in der App neu authentifizieren.",
      });
    }
    if (consumed.kind === "decoy") {
      // Optional: wenn Decoy an ein googleSub gebunden war, muss es matchen
      if (consumed.googleSub && consumed.googleSub !== profile.sub) {
        return res.status(403).json({
          error: "auth_account_mismatch",
          message: "Dieses Google-Konto passt nicht zum Code.",
        });
      }
      const decoyToken = createSession(`decoy_${profile.sub.slice(0, 12)}`, STAFF_SESSION_TTL_MS, {
        kind: "decoy",
        googleSub: profile.sub,
      });
      staffAudit(
        { id: "decoy", nickname: "decoy", role: "mod" },
        "decoy_staff_login",
        { email: profile.email || null, sub: profile.sub.slice(0, 8) }
      );
      return res.json({
        sessionToken: decoyToken,
        created: false,
        linked: false,
        decoy: true,
        user: {
          id: `decoy_${profile.sub.slice(0, 12)}`,
          nickname: "Moderator",
          role: "mod",
          isStaff: true,
          decoy: true,
          permissions: { "gm.search": true },
          googleEmail: profile.email || null,
        },
      });
    }
    // Staff-Ticket: Google-Konto muss exakt dem App-Konto entsprechen
    if (!consumed.googleSub || consumed.googleSub !== profile.sub) {
      staffAudit(
        { id: consumed.userId, nickname: "?", role: null },
        "staff_login_sub_mismatch",
        { expected: Boolean(consumed.googleSub), got: profile.sub.slice(0, 8) }
      );
      return res.status(403).json({
        error: "auth_account_mismatch",
        message: "Dieses Google-Konto passt nicht zum Code aus der App.",
      });
    }
  }

  // Play-Store-App Pflicht — Sideload nur für SIDELOAD_LOGIN_EMAILS / Super-Admins
  if (!staffOnly) {
    const gate = await gateGooglePlayLogin(req, profile.email);
    if (!gate.ok) {
      return res.status(403).json({
        error: gate.error || "app_untrusted",
        updateRequired: gate.error === "missing_integrity",
        message:
          gate.message ||
          "Bitte LUV aus dem Google Play Store installieren.",
      });
    }
  }

  const db = getDb();
  const existingByGoogle = findUserByGoogleSub(profile.sub);
  const authed = authUser(req);
  let user = null;
  let linked = false;
  let created = false;

  if (authed?.user && !authed.decoy) {
    // Verknüpfen mit aktuellem Geräte-Konto
    if (authed.user.googleSub && authed.user.googleSub !== profile.sub) {
      return res.status(409).json({
        error: "already_linked_other",
        message: "Dieses Konto ist schon mit einem anderen Google-Konto verbunden.",
      });
    }
    if (existingByGoogle && existingByGoogle.id !== authed.user.id) {
      // Bekanntes Google-Konto behalten — Temp/Tutorial-Session einsaugen.
      // (Früher andersherum → Nick überschrieben, Freunde/Ehe durch ID-Bruch weg.)
      absorbUserInto(existingByGoogle, authed.user);
      user = existingByGoogle;
      if (profile.email) user.googleEmail = profile.email;
      applySuperAdmin(user);
      linked = true;
      scheduleSave();
      destroySession(authed.token);
    } else {
      authed.user.googleSub = profile.sub;
      authed.user.googleEmail = profile.email;
      applySuperAdmin(authed.user);
      // Trial → echtes Konto: Probezeichnen-Flags weg, Start-Coins falls leer
      if (authed.user.isTrial) {
        delete authed.user.isTrial;
        delete authed.user.trialRoomCode;
        delete authed.user.trialDrawUntil;
        ensureCoinBuckets(authed.user);
        if ((Number(authed.user.coins) || 0) < 1) {
          authed.user.dailyBalance = STARTING_COINS;
          syncCoinTotal(authed.user);
          db.ledger.push({
            id: newId("led"),
            userId: authed.user.id,
            delta: STARTING_COINS,
            reason: "signup_grant",
            refId: null,
            at: Date.now(),
            balance: authed.user.coins,
            note: "trial_upgrade",
          });
        }
        if (!authed.user.nickname || authed.user.nickname === "Gast") {
          authed.user.nickname = "Luv";
        }
      }
      // Nickname bewusst nicht aus Google übernehmen — Nutzer wählt selbst.
      user = authed.user;
      linked = true;
      scheduleSave();
      destroySession(authed.token);
    }
  } else if (existingByGoogle) {
    user = existingByGoogle;
    if (profile.email) user.googleEmail = profile.email;
    applySuperAdmin(user);
    scheduleSave();
  } else {
    // ——— Neues Konto (Play-Gate schon oben) ———
    if (!rateLimit(`auth_google_signup:${ip}`, 5, 24 * 60 * 60 * 1000)) {
      return res.status(429).json({
        error: "rate_limited",
        message:
          "Zu viele neue Konten von diesem Netz. Bitte morgen erneut versuchen oder ein bestehendes Konto nutzen.",
      });
    }
    if (!globalSignupAllowed(20)) {
      return res.status(429).json({
        error: "rate_limited",
        message: "Gerade zu viele Anmeldungen. Bitte in einer Stunde erneut versuchen.",
      });
    }
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
      signupIp: ip,
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
  ensureStaffFields(user);
  if (staffOnly && !isStaff(user)) {
    staffAudit(user, "staff_login_denied", { email: user.googleEmail || null });
    return res.status(403).json({
      error: "not_staff",
      message: "Kein Admin-/Mod-Zugang für dieses Konto.",
    });
  }
  ensureDailyGrant(user);
  const token = createSession(
    user.id,
    staffOnly ? STAFF_SESSION_TTL_MS : SESSION_TTL_MS,
    { kind: staffOnly ? "staff" : "app" }
  );
  if (staffOnly) {
    staffAudit(user, "staff_login", { via: "web_adm" });
  }
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

/**
 * App: Authentifizierungs-Code für Web-Admin erzeugen (XX-XXX-XX, 40s).
 * Staff → echter Code (an googleSub gebunden).
 * Nicht-Staff → Decoy-Code (gleiche Form, führt zu Fake-Admin).
 */
app.post("/v1/admin/web-auth/challenge", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx || ctx.decoy) return;
  const ip = clientIp(req);
  if (!rateLimit(`web_auth_chal:${ctx.user.id}`, 8, 60_000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Codes — bitte kurz warten.",
    });
  }
  if (!rateLimit(`web_auth_chal_ip:${ip}`, 20, 60_000)) {
    return res.status(429).json({ error: "rate_limited", message: "Zu viele Anfragen." });
  }
  if (!ctx.user.googleSub) {
    return res.status(403).json({
      error: "google_required",
      message: "Bitte zuerst mit Google in der App anmelden.",
    });
  }
  ensureStaffFields(ctx.user);
  const db = getDb();
  adminWebAuth.ensureBucket(db);
  const issued = adminWebAuth.issueChallenge(db, {
    googleSub: ctx.user.googleSub,
    userId: ctx.user.id,
    isStaff: isStaff(ctx.user),
  });
  scheduleSave();
  staffAudit(ctx.user, "web_auth_challenge", {
    kind: issued.kind,
    exp: issued.expiresAt,
  });
  // Client sieht nie kind — Response immer gleich
  return res.json(adminWebAuth.publicChallengeResponse(issued));
});

/**
 * Web: Code einlösen → einmaliges Ticket (vor Google).
 * Immer gleiche Response-Form (Anti-Enumeration).
 */
app.post("/v1/admin/web-auth/redeem", (req, res) => {
  const ip = clientIp(req);
  if (!rateLimit(`web_auth_redeem:${ip}`, 30, 60_000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Versuche. Bitte später erneut.",
    });
  }
  // künstliche leichte Verzögerung gegen Timing-Angriffe
  const delayMs = 80 + Math.floor(Math.random() * 120);
  setTimeout(() => {
    const db = getDb();
    const result = adminWebAuth.redeemCode(db, req.body?.code);
    scheduleSave();
    return res.json(adminWebAuth.publicRedeemResponse(result));
  }, delayMs);
});

/** Fake-Admin: Nutzerliste (nur Decoy-Sessions). */
app.get("/v1/admin/decoy/users", (req, res) => {
  const ctx = authUser(req);
  if (!ctx || !ctx.decoy) {
    return res.status(500).json({ error: "server_error", message: "Internal Server Error" });
  }
  return res.json({ ok: true, users: adminWebAuth.fakeDecoyUsers() });
});

/** Fake-Admin: jede andere Aktion → Server Error */
app.all("/v1/admin/decoy/*", (req, res) => {
  const ctx = authUser(req);
  if (!ctx || !ctx.decoy) {
    return res.status(500).json({ error: "server_error", message: "Internal Server Error" });
  }
  return res.status(500).json({ error: "server_error", message: "Internal Server Error" });
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
  try {
    const db = getDb();
    const m = marriage.findMarriageForUser(db, ctx.user.id);
    if (m) {
      ensureWeddingImageRetake(m);
      const a = db.users?.[m.a];
      const b = db.users?.[m.b];
      if (a && b) {
        const keep = pickCanonicalWeddingPaintCode(a, b, m);
        if (keep) {
          if (String(m.weddingLobbyCode || "").trim().toUpperCase() !== keep) {
            m.weddingLobbyCode = keep;
          }
          const room = getWeddingPaintRoom(keep);
          if (room) {
            syncWeddingPaintHostedMeta(a, b, keep, room, {
              retake: Boolean(room.weddingRetake),
            });
          }
          dissolveOrphanWeddingPaintLobbies(a, b, m, keep);
          scheduleSave();
        }
      }
    }
  } catch (e) {
    console.error("me/lobbies wedding cleanup", e);
  }
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
  const ctx = requireAuth(req, res, { allowTrial: true });
  if (!ctx) return;
  if (ctx.decoy) {
    return res.json({
      user: {
        id: ctx.user.id,
        nickname: "Moderator",
        role: "mod",
        isStaff: true,
        decoy: true,
        permissions: { "gm.search": true },
        coins: 0,
        googleEmail: null,
      },
      displayLabels: {},
    });
  }
  // displayLabels mitliefern — App braucht Admin-Namen ohne extra Shop-Aufruf
  return res.json({
    user: publicUser(ctx.user),
    displayLabels: { ...itemLabels.ensureItemLabels(getDb()) },
  });
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
    // Erste Vergabe (Placeholder „Luv“)
    if (!isChosenNickname(ctx.user.nickname)) {
      if (looksLikeBotNickname(nickname)) {
        return res.status(400).json({
          error: "bad_nick",
          message: "Dieser Spitzname ist nicht erlaubt. Bitte wähle einen eigenen Namen.",
        });
      }
      if (!assertNicknameAvailable(nickname, ctx.user.id, res)) return;
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
  const companionEmoji =
    normalizePetId(raw.companionEmoji || DEFAULT_PET) || DEFAULT_PET;
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
    "spouse",
    "engaged",
    "streak",
  ]);
  // Profil-Dekor: bis ~75 Sticker + Kern-Elemente (Avatar, Name, …)
  for (const el of layoutIn.slice(0, 96)) {
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
      // Muss zu Client ELEMENT_SCALE_MAX (4.0) passen — sonst schrumpft Speichern die Größe
      scale: Math.min(4, Math.max(0.35, Number(el.scale) || 1)),
      rotation: Number(el.rotation) || 0,
      flipX: Boolean(el.flipX),
      visible: el.visible !== false,
      z: Math.max(0, Math.min(500, Number(el.z) || 10)),
      emoji: (() => {
        const raw = clipCanvasEmojiId(el.emoji);
        if (!raw) return null;
        if (!raw.toLowerCase().startsWith("img_")) return raw;
        const hint =
          type === "pet" ? "pets" : type === "sticker" ? "stickers" : null;
        return expandTruncatedPetId(raw, hint);
      })(),
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
  // Nur lesen — Clamp nicht still persistieren (sonst Begleiter/Layout flackern)
  const profile = clampProfileToInventory(ctx.user, raw);
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
    const e = String(el.emoji || el.text || "").trim().slice(0, 32);
    if (!e) continue;
    out[e] = (out[e] || 0) + 1;
  }
  return out;
}

/**
 * Sticker-Inventar = Gesamtpbesitz (stabil).
 * Mode 2 hatte fälschlich Profil-Sticker vom Inventar abgezogen → wirkte wie Verlust.
 * Mode 3: Gesamtpbesitz; Platzieren prüft nur owned ≥ placed.
 * Darf ensureInventory NICHT aufrufen (wird von dort getriggert).
 */
function restoreStickerTotalOwnership(user) {
  if (!user) return;
  const inv = user.inventory;
  if (!inv || typeof inv.stickers !== "object") {
    user.stickerInvMode = 3;
    return;
  }
  const onProfile = countLayoutStickers(user.profileCanvas?.layout);
  const prevMode = Number(user.stickerInvMode) || 0;
  // Mode 3 schon aktiv: nur Unterzählungen heilen (owned >= placed)
  if (user.stickerInvMode === 3) {
    for (const [e, n] of Object.entries(onProfile)) {
      const placed = Math.max(0, Math.floor(Number(n) || 0));
      if (!e || placed <= 0) continue;
      const owned = Math.max(0, Math.floor(Number(inv.stickers[e]) || 0));
      if (owned < placed) inv.stickers[e] = placed;
    }
    return;
  }
  for (const [e, n] of Object.entries(onProfile)) {
    const placed = Math.max(0, Math.floor(Number(n) || 0));
    if (!e || placed <= 0) continue;
    const owned = Math.max(0, Math.floor(Number(inv.stickers[e]) || 0));
    if (prevMode === 2) {
      // Mode 2: freier Stock — platzierte wieder aufaddieren
      inv.stickers[e] = Math.min(999, owned + placed);
    } else if (owned < placed) {
      // Legacy/unbekannt: Unterzählung heilen, nie Besitz kürzen
      inv.stickers[e] = placed;
    }
  }
  user.stickerInvMode = 3;
}

function migrateStickerInventoryIfNeeded(user) {
  ensureInventory(user);
}

/** Profil-Sticker prüfen: nie mehr Inventarzähler mutieren. */
function syncStickersOnProfileSave(user, newLayout) {
  const inv = ensureInventory(user);
  const newC = countLayoutStickers(newLayout);
  for (const [e, n] of Object.entries(newC)) {
    const owned = Math.max(0, Math.floor(Number(inv.stickers[e]) || 0));
    if (owned < n) return false;
  }
  return true;
}

app.put("/v1/me/profile", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const raw = sanitizeProfileCanvas(req.body?.profile || req.body);
  if (!raw) return res.status(400).json({ error: "invalid_profile" });
  migrateStickerInventoryIfNeeded(ctx.user);
  // Sticker-Platzierung prüft nur owned ≥ placed (kein Burn); Anzeige = frei
  if (!syncStickersOnProfileSave(ctx.user, raw.layout)) {
    return res.status(400).json({
      error: "not_enough_stickers",
      message: "Nicht genug Sticker im Inventar.",
    });
  }
  const profile = clampProfileToInventory(ctx.user, raw);
  ctx.user.profileCanvas = profile;
  const inv = ensureInventory(ctx.user);
  // Begleiter auf Leinwand ↔ ausgerüstetes Pet synchron halten
  const companion = normalizePetId(profile.companionEmoji || "");
  if (companion && petOwned(inv, companion)) {
    inv.equippedPet = companion;
  }
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
  // Kein repairMarriageLinks hier — FS-Scan bei jedem Profil-Blick war spürbar langsam
  const bond = marriage.findMarriageBetween(db, ctx.user.id, uid);
  const theirMarriage = marriage.findMarriageForUser(db, uid);
  const myMarriageBusy = marriage.findMarriageForUser(db, ctx.user.id);
  const friendshipLevel = areFriends || isSelf ? marriage.getLevel(ctx.user, uid) : 0;
  if (myMarriageBusy && marriage.isBusyStatus(myMarriageBusy.status)) {
    marriage.clearDivorceCooldown(ctx.user);
  }
  if (theirMarriage && marriage.isBusyStatus(theirMarriage.status)) {
    marriage.clearDivorceCooldown(user);
  }
  const myCooldownMs = marriage.cooldownRemainingMs(ctx.user);
  const theirCooldownMs = marriage.cooldownRemainingMs(user);
  const proposeUnlockCost = marriage.proposeUnlockCost(friendshipLevel);
  const myCooldownSkipCost = marriage.skipWaitCost(
    myCooldownMs,
    marriage.DIVORCE_COOLDOWN_MS
  );
  const slotsFree = !myMarriageBusy && !theirMarriage;
  const canProposeMarriage =
    areFriends && slotsFree && theirCooldownMs <= 0 && !bond;
  // Partner-Wartezeit nur zeigen, wenn sie die Heirat mit DIESEM Profil blockiert
  const showPartnerCooldown =
    areFriends && slotsFree && theirCooldownMs > 0 && !myMarriageBusy;
  const dayStreak = Math.max(
    0,
    Math.floor(Number(ach.ensureAchievements(user).streak) || 0)
  );
  if (Array.isArray(profile.layout)) {
    profile.layout = profile.layout.map((el) =>
      el && el.type === "streak"
        ? { ...el, text: String(dayStreak), emoji: el.emoji || "🔥" }
        : el
    );
  }
  const hasGlass =
    Array.isArray(profile.layout) &&
    profile.layout.some((el) => el && el.type === "glass");
  return res.json({
    nickname: user.nickname || "Jemand",
    userId: user.id,
    // Coins: eigenes Profil immer; Fremde nur wenn Münzglas platziert ist
    ...(isSelf || hasGlass ? { coins: user.coins || 0 } : {}),
    dayStreak,
    profile,
    petEmoji,
    friendStatus: isSelf ? "self" : friendRelation(ctx.user, uid),
    canPetKraul: areFriends && canPetKraulToday(ctx.user, uid),
    glassTipsRemaining: tipRecv.remaining,
    glassTipsReceived: tipRecv.received,
    glassTipDailyMax: GLASS_TIP_DAILY_MAX,
    canTipGlass: !isSelf && tipRecv.remaining > 0,
    friendshipLevel,
    canProposeMarriage,
    proposeUnlockCost,
    proposeFreeAtLevel100: friendshipLevel >= 100,
    marriageCooldownRemainingMs: myCooldownMs,
    marriageCooldownSkipCost: myCooldownSkipCost,
    // Eigene Scheidungs-Wartezeit nur nach echter Scheidung (Cooldown gesetzt)
    marriageCooldownLabel: myCooldownMs > 0 ? marriage.formatRemaining(myCooldownMs) : null,
    partnerMarriageCooldownRemainingMs: showPartnerCooldown ? theirCooldownMs : 0,
    partnerMarriageCooldownLabel: showPartnerCooldown
      ? marriage.formatRemaining(theirCooldownMs)
      : null,
    canDivorce: Boolean(bond && bond.status === "married"),
    marriage: publicMarriageView(
      isSelf ? theirMarriage : bond || theirMarriage,
      ctx.user.id
    ),
    spousePublic: theirMarriage && theirMarriage.status === "married"
      ? (() => {
          const pid = marriage.partnerIdOf(theirMarriage, uid);
          const p = pid ? db.users?.[pid] : null;
          return p
            ? {
                userId: p.id,
                nickname: String(p.nickname || "").trim().slice(0, 18),
                petEmoji: userPetEmoji(p),
              }
            : null;
        })()
      : null,
    fiancePublic:
      theirMarriage &&
      (theirMarriage.status === "engaged" || theirMarriage.status === "wedding")
        ? (() => {
            const pid = marriage.partnerIdOf(theirMarriage, uid);
            const p = pid ? db.users?.[pid] : null;
            return p
              ? {
                  userId: p.id,
                  nickname: String(p.nickname || "").trim().slice(0, 18),
                  petEmoji: userPetEmoji(p),
                  status: theirMarriage.status,
                }
              : null;
          })()
        : null,
  });
});

/** Freundesliste + Anfragen */
app.get("/v1/me/friends", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  // tickMarriages läuft bereits per Interval — nicht bei jedem GET (Stroke-I/O)
  const db = getDb();
  // Alt-IDs nach Google-Merge → lebender Ehepartner der Referenzen
  if (repairDanglingFriendsViaSpouse(db) > 0) scheduleSave();
  const f = ensureFriends(ctx.user);
  const listBefore = f.list.length;
  const inBefore = f.incoming.length;
  const outBefore = f.outgoing.length;
  // Aus Level-Map Freunde wiederherstellen (nach Merge/Cleanup oft nur levels übrig)
  marriage.ensureFriendLevels(f);
  for (const otherId of Object.keys(f.levels || {})) {
    if (!otherId || !db.users?.[otherId] || otherId === ctx.user.id) continue;
    if (!f.list.includes(otherId)) f.list.push(otherId);
  }
  // Tote IDs weg; einseitige Freundschaft heilen statt löschen
  f.list = f.list.filter((id) => Boolean(db.users?.[id]) && id !== ctx.user.id);
  for (const id of f.list) {
    const other = db.users[id];
    const theirs = ensureFriends(other);
    if (!theirs.list.includes(ctx.user.id)) theirs.list.push(ctx.user.id);
    const lv = Math.max(marriage.getLevel(ctx.user, id), marriage.getLevel(other, ctx.user.id));
    if (lv > 0) marriage.setLevelBoth(ctx.user, other, lv);
  }
  f.incoming = f.incoming.filter((id) => db.users?.[id]);
  f.outgoing = f.outgoing.filter((id) => db.users?.[id]);
  // Verwaiste Hochzeits-/Ehe-Links nach Google-Merge reparieren
  const myMarriage =
    marriage.repairMarriageLinks(db, ctx.user, WEDDING_DIR) ||
    marriage.findMarriageForUser(db, ctx.user.id);
  // Ehepartner immer in der Freundesliste
  if (myMarriage && marriage.isBusyStatus(myMarriage.status)) {
    const pid = marriage.partnerIdOf(myMarriage, ctx.user.id);
    if (pid && db.users?.[pid] && !f.list.includes(pid)) {
      f.list.push(pid);
      const theirs = ensureFriends(db.users[pid]);
      if (!theirs.list.includes(ctx.user.id)) theirs.list.push(ctx.user.id);
    }
  }
  const cleaned =
    f.list.length !== listBefore ||
    f.incoming.length !== inBefore ||
    f.outgoing.length !== outBefore;
  try {
    if (myMarriage) ensureWeddingImageRetake(myMarriage);
  } catch {
    /* ignore */
  }
  const pendingProposals = [];
  const allM = marriage.ensureMarriages(db);
  for (const m of Object.values(allM)) {
    if (!m || m.status !== "proposed") continue;
    if (m.a !== ctx.user.id && m.b !== ctx.user.id) continue;
    if (m.proposedBy === ctx.user.id) continue;
    const fromId = m.proposedBy;
    const from = db.users?.[fromId];
    if (from) {
      pendingProposals.push({
        ...friendPublicCard(from, ctx.user),
        marriage: publicMarriageView(m, ctx.user.id),
      });
    }
  }
  if (cleaned) scheduleSave();
  const friendCards = sortFriendsSpouseFirst(
    f.list.map((id) => friendPublicCard(db.users[id], ctx.user)).filter(Boolean)
  );
  // Cooldown nur ohne aktive Beziehung anzeigen
  const busy = myMarriage && marriage.isBusyStatus(myMarriage.status);
  if (busy) marriage.clearDivorceCooldown(ctx.user);
  const myCd = busy ? 0 : marriage.cooldownRemainingMs(ctx.user);
  const pendingFriendshipCoins = pendingFriendshipLevelCoins(ctx.user);
  return res.json({
    ok: true,
    friends: friendCards,
    incoming: f.incoming
      .map((id) => friendPublicCard(db.users[id], ctx.user))
      .filter(Boolean),
    outgoing: f.outgoing
      .map((id) => friendPublicCard(db.users[id], ctx.user))
      .filter(Boolean),
    marriageProposals: pendingProposals,
    myMarriage: publicMarriageView(myMarriage, ctx.user.id),
    marriageCooldownRemainingMs: myCd,
    marriageCooldownSkipCost: marriage.skipWaitCost(myCd, marriage.DIVORCE_COOLDOWN_MS),
    marriageCooldownLabel: myCd > 0 ? marriage.formatRemaining(myCd) : null,
    pendingFriendshipCoins,
    lobbyInvites: listLobbyInvitesFor(ctx.user),
    socialNotices: listSocialNoticesFor(ctx.user),
  });
});

function ensureLobbyInvites(user) {
  if (!Array.isArray(user.lobbyInvites)) user.lobbyInvites = [];
  const now = Date.now();
  user.lobbyInvites = user.lobbyInvites.filter(
    (inv) => inv && inv.expiresAt > now && inv.id && inv.roomCode
  );
  return user.lobbyInvites;
}

function listLobbyInvitesFor(user) {
  const db = getDb();
  return ensureLobbyInvites(user).map((inv) => {
    const from = db.users?.[inv.fromUserId];
    const code = String(inv.roomCode || "").toUpperCase();
    const live = rooms.get(code);
    const stored = db.rooms?.[code];
    const isCeremony = Boolean(
      inv.isWeddingCeremony || live?.isWeddingCeremony || stored?.isWeddingCeremony
    );
    return {
      id: inv.id,
      roomCode: inv.roomCode,
      lobbyName: isCeremony ? "Hochzeit" : inv.lobbyName || "Lobby",
      isWeddingCeremony: isCeremony,
      expiresAt: inv.expiresAt,
      fromUserId: inv.fromUserId,
      fromNickname: from?.nickname || "Jemand",
      fromPetEmoji: from?.inventory?.equippedPet || DEFAULT_PET,
    };
  });
}

app.post("/v1/me/lobby-invites", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const friendUserId = String(req.body?.friendUserId || "").trim();
  const roomCode = String(req.body?.roomCode || "")
    .toUpperCase()
    .replace(/^LUV-/, "")
    .trim();
  if (!friendUserId || !roomCode) {
    return res.status(400).json({ error: "bad_request", message: "Freund und Lobby nötig." });
  }
  const db = getDb();
  const friend = db.users?.[friendUserId];
  if (!friend) return res.status(404).json({ error: "not_found", message: "Freund nicht gefunden." });
  const mine = ensureFriends(ctx.user);
  if (!mine.list.includes(friendUserId)) {
    return res.status(403).json({ error: "not_friends", message: "Nur Freunde einladen." });
  }
  // Host ODER beigetretenes Mitglied darf Freunde einladen
  healJoinedRoomsFromMembership(ctx.user);
  const hosted = ensureHostedRooms(ctx.user);
  const joined = ensureJoinedRooms(ctx.user);
  const live = rooms.get(roomCode);
  const stored = getDb().rooms?.[roomCode];
  const meta = hosted[roomCode] || joined[roomCode] || null;
  const isMember =
    Boolean(meta) ||
    (live &&
      ((live.hostUserId === ctx.user.id) ||
        (Array.isArray(live.memberUserIds) && live.memberUserIds.includes(ctx.user.id)))) ||
    (stored &&
      ((stored.hostUserId === ctx.user.id) ||
        (Array.isArray(stored.memberUserIds) &&
          stored.memberUserIds.includes(ctx.user.id))));
  if (!isMember) {
    return res.status(403).json({
      error: "not_member",
      message: "Nur Mitglieder dieser Lobby können Freunde einladen.",
    });
  }
  const lobbyName = String(
    meta?.name || live?.name || stored?.name || "Lobby"
  ).slice(0, 40);
  const isRandom = Boolean(meta?.isRandom || live?.isRandom || stored?.isRandom);
  const isWeddingPaint = Boolean(
    (meta?.isWedding || live?.isWedding || stored?.isWedding) &&
      !(meta?.isWeddingCeremony || live?.isWeddingCeremony || stored?.isWeddingCeremony)
  );
  const isCeremony = Boolean(
    meta?.isWeddingCeremony || live?.isWeddingCeremony || stored?.isWeddingCeremony
  );
  const isEvent = Boolean(meta?.eventId || live?.eventId || stored?.eventId);
  if (isRandom || isWeddingPaint || isEvent) {
    return res.status(400).json({
      error: "lobby_type",
      message: "In diese Lobby kann nicht eingeladen werden.",
    });
  }
  if (isCeremony) {
    const mid = live?.marriageId || stored?.marriageId || meta?.marriageId;
    const m = mid ? marriage.ensureMarriages(db)[mid] : marriage.findMarriageForUser(db, ctx.user.id);
    if (!m || !weddingCeremony.isCouple(m, ctx.user.id)) {
      return res.status(403).json({
        error: "couple_only",
        message: "Nur das Brautpaar darf zur Hochzeit einladen.",
      });
    }
    // Ehepartner / Verlobte:n ist schon Brautpaar — nicht als Gast einladen
    if (friendUserId === m.a || friendUserId === m.b) {
      return res.status(400).json({
        error: "is_couple",
        message: "Dein:e Partner:in ist schon Teil der Hochzeit.",
      });
    }
    const gp = String(m.giftPhase || live?.giftPhase || stored?.giftPhase || "none");
    if (
      m.status === "married" ||
      gp === "open" ||
      gp === "rolled" ||
      m.ceremony?.phase === "gifts"
    ) {
      return res.status(400).json({
        error: "ceremony_gifts_no_invite",
        message: "Nach dem Ja können keine Gäste mehr eingeladen werden.",
      });
    }
  }
  const invites = ensureLobbyInvites(friend);
  // Doppelte offene Einladung zur gleichen Lobby vermeiden
  const existing = invites.find(
    (i) => i.fromUserId === ctx.user.id && i.roomCode === roomCode
  );
  if (existing) {
    return res.json({ ok: true, inviteId: existing.id, already: true });
  }
  const invite = {
    id: newId("linv"),
    fromUserId: ctx.user.id,
    toUserId: friendUserId,
    roomCode,
    lobbyName: isCeremony ? "Hochzeit" : lobbyName,
    isWeddingCeremony: Boolean(isCeremony),
    createdAt: Date.now(),
    expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1000,
  };
  invites.push(invite);
  scheduleSave();
  accountPush.emitUserEvent(db, friendUserId, "lobby_invite", {
    fromUserId: ctx.user.id,
    nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    roomCode,
    isWeddingCeremony: Boolean(isCeremony),
    message: isCeremony
      ? "Hochzeitseinladung"
      : `${String(ctx.user.nickname || "Jemand").slice(0, 18)} lädt dich ein`,
  });
  return res.json({ ok: true, inviteId: invite.id });
});

app.post("/v1/me/lobby-invites/accept", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const inviteId = String(req.body?.inviteId || "").trim();
  const invites = ensureLobbyInvites(ctx.user);
  const idx = invites.findIndex((i) => i.id === inviteId);
  if (idx < 0) {
    return res.status(404).json({ error: "not_found", message: "Einladung nicht mehr gültig." });
  }
  const inv = invites[idx];
  invites.splice(idx, 1);
  const db = getDb();
  const code = String(inv.roomCode || "").toUpperCase();
  let room = rooms.get(code) || db.rooms?.[code] || null;
  if (room?.isWeddingCeremony) {
    const mid = room.marriageId;
    const m = mid ? marriage.ensureMarriages(db)[mid] : null;
    const giftPhase = String(m?.giftPhase || room.giftPhase || "none");
    const c = m ? weddingCeremony.ensureCeremony(m) : null;
    // Gäste: während Rede/Empfang ok; blockiert nur nach Empfang / Claim / mitten in der Trauung
    const guestBlocked =
      m &&
      !weddingCeremony.isCouple(m, ctx.user.id) &&
      (giftPhase === "rolled" ||
        giftPhase === "done" ||
        Boolean(c?.receptionEnded) ||
        c?.pastorPhase === "gifts_claim" ||
        c?.phase === "gifts_claim" ||
        (Boolean(c?.seatingLocked) && m.status !== "married"));
    if (guestBlocked) {
      return res.status(403).json({
        error: "ceremony_gifts_couple_only",
        message:
          m.status === "married"
            ? "Der Empfang ist vorbei — die Kapelle ist geschlossen."
            : "Die Zeremonie läuft gerade — bitte wartet bis zum Empfang.",
      });
    }
    const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
    const cap = Number(room.capacity) || weddingCeremony.CEREMONY_CAPACITY;
    if (!members.includes(ctx.user.id) && members.length < cap) {
      members.push(ctx.user.id);
      room.memberUserIds = members;
      if (!rooms.has(code) && db.rooms?.[code]) {
        db.rooms[code].memberUserIds = members;
      }
      if (m) {
        m.ceremonyMemberIds = [...members];
      }
      rememberJoinedLobby(ctx.user, code, room);
      persistRooms();
    }
  }
  scheduleSave();
  return res.json({
    ok: true,
    roomCode: inv.roomCode,
    lobbyName: inv.lobbyName,
    isWeddingCeremony: Boolean(room?.isWeddingCeremony),
  });
});

app.post("/v1/me/lobby-invites/decline", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const inviteId = String(req.body?.inviteId || "").trim();
  const invites = ensureLobbyInvites(ctx.user);
  const before = invites.length;
  ctx.user.lobbyInvites = invites.filter((i) => i.id !== inviteId);
  if (ctx.user.lobbyInvites.length !== before) scheduleSave();
  return res.json({ ok: true });
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
    // Einseitigkeit heilen: wenn Gegenseite fehlt → bei uns entfernen
    // (entfernen ist die korrekte Lesart, wenn der andere uns rausgeworfen hat)
    if (!theirs.list.includes(ctx.user.id)) {
      mine.list = mine.list.filter((id) => id !== toId);
      scheduleSave();
      return res.json({ ok: true, friendStatus: "none", healed: "dropped_asymmetric" });
    }
    return res.json({ ok: true, friendStatus: "friends", already: true });
  }
  // Gegenanfrage → sofort Freunde
  if (mine.incoming.includes(toId)) {
    mine.incoming = mine.incoming.filter((id) => id !== toId);
    theirs.outgoing = theirs.outgoing.filter((id) => id !== ctx.user.id);
    if (!mine.list.includes(toId)) mine.list.push(toId);
    if (!theirs.list.includes(ctx.user.id)) theirs.list.push(ctx.user.id);
    scheduleSave();
    accountPush.emitUserEvent(db, toId, "friend_accepted", {
      fromUserId: ctx.user.id,
      nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    });
    return res.json({ ok: true, friendStatus: "friends" });
  }
  if (mine.outgoing.includes(toId)) {
    return res.json({ ok: true, friendStatus: "outgoing", already: true });
  }
  mine.outgoing.push(toId);
  if (!theirs.incoming.includes(ctx.user.id)) theirs.incoming.push(ctx.user.id);
  trackAch(ctx.user, "friend_requests_sent", 1);
  scheduleSave();
  accountPush.emitUserEvent(db, toId, "friend_request", {
    fromUserId: ctx.user.id,
    nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
  });
  return res.json({ ok: true, friendStatus: "outgoing" });
});

app.post("/v1/me/friends/claim-level-coins", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const pending = pendingFriendshipLevelCoins(ctx.user);
  if (pending <= 0) {
    return res.json({
      ok: true,
      claimed: 0,
      pendingFriendshipCoins: 0,
      user: publicUser(ctx.user),
    });
  }
  const claimed = claimFriendshipLevelCoins(ctx.user);
  scheduleSave();
  return res.json({
    ok: true,
    claimed,
    pendingFriendshipCoins: pendingFriendshipLevelCoins(ctx.user),
    user: publicUser(ctx.user),
  });
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
  accountPush.emitUserEvent(db, fromId, "friend_accepted", {
    fromUserId: ctx.user.id,
    nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
  });
  return res.json({ ok: true, friend: friendPublicCard(other, ctx.user) });
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
  const bond = marriage.findMarriageBetween(db, ctx.user.id, otherId);
  if (bond && marriage.isBusyStatus(bond.status) && bond.status !== "proposed") {
    return res.status(400).json({
      error: "married_or_engaged",
      message:
        bond.status === "married"
          ? "Erst scheiden, dann Freundschaft beenden."
          : "Während Verlobung/Hochzeit kann die Freundschaft nicht beendet werden.",
    });
  }
  if (bond && bond.status === "proposed") {
    endMarriageRecord(bond, "cancel");
  }
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
    marriage.resetLevelBoth(ctx.user, other);
  }
  scheduleSave();
  if (otherId) {
    accountPush.emitUserEvent(db, otherId, "friend_removed", {
      fromUserId: ctx.user.id,
      nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    });
  }
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
    friends: sortFriendsSpouseFirst(
      next
        .map((id) => friendPublicCard(getDb().users?.[id], ctx.user))
        .filter(Boolean)
    ),
  });
});

/**
 * Begleiter kraulen — nur Freunde, 1× pro Freund / Tag.
 * Beide erhalten Coins je nach Freundschaftslevel (1 / 2 ab Lv25 / 3 ab Lv50 / 5 ab Lv100).
 * Freundschaftslevel steigt nicht mehr durch Kraulen (nur gemeinsame Leinwand).
 */
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
  const day = todayKey();
  // Empfänger: max. 25 erfolgreiche Kraule / Tag (Farming-Deckel)
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
  const level = marriage.getLevel(ctx.user, toId);
  const amount = marriage.kraulCoinAmount(level);
  applyLedger(toId, amount, "pet_kraul_recv", ctx.user.id);
  applyLedger(ctx.user.id, amount, "pet_kraul_give", toId);
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
    fromCoins: ctx.user.coins || 0,
    amount,
    canPetKraul: false,
    friendshipLevel: level,
    friendshipLevelBumped: false,
    user: publicUser(ctx.user),
  });
});

/**
 * Heiratsantrag — kostenlos ab Freundschaftslevel 100,
 * darunter mit Coins (unlockWithCoins). Nach Scheidung 7-Tage-Cooldown
 * (eigene mit Coins überspringbar).
 */
app.post("/v1/users/:userId/marry/propose", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const toId = String(req.params.userId || "").trim();
  if (!toId || toId === ctx.user.id) {
    return res.status(400).json({ error: "invalid_user" });
  }
  const db = getDb();
  const target = db.users?.[toId];
  if (!target) return res.status(404).json({ error: "not_found" });
  if (friendRelation(ctx.user, toId) !== "friends") {
    return res.status(403).json({ error: "friends_only", message: "Nur Freunde heiraten." });
  }
  if (marriage.findMarriageForUser(db, ctx.user.id)) {
    return res.status(400).json({
      error: "already_busy",
      message: "Du bist bereits verlobt oder verheiratet.",
    });
  }
  if (marriage.findMarriageForUser(db, toId)) {
    return res.status(400).json({
      error: "partner_busy",
      message: "Diese Person ist bereits verlobt oder verheiratet.",
    });
  }
  const theirCd = marriage.cooldownRemainingMs(target);
  if (theirCd > 0) {
    return res.status(400).json({
      error: "partner_cooldown",
      message: `Diese Person hat noch Scheidungs-Wartezeit (${marriage.formatRemaining(theirCd)}).`,
      remainingMs: theirCd,
    });
  }
  const level = marriage.getLevel(ctx.user, toId);
  const unlockCost = marriage.proposeUnlockCost(level);
  const myCd = marriage.cooldownRemainingMs(ctx.user);
  const cooldownCost = marriage.skipWaitCost(myCd, marriage.DIVORCE_COOLDOWN_MS);
  const payUnlock = Boolean(req.body?.unlockWithCoins ?? req.body?.pay);
  let charged = 0;
  if (unlockCost > 0 && !payUnlock) {
    return res.status(400).json({
      error: "level_low",
      message: `Freundschaftslevel 100 oder ${unlockCost} Coins nötig.`,
      proposeUnlockCost: unlockCost,
      friendshipLevel: level,
    });
  }
  const total = (unlockCost > 0 && payUnlock ? unlockCost : 0) + cooldownCost;
  if (total > 0) {
    ensureCoinBuckets(ctx.user);
    if (!requireCoins(ctx, total, res)) return;
    if (!applyLedger(ctx.user.id, -total, "marriage_propose_fees", `${unlockCost}:${cooldownCost}`)) {
      return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
    }
    trackAch(ctx.user, "coins_spent", total);
    charged = total;
    if (cooldownCost > 0) marriage.clearDivorceCooldown(ctx.user);
  }
  const key = marriage.pairKey(ctx.user.id, toId);
  const all = marriage.ensureMarriages(db);
  all[key] = {
    id: key,
    a: ctx.user.id < toId ? ctx.user.id : toId,
    b: ctx.user.id < toId ? toId : ctx.user.id,
    status: "proposed",
    proposedBy: ctx.user.id,
    proposedAt: Date.now(),
    guestbook: [],
  };
  trackAch(ctx.user, "marriage_proposals", 1);
  scheduleSave();
  accountPush.emitUserEvent(db, toId, "marriage_proposal", {
    fromUserId: ctx.user.id,
    nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    status: "proposed",
  });
  return res.json({
    ok: true,
    charged,
    unlockCost: unlockCost > 0 && payUnlock ? unlockCost : 0,
    cooldownCost,
    marriage: publicMarriageView(all[key], ctx.user.id),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/me/marriage/accept", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const fromId = String(req.body?.userId || "").trim();
  if (!fromId) return res.status(400).json({ error: "invalid_user" });
  const db = getDb();
  const m = marriage.findMarriageBetween(db, ctx.user.id, fromId);
  if (!m || m.status !== "proposed" || m.proposedBy !== fromId) {
    return res.status(400).json({ error: "no_proposal", message: "Keine offene Anfrage." });
  }
  startEngagement(m);
  trackAch(ctx.user, "engagements", 1);
  const proposer = db.users?.[fromId];
  if (proposer) trackAch(proposer, "engagements", 1);
  scheduleSave();
  accountPush.emitUserEvent(db, fromId, "marriage_update", {
    fromUserId: ctx.user.id,
    nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    status: m.status || "engaged",
    message: `${String(ctx.user.nickname || "Jemand").slice(0, 18)} hat den Antrag angenommen`,
  });
  return res.json({
    ok: true,
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.post("/v1/me/marriage/decline", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const fromId = String(req.body?.userId || "").trim();
  if (!fromId) return res.status(400).json({ error: "invalid_user" });
  const db = getDb();
  const m = marriage.findMarriageBetween(db, ctx.user.id, fromId);
  if (!m || m.status !== "proposed") {
    return res.status(400).json({ error: "no_proposal" });
  }
  if (m.proposedBy !== fromId && m.proposedBy !== ctx.user.id) {
    return res.status(403).json({ error: "forbidden" });
  }
  const notifyId =
    m.proposedBy === ctx.user.id ? fromId : m.proposedBy || fromId;
  const withdrew = m.proposedBy === ctx.user.id;
  endMarriageRecord(m, "cancel");
  scheduleSave();
  if (notifyId && notifyId !== ctx.user.id) {
    const nick = String(ctx.user.nickname || "Jemand").slice(0, 18);
    accountPush.emitUserEvent(db, notifyId, "marriage_update", {
      fromUserId: ctx.user.id,
      nickname: nick,
      status: withdrew ? "cancelled" : "declined",
      message: withdrew
        ? `${nick} hat den Antrag zurückgezogen`
        : `${nick} hat den Antrag abgelehnt`,
    });
  }
  return res.json({ ok: true });
});

app.post("/v1/me/marriage/divorce", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const confirm = String(req.body?.confirm || "").trim().toLowerCase();
  if (confirm !== "scheiden") {
    return res.status(400).json({
      error: "confirm_required",
      message: 'Bitte „scheiden“ zur Bestätigung eingeben.',
    });
  }
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "married") {
    return res.status(400).json({
      error: "not_married",
      message: "Du bist nicht verheiratet.",
    });
  }
  const partnerId = marriage.partnerIdOf(m, ctx.user.id);
  endMarriageRecord(m, "divorce");
  scheduleSave();
  if (partnerId) {
    accountPush.emitUserEvent(db, partnerId, "marriage_update", {
      fromUserId: ctx.user.id,
      nickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
      status: "divorced",
      message: `${String(ctx.user.nickname || "Jemand").slice(0, 18)} hat die Ehe beendet`,
    });
  }
  return res.json({ ok: true });
});

/** Scheidungs-Cooldown (7 Tage) mit Coins überspringen. */
app.post("/v1/me/marriage/skip-cooldown", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const rem = marriage.cooldownRemainingMs(ctx.user);
  if (rem <= 0) {
    marriage.clearDivorceCooldown(ctx.user);
    scheduleSave();
    return res.json({
      ok: true,
      free: true,
      user: publicUser(ctx.user),
      marriageCooldownRemainingMs: 0,
    });
  }
  const cost = marriage.skipWaitCost(rem, marriage.DIVORCE_COOLDOWN_MS);
  ensureCoinBuckets(ctx.user);
  if (!requireCoins(ctx, cost, res)) return;
  if (!applyLedger(ctx.user.id, -cost, "marriage_skip_cooldown", String(rem))) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  trackAch(ctx.user, "coins_spent", cost);
  marriage.clearDivorceCooldown(ctx.user);
  scheduleSave();
  return res.json({
    ok: true,
    cost,
    user: publicUser(ctx.user),
    marriageCooldownRemainingMs: 0,
  });
});

/**
 * Einmal gratis: Verlobungs-Wartezeit überspringen → Hochzeitsbild-Lobby öffnen.
 * Kein Coin-Skip in der Verlobungsphase.
 */
app.post("/v1/me/marriage/open-wedding-lobby", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  // Partner hat schon geöffnet → kein Fehler, aktuelle Phase zurückgeben
  if (
    m &&
    (m.status === "wedding" ||
      m.status === "ceremony_pending" ||
      m.status === "ceremony_scheduled" ||
      m.status === "married")
  ) {
    return res.json({
      ok: true,
      free: true,
      alreadyOpen: true,
      phase: m.status,
      marriage: publicMarriageView(m, ctx.user.id),
      user: publicUser(ctx.user),
      weddingLobbyCode: m.weddingLobbyCode || m.ceremonyLobbyCode || null,
      advanced: false,
    });
  }
  if (!m || m.status !== "engaged") {
    return res.status(400).json({
      error: "wrong_phase",
      message: "Nur während der Verlobung verfügbar.",
      marriage: m ? publicMarriageView(m, ctx.user.id) : null,
    });
  }
  if (m.engageFreeSkipUsed === true) {
    // Skip markiert, aber Phase noch engaged (Randfall) → aktuellen Stand liefern
    return res.json({
      ok: true,
      free: true,
      alreadyOpen: true,
      phase: m.status,
      marriage: publicMarriageView(m, ctx.user.id),
      user: publicUser(ctx.user),
      weddingLobbyCode: m.weddingLobbyCode || null,
      advanced: false,
    });
  }
  const advanced = advanceMarriageNextStep(m, { freeEngageSkip: true });
  if (!advanced.ok) {
    return res.status(400).json({
      error: advanced.error || "failed",
      message: advanced.message || "Öffnen fehlgeschlagen.",
      marriage: publicMarriageView(m, ctx.user.id),
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    free: true,
    phase: "engage",
    marriage: publicMarriageView(m, ctx.user.id),
    user: publicUser(ctx.user),
    weddingLobbyCode: m.weddingLobbyCode || null,
    advanced: true,
  });
});

/** Wartezeit der Hochzeitsbild-Phase mit Coins überspringen (erst nach je 10 Strichen). */
app.post("/v1/me/marriage/skip-wait", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m) {
    return res.status(400).json({ error: "no_marriage", message: "Keine Verlobung/Hochzeit." });
  }
  if (m.status === "engaged") {
    return res.status(400).json({
      error: "use_free_open",
      message: "Verlobung: einmal gratis „Hochzeits-Lobby öffnen“ — kein Coin-Skip.",
      marriage: publicMarriageView(m, ctx.user.id),
    });
  }
  const pub = publicMarriageView(m, ctx.user.id);
  let cost = 0;
  let phase = "";
  if (m.status === "wedding") {
    cost = Number(pub?.weddingSkipCost) || 0;
    phase = "wedding";
    if (!pub?.weddingStrokesReady) {
      return res.status(400).json({
        error: "wedding_strokes",
        message: `Beide Partner brauchen je ${marriage.WEDDING_MIN_STROKES} Striche auf der Hochzeitsleinwand — mit oder ohne Coins.`,
        marriage: pub,
      });
    }
  } else {
    return res.status(400).json({
      error: "wrong_phase",
      message: "Gerade keine Wartezeit zum Überspringen.",
    });
  }
  if (cost <= 0) {
    const advanced = advanceMarriageNextStep(m);
    if (!advanced.ok) {
      return res.status(400).json({
        error: advanced.error || "failed",
        message: advanced.message || "Fortsetzen fehlgeschlagen.",
        marriage: publicMarriageView(m, ctx.user.id),
      });
    }
    scheduleSave();
    return res.json({
      ok: true,
      free: true,
      phase,
      marriage: publicMarriageView(m, ctx.user.id),
      user: publicUser(ctx.user),
      advanced: true,
    });
  }
  ensureCoinBuckets(ctx.user);
  if (!requireCoins(ctx, cost, res)) return;
  if (!applyLedger(ctx.user.id, -cost, "marriage_skip_wait", phase)) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  trackAch(ctx.user, "coins_spent", cost);
  const advanced = advanceMarriageNextStep(m);
  if (!advanced.ok) {
    applyLedger(ctx.user.id, cost, "marriage_skip_wait_refund", phase);
    return res.status(400).json({
      error: advanced.error || "failed",
      message: advanced.message || "Überspringen fehlgeschlagen.",
      marriage: publicMarriageView(m, ctx.user.id),
      user: publicUser(ctx.user),
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    phase,
    cost,
    marriage: publicMarriageView(m, ctx.user.id),
    user: publicUser(ctx.user),
    advanced: true,
  });
});

/** Zeremonie-Status + Anwesenheit (Brautpaar / Gathering). */
app.get("/v1/me/marriage/ceremony", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  // Brautpaar direkt; Gäste über ceremonyMemberIds / Lobby-Mitgliedschaft
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m) {
    return res.status(404).json({ error: "no_marriage" });
  }
  const tick = tickCeremonyRitual(m, db);
  if (tick?.dissolved) {
    scheduleSave();
    return res.status(410).json({
      error: "ceremony_aborted",
      message: tick.liveWindowExpired
        ? "Die Trauungszeit ist abgelaufen — Coins zurück, kein Ja-Wort."
        : "Die Trauung wurde abgebrochen.",
    });
  }
  if (tick?.changed || tick?.receptionStarted || tick?.receptionOver) {
    scheduleSave();
  }
  const pubM = publicMarriageView(m, ctx.user.id);
  const cer = weddingCeremony.publicCeremony(m, ctx.user.id, db.users);
  return res.json({
    ok: true,
    marriage: pubM,
    ceremony: cer,
    // Admin-Raum-Editor → live im Trausaal (gebuchter Layout)
    roomLayout: ceremonyLayoutOf(m, db),
  });
});

app.post("/v1/me/marriage/ceremony/presence", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (
    !m ||
    (m.status !== "ceremony_pending" &&
      m.status !== "ceremony_scheduled" &&
      m.status !== "married")
  ) {
    return res.status(400).json({ error: "wrong_phase", message: "Keine offene Zeremonie." });
  }
  const bucket = String(req.body?.bucket || "presence") === "gathering" ? "gathering" : "presence";
  if (bucket === "presence" && !weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  weddingCeremony.touchPresence(m, ctx.user.id, bucket);
  if (m.status === "ceremony_pending") {
    const cPend = weddingCeremony.ensureCeremony(m);
    const bookStep = String(cPend.booking?.step || "none");
    // Booking-Phase nicht mit „presence“ überschreiben
    if (bookStep === "none" || bookStep === "done") {
      cPend.phase = "presence";
    }
  }
  if (bucket === "gathering") {
    const c = weddingCeremony.ensureCeremony(m);
    // Ohne phase=gathering/altar schlagen move/sit mit API-Fehler (400) fehl
    if (
      !c.phase ||
      c.phase === "none" ||
      c.phase === "presence" ||
      c.phase === "scheduled"
    ) {
      c.phase = "gathering";
    }
    const layout = ceremonyLayoutOf(m, db);
    roomLayouts.ensureSpawnPosition(layout, c.positions, ctx.user.id);
  }
  scheduleSave();
  if (bucket === "presence") {
    const n = weddingCeremony.countCouplePresence(m, "presence");
    emitMarriageLiveUpdate(
      m,
      m.status,
      n >= 2 ? "Beide Brautleute sind da" : "Anwesenheit aktualisiert",
      ctx.user.id
    );
  }
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
    roomLayout: ceremonyLayoutOf(m, db),
  });
});

/** Brautpaar verlässt „Hochzeit öffnen“ → Partner sieht 1/2 sofort */
app.post("/v1/me/marriage/ceremony/presence/leave", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m) {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  const cleared = weddingCeremony.clearPresence(m, ctx.user.id, "presence");
  if (cleared) scheduleSave();
  emitMarriageLiveUpdate(m, m.status, "Partner hat die Anzeige verlassen", ctx.user.id);
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.post("/v1/me/marriage/ceremony/start-confirm", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_pending") {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  weddingCeremony.touchPresence(m, ctx.user.id, "presence");
  const c = weddingCeremony.ensureCeremony(m);
  c.startConfirm[ctx.user.id] = true;
  scheduleSave();
  emitMarriageLiveUpdate(m, m.status, "Zur Hochzeit starten", ctx.user.id);
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

function ceremonyScheduleGate(m, userId, db) {
  if (!m || m.status !== "ceremony_pending") {
    return { ok: false, status: 400, body: { error: "wrong_phase", message: "Zeremonie noch nicht bereit." } };
  }
  if (!weddingCeremony.isCouple(m, userId)) {
    return { ok: false, status: 403, body: { error: "couple_only" } };
  }
  const c = weddingCeremony.ensureCeremony(m);
  if (!c.startConfirm[m.a] || !c.startConfirm[m.b]) {
    return {
      ok: false,
      status: 400,
      body: {
        error: "need_both",
        message: "Beide müssen „Zur Hochzeit starten“ tippen.",
        ceremony: weddingCeremony.publicCeremony(m, userId, db.users),
      },
    };
  }
  if (weddingCeremony.countCouplePresence(m, "presence") < 2) {
    return {
      ok: false,
      status: 400,
      body: {
        error: "need_presence",
        message: "Beide Partner müssen anwesend sein.",
        ceremony: weddingCeremony.publicCeremony(m, userId, db.users),
      },
    };
  }
  if (m.ceremonyLobbyCode) {
    return {
      ok: false,
      status: 400,
      body: {
        error: "already_scheduled",
        ceremonyLobbyCode: m.ceremonyLobbyCode,
        ceremony: weddingCeremony.publicCeremony(m, userId, db.users),
      },
    };
  }
  return { ok: true };
}

function pushCeremonyScheduledNotices(m, db) {
  if (!m) return;
  const at = Number(m.ceremonyAt) || 0;
  const code = String(m.ceremonyLobbyCode || "").trim().toUpperCase();
  if (!code) return;
  const text =
    "Hochzeit-Lobby erstellt — bis zum Termin Gäste einladen (Lobby im Home).";
  for (const uid of [m.a, m.b]) {
    const u = db.users?.[uid];
    if (!u) continue;
    if (!Array.isArray(u.socialNotices)) u.socialNotices = [];
    u.socialNotices = u.socialNotices.filter(
      (n) => !(n?.type === "ceremony_scheduled" && String(n.ceremonyLobbyCode || "").toUpperCase() === code)
    );
    u.socialNotices.push({
      id: newId(),
      type: "ceremony_scheduled",
      ceremonyAt: at,
      ceremonyLobbyCode: code,
      text,
      createdAt: Date.now(),
    });
    if (u.socialNotices.length > 30) {
      u.socialNotices = u.socialNotices.slice(-30);
    }
  }
}

function listSocialNoticesFor(user) {
  if (!Array.isArray(user?.socialNotices)) return [];
  const now = Date.now();
  const keepTypes = new Set(["ceremony_scheduled", "wedding_left", "wedding_remind"]);
  return user.socialNotices
    .filter((n) => n && keepTypes.has(n.type) && Number(n.createdAt) > now - 30 * 24 * 60 * 60 * 1000)
    .slice(-20)
    .map((n) => ({
      id: n.id,
      type: n.type,
      text: String(n.text || "").slice(0, 160),
      fromNickname: n.fromNickname || null,
      ceremonyAt: Number(n.ceremonyAt) || 0,
      ceremonyLobbyCode: n.ceremonyLobbyCode || null,
      createdAt: Number(n.createdAt) || 0,
    }));
}

/** Termin festlegen → Buchungsflow (noch keine Lobby). */
function beginCeremonyBooking(m, userId, ceremonyAt, db) {
  const validated = weddingCeremony.validateCeremonyAt(ceremonyAt);
  if (!validated.ok) return { ok: false, status: 400, body: validated };
  if (m.ceremonyLobbyCode) {
    return {
      ok: false,
      status: 400,
      body: {
        error: "already_scheduled",
        ceremonyLobbyCode: m.ceremonyLobbyCode,
        ceremony: weddingCeremony.publicCeremony(m, userId, db.users),
      },
    };
  }
  weddingCeremony.booking.beginBooking(m, validated.ceremonyAt);
  scheduleSave();
  return {
    ok: true,
    body: {
      ok: true,
      bookingStarted: true,
      ceremonyLobbyCode: null,
      marriage: publicMarriageView(m, userId),
      ceremony: weddingCeremony.publicCeremony(m, userId, db.users),
    },
  };
}

function finalizeCeremonySchedule(m, userId, ceremonyAt, db, opts = {}) {
  const validated = weddingCeremony.validateCeremonyAt(
    ceremonyAt || m.ceremonyAt || opts.ceremonyAt
  );
  if (!validated.ok) return { ok: false, status: 400, body: validated };
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (!a || !b) {
    return { ok: false, status: 400, body: { error: "partner_missing" } };
  }
  const code = createWeddingCeremonyLobby(a, b, m, validated.ceremonyAt, opts);
  weddingCeremony.booking.markBookingDone(m);
  const c = weddingCeremony.ensureCeremony(m);
  c.timeProposals = {};
  pushCeremonyScheduledNotices(m, db);
  scheduleSave();
  const room = rooms.get(code);
  if (room) {
    Promise.resolve()
      .then(() =>
        weddingInviteCard.ensureWeddingInviteCardFile(DATA_DIR, {
          code,
          coupleNicknames: room.coupleNicknames,
          hostNickname: room.hostNickname || a.nickname || "Host",
          ceremonyAt: Number(room.ceremonyAt) || validated.ceremonyAt,
        })
      )
      .catch((e) => console.error("wedding card warm", e?.message || e));
  }
  return {
    ok: true,
    body: {
      ok: true,
      ceremonyLobbyCode: code,
      marriage: publicMarriageView(m, userId),
      ceremony: weddingCeremony.publicCeremony(m, userId, db.users),
      socialNotices: listSocialNoticesFor(db.users?.[userId]),
    },
  };
}

app.post("/v1/me/marriage/ceremony/schedule", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  const gate = ceremonyScheduleGate(m, ctx.user.id, db);
  if (!gate.ok) return res.status(gate.status).json(gate.body);
  const done = beginCeremonyBooking(m, ctx.user.id, req.body?.ceremonyAt, db);
  if (!done.ok) return res.status(done.status).json(done.body);
  return res.json(done.body);
});

app.post("/v1/me/marriage/ceremony/propose", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  const gate = ceremonyScheduleGate(m, ctx.user.id, db);
  if (!gate.ok) return res.status(gate.status).json(gate.body);
  weddingCeremony.touchPresence(m, ctx.user.id, "presence");
  const result = weddingCeremony.proposeTime(m, ctx.user.id, req.body?.ceremonyAt);
  if (!result.ok) return res.status(400).json(result);
  scheduleSave();
  emitMarriageLiveUpdate(m, m.status, "Neuer Terminvorschlag", ctx.user.id);
  return res.json({
    ok: true,
    ceremonyAt: result.ceremonyAt,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.post("/v1/me/marriage/ceremony/propose/withdraw", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_pending") {
    return res.status(400).json({ error: "wrong_phase", message: "Zeremonie noch nicht bereit." });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  weddingCeremony.touchPresence(m, ctx.user.id, "presence");
  const result = weddingCeremony.withdrawTime(m, ctx.user.id, req.body?.ceremonyAt);
  if (!result.ok) return res.status(400).json(result);
  scheduleSave();
  emitMarriageLiveUpdate(m, m.status, "Terminvorschlag zurückgezogen", ctx.user.id);
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.post("/v1/me/marriage/ceremony/propose/accept", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  const gate = ceremonyScheduleGate(m, ctx.user.id, db);
  if (!gate.ok) return res.status(gate.status).json(gate.body);
  const at = Math.floor(Number(req.body?.ceremonyAt) || 0);
  if (!weddingCeremony.canAcceptProposal(m, ctx.user.id, at)) {
    return res.status(400).json({
      error: "not_proposed",
      message: "Zeit muss vom Partner vorgeschlagen sein (oder Treffer von beiden).",
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    });
  }
  weddingCeremony.touchPresence(m, ctx.user.id, "presence");
  const done = beginCeremonyBooking(m, ctx.user.id, at, db);
  if (!done.ok) return res.status(done.status).json(done.body);
  emitMarriageLiveUpdate(m, m.status, "Termin gewählt — Buchung starten", ctx.user.id);
  return res.json(done.body);
});

app.post("/v1/me/marriage/ceremony/booking/money-trees", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_pending") {
    return res.status(400).json({ error: "wrong_phase", message: "Keine offene Buchung." });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  const result = weddingCeremony.booking.voteMoneyTrees(
    m,
    ctx.user.id,
    req.body?.choice
  );
  if (!result.ok) return res.status(400).json(result);
  scheduleSave();
  emitMarriageLiveUpdate(
    m,
    m.status,
    result.advanced ? "Geldbäume entschieden — Raum wählen" : "Geldbäume-Stimme",
    ctx.user.id
  );
  return res.json({
    ok: true,
    advanced: Boolean(result.advanced),
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.post("/v1/me/marriage/ceremony/booking/room", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_pending") {
    return res.status(400).json({ error: "wrong_phase", message: "Keine offene Buchung." });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  const result = weddingCeremony.booking.voteRoom(m, ctx.user.id, req.body?.roomId);
  if (!result.ok) return res.status(400).json(result);
  scheduleSave();
  emitMarriageLiveUpdate(
    m,
    m.status,
    result.advanced ? "Raum gewählt — Bestätigen" : "Raum-Stimme",
    ctx.user.id
  );
  return res.json({
    ok: true,
    advanced: Boolean(result.advanced),
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.post("/v1/me/marriage/ceremony/booking/confirm", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_pending") {
    return res.status(400).json({ error: "wrong_phase", message: "Keine offene Buchung." });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  if (m.ceremonyLobbyCode) {
    return res.status(400).json({
      error: "already_scheduled",
      ceremonyLobbyCode: m.ceremonyLobbyCode,
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    });
  }
  const bookingState = weddingCeremony.booking.ensureBooking(m);
  if (bookingState.charged || bookingState.charging) {
    return res.status(400).json({
      error: bookingState.charged ? "already_scheduled" : "charging",
      message: bookingState.charged
        ? "Hochzeit ist bereits gebucht."
        : "Buchung läuft gerade — einen Moment.",
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
      marriage: publicMarriageView(m, ctx.user.id),
    });
  }
  const want = req.body?.confirm !== false;
  const result = weddingCeremony.booking.voteConfirm(m, ctx.user.id, want);
  if (!result.ok) return res.status(400).json(result);

  if (!result.ready) {
    scheduleSave();
    emitMarriageLiveUpdate(m, m.status, "Hochzeit buchen — warte auf Partner", ctx.user.id);
    return res.json({
      ok: true,
      ready: false,
      billPerPerson: result.billPerPerson,
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
      marriage: publicMarriageView(m, ctx.user.id),
      user: publicUser(ctx.user),
    });
  }

  // Atomar gegen parallele Confirms beider Partner
  bookingState.charging = true;

  const bill = Number(result.billPerPerson) || 0;
  const a = db.users?.[m.a];
  const b = db.users?.[m.b];
  if (!a || !b) {
    bookingState.charging = false;
    return res.status(400).json({ error: "partner_missing" });
  }
  ensureCoinBuckets(a);
  ensureCoinBuckets(b);
  if (bill > 0) {
    const coinsA = Number(a.coins) || 0;
    const coinsB = Number(b.coins) || 0;
    if (coinsA < bill || coinsB < bill) {
      // Confirm zurücksetzen — beide müssen erneut tippen nach Aufladen
      bookingState.confirm = {};
      bookingState.charging = false;
      scheduleSave();
      const shortUser =
        coinsA < bill && coinsB < bill
          ? "both"
          : coinsA < bill
            ? m.a
            : m.b;
      return res.status(402).json({
        error: "no_coins",
        message: "Nicht genug Coins für die Hochzeitsbuchung.",
        billPerPerson: bill,
        shortUserId: shortUser,
        myCoins: Number(ctx.user.coins) || 0,
        needCoins: bill,
        ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
        marriage: publicMarriageView(m, ctx.user.id),
        user: publicUser(ctx.user),
      });
    }
    if (!applyLedger(m.a, -bill, "wedding_booking", result.roomId)) {
      bookingState.charging = false;
      return res.status(500).json({ error: "ledger_failed" });
    }
    if (!applyLedger(m.b, -bill, "wedding_booking", result.roomId)) {
      applyLedger(m.a, bill, "wedding_booking_refund", result.roomId);
      bookingState.charging = false;
      return res.status(500).json({ error: "ledger_failed" });
    }
    trackAch(a, "coins_spent", bill);
    trackAch(b, "coins_spent", bill);
  }
  bookingState.chargedBillPerPerson = bill;
  bookingState.chargedRoomId = result.roomId || null;
  bookingState.refunded = false;

  const done = finalizeCeremonySchedule(m, ctx.user.id, result.ceremonyAt, db, {
    layoutId: result.roomId,
    capacity: result.capacity,
    moneyTreesEnabled: result.moneyTrees,
  });
  if (!done.ok) {
    bookingState.charging = false;
    if (bill > 0) {
      applyLedger(m.a, bill, "wedding_booking_refund", result.roomId);
      applyLedger(m.b, bill, "wedding_booking_refund", result.roomId);
    }
    return res.status(done.status).json(done.body);
  }
  bookingState.charging = false;
  return res.json({
    ...done.body,
    charged: bill,
    billPerPerson: bill,
    user: publicUser(db.users?.[ctx.user.id]),
  });
});

app.post("/v1/me/marriage/ceremony/buy-slot", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || (m.status !== "ceremony_scheduled" && m.status !== "married")) {
    return res.status(400).json({ error: "wrong_phase", message: "Keine Hochzeits-Lobby." });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  const code = String(m.ceremonyLobbyCode || "").trim().toUpperCase();
  if (!code) {
    return res.status(400).json({ error: "no_lobby", message: "Keine Lobby." });
  }
  const room = rooms.get(code) || db.rooms?.[code];
  if (!room?.isWeddingCeremony) {
    return res.status(400).json({ error: "no_lobby", message: "Keine Hochzeits-Lobby." });
  }
  const booking = weddingCeremony.booking;
  const cap = Number(room.capacity) || Number(m.ceremonyCapacity) || 8;
  if (cap >= booking.MAX_CAPACITY) {
    return res.status(400).json({
      error: "max_slots",
      message: `Maximal ${booking.MAX_CAPACITY} Plätze (inkl. Brautpaar).`,
    });
  }
  const cost = booking.EXTRA_SLOT_COST;
  ensureCoinBuckets(ctx.user);
  if (!requireCoins(ctx, cost, res)) return;
  if (!applyLedger(ctx.user.id, -cost, "wedding_extra_slot", code)) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  trackAch(ctx.user, "coins_spent", cost);
  room.capacity = cap + 1;
  m.ceremonyCapacity = room.capacity;
  if (db.rooms?.[code]) db.rooms[code].capacity = room.capacity;
  for (const uid of [m.a, m.b]) {
    const u = db.users?.[uid];
    if (u?.hostedRooms?.[code]) u.hostedRooms[code].capacity = room.capacity;
  }
  scheduleSave();
  persistRooms();
  return res.json({
    ok: true,
    cost,
    capacity: room.capacity,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/me/marriage/ceremony/remind", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m) return res.status(404).json({ error: "no_marriage" });
  const partnerId = weddingCeremony.partnerOf(m, ctx.user.id);
  const partner = partnerId ? db.users?.[partnerId] : null;
  const deepLink = `https://reineke.pro/luv/w/${encodeURIComponent(m.id || marriage.pairKey(m.a, m.b))}`;
  const nick = String(ctx.user.nickname || "").trim().slice(0, 18) || "Dein:e Verlobte:r";
  const message =
    `${nick} wartet auf euch — die Hochzeit ist soweit. Jetzt beitreten:\n${deepLink}`;
  // Soft notify an Partner (Badge / Push-Kanal Freunde)
  if (partner) {
    if (!Array.isArray(partner.socialNotices)) partner.socialNotices = [];
    partner.socialNotices.push({
      id: newId(),
      type: "wedding_remind",
      fromUserId: ctx.user.id,
      fromNickname: nick,
      marriageId: m.id || marriage.pairKey(m.a, m.b),
      text: "Die Hochzeit ist soweit — jetzt beitreten",
      createdAt: Date.now(),
    });
    if (partner.socialNotices.length > 30) {
      partner.socialNotices = partner.socialNotices.slice(-30);
    }
  }
  scheduleSave();
  return res.json({
    ok: true,
    shareText: message,
    shareUrl: deepLink,
    imageUrl: "https://reineke.pro/luv/og-wedding-altar.jpg",
  });
});

app.post("/v1/me/marriage/ceremony/kick-absent", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_scheduled") {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  const code = m.ceremonyLobbyCode;
  const room = code ? rooms.get(code) || getDb().rooms?.[code] : null;
  if (!room?.isWeddingCeremony) {
    return res.status(404).json({ error: "no_lobby" });
  }
  const members = Array.isArray(room.memberUserIds) ? [...room.memberUserIds] : [];
  const kept = [];
  for (const uid of members) {
    if (uid === m.a || uid === m.b) {
      kept.push(uid);
      continue;
    }
    if (weddingCeremony.isPresent(m, uid, "gathering")) {
      kept.push(uid);
      continue;
    }
    const u = db.users?.[uid];
    if (u) {
      forgetJoinedLobby(u, code);
      if (u.hostedRooms?.[code]) delete u.hostedRooms[code];
    }
    if (room.sockets) {
      for (const [peerId, sock] of [...room.sockets.entries()]) {
        if (sock.luvUserId === uid) {
          try {
            sock.close(4001, "ceremony_kick");
          } catch {
            /* ignore */
          }
          room.sockets.delete(peerId);
        }
      }
    }
  }
  room.memberUserIds = kept;
  m.ceremonyMemberIds = kept;
  if (getDb().rooms?.[code]) getDb().rooms[code].memberUserIds = kept;
  scheduleSave();
  persistRooms();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

app.post("/v1/me/marriage/ceremony/enter-altar", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_scheduled") {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  if (!weddingCeremony.ceremonyOpenForEntry(m)) {
    return res.status(400).json({
      error: "too_early",
      message: "Erst 10 Minuten vor der Hochzeit.",
    });
  }
  if (!weddingCeremony.computeAllGathered(m)) {
    return res.status(400).json({
      error: "not_all_present",
      message: "Noch nicht alle Gäste anwesend.",
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    });
  }
  const c = weddingCeremony.ensureCeremony(m);
  c.phase = "altar";
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

app.post("/v1/me/marriage/ceremony/move", async (req, res) => {
  try {
    const ctx = requireAuth(req, res);
    if (!ctx) return;
    const allowed = await redisUtil.allow(`cmove:${ctx.user.id}`, 50, 1);
    if (!allowed) {
      return res.status(429).json({
        error: "rate_limited",
        message: "Zu schnell — kurz warten.",
      });
    }
    const db = getDb();
    let m = findMarriageForCeremonyMember(db, ctx.user.id);
    if (!m && req.body?.marriageId) {
      m = marriage.ensureMarriages(db)[String(req.body.marriageId)];
    }
    if (!m || (m.status !== "ceremony_scheduled" && m.status !== "married")) {
      return res.status(400).json({ error: "wrong_phase" });
    }
    const c = weddingCeremony.ensureCeremony(m);
    if (
      c.phase === "none" ||
      c.phase === "presence" ||
      c.phase === "scheduled"
    ) {
      c.phase = "gathering";
    }
    if (
      c.phase !== "altar" &&
      c.phase !== "vows" &&
      c.phase !== "gifts" &&
      c.phase !== "reception" &&
      c.phase !== "gathering"
    ) {
      return res.status(400).json({
        error: "not_in_room",
        message: "Bitte zuerst eintreten.",
      });
    }
    // Nach Sitz-Lock: Sitzende dürfen nicht mehr laufen
    if (c.seated[ctx.user.id] && !weddingCeremony.canStand(m, ctx.user.id)) {
      return res.status(400).json({
        error: "seating_locked",
        message: "Während der Zeremonie bleibt ihr sitzen.",
        ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
      });
    }
    const layout = ceremonyLayoutOf(m, db);
    const prev = c.positions[ctx.user.id] || { x: 0.5, y: 0.86 };
    const clamped = roomLayouts.clampMove(
      layout,
      prev.x,
      prev.y,
      Number(req.body?.x),
      Number(req.body?.y)
    );
    c.positions[ctx.user.id] = { x: clamped.x, y: clamped.y };
    // Sitzt nur, wer im passenden Rechteck steht (nicht mehr: Move = Aufstehen)
    syncWeddingZoneSeating(m, db, ctx.user.id);
    weddingCeremony.touchPresence(m, ctx.user.id, "gathering");
    tickCeremonyRitual(m, db);
    scheduleSave();
    if (m.ceremonyLobbyCode) {
      broadcastLivePos(m.ceremonyLobbyCode, {
        type: "ceremony_pos",
        roomCode: String(m.ceremonyLobbyCode).toUpperCase(),
        userId: ctx.user.id,
        x: clamped.x,
        y: clamped.y,
      });
    }
    return res.json({
      ok: true,
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
      roomLayout: layout,
      blocked: Boolean(clamped.blocked),
    });
  } catch (err) {
    console.error("[ceremony/move]", err?.message || err);
    return res.status(500).json({ error: "server_error" });
  }
});

app.post("/v1/me/marriage/ceremony/sit", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m || (m.status !== "ceremony_scheduled" && m.status !== "married")) {
    return res.status(400).json({ error: "wrong_phase" });
  }
  const c = weddingCeremony.ensureCeremony(m);
  if (
    c.phase === "none" ||
    c.phase === "presence" ||
    c.phase === "scheduled"
  ) {
    c.phase = "gathering";
  }
  if (
    c.phase !== "altar" &&
    c.phase !== "vows" &&
    c.phase !== "gifts" &&
    c.phase !== "reception" &&
    c.phase !== "gathering"
  ) {
    return res.status(400).json({
      error: "not_in_room",
      message: "Bitte zuerst eintreten.",
    });
  }
  // Legacy: Sitzt aus aktueller Position (Rechtecke). seatId optional / wird ignoriert.
  const layout = ceremonyLayoutOf(m, db);
  if (!c.positions[ctx.user.id]) {
    c.positions[ctx.user.id] = {
      x: Number(layout?.spawn?.x) || 0.5,
      y: Number(layout?.spawn?.y) || 0.86,
    };
  }
  syncWeddingZoneSeating(m, db, ctx.user.id);
  if (!c.seated[ctx.user.id]) {
    return res.status(400).json({
      error: "not_in_seat_zone",
      message: weddingCeremony.isCouple(m, ctx.user.id)
        ? "Bitte in den gelben Brautpaar-Bereich gehen."
        : "Bitte in den blauen Gäste-Bereich gehen.",
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
      roomLayout: layout,
    });
  }
  weddingCeremony.touchPresence(m, ctx.user.id, "gathering");
  if (c.phase === "gathering" || c.phase === "none") c.phase = "altar";
  tickCeremonyRitual(m, db);
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    roomLayout: layout,
  });
});

/** Geldbaum tippen — einmalig 1 Coin pro Baum / Gast */
app.post("/v1/me/marriage/ceremony/money-tree", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m || (m.status !== "ceremony_scheduled" && m.status !== "married")) {
    return res.status(400).json({ error: "wrong_phase", message: "Keine offene Zeremonie." });
  }
  const c = weddingCeremony.ensureCeremony(m);
  if (
    c.phase === "none" ||
    c.phase === "presence" ||
    c.phase === "scheduled"
  ) {
    c.phase = "gathering";
  }
  if (weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({
      error: "couple_no_trees",
      message: "Geldbäume sind nur für Gäste — das Brautpaar erntet nicht.",
    });
  }
  if (m.moneyTreesEnabled !== true) {
    return res.status(400).json({
      error: "trees_disabled",
      message: "Für diese Hochzeit wurden keine Geldbäume gebucht.",
    });
  }
  const treeId = String(req.body?.treeId || "").trim().slice(0, 48);
  if (!treeId) {
    return res.status(400).json({ error: "bad_tree", message: "Kein Baum." });
  }
  const layoutId = String(m.ceremonyLayoutId || "wedding").trim() || "wedding";
  const zone =
    roomLayouts.findZone(db, layoutId, treeId) ||
    roomLayouts.findZone(db, "wedding", treeId);
  if (!zone || !roomLayouts.isMoneyTree(zone)) {
    return res.status(400).json({ error: "bad_tree", message: "Das ist kein Geldbaum." });
  }
  const claimKey = `${ctx.user.id}:${treeId}`;
  if (!c.moneyTreesClaimed || typeof c.moneyTreesClaimed !== "object") {
    c.moneyTreesClaimed = {};
  }
  if (c.moneyTreesClaimed[claimKey]) {
    return res.json({
      ok: true,
      already: true,
      coins: 0,
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
      user: publicUser(ctx.user),
      message: "Diesen Baum hast du schon geerntet.",
    });
  }
  if (!applyLedger(ctx.user.id, 1, "wedding_money_tree", treeId)) {
    return res.status(500).json({ error: "ledger_failed", message: "Coin konnte nicht gutgeschrieben werden." });
  }
  c.moneyTreesClaimed[claimKey] = Date.now();
  weddingCeremony.touchPresence(m, ctx.user.id, "gathering");
  scheduleSave();
  return res.json({
    ok: true,
    already: false,
    coins: 1,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    user: publicUser(ctx.user),
    message: "+1 Coin vom Geldbaum!",
  });
});

app.post("/v1/me/marriage/ceremony/react", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m || (m.status !== "ceremony_scheduled" && m.status !== "married")) {
    return res.status(400).json({ error: "wrong_phase" });
  }
  const emoji = String(req.body?.emoji || "").trim().slice(0, 64);
  if (!emoji) return res.status(400).json({ error: "bad_emoji" });
  const c = weddingCeremony.ensureCeremony(m);
  const until = Date.now() + 2000;
  c.reactions[ctx.user.id] = { emoji, until };
  weddingCeremony.touchPresence(m, ctx.user.id, "gathering");
  broadcastCeremonyLobby(m, {
    type: "ceremony_react",
    userId: ctx.user.id,
    emoji,
    until,
  });
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

app.post("/v1/me/marriage/ceremony/vow", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "ceremony_scheduled") {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "couple_only" });
  }
  const c = weddingCeremony.ensureCeremony(m);
  if (c.pastorPhase !== "vows" && c.phase !== "vows") {
    return res.status(400).json({ error: "not_vows" });
  }
  if (c.pastorPhase !== "vows") {
    return res.status(400).json({
      error: "not_vows",
      message: "Bitte wartet auf die Frage des Pastors.",
    });
  }
  const choice = String(req.body?.choice || "").toLowerCase() === "no" ? "no" : "yes";
  let progress = Math.min(1, Math.max(0, Number(req.body?.progress) || 0));
  const prev = c.vows[ctx.user.id];
  // Einmal eingerastet (15s voll) → nicht mehr zurücksetzen oder umschalten
  if (prev && (Number(prev.progress) || 0) >= 1) {
    scheduleSave();
    return res.json({
      ok: true,
      locked: true,
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    });
  }
  // Fortschritt nur nach oben (Finger-Loslassen darf nicht auf 0 zurück)
  if (prev && prev.choice === choice) {
    progress = Math.max(progress, Number(prev.progress) || 0);
  }
  c.vows[ctx.user.id] = { choice, progress, at: Date.now() };
  if (choice === "no" && progress >= 1) {
    const nick = String(ctx.user.nickname || "").trim().slice(0, 18) || "Jemand";
    weddingCeremony.startRejectClosing(m, ctx.user.id, nick);
    scheduleSave();
    return res.json({
      ok: true,
      rejected: true,
      rejectedBy: nick,
      message: `${nick} hat Nein gesagt.`,
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    });
  }
  const vowA = c.vows[m.a];
  const vowB = c.vows[m.b];
  if (
    vowA?.choice === "yes" &&
    vowB?.choice === "yes" &&
    (Number(vowA.progress) || 0) >= 1 &&
    (Number(vowB.progress) || 0) >= 1
  ) {
    // force=false: Ehe nur wenn beide Ja gesagt haben (Guard in finalizeWeddingMarriage)
    finalizeWeddingMarriage(m, { force: false });
    scheduleSave();
    return res.json({
      ok: true,
      married: true,
      marriage: publicMarriageView(m, ctx.user.id),
      ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

app.post("/v1/me/marriage/ceremony/applause", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m || m.status !== "married") {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (weddingCeremony.isCouple(m, ctx.user.id)) {
    return res.status(403).json({ error: "guests_only" });
  }
  const c = weddingCeremony.ensureCeremony(m);
  const x = Math.min(0.95, Math.max(0.05, Number(req.body?.x) || 0.5));
  const y = Math.min(0.95, Math.max(0.05, Number(req.body?.y) || 0.7));
  const now = Date.now();
  const wantExtras = Math.min(12, Math.max(0, Math.floor(Number(req.body?.extras) || 8)));
  const bursts = [{ userId: ctx.user.id, at: now, emoji: "👏", x, y }];
  for (let i = 0; i < wantExtras; i++) {
    bursts.push({
      userId: ctx.user.id,
      at: now + i + 1,
      emoji: "👏",
      x: Math.min(0.92, Math.max(0.08, 0.15 + Math.random() * 0.7)),
      y: Math.min(0.88, Math.max(0.12, 0.15 + Math.random() * 0.55)),
    });
  }
  for (const b of bursts) c.applauseBursts.push(b);
  if (c.applauseBursts.length > 80) {
    c.applauseBursts = c.applauseBursts.slice(-80);
  }
  const reactUntil = now + 1800;
  c.reactions[ctx.user.id] = { emoji: "👏", until: reactUntil };
  broadcastCeremonyLobby(m, {
    type: "ceremony_applause",
    userId: ctx.user.id,
    bursts: bursts.map((b) => ({ x: b.x, y: b.y, at: b.at })),
    reactionUntil: reactUntil,
  });
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

app.post("/v1/me/marriage/ceremony/confetti", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m || (m.status !== "married" && m.status !== "ceremony_scheduled")) {
    return res.status(400).json({ error: "wrong_phase" });
  }
  const c = weddingCeremony.ensureCeremony(m);
  if (!Array.isArray(c.confettiBursts)) c.confettiBursts = [];
  const x = Math.min(0.95, Math.max(0.05, Number(req.body?.x) || 0.5));
  const y = Math.min(0.95, Math.max(0.05, Number(req.body?.y) || 0.7));
  const at = Date.now();
  c.confettiBursts.push({
    userId: ctx.user.id,
    at,
    x,
    y,
  });
  if (c.confettiBursts.length > 40) {
    c.confettiBursts = c.confettiBursts.slice(-40);
  }
  broadcastCeremonyLobby(m, {
    type: "ceremony_confetti",
    userId: ctx.user.id,
    x,
    y,
    at,
  });
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

app.post("/v1/me/marriage/ceremony/mark-gifted", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m || m.status !== "married") {
    return res.status(400).json({ error: "wrong_phase" });
  }
  const c = weddingCeremony.ensureCeremony(m);
  c.giftedBy[ctx.user.id] = true;
  scheduleSave();
  return res.json({
    ok: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

/** Kapelle verlassen — nur Presence weg, Zeremonie bleibt. */
app.post("/v1/me/marriage/ceremony/exit-room", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = findMarriageForCeremonyMember(db, ctx.user.id);
  if (!m) {
    return res.json({ ok: true, left: true });
  }
  weddingCeremony.clearGatheringPresence(m, ctx.user.id);
  // Empfang/Gift-Meta auf Lobby spiegeln — Home zeigt „Noch … · Empfang“
  if (m.status === "married") {
    keepCeremonyLobbyForGifts(m);
    emitMarriageLiveUpdate(m, "married", "Empfang läuft", ctx.user.id);
  }
  scheduleSave();
  return res.json({
    ok: true,
    left: true,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

/** Cold feet: 30s Hold-Slider bestätigt — Zeremonie abbrechen. */
app.post("/v1/me/marriage/ceremony/leave", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || (m.status !== "ceremony_scheduled" && m.status !== "ceremony_pending")) {
    return res.status(400).json({ error: "wrong_phase" });
  }
  if (!weddingCeremony.isCouple(m, ctx.user.id)) {
    // Gast: einfach aus Lobby
    const code = m.ceremonyLobbyCode;
    if (code) {
      forgetJoinedLobby(ctx.user, code);
      const room = rooms.get(code);
      if (room?.memberUserIds) {
        room.memberUserIds = room.memberUserIds.filter((id) => id !== ctx.user.id);
        m.ceremonyMemberIds = [...room.memberUserIds];
      }
      persistRooms();
    }
    scheduleSave();
    return res.json({ ok: true, guestLeft: true });
  }
  const holdMs = Number(req.body?.holdMs) || 0;
  if (holdMs < 28_000) {
    return res.status(400).json({
      error: "hold_required",
      message: "Schieberegler 30 Sekunden halten.",
    });
  }
  abortWeddingCeremony(m, ctx.user.id);
  scheduleSave();
  return res.json({ ok: true, aborted: true });
});

app.post("/v1/me/marriage/ceremony/dismiss-left-notice", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  // Notice kann nach Abbruch noch in socialNotices / lokal liegen
  if (Array.isArray(ctx.user.socialNotices)) {
    ctx.user.socialNotices = ctx.user.socialNotices.filter(
      (n) => n?.type !== "wedding_left" && n?.type !== "wedding_remind"
    );
  }
  scheduleSave();
  return res.json({ ok: true });
});

/**
 * Nachträgliches Hochzeitsbild: Partner bestätigt die aktuelle Leinwand.
 * Beide bestätigt + Snapshot → Bild speichern, Lobby schließen.
 */
app.post("/v1/me/marriage/confirm-wedding-image", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "married") {
    return res.status(400).json({
      error: "not_married",
      message: "Nur für Verheiratete ohne Hochzeitsbild.",
    });
  }
  if (m.weddingImageFile) {
    return res.status(400).json({
      error: "has_image",
      message: "Hochzeitsbild ist bereits gespeichert.",
    });
  }
  ensureWeddingImageRetake(m);
  if (!m.weddingImageRetake || !m.weddingLobbyCode) {
    return res.status(400).json({
      error: "no_retake",
      message: "Keine Hochzeitsleinwand zum Bestätigen.",
    });
  }
  if (!m.weddingConfirm || typeof m.weddingConfirm !== "object") {
    m.weddingConfirm = { [m.a]: false, [m.b]: false };
  }
  const confirm = req.body?.confirm !== false && req.body?.confirm !== "false";
  m.weddingConfirm[ctx.user.id] = Boolean(confirm);
  const state = weddingConfirmState(m, ctx.user.id);
  const code = String(m.weddingLobbyCode || "").toUpperCase();
  const room = rooms.get(code);
  if (room) {
    broadcastRoom(room, {
      type: "wedding_confirm",
      confirms: {
        [m.a]: Boolean(m.weddingConfirm[m.a]),
        [m.b]: Boolean(m.weddingConfirm[m.b]),
      },
      userId: ctx.user.id,
      done: false,
    });
  }
  if (state.weddingConfirmReady) {
    const done = completeWeddingImageRetake(m);
    if (!done.ok) {
      scheduleSave();
      return res.status(400).json({
        error: done.error || "failed",
        message: done.message || "Bild konnte noch nicht gespeichert werden.",
        ...state,
        marriage: publicMarriageView(m, ctx.user.id),
      });
    }
    return res.json({
      ok: true,
      done: true,
      ...weddingConfirmState(m, ctx.user.id),
      marriage: publicMarriageView(m, ctx.user.id),
      message: "Hochzeitsbild gespeichert.",
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    done: false,
    ...state,
    marriage: publicMarriageView(m, ctx.user.id),
    message: confirm
      ? "Gespeichert — warte auf die Bestätigung deines Partners."
      : "Bestätigung zurückgenommen.",
  });
});

app.get("/v1/me/marriage/wedding-image-confirm", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const m = marriage.findMarriageForUser(getDb(), ctx.user.id);
  if (!m || m.status !== "married" || m.weddingImageFile) {
    return res.json({
      ok: true,
      weddingImageRetake: false,
      weddingConfirmMine: false,
      weddingConfirmPartner: false,
      weddingConfirmReady: false,
      marriage: m ? publicMarriageView(m, ctx.user.id) : null,
    });
  }
  ensureWeddingImageRetake(m);
  return res.json({
    ok: true,
    ...weddingConfirmState(m, ctx.user.id),
    weddingLobbyCode: m.weddingLobbyCode || null,
    marriage: publicMarriageView(m, ctx.user.id),
  });
});

app.get("/v1/users/:userId/wedding", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const db = getDb();
  const m = marriage.findMarriageForUser(db, uid);
  if (!m || m.status !== "married") {
    return res.status(404).json({ error: "not_married" });
  }
  const canDelete =
    m.a === ctx.user.id ||
    m.b === ctx.user.id ||
    hasStaffPerm(ctx.user, "reports.act");
  const spouseCard = (id) => {
    const u = db.users?.[id];
    if (!u) {
      return { userId: id, nickname: "Jemand", petEmoji: "🐣" };
    }
    return {
      userId: u.id,
      nickname: String(u.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
      petEmoji: userPetEmoji(u),
    };
  };
  return res.json({
    ok: true,
    marriage: publicMarriageView(m, ctx.user.id),
    couple: {
      a: spouseCard(m.a),
      b: spouseCard(m.b),
    },
    hasImage: Boolean(m.weddingImageFile),
    guestbook: (m.guestbook || []).map((g) => ({
      id: g.id,
      userId: g.userId,
      nickname: g.nickname,
      text: g.text,
      createdAt: g.createdAt,
    })),
    canDeleteComments: canDelete,
  });
});

app.get("/v1/users/:userId/wedding/image", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const m = marriage.findMarriageForUser(getDb(), uid);
  if (!m?.weddingImageFile) return res.status(404).end();
  const filePath = path.join(WEDDING_DIR, path.basename(m.weddingImageFile));
  if (!fs.existsSync(filePath)) return res.status(404).end();
  res.setHeader("Content-Type", "image/png");
  res.setHeader("Cache-Control", "private, max-age=300");
  return fs.createReadStream(filePath).pipe(res);
});

app.post("/v1/users/:userId/wedding/guestbook", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!rateLimit(`gb:${ctx.user.id}`, 10, 60_000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const uid = String(req.params.userId || "").trim();
  const text = String(req.body?.text || "").trim().slice(0, 280);
  if (!text) return res.status(400).json({ error: "empty" });
  const db = getDb();
  const m = marriage.findMarriageForUser(db, uid);
  if (!m || m.status !== "married") {
    return res.status(404).json({ error: "not_married" });
  }
  if (!Array.isArray(m.guestbook)) m.guestbook = [];
  if (m.guestbook.length >= 200) {
    return res.status(400).json({ error: "full", message: "Gästebuch ist voll." });
  }
  const entry = {
    id: newId("gb"),
    userId: ctx.user.id,
    nickname: String(ctx.user.nickname || "Jemand").trim().slice(0, 18),
    text,
    createdAt: Date.now(),
  };
  m.guestbook.push(entry);
  trackAch(ctx.user, "guestbook_writes", 1);
  // Einmalig 5 Coins für Gästebucheintrag
  const c = weddingCeremony.ensureCeremony(m);
  let coinsGranted = 0;
  if (!c.guestbookedBy[ctx.user.id]) {
    c.guestbookedBy[ctx.user.id] = true;
    if (applyLedger(ctx.user.id, 5, "guestbook_entry", m.id || "wedding")) {
      coinsGranted = 5;
    }
  }
  scheduleSave();
  return res.json({
    ok: true,
    entry,
    coinsGranted,
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

/** Inventar-Item in den gemeinsamen Hochzeits-Geschenktopf legen. */
app.post("/v1/users/:userId/wedding/gift", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!rateLimit(`wgift:${ctx.user.id}`, 30, 60_000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const uid = String(req.params.userId || "").trim();
  const kind = String(req.body?.kind || "").trim();
  const itemId = String(req.body?.itemId || "").trim();
  if (!weddingGifts.ALLOWED_KINDS.has(kind) || !itemId) {
    return res.status(400).json({
      error: "bad_item",
      message: "Nur Emoji, Sticker, Pets oder Themes aus dem Inventar.",
    });
  }
  const db = getDb();
  const m = marriage.findMarriageForUser(db, uid);
  if (!m) {
    return res.status(404).json({ error: "not_found", message: "Keine Ehe gefunden." });
  }
  if (!weddingGifts.canGiftToMarriage(m)) {
    return res.status(400).json({
      error: "gift_closed",
      message: "Geschenke sind gerade nicht möglich.",
      marriage: publicMarriageView(m, ctx.user.id),
    });
  }
  weddingGifts.ensureGiftState(m);
  if (m.giftPool.length >= weddingGifts.MAX_POOL) {
    return res.status(400).json({
      error: "pool_full",
      message: "Der Geschenktopf ist voll.",
    });
  }
  if (weddingGifts.giverGiftCount(m, ctx.user.id) >= weddingGifts.MAX_GIFTS_PER_GIVER) {
    return res.status(400).json({
      error: "giver_limit",
      message: `Maximal ${weddingGifts.MAX_GIFTS_PER_GIVER} Geschenke pro Person.`,
    });
  }
  ensureInventory(ctx.user);
  if (!market.takeItemFromUser(ctx.user, ensureInventory, kind, itemId)) {
    return res.status(400).json({
      error: "no_item",
      message: "Item nicht im Inventar (oder gerade ausgerüstet).",
    });
  }
  m.giftPool.push({
    id: newId("gift"),
    fromUserId: ctx.user.id,
    fromNickname: String(ctx.user.nickname || "Jemand").trim().slice(0, 18) || "Jemand",
    kind,
    itemId,
    at: Date.now(),
  });
  const cer = weddingCeremony.ensureCeremony(m);
  cer.giftedBy[ctx.user.id] = true;
  scheduleSave();
  return res.json({
    ok: true,
    giftPoolCount: m.giftPool.length,
    marriage: publicMarriageView(m, ctx.user.id),
    user: publicUser(ctx.user),
    ceremony: weddingCeremony.publicCeremony(m, ctx.user.id, db.users),
  });
});

/** Nach fairem Split: eigene Geschenke ins Inventar holen. */
app.post("/v1/me/marriage/gifts/claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const m = marriage.findMarriageForUser(db, ctx.user.id);
  if (!m || m.status !== "married") {
    return res.status(404).json({ error: "no_marriage" });
  }
  weddingGifts.ensureGiftState(m);
  if (m.giftPhase === "open" && Date.now() >= (Number(m.giftWindowEndsAt) || 0)) {
    weddingGifts.rollGiftPool(m);
    syncCeremonyGiftMeta(m);
  }
  const result = claimWeddingGiftsForUser(m, ctx.user);
  if (!result.ok) {
    return res.status(400).json({
      error: result.error || "failed",
      message: result.message || "Abholen fehlgeschlagen.",
      marriage: publicMarriageView(m, ctx.user.id),
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    already: Boolean(result.already),
    items: result.items || [],
    bothClaimed: Boolean(result.bothClaimed),
    marriage: publicMarriageView(m, ctx.user.id),
    user: publicUser(ctx.user),
  });
});

app.delete("/v1/users/:userId/wedding/guestbook/:entryId", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const entryId = String(req.params.entryId || "").trim();
  const db = getDb();
  const m = marriage.findMarriageForUser(db, uid);
  if (!m) return res.status(404).json({ error: "not_found" });
  const isCouple = m.a === ctx.user.id || m.b === ctx.user.id;
  if (!isCouple && !hasStaffPerm(ctx.user, "reports.act")) {
    return res.status(403).json({ error: "forbidden" });
  }
  m.guestbook = (m.guestbook || []).filter((g) => g.id !== entryId);
  scheduleSave();
  return res.json({ ok: true });
});

app.post("/v1/users/:userId/wedding/guestbook/:entryId/report", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const entryId = String(req.params.entryId || "").trim();
  const reason = String(req.body?.reason || "").trim().slice(0, 400);
  const db = getDb();
  const m = marriage.findMarriageForUser(db, uid);
  const entry = (m?.guestbook || []).find((g) => g.id === entryId);
  if (!entry) return res.status(404).json({ error: "not_found" });
  if (!db.guestbookReports) db.guestbookReports = [];
  db.guestbookReports.push({
    id: newId("gbr"),
    marriageId: m.id,
    entryId,
    entryUserId: entry.userId,
    entryText: entry.text,
    reportedBy: ctx.user.id,
    reason,
    createdAt: Date.now(),
  });
  if (db.guestbookReports.length > 500) {
    db.guestbookReports = db.guestbookReports.slice(-500);
  }
  scheduleSave();
  return res.json({ ok: true });
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
  ensureCoinBuckets(ctx.user);
  // Tips nur aus paidCoins — verhindert Tagescoins → permanente Paid-Coins
  if ((ctx.user.paidCoins || 0) < 1) {
    return res.status(402).json({
      error: "need_paid_coins",
      message: "Tippen geht nur mit echten Coins (nicht mit Tagescoins).",
    });
  }
  ctx.user.paidCoins -= 1;
  syncCoinTotal(ctx.user);
  const tipDb = getDb();
  tipDb.ledger.push({
    id: newId("led"),
    userId: ctx.user.id,
    delta: -1,
    reason: "glass_tip",
    refId: toId,
    at: Date.now(),
    balance: ctx.user.coins,
  });
  if (tipDb.ledger.length > 5000) tipDb.ledger.splice(0, tipDb.ledger.length - 5000);
  if (!applyLedger(toId, 1, "glass_tip_recv", ctx.user.id)) {
    // Rollback Spender
    ctx.user.paidCoins += 1;
    syncCoinTotal(ctx.user);
    return res.status(500).json({ error: "tip_failed" });
  }
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
      error: result.error || "draw_blocked",
      message:
        result.error === "trial_expired"
          ? "Probezeit vorbei — melde dich mit Google an."
          : result.error === "trial_room"
            ? "Probezeichnen gilt nur für die eingeladene Lobby."
            : "Zeichnen gerade nicht möglich.",
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
  const ip = clientIp(req);
  if (!rateLimit(`redeem_user:${ctx.user.id}`, 12, 60 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Einlöseversuche. Bitte später erneut.",
    });
  }
  if (!rateLimit(`redeem_ip:${ip || "x"}`, 40, 60 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Einlöseversuche von diesem Netz.",
    });
  }
  const code = String(req.body?.code || "").trim();
  if (!code) return res.status(400).json({ error: "invalid_code" });

  const normalized = code.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (normalized.length < 4 || normalized.length > 32) {
    return res.status(400).json({ error: "invalid_code" });
  }
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
  const grantCoins = Math.max(0, Number(voucher.coins) || 0);
  if (grantCoins > 0) {
    applyLedger(ctx.user.id, grantCoins, "voucher", voucher.id);
  }
  const grantedItems = [];
  const items = Array.isArray(voucher.items) ? voucher.items : [];
  for (const it of items) {
    const kind = String(it.kind || "");
    const itemId = String(it.itemId || "");
    const qty = Math.min(50, Math.max(1, Math.floor(Number(it.qty) || 1)));
    for (let i = 0; i < qty; i++) {
      if (safeGiveItem(ctx.user, kind, itemId)) {
        grantedItems.push({ kind, itemId });
      }
    }
  }
  scheduleSave();
  return res.json({
    type: "voucher",
    coins: grantCoins,
    items: grantedItems,
    user: publicUser(ctx.user),
  });
});

/** App: gespeicherte Custom-Räume (Picker); Staff: ?adminTest=1 → alle inkl. Hochzeit */
app.get("/v1/room-layouts", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const adminTest =
    String(req.query?.adminTest || "") === "1" ||
    String(req.query?.adminTest || "").toLowerCase() === "true";
  if (adminTest) {
    if (!isStaff(ctx.user)) {
      return res.status(403).json({ error: "forbidden", message: "Nur Staff." });
    }
    return res.json({ ok: true, rooms: roomLayouts.listRooms(db), adminTest: true });
  }
  return res.json({ ok: true, rooms: roomLayouts.listRooms(db, { forApp: true }) });
});

app.get("/v1/room-layouts/:roomId/image", (req, res) => {
  const id = String(req.params.roomId || "").trim();
  const file = roomLayouts.findImageFile(id);
  if (!file) {
    const weddingFallback = {
      wedding: "wedding-chapel-room.png",
      wedding_small: "wedding-small-room.png",
      wedding_grand: "wedding-grand-room.png",
      wedding_island: "wedding-island-room.png",
    };
    const fbName = weddingFallback[id];
    if (fbName) {
      const fallback = path.join(WEB_DIR, fbName);
      if (fs.existsSync(fallback)) {
        res.type("png");
        return res.sendFile(fallback);
      }
    }
    return res.status(404).json({ error: "no_image" });
  }
  const type =
    file.ext === "jpg" || file.ext === "jpeg"
      ? "image/jpeg"
      : file.ext === "webp"
        ? "image/webp"
        : "image/png";
  res.setHeader("Cache-Control", "public, max-age=3600");
  res.type(type);
  return res.sendFile(file.path);
});

/** Öffentlich für App: Raum-Layout (Zonen) laden */
app.get("/v1/room-layouts/:roomId", (req, res) => {
  const db = getDb();
  const layout = roomLayouts.getLayout(db, req.params.roomId);
  if (!layout) return res.status(404).json({ error: "unknown_room" });
  return res.json({ ok: true, layout });
});

app.get("/v1/admin/room-layouts", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  return res.json({ ok: true, rooms: roomLayouts.listRooms(db) });
});

app.post("/v1/admin/room-layouts", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const result = roomLayouts.createRoom(db, {
    name: req.body?.name,
    imageBase64: req.body?.imageBase64 || req.body?.image,
    mime: req.body?.mime,
  });
  if (result.error) return res.status(400).json({ error: result.error });
  scheduleSave();
  return res.status(201).json({ ok: true, layout: result.layout });
});

app.get("/v1/admin/room-layouts/:roomId", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const layout = roomLayouts.getLayout(db, req.params.roomId);
  if (!layout) return res.status(404).json({ error: "unknown_room" });
  return res.json({ ok: true, layout });
});

app.put("/v1/admin/room-layouts/:roomId", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const result = roomLayouts.saveLayout(db, req.params.roomId, {
    zones: req.body?.zones,
    portals: req.body?.portals,
    actions: req.body?.actions,
    viewRect: req.body?.viewRect,
    cameraRect: req.body?.cameraRect,
    pickable: req.body?.pickable,
    name: req.body?.name,
    imageBase64: req.body?.imageBase64 || req.body?.image,
    mime: req.body?.mime,
  });
  if (result.error) {
    return res.status(400).json({ error: result.error });
  }
  scheduleSave();
  return res.json({
    ok: true,
    layout: result.layout,
    live: true,
    message: "Gespeichert — gilt sofort in der App.",
  });
});

app.get("/v1/admin/room-layouts-link-targets", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const exclude = String(req.query.exclude || "").trim();
  return res.json({
    ok: true,
    rooms: roomLayouts.listLinkTargets(getDb(), exclude),
    actionTypes: roomLayouts.ACTION_TYPES,
  });
});

app.delete("/v1/admin/room-layouts/:roomId", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const result = roomLayouts.deleteRoom(db, req.params.roomId);
  if (result.error) return res.status(400).json({ error: result.error });
  scheduleSave();
  return res.json({ ok: true });
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
  const openBugs = bugReports.openCount(db);
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
    openBugReports: openBugs,
    moderators: mods,
    vouchers: Object.values(db.vouchers || {}).filter((v) => v && !v.revoked).length,
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
  const ctx = requireStaff(req, res, "gm.block");
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
  staffAudit(ctx.user, "room_force_delete", { code: result.code });
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
  target.modPermissions = { ...DEFAULT_MOD_PERMISSIONS };
  for (const id of ADMIN_ONLY_PERMISSION_IDS) {
    target.modPermissions[id] = false;
  }
  target.modSince = target.modSince || Date.now();
  scheduleSave();
  staffAudit(ctx.user, "mod_invite", {
    targetId: target.id,
    nickname: target.nickname,
    already,
  });
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
    next[id] = ADMIN_ONLY_PERMISSION_IDS.has(id) ? false : Boolean(raw[id]);
  }
  target.modPermissions = next;
  scheduleSave();
  staffAudit(ctx.user, "mod_permissions", {
    targetId: target.id,
    permissions: next,
  });
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
  destroyAllSessionsForUser(uid);
  scheduleSave();
  staffAudit(ctx.user, "mod_remove", { targetId: uid });
  return res.json({ ok: true });
});

app.post("/v1/admin/users/:userId/coins", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editCoins");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  const delta = Math.trunc(Number(req.body?.delta) || 0);
  if (!delta || Math.abs(delta) > 5000) {
    return res.status(400).json({ error: "bad_delta" });
  }
  applyLedger(uid, delta, delta > 0 ? "admin_grant" : "admin_grant", ctx.user.id);
  // negative admin_grant still works via applyLedger
  scheduleSave();
  staffAudit(ctx.user, "coins_adjust", { targetId: uid, delta });
  return res.json({ ok: true, user: staffUserCard(target) });
});

app.post("/v1/admin/users/:userId/nickname", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editNick");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
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
  const prev = target.nickname;
  target.nickname = nick;
  scheduleSave();
  staffAudit(ctx.user, "nickname_change", { targetId: uid, from: prev, to: nick });
  return res.json({ ok: true, user: staffUserCard(target) });
});

/** Heirat: Wartezeit verkürzen oder nächsten Schritt (Staff). */
app.post("/v1/admin/users/:userId/marriage/advance", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editCoins");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const db = getDb();
  const target = db.users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  const m = marriage.findMarriageForUser(db, uid);
  if (!m) {
    return res.status(400).json({
      error: "no_marriage",
      message: "Nutzer ist nicht verlobt/verheiratet im Wartezustand.",
    });
  }
  const action = String(req.body?.action || "next").trim().toLowerCase();
  let result;
  if (action === "shorten") {
    const rem = Number(req.body?.remainingMs);
    if (!Number.isFinite(rem) || rem <= 0) {
      result = advanceMarriageNextStep(m, { force: true });
    } else {
      result = shortenMarriageWait(m, rem);
    }
  } else if (action === "set_days") {
    const days = Math.max(0, Math.min(7, Number(req.body?.days) || 0));
    if (days <= 0) {
      result = advanceMarriageNextStep(m, { force: true });
    } else {
      result = shortenMarriageWait(m, days * marriage.DAY_MS);
    }
  } else {
    // next / skip — Staff darf Strich-Pflicht umgehen
    result = advanceMarriageNextStep(m, { force: true });
  }
  if (!result.ok) {
    return res.status(400).json({
      error: result.error || "failed",
      message: result.message || "Aktion fehlgeschlagen.",
    });
  }
  scheduleSave();
  return res.json({
    ok: true,
    user: staffUserCard(target),
    marriage: publicMarriageView(m, uid),
  });
});

app.post("/v1/admin/users/:userId/ban", (req, res) => {
  const ctx = requireStaff(req, res, "gm.block");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
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
    destroyAllSessionsForUser(target.id);
  }
  scheduleSave();
  staffAudit(ctx.user, banned ? "user_ban" : "user_unban", {
    targetId: uid,
    reason: target.bannedReason,
  });
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
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  if (target.role === "admin" || SUPER_ADMIN_EMAILS.has(String(target.googleEmail || "").toLowerCase())) {
    return res.status(400).json({ error: "cannot_delete_admin" });
  }
  // Konto löschen: zusätzlich nur Admins, wenn Ziel jemals Staff war
  if (ctx.user.role !== "admin" && (target.role === "mod" || target.modSince)) {
    return res.status(403).json({
      error: "forbidden",
      message: "Ehemalige/aktuelle Mods können nur Admins löschen.",
    });
  }
  destroyAllSessionsForUser(uid);
  const nick = target.nickname;
  delete db.users[uid];
  scheduleSave();
  staffAudit(ctx.user, "user_delete", { targetId: uid, nickname: nick });
  return res.json({ ok: true });
});

/** Vollständiges Nutzer-Detail für Web-Admin */
app.get("/v1/admin/users/:userId", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const db = getDb();
  const user = db.users?.[uid];
  if (!user) {
    return res.status(404).json({ error: "not_found", message: "Nutzer nicht gefunden." });
  }
  ensureInventory(user);
  ensureFriends(user);
  ach.ensureAchievements(user);
  reconcileAbandonedLobbyOwnership(user);
  ensureHostedRooms(user);
  const day = todayKey();
  const aState = ach.publicAchievementsState(user, day, achDailyCap());
  const a = user.achievements;
  const inv = user.inventory || {};
  const ledger = (Array.isArray(db.ledger) ? db.ledger : [])
    .filter((e) => e && e.userId === uid)
    .slice(-120)
    .reverse();
  const audit = (Array.isArray(db.staffAudit) ? db.staffAudit : [])
    .filter(
      (e) =>
        e &&
        (e.actorId === uid ||
          String(e.detail?.targetId || "") === uid ||
          String(e.detail?.userId || "") === uid)
    )
    .slice(-150)
    .reverse();
  const lobbies = Object.entries(user.hostedRooms || {}).map(([code, meta]) =>
    staffLobbyCard(code, meta, user)
  );
  for (const [code, meta] of Object.entries(user.joinedRooms || {})) {
    if (lobbies.some((l) => l.code === code)) continue;
    lobbies.push({
      ...staffLobbyCard(code, meta, user),
      joined: true,
      name: (meta && meta.name) || code,
    });
  }
  lobbies.sort((x, y) => {
    if (x.live !== y.live) return x.live ? -1 : 1;
    return String(x.name).localeCompare(String(y.name), "de");
  });
  const warnings = Array.isArray(user.staffWarnings) ? user.staffWarnings.slice(-40).reverse() : [];
  return res.json({
    ok: true,
    user: {
      ...staffUserCard(user),
      bannedAt: user.bannedAt || null,
      bannedReason: user.bannedReason || null,
      paidCoins: Math.max(0, Number(user.paidCoins) || 0),
      dailyBalance: Math.max(0, Number(user.dailyBalance) || 0),
      googleSub: user.googleSub ? String(user.googleSub).slice(0, 12) + "…" : null,
      lastSeenAt: user.lastSeenAt || user.updatedAt || null,
    },
    achievements: {
      streak: a.streak || 0,
      lastDailyCompleteDate: a.lastDailyCompleteDate || null,
      coinsEarnedToday: a.coinsEarnedToday || 0,
      totalAchCoins: a.totalAchCoins || 0,
      progress: a.progress || {},
      unlocked: a.unlocked || {},
      daily: aState.daily,
      list: aState.achievements,
      unlockedCount: aState.unlockedCount,
      totalCount: aState.totalCount,
    },
    inventory: {
      equippedPet: inv.equippedPet || null,
      pets: inv.pets && typeof inv.pets === "object" && !Array.isArray(inv.pets) ? inv.pets : {},
      themes: Array.isArray(inv.themes) ? inv.themes : [],
      stickers: inv.stickers && typeof inv.stickers === "object" ? inv.stickers : {},
      emojis: inv.emojis && typeof inv.emojis === "object" ? inv.emojis : {},
      stickerCount: Object.values(inv.stickers || {}).reduce((n, v) => n + (Number(v) || 0), 0),
      emojiCount: Object.values(inv.emojis || {}).reduce((n, v) => n + (Number(v) || 0), 0),
      petCount: Object.values(
        inv.pets && typeof inv.pets === "object" && !Array.isArray(inv.pets) ? inv.pets : {}
      ).reduce((n, v) => n + (Number(v) || 0), 0),
    },
    friends: {
      count: Array.isArray(user.friends?.list) ? user.friends.list.length : 0,
      incoming: Array.isArray(user.friends?.incoming) ? user.friends.incoming.length : 0,
      outgoing: Array.isArray(user.friends?.outgoing) ? user.friends.outgoing.length : 0,
    },
    lobbies,
    ledger,
    audit,
    warnings,
  });
});

app.post("/v1/admin/users/:userId/streak", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editCoins");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  const streak = Math.max(0, Math.min(3650, Math.floor(Number(req.body?.streak) || 0)));
  const a = ach.ensureAchievements(target);
  a.streak = streak;
  a.progress.daily_streak = Math.max(Number(a.progress.daily_streak) || 0, streak);
  // Gestern setzen, damit der nächste echte Tag +1 macht (nicht diff=0 → Reset)
  if (streak > 0) {
    a.lastStreakDate = ach.yesterdayKeyFrom(todayKey()) || todayKey();
    a.lastDailyCompleteDate = a.lastStreakDate;
  } else {
    a.lastStreakDate = null;
    a.lastDailyCompleteDate = null;
  }
  scheduleSave();
  staffAudit(ctx.user, "streak_set", { targetId: uid, streak });
  return res.json({ ok: true, streak, user: staffUserCard(target) });
});

app.post("/v1/admin/users/:userId/achievements", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editCoins");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  const action = String(req.body?.action || "").trim().toLowerCase();
  const id = String(req.body?.id || "").trim();
  const a = ach.ensureAchievements(target);
  const day = todayKey();
  if (action === "set_metric") {
    const metric = String(req.body?.metric || "").trim();
    const value = Math.max(0, Math.floor(Number(req.body?.value) || 0));
    if (!metric) {
      return res.status(400).json({ error: "bad_metric", message: "Metrik fehlt." });
    }
    a.progress[metric] = value;
    ach.bumpMetric(target, metric, 0, day, null);
    scheduleSave();
    staffAudit(ctx.user, "ach_metric_set", { targetId: uid, metric, value });
    return res.json({ ok: true, progress: a.progress });
  }
  if (action === "unlock") {
    const def = ach.findAchievement(id, { includeDisabled: true });
    if (!def) return res.status(404).json({ error: "not_found", message: "Erfolg unbekannt." });
    if (!a.unlocked[id]) {
      a.unlocked[id] = {
        at: Date.now(),
        coins: 0,
        rewardItem: ach.publicRewardItem(def),
        claimed: false,
      };
    }
    a.progress.achievements_unlocked = Object.keys(a.unlocked).length;
    scheduleSave();
    staffAudit(ctx.user, "ach_unlock", { targetId: uid, id });
    return res.json({ ok: true });
  }
  if (action === "lock") {
    delete a.unlocked[id];
    a.progress.achievements_unlocked = Object.keys(a.unlocked).length;
    scheduleSave();
    staffAudit(ctx.user, "ach_lock", { targetId: uid, id });
    return res.json({ ok: true });
  }
  if (action === "unclaim") {
    if (a.unlocked[id]) {
      a.unlocked[id].claimed = false;
      a.unlocked[id].claimedAt = null;
      a.unlocked[id].itemGranted = false;
    }
    scheduleSave();
    staffAudit(ctx.user, "ach_unclaim", { targetId: uid, id });
    return res.json({ ok: true });
  }
  if (action === "reset_progress") {
    a.progress = {};
    a.unlocked = {};
    a.streak = 0;
    a.lastDailyCompleteDate = null;
    a.coinsEarnedToday = 0;
    a.daily = null;
    scheduleSave();
    staffAudit(ctx.user, "ach_reset", { targetId: uid });
    return res.json({ ok: true });
  }
  return res.status(400).json({
    error: "bad_action",
    message: "Aktion: set_metric|unlock|lock|unclaim|reset_progress",
  });
});

app.post("/v1/admin/users/:userId/warn", (req, res) => {
  const ctx = requireStaff(req, res, "gm.block");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  const message = String(req.body?.message || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 280);
  if (message.length < 3) {
    return res.status(400).json({ error: "empty", message: "Verwarnung zu kurz." });
  }
  const severity = String(req.body?.severity || "warn").trim().toLowerCase() === "final"
    ? "final"
    : "warn";
  if (!Array.isArray(target.staffWarnings)) target.staffWarnings = [];
  const warning = {
    id: newId("warn"),
    message,
    severity,
    at: Date.now(),
    by: ctx.user.id,
    byNick: staffDisplayName(ctx.user),
    seen: false,
  };
  target.staffWarnings.push(warning);
  if (target.staffWarnings.length > 80) {
    target.staffWarnings = target.staffWarnings.slice(-60);
  }
  target.pendingStaffNotice = {
    id: warning.id,
    message,
    severity,
    at: warning.at,
    authorNickname: warning.byNick,
  };
  scheduleSave();
  staffAudit(ctx.user, "user_warn", { targetId: uid, message, severity, warningId: warning.id });
  accountPush.emitUserEvent(getDb(), uid, "staff_warning", {
    id: warning.id,
    message,
    severity,
    at: warning.at,
    authorNickname: "Team",
  });
  return res.json({ ok: true, warning, user: staffUserCard(target) });
});

/** Admin schenkt Item → Inventar + Hinweis unter Sozial · Freunde */
app.post("/v1/admin/users/:userId/gift", (req, res) => {
  const ctx = requireStaff(req, res, "gm.editCoins");
  if (!ctx) return;
  const uid = String(req.params.userId || "").trim();
  const target = getDb().users?.[uid];
  if (!assertCanModerateTarget(ctx.user, target, res)) return;
  const kind = String(req.body?.kind || "").trim().toLowerCase();
  const itemId = clipMarketItemId(req.body?.itemId ?? req.body?.id);
  if (!["emojis", "stickers", "themes", "pets"].includes(kind) || !itemId) {
    return res.status(400).json({
      error: "bad_item",
      message: "kind (emojis|stickers|themes|pets) und itemId nötig.",
    });
  }
  let qty = Math.floor(Number(req.body?.qty ?? req.body?.quantity ?? 1) || 1);
  if (kind === "themes") qty = 1;
  qty = Math.max(1, Math.min(50, qty));

  ensureInventory(target);
  let given = 0;
  for (let i = 0; i < qty; i++) {
    if (safeGiveItem(target, kind, itemId)) given += 1;
  }
  if (!given) {
    return res.status(400).json({ error: "give_failed", message: "Item konnte nicht gutgeschrieben werden." });
  }

  const db = getDb();
  const label = friendlyMarketLabel(
    kind,
    itemId,
    String(req.body?.label || "").trim() || itemId
  );
  const emojiGlyph =
    kind === "themes"
      ? "🖼️"
      : String(req.body?.emoji || itemId).trim().slice(0, 16) || "🎁";
  const noteExtra = String(req.body?.message || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 160);
  const qtyLabel = given > 1 ? ` ×${given}` : "";
  const message =
    `🎁 Geschenk: ${emojiGlyph} ${label}${qtyLabel}` +
    (noteExtra ? ` — ${noteExtra}` : "") +
    " · liegt jetzt in deinem Inventar.";

  // Geschenk: nur einmaliges Popup — nicht dauerhaft unter Sozial · Freunde.
  // Admin-Historie behält den Eintrag (staffWarnings), die App filtert gifts raus.
  if (!Array.isArray(target.staffWarnings)) target.staffWarnings = [];
  const notice = {
    id: newId("gift"),
    message: message.slice(0, 280),
    severity: "gift",
    kind: "gift",
    gift: { kind, itemId, qty: given, label, emoji: emojiGlyph },
    at: Date.now(),
    by: ctx.user.id,
    byNick: "Team",
    seen: false,
  };
  target.staffWarnings.push(notice);
  if (target.staffWarnings.length > 80) {
    target.staffWarnings = target.staffWarnings.slice(-60);
  }
  target.pendingStaffNotice = {
    id: notice.id,
    message: notice.message,
    severity: "gift",
    at: notice.at,
    authorNickname: "Team",
    gift: notice.gift,
  };

  scheduleSave();
  staffAudit(ctx.user, "user_gift", {
    targetId: uid,
    kind,
    itemId,
    qty: given,
    noticeId: notice.id,
  });
  accountPush.emitUserEvent(getDb(), uid, "staff_warning", {
    id: notice.id,
    message: notice.message,
    severity: "gift",
    at: notice.at,
    authorNickname: "Team",
  });
  accountPush.emitUserEvent(
    getDb(),
    uid,
    "inventory_new",
    { kind, itemId, qty: given, label, emoji: emojiGlyph },
    { skipFcm: true }
  );
  const inv = target.inventory || {};
  return res.json({
    ok: true,
    notice,
    given: { kind, itemId, qty: given, label, emoji: emojiGlyph },
    inventory: {
      pets: inv.pets && typeof inv.pets === "object" && !Array.isArray(inv.pets) ? inv.pets : {},
      themes: inv.themes || [],
      stickerCount: Object.keys(inv.stickers || {}).length,
      emojiCount: Object.keys(inv.emojis || {}).length,
      equippedPet: inv.equippedPet || null,
    },
    user: staffUserCard(target),
  });
});

app.get("/v1/me/staff-notices", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const user = ctx.user;
  let pending = user.pendingStaffNotice || null;
  // Geschenke: nie Team-Namen nach außen; Verwarnungen behalten Autor nur intern
  if (pending && (pending.severity === "gift" || pending.kind === "gift")) {
    pending = { ...pending, authorNickname: "Team", byNick: "Team" };
  }
  // Inbox unter Sozial · Freunde: nur echte Verwarnungen (keine Geschenke)
  const warnings = Array.isArray(user.staffWarnings)
    ? user.staffWarnings
        .filter((w) => w && w.severity !== "gift" && w.kind !== "gift")
        .slice(-20)
        .reverse()
        .map((w) => ({
          ...w,
          // Keine Mitarbeiternamen in der App
          byNick: "Team",
          authorNickname: "Team",
        }))
    : [];
  return res.json({ ok: true, pending, warnings });
});

app.post("/v1/me/staff-notices/:id/ack", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const user = ctx.user;
  if (user.pendingStaffNotice && user.pendingStaffNotice.id === id) {
    user.pendingStaffNotice = null;
  }
  if (Array.isArray(user.staffWarnings)) {
    const hit = user.staffWarnings.find((w) => w && w.id === id);
    if (hit) hit.seen = true;
    // Geschenk nach Bestätigung aus Nutzer-Liste entfernen (Admin-Audit bleibt im Staff-Log)
    if (hit && (hit.severity === "gift" || hit.kind === "gift")) {
      user.staffWarnings = user.staffWarnings.filter((x) => x && x.id !== id);
    }
  }
  scheduleSave();
  return res.json({ ok: true });
});

app.get("/v1/admin/audit", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const db = getDb();
  const userId = String(req.query?.userId || "").trim();
  const actorId = String(req.query?.actorId || "").trim();
  const action = String(req.query?.action || "").trim().toLowerCase();
  const q = String(req.query?.q || "").trim().toLowerCase();
  const limit = Math.max(1, Math.min(500, Math.floor(Number(req.query?.limit) || 100)));
  let entries = Array.isArray(db.staffAudit) ? [...db.staffAudit] : [];
  entries.reverse();
  if (userId) {
    entries = entries.filter(
      (e) =>
        e &&
        (e.actorId === userId ||
          String(e.detail?.targetId || "") === userId ||
          String(e.detail?.userId || "") === userId)
    );
  }
  if (actorId) entries = entries.filter((e) => e && e.actorId === actorId);
  if (action) entries = entries.filter((e) => e && String(e.action || "").includes(action));
  if (q) {
    entries = entries.filter((e) => {
      const hay = JSON.stringify(e || {}).toLowerCase();
      return hay.includes(q);
    });
  }
  return res.json({ ok: true, entries: entries.slice(0, limit), total: entries.length });
});

app.get("/v1/admin/rooms/:code/canvas", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const live = rooms.get(code);
  let strokes = [];
  if (live && Array.isArray(live.strokes)) {
    strokes = live.strokes.map((s) => sanitizeStoredStroke(s)).filter(Boolean);
  } else {
    strokes = loadRoomStrokes(code);
  }
  const capped = strokes.slice(-2500);
  return res.json({
    ok: true,
    code,
    live: Boolean(live),
    strokeCount: capped.length,
    strokes: capped,
  });
});

app.get("/v1/admin/achievements", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const list = ach.listAchievements({ includeDisabled: true });
  return res.json({
    ok: true,
    achievements: list.map((d) => ({
      ...d,
      rewardItem: ach.publicRewardItem(d),
    })),
    categories: ach.CATEGORIES,
    metrics: ach.METRIC_OPTIONS,
    dailyCap: ach.getAchievementDailyCap(getDb()),
  });
});

app.get("/v1/admin/achievements/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const def = ach.findAchievement(req.params.id, { includeDisabled: true });
  if (!def) return res.status(404).json({ error: "not_found" });
  return res.json({
    ok: true,
    achievement: { ...def, rewardItem: ach.publicRewardItem(def) },
    categories: ach.CATEGORIES,
    metrics: ach.METRIC_OPTIONS,
  });
});

app.post("/v1/admin/achievements", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const body = { ...(req.body || {}), custom: true };
  if (!body.id) {
    const base = String(body.title || "erfolg")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "_")
      .replace(/^_|_$/g, "")
      .slice(0, 28);
    body.id = `custom_${base || "item"}_${crypto.randomBytes(2).toString("hex")}`;
  }
  const result = ach.upsertAchievementDef(getDb(), body, { create: true });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "ach_create", { id: result.achievement.id });
  return res.json({ ok: true, achievement: result.achievement });
});

app.put("/v1/admin/achievements/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const result = ach.upsertAchievementDef(getDb(), { ...(req.body || {}), id }, { create: false });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "ach_update", { id });
  return res.json({ ok: true, achievement: result.achievement });
});

app.post("/v1/admin/achievements/:id/disable", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const result = ach.setAchievementDisabled(getDb(), id, true);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "ach_disable", { id });
  return res.json({ ok: true, achievement: result.achievement });
});

app.post("/v1/admin/achievements/:id/enable", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const result = ach.setAchievementDisabled(getDb(), id, false);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "ach_enable", { id });
  return res.json({ ok: true, achievement: result.achievement });
});

app.delete("/v1/admin/achievements/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const result = ach.deleteCustomAchievement(getDb(), id);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "ach_delete", { id });
  return res.json({ ok: true });
});

/** Nur Anzeigename — ohne Shop-/Preis-Seiteneffekte */
function applyItemDisplayLabel(req, res) {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  const label = req.body?.label;
  const result = itemLabels.setDisplayLabel(getDb(), kind, itemId, label);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  // Bestehenden Katalogeintrag: Label spiegeln, sonst nichts anfassen
  const existing = shopCatalog.getItem(getDb(), kind, itemId);
  if (existing && result.label) {
    existing.label = result.label;
    existing.updatedAt = Date.now();
  }
  flushSave();
  staffAudit(ctx.user, "item_display_label", {
    kind,
    itemId,
    label: result.label,
    cleared: result.cleared,
  });
  return res.json({ ok: true, ...result });
}
app.put("/v1/admin/items/:kind/:itemId/display-label", applyItemDisplayLabel);
app.post("/v1/admin/items/:kind/:itemId/display-label", applyItemDisplayLabel);

/** Benachrichtigungs-/Share-Sprüche (Admin) */
app.get("/v1/admin/notify-phrases", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const pool = String(req.query?.pool || "").trim() || null;
  const q = String(req.query?.q || "").trim();
  const all = notifyPhrases.listPhrases(getDb(), {});
  const phrases = notifyPhrases.listPhrases(getDb(), { pool, q });
  return res.json({
    ok: true,
    phrases,
    targets: notifyPhrases.TARGETS,
    counts: {
      mood: all.filter((p) => p.pool === "mood").length,
      share: all.filter((p) => p.pool === "share").length,
      all: all.length,
    },
  });
});

app.post("/v1/admin/notify-phrases", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const result = notifyPhrases.upsertPhrase(getDb(), req.body || {}, { create: true });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "phrase_create", { id: result.phrase.id });
  return res.json({ ok: true, phrase: result.phrase });
});

app.put("/v1/admin/notify-phrases/:id", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const result = notifyPhrases.upsertPhrase(
    getDb(),
    { ...(req.body || {}), id: req.params.id },
    { create: false }
  );
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "phrase_update", { id: result.phrase.id });
  return res.json({ ok: true, phrase: result.phrase });
});

app.delete("/v1/admin/notify-phrases/:id", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const result = notifyPhrases.deletePhrase(getDb(), req.params.id);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "phrase_delete", { id: req.params.id });
  return res.json({ ok: true });
});

/** Mehrere Sprüche aktivieren/deaktivieren */
app.post("/v1/admin/notify-phrases/bulk", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const ids = Array.isArray(req.body?.ids) ? req.body.ids : [];
  const enabled = Boolean(req.body?.enabled);
  const result = notifyPhrases.bulkSetEnabled(getDb(), ids, enabled);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "phrase_bulk", { updated: result.updated, enabled });
  return res.json({ ok: true, ...result });
});

/** Tagesaufgaben-Planer (Admin) */
/** —— Events (Jahreskalender) —— */
app.get("/v1/admin/events", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const year = Number(req.query.year) || new Date().getUTCFullYear();
  return res.json({
    ok: true,
    events: seasonEvents.listAdminEvents(db),
    year: seasonEvents.yearOverview(db, year),
  });
});

app.put("/v1/admin/events", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const result = seasonEvents.putAdminEvents(db, req.body || {});
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  scheduleSave();
  staffAudit(ctx.user, "events_config", { count: (result.events || []).length });
  return res.json({ ok: true, events: result.events });
});

app.post("/v1/admin/events", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const result = seasonEvents.createAdminEvent(db, req.body || {});
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "events_create", { id: result.event?.id });
  return res.json({
    ok: true,
    event: result.event,
    events: result.events,
    year: seasonEvents.yearOverview(db, new Date().getUTCFullYear()),
  });
});

app.delete("/v1/admin/events/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const id = String(req.params.id || "").trim();
  const result = seasonEvents.deleteAdminEvent(db, id, { liveRooms: rooms });
  if (!result.ok) {
    return res.status(result.error === "not_found" ? 404 : 400).json({
      error: result.error,
      message: result.message,
    });
  }
  flushSave();
  staffAudit(ctx.user, "events_delete", {
    id,
    deleted: result.deleted,
    disabled: result.disabled,
    roomsCleared: result.roomsCleared,
  });
  return res.json({
    ok: true,
    ...result,
    year: seasonEvents.yearOverview(db, new Date().getUTCFullYear()),
  });
});

app.get("/v1/me/events", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  return res.json(seasonEvents.meEventsPayload(db, ctx.user, todayKey()));
});

app.post("/v1/me/events/:id/collect", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!rateLimit(`eventcollect:${ctx.user.id}`, 20, 60_000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const db = getDb();
  const result = seasonEvents.collectEvent(
    db,
    ctx.user,
    req.params.id,
    todayKey(),
    (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
    (u, k, itemId) => safeGiveItem(u, k, itemId)
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
    ...result,
    user: publicUser(ctx.user),
  });
});

app.get("/v1/me/events/:id/contest", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const cfg = seasonEvents.ensureEventsConfig(db);
  const ev = (cfg.events || []).find((e) => e && e.id === String(req.params.id || "").trim());
  if (!ev) return res.status(404).json({ error: "not_found" });
  const engine = require("./event_engine");
  const now = new Date();
  const occ = seasonEvents.nextOccurrence(ev, now);
  const active = seasonEvents.isActiveAtPatched(ev, now);
  const windowEnd =
    engine.eventWindowEndIso(ev, now) ||
    (Number.isFinite(Number(occ?.untilMs))
      ? new Date(Number(occ.untilMs)).toISOString()
      : null) ||
    engine.eventWindowEndIsoFromOccEnd(occ?.end) ||
    null;
  // Nach Vote-Fenster: Ranking finalisieren
  const { until: voteUntil } = engine.resolveVoteBounds(
    engine.enrichEvent(ev).contest,
    windowEnd,
    now.getTime()
  );
  if (!active && voteUntil != null && now.getTime() > voteUntil) {
    try {
      engine.finalizeContestPrizes(db, ev, db.users);
      scheduleSave();
    } catch {
      /* ignore */
    }
  }
  const contest = engine.contestPublicForUser(
    db,
    ctx.user,
    ev,
    occ?.start || null,
    windowEnd,
    active,
    now.getTime()
  );
  return res.json({
    ok: true,
    eventId: ev.id,
    contest,
    state: seasonEvents.meEventsPayload(db, ctx.user, todayKey(), now),
  });
});

app.post("/v1/me/events/:id/contest/vote", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!rateLimit(`evote:${ctx.user.id}`, 60, 60_000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const db = getDb();
  const cfg = seasonEvents.ensureEventsConfig(db);
  const ev = (cfg.events || []).find((e) => e && e.id === String(req.params.id || "").trim());
  if (!ev) return res.status(404).json({ error: "not_found" });
  const engine = require("./event_engine");
  const now = new Date();
  const occ = seasonEvents.nextOccurrence(ev, now);
  const active = seasonEvents.isActiveAtPatched(ev, now);
  const windowEnd =
    engine.eventWindowEndIso(ev, now) ||
    (Number.isFinite(Number(occ?.untilMs))
      ? new Date(Number(occ.untilMs)).toISOString()
      : null) ||
    engine.eventWindowEndIsoFromOccEnd(occ?.end) ||
    null;
  const entryId = String(req.body?.entryId || "").trim();
  const value = req.body?.value;
  const result = engine.castVote(
    db,
    ctx.user,
    ev,
    entryId,
    value,
    (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
    active,
    windowEnd
  );
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  scheduleSave();
  return res.json({
    ok: true,
    ...result,
    contest: engine.contestPublicForUser(
      db,
      ctx.user,
      ev,
      occ?.start || null,
      windowEnd,
      active,
      now.getTime()
    ),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/me/events/:id/contest/claim-prize", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  const cfg = seasonEvents.ensureEventsConfig(db);
  const ev = (cfg.events || []).find((e) => e && e.id === String(req.params.id || "").trim());
  if (!ev) return res.status(404).json({ error: "not_found" });
  const engine = require("./event_engine");
  const result = engine.claimContestPrize(
    db,
    ctx.user,
    ev,
    (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
    (u, k, itemId) => safeGiveItem(u, k, itemId)
  );
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  scheduleSave();
  return res.json({
    ok: true,
    ...result,
    state: seasonEvents.meEventsPayload(db, ctx.user, todayKey()),
    user: publicUser(ctx.user),
  });
});

app.post("/v1/me/events/:id/contest/report", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!rateLimit(`econreport:${ctx.user.id}`, 10, 60_000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const db = getDb();
  const eventId = String(req.params.id || "").trim();
  const entryId = String(req.body?.entryId || "").trim();
  const engine = require("./event_engine");
  const bucket = engine.contestBucket(db, eventId);
  const entry = bucket.entries.find((e) => e.entryId === entryId);
  if (!entry) return res.status(404).json({ error: "not_found" });
  // Melden = Beitrag überspringen, zählt gegen 100er-Limit
  const skip = engine.markContestEntrySeen(ctx.user, eventId, entryId, 0);
  if (!skip.ok && skip.error === "vote_limit") {
    return res.status(400).json({ error: skip.error, message: skip.message });
  }
  const report = {
    id: newId("ecr"),
    eventId,
    entryId,
    targetUserId: entry.userId,
    targetNickname: entry.nickname,
    reporterUserId: ctx.user.id,
    reporterNickname: ctx.user.nickname || "Jemand",
    createdAt: Date.now(),
    reason: String(req.body?.reason || "").slice(0, 200),
  };
  if (!db.contestReports || typeof db.contestReports !== "object") db.contestReports = {};
  db.contestReports[report.id] = report;
  scheduleSave();
  console.log(
    `contest report ${report.id} event=${eventId} entry=${entryId} by=${report.reporterNickname}`
  );
  const now = new Date();
  const cfg = seasonEvents.ensureEventsConfig(db);
  const ev = (cfg.events || []).find((e) => e && e.id === eventId);
  let contest = null;
  if (ev) {
    const occ = seasonEvents.nextOccurrence(ev, now);
    const active = seasonEvents.isActiveAtPatched(ev, now);
    const windowEnd =
      engine.eventWindowEndIso(ev, now) ||
      engine.eventWindowEndIsoFromOccEnd(occ?.end) ||
      null;
    contest = engine.contestPublicForUser(
      db,
      ctx.user,
      ev,
      occ?.start || null,
      windowEnd,
      active,
      now.getTime()
    );
  }
  return res.json({ ok: true, id: report.id, contest });
});

app.get("/v1/me/events/:id/contest/entries/:entryId/image", (req, res) => {
  // Auth: Bearer oder ?session= (Bild-Downloads ohne Header-Support)
  let ctx = authUser(req);
  if (!ctx) {
    const qTok = String(req.query?.session || "").trim();
    if (qTok) {
      const db = getDb();
      const session = db.sessions[qTok];
      if (session && session.expiresAt >= Date.now()) {
        const user = db.users[session.userId];
        if (user && !user.banned) ctx = { user, token: qTok };
      }
    }
  }
  if (!ctx) return res.status(401).json({ error: "unauthorized" });
  const db = getDb();
  const engine = require("./event_engine");
  const bucket = engine.contestBucket(db, req.params.id);
  const entry = bucket.entries.find((e) => e.entryId === String(req.params.entryId || "").trim());
  if (!entry?.imagePath) return res.status(404).json({ error: "not_found" });
  const abs = path.isAbsolute(entry.imagePath)
    ? entry.imagePath
    : path.join(DATA_DIR, entry.imagePath);
  if (!fs.existsSync(abs)) return res.status(404).json({ error: "missing" });
  // Vote/Gewinner: JPEG-Thumb (deutlich kleiner); ?full=1 → Original-PNG
  const wantFull = String(req.query?.full || "") === "1";
  if (!wantFull) {
    const thumb = require("./contest_thumbs").ensureContestThumb(abs);
    if (thumb && fs.existsSync(thumb)) {
      res.setHeader("Content-Type", "image/jpeg");
      res.setHeader("Cache-Control", "private, max-age=604800, immutable");
      return res.sendFile(thumb);
    }
  }
  res.setHeader("Content-Type", "image/png");
  res.setHeader("Cache-Control", "private, max-age=604800, immutable");
  return res.sendFile(abs);
});

app.get("/v1/admin/daily-tasks", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  return res.json({ ok: true, config: dailyTasks.publicConfig(getDb()) });
});

app.put("/v1/admin/daily-tasks", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const result = dailyTasks.setConfig(getDb(), req.body || {});
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "daily_tasks_config", {
    mode: result.config.mode,
    planLen: result.config.plan?.length,
    rewardCoins: result.config.rewardCoins,
  });
  return res.json({ ok: true, config: result.config });
});

app.post("/v1/admin/daily-tasks/templates/:id/enable", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const result = dailyTasks.setTemplateEnabled(getDb(), req.params.id, true);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  return res.json({ ok: true, config: result.config });
});

app.post("/v1/admin/daily-tasks/templates/:id/disable", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const result = dailyTasks.setTemplateEnabled(getDb(), req.params.id, false);
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  return res.json({ ok: true, config: result.config });
});

/** App: zufälligen Mood-Spruch inkl. Tap-Ziel holen */
app.get("/v1/notify-phrases/pick", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const pool = String(req.query?.pool || "mood").trim();
  const excludingId = String(req.query?.excluding || "").trim() || null;
  const phrase = notifyPhrases.pickPhrase(getDb(), { pool, excludingId });
  return res.json({ ok: true, phrase });
});

app.get("/v1/live-notice", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({ ok: true, notice: getLiveNotice() });
});

/** Schmale Home-Benachrichtigungen (öffentlicher Feed). */
app.get("/v1/home-feed", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const db = getDb();
  // Alte aufdringliche Wedding-Live-Notice still entfernen
  if (db.liveNotice?.kind === "wedding") {
    db.liveNotice = null;
    scheduleSave();
  }
  return res.json({ ok: true, items: homeFeed.listPublic(db) });
});

app.delete("/v1/admin/live-notice", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  getDb().liveNotice = null;
  scheduleSave();
  return res.json({ ok: true, cleared: true });
});

app.post("/v1/admin/live-notice", (req, res) => {
  const ctx = requireStaff(req, res, "live.notify");
  if (!ctx) return;
  const message = String(req.body?.message || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 200);
  if (message.length < 2) {
    return res.status(400).json({ error: "empty", message: "Nachricht zu kurz." });
  }
  const notice = publishLiveNotice({
    message,
    authorNickname: staffDisplayName(ctx.user),
    authorId: ctx.user.id,
    kind: "team",
  });
  return res.json({ ok: true, notice });
});

/** Nächtlicher Wartungsmodus (02:59–03:09 Berlin) — Status für App-Overlay */
app.get("/v1/maintenance/status", (req, res) => {
  const ctx = authUser(req);
  if (ctx?.user?.banned) {
    return res.status(403).json({ error: "banned", message: "Dieses Konto ist gesperrt." });
  }
  const db = getDb();
  const status = maintenance.publicStatus(db, ctx?.user || null);
  if (ctx?.user && status.active) scheduleSave();
  return res.json({
    ok: true,
    ...status,
  });
});

app.post("/v1/maintenance/score", (req, res) => {
  const ctx = requireAuth(req, res, { allowTrial: true });
  if (!ctx) return;
  const score = Math.floor(Number(req.body?.score) || 0);
  const result = maintenance.recordScore(getDb(), ctx.user, score);
  scheduleSave();
  return res.json({ ok: true, ...result });
});

app.post("/v1/maintenance/claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const result = maintenance.claimReward(getDb(), ctx.user, applyLedger);
  if (!result.ok) {
    const code =
      result.error === "too_early"
        ? 409
        : result.error === "too_late" || result.error === "not_eligible"
          ? 410
          : 400;
    return res.status(code).json(result);
  }
  scheduleSave();
  syncCoinTotal(ctx.user);
  return res.json({
    ok: true,
    already: Boolean(result.already),
    coins: result.coins,
    nightKey: result.nightKey,
    balance: Number(ctx.user.coins) || 0,
  });
});

app.get("/v1/admin/maintenance/reports", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const list = maintenance.listReports(getDb()).map((r) => ({
    id: r.id,
    nightKey: r.nightKey,
    source: r.source,
    createdAt: r.createdAt,
    status: r.status,
    summary: r.summary,
  }));
  return res.json({ ok: true, reports: list });
});

app.get("/v1/admin/maintenance/reports/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const report = maintenance.getReport(getDb(), req.params.id);
  if (!report) return res.status(404).json({ error: "not_found" });
  return res.json({ ok: true, report });
});

/** Manuell nur Bericht (Backup + Health, kein Shop-Zyklus) */
app.post("/v1/admin/maintenance/report", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const report = maintenance.runManualReport(getDb(), {
    dataFile: DATA_FILE,
    dataDir: DATA_DIR,
    flushSaveSync,
    withShopCycle: false,
  });
  scheduleSave();
  return res.json({ ok: true, report });
});

function sanitizeVoucherItems(raw) {
  const out = [];
  const arr = Array.isArray(raw) ? raw : [];
  for (const it of arr.slice(0, 24)) {
    if (!it || typeof it !== "object") continue;
    const kind = String(it.kind || "").trim().toLowerCase();
    const itemId = String(it.itemId || it.id || it.emoji || "").trim().slice(0, 32);
    const qty = Math.min(50, Math.max(1, Math.floor(Number(it.qty) || 1)));
    if (!itemId) continue;
    if (!["pets", "themes", "stickers", "emojis"].includes(kind)) continue;
    if (!isKnownInventoryItem(kind, itemId)) continue;
    out.push({ kind, itemId, qty });
  }
  return out;
}

app.get("/v1/admin/rooms", (req, res) => {
  const ctx = requireStaff(req, res, "gm.search");
  if (!ctx) return;
  const live = [];
  for (const [code, room] of rooms.entries()) {
    const host = room.hostUserId ? getDb().users?.[room.hostUserId] : null;
    live.push(staffLobbyCard(code, null, host));
  }
  live.sort((a, b) => (b.online || 0) - (a.online || 0) || String(a.code).localeCompare(String(b.code)));
  return res.json({ ok: true, rooms: live });
});

app.get("/v1/admin/vouchers", (req, res) => {
  const ctx = requireStaff(req, res, "codes.view");
  if (!ctx) return;
  const list = Object.values(getDb().vouchers)
    .filter((v) => v && !v.revoked)
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, 100)
    .map((v) => ({
      id: v.id,
      code: v.code,
      coins: v.coins,
      items: Array.isArray(v.items) ? v.items : [],
      maxRedeems: v.maxRedeems,
      redeemCount: v.redeemCount,
      expiresAt: v.expiresAt,
      revoked: false,
      createdAt: v.createdAt,
    }));
  return res.json({ vouchers: list });
});

app.post("/v1/admin/vouchers", (req, res) => {
  const ctx = requireStaff(req, res, "codes.edit");
  if (!ctx) return;
  const coins = Math.min(10000, Math.max(0, Number(req.body?.coins) || 0));
  const items = sanitizeVoucherItems(req.body?.items);
  if (coins < 1 && items.length < 1) {
    return res.status(400).json({
      error: "empty_voucher",
      message: "Mindestens Coins oder ein Item wählen.",
    });
  }
  // maxRedeems = how many different people may redeem; each person only once
  const maxRedeems = Math.min(10000, Math.max(1, Number(req.body?.maxRedeems) || 1));
  const forever = Boolean(req.body?.forever);
  const days = Math.min(365, Math.max(1, Number(req.body?.validDays) || 30));
  const custom = String(req.body?.code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, 24);
  if (custom.length > 0 && custom.length < 8) {
    return res.status(400).json({
      error: "code_too_short",
      message: "Eigene Codes mindestens 8 Zeichen (gegen Ausprobieren).",
    });
  }
  const code = custom.length >= 8 ? custom : randomCode(10);
  const db = getDb();
  if (db.vouchers[code]) return res.status(409).json({ error: "code_exists" });
  const voucher = {
    id: newId("v"),
    code,
    coins,
    items,
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
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  const db = getDb();
  const key =
    (db.vouchers[code] && code) ||
    Object.keys(db.vouchers || {}).find(
      (k) => String(db.vouchers[k]?.code || k).toUpperCase() === code
    ) ||
    null;
  const voucher = key ? db.vouchers[key] : null;
  if (!voucher) return res.status(404).json({ error: "not_found" });
  voucher.revoked = true;
  voucher.revokedAt = Date.now();
  voucher.revokedBy = ctx.user.id;
  // Entfernen — erscheint nicht mehr in der Liste und ist nicht einlösbar
  delete db.vouchers[key];
  if (db.redeems && typeof db.redeems === "object") {
    const vid = String(voucher.id || "");
    for (const rk of Object.keys(db.redeems)) {
      const entry = db.redeems[rk];
      if (entry && String(entry.voucherId || "") === vid) {
        delete db.redeems[rk];
      }
    }
  }
  flushSave();
  return res.json({ ok: true, code: voucher.code || code });
});

app.get("/v1/shop/packs", (req, res) => {
  const ctx = authUser(req);
  const ip = clientIp(req);
  res.json({
    enabled: shopPurchasesEnabled(),
    provider: "google_play",
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

app.get("/v1/shop/catalog", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const db = getDb();
  // Reiner Lese-Pfad — keine Rotation/Expiry hier (Tick + Nacht pflegen Fenster)
  const kind = String(req.query?.kind || "").trim() || null;
  const q = String(req.query?.q || "").trim().slice(0, 40);
  const items = shopCatalog.listPublicCatalog(db, { kind: kind || undefined, q });
  // Admin-Anzeigenamen (auch für Items außerhalb des öffentlichen Shops)
  const displayLabels = { ...itemLabels.ensureItemLabels(db) };
  return res.json({
    ok: true,
    items,
    displayLabels,
    coins: ctx.user.coins || 0,
  });
});

/** Schlanke Label-Overrides für Inventar/Markt (ohne vollen Katalog). */
app.get("/v1/item-labels", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.json({
    ok: true,
    displayLabels: { ...itemLabels.ensureItemLabels(getDb()) },
  });
});

let shopCatalogSeeded = false;
function seedShopCatalogIfNeeded() {
  if (shopCatalogSeeded) return;
  shopCatalog.seedFromStatic(getDb(), {
    emojiPrices: EMOJI_SHOP_PRICES,
    themePrices: THEME_SHOP_PRICES,
    petPrices: PET_SHOP_PRICES,
    stickerPrices: STICKER_SHOP_PRICES,
  });
  shopCatalogSeeded = true;
  try {
    seasonEvents.syncEventShopPets(getDb());
  } catch (_) {
    /* ignore */
  }
}

function resolveShopPrice(kind, itemId) {
  seedShopCatalogIfNeeded();
  const p = shopCatalog.priceOf(getDb(), kind, itemId);
  if (p != null) return p;
  if (kind === "emojis") return Number(EMOJI_SHOP_PRICES[itemId]) || 0;
  if (kind === "themes") return Number(THEME_SHOP_PRICES[itemId]) || 0;
  if (kind === "pets") return Number(PET_SHOP_PRICES[itemId]) || 0;
  if (kind === "stickers") return Number(STICKER_SHOP_PRICES[itemId]) || 0;
  return 0;
}

/** Itemshop: Emoji für Coins kaufen (Mehrfachkauf erlaubt, Limits möglich). */
app.post("/v1/shop/buy-emoji", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const emoji = clipCanvasEmojiId(req.body?.emoji);
  if (!emoji) {
    return res.status(400).json({ error: "unknown_item", message: "Dieses Emoji gibt es im Shop nicht." });
  }
  const check = shopCatalog.canBuy(getDb(), ctx.user, "emojis", emoji);
  // Static-Fallback nur wenn Artikel noch nicht im Katalog — nie bei Disable/Sold-out
  if (!check.ok) {
    if (check.error !== "unknown_item" || EMOJI_SHOP_PRICES[emoji] == null) {
      return res.status(400).json({
        error: check.error || "unknown_item",
        message: check.message || "Dieses Emoji gibt es im Shop nicht.",
      });
    }
  }
  const price = check.ok ? check.price : Number(EMOJI_SHOP_PRICES[emoji]) || 0;
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
  shopCatalog.recordSale(getDb(), ctx.user, "emojis", emoji);
  trackAch(ctx.user, "emojis_bought", 1);
  trackAch(ctx.user, "coins_spent", price);
  // Antwort zuerst — Persistenz verzögert (kein 3s-Warten auf stringify)
  scheduleSave();
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
  seedShopCatalogIfNeeded();
  const themeId = String(req.body?.themeId || "").trim().slice(0, 32);
  if (!themeId) {
    return res.status(400).json({ error: "unknown_item", message: "Hintergrund unbekannt." });
  }
  if (!isKnownInventoryItem("themes", themeId)) {
    return res.status(400).json({ error: "unknown_item", message: "Hintergrund unbekannt." });
  }
  const inv = ensureInventory(ctx.user);
  if (inv.themes.includes(themeId)) {
    return res.json({ ok: true, themeId, alreadyOwned: true, user: publicUser(ctx.user) });
  }
  // Offenes Markt-Listing zählt als Besitz — kein Doppel-Kauf
  {
    const listings = market.ensureMarket(getDb());
    const listed = Object.values(listings).some(
      (e) =>
        e &&
        e.status === "open" &&
        e.sellerId === ctx.user.id &&
        e.kind === "themes" &&
        String(e.itemId) === themeId
    );
    if (listed) {
      return res.status(400).json({
        error: "listed",
        message: "Dieser Hintergrund liegt noch als Angebot auf dem Markt.",
      });
    }
  }
  const check = shopCatalog.canBuy(getDb(), ctx.user, "themes", themeId);
  if (!check.ok) {
    if (check.error !== "unknown_item" || THEME_SHOP_PRICES[themeId] == null) {
      return res.status(400).json({
        error: check.error || "unknown_item",
        message: check.message || "Hintergrund unbekannt.",
      });
    }
  }
  const price = check.ok
    ? check.price
    : Number(THEME_SHOP_PRICES[themeId]) || 0;
  // Gratis nur Starter-Theme meadow — kein Admin-Preis-0-Exploit
  if (price < 1 && themeId !== "meadow") {
    return res.status(400).json({
      error: "invalid_price",
      message: "Hintergrund ist nicht käuflich.",
    });
  }
  if (price < 0) {
    return res.status(400).json({
      error: check.error || "unknown_item",
      message: check.message || "Hintergrund unbekannt.",
    });
  }
  if (price > 0 && !requireCoins(ctx, price, res)) return;
  if (price > 0 && !applyLedger(ctx.user.id, -price, "buy_theme", themeId)) {
    return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
  }
  inv.themes.push(themeId);
  bumpShopPurchase("themes", themeId);
  shopCatalog.recordSale(getDb(), ctx.user, "themes", themeId);
  if (price > 0) trackAch(ctx.user, "coins_spent", price);
  trackAch(ctx.user, "themes_bought", 1);
  scheduleSave();
  return res.json({ ok: true, themeId, price, user: publicUser(ctx.user) });
});

app.post("/v1/shop/buy-sticker", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const emoji = clipCanvasEmojiId(req.body?.emoji);
  if (!emoji) {
    return res.status(400).json({ error: "unknown_item", message: "Sticker unbekannt." });
  }
  const check = shopCatalog.canBuy(getDb(), ctx.user, "stickers", emoji);
  if (!check.ok) {
    if (check.error !== "unknown_item" || STICKER_SHOP_PRICES[emoji] == null) {
      return res.status(400).json({
        error: check.error || "unknown_item",
        message: check.message || "Sticker unbekannt.",
      });
    }
  }
  const price = check.ok
    ? check.price
    : Number(STICKER_SHOP_PRICES[emoji]) || 0;
  if (price < 1) {
    return res.status(400).json({
      error: check.error || "unknown_item",
      message: check.message || "Sticker unbekannt.",
    });
  }
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
  shopCatalog.recordSale(getDb(), ctx.user, "stickers", emoji);
  trackAch(ctx.user, "stickers_bought", 1);
  trackAch(ctx.user, "coins_spent", price);
  scheduleSave();
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
  seedShopCatalogIfNeeded();
  seasonEvents.syncEventShopPets(getDb());
  const emoji = normalizePetId(req.body?.emoji);
  if (!emoji) {
    return res.status(400).json({ error: "unknown_item", message: "Begleiter unbekannt." });
  }
  if (!isKnownInventoryItem("pets", emoji)) {
    return res.status(400).json({ error: "unknown_item", message: "Begleiter unbekannt." });
  }
  const inv = ensureInventory(ctx.user);
  const check = shopCatalog.canBuy(getDb(), ctx.user, "pets", emoji);
  if (!check.ok) {
    if (check.error !== "unknown_item" || PET_SHOP_PRICES[emoji] == null) {
      return res.status(400).json({
        error: check.error || "unknown_item",
        message: check.message || "Begleiter unbekannt.",
      });
    }
  }
  const price = check.ok
    ? check.price
    : Number(PET_SHOP_PRICES[emoji]) || 0;
  if (price < 0) {
    return res.status(400).json({
      error: check.error || "unknown_item",
      message: check.message || "Begleiter unbekannt.",
    });
  }
  if (price > 0 && !requireCoins(ctx, price, res)) return;
  if (price > 0) {
    if (!applyLedger(ctx.user.id, -price, "buy_pet", emoji)) {
      return res.status(402).json({ error: "no_coins", message: "Nicht genug Coins." });
    }
    trackAch(ctx.user, "coins_spent", price);
  }
  const owned = addPetCount(inv, emoji, 1);
  bumpShopPurchase("pets", emoji);
  shopCatalog.recordSale(getDb(), ctx.user, "pets", emoji);
  trackAch(ctx.user, "pets_bought", 1);
  syncAchInventoryMetrics(ctx.user);
  scheduleSave();
  return res.json({ ok: true, emoji, owned, price, user: publicUser(ctx.user) });
});

app.post("/v1/me/equip-pet", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const emoji = normalizePetId(req.body?.emoji);
  const inv = ensureInventory(ctx.user);
  if (!emoji || !petOwned(inv, emoji)) {
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
  // Verheiratet → Ehering; sonst Ring entfernen (kein Ehepartner vor beiden Ja)
  const myM = marriage.findMarriageForUser(getDb(), ctx.user.id);
  if (myM && myM.status === "married") {
    marriage.grantMarriageItem(ctx.user);
    ach.setMetricAtLeast(ctx.user, "married", 1, todayKey(), null);
    tryClaimAchievementReward(ctx.user, "fs_married");
  } else {
    marriage.clearMarriageItem(ctx.user);
  }
  repairAchievementItemRewards(ctx.user);
  const inv = ensureInventory(ctx.user);
  seedShopCatalogIfNeeded();
  const db = getDb();
  // Custom-/Shop-Hintergründe inkl. visualConfig — auch wenn nicht mehr im Public-Shop
  const themeDetails = (Array.isArray(inv.themes) ? inv.themes : [])
    .map((id) => {
      const tid = String(id || "").trim();
      if (!tid) return null;
      const item = shopCatalog.getItem(db, "themes", tid);
      if (item) {
        return shopCatalog.publicItem(item, Date.now(), { admin: true, db });
      }
      return {
        kind: "themes",
        itemId: tid,
        label: itemLabels.resolveDisplayLabel(db, "themes", tid, null, tid),
        emoji: "🖼️",
        priceCoins: 0,
        // visualConfig weglassen — Client behält vorhandenen Cache
      };
    })
    .filter(Boolean);
  // Kein scheduleSave bei jedem Inventar-GET — Full-JSON-Write machte Tabs spürbar träge
  return res.json({
    ok: true,
    emojis: inv.emojis,
    themes: inv.themes,
    themeDetails,
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
    coordSpace: t.coordSpace === "square" ? "square" : "canvas",
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
  const coordSpace =
    String(req.body?.coordSpace || "").toLowerCase() === "square" ? "square" : "canvas";
  const entry = {
    id: `tpl_${crypto.randomBytes(8).toString("hex")}`,
    strokes,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    coordSpace,
  };
  list.push(entry);
  trackAch(ctx.user, "templates_created", 1);
  scheduleSave();
  return res.status(201).json({ ok: true, template: publicDrawTemplate(entry) });
});

app.put("/v1/me/templates/:id", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const strokes = sanitizeTemplateParts(req.body?.strokes);
  if (!strokes || !strokes.length) {
    return res.status(400).json({ error: "empty", message: "Bitte etwas zeichnen." });
  }
  const list = ensureDrawTemplates(ctx.user);
  const entry = list.find((t) => t && t.id === id);
  if (!entry) return res.status(404).json({ error: "not_found" });
  entry.strokes = strokes;
  entry.coordSpace =
    String(req.body?.coordSpace || entry.coordSpace || "").toLowerCase() === "square"
      ? "square"
      : "canvas";
  entry.updatedAt = Date.now();
  scheduleSave();
  return res.json({ ok: true, template: publicDrawTemplate(entry) });
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
  const day = todayKey();
  const beforeDay = ctx.user.achievements?.daily?.date || null;
  const beforeActive = ctx.user.achievements?.progress?._activeDayMarked || null;
  const beforeUnlocked = Object.keys(ctx.user.achievements?.unlocked || {}).length;
  syncAchInventoryMetrics(ctx.user);
  const a = ach.ensureDaily(ctx.user, day);
  if (a.progress._activeDayMarked !== day) {
    a.progress._activeDayMarked = day;
    trackAch(ctx.user, "active_days", 1);
  }
  // Nachträglich Items nachreichen (z. B. Kapelle nach Katalog-Fix)
  const repaired = repairAchievementItemRewards(ctx.user);
  if (repaired > 0) syncAchInventoryMetrics(ctx.user);
  const afterUnlocked = Object.keys(ctx.user.achievements?.unlocked || {}).length;
  const dirty =
    repaired > 0 ||
    beforeDay !== (ctx.user.achievements?.daily?.date || null) ||
    beforeActive !== (ctx.user.achievements?.progress?._activeDayMarked || null) ||
    afterUnlocked !== beforeUnlocked;
  // Store nur bei echter Änderung — sonst blockiert jedes Tab-Öffnen mit großem JSON.stringify
  if (dirty) scheduleSave();
  return res.json({ ok: true, ...achPublicState(ctx.user) });
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
    state: achPublicState(ctx.user),
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
    streak: result.streak,
    state: achPublicState(ctx.user),
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
    (user, kind, itemId) => userAlreadyOwnsUnique(user, kind, itemId),
    achDailyCap()
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
  // Nur wirklich seltene Erfolge (Pet-Belohnung oder sehr hohe Ziele)
  try {
    const def = (ach.ACHIEVEMENTS || []).find((a) => a.id === req.params.id);
    const ri = def?.rewardItem;
    const target = Math.floor(Number(def?.target) || 0);
    const rare =
      Boolean(result.itemGranted) &&
      ri &&
      (ri.kind === "pets" || ri.kind === "themes" || target >= 50);
    if (rare) {
      const nick = String(ctx.user.nickname || "Jemand").trim().slice(0, 18) || "Jemand";
      homeFeed.publish(getDb(), {
        kind: "achievement",
        shortText: `${nick}: ${String(def?.title || "Erfolg").slice(0, 40)}`,
        title: `${nick} hat einen seltenen Erfolg`,
        body:
          `${nick} hat „${String(def?.title || "einen Erfolg")}“ freigeschaltet` +
          (def?.desc ? `: ${String(def.desc).slice(0, 120)}` : ".") +
          " und ein besonderes Item erhalten.",
        ttlMs: homeFeed.DEFAULT_TTL_MS,
        actionType: "profile",
        actionPayload: { userId: ctx.user.id, nickname: nick },
      });
    }
  } catch (e) {
    console.error("home feed achievement failed", e);
  }
  scheduleSave();
  return res.json({
    ok: true,
    coinsGranted: result.coinsGranted || 0,
    coinsRequested: result.coinsRequested || 0,
    partial: Boolean(result.partial),
    itemGranted: result.itemGranted || null,
    achievementId: result.achievementId,
    state: achPublicState(ctx.user),
    user: publicUser(ctx.user),
  });
});

/** —— Spieler-Marktplatz (Nasebär-Stil) —— */
function clipMarketItemId(raw) {
  const e = String(raw || "").trim();
  if (!e) return "";
  if (e.toLowerCase().startsWith("img_") || e.toLowerCase().startsWith("theme_")) {
    return e.slice(0, PET_ID_MAX);
  }
  return e.slice(0, PET_ID_MAX);
}

function friendlyMarketLabel(kind, id, rawLabel) {
  const override = itemLabels.getDisplayLabel(getDb(), kind, id);
  if (override) return override.slice(0, 40);
  const lab = String(rawLabel || "").trim();
  const looksRaw = /^img_/i.test(lab) || /^theme_/i.test(lab);
  // Eigenes Label nutzen, solange es nicht nur die technische ID / das nackte Emoji ist
  if (lab && !looksRaw && lab !== id) return lab.slice(0, 40);
  if (kind === "pets") {
    if (/^img_/i.test(id)) return "Bild-Begleiter";
    const named = shopCatalog.displayNameForEmoji(id);
    return named && named !== id ? named.slice(0, 40) : id;
  }
  if (kind === "stickers") {
    if (/^img_/i.test(id)) return "Eigener Sticker";
    return shopCatalog.displayNameForEmoji(id).slice(0, 40);
  }
  if (kind === "emojis") {
    if (/^img_/i.test(id)) return "Eigenes Emoji";
    return shopCatalog.displayNameForEmoji(id).slice(0, 40);
  }
  if (kind === "themes") return /^theme_/i.test(id) ? "Eigener Hintergrund" : (lab || id).slice(0, 40);
  return shopCatalog.displayNameForEmoji(id).slice(0, 40) || "Item";
}

function marketItemMeta(kind, itemId) {
  const id = clipMarketItemId(itemId);
  if (!id || !["pets", "themes", "stickers", "emojis"].includes(kind)) return null;
  if (!isKnownInventoryItem(kind, id) && !shopCatalog.isDeleted(getDb(), kind, id)) {
    // Legacy-/Composer ohne Katalog: weiter erlauben, wenn Flag/Default greift
  }
  const sellable = marketSellableFor(kind, id);
  const label =
    kind === "pets" && id === marriage.MARRIAGE_PET
      ? marriage.MARRIAGE_PET_LABEL
      : kind === "stickers" && id === marriage.MARRIAGE_CHAPEL_STICKER
        ? "Kapelle"
        : friendlyMarketLabel(kind, id, id);
  seedShopCatalogIfNeeded();
  const cat = shopCatalog.getItem(getDb(), kind, id);
  if (cat) {
    const pub = shopCatalog.publicItem(cat, Date.now(), { admin: false, db: getDb() });
    return {
      category: kind,
      emoji: (pub && pub.emoji) || (kind === "themes" ? "🖼️" : id),
      label: friendlyMarketLabel(kind, id, (pub && pub.label) || cat.label || label),
      sellable,
    };
  }
  if (kind === "pets" && PET_SHOP_PRICES[id] != null) {
    return { category: "pets", emoji: id, label: friendlyMarketLabel(kind, id, label), sellable };
  }
  if (kind === "themes" && THEME_SHOP_PRICES[id] != null) {
    return { category: "themes", emoji: "🖼️", label: friendlyMarketLabel(kind, id, label), sellable };
  }
  if (kind === "stickers" && STICKER_SHOP_PRICES[id] != null) {
    return { category: "stickers", emoji: id, label: friendlyMarketLabel(kind, id, label), sellable };
  }
  if (kind === "emojis" && EMOJI_SHOP_PRICES[id] != null) {
    return { category: "emojis", emoji: id, label: friendlyMarketLabel(kind, id, label), sellable };
  }
  if (kind === "stickers" || kind === "emojis" || kind === "pets" || kind === "themes") {
    return {
      category: kind,
      emoji: kind === "themes" ? "🖼️" : id,
      label: friendlyMarketLabel(kind, id, label),
      sellable,
    };
  }
  return null;
}

/** Markt-Hub-Kacheln: neueste Angebote + meistgekaufte Shop-Items. */
app.get("/v1/market/hub", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const ip = clientIp(req);
  const now = Date.now();
  if (
    !marketHubCache.marketNewest ||
    !marketHubCache.shopTop ||
    now - marketHubCache.at > MARKET_HUB_CACHE_MS
  ) {
    marketHubCache = {
      at: now,
      marketNewest: newestMarketListings(2),
      shopTop: topShopPurchases(2),
    };
  }
  return res.json({
    ok: true,
    marketNewest: marketHubCache.marketNewest,
    shopTop: marketHubCache.shopTop,
    coinNewest: listShopPacks(ctx.user, ip).slice(0, 2),
  });
});

function attachPriceInsight(kind, itemId) {
  const shop = shopItemPrice(kind, itemId);
  return market.priceInsight(getDb(), kind, itemId, shop > 0 ? shop : null);
}

app.get("/v1/market", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  sweepExpiredMarketListings();
  trackAch(ctx.user, "market_opens", 1);
  const mode = String(req.query.mode || "market") === "private" ? "private" : "market";
  const category = String(req.query.category || "all").trim() || "all";
  const q = String(req.query.q || "").trim().slice(0, 40);
  const data = market.aggregateMarket(getDb(), ctx.user.id, { category, q, mode });
  const items = (data.items || []).map((it) => {
    const priceInsight = attachPriceInsight(it.kind, it.itemId);
    return {
      ...it,
      priceInsight,
      trend: priceInsight.trend || it.trend,
    };
  });
  return res.json({
    ok: true,
    ...data,
    items,
    priceWindowDays: market.priceWindowDays(getDb()),
  });
});

/** Angebote zu einem Item (Nasebär-Drill-down). */
app.get("/v1/market/offers", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const mode = String(req.query.mode || "market") === "private" ? "private" : "market";
  const kind = String(req.query.kind || "").trim();
  const itemId = clipMarketItemId(req.query.itemId);
  if (!["pets", "themes", "stickers", "emojis"].includes(kind) || !itemId) {
    return res.status(400).json({ error: "bad_item", message: "Item ungültig." });
  }
  const data = market.listOffersForItem(getDb(), ctx.user.id, { kind, itemId, mode });
  const priceInsight = attachPriceInsight(kind, itemId);
  return res.json({
    ok: true,
    ...data,
    priceInsight,
    priceWindowDays: market.priceWindowDays(getDb()),
  });
});

/** Preis-Infos für Angebot erstellen / Detail. */
app.get("/v1/market/item-price", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const kind = String(req.query.kind || "").trim();
  const itemId = clipMarketItemId(req.query.itemId);
  if (!["pets", "themes", "stickers", "emojis"].includes(kind) || !itemId) {
    return res.status(400).json({ error: "bad_item", message: "Item ungültig." });
  }
  const priceInsight = attachPriceInsight(kind, itemId);
  return res.json({
    ok: true,
    kind,
    itemId,
    priceInsight,
    priceWindowDays: market.priceWindowDays(getDb()),
  });
});

/** Bulk: letzter Verkauf + Shop-Preis für Inventar-Preisanzeige */
app.post("/v1/market/price-hints", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const raw = Array.isArray(req.body?.items) ? req.body.items : [];
  const hints = {};
  for (const row of raw.slice(0, 220)) {
    const kind = String(row?.kind || "").trim();
    const itemId = clipMarketItemId(row?.itemId ?? row?.id);
    if (!["pets", "themes", "stickers", "emojis"].includes(kind) || !itemId) continue;
    const key = `${kind}:${itemId}`;
    if (hints[key]) continue;
    const insight = attachPriceInsight(kind, itemId);
    hints[key] = {
      lastSalePrice: insight.lastSalePrice || null,
      shopPrice: insight.shopPrice || null,
    };
  }
  return res.json({ ok: true, hints });
});

app.get("/v1/admin/market-settings", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  return res.json({
    ok: true,
    priceWindowDays: market.priceWindowDays(getDb()),
    options: market.PRICE_WINDOW_OPTIONS,
    achievementDailyCap: achDailyCap(),
    achievementDailyCapMin: ach.ACHIEVEMENT_DAILY_CAP_MIN,
    achievementDailyCapMax: ach.ACHIEVEMENT_DAILY_CAP_MAX,
  });
});

function applyMarketSettings(req, res) {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const db = getDb();
  const out = {
    ok: true,
    priceWindowDays: market.priceWindowDays(db),
    options: market.PRICE_WINDOW_OPTIONS,
    achievementDailyCap: achDailyCap(),
    achievementDailyCapMin: ach.ACHIEVEMENT_DAILY_CAP_MIN,
    achievementDailyCapMax: ach.ACHIEVEMENT_DAILY_CAP_MAX,
  };
  if (req.body?.priceWindowDays !== undefined && req.body?.priceWindowDays !== null) {
    const result = market.setPriceWindowDays(db, req.body.priceWindowDays);
    if (!result.ok) {
      return res.status(400).json({
        error: "bad_window",
        message: `Erlaubt: ${market.PRICE_WINDOW_OPTIONS.join(", ")} Tage.`,
      });
    }
    out.priceWindowDays = result.priceWindowDays;
    out.options = result.options;
  }
  if (
    req.body?.achievementDailyCap !== undefined &&
    req.body?.achievementDailyCap !== null &&
    req.body?.achievementDailyCap !== ""
  ) {
    const capResult = ach.setAchievementDailyCap(db, req.body.achievementDailyCap);
    if (!capResult.ok) {
      return res.status(400).json({
        error: "bad_achievement_cap",
        message: capResult.message,
      });
    }
    out.achievementDailyCap = capResult.achievementDailyCap;
  }
  flushSave();
  return res.json(out);
}
app.put("/v1/admin/market-settings", applyMarketSettings);
app.post("/v1/admin/market-settings", applyMarketSettings);

/** Alle Items (Shop + Erfolg + Code + Starter) inkl. Markt-Handelbarkeit */
app.get("/v1/admin/items/universe", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const data = itemTrade.listItemUniverse(getDb(), {
    shopCatalog,
    prices: {
      emojiPrices: EMOJI_SHOP_PRICES,
      themePrices: THEME_SHOP_PRICES,
      petPrices: PET_SHOP_PRICES,
      stickerPrices: STICKER_SHOP_PRICES,
    },
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
    q: String(req.query?.q || "").trim().slice(0, 40),
    kind: String(req.query?.kind || "").trim(),
    source: String(req.query?.source || "").trim(),
  });
  return res.json({ ok: true, ...data });
});

function applyItemMarketSellable(req, res) {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const kind = String(req.params.kind || "").trim();
  const itemId = clipMarketItemId(req.params.itemId);
  const want = Boolean(req.body?.marketSellable ?? req.body?.sellable ?? true);
  const result = itemTrade.setMarketSellable(getDb(), kind, itemId, want, {
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
  });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  return res.json({ ok: true, ...result });
}
app.put("/v1/admin/items/:kind/:itemId/market-sellable", applyItemMarketSellable);
app.post("/v1/admin/items/:kind/:itemId/market-sellable", applyItemMarketSellable);

function applyItemLootboxEligible(req, res) {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const kind = String(req.params.kind || "").trim();
  const itemId = clipMarketItemId(req.params.itemId);
  if (seasonEvents.isEventShopBoundItem(getDb(), kind, itemId) || seasonEvents.isEventOnlyItem(kind, itemId)) {
    return res.status(400).json({
      error: "event_item",
      message: "Event-Items sind fest von der Lootbox ausgeschlossen.",
    });
  }
  const want = Boolean(req.body?.lootboxEligible ?? req.body?.eligible ?? true);
  const result = itemTrade.setLootboxEligible(getDb(), kind, itemId, want, {
    defaultPet: DEFAULT_PET,
    starterEmojis: STARTER_EMOJIS,
  });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  invalidateLootboxPoolCache();
  flushSave();
  staffAudit(ctx.user, "item_lootbox", { kind, itemId, lootboxEligible: want });
  return res.json({ ok: true, ...result });
}
app.put("/v1/admin/items/:kind/:itemId/lootbox-eligible", applyItemLootboxEligible);
app.post("/v1/admin/items/:kind/:itemId/lootbox-eligible", applyItemLootboxEligible);

/** Itemshop-Katalog (Staff) */
app.get("/v1/admin/shop/items", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const kind = String(req.query?.kind || "").trim() || null;
  const q = String(req.query?.q || "").trim().slice(0, 40);
  const items = shopCatalog.listPublicCatalog(getDb(), {
    admin: true,
    kind: kind || undefined,
    q,
  });
  return res.json({ ok: true, items, count: items.length });
});

/** Itemshop-Kalender: Fenster, Historie, Kauf- & Markt-Stats */
app.get("/v1/admin/shop/calendar", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  shopCalendar.ensureDefaultExpensiveRotation(getDb());
  const data = shopCalendar.listCalendar(getDb(), {
    kind: String(req.query?.kind || "").trim() || null,
    q: String(req.query?.q || "").trim().slice(0, 40),
    status: String(req.query?.status || "").trim(),
    mark: String(req.query?.mark || "").trim(),
  });
  return res.json({ ok: true, ...data });
});

app.get("/v1/admin/shop/calendar/month", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  shopCalendar.ensureDefaultExpensiveRotation(getDb());
  const now = new Date();
  const year = Number(req.query?.year) || now.getFullYear();
  const month = Number(req.query?.month) || now.getMonth() + 1;
  const data = shopCalendar.monthGrid(getDb(), year, month);
  return res.json({ ok: true, ...data });
});

/** Volle Itemliste für einen Shop-Tag (Inventar-Popup) */
app.get("/v1/admin/shop/calendar/day", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const result = shopCalendar.dayInventory(getDb(), String(req.query?.date || "").trim());
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  return res.json(result);
});

app.get("/v1/admin/shop/rotation-plans", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  shopCalendar.ensureDefaultExpensiveRotation(getDb());
  const plans = shopCalendar.listRotationPlans(getDb());
  return res.json({ ok: true, plans });
});

app.post("/v1/admin/shop/rotation-plans", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const result = shopCalendar.upsertRotationPlan(getDb(), req.body || {}, {
    byUserId: ctx.user?.id || null,
  });
  flushSave();
  staffAudit(ctx.user, "shop_rotation_upsert", { id: result.plan?.id });
  return res.json({ ok: true, plan: result.plan });
});

app.put("/v1/admin/shop/rotation-plans/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const id = String(req.params.id || "").trim();
  const result = shopCalendar.upsertRotationPlan(
    getDb(),
    { ...(req.body || {}), id },
    { byUserId: ctx.user?.id || null }
  );
  flushSave();
  staffAudit(ctx.user, "shop_rotation_upsert", { id });
  return res.json({ ok: true, plan: result.plan });
});

app.delete("/v1/admin/shop/rotation-plans/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  const result = shopCalendar.deleteRotationPlan(getDb(), id);
  if (!result.ok) {
    return res.status(404).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "shop_rotation_delete", { id });
  return res.json({ ok: true });
});

app.post("/v1/admin/shop/rotation-plans/:id/apply", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const id = String(req.params.id || "").trim();
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "rotation_apply",
      payload: { id },
      byUserId: ctx.user.id,
      label: `Rotation neu mischen · ${id}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "rotation_apply", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const plans = shopCalendar.ensureRotationPlans(getDb());
  const plan = plans[id];
  if (!plan) {
    return res.status(404).json({ error: "not_found", message: "Plan nicht gefunden." });
  }
  const result = shopCalendar.applyRotationPlan(getDb(), plan, {
    byUserId: ctx.user?.id || null,
  });
  flushSave();
  return res.json({ ok: true, ...result });
});

/**
 * Nach create/upsert: Lootbox, Marktplatz, Fix/Rotation+Fenster.
 * body: marketSellable, lootboxEligible, joinRotation, cycleFrom, cycleUntil, rotationPlanId, rotationLocked
 */
function finalizeNewShopItemFlags(body, result, { byUserId = null, kind, eventId } = {}) {
  const itemId = result?.item?.itemId;
  if (!itemId || !kind) return { lootboxEligible: !eventId };
  let lootboxEligible = !eventId;
  if (eventId) {
    itemTrade.setLootboxEligible(getDb(), kind, itemId, false, {
      defaultPet: DEFAULT_PET,
      starterEmojis: STARTER_EMOJIS,
    });
    lootboxEligible = false;
  } else if (body.lootboxEligible !== undefined) {
    const want = Boolean(body.lootboxEligible);
    const lb = itemTrade.setLootboxEligible(getDb(), kind, itemId, want, {
      defaultPet: DEFAULT_PET,
      starterEmojis: STARTER_EMOJIS,
    });
    if (lb.ok) lootboxEligible = want;
  }
  if (!eventId && body.marketSellable !== undefined) {
    itemTrade.setMarketSellable(getDb(), kind, itemId, Boolean(body.marketSellable), {
      defaultPet: DEFAULT_PET,
      starterEmojis: STARTER_EMOJIS,
    });
  }
  if (!eventId && body.rotationLocked === true) {
    // Wie leave-rotation: dauerhaft sichtbar, kein Zeitfenster das den Shop-Filter blockiert
    try {
      shopCalendar.removeItemFromRotation(getDb(), kind, itemId, { byUserId });
    } catch (e) {
      console.error("[shop] finalize fixed/leaveRotation", e);
      const item = shopCatalog.getItem(getDb(), kind, itemId);
      if (item) {
        item.rotationLocked = true;
        item.rotationPlanId = null;
        item.availableFrom = null;
        item.availableUntil = null;
        item.enabled = true;
        item.updatedAt = Date.now();
      }
    }
  } else if (!eventId && body.joinRotation === true && body.rotationLocked !== true) {
    try {
      shopCalendar.rejoinRotationPool(getDb(), kind, itemId, { byUserId });
      if (body.cycleFrom != null || body.cycleUntil != null) {
        shopCalendar.updateAvailability(
          getDb(),
          kind,
          itemId,
          {
            availableFrom: body.cycleFrom ?? undefined,
            availableUntil: body.cycleUntil ?? undefined,
            rotationPlanId:
              body.rotationPlanId || result.item.rotationPlanId || "expensive_3m_week",
            rotationLocked: false,
            enabled: true,
          },
          { byUserId }
        );
      }
    } catch (e) {
      console.error("[shop] finalize joinRotation", e);
    }
  }
  return { lootboxEligible };
}

/** Shop-Änderungs-Warteschlange (bis ≈03:00 Berlin). */
function buildShopQueueHandlers() {
  return {
    leave_rotation: (payload, ctx) =>
      shopCalendar.removeItemFromRotation(getDb(), payload.kind, payload.itemId, {
        byUserId: ctx.byUserId,
      }),
    rejoin_rotation: (payload, ctx) =>
      shopCalendar.rejoinRotationPool(getDb(), payload.kind, payload.itemId, {
        byUserId: ctx.byUserId,
      }),
    calendar_batch: (payload, ctx) =>
      shopCalendar.batchAvailability(getDb(), payload, { byUserId: ctx.byUserId }),
    calendar_update: (payload, ctx) =>
      shopCalendar.updateAvailability(getDb(), payload.kind, payload.itemId, payload.patch || {}, {
        byUserId: ctx.byUserId,
      }),
    item_enable: (payload, ctx) =>
      shopCatalog.setEnabled(getDb(), payload.kind, payload.itemId, true, {
        byUserId: ctx.byUserId,
      }),
    item_disable: (payload, ctx) =>
      shopCatalog.setEnabled(getDb(), payload.kind, payload.itemId, false, {
        byUserId: ctx.byUserId,
      }),
    item_upsert: (payload, ctx) => {
      const body = { ...(payload || {}) };
      delete body.schedule;
      delete body.imageBase64;
      delete body.imageDataUrl;
      const kind = String(body.kind || "").trim();
      const itemId = String(body.itemId || "").trim();
      const eventId = String(body.eventId || "")
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9_]/g, "_")
        .slice(0, 40);
      const eventKinds = new Set(["pets", "stickers", "emojis", "themes"]);
      if (eventKinds.has(kind) && eventId) {
        const db = getDb();
        const cfg = seasonEvents.ensureEventsConfig(db);
        const ev = (cfg.events || []).find((e) => String(e.id) === eventId);
        if (!ev) {
          return { ok: false, error: "bad_event", message: "Event nicht gefunden." };
        }
        let eventYear = Math.floor(Number(body.eventYear) || 0);
        if (!eventYear || eventYear < 2020) {
          eventYear = seasonEvents.shopPetYearForEvent(ev);
        }
        const conflict = seasonEvents.findEventKindConflict(
          db,
          eventId,
          kind,
          eventYear,
          itemId
        );
        if (conflict) {
          return {
            ok: false,
            error: "event_kind_taken",
            message: `Für dieses Event gibt es in ${eventYear} schon ein ${kind}-Item (${conflict.itemId}).`,
          };
        }
        body.eventYear = eventYear;
        body.rotationLocked = true;
      }
      const result = shopCatalog.upsertItem(getDb(), body, { byUserId: ctx.byUserId });
      if (result.ok) {
        try {
          seasonEvents.syncEventShopPets(getDb());
        } catch (_) {
          /* ignore */
        }
        finalizeNewShopItemFlags(body, result, {
          byUserId: ctx.byUserId,
          kind,
          eventId,
        });
        invalidateLootboxPoolCache();
      }
      return result;
    },
    rotation_apply: (payload, ctx) => {
      const id = String(payload?.id || "").trim();
      const plans = shopCalendar.ensureRotationPlans(getDb());
      const plan = plans[id];
      if (!plan) return { ok: false, error: "not_found", message: "Plan nicht gefunden." };
      return shopCalendar.applyRotationPlan(getDb(), plan, { byUserId: ctx.byUserId });
    },
  };
}

app.get("/v1/admin/shop/change-queue", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const includeDone = String(req.query?.all || "") === "1";
  return res.json({
    ok: true,
    jobs: shopChangeQueue.listJobs(getDb(), { includeDone }),
    pending: shopChangeQueue.listJobs(getDb()).length,
  });
});

app.delete("/v1/admin/shop/change-queue/:id", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const result = shopChangeQueue.cancelJob(getDb(), req.params.id);
  if (!result.ok) {
    return res.status(400).json(result);
  }
  scheduleSave();
  return res.json(result);
});

/** Warteschlange sofort abarbeiten (ohne auf 03:00 zu warten). */
app.post("/v1/admin/shop/change-queue/flush", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const result = shopChangeQueue.processPending(getDb(), buildShopQueueHandlers(), {
    byUserId: ctx.user.id,
  });
  flushSave();
  staffAudit(ctx.user, "shop_queue_flush", { processed: result.processed });
  return res.json(result);
});

app.post("/v1/admin/shop/calendar/:kind/:itemId/leave-rotation", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  const payload = { kind, itemId };
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "leave_rotation",
      payload,
      byUserId: ctx.user.id,
      label: `Zyklus raus · ${kind}:${itemId}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "leave_rotation", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const result = shopCalendar.removeItemFromRotation(getDb(), kind, itemId, {
    byUserId: ctx.user?.id || null,
  });
  if (!result.ok) {
    return res.status(404).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "shop_rotation_leave", { kind, itemId });
  return res.json(result);
});

app.post("/v1/admin/shop/calendar/:kind/:itemId/rejoin-rotation", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  const payload = { kind, itemId };
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "rejoin_rotation",
      payload,
      byUserId: ctx.user.id,
      label: `Zyklus rein · ${kind}:${itemId}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "rejoin_rotation", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const result = shopCalendar.rejoinRotationPool(getDb(), kind, itemId, {
    byUserId: ctx.user?.id || null,
  });
  if (!result.ok) {
    return res.status(404).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "shop_rotation_rejoin", { kind, itemId });
  return res.json(result);
});

app.post("/v1/admin/shop/calendar/batch", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const body = { ...(req.body || {}) };
  delete body.schedule;
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "calendar_batch",
      payload: body,
      byUserId: ctx.user.id,
      label: `Kalender-Batch (${(body.itemKeys || body.items || []).length || "?"} Items)`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "calendar_batch", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const result = shopCalendar.batchAvailability(getDb(), body, {
    byUserId: ctx.user?.id || null,
  });
  flushSave();
  staffAudit(ctx.user, "shop_calendar_batch", { count: result.count });
  return res.json(result);
});

app.put("/v1/admin/shop/calendar/:kind/:itemId", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  const body = { ...(req.body || {}) };
  delete body.schedule;
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "calendar_update",
      payload: { kind, itemId, patch: body },
      byUserId: ctx.user.id,
      label: `Fenster · ${kind}:${itemId}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "calendar_update", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const result = shopCalendar.updateAvailability(getDb(), kind, itemId, body, {
    byUserId: ctx.user?.id || null,
  });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  flushSave();
  staffAudit(ctx.user, "shop_calendar", { kind, itemId });
  return res.json({ ok: true, item: result.item });
});

function applyPetImageFromBody(body, itemId) {
  const img = body?.imageBase64 || body?.imageDataUrl || null;
  if (!img) {
    return { hasImage: petImages.hasImage(itemId) };
  }
  const saved = petImages.saveImage(itemId, img);
  if (!saved.ok) return saved;
  return { ok: true, hasImage: true, itemId: saved.itemId || itemId };
}

app.post("/v1/admin/shop/items", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const body = { ...(req.body || {}) };
  let itemId = String(body.itemId || "").trim();
  const kind = String(body.kind || "").trim();
  const eventId = String(body.eventId || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .slice(0, 40);
  let eventYear = Math.floor(Number(body.eventYear) || 0);

  // Event-Shop-Item (Begleiter/Sticker/Emoji/Hintergrund): 1× pro Kategorie+Jahr
  const eventKinds = new Set(["pets", "stickers", "emojis", "themes"]);
  if (eventKinds.has(kind) && eventId) {
    const db = getDb();
    const cfg = seasonEvents.ensureEventsConfig(db);
    const ev = (cfg.events || []).find((e) => String(e.id) === eventId);
    if (!ev) {
      return res.status(400).json({
        error: "bad_event",
        message: "Event nicht gefunden.",
      });
    }
    if (!eventYear || eventYear < 2020) {
      eventYear = seasonEvents.shopPetYearForEvent(ev);
    }
    const conflict = seasonEvents.findEventKindConflict(db, eventId, kind, eventYear, itemId);
    if (conflict) {
      return res.status(400).json({
        error: "event_kind_taken",
        message: `Für dieses Event gibt es in ${eventYear} schon ein ${kind}-Item (${conflict.itemId}).`,
      });
    }
    const preferred = seasonEvents.eventShopItemId(eventId, eventYear, kind);
    const hasImgPayload =
      body.petSource === "image" || body.imageBase64 || body.imageDataUrl;
    // Bild-/Theme-Items: feste Event-ID; Unicode-Emoji/Sticker: Client-ID behalten
    if (kind === "themes") {
      if (
        !itemId ||
        itemId === "💖" ||
        (!/^theme_[a-z0-9_]+_t\d{4}$/i.test(itemId) &&
          !/^theme_[a-z0-9]{4,24}$/i.test(itemId))
      ) {
        if (!shopCatalog.getItem(db, kind, preferred)) itemId = preferred;
      }
    } else if (hasImgPayload || /^img_/i.test(itemId)) {
      if (!shopCatalog.getItem(db, kind, preferred)) {
        itemId = preferred;
      }
    } else if (!itemId) {
      if (!shopCatalog.getItem(db, kind, preferred)) itemId = preferred;
    }
    body.itemId = itemId;
    body.eventId = eventId;
    body.eventYear = eventYear;
    body.rotationLocked = true;
    if (body.priceCoins == null || Number(body.priceCoins) < 1) {
      body.priceCoins = kind === "themes" ? 80 : 200;
    }
    const win = seasonEvents.windowMsForEventYear(ev, eventYear);
    if (win) {
      body.availableFrom = win.from;
      body.availableUntil = win.until;
    }
    const pub = seasonEvents.publicEvent(ev);
    if (!body.previewEmoji) body.previewEmoji = pub?.emoji || "🎁";
    const kindDe =
      kind === "pets"
        ? "begleiter"
        : kind === "stickers"
          ? "sticker"
          : kind === "emojis"
            ? "emoji"
            : "hintergrund";
    if (!String(body.searchText || "").trim()) {
      body.searchText = `${body.previewEmoji} ${pub?.title || eventId} ${kindDe} event ${eventYear}`;
    }
    if (!String(body.label || "").trim()) {
      const suffix =
        kind === "pets"
          ? "Begleiter"
          : kind === "stickers"
            ? "Sticker"
            : kind === "emojis"
              ? "Emoji"
              : "Hintergrund";
      body.label = `${pub?.title || "Event"}-${suffix}`.slice(0, 40);
    }
  }

  // Custom-Bild (Begleiter / Emoji / Sticker): stabile, eindeutige ID
  const imageKinds = new Set(["pets", "emojis", "stickers"]);
  if (
    imageKinds.has(kind) &&
    (body.petSource === "image" || body.imageBase64 || body.imageDataUrl)
  ) {
    const db = getDb();
    // Event-Item: preferred ID behalten wenn gesetzt und frei/eigen
    const keepEventId =
      eventId &&
      (isEventShopPetId(itemId) || /^img_ev_/i.test(itemId) || /^img_event_/i.test(itemId)) &&
      (!shopCatalog.getItem(db, kind, itemId) ||
        String(shopCatalog.getItem(db, kind, itemId)?.eventId || "") === eventId);
    // img_new / Emoji / Kollision → immer neue ID (sonst falsches Bild für alle)
    if (
      !keepEventId &&
      (!isStableImageItemId(itemId) || shopCatalog.getItem(db, kind, itemId))
    ) {
      itemId = allocateImageItemId(db, kind);
    }
    body.itemId = itemId;
    const imgResult = applyPetImageFromBody(body, itemId);
    if (imgResult.ok === false) {
      return res.status(400).json({ error: imgResult.error, message: imgResult.message });
    }
    body.hasImage = Boolean(imgResult.hasImage);
  }
  // Hintergründe: ID — Event behält preferred theme_*_tYEAR, sonst auto
  if (kind === "themes") {
    const db = getDb();
    const keepEventTheme =
      eventId &&
      /^theme_[a-z0-9_]+_t\d{4}$/i.test(itemId) &&
      (!shopCatalog.getItem(db, kind, itemId) ||
        String(shopCatalog.getItem(db, kind, itemId)?.eventId || "") === eventId);
    const stable = /^theme_[a-z0-9]{4,24}$/i.test(itemId) || keepEventTheme;
    if (!keepEventTheme && (!stable || shopCatalog.getItem(db, kind, itemId) || itemId === "💖")) {
      for (let i = 0; i < 40; i++) {
        const id = `theme_${crypto.randomBytes(4).toString("hex")}`;
        if (!shopCatalog.getItem(db, kind, id)) {
          itemId = id;
          break;
        }
      }
    }
    body.itemId = itemId;
  }

  // Bild-Uploads nie in die Queue (zu groß) — sonst optional Warteschlange bis ≈03:00
  const hasImgPayload = Boolean(body.imageBase64 || body.imageDataUrl);
  if (
    !hasImgPayload &&
    shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance"
  ) {
    const payload = { ...body };
    delete payload.schedule;
    delete payload.imageBase64;
    delete payload.imageDataUrl;
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "item_upsert",
      payload,
      byUserId: ctx.user.id,
      label: `Neu · ${kind}:${payload.itemId || "?"}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "item_upsert", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }

  const result = shopCatalog.upsertItem(getDb(), body, { byUserId: ctx.user?.id || null });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  try {
    seasonEvents.syncEventShopPets(getDb());
  } catch (_) {
    /* ignore */
  }
  const { lootboxEligible } = finalizeNewShopItemFlags(body, result, {
    byUserId: ctx.user?.id || null,
    kind,
    eventId,
  });
  invalidateLootboxPoolCache();
  scheduleSave();
  const fresh = shopCatalog.getItem(getDb(), kind, result.item.itemId);
  return res.json({
    ok: true,
    item: fresh
      ? shopCatalog.publicItem(fresh, Date.now(), { admin: true, db: getDb() })
      : result.item,
    lootbox: lootboxEligible,
    marketSellable: Boolean(body.marketSellable),
    chromaKey: petImages.CHROMA_KEY_HEX,
    ...(hasImgPayload && shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance"
      ? {
          note: "Bild-Items werden immer sofort angelegt (nicht queuebar).",
        }
      : {}),
  });
});

app.put("/v1/admin/shop/items/:kind/:itemId", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  const body = { ...(req.body || {}), kind, itemId };
  const eventId = String(body.eventId || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .slice(0, 40);
  const eventKindsPut = new Set(["pets", "stickers", "emojis", "themes"]);
  if (eventKindsPut.has(kind) && eventId) {
    const db = getDb();
    const cfg = seasonEvents.ensureEventsConfig(db);
    const ev = (cfg.events || []).find((e) => String(e.id) === eventId);
    if (!ev) {
      return res.status(400).json({
        error: "bad_event",
        message: "Event nicht gefunden.",
      });
    }
    let eventYear = Math.floor(Number(body.eventYear) || 0);
    if (!eventYear || eventYear < 2020) {
      eventYear = seasonEvents.shopPetYearForEvent(ev);
    }
    const conflict = seasonEvents.findEventKindConflict(db, eventId, kind, eventYear, itemId);
    if (conflict) {
      return res.status(400).json({
        error: "event_kind_taken",
        message: `Für dieses Event gibt es in ${eventYear} schon ein ${kind}-Item (${conflict.itemId}).`,
      });
    }
    body.eventId = eventId;
    body.eventYear = eventYear;
    body.rotationLocked = true;
    const win = seasonEvents.windowMsForEventYear(ev, eventYear);
    if (win) {
      body.availableFrom = win.from;
      body.availableUntil = win.until;
    }
    const pub = seasonEvents.publicEvent(ev);
    if (!body.previewEmoji) body.previewEmoji = pub?.emoji || "🎁";
  } else if (eventKindsPut.has(kind) && body.eventId === "") {
    body.eventId = null;
    body.eventYear = null;
  }
  const imageKindsPut = new Set(["pets", "emojis", "stickers"]);
  if (imageKindsPut.has(kind) && (body.imageBase64 || body.imageDataUrl)) {
    const imgResult = applyPetImageFromBody(body, itemId);
    if (imgResult.ok === false) {
      return res.status(400).json({ error: imgResult.error, message: imgResult.message });
    }
    body.hasImage = true;
  } else if (imageKindsPut.has(kind) && petImages.hasImage(itemId)) {
    body.hasImage = true;
  }

  const hasImgPayloadPut = Boolean(req.body?.imageBase64 || req.body?.imageDataUrl);
  if (
    !hasImgPayloadPut &&
    shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance"
  ) {
    const payload = { ...body };
    delete payload.schedule;
    delete payload.imageBase64;
    delete payload.imageDataUrl;
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "item_upsert",
      payload,
      byUserId: ctx.user.id,
      label: `Edit · ${kind}:${itemId}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    staffAudit(ctx.user, "shop_queue", { action: "item_upsert", jobId: enq.job.id });
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }

  const result = shopCatalog.upsertItem(getDb(), body, { byUserId: ctx.user?.id || null });
  if (!result.ok) {
    return res.status(400).json({ error: result.error, message: result.message });
  }
  try {
    seasonEvents.syncEventShopPets(getDb());
  } catch (_) {
    /* ignore */
  }
  if (eventId) {
    itemTrade.setLootboxEligible(getDb(), kind, itemId, false, {
      defaultPet: DEFAULT_PET,
      starterEmojis: STARTER_EMOJIS,
    });
  }
  invalidateLootboxPoolCache();
  scheduleSave();
  return res.json({
    ok: true,
    item: result.item,
    ...(hasImgPayloadPut && shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance"
      ? { note: "Bild-Upload wird immer sofort gespeichert." }
      : {}),
  });
});

app.get("/v1/admin/shop/event-options", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  return res.json({
    ok: true,
    events: seasonEvents.shopEventOptions(getDb()),
  });
});

/** Begleiter-Bild (PNG mit Alpha). Öffentlich lesbar für Shop/Inventar. */
app.get("/v1/shop/pet-image/:itemId", (req, res) => {
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  const buf = petImages.readImage(itemId);
  if (!buf) return res.status(404).json({ error: "not_found" });
  res.setHeader("Content-Type", "image/png");
  res.setHeader("Cache-Control", "public, max-age=604800, immutable");
  return res.send(buf);
});

app.get("/v1/admin/shop/chroma-key", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  return res.json({
    ok: true,
    chromaKey: petImages.CHROMA_KEY_HEX,
    tip: "Mit der Pipette eine Farbe im Bild wählen — die wird transparent. Standard-Vorschlag #00FF00.",
  });
});

app.post("/v1/admin/shop/items/:kind/:itemId/disable", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "item_disable",
      payload: { kind, itemId },
      byUserId: ctx.user.id,
      label: `Shop aus · ${kind}:${itemId}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const result = shopCatalog.setEnabled(getDb(), kind, itemId, false, {
    byUserId: ctx.user?.id || null,
  });
  if (!result.ok) return res.status(404).json({ error: "not_found" });
  scheduleSave();
  return res.json({ ok: true, item: result.item });
});

app.post("/v1/admin/shop/items/:kind/:itemId/enable", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  if (shopChangeQueue.parseSchedule(req.body, req.query) === "maintenance") {
    const enq = shopChangeQueue.enqueue(getDb(), {
      action: "item_enable",
      payload: { kind, itemId },
      byUserId: ctx.user.id,
      label: `Shop an · ${kind}:${itemId}`,
    });
    if (!enq.ok) return res.status(400).json(enq);
    scheduleSave();
    return res.json({
      ok: true,
      queued: true,
      job: enq.job,
      message: "Für nächste Wartung (≈03:00 Berlin) eingeplant.",
    });
  }
  const result = shopCatalog.setEnabled(getDb(), kind, itemId, true, {
    byUserId: ctx.user?.id || null,
  });
  if (!result.ok) return res.status(404).json({ error: "not_found" });
  scheduleSave();
  return res.json({ ok: true, item: result.item });
});

/** Item löschen: Katalog + Inventare + Profile + Lobby-Leinwände + Markt. */
app.delete("/v1/admin/shop/items/:kind/:itemId", (req, res) => {
  const ctx = requireStaff(req, res, "market.settings");
  if (!ctx) return;
  seedShopCatalogIfNeeded();
  const kind = String(req.params.kind || "").trim();
  const itemId = decodeURIComponent(String(req.params.itemId || "")).trim();
  if (!["emojis", "stickers", "themes", "pets"].includes(kind) || !itemId) {
    return res.status(400).json({ error: "bad_item", message: "Ungültiges Item." });
  }
  if (isProtectedShopItem(kind, itemId)) {
    return res.status(400).json({
      error: "protected",
      message: "System-Item (Starter/Erfolg) kann nicht gelöscht werden.",
    });
  }
  const existing = shopCatalog.getItem(getDb(), kind, itemId);
  if (!existing && !shopCatalog.isDeleted(getDb(), kind, itemId)) {
    // Auch ohne Katalogeintrag erlauben, wenn Bild/Inventar existieren (Legacy)
    const maybeKnown =
      (kind === "pets" && petImages.hasImage(itemId)) ||
      PET_SHOP_PRICES[itemId] != null ||
      EMOJI_SHOP_PRICES[itemId] != null ||
      THEME_SHOP_PRICES[itemId] != null ||
      (kind === "stickers" && isKnownSticker(itemId));
    if (!maybeKnown) {
      return res.status(404).json({ error: "not_found", message: "Item nicht gefunden." });
    }
  }
  const purged = purgeShopItemEverywhere(kind, itemId);
  const result = shopCatalog.deleteItem(getDb(), kind, itemId);
  flushSave();
  return res.json({
    ok: true,
    kind,
    itemId,
    purged,
    item: result.item,
  });
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
  const itemId = clipMarketItemId(req.body?.itemId);
  const priceCoins = Math.max(0, Math.min(10_000, Math.floor(Number(req.body?.priceCoins) || 0)));
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
    tradeWantItemId = clipMarketItemId(req.body?.tradeWantItemId);
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
  // Kein Sale-Record beim Einstellen — nur echte Verkäufe zählen für die Spanne
  trackAch(ctx.user, "market_listed", 1);
  if (isPrivate) trackAch(ctx.user, "market_private", 1);
  syncAchInventoryMetrics(ctx.user);
  // Event-Item, das nicht mehr im Shop ist → Home-Feed
  try {
    if (!isPrivate && seasonEvents.isEventOnlyItem(kind, itemId)) {
      const inShop = Boolean(shopCatalog.getItem(getDb(), kind, itemId)?.active);
      if (!inShop) {
        const nick = String(ctx.user.nickname || "Jemand").trim().slice(0, 18) || "Jemand";
        const label = String(meta.label || itemId).slice(0, 40);
        homeFeed.publish(getDb(), {
          kind: "market_event",
          shortText: `${nick} bietet ${meta.emoji || "🎁"} ${label}`,
          title: `Event-Item auf dem Markt`,
          body:
            `${nick} bietet „${label}“ auf dem Marktplatz an — ` +
            `dieses Event-Item ist aktuell nicht mehr im Item- oder Event-Shop erhältlich.`,
          ttlMs: homeFeed.DEFAULT_TTL_MS,
          actionType: "market",
          actionPayload: { listingId: id, kind, itemId },
        });
      }
    }
  } catch (e) {
    console.error("home feed market failed", e);
  }
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
  // Nie unverrechnete Verkäufe droppen — älteste zuerst auszahlen
  while (seller.pendingMarketSales.length > 80) {
    const old = seller.pendingMarketSales.shift();
    if (!old) break;
    const credit = Math.max(0, Math.floor(Number(old.priceCoins) || 0));
    if (credit > 0) {
      applyLedger(seller.id, credit, "market_sale_auto", String(old.id || ""));
    }
  }
  safeGiveItem(ctx.user, entry.kind, entry.itemId);
  market.recordSale(getDb(), entry.kind, entry.itemId, price);
  trackAch(ctx.user, "market_bought", 1);
  trackAch(ctx.user, "coins_spent", price);
  trackAch(seller, "market_sold", 1);
  syncAchInventoryMetrics(ctx.user);
  syncAchInventoryMetrics(seller);
  scheduleSave();
  accountPush.emitUserEvent(getDb(), seller.id, "market_sold", {
    listingId: entry.id,
    kind: entry.kind,
    itemId: entry.itemId,
    emoji: entry.emoji || "📦",
    label: entry.label || entry.itemId,
    priceCoins: price,
    buyerNickname: String(ctx.user.nickname || "Jemand").slice(0, 18),
    message: `${entry.label || entry.itemId} verkauft · ${price} Coins`,
  });
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
  const offerItemId = clipMarketItemId(req.body?.itemId);
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

/** Mollie ist abgeschaltet — Käufe nur noch über Google Play. */
app.post("/v1/shop/checkout", (_req, res) => {
  return res.status(410).json({
    error: "mollie_removed",
    message: "Käufe laufen nur noch über Google Play. Bitte die App aktualisieren.",
  });
});

app.post("/v1/webhooks/mollie", (_req, res) => {
  return res.status(200).send("ok");
});

/**
 * Google Play In-App-Kauf verifizieren und Coins gutschreiben (Consumable).
 * Body: { productId, purchaseToken, orderId?, integrityToken?, integrityNonce? }
 */
app.post("/v1/shop/play-purchase", async (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  if (!ctx.user.googleSub) {
    return res.status(403).json({
      error: "google_required",
      message: "Bitte zuerst mit Google anmelden.",
    });
  }
  if (!shopPurchasesEnabled()) {
    return res.status(503).json({
      error: "shop_disabled",
      message: "Shop kommt bald — bis dahin Daily Coins & Gutscheine.",
    });
  }
  const ip = clientIp(req);
  if (!rateLimit(`play_purchase_user:${ctx.user.id}`, 12, 10 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Kaufversuche. Bitte in ein paar Minuten erneut.",
    });
  }
  if (!rateLimit(`play_purchase_ip:${ip || "unknown"}`, 24, 10 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Kaufversuche aus diesem Netz. Bitte kurz warten.",
    });
  }

  // Play Integrity zusätzlich zur Google-Play-Billing-Prüfung
  if (playIntegrity.isConfigured()) {
    const integrityToken = String(req.body?.integrityToken || "").trim();
    const integrityNonce = String(req.body?.integrityNonce || "").trim();
    if (playIntegrity.isEnforced()) {
      const verdict = await playIntegrity.verifyIntegrityToken(
        integrityToken,
        integrityNonce
      );
      if (!verdict.ok) {
        const missing = !integrityToken || verdict.error === "missing_integrity";
        return res.status(403).json({
          error: verdict.error || "integrity_required",
          updateRequired: missing,
          message: missing
            ? "Bitte aktualisiere LUV im Play Store, um Coins zu kaufen."
            : verdict.message ||
              "Käufe nur über die echte Play-Store-App auf einem Android-Gerät.",
        });
      }
    } else if (integrityToken || integrityNonce) {
      const verdict = await playIntegrity.verifyIntegrityToken(
        integrityToken,
        integrityNonce
      );
      if (!verdict.ok) {
        console.warn(
          "[play-purchase] soft integrity failed",
          verdict.error,
          ctx.user.id
        );
      }
    }
  }

  const productId = String(req.body?.productId || "").trim();
  const purchaseToken = String(req.body?.purchaseToken || "").trim();
  const orderId = String(req.body?.orderId || "").trim() || null;
  if (!productId || !purchaseToken || purchaseToken.length < 10) {
    return res.status(400).json({
      error: "invalid_purchase",
      message: "Ungültige Kaufdaten.",
    });
  }
  const pack = PACKS[productId];
  if (!pack) {
    return res.status(400).json({ error: "invalid_pack", message: "Unbekanntes Paket." });
  }
  if (pack.expiresAt && Date.now() >= pack.expiresAt) {
    return res.status(400).json({
      error: "offer_gone",
      message: "Dieses Angebot ist nicht mehr verfügbar.",
    });
  }
  if (pack.oncePerUserAndIp && !canClaimIntroOffer(ctx.user, ip)) {
    return res.status(403).json({
      error: "offer_used",
      message: "Dieses Angebot hast du schon genutzt.",
    });
  }

  const paymentKey = `play:${purchaseToken}`;
  const db = getDb();
  if (!db.payments || typeof db.payments !== "object") db.payments = {};
  const existing = db.payments[paymentKey];
  if (existing?.credited) {
    return res.json({
      ok: true,
      alreadyCredited: true,
      coinsGranted: 0,
      user: publicUser(ctx.user),
    });
  }
  // Atomare Reservierung vor await — verhindert Doppelgutschrift bei Parallel-Requests
  if (existing?.status === "verifying") {
    return res.status(409).json({
      error: "purchase_busy",
      message: "Kauf wird gerade verbucht. Bitte kurz warten.",
    });
  }
  if (existing?.userId && existing.userId !== ctx.user.id) {
    return res.status(409).json({
      error: "purchase_owned",
      message: "Dieser Kauf ist bereits einem anderen Konto zugeordnet.",
    });
  }
  db.payments[paymentKey] = {
    id: paymentKey,
    provider: "google_play",
    userId: ctx.user.id,
    packId: pack.id,
    coins: Number(pack.coins) || 0,
    quantity: 1,
    orderId: orderId || null,
    purchaseToken,
    ip: ip || "",
    status: "verifying",
    createdAt: existing?.createdAt || Date.now(),
    credited: false,
  };

  let playData;
  try {
    playData = await playBilling.getProductPurchase(productId, purchaseToken);
  } catch (e) {
    console.error("play-purchase verify failed", e.message || e);
    if (db.payments[paymentKey]?.status === "verifying" && !db.payments[paymentKey].credited) {
      delete db.payments[paymentKey];
    }
    return res.status(502).json({
      error: "play_verify_failed",
      message: "Google-Play-Kauf konnte nicht geprüft werden.",
    });
  }

  // Nach await erneut prüfen (zweiter Request könnte inzwischen credited haben)
  if (db.payments[paymentKey]?.credited) {
    return res.json({
      ok: true,
      alreadyCredited: true,
      coinsGranted: 0,
      user: publicUser(ctx.user),
    });
  }

  // 0 = purchased, 1 = canceled, 2 = pending
  if (Number(playData.purchaseState) !== 0) {
    if (db.payments[paymentKey]?.status === "verifying") {
      delete db.payments[paymentKey];
    }
    return res.status(400).json({
      error: "not_purchased",
      message: "Kauf ist noch nicht abgeschlossen.",
    });
  }

  const creditCoins = Number(pack.coins) || 0;
  if (creditCoins <= 0) {
    if (db.payments[paymentKey]?.status === "verifying") {
      delete db.payments[paymentKey];
    }
    return res.status(400).json({ error: "invalid_pack" });
  }

  db.payments[paymentKey] = {
    id: paymentKey,
    provider: "google_play",
    userId: ctx.user.id,
    packId: pack.id,
    coins: creditCoins,
    quantity: 1,
    orderId: orderId || playData.orderId || null,
    purchaseToken,
    ip: ip || "",
    status: "paid",
    createdAt: db.payments[paymentKey]?.createdAt || Date.now(),
    credited: true,
  };
  applyLedger(ctx.user.id, creditCoins, "play_purchase", paymentKey);
  // Durable append-only (survives older pg_dump restores)
  try {
    iapLedger.appendCreditedPurchase(db.payments[paymentKey]);
  } catch (e) {
    console.error("[iap_ledger] append failed", e?.message || e);
  }
  bumpPackPurchase(pack.id, 1);
  if (pack.oncePerUserAndIp) {
    ctx.user.introOfferUsed = true;
    ensureIntroDb(db);
    if (ip) db.introOffersByIp[ip] = ctx.user.id;
  }
  scheduleSave();
  return res.json({
    ok: true,
    coinsGranted: creditCoins,
    user: publicUser(ctx.user),
  });
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
  const considerRandom = (code, room) => {
    if (!room?.isRandom) return;
    healRoomMembership(room);
    const cap = Math.min(MAX_PEERS, Math.max(1, Number(room.capacity) || RANDOM_CAP));
    const count = Array.isArray(room.memberUserIds) ? room.memberUserIds.length : 0;
    if (count >= cap) return;
    if (room.memberUserIds?.includes(ctx.user.id)) {
      targetCode = code;
      targetRoom = room;
      return;
    }
    if (!targetRoom) {
      targetCode = code;
      targetRoom = room;
    }
  };
  for (const [code, room] of rooms.entries()) {
    considerRandom(code, room);
    if (targetRoom?.memberUserIds?.includes(ctx.user.id)) break;
  }
  // Auch persistierte Random-Lobbys (nach Restart) mit freien Plätzen
  if (!targetRoom) {
    const savedRooms = getDb().rooms || {};
    for (const [code, saved] of Object.entries(savedRooms)) {
      if (!saved || typeof saved !== "object" || !saved.isRandom) continue;
      if (rooms.has(code)) continue;
      const room = hydrateRoom(code, saved);
      rooms.set(code, room);
      considerRandom(code, room);
      if (targetRoom?.memberUserIds?.includes(ctx.user.id)) break;
    }
  }

  if (!targetRoom) {
    const hostedCount = Object.keys(hosted || {}).length;
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
      createdByUserId: ctx.user.id,
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
  scheduleSave();
  accountPush.emitUserEvent(
    getDb(),
    ctx.user.id,
    "lootbox_ready",
    { pending: pendingLootboxPublic(ctx.user).length, added: created.length },
    { skipFcm: true }
  );
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
  const grant = grantLootboxReward(ctx.user, entry);
  const shopPrice = Math.floor(Number(entry.shopPrice) || 0);
  if (shopPrice > 500 && !grant.duplicate) {
    try {
      const nick = String(ctx.user.nickname || "Jemand").trim().slice(0, 18) || "Jemand";
      const label = String(entry.label || entry.emoji || "Item").slice(0, 40);
      homeFeed.publish(getDb(), {
        kind: "lootbox",
        shortText: `${nick} zog ${entry.emoji || "🎁"} ${label}`,
        title: `Seltene Lootbox für ${nick}`,
        body:
          `${nick} hat aus einer Lootbox „${label}“ gezogen` +
          ` (Shop-Wert ca. ${shopPrice} Coins).`,
        ttlMs: homeFeed.DEFAULT_TTL_MS,
        actionType: "profile",
        actionPayload: { userId: ctx.user.id, nickname: nick },
      });
    } catch (e) {
      console.error("home feed lootbox failed", e);
    }
  }
  scheduleSave();
  if (!grant.duplicate) {
    accountPush.emitUserEvent(
      getDb(),
      ctx.user.id,
      "inventory_new",
      {
        kind: entry.kind,
        itemId: entry.itemId,
        emoji: entry.emoji,
        label: entry.label,
        source: "lootbox",
      },
      { skipFcm: true }
    );
  }
  accountPush.emitUserEvent(
    getDb(),
    ctx.user.id,
    "lootbox_ready",
    { pending: pendingLootboxPublic(ctx.user).length, added: 0 },
    { skipFcm: true }
  );
  return res.json({
    ok: true,
    item: {
      kind: entry.kind,
      itemId: entry.itemId,
      emoji: entry.emoji,
      label: entry.label,
      shopPrice: entry.shopPrice,
      chancePercent: entry.chancePercent,
      duplicate: Boolean(grant.duplicate),
      coinsRefund: Number(grant.coinsRefund) || 0,
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
  const hostColorSide = normalizeHostColorSide(req.body?.hostColorSide);
  const hostIdx = hostColorIndex(hostColorSide);
  const rawEventId = String(req.body?.eventId || "").trim().slice(0, 64);
  let eventId = null;
  let eventPrompt = null;
  let eventPromptChoices = null;
  let eventEndsAt = null;
  let eventLobbyCfg = null;
  if (rawEventId) {
    const cfg = seasonEvents.ensureEventsConfig(getDb());
    const ev = (cfg.events || []).find((e) => e && e.id === rawEventId);
    if (!ev || ev.enabled === false || !seasonEvents.isActiveAtPatched(ev, new Date())) {
      return res.status(400).json({
        error: "event_inactive",
        message: "Event ist gerade nicht aktiv.",
      });
    }
    const pub = seasonEvents.publicEvent(ev);
    const lobbyCfg = pub?.lobby;
    if (!lobbyCfg || !lobbyCfg.enabled) {
      return res.status(400).json({
        error: "event_lobby_disabled",
        message: "Für dieses Event sind keine Event-Lobbys freigeschaltet.",
      });
    }
    const prog = require("./event_engine").ensureUserProgress(ctx.user, rawEventId);
    if (prog.lobbyCreated) {
      const existingCode = String(prog.lobbyCode || "")
        .toUpperCase()
        .replace(/^LUV-/, "");
      let existing = existingCode ? rooms.get(existingCode) : null;
      if (!existing && existingCode) {
        const saved = getDb().rooms?.[existingCode];
        if (saved && typeof saved === "object") {
          existing = hydrateRoom(existingCode, saved);
          rooms.set(existingCode, existing);
        }
      }
      if (!existing && existingCode) {
        ensureHostedRooms(ctx.user);
        const meta = ctx.user.hostedRooms?.[existingCode];
        if (meta && typeof meta === "object") {
          existing = hydrateRoom(existingCode, {
            token: meta.token,
            createdAt: meta.createdAt,
            hostUserId: ctx.user.id,
            createdByUserId: ctx.user.id,
            name: meta.name || "Event",
            hostNickname: ctx.user.nickname || "Host",
            isFree: true,
            capacity: 1,
            hostColorSide: meta.hostColorSide,
            colorByUserId: meta.colorByUserId || {
              [ctx.user.id]: hostColorIndex(meta.hostColorSide),
            },
            memberUserIds: [ctx.user.id],
            eventId: meta.eventId || rawEventId,
            eventPrompt: meta.eventPrompt || prog.eventPrompt || null,
            eventPromptChoices: meta.eventPromptChoices || prog.eventPromptChoices || null,
            eventEndsAt: meta.eventEndsAt || null,
            invitesAllowed: false,
          });
          rooms.set(existingCode, existing);
          persistRooms();
        }
      }
      if (existing && existing.eventId === rawEventId) {
        const side = normalizeHostColorSide(existing.hostColorSide);
        return res.status(200).json({
          token: existing.token,
          maxPeers: MAX_PEERS,
          capacity: roomCapacity(existing),
          isFree: true,
          name: existing.name || "Event",
          hostNickname: ctx.user.nickname || existing.hostNickname || "Host",
          hostColorSide: side,
          suggestedColorIndex:
            existing.colorByUserId?.[ctx.user.id] ?? hostColorIndex(side),
          charged: 0,
          eventId: existing.eventId,
          eventPrompt: existing.eventPrompt || prog.eventPrompt || null,
          eventPromptChoices: Array.isArray(existing.eventPromptChoices)
            ? existing.eventPromptChoices
            : Array.isArray(prog.eventPromptChoices)
              ? prog.eventPromptChoices
              : null,
          eventEndsAt: existing.eventEndsAt || null,
          reused: true,
          palette: lobbyCfg.palette || null,
          user: publicUser(ctx.user),
          ...inviteFor(existingCode),
        });
      }
      return res.status(400).json({
        error: "event_lobby_exists",
        message: "Für dieses Event hast du schon eine Event-Lobby erstellt.",
        lobbyCode: prog.lobbyCode || null,
      });
    }
    eventId = rawEventId;
    eventLobbyCfg = lobbyCfg;
    const engine = require("./event_engine");
    eventPromptChoices = engine.sampleEventPrompts(lobbyCfg, 3, pub.title);
    eventPrompt = null;
    const enriched = engine.enrichEvent(ev);
    if (enriched.schedule?.mode === "absolute" && enriched.schedule.absoluteUntil) {
      eventEndsAt = String(enriched.schedule.absoluteUntil);
    } else {
      const occ = seasonEvents.nextOccurrence(ev, new Date());
      if (occ?.end) {
        const parts = String(occ.end).split("-").map((x) => Number(x));
        if (parts.length === 3 && parts.every((n) => Number.isFinite(n))) {
          // Ende des Event-Tages (ca. 23:59 Berlin ≈ 21:59 UTC)
          eventEndsAt = new Date(
            Date.UTC(parts[0], parts[1] - 1, parts[2], 21, 59, 59, 999)
          ).toISOString();
        }
      }
    }
    if (!eventEndsAt) {
      eventEndsAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
    }
  }
  const adminTest =
    Boolean(req.body?.adminTest) && isStaff(ctx.user);
  const rawCustomRoomId = String(req.body?.customRoomId || "").trim().slice(0, 64);
  let customRoomId = null;
  let customRoomImageUrl = null;
  if (rawCustomRoomId) {
    if (eventId) {
      return res.status(400).json({ error: "bad_combo", message: "Kein Event mit Custom-Raum." });
    }
    const lay = roomLayouts.getLayout(getDb(), rawCustomRoomId);
    if (!lay || !lay.hasImage) {
      return res.status(400).json({
        error: "unknown_room",
        message: "Raum nicht gefunden. Bitte zuerst im Admin anlegen.",
      });
    }
    // Hochzeits-Builtins nur als Staff-Testlobby
    if (roomLayouts.isWeddingBuiltin(lay.id) && !adminTest) {
      return res.status(400).json({
        error: "unknown_room",
        message: "Raum nicht gefunden. Bitte zuerst im Admin anlegen.",
      });
    }
    customRoomId = lay.id;
    customRoomImageUrl = lay.imageUrl
      ? lay.imageUrl.startsWith("http")
        ? lay.imageUrl
        : `https://reineke.pro${lay.imageUrl.startsWith("/") ? "" : "/"}${lay.imageUrl}`
      : null;
  }
  const name = eventId
    ? "Event"
    : customRoomId
      ? String(
          (adminTest ? "Test · " : "") +
            (roomLayouts.getLayout(getDb(), customRoomId)?.name ||
              req.body?.name ||
              "Raum")
        )
          .trim()
          .slice(0, MAX_LOBBY_NAME_LENGTH) || "Raum"
      : String(req.body?.name || "Zusammen").trim().slice(0, MAX_LOBBY_NAME_LENGTH) ||
        "Zusammen";
  let charged = 0;
  let isFree = false;
  if (adminTest) {
    // Staff-Test: gratis, ohne Free-Slot zu verbrauchen
    isFree = true;
  } else if (eventId) {
    // Event-Lobby: gratis, ohne Tages-Free-Slot zu verbrauchen
    isFree = true;
  } else {
  const eligibleFree = evaluateCanCreateFreeLobby(ctx.user);
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
  }
  let code = randomCode();
  while (rooms.has(code)) code = randomCode();
  const token = randomToken().slice(0, 32);
  const capacity = eventId ? 1 : defaultLobbyCapacity(isFree);
  rooms.set(code, {
    token,
    createdAt: Date.now(),
    lastActiveAt: Date.now(),
    sockets: new Map(),
    clearProposal: null,
    hostUserId: ctx.user.id,
    createdByUserId: ctx.user.id,
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
    eventId,
    eventPrompt,
    eventPromptChoices,
    eventEndsAt,
    invitesAllowed: eventId ? false : true,
    isCustomRoom: Boolean(customRoomId),
    customRoomId,
    customRoomImageUrl,
    adminTestRoom: Boolean(adminTest && customRoomId),
    space: customRoomId
      ? { positions: {}, seated: {}, reactions: {}, lastMoveAt: 0, lastMoveBy: null }
      : null,
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
    eventId,
    eventPrompt,
    eventPromptChoices,
    eventEndsAt,
    isCustomRoom: Boolean(customRoomId),
    customRoomId,
    customRoomImageUrl,
  };
  if (eventId) {
    const prog = require("./event_engine").ensureUserProgress(ctx.user, eventId);
    prog.lobbyCreated = true;
    prog.lobbyCode = code;
    prog.eventPrompt = eventPrompt;
    prog.eventPromptChoices = eventPromptChoices;
    prog.eventLobbyOpens = (prog.eventLobbyOpens || 0) + 1;
  }
  persistRooms();
  trackAch(ctx.user, "lobbies_created", 1);
  if (isFree && !eventId) trackAch(ctx.user, "free_lobbies", 1);
  else if (!isFree) trackAch(ctx.user, "paid_lobbies", 1);
  if (charged > 0) trackAch(ctx.user, "coins_spent", charged);
  if (eventId) trackAch(ctx.user, "event_lobby_opens", 1);
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
    eventId,
    eventPrompt,
    eventPromptChoices,
    eventEndsAt,
    isCustomRoom: Boolean(customRoomId),
    customRoomId,
    customRoomImageUrl,
    palette: eventLobbyCfg?.palette || null,
    user: publicUser(ctx.user),
    ...inviteFor(code),
  });
});

function ensureRoomSpace(room) {
  if (!room.space || typeof room.space !== "object") {
    room.space = {
      positions: {},
      seated: {},
      reactions: {},
      portalCooldown: {},
      lastMoveAt: 0,
      lastMoveBy: null,
    };
  }
  if (!room.space.positions) room.space.positions = {};
  if (!room.space.seated) room.space.seated = {};
  if (!room.space.reactions) room.space.reactions = {};
  if (!room.space.portalCooldown) room.space.portalCooldown = {};
  return room.space;
}

function hubLayoutId(room) {
  return String(room.customRoomId || "").trim();
}

function playerLayoutId(space, uid, hubId) {
  const pos = space.positions?.[uid];
  const lid = String(pos?.layoutId || hubId || "").trim();
  return lid || hubId;
}

function normalizeSpacePos(pos, hubId, spawn) {
  return {
    x: Number(pos?.x ?? spawn?.x) || 0.5,
    y: Number(pos?.y ?? spawn?.y) || 0.85,
    layoutId: String(pos?.layoutId || hubId || "").trim() || hubId,
  };
}

function publicRoomSpace(room, db, viewerId) {
  const space = ensureRoomSpace(room);
  const hubId = hubLayoutId(room);
  const viewerLayoutId = playerLayoutId(space, viewerId, hubId);
  const layout = viewerLayoutId
    ? roomLayouts.getLayout(db, viewerLayoutId)
    : null;
  const memberIds = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
  const people = memberIds
    .filter((uid) => playerLayoutId(space, uid, hubId) === viewerLayoutId)
    .map((uid) => {
      const u = db.users?.[uid];
      const pos = normalizeSpacePos(
        space.positions[uid],
        hubId,
        layout?.spawn
      );
      const react = space.reactions[uid];
      const petRaw = String(u?.inventory?.equippedPet || "🐣").trim();
      const petEmoji = petRaw.startsWith("img_")
        ? petRaw.slice(0, 32)
        : petRaw.slice(0, 16) || "🐣";
      return {
        userId: uid,
        nickname: u
          ? String(u.nickname || "").trim().slice(0, 18) || "Jemand"
          : "Jemand",
        petEmoji,
        x: pos.x,
        y: pos.y,
        layoutId: pos.layoutId,
        seatedSeatId: space.seated[uid] || null,
        reaction: react && react.until > Date.now() ? react.emoji : null,
        reactionUntil: react ? Number(react.until) || 0 : 0,
        isMe: uid === viewerId,
      };
    });
  return {
    customRoomId: hubId || null,
    activeLayoutId: viewerLayoutId || null,
    roomName: layout?.name || room.name || "Raum",
    layout,
    people,
    lastMoveAt: Number(space.lastMoveAt) || 0,
    lastMoveBy: space.lastMoveBy || null,
  };
}

function requireCustomRoomMember(req, res) {
  const ctx = requireAuth(req, res);
  if (!ctx) return null;
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
  if (!room || !room.isCustomRoom || !room.customRoomId) {
    res.status(400).json({ error: "not_custom_room" });
    return null;
  }
  const members = Array.isArray(room.memberUserIds) ? room.memberUserIds : [];
  if (!members.includes(ctx.user.id) && room.hostUserId !== ctx.user.id) {
    res.status(403).json({ error: "not_member" });
    return null;
  }
  return { ctx, code, room };
}

app.get("/v1/rooms/:code/space", (req, res) => {
  const pack = requireCustomRoomMember(req, res);
  if (!pack) return;
  const db = getDb();
  const space = ensureRoomSpace(pack.room);
  const hubId = hubLayoutId(pack.room);
  const layoutId = playerLayoutId(space, pack.ctx.user.id, hubId);
  const layout = roomLayouts.getLayout(db, layoutId);
  roomLayouts.ensureSpawnPosition(layout, space.positions, pack.ctx.user.id);
  const pos = space.positions[pack.ctx.user.id];
  if (pos && !pos.layoutId) pos.layoutId = layoutId;
  return res.json({ ok: true, space: publicRoomSpace(pack.room, db, pack.ctx.user.id) });
});

app.post("/v1/rooms/:code/space/move", async (req, res) => {
  try {
    const pack = requireCustomRoomMember(req, res);
    if (!pack) return;
    const allowed = await redisUtil.allow(`smove:${pack.ctx.user.id}`, 40, 1);
    if (!allowed) {
      return res.status(429).json({
        error: "rate_limited",
        message: "Zu schnell — kurz warten.",
      });
    }
    const db = getDb();
    const space = ensureRoomSpace(pack.room);
    const hubId = hubLayoutId(pack.room);
    const uid = pack.ctx.user.id;
    const layoutId = playerLayoutId(space, uid, hubId);
    const layout = roomLayouts.getLayout(db, layoutId);
    if (space.seated[uid]) {
      delete space.seated[uid];
    }
    const prev = normalizeSpacePos(
      space.positions[uid],
      hubId,
      layout?.spawn
    );
    const clamped = roomLayouts.clampMove(
      layout,
      prev.x,
      prev.y,
      Number(req.body?.x),
      Number(req.body?.y)
    );
    const others = Object.entries(space.positions)
      .filter(([id, p]) => id !== uid && playerLayoutId(space, id, hubId) === layoutId)
      .map(([, p]) => ({ x: p.x, y: p.y }));
    const sep = roomLayouts.separateFromOthers(
      clamped.x,
      clamped.y,
      others,
      (layout?.avatarR || 0.022) * 2.2
    );
    const finalPos = roomLayouts.isWalkable(layout, sep.x, sep.y)
      ? sep
      : { x: clamped.x, y: clamped.y };
    space.positions[uid] = {
      x: finalPos.x,
      y: finalPos.y,
      layoutId,
    };
    space.lastMoveAt = Date.now();
    space.lastMoveBy = uid;
    pack.room.lastActiveAt = Date.now();

    // Portal-Cooldown: nicht sofort wieder auslösen (Spawn oft nahe Eingang)
    const now = Date.now();
    if (!space.portalCooldown) space.portalCooldown = {};
    const cooled = Number(space.portalCooldown[uid]) || 0;
    let enteredPortal = false;
    let portal = null;
    let action = roomLayouts.findActionAt(layout, finalPos.x, finalPos.y);
    if (now >= cooled) {
      portal = roomLayouts.findPortalAt(layout, finalPos.x, finalPos.y);
      if (portal) {
        const target = roomLayouts.getLayout(db, portal.targetRoomId);
        if (target && target.hasImage) {
          delete space.seated[uid];
          const spawn = target.spawn || { x: 0.5, y: 0.85 };
          space.positions[uid] = {
            x: spawn.x,
            y: spawn.y,
            layoutId: target.id,
          };
          roomLayouts.ensureSpawnPosition(target, space.positions, uid);
          if (space.positions[uid]) {
            space.positions[uid].layoutId = target.id;
            // Spawn leicht vom Portal wegschieben
            const landed = space.positions[uid];
            const backHit = roomLayouts.findPortalAt(target, landed.x, landed.y);
            if (backHit) {
              landed.y = Math.max(0.05, landed.y - 0.08);
              if (!roomLayouts.isWalkable(target, landed.x, landed.y)) {
                const sp = target.spawn || spawn;
                landed.x = sp.x;
                landed.y = Math.max(0.05, (sp.y || 0.8) - 0.1);
              }
            }
          }
          space.portalCooldown[uid] = now + 2500;
          enteredPortal = true;
          action = null;
        } else {
          portal = null;
        }
      }
    }

    persistRooms();
    const posOut = space.positions[uid] || finalPos;
    broadcastLivePos(pack.code, {
      type: "space_pos",
      roomCode: pack.code,
      userId: uid,
      x: posOut.x,
      y: posOut.y,
      layoutId: posOut.layoutId || layoutId,
      enteredPortal,
    });
    return res.json({
      ok: true,
      blocked: Boolean(clamped.blocked),
      enteredPortal,
      portalId: enteredPortal ? portal?.id || null : null,
      actionId: action?.id || null,
      actionType: action?.actionType || null,
      space: publicRoomSpace(pack.room, db, uid),
    });
  } catch (err) {
    console.error("[space/move]", err?.message || err);
    return res.status(500).json({ error: "server_error" });
  }
});

app.post("/v1/rooms/:code/space/enter-portal", (req, res) => {
  const pack = requireCustomRoomMember(req, res);
  if (!pack) return;
  const db = getDb();
  const space = ensureRoomSpace(pack.room);
  const hubId = hubLayoutId(pack.room);
  const uid = pack.ctx.user.id;
  const layoutId = playerLayoutId(space, uid, hubId);
  const layout = roomLayouts.getLayout(db, layoutId);
  const portalId = String(req.body?.portalId || "").trim().slice(0, 48);
  let portal = (layout?.portals || []).find((p) => p.id === portalId) || null;
  if (!portal) {
    const pos = normalizeSpacePos(space.positions[uid], hubId, layout?.spawn);
    portal = roomLayouts.findPortalAt(layout, pos.x, pos.y);
  }
  if (!portal) {
    // Idempotent: schon woanders / kein Portal — kein Fehler-Toast nötig
    return res.json({
      ok: true,
      enteredPortal: false,
      space: publicRoomSpace(pack.room, db, uid),
    });
  }
  const target = roomLayouts.getLayout(db, portal.targetRoomId);
  if (!target || !target.hasImage) {
    return res.status(400).json({ error: "bad_target" });
  }
  delete space.seated[uid];
  const spawn = target.spawn || { x: 0.5, y: 0.85 };
  space.positions[uid] = {
    x: spawn.x,
    y: spawn.y,
    layoutId: target.id,
  };
  roomLayouts.ensureSpawnPosition(target, space.positions, uid);
  if (space.positions[uid]) {
    space.positions[uid].layoutId = target.id;
    const landed = space.positions[uid];
    if (roomLayouts.findPortalAt(target, landed.x, landed.y)) {
      landed.y = Math.max(0.05, landed.y - 0.08);
    }
  }
  if (!space.portalCooldown) space.portalCooldown = {};
  space.portalCooldown[uid] = Date.now() + 2500;
  space.lastMoveAt = Date.now();
  space.lastMoveBy = uid;
  pack.room.lastActiveAt = Date.now();
  persistRooms();
  return res.json({
    ok: true,
    enteredPortal: true,
    space: publicRoomSpace(pack.room, db, uid),
  });
});

app.post("/v1/rooms/:code/space/sit", (req, res) => {
  const pack = requireCustomRoomMember(req, res);
  if (!pack) return;
  const db = getDb();
  const space = ensureRoomSpace(pack.room);
  const hubId = hubLayoutId(pack.room);
  const uid = pack.ctx.user.id;
  const layoutId = playerLayoutId(space, uid, hubId);
  const seatId = String(req.body?.seatId || "").trim().slice(0, 48);
  const zone = roomLayouts.findSitZone(db, layoutId, seatId);
  if (!zone || zone.color !== "blue") {
    return res.status(400).json({ error: "bad_seat" });
  }
  // Sitz nur blockieren, wenn jemand im selben Layout sitzt
  for (const [otherId, sid] of Object.entries(space.seated || {})) {
    if (sid === seatId && playerLayoutId(space, otherId, hubId) === layoutId) {
      return res.status(400).json({ error: "seat_taken" });
    }
  }
  space.seated[uid] = seatId;
  const sx = zone.shape === "circle" ? zone.cx : zone.x + zone.w / 2;
  const sy = zone.shape === "circle" ? zone.cy : zone.y + zone.h / 2;
  space.positions[uid] = { x: sx, y: sy, layoutId };
  space.lastMoveAt = Date.now();
  space.lastMoveBy = uid;
  persistRooms();
  return res.json({ ok: true, space: publicRoomSpace(pack.room, db, uid) });
});

app.post("/v1/rooms/:code/space/stand", (req, res) => {
  const pack = requireCustomRoomMember(req, res);
  if (!pack) return;
  const space = ensureRoomSpace(pack.room);
  delete space.seated[pack.ctx.user.id];
  persistRooms();
  return res.json({
    ok: true,
    space: publicRoomSpace(pack.room, getDb(), pack.ctx.user.id),
  });
});

app.post("/v1/rooms/:code/space/react", (req, res) => {
  const pack = requireCustomRoomMember(req, res);
  if (!pack) return;
  const emoji = clipCanvasEmojiId(req.body?.emoji);
  if (!emoji) return res.status(400).json({ error: "bad_emoji" });
  const space = ensureRoomSpace(pack.room);
  space.reactions[pack.ctx.user.id] = { emoji, until: Date.now() + 2000 };
  persistRooms();
  return res.json({
    ok: true,
    space: publicRoomSpace(pack.room, getDb(), pack.ctx.user.id),
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
  if (room.isWedding && !room.isWeddingCeremony) {
    return res.status(403).json({
      error: "wedding_locked",
      message: "Zur Hochzeitsleinwand kann niemand eingeladen werden.",
    });
  }
  if (room.isWeddingCeremony) {
    return res.status(403).json({
      error: "ceremony_slots_fixed",
      message: "Die Hochzeits-Lobby hat feste Plätze je nach Raum (Brautpaar + Gäste-Slots).",
    });
  }
  // Nur Host oder Mitglied — fremde Codes nicht manipulieren
  const isMember =
    room.hostUserId === ctx.user.id ||
    (Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id)) ||
    Boolean(ctx.user.hostedRooms?.[code]) ||
    Boolean(ctx.user.joinedRooms?.[code]);
  if (!isMember) {
    return res.status(403).json({
      error: "not_member",
      message: "Nur Lobby-Mitglieder können Plätze freischalten.",
    });
  }
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
  const dbUsers = getDb().users || {};
  const memberIds = new Set(
    [
      room.hostUserId,
      room.createdByUserId,
      ...(Array.isArray(room.memberUserIds) ? room.memberUserIds : []),
    ].filter(Boolean)
  );
  for (const uid of memberIds) {
    const u = dbUsers[uid];
    if (!u) continue;
    ensureHostedRooms(u);
    if (u.hostedRooms?.[code]) {
      u.hostedRooms[code].capacity = room.capacity;
    }
    if (!u.joinedRooms || typeof u.joinedRooms !== "object") u.joinedRooms = {};
    if (u.joinedRooms[code]) {
      u.joinedRooms[code].capacity = room.capacity;
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
  const ctx = requireAuth(req, res, { allowTrial: true });
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
  {
    const savedW = getDb().rooms?.[code];
    const isPaintWedding =
      (room?.isWedding && !room?.isWeddingCeremony) ||
      (savedW?.isWedding && !savedW?.isWeddingCeremony);
    if (isPaintWedding) {
      return res.status(403).json({
        error: "wedding_locked",
        message: "Die Hochzeitsleinwand kann nicht verlassen werden.",
      });
    }
    if (room?.isWeddingCeremony || savedW?.isWeddingCeremony) {
      return res.status(400).json({
        error: "use_ceremony_leave",
        message: "Hochzeit verlassen: bitte den Schieberegler in der Lobby nutzen.",
      });
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
  if (room.eventId) {
    // Event-Lobby verlassen = Contest-Eintrag + Auflösen (eine Lobby pro Event)
    closeEventLobbyRoom(code, room);
  } else {
    ensureHostOrDissolve(room, code, { immediateEmpty: true });
    releaseHostedRoom(code, ctx.user.id);
    forgetJoinedLobby(ctx.user, code);
    if (rooms.has(code)) {
      touchRoom(room);
      persistRooms();
    }
  }
  scheduleSave();

  return res.json({ ok: true });
});

/** Event-Lobby: Begriff aus den 3 Vorschlägen wählen (nur einmal). */
app.post("/v1/rooms/:code/event-prompt", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const token = String(req.body?.token || "").trim();
  const picked = String(req.body?.prompt || "").trim().slice(0, 80);
  if (!picked) {
    return res.status(400).json({ error: "invalid_prompt", message: "Bitte ein Wort wählen." });
  }
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && typeof saved === "object") {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  if (!room || !room.eventId) {
    return res.status(404).json({ error: "not_found", message: "Keine Event-Lobby." });
  }
  const isHost =
    room.hostUserId === ctx.user.id || room.createdByUserId === ctx.user.id;
  const isMember =
    isHost ||
    (Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id)) ||
    Boolean(ctx.user.hostedRooms?.[code]);
  if (!isMember && token && room.token !== token) {
    return res.status(403).json({ error: "forbidden" });
  }
  if (!isMember) {
    return res.status(403).json({ error: "forbidden" });
  }
  if (room.eventPrompt) {
    return res.json({
      ok: true,
      already: true,
      eventPrompt: room.eventPrompt,
      eventPromptChoices: null,
    });
  }
  const choices = Array.isArray(room.eventPromptChoices)
    ? room.eventPromptChoices.map((p) => String(p || "").trim()).filter(Boolean)
    : [];
  if (choices.length && !choices.includes(picked)) {
    return res.status(400).json({
      error: "invalid_prompt",
      message: "Bitte eines der vorgeschlagenen Wörter wählen.",
      eventPromptChoices: choices,
    });
  }
  room.eventPrompt = picked;
  room.eventPromptChoices = null;
  ensureHostedRooms(ctx.user);
  if (ctx.user.hostedRooms?.[code]) {
    ctx.user.hostedRooms[code].eventPrompt = picked;
    ctx.user.hostedRooms[code].eventPromptChoices = null;
  }
  const prog = require("./event_engine").ensureUserProgress(ctx.user, room.eventId);
  prog.eventPrompt = picked;
  prog.eventPromptChoices = null;
  persistRooms();
  scheduleSave();
  return res.json({
    ok: true,
    eventPrompt: picked,
    eventPromptChoices: null,
  });
});

/** Event-Lobby: Snapshot übernehmen und Contest schließen (Timer-Ende). */
app.post("/v1/rooms/:code/event-close", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const token = String(req.body?.token || "").trim();
  let room = rooms.get(code);
  if (!room) {
    const saved = getDb().rooms?.[code];
    if (saved && typeof saved === "object") {
      room = hydrateRoom(code, saved);
      rooms.set(code, room);
    }
  }
  if (!room || !room.eventId) {
    return res.status(404).json({ error: "not_found", message: "Keine Event-Lobby." });
  }
  const isHost =
    room.hostUserId === ctx.user.id || room.createdByUserId === ctx.user.id;
  if (!isHost && token && room.token !== token) {
    return res.status(403).json({ error: "forbidden" });
  }
  if (!isHost && !(Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id))) {
    return res.status(403).json({ error: "forbidden" });
  }
  closeEventLobbyRoom(code, room);
  scheduleSave();
  return res.json({ ok: true, eventId: room.eventId });
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

/** Bug-Meldungen (Screenshot-Link, Video, Ort, Repro) */
app.post("/v1/bug-reports", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const result = bugReports.createReport(getDb(), ctx.user, req.body || {});
  if (!result.ok) {
    return res.status(result.status || 400).json({
      error: result.error,
      message: result.message,
    });
  }
  scheduleSave();
  console.log(
    `bug report ${result.report.id} by=${ctx.user.nickname || ctx.user.id}`
  );
  return res.status(201).json({ ok: true, report: result.report });
});

app.get("/v1/me/bug-reports/pending", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const reports = bugReports.pendingRewardReports(getDb(), ctx.user.id);
  return res.json({
    ok: true,
    pending: reports.length,
    rewardCoins: bugReports.REWARD_COINS,
    reports,
  });
});

app.post("/v1/me/bug-reports/claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const reportId = String(req.body?.reportId || "").trim() || null;
  const result = bugReports.claimRewards(getDb(), ctx.user, applyLedger, {
    reportId,
  });
  flushSave();
  return res.json({
    ok: true,
    claimed: result.claimed,
    coins: result.coins,
    pending: result.pending,
    rewardCoins: bugReports.REWARD_COINS,
    user: publicUser(ctx.user),
  });
});

app.get("/v1/admin/bug-reports", (req, res) => {
  const ctx = requireStaff(req, res, "reports.view");
  if (!ctx) return;
  return res.json({
    ok: true,
    reports: bugReports.listOpenForAdmin(getDb()),
    rewardCoins: bugReports.REWARD_COINS,
  });
});

app.post("/v1/admin/bug-reports/:id/helpful", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const result = bugReports.markHelpful(getDb(), id, ctx.user.id, (uid, coins, reason, ref) =>
    applyLedger(uid, coins, reason, ref)
  );
  if (!result.ok) {
    return res.status(result.status || 400).json({
      error: result.error,
      message: result.message,
    });
  }
  flushSave();
  staffAudit(ctx.user, "bug_report_helpful", {
    id,
    already: Boolean(result.already),
    coins: result.coins || 0,
  });
  return res.json({
    ok: true,
    report: result.report,
    coins: result.coins || 0,
    message:
      result.coins > 0
        ? `+${result.coins} Coins gutgeschrieben — Fall geschlossen.`
        : result.already
          ? "Bereits erledigt."
          : "Fall geschlossen.",
  });
});

app.post("/v1/admin/bug-reports/:id/delete", (req, res) => {
  const ctx = requireStaff(req, res, "reports.act");
  if (!ctx) return;
  const id = String(req.params.id || "").replace(/[^a-zA-Z0-9_-]/g, "");
  const result = bugReports.deleteReport(getDb(), id, ctx.user.id);
  if (!result.ok) {
    return res.status(result.status || 400).json({
      error: result.error,
      message: result.message,
    });
  }
  scheduleSave();
  staffAudit(ctx.user, "bug_report_delete", { id });
  return res.json({ ok: true, report: result.report });
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
  if (room.isWedding && !room.isWeddingCeremony && !isKnownMember) {
    return res.status(403).json({
      error: "wedding_locked",
      message: "Nur das Brautpaar darf die Hochzeitsleinwand betreten.",
    });
  }
  if (room.isWeddingCeremony) {
    const mid = room.marriageId;
    const m = mid
      ? marriage.ensureMarriages(getDb())[mid]
      : null;
    if (m) {
      tickCeremonyRitual(m, getDb());
      const enter = weddingCeremony.canEnterChapel(m, ctx.user.id, {
        isKnownMember,
      });
      if (!enter.ok) {
        return res.status(403).json({
          error: enter.error || "ceremony_in_progress",
          message:
            enter.message ||
            "Die Zeremonie ist im Gange — bitte wartet bis zum Empfang.",
        });
      }
    }
    if (!isKnownMember) {
      // Gäste nur mit vorheriger Einladung (member) oder Lobby-Invite-Accept
      return res.status(403).json({
        error: "ceremony_invite_only",
        message: "Nur Eingeladene dürfen zur Hochzeit.",
      });
    }
  }
  if (room.eventId && !isKnownMember) {
    return res.status(403).json({
      error: "event_lobby_solo",
      message: "Event-Lobbys sind Solo — keine Einladungen.",
    });
  }
  // Neue Mitglieder: verbundene Sockets UND gespeicherte Members gegen Cap prüfen
  if (!isKnownMember) {
    const seatsTaken = Math.max(uniqueConnectedCount(room), roomMemberCount(room));
    if (seatsTaken >= capacity) {
      return res.status(409).json({
        error: "room_full",
        message: "Lobby ist voll — jemand kann weitere Plätze freischalten.",
      });
    }
  } else if (!alreadyHere && uniqueConnectedCount(room) >= capacity) {
    // Bekanntes Mitglied darf reconnecten, Cap nicht gratis erhöhen
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
  if (room.isCustomRoom && room.customRoomId) {
    ensureJoinedRooms(ctx.user);
    ctx.user.joinedRooms[code] = {
      ...(ctx.user.joinedRooms[code] || {}),
      token: room.token,
      name: room.name || "Raum",
      capacity: capacity,
      isFree: Boolean(room.isFree),
      isCustomRoom: true,
      customRoomId: room.customRoomId,
      customRoomImageUrl: room.customRoomImageUrl || null,
      hostColorSide: normalizeHostColorSide(room.hostColorSide),
      hostNickname: room.hostNickname || "Host",
    };
    scheduleSave();
  }
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
    isWedding: Boolean(room.isWedding),
    isWeddingCeremony: Boolean(room.isWeddingCeremony),
    isCustomRoom: Boolean(room.isCustomRoom),
    customRoomId: room.customRoomId || null,
    customRoomImageUrl: room.customRoomImageUrl || null,
    weddingRetake: Boolean(room.weddingRetake),
    ceremonyAt: Number(room.ceremonyAt) || 0,
    giftWindowEndsAt: Number(room.giftWindowEndsAt) || 0,
    giftPhase: String(room.giftPhase || "none"),
    coupleNicknames: room.coupleNicknames || null,
    hostColorSide: normalizeHostColorSide(room.hostColorSide),
    suggestedColorIndex,
    members: roster.map((m) => m.nickname),
    memberList: roster,
    ...inviteFor(code),
  });
});

function loadRoomForInvite(code) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/^LUV-/, "")
    .replace(/[^A-Z0-9]/g, "");
  if (!clean) return { code: "", room: null };
  let room = rooms.get(clean);
  if (!room) {
    const saved = getDb().rooms?.[clean];
    if (saved) {
      room = hydrateRoom(clean, saved);
      rooms.set(clean, room);
    }
  }
  if (room && !Array.isArray(room.strokes)) {
    try {
      room.strokes = loadRoomStrokes(clean);
    } catch {
      room.strokes = [];
    }
  }
  if (room) healCeremonyRoomFlags(room, clean);
  return { code: clean, room };
}

app.get("/v1/rooms/:code/preview", (req, res) => {
  const { code, room } = loadRoomForInvite(req.params.code);
  if (!room) {
    return res.status(404).json({ error: "room_not_found", message: "Lobby nicht gefunden." });
  }
  return res.json(publicRoom(room, code));
});

/** Öffentliches Lobby-Vorschaubild für Invite-Landing (Snapshot). */
app.get("/v1/rooms/:code/invite-image", (req, res) => {
  const ip = clientIp(req);
  if (!rateLimit(`invite_image:${ip || "x"}`, 120, 60 * 60 * 1000)) {
    return res.status(429).json({ error: "rate_limited" });
  }
  const { code, room } = loadRoomForInvite(req.params.code);
  if (!room) return res.status(404).end();
  if (room.invitesAllowed === false) return res.status(404).end();
  const file = inviteTrial.snapshotFile(SNAPSHOT_DIR, code);
  if (!fs.existsSync(file)) return res.status(404).end();
  let mtime = Date.now();
  try {
    mtime = Math.floor(fs.statSync(file).mtimeMs);
  } catch {
    /* ignore */
  }
  res.setHeader("Cache-Control", "public, max-age=120");
  res.setHeader("ETag", `"inv-${code}-${mtime}"`);
  res.type("png");
  return fs.createReadStream(file).pipe(res);
});

/**
 * Trial-Gast verlässt: eigene Strokes weg + aus Lobby kicken.
 * Danach Google-Login / Onboarding → Client rejoined mit echtem Namen.
 */
app.post("/v1/rooms/:code/trial-exit", (req, res) => {
  const ctx = requireAuth(req, res, { allowTrial: true });
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
  const uid = ctx.user.id;
  let removed = 0;
  if (room) {
    const list = ensureRoomStrokes(room, code);
    const keep = [];
    const removeIds = [];
    for (const s of list) {
      if (s && String(s.authorId || "") === uid) {
        removeIds.push(String(s.id || "").trim());
      } else {
        keep.push(s);
      }
    }
    if (removeIds.length > 0) {
      room.strokes = keep;
      scheduleStrokeSave(code, room);
      removed = removeIds.length;
      broadcastRoom(room, {
        type: "erase_commit",
        remove: removeIds.filter(Boolean).slice(0, 400),
        add: [],
      });
    }
    const leftNick =
      String(ctx.user.nickname || "")
        .trim()
        .slice(0, 18) || "Gast";
    for (const [peerId, sock] of [...room.sockets.entries()]) {
      if (sock.luvUserId && sock.luvUserId === uid) {
        sock.luvLeft = true;
        try {
          sock.close(4001, "left");
        } catch {
          /* ignore */
        }
        room.sockets.delete(peerId);
      }
    }
    forgetMember(room, uid);
    const roster = roomRosterAll(room);
    const online = roster.filter((m) => m.online).length;
    broadcastRoom(room, {
      type: "peer_left",
      userId: uid,
      nickname: leftNick,
      peers: online,
      count: online,
      capacity: roomCapacity(room),
      members: roster.map((m) => m.nickname),
      memberList: roster,
    });
    broadcastPeerCount(room);
    ensureHostOrDissolve(room, code, { immediateEmpty: true });
    releaseHostedRoom(code, uid);
    forgetJoinedLobby(ctx.user, code);
    touchRoom(room);
    persistRooms();
  } else {
    releaseHostedRoom(code, uid);
    forgetJoinedLobby(ctx.user, code);
  }
  // Trial-Flags behalten bis Google-Upgrade (Client rejoined danach)
  if (ctx.user.isTrial) {
    ctx.user.trialRoomCode = code;
  }
  scheduleSave();
  return res.json({ ok: true, code, removed });
});

/**
 * Probezeichnen ohne Google — nur diese Lobby, 60 Sekunden.
 * Body optional: { installId? }
 */
app.post("/v1/rooms/:code/trial-join", (req, res) => {
  const ip = clientIp(req);
  if (!rateLimit(`trial_join_ip:${ip || "x"}`, 8, 60 * 60 * 1000)) {
    return res.status(429).json({
      error: "rate_limited",
      message: "Zu viele Probe-Zugänge. Bitte später erneut oder mit Google anmelden.",
    });
  }
  const installId = String(req.body?.installId || "").trim().slice(0, 80);
  if (installId) {
    if (!rateLimit(`trial_join_install:${installId}`, 4, 60 * 60 * 1000)) {
      return res.status(429).json({
        error: "rate_limited",
        message: "Probezeichnen auf diesem Gerät schon genutzt. Bitte mit Google anmelden.",
      });
    }
  }
  const { code, room } = loadRoomForInvite(req.params.code);
  if (!room) {
    return res.status(404).json({
      error: "room_not_found",
      message: "Lobby nicht gefunden. Bitte den Link erneut öffnen.",
    });
  }
  if (room.invitesAllowed === false) {
    return res.status(403).json({
      error: "invites_closed",
      message: "Einladungen sind für diese Lobby geschlossen.",
    });
  }
  if (room.isWedding) {
    return res.status(403).json({
      error: "wedding_locked",
      message: "Nur das Brautpaar darf die Hochzeitsleinwand betreten.",
    });
  }
  if (room.eventId) {
    return res.status(403).json({
      error: "event_lobby_solo",
      message: "Event-Lobbys sind Solo — keine Einladungen.",
    });
  }
  healRoomMembership(room);
  const capacity = roomCapacity(room);
  const seatsTaken = Math.max(uniqueConnectedCount(room), roomMemberCount(room));
  if (seatsTaken >= capacity) {
    return res.status(409).json({
      error: "room_full",
      message: "Lobby ist voll.",
    });
  }

  const db = getDb();
  const drawUntil = Date.now() + inviteTrial.TRIAL_DRAW_MS;
  const user = {
    id: newId("u"),
    secretHash: hashSecret(`trial:${code}:${crypto.randomBytes(16).toString("hex")}`),
    installIdHash: installId ? hashSecret(installId) : null,
    nickname: "Gast",
    paidCoins: 0,
    dailyBalance: 0,
    coins: 0,
    role: "user",
    lastDailyGrantDate: todayKey(),
    sessionsByDay: {},
    drawLocks: {},
    createdAt: Date.now(),
    googleSub: null,
    googleEmail: null,
    signupIp: ip,
    hostedRooms: {},
    joinedRooms: {},
    isTrial: true,
    trialRoomCode: code,
    trialDrawUntil: drawUntil,
  };
  db.users[user.id] = user;
  rememberMember(room, user.id);
  healRoomMembership(room);
  cancelEmptyFreeLobbyTimer(code);
  touchRoom(room);
  persistRooms();
  const sessionToken = createSession(user.id, Math.max(inviteTrial.TRIAL_DRAW_MS * 30, 2 * 60 * 60 * 1000));
  const isHost = false;
  const suggestedColorIndex = assignRoomColor(room, user.id, isHost);
  scheduleSave();
  const pub = publicRoom(room, code);
  return res.json({
    ok: true,
    sessionToken,
    trialToken: sessionToken,
    drawUntilMs: inviteTrial.TRIAL_DRAW_MS,
    trialDrawUntil: drawUntil,
    token: room.token,
    suggestedColorIndex,
    user: publicUser(user),
    ...pub,
  });
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
  return res.json({ line: notifyPhrases.pickShareLineFromDb(getDb()) });
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

/** Mehrere öffentliche Bilder für Sozial → Bilder (ohne Auth). */
app.get("/v1/public-canvases/sample", (req, res) => {
  cleanupPublicCanvases();
  const limit = Math.min(6, Math.max(1, Math.floor(Number(req.query.limit) || 3)));
  const exclude = new Set(
    String(req.query.exclude || "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, 80)
  );
  const list = listPublicCanvasEntries();
  if (!list.length) return res.json({ items: [] });
  const fresh = list.filter((e) => e && e.id && !exclude.has(e.id));
  const pool = fresh.length ? fresh.slice() : list.slice();
  for (let i = pool.length - 1; i > 0; i--) {
    const j = crypto.randomInt(0, i + 1);
    const tmp = pool[i];
    pool[i] = pool[j];
    pool[j] = tmp;
  }
  const items = pool.slice(0, limit).map((entry) => ({
    id: entry.id,
    lobbyName: entry.lobbyName || "Lobby",
    hostNickname: entry.hostNickname || "Jemand",
    memberNicknames: Array.isArray(entry.memberNicknames) ? entry.memberNicknames : [],
    nameLine: entry.nameLine || splashNameLine(entry),
    imageUrl: `/v1/public-canvases/${entry.id}/image`,
    createdAt: entry.createdAt,
    expiresAt: entry.expiresAt || Number(entry.createdAt) + PUBLIC_TTL_MS,
  }));
  return res.json({ items });
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
  const line = notifyPhrases.pickShareLineFromDb(getDb());
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

/** WhatsApp / Social Preview + Invite-Landing für /luv/j/:code */
app.get("/invite/:code", async (req, res) => {
  const { code, room } = loadRoomForInvite(req.params.code);
  const host = room?.hostNickname || "Jemand";
  const joinUrl = `https://reineke.pro/luv/j/${code}`;
  const deep = code ? `luv://join/${code}` : "https://reineke.pro/luv/";
  const playUrl = inviteTrial.playStoreUrl(code);
  const found = Boolean(room);
  const isCeremony = Boolean(found && room.isWeddingCeremony);
  const hasSnap = found && inviteTrial.hasInviteSnapshot(SNAPSHOT_DIR, code);
  const hasDrawing = found && inviteTrial.roomHasDrawing(SNAPSHOT_DIR, code, room);
  let imageVer = Date.now();
  if (hasSnap) {
    try {
      imageVer = Math.floor(fs.statSync(inviteTrial.snapshotFile(SNAPSHOT_DIR, code)).mtimeMs);
    } catch {
      /* ignore */
    }
    const memAt = Number(canvasMemories()?.[code]?.updatedAt) || 0;
    if (memAt > 0) imageVer = Math.max(imageVer, memAt);
  }

  let inviteImageUrl = null;
  let ceremonyDetails = "";
  if (isCeremony) {
    const at = Number(room.ceremonyAt) || 0;
    try {
      await weddingInviteCard.ensureWeddingInviteCardFile(DATA_DIR, {
        code,
        coupleNicknames: room.coupleNicknames,
        hostNickname: host,
        ceremonyAt: at,
      });
    } catch (e) {
      console.error("wedding card warm", e?.message || e);
    }
    inviteImageUrl = weddingInviteCard.publicWeddingCardUrl(code, at);
    const when = weddingInviteCard.formatCeremonyParts(at);
    ceremonyDetails = [when.dateLine, when.umLine, when.timeLine]
      .filter(Boolean)
      .join(" · ");
  } else if (hasSnap) {
    inviteImageUrl = inviteTrial.inviteImageAbsoluteUrl(code, imageVer);
  }

  // Immer 200 — WhatsApp/Link-Preview crawlen keine 404-Seiten für OG-Tags
  res
    .status(200)
    .type("html")
    .send(
      inviteTrial.buildInviteLandingHtml({
        code,
        found,
        host,
        lobbyName: room?.name || "Lobby",
        hasDrawing: isCeremony ? true : Boolean(hasDrawing && hasSnap),
        inviteImageUrl,
        joinUrl,
        deep,
        playUrl,
        isWeddingCeremony: isCeremony,
        coupleLine: isCeremony ? ceremonyCoupleLine(room) : "",
        ceremonyDetails,
      })
    );
});

/** Dynamische Hochzeits-Einladungskarte (OG / WhatsApp). */
app.get("/v1/rooms/:code/wedding-invite-card", async (req, res) => {
  const { code, room } = loadRoomForInvite(req.params.code);
  if (!room) return res.status(404).end();
  healCeremonyRoomFlags(room, code);
  if (!room.isWeddingCeremony) return res.status(404).end();
  try {
    const file = await weddingInviteCard.ensureWeddingInviteCardFile(DATA_DIR, {
      code,
      coupleNicknames: room.coupleNicknames,
      hostNickname: room.hostNickname || "Host",
      ceremonyAt: Number(room.ceremonyAt) || 0,
    });
    if (!file || !fs.existsSync(file)) {
      return res.redirect(302, inviteTrial.WEDDING_ALTAR_OG);
    }
    res.setHeader("Content-Type", "image/jpeg");
    res.setHeader("Cache-Control", "public, max-age=300");
    return fs.createReadStream(file).pipe(res);
  } catch (e) {
    console.error("wedding-invite-card", e?.message || e);
    return res.redirect(302, inviteTrial.WEDDING_ALTAR_OG);
  }
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

/** Lobby-Umbenennen ist abgeschaltet (UI entfernt). */
app.patch("/v1/rooms/:code", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  return res.status(410).json({
    error: "rename_disabled",
    message: "Lobbys können nicht mehr umbenannt werden.",
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
  const snapMember =
    (Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id)) ||
    room.hostUserId === ctx.user.id;
  if (!snapMember) {
    return res.status(403).json({ error: "not_member", message: "Nur Lobby-Mitglieder." });
  }
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
  // Fast leere PNGs dürfen gute Invite-Vorschaubilder nicht überschreiben
  const strokeCount = ensureRoomStrokes(room, code).length;
  if (fs.existsSync(filePath) && strokeCount > 0 && buf.length < 12_000) {
    const mem = canvasMemories();
    return res.json({
      ok: true,
      skipped: "empty_overwrite",
      updatedAt: mem[code]?.updatedAt || Date.now(),
    });
  }
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

/** Aktivität auf der Leinwand melden (Presence / Öffnen) — kein Glow-Trigger. */
app.post("/v1/rooms/:code/canvas-touch", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const token = String(req.body?.token || "").trim();
  const found = roomForAuth(req.params.code, token, ctx.user);
  if (!found) return res.status(404).json({ error: "room_not_found" });
  const { code, room } = found;
  const touchMember =
    (Array.isArray(room.memberUserIds) && room.memberUserIds.includes(ctx.user.id)) ||
    room.hostUserId === ctx.user.id;
  if (!touchMember) {
    return res.status(403).json({ error: "not_member", message: "Nur Lobby-Mitglieder." });
  }
  touchRoom(room);
  // Bei neuer Aktivität: pending Release zurücknehmen, wenn wieder jemand malt
  const mem = canvasMemories();
  if (mem[code]?.releasedAt) {
    // Nach Release bleibt das Abbild 24h zum Teilen — nicht löschen.
  }
  return res.json({
    ok: true,
    lastCanvasAt: room.lastCanvasAt,
    lastCanvasActorId: room.lastCanvasActorId || null,
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
  // Auth: Bearer oder ?session= (Bild-Downloads ohne Header-Support)
  let ctx = authUser(req);
  if (!ctx) {
    const qTok = String(req.query?.session || "").trim();
    if (qTok) {
      const db = getDb();
      const session = db.sessions[qTok];
      if (session && session.expiresAt >= Date.now()) {
        const user = db.users[session.userId];
        if (user && !user.banned) ctx = { user, token: qTok };
      }
    }
  }
  if (!ctx) {
    return res.status(401).json({ error: "unauthorized" });
  }
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const entry = canvasMemories()[code];
  const pub = memoryPublic(code, entry);
  if (!pub || !entry?.file) return res.status(404).end();

  // Nur Host / bekannte Mitglieder / Staff
  ensureStaffFields(ctx.user);
  const isStaffUser = isStaff(ctx.user);
  let allowed = isStaffUser;
  if (!allowed) {
    const hosted = ctx.user.hostedRooms || {};
    if (hosted[code]) allowed = true;
  }
  if (!allowed) {
    const live = rooms.get(code) || getDb().rooms?.[code];
    if (live?.hostUserId === ctx.user.id) allowed = true;
    if (
      Array.isArray(live?.memberUserIds) &&
      live.memberUserIds.includes(ctx.user.id)
    ) {
      allowed = true;
    }
  }
  if (!allowed) {
    return res.status(403).json({ error: "forbidden", message: "Kein Zugriff auf dieses Memory." });
  }

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
// Zwei WS-Pfade: kein { server, path } parallel — sonst antwortet der andere mit 400.
const wss = new WebSocketServer({ noServer: true });
const accountWss = accountPush.attachAccountWss(null, userFromSessionToken);
server.on("upgrade", (request, socket, head) => {
  let pathname = "/";
  try {
    pathname = new URL(request.url || "", "http://localhost").pathname;
  } catch {
    /* ignore */
  }
  if (pathname === "/v1/ws/account") {
    accountWss.handleUpgrade(request, socket, head, (ws) => {
      accountWss.emit("connection", ws, request);
    });
    return;
  }
  if (pathname === "/v1/ws") {
    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit("connection", ws, request);
    });
    return;
  }
  try {
    socket.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n");
  } catch {
    /* ignore */
  }
  try {
    socket.destroy();
  } catch {
    /* ignore */
  }
});

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
  if (!user) {
    try {
      socket.close(4401, "auth_required");
    } catch {
      /* ignore */
    }
    return;
  }
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
  if (user?.isTrial) {
    const trialRoom = String(user.trialRoomCode || "").toUpperCase();
    if (trialRoom && trialRoom !== code) {
      try {
        socket.close(4403, "trial_room");
      } catch {
        /* ignore */
      }
      return;
    }
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
        room.hostUserId === user.id ||
        room.createdByUserId === user.id)
  );
  if (room.eventId && !isKnownMember) {
    console.log(
      `ws event_lobby_solo code=${code} user=${user?.id || "?"}`
    );
    socket.close(4403, "event_lobby_solo");
    return;
  }
  if (!isKnownMember) {
    const seatsTaken = Math.max(others, roomMemberCount(room));
    if (seatsTaken >= capacity) {
      console.log(
        `ws room_full code=${code} seats=${seatsTaken} cap=${capacity} user=${user?.id || "?"}`
      );
      socket.close(4409, "room_full");
      return;
    }
  }
  // Bekannte Mitglieder: Reconnect/Zweitgerät erlaubt, Cap nicht aufblasen
  if (user?.nickname && room.hostUserId && user.id === room.hostUserId) {
    room.hostNickname = user.nickname;
  }

  const peerId = `${role}-${crypto.randomBytes(4).toString("hex")}`;
  attachWsErrorGuard(socket, `code=${code} peer=${peerId}`);
  socket.luvPeerId = peerId;
  socket.luvUserId = user?.id || null;
  if (user?.id) touchActiveAppUser(user.id);
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
      isTrial: Boolean(user?.isTrial),
      trialDrawUntil: user?.isTrial ? Number(user.trialDrawUntil) || 0 : null,
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
      if (user.isTrial) {
        const trialRoom = String(user.trialRoomCode || "").toUpperCase();
        if (trialRoom && trialRoom !== code) {
          socket.send(
            JSON.stringify({
              type: "economy_block",
              error: "trial_room",
              message: "Probezeichnen gilt nur für die eingeladene Lobby.",
            })
          );
          return;
        }
        if (Date.now() > (Number(user.trialDrawUntil) || 0)) {
          socket.send(
            JSON.stringify({
              type: "trial_expired",
              error: "trial_expired",
              message: "Probezeit vorbei — melde dich mit Google an, um weiterzumalen.",
              trialDrawUntil: Number(user.trialDrawUntil) || 0,
            })
          );
          return;
        }
      }
      let drawResult = null;
      drawResult = consumeDrawSession(user, code);
      if (!drawResult.ok) {
        const trialGone = drawResult.error === "trial_expired";
        socket.send(
          JSON.stringify({
            type: trialGone ? "trial_expired" : "economy_block",
            error: drawResult.error || "no_coins",
            message: trialGone
              ? "Probezeit vorbei — melde dich mit Google an, um weiterzumalen."
              : drawResult.error === "trial_room"
                ? "Probezeichnen gilt nur für die eingeladene Lobby."
                : "Zeichnen gerade nicht möglich.",
            trialDrawUntil: Number(user.trialDrawUntil) || 0,
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
      touchCanvasActivity(room, room.sockets.size, user?.id || socket.luvUserId);
      if (user) {
        trackAch(user, "strokes", 1);
        seasonEvents.bumpQuestsForUser(
          getDb(),
          user,
          "strokes",
          1,
          (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
          (u, k, itemId) => safeGiveItem(u, k, itemId)
        );
        if (room.eventId) {
          require("./event_engine").noteEventLobbyStroke(user, room.eventId, 1);
          seasonEvents.bumpQuestsForUser(
            getDb(),
            user,
            "event_lobby_strokes",
            1,
            (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
            (u, k, itemId) => safeGiveItem(u, k, itemId)
          );
        }
        if (stored.emoji) trackAch(user, "stickers_placed", 1);
        if (stored.templateParts) trackAch(user, "templates_placed", 1);
        if (drawResult && (drawResult.free || drawResult.charged) && !drawResult.already) {
          trackAch(user, "draw_sessions", 1);
          seasonEvents.bumpQuestsForUser(
            getDb(),
            user,
            "draw_sessions",
            1,
            (uid, coins, reason, ref) => applyLedger(uid, coins, reason, ref),
            (u, k, itemId) => safeGiveItem(u, k, itemId)
          );
        }
        const peers = uniqueConnectedCount(room);
        if (peers >= 2) trackAch(user, "draw_with_peers", 1);
        if (peers >= 4) trackAch(user, "draw_group4", 1);
        ach.setMetricAtLeast(user, "lobby_peak", room.peakPeers || peers, todayKey(), null);
        // Freundschaftslevel: +1/Tag wenn beide auf dieser Lobby heute gemalt haben
        noteFriendshipCanvasStroke(room, user);
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
        if (stored.templateCoordSpace) {
          relayPayload.templateCoordSpace = stored.templateCoordSpace;
        }
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
      if (!user || !socket.luvUserId) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "auth_required",
            message: "Bitte einloggen zum Rückgängigmachen.",
          })
        );
        return;
      }
      removeRoomStroke(room, code, json.id);
    }

    if (type === "erase_commit") {
      if (!user || !socket.luvUserId) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "auth_required",
            message: "Bitte einloggen zum Radieren.",
          })
        );
        return;
      }
      const removeIds = Array.isArray(json.remove)
        ? json.remove.map((x) => String(x || "").trim()).filter(Boolean).slice(0, 400)
        : [];
      const rawAdd = Array.isArray(json.add) ? json.add.slice(0, 400) : [];
      const added = [];
      for (const id of removeIds) {
        removeRoomStroke(room, code, id);
      }
      for (const raw of rawAdd) {
        const stored = strokeFromSocketMessage(
          typeof raw === "object" && raw ? { ...raw, type: "stroke" } : null,
          socket
        );
        if (!stored) continue;
        if (appendRoomStroke(room, code, stored)) added.push(stored);
      }
      touchCanvasActivity(room, room.sockets.size, user?.id || socket.luvUserId);
      const relay = JSON.stringify({
        type: "erase_commit",
        remove: removeIds,
        add: added.map((stored) => {
          const o = {
            type: "stroke",
            id: stored.id,
            width: stored.width,
            nickname: stored.nickname,
            colorIndex: stored.colorIndex,
            authorId: stored.authorId,
            points: stored.points,
            colorLocked: Boolean(stored.colorLocked),
          };
          if (stored.emoji) o.emoji = stored.emoji;
          if (stored.templateParts) {
            o.templateParts = stored.templateParts;
            o.templateScale = stored.templateScale;
            o.templateRotation = stored.templateRotation;
            if (stored.templateCoordSpace) o.templateCoordSpace = stored.templateCoordSpace;
          }
          return o;
        }),
      });
      relayToPeers(room, relay, peerId);
      return;
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
      touchCanvasActivity(room, room.sockets.size, user?.id || socket.luvUserId);
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
      if (!user || !socket.luvUserId) {
        socket.send(
          JSON.stringify({
            type: "economy_block",
            error: "auth_required",
            message: "Bitte einloggen zum Entfernen.",
          })
        );
        return;
      }
      const sid = String(json.id || "").trim();
      if (!sid) return;
      removeRoomStroke(room, code, sid);
      touchCanvasActivity(room, room.sockets.size, user?.id || socket.luvUserId);
      const undoRelay = JSON.stringify({ type: "undo", id: sid });
      const relay = JSON.stringify({ type: "sticker_remove", id: sid });
      relayToPeers(room, undoRelay, peerId);
      relayToPeers(room, relay, peerId);
      return;
    }

    if (type === "presence" && json.active === true) {
      // Nur Presence — kein lastCanvasAt (sonst Glow ohne Zeichnen)
      touchRoom(room);
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
    `luv-api :${PORT} rooms=${rooms.size} maxPeers=${MAX_PEERS} maxLobbies=${MAX_LOBBIES} clearCost=${CLEAR_COST} lobbyCreate=${LOBBY_CREATE_COST} shop=${shopPurchasesEnabled()}`
  );
  // Lootbox-Pool vorwärmen — erster Kauf sonst oft mehrere Sekunden
  setImmediate(() => {
    try {
      const pool = buildLootboxPoolForUser({ inventory: {} });
      console.log(`[lootbox] pool warmed (${pool?.length || 0} items)`);
    } catch (e) {
      console.error("[lootbox] pool warm failed", e);
    }
  });
  try {
    ensureWeddingDir();
    const n = marriage.recoverAllOrphanedWeddings(getDb(), WEDDING_DIR);
    if (n > 0) {
      scheduleSave();
      console.log(`[marriage] recovered ${n} orphaned wedding(s) on boot`);
    }
    // Vor dem Ja: Final-Bilder → Pending, sonst Boot-Recovery macht Ehepartner ohne Ja
    let demoted = 0;
    for (const m of Object.values(marriage.ensureMarriages(getDb()))) {
      if (!m || m.status === "married") continue;
      if (m.weddingImageFile) {
        demoteFinalWeddingImageToPending(m);
        demoted++;
      }
    }
    if (demoted > 0) {
      scheduleSave();
      console.log(`[marriage] demoted ${demoted} pre-vow wedding image(s) to pending`);
    }
    const retakes = ensureAllWeddingImageRetakes();
    if (retakes > 0) {
      console.log(`[marriage] wedding image retake lobbies: ${retakes}`);
    }
    const dupes = cleanupAllDuplicateWeddingPaintLobbies();
    if (dupes > 0) {
      console.log(`[marriage] removed ${dupes} duplicate wedding-paint lobby(ies)`);
    }
  } catch (e) {
    console.error("[marriage] boot recover failed", e);
  }
  try {
    seedShopCatalogIfNeeded();
    // Kein Full-Apply beim Boot — Rotation nur in der Nachtwartung (≈03:00) / Admin „Neu mischen“.
    const expired = shopCatalog.deactivateExpired(getDb());
    const rot = shopCalendar.ensureDefaultExpensiveRotation(getDb());
    scheduleSave();
    console.log(
      `[shop] catalog items=${Object.keys(shopCatalog.ensureShopCatalog(getDb()).items).length} expiredOff=${expired} rotation=${rot.created ? "seeded" : "ok"}`
    );
  } catch (e) {
    console.error("[shop] seed failed", e);
  }
});

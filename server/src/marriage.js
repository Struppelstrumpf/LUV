/**
 * Freundschaftslevel + Verlobung / Hochzeit / Ehe.
 * Level steigt max. 1×/Tag pro Freund durch Begleiter-Kraulen (0–100).
 */

const fs = require("fs");

const ENGAGE_WAIT_MS = 7 * 24 * 60 * 60 * 1000;
const WEDDING_LOBBY_MS = 7 * 24 * 60 * 60 * 1000;
const DIVORCE_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;
/** Einzigartiges Ehe-Item (Pet) — nicht im Shop, nicht handelbar, fällt bei Scheidung weg */
const MARRIAGE_PET = "💍";
const MARRIAGE_PET_LABEL = "Ehering";
/** Kapelle-Sticker — Heirats-Bonus, nicht handelbar (bleibt nach Scheidung) */
const MARRIAGE_CHAPEL_STICKER = "💒";
/** Volle 7-Tage-Wartezeit überspringen (Coins) */
const SKIP_WAIT_FULL_COST = 28;
/** Heirat ohne Level 100 — volle Kosten bei Level 0 */
const PROPOSE_UNLOCK_FULL_COST = 40;
/** Pro Partner: mind. so viele Striche auf der Hochzeitsleinwand vor Ehe */
const WEDDING_MIN_STROKES = 10;
const DAY_MS = 24 * 60 * 60 * 1000;

function countStrokesByAuthor(strokes, userId) {
  const uid = String(userId || "").trim();
  if (!uid || !Array.isArray(strokes)) return 0;
  let n = 0;
  for (const s of strokes) {
    if (String(s?.authorId || "").trim() === uid) n += 1;
  }
  return n;
}

function areWeddingStrokesReady(strokes, aId, bId, min = WEDDING_MIN_STROKES) {
  const need = Math.max(1, Math.floor(Number(min) || WEDDING_MIN_STROKES));
  return (
    countStrokesByAuthor(strokes, aId) >= need &&
    countStrokesByAuthor(strokes, bId) >= need
  );
}

/** Coins proportional zur Restzeit (mind. 4, max. SKIP_WAIT_FULL_COST). */
function skipWaitCost(remainingMs, fullMs = ENGAGE_WAIT_MS) {
  const rem = Math.max(0, Number(remainingMs) || 0);
  if (rem <= 0) return 0;
  const frac = Math.min(1, rem / Math.max(1, fullMs));
  return Math.max(4, Math.min(SKIP_WAIT_FULL_COST, Math.ceil(frac * SKIP_WAIT_FULL_COST)));
}

/** Coins um unter Level 100 zu heiraten (0 bei Level 100). */
function proposeUnlockCost(level) {
  const lv = Math.max(0, Math.min(100, Math.floor(Number(level) || 0)));
  if (lv >= 100) return 0;
  return Math.max(8, Math.ceil(((100 - lv) / 100) * PROPOSE_UNLOCK_FULL_COST));
}

function cooldownRemainingMs(user) {
  if (!user) return 0;
  // Nur nach aktiver Scheidung — verwaiste Cooldowns (Merge/Bug) ignorieren
  if (!user.marriageDivorcedAt) {
    if (Number(user.marriageCooldownUntil) || 0) user.marriageCooldownUntil = 0;
    return 0;
  }
  const rem = Math.max(0, (Number(user.marriageCooldownUntil) || 0) - Date.now());
  // Abgelaufen → Flags sofort löschen, damit nichts „null“/Ghost-UI triggert
  if (rem <= 0) {
    clearDivorceCooldown(user);
    return 0;
  }
  return rem;
}

function setDivorceCooldown(user) {
  if (!user) return;
  const now = Date.now();
  user.marriageDivorcedAt = now;
  user.marriageCooldownUntil = now + DIVORCE_COOLDOWN_MS;
}

function clearDivorceCooldown(user) {
  if (!user) return;
  user.marriageCooldownUntil = 0;
  user.marriageDivorcedAt = 0;
}

function formatRemaining(ms) {
  const rem = Math.max(0, Number(ms) || 0);
  const days = Math.floor(rem / DAY_MS);
  const hours = Math.floor((rem % DAY_MS) / (60 * 60 * 1000));
  if (days >= 1) return `${days}d ${hours}h`;
  const mins = Math.floor((rem % (60 * 60 * 1000)) / 60_000);
  if (hours >= 1) return `${hours}h ${mins}m`;
  return `${Math.max(1, mins)}m`;
}

function pairKey(a, b) {
  return [String(a), String(b)].sort().join("|");
}

function ensureMarriages(db) {
  if (!db.marriages || typeof db.marriages !== "object") db.marriages = {};
  return db.marriages;
}

function ensureFriendLevels(friends) {
  if (!friends.levels || typeof friends.levels !== "object") friends.levels = {};
  if (!friends.levelDays || typeof friends.levelDays !== "object") friends.levelDays = {};
  return friends;
}

function getLevel(user, otherId) {
  const f = user.friends;
  if (!f) return 0;
  ensureFriendLevels(f);
  return Math.max(0, Math.min(100, Math.floor(Number(f.levels[otherId]) || 0)));
}

function setLevelBoth(a, b, level) {
  const lv = Math.max(0, Math.min(100, Math.floor(Number(level) || 0)));
  ensureFriendLevels(a.friends);
  ensureFriendLevels(b.friends);
  a.friends.levels[b.id] = lv;
  b.friends.levels[a.id] = lv;
}

/**
 * +1 Freundschaftslevel / Tag, wenn beide an dem Tag auf derselben Lobby
 * mindestens einen Strich gesetzt haben (nicht mehr über Kraulen).
 */
function bumpLevelFromSharedCanvas(me, other, dayKey) {
  if (!me?.friends || !other?.friends || !me.id || !other.id) {
    return { bumped: false, level: 0 };
  }
  ensureFriendLevels(me.friends);
  ensureFriendLevels(other.friends);
  if (me.friends.levelDays[other.id] === dayKey) {
    return { bumped: false, level: getLevel(me, other.id) };
  }
  const next = Math.min(100, getLevel(me, other.id) + 1);
  setLevelBoth(me, other, next);
  me.friends.levelDays[other.id] = dayKey;
  other.friends.levelDays[me.id] = dayKey;
  return { bumped: true, level: next };
}

/** @deprecated — Level kommt von gemeinsamer Leinwand; Alias für Kompatibilität. */
function bumpLevelFromKraul(me, other, dayKey) {
  return bumpLevelFromSharedCanvas(me, other, dayKey);
}

/** Coins pro Kraul-Seite je nach Freundschaftslevel. */
function kraulCoinAmount(level) {
  const lv = Math.max(0, Math.floor(Number(level) || 0));
  if (lv >= 100) return 5;
  if (lv >= 50) return 3; // 50 und 75
  if (lv >= 25) return 2;
  return 1;
}

function resetLevelBoth(a, b) {
  if (a?.friends) {
    ensureFriendLevels(a.friends);
    delete a.friends.levels[b.id];
    delete a.friends.levelDays[b.id];
  }
  if (b?.friends) {
    ensureFriendLevels(b.friends);
    delete b.friends.levels[a.id];
    delete b.friends.levelDays[a.id];
  }
}

function findMarriageForUser(db, userId) {
  const all = ensureMarriages(db);
  for (const m of Object.values(all)) {
    if (!m) continue;
    if (m.a === userId || m.b === userId) return m;
  }
  return null;
}

/** Marriage-Record unter neuem pairKey speichern, alten Key entfernen. */
function rekeyMarriage(db, oldKey, m) {
  const all = ensureMarriages(db);
  const newKey = pairKey(m.a, m.b);
  m.id = newKey;
  if (oldKey !== newKey) delete all[oldKey];
  const existing = all[newKey];
  if (existing && existing !== m && isBusyStatus(existing.status) && !isBusyStatus(m.status)) {
    return existing;
  }
  all[newKey] = m;
  return m;
}

function parsePairFromWeddingFileName(name) {
  const base = String(name || "")
    .replace(/\.png$/i, "")
    .trim();
  const parts = base.split("|");
  if (parts.length !== 2) return null;
  const a = parts[0].trim();
  const b = parts[1].trim();
  if (!a || !b || a === b) return null;
  return a < b ? [a, b] : [b, a];
}

function inferPartnerFromWeddingFile(m, selfId) {
  const pair = parsePairFromWeddingFileName(m?.weddingImageFile || m?.id);
  if (!pair) return null;
  if (pair[0] === selfId) return pair[1];
  if (pair[1] === selfId) return pair[0];
  return null;
}

/**
 * User-ID in allen Ehe-/Verlobungs-Records umhängen (Google-Merge).
 * @returns {number} Anzahl geänderter Records
 */
function remapUserIdInMarriages(db, fromId, toId) {
  if (!fromId || !toId || fromId === toId) return 0;
  const all = ensureMarriages(db);
  let n = 0;
  for (const key of Object.keys(all)) {
    const m = all[key];
    if (!m) continue;
    let changed = false;
    if (m.a === fromId) {
      m.a = toId;
      changed = true;
    }
    if (m.b === fromId) {
      m.b = toId;
      changed = true;
    }
    if (m.proposedBy === fromId) {
      m.proposedBy = toId;
      changed = true;
    }
    if (!changed) continue;
    // Doppel-ID: nie busy Ehe / Bild / Gästebuch löschen
    if (m.a === m.b) {
      const partner =
        inferPartnerFromWeddingFile(m, toId) ||
        inferPartnerFromWeddingFile({ weddingImageFile: key }, toId);
      if (partner && partner !== toId && db.users?.[partner]) {
        m.a = toId < partner ? toId : partner;
        m.b = toId < partner ? partner : toId;
        rekeyMarriage(db, key, m);
      } else if (
        !isBusyStatus(m.status) &&
        !m.weddingImageFile &&
        !(Array.isArray(m.guestbook) && m.guestbook.length)
      ) {
        delete all[key];
      } else {
        console.error(
          "[marriage] remap collapsed a===b — keeping record",
          key,
          m.status,
          m.weddingImageFile
        );
      }
      n++;
      continue;
    }
    rekeyMarriage(db, key, m);
    n++;
  }
  return n;
}

/**
 * Verwaiste Hochzeitsbilder → Ehe-Record wiederherstellen.
 * Dateiname: `{userA}|{userB}.png`
 */
function recoverMarriageFromWeddingDir(db, user, weddingDir) {
  if (!user?.id || !weddingDir) return null;
  let files = [];
  try {
    if (!fs.existsSync(weddingDir)) return null;
    files = fs.readdirSync(weddingDir);
  } catch {
    return null;
  }
  const all = ensureMarriages(db);
  for (const name of files) {
    if (!/\.png$/i.test(name)) continue;
    const pair = parsePairFromWeddingFileName(name);
    if (!pair) continue;
    if (pair[0] !== user.id && pair[1] !== user.id) continue;
    const otherId = pair[0] === user.id ? pair[1] : pair[0];
    if (!db.users?.[otherId]) continue;
    const key = pairKey(user.id, otherId);
    let rec = all[key] || findMarriageBetween(db, user.id, otherId);
    if (rec && isBusyStatus(rec.status)) {
      // Bild nur verknüpfen — Ehering/Ehepartner erst nach beiden Ja
      if (rec.status === "married") {
        if (!rec.weddingImageFile) rec.weddingImageFile = name;
        grantMarriageItem(user);
        grantMarriageItem(db.users[otherId]);
      } else if (!rec.weddingImagePending && !rec.weddingImageFile) {
        rec.weddingImagePending = name;
      }
      if (!Array.isArray(rec.guestbook)) rec.guestbook = [];
      clearDivorceCooldown(user);
      clearDivorceCooldown(db.users[otherId]);
      return rec;
    }
    rec = {
      id: key,
      a: pair[0],
      b: pair[1],
      status: "married",
      marriedAt: Date.now(),
      weddingImageFile: name,
      guestbook: Array.isArray(rec?.guestbook) ? rec.guestbook : [],
      weddingLobbyCode: null,
    };
    all[key] = rec;
    grantMarriageItem(user);
    grantMarriageItem(db.users[otherId]);
    clearDivorceCooldown(user);
    clearDivorceCooldown(db.users[otherId]);
    console.log(`[marriage] recovered from wedding image ${name}`);
    return rec;
  }
  return null;
}

/** Alle verwaiste Hochzeitsbilder → Ehen (Boot / Deploy). */
function recoverAllOrphanedWeddings(db, weddingDir) {
  if (!weddingDir) return 0;
  let files = [];
  try {
    if (!fs.existsSync(weddingDir)) return 0;
    files = fs.readdirSync(weddingDir);
  } catch {
    return 0;
  }
  let n = 0;
  const all = ensureMarriages(db);
  for (const name of files) {
    if (!/\.png$/i.test(name)) continue;
    const pair = parsePairFromWeddingFileName(name);
    if (!pair) continue;
    const [aId, bId] = pair;
    if (!db.users?.[aId] || !db.users?.[bId]) continue;
    const key = pairKey(aId, bId);
    const existing = all[key] || findMarriageBetween(db, aId, bId);
    if (existing && isBusyStatus(existing.status)) {
      if (!existing.weddingImageFile) {
        existing.weddingImageFile = name;
        n++;
      }
      continue;
    }
    all[key] = {
      id: key,
      a: aId,
      b: bId,
      status: "married",
      marriedAt: Date.now(),
      weddingImageFile: name,
      guestbook: Array.isArray(existing?.guestbook) ? existing.guestbook : [],
      weddingLobbyCode: null,
    };
    grantMarriageItem(db.users[aId]);
    grantMarriageItem(db.users[bId]);
    clearDivorceCooldown(db.users[aId]);
    clearDivorceCooldown(db.users[bId]);
    n++;
    console.log(`[marriage] boot-recovered ${name}`);
  }
  return n;
}

/**
 * Verwaiste Wedding-Lobbys ↔ Marriage wieder verknüpfen
 * (z. B. nach altem Merge ohne Remap).
 */
function repairMarriageLinks(db, user, weddingDir) {
  if (!user?.id) return null;
  let m = findMarriageForUser(db, user.id);
  if (m) {
    if (isBusyStatus(m.status)) clearDivorceCooldown(user);
    return m;
  }
  const fromFile = recoverMarriageFromWeddingDir(db, user, weddingDir);
  if (fromFile) return fromFile;

  const weddingCodes = new Set();
  for (const [code, meta] of Object.entries(user.hostedRooms || {})) {
    if (meta?.isWedding) weddingCodes.add(code);
  }
  for (const [code, meta] of Object.entries(user.joinedRooms || {})) {
    if (meta?.isWedding) weddingCodes.add(code);
  }
  if (!weddingCodes.size) return null;

  const all = ensureMarriages(db);
  for (const key of Object.keys(all)) {
    const rec = all[key];
    if (!rec || !rec.weddingLobbyCode || !weddingCodes.has(rec.weddingLobbyCode)) continue;
    if (rec.a === user.id || rec.b === user.id) {
      if (isBusyStatus(rec.status)) clearDivorceCooldown(user);
      return rec;
    }
    const aOk = Boolean(db.users?.[rec.a]);
    const bOk = Boolean(db.users?.[rec.b]);
    if (!aOk && bOk) {
      rec.a = user.id;
      rekeyMarriage(db, key, rec);
      clearDivorceCooldown(user);
      return rec;
    }
    if (!bOk && aOk) {
      rec.b = user.id;
      rekeyMarriage(db, key, rec);
      clearDivorceCooldown(user);
      return rec;
    }
    // Beide IDs tot → aus Lobby-Mitgliedern rekonstruieren
    const room = db.rooms?.[rec.weddingLobbyCode];
    const members = (room?.memberUserIds || []).filter((id) => id && db.users?.[id]);
    if (members.includes(user.id) && members.length >= 2) {
      const other = members.find((id) => id !== user.id);
      if (other) {
        rec.a = user.id < other ? user.id : other;
        rec.b = user.id < other ? other : user.id;
        rekeyMarriage(db, key, rec);
        clearDivorceCooldown(user);
        return rec;
      }
    }
  }
  return null;
}

function findMarriageBetween(db, aId, bId) {
  const all = ensureMarriages(db);
  const key = pairKey(aId, bId);
  if (all[key]) return all[key];
  for (const m of Object.values(all)) {
    if (!m) continue;
    if (
      (m.a === aId && m.b === bId) ||
      (m.a === bId && m.b === aId)
    ) {
      return m;
    }
  }
  return null;
}

function partnerIdOf(marriage, userId) {
  if (!marriage) return null;
  if (marriage.a === userId) return marriage.b;
  if (marriage.b === userId) return marriage.a;
  return null;
}

function isBusyStatus(status) {
  return (
    status === "proposed" ||
    status === "engaged" ||
    status === "wedding" ||
    status === "ceremony_pending" ||
    status === "ceremony_scheduled" ||
    status === "married"
  );
}

function publicMarriage(m, viewerId, users, opts = {}) {
  if (!m) return null;
  const partnerId = partnerIdOf(m, viewerId);
  const partner = partnerId ? users?.[partnerId] : null;
  const now = Date.now();
  let engageRemainingMs = 0;
  let weddingRemainingMs = 0;
  if (m.status === "engaged") {
    engageRemainingMs = Math.max(0, (Number(m.engageReadyAt) || 0) - now);
  }
  if (m.status === "wedding") {
    weddingRemainingMs = Math.max(0, (Number(m.weddingEndsAt) || 0) - now);
  }
  const strokes = Array.isArray(opts?.strokes) ? opts.strokes : [];
  const weddingMyStrokes =
    m.status === "wedding" ? countStrokesByAuthor(strokes, viewerId) : 0;
  const weddingPartnerStrokes =
    m.status === "wedding" ? countStrokesByAuthor(strokes, partnerId) : 0;
  const weddingStrokesRequired = WEDDING_MIN_STROKES;
  const weddingStrokesReady =
    m.status !== "wedding" ||
    (weddingMyStrokes >= weddingStrokesRequired &&
      weddingPartnerStrokes >= weddingStrokesRequired);
  const engageFreeSkipUsed = m.engageFreeSkipUsed === true;
  // Verlobung: kein Coin-Skip — nur 1× gratis „Hochzeits-Lobby öffnen“
  const engageSkipCost = 0;
  // Wedding-Mal-Phase: Coin-Skip erst nach je 10 Strichen
  const weddingSkipCost =
    m.status === "wedding" && weddingStrokesReady
      ? skipWaitCost(weddingRemainingMs, WEDDING_LOBBY_MS)
      : 0;
  const ceremonyReady =
    m.status === "ceremony_pending" || m.status === "ceremony_scheduled";
  // Gift-View lazy laden (vermeidet Zyklus marriage ↔ wedding_gifts)
  let gift = {};
  try {
    gift = require("./wedding_gifts").publicGiftView(m, viewerId) || {};
  } catch {
    gift = {};
  }
  return {
    id: m.id || pairKey(m.a, m.b),
    status: m.status,
    partnerId,
    partnerNickname: partner
      ? String(partner.nickname || "").trim().slice(0, 18)
      : null,
    partnerPetEmoji: partner?.inventory?.equippedPet || "🐣",
    proposedBy: m.proposedBy || null,
    engagedAt: m.engagedAt || 0,
    engageReadyAt: m.engageReadyAt || 0,
    engageRemainingMs,
    weddingLobbyCode: m.weddingLobbyCode || null,
    weddingStartedAt: m.weddingStartedAt || 0,
    weddingEndsAt: m.weddingEndsAt || 0,
    weddingRemainingMs,
    marriedAt: m.marriedAt || 0,
    hasWeddingImage: Boolean(m.weddingImageFile || m.weddingImagePending),
    guestbookCount: Array.isArray(m.guestbook) ? m.guestbook.length : 0,
    engageSkipCost,
    weddingSkipCost,
    engageFreeSkipUsed,
    engageFreeSkipAvailable: m.status === "engaged" && !engageFreeSkipUsed,
    engageRemainingLabel:
      m.status === "engaged" ? formatRemaining(engageRemainingMs) : null,
    weddingRemainingLabel:
      m.status === "wedding" ? formatRemaining(weddingRemainingMs) : null,
    weddingMyStrokes,
    weddingPartnerStrokes,
    weddingStrokesRequired,
    weddingStrokesReady,
    weddingImageRetake: Boolean(m.weddingImageRetake),
    weddingConfirmMine: Boolean(
      m.weddingConfirm && typeof m.weddingConfirm === "object"
        ? m.weddingConfirm[viewerId]
        : false
    ),
    weddingConfirmPartner: Boolean(
      partnerId &&
        m.weddingConfirm &&
        typeof m.weddingConfirm === "object"
        ? m.weddingConfirm[partnerId]
        : false
    ),
    ceremonyReady,
    ceremonyAt: Number(m.ceremonyAt) || 0,
    ceremonyLobbyCode: m.ceremonyLobbyCode || null,
    ceremonyPhase: m.ceremony?.phase || "none",
    giftPhase: gift.giftPhase || "none",
    giftWindowEndsAt: Number(gift.giftWindowEndsAt) || 0,
    giftPoolCount: Number(gift.giftPoolCount) || 0,
    giftRemainingMs: Number(gift.giftRemainingMs) || 0,
    canGift: Boolean(gift.canGift),
    myGiftClaimReady: Boolean(gift.myGiftClaimReady),
    myGiftClaimed: Boolean(gift.myGiftClaimed),
    myGiftPreview: Array.isArray(gift.myGiftPreview) ? gift.myGiftPreview : [],
  };
}

function stripSpouseFromProfile(user) {
  const p = user?.profileCanvas;
  if (!p || !Array.isArray(p.layout)) return;
  p.layout = p.layout.filter((el) => {
    const t = String(el?.type || "").toLowerCase();
    return t !== "spouse" && t !== "engaged";
  });
}

function ensurePetsCountMap(inv) {
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

function clearMarriageItem(user) {
  if (!user?.inventory?.pets) return;
  const pets = ensurePetsCountMap(user.inventory);
  delete pets[MARRIAGE_PET];
  if (user.inventory.equippedPet === MARRIAGE_PET) {
    user.inventory.equippedPet = "🐣";
    if (user.profileCanvas) {
      user.profileCanvas.companionEmoji = "🐣";
    }
  }
}

function grantMarriageItem(user) {
  if (!user) return;
  if (!user.inventory) user.inventory = {};
  const pets = ensurePetsCountMap(user.inventory);
  if ((Number(pets[MARRIAGE_PET]) || 0) < 1) {
    pets[MARRIAGE_PET] = 1;
  }
  if (!user.inventory.stickers || typeof user.inventory.stickers !== "object") {
    user.inventory.stickers = {};
  }
  // Kapelle einmalig — nicht stapeln bei Recovery/Re-Grant
  if ((Number(user.inventory.stickers[MARRIAGE_CHAPEL_STICKER]) || 0) < 1) {
    user.inventory.stickers[MARRIAGE_CHAPEL_STICKER] = 1;
  }
}

module.exports = {
  ENGAGE_WAIT_MS,
  WEDDING_LOBBY_MS,
  DIVORCE_COOLDOWN_MS,
  MARRIAGE_PET,
  MARRIAGE_PET_LABEL,
  MARRIAGE_CHAPEL_STICKER,
  SKIP_WAIT_FULL_COST,
  PROPOSE_UNLOCK_FULL_COST,
  WEDDING_MIN_STROKES,
  DAY_MS,
  skipWaitCost,
  proposeUnlockCost,
  cooldownRemainingMs,
  setDivorceCooldown,
  clearDivorceCooldown,
  formatRemaining,
  pairKey,
  countStrokesByAuthor,
  areWeddingStrokesReady,
  ensureMarriages,
  ensureFriendLevels,
  getLevel,
  setLevelBoth,
  bumpLevelFromKraul,
  bumpLevelFromSharedCanvas,
  kraulCoinAmount,
  resetLevelBoth,
  findMarriageForUser,
  findMarriageBetween,
  partnerIdOf,
  isBusyStatus,
  publicMarriage,
  stripSpouseFromProfile,
  clearMarriageItem,
  grantMarriageItem,
  remapUserIdInMarriages,
  repairMarriageLinks,
  rekeyMarriage,
  recoverMarriageFromWeddingDir,
  recoverAllOrphanedWeddings,
  parsePairFromWeddingFileName,
};

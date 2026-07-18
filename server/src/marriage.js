/**
 * Freundschaftslevel + Verlobung / Hochzeit / Ehe.
 * Level steigt max. 1×/Tag pro Freund durch Begleiter-Kraulen (0–100).
 */

const ENGAGE_WAIT_MS = 7 * 24 * 60 * 60 * 1000;
const WEDDING_LOBBY_MS = 7 * 24 * 60 * 60 * 1000;
const DIVORCE_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;
/** Einzigartiges Ehe-Item (Pet) — nicht im Shop, fällt bei Scheidung weg */
const MARRIAGE_PET = "💍";
const MARRIAGE_PET_LABEL = "Ehering";
/** Volle 7-Tage-Wartezeit überspringen (Coins) */
const SKIP_WAIT_FULL_COST = 28;
/** Heirat ohne Level 100 — volle Kosten bei Level 0 */
const PROPOSE_UNLOCK_FULL_COST = 40;
const DAY_MS = 24 * 60 * 60 * 1000;

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
  return Math.max(0, (Number(user.marriageCooldownUntil) || 0) - Date.now());
}

function setDivorceCooldown(user) {
  if (!user) return;
  user.marriageCooldownUntil = Date.now() + DIVORCE_COOLDOWN_MS;
}

function clearDivorceCooldown(user) {
  if (!user) return;
  user.marriageCooldownUntil = 0;
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

/** +1 Level wenn heute noch nicht durch Kraulen erhöht (beide Seiten). */
function bumpLevelFromKraul(me, other, dayKey) {
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
    // Doppel-ID vermeiden
    if (m.a === m.b) {
      delete all[key];
      n++;
      continue;
    }
    rekeyMarriage(db, key, m);
    n++;
  }
  return n;
}

/**
 * Verwaiste Wedding-Lobbys ↔ Marriage wieder verknüpfen
 * (z. B. nach altem Merge ohne Remap).
 */
function repairMarriageLinks(db, user) {
  if (!user?.id) return null;
  let m = findMarriageForUser(db, user.id);
  if (m) {
    if (isBusyStatus(m.status)) clearDivorceCooldown(user);
    return m;
  }
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
    status === "married"
  );
}

function publicMarriage(m, viewerId, users) {
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
    hasWeddingImage: Boolean(m.weddingImageFile),
    guestbookCount: Array.isArray(m.guestbook) ? m.guestbook.length : 0,
    engageSkipCost:
      m.status === "engaged" ? skipWaitCost(engageRemainingMs, ENGAGE_WAIT_MS) : 0,
    weddingSkipCost:
      m.status === "wedding" ? skipWaitCost(weddingRemainingMs, WEDDING_LOBBY_MS) : 0,
    engageRemainingLabel:
      m.status === "engaged" ? formatRemaining(engageRemainingMs) : null,
    weddingRemainingLabel:
      m.status === "wedding" ? formatRemaining(weddingRemainingMs) : null,
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

function clearMarriageItem(user) {
  if (!user?.inventory?.pets) return;
  user.inventory.pets = user.inventory.pets.filter((p) => p !== MARRIAGE_PET);
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
  if (!Array.isArray(user.inventory.pets)) user.inventory.pets = [];
  if (!user.inventory.pets.includes(MARRIAGE_PET)) {
    user.inventory.pets.push(MARRIAGE_PET);
  }
}

module.exports = {
  ENGAGE_WAIT_MS,
  WEDDING_LOBBY_MS,
  DIVORCE_COOLDOWN_MS,
  MARRIAGE_PET,
  MARRIAGE_PET_LABEL,
  SKIP_WAIT_FULL_COST,
  PROPOSE_UNLOCK_FULL_COST,
  DAY_MS,
  skipWaitCost,
  proposeUnlockCost,
  cooldownRemainingMs,
  setDivorceCooldown,
  clearDivorceCooldown,
  formatRemaining,
  pairKey,
  ensureMarriages,
  ensureFriendLevels,
  getLevel,
  setLevelBoth,
  bumpLevelFromKraul,
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
};

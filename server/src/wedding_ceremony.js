/**
 * Hochzeits-Zeremonie (Zusatz-Lobby, ohne Canvas).
 * Unabhängig von Normal-/Event-/Random-/Hochzeitsbild-Lobbys.
 */

const CEREMONY_CAPACITY = 10; // 2 Brautpaar + 8 Gäste
const CEREMONY_MIN_AHEAD_MS = 30 * 60 * 1000;
const CEREMONY_MAX_AHEAD_MS = 14 * 24 * 60 * 60 * 1000;
const CEREMONY_OPEN_BEFORE_MS = 10 * 60 * 1000;
const PRESENCE_FRESH_MS = 45_000;
const VOW_HOLD_MS = 15_000;
const MAX_TIME_PROPOSALS_PER_USER = 3;

function ensureCeremony(m) {
  if (!m) return null;
  if (!m.ceremony || typeof m.ceremony !== "object") {
    m.ceremony = {
      presence: {},
      startConfirm: {},
      gatheringPresence: {},
      seated: {},
      positions: {},
      vows: {},
      reactions: {},
      timeProposals: {},
      phase: "none", // none | presence | scheduled | gathering | altar | vows | ended
      leftBy: null,
      leftNotify: {},
    };
  }
  if (!m.ceremony.presence || typeof m.ceremony.presence !== "object") m.ceremony.presence = {};
  if (!m.ceremony.startConfirm || typeof m.ceremony.startConfirm !== "object") {
    m.ceremony.startConfirm = {};
  }
  if (!m.ceremony.gatheringPresence || typeof m.ceremony.gatheringPresence !== "object") {
    m.ceremony.gatheringPresence = {};
  }
  if (!m.ceremony.seated || typeof m.ceremony.seated !== "object") m.ceremony.seated = {};
  if (!m.ceremony.positions || typeof m.ceremony.positions !== "object") m.ceremony.positions = {};
  if (!m.ceremony.vows || typeof m.ceremony.vows !== "object") m.ceremony.vows = {};
  if (!m.ceremony.reactions || typeof m.ceremony.reactions !== "object") m.ceremony.reactions = {};
  if (!m.ceremony.leftNotify || typeof m.ceremony.leftNotify !== "object") {
    m.ceremony.leftNotify = {};
  }
  if (!m.ceremony.timeProposals || typeof m.ceremony.timeProposals !== "object") {
    m.ceremony.timeProposals = {};
  }
  return m.ceremony;
}

function proposalListFor(c, userId) {
  const raw = c.timeProposals?.[userId];
  if (!Array.isArray(raw)) return [];
  return raw.map((n) => Math.floor(Number(n) || 0)).filter((n) => n > 0);
}

function proposeTime(m, userId, ceremonyAt) {
  const validated = validateCeremonyAt(ceremonyAt);
  if (!validated.ok) return validated;
  if (!isCouple(m, userId)) {
    return { ok: false, error: "couple_only", message: "Nur das Brautpaar." };
  }
  const c = ensureCeremony(m);
  const list = proposalListFor(c, userId);
  const at = validated.ceremonyAt;
  if (list.includes(at)) {
    return { ok: true, ceremonyAt: at, already: true };
  }
  if (list.length >= MAX_TIME_PROPOSALS_PER_USER) {
    return {
      ok: false,
      error: "too_many",
      message: `Maximal ${MAX_TIME_PROPOSALS_PER_USER} Vorschläge.`,
    };
  }
  list.push(at);
  list.sort((a, b) => a - b);
  c.timeProposals[userId] = list;
  return { ok: true, ceremonyAt: at };
}

function withdrawTime(m, userId, ceremonyAt) {
  if (!isCouple(m, userId)) {
    return { ok: false, error: "couple_only", message: "Nur das Brautpaar." };
  }
  const c = ensureCeremony(m);
  const at = Math.floor(Number(ceremonyAt) || 0);
  const list = proposalListFor(c, userId).filter((n) => n !== at);
  c.timeProposals[userId] = list;
  return { ok: true, ceremonyAt: at };
}

/** Accept erlaubt, wenn Partner den Slot vorgeschlagen hat oder beide denselben Slot haben. */
function canAcceptProposal(m, accepterId, ceremonyAt) {
  if (!isCouple(m, accepterId)) return false;
  const at = Math.floor(Number(ceremonyAt) || 0);
  if (!at) return false;
  const c = ensureCeremony(m);
  const partnerId = partnerOf(m, accepterId);
  if (!partnerId) return false;
  const mine = proposalListFor(c, accepterId);
  const theirs = proposalListFor(c, partnerId);
  return theirs.includes(at) || (mine.includes(at) && theirs.includes(at));
}

function listPublicProposals(m, viewerId, users = {}) {
  const c = ensureCeremony(m);
  const out = [];
  for (const uid of [m.a, m.b]) {
    if (!uid) continue;
    const nick =
      users[uid] ? String(users[uid].nickname || "").trim().slice(0, 18) || "Jemand" : "Jemand";
    for (const ceremonyAt of proposalListFor(c, uid)) {
      out.push({
        userId: uid,
        nickname: nick,
        mine: uid === viewerId,
        ceremonyAt,
      });
    }
  }
  out.sort((a, b) => a.ceremonyAt - b.ceremonyAt || Number(a.mine) - Number(b.mine));
  return out;
}

function matchingProposalAts(m) {
  const c = ensureCeremony(m);
  const a = new Set(proposalListFor(c, m.a));
  const b = proposalListFor(c, m.b);
  return b.filter((at) => a.has(at));
}

function isCouple(m, userId) {
  return m && (m.a === userId || m.b === userId);
}

function touchPresence(m, userId, bucket = "presence") {
  const c = ensureCeremony(m);
  const now = Date.now();
  if (bucket === "gathering") c.gatheringPresence[userId] = now;
  else c.presence[userId] = now;
  return now;
}

function countCouplePresence(m, bucket = "presence") {
  const c = ensureCeremony(m);
  const now = Date.now();
  const map = bucket === "gathering" ? c.gatheringPresence : c.presence;
  let n = 0;
  for (const uid of [m.a, m.b]) {
    if (now - (Number(map[uid]) || 0) <= PRESENCE_FRESH_MS) n += 1;
  }
  return n;
}

function isPresent(m, userId, bucket = "presence") {
  const c = ensureCeremony(m);
  const map = bucket === "gathering" ? c.gatheringPresence : c.presence;
  return Date.now() - (Number(map[userId]) || 0) <= PRESENCE_FRESH_MS;
}

function validateCeremonyAt(ts) {
  const at = Math.floor(Number(ts) || 0);
  const now = Date.now();
  if (!at || !Number.isFinite(at)) {
    return { ok: false, error: "bad_time", message: "Ungültiges Datum." };
  }
  if (at < now + CEREMONY_MIN_AHEAD_MS) {
    return {
      ok: false,
      error: "too_soon",
      message: "Mindestens 30 Minuten in der Zukunft.",
    };
  }
  if (at > now + CEREMONY_MAX_AHEAD_MS) {
    return {
      ok: false,
      error: "too_far",
      message: "Maximal 14 Tage in der Zukunft.",
    };
  }
  return { ok: true, ceremonyAt: at };
}

function ceremonyOpenForEntry(m) {
  const at = Number(m?.ceremonyAt) || 0;
  if (!at) return false;
  return Date.now() >= at - CEREMONY_OPEN_BEFORE_MS;
}

function publicCeremony(m, viewerId, users = {}) {
  if (!m) return null;
  const c = ensureCeremony(m);
  const now = Date.now();
  const couplePresent = countCouplePresence(m, "presence");
  const startConfirmA = Boolean(c.startConfirm[m.a]);
  const startConfirmB = Boolean(c.startConfirm[m.b]);
  const partnerId = m.a === viewerId ? m.b : m.a === viewerId ? m.a : partnerOf(m, viewerId);
  const partnerNick =
    partnerId && users[partnerId]
      ? String(users[partnerId].nickname || "").trim().slice(0, 18)
      : null;

  const memberIds = Array.isArray(m.ceremonyMemberIds) ? m.ceremonyMemberIds : [m.a, m.b];
  const gathering = memberIds.map((uid) => {
    const u = users[uid];
    return {
      userId: uid,
      nickname: u ? String(u.nickname || "").trim().slice(0, 18) || "Jemand" : "Jemand",
      petEmoji: u?.inventory?.equippedPet || "🐣",
      present: isPresent(m, uid, "gathering"),
      isCouple: uid === m.a || uid === m.b,
      seatedSeatId: c.seated[uid] || null,
      x: Number(c.positions[uid]?.x) || 0.5,
      y: Number(c.positions[uid]?.y) || 0.75,
      reaction: c.reactions[uid]?.emoji || null,
      reactionUntil: Number(c.reactions[uid]?.until) || 0,
      vow: c.vows[uid]?.choice || null,
      vowProgress: Number(c.vows[uid]?.progress) || 0,
    };
  });
  const gatheringPresentCount = gathering.filter((g) => g.present).length;
  const timeProposals = listPublicProposals(m, viewerId, users);
  const matchingAts = matchingProposalAts(m);

  return {
    phase: c.phase || "none",
    ceremonyAt: Number(m.ceremonyAt) || 0,
    ceremonyLobbyCode: m.ceremonyLobbyCode || null,
    couplePresent,
    coupleRequired: 2,
    partnerPresent: partnerId ? isPresent(m, partnerId, "presence") : false,
    partnerNickname: partnerNick,
    startConfirmMine: Boolean(c.startConfirm[viewerId]),
    startConfirmPartner: partnerId ? Boolean(c.startConfirm[partnerId]) : false,
    startConfirmReady: startConfirmA && startConfirmB,
    openForEntry: ceremonyOpenForEntry(m),
    msUntilCeremony: Math.max(0, (Number(m.ceremonyAt) || 0) - now),
    msUntilOpen: Math.max(0, (Number(m.ceremonyAt) || 0) - CEREMONY_OPEN_BEFORE_MS - now),
    gathering,
    gatheringPresentCount,
    gatheringTotal: gathering.length,
    allGathered: computeAllGathered(m),
    leftNotify: Boolean(c.leftNotify[viewerId]),
    leftByNickname: c.leftByNickname || null,
    capacity: CEREMONY_CAPACITY,
    timeProposals,
    matchingProposalAts: matchingAts,
  };
}

function partnerOf(m, userId) {
  if (!m) return null;
  if (m.a === userId) return m.b;
  if (m.b === userId) return m.a;
  return null;
}

/** allGathered: jedes aktuelle Lobby-Mitglied ist present */
function computeAllGathered(m) {
  const memberIds = Array.isArray(m.ceremonyMemberIds) ? m.ceremonyMemberIds : [m.a, m.b];
  if (!memberIds.length) return false;
  return memberIds.every((uid) => isPresent(m, uid, "gathering"));
}

module.exports = {
  CEREMONY_CAPACITY,
  CEREMONY_MIN_AHEAD_MS,
  CEREMONY_MAX_AHEAD_MS,
  CEREMONY_OPEN_BEFORE_MS,
  PRESENCE_FRESH_MS,
  VOW_HOLD_MS,
  MAX_TIME_PROPOSALS_PER_USER,
  ensureCeremony,
  isCouple,
  touchPresence,
  countCouplePresence,
  isPresent,
  validateCeremonyAt,
  ceremonyOpenForEntry,
  publicCeremony,
  partnerOf,
  computeAllGathered,
  proposeTime,
  withdrawTime,
  canAcceptProposal,
  listPublicProposals,
  matchingProposalAts,
};

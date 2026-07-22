/**
 * Hochzeits-Zeremonie (Zusatz-Lobby, ohne Canvas).
 * Unabhängig von Normal-/Event-/Random-/Hochzeitsbild-Lobbys.
 */

const CEREMONY_CAPACITY = 10; // 2 Brautpaar + 8 Gäste
const CEREMONY_MIN_AHEAD_MS = 10 * 60 * 1000;
const CEREMONY_MAX_AHEAD_MS = 14 * 24 * 60 * 60 * 1000;
const CEREMONY_OPEN_BEFORE_MS = 10 * 60 * 1000;
/** Länger + Heartbeat im Client — Idle-Gäste bleiben sichtbar */
const PRESENCE_FRESH_MS = 120_000;
const VOW_HOLD_MS = 15_000;
const ALTAR_HOLD_MS = 30_000;
const RECEPTION_MS = 60 * 60 * 1000;
const PASTOR_LINE_HOLD_MS = 5_000;
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
      phase: "none", // none | presence | scheduled | gathering | altar | vows | gifts | reception | ended
      leftBy: null,
      leftNotify: {},
      seatingLocked: false,
      altarHoldStartedAt: 0,
      pastorPhase: "idle", // idle | dots | speech | vows | closing_no | married | reception
      pastorLineIndex: 0,
      pastorLineStartedAt: 0,
      receptionEndsAt: 0,
      giftedBy: {},
      guestbookedBy: {},
      applauseBursts: [],
      confettiBursts: [],
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
  if (!m.ceremony.giftedBy || typeof m.ceremony.giftedBy !== "object") m.ceremony.giftedBy = {};
  if (!m.ceremony.guestbookedBy || typeof m.ceremony.guestbookedBy !== "object") {
    m.ceremony.guestbookedBy = {};
  }
  if (!m.ceremony.moneyTreesClaimed || typeof m.ceremony.moneyTreesClaimed !== "object") {
    m.ceremony.moneyTreesClaimed = {};
  }
  if (!Array.isArray(m.ceremony.applauseBursts)) m.ceremony.applauseBursts = [];
  if (!Array.isArray(m.ceremony.confettiBursts)) m.ceremony.confettiBursts = [];
  if (typeof m.ceremony.seatingLocked !== "boolean") m.ceremony.seatingLocked = false;
  if (!Number.isFinite(Number(m.ceremony.altarHoldStartedAt))) m.ceremony.altarHoldStartedAt = 0;
  if (!m.ceremony.pastorPhase) m.ceremony.pastorPhase = "idle";
  if (!Number.isFinite(Number(m.ceremony.pastorLineIndex))) m.ceremony.pastorLineIndex = 0;
  if (!Number.isFinite(Number(m.ceremony.pastorLineStartedAt))) m.ceremony.pastorLineStartedAt = 0;
  if (!Number.isFinite(Number(m.ceremony.receptionEndsAt))) m.ceremony.receptionEndsAt = 0;
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

/** Raum verlassen — Avatar sofort weg für alle anderen */
function clearGatheringPresence(m, userId) {
  const c = ensureCeremony(m);
  if (!c || !userId) return;
  delete c.gatheringPresence[userId];
  // Sitz freigeben, solange die Zeremonie nicht gelockt ist
  if (!c.seatingLocked && c.seated) {
    delete c.seated[userId];
  }
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
      message: "Mindestens 10 Minuten in der Zukunft.",
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

function nickOf(users, uid, fallback = "Jemand") {
  const u = users?.[uid];
  return u ? String(u.nickname || "").trim().slice(0, 18) || fallback : fallback;
}

function formatDeDay(ms) {
  const t = Number(ms) || 0;
  if (!t) return "einem besonderen Tag";
  try {
    return new Intl.DateTimeFormat("de-DE", {
      day: "numeric",
      month: "long",
      year: "numeric",
    }).format(new Date(t));
  } catch {
    return "einem besonderen Tag";
  }
}

function buildPastorLines(m, users = {}) {
  const nameA = nickOf(users, m.a, "Name 1");
  const nameB = nickOf(users, m.b, "Name 2");
  const proposerId = m.proposedBy || m.a;
  const proposeeId = proposerId === m.a ? m.b : m.a;
  const proposer = nickOf(users, proposerId, nameA);
  const proposee = nickOf(users, proposeeId, nameB);
  const day = formatDeDay(m.engagedAt || m.proposedAt || m.ceremonyAt);
  return [
    `Liebes Brautpaar, liebe Gäste, wir haben uns heute hier eingefunden, um ${nameA} und ${nameB} in den heiligen Bund der Ehe zu führen.`,
    `Am ${day} hat ${proposer} ${proposee} den Heiratsantrag gemacht — ein Moment, der euch bis hierher geführt hat.`,
    `${nameA} und ${nameB}, vor euren Gästen und vor diesem Altar frage ich euch nun: Wollt ihr einander die Ehe versprechen?`,
  ];
}

function buildPastorRejectLines(m, users, rejectedByUserId) {
  const nameA = nickOf(users, m.a, "Name 1");
  const nameB = nickOf(users, m.b, "Name 2");
  const who = nickOf(users, rejectedByUserId, "Jemand");
  return [
    `Oh, damit habe ich nicht gerechnet. ${who} hat leider Nein gesagt.`,
    `Nun, ${nameA} und ${nameB} — ich wünsche euch trotzdem von Herzen alles Gute auf euren Wegen.`,
  ];
}

function buildPastorMarriedLines(m, users = {}) {
  const nameA = nickOf(users, m.a, "Name 1");
  const nameB = nickOf(users, m.b, "Name 2");
  return [
    `Dann erkläre ich euch hiermit zu Mann und Frau. ${nameA} und ${nameB} — ihr seid verheiratet!`,
    `Liebe Gäste, feiert mit dem Brautpaar. Die Empfangszeit hat begonnen.`,
  ];
}

/** Beide Eheleute sitzen auf gelben Altar-Plätzen? */
function coupleAtAltar(m, isCoupleSeatFn) {
  const c = ensureCeremony(m);
  if (!c.seated[m.a] || !c.seated[m.b]) return false;
  if (typeof isCoupleSeatFn !== "function") return true;
  return isCoupleSeatFn(c.seated[m.a]) && isCoupleSeatFn(c.seated[m.b]);
}

/**
 * Altar-Hold synchronisieren.
 * @param {object} m marriage
 * @param {(seatId:string)=>boolean} isCoupleSeatFn
 */
function syncAltarHold(m, isCoupleSeatFn) {
  const c = ensureCeremony(m);
  if (c.seatingLocked || c.pastorPhase === "reception" || c.phase === "reception") {
    return { changed: false };
  }
  if (["closing_no", "married", "ended"].includes(c.pastorPhase)) {
    return { changed: false };
  }
  const atAltar = coupleAtAltar(m, isCoupleSeatFn);
  const now = Date.now();
  let changed = false;
  if (!atAltar) {
    if (c.altarHoldStartedAt) {
      c.altarHoldStartedAt = 0;
      changed = true;
    }
    return { changed, atAltar: false };
  }
  if (!c.altarHoldStartedAt) {
    c.altarHoldStartedAt = now;
    if (c.phase === "altar" || c.phase === "gathering") c.phase = "altar";
    changed = true;
  }
  if (c.altarHoldStartedAt && now - c.altarHoldStartedAt >= ALTAR_HOLD_MS) {
    c.seatingLocked = true;
    c.altarHoldStartedAt = c.altarHoldStartedAt; // freeze
    c.pastorPhase = "dots";
    c.pastorLineIndex = 0;
    c.pastorLineStartedAt = now;
    c.phase = "vows";
    changed = true;
  }
  return { changed, atAltar: true };
}

function advancePastor(m, users = {}) {
  const c = ensureCeremony(m);
  const now = Date.now();
  if (c.pastorPhase === "idle" || !c.seatingLocked) return { changed: false };

  if (c.pastorPhase === "dots") {
    // Client zeigt Punkte; nach kurzer Pause → Rede
    if (now - (Number(c.pastorLineStartedAt) || 0) >= 2800) {
      c.pastorPhase = "speech";
      c.pastorLineIndex = 0;
      c.pastorLineStartedAt = now;
      return { changed: true };
    }
    return { changed: false };
  }

  if (c.pastorPhase === "speech") {
    const lines = buildPastorLines(m, users);
    const idx = Number(c.pastorLineIndex) || 0;
    const started = Number(c.pastorLineStartedAt) || now;
    // Typdauer grob + 5s Halt
    const line = lines[idx] || "";
    const typeMs = Math.min(12_000, Math.max(2_500, line.length * 45));
    if (now - started >= typeMs + PASTOR_LINE_HOLD_MS) {
      if (idx + 1 < lines.length) {
        c.pastorLineIndex = idx + 1;
        c.pastorLineStartedAt = now;
      } else {
        c.pastorPhase = "vows";
        c.pastorLineIndex = 0;
        c.pastorLineStartedAt = now;
      }
      return { changed: true };
    }
    return { changed: false };
  }

  if (c.pastorPhase === "closing_no") {
    const lines = buildPastorRejectLines(m, users, c.rejectUserId);
    const idx = Number(c.pastorLineIndex) || 0;
    const started = Number(c.pastorLineStartedAt) || now;
    const line = lines[idx] || "";
    const typeMs = Math.min(10_000, Math.max(2_000, line.length * 45));
    if (now - started >= typeMs + PASTOR_LINE_HOLD_MS) {
      if (idx + 1 < lines.length) {
        c.pastorLineIndex = idx + 1;
        c.pastorLineStartedAt = now;
        return { changed: true };
      }
      c.pastorPhase = "ended";
      c.phase = "ended";
      return { changed: true, rejectDone: true };
    }
    return { changed: false };
  }

  if (c.pastorPhase === "married") {
    const lines = buildPastorMarriedLines(m, users);
    const idx = Number(c.pastorLineIndex) || 0;
    const started = Number(c.pastorLineStartedAt) || now;
    const line = lines[idx] || "";
    const typeMs = Math.min(10_000, Math.max(2_000, line.length * 45));
    if (now - started >= typeMs + PASTOR_LINE_HOLD_MS) {
      if (idx + 1 < lines.length) {
        c.pastorLineIndex = idx + 1;
        c.pastorLineStartedAt = now;
        return { changed: true };
      }
      c.pastorPhase = "reception";
      c.phase = "reception";
      if (!c.receptionEndsAt) c.receptionEndsAt = now + RECEPTION_MS;
      return { changed: true, receptionStarted: true };
    }
    return { changed: false };
  }

  return { changed: false };
}

function startRejectClosing(m, rejectedByUserId) {
  const c = ensureCeremony(m);
  c.pastorPhase = "closing_no";
  c.rejectUserId = rejectedByUserId;
  c.pastorLineIndex = 0;
  c.pastorLineStartedAt = Date.now();
  c.phase = "vows";
}

function startMarriedSpeech(m) {
  const c = ensureCeremony(m);
  c.pastorPhase = "married";
  c.pastorLineIndex = 0;
  c.pastorLineStartedAt = Date.now();
  c.phase = "gifts";
  // Daumen/Vow-UI zurücksetzen
  c.vows = {};
  if (!c.receptionEndsAt) {
    // Empfang startet nach Abschluss-Rede; vorläufig setzen für Home-Timer
    c.receptionEndsAt = Date.now() + RECEPTION_MS;
  }
}

function canStand(m, userId) {
  const c = ensureCeremony(m);
  if (!c.seatingLocked) return true;
  // Bis Pastor fertig gesprochen hat (inkl. „ihr seid verheiratet“) sitzen bleiben
  if (["dots", "speech", "vows", "closing_no", "married"].includes(c.pastorPhase)) {
    return false;
  }
  // Empfang: Plätze wieder verlassen
  return true;
}

function canSitAfterLock(m, userId) {
  const c = ensureCeremony(m);
  if (!c.seatingLocked) return true;
  // Stehende dürfen sich noch setzen; Sitzende bleiben
  return !c.seated[userId] || true;
}

/** Darf jemand die Kapelle betreten / rejoinen? */
function canEnterChapel(m, userId, { isKnownMember = false } = {}) {
  const c = ensureCeremony(m);
  const now = Date.now();
  if (
    c.receptionEnded ||
    c.pastorPhase === "gifts_claim" ||
    c.phase === "gifts_claim"
  ) {
    return {
      ok: false,
      error: "reception_over",
      message: isCouple(m, userId)
        ? "Empfang vorbei — hole deine Geschenke auf dem Home ab."
        : "Die Empfangszeit ist vorbei.",
    };
  }
  if (c.pastorPhase === "reception" || c.phase === "reception") {
    const ends = Number(c.receptionEndsAt) || 0;
    if (ends > 0 && now > ends) {
      return {
        ok: false,
        error: "reception_over",
        message: "Die Empfangszeit ist vorbei.",
      };
    }
    return { ok: true };
  }
  if (m.status === "married" && (c.phase === "gifts" || c.pastorPhase === "married")) {
    return { ok: true };
  }
  // Während Altar-Hold (noch nicht locked): Rejoin ok
  if (!c.seatingLocked) {
    return { ok: true };
  }
  // Zeremonie läuft (nach 30s) — nicht reinplatzen
  if (isCouple(m, userId)) {
    // Brautpaar darf drin bleiben / reconnect
    return { ok: true };
  }
  if (isKnownMember && isPresent(m, userId, "gathering")) {
    return { ok: true };
  }
  return {
    ok: false,
    error: "ceremony_in_progress",
    message:
      "Die Zeremonie ist im Gange — es wäre unhöflich, jetzt hereinzuplatzen. Bitte wartet bis zum Empfang.",
  };
}

function altarHoldRemainingMs(m) {
  const c = ensureCeremony(m);
  if (c.seatingLocked || !c.altarHoldStartedAt) return 0;
  return Math.max(0, ALTAR_HOLD_MS - (Date.now() - Number(c.altarHoldStartedAt)));
}

function receptionRemainingMs(m) {
  const c = ensureCeremony(m);
  if (c.receptionEnded) return 0;
  const ends = Number(c.receptionEndsAt) || 0;
  if (!ends) return 0;
  return Math.max(0, ends - Date.now());
}

function pruneStaleGathering(m) {
  const c = ensureCeremony(m);
  if (!c || c.seatingLocked) return;
  for (const uid of Object.keys(c.seated || {})) {
    if (!isPresent(m, uid, "gathering")) {
      delete c.seated[uid];
    }
  }
}

function publicCeremony(m, viewerId, users = {}) {
  if (!m) return null;
  const c = ensureCeremony(m);
  pruneStaleGathering(m);
  const now = Date.now();
  const couplePresent = countCouplePresence(m, "presence");
  const startConfirmA = Boolean(c.startConfirm[m.a]);
  const startConfirmB = Boolean(c.startConfirm[m.b]);
  const partnerId = m.a === viewerId ? m.b : m.b === viewerId ? m.a : partnerOf(m, viewerId);
  const partnerNick =
    partnerId && users[partnerId]
      ? String(users[partnerId].nickname || "").trim().slice(0, 18)
      : null;

  const memberIds = Array.isArray(m.ceremonyMemberIds) ? m.ceremonyMemberIds : [m.a, m.b];
  const gathering = memberIds.map((uid) => {
    const u = users[uid];
    const present = isPresent(m, uid, "gathering");
    return {
      userId: uid,
      nickname: u ? String(u.nickname || "").trim().slice(0, 18) || "Jemand" : "Jemand",
      petEmoji: u?.inventory?.equippedPet || "🐣",
      present,
      isCouple: uid === m.a || uid === m.b,
      // Offline: kein Sitz / Default-Spawn — sonst „Geister“ am alten Platz
      seatedSeatId: present ? c.seated[uid] || null : null,
      x: present ? Number(c.positions[uid]?.x) || 0.5 : 0.5,
      y: present ? Number(c.positions[uid]?.y) || 0.75 : 0.86,
      reaction: present ? c.reactions[uid]?.emoji || null : null,
      reactionUntil: present ? Number(c.reactions[uid]?.until) || 0 : 0,
      vow: c.vows[uid]?.choice || null,
      vowProgress: Number(c.vows[uid]?.progress) || 0,
    };
  });
  const gatheringPresentCount = gathering.filter((g) => g.present).length;
  const timeProposals = listPublicProposals(m, viewerId, users);
  const matchingAts = matchingProposalAts(m);

  let pastorLines = [];
  if (c.pastorPhase === "speech") pastorLines = buildPastorLines(m, users);
  else if (c.pastorPhase === "closing_no") {
    pastorLines = buildPastorRejectLines(m, users, c.rejectUserId);
  } else if (c.pastorPhase === "married") {
    pastorLines = buildPastorMarriedLines(m, users);
  }
  const pastorLineIndex = Number(c.pastorLineIndex) || 0;
  const pastorLineFull = pastorLines[pastorLineIndex] || "";
  const pastorStarted = Number(c.pastorLineStartedAt) || now;
  const typeMs = Math.min(12_000, Math.max(2_000, pastorLineFull.length * 45 || 2000));
  const typedChars = Math.min(
    pastorLineFull.length,
    Math.floor(((now - pastorStarted) / typeMs) * pastorLineFull.length)
  );
  const pastorLineVisible =
    c.pastorPhase === "speech" ||
    c.pastorPhase === "closing_no" ||
    c.pastorPhase === "married"
      ? pastorLineFull.slice(0, typedChars)
      : "";

  const coupleNicks = {
    a: nickOf(users, m.a, "Name 1"),
    b: nickOf(users, m.b, "Name 2"),
  };

  const meGuest = !isCouple(m, viewerId);
  const iGifted = Boolean(c.giftedBy[viewerId]);
  const iGuestbooked = Boolean(c.guestbookedBy[viewerId]);
  const inReception =
    !c.receptionEnded &&
    receptionRemainingMs(m) > 0 &&
    (c.pastorPhase === "reception" || c.phase === "reception");

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
    // Ritual
    seatingLocked: Boolean(c.seatingLocked),
    altarHoldActive: Boolean(c.altarHoldStartedAt) && !c.seatingLocked,
    altarHoldRemainingMs: altarHoldRemainingMs(m),
    altarHoldTotalMs: ALTAR_HOLD_MS,
    canStand: canStand(m, viewerId),
    pastorPhase: c.pastorPhase || "idle",
    pastorLineIndex,
    pastorLineFull,
    pastorLineVisible,
    pastorLineCount: pastorLines.length,
    coupleNicknames: coupleNicks,
    receptionEndsAt: Number(c.receptionEndsAt) || 0,
    receptionRemainingMs: receptionRemainingMs(m),
    showGiftButton: Boolean(meGuest && inReception && !iGifted && m.status === "married"),
    showGuestbookButton: Boolean(meGuest && inReception && (iGifted || iGuestbooked)),
    showApplause: Boolean(meGuest && inReception && m.status === "married"),
    iGifted,
    iGuestbooked,
    vowsReady: c.pastorPhase === "vows",
    marriageStatus: m.status || null,
    applauseBursts: (c.applauseBursts || [])
      .slice(-12)
      .map((b) => ({
        userId: b.userId || null,
        at: Number(b.at) || 0,
        emoji: b.emoji || "👏",
        x: Number.isFinite(Number(b.x)) ? Number(b.x) : 0.5,
        y: Number.isFinite(Number(b.y)) ? Number(b.y) : 0.7,
      })),
    confettiBursts: (c.confettiBursts || [])
      .slice(-12)
      .map((b) => ({
        userId: b.userId || null,
        at: Number(b.at) || 0,
        x: Number.isFinite(Number(b.x)) ? Number(b.x) : 0.5,
        y: Number.isFinite(Number(b.y)) ? Number(b.y) : 0.7,
      })),
    claimedMoneyTreeIds: Object.keys(c.moneyTreesClaimed || {})
      .filter((k) => k.startsWith(`${viewerId}:`))
      .map((k) => k.slice(String(viewerId).length + 1))
      .filter(Boolean),
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
  ALTAR_HOLD_MS,
  RECEPTION_MS,
  PASTOR_LINE_HOLD_MS,
  MAX_TIME_PROPOSALS_PER_USER,
  ensureCeremony,
  isCouple,
  touchPresence,
  clearGatheringPresence,
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
  syncAltarHold,
  advancePastor,
  startRejectClosing,
  startMarriedSpeech,
  canStand,
  canEnterChapel,
  coupleAtAltar,
  buildPastorLines,
  altarHoldRemainingMs,
  receptionRemainingMs,
};

/**
 * Hochzeits-Geschenke: gemeinsamer Topf → fairer Split → Claim.
 */

const GIFT_WINDOW_MS = 24 * 60 * 60 * 1000;
const GIFT_CLAIM_GRACE_MS = 7 * 24 * 60 * 60 * 1000;
const MAX_GIFTS_PER_GIVER = 20;
const MAX_POOL = 100;
const ALLOWED_KINDS = new Set(["emojis", "stickers", "pets", "themes"]);

/** Trost-Geschenke wenn der Topf leer bleibt — alles Shop ≤20 Coins. */
const CONSOLATION_ITEMS = [
  { kind: "stickers", itemId: "🍰" }, // 5 — Tortenstück
  { kind: "stickers", itemId: "🎀" }, // 12 — Schleife
  { kind: "stickers", itemId: "✨" }, // 17
  { kind: "stickers", itemId: "🍪" }, // 20
  { kind: "stickers", itemId: "🌼" }, // 5
  { kind: "emojis", itemId: "🤍" }, // 8
  { kind: "emojis", itemId: "💕" }, // 12
  { kind: "emojis", itemId: "🍓" }, // 6
];

function ensureGiftState(m) {
  if (!m) return null;
  if (!Array.isArray(m.giftPool)) m.giftPool = [];
  if (!m.giftSplit || typeof m.giftSplit !== "object") m.giftSplit = {};
  if (!m.giftClaimed || typeof m.giftClaimed !== "object") m.giftClaimed = {};
  if (!m.giftPhase) m.giftPhase = "none"; // none | open | rolled | done
  return m;
}

function openGiftWindow(m, now = Date.now()) {
  ensureGiftState(m);
  m.giftWindowEndsAt = now + GIFT_WINDOW_MS;
  m.giftPhase = "open";
  m.giftSplit = {};
  m.giftClaimed = {};
  if (!Array.isArray(m.giftPool)) m.giftPool = [];
}

function canGiftToMarriage(m, now = Date.now()) {
  if (!m) return false;
  ensureGiftState(m);
  if (m.giftPhase === "open") {
    return now < (Number(m.giftWindowEndsAt) || 0);
  }
  // Während Zeremonie (vor Ja) ebenfalls schenken
  if (
    m.status === "ceremony_scheduled" &&
    (m.ceremony?.phase === "gathering" ||
      m.ceremony?.phase === "altar" ||
      m.ceremony?.phase === "vows")
  ) {
    return true;
  }
  return false;
}

function giverGiftCount(m, userId) {
  ensureGiftState(m);
  return m.giftPool.filter((g) => g && g.fromUserId === userId).length;
}

function hashSeed(str) {
  let h = 2166136261;
  const s = String(str || "");
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function mulberry32(a) {
  return function next() {
    let t = (a += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function shuffleInPlace(arr, rng) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    const tmp = arr[i];
    arr[i] = arr[j];
    arr[j] = tmp;
  }
  return arr;
}

function consolationEntry(rng) {
  const pick =
    CONSOLATION_ITEMS[Math.floor(rng() * CONSOLATION_ITEMS.length)] ||
    CONSOLATION_ITEMS[0];
  return {
    kind: pick.kind,
    itemId: pick.itemId,
    fromNickname: "LUV",
    fromUserId: null,
    giftId: null,
    consolation: true,
  };
}

/**
 * Pool fair auf A/B verteilen.
 * - 1 Item: Münzwurf, wer es bekommt
 * - 0 Items: beide je ein kleines Shop-Favor (≤20 Coins) von LUV
 */
function rollGiftPool(m) {
  ensureGiftState(m);
  if (m.giftPhase === "rolled" || m.giftPhase === "done") return { ok: true, already: true };
  const pool = [...(m.giftPool || [])].filter((g) => g && g.kind && g.itemId);
  const seed = hashSeed(`${m.id || m.a}|${m.b}|${m.giftWindowEndsAt || 0}`);
  const rng = mulberry32(seed);

  // Nichts geschenkt → kleines Andenken für beide
  if (pool.length === 0) {
    const favorA = consolationEntry(rng);
    const favorB = consolationEntry(rng);
    m.giftSplit = {
      [m.a]: [favorA],
      [m.b]: [favorB],
    };
    m.giftClaimed = {};
    m.giftPhase = "rolled";
    return { ok: true, count: 0, consolation: true };
  }

  shuffleInPlace(pool, rng);
  // 1 Item: Münzwurf; mehrere: abwechselnd ab zufälligem Start
  const startA = rng() < 0.5;
  const splitA = [];
  const splitB = [];
  pool.forEach((g, i) => {
    const toA = startA ? i % 2 === 0 : i % 2 === 1;
    const entry = {
      kind: g.kind,
      itemId: g.itemId,
      fromNickname: g.fromNickname || "Jemand",
      fromUserId: g.fromUserId || null,
      giftId: g.id || null,
    };
    if (toA) splitA.push(entry);
    else splitB.push(entry);
  });
  m.giftSplit = {
    [m.a]: splitA,
    [m.b]: splitB,
  };
  m.giftClaimed = {};
  m.giftPhase = "rolled";
  return { ok: true, count: pool.length };
}

function publicGiftView(m, viewerId) {
  if (!m) {
    return {
      giftPhase: "none",
      giftWindowEndsAt: 0,
      giftPoolCount: 0,
      giftRemainingMs: 0,
      canGift: false,
      myGiftClaimReady: false,
      myGiftClaimed: false,
      myGiftPreview: [],
    };
  }
  ensureGiftState(m);
  const now = Date.now();
  const ends = Number(m.giftWindowEndsAt) || 0;
  const phase = String(m.giftPhase || "none");
  const isSpouse = viewerId && (m.a === viewerId || m.b === viewerId);
  const claimed = Boolean(m.giftClaimed?.[viewerId]);
  const mySplit = isSpouse && Array.isArray(m.giftSplit?.[viewerId])
    ? m.giftSplit[viewerId]
    : [];
  return {
    giftPhase: phase,
    giftWindowEndsAt: ends,
    giftPoolCount: Array.isArray(m.giftPool) ? m.giftPool.length : 0,
    giftRemainingMs: phase === "open" ? Math.max(0, ends - now) : 0,
    canGift: canGiftToMarriage(m, now),
    myGiftClaimReady: phase === "rolled" && isSpouse && !claimed,
    myGiftClaimed: claimed,
    myGiftPreview:
      phase === "rolled" && isSpouse
        ? mySplit.map((g) => ({
            kind: g.kind,
            itemId: g.itemId,
            fromNickname: g.fromNickname || "Jemand",
          }))
        : [],
  };
}

module.exports = {
  GIFT_WINDOW_MS,
  GIFT_CLAIM_GRACE_MS,
  MAX_GIFTS_PER_GIVER,
  MAX_POOL,
  ALLOWED_KINDS,
  CONSOLATION_ITEMS,
  ensureGiftState,
  openGiftWindow,
  canGiftToMarriage,
  giverGiftCount,
  rollGiftPool,
  publicGiftView,
};

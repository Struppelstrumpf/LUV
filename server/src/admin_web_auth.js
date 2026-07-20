/**
 * Web-Admin Auth-Codes (XX-XXX-XX), 40s TTL.
 * Speichert nur HMAC-Hashes — Klartext-Codes nie persistent.
 * Staff-Codes sind an googleSub gebunden; sonst Decoy (Fake-Admin).
 */
const crypto = require("crypto");

const CODE_TTL_MS = 40_000;
const TICKET_TTL_MS = 5 * 60_000;
const ALPH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

function ensureBucket(db) {
  if (!db.adminWebAuth || typeof db.adminWebAuth !== "object") {
    db.adminWebAuth = { pepper: null, challenges: {}, tickets: {} };
  }
  if (!db.adminWebAuth.challenges) db.adminWebAuth.challenges = {};
  if (!db.adminWebAuth.tickets) db.adminWebAuth.tickets = {};
  if (!db.adminWebAuth.pepper || String(db.adminWebAuth.pepper).length < 32) {
    db.adminWebAuth.pepper = crypto.randomBytes(32).toString("hex");
  }
  return db.adminWebAuth;
}

function getPepper(db) {
  return ensureBucket(db).pepper;
}

function normalizeCode(raw) {
  return String(raw || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, 7);
}

function formatCode(raw7) {
  const s = normalizeCode(raw7);
  if (s.length !== 7) return null;
  return `${s.slice(0, 2)}-${s.slice(2, 5)}-${s.slice(5, 7)}`;
}

function hashCode(db, raw7) {
  const pepper = getPepper(db);
  return crypto.createHmac("sha256", pepper).update(normalizeCode(raw7)).digest("hex");
}

function hashTicket(db, ticket) {
  const pepper = getPepper(db);
  return crypto.createHmac("sha256", pepper).update(String(ticket || "")).digest("hex");
}

function timingSafeEqualHex(a, b) {
  try {
    const ba = Buffer.from(String(a), "hex");
    const bb = Buffer.from(String(b), "hex");
    if (ba.length !== bb.length || ba.length === 0) return false;
    return crypto.timingSafeEqual(ba, bb);
  } catch {
    return false;
  }
}

function generateRawCode() {
  const bytes = crypto.randomBytes(7);
  let raw = "";
  for (let i = 0; i < 7; i++) raw += ALPH[bytes[i] % ALPH.length];
  return raw;
}

function pruneExpired(db) {
  const bucket = ensureBucket(db);
  const now = Date.now();
  for (const [k, v] of Object.entries(bucket.challenges)) {
    if (!v || v.exp < now || v.used) delete bucket.challenges[k];
  }
  for (const [k, v] of Object.entries(bucket.tickets)) {
    if (!v || v.exp < now || v.used) delete bucket.tickets[k];
  }
}

/**
 * @returns {{ code: string, expiresAt: number, expiresInSec: number, kind: "staff"|"decoy" }}
 */
function issueChallenge(db, { googleSub, userId, isStaff }) {
  pruneExpired(db);
  const bucket = ensureBucket(db);
  const kind = isStaff && googleSub ? "staff" : "decoy";
  let raw = generateRawCode();
  let h = hashCode(db, raw);
  // Kollision extrem unwahrscheinlich — trotzdem absichern
  for (let i = 0; i < 5 && bucket.challenges[h]; i++) {
    raw = generateRawCode();
    h = hashCode(db, raw);
  }
  const exp = Date.now() + CODE_TTL_MS;
  bucket.challenges[h] = {
    kind,
    googleSub: googleSub || null,
    userId: userId || null,
    exp,
    used: false,
    createdAt: Date.now(),
  };
  return {
    code: formatCode(raw),
    expiresAt: exp,
    expiresInSec: Math.round(CODE_TTL_MS / 1000),
    kind, // nur für Server-Logs; Client bekommt kind NICHT (gleiche Response)
  };
}

function publicChallengeResponse(issued) {
  return {
    ok: true,
    code: issued.code,
    expiresAt: issued.expiresAt,
    expiresInSec: issued.expiresInSec,
  };
}

function createTicket(db, payload) {
  const bucket = ensureBucket(db);
  const ticket = crypto.randomBytes(32).toString("hex");
  const th = hashTicket(db, ticket);
  const exp = Date.now() + TICKET_TTL_MS;
  bucket.tickets[th] = {
    kind: payload.kind === "staff" ? "staff" : "decoy",
    googleSub: payload.googleSub || null,
    userId: payload.userId || null,
    exp,
    used: false,
    createdAt: Date.now(),
  };
  return { ticket, expiresAt: exp, expiresInSec: Math.round(TICKET_TTL_MS / 1000) };
}

function sanitizeIncomingCode(raw) {
  // Kein JSON-Objekt/Array, keine langen Payloads — nur Kurzstring
  if (raw == null) return "";
  if (typeof raw !== "string") return "";
  if (raw.length > 32) return "";
  return normalizeCode(raw);
}

/**
 * Redeem: immer gleiche Response-Form (Anti-Enumeration).
 * Ungültig/abgelaufen → frisches Decoy-Ticket.
 */
function redeemCode(db, codeRaw) {
  pruneExpired(db);
  const bucket = ensureBucket(db);
  const raw = sanitizeIncomingCode(codeRaw);
  let entry = null;
  let hashKey = null;
  if (raw.length === 7) {
    hashKey = hashCode(db, raw);
    entry = bucket.challenges[hashKey] || null;
  }

  const valid =
    entry &&
    !entry.used &&
    entry.exp >= Date.now() &&
    (entry.kind === "staff" || entry.kind === "decoy");

  if (valid) {
    entry.used = true;
    delete bucket.challenges[hashKey];
    const t = createTicket(db, {
      kind: entry.kind,
      googleSub: entry.googleSub,
      userId: entry.userId,
    });
    return {
      ok: true,
      ticket: t.ticket,
      expiresAt: t.expiresAt,
      expiresInSec: t.expiresInSec,
      // intern — nicht an Client senden
      _kind: entry.kind,
    };
  }

  const t = createTicket(db, { kind: "decoy", googleSub: null, userId: null });
  return {
    ok: true,
    ticket: t.ticket,
    expiresAt: t.expiresAt,
    expiresInSec: t.expiresInSec,
    _kind: "decoy",
  };
}

function publicRedeemResponse(result) {
  return {
    ok: true,
    ticket: result.ticket,
    expiresAt: result.expiresAt,
    expiresInSec: result.expiresInSec,
  };
}

/**
 * Ticket konsumieren (einmalig). null = ungültig.
 */
function consumeTicket(db, ticketRaw) {
  pruneExpired(db);
  const bucket = ensureBucket(db);
  const ticket = String(ticketRaw || "").trim();
  // Nur 64 Hex-Zeichen — verhindert Injection / übergroße Payloads
  if (!/^[a-f0-9]{64}$/.test(ticket)) return null;
  const th = hashTicket(db, ticket);
  const entry = bucket.tickets[th];
  if (!entry || entry.used || entry.exp < Date.now()) return null;
  entry.used = true;
  delete bucket.tickets[th];
  return {
    kind: entry.kind === "staff" ? "staff" : "decoy",
    googleSub: entry.googleSub || null,
    userId: entry.userId || null,
  };
}

function fakeDecoyUsers() {
  const names = ["Alex", "Sam", "Jordan", "Casey", "Riley", "Quinn", "Morgan", "Avery"];
  return names.map((n, i) => ({
    id: `u_demo_${i + 1}`,
    nickname: n,
    coins: 100 + i * 37,
    role: "user",
    banned: false,
    createdAt: Date.now() - (i + 1) * 86400000,
  }));
}

module.exports = {
  CODE_TTL_MS,
  TICKET_TTL_MS,
  issueChallenge,
  publicChallengeResponse,
  redeemCode,
  publicRedeemResponse,
  consumeTicket,
  formatCode,
  normalizeCode,
  fakeDecoyUsers,
  ensureBucket,
};

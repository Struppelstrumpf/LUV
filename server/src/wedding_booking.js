/**
 * Hochzeits-Buchungsflow nach Terminwahl:
 * Geldbäume → Raum → gemeinsame Bestätigung / Coin-Abbuchung.
 */

const MONEY_TREES_TOTAL = 50;
const MONEY_TREES_PER_PERSON = 25;
const EXTRA_SLOT_COST = 25;
const MAX_CAPACITY = 10;
const COUPLE_SEATS = 2;

const WEDDING_ROOMS = {
  wedding_small: {
    id: "wedding_small",
    name: "Kleine Trauung",
    shortLabel: "Klein",
    guestSlots: 8,
    capacity: 10,
    totalCost: 0,
    perPerson: 0,
    imageUrl: "/luv/wedding-small-room.png",
    blurb: "Intim: 8 Gäste, kleiner Raum mit viel Rand.",
  },
  wedding: {
    id: "wedding",
    name: "Kapelle",
    shortLabel: "Kapelle",
    guestSlots: 6,
    capacity: 8,
    totalCost: 100,
    perPerson: 50,
    imageUrl: "/luv/wedding-chapel-room.png",
    blurb: "Klassische Kapelle: 6 Gäste.",
  },
  wedding_grand: {
    id: "wedding_grand",
    name: "Prunksaal",
    shortLabel: "Prunk",
    guestSlots: 8,
    capacity: 10,
    totalCost: 250,
    perPerson: 125,
    imageUrl: "/luv/wedding-grand-room.png",
    blurb: "Prunkvoller Saal: 8 Gäste.",
  },
  wedding_island: {
    id: "wedding_island",
    name: "Insel",
    shortLabel: "Insel",
    guestSlots: 8,
    capacity: 10,
    totalCost: 400,
    perPerson: 200,
    imageUrl: "/luv/wedding-island-room.png",
    blurb: "Trauminsel im Wasser: 8 Gäste.",
  },
};

function roomCatalog() {
  return Object.values(WEDDING_ROOMS).map((r) => ({ ...r }));
}

function getRoomDef(roomId) {
  return WEDDING_ROOMS[String(roomId || "").trim()] || null;
}

function isWeddingLayoutId(id) {
  return Boolean(WEDDING_ROOMS[String(id || "").trim()]);
}

function ensureBooking(m) {
  if (!m) return null;
  if (!m.ceremony || typeof m.ceremony !== "object") m.ceremony = {};
  const c = m.ceremony;
  if (!c.booking || typeof c.booking !== "object") {
    c.booking = {
      ceremonyAt: 0,
      step: "none", // none | trees | room | confirm | done
      moneyTreesVote: {},
      moneyTrees: null,
      roomVotes: {},
      roomId: null,
      confirm: {},
      charged: false,
      charging: false,
    };
  }
  const b = c.booking;
  if (!b.moneyTreesVote || typeof b.moneyTreesVote !== "object") b.moneyTreesVote = {};
  if (!b.roomVotes || typeof b.roomVotes !== "object") b.roomVotes = {};
  if (!b.confirm || typeof b.confirm !== "object") b.confirm = {};
  if (!Number.isFinite(Number(b.chargedBillPerPerson))) b.chargedBillPerPerson = 0;
  if (typeof b.refunded !== "boolean") b.refunded = false;
  if (typeof b.charged !== "boolean") b.charged = false;
  if (typeof b.charging !== "boolean") b.charging = false;
  if (b.moneyTrees !== true && b.moneyTrees !== false) b.moneyTrees = null;
  return b;
}

function voteCounts(votes, aId, bId, want) {
  let n = 0;
  if (votes[aId] === want) n += 1;
  if (votes[bId] === want) n += 1;
  return n;
}

function billPerPerson(b) {
  if (!b) return 0;
  let sum = 0;
  if (b.moneyTrees === true) sum += MONEY_TREES_PER_PERSON;
  const room = getRoomDef(b.roomId);
  if (room) sum += room.perPerson;
  return sum;
}

function publicBooking(m, viewerId) {
  const b = ensureBooking(m);
  if (!b || b.step === "none" || b.step === "done") {
    return {
      active: false,
      step: b?.step || "none",
      ceremonyAt: Number(b?.ceremonyAt || m?.ceremonyAt) || 0,
      billPerPerson: 0,
      rooms: roomCatalog(),
    };
  }
  const a = m.a;
  const bId = m.b;
  const treesDecline = voteCounts(b.moneyTreesVote, a, bId, "decline");
  const treesBook = voteCounts(b.moneyTreesVote, a, bId, "book");
  const roomVotesById = {};
  for (const rid of Object.keys(WEDDING_ROOMS)) {
    roomVotesById[rid] = voteCounts(b.roomVotes, a, bId, rid);
  }
  const confirmCount =
    (b.confirm[a] ? 1 : 0) + (b.confirm[bId] ? 1 : 0);
  return {
    active: true,
    step: b.step,
    ceremonyAt: Number(b.ceremonyAt) || 0,
    moneyTrees: b.moneyTrees,
    moneyTreesMine: b.moneyTreesVote[viewerId] || null,
    moneyTreesDeclineCount: treesDecline,
    moneyTreesBookCount: treesBook,
    roomId: b.roomId,
    roomMine: b.roomVotes[viewerId] || null,
    roomVoteCounts: roomVotesById,
    confirmMine: Boolean(b.confirm[viewerId]),
    confirmCount,
    billPerPerson: billPerPerson(b),
    moneyTreesPerPerson: MONEY_TREES_PER_PERSON,
    rooms: roomCatalog(),
    charged: Boolean(b.charged),
  };
}

/** Termin akzeptiert → Booking starten (keine Lobby). */
function beginBooking(m, ceremonyAt) {
  const b = ensureBooking(m);
  b.ceremonyAt = Math.floor(Number(ceremonyAt) || 0);
  b.step = "trees";
  b.moneyTreesVote = {};
  b.moneyTrees = null;
  b.roomVotes = {};
  b.roomId = null;
  b.confirm = {};
  b.charged = false;
  b.charging = false;
  m.ceremonyAt = b.ceremonyAt;
  const c = m.ceremony;
  c.phase = "booking";
  c.timeProposals = {};
  return { ok: true, booking: b };
}

function voteMoneyTrees(m, userId, choice) {
  const b = ensureBooking(m);
  if (!b || b.step !== "trees") {
    return { ok: false, error: "wrong_step", message: "Gerade kein Geldbäume-Schritt." };
  }
  const c = String(choice || "").toLowerCase();
  if (c !== "decline" && c !== "book") {
    return { ok: false, error: "bad_choice", message: "Ablehnen oder Buchen." };
  }
  b.moneyTreesVote[userId] = c;
  const a = m.a;
  const bId = m.b;
  const declineN = voteCounts(b.moneyTreesVote, a, bId, "decline");
  const bookN = voteCounts(b.moneyTreesVote, a, bId, "book");
  if (declineN >= 2) {
    b.moneyTrees = false;
    b.step = "room";
    b.roomVotes = {};
    b.roomId = null;
    b.confirm = {};
  } else if (bookN >= 2) {
    b.moneyTrees = true;
    b.step = "room";
    b.roomVotes = {};
    b.roomId = null;
    b.confirm = {};
  }
  return { ok: true, advanced: b.step === "room" };
}

function voteRoom(m, userId, roomId) {
  const b = ensureBooking(m);
  if (!b || b.step !== "room") {
    return { ok: false, error: "wrong_step", message: "Gerade keine Raumwahl." };
  }
  const def = getRoomDef(roomId);
  if (!def) {
    return { ok: false, error: "bad_room", message: "Unbekannter Raum." };
  }
  b.roomVotes[userId] = def.id;
  const a = m.a;
  const bId = m.b;
  if (b.roomVotes[a] && b.roomVotes[a] === b.roomVotes[bId]) {
    b.roomId = b.roomVotes[a];
    b.step = "confirm";
    b.confirm = {};
  }
  return { ok: true, advanced: b.step === "confirm" };
}

/**
 * Confirm-Vote. Wenn beide bestätigt: { ready: true, billPerPerson }.
 * Abbuchen macht der Caller (index.js) atomar.
 */
function voteConfirm(m, userId, confirm) {
  const b = ensureBooking(m);
  if (!b || b.step !== "confirm") {
    return { ok: false, error: "wrong_step", message: "Gerade keine Bestätigung." };
  }
  if (b.charged || b.charging) {
    return { ok: false, error: "charging", message: "Buchung läuft bereits." };
  }
  if (!b.roomId || b.moneyTrees === null) {
    return { ok: false, error: "incomplete", message: "Buchung unvollständig." };
  }
  if (confirm === false) {
    delete b.confirm[userId];
    return { ok: true, ready: false, billPerPerson: billPerPerson(b) };
  }
  b.confirm[userId] = true;
  const ready = Boolean(b.confirm[m.a] && b.confirm[m.b]);
  return {
    ok: true,
    ready,
    billPerPerson: billPerPerson(b),
    roomId: b.roomId,
    moneyTrees: b.moneyTrees === true,
    ceremonyAt: b.ceremonyAt,
    capacity: getRoomDef(b.roomId)?.capacity || 8,
  };
}

function markBookingDone(m) {
  const b = ensureBooking(m);
  b.step = "done";
  b.charged = true;
  b.charging = false;
  m.ceremonyLayoutId = b.roomId || "wedding";
  m.moneyTreesEnabled = b.moneyTrees === true;
  m.ceremonyCapacity = getRoomDef(b.roomId)?.capacity || 8;
}

module.exports = {
  MONEY_TREES_TOTAL,
  MONEY_TREES_PER_PERSON,
  EXTRA_SLOT_COST,
  MAX_CAPACITY,
  COUPLE_SEATS,
  WEDDING_ROOMS,
  roomCatalog,
  getRoomDef,
  isWeddingLayoutId,
  ensureBooking,
  billPerPerson,
  publicBooking,
  beginBooking,
  voteMoneyTrees,
  voteRoom,
  voteConfirm,
  markBookingDone,
};

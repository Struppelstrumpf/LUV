const http = require("http");
const crypto = require("crypto");
const express = require("express");
const { WebSocketServer } = require("ws");
const {
  getDb,
  scheduleSave,
  todayKey,
  hashSecret,
  newId,
} = require("./store");

const PORT = Number(process.env.PORT || 8080);
const ROOM_TTL_MS = Number(process.env.ROOM_TTL_MS || 24 * 60 * 60 * 1000);
const MAX_PEERS = Number(process.env.MAX_PEERS || 4);
const PUBLIC_JOIN_BASE =
  process.env.PUBLIC_JOIN_BASE || "https://reineke.pro/love/j";
const SESSION_TTL_MS = Number(process.env.SESSION_TTL_MS || 30 * 24 * 60 * 60 * 1000);
const ADMIN_BOOTSTRAP_CODE =
  process.env.ADMIN_BOOTSTRAP_CODE || "Warehouse295?";
const MOLLIE_API_KEY = process.env.MOLLIE_API_KEY || "";
const MOLLIE_WEBHOOK_URL =
  process.env.MOLLIE_WEBHOOK_URL || "https://reineke.pro/luv/v1/webhooks/mollie";
const PUBLIC_SHOP_REDIRECT =
  process.env.PUBLIC_SHOP_REDIRECT || "https://reineke.pro/love/";

const DAILY_COINS = 10;
const STARTING_COINS = 15;
const FREE_SESSIONS_PER_DAY = 5;
const SESSION_COST = 1;
const CLEAR_VOTE_MS = 60_000;

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

const PACKS = {
  pack_50: { id: "pack_50", coins: 50, amountEur: "2.99", label: "50 Coins" },
  pack_150: { id: "pack_150", coins: 150, amountEur: "6.99", label: "150 Coins" },
  pack_400: { id: "pack_400", coins: 400, amountEur: "14.99", label: "400 Coins" },
};

/** @type {Map<string, any>} */
const rooms = new Map();

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

function cleanupRooms() {
  const now = Date.now();
  for (const [code, room] of rooms.entries()) {
    if (room.sockets.size === 0 && now - room.createdAt > ROOM_TTL_MS) {
      rooms.delete(code);
    }
  }
}

function publicUser(user) {
  const day = todayKey();
  const freeLeft = Math.max(0, FREE_SESSIONS_PER_DAY - (user.sessionsByDay?.[day] || 0));
  return {
    id: user.id,
    nickname: user.nickname,
    coins: user.coins,
    role: user.role || "user",
    freeSessionsLeft: freeLeft,
    freeSessionsPerDay: FREE_SESSIONS_PER_DAY,
    dailyCoins: DAILY_COINS,
    sessionCost: SESSION_COST,
    lastDailyGrantDate: user.lastDailyGrantDate || null,
    canClaimDaily: user.lastDailyGrantDate !== day,
    googleLinked: Boolean(user.googleSub),
  };
}

function applyLedger(userId, delta, reason, refId) {
  const db = getDb();
  const user = db.users[userId];
  if (!user) return null;
  user.coins = Math.max(0, (user.coins || 0) + delta);
  db.ledger.push({
    id: newId("led"),
    userId,
    delta,
    reason,
    refId: refId || null,
    at: Date.now(),
    balance: user.coins,
  });
  if (db.ledger.length > 5000) db.ledger.splice(0, db.ledger.length - 5000);
  scheduleSave();
  return user;
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
  return ctx;
}

function requireAdmin(req, res) {
  const ctx = requireAuth(req, res);
  if (!ctx) return null;
  if (ctx.user.role !== "admin") {
    res.status(403).json({ error: "forbidden" });
    return null;
  }
  return ctx;
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
  if ((user.coins || 0) < SESSION_COST) {
    return { ok: false, error: "no_coins" };
  }
  applyLedger(user.id, -SESSION_COST, "session", key);
  user.drawLocks[key] = "paid";
  scheduleSave();
  return { ok: true, charged: true, free: false };
}

function broadcastRoom(room, payload) {
  const text = typeof payload === "string" ? payload : JSON.stringify(payload);
  for (const socket of room.sockets.values()) {
    if (socket.readyState === 1) socket.send(text);
  }
}

function broadcastPeerCount(room) {
  broadcastRoom(room, {
    type: "peers",
    count: room.sockets.size,
    maxPeers: MAX_PEERS,
  });
}

function resolveClear(room, code) {
  const proposal = room.clearProposal;
  if (!proposal || proposal.status !== "open") return;
  const voters = [...room.sockets.keys()];
  const total = Math.max(1, voters.length);
  const yes = proposal.yes.filter((id) => voters.includes(id) || id === proposal.byPeerId).length;
  // Recount only current peers + ensure proposer counted
  const yesSet = new Set(proposal.yes.filter((id) => voters.includes(id)));
  yesSet.add(proposal.byPeerId);
  const yesCount = yesSet.size;
  const approved = yesCount * 2 > total; // strict majority
  proposal.status = approved ? "approved" : "rejected";
  broadcastRoom(room, {
    type: "clear_result",
    proposalId: proposal.id,
    approved,
    yes: yesCount,
    total,
  });
  if (approved) {
    broadcastRoom(room, { type: "clear" });
  }
  room.clearProposal = null;
  void code;
  void yes;
}

setInterval(cleanupRooms, 60_000).unref();
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
app.use(express.json({ limit: "64kb" }));
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
      freeSessionsPerDay: FREE_SESSIONS_PER_DAY,
      sessionCost: SESSION_COST,
      startingCoins: STARTING_COINS,
    },
    shopEnabled: Boolean(MOLLIE_API_KEY),
    uptimeSec: Math.round(process.uptime()),
  });
});

app.post("/v1/auth/device", (req, res) => {
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
    if (!nickname || nickname.length < 2) nickname = "Luv";
    user = {
      id: newId("u"),
      secretHash,
      installIdHash: hashSecret(installId),
      nickname,
      coins: STARTING_COINS,
      role: "user",
      lastDailyGrantDate: todayKey(),
      sessionsByDay: {},
      drawLocks: {},
      createdAt: Date.now(),
      googleSub: null,
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
    });
    scheduleSave();
    created = true;
  } else if (nickname && nickname.length >= 2) {
    user.nickname = nickname;
    scheduleSave();
  }
  const token = createSession(user.id);
  return res.json({
    sessionToken: token,
    created,
    user: publicUser(user),
  });
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
    ctx.user.nickname = nickname;
    scheduleSave();
  }
  return res.json({ user: publicUser(ctx.user) });
});

app.post("/v1/me/daily-claim", (req, res) => {
  const ctx = requireAuth(req, res);
  if (!ctx) return;
  const day = todayKey();
  if (ctx.user.lastDailyGrantDate === day) {
    return res.json({ user: publicUser(ctx.user), claimed: false });
  }
  applyLedger(ctx.user.id, DAILY_COINS, "daily_grant", day);
  ctx.user.lastDailyGrantDate = day;
  scheduleSave();
  return res.json({ user: publicUser(ctx.user), claimed: true, amount: DAILY_COINS });
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

  // Admin bootstrap — only validated server-side against env secret
  if (code === ADMIN_BOOTSTRAP_CODE) {
    ctx.user.role = "admin";
    scheduleSave();
    return res.json({
      type: "admin",
      message: "Admin freigeschaltet",
      user: publicUser(ctx.user),
    });
  }

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

app.get("/v1/admin/vouchers", (req, res) => {
  const ctx = requireAdmin(req, res);
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
  const ctx = requireAdmin(req, res);
  if (!ctx) return;
  const coins = Math.min(10000, Math.max(1, Number(req.body?.coins) || 0));
  const maxRedeems = Math.min(10000, Math.max(1, Number(req.body?.maxRedeems) || 1));
  const days = Math.min(365, Math.max(1, Number(req.body?.validDays) || 30));
  const custom = String(req.body?.code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, 24);
  const code = custom.length >= 6 ? custom : randomCode(10);
  const db = getDb();
  if (db.vouchers[code]) return res.status(409).json({ error: "code_exists" });
  const voucher = {
    id: newId("v"),
    code,
    coins,
    maxRedeems,
    redeemCount: 0,
    expiresAt: Date.now() + days * 86400000,
    createdAt: Date.now(),
    createdBy: ctx.user.id,
    revoked: false,
  };
  db.vouchers[code] = voucher;
  scheduleSave();
  return res.status(201).json({ voucher });
});

app.post("/v1/admin/vouchers/:code/revoke", (req, res) => {
  const ctx = requireAdmin(req, res);
  if (!ctx) return;
  const code = String(req.params.code || "").toUpperCase();
  const voucher = getDb().vouchers[code];
  if (!voucher) return res.status(404).json({ error: "not_found" });
  voucher.revoked = true;
  scheduleSave();
  return res.json({ ok: true });
});

app.get("/v1/shop/packs", (_req, res) => {
  res.json({
    enabled: Boolean(MOLLIE_API_KEY),
    packs: Object.values(PACKS),
  });
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
  try {
    const body = {
      amount: { currency: "EUR", value: pack.amountEur },
      description: `LUV ${pack.label}`,
      redirectUrl: PUBLIC_SHOP_REDIRECT,
      webhookUrl: MOLLIE_WEBHOOK_URL,
      metadata: { userId: ctx.user.id, packId: pack.id, coins: String(pack.coins) },
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
      coins: pack.coins,
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
      payment = {
        id,
        userId: data.metadata?.userId,
        packId: data.metadata?.packId,
        coins: Number(data.metadata?.coins || 0),
        status: data.status,
        createdAt: Date.now(),
        credited: false,
      };
      db.payments[id] = payment;
    }
    payment.status = data.status;
    if (data.status === "paid" && !payment.credited && payment.userId && payment.coins > 0) {
      applyLedger(payment.userId, payment.coins, "mollie_purchase", id);
      payment.credited = true;
    }
    scheduleSave();
  } catch {
    // acknowledge to avoid retries storm; next webhook can retry
  }
  return res.status(200).send("ok");
});

app.post("/v1/rooms", (req, res) => {
  cleanupRooms();
  const ctx = authUser(req); // optional, but preferred
  let code = randomCode();
  while (rooms.has(code)) code = randomCode();
  const token = randomToken().slice(0, 32);
  rooms.set(code, {
    token,
    createdAt: Date.now(),
    sockets: new Map(),
    clearProposal: null,
    hostUserId: ctx?.user?.id || null,
  });
  res.status(201).json({
    token,
    maxPeers: MAX_PEERS,
    ...inviteFor(code),
  });
});

app.post("/v1/rooms/:code/join", (req, res) => {
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const room = rooms.get(code);
  if (!room) return res.status(404).json({ error: "room_not_found" });
  if (room.sockets.size >= MAX_PEERS) {
    return res.status(409).json({ error: "room_full" });
  }
  return res.json({
    token: room.token,
    peers: room.sockets.size,
    maxPeers: MAX_PEERS,
    ...inviteFor(code),
  });
});

app.get("/v1/rooms/:code", (req, res) => {
  const code = String(req.params.code || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const room = rooms.get(code);
  if (!room) return res.status(404).json({ error: "room_not_found" });
  return res.json({
    peers: room.sockets.size,
    maxPeers: MAX_PEERS,
    ...inviteFor(code),
  });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/v1/ws" });

wss.on("connection", (socket, req) => {
  const url = new URL(req.url || "", `http://${req.headers.host}`);
  const code = String(url.searchParams.get("code") || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const token = String(url.searchParams.get("token") || "");
  const role = String(url.searchParams.get("role") || "peer");
  const sessionToken = String(url.searchParams.get("session") || "");
  const user = userFromSessionToken(sessionToken);

  const room = rooms.get(code);
  if (!room || room.token !== token) {
    socket.close(4401, "unauthorized");
    return;
  }
  if (room.sockets.size >= MAX_PEERS) {
    socket.close(4409, "room_full");
    return;
  }

  const peerId = `${role}-${crypto.randomBytes(4).toString("hex")}`;
  socket.luvPeerId = peerId;
  socket.luvUserId = user?.id || null;
  socket.luvCanDraw = true;
  room.sockets.set(peerId, socket);

  socket.send(
    JSON.stringify({
      type: "welcome",
      code,
      peerId,
      peers: room.sockets.size,
      maxPeers: MAX_PEERS,
      userId: user?.id || null,
    })
  );
  broadcastPeerCount(room);

  socket.on("message", (data) => {
    const text = typeof data === "string" ? data : data.toString("utf8");
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      return;
    }
    const type = json?.type;

    if (type === "clear") {
      // Clients may not force-clear anymore — start proposal instead
      json = { type: "clear_propose", nickname: json.nickname };
    }

    if (json.type === "clear_propose") {
      if (room.clearProposal?.status === "open") {
        socket.send(JSON.stringify({ type: "clear_busy", proposalId: room.clearProposal.id }));
        return;
      }
      const proposalId = newId("clr");
      room.clearProposal = {
        id: proposalId,
        byPeerId: peerId,
        byNickname: String(json.nickname || user?.nickname || "Jemand").slice(0, 18),
        yes: [peerId],
        no: [],
        status: "open",
        endsAt: Date.now() + CLEAR_VOTE_MS,
      };
      broadcastRoom(room, {
        type: "clear_vote_open",
        proposalId,
        by: room.clearProposal.byNickname,
        byPeerId: peerId,
        endsAt: room.clearProposal.endsAt,
        yes: 1,
        total: room.sockets.size,
      });
      setTimeout(() => {
        if (room.clearProposal?.id === proposalId && room.clearProposal.status === "open") {
          resolveClear(room, code);
        }
      }, CLEAR_VOTE_MS + 50);
      return;
    }

    if (type === "clear_vote") {
      const proposal = room.clearProposal;
      if (!proposal || proposal.status !== "open") return;
      if (proposal.id !== json.proposalId) return;
      proposal.yes = proposal.yes.filter((id) => id !== peerId);
      proposal.no = proposal.no.filter((id) => id !== peerId);
      if (json.yes === true || json.vote === "yes") proposal.yes.push(peerId);
      else proposal.no.push(peerId);
      broadcastRoom(room, {
        type: "clear_vote_update",
        proposalId: proposal.id,
        yes: new Set(proposal.yes).size,
        no: new Set(proposal.no).size,
        total: room.sockets.size,
      });
      const voted = new Set([...proposal.yes, ...proposal.no]);
      if (voted.size >= room.sockets.size) {
        resolveClear(room, code);
      }
      return;
    }

    if (type === "stroke") {
      // Economy gate when authenticated
      if (user) {
        const result = consumeDrawSession(user, code);
        if (!result.ok) {
          socket.send(
            JSON.stringify({
              type: "economy_block",
              error: "no_coins",
              message: "Keine freien Sessions/Coins — Zuschauen geht weiter.",
            })
          );
          return;
        }
        if (result.charged || result.free) {
          socket.send(
            JSON.stringify({
              type: "economy_ok",
              charged: Boolean(result.charged),
              user: publicUser(user),
            })
          );
        }
      }
    }

    // Relay to others
    for (const [id, peer] of room.sockets.entries()) {
      if (id === peerId) continue;
      if (peer.readyState === 1) peer.send(text);
    }
  });

  socket.on("close", () => {
    room.sockets.delete(peerId);
    broadcastPeerCount(room);
    if (room.clearProposal?.status === "open") {
      // Re-evaluate majority if people leave
      const total = room.sockets.size;
      if (total === 0) {
        room.clearProposal = null;
      } else {
        const yesSet = new Set(
          room.clearProposal.yes.filter((id) => room.sockets.has(id) || id === room.clearProposal.byPeerId)
        );
        broadcastRoom(room, {
          type: "clear_vote_update",
          proposalId: room.clearProposal.id,
          yes: yesSet.size,
          no: room.clearProposal.no.filter((id) => room.sockets.has(id)).length,
          total,
        });
      }
    }
  });

  socket.on("error", () => {
    room.sockets.delete(peerId);
  });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(
    `luv-api :${PORT} maxPeers=${MAX_PEERS} freeSessions=${FREE_SESSIONS_PER_DAY} shop=${Boolean(MOLLIE_API_KEY)}`
  );
});

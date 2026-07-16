const http = require("http");
const crypto = require("crypto");
const express = require("express");
const { WebSocketServer } = require("ws");

const PORT = Number(process.env.PORT || 8080);
const ROOM_TTL_MS = Number(process.env.ROOM_TTL_MS || 24 * 60 * 60 * 1000);
const MAX_PEERS = Number(process.env.MAX_PEERS || 4);
const PUBLIC_JOIN_BASE =
  process.env.PUBLIC_JOIN_BASE || "https://reineke.pro/love/j";
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

/** @type {Map<string, { token: string, createdAt: number, sockets: Map<string, import('ws').WebSocket> }>} */
const rooms = new Map();

function randomCode(length = 6) {
  let out = "";
  for (let i = 0; i < length; i++) {
    out += CODE_ALPHABET[crypto.randomInt(0, CODE_ALPHABET.length)];
  }
  return out;
}

function randomToken() {
  return crypto.randomBytes(16).toString("hex");
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
    const empty = room.sockets.size === 0;
    const expired = now - room.createdAt > ROOM_TTL_MS;
    if (empty && expired) {
      rooms.delete(code);
    }
  }
}

setInterval(cleanupRooms, 60_000).unref();

const app = express();
app.use(express.json({ limit: "32kb" }));

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "luv-api",
    rooms: rooms.size,
    maxPeers: MAX_PEERS,
    uptimeSec: Math.round(process.uptime()),
  });
});

app.post("/v1/rooms", (_req, res) => {
  cleanupRooms();
  let code = randomCode();
  while (rooms.has(code)) code = randomCode();
  const token = randomToken();
  rooms.set(code, {
    token,
    createdAt: Date.now(),
    sockets: new Map(),
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
  if (!room) {
    return res.status(404).json({ error: "room_not_found" });
  }
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
  if (!room) {
    return res.status(404).json({ error: "room_not_found" });
  }
  return res.json({
    peers: room.sockets.size,
    maxPeers: MAX_PEERS,
    ...inviteFor(code),
  });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/v1/ws" });

function broadcastPeerCount(room) {
  const payload = JSON.stringify({
    type: "peers",
    count: room.sockets.size,
    maxPeers: MAX_PEERS,
  });
  for (const socket of room.sockets.values()) {
    if (socket.readyState === 1) socket.send(payload);
  }
}

wss.on("connection", (socket, req) => {
  const url = new URL(req.url || "", `http://${req.headers.host}`);
  const code = String(url.searchParams.get("code") || "")
    .toUpperCase()
    .replace(/^LUV-/, "");
  const token = String(url.searchParams.get("token") || "");
  const role = String(url.searchParams.get("role") || "peer");

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
  room.sockets.set(peerId, socket);
  socket.send(
    JSON.stringify({
      type: "welcome",
      code,
      peerId,
      peers: room.sockets.size,
      maxPeers: MAX_PEERS,
    })
  );
  broadcastPeerCount(room);

  socket.on("message", (data) => {
    const text = typeof data === "string" ? data : data.toString("utf8");
    for (const [id, peer] of room.sockets.entries()) {
      if (id === peerId) continue;
      if (peer.readyState === 1) peer.send(text);
    }
  });

  socket.on("close", () => {
    room.sockets.delete(peerId);
    broadcastPeerCount(room);
  });

  socket.on("error", () => {
    room.sockets.delete(peerId);
  });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`luv-api listening on :${PORT} (maxPeers=${MAX_PEERS})`);
});

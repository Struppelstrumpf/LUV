/**
 * Account-level push: WebSocket /v1/ws/account + optional FCM.
 * Env: ACCOUNT_PUSH=0 to disable emits. FCM via FCM_SERVICE_ACCOUNT_FILE or GOOGLE_PLAY_SERVICE_ACCOUNT_FILE.
 */
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");
const https = require("https");

const ACCOUNT_PUSH_ENABLED = String(process.env.ACCOUNT_PUSH || "1").trim() !== "0";

/** @type {Map<string, Set<import('ws').WebSocket>>} */
const socketsByUser = new Map();

function enabled() {
  return ACCOUNT_PUSH_ENABLED;
}

function attachSocket(userId, socket) {
  const uid = String(userId || "").trim();
  if (!uid) return;
  let set = socketsByUser.get(uid);
  if (!set) {
    set = new Set();
    socketsByUser.set(uid, set);
  }
  set.add(socket);
  socket.luvAccountUserId = uid;
}

function detachSocket(socket) {
  const uid = socket?.luvAccountUserId;
  if (!uid) return;
  const set = socketsByUser.get(uid);
  if (!set) return;
  set.delete(socket);
  if (set.size === 0) socketsByUser.delete(uid);
}

function onlineCount(userId) {
  return socketsByUser.get(String(userId || "").trim())?.size || 0;
}

function sendWs(userId, payload) {
  const set = socketsByUser.get(String(userId || "").trim());
  if (!set || set.size === 0) return 0;
  const raw = JSON.stringify(payload);
  let n = 0;
  for (const socket of [...set]) {
    try {
      if (socket.readyState === 1) {
        socket.send(raw);
        n += 1;
      } else {
        set.delete(socket);
      }
    } catch {
      set.delete(socket);
    }
  }
  if (set.size === 0) socketsByUser.delete(String(userId || "").trim());
  return n;
}

let fcmProjectId = null;
let fcmAccessToken = null;
let fcmTokenExpiresAt = 0;

function loadServiceAccount() {
  const candidates = [
    process.env.FCM_SERVICE_ACCOUNT_FILE,
    process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE,
    "/run/secrets/fcm-service-account.json",
    "/run/secrets/play-service-account.json",
  ].filter(Boolean);
  for (const p of candidates) {
    try {
      if (fs.existsSync(p)) {
        const json = JSON.parse(fs.readFileSync(p, "utf8"));
        if (json.client_email && json.private_key) return json;
      }
    } catch {
      /* try next */
    }
  }
  const inline = String(
    process.env.FCM_SERVICE_ACCOUNT_JSON ||
      process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON ||
      ""
  ).trim();
  if (inline) {
    try {
      const json = JSON.parse(inline);
      if (json.client_email && json.private_key) return json;
    } catch {
      /* ignore */
    }
  }
  return null;
}

async function getFcmAccessToken() {
  const now = Date.now();
  if (fcmAccessToken && now < fcmTokenExpiresAt - 60_000) return fcmAccessToken;
  const sa = loadServiceAccount();
  if (!sa) return null;
  fcmProjectId = sa.project_id || fcmProjectId;
  try {
    const { fetchAccessToken } = require("./fcm_auth");
    const token = await fetchAccessToken(sa);
    fcmAccessToken = token.access_token;
    fcmTokenExpiresAt = Date.now() + (Number(token.expires_in) || 3600) * 1000;
    return fcmAccessToken;
  } catch (e) {
    console.warn("[account_push] fcm auth failed", e?.message || e);
    return null;
  }
}

function fcmHttpPost(projectId, accessToken, body) {
  return new Promise((resolve) => {
    const data = JSON.stringify(body);
    const req = https.request(
      {
        hostname: "fcm.googleapis.com",
        path: `/v1/projects/${encodeURIComponent(projectId)}/messages:send`,
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(data),
        },
      },
      (res) => {
        let raw = "";
        res.on("data", (c) => {
          raw += c;
        });
        res.on("end", () => {
          resolve({ status: res.statusCode || 0, raw });
        });
      }
    );
    req.on("error", (e) => resolve({ status: 0, raw: String(e?.message || e) }));
    req.write(data);
    req.end();
  });
}

function fcmTitleBody(type, data) {
  const nick = String(data?.nickname || data?.fromNickname || "Jemand").trim() || "Jemand";
  switch (String(type || "")) {
    case "friend_request":
      return { title: "Neue Freundschaftsanfrage", body: `${nick} möchte befreundet sein` };
    case "friend_accepted":
      return { title: "Freundschaft", body: `${nick} hat angenommen` };
    case "friend_removed":
      return { title: "Freundschaft beendet", body: `${nick} hat die Freundschaft beendet` };
    case "marriage_proposal":
      return { title: "Heiratsantrag", body: `${nick} hat dich gefragt` };
    case "marriage_update":
      return {
        title: "Hochzeit",
        body: String(data?.message || data?.status || "Status geändert"),
      };
    case "market_sold":
      return {
        title: "Marktplatz",
        body: String(data?.message || "Dein Item wurde verkauft"),
      };
    case "achievement_unlocked":
    case "achievement_claimable":
      return {
        title: "Erfolg",
        body: String(data?.message || data?.title || "Neuer Erfolg — Belohnung abholen"),
      };
    case "lobby_invite":
      return { title: "Lobby-Einladung", body: `${nick} lädt dich ein` };
    default:
      return { title: "LUV", body: String(data?.message || "Etwas Neues in LUV") };
  }
}

async function sendFcmToUser(user, type, data) {
  const tokens = Array.isArray(user?.fcmTokens)
    ? user.fcmTokens.map((t) => (typeof t === "string" ? t : t?.token)).filter(Boolean)
    : [];
  if (!tokens.length) return { sent: 0, reason: "no_tokens" };
  const access = await getFcmAccessToken();
  const projectId =
    fcmProjectId ||
    process.env.FCM_PROJECT_ID ||
    process.env.GOOGLE_CLOUD_PROJECT ||
    loadServiceAccount()?.project_id;
  if (!access || !projectId) return { sent: 0, reason: "no_fcm_creds" };
  const { title, body } = fcmTitleBody(type, data);
  let sent = 0;
  const stale = [];
  for (const token of tokens) {
    const res = await fcmHttpPost(projectId, access, {
      message: {
        token,
        notification: { title, body },
        data: {
          type: String(type || ""),
          ...(data && typeof data === "object"
            ? Object.fromEntries(
                Object.entries(data).map(([k, v]) => [k, v == null ? "" : String(v)])
              )
            : {}),
        },
        android: { priority: "HIGH" },
      },
    });
    if (res.status >= 200 && res.status < 300) {
      sent += 1;
    } else if (
      res.raw.includes("UNREGISTERED") ||
      res.raw.includes("NOT_FOUND") ||
      res.raw.includes("INVALID_ARGUMENT")
    ) {
      stale.push(token);
    } else {
      console.warn("[account_push] fcm send failed", res.status, res.raw.slice(0, 200));
    }
  }
  if (stale.length && user && Array.isArray(user.fcmTokens)) {
    user.fcmTokens = user.fcmTokens.filter((t) => {
      const tok = typeof t === "string" ? t : t?.token;
      return tok && !stale.includes(tok);
    });
  }
  return { sent, stale: stale.length };
}

/**
 * @param {object} db
 * @param {string} userId
 * @param {string} type
 * @param {object} [data]
 * @param {{ skipFcm?: boolean }} [opts]
 */
function emitUserEvent(db, userId, type, data = {}, opts = {}) {
  if (!enabled()) return { ws: 0, fcm: 0 };
  const uid = String(userId || "").trim();
  if (!uid || !type) return { ws: 0, fcm: 0 };
  const payload = {
    type: "account_event",
    event: String(type),
    at: Date.now(),
    data: data && typeof data === "object" ? data : {},
  };
  const ws = sendWs(uid, payload);
  const user = db?.users?.[uid];
  // FCM nur wenn kein Account-WS online (sonst Doppel-Hinweise)
  const forceFcm = Boolean(opts.forceFcm);
  if (!opts.skipFcm && user && (forceFcm || ws === 0)) {
    setImmediate(() => {
      sendFcmToUser(user, type, data).catch((e) =>
        console.warn("[account_push] fcm", e?.message || e)
      );
    });
  }
  return { ws, fcm: forceFcm || ws === 0 ? "async" : "skipped_ws_online" };
}

function registerDeviceToken(user, token, platform = "android") {
  if (!user || typeof user !== "object") return false;
  const t = String(token || "").trim();
  if (!t || t.length < 20 || t.length > 4096) return false;
  if (!Array.isArray(user.fcmTokens)) user.fcmTokens = [];
  const now = Date.now();
  const existing = user.fcmTokens.findIndex(
    (x) => (typeof x === "string" ? x : x?.token) === t
  );
  const row = { token: t, platform: String(platform || "android"), updatedAt: now };
  if (existing >= 0) user.fcmTokens[existing] = row;
  else user.fcmTokens.push(row);
  if (user.fcmTokens.length > 8) {
    user.fcmTokens = user.fcmTokens
      .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
      .slice(0, 8);
  }
  return true;
}

function unregisterDeviceToken(user, token) {
  if (!user || !Array.isArray(user.fcmTokens)) return false;
  const t = String(token || "").trim();
  const before = user.fcmTokens.length;
  user.fcmTokens = user.fcmTokens.filter(
    (x) => (typeof x === "string" ? x : x?.token) !== t
  );
  return user.fcmTokens.length !== before;
}

/**
 * @param {import('http').Server} httpServer
 * @param {(sessionToken: string) => object|null} userFromSessionToken
 */
function attachAccountWss(httpServer, userFromSessionToken) {
  const wss = new WebSocketServer({ server: httpServer, path: "/v1/ws/account" });
  wss.on("error", (err) => {
    console.warn("[account_push] wss error", err?.message || err);
  });
  wss.on("connection", (socket, req) => {
    socket.on("error", (err) => {
      console.warn("[account_push] socket error", err?.message || err);
    });
    try {
      const url = new URL(req.url || "", `http://${req.headers.host}`);
      const sessionToken = String(url.searchParams.get("session") || "");
      const user = userFromSessionToken(sessionToken);
      if (!user || user.banned) {
        try {
          socket.close(4401, "auth_required");
        } catch {
          /* ignore */
        }
        return;
      }
      attachSocket(user.id, socket);
      try {
        socket.send(
          JSON.stringify({
            type: "account_hello",
            userId: user.id,
            at: Date.now(),
          })
        );
      } catch {
        /* ignore */
      }
      socket.on("message", (buf) => {
        // ping/pong soft
        try {
          const msg = JSON.parse(String(buf || ""));
          if (msg?.type === "ping") {
            socket.send(JSON.stringify({ type: "pong", at: Date.now() }));
          }
        } catch {
          /* ignore */
        }
      });
      socket.on("close", () => detachSocket(socket));
    } catch (e) {
      console.warn("[account_push] connection failed", e?.message || e);
      try {
        socket.close(1011, "error");
      } catch {
        /* ignore */
      }
    }
  });
  console.log("[account_push] account ws on /v1/ws/account enabled=" + enabled());
  return wss;
}

module.exports = {
  enabled,
  attachAccountWss,
  emitUserEvent,
  registerDeviceToken,
  unregisterDeviceToken,
  onlineCount,
  sendFcmToUser,
};

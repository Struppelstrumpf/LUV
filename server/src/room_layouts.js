/**
 * Raum-Layouts (Hochzeit + nutzerdefinierte Räume).
 * Zonen 0–1 relativ zum Bild. viewRect = sichtbarer Raumbereich (weiße Linie).
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "..", "data");
const IMAGE_DIR = path.join(DATA_DIR, "room-images");

const WEDDING = {
  id: "wedding",
  name: "Hochzeit",
  imageUrl: "/luv/wedding-chapel-room.png",
  builtin: true,
};

const DEFAULT_AVATAR_R = 0.028;
const GRID_W = 48;
const GRID_H = 64;

function ensureDirs() {
  fs.mkdirSync(IMAGE_DIR, { recursive: true });
}

function ensureStore(db) {
  if (!db.roomLayouts || typeof db.roomLayouts !== "object") {
    db.roomLayouts = {};
  }
  return db.roomLayouts;
}

function clamp01(n, fallback = 0) {
  const v = Number(n);
  if (!Number.isFinite(v)) return fallback;
  return Math.min(1, Math.max(0, v));
}

function sanitizeViewRect(raw) {
  if (!raw || typeof raw !== "object") {
    return { x: 0, y: 0, w: 1, h: 1 };
  }
  const x = clamp01(raw.x, 0);
  const y = clamp01(raw.y, 0);
  const w = Math.min(1 - x, Math.max(0.05, Number(raw.w) || 1));
  const h = Math.min(1 - y, Math.max(0.05, Number(raw.h) || 1));
  return { x, y, w, h };
}

/** Kamera-Fenster (schwarz): nur Breite/Höhe in Bildkoordinaten; Position läuft in der App. */
function sanitizeCameraRect(raw, viewRect) {
  const vr = sanitizeViewRect(viewRect);
  if (!raw || typeof raw !== "object") {
    return { w: vr.w, h: vr.h };
  }
  let w = Number(raw.w);
  let h = Number(raw.h);
  if (!Number.isFinite(w) || w <= 0) w = vr.w;
  if (!Number.isFinite(h) || h <= 0) h = vr.h;
  w = Math.min(vr.w, Math.max(0.12, w));
  h = Math.min(vr.h, Math.max(0.12, h));
  return { w, h };
}

const ACTION_TYPES = {
  cook: { label: "Kochen", icon: "🍳" },
};

function sanitizePortal(raw, index) {
  if (!raw || typeof raw !== "object") return null;
  const targetRoomId = String(raw.targetRoomId || "").trim().slice(0, 64);
  if (!targetRoomId) return null;
  const x = clamp01(raw.x, 0);
  const y = clamp01(raw.y, 0);
  const w = Math.min(1 - x, Math.max(0.02, Number(raw.w) || 0.08));
  const h = Math.min(1 - y, Math.max(0.02, Number(raw.h) || 0.08));
  let id = String(raw.id || "").trim().slice(0, 48);
  if (!id) id = `portal_${index}_${Date.now().toString(36)}`;
  return {
    id,
    x,
    y,
    w,
    h,
    targetRoomId,
    label: String(raw.label || "").trim().slice(0, 40),
  };
}

function sanitizeAction(raw, index) {
  if (!raw || typeof raw !== "object") return null;
  const actionType = String(raw.actionType || "").trim().slice(0, 32);
  if (!ACTION_TYPES[actionType]) return null;
  const x = clamp01(raw.x, 0);
  const y = clamp01(raw.y, 0);
  const w = Math.min(1 - x, Math.max(0.02, Number(raw.w) || 0.08));
  const h = Math.min(1 - y, Math.max(0.02, Number(raw.h) || 0.08));
  let id = String(raw.id || "").trim().slice(0, 48);
  if (!id) id = `action_${actionType}_${index}`;
  const def = ACTION_TYPES[actionType];
  return {
    id,
    x,
    y,
    w,
    h,
    actionType,
    label: String(raw.label || def.label).trim().slice(0, 40) || def.label,
  };
}

function sanitizeZone(raw, index) {
  if (!raw || typeof raw !== "object") return null;
  const color = String(raw.color || "").toLowerCase();
  const shape = String(raw.shape || "").toLowerCase();
  // gold = Deko, pink = Flamme, lime = Geldbaum (Hochzeit, 1 Coin tippen)
  if (
    ![
      "red",
      "green",
      "yellow",
      "blue",
      "brown",
      "orange",
      "gold",
      "pink",
      "lime",
    ].includes(color)
  ) {
    return null;
  }
  if (!["rect", "circle"].includes(shape)) return null;
  if (
    ["yellow", "blue", "brown", "orange", "gold", "pink", "lime"].includes(color) &&
    shape !== "circle"
  ) {
    return null;
  }
  if ((color === "red" || color === "green") && shape !== "rect") return null;

  let id = String(raw.id || "").trim().slice(0, 48);
  if (!id) {
    const prefix =
      color === "yellow"
        ? "altar_"
        : color === "blue"
          ? "sit_"
          : color === "brown"
            ? "spawn_"
            : color === "orange"
              ? "avatar_"
              : color === "gold"
                ? "deco_"
                : color === "pink"
                  ? "flame_"
                  : color === "lime"
                    ? "money_"
                    : `${color}_`;
    id = `${prefix}${index}_${Date.now().toString(36)}`;
  }
  if (color === "yellow" && !id.startsWith("altar_")) {
    id = `altar_${id.replace(/^altar_/, "")}`.slice(0, 48);
  }
  if (color === "lime" && !id.startsWith("money_")) {
    id = `money_${id.replace(/^money_/, "")}`.slice(0, 48);
  }

  if (shape === "circle") {
    const cx = clamp01(raw.cx ?? raw.x, 0.5);
    const cy = clamp01(raw.cy ?? raw.y, 0.5);
    const minR = color === "orange" ? 0.008 : 0.01;
    const r = Math.min(0.5, Math.max(minR, Number(raw.r) || DEFAULT_AVATAR_R));
    return { id, color, shape, cx, cy, r };
  }
  const x = clamp01(raw.x, 0);
  const y = clamp01(raw.y, 0);
  const w = Math.min(1, Math.max(0.01, Number(raw.w) || 0.1));
  const h = Math.min(1, Math.max(0.01, Number(raw.h) || 0.1));
  return {
    id,
    color,
    shape,
    x,
    y,
    w: Math.min(w, 1 - x),
    h: Math.min(h, 1 - y),
  };
}

function rectsOverlapOrTouch(a, b, eps = 0.002) {
  return !(
    a.x + a.w < b.x - eps ||
    b.x + b.w < a.x - eps ||
    a.y + a.h < b.y - eps ||
    b.y + b.h < a.y - eps
  );
}

function mergeGreenZones(zones) {
  const greens = zones.filter((z) => z.color === "green" && z.shape === "rect");
  const others = zones.filter((z) => !(z.color === "green" && z.shape === "rect"));
  if (greens.length <= 1) return zones;
  const n = greens.length;
  const parent = Array.from({ length: n }, (_, i) => i);
  const find = (i) => {
    while (parent[i] !== i) {
      parent[i] = parent[parent[i]];
      i = parent[i];
    }
    return i;
  };
  const uni = (a, b) => {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent[rb] = ra;
  };
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      if (rectsOverlapOrTouch(greens[i], greens[j])) uni(i, j);
    }
  }
  const groups = new Map();
  for (let i = 0; i < n; i++) {
    const r = find(i);
    if (!groups.has(r)) groups.set(r, []);
    groups.get(r).push(greens[i]);
  }
  const merged = [];
  for (const list of groups.values()) {
    let x0 = Infinity;
    let y0 = Infinity;
    let x1 = -Infinity;
    let y1 = -Infinity;
    for (const g of list) {
      x0 = Math.min(x0, g.x);
      y0 = Math.min(y0, g.y);
      x1 = Math.max(x1, g.x + g.w);
      y1 = Math.max(y1, g.y + g.h);
    }
    merged.push({
      id: list[0].id || `green_${merged.length}`,
      color: "green",
      shape: "rect",
      x: clamp01(x0),
      y: clamp01(y0),
      w: Math.min(1, Math.max(0.01, x1 - x0)),
      h: Math.min(1, Math.max(0.01, y1 - y0)),
    });
  }
  return [...others, ...merged];
}

function imagePublicUrl(roomId, ver) {
  const v = ver ? `?v=${ver}` : "";
  // Site-Pfad (Browser/Admin). App: CanvasMemoryKeeper.absoluteImageUrl entdoppelt /luv.
  return `/luv/v1/room-layouts/${encodeURIComponent(roomId)}/image${v}`;
}

function imageFilePath(roomId, ext) {
  return path.join(IMAGE_DIR, `${roomId}.${ext}`);
}

function findImageFile(roomId) {
  ensureDirs();
  for (const ext of ["png", "jpg", "jpeg", "webp"]) {
    const p = imageFilePath(roomId, ext);
    if (fs.existsSync(p)) return { path: p, ext };
  }
  return null;
}

function saveImageBase64(roomId, dataUrlOrB64, mimeHint) {
  ensureDirs();
  let b64 = String(dataUrlOrB64 || "");
  let mime = String(mimeHint || "");
  const m = /^data:([^;]+);base64,(.+)$/i.exec(b64);
  if (m) {
    mime = m[1];
    b64 = m[2];
  }
  b64 = b64.replace(/\s/g, "");
  if (!b64 || b64.length < 32) return { error: "bad_image" };
  if (b64.length > 12_000_000) return { error: "image_too_large" };
  let ext = "png";
  if (/jpeg|jpg/i.test(mime)) ext = "jpg";
  else if (/webp/i.test(mime)) ext = "webp";
  else if (/png/i.test(mime)) ext = "png";
  // alte Dateien weg
  for (const e of ["png", "jpg", "jpeg", "webp"]) {
    const old = imageFilePath(roomId, e);
    if (fs.existsSync(old)) fs.unlinkSync(old);
  }
  const buf = Buffer.from(b64, "base64");
  if (buf.length < 64) return { error: "bad_image" };
  fs.writeFileSync(imageFilePath(roomId, ext), buf);
  return { ok: true, ext };
}

function listRooms(db, { forApp = false } = {}) {
  ensureStore(db);
  const out = [];
  // Hochzeit immer (Admin); App-Picker nur Custom
  if (!forApp) {
    out.push(summarize(db, "wedding"));
  }
  for (const id of Object.keys(db.roomLayouts)) {
    if (id === "wedding") continue;
    out.push(summarize(db, id));
  }
  if (forApp) {
    // Plus-Kachel: nur pickable Räume mit Bild
    return out.filter(
      (r) => r && r.id !== "wedding" && r.hasImage && r.pickable !== false
    );
  }
  return out.filter(Boolean);
}

/** Räume, die als Portal-Ziel gewählt werden können (nicht via Plus-Kachel). */
function listLinkTargets(db, excludeId) {
  ensureStore(db);
  return Object.keys(db.roomLayouts)
    .map((id) => summarize(db, id))
    .filter(
      (r) =>
        r &&
        !r.builtin &&
        r.hasImage &&
        r.pickable === false &&
        r.id !== excludeId
    );
}

function summarize(db, roomId) {
  const layout = getLayout(db, roomId);
  if (!layout) return null;
  return {
    id: layout.id,
    name: layout.name,
    imageUrl: layout.imageUrl,
    zoneCount: layout.zones.length,
    portalCount: (layout.portals || []).length,
    actionCount: (layout.actions || []).length,
    updatedAt: layout.updatedAt,
    builtin: layout.builtin,
    hasImage: layout.hasImage,
    pickable: layout.pickable !== false,
    viewRect: layout.viewRect,
  };
}

function getLayout(db, roomId) {
  const id = String(roomId || "").trim().slice(0, 64);
  if (!id) return null;
  ensureStore(db);
  const saved = db.roomLayouts[id];
  const builtin = id === "wedding";
  if (!saved && !builtin) return null;

  const zones = Array.isArray(saved?.zones)
    ? mergeGreenZones(saved.zones.map((z, i) => sanitizeZone(z, i)).filter(Boolean))
    : [];
  const viewRect = sanitizeViewRect(saved?.viewRect);
  const cameraRect = sanitizeCameraRect(saved?.cameraRect, viewRect);
  const img = findImageFile(id);
  const hasImage = Boolean(img) || builtin;
  const updatedAt = saved?.updatedAt || null;
  let imageUrl = WEDDING.imageUrl;
  if (builtin && !img) imageUrl = WEDDING.imageUrl;
  else if (img || (saved && saved.imageExt)) {
    imageUrl = imagePublicUrl(id, updatedAt || Date.now());
  }
  const name =
    (saved && String(saved.name || "").trim().slice(0, 40)) ||
    (builtin ? WEDDING.name : id);
  const portals = Array.isArray(saved?.portals)
    ? saved.portals.map((p, i) => sanitizePortal(p, i)).filter(Boolean).slice(0, 40)
    : [];
  const actions = Array.isArray(saved?.actions)
    ? saved.actions.map((a, i) => sanitizeAction(a, i)).filter(Boolean).slice(0, 40)
    : [];
  const pickable = builtin ? false : saved?.pickable !== false;

  return {
    id,
    name,
    imageUrl,
    updatedAt,
    zones,
    portals,
    actions,
    pickable,
    viewRect,
    cameraRect,
    builtin,
    hasImage,
    avatarR: avatarRadius(zones),
    spawn: spawnPoint(zones, viewRect),
  };
}

function createRoom(db, { name, imageBase64, mime } = {}) {
  ensureStore(db);
  const id = `room_${crypto.randomBytes(6).toString("hex")}`;
  const nick = String(name || "Raum").trim().slice(0, 40) || "Raum";
  const img = saveImageBase64(id, imageBase64, mime);
  if (img.error) return { error: img.error };
  db.roomLayouts[id] = {
    id,
    name: nick,
    imageExt: img.ext,
    viewRect: { x: 0, y: 0, w: 1, h: 1 },
    zones: [],
    portals: [],
    actions: [],
    pickable: true,
    updatedAt: Date.now(),
    createdAt: Date.now(),
  };
  return { ok: true, layout: getLayout(db, id) };
}

function saveLayout(db, roomId, body = {}) {
  const id = String(roomId || "").trim().slice(0, 64);
  if (!id) return { error: "unknown_room" };
  ensureStore(db);
  const builtin = id === "wedding";
  if (!builtin && !db.roomLayouts[id] && !body.imageBase64) {
    return { error: "unknown_room" };
  }
  const prev = db.roomLayouts[id] || {
    id,
    name: builtin ? WEDDING.name : id,
    viewRect: { x: 0, y: 0, w: 1, h: 1 },
    zones: [],
  };
  if (body.imageBase64) {
    const img = saveImageBase64(id, body.imageBase64, body.mime);
    if (img.error) return { error: img.error };
    prev.imageExt = img.ext;
  }
  if (body.name != null) {
    prev.name = String(body.name || "").trim().slice(0, 40) || prev.name;
  }
  if (body.viewRect) prev.viewRect = sanitizeViewRect(body.viewRect);
  if (body.cameraRect) {
    prev.cameraRect = sanitizeCameraRect(
      body.cameraRect,
      body.viewRect || prev.viewRect
    );
  }
  if (Array.isArray(body.zones)) {
    prev.zones = mergeGreenZones(
      body.zones
        .slice(0, 200)
        .map((z, i) => sanitizeZone(z, i))
        .filter(Boolean)
    );
  }
  if (Array.isArray(body.portals)) {
    prev.portals = body.portals
      .slice(0, 40)
      .map((p, i) => sanitizePortal(p, i))
      .filter(Boolean);
  }
  if (Array.isArray(body.actions)) {
    prev.actions = body.actions
      .slice(0, 40)
      .map((a, i) => sanitizeAction(a, i))
      .filter(Boolean);
  }
  if (body.pickable != null && !builtin) {
    prev.pickable = Boolean(body.pickable);
  }
  prev.id = id;
  prev.updatedAt = Date.now();
  // Kamera nicht größer als Karte
  prev.cameraRect = sanitizeCameraRect(prev.cameraRect, prev.viewRect);
  db.roomLayouts[id] = prev;
  return { ok: true, layout: getLayout(db, id) };
}

function deleteRoom(db, roomId) {
  const id = String(roomId || "").trim();
  if (!id || id === "wedding") return { error: "cannot_delete" };
  ensureStore(db);
  if (!db.roomLayouts[id]) return { error: "unknown_room" };
  delete db.roomLayouts[id];
  for (const e of ["png", "jpg", "jpeg", "webp"]) {
    const p = imageFilePath(id, e);
    if (fs.existsSync(p)) fs.unlinkSync(p);
  }
  return { ok: true };
}

function findZone(db, roomId, seatId) {
  const layout = getLayout(db, roomId);
  if (!layout) return null;
  return layout.zones.find((z) => z.id === String(seatId || "")) || null;
}

function findSitZone(db, roomId, seatId) {
  const z = findZone(db, roomId, seatId);
  if (!z || (z.color !== "yellow" && z.color !== "blue")) return null;
  return z;
}

function findPortalAt(layout, x, y) {
  if (!layout?.portals?.length) return null;
  return (
    layout.portals.find((p) =>
      x >= p.x && x <= p.x + p.w && y >= p.y && y <= p.y + p.h
    ) || null
  );
}

function findActionAt(layout, x, y, pad = 0.045) {
  if (!layout?.actions?.length) return null;
  const p = Number(pad) || 0;
  return (
    layout.actions.find(
      (a) =>
        x >= a.x - p &&
        x <= a.x + a.w + p &&
        y >= a.y - p &&
        y <= a.y + a.h + p
    ) || null
  );
}

/** Andere Spieler nicht überlappen (gleicher Layout). */
function separateFromOthers(x, y, others, minDist) {
  let nx = x;
  let ny = y;
  const dMin = Math.max(0.02, Number(minDist) || 0.04);
  for (let pass = 0; pass < 4; pass++) {
    for (const o of others || []) {
      const ox = Number(o.x);
      const oy = Number(o.y);
      if (!Number.isFinite(ox) || !Number.isFinite(oy)) continue;
      const dx = nx - ox;
      const dy = ny - oy;
      const dist = Math.hypot(dx, dy);
      if (dist >= dMin || dist < 1e-6) continue;
      const push = (dMin - dist) / dist;
      nx += dx * push;
      ny += dy * push;
    }
  }
  return {
    x: Math.min(1, Math.max(0, nx)),
    y: Math.min(1, Math.max(0, ny)),
  };
}

function isCoupleSeat(zoneOrId) {
  if (!zoneOrId) return false;
  if (typeof zoneOrId === "string") return zoneOrId.startsWith("altar_");
  return zoneOrId.color === "yellow" || String(zoneOrId.id || "").startsWith("altar_");
}

function isGuestSeat(zoneOrId) {
  if (!zoneOrId) return false;
  if (typeof zoneOrId === "string") return zoneOrId.startsWith("sit_");
  return zoneOrId.color === "blue";
}

function isMoneyTree(zoneOrId) {
  if (!zoneOrId) return false;
  if (typeof zoneOrId === "string") return zoneOrId.startsWith("money_");
  return zoneOrId.color === "lime" || String(zoneOrId.id || "").startsWith("money_");
}

function zoneContains(z, x, y, pad = 0) {
  if (!z) return false;
  if (z.shape === "circle") {
    return Math.hypot(x - z.cx, y - z.cy) <= (Number(z.r) || 0) + pad;
  }
  return (
    x >= z.x - pad &&
    x <= z.x + z.w + pad &&
    y >= z.y - pad &&
    y <= z.y + z.h + pad
  );
}

function avatarRadius(zones) {
  const orange = (zones || []).find((z) => z.color === "orange" && z.shape === "circle");
  if (orange && Number(orange.r) > 0) return Number(orange.r);
  return DEFAULT_AVATAR_R;
}

function spawnPoint(zones, viewRect) {
  const brown = (zones || []).find((z) => z.color === "brown" && z.shape === "circle");
  if (brown) return { x: brown.cx, y: brown.cy };
  const vr = viewRect || { x: 0, y: 0, w: 1, h: 1 };
  return { x: vr.x + vr.w * 0.5, y: vr.y + vr.h * 0.85 };
}

function pointInGreen(zones, x, y) {
  return (zones || []).some((z) => z.color === "green" && zoneContains(z, x, y, 0));
}

function isWalkable(layout, x, y) {
  const zones = layout?.zones || [];
  const r = layout?.avatarR != null ? layout.avatarR : avatarRadius(zones);
  const greens = zones.filter((z) => z.color === "green");
  if (!greens.length) return false;
  // Wie App: Mittelpunkt in Grün, Rot nur bei echter Überlappung
  if (!pointInGreen(zones, x, y)) return false;
  return !zones.some((z) => z.color === "red" && zoneContains(z, x, y, 0));
}

function isBlocked(layout, x, y) {
  return !isWalkable(layout, x, y);
}

function cellCenter(ix, iy) {
  return { x: (ix + 0.5) / GRID_W, y: (iy + 0.5) / GRID_H };
}

function toCell(x, y) {
  return {
    ix: Math.min(GRID_W - 1, Math.max(0, Math.floor(x * GRID_W))),
    iy: Math.min(GRID_H - 1, Math.max(0, Math.floor(y * GRID_H))),
  };
}

function buildWalkGrid(layout) {
  const walk = new Array(GRID_W * GRID_H).fill(false);
  for (let iy = 0; iy < GRID_H; iy++) {
    for (let ix = 0; ix < GRID_W; ix++) {
      const { x, y } = cellCenter(ix, iy);
      walk[iy * GRID_W + ix] = isWalkable(layout, x, y);
    }
  }
  return walk;
}

function nearestWalkableCell(layout, walk, x, y) {
  const start = toCell(x, y);
  if (walk[start.iy * GRID_W + start.ix]) return start;
  let best = null;
  let bestD = Infinity;
  for (let iy = 0; iy < GRID_H; iy++) {
    for (let ix = 0; ix < GRID_W; ix++) {
      if (!walk[iy * GRID_W + ix]) continue;
      const c = cellCenter(ix, iy);
      const d = Math.hypot(c.x - x, c.y - y);
      if (d < bestD) {
        bestD = d;
        best = { ix, iy };
      }
    }
  }
  return best;
}

function findPath(layout, fromX, fromY, toX, toY) {
  const walk = buildWalkGrid(layout);
  const start = nearestWalkableCell(layout, walk, fromX, fromY);
  const goal = nearestWalkableCell(layout, walk, toX, toY);
  if (!start || !goal) return [];
  const key = (ix, iy) => iy * GRID_W + ix;
  const open = [{ ix: start.ix, iy: start.iy, g: 0, f: 0 }];
  const came = new Map();
  const gScore = new Map([[key(start.ix, start.iy), 0]]);
  const closed = new Set();
  const dirs = [
    [1, 0], [-1, 0], [0, 1], [0, -1],
    [1, 1], [1, -1], [-1, 1], [-1, -1],
  ];
  while (open.length) {
    open.sort((a, b) => a.f - b.f);
    const cur = open.shift();
    const ck = key(cur.ix, cur.iy);
    if (closed.has(ck)) continue;
    closed.add(ck);
    if (cur.ix === goal.ix && cur.iy === goal.iy) {
      const path = [];
      let k = ck;
      while (came.has(k)) {
        const ix = k % GRID_W;
        const iy = (k / GRID_W) | 0;
        path.push(cellCenter(ix, iy));
        k = came.get(k);
      }
      path.reverse();
      return path;
    }
    for (const [dx, dy] of dirs) {
      const nix = cur.ix + dx;
      const niy = cur.iy + dy;
      if (nix < 0 || niy < 0 || nix >= GRID_W || niy >= GRID_H) continue;
      const nk = key(nix, niy);
      if (!walk[nk] || closed.has(nk)) continue;
      if (dx && dy) {
        if (!walk[key(cur.ix + dx, cur.iy)] || !walk[key(cur.ix, cur.iy + dy)]) continue;
      }
      const step = dx && dy ? 1.414 : 1;
      const ng = (gScore.get(ck) || 0) + step;
      if (ng >= (gScore.get(nk) ?? Infinity)) continue;
      came.set(nk, ck);
      gScore.set(nk, ng);
      open.push({
        ix: nix,
        iy: niy,
        g: ng,
        f: ng + Math.hypot(nix - goal.ix, niy - goal.iy),
      });
    }
  }
  return [];
}

function ensureSpawnPosition(layout, positions, userId) {
  if (!positions || !userId) return null;
  if (positions[userId]) {
    if (!positions[userId].layoutId && layout?.id) {
      positions[userId].layoutId = layout.id;
    }
    return positions[userId];
  }
  const sp = layout?.spawn || spawnPoint(layout?.zones || [], layout?.viewRect);
  const layoutId = layout?.id || undefined;
  if (isWalkable(layout, sp.x, sp.y)) {
    positions[userId] = { x: sp.x, y: sp.y, layoutId };
    return positions[userId];
  }
  const walk = buildWalkGrid(layout);
  const near = nearestWalkableCell(layout, walk, sp.x, sp.y);
  if (near) {
    const c = cellCenter(near.ix, near.iy);
    positions[userId] = { x: c.x, y: c.y, layoutId };
  } else {
    positions[userId] = { x: sp.x, y: sp.y, layoutId };
  }
  return positions[userId];
}

function clampMove(layout, fromX, fromY, toX, toY) {
  let x = Math.min(1, Math.max(0, Number(fromX) || 0.5));
  let y = Math.min(1, Math.max(0, Number(fromY) || 0.75));
  const tx = Math.min(1, Math.max(0, Number(toX) || x));
  const ty = Math.min(1, Math.max(0, Number(toY) || y));
  if (!layout) return { x: tx, y: ty };
  const path = findPath(layout, x, y, tx, ty);
  if (!path.length) {
    const dist = Math.hypot(tx - x, ty - y);
    const steps = Math.min(80, Math.max(1, Math.ceil(dist / 0.01)));
    for (let i = 1; i <= steps; i++) {
      const nx = x + ((tx - x) * i) / steps;
      const ny = y + ((ty - y) * i) / steps;
      if (!isWalkable(layout, nx, ny)) return { x, y, blocked: true };
      x = nx;
      y = ny;
    }
    return { x, y, blocked: false };
  }
  const last = path[path.length - 1];
  return { x: last.x, y: last.y, blocked: false, path };
}

module.exports = {
  WEDDING,
  IMAGE_DIR,
  ACTION_TYPES,
  listRooms,
  listLinkTargets,
  sanitizeCameraRect,
  getLayout,
  createRoom,
  saveLayout,
  deleteRoom,
  findImageFile,
  findZone,
  findSitZone,
  findPortalAt,
  findActionAt,
  separateFromOthers,
  isCoupleSeat,
  isGuestSeat,
  isMoneyTree,
  isBlocked,
  isWalkable,
  clampMove,
  findPath,
  mergeGreenZones,
  avatarRadius,
  spawnPoint,
  ensureSpawnPosition,
  zoneContains,
  DEFAULT_AVATAR_R,
};

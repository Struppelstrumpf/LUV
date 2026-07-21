/**
 * Admin-konfigurierbare Raum-Layouts (z. B. Hochzeitskapelle).
 * Zonen: normalisierte 0–1 Koordinaten relativ zum Raumbild.
 *
 * Farben:
 *  - green  + rect   → begehbar
 *  - red    + rect   → blockiert (Avatar stoppt am Rand)
 *  - yellow + circle → Sitzplätze Eheleute
 *  - blue   + rect|circle → Sitzplätze (Tipp → hinlaufen & setzen)
 */

const ROOM_DEFS = {
  wedding: {
    id: "wedding",
    name: "Hochzeit",
    imageUrl: "/luv/wedding-chapel-room.png",
  },
};

function defaultWeddingZones() {
  return [
    { id: "altar_a", color: "yellow", shape: "circle", cx: 0.4, cy: 0.3, r: 0.04 },
    { id: "altar_b", color: "yellow", shape: "circle", cx: 0.6, cy: 0.3, r: 0.04 },
    { id: "bench_0", color: "blue", shape: "circle", cx: 0.3, cy: 0.44, r: 0.035 },
    { id: "bench_1", color: "blue", shape: "circle", cx: 0.3, cy: 0.54, r: 0.035 },
    { id: "bench_2", color: "blue", shape: "circle", cx: 0.3, cy: 0.63, r: 0.035 },
    { id: "bench_3", color: "blue", shape: "circle", cx: 0.3, cy: 0.72, r: 0.035 },
    { id: "bench_4", color: "blue", shape: "circle", cx: 0.7, cy: 0.44, r: 0.035 },
    { id: "bench_5", color: "blue", shape: "circle", cx: 0.7, cy: 0.54, r: 0.035 },
    { id: "bench_6", color: "blue", shape: "circle", cx: 0.7, cy: 0.63, r: 0.035 },
    { id: "bench_7", color: "blue", shape: "circle", cx: 0.7, cy: 0.72, r: 0.035 },
  ];
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

function sanitizeZone(raw, index) {
  if (!raw || typeof raw !== "object") return null;
  const color = String(raw.color || "").toLowerCase();
  const shape = String(raw.shape || "").toLowerCase();
  if (!["red", "green", "yellow", "blue"].includes(color)) return null;
  if (!["rect", "circle"].includes(shape)) return null;
  if (color === "yellow" && shape !== "circle") return null;
  if ((color === "red" || color === "green") && shape !== "rect") return null;

  let id = String(raw.id || "").trim().slice(0, 48);
  if (!id) {
    const prefix =
      color === "yellow" ? "altar_" : color === "blue" ? "sit_" : `${color}_`;
    id = `${prefix}${index}_${Date.now().toString(36)}`;
  }
  // Eheleute: Id mit altar_ für Rückwärtskompatibilität
  if (color === "yellow" && !id.startsWith("altar_")) {
    id = `altar_${id.replace(/^altar_/, "")}`.slice(0, 48);
  }

  if (shape === "circle") {
    const cx = clamp01(raw.cx ?? raw.x, 0.5);
    const cy = clamp01(raw.cy ?? raw.y, 0.5);
    const r = Math.min(0.5, Math.max(0.01, Number(raw.r) || 0.04));
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

function listRooms(db) {
  ensureStore(db);
  return Object.values(ROOM_DEFS).map((def) => {
    const saved = db.roomLayouts[def.id];
    const zones = Array.isArray(saved?.zones) ? saved.zones : defaultWeddingZones();
    return {
      id: def.id,
      name: def.name,
      imageUrl: def.imageUrl,
      zoneCount: zones.length,
      updatedAt: saved?.updatedAt || null,
    };
  });
}

function getLayout(db, roomId) {
  const def = ROOM_DEFS[String(roomId || "")];
  if (!def) return null;
  ensureStore(db);
  const saved = db.roomLayouts[def.id];
  const zones = Array.isArray(saved?.zones)
    ? saved.zones.map((z, i) => sanitizeZone(z, i)).filter(Boolean)
    : defaultWeddingZones();
  return {
    id: def.id,
    name: def.name,
    imageUrl: def.imageUrl,
    updatedAt: saved?.updatedAt || null,
    zones,
  };
}

function saveLayout(db, roomId, zonesRaw) {
  const def = ROOM_DEFS[String(roomId || "")];
  if (!def) return { error: "unknown_room" };
  if (!Array.isArray(zonesRaw)) return { error: "bad_zones" };
  ensureStore(db);
  const zones = zonesRaw
    .slice(0, 200)
    .map((z, i) => sanitizeZone(z, i))
    .filter(Boolean);
  const next = {
    id: def.id,
    name: def.name,
    imageUrl: def.imageUrl,
    updatedAt: Date.now(),
    zones,
  };
  db.roomLayouts[def.id] = next;
  return { ok: true, layout: getLayout(db, def.id) };
}

function findZone(db, roomId, seatId) {
  const layout = getLayout(db, roomId);
  if (!layout) return null;
  const id = String(seatId || "");
  return layout.zones.find((z) => z.id === id) || null;
}

function findSitZone(db, roomId, seatId) {
  const z = findZone(db, roomId, seatId);
  if (!z) return null;
  if (z.color !== "yellow" && z.color !== "blue") return null;
  return z;
}

function isCoupleSeat(zoneOrId) {
  if (!zoneOrId) return false;
  if (typeof zoneOrId === "string") return zoneOrId.startsWith("altar_");
  return zoneOrId.color === "yellow" || String(zoneOrId.id || "").startsWith("altar_");
}

function isGuestSeat(zoneOrId) {
  if (!zoneOrId) return false;
  if (typeof zoneOrId === "string") {
    return !zoneOrId.startsWith("altar_");
  }
  return zoneOrId.color === "blue";
}

const AVATAR_R = 0.028;

function zoneContains(z, x, y, pad = 0) {
  if (!z) return false;
  if (z.shape === "circle") {
    const dx = x - z.cx;
    const dy = y - z.cy;
    return Math.hypot(dx, dy) <= (Number(z.r) || 0) + pad;
  }
  return (
    x >= z.x - pad &&
    x <= z.x + z.w + pad &&
    y >= z.y - pad &&
    y <= z.y + z.h + pad
  );
}

function isBlocked(layout, x, y) {
  const zones = layout?.zones || [];
  return zones.some((z) => z.color === "red" && zoneContains(z, x, y, AVATAR_R * 0.35));
}

function isWalkable(layout, x, y) {
  if (isBlocked(layout, x, y)) return false;
  const greens = (layout?.zones || []).filter((z) => z.color === "green");
  if (!greens.length) return true;
  return greens.some((z) => zoneContains(z, x, y, AVATAR_R));
}

/** Bewegung schrittweise clampen — stoppt vor roten Bereichen. */
function clampMove(layout, fromX, fromY, toX, toY) {
  let x = Math.min(1, Math.max(0, Number(fromX) || 0.5));
  let y = Math.min(1, Math.max(0, Number(fromY) || 0.75));
  const tx = Math.min(1, Math.max(0, Number(toX) || x));
  const ty = Math.min(1, Math.max(0, Number(toY) || y));
  if (!layout) return { x: tx, y: ty };
  const dist = Math.hypot(tx - x, ty - y);
  if (dist < 1e-6) return { x, y };
  const steps = Math.min(80, Math.max(1, Math.ceil(dist / 0.012)));
  for (let i = 1; i <= steps; i++) {
    const nx = x + ((tx - x) * i) / steps;
    const ny = y + ((ty - y) * i) / steps;
    if (!isWalkable(layout, nx, ny)) {
      return { x, y, blocked: true };
    }
    x = nx;
    y = ny;
  }
  return { x, y, blocked: false };
}

module.exports = {
  ROOM_DEFS,
  listRooms,
  getLayout,
  saveLayout,
  findZone,
  findSitZone,
  isCoupleSeat,
  isGuestSeat,
  isBlocked,
  isWalkable,
  clampMove,
  defaultWeddingZones,
};

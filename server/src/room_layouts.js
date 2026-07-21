/**
 * Admin-konfigurierbare Raum-Layouts (z. B. Hochzeitskapelle).
 * Zonen: normalisierte 0–1 Koordinaten relativ zum Raumbild.
 *
 *  - green  + rect   → nur hier darf gelaufen werden (Avatar komplett drin)
 *  - red    + rect   → Hindernis (Bänke etc.) — drum herum laufen
 *  - yellow + circle → Sitz Eheleute
 *  - blue   + circle → Sitz (auch außerhalb Grün / in Rot)
 *  - brown  + circle → Spawn aller Avatare
 *  - orange + circle → Avatar-Radius (Größe der runden Avatare)
 *
 * Schneiden sich zwei Grüns → ein Rechteck (Bounding-Box der Komponente).
 * In der App sind die Zonen unsichtbar — nur System-Logik.
 */

const ROOM_DEFS = {
  wedding: {
    id: "wedding",
    name: "Hochzeit",
    imageUrl: "/luv/wedding-chapel-room.png",
  },
};

const DEFAULT_AVATAR_R = 0.028;
const GRID_W = 48;
const GRID_H = 64;

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
  if (!["red", "green", "yellow", "blue", "brown", "orange"].includes(color)) {
    return null;
  }
  if (!["rect", "circle"].includes(shape)) return null;
  if (["yellow", "blue", "brown", "orange"].includes(color) && shape !== "circle") {
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
              : `${color}_`;
    id = `${prefix}${index}_${Date.now().toString(36)}`;
  }
  if (color === "yellow" && !id.startsWith("altar_")) {
    id = `altar_${id.replace(/^altar_/, "")}`.slice(0, 48);
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

/** Schneiden/berühren sich Grüns → zu einem Rechteck (Bounding-Box) mergen. */
function mergeGreenZones(zones) {
  const greens = zones.filter((z) => z.color === "green" && z.shape === "rect");
  const others = zones.filter((z) => !(z.color === "green" && z.shape === "rect"));
  if (greens.length <= 1) return zones;

  const n = greens.length;
  const parent = Array.from({ length: n }, (_, i) => i);
  function find(i) {
    while (parent[i] !== i) {
      parent[i] = parent[parent[i]];
      i = parent[i];
    }
    return i;
  }
  function uni(a, b) {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent[rb] = ra;
  }
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
  let gi = 0;
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
      id: list[0].id || `green_${gi}`,
      color: "green",
      shape: "rect",
      x: clamp01(x0),
      y: clamp01(y0),
      w: Math.min(1, Math.max(0.01, x1 - x0)),
      h: Math.min(1, Math.max(0.01, y1 - y0)),
    });
    gi++;
  }
  return [...others, ...merged];
}

function listRooms(db) {
  ensureStore(db);
  return Object.values(ROOM_DEFS).map((def) => {
    const saved = db.roomLayouts[def.id];
    const zones = Array.isArray(saved?.zones) ? saved.zones : [];
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
    ? mergeGreenZones(saved.zones.map((z, i) => sanitizeZone(z, i)).filter(Boolean))
    : [];
  return {
    id: def.id,
    name: def.name,
    imageUrl: def.imageUrl,
    updatedAt: saved?.updatedAt || null,
    zones,
    avatarR: avatarRadius(zones),
    spawn: spawnPoint(zones),
  };
}

function saveLayout(db, roomId, zonesRaw) {
  const def = ROOM_DEFS[String(roomId || "")];
  if (!def) return { error: "unknown_room" };
  if (!Array.isArray(zonesRaw)) return { error: "bad_zones" };
  ensureStore(db);
  const zones = mergeGreenZones(
    zonesRaw
      .slice(0, 200)
      .map((z, i) => sanitizeZone(z, i))
      .filter(Boolean)
  );
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
  if (typeof zoneOrId === "string") return !zoneOrId.startsWith("altar_");
  return zoneOrId.color === "blue";
}

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

function avatarRadius(zones) {
  const orange = (zones || []).find((z) => z.color === "orange" && z.shape === "circle");
  if (orange && Number(orange.r) > 0) return Number(orange.r);
  return DEFAULT_AVATAR_R;
}

function spawnPoint(zones) {
  const brown = (zones || []).find((z) => z.color === "brown" && z.shape === "circle");
  if (brown) return { x: brown.cx, y: brown.cy };
  return { x: 0.5, y: 0.86 };
}

/** Punkt in mind. einem Grün (ohne Pad). */
function pointInGreen(zones, x, y) {
  return (zones || []).some((z) => z.color === "green" && zoneContains(z, x, y, 0));
}

/** Avatar-Kreis komplett in Grün und ohne Rot-Schnitt. */
function isWalkable(layout, x, y) {
  const zones = layout?.zones || [];
  const r = layout?.avatarR != null ? layout.avatarR : avatarRadius(zones);
  const greens = zones.filter((z) => z.color === "green");
  if (!greens.length) return false;

  const samples = [[x, y]];
  for (let i = 0; i < 12; i++) {
    const a = (i / 12) * Math.PI * 2;
    samples.push([x + Math.cos(a) * r, y + Math.sin(a) * r]);
  }
  for (const [px, py] of samples) {
    if (!pointInGreen(zones, px, py)) return false;
  }
  const reds = zones.filter((z) => z.color === "red");
  for (const red of reds) {
    if (zoneContains(red, x, y, r)) return false;
  }
  return true;
}

function isBlocked(layout, x, y) {
  return !isWalkable(layout, x, y);
}

function cellCenter(ix, iy) {
  return {
    x: (ix + 0.5) / GRID_W,
    y: (iy + 0.5) / GRID_H,
  };
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

/** A* in Grün um Rot herum. Ziel = nächste begehbare Zelle zum Wunschpunkt. */
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
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
    [1, 1],
    [1, -1],
    [-1, 1],
    [-1, -1],
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
      // Diagonale: beide Seiten müssen begehbar sein
      if (dx !== 0 && dy !== 0) {
        if (!walk[key(cur.ix + dx, cur.iy)] || !walk[key(cur.ix, cur.iy + dy)]) continue;
      }
      const step = dx !== 0 && dy !== 0 ? 1.414 : 1;
      const ng = (gScore.get(ck) || 0) + step;
      if (ng >= (gScore.get(nk) ?? Infinity)) continue;
      came.set(nk, ck);
      gScore.set(nk, ng);
      const h = Math.hypot(nix - goal.ix, niy - goal.iy);
      open.push({ ix: nix, iy: niy, g: ng, f: ng + h });
    }
  }
  return [];
}

/** Erste Position am braunen Spawn (oder nächstes begehbares Grün). */
function ensureSpawnPosition(layout, positions, userId) {
  if (!positions || !userId || positions[userId]) return positions?.[userId] || null;
  const sp = layout?.spawn || spawnPoint(layout?.zones || []);
  if (isWalkable(layout, sp.x, sp.y)) {
    positions[userId] = { x: sp.x, y: sp.y };
    return positions[userId];
  }
  const walk = buildWalkGrid(layout);
  const near = nearestWalkableCell(layout, walk, sp.x, sp.y);
  if (near) {
    const c = cellCenter(near.ix, near.iy);
    positions[userId] = { x: c.x, y: c.y };
  } else {
    positions[userId] = { x: sp.x, y: sp.y };
  }
  return positions[userId];
}

/** Bewegung entlang Pathfinding; stoppt am Grün-Rand / vor Rot. */
function clampMove(layout, fromX, fromY, toX, toY) {
  let x = Math.min(1, Math.max(0, Number(fromX) || 0.5));
  let y = Math.min(1, Math.max(0, Number(fromY) || 0.75));
  const tx = Math.min(1, Math.max(0, Number(toX) || x));
  const ty = Math.min(1, Math.max(0, Number(toY) || y));
  if (!layout) return { x: tx, y: ty };
  const path = findPath(layout, x, y, tx, ty);
  if (!path.length) {
    // Geradeaus so weit wie möglich in Grün
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
  findPath,
  mergeGreenZones,
  avatarRadius,
  spawnPoint,
  ensureSpawnPosition,
  zoneContains,
  DEFAULT_AVATAR_R,
};

/**
 * Custom-Begleiter-Bilder (PNG mit Alpha, Chroma-Key bereits clientseitig).
 * Ablage: DATA_DIR/pet-images/{itemId}.png
 */

const fs = require("fs");
const path = require("path");
const { DATA_DIR } = require("./store");

const DIR = path.join(DATA_DIR, "pet-images");
const MAX_BYTES = 1_200_000; // ~1.2 MB raw base64 payload limit after decode
/** Kleiner RAM-Cache — Inventar lädt viele img_* parallel. */
const memCache = new Map();
const MEM_CACHE_MAX = 80;

function ensureDir() {
  if (!fs.existsSync(DIR)) fs.mkdirSync(DIR, { recursive: true });
}

function safeId(itemId) {
  return String(itemId || "")
    .trim()
    .replace(/[^a-zA-Z0-9_\-]/g, "")
    .slice(0, 32);
}

function filePath(itemId) {
  const id = safeId(itemId);
  if (!id) return null;
  return path.join(DIR, `${id}.png`);
}

function hasImage(itemId) {
  const id = safeId(itemId);
  if (!id) return false;
  if (memCache.has(id)) return true;
  const p = filePath(id);
  return Boolean(p && fs.existsSync(p));
}

function readImage(itemId) {
  const id = safeId(itemId);
  if (!id) return null;
  if (memCache.has(id)) return memCache.get(id);
  const p = filePath(id);
  if (!p || !fs.existsSync(p)) return null;
  const buf = fs.readFileSync(p);
  if (memCache.size >= MEM_CACHE_MAX) {
    const first = memCache.keys().next().value;
    memCache.delete(first);
  }
  memCache.set(id, buf);
  return buf;
}

/**
 * @param {string} itemId
 * @param {string} dataUrlOrBase64  data:image/png;base64,... oder pure base64
 */
function saveImage(itemId, dataUrlOrBase64) {
  ensureDir();
  const id = safeId(itemId);
  if (!id) return { ok: false, error: "bad_id", message: "Ungültige Pet-ID." };
  let raw = String(dataUrlOrBase64 || "").trim();
  const m = raw.match(/^data:image\/(png|jpeg|jpg|webp);base64,(.+)$/i);
  if (m) raw = m[2];
  raw = raw.replace(/\s+/g, "");
  if (!raw) return { ok: false, error: "empty", message: "Kein Bild." };
  let buf;
  try {
    buf = Buffer.from(raw, "base64");
  } catch {
    return { ok: false, error: "bad_base64", message: "Bild ungültig." };
  }
  if (!buf.length || buf.length > MAX_BYTES) {
    return {
      ok: false,
      error: "too_large",
      message: "Bild zu groß (max. ~1 MB).",
    };
  }
  // PNG magic oder JPEG — wir speichern als .png; Client schickt PNG nach Chroma-Key
  const p = filePath(id);
  fs.writeFileSync(p, buf);
  memCache.set(id, buf);
  return { ok: true, itemId: id, bytes: buf.length };
}

function deleteImage(itemId) {
  const id = safeId(itemId);
  if (id) memCache.delete(id);
  const p = filePath(itemId);
  if (p && fs.existsSync(p)) fs.unlinkSync(p);
}

module.exports = {
  CHROMA_KEY_HEX: "#00FF00",
  ensureDir,
  hasImage,
  readImage,
  saveImage,
  deleteImage,
  filePath,
  safeId,
};

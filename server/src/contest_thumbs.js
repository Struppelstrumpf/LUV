/**
 * Contest-Vorschaubilder: volle Lobby-PNGs sind oft mehrere MB.
 * Für Abstimmung/Gewinner erzeugen wir einmalig ein JPEG-Thumb (~720px).
 */
const fs = require("fs");
const path = require("path");
const UPNG = require("upng-js");
const jpeg = require("jpeg-js");

const MAX_EDGE = 720;
const JPEG_QUALITY = 78;

function thumbPathFor(absImagePath) {
  const ext = path.extname(absImagePath);
  const base = ext ? absImagePath.slice(0, -ext.length) : absImagePath;
  return `${base}_thumb.jpg`;
}

function downsampleRgba(rgba, srcW, srcH, dstW, dstH) {
  const out = Buffer.alloc(dstW * dstH * 4);
  for (let y = 0; y < dstH; y++) {
    const sy = Math.min(srcH - 1, Math.floor((y * srcH) / dstH));
    for (let x = 0; x < dstW; x++) {
      const sx = Math.min(srcW - 1, Math.floor((x * srcW) / dstW));
      const si = (sy * srcW + sx) * 4;
      const di = (y * dstW + x) * 4;
      out[di] = rgba[si];
      out[di + 1] = rgba[si + 1];
      out[di + 2] = rgba[si + 2];
      out[di + 3] = 255;
    }
  }
  return out;
}

function encodeThumbFromPng(pngBuf) {
  const img = UPNG.decode(pngBuf);
  const frames = UPNG.toRGBA8(img);
  if (!frames?.length) throw new Error("empty_png");
  const rgba = Buffer.from(frames[0]);
  const srcW = img.width | 0;
  const srcH = img.height | 0;
  if (srcW < 1 || srcH < 1) throw new Error("bad_size");
  const scale = Math.min(1, MAX_EDGE / Math.max(srcW, srcH));
  const dstW = Math.max(1, Math.round(srcW * scale));
  const dstH = Math.max(1, Math.round(srcH * scale));
  const pixels =
    dstW === srcW && dstH === srcH
      ? rgba
      : downsampleRgba(rgba, srcW, srcH, dstW, dstH);
  return jpeg.encode({ data: pixels, width: dstW, height: dstH }, JPEG_QUALITY).data;
}

/**
 * Liefert absoluten Pfad zu einem JPEG-Thumb (erzeugt bei Bedarf).
 * Bei Fehler: null → Caller kann Original-PNG senden.
 */
function ensureContestThumb(absImagePath) {
  try {
    if (!absImagePath || !fs.existsSync(absImagePath)) return null;
    const thumb = thumbPathFor(absImagePath);
    const srcStat = fs.statSync(absImagePath);
    if (fs.existsSync(thumb)) {
      const tStat = fs.statSync(thumb);
      if (tStat.size > 64 && tStat.mtimeMs >= srcStat.mtimeMs) return thumb;
    }
    const pngBuf = fs.readFileSync(absImagePath);
    if (pngBuf.length < 64) return null;
    const jpg = encodeThumbFromPng(pngBuf);
    if (!jpg || jpg.length < 64) return null;
    fs.writeFileSync(thumb, jpg);
    return thumb;
  } catch (e) {
    console.error("contest_thumb", e?.message || e);
    return null;
  }
}

/** Best-effort beim Speichern einer Contest-Entry (nicht blockierend kritisch). */
function warmContestThumb(absImagePath) {
  try {
    ensureContestThumb(absImagePath);
  } catch {
    /* ignore */
  }
}

module.exports = {
  ensureContestThumb,
  warmContestThumb,
  thumbPathFor,
};

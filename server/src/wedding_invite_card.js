/**
 * Dynamische Hochzeits-Einladungskarte (JPEG) für Link-Vorschau / OG.
 */
const fs = require("fs");
const path = require("path");

const WIDTH = 1080;
const HEIGHT = 1440;
const MONTHS_DE = [
  "Januar",
  "Februar",
  "März",
  "April",
  "Mai",
  "Juni",
  "Juli",
  "August",
  "September",
  "Oktober",
  "November",
  "Dezember",
];

const BASE_PNG = path.join(__dirname, "assets", "wedding-invite-card-base.png");

let canvasMod = null;
function getCanvas() {
  if (canvasMod) return canvasMod;
  try {
    canvasMod = require("@napi-rs/canvas");
    return canvasMod;
  } catch (e) {
    console.error("wedding_invite_card: @napi-rs/canvas missing", e?.message || e);
    return null;
  }
}

function formatCeremonyParts(ceremonyAtMs) {
  const at = Number(ceremonyAtMs) || 0;
  if (!at) {
    return { dateLine: "Termin folgt", timeLine: "", umLine: "" };
  }
  const d = new Date(at);
  // Europe/Berlin-näher: Server UTC → +2 im Sommer oft ok; nutze lokale Server-TZ (UTC auf Hetzner)
  // Explizit Europe/Berlin via Intl
  const fmt = new Intl.DateTimeFormat("de-DE", {
    timeZone: "Europe/Berlin",
    day: "numeric",
    month: "long",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = Object.fromEntries(
    fmt.formatToParts(d).map((p) => [p.type, p.value])
  );
  const day = parts.day || "";
  const month = parts.month || MONTHS_DE[d.getUTCMonth()] || "";
  const year = parts.year || "";
  const hour = parts.hour || "00";
  const minute = parts.minute || "00";
  return {
    dateLine: `${day}. ${month} ${year}`,
    umLine: "um",
    timeLine: `${hour}:${minute} Uhr`,
  };
}

function coupleNames(coupleNicknames, fallbackHost) {
  const a = String(coupleNicknames?.a || "").trim().slice(0, 18);
  const b = String(coupleNicknames?.b || "").trim().slice(0, 18);
  if (a && b) return { a, b, line: `${a} & ${b}` };
  const host = String(fallbackHost || "Wir").trim().slice(0, 18) || "Wir";
  return { a: host, b: "", line: host };
}

function cardCachePath(dataDir, code, ceremonyAt) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  const at = Math.floor(Number(ceremonyAt) || 0);
  const dir = path.join(dataDir, "wedding-cards");
  return { dir, file: path.join(dir, `${clean}-${at}.jpg`) };
}

/**
 * @returns {Buffer|null} JPEG buffer
 */
async function renderWeddingInviteCard({
  coupleNicknames,
  hostNickname,
  ceremonyAt,
}) {
  const mod = getCanvas();
  if (!mod) return null;
  const { createCanvas, loadImage } = mod;
  if (!fs.existsSync(BASE_PNG)) {
    console.error("wedding_invite_card: base missing", BASE_PNG);
    return null;
  }

  const names = coupleNames(coupleNicknames, hostNickname);
  const when = formatCeremonyParts(ceremonyAt);

  const canvas = createCanvas(WIDTH, HEIGHT);
  const ctx = canvas.getContext("2d");

  const base = await loadImage(BASE_PNG);
  ctx.drawImage(base, 0, 0, WIDTH, HEIGHT);

  // Soft cream panel in the open center so text stays readable
  const panelX = WIDTH * 0.18;
  const panelY = HEIGHT * 0.28;
  const panelW = WIDTH * 0.64;
  const panelH = HEIGHT * 0.42;
  ctx.fillStyle = "rgba(255, 250, 245, 0.72)";
  roundRect(ctx, panelX, panelY, panelW, panelH, 28);
  ctx.fill();

  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  const cx = WIDTH / 2;

  // Title
  ctx.fillStyle = "#5c3d2e";
  ctx.font = "600 52px Georgia, 'Times New Roman', serif";
  ctx.fillText("Einladung zur Hochzeit", cx, panelY + 70);

  // Decorative rule
  ctx.strokeStyle = "rgba(184, 149, 90, 0.75)";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(cx - 160, panelY + 110);
  ctx.lineTo(cx + 160, panelY + 110);
  ctx.stroke();

  // Date block
  ctx.fillStyle = "#3a2a22";
  ctx.font = "500 48px Georgia, 'Times New Roman', serif";
  ctx.fillText(when.dateLine, cx, panelY + 190);

  if (when.umLine) {
    ctx.fillStyle = "#8a6a4a";
    ctx.font = "italic 36px Georgia, 'Times New Roman', serif";
    ctx.fillText(when.umLine, cx, panelY + 250);
  }

  if (when.timeLine) {
    ctx.fillStyle = "#3a2a22";
    ctx.font = "500 48px Georgia, 'Times New Roman', serif";
    ctx.fillText(when.timeLine, cx, panelY + 310);
  }

  // Signatures
  ctx.fillStyle = "#6b4a38";
  ctx.font = "italic 44px Georgia, 'Times New Roman', serif";
  if (names.a && names.b) {
    ctx.fillText(names.a, cx - 150, panelY + panelH - 70);
    ctx.fillText(names.b, cx + 150, panelY + panelH - 70);
    ctx.strokeStyle = "rgba(107, 74, 56, 0.35)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(cx - 230, panelY + panelH - 48);
    ctx.lineTo(cx - 70, panelY + panelH - 48);
    ctx.moveTo(cx + 70, panelY + panelH - 48);
    ctx.lineTo(cx + 230, panelY + panelH - 48);
    ctx.stroke();
  } else {
    ctx.fillText(names.line, cx, panelY + panelH - 70);
  }

  return canvas.toBuffer("image/jpeg", 88);
}

function roundRect(ctx, x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h / 2);
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
}

async function ensureWeddingInviteCardFile(dataDir, {
  code,
  coupleNicknames,
  hostNickname,
  ceremonyAt,
}) {
  const { dir, file } = cardCachePath(dataDir, code, ceremonyAt);
  try {
    if (fs.existsSync(file) && fs.statSync(file).size > 8_000) {
      return file;
    }
  } catch {
    /* regenerate */
  }
  const buf = await renderWeddingInviteCard({
    coupleNicknames,
    hostNickname,
    ceremonyAt,
  });
  if (!buf || buf.length < 8_000) return null;
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(file, buf);
  return file;
}

function publicWeddingCardUrl(code, ceremonyAt) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  const at = Math.floor(Number(ceremonyAt) || 0);
  return `https://reineke.pro/luv/v1/rooms/${clean}/wedding-invite-card?v=${at || Date.now()}`;
}

function inviteShareText({ coupleNicknames, hostNickname, ceremonyAt, joinUrl }) {
  const names = coupleNames(coupleNicknames, hostNickname);
  const when = formatCeremonyParts(ceremonyAt);
  const whenBlock = [when.dateLine, when.umLine, when.timeLine]
    .filter(Boolean)
    .join("\n");
  return [
    `${names.line} heiraten`,
    whenBlock,
    "Du bist herzlich eingeladen!",
    String(joinUrl || "").trim(),
  ]
    .filter(Boolean)
    .join("\n");
}

module.exports = {
  formatCeremonyParts,
  coupleNames,
  renderWeddingInviteCard,
  ensureWeddingInviteCardFile,
  publicWeddingCardUrl,
  inviteShareText,
  BASE_PNG,
};

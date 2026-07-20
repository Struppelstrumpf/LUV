/**
 * Invite-Landing + 60s-Probezeichnen (Trial) — Hilfen für index.js
 */
const fs = require("fs");
const path = require("path");

const TRIAL_DRAW_MS = 60_000;
const FALLBACK_OG = "https://reineke.pro/downloads/luv/og.jpg?v=1813";

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function snapshotFile(snapshotDir, code) {
  return path.join(snapshotDir, `${String(code || "").toUpperCase()}.png`);
}

function hasInviteSnapshot(snapshotDir, code) {
  try {
    return fs.existsSync(snapshotFile(snapshotDir, code));
  } catch {
    return false;
  }
}

function roomHasDrawing(snapshotDir, code, room) {
  if (hasInviteSnapshot(snapshotDir, code)) return true;
  if (Array.isArray(room?.strokes) && room.strokes.length > 0) return true;
  return false;
}

function playStoreUrl(code) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  const referrer = encodeURIComponent(`join=${clean}`);
  return `https://play.google.com/store/apps/details?id=com.luv.couple&referrer=${referrer}`;
}

function inviteImageAbsoluteUrl(code) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  return `https://reineke.pro/luv/v1/rooms/${clean}/invite-image`;
}

/**
 * Emotionale Invite-Landingseite (neutral: Partner oder Freund).
 */
function buildInviteLandingHtml({
  code,
  found,
  host,
  lobbyName,
  hasDrawing,
  inviteImageUrl,
  joinUrl,
  deep,
  playUrl,
}) {
  const safeHost = String(host || "Jemand").trim() || "Jemand";
  const title = found
    ? `${safeHost} lädt dich zu LUV ein`
    : "LUV — Einladung";
  const lede = found
    ? `${safeHost} möchte mit dir auf einer gemeinsamen Leinwand zeichnen — live, egal wo ihr seid.`
    : "Diese Einladung ist gerade nicht erreichbar. Bitte den Link später erneut öffnen.";
  const ogImage = hasDrawing && inviteImageUrl ? inviteImageUrl : FALLBACK_OG;
  const ogType = hasDrawing && inviteImageUrl ? "image/png" : "image/jpeg";
  const ctaLabel = "Jetzt mitmachen!";

  const previewBlock = hasDrawing && inviteImageUrl
    ? `<figure class="lobby-preview">
      <img src="${escapeHtml(inviteImageUrl)}" alt="Leinwand von ${escapeHtml(safeHost)}" width="720" height="1280" loading="eager" />
      <figcaption>${escapeHtml(lobbyName || "Lobby")} · ${escapeHtml(safeHost)}</figcaption>
    </figure>`
    : `<div class="fake-lobby" aria-hidden="true">
      <div class="fake-phone">
        <div class="fake-notch"></div>
        <div class="fake-canvas">
          <span class="fake-dot fake-dot-a"></span>
          <span class="fake-dot fake-dot-b"></span>
          <p class="fake-hint">Noch leer — eure ersten Striche warten.</p>
        </div>
      </div>
    </div>`;

  return `<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
  <title>${escapeHtml(title)}</title>
  <meta name="description" content="${escapeHtml(lede)}" />
  <meta name="theme-color" content="#0B0E14" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="LUV" />
  <meta property="og:url" content="${escapeHtml(joinUrl)}" />
  <meta property="og:title" content="${escapeHtml(title)}" />
  <meta property="og:description" content="${escapeHtml(lede)}" />
  <meta property="og:image" content="${escapeHtml(ogImage)}" />
  <meta property="og:image:secure_url" content="${escapeHtml(ogImage)}" />
  <meta property="og:image:alt" content="${escapeHtml(title)}" />
  <meta property="og:image:type" content="${ogType}" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="1200" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="${escapeHtml(title)}" />
  <meta name="twitter:description" content="${escapeHtml(lede)}" />
  <meta name="twitter:image" content="${escapeHtml(ogImage)}" />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,700&family=Outfit:wght@400;500;600;700&display=swap" rel="stylesheet" />
  <link rel="stylesheet" href="/luv/styles.css" />
  <style>
    .join-stage { min-height: 100dvh; display: grid; place-content: center; padding: 2rem 1.25rem 2.5rem; text-align: center; }
    .join-stage .brand { font-family: Fraunces, serif; font-size: clamp(2.6rem, 9vw, 4.2rem); letter-spacing: 0.12em; margin: 0; color: #f4f1ec; }
    .join-stage .brand .c-l { color: #00b7e4; }
    .join-stage .brand .c-u {
      display: inline-block;
      background: linear-gradient(90deg, #00b7e4 0%, #c218a8 100%);
      -webkit-background-clip: text; background-clip: text;
      color: transparent; -webkit-text-fill-color: transparent;
    }
    .join-stage .brand .c-v { color: #c218a8; }
    .join-stage .headline { font-family: Fraunces, serif; font-weight: 500; font-size: clamp(1.35rem, 4vw, 1.85rem); margin: 1rem 0 0.45rem; line-height: 1.25; }
    .join-stage .lede { opacity: 0.78; max-width: 26rem; margin: 0 auto 1.35rem; line-height: 1.5; font-family: Outfit, system-ui, sans-serif; }
    .join-stage .cta-row { display: flex; flex-direction: column; gap: 0.85rem; align-items: center; margin-top: 0.35rem; }
    .join-stage .download { text-decoration: none; }
    .lobby-preview {
      margin: 0 auto 1.35rem; max-width: min(18rem, 78vw);
      border-radius: 1.25rem; overflow: hidden;
      border: 1px solid rgba(255,255,255,0.14);
      background: rgba(0,0,0,0.35);
      box-shadow: 0 18px 50px rgba(0,0,0,0.35);
    }
    .lobby-preview img { display: block; width: 100%; height: auto; aspect-ratio: 9/16; object-fit: cover; }
    .lobby-preview figcaption {
      font-family: Outfit, system-ui, sans-serif; font-size: 0.78rem;
      padding: 0.65rem 0.8rem 0.8rem; color: rgba(244,241,236,0.78); text-align: left;
    }
    .fake-lobby { margin: 0 auto 1.35rem; }
    .fake-phone {
      width: min(15.5rem, 72vw); margin: 0 auto; border-radius: 1.6rem;
      padding: 0.55rem; background: linear-gradient(160deg, #1a2030, #0d111a);
      border: 1px solid rgba(255,255,255,0.16);
      box-shadow: 0 22px 55px rgba(0,0,0,0.4);
    }
    .fake-notch {
      width: 38%; height: 0.35rem; margin: 0.15rem auto 0.55rem;
      border-radius: 999px; background: rgba(255,255,255,0.12);
    }
    .fake-canvas {
      position: relative; aspect-ratio: 9/16; border-radius: 1.1rem;
      background:
        radial-gradient(ellipse at 30% 25%, rgba(0,183,228,0.18), transparent 55%),
        radial-gradient(ellipse at 70% 70%, rgba(194,24,168,0.16), transparent 50%),
        #121826;
      overflow: hidden;
    }
    .fake-dot {
      position: absolute; width: 0.7rem; height: 0.7rem; border-radius: 50%;
      animation: fakePulse 2.4s ease-in-out infinite;
    }
    .fake-dot-a { left: 28%; top: 38%; background: #00b7e4; }
    .fake-dot-b { right: 30%; top: 52%; background: #c218a8; animation-delay: 0.7s; }
    .fake-hint {
      position: absolute; left: 0.8rem; right: 0.8rem; bottom: 1.1rem;
      margin: 0; font-family: Outfit, system-ui, sans-serif; font-size: 0.78rem;
      color: rgba(244,241,236,0.55); line-height: 1.35;
    }
    @keyframes fakePulse {
      0%, 100% { transform: scale(1); opacity: 0.75; }
      50% { transform: scale(1.35); opacity: 1; }
    }
    .play-cta {
      display: inline-flex; align-items: center; gap: 0.75rem;
      padding: 0.85rem 1.15rem; border-radius: 1rem;
      background: #fff; color: #111; text-decoration: none;
      font-family: Outfit, system-ui, sans-serif; font-weight: 600;
      box-shadow: 0 10px 28px rgba(0,0,0,0.28);
      max-width: 100%;
    }
    .play-cta .play-ico { flex-shrink: 0; }
    .play-cta .download-text { text-align: left; display: flex; flex-direction: column; gap: 0.1rem; }
    .play-cta .download-label { font-size: 1.05rem; letter-spacing: 0.01em; }
    .play-cta .download-meta { font-size: 0.75rem; font-weight: 500; opacity: 0.65; }
    .open-app-link {
      font-family: Outfit, system-ui, sans-serif; font-size: 0.85rem;
      color: rgba(244,241,236,0.55); text-decoration: underline; text-underline-offset: 0.2em;
    }
  </style>
</head>
<body>
  <div class="atmosphere" aria-hidden="true">
    <div class="wash wash-a"></div>
    <div class="wash wash-b"></div>
    <div class="grain"></div>
  </div>
  <main class="join-stage">
    <p class="brand" aria-label="LUV"><span class="c-l">L</span><span class="c-u">U</span><span class="c-v">V</span></p>
    <h1 class="headline">${escapeHtml(found ? `${safeHost} lädt dich ein` : "Einladung")}</h1>
    <p class="lede">${escapeHtml(lede)}</p>
    ${previewBlock}
    <div class="cta-row">
      <a class="play-cta" href="${escapeHtml(playUrl)}" rel="noopener noreferrer">
        <span class="play-ico" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="28" height="28" focusable="false">
            <path fill="#EA4335" d="M3.6 2.2 13.5 12 3.6 21.8c-.5-.3-.8-.9-.8-1.5V3.7c0-.6.3-1.2.8-1.5z"/>
            <path fill="#FBBC04" d="m13.5 12 2.9-2.9 4.2 2.4c.7.4.7 1.4 0 1.8l-4.2 2.4L13.5 12z"/>
            <path fill="#4285F4" d="M13.5 12 3.6 2.2c.3-.2.7-.3 1.1-.1l11.7 6.7L13.5 12z"/>
            <path fill="#34A853" d="M13.5 12 16.4 14.9 4.7 21.6c-.4.2-.8.1-1.1-.1L13.5 12z"/>
          </svg>
        </span>
        <span class="download-text">
          <span class="download-label">${escapeHtml(ctaLabel)}</span>
          <span class="download-meta">Google Play · kostenlos</span>
        </span>
      </a>
      <a class="open-app-link" id="openApp" href="${escapeHtml(deep)}">App bereits installiert? Öffnen</a>
    </div>
  </main>
  <script>
    (function () {
      var deep = ${JSON.stringify(deep)};
      var found = ${found ? "true" : "false"};
      // App-Link nur anbieten — kein Auto-Redirect (verhindert leeren Fehler ohne App)
      if (!found) document.getElementById("openApp").style.display = "none";
    })();
  </script>
</body>
</html>`;
}

module.exports = {
  TRIAL_DRAW_MS,
  FALLBACK_OG,
  escapeHtml,
  snapshotFile,
  hasInviteSnapshot,
  roomHasDrawing,
  playStoreUrl,
  inviteImageAbsoluteUrl,
  buildInviteLandingHtml,
};

/**
 * Invite-Landing + 30s-Probezeichnen (Trial) — Hilfen für index.js
 */
const fs = require("fs");
const path = require("path");

const TRIAL_DRAW_MS = 30_000;
const FALLBACK_OG = "https://reineke.pro/downloads/luv/og.jpg?v=1813";
const WEDDING_ALTAR_OG = "https://reineke.pro/luv/og-wedding-altar.jpg";

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
    const file = snapshotFile(snapshotDir, code);
    if (!fs.existsSync(file)) return false;
    // Einfarbige / leere PNGs sind klein — zählen nicht als Zeichnung für OG
    return fs.statSync(file).size >= 10_000;
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

function inviteImageAbsoluteUrl(code, version) {
  const clean = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  const v = Number(version) > 0 ? String(Math.floor(Number(version))) : String(Date.now());
  // ?v= bricht WhatsApp/Link-Preview-Caches nach frischem Snapshot
  return `https://reineke.pro/luv/v1/rooms/${clean}/invite-image?v=${v}`;
}

/**
 * Emotionale Invite-Landingseite (neutral: Partner oder Freund).
 * isWeddingCeremony → Altar-OG + Hochzeits-Texte (kein „zeichnet gerade“).
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
  isWeddingCeremony = false,
  coupleLine = "",
  ceremonyDetails = "",
}) {
  const safeHost = String(host || "Jemand").trim() || "Jemand";
  const couple =
    String(coupleLine || "").trim() ||
    safeHost;
  const details = String(ceremonyDetails || "").trim();
  // Klar & einfach — WhatsApp zeigt title/description + Bild
  let title;
  let lede;
  let headline;
  let ctaLabel;
  let ogImage;
  let ogType;
  let previewBlock;

  if (isWeddingCeremony && found) {
    title = "Einladung zur Hochzeit";
    headline = "Einladung zur Hochzeit";
    lede = details
      ? `${couple} heiraten am ${details}. Du bist herzlich eingeladen!`
      : `${couple} heiraten — du bist herzlich eingeladen!`;
    ctaLabel = "Zur Hochzeit";
    ogImage = inviteImageUrl || WEDDING_ALTAR_OG;
    ogType = "image/jpeg";
    const previewSrc = inviteImageUrl || WEDDING_ALTAR_OG;
    previewBlock = `<figure class="lobby-preview lobby-preview--altar">
      <img src="${escapeHtml(previewSrc)}" alt="Hochzeitseinladung" width="1080" height="1440" loading="eager" />
      <figcaption>${escapeHtml(couple)}${details ? " · " + escapeHtml(details) : ""}</figcaption>
    </figure>`;
  } else {
    title = found
      ? "Du wurdest eingeladen mitzuzeichnen"
      : "LUV — Einladung";
    lede = found
      ? hasDrawing && inviteImageUrl
        ? `${safeHost} zeichnet gerade — das ist die Leinwand. Komm rein und male mit.`
        : `${safeHost} zeichnet gerade auf LUV — komm rein und male mit.`
      : "Diese Einladung ist gerade nicht erreichbar. Bitte den Link später erneut öffnen.";
    ogImage = hasDrawing && inviteImageUrl ? inviteImageUrl : FALLBACK_OG;
    ogType = hasDrawing && inviteImageUrl ? "image/png" : "image/jpeg";
    ctaLabel = "Jetzt mitmachen!";
    headline = found
      ? "Du wurdest eingeladen mitzuzeichnen"
      : "Einladung";
    previewBlock = hasDrawing && inviteImageUrl
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
  }

  const cleanCode = String(code || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  // Fallback mit ?stay=1 verhindert Redirect-Schleife, wenn die App fehlt
  const stayUrl =
    String(joinUrl || "").indexOf("?") >= 0
      ? `${joinUrl}&stay=1`
      : `${joinUrl}?stay=1`;
  // Intent: App öffnen wenn installiert, sonst auf dieser Seite bleiben (kein Play-Zwang)
  const intentUrl =
    `intent://join/${cleanCode}#Intent;scheme=luv;package=com.luv.couple;` +
    `S.browser_fallback_url=${encodeURIComponent(stayUrl)};end`;

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
  <meta property="og:image:width" content="${isWeddingCeremony && found ? "1080" : "1200"}" />
  <meta property="og:image:height" content="${isWeddingCeremony && found ? "1440" : "1200"}" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="${escapeHtml(title)}" />
  <meta name="twitter:description" content="${escapeHtml(lede)}" />
  <meta name="twitter:image" content="${escapeHtml(ogImage)}" />
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,700&family=Outfit:wght@400;500;600;700&display=swap" rel="stylesheet" />
  <link rel="stylesheet" href="/luv/styles.css" />
  <style>
    html, body { height: 100%; margin: 0; overflow: hidden; }
    .join-stage {
      height: 100dvh; height: 100svh; max-height: 100dvh;
      box-sizing: border-box;
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      padding: max(0.55rem, env(safe-area-inset-top)) 1rem max(0.7rem, env(safe-area-inset-bottom));
      text-align: center; overflow: hidden; gap: 0.35rem;
    }
    .join-stage .brand {
      font-family: Fraunces, serif; font-size: clamp(1.65rem, 5.5vw, 2.35rem);
      letter-spacing: 0.12em; margin: 0; color: #f4f1ec; line-height: 1;
      flex-shrink: 0;
    }
    .join-stage .brand .c-l { color: #00b7e4; }
    .join-stage .brand .c-u {
      display: inline-block;
      background: linear-gradient(90deg, #00b7e4 0%, #c218a8 100%);
      -webkit-background-clip: text; background-clip: text;
      color: transparent; -webkit-text-fill-color: transparent;
    }
    .join-stage .brand .c-v { color: #c218a8; }
    .join-stage .headline {
      font-family: Fraunces, serif; font-weight: 500;
      font-size: clamp(1.05rem, 3.6vw, 1.35rem);
      margin: 0; line-height: 1.2; flex-shrink: 0;
      max-width: 22rem;
    }
    .join-stage .lede {
      opacity: 0.78; max-width: 22rem; margin: 0;
      line-height: 1.35; font-family: Outfit, system-ui, sans-serif;
      font-size: clamp(0.78rem, 2.6vw, 0.9rem);
      flex-shrink: 0;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
    }
    .join-stage .cta-row {
      display: flex; flex-direction: column; gap: 0.45rem; align-items: center;
      margin-top: 0.15rem; flex-shrink: 0; width: 100%;
    }
    .lobby-preview {
      margin: 0; width: min(11.5rem, 42vw);
      max-height: min(38dvh, 42vh);
      border-radius: 1rem; overflow: hidden;
      border: 1px solid rgba(255,255,255,0.14);
      background: rgba(0,0,0,0.35);
      box-shadow: 0 12px 32px rgba(0,0,0,0.32);
      flex: 0 1 auto; min-height: 0;
      display: flex; flex-direction: column;
    }
    .lobby-preview img {
      display: block; width: 100%; height: 100%; min-height: 0;
      flex: 1 1 auto; object-fit: cover; aspect-ratio: 9/16;
      max-height: calc(min(38dvh, 42vh) - 1.6rem);
    }
    .lobby-preview--altar { width: min(14rem, 52vw); }
    .lobby-preview--altar img {
      aspect-ratio: 1/1;
      object-fit: cover;
      max-height: calc(min(38dvh, 42vh) - 1.6rem);
    }
    .lobby-preview figcaption {
      font-family: Outfit, system-ui, sans-serif; font-size: 0.68rem;
      padding: 0.35rem 0.55rem 0.45rem; color: rgba(244,241,236,0.78); text-align: left;
      flex-shrink: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .fake-lobby { margin: 0; flex: 0 1 auto; min-height: 0; max-height: min(38dvh, 42vh); }
    .fake-phone {
      width: min(10.5rem, 40vw); max-height: inherit; margin: 0 auto; border-radius: 1.2rem;
      padding: 0.4rem; background: linear-gradient(160deg, #1a2030, #0d111a);
      border: 1px solid rgba(255,255,255,0.16);
      box-shadow: 0 14px 36px rgba(0,0,0,0.35);
      box-sizing: border-box; height: 100%;
      display: flex; flex-direction: column;
    }
    .fake-notch {
      width: 38%; height: 0.28rem; margin: 0.1rem auto 0.35rem;
      border-radius: 999px; background: rgba(255,255,255,0.12); flex-shrink: 0;
    }
    .fake-canvas {
      position: relative; flex: 1 1 auto; min-height: 0;
      aspect-ratio: 9/16; max-height: calc(min(38dvh, 42vh) - 1.2rem);
      border-radius: 0.9rem;
      background:
        radial-gradient(ellipse at 30% 25%, rgba(0,183,228,0.18), transparent 55%),
        radial-gradient(ellipse at 70% 70%, rgba(194,24,168,0.16), transparent 50%),
        #121826;
      overflow: hidden;
    }
    .fake-dot {
      position: absolute; width: 0.55rem; height: 0.55rem; border-radius: 50%;
      animation: fakePulse 2.4s ease-in-out infinite;
    }
    .fake-dot-a { left: 28%; top: 38%; background: #00b7e4; }
    .fake-dot-b { right: 30%; top: 52%; background: #c218a8; animation-delay: 0.7s; }
    .fake-hint {
      position: absolute; left: 0.55rem; right: 0.55rem; bottom: 0.7rem;
      margin: 0; font-family: Outfit, system-ui, sans-serif; font-size: 0.68rem;
      color: rgba(244,241,236,0.55); line-height: 1.3;
    }
    @keyframes fakePulse {
      0%, 100% { transform: scale(1); opacity: 0.75; }
      50% { transform: scale(1.35); opacity: 1; }
    }
    .play-cta {
      display: inline-flex; align-items: center; gap: 0.6rem;
      padding: 0.65rem 0.95rem; border-radius: 0.9rem;
      background: #fff; color: #111; text-decoration: none;
      font-family: Outfit, system-ui, sans-serif; font-weight: 600;
      box-shadow: 0 8px 22px rgba(0,0,0,0.28);
      max-width: min(100%, 18rem);
    }
    .play-cta .play-ico { flex-shrink: 0; }
    .play-cta .play-ico svg { width: 22px; height: 22px; }
    .play-cta .download-text { text-align: left; display: flex; flex-direction: column; gap: 0.05rem; }
    .play-cta .download-label { font-size: 0.95rem; letter-spacing: 0.01em; }
    .play-cta .download-meta { font-size: 0.68rem; font-weight: 500; opacity: 0.65; }
    .open-app-link {
      font-family: Outfit, system-ui, sans-serif; font-size: 0.78rem;
      color: rgba(244,241,236,0.55); text-decoration: underline; text-underline-offset: 0.2em;
      padding: 0.15rem;
    }
    @media (max-height: 620px) {
      .join-stage { gap: 0.22rem; padding-top: 0.4rem; padding-bottom: 0.45rem; }
      .lobby-preview, .fake-lobby { max-height: 32dvh; }
      .lobby-preview img, .fake-canvas { max-height: calc(32dvh - 1.4rem); }
      .join-stage .lede { -webkit-line-clamp: 1; }
    }
    @media (max-height: 520px) {
      .lobby-preview, .fake-lobby { max-height: 26dvh; }
      .join-stage .brand { font-size: 1.45rem; }
      .join-stage .headline { font-size: 1rem; }
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
    <h1 class="headline">${escapeHtml(headline)}</h1>
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
      <a class="open-app-link" id="openApp" href="${escapeHtml(intentUrl)}">App bereits installiert? Öffnen</a>
    </div>
  </main>
  <script>
    (function () {
      var found = ${found ? "true" : "false"};
      var intentUrl = ${JSON.stringify(intentUrl)};
      var deep = ${JSON.stringify(deep)};
      var openApp = document.getElementById("openApp");
      if (!found) {
        if (openApp) openApp.style.display = "none";
        return;
      }
      var ua = navigator.userAgent || "";
      var isAndroid = /Android/i.test(ua);
      // Nur Crawler auslassen — WhatsApp-In-App-Browser soll die App öffnen
      var isBot = /bot|crawl|spider|facebookexternalhit|twitterbot|slackbot|discordbot|linkedinbot|applebot/i.test(ua);
      var stay = false;
      try {
        stay = new URLSearchParams(window.location.search || "").get("stay") === "1";
      } catch (e) {}
      function tryOpen() {
        try {
          window.location.href = intentUrl || deep;
        } catch (e) {}
      }
      // Auto-Open nur einmal — nach Fallback (?stay=1) auf der Landing bleiben
      if (isAndroid && !isBot && !stay) {
        setTimeout(tryOpen, 280);
      }
    })();
  </script>
</body>
</html>`;
}

module.exports = {
  TRIAL_DRAW_MS,
  FALLBACK_OG,
  WEDDING_ALTAR_OG,
  escapeHtml,
  snapshotFile,
  hasInviteSnapshot,
  roomHasDrawing,
  playStoreUrl,
  inviteImageAbsoluteUrl,
  buildInviteLandingHtml,
};

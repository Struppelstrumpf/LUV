/**
 * Google Play Integrity — prüft: echte App (Play) auf echtem Gerät.
 * Nutzt denselben Service-Account wie Play Billing (Scope playintegrity).
 */
const crypto = require("crypto");
const playBilling = require("./play_billing");

const PACKAGE_NAME = playBilling.PACKAGE_NAME;
const nonces = new Map(); // nonce → { exp, ip }

function cloudProjectNumber() {
  const n = String(process.env.GOOGLE_CLOUD_PROJECT_NUMBER || "").trim();
  if (n) return n;
  // Fallback: aus Web-Client-ID (PREFIX-….apps.googleusercontent.com)
  const cid = String(process.env.GOOGLE_CLIENT_ID || "").trim();
  const m = cid.match(/^(\d+)-/);
  return m ? m[1] : "";
}

function isConfigured() {
  return Boolean(playBilling.loadServiceAccount() && cloudProjectNumber());
}

/** Pflicht nur wenn explizit an ODER Service-Account + Project gesetzt und nicht disabled. */
function isEnforced() {
  if (String(process.env.PLAY_INTEGRITY_REQUIRED || "").trim() === "0") return false;
  if (String(process.env.PLAY_INTEGRITY_REQUIRED || "").trim() === "1") {
    return isConfigured();
  }
  // Default: durchsetzen sobald konfiguriert
  return isConfigured();
}

function issueNonce(ip) {
  const nonce = crypto.randomBytes(24).toString("base64url");
  const exp = Date.now() + 5 * 60 * 1000;
  nonces.set(nonce, { exp, ip: String(ip || "") });
  if (nonces.size > 4000) {
    const now = Date.now();
    for (const [k, v] of nonces) {
      if (v.exp < now) nonces.delete(k);
    }
  }
  return { nonce, expiresAt: exp, cloudProjectNumber: cloudProjectNumber() };
}

function takeNonce(nonce) {
  const key = String(nonce || "").trim();
  if (!key) return null;
  const row = nonces.get(key);
  nonces.delete(key);
  if (!row || row.exp < Date.now()) return null;
  return row;
}

async function getIntegrityAccessToken() {
  const sa = playBilling.loadServiceAccount();
  if (!sa) throw new Error("play_integrity_not_configured");
  const now = Math.floor(Date.now() / 1000);
  const b64url = (input) =>
    Buffer.from(input)
      .toString("base64")
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/playintegrity",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    })
  );
  const unsigned = `${header}.${claim}`;
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(unsigned);
  signer.end();
  const sig = signer
    .sign(sa.private_key)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  const assertion = `${unsigned}.${sig}`;
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });
  const resp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  const data = await resp.json();
  if (!resp.ok || !data.access_token) {
    throw new Error(`integrity_token_failed: ${data.error || resp.status}`);
  }
  return data.access_token;
}

/**
 * @returns {Promise<{ ok: boolean, error?: string, message?: string, verdicts?: string[] }>}
 */
async function verifyIntegrityToken(integrityToken, expectedNonce) {
  const token = String(integrityToken || "").trim();
  if (!token) {
    return { ok: false, error: "missing_integrity", message: "Geräteprüfung fehlt." };
  }
  if (!isConfigured()) {
    return { ok: true, verdicts: ["skipped_not_configured"] };
  }
  const nonceRow = takeNonce(expectedNonce);
  if (!nonceRow) {
    return {
      ok: false,
      error: "bad_nonce",
      message: "Geräteprüfung abgelaufen — bitte erneut mit Google anmelden.",
    };
  }
  try {
    const access = await getIntegrityAccessToken();
    const url =
      "https://playintegrity.googleapis.com/v1/" +
      encodeURIComponent(PACKAGE_NAME) +
      ":decodeIntegrityToken";
    const resp = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${access}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ integrity_token: token }),
    });
    const data = await resp.json();
    if (!resp.ok) {
      console.error("[play_integrity] decode failed", resp.status, data);
      return {
        ok: false,
        error: "integrity_decode_failed",
        message: "Geräteprüfung fehlgeschlagen. Bitte die App aus dem Play Store nutzen.",
      };
    }
    const payload = data.tokenPayloadExternal || {};
    const reqDetails = payload.requestDetails || {};
    const gotNonce = String(reqDetails.nonce || "").trim();
    if (gotNonce && gotNonce !== expectedNonce) {
      return {
        ok: false,
        error: "nonce_mismatch",
        message: "Geräteprüfung ungültig.",
      };
    }
    // packageName prüfen
    if (
      reqDetails.requestPackageName &&
      reqDetails.requestPackageName !== PACKAGE_NAME
    ) {
      return {
        ok: false,
        error: "package_mismatch",
        message: "Ungültige App-Version.",
      };
    }
    const device = payload.deviceIntegrity?.deviceRecognitionVerdict || [];
    const appLic = payload.appIntegrity?.appRecognitionVerdict || "";
    // Mindestens Basic Device Integrity (echte Android-Umgebung)
    const deviceOk =
      device.includes("MEETS_DEVICE_INTEGRITY") ||
      device.includes("MEETS_BASIC_INTEGRITY") ||
      device.includes("MEETS_STRONG_INTEGRITY");
    // App aus Play oder UNEVALUATED (interne Tests) — PLAY_RECOGNIZED ideal
    const appOk =
      !appLic ||
      appLic === "PLAY_RECOGNIZED" ||
      appLic === "UNRECOGNIZED_VERSION" ||
      appLic === "UNEVALUATED";

    if (!deviceOk) {
      return {
        ok: false,
        error: "device_untrusted",
        message:
          "Anmeldung nur von einem echten Android-Gerät mit der Play-Store-App möglich.",
        verdicts: device,
      };
    }
    if (appLic === "UNEVALUATED" || appLic === "UNRECOGNIZED_VERSION") {
      // Internal testing / frische Builds: Device-Check reicht
      console.warn("[play_integrity] soft app verdict", appLic);
    } else if (!appOk) {
      return {
        ok: false,
        error: "app_untrusted",
        message: "Bitte LUV aus dem Google Play Store installieren.",
        verdicts: device,
      };
    }
    return { ok: true, verdicts: device, appLic };
  } catch (e) {
    console.error("[play_integrity]", e);
    return {
      ok: false,
      error: "integrity_error",
      message: "Geräteprüfung vorübergehend nicht möglich. Bitte später erneut.",
    };
  }
}

module.exports = {
  isConfigured,
  isEnforced,
  issueNonce,
  verifyIntegrityToken,
  cloudProjectNumber,
  PACKAGE_NAME,
};

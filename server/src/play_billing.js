/**
 * Google Play Billing — Server-seitige Kaufprüfung (Android Publisher API).
 * Auth: Service-Account JWT (kein googleapis-Package nötig).
 */
const fs = require("fs");
const crypto = require("crypto");

const PACKAGE_NAME = String(
  process.env.GOOGLE_PLAY_PACKAGE || "com.luv.couple"
).trim();

let cachedSa = null;
let cachedToken = null;
let cachedTokenExp = 0;

function loadServiceAccount() {
  if (cachedSa) return cachedSa;
  const file = String(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE || "").trim();
  const raw = String(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || "").trim();
  let json = null;
  if (file && fs.existsSync(file)) {
    try {
      json = JSON.parse(fs.readFileSync(file, "utf8"));
    } catch (e) {
      console.error("play_billing: cannot read service account file", e.message);
    }
  }
  if (!json && raw) {
    try {
      json = JSON.parse(raw);
    } catch (e) {
      console.error("play_billing: invalid GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", e.message);
      return null;
    }
  }
  if (!json?.client_email || !json?.private_key) return null;
  cachedSa = json;
  return cachedSa;
}

function isConfigured() {
  if (String(process.env.GOOGLE_PLAY_BILLING_ENABLED || "").trim() === "0") {
    return false;
  }
  return Boolean(loadServiceAccount());
}

function b64url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

async function getAccessToken() {
  const sa = loadServiceAccount();
  if (!sa) throw new Error("play_billing_not_configured");
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedTokenExp > now + 60) return cachedToken;

  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = b64url(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/androidpublisher",
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
    throw new Error(
      `play_token_failed: ${data.error || resp.status} ${data.error_description || ""}`
    );
  }
  cachedToken = data.access_token;
  cachedTokenExp = now + Number(data.expires_in || 3600);
  return cachedToken;
}

/**
 * @returns {Promise<{ purchaseState: number, consumptionState?: number, orderId?: string, purchaseTimeMillis?: string }>}
 */
async function getProductPurchase(productId, purchaseToken) {
  const token = await getAccessToken();
  const url =
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
    encodeURIComponent(PACKAGE_NAME) +
    "/purchases/products/" +
    encodeURIComponent(productId) +
    "/tokens/" +
    encodeURIComponent(purchaseToken);
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await resp.json();
  if (!resp.ok) {
    const err = new Error(data.error?.message || `play_verify_${resp.status}`);
    err.status = resp.status;
    err.detail = data;
    throw err;
  }
  return data;
}

module.exports = {
  PACKAGE_NAME,
  isConfigured,
  getProductPurchase,
  loadServiceAccount,
};

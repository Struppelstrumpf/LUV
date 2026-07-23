/**
 * Minimal Google OAuth2 JWT for FCM HTTP v1 (no google-auth-library dependency).
 */
const crypto = require("crypto");
const https = require("https");

function b64url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function fetchAccessToken(sa) {
  return new Promise((resolve, reject) => {
    const now = Math.floor(Date.now() / 1000);
    const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const claim = b64url(
      JSON.stringify({
        iss: sa.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      })
    );
    const unsigned = `${header}.${claim}`;
    const sign = crypto.createSign("RSA-SHA256");
    sign.update(unsigned);
    sign.end();
    const sig = sign
      .sign(sa.private_key)
      .toString("base64")
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    const assertion = `${unsigned}.${sig}`;
    const body = new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString();
    const req = https.request(
      {
        hostname: "oauth2.googleapis.com",
        path: "/token",
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Content-Length": Buffer.byteLength(body),
        },
      },
      (res) => {
        let raw = "";
        res.on("data", (c) => {
          raw += c;
        });
        res.on("end", () => {
          try {
            const json = JSON.parse(raw);
            if (!json.access_token) {
              reject(new Error(json.error_description || json.error || raw.slice(0, 200)));
              return;
            }
            resolve({
              access_token: json.access_token,
              expires_in: Number(json.expires_in) || 3600,
            });
          } catch (e) {
            reject(e);
          }
        });
      }
    );
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}

module.exports = { fetchAccessToken };

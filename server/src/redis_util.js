/**
 * Optional Redis (REDIS_URL). Fail-open: if Redis is down, limits are skipped.
 */
const REDIS_URL = String(process.env.REDIS_URL || "").trim();

let client = null;
let ready = false;
let connectAttempted = false;

function getClient() {
  if (!REDIS_URL) return null;
  if (client) return client;
  if (connectAttempted) return client;
  connectAttempted = true;
  try {
    // redis v4
    const { createClient } = require("redis");
    client = createClient({ url: REDIS_URL });
    client.on("error", (err) => {
      ready = false;
      console.warn("[redis]", err?.message || err);
    });
    client.on("ready", () => {
      ready = true;
    });
    client.connect().catch((err) => {
      ready = false;
      console.warn("[redis] connect failed", err?.message || err);
    });
  } catch (err) {
    console.warn("[redis] unavailable", err?.message || err);
    client = null;
  }
  return client;
}

/** @returns {Promise<boolean>} true if allowed */
async function allow(key, max, windowSec) {
  const c = getClient();
  if (!c || !ready) return true;
  try {
    const k = `rl:${key}`;
    const n = await c.incr(k);
    if (n === 1) await c.expire(k, Math.max(1, windowSec | 0));
    return n <= max;
  } catch (err) {
    console.warn("[redis] allow", err?.message || err);
    return true;
  }
}

function ping() {
  const c = getClient();
  if (!c) return Promise.resolve("NO_REDIS");
  if (!ready) return Promise.resolve("CONNECTING");
  return c.ping().catch(() => "ERR");
}

module.exports = { getClient, allow, ping, REDIS_URL };

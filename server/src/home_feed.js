/**
 * Schmale Home-Benachrichtigungen (öffentlicher Feed, ~1h TTL).
 */

const FEED_MAX = 50;
const DEFAULT_TTL_MS = 60 * 60 * 1000;

function ensureFeed(db) {
  if (!db.homeFeed || !Array.isArray(db.homeFeed)) db.homeFeed = [];
  return db.homeFeed;
}

function pruneFeed(db, now = Date.now()) {
  const feed = ensureFeed(db);
  db.homeFeed = feed.filter((n) => Number(n?.expiresAt) > now);
  if (db.homeFeed.length > FEED_MAX) {
    db.homeFeed = db.homeFeed.slice(-FEED_MAX);
  }
  return db.homeFeed;
}

/**
 * @param {object} db
 * @param {{
 *   kind: string,
 *   shortText: string,
 *   title?: string,
 *   body?: string,
 *   ttlMs?: number,
 *   actionType?: string|null,
 *   actionPayload?: object|null,
 *   id?: string,
 * }} item
 */
function publish(db, item) {
  if (!db || !item) return null;
  pruneFeed(db);
  const now = Date.now();
  const id =
    String(item.id || "").trim() ||
    `hf_${now.toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  const ttl = Math.max(60_000, Number(item.ttlMs) || DEFAULT_TTL_MS);
  const row = {
    id,
    kind: String(item.kind || "info").slice(0, 32),
    shortText: String(item.shortText || "").trim().slice(0, 90),
    title: String(item.title || item.shortText || "").trim().slice(0, 90),
    body: String(item.body || "").trim().slice(0, 600),
    createdAt: now,
    expiresAt: now + ttl,
    actionType: item.actionType ? String(item.actionType).slice(0, 40) : null,
    actionPayload:
      item.actionPayload && typeof item.actionPayload === "object"
        ? item.actionPayload
        : null,
  };
  if (!row.shortText) return null;
  ensureFeed(db).push(row);
  if (db.homeFeed.length > FEED_MAX) db.homeFeed = db.homeFeed.slice(-FEED_MAX);
  try {
    const accountPush = require("./account_push");
    accountPush.broadcastEvent("home_feed", {
      id: row.id,
      shortText: row.shortText,
      kind: row.kind,
    });
  } catch {
    /* optional */
  }
  return row;
}

function listPublic(db, now = Date.now()) {
  return pruneFeed(db, now)
    .filter((n) => Number(n.expiresAt) > now)
    .filter((n) => {
      const k = String(n.kind || "");
      // Keine Verlobung / Allerwelts-Käufe in der Home-Kachel
      if (k === "engagement" || k === "market_sale") return false;
      return true;
    })
    .sort((a, b) => (Number(b.createdAt) || 0) - (Number(a.createdAt) || 0))
    .map((n) => ({
      id: n.id,
      kind: n.kind,
      shortText: n.shortText,
      title: n.title,
      body: n.body,
      createdAt: n.createdAt,
      expiresAt: n.expiresAt,
      actionType: n.actionType || null,
      actionPayload: n.actionPayload || null,
    }));
}

module.exports = {
  DEFAULT_TTL_MS,
  FEED_MAX,
  ensureFeed,
  pruneFeed,
  publish,
  listPublic,
};

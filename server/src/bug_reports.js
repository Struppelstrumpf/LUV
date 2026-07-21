/**
 * Bug-Meldungen aus der App — Admin markiert hilfreich → User claimt 10 Coins (server-validiert).
 */
const REWARD_COINS = 10;
const MAX_OPEN_PER_USER = 3;
const COOLDOWN_MS = 90_000;
const DESC_MAX = 1200;
const URL_MAX = 500;
const OTHER_MAX = 80;

const LOCATIONS = new Set([
  "home",
  "inventar",
  "markt",
  "sozial",
  "canvas",
  "shop",
  "account",
  "other",
]);

const VISIBILITY = new Set(["self", "others", "both"]);

function ensureStore(db) {
  if (!db.bugReports || typeof db.bugReports !== "object") {
    db.bugReports = {};
  }
  return db.bugReports;
}

function safeUrl(raw, { allowEmpty = true } = {}) {
  const s = String(raw || "").trim().slice(0, URL_MAX);
  if (!s) return allowEmpty ? "" : null;
  if (!/^https?:\/\//i.test(s)) return null;
  return s;
}

function locationLabel(loc, other) {
  const map = {
    home: "Hauptmenü",
    inventar: "Inventar",
    markt: "Markt",
    sozial: "Sozial",
    canvas: "Leinwand",
    shop: "Itemshop / Coinshop",
    account: "Konto / Einstellungen",
    other: "Sonstiges",
  };
  const base = map[loc] || loc || "—";
  if (loc === "other" && other) return `${base}: ${other}`;
  return base;
}

function visibilityLabel(v) {
  if (v === "self") return "Nur ich sehe den Bug";
  if (v === "others") return "Andere sehen den Bug";
  if (v === "both") return "Ich und andere";
  return v || "—";
}

function publicView(entry) {
  if (!entry) return null;
  return {
    id: entry.id,
    status: entry.status || "open",
    description: entry.description || "",
    imageUrl: entry.imageUrl || "",
    videoUrl: entry.videoUrl || "",
    reproducible: entry.reproducible === true,
    location: entry.location || "other",
    locationLabel: locationLabel(entry.location, entry.locationOther),
    locationOther: entry.locationOther || "",
    visibility: entry.visibility || "self",
    visibilityLabel: visibilityLabel(entry.visibility),
    nickname: entry.nickname || "Jemand",
    userId: entry.userId || null,
    createdAt: entry.createdAt || 0,
    helpfulAt: entry.helpfulAt || null,
    rewardPending: entry.status === "helpful" && !entry.rewardClaimedAt,
    rewardClaimedAt: entry.rewardClaimedAt || null,
    rewardCoins: REWARD_COINS,
  };
}

function createReport(db, user, body) {
  const description = String(body?.description || "")
    .trim()
    .replace(/\r\n/g, "\n")
    .slice(0, DESC_MAX);
  if (description.length < 10) {
    return {
      ok: false,
      status: 400,
      error: "empty",
      message: "Bitte beschreib den Bug etwas genauer (mind. 10 Zeichen).",
    };
  }

  const imageUrl = safeUrl(body?.imageUrl, { allowEmpty: true });
  if (imageUrl === null) {
    return {
      ok: false,
      status: 400,
      error: "bad_image_url",
      message: "Bild-Link muss mit https:// beginnen (Direktlink von z. B. postimages.org).",
    };
  }
  const videoUrl = safeUrl(body?.videoUrl, { allowEmpty: true });
  if (videoUrl === null) {
    return {
      ok: false,
      status: 400,
      error: "bad_video_url",
      message: "Video-Link muss mit https:// beginnen.",
    };
  }

  let location = String(body?.location || "other")
    .trim()
    .toLowerCase();
  if (!LOCATIONS.has(location)) location = "other";
  const locationOther =
    location === "other"
      ? String(body?.locationOther || "")
          .trim()
          .slice(0, OTHER_MAX)
      : "";
  if (location === "other" && locationOther.length < 2) {
    return {
      ok: false,
      status: 400,
      error: "bad_location",
      message: "Bei „Sonstiges“ bitte kurz angeben, wo der Bug war.",
    };
  }

  let visibility = String(body?.visibility || "self")
    .trim()
    .toLowerCase();
  if (!VISIBILITY.has(visibility)) visibility = "self";

  const store = ensureStore(db);
  const openMine = Object.values(store).filter(
    (r) =>
      (r.status || "open") === "open" &&
      r.userId === user.id
  );
  if (openMine.length >= MAX_OPEN_PER_USER) {
    return {
      ok: false,
      status: 429,
      error: "too_many",
      message: "Du hast schon offene Bug-Meldungen. Bitte warte auf Rückmeldung.",
    };
  }
  const newest = openMine.sort(
    (a, b) => Number(b.createdAt) - Number(a.createdAt)
  )[0];
  if (newest && Date.now() - Number(newest.createdAt || 0) < COOLDOWN_MS) {
    return {
      ok: false,
      status: 429,
      error: "cooldown",
      message: "Gleich nochmal — warte kurz zwischen Meldungen.",
    };
  }

  const id = `bug_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  const entry = {
    id,
    description,
    imageUrl,
    videoUrl,
    reproducible: body?.reproducible === true || body?.reproducible === "true",
    location,
    locationOther,
    visibility,
    userId: user.id,
    nickname: user.nickname || "Jemand",
    createdAt: Date.now(),
    status: "open",
    helpfulAt: null,
    helpfulBy: null,
    rewardClaimedAt: null,
    resolvedAt: null,
    resolvedBy: null,
  };
  store[id] = entry;
  return { ok: true, report: publicView(entry) };
}

function listOpenForAdmin(db) {
  const store = ensureStore(db);
  return Object.values(store)
    .filter((r) => {
      const st = r.status || "open";
      return st === "open" || st === "helpful";
    })
    .sort((a, b) => Number(b.createdAt) - Number(a.createdAt))
    .slice(0, 120)
    .map(publicView);
}

function markHelpful(db, id, staffUserId) {
  const store = ensureStore(db);
  const entry = store[String(id || "")];
  if (!entry) return { ok: false, status: 404, error: "not_found" };
  const st = entry.status || "open";
  if (st === "deleted") {
    return { ok: false, status: 400, error: "deleted", message: "Bereits gelöscht." };
  }
  if (st === "claimed") {
    return { ok: false, status: 400, error: "claimed", message: "Belohnung schon abgeholt." };
  }
  if (st === "helpful") {
    return { ok: true, already: true, report: publicView(entry) };
  }
  entry.status = "helpful";
  entry.helpfulAt = Date.now();
  entry.helpfulBy = staffUserId || null;
  return { ok: true, report: publicView(entry) };
}

function deleteReport(db, id, staffUserId) {
  const store = ensureStore(db);
  const entry = store[String(id || "")];
  if (!entry) return { ok: false, status: 404, error: "not_found" };
  if ((entry.status || "open") === "helpful" && !entry.rewardClaimedAt) {
    // Hilfreich aber noch nicht geclaimt — Löschen verhindert Claim
  }
  entry.status = "deleted";
  entry.resolvedAt = Date.now();
  entry.resolvedBy = staffUserId || null;
  return { ok: true, report: publicView(entry) };
}

function pendingRewardCount(db, userId) {
  const store = ensureStore(db);
  return Object.values(store).filter(
    (r) =>
      r.userId === userId &&
      r.status === "helpful" &&
      !r.rewardClaimedAt
  ).length;
}

function pendingRewardReports(db, userId) {
  const store = ensureStore(db);
  return Object.values(store)
    .filter(
      (r) =>
        r.userId === userId &&
        r.status === "helpful" &&
        !r.rewardClaimedAt
    )
    .sort((a, b) => Number(b.helpfulAt || b.createdAt) - Number(a.helpfulAt || a.createdAt))
    .map(publicView);
}

/**
 * Claim one or all pending helpful bug rewards. Server authenticates status.
 * @param {function} applyLedger (userId, delta, reason, refId) => user|null
 */
function claimRewards(db, user, applyLedger, { reportId = null } = {}) {
  const store = ensureStore(db);
  const uid = user.id;
  let targets = Object.values(store).filter(
    (r) =>
      r.userId === uid &&
      r.status === "helpful" &&
      !r.rewardClaimedAt
  );
  if (reportId) {
    targets = targets.filter((r) => r.id === String(reportId));
  }
  if (!targets.length) {
    return {
      ok: true,
      claimed: 0,
      coins: 0,
      pending: pendingRewardCount(db, uid),
    };
  }
  let claimed = 0;
  let coins = 0;
  for (const entry of targets) {
    // Doppel-Claim-Schutz: Status atomar prüfen
    if (entry.status !== "helpful" || entry.rewardClaimedAt) continue;
    const u = applyLedger(uid, REWARD_COINS, "bug_report_reward", entry.id);
    if (!u) continue;
    entry.status = "claimed";
    entry.rewardClaimedAt = Date.now();
    claimed += 1;
    coins += REWARD_COINS;
  }
  return {
    ok: true,
    claimed,
    coins,
    pending: pendingRewardCount(db, uid),
  };
}

function openCount(db) {
  return Object.values(ensureStore(db)).filter((r) => (r.status || "open") === "open")
    .length;
}

module.exports = {
  REWARD_COINS,
  ensureStore,
  publicView,
  createReport,
  listOpenForAdmin,
  markHelpful,
  deleteReport,
  pendingRewardCount,
  pendingRewardReports,
  claimRewards,
  openCount,
  locationLabel,
  visibilityLabel,
};

/**
 * Shop-Änderungs-Warteschlange: Admin-Edits optional bis zur Nachtwartung (≈03:00 Berlin).
 */
const MAX_PENDING = 200;
const MAX_HISTORY = 40;

function ensureQueue(db) {
  if (!db.shopChangeQueue || typeof db.shopChangeQueue !== "object") {
    db.shopChangeQueue = { jobs: [] };
  }
  if (!Array.isArray(db.shopChangeQueue.jobs)) db.shopChangeQueue.jobs = [];
  return db.shopChangeQueue;
}

function newId() {
  return `scq_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function enqueue(db, { action, payload, byUserId = null, label = "" } = {}) {
  const q = ensureQueue(db);
  const job = {
    id: newId(),
    createdAt: Date.now(),
    byUserId: byUserId || null,
    action: String(action || "").trim(),
    payload: payload && typeof payload === "object" ? payload : {},
    label: String(label || action || "Änderung").trim().slice(0, 140),
    status: "pending",
    finishedAt: null,
    error: null,
  };
  if (!job.action) {
    return { ok: false, error: "bad_action", message: "Keine Aktion." };
  }
  const pendingCount = q.jobs.filter((j) => j.status === "pending").length;
  if (pendingCount >= MAX_PENDING) {
    return {
      ok: false,
      error: "queue_full",
      message: `Warteschlange voll (max. ${MAX_PENDING} offene Jobs). Stornieren oder „Jetzt ausführen“.`,
    };
  }
  q.jobs.push(job);
  return { ok: true, job };
}

function listJobs(db, { includeDone = false } = {}) {
  const q = ensureQueue(db);
  const jobs = q.jobs.slice().sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  if (includeDone) return jobs.slice(0, 80);
  return jobs.filter((j) => j.status === "pending");
}

function cancelJob(db, id) {
  const q = ensureQueue(db);
  const job = q.jobs.find((j) => j.id === String(id || ""));
  if (!job) return { ok: false, error: "not_found" };
  if (job.status !== "pending") {
    return { ok: false, error: "not_pending", message: "Nur offene Jobs stornierbar." };
  }
  job.status = "cancelled";
  job.finishedAt = Date.now();
  return { ok: true, job };
}

function pruneHistory(db) {
  const q = ensureQueue(db);
  const pending = q.jobs.filter((j) => j.status === "pending");
  const done = q.jobs
    .filter((j) => j.status !== "pending")
    .sort((a, b) => (b.finishedAt || b.createdAt || 0) - (a.finishedAt || a.createdAt || 0))
    .slice(0, MAX_HISTORY);
  q.jobs = [...pending, ...done];
}

/**
 * @param {object} db
 * @param {Record<string, function>} handlers action -> (payload, ctx) => result
 */
function processPending(db, handlers, { byUserId = null } = {}) {
  const q = ensureQueue(db);
  const results = [];
  for (const job of q.jobs) {
    if (job.status !== "pending") continue;
    const fn = handlers && handlers[job.action];
    if (typeof fn !== "function") {
      job.status = "error";
      job.finishedAt = Date.now();
      job.error = `Unbekannte Aktion: ${job.action}`;
      results.push({ id: job.id, ok: false, action: job.action, error: job.error });
      continue;
    }
    try {
      const out = fn(job.payload || {}, {
        byUserId: job.byUserId || byUserId,
        jobId: job.id,
      });
      if (out && out.ok === false) {
        job.status = "error";
        job.finishedAt = Date.now();
        job.error = out.message || out.error || "failed";
        results.push({ id: job.id, ok: false, action: job.action, error: job.error });
      } else {
        job.status = "done";
        job.finishedAt = Date.now();
        job.error = null;
        results.push({ id: job.id, ok: true, action: job.action });
      }
    } catch (e) {
      job.status = "error";
      job.finishedAt = Date.now();
      job.error = e?.message || String(e);
      results.push({ id: job.id, ok: false, action: job.action, error: job.error });
    }
  }
  pruneHistory(db);
  return {
    ok: true,
    processed: results.length,
    okCount: results.filter((r) => r.ok).length,
    failCount: results.filter((r) => !r.ok).length,
    results,
  };
}

function parseSchedule(body, query) {
  const raw = String(body?.schedule ?? query?.schedule ?? "now")
    .trim()
    .toLowerCase();
  if (
    raw === "maintenance" ||
    raw === "queue" ||
    raw === "wartung" ||
    raw === "warteschlange" ||
    raw === "later"
  ) {
    return "maintenance";
  }
  return "now";
}

module.exports = {
  ensureQueue,
  enqueue,
  listJobs,
  cancelJob,
  processPending,
  parseSchedule,
  pruneHistory,
};

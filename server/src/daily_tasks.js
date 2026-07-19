/**
 * Admin-geplante Tagesaufgaben: Vorlagen, Plan (+ Kacheln), Belohnung.
 */

const DEFAULT_REWARD_COINS = 3;
const REWARD_MIN = 0;
const REWARD_MAX = 100;
const PLAN_MAX = 12;

/** Builtin-Vorlagen mit klarer Anleitung für Spieler. */
const BUILTIN_TEMPLATES = [
  {
    id: "d_kraul",
    title: "Begleiter kraulen",
    hint: "Öffne dein Profil und tippe mehrmals auf deinen Begleiter (das Tier oben).",
    metric: "krauls",
    target: 1,
  },
  {
    id: "d_draw",
    title: "Einmal malen",
    hint: "Öffne eine Lobby oder deine Leinwand und zeichne mindestens einen Strich.",
    metric: "draw_sessions",
    target: 1,
  },
  {
    id: "d_stroke",
    title: "10 Striche zeichnen",
    hint: "Zeichne auf einer Leinwand insgesamt 10 Striche (Finger/Stift loslassen zählt).",
    metric: "strokes",
    target: 10,
  },
  {
    id: "d_friend",
    title: "Freundesprofil ansehen",
    hint: "Geh zu Sozial → Freunde und öffne das Profil von jemandem.",
    metric: "profile_views",
    target: 1,
  },
  {
    id: "d_lobby",
    title: "Lobby öffnen",
    hint: "Öffne eine bestehende Lobby oder erstelle kurz eine neue.",
    metric: "lobby_opens",
    target: 1,
  },
  {
    id: "d_moment",
    title: "Moment speichern",
    hint: "Auf der Leinwand einen Moment/Screenshot speichern (Moment-Button).",
    metric: "moments_saved",
    target: 1,
  },
  {
    id: "d_react",
    title: "Reaktion senden",
    hint: "Sende in einer Lobby eine Reaktion (Emoji in der Leiste unten).",
    metric: "reactions_sent",
    target: 1,
  },
  {
    id: "d_gallery",
    title: "Galerie öffnen",
    hint: "Öffne die Galerie (gespeicherte Bilder/Momente ansehen).",
    metric: "gallery_opens",
    target: 1,
  },
  {
    id: "d_market",
    title: "Marktplatz besuchen",
    hint: "Öffne den Marktplatz (Handel mit anderen Spielern).",
    metric: "market_opens",
    target: 1,
  },
  {
    id: "d_social",
    title: "Sozial öffnen",
    hint: "Öffne den Bereich Sozial (Freunde / Erfolge).",
    metric: "social_opens",
    target: 1,
  },
  {
    id: "d_equip",
    title: "Begleiter wechseln",
    hint: "Im Inventar oder Profil einen anderen Begleiter auswählen und ausrüsten.",
    metric: "pet_equips",
    target: 1,
  },
  {
    id: "d_sticker",
    title: "Sticker platzieren",
    hint: "Im Profil gestalten einen Sticker aus dem Inventar auf die Leinwand setzen.",
    metric: "stickers_placed",
    target: 1,
  },
  {
    id: "d_template",
    title: "Vorlage platzieren",
    hint: "Auf einer Leinwand eine Vorlage auswählen und platzieren.",
    metric: "templates_placed",
    target: 1,
  },
];

const METRIC_SET = new Set(BUILTIN_TEMPLATES.map((t) => t.metric));

function ensureDailyTasksConfig(db) {
  if (!db || typeof db !== "object") {
    return defaultConfig();
  }
  if (!db.dailyTasksConfig || typeof db.dailyTasksConfig !== "object") {
    db.dailyTasksConfig = defaultConfig();
  }
  const cfg = db.dailyTasksConfig;
  if (!Array.isArray(cfg.templates) || !cfg.templates.length) {
    cfg.templates = BUILTIN_TEMPLATES.map((t) => ({ ...t, enabled: true }));
  } else {
    // Fehlende Builtins nachziehen (ohne bestehende Overrides zu überschreiben)
    const byId = new Map(cfg.templates.map((t) => [t.id, t]));
    for (const b of BUILTIN_TEMPLATES) {
      if (!byId.has(b.id)) {
        cfg.templates.push({ ...b, enabled: true });
      } else {
        const cur = byId.get(b.id);
        if (!cur.hint) cur.hint = b.hint;
        if (!cur.title) cur.title = b.title;
        if (!cur.metric) cur.metric = b.metric;
        if (!cur.target) cur.target = b.target;
      }
    }
  }
  if (!Array.isArray(cfg.plan)) cfg.plan = [];
  if (cfg.rewardCoins === undefined || cfg.rewardCoins === null) {
    cfg.rewardCoins = DEFAULT_REWARD_COINS;
  }
  if (cfg.tasksPerDay === undefined || cfg.tasksPerDay === null) {
    cfg.tasksPerDay = 4;
  }
  // mode: "plan" = genau die geplanten Kacheln; "random" = Zufall aus aktivem Pool
  if (cfg.mode !== "random" && cfg.mode !== "plan") {
    cfg.mode = cfg.plan.length > 0 ? "plan" : "random";
  }
  return cfg;
}

function defaultConfig() {
  return {
    rewardCoins: DEFAULT_REWARD_COINS,
    tasksPerDay: 4,
    mode: "random",
    plan: [],
    templates: BUILTIN_TEMPLATES.map((t) => ({ ...t, enabled: true })),
    updatedAt: null,
  };
}

function clampReward(n) {
  const v = Math.floor(Number(n));
  if (!Number.isFinite(v)) return DEFAULT_REWARD_COINS;
  return Math.max(REWARD_MIN, Math.min(REWARD_MAX, v));
}

function clampTasksPerDay(n) {
  const v = Math.floor(Number(n));
  if (!Number.isFinite(v)) return 4;
  return Math.max(1, Math.min(PLAN_MAX, v));
}

function publicTemplate(t) {
  if (!t) return null;
  return {
    id: String(t.id || ""),
    title: String(t.title || "").slice(0, 60),
    hint: String(t.hint || "").slice(0, 200),
    metric: String(t.metric || ""),
    target: Math.max(1, Math.min(999, Math.floor(Number(t.target) || 1))),
    enabled: t.enabled !== false,
  };
}

function getRewardCoins(db) {
  return clampReward(ensureDailyTasksConfig(db).rewardCoins);
}

function hashDay(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h;
}

function resolveTemplate(cfg, id) {
  const want = String(id || "").trim();
  const t = (cfg.templates || []).find((x) => x && x.id === want);
  return t ? publicTemplate(t) : null;
}

/**
 * Baut die Tagesaufgaben für einen Berlin-Tag.
 * plan-Modus: alle geplanten Kacheln (gleiche für alle Nutzer).
 * random: seeded Fisher–Yates aus aktivem Pool.
 */
function pickDailyTasks(db, dayKey) {
  const cfg = ensureDailyTasksConfig(db);
  const makeTask = (tpl, targetOverride) => ({
    id: tpl.id,
    title: tpl.title,
    hint: tpl.hint || "",
    metric: tpl.metric,
    target: Math.max(1, Math.floor(Number(targetOverride ?? tpl.target) || 1)),
    progress: 0,
    done: false,
  });

  if (cfg.mode === "plan" && Array.isArray(cfg.plan) && cfg.plan.length > 0) {
    const tasks = [];
    for (const slot of cfg.plan.slice(0, PLAN_MAX)) {
      const tid = typeof slot === "string" ? slot : slot?.templateId || slot?.id;
      const tpl = resolveTemplate(cfg, tid);
      if (!tpl || !tpl.enabled) continue;
      const target =
        typeof slot === "object" && slot?.target != null ? slot.target : tpl.target;
      // Keine Duplikate derselben Vorlage am selben Tag
      if (tasks.some((t) => t.id === tpl.id)) continue;
      tasks.push(makeTask(tpl, target));
    }
    if (tasks.length) return tasks;
  }

  const pool = (cfg.templates || [])
    .map(publicTemplate)
    .filter((t) => t && t.enabled && METRIC_SET.has(t.metric));
  if (!pool.length) {
    return BUILTIN_TEMPLATES.slice(0, 4).map((t) => makeTask(publicTemplate({ ...t, enabled: true })));
  }
  const seed = hashDay(`luv-daily-${dayKey}`);
  const shuffled = [...pool];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = (seed + i * 17) % (i + 1);
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  const n = Math.min(clampTasksPerDay(cfg.tasksPerDay), shuffled.length);
  return shuffled.slice(0, n).map((t) => makeTask(t));
}

function publicConfig(db) {
  const cfg = ensureDailyTasksConfig(db);
  return {
    rewardCoins: clampReward(cfg.rewardCoins),
    tasksPerDay: clampTasksPerDay(cfg.tasksPerDay),
    mode: cfg.mode === "plan" ? "plan" : "random",
    plan: (cfg.plan || []).map((slot) => {
      const tid = typeof slot === "string" ? slot : slot?.templateId || slot?.id;
      const tpl = resolveTemplate(cfg, tid);
      return {
        templateId: tid,
        target:
          typeof slot === "object" && slot?.target != null
            ? Math.max(1, Math.floor(Number(slot.target) || 1))
            : tpl?.target || 1,
        title: tpl?.title || tid,
        hint: tpl?.hint || "",
        metric: tpl?.metric || "",
        enabled: tpl ? tpl.enabled : false,
      };
    }),
    templates: (cfg.templates || []).map(publicTemplate).filter(Boolean),
    updatedAt: cfg.updatedAt || null,
    limits: {
      rewardMin: REWARD_MIN,
      rewardMax: REWARD_MAX,
      planMax: PLAN_MAX,
    },
  };
}

function setConfig(db, patch = {}) {
  const cfg = ensureDailyTasksConfig(db);
  if (patch.rewardCoins !== undefined) {
    cfg.rewardCoins = clampReward(patch.rewardCoins);
  }
  if (patch.tasksPerDay !== undefined) {
    cfg.tasksPerDay = clampTasksPerDay(patch.tasksPerDay);
  }
  if (patch.mode === "plan" || patch.mode === "random") {
    cfg.mode = patch.mode;
  }
  if (Array.isArray(patch.plan)) {
    const next = [];
    for (const slot of patch.plan.slice(0, PLAN_MAX)) {
      const tid = String(
        typeof slot === "string" ? slot : slot?.templateId || slot?.id || ""
      ).trim();
      if (!tid) continue;
      const tpl = resolveTemplate(cfg, tid);
      if (!tpl) continue;
      if (next.some((s) => s.templateId === tid)) continue;
      const target =
        typeof slot === "object" && slot?.target != null
          ? Math.max(1, Math.min(999, Math.floor(Number(slot.target) || tpl.target)))
          : tpl.target;
      next.push({ templateId: tid, target });
    }
    cfg.plan = next;
    if (next.length && patch.mode === undefined) cfg.mode = "plan";
  }
  if (Array.isArray(patch.templates)) {
    for (const raw of patch.templates) {
      const id = String(raw?.id || "").trim();
      if (!id) continue;
      const idx = cfg.templates.findIndex((t) => t && t.id === id);
      if (idx < 0) continue;
      const prev = cfg.templates[idx];
      cfg.templates[idx] = {
        ...prev,
        title: String(raw.title ?? prev.title).trim().slice(0, 60) || prev.title,
        hint: String(raw.hint ?? prev.hint ?? "").trim().slice(0, 200),
        target: Math.max(1, Math.min(999, Math.floor(Number(raw.target ?? prev.target) || 1))),
        enabled: raw.enabled === undefined ? prev.enabled !== false : Boolean(raw.enabled),
      };
    }
  }
  cfg.updatedAt = Date.now();
  return { ok: true, config: publicConfig(db) };
}

function setTemplateEnabled(db, id, enabled) {
  const cfg = ensureDailyTasksConfig(db);
  const want = String(id || "").trim();
  const t = cfg.templates.find((x) => x && x.id === want);
  if (!t) return { ok: false, error: "not_found", message: "Vorlage nicht gefunden." };
  t.enabled = Boolean(enabled);
  cfg.updatedAt = Date.now();
  return { ok: true, template: publicTemplate(t), config: publicConfig(db) };
}

module.exports = {
  BUILTIN_TEMPLATES,
  DEFAULT_REWARD_COINS,
  ensureDailyTasksConfig,
  getRewardCoins,
  pickDailyTasks,
  publicConfig,
  setConfig,
  setTemplateEnabled,
  METRIC_SET,
};

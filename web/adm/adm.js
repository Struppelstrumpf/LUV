(() => {
  const API = "/luv/v1";
  const TOKEN_KEY = "luv_adm_token";
  const USER_KEY = "luv_adm_user";
  const TICKET_KEY = "luv_adm_web_ticket";

  const state = {
    token: localStorage.getItem(TOKEN_KEY) || "",
    user: null,
    webAuthTicket: sessionStorage.getItem(TICKET_KEY) || "",
    decoy: false,
    tab: "overview",
    shopItems: [],
    shopKind: "",
    shopQ: "",
    shopSource: "",
    shopUniverse: [],
    shopView: "katalog", // katalog | events | kalender | queue
    shopSchedule: "now", // now | maintenance
    calOpenInv: null,
    calKind: "",
    calQ: "",
    calStatus: "",
    calMark: "",
    calOpenKey: "",
    calYear: new Date().getFullYear(),
    calMonth: new Date().getMonth() + 1,
    calDay: "",
    calTab: "month", // month | plans | items
    calPick: {}, // itemKey -> true for batch
    achQ: "",
    achCat: "",
    achFocusId: null,
    userQ: "",
    userFocusId: null,
    phrasePool: "",
    phraseQ: "",
    phraseSelected: {},
  };

  try {
    state.user = JSON.parse(localStorage.getItem(USER_KEY) || "null");
  } catch {
    state.user = null;
  }
  try {
    const sched = localStorage.getItem("luv_adm_shop_schedule");
    if (sched === "maintenance" || sched === "now") state.shopSchedule = sched;
  } catch {
    /* ignore */
  }

  const $ = (id) => document.getElementById(id);
  const loginView = $("loginView");
  const appView = $("appView");
  const content = $("content");
  const nav = $("nav");
  const pageTitle = $("pageTitle");
  const pageHint = $("pageHint");
  const modal = $("modal");
  const modalCard = $("modalCard");

  const TABS = [
    { id: "overview", label: "Übersicht", hint: "Live-Zahlen und Räume auf einen Blick." },
    { id: "shop", label: "Itemshop", hint: "Katalog & Kalender: Preise, Zeitfenster, Käufe und Marktplatz-Stats.", perm: "market.settings" },
    { id: "events", label: "Events", hint: "Jahreskalender: wiederkehrende Events, Belohnungen und App-Schmuck.", perm: "market.settings" },
    { id: "achievements", label: "Erfolge", hint: "Erfolge ansehen, deaktivieren und mit Wizard erstellen/bearbeiten.", perm: "market.settings" },
    { id: "dailies", label: "Tagesaufgaben", hint: "Planer: welche Aufgaben, wie viele, Coin-Belohnung und Anleitungen.", perm: "market.settings" },
    { id: "phrases", label: "Sprüche", hint: "Push- und Share-Sprüche bearbeiten, Tap-Ziel wählen. Mehrfachauswahl möglich.", perm: "live.notify" },
    { id: "reports", label: "Meldungen", hint: "Bugs, Hilfe, Lobby- und Galerie-Meldungen prüfen.", perm: "reports.view" },
    { id: "codes", label: "Codes", hint: "Gutscheincodes erstellen und widerrufen.", perm: "codes.view" },
    { id: "users", label: "Nutzer", hint: "Vollprofil: Coins, Erfolge, Logs, Lobbys, Verwarnungen, Streak.", perm: "gm.search" },
    { id: "mods", label: "Moderatoren", hint: "Mods einladen und Rechte setzen.", perm: "mods.manage", adminOnly: true },
    { id: "bericht", label: "Bericht", hint: "Nächtliche Wartungsberichte: Backup, Shop-Zyklus, Health — kopieren für die KI.", perm: "market.settings" },
    { id: "market", label: "Einstellungen", hint: "Markt-Preisfenster und Erfolgs-Tageslimit.", perm: "market.settings" },
    { id: "live", label: "Live-Hinweis", hint: "Nachricht an alle App-Nutzer senden.", perm: "live.notify" },
  ];

  function isStaff(u) {
    return u && (u.role === "admin" || u.role === "mod");
  }
  function isAdmin(u) {
    return u && u.role === "admin";
  }
  function isDecoy(u) {
    return Boolean(u && (u.decoy === true || state.decoy));
  }
  function hasPerm(id) {
    if (!state.user) return false;
    if (isDecoy(state.user)) return id === "gm.search";
    if (isAdmin(state.user)) return true;
    return Boolean(state.user.permissions && state.user.permissions[id]);
  }

  function setWebTicket(ticket) {
  const t = String(ticket || "");
  if (t && !/^[a-f0-9]{64}$/.test(t)) {
    state.webAuthTicket = "";
    sessionStorage.removeItem(TICKET_KEY);
    return;
  }
  state.webAuthTicket = t;
  if (t) sessionStorage.setItem(TICKET_KEY, t);
  else sessionStorage.removeItem(TICKET_KEY);
}

  async function api(path, opts = {}) {
    const headers = Object.assign({ "Content-Type": "application/json" }, opts.headers || {});
    if (state.token) headers.Authorization = `Bearer ${state.token}`;
    const res = await fetch(API + path, { ...opts, headers });
    const text = await res.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    if (!res.ok) {
      const msg = (json && (json.message || json.error)) || `Fehler ${res.status}`;
      throw new Error(msg);
    }
    return json;
  }

  function setShopSchedule(mode) {
    state.shopSchedule = mode === "maintenance" ? "maintenance" : "now";
    try {
      localStorage.setItem("luv_adm_shop_schedule", state.shopSchedule);
    } catch {
      /* ignore */
    }
  }

  function withSchedule(obj = {}) {
    return { ...obj, schedule: state.shopSchedule === "maintenance" ? "maintenance" : "now" };
  }

  function scheduleBarHtml() {
    const m = state.shopSchedule === "maintenance";
    return `<div class="panel" style="margin:0.65rem 0 0;padding:0.6rem 0.8rem">
      <div style="display:flex;flex-wrap:wrap;align-items:center;gap:0.55rem;justify-content:space-between">
        <div style="min-width:12rem;flex:1">
          <strong>Änderungsmodus</strong>
          <p class="help" style="margin:0.15rem 0 0">
            Rotation nur ≈03:00 Berlin. Tagsüber keine Shop-Wechsel.
            ${m ? "Neue Edits landen in der Warteschlange." : "Edits gelten sofort."}
          </p>
        </div>
        <div class="shop-cats" id="shopScheduleMode" style="margin:0">
          <button type="button" class="shop-cat ${!m ? "on" : ""}" data-sched="now">Sofort</button>
          <button type="button" class="shop-cat ${m ? "on" : ""}" data-sched="maintenance">Warteschlange</button>
        </div>
      </div>
    </div>`;
  }

  function wireScheduleBar() {
    content.querySelectorAll("#shopScheduleMode [data-sched]").forEach((btn) => {
      btn.onclick = () => {
        setShopSchedule(btn.getAttribute("data-sched"));
        content.querySelectorAll("#shopScheduleMode [data-sched]").forEach((b) => {
          b.classList.toggle("on", b.getAttribute("data-sched") === state.shopSchedule);
        });
        const help = content.querySelector("#shopScheduleMode")?.closest(".panel")?.querySelector(".help");
        if (help) {
          help.textContent =
            "Rotation nur ≈03:00 Berlin. Tagsüber keine Shop-Wechsel. " +
            (state.shopSchedule === "maintenance"
              ? "Neue Edits landen in der Warteschlange."
              : "Edits gelten sofort.");
        }
      };
    });
  }

  function shopViewsHtml(active) {
    return `<div class="shop-cats" id="shopViews">
      <button type="button" class="shop-cat ${active === "katalog" ? "on" : ""}" data-shop-view="katalog">Katalog</button>
      <button type="button" class="shop-cat ${active === "events" ? "on" : ""}" data-shop-view="events">Event-Katalog</button>
      <button type="button" class="shop-cat ${active === "kalender" ? "on" : ""}" data-shop-view="kalender">Kalender</button>
      <button type="button" class="shop-cat ${active === "queue" ? "on" : ""}" data-shop-view="queue">Warteschlange</button>
    </div>`;
  }

  function noteQueued(res) {
    if (res?.queued) {
      alert(res.message || "In Warteschlange für nächste Wartung (≈03:00 Berlin) gelegt.");
      return true;
    }
    if (res?.note) alert(res.note);
    return false;
  }

  function setSession(token, user) {
    state.token = token || "";
    state.user = user;
    state.decoy = Boolean(user && user.decoy);
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
  }

  function showCodeGate() {
    const codeGate = $("codeGate");
    const googleGate = $("googleGate");
    if (codeGate) codeGate.hidden = false;
    if (googleGate) googleGate.hidden = true;
  }

  function showGoogleGate() {
    const codeGate = $("codeGate");
    const googleGate = $("googleGate");
    if (codeGate) codeGate.hidden = true;
    if (googleGate) googleGate.hidden = false;
  }

  function showLogin(err) {
    loginView.hidden = false;
    appView.hidden = true;
    if (state.webAuthTicket) showGoogleGate();
    else showCodeGate();
    const el = $("loginError");
    const ge = $("googleError");
    if (el) {
      if (err) {
        el.hidden = false;
        el.textContent = err;
      } else {
        el.hidden = true;
      }
    }
    if (ge && !err) ge.hidden = true;
  }

  function showApp() {
    loginView.hidden = true;
    appView.hidden = false;
    $("userNick").textContent = state.user.nickname || "Staff";
    $("userRole").textContent =
      state.decoy || state.user.decoy
        ? "Moderator"
        : state.user.role === "admin"
          ? "Admin"
          : "Moderator";
    renderNav();
    loadTab(state.tab);
  }

  function showDecoyApp() {
    state.decoy = true;
    state.tab = "users";
    showApp();
  }

  function renderNav() {
    nav.innerHTML = "";
    const tabs = state.decoy || isDecoy(state.user)
      ? TABS.filter((t) => t.id === "users")
      : TABS;
    tabs.forEach((t) => {
      if (t.adminOnly && !isAdmin(state.user)) return;
      if (t.perm && !hasPerm(t.perm) && !isAdmin(state.user) && !state.decoy) return;
      const b = document.createElement("button");
      b.type = "button";
      b.textContent = t.label;
      b.className = state.tab === t.id ? "active" : "";
      b.onclick = () => {
        state.tab = t.id;
        renderNav();
        loadTab(t.id);
      };
      nav.appendChild(b);
    });
  }

  function openModal(html, wide) {
    modalCard.innerHTML = html;
    modalCard.classList.toggle("wide", Boolean(wide));
    modal.hidden = false;
    modal.classList.add("is-open");
  }
  function closeModal() {
    modal.hidden = true;
    modal.classList.remove("is-open");
    modalCard.classList.remove("wide");
    modalCard.innerHTML = "";
  }
  function studioOverlayOpen() {
    return Boolean(
      document.getElementById("glyphComposerLayer") ||
        document.getElementById("petPasteLayer") ||
        document.getElementById("emojiPickerLayer") ||
        document.getElementById("themeStudioLayer")
    );
  }
  /** Nach Composer-Übernehmen: Ghost-Clicks auf Abbrechen/Backdrop ignorieren */
  let modalUiGuardUntil = 0;
  document.addEventListener("luv-studio-dismiss", () => {
    modalUiGuardUntil = Date.now() + 500;
  });
  // Backdrop-Klick schließt NICHT — nur „Abbrechen“ / Speichern-Erfolg
  // Ghost-Click trifft oft genau den Abbrechen-Button unter „Übernehmen“
  modalCard.addEventListener(
    "click",
    (e) => {
      if (Date.now() >= modalUiGuardUntil) return;
      const t = e.target;
      if (!t) return;
      if (t.id === "cancelModal" || (t.closest && t.closest("#cancelModal"))) {
        e.preventDefault();
        e.stopPropagation();
      }
    },
    true
  );

  async function googleCredential(idToken) {
    if (!state.webAuthTicket) {
      showGoogleError("Bitte zuerst den Code aus der App eingeben.");
      showCodeGate();
      return;
    }
    const json = await api("/auth/google", {
      method: "POST",
      body: JSON.stringify({
        idToken,
        staffOnly: true,
        webAuthTicket: state.webAuthTicket,
      }),
    });
    const token = json.sessionToken || json.token;
    const user = json.user;
    if (!token || !user) throw new Error("Login unvollständig");
    setWebTicket("");
    setSession(token, user);
    if (json.decoy || user.decoy) {
      showDecoyApp();
      return;
    }
    if (!isStaff(user)) throw new Error("Kein Admin-/Mod-Zugang für dieses Konto.");
    showApp();
  }

  function showLoginError(msg) {
    const el = $("loginError");
    if (!el) return;
    el.hidden = false;
    el.textContent = msg;
  }

  function showGoogleError(msg) {
    const el = $("googleError") || $("loginError");
    if (!el) return;
    el.hidden = false;
    el.textContent = msg;
  }

  function renderGoogleButton(clientId) {
    const slot = $("googleBtn");
    slot.innerHTML = "";
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (resp) => {
        googleCredential(resp.credential).catch((e) => showGoogleError(e.message));
      },
      auto_select: false,
      cancel_on_tap_outside: true,
    });
    // Eigener Button — vermeidet leere GIS-Iframes/Overlays auf dunklem UI
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "btn google-login-btn";
    btn.textContent = "Mit Google anmelden";
    btn.onclick = () => {
      try {
        window.google.accounts.id.prompt((notification) => {
          if (
            notification.isNotDisplayed() ||
            notification.isSkippedMoment() ||
            notification.isDismissedMoment()
          ) {
            slot.querySelectorAll("iframe, div[id^='gsi']").forEach((n) => n.remove());
            const host = document.createElement("div");
            host.className = "google-official";
            slot.appendChild(host);
            window.google.accounts.id.renderButton(host, {
              theme: "filled_blue",
              size: "large",
              shape: "rectangular",
              text: "continue_with",
              width: Math.min(320, slot.clientWidth || 320),
            });
          }
        });
      } catch (e) {
        showLoginError(e.message || "Google Prompt fehlgeschlagen");
      }
    };
    slot.appendChild(btn);
  }

  function initGoogle() {
    api("/auth/config")
      .then((cfg) => {
        const clientId = cfg.googleWebClientId;
        if (!clientId) {
          showLoginError("Google-Login ist auf dem Server nicht konfiguriert.");
          return;
        }
        let tries = 0;
        const wait = setInterval(() => {
          tries += 1;
          if (window.google?.accounts?.id) {
            clearInterval(wait);
            renderGoogleButton(clientId);
            return;
          }
          if (tries >= 40) {
            clearInterval(wait);
            showLoginError(
              "Google-Skript nicht geladen (Netzwerk/Adblocker?). Seite neu laden oder Adblocker für reineke.pro erlauben."
            );
          }
        }, 250);
      })
      .catch(() => {
        showLoginError("API nicht erreichbar.");
      });
  }

  async function resume() {
    if (state.user && state.user.decoy) state.decoy = true;
    if (!state.token) {
      showLogin();
      if (state.webAuthTicket) initGoogle();
      wireAuthCodeUi();
      return;
    }
    try {
      const me = await api("/me");
      const user = me.user || me;
      if (user.decoy) {
        setSession(state.token, user);
        showDecoyApp();
        return;
      }
      if (!isStaff(user)) throw new Error("Kein Staff");
      setSession(state.token, user);
      showApp();
    } catch {
      try {
        await api("/auth/logout", { method: "POST", body: "{}" });
      } catch {
        /* ignore */
      }
      setSession("", null);
      showLogin();
      if (state.webAuthTicket) initGoogle();
      wireAuthCodeUi();
    }
  }

  function formatAuthCodeInput(raw) {
    // Nur A–Z / 0–9 — alles andere (JS, HTML, Unicode) wird verworfen
    const s = String(raw || "")
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, "")
      .slice(0, 7);
    if (s.length <= 2) return s;
    if (s.length <= 5) return `${s.slice(0, 2)}-${s.slice(2)}`;
    return `${s.slice(0, 2)}-${s.slice(2, 5)}-${s.slice(5)}`;
  }

  function isValidAuthCode(formatted) {
    return /^[A-Z0-9]{2}-[A-Z0-9]{3}-[A-Z0-9]{2}$/.test(String(formatted || ""));
  }

  function isValidWebTicket(ticket) {
    return /^[a-f0-9]{64}$/.test(String(ticket || ""));
  }

  function wireAuthCodeUi() {
    const input = $("authCodeInput");
    const submit = $("authCodeSubmit");
    const reset = $("authCodeReset");
    if (input && !input._luvWired) {
      input._luvWired = true;
      const sanitizeField = () => {
        const formatted = formatAuthCodeInput(input.value);
        if (input.value !== formatted) input.value = formatted;
      };
      input.addEventListener("beforeinput", (e) => {
        if (e.inputType === "insertFromPaste" || e.inputType === "insertText") {
          const data = String(e.data || "");
          if (data && /[^A-Za-z0-9\-]/.test(data)) {
            e.preventDefault();
            const next = formatAuthCodeInput((input.value || "") + data);
            input.value = next;
          }
        }
      });
      input.addEventListener("paste", (e) => {
        e.preventDefault();
        const text = (e.clipboardData || window.clipboardData)?.getData("text") || "";
        input.value = formatAuthCodeInput(text);
      });
      input.addEventListener("drop", (e) => e.preventDefault());
      input.addEventListener("input", sanitizeField);
      input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          submit?.click();
        }
      });
    }
    if (submit && !submit._luvWired) {
      submit._luvWired = true;
      submit.onclick = async () => {
        const code = formatAuthCodeInput(input?.value || "");
        if (!isValidAuthCode(code)) {
          showLoginError("Ungültiger Code.");
          return;
        }
        submit.disabled = true;
        try {
          const res = await api("/admin/web-auth/redeem", {
            method: "POST",
            body: JSON.stringify({ code }),
          });
          const ticket = String(res?.ticket || "");
          if (!isValidWebTicket(ticket)) throw new Error("Anmeldung fehlgeschlagen");
          setWebTicket(ticket);
          if (input) input.value = "";
          showGoogleGate();
          initGoogle();
        } catch (err) {
          // Keine Rohdaten aus der Eingabe in die UI spiegeln
          const msg = String(err?.message || "Code ungültig").slice(0, 120);
          showLoginError(/[<>&]/.test(msg) ? "Code ungültig" : msg);
        } finally {
          submit.disabled = false;
        }
      };
    }
    if (reset && !reset._luvWired) {
      reset._luvWired = true;
      reset.onclick = () => {
        setWebTicket("");
        if (input) input.value = "";
        showCodeGate();
      };
    }
  }

  function esc(s) {
    return String(s ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function fmtMs(ms) {
    if (ms == null || ms < 0) return "—";
    const m = Math.floor(ms / 60000);
    const d = Math.floor(m / (60 * 24));
    const h = Math.floor(m / 60) % 24;
    const min = m % 60;
    if (d >= 1) return `${d}d ${h}h`;
    if (h >= 1) return `${h}h ${min}m`;
    return `${Math.max(1, min)}m`;
  }

  function goTab(id, opts = {}) {
    if (opts.focusId && id === "achievements") state.achFocusId = opts.focusId;
    if (opts.userId && id === "users") state.userFocusId = opts.userId;
    state.tab = id;
    renderNav();
    loadTab(id);
  }

  async function loadTab(id) {
    if ((state.decoy || isDecoy(state.user)) && id !== "users") {
      id = "users";
      state.tab = "users";
    }
    const meta = TABS.find((t) => t.id === id) || TABS[0];
    pageTitle.textContent = meta.label;
    pageHint.textContent = meta.hint || "";
    content.innerHTML = `<p class="muted">Lade…</p>`;
    try {
      if (id === "overview") await renderOverview();
      else if (id === "shop") {
        if (state.shopView === "kalender") await renderShopCalendar();
        else if (state.shopView === "queue") await renderShopChangeQueue();
        else await renderShop();
      }
      else if (id === "events") await window.LuvAdmPanels.renderEvents();
      else if (id === "achievements") {
        const focus = state.achFocusId;
        state.achFocusId = null;
        await window.LuvAdmPanels.renderAchievements(focus);
      } else if (id === "dailies") await window.LuvAdmPanels.renderDailyTasks();
      else if (id === "phrases") await window.LuvAdmPanels.renderPhrases();
      else if (id === "reports") await renderReports();
      else if (id === "codes") await renderCodes();
      else if (id === "users") {
        if (state.decoy || isDecoy(state.user)) await renderDecoyUsers();
        else await window.LuvAdmPanels.renderUsers();
      }
      else if (id === "mods") await renderMods();
      else if (id === "bericht") await renderMaintenanceBericht();
      else if (id === "market") await renderMarketSettings();
      else if (id === "live") await renderLive();
    } catch (e) {
      content.innerHTML = `<p class="error">${esc(e.message)}</p>`;
    }
  }

  async function renderOverview() {
    const o = await api("/admin/overview");
    content.innerHTML = `
      <div class="cards">
        <div class="card"><div class="k">Nutzer</div><div class="v">${o.users ?? "—"}</div></div>
        <div class="card"><div class="k">Räume</div><div class="v">${o.rooms ?? "—"}</div></div>
        <div class="card"><div class="k">Meldungen</div><div class="v">${(o.openPublicReports || 0) + (o.openPeerReports || 0)}</div></div>
        <div class="card"><div class="k">Moderatoren</div><div class="v">${o.moderators ?? "—"}</div></div>
      </div>
      <div class="panel">
        <h3>Tipp</h3>
        <p class="help">Unter <strong>Itemshop</strong> kannst du Preise, zeitlich begrenzte Angebote und Kauf-Limits steuern. Abgelaufene Items werden deaktiviert — nicht gelöscht.</p>
      </div>`;
  }

  async function renderShop() {
    const eventMode = state.shopView === "events";
    const SHOP_CATS = [
      { id: "emojis", label: "Emojis", emoji: "😊", hint: "Reaktionen in der Leinwand" },
      { id: "stickers", label: "Sticker", emoji: "🏷️", hint: "Profil-Sticker" },
      { id: "themes", label: "Hintergründe", emoji: "🖼️", hint: "Profil-Hintergründe" },
      { id: "pets", label: "Begleiter", emoji: "🐾", hint: "Avatar-Begleiter" },
    ];
    const SOURCE_FILTERS = eventMode
      ? [
          { id: "", label: "Alle Quellen" },
          { id: "shop", label: "Itemshop" },
          { id: "event", label: "Event" },
        ]
      : [
          { id: "", label: "Alle Quellen" },
          { id: "shop", label: "Itemshop" },
          { id: "achievement", label: "Erfolg" },
          { id: "code", label: "Code" },
          { id: "starter", label: "Starter" },
          { id: "marriage", label: "Ehe" },
          { id: "tradeable", label: "Handelbar" },
          { id: "locked", label: "Gesperrt" },
        ];
    const SRC_LABEL = {
      shop: "Shop",
      achievement: "Erfolg",
      code: "Code",
      starter: "Starter",
      marriage: "Ehe",
      event: "Event",
    };

    const q = new URLSearchParams();
    if (state.shopQ) q.set("q", state.shopQ);
    if (state.shopKind) q.set("kind", state.shopKind);
    if (state.shopSource) q.set("source", state.shopSource);
    const shopQs = new URLSearchParams();
    if (state.shopQ) shopQs.set("q", state.shopQ);
    if (state.shopKind) shopQs.set("kind", state.shopKind);
    const [data, shopData] = await Promise.all([
      api("/admin/items/universe?" + q.toString()),
      api("/admin/shop/items?" + shopQs.toString()),
    ]);
    state.shopUniverse = data.items || [];
    state.shopItems = shopData.items || [];
    const shopByKey = Object.fromEntries(
      state.shopItems.map((it) => [`${it.kind}:${it.itemId}`, it])
    );
    const universeByKey = Object.fromEntries(
      state.shopUniverse.map((it) => [`${it.kind}:${it.itemId}`, it])
    );

    function isEventCatalogItem(it, shop) {
      if (shop?.eventId) return true;
      if ((it?.sources || []).includes("event")) return true;
      const id = String(it?.itemId || shop?.itemId || "");
      return /^img_ev_/i.test(id) || /^img_event_/i.test(id);
    }

    /** Anzuzeigende Items: normaler Katalog ohne Events, Event-Katalog nur Events. */
    let displayItems = [];
    if (eventMode) {
      const seen = new Set();
      for (const shop of state.shopItems) {
        if (!isEventCatalogItem({ itemId: shop.itemId, sources: [] }, shop)) continue;
        const key = `${shop.kind}:${shop.itemId}`;
        if (seen.has(key)) continue;
        if (state.shopKind && shop.kind !== state.shopKind) continue;
        seen.add(key);
        const uni = universeByKey[key];
        displayItems.push(
          uni || {
            kind: shop.kind,
            itemId: shop.itemId,
            label: shop.label || shop.itemId,
            emoji: shop.emoji || shop.previewEmoji || "🎁",
            priceCoins: shop.priceCoins,
            sources: ["event", "shop"],
            marketSellable: false,
            marketLocked: false,
            lootboxEligible: false,
          }
        );
      }
      for (const it of state.shopUniverse) {
        const shop = shopByKey[`${it.kind}:${it.itemId}`];
        if (!isEventCatalogItem(it, shop)) continue;
        const key = `${it.kind}:${it.itemId}`;
        if (seen.has(key)) continue;
        seen.add(key);
        displayItems.push(it);
      }
    } else {
      displayItems = state.shopUniverse.filter((it) => {
        const shop = shopByKey[`${it.kind}:${it.itemId}`];
        return !isEventCatalogItem(it, shop);
      });
    }

    const byKind = Object.fromEntries(SHOP_CATS.map((c) => [c.id, []]));
    for (const it of displayItems) {
      if (!byKind[it.kind]) byKind[it.kind] = [];
      byKind[it.kind].push(it);
    }

    const activeKind = state.shopKind || "";
    const visibleCats = activeKind
      ? SHOP_CATS.filter((c) => c.id === activeKind)
      : SHOP_CATS;

    function openNewEventChooser() {
      openModal(
        `
        <h3 style="margin:0 0 0.5rem;font-family:var(--display)">Neues Event-Item</h3>
        <p class="help" style="margin:0 0 0.75rem">Welche Art soll angelegt werden?</p>
        <div class="actions" style="flex-direction:column;align-items:stretch;gap:0.45rem">
          <button type="button" class="btn teal" data-ev-new="event_emojis">🎉 Event-Emoji</button>
          <button type="button" class="btn teal" data-ev-new="event_stickers">🎉 Event-Sticker</button>
          <button type="button" class="btn teal" data-ev-new="event_themes">🎉 Event-Hintergrund</button>
          <button type="button" class="btn teal" data-ev-new="event_pets">🎉 Event-Begleiter</button>
          <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
        </div>`
      );
      const cancel = $("cancelModal");
      if (cancel) cancel.onclick = closeModal;
      document.querySelectorAll("[data-ev-new]").forEach((btn) => {
        btn.onclick = () => {
          closeModal();
          openWizard(btn.getAttribute("data-ev-new"));
        };
      });
    }

    function sourceBadges(sources) {
      return (sources || [])
        .map((s) => `<span class="badge src-${esc(s)}">${esc(SRC_LABEL[s] || s)}</span>`)
        .join(" ");
    }

    function shopStatusBadges(shop, it) {
      if (!shop) {
        return `<span class="badge src-noshop">nicht im Shop-Katalog</span>`;
      }
      const bits = [];
      if (shop.eventId || (it.sources || []).includes("event")) {
        bits.push(
          `<span class="badge" title="Event-Item">🎉 Event${
            shop.eventId ? " · " + esc(shop.eventId) : ""
          }</span>`
        );
      }
      if (shop.rotationPlanId && !shop.rotationLocked) {
        bits.push(`<span class="badge badge-cycle" title="Im Rotationszyklus">↻ Zyklus</span>`);
      } else if (shop.rotationLocked) {
        bits.push(`<span class="badge" title="Nicht in Rotation">📌 Fix</span>`);
      }
      const now = Date.now();
      const from = shop.availableFrom || null;
      const until = shop.availableUntil || null;
      const enabled = shop.enabled !== false;
      const inWindow =
        enabled &&
        (!from || now >= from) &&
        (!until || now < until);
      if (!enabled) {
        bits.push(`<span class="badge off">Shop aus</span>`);
      } else if (inWindow) {
        bits.push(`<span class="badge badge-on">Im Shop aktiv</span>`);
        if (shop.remainingMs != null) {
          bits.push(
            `<span class="badge muted-badge">noch ${esc(fmtDuration(shop.remainingMs))}</span>`
          );
        }
      } else if (shop.opensInMs != null && shop.opensInMs > 0) {
        bits.push(
          `<span class="badge badge-soon">in ${esc(fmtDuration(shop.opensInMs))} aktiv</span>`
        );
      } else if (until && now >= until) {
        bits.push(`<span class="badge off">Fenster vorbei</span>`);
      } else {
        bits.push(`<span class="badge">Pause / geplant</span>`);
      }
      return bits.join(" ");
    }

    function openCycleModal(kind, itemId) {
      const shop = shopByKey[`${kind}:${itemId}`];
      const uni = state.shopUniverse.find((x) => x.kind === kind && x.itemId === itemId);
      if (!shop) {
        alert("Item ist nicht im Shop-Katalog.");
        return;
      }
      const label = uni?.label || shop.label || itemId;
      openModal(
        `
        <h3 style="margin:0 0 0.4rem;font-family:var(--display)">Zyklus · ${esc(label)}</h3>
        <p class="help" style="margin:0 0 0.75rem">
          Shop-Wechsel immer um <strong>03:00 Europe/Berlin</strong>.
          Starter bleiben dauerhaft; Event-Items sind von der Rotation ausgeschlossen.
        </p>
        <div class="panel" style="margin:0 0 0.75rem">
          <div class="shop-card-meta" style="display:flex;flex-wrap:wrap;gap:0.35rem">
            ${shopStatusBadges(shop, uni || { sources: [] })}
          </div>
          <p class="muted mono" style="margin:0.55rem 0 0;font-size:0.8rem">${esc(kind)}:${esc(itemId)}</p>
          <p class="help" style="margin:0.45rem 0 0">
            Fenster: ${esc(fmtWhen(shop.availableFrom))} → ${esc(fmtWhen(shop.availableUntil))}
            ${shop.rotationPlanId ? ` · Plan ${esc(shop.rotationPlanId)}` : ""}
          </p>
        </div>
        <div class="actions" style="flex-wrap:wrap;gap:0.45rem">
          ${
            shop.rotationPlanId && !shop.rotationLocked
              ? `<button type="button" class="btn danger" id="cycLeave">Aus Zyklus nehmen</button>`
              : `<button type="button" class="btn teal" id="cycRejoin">In Zyklus aufnehmen</button>`
          }
          <button type="button" class="btn secondary" id="cycGotoPlans">Rotationspläne öffnen</button>
          <button type="button" class="btn ghost" id="cancelModal">Schließen</button>
        </div>`,
        true
      );
      const leave = $("cycLeave");
      if (leave) {
        leave.onclick = async () => {
          if (!confirm("Aus dem Zyklus nehmen? Item bleibt dann dauerhaft im Shop (Fix).")) return;
          try {
            const res = await api(
              `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/leave-rotation`,
              { method: "POST", body: JSON.stringify(withSchedule()) }
            );
            closeModal();
            noteQueued(res);
            renderShop();
          } catch (err) {
            alert(err?.message || "Fehlgeschlagen");
          }
        };
      }
      const rejoin = $("cycRejoin");
      if (rejoin) {
        rejoin.onclick = async () => {
          try {
            const res = await api(
              `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/rejoin-rotation`,
              { method: "POST", body: JSON.stringify(withSchedule()) }
            );
            closeModal();
            noteQueued(res);
            renderShop();
          } catch (err) {
            alert(err?.message || "Fehlgeschlagen");
          }
        };
      }
      const goto = $("cycGotoPlans");
      if (goto) {
        goto.onclick = () => {
          closeModal();
          state.shopView = "kalender";
          state.calTab = "plans";
          loadTab("shop");
        };
      }
      const cancel = $("cancelModal");
      if (cancel) cancel.onclick = closeModal;
    }

    function cardHtml(it) {
      const shop = shopByKey[`${it.kind}:${it.itemId}`];
      const thumb =
        shop?.hasImage && shop?.imageUrl
          ? `<img class="shop-card-thumb" src="${esc(shop.imageUrl)}" alt="" />`
          : shop?.kind === "themes" && shop?.visualConfig
            ? `<span class="shop-card-theme" style="background:linear-gradient(160deg,${esc(
                shop.visualConfig.skyTop || "#7EB8D8"
              )},${esc(shop.visualConfig.skyBottom || "#B8D4E8")} 55%,${esc(
                shop.visualConfig.groundTop || "#2F5D2E"
              )})"></span>`
            : `<span class="shop-card-emoji">${esc(
                /^img_/i.test(String(it.emoji || "")) ? "🎁" : it.emoji || it.itemId
              )}</span>`;
      const price =
        it.priceCoins != null
          ? `<strong class="shop-price">${it.priceCoins}</strong> <span class="muted">Coins</span>`
          : `<span class="muted">kein Shop-Preis</span>`;
      const marketBtn = it.marketLocked
        ? `<span class="market-lock" title="Dauerhaft gesperrt (Starter/Ehe)">🔒</span>`
        : `<button type="button" class="btn-market ${
            it.marketSellable ? "is-on" : "is-off"
          }" data-market="${esc(it.kind)}|${esc(it.itemId)}|${
            it.marketSellable ? "0" : "1"
          }" title="${
            it.marketSellable ? "Marktplatz: handelbar — klicken zum Sperren" : "Marktplatz: gesperrt — klicken zum Freigeben"
          }">${it.marketSellable ? "🏪" : "🚫"}</button>`;
      const isEventItem =
        Boolean(shop?.eventId) || (it.sources || []).includes("event");
      const lootBtn = it.marketLocked
        ? ""
        : isEventItem
          ? `<button type="button" class="btn-loot is-off" disabled title="Event-Items sind fest von der Lootbox ausgeschlossen">⛔</button>`
          : `<button type="button" class="btn-loot ${
              it.lootboxEligible !== false ? "is-on" : "is-off"
            }" data-loot="${esc(it.kind)}|${esc(it.itemId)}|${
              it.lootboxEligible !== false ? "0" : "1"
            }" title="${
              it.lootboxEligible !== false
                ? "Lootbox: erhältlich — klicken zum Ausschließen"
                : "Lootbox: aus — klicken zum Freigeben"
            }">${it.lootboxEligible !== false ? "🎁" : "⛔"}</button>`;
      const shopOff = shop && shop.enabled === false;
      const achLinks = (it.achievements || [])
        .map(
          (a) =>
            `<button type="button" class="ach-chip" data-goto-ach="${esc(a.id)}" title="${esc(a.desc || "")}">🏆 ${esc(a.title)}</button>`
        )
        .join(" ");
      return `<article class="shop-card ${shopOff ? "is-off" : ""} ${
        it.marketSellable ? "is-tradeable" : "is-untradeable"
      }">
        <div class="shop-card-visual">${thumb}
          <div class="shop-card-market">${marketBtn}${lootBtn}</div>
        </div>
        <div class="shop-card-body">
          <strong class="shop-card-title">${esc(it.label || it.itemId)}</strong>
          <div class="shop-card-status">${shopStatusBadges(shop, it)}</div>
          <div class="muted mono shop-card-id">${esc(it.itemId)}</div>
          <div class="shop-card-price-row">${price}</div>
          <div class="shop-card-meta source-row">${sourceBadges(it.sources)}</div>
          ${achLinks ? `<div class="ach-chip-row">${achLinks}</div>` : ""}
        </div>
        <div class="shop-card-actions">
          ${
            shop && !eventMode && !isEventItem
              ? `<button type="button" class="btn secondary" data-cycle="${esc(it.kind)}|${esc(
                  it.itemId
                )}">Zyklus</button>`
              : ""
          }
          <button type="button" class="btn secondary" data-name="${esc(it.kind)}|${esc(it.itemId)}" title="Nur Anzeigename">Name</button>
          ${
            shop
              ? `<button type="button" class="btn secondary btn-edit" data-edit="${esc(it.kind)}|${esc(it.itemId)}">Bearbeiten</button>
          <button type="button" class="btn ghost btn-toggle" data-toggle="${esc(it.kind)}|${esc(it.itemId)}|${shop.enabled ? "off" : "on"}" title="${shop.enabled ? "Im Shop ausblenden" : "Im Shop aktivieren"}">${
                  shop.enabled ? "Shop aus" : "Shop an"
                }</button>
          <button type="button" class="btn btn-del" data-del="${esc(it.kind)}|${esc(it.itemId)}" title="Aus Shop löschen">Löschen</button>`
              : `<span class="muted" style="font-size:0.78rem;padding:0.35rem 0">Herkunft / Markt</span>`
          }
        </div>
      </article>`;
    }

    function sectionHtml(cat) {
      const items = byKind[cat.id] || [];
      if (!items.length && activeKind) {
        return `<section class="shop-section" id="cat-${cat.id}">
          <header class="shop-section-head">
            <div>
              <h3>${cat.emoji} ${cat.label}</h3>
              <p class="help" style="margin:0.2rem 0 0">${cat.hint}</p>
            </div>
          </header>
          <p class="help">Keine Einträge für diesen Filter.</p>
        </section>`;
      }
      if (!items.length) return "";
      return `<section class="shop-section" id="cat-${cat.id}">
        <header class="shop-section-head">
          <div>
            <h3>${cat.emoji} ${cat.label}</h3>
            <p class="help" style="margin:0.2rem 0 0">${cat.hint} · ${items.length}</p>
          </div>
        </header>
        <div class="shop-card-grid">${items.map(cardHtml).join("")}</div>
      </section>`;
    }

    content.innerHTML = `
      <div class="panel shop-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">${
              eventMode ? "Event-Katalog" : "Katalog"
            }</h3>
          </div>
          ${
            eventMode
              ? `<button class="btn teal" id="shopWizard">＋ Neues Event-Item</button>`
              : `<button class="btn teal" id="shopWizard">＋ Neues Shop-Item</button>`
          }
        </div>

        <div class="shop-cats" id="shopViews">
          <button type="button" class="shop-cat ${!eventMode ? "on" : ""}" data-shop-view="katalog">Katalog</button>
          <button type="button" class="shop-cat ${eventMode ? "on" : ""}" data-shop-view="events">Event-Katalog</button>
          <button type="button" class="shop-cat" data-shop-view="kalender">Kalender</button>
          <button type="button" class="shop-cat" data-shop-view="queue">Warteschlange</button>
        </div>
        ${scheduleBarHtml()}

        <div class="shop-cats" id="shopCats">
          <button type="button" class="shop-cat ${!activeKind ? "on" : ""}" data-kind="">Alle</button>
          ${SHOP_CATS.map((c) => {
            const n = (byKind[c.id] || []).length;
            return `<button type="button" class="shop-cat ${activeKind === c.id ? "on" : ""}" data-kind="${c.id}">
              ${c.emoji} ${c.label} <span class="shop-cat-n">${n}</span>
            </button>`;
          }).join("")}
        </div>

        <div class="shop-cats shop-source-cats" id="shopSources">
          ${SOURCE_FILTERS.map(
            (s) =>
              `<button type="button" class="shop-cat src-filter ${
                (state.shopSource || "") === s.id ? "on" : ""
              }" data-source="${s.id}">${esc(s.label)}</button>`
          ).join("")}
        </div>

        <div class="toolbar shop-search-bar">
          <input id="shopQ" placeholder="Suchen (Name, ID)…" value="${esc(state.shopQ)}" />
          <button class="btn secondary" id="shopFilter">Suchen</button>
          ${state.shopQ || state.shopSource ? `<button class="btn ghost" id="shopClearQ">Zurücksetzen</button>` : ""}
        </div>
      </div>

      <div class="shop-sections">
        ${
          visibleCats.map(sectionHtml).join("") ||
          `<div class="panel"><p class="help">${
            eventMode
              ? "Noch keine Event-Items. Oben „Neues Event-Item“ anlegen."
              : "Keine Items gefunden."
          }</p></div>`
        }
      </div>`;

    content.querySelectorAll("#shopViews [data-shop-view]").forEach((btn) => {
      btn.onclick = () => {
        state.shopView = btn.getAttribute("data-shop-view") || "katalog";
        loadTab("shop");
      };
    });
    wireScheduleBar();
    $("shopWizard").onclick = () => {
      if (eventMode) openNewEventChooser();
      else openWizard();
    };
    content.querySelectorAll("#shopViews [data-shop-view]").forEach((btn) => {
      btn.onclick = () => {
        state.shopView = btn.getAttribute("data-shop-view") || "katalog";
        loadTab("shop");
      };
    });
    content.querySelectorAll("[data-new-kind]").forEach((btn) => {
      btn.onclick = () => openWizard(btn.getAttribute("data-new-kind"));
    });
    content.querySelectorAll("[data-cycle]").forEach((btn) => {
      btn.onclick = () => {
        const raw = btn.getAttribute("data-cycle") || "";
        const [kind, ...rest] = raw.split("|");
        const itemId = rest.join("|");
        if (kind && itemId) openCycleModal(kind, itemId);
      };
    });
    $("shopFilter").onclick = () => {
      state.shopQ = $("shopQ").value.trim();
      renderShop();
    };
    $("shopQ").onkeydown = (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        state.shopQ = $("shopQ").value.trim();
        renderShop();
      }
    };
    const clearQ = $("shopClearQ");
    if (clearQ)
      clearQ.onclick = () => {
        state.shopQ = "";
        state.shopSource = "";
        renderShop();
      };
    content.querySelectorAll("#shopCats [data-kind]").forEach((btn) => {
      btn.onclick = () => {
        state.shopKind = btn.getAttribute("data-kind") || "";
        renderShop();
      };
    });
    content.querySelectorAll("#shopSources [data-source]").forEach((btn) => {
      btn.onclick = () => {
        state.shopSource = btn.getAttribute("data-source") || "";
        renderShop();
      };
    });
    content.querySelectorAll("[data-market]").forEach((btn) => {
      btn.onclick = async () => {
        const [kind, itemId, mode] = btn.getAttribute("data-market").split("|");
        btn.disabled = true;
        try {
          await api(
            `/admin/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/market-sellable`,
            {
              method: "PUT",
              body: JSON.stringify({ marketSellable: mode === "1" }),
            }
          );
          renderShop();
        } catch (err) {
          alert(err?.message || "Handelbarkeit speichern fehlgeschlagen.");
          btn.disabled = false;
        }
      };
    });
    content.querySelectorAll("[data-loot]").forEach((btn) => {
      btn.onclick = async () => {
        const [kind, itemId, mode] = btn.getAttribute("data-loot").split("|");
        btn.disabled = true;
        try {
          await api(
            `/admin/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/lootbox-eligible`,
            {
              method: "PUT",
              body: JSON.stringify({ lootboxEligible: mode === "1" }),
            }
          );
          renderShop();
        } catch (err) {
          alert(err?.message || "Lootbox-Einstellung speichern fehlgeschlagen.");
          btn.disabled = false;
        }
      };
    });
    content.querySelectorAll("[data-edit]").forEach((btn) => {
      btn.onclick = () => {
        const [kind, itemId] = btn.getAttribute("data-edit").split("|");
        const it = state.shopItems.find((x) => x.kind === kind && x.itemId === itemId);
        const uni = state.shopUniverse.find((x) => x.kind === kind && x.itemId === itemId);
        openEditItem(it || { kind, itemId, label: uni?.label });
      };
    });
    content.querySelectorAll("[data-name]").forEach((btn) => {
      btn.onclick = () => {
        const [kind, itemId] = btn.getAttribute("data-name").split("|");
        const uni = state.shopUniverse.find((x) => x.kind === kind && x.itemId === itemId);
        openDisplayNameEditor(kind, itemId, uni?.label || itemId, uni?.displayLabelOverride);
      };
    });
    content.querySelectorAll("[data-goto-ach]").forEach((btn) => {
      btn.onclick = () => goTab("achievements", { focusId: btn.getAttribute("data-goto-ach") });
    });
    content.querySelectorAll("[data-toggle]").forEach((btn) => {
      btn.onclick = async () => {
        const [kind, itemId, mode] = btn.getAttribute("data-toggle").split("|");
        const path =
          mode === "off"
            ? `/admin/shop/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/disable`
            : `/admin/shop/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/enable`;
        const res = await api(path, { method: "POST", body: JSON.stringify(withSchedule()) });
        noteQueued(res);
        renderShop();
      };
    });
    content.querySelectorAll("[data-del]").forEach((btn) => {
      btn.onclick = async () => {
        const [kind, itemId] = btn.getAttribute("data-del").split("|");
        const label =
          state.shopUniverse.find((x) => x.kind === kind && x.itemId === itemId)?.label ||
          state.shopItems.find((x) => x.kind === kind && x.itemId === itemId)?.label ||
          itemId;
        if (
          !confirm(
            `Nur dieses eine Item löschen?\n\n` +
              `„${label}"\nID: ${itemId}\n\n` +
              `Entfernt wird ausschließlich dieses Item aus Shop, Inventaren, Profilen und Leinwänden.\n` +
              `Alle anderen Items bleiben erhalten.`
          )
        ) {
          return;
        }
        btn.disabled = true;
        try {
          const res = await api(
            `/admin/shop/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}`,
            { method: "DELETE", body: "{}" }
          );
          const p = res?.purged || {};
          alert(
            `Gelöscht.\n` +
              `Nutzer bereinigt: ${p.users ?? 0}\n` +
              `Leinwände: ${p.rooms ?? 0}\n` +
              `Markt-Angebote: ${p.listings ?? 0}` +
              (p.pendingRefunds ? `\nLootbox-Erstattungen: ${p.pendingRefunds}` : "")
          );
          renderShop();
        } catch (err) {
          alert(err?.message || "Löschen fehlgeschlagen.");
          btn.disabled = false;
        }
      };
    });
  }

  async function renderShopChangeQueue() {
    const data = await api("/admin/shop/change-queue?all=1");
    const jobs = data.jobs || [];
    const pending = jobs.filter((j) => j.status === "pending");
    const done = jobs.filter((j) => j.status !== "pending").slice(0, 30);
    const fmt = (ms) => {
      try {
        return new Date(ms).toLocaleString("de-DE", { dateStyle: "short", timeStyle: "short" });
      } catch {
        return "—";
      }
    };
    const row = (j) => {
      const st =
        j.status === "pending"
          ? `<span class="badge">offen</span>`
          : j.status === "done"
            ? `<span class="badge" style="background:rgba(80,180,120,0.25)">fertig</span>`
            : j.status === "cancelled"
              ? `<span class="badge">storniert</span>`
              : `<span class="badge" style="background:rgba(220,80,80,0.2)">fehler</span>`;
      return `<tr>
        <td>${st}</td>
        <td><strong>${esc(j.label || j.action)}</strong>
          <div class="muted mono" style="font-size:0.72rem">${esc(j.action)} · ${esc(j.id)}</div>
          ${j.error ? `<div class="error" style="font-size:0.78rem">${esc(j.error)}</div>` : ""}
        </td>
        <td class="muted" style="white-space:nowrap">${esc(fmt(j.createdAt))}</td>
        <td>
          ${
            j.status === "pending"
              ? `<button type="button" class="btn ghost btn-xs" data-q-cancel="${esc(j.id)}">Stornieren</button>`
              : ""
          }
        </td>
      </tr>`;
    };
    content.innerHTML = `
      <div class="panel shop-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Warteschlange</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:48rem">
              Offene Jobs werden in der Nachtwartung (≈03:00 Berlin) vor dem Shop-Zyklus ausgeführt —
              oder jetzt mit „Jetzt ausführen“. Flush remischt die Rotation nicht automatisch
              (dafür Job „Neu mischen“ oder nächste Wartung).
            </p>
          </div>
          <div class="actions" style="margin:0">
            <button type="button" class="btn" id="qFlush" ${pending.length ? "" : "disabled"}>Jetzt ausführen (${pending.length})</button>
          </div>
        </div>
        ${shopViewsHtml("queue")}
        ${scheduleBarHtml()}
      </div>
      <div class="panel">
        <h3 style="margin:0 0 0.5rem;font-family:var(--display)">Offen (${pending.length})</h3>
        <div class="cal-mem-scroll" style="max-height:22rem">
          <table class="cal-mem-table">
            <thead><tr><th></th><th>Job</th><th>Erstellt</th><th></th></tr></thead>
            <tbody>
              ${
                pending.map(row).join("") ||
                `<tr><td colspan="4" class="muted">Keine offenen Jobs.</td></tr>`
              }
            </tbody>
          </table>
        </div>
      </div>
      <div class="panel">
        <h3 style="margin:0 0 0.5rem;font-family:var(--display)">Zuletzt</h3>
        <div class="cal-mem-scroll" style="max-height:16rem">
          <table class="cal-mem-table">
            <thead><tr><th></th><th>Job</th><th>Erstellt</th><th></th></tr></thead>
            <tbody>
              ${
                done.map(row).join("") ||
                `<tr><td colspan="4" class="muted">Noch keine Historie.</td></tr>`
              }
            </tbody>
          </table>
        </div>
      </div>`;
    content.querySelectorAll("#shopViews [data-shop-view]").forEach((btn) => {
      btn.onclick = () => {
        state.shopView = btn.getAttribute("data-shop-view") || "katalog";
        loadTab("shop");
      };
    });
    wireScheduleBar();
    const flush = $("qFlush");
    if (flush) {
      flush.onclick = async () => {
        if (!pending.length) return;
        if (!confirm(`${pending.length} Job(s) jetzt ausführen (ohne auf 03:00 zu warten)?`)) return;
        flush.disabled = true;
        try {
          const res = await api("/admin/shop/change-queue/flush", {
            method: "POST",
            body: "{}",
          });
          alert(`Erledigt: ${res.okCount || 0} ok, ${res.failCount || 0} Fehler.`);
          renderShopChangeQueue();
        } catch (err) {
          alert(err?.message || "Flush fehlgeschlagen");
          flush.disabled = false;
        }
      };
    }
    content.querySelectorAll("[data-q-cancel]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.getAttribute("data-q-cancel");
        if (!confirm("Job stornieren?")) return;
        try {
          await api(`/admin/shop/change-queue/${encodeURIComponent(id)}`, {
            method: "DELETE",
          });
          renderShopChangeQueue();
        } catch (err) {
          alert(err?.message || "Stornieren fehlgeschlagen");
        }
      };
    });
  }

  function toLocalInput(ms) {
    if (!ms) return "";
    const d = new Date(ms);
    if (!Number.isFinite(d.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function fmtWhen(ms) {
    if (!ms) return "—";
    try {
      return new Date(ms).toLocaleString("de-DE", {
        dateStyle: "short",
        timeStyle: "short",
      });
    } catch {
      return "—";
    }
  }

  function fmtDuration(ms) {
    if (!ms || ms < 0) return "—";
    const h = Math.floor(ms / 3600000);
    if (h < 48) return `${h} Std.`;
    const d = Math.floor(h / 24);
    return `${d} Tage`;
  }

  async function renderShopCalendar() {
    const MONTHS = [
      "Januar", "Februar", "März", "April", "Mai", "Juni",
      "Juli", "August", "September", "Oktober", "November", "Dezember",
    ];
    const STATUS = {
      always: "Immer an",
      window: "Zeitfenster",
      scheduled: "Geplant",
      off: "Aus",
      expired: "Abgelaufen",
      missing: "Fehlt",
    };

    const monthData = await api(
      `/admin/shop/calendar/month?year=${state.calYear}&month=${state.calMonth}`
    );
    let plans = monthData.plans || [];
    // Volle Plan-Felder (itemKeys/concurrent) für Edit — Month-Payload kann gekürzt sein
    if (state.calTab === "plans") {
      try {
        const full = await api("/admin/shop/rotation-plans");
        if (Array.isArray(full.plans) && full.plans.length) plans = full.plans;
      } catch (_) {
        /* Month-Pläne als Fallback */
      }
    }
    const days = monthData.days || [];
    const selectedDay = state.calDay
      ? days.find((d) => d.date === state.calDay)
      : null;

    if (state.calTab === "items") state.calTab = "month";

    let itemsData = { items: [], marks: {} };
    if (state.calTab === "items") {
      const qs = new URLSearchParams();
      if (state.calKind) qs.set("kind", state.calKind);
      if (state.calQ) qs.set("q", state.calQ);
      if (state.calStatus) qs.set("status", state.calStatus);
      if (state.calMark) qs.set("mark", state.calMark);
      itemsData = await api("/admin/shop/calendar?" + qs.toString());
    }
    const items = itemsData.items || [];
    const marks = itemsData.marks || {};
    const pickCount = Object.keys(state.calPick || {}).filter((k) => state.calPick[k]).length;

    function monthNavHtml() {
      return `<div class="cal-month-nav">
        <button type="button" class="btn secondary" id="calPrev">‹</button>
        <h3 class="cal-month-title">${MONTHS[state.calMonth - 1]} ${state.calYear}</h3>
        <button type="button" class="btn secondary" id="calNext">›</button>
        <button type="button" class="btn secondary" id="calToday">Heute</button>
      </div>`;
    }

    function densClass(count) {
      const n = Number(count) || 0;
      if (n <= 0) return "dens-0";
      if (n <= 4) return "dens-1";
      if (n <= 12) return "dens-2";
      if (n <= 30) return "dens-3";
      return "dens-4";
    }

    function kindDotsHtml(byKind, date) {
      const parts = [
        ["stickers", "S", "Sticker"],
        ["themes", "H", "Hintergründe"],
        ["pets", "B", "Begleiter"],
        ["emojis", "E", "Emojis"],
        ["other", "?", "Sonstige"],
      ];
      return parts
        .filter(([k]) => (byKind?.[k] || 0) > 0)
        .map(
          ([k, letter, label]) =>
            `<button type="button" class="cal-kdot cal-k-${k}" data-cal-day="${esc(
              date
            )}" data-cal-kind-filter="${esc(k)}" title="${esc(label)}: ${byKind[k]}">${letter}<i>${
              byKind[k]
            }</i></button>`
        )
        .join("");
    }

    function monthGridHtml() {
      const pads = Array.from({ length: monthData.startPad || 0 }, () => `<div class="cal-cell is-pad"></div>`);
      const cells = days.map((d) => {
        const isToday = d.date === monthData.today;
        const isSel = d.date === state.calDay;
        const n = d.count || 0;
        const byKind = d.byKind || {};
        const tipParts = [];
        if (byKind.stickers) tipParts.push(`${byKind.stickers} Sticker`);
        if (byKind.themes) tipParts.push(`${byKind.themes} Hintergründe`);
        if (byKind.pets) tipParts.push(`${byKind.pets} Begleiter`);
        if (byKind.emojis) tipParts.push(`${byKind.emojis} Emojis`);
        if (byKind.other) tipParts.push(`${byKind.other} Sonstige`);
        const tip = n
          ? `${n} Items · Klick öffnet Inventar (03:00 Berlin)`
          : "Leer — Klick zum Befüllen";

        let body;
        if (n <= 0) {
          body = `<span class="cal-cell-empty">—</span>`;
        } else if (n <= 4) {
          const chips = (d.preview || d.items || [])
            .slice(0, 3)
            .map((it) => `<span class="cal-chip" title="${esc(it.label || it.itemId)}">${esc(it.emoji || "·")}</span>`)
            .join("");
          const more = n > 3 ? `<span class="cal-more">+${n - 3}</span>` : "";
          body = `<span class="cal-cell-chips">${chips}${more}</span>`;
        } else {
          body = `<span class="cal-cell-dense">
            <span class="cal-cell-big">${n}</span>
            <span class="cal-kdots">${kindDotsHtml(byKind, d.date)}</span>
          </span>`;
        }

        return `<div role="button" tabindex="0" class="cal-cell ${densClass(n)} ${isToday ? "is-today" : ""} ${isSel ? "is-sel" : ""} ${n ? "has-items" : ""}" data-cal-day="${esc(d.date)}" title="${esc(tip)}">
          <span class="cal-cell-top">
            <span class="cal-cell-num">${d.day}</span>
          </span>
          ${body}
          <span class="cal-dens-bar" aria-hidden="true"></span>
        </div>`;
      });
      return `<div class="cal-weekdays">${["Mo","Di","Mi","Do","Fr","Sa","So"].map((w) => `<div>${w}</div>`).join("")}</div>
        <div class="cal-grid">${pads.join("")}${cells.join("")}</div>
        <p class="help cal-legend" style="margin:0.55rem 0 0">
          Shop-Tag = 03:00→03:00 Berlin. Tippe Tag oder Kategorie-Punkt (S/H/B/E) für Inventar-Popup.
        </p>`;
    }

    function invThumb(it) {
      if (it?.hasImage && it?.imageUrl) {
        return `<img class="cal-inv-thumb" src="${esc(it.imageUrl)}" alt="" loading="lazy" />`;
      }
      return `<span class="cal-inv-emoji">${esc(it?.emoji || "🎁")}</span>`;
    }

    async function openAddToDayModal(date, kindFilter, presentKeys) {
      const KIND_LABEL = {
        stickers: "Sticker",
        themes: "Hintergründe",
        pets: "Begleiter",
        emojis: "Emojis",
      };
      const kinds = kindFilter
        ? [kindFilter]
        : ["stickers", "themes", "pets", "emojis"];
      let pool = [];
      try {
        const qs = kindFilter ? `?kind=${encodeURIComponent(kindFilter)}` : "";
        const data = await api("/admin/shop/items" + qs);
        pool = (data.items || []).filter((it) => kinds.includes(it.kind));
      } catch (err) {
        alert(err?.message || "Katalog laden fehlgeschlagen");
        return;
      }
      const available = pool.filter((it) => !presentKeys.has(`${it.kind}:${it.itemId}`));
      let q = "";
      let filterKind = kindFilter || "";

      function paintAdd() {
        const list = available.filter((it) => {
          if (filterKind && it.kind !== filterKind) return false;
          if (!q) return true;
          const hay = `${it.label || ""} ${it.itemId} ${it.kind}`.toLowerCase();
          return hay.includes(q);
        });
        openModal(
          `
          <h3 style="margin:0 0 0.35rem;font-family:var(--display)">Hinzufügen · ${esc(date)}${
            kindFilter ? ` · ${esc(KIND_LABEL[kindFilter] || kindFilter)}` : ""
          }</h3>
          <p class="help">Nur Items, die an diesem Tag noch fehlen. Fenster: 03:00→nächster 03:00 Berlin.</p>
          ${
            kindFilter
              ? ""
              : `<div class="shop-cats" id="calAddKinds" style="margin:0.4rem 0">
              <button type="button" class="shop-cat ${!filterKind ? "on" : ""}" data-add-kind="">Alle</button>
              ${["stickers", "themes", "pets", "emojis"]
                .map(
                  (k) =>
                    `<button type="button" class="shop-cat ${
                      filterKind === k ? "on" : ""
                    }" data-add-kind="${k}">${esc(KIND_LABEL[k])}</button>`
                )
                .join("")}
            </div>`
          }
          <input type="search" id="calAddQ" class="ev-pick-search" placeholder="Suchen…" value="${esc(q)}" />
          <div class="cal-inv-grid" id="calAddGrid">
            ${
              list.length
                ? list
                    .map(
                      (it) => `<button type="button" class="cal-inv-tile" data-add-key="${esc(
                        it.kind
                      )}:${esc(it.itemId)}">
                  ${invThumb(it)}
                  <span class="cal-inv-name">${esc(it.label || it.itemId)}</span>
                  <span class="muted" style="font-size:0.7rem">${esc(it.kind)}</span>
                </button>`
                    )
                    .join("")
                : `<p class="help">Keine passenden Items übrig.</p>`
            }
          </div>
          <div class="actions" style="margin-top:0.75rem">
            <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
          </div>`,
          true
        );
        const cancel = $("cancelModal");
        if (cancel) cancel.onclick = () => {
          closeModal();
          openDayInventoryModal(date, kindFilter);
        };
        const qEl = $("calAddQ");
        if (qEl) {
          qEl.oninput = () => {
            q = String(qEl.value || "").trim().toLowerCase();
            paintAdd();
          };
        }
        document.querySelectorAll("#calAddKinds [data-add-kind]").forEach((btn) => {
          btn.onclick = () => {
            filterKind = btn.getAttribute("data-add-kind") || "";
            paintAdd();
          };
        });
        document.querySelectorAll("#calAddGrid [data-add-key]").forEach((btn) => {
          btn.onclick = async () => {
            const key = btn.getAttribute("data-add-key") || "";
            const dayMeta = days.find((d) => d.date === date);
            const from = dayMeta?.shopFrom || null;
            const until = dayMeta?.shopUntil || null;
            if (!from || !until) {
              alert("Shop-Fenster für diesen Tag fehlt.");
              return;
            }
            try {
              const res = await api("/admin/shop/calendar/batch", {
                method: "POST",
                body: JSON.stringify(
                  withSchedule({
                    itemKeys: [key],
                    availableFrom: from,
                    availableUntil: until,
                    enabled: true,
                  })
                ),
              });
              closeModal();
              noteQueued(res);
              state.calOpenInv = { date, kindFilter };
              renderShopCalendar();
            } catch (err) {
              alert(err?.message || "Hinzufügen fehlgeschlagen");
            }
          };
        });
      }
      paintAdd();
    }

    async function openDayInventoryModal(date, kindFilter) {
      const dayMeta = days.find((d) => d.date === date) || { date, shopFrom: null, shopUntil: null };
      state.calDay = date;
      const KIND_LABEL = {
        stickers: "Sticker",
        themes: "Hintergründe",
        pets: "Begleiter",
        emojis: "Emojis",
        other: "Sonstige",
      };
      openModal(
        `<h3 style="margin:0 0 0.35rem;font-family:var(--display)">${esc(date)} · Laden…</h3>
         <p class="help">Inventar wird geladen.</p>`,
        true
      );
      let dayPayload;
      try {
        dayPayload = await api(
          `/admin/shop/calendar/day?date=${encodeURIComponent(date)}`
        );
      } catch (err) {
        openModal(
          `<h3>Fehler</h3><p class="error">${esc(err?.message || "Laden fehlgeschlagen")}</p>
           <div class="actions"><button type="button" class="btn ghost" id="cancelModal">Schließen</button></div>`,
          true
        );
        const c = $("cancelModal");
        if (c) c.onclick = closeModal;
        return;
      }
      const allItems = dayPayload.items || [];
      const shopFrom = dayPayload.shopFrom || dayMeta.shopFrom;
      const shopUntil = dayPayload.shopUntil || dayMeta.shopUntil;
      let q = "";
      let filterKind = kindFilter || "";

      function paintInv() {
        const items = allItems.filter((it) => {
          if (filterKind && it.kind !== filterKind) return false;
          if (!q) return true;
          const hay = `${it.label || ""} ${it.itemId} ${it.kind} ${it.emoji || ""}`.toLowerCase();
          return hay.includes(q);
        });
        items.sort((a, b) =>
          String(a.label || a.itemId).localeCompare(String(b.label || b.itemId), "de")
        );
        const presentKeys = new Set(allItems.map((it) => `${it.kind}:${it.itemId}`));
        openModal(
          `
          <h3 style="margin:0 0 0.35rem;font-family:var(--display)">
            ${esc(date)}${filterKind ? ` · ${esc(KIND_LABEL[filterKind] || filterKind)}` : ""} · Shop-Tag
          </h3>
          <p class="help" style="margin:0 0 0.5rem">
            ${items.length} Items${filterKind ? "" : ` (gesamt ${allItems.length})`} · Fenster 03:00→03:00 Berlin
            ${shopFrom ? ` (${esc(fmtWhen(shopFrom))} – ${esc(fmtWhen(shopUntil))})` : ""}
          </p>
          ${
            allItems.length === 0
              ? `<p class="help" style="margin:0 0 0.65rem">
                  An diesem Tag ist noch nichts geplant. Mit <strong>+</strong> Items für genau diesen Shop-Tag setzen —
                  oder unter <strong>Rotation → Neu mischen</strong> die Fenster neu berechnen.
                </p>`
              : ""
          }
          <div class="shop-cats" id="calInvKinds" style="margin:0 0 0.45rem">
            <button type="button" class="shop-cat ${!filterKind ? "on" : ""}" data-inv-kind="">Alle</button>
            ${["stickers", "themes", "pets", "emojis"]
              .map((k) => {
                const n = allItems.filter((it) => it.kind === k).length;
                if (!n && kindFilter && kindFilter !== k) return "";
                return `<button type="button" class="shop-cat ${
                  filterKind === k ? "on" : ""
                }" data-inv-kind="${k}">${esc(KIND_LABEL[k])} <span class="shop-cat-n">${n}</span></button>`;
              })
              .join("")}
          </div>
          <input type="search" id="calInvQ" class="ev-pick-search" placeholder="Suchen…" value="${esc(q)}" />
          <div class="cal-inv-grid" id="calInvGrid">
            <button type="button" class="cal-inv-tile is-add" id="calInvAdd" title="Item hinzufügen">
              <span class="cal-inv-plus">+</span>
              <span class="cal-inv-name">Hinzufügen</span>
            </button>
            ${items
              .map(
                (it) => `<div class="cal-inv-tile" title="${esc(it.label || it.itemId)}">
                ${invThumb(it)}
                <span class="cal-inv-name">${esc(it.label || it.itemId)}</span>
                <span class="muted" style="font-size:0.7rem">${it.priceCoins ?? "—"}💰${
                  it.rotationPlanId ? " · ↻" : ""
                }</span>
              </div>`
              )
              .join("")}
          </div>
          <div class="actions" style="margin-top:0.75rem">
            <button type="button" class="btn ghost" id="cancelModal">Schließen</button>
          </div>`,
          true
        );
        const cancel = $("cancelModal");
        if (cancel) cancel.onclick = closeModal;
        const qEl = $("calInvQ");
        if (qEl) {
          qEl.oninput = () => {
            q = String(qEl.value || "").trim().toLowerCase();
            paintInv();
          };
        }
        document.querySelectorAll("#calInvKinds [data-inv-kind]").forEach((btn) => {
          btn.onclick = () => {
            filterKind = btn.getAttribute("data-inv-kind") || "";
            paintInv();
          };
        });
        const addBtn = $("calInvAdd");
        if (addBtn) {
          addBtn.onclick = () => {
            closeModal();
            openAddToDayModal(date, filterKind || kindFilter || "", presentKeys);
          };
        }
      }
      paintInv();
    }

    function dayPanelHtml() {
      return `<div class="cal-side panel">
        <h4 style="margin:0 0 0.4rem">Was bedeuten die Zahlen?</h4>
        <p class="help" style="margin:0 0 0.55rem">
          Jede Zelle = ein Shop-Tag (03:00→03:00 Berlin).<br />
          Die große Zahl = wie viele Items an dem Tag im Shop sind.<br />
          <strong>S / H / B / E</strong> = Sticker / Hintergründe / Begleiter / Emojis.
        </p>
        <p class="help" style="margin:0 0 0.55rem">
          Klick auf Tag oder Buchstaben → Inventar. Dort kannst du fehlende Items mit <strong>+</strong> hinzufügen.
        </p>
        <p class="help" style="margin:0">
          Rotation steuern: Tab <strong>Rotation</strong> oder im Katalog den Button <strong>Zyklus</strong>.
        </p>
      </div>`;
    }

    function plansHtml() {
      const plan = plans.find((p) => p.enabled !== false) || plans[0] || null;
      const activeNow = (plan?.activeNow || []).slice().sort((a, b) =>
        String(a.label || a.itemId).localeCompare(String(b.label || b.itemId), "de")
      );
      const activeCount = plan?.activeCount ?? activeNow.length;
      const total = plan?.itemCount || 0;
      const rows = activeNow
        .slice(0, 120)
        .map((m) => {
          const until = m.availableUntil
            ? new Date(m.availableUntil).toLocaleString("de-DE", {
                day: "2-digit",
                month: "short",
                hour: "2-digit",
                minute: "2-digit",
              })
            : "—";
          return `<tr>
            <td class="cal-mem-emoji">${esc(m.emoji || "·")}</td>
            <td>
              <strong>${esc(m.label || m.itemId)}</strong>
              <div class="muted" style="font-size:0.75rem">${esc(m.kind || "")}</div>
            </td>
            <td class="muted" style="white-space:nowrap">bis ${esc(until)}</td>
            <td>
              <button type="button" class="btn secondary btn-xs" data-leave-rot="${esc(m.kind)}|${esc(m.itemId)}" title="Bleibt dann dauerhaft im Shop">
                Fest im Shop
              </button>
            </td>
          </tr>`;
        })
        .join("");

      return `<div class="cal-plans cal-plans-simple">
        <div class="panel">
          <h3 style="margin:0 0 0.5rem;font-family:var(--display)">Shop-Rotation — kurz erklärt</h3>
          <div class="cal-rot-plain">
            <p><strong>1.</strong> Jede Nacht um <strong>03:00</strong> (Berlin) wechseln ablaufende Items raus und neue rein — tagsüber passiert nichts.</p>
            <p><strong>2.</strong> Danach wird die Rotation neu gemischt und die Admin-Warteschlange abgearbeitet.</p>
            <p><strong>3.</strong> Ungefähr die <strong>Hälfte</strong> aller Items ist im Shop, der Rest macht Pause.</p>
            <p><strong>4.</strong> Ein Item bleibt ein paar Tage (ca. 3–14), dann Pause — später kommt es wieder.</p>
            <p><strong>5.</strong> Starter (z. B. Wiese, Basis-Emojis) bleiben <strong>immer</strong> im Shop.</p>
          </div>
          <p class="help" style="margin:0.75rem 0 0">
            Einzelne Items: Katalog → <strong>Zyklus</strong>. Edits: oben <strong>Sofort</strong> oder <strong>Warteschlange</strong>.
          </p>
        </div>

        <div class="panel">
          <div class="cal-plan-head" style="align-items:flex-start">
            <div>
              <h3 style="margin:0;font-family:var(--display)">${esc(plan?.label || "Shop-Rotation")}</h3>
              <p class="muted" style="margin:0.35rem 0 0">
                Gerade im Shop: <strong>${activeCount}</strong>
                ${total ? ` von ${total}` : ""} Items
                ${plan?.activeSharePct != null ? ` · Ziel ~${plan.activeSharePct} %` : ""}
              </p>
            </div>
            <div class="actions" style="margin:0;flex-wrap:wrap">
              ${
                plan?.id
                  ? `<button type="button" class="btn" data-plan-apply="${esc(plan.id)}">Neu mischen</button>`
                  : ""
              }
            </div>
          </div>
          <p class="help" style="margin:0.65rem 0 0.35rem">
            „Neu mischen“ setzt neue Zeitfenster. Mit Modus <strong>Warteschlange</strong> erst in der nächsten Nachtwartung.
          </p>
          <input type="search" id="calRotQ" class="ev-pick-search" placeholder="Jetzt im Shop suchen…" />
          <div class="cal-mem-scroll" style="max-height:28rem;margin-top:0.55rem">
            <table class="cal-mem-table" id="calRotTable">
              <thead>
                <tr><th></th><th>Item</th><th>Im Shop bis</th><th></th></tr>
              </thead>
              <tbody id="calRotBody">
                ${
                  rows ||
                  `<tr><td colspan="4" class="muted">Gerade keine rotierenden Items aktiv — „Neu mischen“ oder Katalog prüfen.</td></tr>`
                }
              </tbody>
            </table>
          </div>
          ${
            activeNow.length > 120
              ? `<p class="help">Erste 120 von ${activeNow.length} — Suche filtert in der Liste.</p>`
              : ""
          }
        </div>
      </div>`;
    }

    function itemsHtml() {
      return `
        <div class="shop-cats" id="calKinds">
          <button type="button" class="shop-cat ${!state.calKind ? "on" : ""}" data-cal-kind="">Alle</button>
          ${["emojis", "stickers", "themes", "pets"]
            .map(
              (k) =>
                `<button type="button" class="shop-cat ${
                  state.calKind === k ? "on" : ""
                }" data-cal-kind="${k}">${k}</button>`
            )
            .join("")}
        </div>
        <div class="shop-cats" id="calStatus">
          ${[
            ["", "Status: alle"],
            ["window", "Zeitfenster"],
            ["scheduled", "Geplant"],
            ["always", "Immer an"],
            ["off", "Aus"],
            ["expired", "Abgelaufen"],
          ]
            .map(
              ([id, label]) =>
                `<button type="button" class="shop-cat ${
                  (state.calStatus || "") === id ? "on" : ""
                }" data-cal-status="${id}">${label}</button>`
            )
            .join("")}
        </div>
        <div class="shop-cats" id="calMarks">
          <button type="button" class="shop-cat ${!state.calMark ? "on" : ""}" data-cal-mark="">Marken</button>
          <button type="button" class="shop-cat ${state.calMark === "rotation" ? "on" : ""}" data-cal-mark="rotation">↻ Rotation (${marks.rotation || 0})</button>
          <button type="button" class="shop-cat ${state.calMark === "hot" ? "on" : ""}" data-cal-mark="hot">🔥 Hot</button>
          <button type="button" class="shop-cat ${state.calMark === "premium" ? "on" : ""}" data-cal-mark="premium">💎 Premium</button>
        </div>
        <div class="toolbar shop-search-bar">
          <input id="calQ" placeholder="Suchen…" value="${esc(state.calQ || "")}" />
          <button class="btn secondary" id="calFilter">Suchen</button>
          <span class="muted" style="align-self:center">${items.length} · ${pickCount} gewählt</span>
        </div>
        <div class="cal-list">
          ${
            items.length
              ? items
                  .map((it) => {
                    const key = `${it.kind}:${it.itemId}`;
                    const open = state.calOpenKey === key;
                    const picked = !!state.calPick[key];
                    const m = it.market || {};
                    return `<article class="cal-row ${open ? "is-open" : ""}">
                      <header class="cal-row-head">
                        <label class="cal-pick"><input type="checkbox" data-cal-pick="${esc(key)}" ${picked ? "checked" : ""} /></label>
                        <div class="cal-emoji" data-cal-toggle="${esc(key)}">${esc(it.emoji || it.itemId)}</div>
                        <div class="cal-main" data-cal-toggle="${esc(key)}">
                          <strong>${esc(it.label || it.itemId)}</strong>
                          <div class="muted mono" style="font-size:0.75rem">${esc(it.kind)} · ${esc(it.itemId)}</div>
                          <div class="cal-badges">
                            <span class="badge cal-st-${esc(it.status)}">${esc(STATUS[it.status] || it.status)}</span>
                            ${it.rotationPlanId ? '<span class="badge cal-hot">↻</span>' : ""}
                            ${m.hot ? '<span class="badge cal-hot">🔥</span>' : ""}
                          </div>
                        </div>
                        <div class="cal-stats" data-cal-toggle="${esc(key)}">
                          <div><span class="muted">Preis</span> <strong>${it.listPrice ?? it.priceCoins ?? "—"}</strong></div>
                          <div><span class="muted">Shop</span> <strong>${it.shopBuys || 0}</strong></div>
                        </div>
                      </header>
                      ${
                        open
                          ? `<div class="cal-detail">
                        <form class="cal-form" data-cal-form="${esc(it.kind)}|${esc(it.itemId)}">
                          <div class="grid-2">
                            <label class="field">Verfügbar ab
                              <input type="datetime-local" name="availableFrom" value="${esc(toLocalInput(it.availableFrom))}" />
                            </label>
                            <label class="field">Verfügbar bis
                              <input type="datetime-local" name="availableUntil" value="${esc(toLocalInput(it.availableUntil))}" />
                            </label>
                          </div>
                          <label class="field" style="flex-direction:row;align-items:center;gap:0.5rem;margin-top:0.5rem">
                            <input type="checkbox" name="enabled" ${it.enabled !== false ? "checked" : ""} /> Im Shop aktiv
                          </label>
                          <p class="help">Leere Felder = kein Limit. Speichern setzt ein manuelles Fenster und nimmt das Item aus der Rotation.</p>
                          <div class="actions" style="margin-top:0.75rem">
                            <button type="submit" class="btn">Fenster speichern</button>
                            ${
                              it.rotationPlanId
                                ? `<button type="button" class="btn secondary" data-leave-rot="${esc(it.kind)}|${esc(it.itemId)}">Aus Rotation → dauerhaft im Shop</button>`
                                : it.rotationLocked
                                  ? `<button type="button" class="btn secondary" data-rejoin-rot="${esc(it.kind)}|${esc(it.itemId)}">Wieder in Rotation aufnehmen</button>`
                                  : ""
                            }
                          </div>
                        </form>
                      </div>`
                          : ""
                      }
                    </article>`;
                  })
                  .join("")
              : `<div class="panel"><p class="help">Keine Items.</p></div>`
          }
        </div>`;
    }

    content.innerHTML = `
      <div class="panel shop-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Itemshop-Kalender</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:52rem">
              Übersicht der Shop-Tage. Klick auf einen Tag zeigt die Items.
              Rotation (= was wann im Shop ist) erklärst du dir unter dem Tab <strong>Rotation</strong>.
            </p>
          </div>
        </div>
        <div class="shop-cats" id="shopViews">
          <button type="button" class="shop-cat" data-shop-view="katalog">Katalog</button>
          <button type="button" class="shop-cat" data-shop-view="events">Event-Katalog</button>
          <button type="button" class="shop-cat on" data-shop-view="kalender">Kalender</button>
          <button type="button" class="shop-cat" data-shop-view="queue">Warteschlange</button>
        </div>
        ${scheduleBarHtml()}
        <div class="shop-cats" id="calTabs">
          <button type="button" class="shop-cat ${state.calTab === "month" ? "on" : ""}" data-cal-tab="month">Monat</button>
          <button type="button" class="shop-cat ${state.calTab === "plans" ? "on" : ""}" data-cal-tab="plans">Rotation</button>
        </div>
      </div>
      ${
        state.calTab === "month"
          ? `<div class="cal-layout">
              <div class="cal-main-panel panel">
                ${monthNavHtml()}
                ${monthGridHtml()}
              </div>
              ${dayPanelHtml()}
            </div>`
          : state.calTab === "plans"
            ? plansHtml()
            : itemsHtml()
      }`;

    content.querySelectorAll("#shopViews [data-shop-view]").forEach((btn) => {
      btn.onclick = () => {
        state.shopView = btn.getAttribute("data-shop-view") || "katalog";
        loadTab("shop");
      };
    });
    wireScheduleBar();
    content.querySelectorAll("#calTabs [data-cal-tab]").forEach((btn) => {
      btn.onclick = () => {
        state.calTab = btn.getAttribute("data-cal-tab") || "month";
        renderShopCalendar();
      };
    });

    const prev = $("calPrev");
    const next = $("calNext");
    const todayBtn = $("calToday");
    if (prev) {
      prev.onclick = () => {
        state.calMonth -= 1;
        if (state.calMonth < 1) {
          state.calMonth = 12;
          state.calYear -= 1;
        }
        state.calDay = "";
        renderShopCalendar();
      };
    }
    if (next) {
      next.onclick = () => {
        state.calMonth += 1;
        if (state.calMonth > 12) {
          state.calMonth = 1;
          state.calYear += 1;
        }
        state.calDay = "";
        renderShopCalendar();
      };
    }
    if (todayBtn) {
      todayBtn.onclick = () => {
        const n = new Date();
        state.calYear = n.getFullYear();
        state.calMonth = n.getMonth() + 1;
        state.calDay = monthData.today || "";
        renderShopCalendar();
      };
    }
    content.querySelectorAll("[data-cal-day]").forEach((el) => {
      const open = (kindFilter) => {
        const date = el.getAttribute("data-cal-day") || "";
        if (!date) return;
        openDayInventoryModal(date, kindFilter || "");
      };
      if (el.classList.contains("cal-kdot")) {
        el.onclick = (e) => {
          e.preventDefault();
          e.stopPropagation();
          open(el.getAttribute("data-cal-kind-filter") || "");
        };
      } else if (el.classList.contains("cal-cell")) {
        el.onclick = (e) => {
          if (e.target.closest && e.target.closest(".cal-kdot")) return;
          open("");
        };
        el.onkeydown = (e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            open("");
          }
        };
      }
    });

    if (state.calOpenInv?.date) {
      const pending = state.calOpenInv;
      state.calOpenInv = null;
      openDayInventoryModal(pending.date, pending.kindFilter || "");
    }

    const planForm = $("calPlanForm");
    if (planForm) {
      planForm.onsubmit = async (e) => {
        e.preventDefault();
        const fd = new FormData(planForm);
        const keys = String(fd.get("itemKeys") || "")
          .split(/[,;\n]+/)
          .map((s) => s.trim())
          .filter(Boolean);
        const body = {
          id: String(fd.get("id") || "").trim() || undefined,
          label: String(fd.get("label") || "").trim(),
          model: String(fd.get("model") || "independent"),
          cycleLength: Number(fd.get("cycleLength") || 3),
          cycleUnit: String(fd.get("cycleUnit") || "month"),
          activeLength: Number(fd.get("activeLength") || 1),
          activeUnit: String(fd.get("activeUnit") || "week"),
          onDaysMin: Number(fd.get("onDaysMin") || 3),
          onDaysMax: Number(fd.get("onDaysMax") || 14),
          offDaysMin: Number(fd.get("offDaysMin") || 7),
          offDaysMax: Number(fd.get("offDaysMax") || 90),
          targetSharePct: Number(fd.get("targetSharePct") || 50),
          longPauseChancePct: Number(fd.get("longPauseChancePct") || 12),
          shortDays: Number(fd.get("onDaysMin") || fd.get("shortDays") || 3),
          longDays: Number(fd.get("onDaysMax") || fd.get("longDays") || 14),
          concurrent: Number(fd.get("concurrent") || 1),
          mode: String(fd.get("mode") || "price"),
          priceMin: fd.get("priceMin") === "" ? 0 : Number(fd.get("priceMin")),
          priceMax: fd.get("priceMax") === "" ? null : Number(fd.get("priceMax")),
          itemKeys: keys,
          enabled: planForm.querySelector('[name="enabled"]').checked,
        };
        try {
          await api("/admin/shop/rotation-plans", {
            method: "POST",
            body: JSON.stringify(body),
          });
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Plan speichern fehlgeschlagen");
        }
      };
      const reset = $("calPlanReset");
      if (reset) {
        reset.onclick = () => {
          planForm.reset();
          planForm.querySelector('[name="id"]').value = "";
        };
      }
      content.querySelectorAll("[data-plan-edit]").forEach((btn) => {
        btn.onclick = () => {
          const id = btn.getAttribute("data-plan-edit");
          const p = plans.find((x) => x.id === id);
          if (!p) return;
          planForm.querySelector('[name="id"]').value = p.id || "";
          planForm.querySelector('[name="label"]').value = p.label || "";
          planForm.querySelector('[name="model"]').value = p.model || "independent";
          planForm.querySelector('[name="cycleLength"]').value = p.cycleLength || 3;
          planForm.querySelector('[name="cycleUnit"]').value = p.cycleUnit || "month";
          planForm.querySelector('[name="activeLength"]').value = p.activeLength || 1;
          planForm.querySelector('[name="activeUnit"]').value = p.activeUnit || "week";
          if (planForm.querySelector('[name="onDaysMin"]')) {
            planForm.querySelector('[name="onDaysMin"]').value = p.onDaysMin || p.shortDays || 3;
          }
          if (planForm.querySelector('[name="onDaysMax"]')) {
            planForm.querySelector('[name="onDaysMax"]').value = p.onDaysMax || p.longDays || 14;
          }
          if (planForm.querySelector('[name="offDaysMin"]')) {
            planForm.querySelector('[name="offDaysMin"]').value = p.offDaysMin || 7;
          }
          if (planForm.querySelector('[name="offDaysMax"]')) {
            planForm.querySelector('[name="offDaysMax"]').value = p.offDaysMax || 90;
          }
          if (planForm.querySelector('[name="targetSharePct"]')) {
            planForm.querySelector('[name="targetSharePct"]').value = p.targetSharePct || 50;
          }
          if (planForm.querySelector('[name="longPauseChancePct"]')) {
            planForm.querySelector('[name="longPauseChancePct"]').value = p.longPauseChancePct ?? 12;
          }
          planForm.querySelector('[name="concurrent"]').value = p.concurrent || 1;
          planForm.querySelector('[name="mode"]').value = p.mode || "price";
          planForm.querySelector('[name="priceMin"]').value = p.priceMin ?? 0;
          planForm.querySelector('[name="priceMax"]').value = p.priceMax ?? "";
          planForm.querySelector('[name="itemKeys"]').value = (p.itemKeys || []).join(", ");
          planForm.querySelector('[name="enabled"]').checked = p.enabled !== false;
          planForm.scrollIntoView({ behavior: "smooth", block: "start" });
        };
      });
    }

    content.querySelectorAll("[data-plan-apply]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.getAttribute("data-plan-apply");
        const queued = state.shopSchedule === "maintenance";
        if (
          !confirm(
            queued
              ? "Neu mischen in die Warteschlange legen (nächste Wartung ≈03:00)?"
              : "Shop-Fenster jetzt neu mischen? Das ändert, welche Items in den nächsten Tagen im Shop sind."
          )
        ) {
          return;
        }
        try {
          const res = await api(`/admin/shop/rotation-plans/${encodeURIComponent(id)}/apply`, {
            method: "POST",
            body: JSON.stringify(withSchedule()),
          });
          noteQueued(res) || alert("Rotation neu gemischt.");
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Anwenden fehlgeschlagen");
        }
      };
    });
    content.querySelectorAll("[data-plan-del]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.getAttribute("data-plan-del");
        if (!confirm("Rotationsplan löschen?")) return;
        try {
          await api(`/admin/shop/rotation-plans/${encodeURIComponent(id)}`, {
            method: "DELETE",
          });
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Löschen fehlgeschlagen");
        }
      };
    });
    content.querySelectorAll("[data-leave-rot]").forEach((btn) => {
      btn.onclick = async () => {
        const raw = btn.getAttribute("data-leave-rot") || "";
        const [kind, ...rest] = raw.split("|");
        const itemId = rest.join("|");
        if (!kind || !itemId) return;
        if (!confirm("Item fest in den Shop legen? Es rotiert dann nicht mehr.")) return;
        try {
          const res = await api(
            `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/leave-rotation`,
            { method: "POST", body: JSON.stringify(withSchedule()) }
          );
          noteQueued(res);
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Entfernen fehlgeschlagen");
        }
      };
    });
    content.querySelectorAll("[data-rejoin-rot]").forEach((btn) => {
      btn.onclick = async () => {
        const raw = btn.getAttribute("data-rejoin-rot") || "";
        const [kind, ...rest] = raw.split("|");
        const itemId = rest.join("|");
        if (!kind || !itemId) return;
        try {
          const res = await api(
            `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/rejoin-rotation`,
            { method: "POST", body: JSON.stringify(withSchedule()) }
          );
          noteQueued(res);
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Aufnehmen fehlgeschlagen");
        }
      };
    });

    const rotQ = $("calRotQ");
    if (rotQ) {
      rotQ.oninput = () => {
        const q = String(rotQ.value || "").trim().toLowerCase();
        document.querySelectorAll("#calRotBody tr").forEach((tr) => {
          const hay = (tr.textContent || "").toLowerCase();
          tr.style.display = !q || hay.includes(q) ? "" : "none";
        });
      };
    }

    content.querySelectorAll("#calKinds [data-cal-kind]").forEach((btn) => {
      btn.onclick = () => {
        state.calKind = btn.getAttribute("data-cal-kind") || "";
        renderShopCalendar();
      };
    });
    content.querySelectorAll("#calStatus [data-cal-status]").forEach((btn) => {
      btn.onclick = () => {
        state.calStatus = btn.getAttribute("data-cal-status") || "";
        renderShopCalendar();
      };
    });
    content.querySelectorAll("#calMarks [data-cal-mark]").forEach((btn) => {
      btn.onclick = () => {
        state.calMark = btn.getAttribute("data-cal-mark") || "";
        renderShopCalendar();
      };
    });
    if ($("calFilter")) {
      $("calFilter").onclick = () => {
        state.calQ = $("calQ").value.trim();
        renderShopCalendar();
      };
    }
    if ($("calQ")) {
      $("calQ").onkeydown = (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          state.calQ = $("calQ").value.trim();
          renderShopCalendar();
        }
      };
    }
    content.querySelectorAll("[data-cal-pick]").forEach((el) => {
      el.onchange = () => {
        const key = el.getAttribute("data-cal-pick");
        state.calPick[key] = el.checked;
      };
    });
    content.querySelectorAll("[data-cal-toggle]").forEach((el) => {
      el.onclick = () => {
        const key = el.getAttribute("data-cal-toggle");
        state.calOpenKey = state.calOpenKey === key ? "" : key;
        renderShopCalendar();
      };
    });
    content.querySelectorAll("[data-cal-form]").forEach((form) => {
      form.onsubmit = async (e) => {
        e.preventDefault();
        const [kind, itemId] = form.getAttribute("data-cal-form").split("|");
        const fd = new FormData(form);
        const body = {
          availableFrom: String(fd.get("availableFrom") || "").trim() || null,
          availableUntil: String(fd.get("availableUntil") || "").trim() || null,
          enabled: form.querySelector('[name="enabled"]').checked,
        };
        try {
          const res = await api(
            `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}`,
            { method: "PUT", body: JSON.stringify(withSchedule(body)) }
          );
          noteQueued(res);
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Speichern fehlgeschlagen");
        }
      };
    });
  }

  function itemFormFields(it = {}, stepHint = "", eventOptions = []) {
    const fromLocal = toLocalInput(it.availableFrom);
    const untilLocal = toLocalInput(it.availableUntil);
    const eventSelect =
      ["pets", "stickers", "emojis", "themes"].includes(it.kind)
        ? `<label class="field">Event-Item (optional)
          <select name="eventId">
            <option value="">— kein Event —</option>
            ${(eventOptions || [])
              .map((ev) => {
                const occ = ev.occupiedKinds || {};
                const taken = occ[it.kind] && ev.existingByKind?.[it.kind] !== it.itemId;
                const mark = taken ? " · belegt" : "";
                return `<option value="${esc(ev.id)}" ${
                  it.eventId === ev.id ? "selected" : ""
                } ${taken && it.eventId !== ev.id ? "disabled" : ""}>${esc(ev.emoji || "🎉")} ${esc(ev.title)} (${ev.year})${mark}</option>`;
              })
              .join("")}
          </select>
          <span class="tip">Max. 1× pro Kategorie und Event-Jahr. Shop nur im Event-Zeitraum.</span>
        </label>
        <label class="field">Event-Jahr
          <input name="eventYear" type="number" min="2020" max="2100" value="${esc(
            it.eventYear ?? ""
          )}" placeholder="z.B. 2026" />
        </label>`
        : "";
    return `
      ${stepHint}
      <div class="grid-2">
        <label class="field">Kategorie
          <select name="kind">
            ${["emojis", "stickers", "themes", "pets"]
              .map(
                (k) =>
                  `<option value="${k}" ${it.kind === k ? "selected" : ""}>${k}</option>`
              )
              .join("")}
          </select>
          <span class="tip">Was für ein Artikel ist das?</span>
        </label>
        <label class="field">Item-ID / Emoji
          <input name="itemId" value="${esc(it.itemId || "")}" maxlength="32" required />
          <span class="tip">Bei Emoji/Sticker/Pet: das Emoji selbst. Bei Theme: z. B. ocean</span>
        </label>
        <label class="field">Anzeigename
          <input name="label" value="${esc(it.label || it.itemId || "")}" maxlength="40" />
        </label>
        <label class="field">Suchbegriffe
          <input name="searchText" value="${esc(it.searchText || "")}" placeholder="herz heart liebe" />
          <span class="tip">Damit Nutzer in der App danach suchen können</span>
        </label>
        ${eventSelect}
        <label class="field">Listenpreis (Coins)
          <input name="priceCoins" type="number" min="1" max="100000" value="${esc(it.listPrice ?? it.priceCoins ?? 10)}" />
        </label>
        <label class="field">Angebotspreis (optional)
          <input name="salePrice" type="number" min="0" max="100000" value="${esc(it.salePrice ?? "")}" placeholder="leer = kein Angebot" />
          <span class="tip">Wenn gesetzt: Nutzer zahlt das, Listenpreis wird durchgestrichen</span>
        </label>
        <label class="field">Alter Vergleichspreis (optional)
          <input name="compareAtPrice" type="number" min="0" value="${esc(it.compareAtPrice ?? "")}" />
        </label>
        <label class="field">Verfügbar ab (optional)
          <input name="availableFrom" type="datetime-local" value="${fromLocal}" />
          <span class="tip">Vorher unsichtbar im Shop — bei Event-Begleiter automatisch</span>
        </label>
        <label class="field">Verfügbar bis (optional)
          <input name="availableUntil" type="datetime-local" value="${untilLocal}" />
          <span class="tip">Danach: deaktiviert (bleibt in der DB)</span>
        </label>
        <label class="field">Max. Verkäufe gesamt
          <input name="maxTotalSales" type="number" min="0" value="${esc(it.maxTotalSales ?? "")}" placeholder="∞" />
        </label>
        <label class="field">Max. Käufe pro Nutzer
          <input name="maxPerUser" type="number" min="0" value="${esc(it.maxPerUser ?? "")}" placeholder="∞" />
        </label>
      </div>
      <label class="field" style="margin-top:0.75rem;flex-direction:row;align-items:center;gap:0.5rem">
        <input type="checkbox" name="enabled" ${it.enabled !== false ? "checked" : ""} /> Aktiv im Shop
      </label>`;
  }

  function readForm(form) {
    const fd = new FormData(form);
    const numOrNull = (k) => {
      const v = String(fd.get(k) || "").trim();
      if (v === "") return null;
      return Number(v);
    };
    const from = String(fd.get("availableFrom") || "").trim();
    const until = String(fd.get("availableUntil") || "").trim();
    const eventId = String(fd.get("eventId") || "").trim();
    return {
      kind: String(fd.get("kind") || "").trim(),
      itemId: String(fd.get("itemId") || "").trim(),
      label: String(fd.get("label") || "").trim(),
      searchText: String(fd.get("searchText") || "").trim(),
      priceCoins: Number(fd.get("priceCoins") || 0),
      salePrice: numOrNull("salePrice"),
      compareAtPrice: numOrNull("compareAtPrice"),
      availableFrom: from ? new Date(from).getTime() : null,
      availableUntil: until ? new Date(until).getTime() : null,
      maxTotalSales: numOrNull("maxTotalSales"),
      maxPerUser: numOrNull("maxPerUser"),
      enabled: form.querySelector('[name="enabled"]').checked,
      eventId: eventId || "",
      eventYear: numOrNull("eventYear"),
    };
  }

  async function openEditItem(it) {
    let eventOptions = [];
    if (it.kind === "pets") {
      try {
        const evData = await api("/admin/shop/event-options");
        eventOptions = evData.events || [];
      } catch (_) {
        eventOptions = [];
      }
    }
    openModal(`
      <h3 style="font-family:var(--display);margin:0 0 0.5rem">Item bearbeiten</h3>
      <p class="help">Modus oben im Itemshop: <strong>${
        state.shopSchedule === "maintenance" ? "Warteschlange (≈03:00)" : "Sofort"
      }</strong>. Bild-Uploads immer sofort.</p>
      <form id="editForm">${itemFormFields(it, "", eventOptions)}
        <div class="actions" style="margin-top:1rem">
          <button type="submit" class="btn">Speichern</button>
          <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
        </div>
      </form>`);
    $("cancelModal").onclick = closeModal;
    $("editForm").onsubmit = async (e) => {
      e.preventDefault();
      const body = readForm(e.target);
      // Anzeigename: bei Warteschlange nicht vorziehen (sonst schon live, Rest erst 03:00)
      if (body.label && state.shopSchedule !== "maintenance") {
        await api(
          `/admin/items/${encodeURIComponent(body.kind)}/${encodeURIComponent(body.itemId)}/display-label`,
          { method: "PUT", body: JSON.stringify({ label: body.label }) }
        );
      }
      const res = await api(
        `/admin/shop/items/${encodeURIComponent(body.kind)}/${encodeURIComponent(body.itemId)}`,
        { method: "PUT", body: JSON.stringify(withSchedule(body)) }
      );
      noteQueued(res);
      closeModal();
      renderShop();
    };
  }

  function openDisplayNameEditor(kind, itemId, currentLabel, override) {
    openModal(`
      <h3 style="font-family:var(--display);margin:0 0 0.4rem">Anzeigename</h3>
      <p class="help">Wirkt nur auf die Namensanzeige in Shop, Inventar und Admin — nicht auf Preise, Limits oder Belohnungen.</p>
      <p class="muted mono" style="margin:0.25rem 0 0.75rem">${esc(kind)} · ${esc(itemId)}</p>
      <form id="nameForm">
        <label class="field">Anzeigename
          <input name="label" maxlength="40" value="${esc(override || currentLabel || "")}" required />
        </label>
        <div class="actions" style="margin-top:1rem">
          <button type="submit" class="btn">Speichern</button>
          <button type="button" class="btn ghost" id="nameClear">Zurücksetzen</button>
          <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
        </div>
      </form>`);
    $("cancelModal").onclick = closeModal;
    $("nameClear").onclick = async () => {
      await api(
        `/admin/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/display-label`,
        { method: "PUT", body: JSON.stringify({ label: "" }) }
      );
      closeModal();
      renderShop();
    };
    $("nameForm").onsubmit = async (e) => {
      e.preventDefault();
      const label = new FormData(e.target).get("label");
      await api(
        `/admin/items/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/display-label`,
        { method: "PUT", body: JSON.stringify({ label }) }
      );
      closeModal();
      renderShop();
    };
  }

  const CHROMA_KEY = "#00FF00";
  const Studio = () => window.LuvShopStudio;

  function openPetImageEditor(onDone, title) {
    const S = Studio();
    if (!S) {
      alert("Shop-Studio nicht geladen — Seite neu laden.");
      return;
    }
    S.openChromaImageEditor((dataUrl) => onDone(dataUrl), {
      title: title || "Bild freistellen",
      keyHex: CHROMA_KEY,
    });
  }

  async function openWizard(preKind) {
    let step = 0;
    let eventOptions = [];
    let rotationPlans = [];
    try {
      const evData = await api("/admin/shop/event-options");
      eventOptions = evData.events || [];
    } catch (_) {
      eventOptions = [];
    }
    try {
      const planData = await api("/admin/shop/rotation-plans");
      rotationPlans = (planData.plans || []).filter((p) => p && p.enabled !== false);
    } catch (_) {
      rotationPlans = [];
    }
    const defaultPlanId =
      rotationPlans.find((p) => p.id === "expensive_3m_week")?.id ||
      rotationPlans[0]?.id ||
      "expensive_3m_week";
    const draft = {
      kind: String(preKind || "emojis").startsWith("event_")
        ? String(preKind).slice("event_".length)
        : preKind || "emojis",
      isEventItem: String(preKind || "").startsWith("event_"),
      eventId: "",
      eventYear: null,
      availableFrom: null,
      itemId: "💖",
      label: "Herz",
      searchText: "herz heart liebe",
      priceCoins: String(preKind || "").startsWith("event_")
        ? (String(preKind).includes("themes") ? 80 : 200)
        : 40,
      salePrice: null,
      compareAtPrice: null,
      availableUntil: null,
      maxTotalSales: null,
      maxPerUser: null,
      enabled: true,
      source: String(preKind || "").startsWith("event_")
        ? (String(preKind).includes("themes") ? "custom" : "custom")
        : "emoji",
      imageDataUrl: null,
      visualConfig: null,
      previewEmoji: "",
      /** fixed = dauerhaft im Shop · cycle = Rotationsplan */
      shopPresence: "fixed",
      cycleMode: "random",
      rotationPlanId: defaultPlanId,
      cycleFrom: null,
      cycleUntil: null,
      marketSellable: false,
      lootboxEligible: true,
    };
    if (draft.isEventItem) {
      const kindDe =
        draft.kind === "pets"
          ? "Begleiter"
          : draft.kind === "stickers"
            ? "Sticker"
            : draft.kind === "emojis"
              ? "Emoji"
              : "Hintergrund";
      draft.label = "Event-" + kindDe;
      draft.searchText = "event " + kindDe.toLowerCase();
      draft.itemId = "";
      if (draft.kind === "themes") draft.source = "custom";
      draft.shopPresence = "fixed";
      draft.lootboxEligible = false;
      draft.marketSellable = false;
    }
    function wizardSteps() {
      return draft.isEventItem
        ? ["Kategorie", "Gestalten", "Preis", "Angebot & Timer", "Limits", "Fertig"]
        : [
            "Kategorie",
            "Gestalten",
            "Preis",
            "Angebot & Timer",
            "Limits",
            "Shop & Sichtbarkeit",
            "Fertig",
          ];
    }
    function isVisibilityStep() {
      return !draft.isEventItem && step === 5;
    }
    function isFinishStep() {
      return draft.isEventItem ? step === 5 : step === 6;
    }

    function eventKindDe(kind) {
      return kind === "pets"
        ? "Begleiter"
        : kind === "stickers"
          ? "Sticker"
          : kind === "emojis"
            ? "Emoji"
            : "Hintergrund";
    }

    function isPlaceholderLabel(label) {
      const s = String(label || "").trim();
      if (!s) return true;
      if (
        [
          "Herz",
          "Bild-Begleiter",
          "Bild-Item",
          "Eigenes Emoji",
          "Eigener Hintergrund",
          "Event-Begleiter",
          "Event-Emoji",
          "Event-Sticker",
          "Event-Hintergrund",
        ].includes(s)
      ) {
        return true;
      }
      // Auto-Vorschlag vom Event, z. B. „Herzltag-Begleiter“
      if (/^.+-(Begleiter|Emoji|Sticker|Hintergrund)$/.test(s)) return true;
      return false;
    }

    /** Formularfelder aus Step „Gestalten“ in draft lesen — vor jedem Repaint. */
    function readDesignFormIntoDraft() {
      const form = $("wizForm");
      if (!form || step !== 1) return;
      const fd = new FormData(form);
      const label = String(fd.get("label") || "").trim();
      const search = String(fd.get("searchText") || "").trim();
      const earlyPrice = String(fd.get("priceCoinsEarly") || "").trim();
      if (label) draft.label = label;
      if (search) draft.searchText = search;
      if (earlyPrice !== "" && Number(earlyPrice) >= 1) {
        draft.priceCoins = Number(earlyPrice);
      }
      const evId = String(fd.get("eventId") || "").trim();
      if (evId) draft.eventId = evId;
    }

    function applyEventChoice(evId, { keepUserFields = false } = {}) {
      const prevLabel = draft.label;
      const prevSearch = draft.searchText;
      const prevItemId = draft.itemId;
      const prevImg = draft.imageDataUrl;
      const prevVc = draft.visualConfig;
      const ev = eventOptions.find((e) => e.id === evId);
      draft.eventId = evId || "";
      if (!ev) {
        draft.eventYear = null;
        draft.availableFrom = null;
        draft.availableUntil = null;
        return;
      }
      const sug = (ev.suggestedByKind && ev.suggestedByKind[draft.kind]) || {};
      draft.eventYear = ev.year;
      draft.availableFrom = ev.availableFrom;
      draft.availableUntil = ev.availableUntil;
      draft.itemId = sug.itemId || ev.suggestedItemId || "";
      draft.label = sug.label || `${ev.title || "Event"}-${eventKindDe(draft.kind)}`.slice(0, 40);
      draft.previewEmoji = ev.emoji || "🎁";
      draft.searchText = `${ev.emoji || ""} ${ev.title || ""} ${eventKindDe(draft.kind).toLowerCase()} event ${ev.year}`.trim();
      if (!draft.priceCoins || draft.priceCoins < 1) {
        draft.priceCoins = draft.kind === "themes" ? 80 : 200;
      }
      draft.source = "custom";
      draft.isEventItem = true;
      if (keepUserFields) {
        if (!isPlaceholderLabel(prevLabel)) draft.label = prevLabel;
        if (prevSearch && !isPlaceholderLabel(prevLabel)) draft.searchText = prevSearch;
        if (prevImg) draft.imageDataUrl = prevImg;
        if (prevVc) draft.visualConfig = prevVc;
        if (prevItemId && (prevImg || prevVc || !isPlaceholderLabel(prevLabel))) {
          draft.itemId = prevItemId;
        }
      }
    }

    function paint() {
      const steps = wizardSteps();
      if (step >= steps.length) step = steps.length - 1;
      openModal(`
        <h3 style="font-family:var(--display);margin:0 0 0.5rem">${
          draft.isEventItem ? "Neues Event-Item" : "Neues Shop-Item"
        }</h3>
        <div class="wizard-steps">${steps
          .map((s, i) => `<span class="${i === step ? "on" : ""}">${i + 1}. ${s}</span>`)
          .join("")}</div>
        <form id="wizForm">
          ${stepBody()}
          <div class="actions" style="margin-top:1rem">
            ${step > 0 ? `<button type="button" class="btn ghost" id="wizBack">Zurück</button>` : ""}
            ${
              !isFinishStep()
                ? `<button type="button" class="btn" id="wizNext">Weiter</button>`
                : `<button type="submit" class="btn teal">Im Shop anlegen</button>`
            }
            <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
          </div>
        </form>`);
      if (modalCard) {
        modalCard.classList.toggle(
          "wide",
          (step === 1 && (draft.source === "custom" || draft.kind === "themes" || draft.isEventItem)) ||
            isVisibilityStep()
        );
      }
      $("cancelModal").onclick = closeModal;
      const back = $("wizBack");
      if (back)
        back.onclick = () => {
          saveStep();
          step--;
          paint();
        };
      const next = $("wizNext");
      if (next)
        next.onclick = () => {
          if (!saveStep()) return;
          step++;
          paint();
        };
      wireDesignStep();
      wireVisibilityStep();
      const eventSel = $("eventPetSelect");
      if (eventSel) {
        eventSel.onchange = () => {
          readDesignFormIntoDraft();
          applyEventChoice(String(eventSel.value || "").trim(), { keepUserFields: true });
          paint();
        };
      }
      const form = $("wizForm");
      form.addEventListener("keydown", (e) => {
        if (e.key !== "Enter") return;
        const tag = (e.target && e.target.tagName) || "";
        if (tag !== "INPUT" && tag !== "SELECT") return;
        e.preventDefault();
        if (!isFinishStep()) {
          if (!saveStep()) return;
          step++;
          paint();
        }
      });
      form.onsubmit = async (e) => {
        e.preventDefault();
        if (!isFinishStep()) {
          if (!saveStep()) return;
          step++;
          paint();
          return;
        }
        if (!saveStep()) return;
        if (draft.isEventItem && !draft.eventId) {
          alert("Bitte ein Event für das Event-Item wählen.");
          return;
        }
        if (draft.kind === "themes") ensureThemeId();
        const body = {
          kind: draft.kind,
          itemId: draft.itemId,
          label: draft.label,
          searchText: draft.searchText,
          priceCoins: draft.priceCoins,
          salePrice: draft.salePrice,
          compareAtPrice: draft.compareAtPrice,
          availableFrom: draft.availableFrom,
          availableUntil: draft.availableUntil,
          maxTotalSales: draft.maxTotalSales,
          maxPerUser: draft.maxPerUser,
          enabled: draft.enabled,
        };
        if (draft.isEventItem && draft.eventId) {
          body.eventId = draft.eventId;
          body.eventYear = draft.eventYear;
          body.previewEmoji = draft.previewEmoji || "";
          body.rotationLocked = true;
          if (!body.priceCoins || body.priceCoins < 1) {
            body.priceCoins = draft.kind === "themes" ? 80 : 200;
          }
        } else {
          const fixed = draft.shopPresence === "fixed";
          body.marketSellable = Boolean(draft.marketSellable);
          body.lootboxEligible = Boolean(draft.lootboxEligible);
          if (fixed) {
            // Dauerhaft = Lock + kein Fenster (sonst filtert isWithinWindow das Item raus)
            body.rotationLocked = true;
            body.rotationPlanId = null;
            body.joinRotation = false;
            body.availableFrom = null;
            body.availableUntil = null;
            body.enabled = true;
          } else {
            body.rotationLocked = false;
            body.rotationPlanId = draft.rotationPlanId || defaultPlanId;
            body.joinRotation = true;
            body.enabled = true;
            if (draft.cycleMode === "manual") {
              if (!draft.cycleFrom || !draft.cycleUntil) {
                alert("Bitte Von/Bis für das Zyklus-Fenster setzen oder „Zufall“ wählen.");
                return;
              }
              if (draft.cycleUntil <= draft.cycleFrom) {
                alert("Bis-Datum muss nach Von-Datum liegen.");
                return;
              }
              body.cycleFrom = draft.cycleFrom;
              body.cycleUntil = draft.cycleUntil;
              // Manuelles Zyklusfenster steuert die Sichtbarkeit — Sale-Ende aus Step 3 nicht mischen
              body.availableUntil = null;
              body.availableFrom = null;
            }
          }
        }
        if (draft.kind === "themes" && draft.visualConfig) {
          body.visualConfig = draft.visualConfig;
          body.emoji = (draft.visualConfig.emojis && draft.visualConfig.emojis[0]) || "🖼️";
          body.itemId = draft.itemId;
        }
        if (draft.source === "custom" && draft.imageDataUrl) {
          body.petSource = "image";
          body.imageDataUrl = draft.imageDataUrl;
          const S = Studio();
          if (!draft.isEventItem && !S?.isStableImageItemId?.(draft.itemId)) {
            draft.itemId = S?.newImageItemId?.() || `img_${Date.now().toString(36)}`;
          }
          body.itemId = draft.itemId;
        } else if (draft.isEventItem && draft.kind === "pets") {
          alert("Event-Begleiter braucht ein Bild (Chromakey / Composer).");
          return;
        } else if (draft.isEventItem && draft.kind === "themes" && !draft.visualConfig) {
          alert("Event-Hintergrund braucht den Designer.");
          return;
        } else if (
          draft.isEventItem &&
          (draft.kind === "stickers" || draft.kind === "emojis") &&
          !draft.imageDataUrl &&
          (!draft.itemId || /^img_/i.test(draft.itemId))
        ) {
          alert("Bitte ein Emoji wählen oder ein Bild gestalten.");
          return;
        }
        try {
          const created = await api("/admin/shop/items", {
            method: "POST",
            body: JSON.stringify(withSchedule(body)),
          });
          if (created?.item?.itemId) draft.itemId = created.item.itemId;
          noteQueued(created);
          closeModal();
          renderShop();
        } catch (err) {
          alert(err?.message || "Anlegen fehlgeschlagen");
        }
      };
    }

    function wireVisibilityStep() {
      if (!isVisibilityStep()) return;
      const syncCycleUi = () => {
        const presence = String($("wizShopPresence")?.value || draft.shopPresence || "fixed");
        const cycleBox = $("wizCycleBox");
        if (cycleBox) cycleBox.style.display = presence === "cycle" ? "" : "none";
        const mode =
          document.querySelector('#wizForm input[name="cycleMode"]:checked')?.value ||
          draft.cycleMode ||
          "random";
        const manual = $("wizCycleManual");
        if (manual) manual.style.display = mode === "manual" ? "" : "none";
      };
      const presence = $("wizShopPresence");
      if (presence) presence.onchange = syncCycleUi;
      document.querySelectorAll('#wizForm input[name="cycleMode"]').forEach((el) => {
        el.onchange = syncCycleUi;
      });
      const rnd = $("wizCycleRandom");
      if (rnd) {
        rnd.onclick = () => {
          const radio = document.querySelector('#wizForm input[name="cycleMode"][value="random"]');
          if (radio) radio.checked = true;
          draft.cycleMode = "random";
          draft.cycleFrom = null;
          draft.cycleUntil = null;
          const from = document.querySelector('#wizForm input[name="cycleFrom"]');
          const until = document.querySelector('#wizForm input[name="cycleUntil"]');
          if (from) from.value = "";
          if (until) until.value = "";
          syncCycleUi();
        };
      }
      syncCycleUi();
    }

    function ensureCustomImageId() {
      if (draft.isEventItem && draft.itemId && (/^img_event_/i.test(draft.itemId) || /^img_ev_/i.test(draft.itemId))) return;
      const S = Studio();
      if (!S?.isStableImageItemId?.(draft.itemId)) {
        draft.itemId = S?.newImageItemId?.() || `img_${Date.now().toString(36)}`;
      }
    }

    function ensureThemeId() {
      const id = String(draft.itemId || "");
      if (/^theme_[a-z0-9]{4,24}$/i.test(id)) return;
      if (/^theme_[a-z0-9_]+_t\d{4}$/i.test(id)) return;
      const bytes = new Uint8Array(4);
      if (crypto && crypto.getRandomValues) crypto.getRandomValues(bytes);
      else for (let i = 0; i < 4; i++) bytes[i] = (Math.random() * 256) | 0;
      draft.itemId =
        "theme_" +
        Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    }

    function wireDesignStep() {
      const srcEmoji = $("srcEmoji");
      const srcCustom = $("srcCustom");
      if (srcEmoji)
        srcEmoji.onclick = () => {
          if (draft.isEventItem && draft.kind === "pets") return;
          readDesignFormIntoDraft();
          draft.source = "emoji";
          draft.imageDataUrl = null;
          paint();
        };
      if (srcCustom)
        srcCustom.onclick = () => {
          readDesignFormIntoDraft();
          draft.source = "custom";
          paint();
        };
      const pickEmoji = $("pickEmoji");
      if (pickEmoji)
        pickEmoji.onclick = () => {
          readDesignFormIntoDraft();
          Studio()?.openEmojiPicker((emoji) => {
            readDesignFormIntoDraft();
            draft.itemId = emoji;
            draft.source = "emoji";
            draft.imageDataUrl = null;
            if (isPlaceholderLabel(draft.label)) {
              draft.label = emoji;
            }
            paint();
          });
        };

      const focusWizardLabel = () => {
        const label = document.querySelector('#wizForm input[name="label"]');
        if (label) {
          try {
            label.focus();
            if (typeof label.select === "function") label.select();
          } catch (_) {
            /* ignore */
          }
        }
      };

      const openGlyph = $("openGlyph");
      if (openGlyph)
        openGlyph.onclick = () => {
          readDesignFormIntoDraft();
          Studio()?.openGlyphComposer(
            (dataUrl) => {
              readDesignFormIntoDraft();
              draft.imageDataUrl = dataUrl;
              draft.source = "custom";
              ensureCustomImageId();
              if (isPlaceholderLabel(draft.label)) draft.label = "Eigenes Emoji";
              paint();
              focusWizardLabel();
            },
            { title: draft.kind === "pets" ? "Begleiter gestalten" : "Emoji / Sticker gestalten" }
          );
        };

      const openImg = $("openPetImage");
      if (openImg)
        openImg.onclick = () => {
          readDesignFormIntoDraft();
          openPetImageEditor((dataUrl) => {
            readDesignFormIntoDraft();
            draft.imageDataUrl = dataUrl;
            draft.source = "custom";
            ensureCustomImageId();
            if (isPlaceholderLabel(draft.label)) draft.label = "Bild-Item";
            paint();
            focusWizardLabel();
          }, draft.kind === "pets" ? "Begleiter freistellen" : "Bild freistellen");
        };

      const clearImg = $("clearPetImage");
      if (clearImg)
        clearImg.onclick = () => {
          readDesignFormIntoDraft();
          draft.imageDataUrl = null;
          paint();
        };

      const openTheme = $("openThemeStudio");
      if (openTheme)
        openTheme.onclick = () => {
          readDesignFormIntoDraft();
          Studio()?.openThemeStudio(draft.visualConfig, (cfg) => {
            readDesignFormIntoDraft();
            draft.visualConfig = cfg;
            if (isPlaceholderLabel(draft.label)) draft.label = "Eigener Hintergrund";
            ensureThemeId();
            paint();
          });
        };
    }

    function saveStep() {
      const form = $("wizForm");
      if (!form) return true;
      const fd = new FormData(form);
      if (step === 0) {
        const kindRaw = String(fd.get("kind") || "emojis");
        if (String(kindRaw).startsWith("event_")) {
          draft.kind = String(kindRaw).slice("event_".length);
          draft.isEventItem = true;
          draft.source = "custom";
          if (!draft.priceCoins || draft.priceCoins === 40) {
            draft.priceCoins = draft.kind === "themes" ? 80 : 200;
          }
          if (!draft.label || draft.label === "Herz") {
            draft.label = "Event-" + eventKindDe(draft.kind);
          }
        } else {
          draft.kind = kindRaw;
          draft.isEventItem = false;
          draft.eventId = "";
          draft.eventYear = null;
          if (draft.kind === "themes") draft.source = "custom";
        }
      }
      if (step === 1) {
        draft.label = String(fd.get("label") || draft.label || "").trim();
        draft.searchText = String(fd.get("searchText") || "").trim();
        const earlyPrice = String(fd.get("priceCoinsEarly") || "").trim();
        if (earlyPrice !== "" && Number(earlyPrice) >= 1) {
          draft.priceCoins = Number(earlyPrice);
        }
        if (draft.isEventItem) {
          const evId = String(fd.get("eventId") || draft.eventId || "").trim();
          if (!evId) {
            alert("Bitte ein Event wählen.");
            return false;
          }
          applyEventChoice(evId, { keepUserFields: true });
          if (draft.kind === "themes") {
            if (!draft.visualConfig) {
              alert("Bitte zuerst den Hintergrund-Designer öffnen.");
              return false;
            }
            ensureThemeId();
          } else if (draft.kind === "pets") {
            if (!draft.imageDataUrl) {
              alert("Bitte Begleiter-Bild gestalten oder freistellen.");
              return false;
            }
            ensureCustomImageId();
          } else {
            // Event-Sticker / Event-Emoji: Bild oder normales Emoji
            const picked = String(fd.get("itemId") || "").trim();
            if (draft.imageDataUrl) {
              ensureCustomImageId();
            } else if (picked && !/^img_/i.test(picked)) {
              draft.itemId = picked;
              draft.source = "emoji";
            } else if (draft.itemId && !/^img_/i.test(draft.itemId)) {
              draft.source = "emoji";
            } else {
              alert("Bitte ein Emoji wählen oder ein Bild gestalten.");
              return false;
            }
          }
        } else if (draft.kind === "themes") {
          if (!draft.visualConfig) {
            alert("Bitte zuerst den Hintergrund-Designer öffnen.");
            return false;
          }
          ensureThemeId();
          if (!draft.label) draft.label = "Hintergrund";
        } else if (draft.source === "custom") {
          if (!draft.imageDataUrl) {
            alert("Bitte Emoji gestalten oder Bild freistellen.");
            return false;
          }
          if (!draft.label) draft.label = "Eigenes Emoji";
          ensureCustomImageId();
        } else {
          draft.itemId = String(fd.get("itemId") || draft.itemId || "").trim();
          draft.label = String(fd.get("label") || draft.itemId).trim();
          if (!draft.itemId) {
            alert("Bitte ein Emoji wählen.");
            return false;
          }
        }
      }
      if (step === 2) {
        draft.priceCoins = Number(fd.get("priceCoins") || 0);
        if (draft.priceCoins < 1) {
          alert("Preis mind. 1 Coin.");
          return false;
        }
      }
      if (step === 3) {
        const sp = String(fd.get("salePrice") || "").trim();
        draft.salePrice = sp === "" ? null : Number(sp);
        const cp = String(fd.get("compareAtPrice") || "").trim();
        draft.compareAtPrice = cp === "" ? null : Number(cp);
        if (!draft.isEventItem) {
          const until = String(fd.get("availableUntil") || "").trim();
          draft.availableUntil = until ? new Date(until).getTime() : null;
        }
      }
      if (step === 4) {
        const mt = String(fd.get("maxTotalSales") || "").trim();
        const mp = String(fd.get("maxPerUser") || "").trim();
        draft.maxTotalSales = mt === "" ? null : Number(mt);
        draft.maxPerUser = mp === "" ? null : Number(mp);
      }
      if (isVisibilityStep()) {
        draft.shopPresence =
          String(fd.get("shopPresence") || "fixed") === "cycle" ? "cycle" : "fixed";
        draft.cycleMode =
          String(fd.get("cycleMode") || "random") === "manual" ? "manual" : "random";
        draft.rotationPlanId = String(fd.get("rotationPlanId") || defaultPlanId).trim() || defaultPlanId;
        const fromRaw = String(fd.get("cycleFrom") || "").trim();
        const untilRaw = String(fd.get("cycleUntil") || "").trim();
        draft.cycleFrom = fromRaw ? new Date(fromRaw).getTime() : null;
        draft.cycleUntil = untilRaw ? new Date(untilRaw).getTime() : null;
        draft.marketSellable = fd.get("marketSellable") === "on" || fd.get("marketSellable") === "true";
        draft.lootboxEligible = fd.get("lootboxEligible") === "on" || fd.get("lootboxEligible") === "true";
        if (draft.shopPresence === "cycle" && draft.cycleMode === "manual") {
          if (!draft.cycleFrom || !draft.cycleUntil) {
            alert("Zyklus-Fenster: Von und Bis ausfüllen, oder „Zufall“ wählen.");
            return false;
          }
          if (draft.cycleUntil <= draft.cycleFrom) {
            alert("Bis-Datum muss nach Von-Datum liegen.");
            return false;
          }
        }
      }
      return true;
    }

    function glyphPreviewHtml() {
      if (!draft.imageDataUrl) return "";
      return `<div class="pet-preview-row"><div class="pet-preview-circle"><img src="${draft.imageDataUrl}" alt="" /></div><span class="muted">Vorschau</span></div>`;
    }

    function eventOptionsHtml() {
      if (!eventOptions.length) {
        return `<p class="help">Keine Events geladen — unter „Events“ prüfen.</p>`;
      }
      return `<label class="field">Event
        <select name="eventId" id="eventPetSelect" required>
          <option value="">— Event wählen —</option>
          ${eventOptions
            .map((ev) => {
              const when =
                [ev.windowStart, ev.windowEnd].filter(Boolean).join(" – ") || "Zeitfenster offen";
              const occ = ev.occupiedKinds || {};
              const taken = Boolean(occ[draft.kind]);
              const mark = taken ? ` · schon ${eventKindDe(draft.kind)}` : "";
              const act = ev.active ? " · aktiv" : "";
              const dis = taken && draft.eventId !== ev.id ? "disabled" : "";
              return `<option value="${esc(ev.id)}" ${
                draft.eventId === ev.id ? "selected" : ""
              } ${dis}>${esc(ev.emoji || "🎉")} ${esc(ev.title)} (${ev.year}) — ${esc(
                when
              )}${mark}${act}</option>`;
            })
            .join("")}
        </select>
        <span class="tip">Nur während diesem Event-Zeitraum im Itemshop &amp; Event-Popup. 1× ${esc(
          eventKindDe(draft.kind)
        )} pro Event-Jahr.</span>
      </label>`;
    }

    function stepBody() {
      if (step === 0)
        return draft.isEventItem
          ? `<label class="field">Kategorie
          <select name="kind">
            <option value="event_emojis" ${draft.kind === "emojis" ? "selected" : ""}>🎉 Event-Emoji</option>
            <option value="event_stickers" ${draft.kind === "stickers" ? "selected" : ""}>🎉 Event-Sticker</option>
            <option value="event_themes" ${draft.kind === "themes" ? "selected" : ""}>🎉 Event-Hintergrund</option>
            <option value="event_pets" ${draft.kind === "pets" ? "selected" : ""}>🎉 Event-Begleiter</option>
          </select>
          <span class="tip">Nur während dem Event im Shop &amp; Event-Popup · 1× pro Kategorie und Jahr · nicht in der Lootbox.</span>
        </label>`
          : `<label class="field">Kategorie
          <select name="kind">
            <option value="emojis" ${draft.kind === "emojis" ? "selected" : ""}>😊 Emoji (Reaktion)</option>
            <option value="stickers" ${draft.kind === "stickers" ? "selected" : ""}>🏷️ Sticker</option>
            <option value="themes" ${draft.kind === "themes" ? "selected" : ""}>🖼️ Hintergrund</option>
            <option value="pets" ${draft.kind === "pets" ? "selected" : ""}>🐾 Begleiter</option>
          </select>
        </label>`;
      if (step === 1) {
        if (draft.isEventItem) {
          if (draft.kind === "themes") {
            const vc = draft.visualConfig;
            return `
              ${eventOptionsHtml()}
              <div class="panel" style="margin-top:0.75rem">
                <p class="help" style="margin:0 0 0.65rem">Event-Hintergrund — Designer inkl. Animation.</p>
                <button type="button" class="btn teal" id="openThemeStudio">${
                  vc ? "Hintergrund bearbeiten" : "Hintergrund-Designer öffnen"
                }</button>
                ${
                  vc
                    ? `<div class="theme-mini-preview" style="margin-top:0.75rem;background:linear-gradient(160deg,${esc(
                        vc.skyTop
                      )},${esc(vc.skyBottom)} 50%,${esc(vc.groundTop)})">
                        <span>${esc((vc.emojis || []).slice(0, 4).join(" "))}</span>
                        <span class="muted">${esc(vc.motion)} · ${esc(vc.coverage)}</span>
                      </div>`
                    : ""
                }
              </div>
              <label class="field" style="margin-top:0.75rem">Name
                <input name="label" value="${esc(draft.label)}" />
              </label>
              <label class="field" style="margin-top:0.6rem">Preis (Coins)
                <input name="priceCoinsEarly" type="number" min="1" value="${draft.priceCoins}" />
              </label>
              <label class="field" style="margin-top:0.6rem">Suchworte
                <input name="searchText" value="${esc(draft.searchText)}" placeholder="event hintergrund" />
              </label>`;
          }
          return `
            ${eventOptionsHtml()}
            ${
              draft.kind === "pets"
                ? `<div class="panel" style="margin-top:0.75rem">
              <p class="help" style="margin:0 0 0.65rem">
                Chromakey-Bild oder Composer — feste ID pro Event-Jahr
                ${draft.itemId ? `(<code>${esc(draft.itemId)}</code>)` : ""}.
                Max. 1 ${esc(eventKindDe(draft.kind))} pro Event.
              </p>
              <div class="actions">
                <button type="button" class="btn teal" id="openGlyph">✨ Composer öffnen</button>
                <button type="button" class="btn secondary" id="openPetImage">Bild + Pipette</button>
                ${draft.imageDataUrl ? `<button type="button" class="btn ghost" id="clearPetImage">Verwerfen</button>` : ""}
              </div>
              ${glyphPreviewHtml()}
            </div>`
                : `<div class="seg" style="margin-top:0.75rem">
              <button type="button" id="srcEmoji" class="${draft.source !== "custom" ? "on" : ""}">Normales Emoji</button>
              <button type="button" id="srcCustom" class="${draft.source === "custom" ? "on" : ""}">Selbst gestalten</button>
            </div>
            ${
              draft.source === "custom"
                ? `<div class="panel">
                  <div class="actions">
                    <button type="button" class="btn teal" id="openGlyph">✨ Composer öffnen</button>
                    <button type="button" class="btn secondary" id="openPetImage">Bild + Pipette</button>
                    ${draft.imageDataUrl ? `<button type="button" class="btn ghost" id="clearPetImage">Verwerfen</button>` : ""}
                  </div>
                  ${glyphPreviewHtml()}
                </div>`
                : `<div class="emoji-pick-row">
                  <button type="button" class="btn teal round-plus" id="pickEmoji" title="Emoji wählen">＋</button>
                  <div class="emoji-pick-current">${esc(draft.itemId && !/^img_/i.test(draft.itemId) ? draft.itemId : "❓")}</div>
                  <input type="hidden" name="itemId" value="${esc(
                    draft.itemId && !/^img_/i.test(draft.itemId) ? draft.itemId : ""
                  )}" />
                </div>`
            }`
            }
            <label class="field" style="margin-top:0.75rem">Name
              <input name="label" value="${esc(draft.label)}" />
            </label>
            <label class="field" style="margin-top:0.6rem">Preis (Coins)
              <input name="priceCoinsEarly" type="number" min="1" value="${draft.priceCoins}" />
              <span class="tip">Standard ${draft.kind === "themes" ? "80" : "200"} — kannst du hier oder im Preis-Schritt ändern.</span>
            </label>
            <label class="field" style="margin-top:0.6rem">Suchworte
              <input name="searchText" value="${esc(draft.searchText)}" placeholder="event item" />
            </label>`;
        }
        if (draft.kind === "themes") {
          const vc = draft.visualConfig;
          return `
            <div class="panel">
              <p class="help" style="margin:0 0 0.65rem">Farbverläufe, Emoji-Regen und Animationen — live im Designer.</p>
              <button type="button" class="btn teal" id="openThemeStudio">${
                vc ? "Hintergrund bearbeiten" : "Hintergrund-Designer öffnen"
              }</button>
              ${
                vc
                  ? `<div class="theme-mini-preview" style="margin-top:0.75rem;background:linear-gradient(160deg,${esc(
                      vc.skyTop
                    )},${esc(vc.skyBottom)} 50%,${esc(vc.groundTop)})">
                      <span>${esc((vc.emojis || []).slice(0, 4).join(" "))}</span>
                      <span class="muted">${esc(vc.motion)} · ${esc(vc.coverage)}</span>
                    </div>`
                  : ""
              }
            </div>
            <label class="field" style="margin-top:0.75rem">Name
              <input name="label" value="${esc(draft.label === "Herz" ? "Eigener Hintergrund" : draft.label)}" />
            </label>
            <label class="field" style="margin-top:0.6rem">Suchworte
              <input name="searchText" value="${esc(draft.searchText)}" placeholder="schnee winter cozy" />
            </label>`;
        }
        return `
          <div class="seg">
            <button type="button" id="srcEmoji" class="${draft.source === "emoji" ? "on" : ""}">Normales Emoji</button>
            <button type="button" id="srcCustom" class="${draft.source === "custom" ? "on" : ""}">Selbst gestalten</button>
          </div>
          ${
            draft.source === "custom"
              ? `<div class="panel">
                  <p class="help" style="margin:0 0 0.65rem">
                    Mehrere Emojis kombinieren, Bilder hochladen und mit der Pipette freistellen.
                    Jedes Bild bekommt eine feste ID — sie ändert sich danach nicht mehr.
                  </p>
                  <div class="actions">
                    <button type="button" class="btn teal" id="openGlyph">✨ Composer öffnen</button>
                    <button type="button" class="btn secondary" id="openPetImage">Nur Bild + Pipette</button>
                    ${draft.imageDataUrl ? `<button type="button" class="btn ghost" id="clearPetImage">Verwerfen</button>` : ""}
                  </div>
                  ${glyphPreviewHtml()}
                </div>
                <label class="field" style="margin-top:0.75rem">Name
                  <input name="label" value="${esc(draft.label === "Herz" ? "Eigenes Emoji" : draft.label)}" />
                </label>
                <label class="field" style="margin-top:0.6rem">Suchworte
                  <input name="searchText" value="${esc(draft.searchText)}" placeholder="custom emoji" />
                </label>`
              : `<div class="emoji-pick-row">
                  <button type="button" class="btn teal round-plus" id="pickEmoji" title="Emoji wählen">＋</button>
                  <div class="emoji-pick-current">${esc(draft.itemId)}</div>
                  <input type="hidden" name="itemId" value="${esc(draft.itemId)}" />
                </div>
                <label class="field" style="margin-top:0.75rem">Name
                  <input name="label" value="${esc(draft.label)}" />
                </label>
                <label class="field" style="margin-top:0.6rem">Suchworte
                  <input name="searchText" value="${esc(draft.searchText)}" />
                </label>`
          }`;
      }
      if (step === 2)
        return `<label class="field">Listenpreis in Coins
          <input name="priceCoins" type="number" min="1" value="${draft.priceCoins}" />
          <span class="tip">${
            draft.isEventItem
              ? draft.kind === "themes"
              ? "Standard für Event-Hintergrund: 80 Coins."
              : "Standard für Event-Items: 200 Coins."
              : "Lootbox-Seltenheit folgt dem Preis."
          }</span>
        </label>`;
      if (step === 3) {
        if (draft.isEventItem) {
          const from = draft.availableFrom
            ? new Date(draft.availableFrom).toLocaleString("de-DE")
            : "—";
          const until = draft.availableUntil
            ? new Date(draft.availableUntil).toLocaleString("de-DE")
            : "—";
          return `<div class="panel">
            <p class="help" style="margin:0">Zeitraum kommt automatisch vom Event:</p>
            <p style="margin:0.5rem 0 0"><strong>${esc(from)}</strong> → <strong>${esc(until)}</strong></p>
            <p class="muted" style="margin:0.5rem 0 0">Im Shop nur in diesem Fenster; danach verschwindet der Eintrag (Besitz bleibt).</p>
          </div>
          <div class="grid-2" style="margin-top:0.75rem">
            <label class="field">Angebotspreis<input name="salePrice" type="number" value="${draft.salePrice ?? ""}" placeholder="optional" /></label>
            <label class="field">Durchgestrichener Preis<input name="compareAtPrice" type="number" value="${draft.compareAtPrice ?? ""}" placeholder="optional" /></label>
          </div>`;
        }
        return `<div class="grid-2">
          <label class="field">Angebotspreis<input name="salePrice" type="number" value="${draft.salePrice ?? ""}" placeholder="optional" /></label>
          <label class="field">Durchgestrichener Preis<input name="compareAtPrice" type="number" value="${draft.compareAtPrice ?? ""}" placeholder="optional" /></label>
          <label class="field">Ende<input name="availableUntil" type="datetime-local" value="${
            draft.availableUntil ? new Date(draft.availableUntil).toISOString().slice(0, 16) : ""
          }" /></label>
        </div>
        <p class="help">Leer = dauerhaft. Mit Datum = Countdown in der App.</p>`;
      }
      if (step === 4)
        return `<div class="grid-2">
          <label class="field">Max. Verkäufe gesamt<input name="maxTotalSales" type="number" value="${draft.maxTotalSales ?? ""}" placeholder="∞" /></label>
          <label class="field">Max. pro Nutzer<input name="maxPerUser" type="number" value="${draft.maxPerUser ?? ""}" placeholder="∞" /></label>
        </div>`;
      if (isVisibilityStep()) {
        const planOpts = rotationPlans.length
          ? rotationPlans
              .map(
                (p) =>
                  `<option value="${esc(p.id)}" ${
                    draft.rotationPlanId === p.id ? "selected" : ""
                  }>${esc(p.label || p.id)}</option>`
              )
              .join("")
          : `<option value="${esc(defaultPlanId)}">Standard-Rotation</option>`;
        const toLocal = (ms) =>
          ms ? new Date(ms).toISOString().slice(0, 16) : "";
        return `
          <label class="field">Im Shop
            <select name="shopPresence" id="wizShopPresence">
              <option value="fixed" ${draft.shopPresence === "fixed" ? "selected" : ""}>Dauerhaft (Fix — kein Zyklus)</option>
              <option value="cycle" ${draft.shopPresence === "cycle" ? "selected" : ""}>Im Rotationszyklus</option>
            </select>
            <span class="tip">Fix = sofort und dauerhaft im Itemshop (ohne Zeitfenster). Zyklus = kommt und geht laut Plan (≈03:00 Berlin).</span>
          </label>
          <div id="wizCycleBox" class="panel" style="margin-top:0.75rem;${
            draft.shopPresence === "cycle" ? "" : "display:none"
          }">
            <label class="field">Rotationsplan
              <select name="rotationPlanId">${planOpts}</select>
            </label>
            <div style="display:flex;flex-wrap:wrap;gap:0.75rem;margin-top:0.65rem;align-items:center">
              <label style="display:flex;gap:0.35rem;align-items:center;cursor:pointer">
                <input type="radio" name="cycleMode" value="random" ${
                  draft.cycleMode !== "manual" ? "checked" : ""
                } /> Zufälliges Fenster (Plan)
              </label>
              <label style="display:flex;gap:0.35rem;align-items:center;cursor:pointer">
                <input type="radio" name="cycleMode" value="manual" ${
                  draft.cycleMode === "manual" ? "checked" : ""
                } /> Fenster genau einstellen
              </label>
              <button type="button" class="btn secondary" id="wizCycleRandom">Zufall</button>
            </div>
            <div id="wizCycleManual" class="grid-2" style="margin-top:0.65rem;${
              draft.cycleMode === "manual" ? "" : "display:none"
            }">
              <label class="field">Von<input name="cycleFrom" type="datetime-local" value="${toLocal(
                draft.cycleFrom
              )}" /></label>
              <label class="field">Bis<input name="cycleUntil" type="datetime-local" value="${toLocal(
                draft.cycleUntil
              )}" /></label>
            </div>
            <p class="help" style="margin:0.55rem 0 0">
              Zufall würfelt das erste Fenster nach Plan-Regeln. „Genau“ setzt Von/Bis; danach läuft die Rotation weiter.
            </p>
          </div>
          <div style="margin-top:0.85rem;display:flex;flex-direction:column;gap:0.55rem">
            <label style="display:flex;gap:0.5rem;align-items:center;cursor:pointer">
              <input type="checkbox" name="marketSellable" ${
                draft.marketSellable ? "checked" : ""
              } /> Handelbar auf dem Spieler-Marktplatz
            </label>
            <label style="display:flex;gap:0.5rem;align-items:center;cursor:pointer">
              <input type="checkbox" name="lootboxEligible" ${
                draft.lootboxEligible ? "checked" : ""
              } /> Kann in der Lootbox erscheinen
            </label>
          </div>`;
      }
      const presenceLabel =
        draft.shopPresence === "cycle"
          ? draft.cycleMode === "manual"
            ? "Zyklus (Fenster manuell)"
            : "Zyklus (Zufallsfenster)"
          : "Dauerhaft im Shop";
      return `<div class="panel">
        <p><strong>${
          draft.source === "custom" && draft.imageDataUrl
            ? "✨ Eigenes Design"
            : esc(draft.itemId)
        }</strong> · ${esc(draft.isEventItem ? "Event-" + eventKindDe(draft.kind) : draft.kind)}</p>
        ${glyphPreviewHtml()}
        ${
          draft.isEventItem
            ? `<p class="muted">Event: ${esc(draft.eventId || "—")} · Jahr ${esc(
                String(draft.eventYear || "—")
              )}</p>`
            : ""
        }
        ${
          draft.visualConfig
            ? `<div class="theme-mini-preview" style="background:linear-gradient(160deg,${esc(
                draft.visualConfig.skyTop
              )},${esc(draft.visualConfig.skyBottom)})"></div>`
            : ""
        }
        <p>${esc(draft.label)} — ${draft.priceCoins} Coins</p>
        <p class="muted">Suche: ${esc(draft.searchText || "—")}</p>
        <p class="muted">${
          draft.isEventItem
            ? "→ Event-Shop + Itemshop im Zeitfenster · nicht in der Lootbox · nicht handelbar"
            : `→ ${presenceLabel}${
                draft.shopPresence === "cycle" && draft.rotationPlanId
                  ? ` · Plan ${esc(draft.rotationPlanId)}`
                  : ""
              } · ${draft.marketSellable ? "handelbar" : "nicht handelbar"} · ${
                draft.lootboxEligible ? "Lootbox ja" : "Lootbox nein"
              }`
        }</p>
      </div>`;
    }

    paint();
  }

  async function renderReports() {
    const [peer, pub, bugs, help] = await Promise.all([
      api("/admin/peer-reports").catch(() => ({ reports: [] })),
      api("/admin/public-reports").catch(() => ({ reports: [] })),
      api("/admin/bug-reports").catch(() => ({ reports: [] })),
      api("/admin/help-messages").catch(() => ({ messages: [] })),
    ]);
    const bugRows = bugs.reports || [];
    const helpRows = help.messages || [];
    const rows = [...(peer.reports || []), ...(pub.reports || [])];
    const fmtWhen = (ms) => {
      try {
        return new Date(ms).toLocaleString("de-DE", { dateStyle: "short", timeStyle: "short" });
      } catch {
        return "—";
      }
    };
    content.innerHTML = `
      <div class="panel">
        <h3 style="font-family:var(--display);margin:0 0 0.35rem">Bug-Meldungen</h3>
        <p class="help" style="margin:0 0 0.75rem">
          Hilfreich = User kann unter Sozial → Freunde +10 Coins abholen (servergeprüft).
          Profil / Nutzer öffnet den Spieler zum Einstellen.
        </p>
        <div class="list">
          ${
            bugRows.length
              ? bugRows
                  .map(
                    (r) => `<div class="list-item" style="margin-bottom:0.65rem">
              <div class="row" style="align-items:flex-start;gap:0.75rem;flex-wrap:wrap">
                <div style="flex:1;min-width:12rem">
                  <strong>${esc(r.nickname || "Jemand")}</strong>
                  ${r.status === "helpful" ? `<span class="badge" style="margin-left:0.35rem">hilfreich</span>` : ""}
                  <div class="muted" style="font-size:0.8rem;margin-top:0.2rem">${esc(fmtWhen(r.createdAt))} · ${esc(r.locationLabel || "")} · ${esc(r.visibilityLabel || "")}${r.reproducible ? " · reproduzierbar" : ""}</div>
                  <p style="margin:0.45rem 0 0;white-space:pre-wrap">${esc(r.description || "")}</p>
                  ${
                    r.imageUrl
                      ? `<a href="${esc(r.imageUrl)}" target="_blank" rel="noopener" style="display:inline-block;margin-top:0.5rem">
                          <img src="${esc(r.imageUrl)}" alt="" style="max-width:100%;max-height:180px;border-radius:10px;border:1px solid rgba(255,255,255,0.1)" />
                        </a>`
                      : ""
                  }
                  ${
                    r.videoUrl
                      ? `<div style="margin-top:0.4rem"><a href="${esc(r.videoUrl)}" target="_blank" rel="noopener">Video öffnen</a></div>`
                      : ""
                  }
                </div>
                <div class="actions" style="flex-direction:column;align-items:stretch;gap:0.35rem">
                  ${r.userId ? `<button class="btn secondary btn-xs" data-bug-user="${esc(r.userId)}" data-bug-nick="${esc(r.nickname || "")}">Nutzer</button>` : ""}
                  ${
                    r.status === "open"
                      ? `<button class="btn btn-xs" data-bug-helpful="${esc(r.id)}">Hilfreich · +10 Coins &amp; schließen</button>`
                      : ""
                  }
                  <button class="btn danger btn-xs" data-bug-del="${esc(r.id)}">Löschen</button>
                </div>
              </div>
            </div>`
                  )
                  .join("")
              : `<p class="muted">Keine Bug-Meldungen.</p>`
          }
        </div>
      </div>

      <div class="panel">
        <h3>Hilfe-Anfragen</h3>
        <div class="list">
          ${
            helpRows.length
              ? helpRows
                  .map(
                    (m) => `<div class="list-item">
              <div class="row">
                <div style="flex:1">
                  <strong>${esc(m.nickname || "Jemand")}</strong>
                  <div class="muted" style="font-size:0.8rem">${esc(fmtWhen(m.createdAt))}</div>
                  <p style="margin:0.35rem 0 0;white-space:pre-wrap">${esc(m.message || "")}</p>
                </div>
                <div class="actions">
                  ${m.userId ? `<button class="btn secondary btn-xs" data-bug-user="${esc(m.userId)}" data-bug-nick="${esc(m.nickname || "")}">Nutzer</button>` : ""}
                  <button class="btn danger btn-xs" data-help-del="${esc(m.id)}">Löschen</button>
                </div>
              </div>
            </div>`
                  )
                  .join("")
              : `<p class="muted">Keine Hilfe-Anfragen.</p>`
          }
        </div>
      </div>

      <div class="panel">
        <h3>Bild- & Lobby-Meldungen</h3>
        <p class="help">„Behalten“ schließt die Meldung, „Löschen“ entfernt den Inhalt.</p>
        <div class="list">
          ${
            rows.length
              ? rows
                  .map(
                    (r) => `<div class="list-item">
              <div class="row">
                <div>
                  <strong>${esc(r.targetNickname || r.nameLine || r.id || r.publicId || "report")}</strong>
                  <div class="muted">${esc(r.lobbyName || r.hostNickname || r.status || "")} · von ${esc(r.reporterNickname || "")}</div>
                </div>
                <div class="actions">
                  ${
                    r.id
                      ? `<button class="btn secondary" data-keep="${esc(r.id)}" data-type="${r.publicId ? "public" : "peer"}">Behalten</button>
                         <button class="btn danger" data-del="${esc(r.id)}" data-type="${r.publicId ? "public" : "peer"}">Löschen</button>`
                      : ""
                  }
                </div>
              </div>
            </div>`
                  )
                  .join("")
              : `<p class="muted">Keine Meldungen.</p>`
          }
        </div>
      </div>`;

    content.querySelectorAll("[data-bug-helpful]").forEach((b) => {
      b.onclick = async () => {
        const id = b.getAttribute("data-bug-helpful");
        try {
          const res = await api(`/admin/bug-reports/${encodeURIComponent(id)}/helpful`, {
            method: "POST",
            body: "{}",
          });
          const c = Number(res?.coins) || 0;
          if (c > 0) {
            /* ok */
          }
          renderReports();
        } catch (err) {
          alert(err?.message || "Fehlgeschlagen");
        }
      };
    });
    content.querySelectorAll("[data-bug-del]").forEach((b) => {
      b.onclick = async () => {
        const id = b.getAttribute("data-bug-del");
        if (!confirm("Bug-Meldung löschen?")) return;
        try {
          await api(`/admin/bug-reports/${encodeURIComponent(id)}/delete`, {
            method: "POST",
            body: "{}",
          });
          renderReports();
        } catch (err) {
          alert(err?.message || "Fehlgeschlagen");
        }
      };
    });
    content.querySelectorAll("[data-help-del]").forEach((b) => {
      b.onclick = async () => {
        const id = b.getAttribute("data-help-del");
        try {
          await api(`/admin/help-messages/${encodeURIComponent(id)}/delete`, {
            method: "POST",
            body: "{}",
          });
          renderReports();
        } catch (err) {
          alert(err?.message || "Fehlgeschlagen");
        }
      };
    });
    content.querySelectorAll("[data-bug-user]").forEach((b) => {
      b.onclick = () => {
        const id = b.getAttribute("data-bug-user");
        if (!id) return;
        state.userFocusId = id;
        goTab("users", { userId: id });
      };
    });
    content.querySelectorAll("[data-keep]").forEach((b) => {
      b.onclick = async () => {
        const type = b.getAttribute("data-type");
        const id = b.getAttribute("data-keep");
        await api(`/admin/${type === "public" ? "public" : "peer"}-reports/${encodeURIComponent(id)}/keep`, {
          method: "POST",
          body: "{}",
        });
        renderReports();
      };
    });
    content.querySelectorAll("[data-del]").forEach((b) => {
      b.onclick = async () => {
        const type = b.getAttribute("data-type");
        const id = b.getAttribute("data-del");
        await api(`/admin/${type === "public" ? "public" : "peer"}-reports/${encodeURIComponent(id)}/delete`, {
          method: "POST",
          body: "{}",
        });
        renderReports();
      };
    });
  }

  async function renderCodes() {
    const data = await api("/admin/vouchers");
    const list = data.vouchers || data.codes || [];
    content.innerHTML = `
      <div class="panel">
        <h3>Neuen Code erstellen</h3>
        <p class="help">Coins und/oder Items an Spieler vergeben. Code einmalig oder mehrfach einlösbar.</p>
        <form id="codeForm" class="grid-2">
          <label class="field">Code (leer = auto)<input name="code" /></label>
          <label class="field">Coins<input name="coins" type="number" value="10" /></label>
          <label class="field">Max. Einlösungen<input name="maxRedeems" type="number" value="1" /></label>
          <label class="field">Notiz<input name="note" placeholder="optional" /></label>
          <div class="actions" style="grid-column:1/-1">
            <button class="btn" type="submit">Erstellen</button>
          </div>
        </form>
      </div>
      <div class="panel">
        <h3>Bestehende Codes</h3>
        <div class="list">
          ${
            list.length
              ? list
                  .map(
                    (v) => `<div class="list-item">
              <strong>${esc(v.code)}</strong>
              <div class="muted">${v.coins || 0} Coins · ${v.redeemCount || v.redeemed || 0}/${v.maxRedeems ?? "∞"} · aktiv</div>
              <div class="actions"><button class="btn ghost" data-revoke="${esc(v.code)}">Widerrufen</button></div>
            </div>`
                  )
                  .join("")
              : `<p class="muted">Noch keine Codes.</p>`
          }
        </div>
      </div>`;
    $("codeForm").onsubmit = async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      await api("/admin/vouchers", {
        method: "POST",
        body: JSON.stringify({
          code: String(fd.get("code") || "").trim() || undefined,
          coins: Number(fd.get("coins") || 0),
          maxRedeems: Number(fd.get("maxRedeems") || 1),
          note: String(fd.get("note") || "").trim(),
        }),
      });
      renderCodes();
    };
    content.querySelectorAll("[data-revoke]").forEach((b) => {
      b.onclick = async () => {
        await api(`/admin/vouchers/${encodeURIComponent(b.getAttribute("data-revoke"))}/revoke`, {
          method: "POST",
          body: "{}",
        });
        renderCodes();
      };
    });
  }

  async function renderUsers() {
    content.innerHTML = `
      <div class="panel">
        <h3>Nutzer suchen</h3>
        <p class="help">Spitzname oder User-ID. Danach Coins, Nick oder Ban anpassen.</p>
        <div class="toolbar">
          <input id="userQ" placeholder="Suche…" style="min-width:220px" />
          <button class="btn secondary" id="userSearch">Suchen</button>
        </div>
        <div id="userResults" class="list"></div>
      </div>`;
    $("userSearch").onclick = async () => {
      const q = $("userQ").value.trim();
      const data = await api("/admin/users/search?q=" + encodeURIComponent(q));
      const users = data.users || [];
      $("userResults").innerHTML = users.length
        ? users
            .map(
              (u) => `<div class="list-item">
            <div class="row">
              <div>
                <strong>${esc(u.nickname)}</strong>
                <div class="muted">${esc(u.userId)} · ${u.coins || 0} Coins · ${esc(u.role || "user")}</div>
              </div>
            </div>
            <div class="actions">
              <input data-coins-for="${esc(u.userId)}" type="number" placeholder="±Coins" style="width:110px" />
              <button class="btn secondary" data-coins="${esc(u.userId)}">Coins setzen/ändern</button>
              <input data-nick-for="${esc(u.userId)}" placeholder="Neuer Nick" style="width:140px" />
              <button class="btn ghost" data-nick="${esc(u.userId)}">Nick</button>
              <button class="btn danger" data-ban="${esc(u.userId)}">${u.banned ? "Entbannen" : "Bannen"}</button>
            </div>
          </div>`
            )
            .join("")
        : `<p class="muted">Keine Treffer.</p>`;
      $("userResults").querySelectorAll("[data-coins]").forEach((b) => {
        b.onclick = async () => {
          const id = b.getAttribute("data-coins");
          const inp = $("userResults").querySelector(`[data-coins-for="${CSS.escape(id)}"]`);
          await api(`/admin/users/${encodeURIComponent(id)}/coins`, {
            method: "POST",
            body: JSON.stringify({ delta: Number(inp.value || 0) }),
          });
          alert("Coins aktualisiert");
        };
      });
      $("userResults").querySelectorAll("[data-nick]").forEach((b) => {
        b.onclick = async () => {
          const id = b.getAttribute("data-nick");
          const inp = $("userResults").querySelector(`[data-nick-for="${CSS.escape(id)}"]`);
          await api(`/admin/users/${encodeURIComponent(id)}/nickname`, {
            method: "POST",
            body: JSON.stringify({ nickname: inp.value }),
          });
          alert("Nick aktualisiert");
        };
      });
      $("userResults").querySelectorAll("[data-ban]").forEach((b) => {
        b.onclick = async () => {
          const id = b.getAttribute("data-ban");
          await api(`/admin/users/${encodeURIComponent(id)}/ban`, {
            method: "POST",
            body: JSON.stringify({}),
          });
          $("userSearch").click();
        };
      });
    };
  }

  async function renderMods() {
    const data = await api("/admin/moderators");
    const mods = data.moderators || data.mods || [];
    content.innerHTML = `
      <div class="panel">
        <h3>Moderator einladen</h3>
        <p class="help">Spitzname des bestehenden Accounts eingeben.</p>
        <div class="toolbar">
          <input id="modNick" placeholder="Spitzname" />
          <button class="btn" id="modInvite">Einladen</button>
        </div>
      </div>
      <div class="panel">
        <h3>Aktuelle Mods</h3>
        <div class="list">
          ${
            mods.length
              ? mods
                  .map(
                    (m) => `<div class="list-item">
              <strong>${esc(m.nickname)}</strong>
              <div class="muted">${esc(m.userId)}</div>
              <div class="actions">
                <button class="btn danger" data-rm="${esc(m.userId)}">Entfernen</button>
              </div>
            </div>`
                  )
                  .join("")
              : `<p class="muted">Keine Mods.</p>`
          }
        </div>
      </div>`;
    $("modInvite").onclick = async () => {
      await api("/admin/moderators/invite", {
        method: "POST",
        body: JSON.stringify({ nickname: $("modNick").value.trim() }),
      });
      renderMods();
    };
    content.querySelectorAll("[data-rm]").forEach((b) => {
      b.onclick = async () => {
        await api(`/admin/moderators/${encodeURIComponent(b.getAttribute("data-rm"))}/remove`, {
          method: "POST",
          body: "{}",
        });
        renderMods();
      };
    });
  }

  async function renderMarketSettings() {
    const s = await api("/admin/market-settings");
    const capMin = Number(s.achievementDailyCapMin ?? 0);
    const capMax = Number(s.achievementDailyCapMax ?? 500);
    content.innerHTML = `
      <div class="panel">
        <h3>Markt-Preisfenster</h3>
        <p class="help">Über wie viele Tage Verkaufspreise für die Spanne „ab X Coins“ berechnet werden.</p>
        <div class="toolbar">
          <input id="priceDays" type="number" min="1" max="90" value="${esc(s.priceWindowDays ?? 14)}" />
          <button class="btn" id="saveMarket">Speichern</button>
        </div>
      </div>
      <div class="panel" style="margin-top:16px">
        <h3>Erfolge · Coins pro Tag</h3>
        <p class="help">
          Maximal so viele Coins darf ein Spieler pro Tag durch Erfolge abholen.
          Ist das Limit teilweise erreicht, wird nur noch die Differenz gutgeschrieben —
          der Erfolg gilt trotzdem als abgeholt. Bei 0 gibt es keine Erfolgs-Coins mehr.
        </p>
        <div class="toolbar">
          <input id="achDailyCap" type="number" min="${capMin}" max="${capMax}"
            value="${esc(s.achievementDailyCap ?? 12)}" />
          <button class="btn" id="saveAchCap">Speichern</button>
        </div>
        <p class="help">Erlaubt: ${capMin}–${capMax} · Aktuell: ${esc(s.achievementDailyCap ?? 12)}</p>
      </div>`;
    $("saveMarket").onclick = async () => {
      await api("/admin/market-settings", {
        method: "PUT",
        body: JSON.stringify({ priceWindowDays: Number($("priceDays").value || 14) }),
      });
      alert("Gespeichert");
    };
    $("saveAchCap").onclick = async () => {
      const n = Number($("achDailyCap").value);
      await api("/admin/market-settings", {
        method: "PUT",
        body: JSON.stringify({ achievementDailyCap: n }),
      });
      alert("Erfolgs-Tageslimit gespeichert");
      renderMarketSettings();
    };
  }

  async function renderMaintenanceBericht() {
    const data = await api("/admin/maintenance/reports");
    const reports = data.reports || [];
    content.innerHTML = `
      <div class="panel">
        <h3>Wartungsbericht</h3>
        <p class="help">Jede Nacht 02:59–03:09 (Berlin): Backup (max. 10), Shop-Zyklus, Health-Check. Hier nur den Bericht manuell erfassen — ohne Shop umzubauen.</p>
        <div class="actions">
          <button class="btn" id="maintManual">Bericht jetzt erfassen</button>
        </div>
      </div>
      <div class="panel" id="maintDetail" style="display:none">
        <div class="actions" style="justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap">
          <h3 id="maintDetailTitle" style="margin:0">Bericht</h3>
          <button class="btn ghost" id="maintCopy">Alles kopieren</button>
        </div>
        <pre id="maintCopyText" class="codeblock" style="white-space:pre-wrap;max-height:420px;overflow:auto;margin-top:12px"></pre>
        <div id="maintEntries" style="margin-top:12px"></div>
      </div>
      <div class="panel">
        <h3>Letzte Berichte</h3>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Zeit</th><th>Nacht</th><th>Quelle</th><th>Status</th><th>Summary</th><th></th></tr></thead>
            <tbody>
              ${
                reports.length
                  ? reports
                      .map(
                        (r) => `<tr>
                    <td>${esc(new Date(r.createdAt).toLocaleString("de-DE"))}</td>
                    <td>${esc(r.nightKey || "—")}</td>
                    <td>${esc(r.source || "—")}</td>
                    <td><span class="badge ${
                      r.status === "error" ? "bad" : r.status === "warn" ? "warn" : "ok"
                    }">${esc(r.status || "?")}</span></td>
                    <td>${esc(r.summary || "")}</td>
                    <td><button class="btn ghost tiny" data-maint-id="${esc(r.id)}">Öffnen</button></td>
                  </tr>`
                      )
                      .join("")
                  : `<tr><td colspan="6" class="muted">Noch keine Berichte.</td></tr>`
              }
            </tbody>
          </table>
        </div>
      </div>`;

    async function openReport(id) {
      const res = await api(`/admin/maintenance/reports/${encodeURIComponent(id)}`);
      const report = res.report;
      if (!report) return;
      const detail = $("maintDetail");
      detail.style.display = "block";
      $("maintDetailTitle").textContent = `${report.nightKey || "Bericht"} · ${report.status}`;
      $("maintCopyText").textContent = report.copyText || "";
      const entries = Array.isArray(report.entries) ? report.entries : [];
      $("maintEntries").innerHTML = entries
        .map((e) => {
          const lvl = String(e.level || "info");
          return `<div class="card" style="margin-bottom:8px">
            <div class="k">[${esc(lvl.toUpperCase())}] ${esc(e.code || "")}</div>
            <div class="v" style="font-size:14px">${esc(e.message || "")}</div>
            ${
              e.recommendation
                ? `<p class="help" style="margin:6px 0 0">→ ${esc(e.recommendation)}</p>`
                : ""
            }
          </div>`;
        })
        .join("");
      $("maintCopy").onclick = async () => {
        const text = report.copyText || $("maintCopyText").textContent || "";
        try {
          await navigator.clipboard.writeText(text);
          alert("Bericht kopiert — kannst du der KI schicken.");
        } catch {
          $("maintCopyText").focus();
          $("maintCopyText").select?.();
          alert("Bitte Text manuell markieren und kopieren.");
        }
      };
    }

    $("maintManual").onclick = async () => {
      const res = await api("/admin/maintenance/report", {
        method: "POST",
        body: "{}",
      });
      if (res.report?.id) {
        alert("Bericht erfasst.");
        await renderMaintenanceBericht();
        await openReport(res.report.id);
      }
    };

    content.querySelectorAll("[data-maint-id]").forEach((btn) => {
      btn.onclick = () => openReport(btn.getAttribute("data-maint-id"));
    });
  }

  async function renderLive() {
    content.innerHTML = `
      <div class="panel">
        <h3>Live-Hinweis</h3>
        <p class="help">Kurze Nachricht, die in der App für alle sichtbar ist (z. B. Wartung, Event).</p>
        <label class="field">Text
          <textarea id="liveText" maxlength="280" placeholder="Hallo zusammen…"></textarea>
        </label>
        <div class="actions">
          <button class="btn" id="sendLive">Senden</button>
          <button class="btn ghost" id="clearLive">Aktiven Hinweis löschen</button>
        </div>
      </div>`;
    $("sendLive").onclick = async () => {
      await api("/admin/live-notice", {
        method: "POST",
        body: JSON.stringify({ message: $("liveText").value.trim() }),
      });
      alert("Hinweis gesendet");
    };
    $("clearLive").onclick = async () => {
      await api("/admin/live-notice", { method: "DELETE" });
      alert("Live-Hinweis gelöscht");
    };
  }

  $("logoutBtn").onclick = async () => {
    try {
      await api("/auth/logout", { method: "POST", body: "{}" });
    } catch {
      /* Session lokal trotzdem beenden */
    }
    setWebTicket("");
    setSession("", null);
    state.decoy = false;
    showLogin();
    wireAuthCodeUi();
  };
  $("refreshBtn").onclick = () => loadTab(state.tab);

  async function renderDecoyUsers() {
    pageTitle.textContent = "Nutzer";
    pageHint.textContent = "Nutzerverwaltung";
    try {
      const data = await api("/admin/decoy/users");
      const users = data.users || [];
      content.innerHTML = `
        <div class="panel">
          <h3 style="margin:0 0 0.75rem">Nutzer</h3>
          <div class="grid-2" style="gap:0.55rem">
            ${users
              .map(
                (u) => `<button type="button" class="btn secondary decoy-user" data-id="${esc(u.id)}" style="justify-content:flex-start;text-align:left">
                  <strong>${esc(u.nickname)}</strong>
                  <span class="muted" style="margin-left:0.5rem">${esc(u.coins)} Coins</span>
                </button>`
              )
              .join("")}
          </div>
          <p class="help" id="decoyErr" hidden style="color:#f88;margin-top:0.75rem"></p>
        </div>`;
      content.querySelectorAll(".decoy-user").forEach((btn) => {
        btn.onclick = async () => {
          const err = $("decoyErr");
          try {
            await api("/admin/decoy/act", {
              method: "POST",
              body: JSON.stringify({ userId: btn.getAttribute("data-id"), action: "view" }),
            });
          } catch (e) {
            if (err) {
              err.hidden = false;
              err.textContent = e?.message || "Internal Server Error";
            } else {
              alert(e?.message || "Internal Server Error");
            }
          }
        };
      });
    } catch (e) {
      content.innerHTML = `<p class="error">${esc(e?.message || "Internal Server Error")}</p>`;
    }
  }

  window.LuvAdmHost = {
    api,
    esc,
    $,
    openModal,
    closeModal,
    content,
    state,
    goTab,
  };

  // boot
  wireAuthCodeUi();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", resume);
  } else {
    resume();
  }
})();

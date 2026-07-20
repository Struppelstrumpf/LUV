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
    shopView: "katalog", // katalog | kalender
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
    { id: "reports", label: "Meldungen", hint: "Gemeldete Lobby-/Galerie-Bilder prüfen.", perm: "reports.view" },
    { id: "codes", label: "Codes", hint: "Gutscheincodes erstellen und widerrufen.", perm: "codes.view" },
    { id: "users", label: "Nutzer", hint: "Vollprofil: Coins, Erfolge, Logs, Lobbys, Verwarnungen, Streak.", perm: "gm.search" },
    { id: "mods", label: "Moderatoren", hint: "Mods einladen und Rechte setzen.", perm: "mods.manage", adminOnly: true },
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
    state.webAuthTicket = ticket || "";
    if (ticket) sessionStorage.setItem(TICKET_KEY, ticket);
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
    const s = String(raw || "")
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, "")
      .slice(0, 7);
    if (s.length <= 2) return s;
    if (s.length <= 5) return `${s.slice(0, 2)}-${s.slice(2)}`;
    return `${s.slice(0, 2)}-${s.slice(2, 5)}-${s.slice(5)}`;
  }

  function wireAuthCodeUi() {
    const input = $("authCodeInput");
    const submit = $("authCodeSubmit");
    const reset = $("authCodeReset");
    if (input && !input._luvWired) {
      input._luvWired = true;
      input.addEventListener("input", () => {
        const formatted = formatAuthCodeInput(input.value);
        if (input.value !== formatted) input.value = formatted;
      });
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
        if (code.replace(/-/g, "").length !== 7) {
          showLoginError("Code im Format XX-XXX-XX eingeben.");
          return;
        }
        submit.disabled = true;
        try {
          const res = await api("/admin/web-auth/redeem", {
            method: "POST",
            body: JSON.stringify({ code }),
          });
          if (!res?.ticket) throw new Error("Kein Ticket erhalten");
          setWebTicket(res.ticket);
          showGoogleGate();
          initGoogle();
        } catch (err) {
          showLoginError(err?.message || "Code ungültig");
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
    const SHOP_CATS = [
      { id: "emojis", label: "Emojis", emoji: "😊", hint: "Reaktionen in der Leinwand" },
      { id: "stickers", label: "Sticker", emoji: "🏷️", hint: "Profil-Sticker" },
      { id: "themes", label: "Hintergründe", emoji: "🖼️", hint: "Profil-Hintergründe" },
      { id: "pets", label: "Begleiter", emoji: "🐾", hint: "Avatar-Begleiter" },
    ];
    const SOURCE_FILTERS = [
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
    };

    const q = new URLSearchParams();
    if (state.shopQ) q.set("q", state.shopQ);
    if (state.shopKind) q.set("kind", state.shopKind);
    if (state.shopSource) q.set("source", state.shopSource);
    const data = await api("/admin/items/universe?" + q.toString());
    state.shopUniverse = data.items || [];

    // Shop-Katalog parallel für Bearbeiten/Preis/Bild
    const shopData = await api(
      "/admin/shop/items?" +
        new URLSearchParams(state.shopQ ? { q: state.shopQ } : {}).toString()
    );
    state.shopItems = shopData.items || [];
    const shopByKey = Object.fromEntries(
      state.shopItems.map((it) => [`${it.kind}:${it.itemId}`, it])
    );

    const byKind = Object.fromEntries(SHOP_CATS.map((c) => [c.id, []]));
    for (const it of state.shopUniverse) {
      if (!byKind[it.kind]) byKind[it.kind] = [];
      byKind[it.kind].push(it);
    }

    const activeKind = state.shopKind || "";
    const visibleCats = activeKind
      ? SHOP_CATS.filter((c) => c.id === activeKind)
      : SHOP_CATS;

    function sourceBadges(sources) {
      return (sources || [])
        .map((s) => `<span class="badge src-${esc(s)}">${esc(SRC_LABEL[s] || s)}</span>`)
        .join(" ");
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
            : `<span class="shop-card-emoji">${esc(it.emoji || it.itemId)}</span>`;
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
      const lootBtn = it.marketLocked
        ? ""
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
          <div class="muted mono shop-card-id">${esc(it.itemId)}</div>
          <div class="shop-card-price-row">${price}
            ${shopOff ? '<span class="badge off">Shop aus</span>' : ""}
            ${
              !it.sources.includes("shop")
                ? '<span class="badge src-noshop">nicht im Shop</span>'
                : ""
            }
          </div>
          <div class="shop-card-meta source-row">${sourceBadges(it.sources)}</div>
          ${achLinks ? `<div class="ach-chip-row">${achLinks}</div>` : ""}
        </div>
        <div class="shop-card-actions">
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
            <button type="button" class="btn teal" data-new-kind="${cat.id}">＋ Neu</button>
          </header>
          <p class="help">Keine Einträge für diesen Filter.</p>
        </section>`;
      }
      if (!items.length) return "";
      return `<section class="shop-section" id="cat-${cat.id}">
        <header class="shop-section-head">
          <div>
            <h3>${cat.emoji} ${cat.label}</h3>
            <p class="help" style="margin:0.2rem 0 0">${cat.hint} · ${items.length} Items</p>
          </div>
          <button type="button" class="btn teal" data-new-kind="${cat.id}">＋ Neu</button>
        </header>
        <div class="shop-card-grid">${items.map(cardHtml).join("")}</div>
      </section>`;
    }

    const total = state.shopUniverse.length;
    content.innerHTML = `
      <div class="panel shop-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Items</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:40rem">
              Alle Items aus Shop, Erfolgen und Codes. Farben = Herkunft.
              🏪 = am Marktplatz handelbar, 🚫 = gesperrt, 🔒 = dauerhaft gesperrt.
              ${total} Items${state.shopQ ? ` · „${esc(state.shopQ)}“` : ""}.
            </p>
          </div>
          <button class="btn teal" id="shopWizard">＋ Neues Shop-Item</button>
        </div>

        <div class="shop-cats" id="shopViews">
          <button type="button" class="shop-cat on" data-shop-view="katalog">Katalog</button>
          <button type="button" class="shop-cat" data-shop-view="kalender">Kalender</button>
        </div>

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
          <input id="shopQ" placeholder="Suchen (Name, ID, Quelle)…" value="${esc(state.shopQ)}" />
          <button class="btn secondary" id="shopFilter">Suchen</button>
          ${state.shopQ || state.shopSource ? `<button class="btn ghost" id="shopClearQ">Zurücksetzen</button>` : ""}
        </div>
        <div class="source-legend">
          <span class="badge src-shop">Shop</span>
          <span class="badge src-achievement">Erfolg</span>
          <span class="badge src-code">Code</span>
          <span class="badge src-starter">Starter</span>
          <span class="badge src-marriage">Ehe</span>
        </div>
      </div>

      <div class="shop-sections">
        ${visibleCats.map(sectionHtml).join("") || `<div class="panel"><p class="help">Keine Items gefunden.</p></div>`}
      </div>`;

    $("shopWizard").onclick = () => openWizard();
    content.querySelectorAll("#shopViews [data-shop-view]").forEach((btn) => {
      btn.onclick = () => {
        state.shopView = btn.getAttribute("data-shop-view") || "katalog";
        loadTab("shop");
      };
    });
    content.querySelectorAll("[data-new-kind]").forEach((btn) => {
      btn.onclick = () => openWizard(btn.getAttribute("data-new-kind"));
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
        await api(path, { method: "POST", body: "{}" });
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

    function kindDotsHtml(byKind, total) {
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
            `<span class="cal-kdot cal-k-${k}" title="${esc(label)}: ${byKind[k]}">${letter}<i>${byKind[k]}</i></span>`
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
          ? `${n} Items aktiv${tipParts.length ? " · " + tipParts.join(", ") : ""} — Klick für Liste`
          : "Keine zeitlich begrenzten Items";

        let body;
        if (n <= 0) {
          body = `<span class="cal-cell-empty">—</span>`;
        } else if (n <= 4) {
          // Wenige: Glyphs + Restzahl
          const chips = (d.items || [])
            .slice(0, 3)
            .map((it) => `<span class="cal-chip" title="${esc(it.label || it.itemId)}">${esc(it.emoji || "·")}</span>`)
            .join("");
          const more = n > 3 ? `<span class="cal-more">+${n - 3}</span>` : "";
          body = `<span class="cal-cell-chips">${chips}${more}</span>`;
        } else {
          // Viele: Kategorie-Dots + große Zahl — skaliert auch bei 50+ Items
          body = `<span class="cal-cell-dense">
            <span class="cal-cell-big">${n}</span>
            <span class="cal-kdots">${kindDotsHtml(byKind, n)}</span>
          </span>`;
        }

        return `<button type="button" class="cal-cell ${densClass(n)} ${isToday ? "is-today" : ""} ${isSel ? "is-sel" : ""} ${n ? "has-items" : ""}" data-cal-day="${esc(d.date)}" title="${esc(tip)}">
          <span class="cal-cell-top">
            <span class="cal-cell-num">${d.day}</span>
            ${n ? `<span class="cal-cell-pill">${n}</span>` : ""}
          </span>
          ${body}
          <span class="cal-dens-bar" aria-hidden="true"></span>
        </button>`;
      });
      return `<div class="cal-weekdays">${["Mo","Di","Mi","Do","Fr","Sa","So"].map((w) => `<div>${w}</div>`).join("")}</div>
        <div class="cal-grid">${pads.join("")}${cells.join("")}</div>
        <p class="help cal-legend" style="margin:0.55rem 0 0">
          Wenige Items: Vorschau-Glyphs · Viele Items: Zahl + Kategorien (S/H/B/E). Details immer per Klick rechts.
        </p>`;
    }

    function dayPanelHtml() {
      if (!selectedDay) {
        return `<div class="cal-side panel">
          <h4 style="margin:0 0 0.4rem">Tag wählen</h4>
          <p class="help">Tippe einen Tag im Kalender an, um die Items an diesem Tag zu sehen oder ein Fenster für mehrere Items zu setzen.</p>
          <div class="cal-plan-preview">
            <h4 style="margin:0.8rem 0 0.35rem">Aktive Rotation</h4>
            ${
              plans.length
                ? plans
                    .map((p) => {
                      const act = (p.activeNow || [])
                        .map((a) => `${a.emoji || ""} ${a.label || a.itemId}`)
                        .join(", ") || "—";
                      return `<div class="cal-plan-mini ${p.enabled ? "" : "is-off"}">
                        <strong>${esc(p.label)}</strong>
                        <div class="muted" style="font-size:0.8rem">${esc(p.cycleLength)} ${esc(p.cycleUnit)} Zyklus · ${esc(p.activeLength)} ${esc(p.activeUnit)} im Shop · ${p.itemCount || 0} Items</div>
                        <div style="margin-top:0.25rem">${esc(act)}</div>
                      </div>`;
                    })
                    .join("")
                : `<p class="help">Noch keine Rotationspläne.</p>`
            }
          </div>
        </div>`;
      }
      const KIND_LABEL = {
        stickers: "Sticker",
        themes: "Hintergründe",
        pets: "Begleiter",
        emojis: "Emojis",
      };
      const byKind = {};
      for (const it of selectedDay.items || []) {
        const k = String(it.kind || "other");
        if (!byKind[k]) byKind[k] = [];
        byKind[k].push(it);
      }
      const kindOrder = ["stickers", "themes", "pets", "emojis"];
      const kindKeys = [
        ...kindOrder.filter((k) => byKind[k]?.length),
        ...Object.keys(byKind).filter((k) => !kindOrder.includes(k)),
      ];
      const groupsHtml = kindKeys.length
        ? kindKeys
            .map((k) => {
              const rows = byKind[k]
                .map(
                  (it) => `<li class="cal-day-row" data-cal-q="${esc(
                    `${it.label || ""} ${it.itemId || ""} ${it.emoji || ""} ${it.kind || ""}`.toLowerCase()
                  )}">
                    <span class="cal-emoji-inline">${esc(it.emoji || "·")}</span>
                    <span class="cal-day-meta">
                      <strong>${esc(it.label || it.itemId)}</strong>
                      <span class="muted">${it.priceCoins ?? "—"}💰${
                        it.rotationPlanId ? " · ↻" : ""
                      }</span>
                    </span>
                  </li>`
                )
                .join("");
              return `<div class="cal-day-group" data-kind="${esc(k)}">
                <div class="cal-day-group-h">${esc(KIND_LABEL[k] || k)} <span class="muted">${byKind[k].length}</span></div>
                <ul class="cal-day-items">${rows}</ul>
              </div>`;
            })
            .join("")
        : `<p class="muted">Keine zeitlich begrenzten Items</p>`;
      return `<div class="cal-side panel">
        <h4 style="margin:0 0 0.35rem">${esc(selectedDay.date)}</h4>
        <p class="help" style="margin:0 0 0.45rem">${selectedDay.count || 0} Items mit Zeitfenster an diesem Tag</p>
        <label class="field cal-day-filter">
          <input type="search" id="calDayFilter" placeholder="Im Tag filtern…" autocomplete="off" />
        </label>
        <div class="cal-day-groups" id="calDayGroups">${groupsHtml}</div>
        <hr class="cal-hr" />
        <h4 style="margin:0 0 0.35rem">Fenster für Auswahl setzen</h4>
        <p class="help">Unten unter „Items“ anhaken, dann hier speichern — oder einzelne IDs eintragen.</p>
        <form id="calBatchForm" class="cal-batch-form">
          <label class="field">Item-Keys (kind:id, kommagetrennt)
            <textarea name="keys" rows="2" placeholder="emojis:💎, pets:🦄">${esc(
              Object.keys(state.calPick || {})
                .filter((k) => state.calPick[k])
                .join(", ")
            )}</textarea>
          </label>
          <div class="grid-2">
            <label class="field">Ab
              <input type="datetime-local" name="availableFrom" value="${esc(selectedDay.date)}T00:00" />
            </label>
            <label class="field">Bis
              <input type="datetime-local" name="availableUntil" value="${esc(selectedDay.date)}T23:59" />
            </label>
          </div>
          <label class="field" style="flex-direction:row;align-items:center;gap:0.5rem;margin-top:0.4rem">
            <input type="checkbox" name="enabled" checked /> Im Shop aktiv
          </label>
          <div class="actions" style="margin-top:0.6rem">
            <button type="submit" class="btn">Auf ${pickCount || "…"} Items anwenden</button>
          </div>
        </form>
      </div>`;
    }

    function plansHtml() {
      const fmtWin = (ms) => {
        if (!ms) return "—";
        try {
          return new Date(ms).toLocaleDateString("de-DE", { day: "2-digit", month: "short" });
        } catch {
          return "—";
        }
      };
      return `<div class="cal-plans">
        <div class="panel cal-rot-explain">
          <h4 style="margin:0 0 0.5rem">So funktioniert die Rotation</h4>
          <ol class="cal-rot-steps">
            <li><strong>Alle Items</strong> (außer Starter wie Wiese / Basis-Emojis) rotieren.</li>
            <li><strong>Zufällig im Shop:</strong> 3–14 Tage, dann Pause 7 Tage bis max. 3 Monate.</li>
            <li><strong>~50 % gleichzeitig</strong> im Shop — Starts sind versetzt.</li>
            <li>Jedes Item kommt regelmäßig wieder; nichts bleibt ewig ohne Pause (außer Starter).</li>
          </ol>
          <p class="help" style="margin:0.6rem 0 0">
            Im Monatskalender siehst du die Rotationstage. Unter Items kannst du einzelne dauerhaft
            aus der Rotation nehmen („Raus“) oder wieder aufnehmen.
          </p>
        </div>
        <div class="panel">
          <h4 style="margin:0 0 0.35rem">Aktive Pläne</h4>
          <div class="cal-plan-list">
            ${
              plans.length
                ? plans
                    .map((p) => {
                      const act = (p.activeNow || [])
                        .slice(0, 24)
                        .map((a) => esc(a.emoji || a.itemId))
                        .join(" ");
                      const members = p.members || [];
                      const memberRows = members
                        .slice(0, 40)
                        .map((m) => {
                          const key = `${m.kind}:${m.itemId}`;
                          return `<tr>
                            <td class="cal-mem-emoji">${esc(m.emoji || "·")}</td>
                            <td><strong>${esc(m.label || m.itemId)}</strong>
                              <div class="muted mono" style="font-size:0.72rem">${esc(key)}</div></td>
                            <td>${m.active ? '<span class="badge cal-st-window">jetzt im Shop</span>' : '<span class="badge cal-st-scheduled">Pause</span>'}</td>
                            <td class="muted" style="white-space:nowrap">${fmtWin(m.availableFrom)} → ${fmtWin(m.availableUntil)}</td>
                            <td><button type="button" class="btn secondary btn-xs" data-leave-rot="${esc(m.kind)}|${esc(m.itemId)}">Raus</button></td>
                          </tr>`;
                        })
                        .join("");
                      return `<article class="cal-plan-card ${p.enabled ? "" : "is-off"}">
                        <div class="cal-plan-head">
                          <strong>${esc(p.label)}</strong>
                          <span class="badge ${p.enabled ? "cal-st-window" : "cal-st-off"}">${p.enabled ? "an" : "aus"}</span>
                        </div>
                        <p class="cal-plan-explain">${esc(p.explain || "")}</p>
                        <div class="cal-plan-stats">
                          <div><span class="muted">Items</span><strong>${p.itemCount || 0}</strong></div>
                          <div><span class="muted">Jetzt im Shop</span><strong>${p.activeCount ?? (p.activeNow || []).length}</strong></div>
                          <div><span class="muted">Anteil</span><strong>${p.activeSharePct ?? "—"} %</strong></div>
                          <div><span class="muted">Auswahl</span><strong>${
                            p.mode === "price" ? `Preis ≥ ${p.priceMin ?? 0}` : "Manuell"
                          }</strong></div>
                        </div>
                        <div class="cal-plan-now" title="Gerade im Shop">${act || "<span class='muted'>gerade keines</span>"}</div>
                        ${
                          memberRows
                            ? `<details class="cal-mem-details" ${p.id === "expensive_3m_week" ? "open" : ""}>
                          <summary>Alle Items in diesem Plan (${members.length})</summary>
                          <div class="cal-mem-scroll">
                            <table class="cal-mem-table">
                              <thead><tr><th></th><th>Item</th><th>Status</th><th>Fenster</th><th></th></tr></thead>
                              <tbody>${memberRows}</tbody>
                            </table>
                          </div>
                          ${
                            members.length > 40
                              ? `<p class="help">Erste 40 von ${members.length} — Rest unter Tab „Items“.</p>`
                              : ""
                          }
                        </details>`
                            : ""
                        }
                        <div class="actions" style="margin-top:0.55rem">
                          <button type="button" class="btn secondary" data-plan-edit="${esc(p.id)}">Bearbeiten</button>
                          <button type="button" class="btn secondary" data-plan-apply="${esc(p.id)}">Neu berechnen</button>
                          ${
                            p.id === "expensive_3m_week"
                              ? ""
                              : `<button type="button" class="btn danger" data-plan-del="${esc(p.id)}">Löschen</button>`
                          }
                        </div>
                      </article>`;
                    })
                    .join("")
                : `<p class="help">Keine Pläne.</p>`
            }
          </div>
        </div>
        <div class="panel" id="calPlanEditor">
          <h4 style="margin:0 0 0.35rem">Plan erstellen / bearbeiten</h4>
          <p class="help">Zufallsmodell: 3–14 Tage im Shop, 7–90 Tage Pause, Zielanteil ca. 50 %.</p>
          <form id="calPlanForm">
            <input type="hidden" name="id" value="" />
            <label class="field">Name
              <input name="label" required maxlength="60" placeholder="z. B. Shop-Rotation" />
            </label>
            <input type="hidden" name="model" value="independent" />
            <div class="grid-2">
              <label class="field">Min. Tage im Shop
                <input name="onDaysMin" type="number" min="1" max="30" value="3" />
              </label>
              <label class="field">Max. Tage im Shop
                <input name="onDaysMax" type="number" min="1" max="60" value="14" />
              </label>
            </div>
            <div class="grid-2">
              <label class="field">Min. Tage Pause
                <input name="offDaysMin" type="number" min="1" max="180" value="7" />
              </label>
              <label class="field">Max. Tage Pause
                <input name="offDaysMax" type="number" min="1" max="180" value="90" />
              </label>
            </div>
            <div class="grid-2">
              <label class="field">Ziel-Anteil im Shop (%)
                <input name="targetSharePct" type="number" min="10" max="90" value="50" />
              </label>
              <label class="field">Chance lange Pause (%)
                <input name="longPauseChancePct" type="number" min="0" max="60" value="12" />
              </label>
            </div>
            <div class="grid-2" hidden>
              <label class="field">Pause / Zyklus-Länge
                <input name="cycleLength" type="number" min="1" max="36" value="3" />
              </label>
              <label class="field">Einheit
                <select name="cycleUnit">
                  <option value="day">Tage</option>
                  <option value="week">Wochen</option>
                  <option value="month" selected>Monate</option>
                </select>
              </label>
            </div>
            <div class="grid-2" hidden>
              <label class="field">Kurze Shop-Dauer (Tage)
                <input name="shortDays" type="number" min="1" max="14" value="3" />
              </label>
              <label class="field">Lange Shop-Dauer (Tage)
                <input name="longDays" type="number" min="1" max="30" value="14" />
              </label>
            </div>
            <div class="grid-2" hidden>
              <label class="field">Im Shop (Länge, Legacy)
                <input name="activeLength" type="number" min="1" max="90" value="1" />
              </label>
              <label class="field">Im Shop (Einheit, Legacy)
                <select name="activeUnit">
                  <option value="day">Tage</option>
                  <option value="week" selected>Wochen</option>
                  <option value="month">Monate</option>
                </select>
              </label>
            </div>
            <div class="grid-2">
              <label class="field">Auswahl
                <select name="mode">
                  <option value="price" selected>Nach Preis</option>
                  <option value="manual">Manuelle Liste</option>
                </select>
              </label>
              <label class="field">Gleichzeitig (nur Warteschlange)
                <input name="concurrent" type="number" min="1" max="50" value="1" />
              </label>
            </div>
            <div class="grid-2">
              <label class="field">Preis min.
                <input name="priceMin" type="number" min="0" value="0" />
              </label>
              <label class="field">Preis max. (leer = ∞)
                <input name="priceMax" type="number" min="0" placeholder="optional" />
              </label>
            </div>
            <label class="field">Manuelle Keys (kind:id, Komma)
              <textarea name="itemKeys" rows="2" placeholder="emojis:💎, themes:aurora"></textarea>
            </label>
            <label class="field" style="flex-direction:row;align-items:center;gap:0.5rem">
              <input type="checkbox" name="enabled" checked /> Plan aktiv
            </label>
            <div class="actions" style="margin-top:0.65rem">
              <button type="submit" class="btn">Plan speichern</button>
              <button type="button" class="btn secondary" id="calPlanReset">Neu</button>
            </div>
          </form>
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
              <strong>Rotation:</strong> alle Shop-Items (außer Starter) rotieren zufällig —
              3–14 Tage rein, 7 Tage bis 3 Monate raus, ca. 50 % gleichzeitig im Shop.
              Tab „Rotation“ erklärt Details; Items können rausgenommen werden.
            </p>
          </div>
        </div>
        <div class="shop-cats" id="shopViews">
          <button type="button" class="shop-cat" data-shop-view="katalog">Katalog</button>
          <button type="button" class="shop-cat on" data-shop-view="kalender">Kalender</button>
        </div>
        <div class="shop-cats" id="calTabs">
          <button type="button" class="shop-cat ${state.calTab === "month" ? "on" : ""}" data-cal-tab="month">Monat</button>
          <button type="button" class="shop-cat ${state.calTab === "plans" ? "on" : ""}" data-cal-tab="plans">Rotation</button>
          <button type="button" class="shop-cat ${state.calTab === "items" ? "on" : ""}" data-cal-tab="items">Items</button>
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
    content.querySelectorAll("[data-cal-day]").forEach((btn) => {
      btn.onclick = () => {
        state.calDay = btn.getAttribute("data-cal-day") || "";
        renderShopCalendar();
      };
    });

    const dayFilter = $("calDayFilter");
    if (dayFilter) {
      dayFilter.oninput = () => {
        const q = String(dayFilter.value || "").trim().toLowerCase();
        content.querySelectorAll("#calDayGroups .cal-day-row").forEach((row) => {
          const hay = row.getAttribute("data-cal-q") || "";
          row.hidden = Boolean(q) && !hay.includes(q);
        });
        content.querySelectorAll("#calDayGroups .cal-day-group").forEach((g) => {
          const any = [...g.querySelectorAll(".cal-day-row")].some((r) => !r.hidden);
          g.hidden = Boolean(q) && !any;
        });
      };
    }

    const batchForm = $("calBatchForm");
    if (batchForm) {
      batchForm.onsubmit = async (e) => {
        e.preventDefault();
        const fd = new FormData(batchForm);
        const keys = String(fd.get("keys") || "")
          .split(/[,;\n]+/)
          .map((s) => s.trim())
          .filter(Boolean);
        if (!keys.length) {
          alert("Mindestens ein Item-Key (kind:id) angeben oder unter Items anhaken.");
          return;
        }
        try {
          const fromRaw = String(fd.get("availableFrom") || "").trim();
          const untilRaw = String(fd.get("availableUntil") || "").trim();
          const fromMs = fromRaw ? new Date(fromRaw).getTime() : null;
          const untilMs = untilRaw ? new Date(untilRaw).getTime() : null;
          if (fromMs != null && Number.isNaN(fromMs)) {
            alert("Ungültiges Von-Datum");
            return;
          }
          if (untilMs != null && Number.isNaN(untilMs)) {
            alert("Ungültiges Bis-Datum");
            return;
          }
          if (fromMs != null && untilMs != null && untilMs < fromMs) {
            alert("Bis-Datum liegt vor Von-Datum");
            return;
          }
          const result = await api("/admin/shop/calendar/batch", {
            method: "POST",
            body: JSON.stringify({
              itemKeys: keys,
              availableFrom: fromMs,
              availableUntil: untilMs,
              enabled: batchForm.querySelector('[name="enabled"]').checked,
            }),
          });
          if (!result.count) {
            alert("Keine Items aktualisiert — Keys prüfen (kind:id).");
            return;
          }
          state.calPick = {};
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Batch fehlgeschlagen");
        }
      };
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
          if (planForm.querySelector('[name="shortDays"]')) {
            planForm.querySelector('[name="shortDays"]').value = p.shortDays || 3;
          }
          if (planForm.querySelector('[name="longDays"]')) {
            planForm.querySelector('[name="longDays"]').value = p.longDays || 14;
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
      content.querySelectorAll("[data-plan-apply]").forEach((btn) => {
        btn.onclick = async () => {
          const id = btn.getAttribute("data-plan-apply");
          try {
            await api(`/admin/shop/rotation-plans/${encodeURIComponent(id)}/apply`, {
              method: "POST",
              body: "{}",
            });
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
          if (!confirm("Item aus der Rotation nehmen? Es bleibt dann dauerhaft im Shop.")) return;
          try {
            await api(
              `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/leave-rotation`,
              { method: "POST", body: "{}" }
            );
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
            await api(
              `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/rejoin-rotation`,
              { method: "POST", body: "{}" }
            );
            renderShopCalendar();
          } catch (err) {
            alert(err?.message || "Aufnehmen fehlgeschlagen");
          }
        };
      });
    }

    // Leave/rejoin auch außerhalb Plan-Form (Items-Tab)
    if (!planForm) {
      content.querySelectorAll("[data-leave-rot]").forEach((btn) => {
        btn.onclick = async () => {
          const raw = btn.getAttribute("data-leave-rot") || "";
          const [kind, ...rest] = raw.split("|");
          const itemId = rest.join("|");
          if (!kind || !itemId) return;
          if (!confirm("Item aus der Rotation nehmen? Es bleibt dann dauerhaft im Shop.")) return;
          try {
            await api(
              `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/leave-rotation`,
              { method: "POST", body: "{}" }
            );
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
            await api(
              `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}/rejoin-rotation`,
              { method: "POST", body: "{}" }
            );
            renderShopCalendar();
          } catch (err) {
            alert(err?.message || "Aufnehmen fehlgeschlagen");
          }
        };
      });
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
          await api(
            `/admin/shop/calendar/${encodeURIComponent(kind)}/${encodeURIComponent(itemId)}`,
            { method: "PUT", body: JSON.stringify(body) }
          );
          renderShopCalendar();
        } catch (err) {
          alert(err?.message || "Speichern fehlgeschlagen");
        }
      };
    });
  }

  function itemFormFields(it = {}, stepHint = "") {
    const fromLocal = toLocalInput(it.availableFrom);
    const untilLocal = toLocalInput(it.availableUntil);
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
          <span class="tip">Vorher unsichtbar im Shop</span>
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
    };
  }

  function openEditItem(it) {
    openModal(`
      <h3 style="font-family:var(--display);margin:0 0 0.5rem">Item bearbeiten</h3>
      <p class="help">Änderungen sind sofort für alle Spieler sichtbar. Nur den Namen ändern? Nutze „Name“ auf der Karte — ohne Shop-Seiteneffekte.</p>
      <form id="editForm">${itemFormFields(it)}
        <div class="actions" style="margin-top:1rem">
          <button type="submit" class="btn">Speichern</button>
          <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
        </div>
      </form>`);
    $("cancelModal").onclick = closeModal;
    $("editForm").onsubmit = async (e) => {
      e.preventDefault();
      const body = readForm(e.target);
      // Anzeigename separat speichern — wirkt auch ohne Katalog-Seiteneffekte
      if (body.label) {
        await api(
          `/admin/items/${encodeURIComponent(body.kind)}/${encodeURIComponent(body.itemId)}/display-label`,
          { method: "PUT", body: JSON.stringify({ label: body.label }) }
        );
      }
      await api(
        `/admin/shop/items/${encodeURIComponent(body.kind)}/${encodeURIComponent(body.itemId)}`,
        { method: "PUT", body: JSON.stringify(body) }
      );
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

  function openWizard(preKind) {
    let step = 0;
    const draft = {
      kind: preKind || "emojis",
      itemId: "💖",
      label: "Herz",
      searchText: "herz heart liebe",
      priceCoins: 40,
      salePrice: null,
      compareAtPrice: null,
      availableUntil: null,
      maxTotalSales: null,
      maxPerUser: null,
      enabled: true,
      source: "emoji", // emoji | custom
      imageDataUrl: null,
      visualConfig: null,
    };
    const steps = ["Kategorie", "Gestalten", "Preis", "Angebot & Timer", "Limits", "Fertig"];

    function paint() {
      openModal(`
        <h3 style="font-family:var(--display);margin:0 0 0.5rem">Neues Item</h3>
        <div class="wizard-steps">${steps
          .map((s, i) => `<span class="${i === step ? "on" : ""}">${i + 1}. ${s}</span>`)
          .join("")}</div>
        <form id="wizForm">
          ${stepBody()}
          <div class="actions" style="margin-top:1rem">
            ${step > 0 ? `<button type="button" class="btn ghost" id="wizBack">Zurück</button>` : ""}
            ${
              step < steps.length - 1
                ? `<button type="button" class="btn" id="wizNext">Weiter</button>`
                : `<button type="submit" class="btn teal">Im Shop anlegen</button>`
            }
            <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
          </div>
        </form>`);
      if (modalCard) {
        modalCard.classList.toggle(
          "wide",
          step === 1 && (draft.source === "custom" || draft.kind === "themes")
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
      const form = $("wizForm");
      // Enter in Suchworte/Name darf den Wizard NICHT speichern/schließen
      form.addEventListener("keydown", (e) => {
        if (e.key !== "Enter") return;
        const tag = (e.target && e.target.tagName) || "";
        if (tag !== "INPUT" && tag !== "SELECT") return;
        e.preventDefault();
        if (step < steps.length - 1) {
          if (!saveStep()) return;
          step++;
          paint();
        }
      });
      form.onsubmit = async (e) => {
        e.preventDefault();
        // Nur auf dem letzten Schritt wirklich anlegen (Enter auf früheren Steps = weiter)
        if (step < steps.length - 1) {
          if (!saveStep()) return;
          step++;
          paint();
          return;
        }
        if (!saveStep()) return;
        if (draft.kind === "themes") ensureThemeId();
        const body = {
          kind: draft.kind,
          itemId: draft.itemId,
          label: draft.label,
          searchText: draft.searchText,
          priceCoins: draft.priceCoins,
          salePrice: draft.salePrice,
          compareAtPrice: draft.compareAtPrice,
          availableUntil: draft.availableUntil,
          maxTotalSales: draft.maxTotalSales,
          maxPerUser: draft.maxPerUser,
          enabled: draft.enabled,
        };
        if (draft.kind === "themes" && draft.visualConfig) {
          body.visualConfig = draft.visualConfig;
          body.emoji = (draft.visualConfig.emojis && draft.visualConfig.emojis[0]) || "🖼️";
          body.itemId = draft.itemId;
        }
        if (draft.source === "custom" && draft.imageDataUrl) {
          body.petSource = "image";
          body.imageDataUrl = draft.imageDataUrl;
          // Stabile Bild-ID — nie img_new (sonst überschreiben sich alle Bilder)
          const S = Studio();
          if (!S?.isStableImageItemId?.(draft.itemId)) {
            draft.itemId = S?.newImageItemId?.() || `img_${Date.now().toString(36)}`;
          }
          body.itemId = draft.itemId;
        }
        const created = await api("/admin/shop/items", {
          method: "POST",
          body: JSON.stringify(body),
        });
        if (created?.item?.itemId) draft.itemId = created.item.itemId;
        closeModal();
        renderShop();
      };
    }

    function ensureCustomImageId() {
      const S = Studio();
      if (!S?.isStableImageItemId?.(draft.itemId)) {
        draft.itemId = S?.newImageItemId?.() || `img_${Date.now().toString(36)}`;
      }
    }

    function isStableThemeId(id) {
      const s = String(id || "").trim();
      return /^theme_[a-z0-9]{4,24}$/i.test(s);
    }

    function ensureThemeId() {
      if (isStableThemeId(draft.itemId)) return;
      const bytes = new Uint8Array(4);
      if (crypto && crypto.getRandomValues) crypto.getRandomValues(bytes);
      else for (let i = 0; i < 4; i++) bytes[i] = (Math.random() * 256) | 0;
      draft.itemId =
        "theme_" +
        Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    }

    function wireDesignStep() {
      const setSrc = (src) => {
        draft.source = src;
        if (src === "custom") {
          ensureCustomImageId();
        } else if (src === "emoji") {
          // Bild-ID nicht als Emoji-ID weiterverwenden
          if (/^img_/i.test(String(draft.itemId || ""))) {
            draft.itemId = "💖";
            draft.imageDataUrl = null;
          }
        }
        paint();
      };
      const srcEmoji = $("srcEmoji");
      const srcCustom = $("srcCustom");
      if (srcEmoji) srcEmoji.onclick = () => setSrc("emoji");
      if (srcCustom) srcCustom.onclick = () => setSrc("custom");

      const pickEmoji = $("pickEmoji");
      if (pickEmoji)
        pickEmoji.onclick = () => {
          Studio()?.openEmojiPicker((emoji) => {
            draft.itemId = emoji;
            draft.source = "emoji";
            draft.imageDataUrl = null;
            if (!draft.label || draft.label === "Herz" || draft.label === "Bild-Begleiter") {
              draft.label = emoji;
            }
            paint();
          });
        };

      const focusWizardLabel = () => {
        requestAnimationFrame(() => {
          const label = document.querySelector('#wizForm [name="label"]');
          if (label && typeof label.focus === "function") {
            try {
              label.focus();
              if (typeof label.select === "function") label.select();
            } catch (_) {
              /* ignore */
            }
          }
        });
      };

      const openGlyph = $("openGlyph");
      if (openGlyph)
        openGlyph.onclick = () => {
          Studio()?.openGlyphComposer(
            (dataUrl) => {
              draft.imageDataUrl = dataUrl;
              draft.source = "custom";
              ensureCustomImageId();
              if (!draft.label || draft.label === "Herz") draft.label = "Eigenes Emoji";
              paint();
              focusWizardLabel();
            },
            { title: draft.kind === "pets" ? "Begleiter gestalten" : "Emoji / Sticker gestalten" }
          );
        };

      const openImg = $("openPetImage");
      if (openImg)
        openImg.onclick = () => {
          openPetImageEditor((dataUrl) => {
            draft.imageDataUrl = dataUrl;
            draft.source = "custom";
            ensureCustomImageId();
            if (!draft.label || draft.label === "Herz") draft.label = "Bild-Item";
            paint();
            focusWizardLabel();
          }, draft.kind === "pets" ? "Begleiter freistellen" : "Bild freistellen");
        };

      const clearImg = $("clearPetImage");
      if (clearImg)
        clearImg.onclick = () => {
          draft.imageDataUrl = null;
          paint();
        };

      const openTheme = $("openThemeStudio");
      if (openTheme)
        openTheme.onclick = () => {
          Studio()?.openThemeStudio(draft.visualConfig, (cfg) => {
            draft.visualConfig = cfg;
            if (!draft.label || draft.label === "Herz") draft.label = "Eigener Hintergrund";
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
        draft.kind = String(fd.get("kind") || "emojis");
        if (draft.kind === "themes") draft.source = "custom";
      }
      if (step === 1) {
        draft.label = String(fd.get("label") || draft.label || "").trim();
        draft.searchText = String(fd.get("searchText") || "").trim();
        if (draft.kind === "themes") {
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
        const until = String(fd.get("availableUntil") || "").trim();
        draft.availableUntil = until ? new Date(until).getTime() : null;
      }
      if (step === 4) {
        const mt = String(fd.get("maxTotalSales") || "").trim();
        const mp = String(fd.get("maxPerUser") || "").trim();
        draft.maxTotalSales = mt === "" ? null : Number(mt);
        draft.maxPerUser = mp === "" ? null : Number(mp);
      }
      return true;
    }

    function glyphPreviewHtml() {
      if (!draft.imageDataUrl) return "";
      return `<div class="pet-preview-row"><div class="pet-preview-circle"><img src="${draft.imageDataUrl}" alt="" /></div><span class="muted">Vorschau</span></div>`;
    }

    function stepBody() {
      if (step === 0)
        return `<label class="field">Kategorie
          <select name="kind">
            <option value="emojis" ${draft.kind === "emojis" ? "selected" : ""}>😊 Emoji (Reaktion)</option>
            <option value="stickers" ${draft.kind === "stickers" ? "selected" : ""}>🏷️ Sticker</option>
            <option value="themes" ${draft.kind === "themes" ? "selected" : ""}>🖼️ Hintergrund</option>
            <option value="pets" ${draft.kind === "pets" ? "selected" : ""}>🐾 Begleiter</option>
          </select>
          <span class="tip">Neue Items landen in der Lootbox (Seltenheit nach Preis).</span>
        </label>`;
      if (step === 1) {
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
          <span class="tip">Lootbox-Seltenheit folgt dem Preis.</span>
        </label>`;
      if (step === 3)
        return `<div class="grid-2">
          <label class="field">Angebotspreis<input name="salePrice" type="number" value="${draft.salePrice ?? ""}" placeholder="optional" /></label>
          <label class="field">Durchgestrichener Preis<input name="compareAtPrice" type="number" value="${draft.compareAtPrice ?? ""}" placeholder="optional" /></label>
          <label class="field">Ende<input name="availableUntil" type="datetime-local" value="${
            draft.availableUntil ? new Date(draft.availableUntil).toISOString().slice(0, 16) : ""
          }" /></label>
        </div>
        <p class="help">Leer = dauerhaft. Mit Datum = Countdown in der App.</p>`;
      if (step === 4)
        return `<div class="grid-2">
          <label class="field">Max. Verkäufe gesamt<input name="maxTotalSales" type="number" value="${draft.maxTotalSales ?? ""}" placeholder="∞" /></label>
          <label class="field">Max. pro Nutzer<input name="maxPerUser" type="number" value="${draft.maxPerUser ?? ""}" placeholder="∞" /></label>
        </div>`;
      return `<div class="panel">
        <p><strong>${
          draft.source === "custom" && draft.imageDataUrl
            ? "✨ Eigenes Design"
            : esc(draft.itemId)
        }</strong> · ${esc(draft.kind)}</p>
        ${glyphPreviewHtml()}
        ${
          draft.visualConfig
            ? `<div class="theme-mini-preview" style="background:linear-gradient(160deg,${esc(
                draft.visualConfig.skyTop
              )},${esc(draft.visualConfig.skyBottom)})"></div>`
            : ""
        }
        <p>${esc(draft.label)} — ${draft.priceCoins} Coins</p>
        <p class="muted">Suche: ${esc(draft.searchText || "—")}</p>
        <p class="muted">→ landet in der Lootbox</p>
      </div>`;
    }

    paint();
  }

  async function renderReports() {
    const peer = await api("/admin/peer-reports").catch(() => ({ reports: [] }));
    const pub = await api("/admin/public-reports").catch(() => ({ reports: [] }));
    const rows = [...(peer.reports || []), ...(pub.reports || [])];
    content.innerHTML = `
      <div class="panel">
        <h3>Meldungen</h3>
        <p class="help">Offene Fälle zuerst prüfen. „Behalten“ schließt die Meldung, „Löschen“ entfernt den Inhalt.</p>
        <div class="list">
          ${
            rows.length
              ? rows
                  .map(
                    (r) => `<div class="list-item">
              <div class="row">
                <div>
                  <strong>${esc(r.id || r.publicId || "report")}</strong>
                  <div class="muted">${esc(r.reason || r.status || "")}</div>
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

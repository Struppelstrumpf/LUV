(() => {
  const API = "/luv/v1";
  const TOKEN_KEY = "luv_adm_token";
  const USER_KEY = "luv_adm_user";

  const state = {
    token: localStorage.getItem(TOKEN_KEY) || "",
    user: null,
    tab: "overview",
    shopItems: [],
    shopKind: "",
    shopQ: "",
    shopSource: "",
    shopUniverse: [],
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
    { id: "shop", label: "Itemshop", hint: "Alle Items: Shop, Erfolge, Codes — Preise, Handelbarkeit und Limits.", perm: "market.settings" },
    { id: "reports", label: "Meldungen", hint: "Gemeldete Lobby-/Galerie-Bilder prüfen.", perm: "reports.view" },
    { id: "codes", label: "Codes", hint: "Gutscheincodes erstellen und widerrufen.", perm: "codes.view" },
    { id: "users", label: "Nutzer", hint: "Spieler suchen, Coins und Nick anpassen.", perm: "gm.search" },
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
  function hasPerm(id) {
    if (!state.user) return false;
    if (isAdmin(state.user)) return true;
    return Boolean(state.user.permissions && state.user.permissions[id]);
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
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    else localStorage.removeItem(USER_KEY);
  }

  function showLogin(err) {
    loginView.hidden = false;
    appView.hidden = true;
    const el = $("loginError");
    if (err) {
      el.hidden = false;
      el.textContent = err;
    } else {
      el.hidden = true;
    }
  }

  function showApp() {
    loginView.hidden = true;
    appView.hidden = false;
    $("userNick").textContent = state.user.nickname || "Staff";
    $("userRole").textContent = state.user.role === "admin" ? "Admin" : "Moderator";
    renderNav();
    loadTab(state.tab);
  }

  function renderNav() {
    nav.innerHTML = "";
    TABS.forEach((t) => {
      if (t.adminOnly && !isAdmin(state.user)) return;
      if (t.perm && !hasPerm(t.perm) && !isAdmin(state.user)) return;
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

  function openModal(html) {
    modalCard.innerHTML = html;
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
    const json = await api("/auth/google", {
      method: "POST",
      body: JSON.stringify({ idToken, staffOnly: true }),
    });
    const token = json.sessionToken || json.token;
    const user = json.user;
    if (!token || !user) throw new Error("Login unvollständig");
    if (!isStaff(user)) throw new Error("Kein Admin-/Mod-Zugang für dieses Konto.");
    setSession(token, user);
    showApp();
  }

  function showLoginError(msg) {
    const el = $("loginError");
    el.hidden = false;
    el.textContent = msg;
  }

  function renderGoogleButton(clientId) {
    const slot = $("googleBtn");
    slot.innerHTML = "";
    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: (resp) => {
        googleCredential(resp.credential).catch((e) => showLoginError(e.message));
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
    if (!state.token) {
      showLogin();
      initGoogle();
      return;
    }
    try {
      const me = await api("/me");
      const user = me.user || me;
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
      initGoogle();
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

  async function loadTab(id) {
    const meta = TABS.find((t) => t.id === id) || TABS[0];
    pageTitle.textContent = meta.label;
    pageHint.textContent = meta.hint || "";
    content.innerHTML = `<p class="muted">Lade…</p>`;
    try {
      if (id === "overview") await renderOverview();
      else if (id === "shop") await renderShop();
      else if (id === "reports") await renderReports();
      else if (id === "codes") await renderCodes();
      else if (id === "users") await renderUsers();
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
      const shopOff = shop && shop.enabled === false;
      return `<article class="shop-card ${shopOff ? "is-off" : ""} ${
        it.marketSellable ? "is-tradeable" : "is-untradeable"
      }">
        <div class="shop-card-visual">${thumb}
          <div class="shop-card-market">${marketBtn}</div>
        </div>
        <div class="shop-card-body">
          <strong class="shop-card-title">${esc(it.label || it.itemId)}</strong>
          <div class="muted mono shop-card-id">${esc(it.itemId)}</div>
          <div class="shop-card-price-row">${price}
            ${shopOff ? '<span class="badge off">Shop aus</span>' : ""}
            ${!shop && it.sources.includes("shop") ? "" : ""}
            ${
              !it.sources.includes("shop")
                ? '<span class="badge src-noshop">nicht im Shop</span>'
                : ""
            }
          </div>
          <div class="shop-card-meta source-row">${sourceBadges(it.sources)}</div>
        </div>
        <div class="shop-card-actions">
          ${
            shop
              ? `<button type="button" class="btn secondary btn-edit" data-edit="${esc(it.kind)}|${esc(it.itemId)}">Bearbeiten</button>
          <button type="button" class="btn ghost btn-toggle" data-toggle="${esc(it.kind)}|${esc(it.itemId)}|${shop.enabled ? "off" : "on"}" title="${shop.enabled ? "Im Shop ausblenden" : "Im Shop aktivieren"}">${
                  shop.enabled ? "Shop aus" : "Shop an"
                }</button>
          <button type="button" class="btn btn-del" data-del="${esc(it.kind)}|${esc(it.itemId)}" title="Aus Shop löschen">Löschen</button>`
              : `<span class="muted" style="font-size:0.78rem;padding:0.35rem 0">Nur Herkunft / Markt</span>`
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
    content.querySelectorAll("[data-edit]").forEach((btn) => {
      btn.onclick = () => {
        const [kind, itemId] = btn.getAttribute("data-edit").split("|");
        const it = state.shopItems.find((x) => x.kind === kind && x.itemId === itemId);
        openEditItem(it || { kind, itemId });
      };
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

  function itemFormFields(it = {}, stepHint = "") {
    const untilLocal = it.availableUntil
      ? new Date(it.availableUntil).toISOString().slice(0, 16)
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
    const until = String(fd.get("availableUntil") || "").trim();
    return {
      kind: String(fd.get("kind") || "").trim(),
      itemId: String(fd.get("itemId") || "").trim(),
      label: String(fd.get("label") || "").trim(),
      searchText: String(fd.get("searchText") || "").trim(),
      priceCoins: Number(fd.get("priceCoins") || 0),
      salePrice: numOrNull("salePrice"),
      compareAtPrice: numOrNull("compareAtPrice"),
      availableUntil: until ? new Date(until).getTime() : null,
      maxTotalSales: numOrNull("maxTotalSales"),
      maxPerUser: numOrNull("maxPerUser"),
      enabled: form.querySelector('[name="enabled"]').checked,
    };
  }

  function openEditItem(it) {
    openModal(`
      <h3 style="font-family:var(--display);margin:0 0 0.5rem">Item bearbeiten</h3>
      <p class="help">Änderungen sind sofort für alle Spieler sichtbar.</p>
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
      await api(
        `/admin/shop/items/${encodeURIComponent(body.kind)}/${encodeURIComponent(body.itemId)}`,
        { method: "PUT", body: JSON.stringify(body) }
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
              <div class="muted">${v.coins || 0} Coins · ${v.redeemed || 0}/${v.maxRedeems ?? "∞"} · ${
                      v.revoked ? "widerrufen" : "aktiv"
                    }</div>
              ${
                !v.revoked
                  ? `<div class="actions"><button class="btn ghost" data-revoke="${esc(v.code)}">Widerrufen</button></div>`
                  : ""
              }
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
    setSession("", null);
    showLogin();
    initGoogle();
  };
  $("refreshBtn").onclick = () => loadTab(state.tab);

  // boot
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", resume);
  } else {
    resume();
  }
})();

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
    { id: "shop", label: "Itemshop", hint: "Preise, Angebote, Timer und Limits — Änderungen gelten sofort in der App.", perm: "market.settings" },
    { id: "reports", label: "Meldungen", hint: "Gemeldete Lobby-/Galerie-Bilder prüfen.", perm: "reports.view" },
    { id: "codes", label: "Codes", hint: "Gutscheincodes erstellen und widerrufen.", perm: "codes.view" },
    { id: "users", label: "Nutzer", hint: "Spieler suchen, Coins und Nick anpassen.", perm: "gm.search" },
    { id: "mods", label: "Moderatoren", hint: "Mods einladen und Rechte setzen.", perm: "mods.manage", adminOnly: true },
    { id: "market", label: "Markt-Fenster", hint: "Preis-Zeitfenster für Marktplatz-Statistik.", perm: "market.settings" },
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
  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
  });

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
      { id: "emojis", label: "Emojis", emoji: "😊" },
      { id: "stickers", label: "Sticker", emoji: "🏷️" },
      { id: "themes", label: "Hintergründe", emoji: "🖼️" },
      { id: "pets", label: "Begleiter", emoji: "🐾" },
    ];
    const q = new URLSearchParams();
    if (state.shopQ) q.set("q", state.shopQ);
    const data = await api("/admin/shop/items?" + q.toString());
    state.shopItems = data.items || [];

    const byKind = Object.fromEntries(SHOP_CATS.map((c) => [c.id, []]));
    for (const it of state.shopItems) {
      if (!byKind[it.kind]) byKind[it.kind] = [];
      byKind[it.kind].push(it);
    }
    for (const list of Object.values(byKind)) {
      list.sort(
        (a, b) =>
          (Number(a.priceCoins) || 0) - (Number(b.priceCoins) || 0) ||
          String(a.label || a.itemId).localeCompare(String(b.label || b.itemId), "de")
      );
    }

    const activeKind = state.shopKind || "";
    const visibleCats = activeKind
      ? SHOP_CATS.filter((c) => c.id === activeKind)
      : SHOP_CATS;

    function rowHtml(it) {
      const sale =
        it.onSale && it.compareAtPrice
          ? `<span class="price-old">${it.compareAtPrice}</span> <strong>${it.priceCoins}</strong>`
          : `<strong>${it.priceCoins}</strong>`;
      const timer = it.availableUntil
        ? fmtMs(it.remainingMs) + (it.enabled ? "" : " (aus)")
        : "—";
      const limits = [
        it.maxTotalSales != null ? `gesamt ${it.soldTotal || 0}/${it.maxTotalSales}` : "gesamt ∞",
        it.maxPerUser != null ? `p.P. ${it.maxPerUser}` : "p.P. ∞",
      ].join(" · ");
      const thumb =
        it.hasImage && it.imageUrl
          ? `<img class="shop-thumb" src="${esc(it.imageUrl)}" alt="" />`
          : `<span class="shop-emoji">${esc(it.emoji || it.itemId)}</span>`;
      return `<tr class="${it.enabled ? "" : "is-off"}">
        <td>
          <div class="shop-item-cell">
            ${thumb}
            <div>
              <strong>${esc(it.label || it.itemId)}</strong>
              <div class="muted mono">${esc(it.itemId)}</div>
            </div>
          </div>
        </td>
        <td class="num">${it.listPrice ?? it.priceCoins}</td>
        <td class="num">${sale}${it.onSale ? ' <span class="badge sale">Angebot</span>' : ""}</td>
        <td>${esc(timer)}</td>
        <td class="muted">${esc(limits)}</td>
        <td>${it.enabled ? '<span class="badge">aktiv</span>' : '<span class="badge off">aus</span>'}</td>
        <td class="actions">
          <button class="btn secondary" data-edit="${esc(it.kind)}|${esc(it.itemId)}">Bearbeiten</button>
          <button class="btn ghost" data-toggle="${esc(it.kind)}|${esc(it.itemId)}|${it.enabled ? "off" : "on"}">${it.enabled ? "Aus" : "An"}</button>
        </td>
      </tr>`;
    }

    function sectionHtml(cat) {
      const items = byKind[cat.id] || [];
      if (!items.length && activeKind) {
        return `<section class="shop-section" id="cat-${cat.id}">
          <header class="shop-section-head">
            <h3>${cat.emoji} ${cat.label}</h3>
            <span class="muted">0 Items</span>
          </header>
          <p class="help">Keine Einträge in dieser Kategorie${state.shopQ ? " für diese Suche" : ""}.</p>
        </section>`;
      }
      if (!items.length) return "";
      return `<section class="shop-section" id="cat-${cat.id}">
        <header class="shop-section-head">
          <h3>${cat.emoji} ${cat.label}</h3>
          <span class="muted">${items.length} · nach Preis sortiert</span>
        </header>
        <div class="shop-table-wrap">
          <table>
            <thead>
              <tr>
                <th>Item</th><th>Listenpreis</th><th>Effektiver Preis</th><th>Timer</th><th>Limits</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>${items.map(rowHtml).join("")}</tbody>
          </table>
        </div>
      </section>`;
    }

    const total = state.shopItems.length;
    content.innerHTML = `
      <div class="panel">
        <div class="shop-top">
          <div>
            <h3 style="margin:0">Itemshop</h3>
            <p class="help" style="margin:0.35rem 0 0">
              Kategorien getrennt · innerhalb jeder Kategorie nach Preis (aufsteigend).
              ${total} Items gesamt${state.shopQ ? ` · Suche „${esc(state.shopQ)}“` : ""}.
            </p>
          </div>
          <button class="btn teal" id="shopWizard">+ Neues Item</button>
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

        <div class="toolbar" style="margin-top:0.85rem">
          <input id="shopQ" placeholder="Suchen (Name, ID, Suchworte)…" value="${esc(state.shopQ)}" />
          <button class="btn secondary" id="shopFilter">Suchen</button>
          ${state.shopQ ? `<button class="btn ghost" id="shopClearQ">Suche löschen</button>` : ""}
        </div>
      </div>

      <div class="shop-sections">
        ${visibleCats.map(sectionHtml).join("") || `<div class="panel"><p class="help">Keine Items gefunden.</p></div>`}
      </div>`;

    $("shopWizard").onclick = () => openWizard();
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
        renderShop();
      };
    content.querySelectorAll("#shopCats [data-kind]").forEach((btn) => {
      btn.onclick = () => {
        state.shopKind = btn.getAttribute("data-kind") || "";
        renderShop();
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

  function openPetImageEditor(onDone) {
    const prev = document.getElementById("petPasteLayer");
    if (prev) prev.remove();
    const layer = document.createElement("div");
    layer.id = "petPasteLayer";
    layer.className = "pet-paste-layer";
    layer.innerHTML = `
      <div class="pet-paste-card">
        <h3 style="font-family:var(--display);margin:0 0 0.35rem">Begleiter-Bild</h3>
        <p class="help" style="margin:0 0 0.6rem">
          Bild mit <strong>Strg+V</strong> einfügen (oder Datei wählen). Grüner Hintergrund wird transparent.
        </p>
        <div class="chroma-swatch"><i></i> Chroma-Key: <strong>${CHROMA_KEY}</strong></div>
        <div class="pet-dropzone" id="petDrop" style="margin-top:0.75rem" tabindex="0">
          Hier klicken, dann <strong>Strg+V</strong> — oder Datei ablegen / wählen
          <div style="margin-top:0.6rem">
            <input type="file" id="petFile" accept="image/*" />
          </div>
        </div>
        <div class="pet-paste-stage" id="petStage" hidden>
          <canvas id="petCanvas" width="512" height="512"></canvas>
          <div class="crop" id="petCrop"></div>
        </div>
        <p class="help" id="petCropHint" hidden>Ziehen am Bild, um den Ausschnitt zu wählen (Quadrat).</p>
        <div class="pet-preview-row" id="petPrevRow" hidden>
          <div class="pet-preview-circle"><img id="petPrevImg" alt="Vorschau" /></div>
          <div class="muted">So erscheint der Begleiter im Avatar</div>
        </div>
        <div class="actions" style="margin-top:1rem">
          <button type="button" class="btn teal" id="petAccept" disabled>Übernehmen</button>
          <button type="button" class="btn ghost" id="petCancel">Abbrechen</button>
        </div>
      </div>`;
    document.body.appendChild(layer);

    const canvas = layer.querySelector("#petCanvas");
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    const stage = layer.querySelector("#petStage");
    const cropEl = layer.querySelector("#petCrop");
    const drop = layer.querySelector("#petDrop");
    const prevRow = layer.querySelector("#petPrevRow");
    const prevImg = layer.querySelector("#petPrevImg");
    const acceptBtn = layer.querySelector("#petAccept");
    const hint = layer.querySelector("#petCropHint");
    let sourceImg = null;
    let crop = { x: 0.1, y: 0.1, s: 0.8 }; // relative 0–1
    let dragging = null;

    function close() {
      layer.remove();
      window.removeEventListener("paste", onPaste);
    }
    layer.querySelector("#petCancel").onclick = close;

    function chromaKey(img) {
      const w = 512;
      const h = 512;
      canvas.width = w;
      canvas.height = h;
      ctx.clearRect(0, 0, w, h);
      const scale = Math.min(w / img.width, h / img.height);
      const dw = img.width * scale;
      const dh = img.height * scale;
      const dx = (w - dw) / 2;
      const dy = (h - dh) / 2;
      ctx.drawImage(img, dx, dy, dw, dh);
      const data = ctx.getImageData(0, 0, w, h);
      const px = data.data;
      const keyR = 0;
      const keyG = 255;
      const keyB = 0;
      const thresh = 95;
      for (let i = 0; i < px.length; i += 4) {
        const r = px[i];
        const g = px[i + 1];
        const b = px[i + 2];
        const dist = Math.sqrt((r - keyR) ** 2 + (g - keyG) ** 2 + (b - keyB) ** 2);
        if (dist < thresh || (g > 180 && g > r * 1.35 && g > b * 1.35)) {
          const soft = Math.max(0, 1 - dist / (thresh + 40));
          px[i + 3] = Math.floor(px[i + 3] * (1 - soft));
        }
      }
      ctx.putImageData(data, 0, 0);
    }

    function layoutCrop() {
      const rect = stage.getBoundingClientRect();
      cropEl.style.left = `${crop.x * 100}%`;
      cropEl.style.top = `${crop.y * 100}%`;
      cropEl.style.width = `${crop.s * 100}%`;
      cropEl.style.height = `${crop.s * 100}%`;
      void rect;
    }

    function exportPng() {
      if (!sourceImg) return null;
      const size = 256;
      const out = document.createElement("canvas");
      out.width = size;
      out.height = size;
      const octx = out.getContext("2d");
      const sx = crop.x * canvas.width;
      const sy = crop.y * canvas.height;
      const ss = crop.s * canvas.width;
      octx.drawImage(canvas, sx, sy, ss, ss, 0, 0, size, size);
      return out.toDataURL("image/png");
    }

    function refreshPreview() {
      const url = exportPng();
      if (!url) return;
      prevImg.src = url;
      prevRow.hidden = false;
      acceptBtn.disabled = false;
    }

    function loadBitmap(fileOrBlob) {
      const url = URL.createObjectURL(fileOrBlob);
      const img = new Image();
      img.onload = () => {
        URL.revokeObjectURL(url);
        sourceImg = img;
        chromaKey(img);
        stage.hidden = false;
        hint.hidden = false;
        drop.classList.add("on");
        crop = { x: 0.12, y: 0.12, s: 0.76 };
        layoutCrop();
        refreshPreview();
      };
      img.onerror = () => {
        URL.revokeObjectURL(url);
        alert("Bild konnte nicht geladen werden.");
      };
      img.src = url;
    }

    function onPaste(e) {
      const items = e.clipboardData?.items;
      if (!items) return;
      for (const it of items) {
        if (it.type.startsWith("image/")) {
          e.preventDefault();
          const f = it.getAsFile();
          if (f) loadBitmap(f);
          return;
        }
      }
    }
    window.addEventListener("paste", onPaste);
    drop.addEventListener("click", () => drop.focus());
    layer.querySelector("#petFile").onchange = (e) => {
      const f = e.target.files?.[0];
      if (f) loadBitmap(f);
    };
    drop.addEventListener("dragover", (e) => {
      e.preventDefault();
      drop.classList.add("on");
    });
    drop.addEventListener("drop", (e) => {
      e.preventDefault();
      const f = e.dataTransfer?.files?.[0];
      if (f) loadBitmap(f);
    });

    stage.addEventListener("pointerdown", (e) => {
      if (!sourceImg) return;
      const rect = stage.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;
      dragging = { ox: x - crop.x, oy: y - crop.y };
      stage.setPointerCapture(e.pointerId);
    });
    stage.addEventListener("pointermove", (e) => {
      if (!dragging) return;
      const rect = stage.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;
      crop.x = Math.min(1 - crop.s, Math.max(0, x - dragging.ox));
      crop.y = Math.min(1 - crop.s, Math.max(0, y - dragging.oy));
      layoutCrop();
      refreshPreview();
    });
    stage.addEventListener("pointerup", () => {
      dragging = null;
    });
    stage.addEventListener("wheel", (e) => {
      if (!sourceImg) return;
      e.preventDefault();
      const delta = e.deltaY > 0 ? 0.04 : -0.04;
      const next = Math.min(1, Math.max(0.25, crop.s + delta));
      const cx = crop.x + crop.s / 2;
      const cy = crop.y + crop.s / 2;
      crop.s = next;
      crop.x = Math.min(1 - crop.s, Math.max(0, cx - crop.s / 2));
      crop.y = Math.min(1 - crop.s, Math.max(0, cy - crop.s / 2));
      layoutCrop();
      refreshPreview();
    }, { passive: false });

    acceptBtn.onclick = () => {
      const dataUrl = exportPng();
      if (!dataUrl) return;
      onDone(dataUrl);
      close();
    };
  }

  function openWizard() {
    let step = 0;
    const draft = {
      kind: "emojis",
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
      petSource: "emoji",
      imageDataUrl: null,
    };
    const steps = ["Kategorie", "Item", "Preis", "Angebot & Timer", "Limits", "Fertig"];

    function paint() {
      openModal(`
        <h3 style="font-family:var(--display);margin:0 0 0.5rem">Neues Item — Wizard</h3>
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
      if (modalCard) modalCard.classList.toggle("wide", draft.kind === "pets" && step === 1);
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
      wirePetStep();
      const form = $("wizForm");
      form.onsubmit = async (e) => {
        e.preventDefault();
        if (!saveStep()) return;
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
        if (draft.kind === "pets" && draft.petSource === "image") {
          body.petSource = "image";
          body.imageDataUrl = draft.imageDataUrl;
          body.itemId = draft.itemId || "img_new";
        }
        await api("/admin/shop/items", { method: "POST", body: JSON.stringify(body) });
        closeModal();
        renderShop();
      };
    }

    function wirePetStep() {
      const emojiBtn = $("petSrcEmoji");
      const imgBtn = $("petSrcImage");
      if (emojiBtn)
        emojiBtn.onclick = () => {
          draft.petSource = "emoji";
          paint();
        };
      if (imgBtn)
        imgBtn.onclick = () => {
          draft.petSource = "image";
          paint();
        };
      const openImg = $("openPetImage");
      if (openImg)
        openImg.onclick = () => {
          openPetImageEditor((dataUrl) => {
            draft.imageDataUrl = dataUrl;
            draft.petSource = "image";
            if (!draft.label || draft.label === "Herz" || draft.label === draft.itemId) {
              draft.label = "Bild-Begleiter";
            }
            paint();
          });
        };
      const clearImg = $("clearPetImage");
      if (clearImg)
        clearImg.onclick = () => {
          draft.imageDataUrl = null;
          paint();
        };
    }

    function saveStep() {
      const form = $("wizForm");
      if (!form) return true;
      const fd = new FormData(form);
      if (step === 0) {
        draft.kind = String(fd.get("kind") || "emojis");
        if (draft.kind === "pets" && draft.petSource === "image" && !draft.imageDataUrl) {
          /* ok */
        }
      }
      if (step === 1) {
        draft.label = String(fd.get("label") || draft.label || "").trim();
        draft.searchText = String(fd.get("searchText") || "").trim();
        if (draft.kind === "pets" && draft.petSource === "image") {
          if (!draft.imageDataUrl) {
            alert("Bitte zuerst ein Bild einfügen und übernehmen.");
            return false;
          }
          if (!draft.label) draft.label = "Bild-Begleiter";
          draft.itemId = draft.itemId && /^img_/.test(draft.itemId) ? draft.itemId : "img_new";
        } else {
          draft.itemId = String(fd.get("itemId") || "").trim();
          draft.label = String(fd.get("label") || draft.itemId).trim();
          if (!draft.itemId) {
            alert("Bitte Item-ID / Emoji eingeben.");
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

    function stepBody() {
      if (step === 0)
        return `<label class="field">Kategorie wählen
          <select name="kind">
            <option value="emojis" ${draft.kind === "emojis" ? "selected" : ""}>Emoji (Reaktion)</option>
            <option value="stickers" ${draft.kind === "stickers" ? "selected" : ""}>Sticker</option>
            <option value="themes" ${draft.kind === "themes" ? "selected" : ""}>Hintergrund</option>
            <option value="pets" ${draft.kind === "pets" ? "selected" : ""}>Begleiter</option>
          </select>
          <span class="tip">Neue Items landen automatisch in der Lootbox (Seltenheit nach Preis).</span>
        </label>`;
      if (step === 1) {
        if (draft.kind === "pets") {
          return `
          <div class="seg">
            <button type="button" id="petSrcEmoji" class="${draft.petSource === "emoji" ? "on" : ""}">Emoji / ID</button>
            <button type="button" id="petSrcImage" class="${draft.petSource === "image" ? "on" : ""}">Bild</button>
          </div>
          ${
            draft.petSource === "image"
              ? `<div class="panel">
                  <div class="chroma-swatch"><i></i> Chroma-Key: <strong>${CHROMA_KEY}</strong></div>
                  <p class="help" style="margin:0.55rem 0">Motiv auf reinem Grün <code>${CHROMA_KEY}</code> — wird transparent. Ausschnitt per Drag/Scroll wählen.</p>
                  <div class="actions">
                    <button type="button" class="btn" id="openPetImage">${draft.imageDataUrl ? "Bild ändern" : "Neues Bild"}</button>
                    ${draft.imageDataUrl ? `<button type="button" class="btn ghost" id="clearPetImage">Bild entfernen</button>` : ""}
                  </div>
                  ${
                    draft.imageDataUrl
                      ? `<div class="pet-preview-row"><div class="pet-preview-circle"><img src="${draft.imageDataUrl}" alt="Begleiter" /></div><span class="muted">Vorschau übernommen</span></div>`
                      : ""
                  }
                </div>
                <label class="field" style="margin-top:0.75rem">Name
                  <input name="label" value="${esc(draft.label === "Herz" ? "Bild-Begleiter" : draft.label)}" />
                </label>
                <label class="field" style="margin-top:0.6rem">Suchworte
                  <input name="searchText" value="${esc(draft.searchText)}" placeholder="begleiter pet custom" />
                </label>`
              : `<div class="grid-2">
                  <label class="field">Emoji / ID<input name="itemId" value="${esc(draft.itemId)}" /></label>
                  <label class="field">Name<input name="label" value="${esc(draft.label)}" /></label>
                </div>
                <label class="field" style="margin-top:0.6rem">Suchworte
                  <input name="searchText" value="${esc(draft.searchText)}" />
                </label>`
          }`;
        }
        return `<div class="grid-2">
          <label class="field">Emoji / ID<input name="itemId" value="${esc(draft.itemId)}" /></label>
          <label class="field">Name<input name="label" value="${esc(draft.label)}" /></label>
        </div>
        <label class="field" style="margin-top:0.6rem">Suchworte
          <input name="searchText" value="${esc(draft.searchText)}" />
          <span class="tip">z. B. „herz heart“ — Nutzer finden das Item über die Lupe</span>
        </label>`;
      }
      if (step === 2)
        return `<label class="field">Listenpreis in Coins
          <input name="priceCoins" type="number" min="1" value="${draft.priceCoins}" />
          <span class="tip">Lootbox-Seltenheit folgt dem Preis (8–12 häufig, 100+ sehr selten).</span>
        </label>`;
      if (step === 3)
        return `<div class="grid-2">
          <label class="field">Angebotspreis<input name="salePrice" type="number" value="${draft.salePrice ?? ""}" placeholder="optional" /></label>
          <label class="field">Durchgestrichener Preis<input name="compareAtPrice" type="number" value="${draft.compareAtPrice ?? ""}" placeholder="optional" /></label>
          <label class="field">Ende<input name="availableUntil" type="datetime-local" value="${
            draft.availableUntil ? new Date(draft.availableUntil).toISOString().slice(0, 16) : ""
          }" /></label>
        </div>
        <p class="help">Leer lassen = dauerhaft. Mit Datum = zeitlich begrenzt; Countdown erscheint in der App.</p>`;
      if (step === 4)
        return `<div class="grid-2">
          <label class="field">Max. Verkäufe gesamt<input name="maxTotalSales" type="number" value="${draft.maxTotalSales ?? ""}" placeholder="∞" /></label>
          <label class="field">Max. pro Nutzer<input name="maxPerUser" type="number" value="${draft.maxPerUser ?? ""}" placeholder="∞" /></label>
        </div>
        <p class="help">Beispiel: max. 100 Verkäufe gesamt und 1× pro Nutzer = exklusives Limit-Item.</p>`;
      return `<div class="panel">
        <p><strong>${
          draft.kind === "pets" && draft.petSource === "image"
            ? "🐾 Bild-Begleiter"
            : esc(draft.itemId)
        }</strong> · ${esc(draft.kind)}</p>
        ${
          draft.imageDataUrl
            ? `<div class="pet-preview-row"><div class="pet-preview-circle"><img src="${draft.imageDataUrl}" alt="" /></div></div>`
            : ""
        }
        <p>${esc(draft.label)} — ${draft.priceCoins} Coins
          ${draft.salePrice != null ? `(Angebot ${draft.salePrice})` : ""}</p>
        <p class="muted">Suche: ${esc(draft.searchText || "—")}</p>
        <p class="muted">→ landet automatisch in der Lootbox</p>
        <p class="muted">Timer: ${draft.availableUntil ? new Date(draft.availableUntil).toLocaleString() : "kein"}</p>
        <p class="muted">Limits: gesamt ${draft.maxTotalSales ?? "∞"}, p.P. ${draft.maxPerUser ?? "∞"}</p>
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
    content.innerHTML = `
      <div class="panel">
        <h3>Markt-Preisfenster</h3>
        <p class="help">Über wie viele Tage Verkaufspreise für die Spanne „ab X Coins“ berechnet werden.</p>
        <div class="toolbar">
          <input id="priceDays" type="number" min="1" max="90" value="${esc(s.priceWindowDays ?? 14)}" />
          <button class="btn" id="saveMarket">Speichern</button>
        </div>
      </div>`;
    $("saveMarket").onclick = async () => {
      await api("/admin/market-settings", {
        method: "PUT",
        body: JSON.stringify({ priceWindowDays: Number($("priceDays").value || 14) }),
      });
      alert("Gespeichert");
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

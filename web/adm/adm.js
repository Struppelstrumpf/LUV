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
  }
  function closeModal() {
    modal.hidden = true;
    modalCard.innerHTML = "";
  }
  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
  });

  async function googleCredential(idToken) {
    const json = await api("/auth/google", {
      method: "POST",
      body: JSON.stringify({ idToken }),
    });
    const token = json.sessionToken || json.token;
    const user = json.user;
    if (!token || !user) throw new Error("Login unvollständig");
    if (!isStaff(user)) throw new Error("Kein Admin-/Mod-Zugang für dieses Konto.");
    setSession(token, user);
    showApp();
  }

  function initGoogle() {
    api("/auth/config")
      .then((cfg) => {
        const clientId = cfg.googleWebClientId;
        if (!clientId || !window.google?.accounts?.id) {
          $("loginError").hidden = false;
          $("loginError").textContent = "Google-Login nicht verfügbar.";
          return;
        }
        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (resp) => {
            googleCredential(resp.credential).catch((e) => showLogin(e.message));
          },
        });
        window.google.accounts.id.renderButton($("googleBtn"), {
          theme: "filled_black",
          size: "large",
          shape: "pill",
          text: "continue_with",
          width: 320,
        });
      })
      .catch(() => {
        $("loginError").hidden = false;
        $("loginError").textContent = "API nicht erreichbar.";
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
    const q = new URLSearchParams();
    if (state.shopKind) q.set("kind", state.shopKind);
    if (state.shopQ) q.set("q", state.shopQ);
    const data = await api("/admin/shop/items?" + q.toString());
    state.shopItems = data.items || [];
    content.innerHTML = `
      <div class="panel">
        <h3>Itemshop verwalten</h3>
        <p class="help">
          <strong>Listenpreis</strong> = normaler Preis.
          <strong>Angebotspreis</strong> = was der Nutzer zahlt (alter Preis durchgestrichen).
          <strong>Timer</strong> = verfügbar bis; danach wird der Artikel deaktiviert.
          <strong>Max. gesamt / pro Nutzer</strong> leer = unendlich.
        </p>
        <div class="toolbar">
          <select id="shopKind">
            <option value="">Alle Kategorien</option>
            <option value="emojis">Emojis</option>
            <option value="stickers">Sticker</option>
            <option value="themes">Hintergründe</option>
            <option value="pets">Begleiter</option>
          </select>
          <input id="shopQ" placeholder="Suchen…" value="${esc(state.shopQ)}" />
          <button class="btn secondary" id="shopFilter">Filtern</button>
          <button class="btn teal" id="shopWizard">+ Neues Item (Wizard)</button>
        </div>
        <div style="overflow:auto">
          <table>
            <thead>
              <tr>
                <th>Item</th><th>Preis</th><th>Angebot</th><th>Timer</th><th>Limits</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              ${state.shopItems
                .map((it) => {
                  const sale =
                    it.onSale && it.compareAtPrice
                      ? `<span class="price-old">${it.compareAtPrice}</span><strong>${it.priceCoins}</strong>`
                      : `${it.priceCoins}`;
                  const timer = it.availableUntil
                    ? fmtMs(it.remainingMs) + (it.enabled ? "" : " (aus)")
                    : "—";
                  const limits = [
                    it.maxTotalSales != null ? `gesamt ${it.soldTotal || 0}/${it.maxTotalSales}` : "gesamt ∞",
                    it.maxPerUser != null ? `p.P. ${it.maxPerUser}` : "p.P. ∞",
                  ].join(" · ");
                  return `<tr>
                    <td>${esc(it.emoji || it.itemId)} <span class="muted">${esc(it.kind)}</span><br/><small>${esc(it.label)}</small></td>
                    <td>${it.listPrice ?? it.priceCoins}</td>
                    <td>${sale}${it.onSale ? ' <span class="badge sale">Angebot</span>' : ""}</td>
                    <td>${esc(timer)}</td>
                    <td>${esc(limits)}</td>
                    <td>${it.enabled ? '<span class="badge">aktiv</span>' : '<span class="badge off">aus</span>'}</td>
                    <td class="actions">
                      <button class="btn secondary" data-edit="${esc(it.kind)}|${esc(it.itemId)}">Bearbeiten</button>
                      <button class="btn ghost" data-toggle="${esc(it.kind)}|${esc(it.itemId)}|${it.enabled ? "off" : "on"}">${it.enabled ? "Deaktivieren" : "Aktivieren"}</button>
                    </td>
                  </tr>`;
                })
                .join("")}
            </tbody>
          </table>
        </div>
      </div>`;
    $("shopKind").value = state.shopKind;
    $("shopFilter").onclick = () => {
      state.shopKind = $("shopKind").value;
      state.shopQ = $("shopQ").value.trim();
      renderShop();
    };
    $("shopWizard").onclick = () => openWizard();
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
      const form = $("wizForm");
      form.onsubmit = async (e) => {
        e.preventDefault();
        if (!saveStep()) return;
        await api("/admin/shop/items", { method: "POST", body: JSON.stringify(draft) });
        closeModal();
        renderShop();
      };
    }

    function saveStep() {
      const form = $("wizForm");
      if (!form) return true;
      const fd = new FormData(form);
      if (step === 0) draft.kind = String(fd.get("kind") || "emojis");
      if (step === 1) {
        draft.itemId = String(fd.get("itemId") || "").trim();
        draft.label = String(fd.get("label") || draft.itemId).trim();
        draft.searchText = String(fd.get("searchText") || "").trim();
        if (!draft.itemId) {
          alert("Bitte Item-ID / Emoji eingeben.");
          return false;
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
          <span class="tip">Später jederzeit änderbar — bestimmt nur, wo es im Shop erscheint.</span>
        </label>`;
      if (step === 1)
        return `<div class="grid-2">
          <label class="field">Emoji / ID<input name="itemId" value="${esc(draft.itemId)}" /></label>
          <label class="field">Name<input name="label" value="${esc(draft.label)}" /></label>
        </div>
        <label class="field" style="margin-top:0.6rem">Suchworte
          <input name="searchText" value="${esc(draft.searchText)}" />
          <span class="tip">z. B. „herz heart“ — Nutzer finden das Item über die Lupe</span>
        </label>`;
      if (step === 2)
        return `<label class="field">Listenpreis in Coins
          <input name="priceCoins" type="number" min="1" value="${draft.priceCoins}" />
          <span class="tip">Normale Preise liegen oft bei 5–50. Extrem seltene Items können tausende Coins kosten.</span>
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
        <p><strong>${esc(draft.emoji || draft.itemId)}</strong> · ${esc(draft.kind)}</p>
        <p>${esc(draft.label)} — ${draft.priceCoins} Coins
          ${draft.salePrice != null ? `(Angebot ${draft.salePrice})` : ""}</p>
        <p class="muted">Suche: ${esc(draft.searchText || "—")}</p>
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
        </div>
      </div>`;
    $("sendLive").onclick = async () => {
      await api("/admin/live-notice", {
        method: "POST",
        body: JSON.stringify({ message: $("liveText").value.trim() }),
      });
      alert("Hinweis gesendet");
    };
  }

  $("logoutBtn").onclick = () => {
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
  // Google script may load late
  const gWait = setInterval(() => {
    if (window.google?.accounts?.id) {
      clearInterval(gWait);
      if (!state.token) initGoogle();
    }
  }, 300);
  setTimeout(() => clearInterval(gWait), 8000);
})();

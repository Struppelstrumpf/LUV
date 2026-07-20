/**
 * Erfolge-Wizard + Nutzer-Vollverwaltung für LUV Admin.
 * Hängt an window.LuvAdmPanels — braucht api/esc/openModal/closeModal/$ vom Host.
 */
(() => {
  function host() {
    return window.LuvAdmHost || {};
  }

  function api(path, opts) {
    return host().api(path, opts);
  }
  function esc(s) {
    return host().esc(s);
  }
  function $(id) {
    return host().$(id);
  }
  function openModal(html, wide) {
    return host().openModal(html, wide);
  }
  function closeModal() {
    return host().closeModal();
  }
  function contentEl() {
    return host().content;
  }
  function goTab(id, opts) {
    return host().goTab(id, opts);
  }

  const CAT_EMOJI = {
    sozial: "💬",
    begleiter: "🐾",
    malen: "🎨",
    markt: "🏪",
    profil: "👤",
  };

  function rewardHtml(a) {
    if (a.rewardItem) {
      const r = a.rewardItem;
      return `<span class="ach-reward">${esc(r.emoji || "")} ${esc(r.label || r.itemId)} <span class="muted">(${esc(r.kind)})</span></span>`;
    }
    return `<span class="ach-reward">🪙 ${a.coins || 0} Coins</span>`;
  }

  async function renderAchievements(focusId) {
    const content = contentEl();
    const data = await api("/admin/achievements");
    const list = data.achievements || [];
    const q = (host().state.achQ || "").trim().toLowerCase();
    const cat = host().state.achCat || "";
    let filtered = list;
    if (cat) filtered = filtered.filter((a) => a.category === cat);
    if (q) {
      filtered = filtered.filter((a) => {
        const hay = `${a.title} ${a.desc} ${a.id} ${a.metric}`.toLowerCase();
        return hay.includes(q);
      });
    }

    content.innerHTML = `
      <div class="panel ach-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Erfolge</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:42rem">
              Alle Erfolge, Belohnungen und Bedingungen. Deaktivieren blendet sie für Spieler aus.
              Tageslimit aktuell: <strong>${esc(data.dailyCap ?? 12)}</strong> Coins
              (änderbar unter Einstellungen).
            </p>
          </div>
          <button class="btn teal" id="achWizard">＋ Neuer Erfolg</button>
        </div>
        <div class="shop-cats" id="achCats">
          <button type="button" class="shop-cat ${!cat ? "on" : ""}" data-cat="">Alle <span class="shop-cat-n">${list.length}</span></button>
          ${(data.categories || [])
            .map((c) => {
              const n = list.filter((a) => a.category === c.id).length;
              return `<button type="button" class="shop-cat ${cat === c.id ? "on" : ""}" data-cat="${esc(c.id)}">${CAT_EMOJI[c.id] || ""} ${esc(c.label)} <span class="shop-cat-n">${n}</span></button>`;
            })
            .join("")}
        </div>
        <div class="toolbar shop-search-bar">
          <input id="achQ" placeholder="Suchen (Titel, ID, Metrik)…" value="${esc(host().state.achQ || "")}" />
          <button class="btn secondary" id="achFilter">Suchen</button>
        </div>
      </div>
      <div class="ach-grid">
        ${
          filtered.length
            ? filtered
                .map((a) => {
                  const focus = focusId && focusId === a.id ? " is-focus" : "";
                  return `<article class="ach-card${a.disabled ? " is-off" : ""}${focus}" id="ach-${esc(a.id)}" data-ach-id="${esc(a.id)}">
              <header>
                <div>
                  <strong>${esc(a.title)}</strong>
                  <div class="muted mono" style="font-size:0.75rem">${esc(a.id)}</div>
                </div>
                <span class="badge src-achievement">${esc(CAT_EMOJI[a.category] || "")} ${esc(a.category)}</span>
              </header>
              <p class="ach-desc">${esc(a.desc)}</p>
              <div class="ach-meta">
                <span>Ziel: <strong>${esc(a.metric)}</strong> ≥ ${a.target}</span>
                ${rewardHtml(a)}
              </div>
              <div class="ach-flags">
                ${a.disabled ? '<span class="badge off">Deaktiviert</span>' : '<span class="badge src-shop">Aktiv</span>'}
                ${a.custom ? '<span class="badge src-code">Eigen</span>' : '<span class="badge src-starter">Builtin</span>'}
              </div>
              <div class="actions">
                <button type="button" class="btn secondary" data-edit-ach="${esc(a.id)}">Bearbeiten</button>
                <button type="button" class="btn ghost" data-toggle-ach="${esc(a.id)}|${a.disabled ? "on" : "off"}">${a.disabled ? "Aktivieren" : "Deaktivieren"}</button>
                ${a.custom ? `<button type="button" class="btn btn-del" data-del-ach="${esc(a.id)}">Löschen</button>` : ""}
              </div>
            </article>`;
                })
                .join("")
            : `<div class="panel"><p class="help">Keine Erfolge für diesen Filter.</p></div>`
        }
      </div>`;

    $("achWizard").onclick = () => openAchievementWizard(data);
    $("achFilter").onclick = () => {
      host().state.achQ = $("achQ").value.trim();
      renderAchievements(focusId);
    };
    $("achQ").onkeydown = (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        host().state.achQ = $("achQ").value.trim();
        renderAchievements(focusId);
      }
    };
    content.querySelectorAll("#achCats [data-cat]").forEach((btn) => {
      btn.onclick = () => {
        host().state.achCat = btn.getAttribute("data-cat") || "";
        renderAchievements(focusId);
      };
    });
    content.querySelectorAll("[data-edit-ach]").forEach((btn) => {
      btn.onclick = () => {
        const id = btn.getAttribute("data-edit-ach");
        const a = list.find((x) => x.id === id);
        if (a) openAchievementWizard(data, a);
      };
    });
    content.querySelectorAll("[data-toggle-ach]").forEach((btn) => {
      btn.onclick = async () => {
        const [id, mode] = btn.getAttribute("data-toggle-ach").split("|");
        const path =
          mode === "off"
            ? `/admin/achievements/${encodeURIComponent(id)}/disable`
            : `/admin/achievements/${encodeURIComponent(id)}/enable`;
        await api(path, { method: "POST", body: "{}" });
        renderAchievements(id);
      };
    });
    content.querySelectorAll("[data-del-ach]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.getAttribute("data-del-ach");
        if (!confirm(`Eigenen Erfolg „${id}“ wirklich löschen?`)) return;
        await api(`/admin/achievements/${encodeURIComponent(id)}`, { method: "DELETE" });
        renderAchievements();
      };
    });

    if (focusId) {
      const el = document.getElementById(`ach-${focusId}`);
      if (el) {
        el.scrollIntoView({ behavior: "smooth", block: "center" });
        el.classList.add("pulse-once");
      }
    }
  }

  function openAchievementWizard(meta, existing) {
    const categories = meta.categories || [];
    const metrics = meta.metrics || [];
    let step = 0;
    const draft = {
      id: existing?.id || "",
      title: existing?.title || "",
      desc: existing?.desc || "",
      category: existing?.category || "profil",
      metric: existing?.metric || "friends",
      target: existing?.target || 1,
      coins: existing?.coins || 1,
      coinsFallback: existing?.coinsFallback || 3,
      rewardKind: existing?.rewardItem?.kind || "",
      rewardItemId: existing?.rewardItem?.itemId || "",
      rewardLabel: existing?.rewardItem?.label || "",
      useItem: Boolean(existing?.rewardItem),
      disabled: Boolean(existing?.disabled),
    };
    const steps = ["Grundlagen", "Bedingung", "Belohnung", "Prüfen"];
    const isEdit = Boolean(existing);

    function paint() {
      openModal(
        `
        <h3 style="font-family:var(--display);margin:0 0 0.35rem">${isEdit ? "Erfolg bearbeiten" : "Neuer Erfolg"}</h3>
        <div class="wizard-steps">${steps
          .map((s, i) => `<span class="${i === step ? "on" : i < step ? "done" : ""}">${i + 1}. ${s}</span>`)
          .join("")}</div>
        <div id="achStepBody"></div>
        <div class="actions" style="margin-top:1rem">
          ${step > 0 ? `<button type="button" class="btn ghost" id="achBack">Zurück</button>` : `<button type="button" class="btn ghost" id="achCancel">Abbrechen</button>`}
          <button type="button" class="btn" id="achNext">${step >= steps.length - 1 ? "Speichern" : "Weiter"}</button>
        </div>`,
        true
      );
      const body = $("achStepBody");
      if (step === 0) {
        body.innerHTML = `
          <label class="field">Titel
            <input id="awTitle" maxlength="48" value="${esc(draft.title)}" placeholder="z. B. Wildkatze" />
          </label>
          <label class="field">Beschreibung
            <textarea id="awDesc" maxlength="160" rows="3" placeholder="Was muss der Spieler tun?">${esc(draft.desc)}</textarea>
          </label>
          <label class="field">Kategorie
            <select id="awCat">${categories
              .map(
                (c) =>
                  `<option value="${esc(c.id)}" ${draft.category === c.id ? "selected" : ""}>${esc(c.label)}</option>`
              )
              .join("")}</select>
          </label>
          ${
            isEdit
              ? `<p class="help mono">ID: ${esc(draft.id)}</p>`
              : `<label class="field">ID (optional)
            <input id="awId" maxlength="48" value="${esc(draft.id)}" placeholder="leer = automatisch" />
            <span class="tip">Nur Kleinbuchstaben, Zahlen, Unterstrich</span>
          </label>`
          }`;
      } else if (step === 1) {
        body.innerHTML = `
          <label class="field">Metrik
            <select id="awMetric">${metrics
              .map(
                (m) =>
                  `<option value="${esc(m.id)}" ${draft.metric === m.id ? "selected" : ""}>${esc(m.label)} <span class="muted">(${esc(m.id)})</span></option>`
              )
              .join("")}</select>
            <span class="tip">Wann zählt der Fortschritt?</span>
          </label>
          <label class="field">Zielwert
            <input id="awTarget" type="number" min="1" max="1000000" value="${esc(draft.target)}" />
            <span class="tip">Erfolg freigeschaltet wenn Metrik ≥ Ziel</span>
          </label>`;
      } else if (step === 2) {
        body.innerHTML = `
          <div class="seg" id="awRewardSeg">
            <button type="button" class="${!draft.useItem ? "on" : ""}" data-mode="coins">Coins</button>
            <button type="button" class="${draft.useItem ? "on" : ""}" data-mode="item">Item</button>
          </div>
          <div id="awRewardFields"></div>`;
        const paintReward = () => {
          const box = $("awRewardFields");
          if (!draft.useItem) {
            box.innerHTML = `
              <label class="field">Coins (1–50)
                <input id="awCoins" type="number" min="1" max="50" value="${esc(Math.max(1, draft.coins || 1))}" />
                <span class="tip">Zählt gegen das Tageslimit</span>
              </label>`;
          } else {
            box.innerHTML = `
              <div class="grid-2">
                <label class="field">Art
                  <select id="awRKind">
                    ${["pets", "stickers", "emojis", "themes"]
                      .map(
                        (k) =>
                          `<option value="${k}" ${draft.rewardKind === k ? "selected" : ""}>${k}</option>`
                      )
                      .join("")}
                  </select>
                </label>
                <label class="field">Item-ID / Emoji
                  <input id="awRId" maxlength="32" value="${esc(draft.rewardItemId)}" placeholder="🐯" />
                </label>
                <label class="field">Anzeigename
                  <input id="awRLabel" maxlength="40" value="${esc(draft.rewardLabel)}" placeholder="Tiger" />
                </label>
                <label class="field">Coin-Fallback
                  <input id="awFallback" type="number" min="0" max="50" value="${esc(draft.coinsFallback)}" />
                  <span class="tip">Wenn Unique-Item schon besitzt</span>
                </label>
              </div>`;
          }
        };
        paintReward();
        body.querySelectorAll("#awRewardSeg [data-mode]").forEach((b) => {
          b.onclick = () => {
            draft.useItem = b.getAttribute("data-mode") === "item";
            body.querySelectorAll("#awRewardSeg button").forEach((x) => x.classList.remove("on"));
            b.classList.add("on");
            paintReward();
          };
        });
      } else {
        const rewardLine = draft.useItem
          ? `Item ${draft.rewardKind}:${draft.rewardItemId} „${draft.rewardLabel || draft.rewardItemId}"`
          : `${draft.coins} Coins`;
        body.innerHTML = `
          <div class="ach-review">
            <h4 style="margin:0 0 0.5rem;font-family:var(--display)">${esc(draft.title)}</h4>
            <p class="help">${esc(draft.desc)}</p>
            <ul class="ach-review-list">
              <li><strong>Kategorie:</strong> ${esc(draft.category)}</li>
              <li><strong>Bedingung:</strong> ${esc(draft.metric)} ≥ ${esc(draft.target)}</li>
              <li><strong>Belohnung:</strong> ${esc(rewardLine)}</li>
              ${isEdit ? `<li><strong>ID:</strong> <span class="mono">${esc(draft.id)}</span></li>` : ""}
            </ul>
            <p class="help">Nach dem Speichern sofort für alle Spieler aktiv (außer deaktiviert).</p>
          </div>`;
      }

      const cancel = $("achCancel");
      if (cancel) cancel.onclick = closeModal;
      const back = $("achBack");
      if (back)
        back.onclick = () => {
          readStep();
          step -= 1;
          paint();
        };
      $("achNext").onclick = async () => {
        if (!readStep()) return;
        if (step < steps.length - 1) {
          step += 1;
          paint();
          return;
        }
        const bodyPayload = {
          id: draft.id || undefined,
          title: draft.title,
          desc: draft.desc,
          category: draft.category,
          metric: draft.metric,
          target: Number(draft.target) || 1,
          coins: draft.useItem ? 0 : Number(draft.coins) || 1,
          coinsFallback: draft.useItem ? Number(draft.coinsFallback) || 3 : undefined,
          rewardItem: draft.useItem
            ? {
                kind: draft.rewardKind || "pets",
                itemId: draft.rewardItemId,
                emoji: draft.rewardItemId,
                label: draft.rewardLabel || draft.rewardItemId,
              }
            : null,
          disabled: draft.disabled,
        };
        try {
          if (isEdit) {
            await api(`/admin/achievements/${encodeURIComponent(draft.id)}`, {
              method: "PUT",
              body: JSON.stringify(bodyPayload),
            });
          } else {
            await api("/admin/achievements", {
              method: "POST",
              body: JSON.stringify(bodyPayload),
            });
          }
          closeModal();
          renderAchievements(draft.id || undefined);
        } catch (err) {
          alert(err?.message || "Speichern fehlgeschlagen");
        }
      };
    }

    function readStep() {
      if (step === 0) {
        draft.title = ($("awTitle")?.value || "").trim();
        draft.desc = ($("awDesc")?.value || "").trim();
        draft.category = $("awCat")?.value || "profil";
        if (!isEdit) draft.id = ($("awId")?.value || "").trim();
        if (!draft.title || !draft.desc) {
          alert("Titel und Beschreibung ausfüllen.");
          return false;
        }
      } else if (step === 1) {
        draft.metric = $("awMetric")?.value || draft.metric;
        draft.target = Number($("awTarget")?.value || 1);
        if (!draft.metric || draft.target < 1) {
          alert("Metrik und Ziel prüfen.");
          return false;
        }
      } else if (step === 2) {
        if (!draft.useItem) {
          draft.coins = Number($("awCoins")?.value || 1);
          if (draft.coins < 1) {
            alert("Mindestens 1 Coin.");
            return false;
          }
        } else {
          draft.rewardKind = $("awRKind")?.value || "pets";
          draft.rewardItemId = ($("awRId")?.value || "").trim();
          draft.rewardLabel = ($("awRLabel")?.value || "").trim();
          draft.coinsFallback = Number($("awFallback")?.value || 3);
          if (!draft.rewardItemId) {
            alert("Item-ID fehlt.");
            return false;
          }
        }
      }
      return true;
    }

    paint();
  }

  // —— Nutzer ——
  async function renderUsers() {
    const content = contentEl();
    content.innerHTML = `
      <div class="panel">
        <h3 style="margin:0;font-family:var(--display)">Nutzer</h3>
        <p class="help">Suche nach Nick, E-Mail oder ID — dann Vollprofil mit Erfolgen, Logs, Lobbys und Einstellungen.</p>
        <div class="toolbar">
          <input id="userQ" placeholder="Suche…" style="min-width:240px" value="${esc(host().state.userQ || "")}" />
          <button class="btn secondary" id="userSearch">Suchen</button>
        </div>
        <div id="userResults" class="list"></div>
      </div>
      <div id="userDetail"></div>`;

    const runSearch = async () => {
      const q = $("userQ").value.trim();
      host().state.userQ = q;
      if (!q) {
        $("userResults").innerHTML = `<p class="muted">Mindestens 1 Zeichen eingeben.</p>`;
        return;
      }
      const data = await api("/admin/users/search?q=" + encodeURIComponent(q));
      const users = data.users || [];
      $("userResults").innerHTML = users.length
        ? users
            .map(
              (u) => `<button type="button" class="list-item user-pick" data-uid="${esc(u.userId)}">
            <div class="row">
              <div>
                <strong>${esc(u.nickname)}</strong>
                ${u.banned ? '<span class="badge off">Gesperrt</span>' : ""}
                <div class="muted">${esc(u.email || "keine Google-Mail")} · ${u.coins || 0} Coins · ${esc(u.role || "user")}</div>
                <div class="muted mono" style="font-size:0.72rem">${esc(u.userId)}</div>
              </div>
            </div>
          </button>`
            )
            .join("")
        : `<p class="muted">Keine Treffer.</p>`;
      $("userResults").querySelectorAll("[data-uid]").forEach((b) => {
        b.onclick = () => openUserDetail(b.getAttribute("data-uid"));
      });
    };

    $("userSearch").onclick = runSearch;
    $("userQ").onkeydown = (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        runSearch();
      }
    };
    if (host().state.userFocusId) {
      const id = host().state.userFocusId;
      host().state.userFocusId = null;
      openUserDetail(id);
    } else if (host().state.userQ) {
      runSearch();
    }
  }

  async function openUserDetail(userId) {
    const box = $("userDetail");
    if (!box) return;
    box.innerHTML = `<div class="panel"><p class="muted">Lade Profil…</p></div>`;
    const data = await api(`/admin/users/${encodeURIComponent(userId)}`);
    const u = data.user || {};
    const ach = data.achievements || {};
    const inv = data.inventory || {};
    const lobbies = data.lobbies || [];
    const ledger = data.ledger || [];
    const audit = data.audit || [];
    const warnings = data.warnings || [];
    const logFilter = { q: "", action: "" };

    function paint() {
      const unlocked = Object.keys(ach.unlocked || {});
      box.innerHTML = `
        <div class="user-detail">
          <div class="panel user-hero">
            <div class="shop-top">
              <div>
                <h3 style="margin:0;font-family:var(--display)">${esc(u.nickname)} ${u.petEmoji || ""}</h3>
                <p class="help" style="margin:0.35rem 0 0">
                  <span class="mono">${esc(u.userId)}</span><br/>
                  Google: <strong>${esc(u.email || "—")}</strong>
                  ${u.googleLinked ? " · verknüpft" : " · nicht verknüpft"}
                  · Rolle ${esc(u.role)}
                  ${u.banned ? ` · <span class="badge off">Gesperrt${u.bannedReason ? ": " + esc(u.bannedReason) : ""}</span>` : ""}
                </p>
              </div>
              <button type="button" class="btn ghost" id="udClose">Schließen</button>
            </div>
            <div class="user-stat-grid">
              <div class="card"><div class="k">Coins</div><div class="v">${u.coins ?? 0}</div><div class="muted">paid ${u.paidCoins ?? 0} · daily ${u.dailyBalance ?? 0}</div></div>
              <div class="card"><div class="k">Daily-Streak</div><div class="v">${ach.streak ?? 0}</div><div class="muted">letzte Daily ${esc(ach.lastDailyCompleteDate || "—")}</div></div>
              <div class="card"><div class="k">Erfolge</div><div class="v">${ach.unlockedCount ?? 0}/${ach.totalCount ?? 0}</div><div class="muted">${unlocked.length} freigeschaltet</div></div>
              <div class="card"><div class="k">Freunde</div><div class="v">${data.friends?.count ?? 0}</div><div class="muted">↓${data.friends?.incoming ?? 0} · ↑${data.friends?.outgoing ?? 0}</div></div>
            </div>
          </div>

          <div class="panel">
            <h3>Schnellaktionen</h3>
            <div class="toolbar wrap">
              <input id="udCoins" type="number" placeholder="±Coins" style="width:110px" />
              <button class="btn secondary" id="udCoinsBtn">Coins</button>
              <input id="udNick" placeholder="Neuer Nick" value="${esc(u.nickname || "")}" style="width:140px" />
              <button class="btn ghost" id="udNickBtn">Nick</button>
              <input id="udStreak" type="number" min="0" value="${esc(ach.streak ?? 0)}" style="width:90px" title="Daily-Streak" />
              <button class="btn ghost" id="udStreakBtn">Streak setzen</button>
              <button class="btn danger" id="udBanBtn">${u.banned ? "Entsperren" : "Sperren"}</button>
            </div>
            <div class="toolbar wrap" style="margin-top:0.6rem">
              <input id="udWarn" placeholder="Verwarnung (erscheint unter Sozial · Freunde)…" style="flex:1;min-width:220px" maxlength="280" />
              <select id="udWarnSev"><option value="warn">Verwarnung</option><option value="final">Letzte Warnung</option></select>
              <button class="btn" id="udWarnBtn">Verwarnen</button>
            </div>
          </div>

          <div class="panel">
            <h3>Inventar</h3>
            <p class="help">Begleiter: ${(inv.pets || []).map((p) => esc(p)).join(" ") || "—"}
              · Themes: ${(inv.themes || []).length} · Sticker: ${inv.stickerCount || 0} · Emojis: ${inv.emojiCount || 0}
              · ausgerüstet: ${esc(inv.equippedPet || "—")}</p>
          </div>

          <div class="panel">
            <h3>Item schenken</h3>
            <p class="help">Beliebiges Item ins Inventar legen. Der Nutzer sieht eine Benachrichtigung unter Sozial → Freunde.</p>
            <div class="toolbar wrap">
              <select id="udGiftKind">
                <option value="">Alle</option>
                <option value="emojis">Emojis</option>
                <option value="stickers">Sticker</option>
                <option value="pets">Begleiter</option>
                <option value="themes">Hintergründe</option>
              </select>
              <input id="udGiftQ" placeholder="Suchen…" style="flex:1;min-width:140px" />
              <button type="button" class="btn secondary" id="udGiftSearch">Suchen</button>
              <input id="udGiftQty" type="number" min="1" max="50" value="1" style="width:70px" title="Anzahl (Emoji/Sticker)" />
            </div>
            <input id="udGiftMsg" placeholder="Optionale Nachricht…" maxlength="160" style="width:100%;margin-top:0.45rem" />
            <div id="udGiftList" class="ud-gift-list" style="margin-top:0.55rem;max-height:240px;overflow:auto"></div>
          </div>

          <div class="panel">
            <div class="shop-top">
              <h3 style="margin:0">Erfolge des Nutzers</h3>
              <button class="btn ghost" id="udAchReset">Fortschritt zurücksetzen</button>
            </div>
            <div class="ach-mini-list">
              ${(ach.list || [])
                .slice(0, 80)
                .map((a) => {
                  const st = a.claimed ? "abgeholt" : a.unlocked ? "frei" : `${a.progress}/${a.target}`;
                  return `<div class="ach-mini ${a.unlocked ? "on" : ""}">
                    <button type="button" class="linkish" data-goto-ach="${esc(a.id)}" title="${esc(a.desc)}">${esc(a.title)}</button>
                    <span class="muted">${esc(st)}</span>
                    <span class="actions tight">
                      ${!a.unlocked ? `<button type="button" class="btn ghost" data-ach-act="unlock|${esc(a.id)}">Unlock</button>` : ""}
                      ${a.unlocked && !a.claimed ? `<button type="button" class="btn ghost" data-ach-act="lock|${esc(a.id)}">Lock</button>` : ""}
                      ${a.claimed ? `<button type="button" class="btn ghost" data-ach-act="unclaim|${esc(a.id)}">Unclaim</button>` : ""}
                    </span>
                  </div>`;
                })
                .join("")}
            </div>
          </div>

          <div class="panel">
            <h3>Lobbys</h3>
            ${
              lobbies.length
                ? `<div class="list">${lobbies
                    .map(
                      (l) => `<div class="list-item">
                  <div><strong>${esc(l.name || l.code)}</strong>
                    <div class="muted mono">${esc(l.code)} · ${l.joined ? "beigetreten" : "gehostet"} · ${l.live ? "live" : l.active ? "aktiv" : "inaktiv"} · ${l.online ?? 0}/${l.capacity ?? "?"} online</div>
                  </div>
                  <div class="actions">
                    <button type="button" class="btn secondary" data-canvas="${esc(l.code)}">Leinwand ansehen</button>
                  </div>
                </div>`
                    )
                    .join("")}</div>`
                : `<p class="muted">Keine Lobbys (weder gehostet noch beigetreten).</p>`
            }
          </div>

          <div class="panel">
            <h3>Verwarnungen</h3>
            ${
              warnings.length
                ? `<div class="list">${warnings
                    .map(
                      (w) => `<div class="list-item">
                  <div><strong>${w.severity === "final" ? "Letzte Warnung" : "Verwarnung"}</strong>
                    <div>${esc(w.message)}</div>
                    <div class="muted">${esc(w.byNick || "")} · ${w.at ? new Date(w.at).toLocaleString("de-DE") : ""} ${w.seen ? "· gesehen" : "· neu"}</div>
                  </div>
                </div>`
                    )
                    .join("")}</div>`
                : `<p class="muted">Keine Verwarnungen.</p>`
            }
          </div>

          <div class="panel">
            <h3>Coin-Ledger</h3>
            <div class="log-table">${
              ledger.length
                ? `<table><thead><tr><th>Zeit</th><th>Δ</th><th>Grund</th><th>Stand</th></tr></thead><tbody>
              ${ledger
                .map(
                  (e) => `<tr>
                <td>${e.at ? new Date(e.at).toLocaleString("de-DE") : "—"}</td>
                <td class="${(e.delta || 0) >= 0 ? "pos" : "neg"}">${e.delta > 0 ? "+" : ""}${e.delta}</td>
                <td>${esc(e.reason || "")} <span class="muted mono">${esc(e.refId || "")}</span></td>
                <td>${e.balance ?? "—"}</td>
              </tr>`
                )
                .join("")}
              </tbody></table>`
                : `<p class="muted">Kein Ledger.</p>`
            }</div>
          </div>

          <div class="panel">
            <h3>Staff-Logs</h3>
            <div class="toolbar">
              <input id="udLogQ" placeholder="Filter / Suche…" value="${esc(logFilter.q)}" />
              <input id="udLogAction" placeholder="Aktion" value="${esc(logFilter.action)}" style="width:140px" />
              <button class="btn secondary" id="udLogFilter">Filtern</button>
            </div>
            <div class="log-table" id="udAuditBox"></div>
          </div>
        </div>`;

      const paintAudit = (entries) => {
        const el = $("udAuditBox");
        if (!el) return;
        el.innerHTML = entries.length
          ? `<table><thead><tr><th>Zeit</th><th>Aktion</th><th>Actor</th><th>Detail</th></tr></thead><tbody>
            ${entries
              .map(
                (e) => `<tr>
              <td>${e.at ? new Date(e.at).toLocaleString("de-DE") : "—"}</td>
              <td><code>${esc(e.action || "")}</code></td>
              <td>${esc(e.actorNick || e.actorId || "")}</td>
              <td class="mono" style="font-size:0.72rem">${esc(JSON.stringify(e.detail || {}))}</td>
            </tr>`
              )
              .join("")}
            </tbody></table>`
          : `<p class="muted">Keine Log-Einträge.</p>`;
      };
      paintAudit(audit);

      $("udClose").onclick = () => {
        box.innerHTML = "";
      };
      $("udCoinsBtn").onclick = async () => {
        await api(`/admin/users/${encodeURIComponent(userId)}/coins`, {
          method: "POST",
          body: JSON.stringify({ delta: Number($("udCoins").value || 0) }),
        });
        openUserDetail(userId);
      };
      $("udNickBtn").onclick = async () => {
        await api(`/admin/users/${encodeURIComponent(userId)}/nickname`, {
          method: "POST",
          body: JSON.stringify({ nickname: $("udNick").value }),
        });
        openUserDetail(userId);
      };
      $("udStreakBtn").onclick = async () => {
        await api(`/admin/users/${encodeURIComponent(userId)}/streak`, {
          method: "POST",
          body: JSON.stringify({ streak: Number($("udStreak").value || 0) }),
        });
        openUserDetail(userId);
      };
      $("udBanBtn").onclick = async () => {
        const reason = u.banned ? undefined : prompt("Grund (optional)", "Regelverstoß") || "staff_ban";
        await api(`/admin/users/${encodeURIComponent(userId)}/ban`, {
          method: "POST",
          body: JSON.stringify({ banned: !u.banned, reason }),
        });
        openUserDetail(userId);
      };
      $("udWarnBtn").onclick = async () => {
        const message = $("udWarn").value.trim();
        if (!message) return alert("Text fehlt");
        await api(`/admin/users/${encodeURIComponent(userId)}/warn`, {
          method: "POST",
          body: JSON.stringify({ message, severity: $("udWarnSev").value }),
        });
        $("udWarn").value = "";
        openUserDetail(userId);
      };

      async function loadGiftItems() {
        const list = $("udGiftList");
        if (!list) return;
        list.innerHTML = `<p class="muted">Lade Items…</p>`;
        const qs = new URLSearchParams();
        const kind = $("udGiftKind")?.value || "";
        const q = $("udGiftQ")?.value?.trim() || "";
        if (kind) qs.set("kind", kind);
        if (q) qs.set("q", q);
        try {
          const data = await api("/admin/items/universe?" + qs.toString());
          const items = (data.items || []).slice(0, 120);
          if (!items.length) {
            list.innerHTML = `<p class="muted">Keine Items gefunden.</p>`;
            return;
          }
          list.innerHTML = items
            .map((it) => {
              const key = `${it.kind}:${it.itemId}`;
              return `<div class="list-item ud-gift-row">
                <div class="ud-gift-emoji">${esc(it.emoji || it.itemId)}</div>
                <div style="flex:1;min-width:0">
                  <strong>${esc(it.label || it.itemId)}</strong>
                  <div class="muted mono" style="font-size:0.72rem">${esc(key)}</div>
                </div>
                <button type="button" class="btn" data-gift="${esc(it.kind)}|${esc(it.itemId)}|${esc(it.label || "")}|${esc(it.emoji || "")}">Schenken</button>
              </div>`;
            })
            .join("");
          list.querySelectorAll("[data-gift]").forEach((btn) => {
            btn.onclick = async () => {
              const raw = btn.getAttribute("data-gift") || "";
              const parts = raw.split("|");
              const gKind = parts[0];
              const gId = parts[1];
              const gLabel = parts[2] || gId;
              const gEmoji = parts[3] || "";
              if (!gKind || !gId) return;
              const qty = Number($("udGiftQty")?.value || 1);
              const message = $("udGiftMsg")?.value?.trim() || "";
              if (!confirm(`„${gLabel}“ (${gKind}) an ${u.nickname || userId} schenken?`)) return;
              try {
                btn.disabled = true;
                const res = await api(`/admin/users/${encodeURIComponent(userId)}/gift`, {
                  method: "POST",
                  body: JSON.stringify({
                    kind: gKind,
                    itemId: gId,
                    qty,
                    label: gLabel,
                    emoji: gEmoji,
                    message,
                  }),
                });
                alert(`Geschenkt: ${res.given?.emoji || ""} ${res.given?.label || gLabel}${res.given?.qty > 1 ? " ×" + res.given.qty : ""}`);
                openUserDetail(userId);
              } catch (err) {
                alert(err?.message || "Schenken fehlgeschlagen");
                btn.disabled = false;
              }
            };
          });
        } catch (err) {
          list.innerHTML = `<p class="muted">${esc(err?.message || "Laden fehlgeschlagen")}</p>`;
        }
      }
      if ($("udGiftSearch")) $("udGiftSearch").onclick = () => loadGiftItems();
      if ($("udGiftKind")) $("udGiftKind").onchange = () => loadGiftItems();
      if ($("udGiftQ")) {
        $("udGiftQ").onkeydown = (e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            loadGiftItems();
          }
        };
      }
      loadGiftItems();

      $("udAchReset").onclick = async () => {
        if (!confirm("Wirklich alle Erfolge/Fortschritt dieses Nutzers zurücksetzen?")) return;
        await api(`/admin/users/${encodeURIComponent(userId)}/achievements`, {
          method: "POST",
          body: JSON.stringify({ action: "reset_progress" }),
        });
        openUserDetail(userId);
      };
      box.querySelectorAll("[data-ach-act]").forEach((b) => {
        b.onclick = async () => {
          const [action, id] = b.getAttribute("data-ach-act").split("|");
          await api(`/admin/users/${encodeURIComponent(userId)}/achievements`, {
            method: "POST",
            body: JSON.stringify({ action, id }),
          });
          openUserDetail(userId);
        };
      });
      box.querySelectorAll("[data-goto-ach]").forEach((b) => {
        b.onclick = () => goTab("achievements", { focusId: b.getAttribute("data-goto-ach") });
      });
      box.querySelectorAll("[data-canvas]").forEach((b) => {
        b.onclick = () => openCanvasPreview(b.getAttribute("data-canvas"));
      });
      $("udLogFilter").onclick = async () => {
        logFilter.q = $("udLogQ").value.trim();
        logFilter.action = $("udLogAction").value.trim();
        const qs = new URLSearchParams({
          userId,
          limit: "200",
          q: logFilter.q,
          action: logFilter.action,
        });
        const res = await api("/admin/audit?" + qs.toString());
        paintAudit(res.entries || []);
      };
    }

    paint();
  }

  async function openCanvasPreview(code) {
    const data = await api(`/admin/rooms/${encodeURIComponent(code)}/canvas`);
    const strokes = data.strokes || [];
    openModal(
      `
      <h3 style="font-family:var(--display);margin:0 0 0.4rem">Leinwand ${esc(code)}</h3>
      <p class="help">${data.strokeCount || 0} Striche · ${data.live ? "live" : "gespeichert"}</p>
      <canvas id="admCanvas" width="360" height="640" style="width:100%;max-width:360px;background:#1a2030;border-radius:12px;border:1px solid var(--line)"></canvas>
      <p id="admCanvasHint" class="help" style="margin-top:0.45rem"></p>
      <div class="actions" style="margin-top:0.75rem">
        <button type="button" class="btn ghost" id="cancelModal">Schließen</button>
      </div>`,
      true
    );
    $("cancelModal").onclick = closeModal;
    const canvas = $("admCanvas");
    const ctx = canvas.getContext("2d");
    const palette = ["#E94E77", "#4EC4FF", "#2EE6A8", "#FFD54F", "#9B7BFF", "#FF8A65", "#F4F1EA", "#80CBC4"];
    const shortSide = Math.min(canvas.width, canvas.height);
    ctx.fillStyle = "#121826";
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    const drawStrokes = () => {
      for (const s of strokes) {
        const pts = s.points || [];
        if (!pts.length) continue;
        const color = palette[(s.colorIndex || 0) % palette.length];
        // width ist Referenz-Pixel (~18 bei Seite 1000), nicht 0–1
        const lw = Math.max(1.5, ((Number(s.width) || 18) / 1000) * shortSide);
        if (s.emoji) {
          const p = pts[0];
          const x = (p.x || 0) * canvas.width;
          const y = (p.y || 0) * canvas.height;
          const size = Math.max(18, shortSide * 0.08);
          ctx.font = `${size}px "Segoe UI Emoji","Apple Color Emoji",sans-serif`;
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(String(s.emoji), x, y);
          continue;
        }
        if (pts.length < 2) continue;
        ctx.strokeStyle = color;
        ctx.lineWidth = lw;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        ctx.beginPath();
        pts.forEach((p, i) => {
          const x = (p.x || 0) * canvas.width;
          const y = (p.y || 0) * canvas.height;
          if (i === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        });
        ctx.stroke();
      }
    };
    drawStrokes();

    const hint = $("admCanvasHint");
    if (!strokes.length && hint) {
      hint.textContent = "Keine Striche geladen — versuche Memory-Snapshot…";
      try {
        const token = localStorage.getItem("luv_adm_token") || "";
        const resp = await fetch(`/luv/v1/rooms/${encodeURIComponent(code)}/memory/image`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (resp.ok) {
          const blob = await resp.blob();
          const url = URL.createObjectURL(blob);
          const img = new Image();
          img.onload = () => {
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            URL.revokeObjectURL(url);
            hint.textContent = "Memory-Snapshot (kein Live-Strich-Log).";
          };
          img.onerror = () => {
            URL.revokeObjectURL(url);
            hint.textContent = "Keine Leinwand-Daten für diese Lobby.";
          };
          img.src = url;
        } else {
          hint.textContent = "Keine Leinwand-Daten für diese Lobby.";
        }
      } catch (_) {
        hint.textContent = "Keine Leinwand-Daten für diese Lobby.";
      }
    }
  }

  async function renderPhrases() {
    const content = contentEl();
    const pool = host().state.phrasePool || "";
    const q = host().state.phraseQ || "";
    if (!host().state.phraseSelected || typeof host().state.phraseSelected !== "object") {
      host().state.phraseSelected = {};
    }
    const selected = host().state.phraseSelected;
    const qs = new URLSearchParams();
    if (pool) qs.set("pool", pool);
    if (q) qs.set("q", q);
    const data = await api("/admin/notify-phrases?" + qs.toString());
    const phrases = data.phrases || [];
    const targets = data.targets || [];
    const targetLabel = Object.fromEntries(targets.map((t) => [t.id, t.label]));
    const visibleIds = phrases.map((p) => p.id);
    const selectedVisible = visibleIds.filter((id) => selected[id]);
    const allVisibleSelected =
      visibleIds.length > 0 && selectedVisible.length === visibleIds.length;

    content.innerHTML = `
      <div class="panel ach-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Sprüche</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:44rem">
              Mood = Push-Benachrichtigungen in der App. Share = OG-/Einladungs-Texte.
              Einzelne, mehrere oder alle auswählen — dann aktivieren oder deaktivieren.
            </p>
          </div>
          <button class="btn teal" id="phrNew">＋ Neuer Spruch</button>
        </div>
        <div class="shop-cats">
          <button type="button" class="shop-cat ${!pool ? "on" : ""}" data-pool="">Alle <span class="shop-cat-n">${data.counts?.all ?? phrases.length}</span></button>
          <button type="button" class="shop-cat ${pool === "mood" ? "on" : ""}" data-pool="mood">Mood / Push <span class="shop-cat-n">${data.counts?.mood ?? 0}</span></button>
          <button type="button" class="shop-cat ${pool === "share" ? "on" : ""}" data-pool="share">Share / OG <span class="shop-cat-n">${data.counts?.share ?? 0}</span></button>
        </div>
        <div class="toolbar shop-search-bar" style="flex-wrap:wrap;gap:0.5rem">
          <input id="phrQ" placeholder="Suchen…" value="${esc(q)}" />
          <button class="btn secondary" id="phrFilter">Suchen</button>
          <button class="btn secondary" id="phrSelectAll">${allVisibleSelected ? "Auswahl aufheben" : "Alle auswählen"}</button>
          <button class="btn ghost" id="phrBulkOn" ${selectedVisible.length ? "" : "disabled"}>Auswahl aktivieren (${selectedVisible.length})</button>
          <button class="btn ghost" id="phrBulkOff" ${selectedVisible.length ? "" : "disabled"}>Auswahl deaktivieren (${selectedVisible.length})</button>
        </div>
      </div>
      <div class="ach-grid">
        ${
          phrases.length
            ? phrases
                .map(
                  (p) => `<article class="ach-card ${p.enabled ? "" : "is-off"} ${selected[p.id] ? "is-focus" : ""}">
            <header>
              <label style="display:flex;gap:0.55rem;align-items:flex-start;cursor:pointer;flex:1">
                <input type="checkbox" class="phr-check" data-phr-id="${esc(p.id)}" ${selected[p.id] ? "checked" : ""} style="margin-top:0.35rem;width:1.1rem;height:1.1rem" />
                <div>
                  <strong style="font-size:0.95rem;line-height:1.35">${esc(p.text)}</strong>
                  <div class="muted" style="margin-top:0.35rem;font-size:0.8rem">${esc(p.subtitle || "—")}</div>
                </div>
              </label>
              <span class="badge ${p.pool === "mood" ? "src-achievement" : "src-code"}">${esc(p.pool)}</span>
            </header>
            <div class="ach-meta">
              <span>Ziel: <strong>${esc(targetLabel[p.target] || p.target)}</strong></span>
              ${p.enabled ? '<span class="badge src-shop">Aktiv</span>' : '<span class="badge off">Aus</span>'}
            </div>
            <div class="actions">
              <button type="button" class="btn secondary" data-edit-phr="${esc(p.id)}">Bearbeiten</button>
              <button type="button" class="btn ghost" data-tog-phr="${esc(p.id)}|${p.enabled ? "0" : "1"}">${p.enabled ? "Deaktivieren" : "Aktivieren"}</button>
              <button type="button" class="btn btn-del" data-del-phr="${esc(p.id)}">Löschen</button>
            </div>
          </article>`
                )
                .join("")
            : `<div class="panel"><p class="help">Keine Sprüche.</p></div>`
        }
      </div>`;

    content.querySelectorAll("[data-pool]").forEach((b) => {
      b.onclick = () => {
        host().state.phrasePool = b.getAttribute("data-pool") || "";
        renderPhrases();
      };
    });
    $("phrFilter").onclick = () => {
      host().state.phraseQ = $("phrQ").value.trim();
      renderPhrases();
    };
    $("phrNew").onclick = () => openPhraseEditor(null, targets);
    $("phrSelectAll").onclick = () => {
      if (allVisibleSelected) {
        visibleIds.forEach((id) => {
          delete selected[id];
        });
      } else {
        visibleIds.forEach((id) => {
          selected[id] = true;
        });
      }
      renderPhrases();
    };
    const bulk = async (enabled) => {
      const ids = Object.keys(selected).filter((id) => selected[id]);
      if (!ids.length) return;
      await api("/admin/notify-phrases/bulk", {
        method: "POST",
        body: JSON.stringify({ ids, enabled }),
      });
      host().state.phraseSelected = {};
      renderPhrases();
    };
    $("phrBulkOn").onclick = () => bulk(true);
    $("phrBulkOff").onclick = () => bulk(false);
    content.querySelectorAll(".phr-check").forEach((cb) => {
      cb.onchange = () => {
        const id = cb.getAttribute("data-phr-id");
        if (cb.checked) selected[id] = true;
        else delete selected[id];
        renderPhrases();
      };
    });
    content.querySelectorAll("[data-edit-phr]").forEach((b) => {
      b.onclick = () => {
        const id = b.getAttribute("data-edit-phr");
        const p = phrases.find((x) => x.id === id);
        if (p) openPhraseEditor(p, targets);
      };
    });
    content.querySelectorAll("[data-tog-phr]").forEach((b) => {
      b.onclick = async () => {
        const [id, mode] = b.getAttribute("data-tog-phr").split("|");
        const p = phrases.find((x) => x.id === id);
        if (!p) return;
        await api(`/admin/notify-phrases/${encodeURIComponent(id)}`, {
          method: "PUT",
          body: JSON.stringify({ ...p, enabled: mode === "1" }),
        });
        renderPhrases();
      };
    });
    content.querySelectorAll("[data-del-phr]").forEach((b) => {
      b.onclick = async () => {
        const id = b.getAttribute("data-del-phr");
        if (!confirm("Spruch wirklich löschen?")) return;
        await api(`/admin/notify-phrases/${encodeURIComponent(id)}`, { method: "DELETE" });
        delete selected[id];
        renderPhrases();
      };
    });
  }

  async function renderDailyTasks() {
    const content = contentEl();
    const data = await api("/admin/daily-tasks");
    const cfg = data.config || {};
    const plan = cfg.plan || [];
    const templates = cfg.templates || [];
    const inPlan = new Set(plan.map((p) => p.templateId));
    const available = templates.filter((t) => t.enabled && !inPlan.has(t.id));

    content.innerHTML = `
      <div class="panel ach-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Tagesaufgaben-Planer</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:48rem">
              Plane, welche Aufgaben Spieler erledigen müssen. Mit <strong>＋</strong> eine Kachel hinzufügen.
              Wenn alle Aufgaben des Tages fertig sind, gibt es die eingestellte Coin-Belohnung
              (Standard: <strong>3</strong>). Neue Pläne gelten ab dem <em>nächsten</em> Berlin-Tag
              (Spieler, die heute schon Aufgaben haben, behalten ihren aktuellen Satz).
            </p>
          </div>
          <button class="btn" id="dailySave">Plan speichern</button>
        </div>
        <div class="grid-2" style="margin-top:1rem;gap:1rem">
          <label class="field">Coin-Belohnung (wenn alles erledigt)
            <input id="dailyReward" type="number" min="${cfg.limits?.rewardMin ?? 0}" max="${cfg.limits?.rewardMax ?? 100}" value="${esc(cfg.rewardCoins ?? 3)}" />
            <span class="muted" style="font-size:0.8rem">Spieler holen die Coins unter Sozial → Erfolge ab.</span>
          </label>
          <label class="field">Modus
            <select id="dailyMode">
              <option value="plan" ${cfg.mode === "plan" ? "selected" : ""}>Fester Plan (gleiche Aufgaben für alle)</option>
              <option value="random" ${cfg.mode !== "plan" ? "selected" : ""}>Zufällig aus aktivem Pool</option>
            </select>
          </label>
        </div>
        <label class="field" style="margin-top:0.75rem;max-width:16rem">Anzahl bei Zufall
          <input id="dailyCount" type="number" min="1" max="${cfg.limits?.planMax ?? 12}" value="${esc(cfg.tasksPerDay ?? 4)}" />
        </label>
      </div>

      <div class="panel" style="margin-top:1rem">
        <h3 style="margin:0 0 0.35rem;font-family:var(--display)">Heutiger Plan</h3>
        <p class="help" style="margin:0 0 0.85rem">
          Jede Kachel = eine Aufgabe. Tippe ✕ zum Entfernen. Plus-Kachel fügt die nächste hinzu.
        </p>
        <div class="daily-plan-grid" id="dailyPlanGrid">
          ${plan
            .map(
              (p, i) => `<article class="daily-tile" data-plan-idx="${i}">
            <strong>${esc(p.title)}</strong>
            <p class="muted" style="font-size:0.82rem;margin:0.35rem 0 0;line-height:1.35">${esc(p.hint || "Keine Anleitung")}</p>
            <label class="field" style="margin-top:0.55rem">Zielwert
              <input type="number" class="daily-target" min="1" max="999" value="${esc(p.target)}" data-tid="${esc(p.templateId)}" />
            </label>
            <button type="button" class="btn btn-del" data-rm-plan="${i}" style="margin-top:0.5rem">✕ Entfernen</button>
          </article>`
            )
            .join("")}
          <button type="button" class="daily-tile daily-tile-add" id="dailyAddTile" ${available.length ? "" : "disabled"}>
            <span style="font-size:2rem;line-height:1">＋</span>
            <span>Aufgabe hinzufügen</span>
          </button>
        </div>
      </div>

      <div class="panel" style="margin-top:1rem">
        <h3 style="margin:0 0 0.35rem;font-family:var(--display)">Aufgaben-Vorlagen</h3>
        <p class="help" style="margin:0 0 0.85rem">
          Deaktivierte Vorlagen erscheinen weder im Zufalls-Pool noch im Plus-Menü.
          Die Anleitung sieht der Spieler in der App unter der Aufgabe.
        </p>
        <div class="ach-grid">
          ${templates
            .map(
              (t) => `<article class="ach-card ${t.enabled ? "" : "is-off"}">
            <header>
              <div>
                <strong>${esc(t.title)}</strong>
                <div class="muted mono" style="font-size:0.75rem">${esc(t.id)} · ${esc(t.metric)} ≥ ${t.target}</div>
              </div>
              ${t.enabled ? '<span class="badge src-shop">Aktiv</span>' : '<span class="badge off">Aus</span>'}
            </header>
            <p class="ach-desc">${esc(t.hint || "—")}</p>
            <div class="actions">
              <button type="button" class="btn secondary" data-edit-tpl="${esc(t.id)}">Anleitung / Ziel</button>
              <button type="button" class="btn ghost" data-tog-tpl="${esc(t.id)}|${t.enabled ? "0" : "1"}">${t.enabled ? "Deaktivieren" : "Aktivieren"}</button>
              ${
                t.enabled && !inPlan.has(t.id)
                  ? `<button type="button" class="btn teal" data-add-tpl="${esc(t.id)}">＋ Zum Plan</button>`
                  : ""
              }
            </div>
          </article>`
            )
            .join("")}
        </div>
      </div>`;

    const collectPlan = () => {
      const tiles = [...content.querySelectorAll(".daily-tile[data-plan-idx]")];
      return tiles.map((tile) => {
        const input = tile.querySelector(".daily-target");
        return {
          templateId: input.getAttribute("data-tid"),
          target: Math.max(1, Number(input.value) || 1),
        };
      });
    };

    const save = async (extra = {}) => {
      const body = {
        rewardCoins: Number($("dailyReward").value),
        tasksPerDay: Number($("dailyCount").value),
        mode: $("dailyMode").value,
        plan: collectPlan(),
        ...extra,
      };
      await api("/admin/daily-tasks", { method: "PUT", body: JSON.stringify(body) });
      renderDailyTasks();
    };

    $("dailySave").onclick = () => save();
    $("dailyMode").onchange = () => {
      /* nur lokal bis Speichern */
    };

    content.querySelectorAll("[data-rm-plan]").forEach((b) => {
      b.onclick = async () => {
        const idx = Number(b.getAttribute("data-rm-plan"));
        const next = collectPlan().filter((_, i) => i !== idx);
        await save({ plan: next, mode: "plan" });
      };
    });

    const addTemplate = async (tid) => {
      const next = collectPlan();
      if (next.some((p) => p.templateId === tid)) return;
      const tpl = templates.find((t) => t.id === tid);
      next.push({ templateId: tid, target: tpl?.target || 1 });
      await save({ plan: next, mode: "plan" });
    };

    $("dailyAddTile").onclick = () => {
      if (!available.length) {
        alert("Keine weiteren aktiven Vorlagen. Aktiviere welche unten oder entferne eine aus dem Plan.");
        return;
      }
      openModal(
        `
        <h3 style="font-family:var(--display);margin:0 0 0.5rem">Aufgabe hinzufügen</h3>
        <p class="help">Wähle eine Vorlage — der Spieler sieht Titel und Anleitung.</p>
        <div class="ach-mini-list" id="dailyPickList">
          ${available
            .map(
              (t) => `<button type="button" class="ach-mini" data-pick="${esc(t.id)}" style="text-align:left;width:100%;cursor:pointer;border:1px solid var(--line)">
              <div>
                <strong>${esc(t.title)}</strong>
                <div class="muted" style="font-size:0.8rem;margin-top:0.2rem">${esc(t.hint)}</div>
              </div>
            </button>`
            )
            .join("")}
        </div>
        <div class="actions" style="margin-top:1rem">
          <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
        </div>`,
        true
      );
      $("cancelModal").onclick = closeModal;
      const pickList = $("dailyPickList");
      if (pickList) {
        pickList.querySelectorAll("[data-pick]").forEach((b) => {
          b.onclick = async () => {
            const tid = b.getAttribute("data-pick");
            closeModal();
            await addTemplate(tid);
          };
        });
      }
    };

    content.querySelectorAll("[data-add-tpl]").forEach((b) => {
      b.onclick = () => addTemplate(b.getAttribute("data-add-tpl"));
    });
    content.querySelectorAll("[data-tog-tpl]").forEach((b) => {
      b.onclick = async () => {
        const [id, mode] = b.getAttribute("data-tog-tpl").split("|");
        const path =
          mode === "0"
            ? `/admin/daily-tasks/templates/${encodeURIComponent(id)}/disable`
            : `/admin/daily-tasks/templates/${encodeURIComponent(id)}/enable`;
        await api(path, { method: "POST", body: "{}" });
        renderDailyTasks();
      };
    });
    content.querySelectorAll("[data-edit-tpl]").forEach((b) => {
      b.onclick = () => {
        const id = b.getAttribute("data-edit-tpl");
        const t = templates.find((x) => x.id === id);
        if (!t) return;
        openModal(
          `
          <h3 style="font-family:var(--display);margin:0 0 0.4rem">${esc(t.title)}</h3>
          <form id="tplForm">
            <label class="field">Titel
              <input name="title" maxlength="60" value="${esc(t.title)}" required />
            </label>
            <label class="field">Was muss der Spieler tun? (Anleitung)
              <textarea name="hint" maxlength="200" rows="3" required>${esc(t.hint || "")}</textarea>
            </label>
            <label class="field">Zielwert (z. B. 10 Striche)
              <input name="target" type="number" min="1" max="999" value="${esc(t.target)}" />
            </label>
            <div class="actions" style="margin-top:1rem">
              <button type="submit" class="btn">Speichern</button>
              <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
            </div>
          </form>`,
          true
        );
        $("cancelModal").onclick = closeModal;
        $("tplForm").onsubmit = async (e) => {
          e.preventDefault();
          const fd = new FormData(e.target);
          await save({
            templates: [
              {
                id: t.id,
                title: String(fd.get("title") || "").trim(),
                hint: String(fd.get("hint") || "").trim(),
                target: Number(fd.get("target") || 1),
              },
            ],
          });
          closeModal();
        };
      };
    });
  }

  function openPhraseEditor(existing, targets) {
    const isEdit = Boolean(existing);
    openModal(
      `
      <h3 style="font-family:var(--display);margin:0 0 0.4rem">${isEdit ? "Spruch bearbeiten" : "Neuer Spruch"}</h3>
      <form id="phrForm">
        <label class="field">Text
          <textarea name="text" maxlength="200" rows="3" required>${esc(existing?.text || "")}</textarea>
        </label>
        <label class="field">Untertitel (Push)
          <input name="subtitle" maxlength="120" value="${esc(existing?.subtitle || "Tippen — und kurz vorbeischauen")}" />
        </label>
        <div class="grid-2">
          <label class="field">Pool
            <select name="pool">
              <option value="mood" ${existing?.pool !== "share" ? "selected" : ""}>Mood / Push</option>
              <option value="share" ${existing?.pool === "share" ? "selected" : ""}>Share / OG</option>
            </select>
          </label>
          <label class="field">Tap-Ziel
            <select name="target">
              ${(targets || [])
                .map(
                  (t) =>
                    `<option value="${esc(t.id)}" ${
                      (existing?.target || "home") === t.id ? "selected" : ""
                    }>${esc(t.label)}</option>`
                )
                .join("")}
            </select>
          </label>
        </div>
        <label class="field" style="margin-top:0.75rem;flex-direction:row;align-items:center;gap:0.5rem">
          <input type="checkbox" name="enabled" ${existing?.enabled !== false ? "checked" : ""} /> Aktiv
        </label>
        <div class="actions" style="margin-top:1rem">
          <button type="submit" class="btn">Speichern</button>
          <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
        </div>
      </form>`,
      true
    );
    $("cancelModal").onclick = closeModal;
    $("phrForm").onsubmit = async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      const body = {
        text: String(fd.get("text") || "").trim(),
        subtitle: String(fd.get("subtitle") || "").trim(),
        pool: String(fd.get("pool") || "mood"),
        target: String(fd.get("target") || "home"),
        enabled: e.target.querySelector('[name="enabled"]').checked,
      };
      try {
        if (isEdit) {
          await api(`/admin/notify-phrases/${encodeURIComponent(existing.id)}`, {
            method: "PUT",
            body: JSON.stringify(body),
          });
        } else {
          await api("/admin/notify-phrases", { method: "POST", body: JSON.stringify(body) });
        }
        closeModal();
        renderPhrases();
      } catch (err) {
        alert(err?.message || "Speichern fehlgeschlagen");
      }
    };
  }

  const MONTHS_DE = [
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember",
  ];

  const DECOR_PRESETS = [
    { id: "none", label: "Keine" },
    { id: "snow", label: "Schnee" },
    { id: "hearts", label: "Herzen" },
    { id: "leaves", label: "Blätter" },
    { id: "sparkle", label: "Funken" },
  ];

  async function renderEvents() {
    const content = contentEl();
    const year = new Date().getUTCFullYear();
    const data = await api(`/admin/events?year=${year}`);
    const events = data.events || [];
    const overview = data.year || { year, months: [] };
    let selectedId = events.find((e) => e.active)?.id || events[0]?.id || "";

    function decorPreviewHtml(d) {
      const particles = d?.particles || "none";
      const accent = d?.accentHex || "#E94E77";
      const banner = d?.bannerText || "";
      return `<div class="ev-decor-preview" style="--ev-accent:${esc(accent)}" data-particles="${esc(particles)}">
        <div class="ev-decor-particles" aria-hidden="true"></div>
        ${banner ? `<div class="ev-decor-banner">${esc(banner)}</div>` : `<div class="ev-decor-banner is-empty">Kein Banner</div>`}
        <div class="ev-decor-label">${esc(DECOR_PRESETS.find((p) => p.id === particles)?.label || particles)}</div>
      </div>`;
    }

    function editorHtml(ev) {
      if (!ev) return `<p class="help">Event wählen.</p>`;
      const d = ev.decor || {};
      return `<form id="evEditForm" class="ev-edit-form" data-id="${esc(ev.id)}">
        <div class="ev-edit-head">
          <span class="ev-edit-emoji">${esc(ev.emoji || "🎉")}</span>
          <div>
            <h4 style="margin:0">${esc(ev.title)}</h4>
            <div class="muted mono" style="font-size:0.8rem">${esc(ev.id)}</div>
          </div>
          <label class="field" style="flex-direction:row;align-items:center;gap:0.4rem;margin:0">
            <input type="checkbox" name="enabled" ${ev.enabled !== false ? "checked" : ""} /> Aktiv
          </label>
        </div>
        <label class="field">Titel
          <input name="title" maxlength="60" value="${esc(ev.title || "")}" />
        </label>
        <label class="field">Emoji
          <input name="emoji" maxlength="8" value="${esc(ev.emoji || "")}" />
        </label>
        <label class="field">Beschreibung
          <textarea name="description" rows="2" maxlength="240">${esc(ev.description || "")}</textarea>
        </label>
        <label class="field">Hinweis
          <input name="hint" maxlength="200" value="${esc(ev.hint || "")}" />
        </label>
        <div class="grid-2">
          <label class="field">Coins / Collect
            <input name="rewardCoinsPerCollect" type="number" min="0" max="50" value="${esc(ev.rewardCoinsPerCollect ?? 2)}" />
          </label>
          <label class="field">Sammel-Ziel
            <input name="collectTarget" type="number" min="1" max="31" value="${esc(ev.collectTarget ?? 3)}" />
          </label>
        </div>
        <div class="grid-2">
          <label class="field">Meilenstein-Bonus
            <input name="milestoneBonusCoins" type="number" min="0" max="50" value="${esc(ev.milestoneBonusCoins ?? 0)}" />
          </label>
          <label class="field">Dauer (Tage)
            <input name="durationDays" type="number" min="1" max="31" value="${esc(ev.durationDays ?? 1)}" />
          </label>
        </div>
        <h4 style="margin:0.85rem 0 0.35rem">App-Schmuck</h4>
        <div class="grid-2">
          <label class="field">Partikel
            <select name="particles">
              ${DECOR_PRESETS.map(
                (p) =>
                  `<option value="${p.id}" ${d.particles === p.id ? "selected" : ""}>${esc(p.label)}</option>`
              ).join("")}
            </select>
          </label>
          <label class="field">Akzentfarbe
            <input name="accentHex" type="color" value="${esc((d.accentHex || "#E94E77").slice(0, 7))}" />
          </label>
        </div>
        <label class="field">Banner-Text
          <input name="bannerText" maxlength="48" value="${esc(d.bannerText || "")}" />
        </label>
        <label class="field">Intensität (0–1)
          <input name="intensity" type="number" min="0" max="1" step="0.05" value="${esc(d.intensity ?? 0.55)}" />
        </label>
        <label class="field">Ornamente
          <select name="ornaments">
            ${["none", "wreath", "hearts", "spark"]
              .map(
                (o) =>
                  `<option value="${o}" ${(d.ornaments || "none") === o ? "selected" : ""}>${esc(o)}</option>`
              )
              .join("")}
          </select>
        </label>
        <div id="evDecorLive">${decorPreviewHtml(d)}</div>
        <div class="actions" style="margin-top:0.75rem">
          <button type="submit" class="btn">Speichern</button>
          ${
            ev.custom
              ? `<button type="button" class="btn danger" id="evDeleteBtn">Event löschen</button>`
              : `<button type="button" class="btn secondary" id="evDisableBtn">Deaktivieren</button>`
          }
        </div>
        ${
          (ev.quests && ev.quests.length) || ev.lobby?.enabled || ev.contest?.enabled
            ? `<div class="ev-modules help" style="margin-top:0.75rem">
                ${ev.quests?.length ? `<div>Quests: ${ev.quests.length}</div>` : ""}
                ${ev.lobby?.enabled ? `<div>Event-Lobby: an (${esc(ev.lobby.access || "friends")})</div>` : ""}
                ${ev.contest?.enabled ? `<div>Wettbewerb: an</div>` : ""}
              </div>`
            : ""
        }
        ${
          ev.next
            ? `<p class="help" style="margin-top:0.6rem">Fenster: ${esc(ev.next.start || "—")}${
                ev.next.end && ev.next.end !== ev.next.start ? ` – ${esc(ev.next.end)}` : ""
              }${ev.active ? " · gerade aktiv" : ""}${
                ev.schedule?.mode === "absolute" ? " · absolut" : ""
              }</p>`
            : ""
        }
      </form>`;
    }

    function paint() {
      const sel = events.find((e) => e.id === selectedId) || events[0];
      selectedId = sel?.id || "";
      const yearHtml = (overview.months || [])
        .map((mo) => {
          const pills = (mo.events || [])
            .map(
              (ev) =>
                `<button type="button" class="ev-pill ${ev.id === selectedId ? "on" : ""}" data-ev-id="${esc(ev.id)}" title="${esc(ev.title)}">
                  <span>${esc(ev.emoji || "·")}</span>
                  <span>${esc(ev.label || ev.title)}</span>
                </button>`
            )
            .join("");
          return `<div class="ev-month">
            <div class="ev-month-h">${esc(MONTHS_DE[(mo.month || 1) - 1])}</div>
            <div class="ev-month-pills">${pills || `<span class="muted" style="font-size:0.8rem">—</span>`}</div>
          </div>`;
        })
        .join("");

      const listHtml = events
        .map(
          (e) => `<button type="button" class="ev-list-item ${e.id === selectedId ? "on" : ""} ${e.active ? "is-active" : ""} ${e.enabled === false ? "is-off" : ""}" data-ev-id="${esc(e.id)}">
            <span class="ev-list-emoji">${esc(e.emoji || "🎉")}</span>
            <span class="ev-list-meta">
              <strong>${esc(e.title)}</strong>
              <span class="muted">${e.active ? "jetzt aktiv" : e.next?.start || "—"}</span>
            </span>
          </button>`
        )
        .join("");

      content.innerHTML = `
        <div class="panel shop-hero">
          <div class="shop-top">
            <div>
              <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">📅 Eventkalender</h3>
              <p class="help" style="margin:0.4rem 0 0;max-width:52rem">
                Jahres-Events mit Sammeln, Quests, Schmuck. Nutzer: Sozial → Events.
                <strong>Löschen</strong> entfernt Custom-Events inkl. Schmuck-Quelle, Fortschritt und Event-Lobbys.
                Builtin-Events werden nur deaktiviert.
              </p>
            </div>
            <div class="actions" style="gap:0.45rem;flex-wrap:wrap">
              <button type="button" class="btn secondary" id="evTestBtn">2-Tage-Testevent</button>
              <button type="button" class="btn" id="evCreateBtn">+ Neues Event</button>
            </div>
          </div>
        </div>
        <div class="ev-layout">
          <div class="panel">
            <h4 style="margin:0 0 0.55rem">Jahr ${esc(overview.year || year)}</h4>
            <div class="ev-year">${yearHtml}</div>
            <h4 style="margin:1rem 0 0.45rem">Alle Events</h4>
            <div class="ev-list">${listHtml}</div>
          </div>
          <div class="panel" id="evEditorPanel">${editorHtml(sel)}</div>
        </div>`;

      const createBtn = $("evCreateBtn");
      if (createBtn) {
        createBtn.onclick = () => openCreateEventWizard();
      }

      function openCreateEventWizard() {
        const now = new Date();
        const state = {
          viewY: now.getFullYear(),
          viewM: now.getMonth(), // 0-based
          start: null, // {y,m,d} m 1-based
          end: null,
          pick: "start", // start | end
        };

        const QUEST_PRESETS = [
          {
            metric: "strokes",
            title: "10 Striche malen",
            hint: "In einer Lobby zeichnen",
            target: 10,
            coins: 3,
            on: true,
          },
          {
            metric: "draw_sessions",
            title: "Eine Mal-Session starten",
            hint: "Einmal mit Zeichnen beginnen",
            target: 1,
            coins: 2,
            on: true,
          },
          {
            metric: "event_lobby_opens",
            title: "Event-Lobby öffnen",
            hint: "Eigene Event-Lobby starten",
            target: 1,
            coins: 2,
            on: false,
          },
          {
            metric: "event_lobby_strokes",
            title: "20 Striche in der Event-Lobby",
            hint: "Zum Begriff zeichnen",
            target: 20,
            coins: 4,
            on: false,
          },
          {
            metric: "krauls",
            title: "Begleiter kraulen",
            hint: "3× kraulen",
            target: 3,
            coins: 2,
            on: false,
          },
          {
            metric: "social_opens",
            title: "Sozial öffnen",
            hint: "Sozial-Tab öffnen",
            target: 1,
            coins: 1,
            on: false,
          },
          {
            metric: "gallery_opens",
            title: "Galerie öffnen",
            hint: "Galerie besuchen",
            target: 1,
            coins: 1,
            on: false,
          },
          {
            metric: "reactions_sent",
            title: "Reaktion senden",
            hint: "Eine Reaktion schicken",
            target: 1,
            coins: 1,
            on: false,
          },
          {
            metric: "moments_saved",
            title: "Moment speichern",
            hint: "Einen Moment sichern",
            target: 1,
            coins: 2,
            on: false,
          },
          {
            metric: "lobby_opens",
            title: "Lobby öffnen",
            hint: "Beliebige Lobby öffnen",
            target: 1,
            coins: 1,
            on: false,
          },
        ];
        const DEFAULT_PROMPTS = ["Herz", "Stern", "Sonne", "Katze", "Blume", "Mond", "Haus", "Baum"];

        const MONTHS_SHORT = [
          "Jan",
          "Feb",
          "Mär",
          "Apr",
          "Mai",
          "Jun",
          "Jul",
          "Aug",
          "Sep",
          "Okt",
          "Nov",
          "Dez",
        ];
        const WD = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"];

        function ymdKey(p) {
          return p ? `${p.y}-${String(p.m).padStart(2, "0")}-${String(p.d).padStart(2, "0")}` : "";
        }
        function fmtDe(p) {
          if (!p) return "—";
          return `${String(p.d).padStart(2, "0")}.${String(p.m).padStart(2, "0")}.${p.y}`;
        }
        function dayMs(p) {
          return Date.UTC(p.y, p.m - 1, p.d);
        }
        function daysInclusive(a, b) {
          return Math.floor((dayMs(b) - dayMs(a)) / 86400000) + 1;
        }
        function berlinDayStartMs(p) {
          let utc = Date.UTC(p.y, p.m - 1, p.d, 0, 0, 0);
          for (let i = 0; i < 6; i++) {
            const parts = Object.fromEntries(
              new Intl.DateTimeFormat("en-US", {
                timeZone: "Europe/Berlin",
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                hour: "2-digit",
                hourCycle: "h23",
              })
                .formatToParts(new Date(utc))
                .filter((x) => x.type !== "literal")
                .map((x) => [x.type, x.value])
            );
            const by = Number(parts.year);
            const bm = Number(parts.month);
            const bd = Number(parts.day);
            const bh = Number(parts.hour);
            const desired = ymdKey(p);
            const got = `${by}-${String(bm).padStart(2, "0")}-${String(bd).padStart(2, "0")}`;
            if (got === desired && bh === 0) return utc;
            if (got !== desired) {
              utc += Math.round((Date.UTC(p.y, p.m - 1, p.d) - Date.UTC(by, bm - 1, bd)) / 86400000) * 86400000;
            } else {
              utc -= bh * 3600000;
            }
          }
          return utc;
        }
        function berlinDayIso(p, endOfDay) {
          const start = berlinDayStartMs(p);
          if (endOfDay) return new Date(start + 86400000 - 1).toISOString();
          return new Date(start).toISOString();
        }

        function syncModes() {
          const sBtn = $("evCalModeStart");
          const eBtn = $("evCalModeEnd");
          if (sBtn) sBtn.classList.toggle("on", state.pick === "start");
          if (eBtn) eBtn.classList.toggle("on", state.pick === "end");
        }

        function paintCal() {
          const grid = document.getElementById("evCalGrid");
          const label = document.getElementById("evCalLabel");
          const rangeEl = document.getElementById("evCalRange");
          const hint = document.getElementById("evCalHint");
          const submit = document.getElementById("evCreateSubmit");
          if (!grid || !label) return;

          label.textContent = `${MONTHS_SHORT[state.viewM]} ${state.viewY}`;
          const first = new Date(state.viewY, state.viewM, 1);
          const startPad = (first.getDay() + 6) % 7;
          const daysInMonth = new Date(state.viewY, state.viewM + 1, 0).getDate();
          const cells = [];
          for (let i = 0; i < startPad; i++) {
            cells.push(`<span class="ev-cal-cell is-empty"></span>`);
          }
          const a = state.start;
          const b = state.end || state.start;
          const lo = a && b ? (dayMs(a) <= dayMs(b) ? a : b) : null;
          const hi = a && b ? (dayMs(a) <= dayMs(b) ? b : a) : null;

          for (let d = 1; d <= daysInMonth; d++) {
            const p = { y: state.viewY, m: state.viewM + 1, d };
            const key = ymdKey(p);
            const ms = dayMs(p);
            let cls = "ev-cal-cell";
            if (lo && hi && ms >= dayMs(lo) && ms <= dayMs(hi)) cls += " in-range";
            if (state.start && key === ymdKey(state.start)) cls += " is-start";
            if (state.end && key === ymdKey(state.end)) cls += " is-end";
            const today = new Date();
            if (
              p.y === today.getFullYear() &&
              p.m === today.getMonth() + 1 &&
              p.d === today.getDate()
            ) {
              cls += " is-today";
            }
            cells.push(
              `<button type="button" class="${cls}" data-y="${p.y}" data-m="${p.m}" data-d="${p.d}">${d}</button>`
            );
          }
          grid.innerHTML = WD.map((w) => `<span class="ev-cal-wd">${w}</span>`).join("") + cells.join("");

          if (rangeEl) {
            if (state.start && state.end) {
              const from = dayMs(state.start) <= dayMs(state.end) ? state.start : state.end;
              const to = dayMs(state.start) <= dayMs(state.end) ? state.end : state.start;
              const days = daysInclusive(from, to);
              rangeEl.innerHTML = `<strong>${fmtDe(from)}</strong> → <strong>${fmtDe(to)}</strong> <span class="muted">(${days} Tag${days === 1 ? "" : "e"})</span>`;
            } else if (state.start) {
              rangeEl.innerHTML = `<strong>${fmtDe(state.start)}</strong> → <span class="muted">Letzten Tag tippen</span>`;
            } else {
              rangeEl.innerHTML = `<span class="muted">Noch kein Zeitraum gewählt</span>`;
            }
          }
          if (hint) {
            hint.textContent =
              state.pick === "start"
                ? "Tippe den ersten Tag des Events."
                : "Tippe den letzten Tag des Events.";
          }
          if (submit) submit.disabled = !(state.start && state.end);
          syncModes();

          grid.querySelectorAll("button.ev-cal-cell").forEach((btn) => {
            btn.onclick = () => {
              const p = {
                y: Number(btn.getAttribute("data-y")),
                m: Number(btn.getAttribute("data-m")),
                d: Number(btn.getAttribute("data-d")),
              };
              if (state.pick === "start" || !state.start) {
                state.start = p;
                state.end = null;
                state.pick = "end";
              } else {
                if (dayMs(p) < dayMs(state.start)) {
                  state.end = state.start;
                  state.start = p;
                } else {
                  state.end = p;
                }
                state.pick = "start";
              }
              paintCal();
            };
          });
        }

        openModal(
          `
          <h3 style="font-family:var(--display);margin:0 0 0.35rem">Neues Event</h3>
          <p class="help" style="margin:0 0 0.85rem">Titel, Zeitraum, Quests und optional Event-Lobby mit Wortliste (pro Nutzer zufällig).</p>
          <form id="evCreateForm" class="ev-create-form">
            <div class="grid-2">
              <label class="field">Titel
                <input name="title" maxlength="60" value="Mein Event" required autocomplete="off" />
              </label>
              <label class="field">Emoji
                <input name="emoji" maxlength="8" value="🎉" autocomplete="off" />
              </label>
            </div>
            <div class="ev-cal">
              <div class="ev-cal-toolbar">
                <button type="button" class="btn ghost" id="evCalPrev" aria-label="Vorheriger Monat">‹</button>
                <div class="ev-cal-month" id="evCalLabel"></div>
                <button type="button" class="btn ghost" id="evCalNext" aria-label="Nächster Monat">›</button>
              </div>
              <div class="ev-cal-pick">
                <button type="button" class="ev-cal-mode on" id="evCalModeStart" data-mode="start">Erster Tag</button>
                <button type="button" class="ev-cal-mode" id="evCalModeEnd" data-mode="end">Letzter Tag</button>
              </div>
              <p class="help" id="evCalHint" style="margin:0.35rem 0 0.5rem"></p>
              <div class="ev-cal-grid" id="evCalGrid"></div>
              <div class="ev-cal-range" id="evCalRange"></div>
            </div>

            <h4 style="margin:1rem 0 0.35rem">Quests</h4>
            <p class="help" style="margin:0 0 0.5rem">Welche Aufgaben die Nutzer während des Events erfüllen sollen.</p>
            <div class="ev-quest-pick" id="evQuestPick">
              ${QUEST_PRESETS.map(
                (q) => `<label class="ev-quest-opt">
                  <input type="checkbox" name="quest" value="${esc(q.metric)}" data-target="${q.target}" data-coins="${q.coins}" data-title="${esc(q.title)}" ${q.on ? "checked" : ""} />
                  <span>
                    <strong>${esc(q.title)}</strong>
                    <span class="muted">${esc(q.hint)}</span>
                  </span>
                </label>`
              ).join("")}
            </div>

            <h4 style="margin:1rem 0 0.35rem">Event-Lobby</h4>
            <label class="ev-quest-opt" style="margin-bottom:0.55rem">
              <input type="checkbox" id="evLobbyEnabled" checked />
              <span>
                <strong>Event-Lobby aktiv</strong>
                <span class="muted">Zeichnen nach Begriff — jeder Nutzer bekommt ein Wort zufällig aus der Liste.</span>
              </span>
            </label>
            <div id="evLobbyFields">
              <label class="field">Wörter (eines pro Zeile, max. 40)
                <textarea id="evLobbyPrompts" rows="5" placeholder="Herz&#10;Stern&#10;Sonne">${esc(
                  DEFAULT_PROMPTS.join("\n")
                )}</textarea>
              </label>
              <p class="help" style="margin:0.25rem 0 0">Beim Öffnen der eigenen Event-Lobby wird <strong>zufällig eines</strong> gewählt — nicht dasselbe Wort für alle.</p>
            </div>

            <div class="actions" style="margin-top:1rem">
              <button type="submit" class="btn" id="evCreateSubmit" disabled>Event anlegen</button>
              <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
            </div>
            <p class="help" id="evCreateErr" hidden style="color:#f88;margin:0.5rem 0 0"></p>
          </form>`,
          true
        );

        $("cancelModal").onclick = closeModal;
        $("evCalPrev").onclick = () => {
          state.viewM -= 1;
          if (state.viewM < 0) {
            state.viewM = 11;
            state.viewY -= 1;
          }
          paintCal();
        };
        $("evCalNext").onclick = () => {
          state.viewM += 1;
          if (state.viewM > 11) {
            state.viewM = 0;
            state.viewY += 1;
          }
          paintCal();
        };
        $("evCalModeStart").onclick = () => {
          state.pick = "start";
          paintCal();
        };
        $("evCalModeEnd").onclick = () => {
          state.pick = state.start ? "end" : "start";
          paintCal();
        };

        const lobbyToggle = $("evLobbyEnabled");
        const lobbyFields = $("evLobbyFields");
        const syncLobbyFields = () => {
          if (lobbyFields) lobbyFields.hidden = !(lobbyToggle && lobbyToggle.checked);
        };
        if (lobbyToggle) lobbyToggle.onchange = syncLobbyFields;
        syncLobbyFields();

        paintCal();

        $("evCreateForm").onsubmit = async (e) => {
          e.preventDefault();
          const errEl = $("evCreateErr");
          if (errEl) {
            errEl.hidden = true;
            errEl.textContent = "";
          }
          if (!state.start || !state.end) return;
          const lo = dayMs(state.start) <= dayMs(state.end) ? state.start : state.end;
          const hi = dayMs(state.start) <= dayMs(state.end) ? state.end : state.start;
          const span = daysInclusive(lo, hi);
          if (span > 31) {
            if (errEl) {
              errEl.hidden = false;
              errEl.textContent = "Maximal 31 Tage.";
            }
            return;
          }
          const durationDays = Math.max(1, span);
          const form = e.target;
          const title = String(form.title?.value || "").trim() || "Neues Event";
          const emoji = String(form.emoji?.value || "").trim() || "🎉";

          const quests = Array.from(form.querySelectorAll('input[name="quest"]:checked')).map(
            (inp, i) => ({
              id: `q_${inp.value}_${i + 1}`,
              title: inp.getAttribute("data-title") || inp.value,
              hint: "",
              metric: inp.value,
              target: Number(inp.getAttribute("data-target") || 1),
              rewardCoins: Number(inp.getAttribute("data-coins") || 1),
            })
          );

          const lobbyOn = Boolean(lobbyToggle && lobbyToggle.checked);
          let lobby = { enabled: false };
          if (lobbyOn) {
            const rawPrompts = String($("evLobbyPrompts")?.value || "")
              .split(/[\n,;]+/)
              .map((s) => s.trim())
              .filter(Boolean)
              .slice(0, 40);
            if (!rawPrompts.length) {
              if (errEl) {
                errEl.hidden = false;
                errEl.textContent = "Mindestens ein Wort für die Event-Lobby eintragen.";
              }
              return;
            }
            lobby = {
              enabled: true,
              access: "friends",
              createMode: "manual",
              palette: "event",
              invitesAllowed: false,
              drawMode: "promptList",
              prompts: rawPrompts,
              minStrokesToQualify: 5,
              sessionSeconds: 180,
            };
          }

          const submit = $("evCreateSubmit");
          if (submit) submit.disabled = true;
          try {
            const res = await api("/admin/events", {
              method: "POST",
              body: JSON.stringify({
                title,
                emoji,
                month: lo.m,
                day: lo.d,
                durationDays,
                absolute: true,
                absoluteFrom: berlinDayIso(lo, false),
                absoluteUntil: berlinDayIso(hi, true),
                recurrence: { type: "annual", month: lo.m, day: lo.d },
                rewardCoinsPerCollect: 2,
                collectTarget: 3,
                milestoneBonusCoins: 5,
                quests,
                lobby,
              }),
            });
            const next = res.events || [];
            events.length = 0;
            events.push(...next);
            if (res.year) {
              overview.months = res.year.months || overview.months;
              overview.year = res.year.year || overview.year;
            }
            selectedId = res.event?.id || selectedId;
            closeModal();
            paint();
          } catch (err) {
            if (errEl) {
              errEl.hidden = false;
              errEl.textContent = err?.message || "Anlegen fehlgeschlagen";
            }
            if (submit) submit.disabled = false;
          }
        };
      }

      const testBtn = $("evTestBtn");
      if (testBtn) {
        testBtn.onclick = async () => {
          if (!confirm("2-Tage-Testevent jetzt starten? (Sammeln, Quests, Schmuck, Lobby/Contest-Config)")) return;
          try {
            const res = await api("/admin/events", {
              method: "POST",
              body: JSON.stringify({
                id: `test_2d_${Date.now().toString(36).slice(-4)}`,
                title: "Testevent 2 Tage",
                emoji: "🧪",
                description:
                  "Live-Test: täglich sammeln, Striche-Quest, App-Schmuck. Nach dem Test löschen.",
                hint: "Heute und morgen unter Sozial → Events sammeln. Zeichnen zählt für die Quest.",
                absolute: true,
                durationDays: 2,
                sort: 999,
                enabled: true,
                rewardCoinsPerCollect: 3,
                collectTarget: 2,
                milestoneBonusCoins: 5,
                rewardItem: {
                  kind: "emojis",
                  itemId: "💘",
                  emoji: "💘",
                  label: "Test-Liebespfeil",
                },
                decor: {
                  particles: "hearts",
                  accentHex: "#E94E77",
                  bannerText: "Testevent aktiv!",
                  intensity: 0.8,
                  ornaments: "hearts",
                },
                quests: [
                  {
                    id: "q_strokes",
                    title: "10 Striche malen",
                    hint: "Einfach in einer Lobby zeichnen.",
                    metric: "strokes",
                    target: 10,
                    rewardCoins: 3,
                  },
                  {
                    id: "q_session",
                    title: "Eine Mal-Session starten",
                    hint: "Einmal mit dem Zeichnen beginnen.",
                    metric: "draw_sessions",
                    target: 1,
                    rewardCoins: 2,
                  },
                ],
                contest: {
                  enabled: true,
                  places: [
                    { place: 1, coins: 100, rewardItem: { kind: "stickers", itemId: "🥇", emoji: "🥇", label: "Goldmedaille" } },
                    { place: 2, coins: 50 },
                    { place: 3, coins: 25 },
                    { place: 4, coins: 10 },
                    { place: 5, coins: 10 },
                    { place: 6, coins: 10 },
                    { place: 7, coins: 10 },
                    { place: 8, coins: 10 },
                    { place: 9, coins: 10 },
                    { place: 10, coins: 10 },
                  ],
                  voterRewardCoins: 1,
                  voteRequire: { drewInEventLobby: true, minStrokes: 5 },
                },
                lobby: {
                  enabled: true,
                  access: "friends",
                  createMode: "manual",
                  palette: "event",
                  invitesAllowed: false,
                  drawMode: "promptList",
                  prompts: ["Herz", "Stern", "Sonne", "Katze", "Blume", "Mond"],
                  minStrokesToQualify: 5,
                  sessionSeconds: 180,
                },
              }),
            });
            const next = res.events || [];
            events.length = 0;
            events.push(...next);
            selectedId = res.event?.id || selectedId;
            alert("Testevent läuft 2 Tage — App neu öffnen / Sozial → Events.");
            paint();
          } catch (err) {
            alert(err?.message || "Testevent fehlgeschlagen");
          }
        };
      }

      content.querySelectorAll("[data-ev-id]").forEach((btn) => {
        btn.onclick = () => {
          selectedId = btn.getAttribute("data-ev-id") || "";
          paint();
        };
      });

      const form = $("evEditForm");
      if (form) {
        const syncPreview = () => {
          const fd = new FormData(form);
          const live = $("evDecorLive");
          if (live) {
            live.innerHTML = decorPreviewHtml({
              particles: fd.get("particles"),
              accentHex: fd.get("accentHex"),
              bannerText: fd.get("bannerText"),
              intensity: fd.get("intensity"),
              ornaments: fd.get("ornaments"),
            });
          }
        };
        form.querySelectorAll("input, select, textarea").forEach((el) => {
          el.addEventListener("input", syncPreview);
          el.addEventListener("change", syncPreview);
        });
        form.onsubmit = async (e) => {
          e.preventDefault();
          const fd = new FormData(form);
          const id = form.getAttribute("data-id");
          const patch = {
            id,
            title: String(fd.get("title") || ""),
            emoji: String(fd.get("emoji") || ""),
            description: String(fd.get("description") || ""),
            hint: String(fd.get("hint") || ""),
            enabled: Boolean(fd.get("enabled")),
            rewardCoinsPerCollect: Number(fd.get("rewardCoinsPerCollect")),
            collectTarget: Number(fd.get("collectTarget")),
            milestoneBonusCoins: Number(fd.get("milestoneBonusCoins")),
            durationDays: Number(fd.get("durationDays")),
            decor: {
              particles: String(fd.get("particles") || "none"),
              accentHex: String(fd.get("accentHex") || "#E94E77"),
              bannerText: String(fd.get("bannerText") || ""),
              intensity: Number(fd.get("intensity")),
              ornaments: String(fd.get("ornaments") || "none"),
            },
          };
          try {
            const res = await api("/admin/events", {
              method: "PUT",
              body: JSON.stringify({ events: [patch] }),
            });
            const next = res.events || [];
            events.length = 0;
            events.push(...next);
            alert("Gespeichert");
            paint();
          } catch (err) {
            alert(err?.message || "Speichern fehlgeschlagen");
          }
        };
        const delBtn = $("evDeleteBtn");
        if (delBtn) {
          delBtn.onclick = async () => {
            const id = form.getAttribute("data-id");
            if (
              !confirm(
                "Event wirklich löschen?\nSchmuck endet, Fortschritt und Event-Lobbys werden entfernt."
              )
            ) {
              return;
            }
            try {
              const res = await api(`/admin/events/${encodeURIComponent(id)}`, {
                method: "DELETE",
              });
              const next = res.events || [];
              events.length = 0;
              events.push(...next);
              selectedId = events[0]?.id || "";
              alert(
                res.deleted
                  ? `Gelöscht (Lobbys: ${res.roomsCleared || 0}, Progress: ${res.progressCleared || 0})`
                  : "Deaktiviert"
              );
              paint();
            } catch (err) {
              alert(err?.message || "Löschen fehlgeschlagen");
            }
          };
        }
        const disBtn = $("evDisableBtn");
        if (disBtn) {
          disBtn.onclick = async () => {
            const id = form.getAttribute("data-id");
            if (!confirm("Builtin-Event deaktivieren? (Schmuck endet, falls es das aktive war.)")) return;
            try {
              const res = await api(`/admin/events/${encodeURIComponent(id)}`, {
                method: "DELETE",
              });
              const next = res.events || [];
              events.length = 0;
              events.push(...next);
              alert("Deaktiviert");
              paint();
            } catch (err) {
              alert(err?.message || "Deaktivieren fehlgeschlagen");
            }
          };
        }
      }
    }

    paint();
  }

  window.LuvAdmPanels = {
    renderAchievements,
    renderUsers,
    renderPhrases,
    renderDailyTasks,
    renderEvents,
    openAchievementWizard,
    openUserDetail,
  };
})();

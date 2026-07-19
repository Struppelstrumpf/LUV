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
                    <div class="muted mono">${esc(l.code)} · ${l.live ? "live" : l.active ? "aktiv" : "inaktiv"} · ${l.online ?? 0}/${l.capacity ?? "?"} online</div>
                  </div>
                  <div class="actions">
                    <button type="button" class="btn secondary" data-canvas="${esc(l.code)}">Leinwand</button>
                  </div>
                </div>`
                    )
                    .join("")}</div>`
                : `<p class="muted">Keine gehosteten Lobbys.</p>`
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
      <div class="actions" style="margin-top:0.75rem">
        <button type="button" class="btn ghost" id="cancelModal">Schließen</button>
      </div>`,
      true
    );
    $("cancelModal").onclick = closeModal;
    const canvas = $("admCanvas");
    const ctx = canvas.getContext("2d");
    const palette = ["#E94E77", "#4EC4FF", "#2EE6A8", "#FFD54F", "#9B7BFF", "#FF8A65", "#F4F1EA", "#80CBC4"];
    ctx.fillStyle = "#121826";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    for (const s of strokes) {
      const pts = s.points || [];
      if (pts.length < 2) continue;
      ctx.strokeStyle = palette[(s.colorIndex || 0) % palette.length];
      ctx.lineWidth = Math.max(1, (s.width || 0.01) * canvas.width);
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
  }

  async function renderPhrases() {
    const content = contentEl();
    const pool = host().state.phrasePool || "";
    const q = host().state.phraseQ || "";
    const qs = new URLSearchParams();
    if (pool) qs.set("pool", pool);
    if (q) qs.set("q", q);
    const data = await api("/admin/notify-phrases?" + qs.toString());
    const phrases = data.phrases || [];
    const targets = data.targets || [];
    const targetLabel = Object.fromEntries(targets.map((t) => [t.id, t.label]));

    content.innerHTML = `
      <div class="panel ach-hero">
        <div class="shop-top">
          <div>
            <h3 style="margin:0;font-family:var(--display);font-size:1.55rem">Sprüche</h3>
            <p class="help" style="margin:0.4rem 0 0;max-width:44rem">
              Mood = Push-Benachrichtigungen in der App. Share = OG-/Einladungs-Texte.
              Pro Spruch entscheidest du, wohin Tippen führt (Leinwand, Markt, Inventar, Profil …).
            </p>
          </div>
          <button class="btn teal" id="phrNew">＋ Neuer Spruch</button>
        </div>
        <div class="shop-cats">
          <button type="button" class="shop-cat ${!pool ? "on" : ""}" data-pool="">Alle <span class="shop-cat-n">${data.counts?.all ?? phrases.length}</span></button>
          <button type="button" class="shop-cat ${pool === "mood" ? "on" : ""}" data-pool="mood">Mood / Push <span class="shop-cat-n">${data.counts?.mood ?? 0}</span></button>
          <button type="button" class="shop-cat ${pool === "share" ? "on" : ""}" data-pool="share">Share / OG <span class="shop-cat-n">${data.counts?.share ?? 0}</span></button>
        </div>
        <div class="toolbar shop-search-bar">
          <input id="phrQ" placeholder="Suchen…" value="${esc(q)}" />
          <button class="btn secondary" id="phrFilter">Suchen</button>
        </div>
      </div>
      <div class="ach-grid">
        ${
          phrases.length
            ? phrases
                .map(
                  (p) => `<article class="ach-card ${p.enabled ? "" : "is-off"}">
            <header>
              <div>
                <strong style="font-size:0.95rem;line-height:1.35">${esc(p.text)}</strong>
                <div class="muted" style="margin-top:0.35rem;font-size:0.8rem">${esc(p.subtitle || "—")}</div>
              </div>
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
        renderPhrases();
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

  window.LuvAdmPanels = {
    renderAchievements,
    renderUsers,
    renderPhrases,
    openAchievementWizard,
    openUserDetail,
  };
})();

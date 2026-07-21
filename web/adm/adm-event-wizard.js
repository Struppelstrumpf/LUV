/**
 * LUV Admin — Event anlegen/bearbeiten (Mehrschritt-Wizard).
 * window.LuvEventWizard.open({ mode, event, api, esc, openModal, closeModal, $, onSaved })
 */
(function () {
  const DECOR = [
    { id: "none", label: "Keine", emoji: "⬜" },
    { id: "snow", label: "Schnee", emoji: "❄️" },
    { id: "hearts", label: "Herzen", emoji: "💕" },
    { id: "leaves", label: "Blätter", emoji: "🍂" },
    { id: "sparkle", label: "Funken", emoji: "✨" },
  ];
  const ORNAMENTS = [
    { id: "none", label: "Keine" },
    { id: "wreath", label: "Kranz" },
    { id: "hearts", label: "Herzen" },
    { id: "spark", label: "Funken" },
  ];
  const WEEKDAYS = [
    { v: 1, l: "Montag" },
    { v: 2, l: "Dienstag" },
    { v: 3, l: "Mittwoch" },
    { v: 4, l: "Donnerstag" },
    { v: 5, l: "Freitag" },
    { v: 6, l: "Samstag" },
    { v: 0, l: "Sonntag" },
  ];
  const DEFAULT_PROMPTS = ["Herz", "Stern", "Sonne", "Katze", "Blume", "Mond", "Haus", "Baum"];
  const MAX_REWARD_ITEMS = 6;

  function pad2(n) {
    return String(n).padStart(2, "0");
  }

  function isImgId(s) {
    return /^img_/i.test(String(s || "").trim());
  }

  function itemThumbHtml(it, esc) {
    if (!it) return `<span class="ev-shop-glyph">🎁</span>`;
    if (it.hasImage && it.imageUrl) {
      return `<img class="ev-item-thumb" src="${esc(it.imageUrl)}" alt="" loading="lazy" />`;
    }
    if (it.kind === "themes" && it.visualConfig) {
      const v = it.visualConfig;
      return `<span class="ev-item-theme" style="background:linear-gradient(160deg,${esc(
        v.skyTop || "#7EB8D8"
      )},${esc(v.skyBottom || "#B8D4E8")} 55%,${esc(v.groundTop || "#2F5D2E")})"></span>`;
    }
    const emo = String(it.emoji || "").trim();
    if (emo && !isImgId(emo)) {
      return `<span class="ev-shop-glyph">${esc(emo)}</span>`;
    }
    if (isImgId(it.itemId) && ["pets", "emojis", "stickers"].includes(String(it.kind || ""))) {
      const url = `/luv/v1/shop/pet-image/${encodeURIComponent(it.itemId)}`;
      return `<img class="ev-item-thumb" src="${esc(url)}" alt="" loading="lazy" onerror="this.outerHTML='<span class=\\'ev-shop-glyph\\'>🎁</span>'" />`;
    }
    return `<span class="ev-shop-glyph">🎁</span>`;
  }

  function catalogHit(catalogItems, kind, itemId) {
    return catalogItems.find((x) => x.kind === kind && x.itemId === itemId) || null;
  }

  function rewardFromCatalog(it) {
    if (!it) return null;
    const emo = String(it.emoji || "").trim();
    return {
      kind: it.kind,
      itemId: it.itemId,
      emoji: emo && !isImgId(emo) ? emo : it.previewEmoji || it.itemId,
      label: it.label || it.itemId,
      hasImage: Boolean(it.hasImage),
      imageUrl: it.imageUrl || null,
      visualConfig: it.visualConfig || null,
    };
  }

  function toLocalInput(isoOrMs) {
    if (!isoOrMs && isoOrMs !== 0) return "";
    const d = new Date(typeof isoOrMs === "number" ? isoOrMs : Date.parse(String(isoOrMs)));
    if (!Number.isFinite(d.getTime())) return "";
    // datetime-local in Europe/Berlin Näherung über lokale Browser-TZ — Admin meist DE
    const y = d.getFullYear();
    const m = pad2(d.getMonth() + 1);
    const day = pad2(d.getDate());
    const h = pad2(d.getHours());
    const min = pad2(d.getMinutes());
    return `${y}-${m}-${day}T${h}:${min}`;
  }

  function localInputToIso(v) {
    const s = String(v || "").trim();
    if (!s) return null;
    const t = Date.parse(s);
    return Number.isFinite(t) ? new Date(t).toISOString() : null;
  }

  function open(opts) {
    const api = opts.api;
    const esc = opts.esc;
    const openModal = opts.openModal;
    const closeModal = opts.closeModal;
    const $ = opts.$;
    const onSaved = opts.onSaved || (() => {});
    const mode = opts.mode === "edit" ? "edit" : "create";
    const existing = opts.event || null;

    let step = 0;
    const steps = ["Basis", "Zeit", "Belohnung", "Shop-Items", "Lobby", "Look", "Fertig"];

    const draft = {
      id: existing?.id || "",
      title: existing?.title || "",
      emoji: existing?.emoji || "🎉",
      description: existing?.description || "",
      hint: existing?.hint || "",
      enabled: existing?.enabled !== false,
      scheduleMode: existing?.schedule?.mode === "absolute" || existing?.schedule?.absoluteFrom
        ? "absolute"
        : existing?.recurrence?.type === "weekly"
          ? "weekly"
          : "absolute",
      absoluteFrom: toLocalInput(existing?.schedule?.absoluteFrom) || toLocalInput(Date.now()),
      absoluteUntil:
        toLocalInput(existing?.schedule?.absoluteUntil) ||
        toLocalInput(Date.now() + 2 * 86400000),
      weekday: Number(existing?.recurrence?.weekday ?? 5),
      startHour: Number.isFinite(Number(existing?.recurrence?.startHour))
        ? Number(existing.recurrence.startHour)
        : 18,
      startMinute: Number(existing?.recurrence?.startMinute || 0),
      endHour: Number.isFinite(Number(existing?.recurrence?.endHour))
        ? Number(existing.recurrence.endHour)
        : 1,
      endMinute: Number(existing?.recurrence?.endMinute || 0),
      endDayOffset: Number(existing?.recurrence?.endDayOffset || 1),
      rewardCoinsPerCollect: Number(existing?.rewardCoinsPerCollect ?? 2),
      collectTarget: Number(existing?.collectTarget ?? 1),
      milestoneBonusCoins: Number(existing?.milestoneBonusCoins ?? 0),
      rewardItems: (() => {
        const fromArr = Array.isArray(existing?.rewardItems) ? existing.rewardItems : [];
        const list = fromArr.length
          ? fromArr
          : existing?.rewardItem
            ? [existing.rewardItem]
            : [];
        return list
          .filter((r) => r && r.kind && r.itemId)
          .slice(0, MAX_REWARD_ITEMS)
          .map((r) => ({
            kind: r.kind,
            itemId: r.itemId,
            emoji: r.emoji || r.itemId,
            label: r.label || r.itemId,
            hasImage: Boolean(r.hasImage),
            imageUrl: r.imageUrl || null,
            visualConfig: r.visualConfig || null,
          }));
      })(),
      shopBindings: [], // {kind,itemId,label}
      lobbyEnabled: Boolean(existing?.lobby?.enabled),
      lobbyPrompts: (existing?.lobby?.prompts || DEFAULT_PROMPTS).join("\n"),
      particles: existing?.decor?.particles || "sparkle",
      accentHex: (existing?.decor?.accentHex || "#E94E77").slice(0, 7),
      bannerText: existing?.decor?.bannerText || "",
      intensity: Number(existing?.decor?.intensity ?? 0.55),
      ornaments: existing?.decor?.ornaments || "none",
    };

    let catalogItems = [];
    let eventOptions = [];

    async function loadExtras() {
      try {
        const shop = await api("/admin/shop/items");
        catalogItems = shop.items || [];
      } catch (_) {
        catalogItems = [];
      }
      try {
        const ev = await api("/admin/shop/event-options");
        eventOptions = ev.events || [];
      } catch (_) {
        eventOptions = [];
      }
      // Vorbelegung: Items die schon an dieses Event gebunden sind
      if (existing?.id) {
        draft.shopBindings = catalogItems
          .filter((it) => String(it.eventId || "") === String(existing.id))
          .map((it) => ({
            kind: it.kind,
            itemId: it.itemId,
            label: it.label || it.itemId,
          }));
      }
      // Belohnungs-Thumbs aus Katalog anreichern (Custom-Bilder)
      draft.rewardItems = draft.rewardItems.map((r) => {
        const hit = catalogHit(catalogItems, r.kind, r.itemId);
        return hit ? { ...rewardFromCatalog(hit), label: r.label || hit.label } : r;
      });
    }

    function saveStep() {
      const form = $("evWizForm");
      if (!form) return true;
      const fd = new FormData(form);
      if (step === 0) {
        draft.title = String(fd.get("title") || "").trim();
        draft.emoji = String(fd.get("emoji") || "🎉").trim().slice(0, 8) || "🎉";
        draft.description = String(fd.get("description") || "").trim();
        draft.hint = String(fd.get("hint") || "").trim();
        draft.enabled = Boolean(fd.get("enabled"));
        if (!draft.title) {
          alert("Bitte einen Titel eingeben.");
          return false;
        }
      }
      if (step === 1) {
        draft.scheduleMode = String(fd.get("scheduleMode") || "absolute");
        if (draft.scheduleMode === "absolute") {
          draft.absoluteFrom = String(fd.get("absoluteFrom") || "");
          draft.absoluteUntil = String(fd.get("absoluteUntil") || "");
          if (!localInputToIso(draft.absoluteFrom) || !localInputToIso(draft.absoluteUntil)) {
            alert("Bitte Von und Bis mit Datum und Uhrzeit setzen.");
            return false;
          }
        } else {
          draft.weekday = Number(fd.get("weekday"));
          draft.startHour = Number(fd.get("startHour"));
          draft.startMinute = Number(fd.get("startMinute"));
          draft.endHour = Number(fd.get("endHour"));
          draft.endMinute = Number(fd.get("endMinute"));
          draft.endDayOffset = Number(fd.get("endDayOffset") || 0);
        }
      }
      if (step === 2) {
        draft.rewardCoinsPerCollect = Number(fd.get("rewardCoinsPerCollect") || 0);
        draft.collectTarget = Math.max(1, Number(fd.get("collectTarget") || 1));
        draft.milestoneBonusCoins = Number(fd.get("milestoneBonusCoins") || 0);
        // rewardItems kommen aus den Plus-Kacheln (draft), nicht aus Form-Feldern
      }
      if (step === 3) {
        const checks = form.querySelectorAll("[data-shop-bind]:checked");
        draft.shopBindings = [...checks].map((el) => ({
          kind: el.getAttribute("data-kind"),
          itemId: el.getAttribute("data-item-id"),
          label: el.getAttribute("data-label") || el.getAttribute("data-item-id"),
        }));
      }
      if (step === 4) {
        draft.lobbyEnabled = Boolean(fd.get("lobbyEnabled"));
        draft.lobbyPrompts = String(fd.get("lobbyPrompts") || "");
        if (draft.lobbyEnabled) {
          const words = draft.lobbyPrompts
            .split(/[\n,;]+/)
            .map((s) => s.trim())
            .filter(Boolean);
          if (!words.length) {
            alert("Lobby an: bitte Wörter für den Pool eintragen.");
            return false;
          }
        }
      }
      if (step === 5) {
        draft.particles = String(fd.get("particles") || "sparkle");
        draft.accentHex = String(fd.get("accentHex") || "#E94E77");
        draft.bannerText = String(fd.get("bannerText") || "").trim();
        draft.intensity = Number(fd.get("intensity") || 0.55);
        draft.ornaments = String(fd.get("ornaments") || "none");
      }
      return true;
    }

    function stepBody() {
      if (step === 0) {
        return `
          <div class="ev-wiz-hero" style="--ev-accent:${esc(draft.accentHex)}">
            <span class="ev-wiz-emoji">${esc(draft.emoji)}</span>
            <div>
              <strong>${esc(mode === "edit" ? "Event bearbeiten" : "Neues Event")}</strong>
              <p class="muted" style="margin:0.2rem 0 0">Titel, Emoji und Texte — wie in der App.</p>
            </div>
          </div>
          <label class="field">Titel
            <input name="title" maxlength="60" value="${esc(draft.title)}" required placeholder="Date-Night Freitag" />
          </label>
          <div class="grid-2">
            <label class="field">Emoji
              <input name="emoji" maxlength="8" value="${esc(draft.emoji)}" />
            </label>
            <label class="field" style="flex-direction:row;align-items:center;gap:0.5rem;margin-top:1.4rem">
              <input type="checkbox" name="enabled" ${draft.enabled ? "checked" : ""} /> Aktiv
            </label>
          </div>
          <label class="field">Beschreibung
            <textarea name="description" rows="2" maxlength="240" placeholder="Was passiert in diesem Event?">${esc(draft.description)}</textarea>
          </label>
          <label class="field">Hinweis (kurz)
            <input name="hint" maxlength="200" value="${esc(draft.hint)}" placeholder="Freitags ab 18 Uhr einmal sammeln" />
          </label>`;
      }
      if (step === 1) {
        return `
          <p class="help">Zeitfenster steuert, wann das Event in der App aktiv ist (Home-Schmuck, Sammeln, Shop).</p>
          <div class="seg" style="margin-bottom:0.75rem">
            <button type="button" class="ev-mode-btn ${draft.scheduleMode === "absolute" ? "on" : ""}" data-mode="absolute">Einmalig (Datum + Uhrzeit)</button>
            <button type="button" class="ev-mode-btn ${draft.scheduleMode === "weekly" ? "on" : ""}" data-mode="weekly">Wöchentlich (z. B. Freitagabend)</button>
          </div>
          <input type="hidden" name="scheduleMode" id="evScheduleMode" value="${esc(draft.scheduleMode)}" />
          <div id="evAbsBlock" ${draft.scheduleMode === "absolute" ? "" : "hidden"}>
            <div class="grid-2">
              <label class="field">Von
                <input name="absoluteFrom" type="datetime-local" value="${esc(draft.absoluteFrom)}" />
              </label>
              <label class="field">Bis
                <input name="absoluteUntil" type="datetime-local" value="${esc(draft.absoluteUntil)}" />
              </label>
            </div>
            <p class="tip">Beispiel: Freitag 18:00 bis Samstag 01:00 — nur einmal Sammel-Ziel setzen.</p>
          </div>
          <div id="evWeekBlock" ${draft.scheduleMode === "weekly" ? "" : "hidden"}>
            <label class="field">Wochentag Start
              <select name="weekday">
                ${WEEKDAYS.map(
                  (w) =>
                    `<option value="${w.v}" ${draft.weekday === w.v ? "selected" : ""}>${esc(w.l)}</option>`
                ).join("")}
              </select>
            </label>
            <div class="grid-2">
              <label class="field">Start Uhrzeit
                <div class="grid-2" style="gap:0.35rem">
                  <input name="startHour" type="number" min="0" max="23" value="${draft.startHour}" />
                  <input name="startMinute" type="number" min="0" max="59" value="${draft.startMinute}" />
                </div>
              </label>
              <label class="field">Ende Uhrzeit
                <div class="grid-2" style="gap:0.35rem">
                  <input name="endHour" type="number" min="0" max="23" value="${draft.endHour}" />
                  <input name="endMinute" type="number" min="0" max="59" value="${draft.endMinute}" />
                </div>
              </label>
            </div>
            <label class="field">Ende-Tag Offset (0 = gleicher Tag, 1 = nächster Tag)
              <input name="endDayOffset" type="number" min="0" max="2" value="${draft.endDayOffset}" />
            </label>
            <p class="tip">Date-Night: Freitag 18:00 → Offset 1, Ende 01:00 (Samstag).</p>
          </div>`;
      }
      if (step === 2) {
        const slots = [];
        for (let i = 0; i < MAX_REWARD_ITEMS; i++) {
          const it = draft.rewardItems[i];
          if (it) {
            slots.push(`<button type="button" class="ev-reward-slot is-filled" data-reward-slot="${i}" title="Tippen zum Entfernen">
              ${itemThumbHtml(it, esc)}
              <span class="ev-reward-slot-label">${esc(it.label || it.itemId)}</span>
              <span class="ev-reward-slot-x">×</span>
            </button>`);
          } else {
            slots.push(`<button type="button" class="ev-reward-slot is-empty" data-reward-add title="Item wählen">
              <span class="ev-reward-plus">+</span>
              <span class="ev-reward-slot-label">Item</span>
            </button>`);
          }
        }
        return `
          <div class="grid-2">
            <label class="field">Coins pro Sammeln
              <input name="rewardCoinsPerCollect" type="number" min="0" max="50" value="${draft.rewardCoinsPerCollect}" />
            </label>
            <label class="field">Sammel-Ziel (Tipps)
              <input name="collectTarget" type="number" min="1" max="31" value="${draft.collectTarget}" />
              <span class="tip">Kurzes Event = meist 1</span>
            </label>
          </div>
          <label class="field">Meilenstein-Bonus (Coins)
            <input name="milestoneBonusCoins" type="number" min="0" max="100" value="${draft.milestoneBonusCoins}" />
          </label>
          <div class="field">
            <span>Item-Belohnungen (optional, max. ${MAX_REWARD_ITEMS})</span>
            <p class="tip" style="margin:0.25rem 0 0.55rem">Plus-Kachel → Item wählen. Belegte Kachel tippen = entfernen. Alle werden beim Meilenstein vergeben.</p>
            <div class="ev-reward-slots">${slots.join("")}</div>
          </div>
          <div id="evRewardPickLayer" class="ev-pick-layer" hidden>
            <div class="ev-pick-panel">
              <div class="ev-pick-head">
                <strong>Item wählen</strong>
                <button type="button" class="btn ghost" id="evRewardPickClose">✕</button>
              </div>
              <input type="search" id="evRewardPickQ" class="ev-pick-search" placeholder="Suchen…" autocomplete="off" />
              <div class="ev-pick-grid" id="evRewardPickGrid"></div>
            </div>
          </div>`;
      }
      if (step === 3) {
        const unbound = catalogItems.filter((it) => {
          const eid = String(it.eventId || "");
          return !eid || (existing && eid === String(existing.id));
        });
        const rows = unbound
          .map((it) => {
            const on = draft.shopBindings.some(
              (b) => b.kind === it.kind && b.itemId === it.itemId
            );
            const taken =
              it.eventId && (!existing || String(it.eventId) !== String(existing.id));
            return `<label class="ev-shop-pick ${taken ? "is-taken" : ""}">
              <input type="checkbox" data-shop-bind data-kind="${esc(it.kind)}" data-item-id="${esc(
              it.itemId
            )}" data-label="${esc(it.label || it.itemId)}" ${on ? "checked" : ""} ${
              taken ? "disabled" : ""
            } />
              <span class="ev-shop-glyph-wrap">${itemThumbHtml(it, esc)}</span>
              <span>
                <strong>${esc(it.label || it.itemId)}</strong>
                <span class="muted">${esc(it.kind)} · ${esc(String(it.priceCoins ?? "—"))} Coins${
              taken ? " · anderes Event" : ""
            }</span>
              </span>
            </label>`;
          })
          .join("");
        return `
          <p class="help">Wähle Items, die nur während diesem Event im Shop / Event-Popup erscheinen (max. 1 pro Kategorie und Jahr beim Anlegen neuer Event-Items).</p>
          <p class="tip">Neue Event-Items legst du unter Items → Event-Begleiter/Sticker/… an und wählst hier das Event.</p>
          <div class="ev-shop-grid">${rows || `<p class="help">Keine Shop-Items geladen.</p>`}</div>`;
      }
      if (step === 4) {
        return `
          <label class="field" style="flex-direction:row;align-items:center;gap:0.5rem">
            <input type="checkbox" name="lobbyEnabled" ${draft.lobbyEnabled ? "checked" : ""} />
            Event-Zeichenlobby aktiv
          </label>
          <label class="field">Wort-Pool (eins pro Zeile)
            <textarea name="lobbyPrompts" rows="6" placeholder="Herz&#10;Stern">${esc(
              draft.lobbyPrompts
            )}</textarea>
          </label>
          <p class="tip">Spieler zeichnen eines der Wörter — optional für Wettbewerb.</p>`;
      }
      if (step === 5) {
        return `
          <div class="ev-decor-preview" style="--ev-accent:${esc(draft.accentHex)}" data-particles="${esc(
          draft.particles
        )}">
            <div class="ev-decor-particles" aria-hidden="true"></div>
            <div class="ev-decor-banner">${esc(draft.bannerText || draft.title || "Banner")}</div>
            <div class="ev-decor-label">${esc(draft.emoji)} Home-Schmuck-Vorschau</div>
          </div>
          <div class="grid-2" style="margin-top:0.75rem">
            <label class="field">Partikel / Animation
              <select name="particles" id="evParticles">
                ${DECOR.map(
                  (p) =>
                    `<option value="${p.id}" ${draft.particles === p.id ? "selected" : ""}>${esc(
                      p.emoji
                    )} ${esc(p.label)}</option>`
                ).join("")}
              </select>
            </label>
            <label class="field">Akzentfarbe
              <input name="accentHex" id="evAccent" type="color" value="${esc(draft.accentHex)}" />
            </label>
          </div>
          <label class="field">Banner-Text
            <input name="bannerText" id="evBanner" maxlength="48" value="${esc(draft.bannerText)}" />
          </label>
          <div class="grid-2">
            <label class="field">Intensität
              <input name="intensity" type="number" min="0" max="1" step="0.05" value="${draft.intensity}" />
            </label>
            <label class="field">Ornamente
              <select name="ornaments">
                ${ORNAMENTS.map(
                  (o) =>
                    `<option value="${o.id}" ${draft.ornaments === o.id ? "selected" : ""}>${esc(
                      o.label
                    )}</option>`
                ).join("")}
              </select>
            </label>
          </div>
          <p class="tip">Farbe & Partikel erscheinen im Home-Menü und links vom Plus — wie beim echten Event.</p>`;
      }
      // Fertig
      const when =
        draft.scheduleMode === "weekly"
          ? `Jeden ${WEEKDAYS.find((w) => w.v === draft.weekday)?.l || "?"} ${pad2(
              draft.startHour
            )}:${pad2(draft.startMinute)} → +${draft.endDayOffset}d ${pad2(draft.endHour)}:${pad2(
              draft.endMinute
            )}`
          : `${draft.absoluteFrom} → ${draft.absoluteUntil}`;
      return `
        <div class="panel" style="margin:0">
          <p><strong>${esc(draft.emoji)} ${esc(draft.title)}</strong></p>
          <p class="muted">${esc(when)}</p>
          <p>Sammeln: ${draft.collectTarget}× · ${draft.rewardCoinsPerCollect} Coins${
        draft.rewardItems.length
          ? ` · Belohnung ${esc(
              draft.rewardItems.map((r) => r.label || r.itemId).join(", ")
            )}`
          : ""
      }</p>
          <p>Shop-Items: ${draft.shopBindings.length} · Lobby: ${
        draft.lobbyEnabled ? "an" : "aus"
      }</p>
          <p class="muted">Look: ${esc(draft.particles)} · ${esc(draft.accentHex)}</p>
        </div>`;
    }

    function paint() {
      openModal(
        `
        <h3 style="font-family:var(--display);margin:0 0 0.5rem">${
          mode === "edit" ? "Event bearbeiten" : "Neues Event"
        }</h3>
        <div class="wizard-steps">${steps
          .map((s, i) => `<span class="${i === step ? "on" : ""}">${i + 1}. ${esc(s)}</span>`)
          .join("")}</div>
        <form id="evWizForm">
          ${stepBody()}
          <p id="evWizErr" class="error" hidden></p>
          <div class="actions" style="margin-top:1rem">
            ${step > 0 ? `<button type="button" class="btn ghost" id="evWizBack">Zurück</button>` : ""}
            ${
              step < steps.length - 1
                ? `<button type="button" class="btn" id="evWizNext">Weiter</button>`
                : `<button type="submit" class="btn teal">${
                    mode === "edit" ? "Speichern" : "Event anlegen"
                  }</button>`
            }
            <button type="button" class="btn ghost" id="cancelModal">Abbrechen</button>
          </div>
        </form>`,
        true
      );
      const cancel = $("cancelModal");
      if (cancel) cancel.onclick = closeModal;
      const back = $("evWizBack");
      if (back)
        back.onclick = () => {
          if (!saveStep()) return;
          step--;
          paint();
        };
      const next = $("evWizNext");
      if (next)
        next.onclick = () => {
          if (!saveStep()) return;
          step++;
          paint();
        };

      document.querySelectorAll(".ev-mode-btn").forEach((btn) => {
        btn.onclick = () => {
          draft.scheduleMode = btn.getAttribute("data-mode") || "absolute";
          const hid = $("evScheduleMode");
          if (hid) hid.value = draft.scheduleMode;
          const abs = $("evAbsBlock");
          const week = $("evWeekBlock");
          if (abs) abs.hidden = draft.scheduleMode !== "absolute";
          if (week) week.hidden = draft.scheduleMode !== "weekly";
          document.querySelectorAll(".ev-mode-btn").forEach((b) => {
            b.classList.toggle("on", b.getAttribute("data-mode") === draft.scheduleMode);
          });
        };
      });

      function openRewardPicker() {
        if (!saveStep()) return;
        if (draft.rewardItems.length >= MAX_REWARD_ITEMS) {
          alert(`Maximal ${MAX_REWARD_ITEMS} Belohnungs-Items.`);
          return;
        }
        const layer = $("evRewardPickLayer");
        const grid = $("evRewardPickGrid");
        const qEl = $("evRewardPickQ");
        if (!layer || !grid) return;
        layer.hidden = false;
        const renderGrid = () => {
          const q = String(qEl?.value || "")
            .trim()
            .toLowerCase();
          const taken = new Set(draft.rewardItems.map((r) => `${r.kind}:${r.itemId}`));
          const list = catalogItems
            .filter((it) => ["emojis", "stickers", "pets", "themes"].includes(it.kind))
            .filter((it) => {
              if (!q) return true;
              const hay = `${it.label || ""} ${it.itemId} ${it.kind} ${it.emoji || ""}`.toLowerCase();
              return hay.includes(q);
            })
            .slice(0, 240);
          grid.innerHTML = list
            .map((it) => {
              const key = `${it.kind}:${it.itemId}`;
              const disabled = taken.has(key);
              return `<button type="button" class="ev-pick-card ${disabled ? "is-taken" : ""}" data-kind="${esc(
                it.kind
              )}" data-item-id="${esc(it.itemId)}" ${disabled ? "disabled" : ""}>
                ${itemThumbHtml(it, esc)}
                <span class="ev-pick-card-meta">
                  <strong>${esc(it.label || it.itemId)}</strong>
                  <span class="muted">${esc(it.kind)}</span>
                </span>
              </button>`;
            })
            .join("");
          grid.querySelectorAll(".ev-pick-card:not([disabled])").forEach((btn) => {
            btn.onclick = () => {
              const kind = btn.getAttribute("data-kind");
              const itemId = btn.getAttribute("data-item-id");
              const hit = catalogHit(catalogItems, kind, itemId);
              const entry = rewardFromCatalog(hit || { kind, itemId, label: itemId });
              if (!entry) return;
              if (draft.rewardItems.some((r) => r.kind === entry.kind && r.itemId === entry.itemId)) {
                return;
              }
              if (draft.rewardItems.length >= MAX_REWARD_ITEMS) return;
              draft.rewardItems.push(entry);
              layer.hidden = true;
              saveStep();
              paint();
            };
          });
        };
        if (qEl) {
          qEl.value = "";
          qEl.oninput = renderGrid;
        }
        const closeBtn = $("evRewardPickClose");
        if (closeBtn) closeBtn.onclick = () => {
          layer.hidden = true;
        };
        layer.onclick = (ev) => {
          if (ev.target === layer) layer.hidden = true;
        };
        renderGrid();
      }
      document.querySelectorAll("[data-reward-add]").forEach((btn) => {
        btn.onclick = (e) => {
          e.preventDefault();
          openRewardPicker();
        };
      });
      document.querySelectorAll("[data-reward-slot]").forEach((btn) => {
        btn.onclick = (e) => {
          e.preventDefault();
          saveStep();
          const idx = Number(btn.getAttribute("data-reward-slot"));
          if (!Number.isFinite(idx)) return;
          draft.rewardItems.splice(idx, 1);
          paint();
        };
      });

      const accent = $("evAccent");
      const particles = $("evParticles");
      const banner = $("evBanner");
      const live = () => {
        const prev = document.querySelector(".ev-decor-preview");
        if (!prev) return;
        if (accent) {
          prev.style.setProperty("--ev-accent", accent.value);
          draft.accentHex = accent.value;
        }
        if (particles) {
          prev.setAttribute("data-particles", particles.value);
          draft.particles = particles.value;
        }
        if (banner) {
          const b = prev.querySelector(".ev-decor-banner");
          if (b) b.textContent = banner.value || draft.title || "Banner";
        }
      };
      if (accent) accent.oninput = live;
      if (particles) particles.onchange = live;
      if (banner) banner.oninput = live;

      const form = $("evWizForm");
      form.onsubmit = async (e) => {
        e.preventDefault();
        if (!saveStep()) return;
        const errEl = $("evWizErr");
        const body = buildPayload();
        try {
          let res;
          if (mode === "edit" && draft.id) {
            res = await api("/admin/events", {
              method: "PUT",
              body: JSON.stringify({ events: [{ id: draft.id, ...body }] }),
            });
          } else {
            res = await api("/admin/events", {
              method: "POST",
              body: JSON.stringify(body),
            });
          }
          const eventId = res.event?.id || draft.id;
          await bindShopItems(eventId);
          closeModal();
          onSaved(res);
        } catch (err) {
          if (errEl) {
            errEl.hidden = false;
            errEl.textContent = err?.message || "Speichern fehlgeschlagen";
          } else {
            alert(err?.message || "Speichern fehlgeschlagen");
          }
        }
      };
    }

    function buildPayload() {
      const rewardItems = draft.rewardItems
        .filter((r) => r && r.kind && r.itemId)
        .slice(0, MAX_REWARD_ITEMS)
        .map((r) => ({
          kind: r.kind,
          itemId: r.itemId,
          emoji: r.emoji || r.itemId,
          label: r.label || r.itemId,
        }));
      const rewardItem = rewardItems[0] || null;
      const decor = {
        particles: draft.particles,
        accentHex: draft.accentHex,
        bannerText: draft.bannerText,
        intensity: draft.intensity,
        ornaments: draft.ornaments,
      };
      const lobby = draft.lobbyEnabled
        ? {
            enabled: true,
            access: "friends",
            createMode: "manual",
            palette: "event",
            invitesAllowed: false,
            drawMode: "promptList",
            prompts: draft.lobbyPrompts
              .split(/[\n,;]+/)
              .map((s) => s.trim())
              .filter(Boolean)
              .slice(0, 40),
            minStrokesToQualify: 5,
            sessionSeconds: 180,
          }
        : { enabled: false };

      const base = {
        title: draft.title,
        emoji: draft.emoji,
        description: draft.description,
        hint: draft.hint,
        enabled: draft.enabled,
        rewardCoinsPerCollect: draft.rewardCoinsPerCollect,
        collectTarget: draft.collectTarget,
        milestoneBonusCoins: draft.milestoneBonusCoins,
        rewardItems,
        rewardItem,
        decor,
        lobby,
      };

      if (draft.scheduleMode === "weekly") {
        return {
          ...base,
          absolute: false,
          durationDays: 1,
          recurrence: {
            type: "weekly",
            weekday: draft.weekday,
            startHour: draft.startHour,
            startMinute: draft.startMinute,
            endHour: draft.endHour,
            endMinute: draft.endMinute,
            endDayOffset: draft.endDayOffset,
          },
          schedule: {
            mode: "recurring",
            recurrence: {
              type: "weekly",
              weekday: draft.weekday,
              startHour: draft.startHour,
              startMinute: draft.startMinute,
              endHour: draft.endHour,
              endMinute: draft.endMinute,
              endDayOffset: draft.endDayOffset,
            },
            durationDays: 1,
          },
        };
      }

      return {
        ...base,
        absolute: true,
        absoluteFrom: localInputToIso(draft.absoluteFrom),
        absoluteUntil: localInputToIso(draft.absoluteUntil),
        schedule: {
          mode: "absolute",
          absoluteFrom: localInputToIso(draft.absoluteFrom),
          absoluteUntil: localInputToIso(draft.absoluteUntil),
        },
        durationDays: 1,
      };
    }

    async function bindShopItems(eventId) {
      if (!eventId) return;
      const year = new Date().getFullYear();
      for (const b of draft.shopBindings) {
        try {
          await api(`/admin/shop/items/${encodeURIComponent(b.kind)}/${encodeURIComponent(b.itemId)}`, {
            method: "PUT",
            body: JSON.stringify({
              kind: b.kind,
              itemId: b.itemId,
              eventId,
              eventYear: year,
              rotationLocked: true,
            }),
          });
        } catch (_) {
          /* Konflikt / belegt — überspringen */
        }
      }
    }

    loadExtras().then(paint);
  }

  window.LuvEventWizard = { open };
})();

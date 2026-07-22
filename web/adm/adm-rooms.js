/**
 * Admin Räume: + Raum, Bild-Upload, Crop (weiß), Zonen.
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
  function contentEl() {
    return host().content;
  }

  const WEDDING_ROOM_IDS = ["wedding_small", "wedding", "wedding_grand"];
  const PLACE_PROP_COLORS = new Set(["gold", "pink", "lime"]);
  const DEFAULT_PROP_R = { gold: 0.028, pink: 0.032, lime: 0.04 };

  const TOOLS = [
    { id: "view-rect", label: "Weiß Karte", color: "white", shape: "rect", hint: "Gesamte Lauffläche" },
    { id: "camera-rect", label: "Schwarz Kamera", color: "black", shape: "rect", hint: "Was auf dem Handy sichtbar ist" },
    { id: "red-rect", label: "Rot", color: "red", shape: "rect", hint: "Hindernis" },
    { id: "green-rect", label: "Grün", color: "green", shape: "rect", hint: "Laufen" },
    { id: "blue-rect", label: "Blau Gäste", color: "blue", shape: "rect", hint: "Gast-Bereich = sitzen" },
    { id: "brown-circle", label: "Braun Spawn", color: "brown", shape: "circle", hint: "Spawn" },
    { id: "orange-circle", label: "Orange Größe", color: "orange", shape: "circle", hint: "Avatar" },
    { id: "yellow-rect", label: "Gelb Brautpaar", color: "yellow", shape: "rect", hint: "Altar: Lied + Timer" },
    { id: "gold-circle", label: "+ Kerze (Gold)", color: "gold", shape: "circle", hint: "Klick setzt Prop" },
    { id: "pink-circle", label: "+ Flamme", color: "pink", shape: "circle", hint: "Klick setzt Prop" },
    { id: "lime-circle", label: "+ Geldbaum", color: "lime", shape: "circle", hint: "Klick setzt Prop (1 Coin)" },
  ];

  function isWeddingBuiltin(id) {
    return WEDDING_ROOM_IDS.includes(String(id || "").trim());
  }

  function zoneLabel(z) {
    if (!z) return "?";
    const map = {
      white: "Karte",
      black: "Kamera",
      red: "Hindernis",
      green: "Laufen",
      blue: "Gast-Sitz",
      yellow: "Brautpaar",
      brown: "Spawn",
      orange: "Avatar-Größe",
      gold: "Kerze/Deko",
      pink: "Flamme",
      lime: "Geldbaum",
    };
    const kind = map[z.color] || z.color;
    if (z.shape === "circle") {
      return `${kind} · r=${Math.round((z.r || 0) * 100)}%`;
    }
    return `${kind} · ${Math.round((z.w || 0) * 100)}×${Math.round((z.h || 0) * 100)}%`;
  }

  const FILL = {
    white: "rgba(255,255,255,0.12)",
    black: "rgba(0,0,0,0.08)",
    red: "rgba(229,57,53,0.4)",
    green: "rgba(67,160,71,0.35)",
    yellow: "rgba(255,213,79,0.45)",
    blue: "rgba(66,165,245,0.45)",
    brown: "rgba(141,110,99,0.45)",
    orange: "rgba(255,152,0,0.4)",
    gold: "rgba(212,175,55,0.5)",
    pink: "rgba(255,112,67,0.55)",
    lime: "rgba(124,179,66,0.5)",
    purple: "rgba(156,39,176,0.4)",
    teal: "rgba(0,150,136,0.4)",
  };
  const STROKE = {
    white: "#ffffff",
    black: "#111111",
    red: "#e53935",
    green: "#43a047",
    yellow: "#ffd54f",
    blue: "#42a5f5",
    brown: "#8d6e63",
    orange: "#ff9800",
    gold: "#d4af37",
    pink: "#ff7043",
    lime: "#7cb342",
    purple: "#9c27b0",
    teal: "#009688",
  };

  let editor = null;

  function mergeGreens(zones) {
    const greens = zones.filter((z) => z.color === "green" && z.shape === "rect");
    const others = zones.filter((z) => !(z.color === "green" && z.shape === "rect"));
    if (greens.length <= 1) return zones.slice();
    const n = greens.length;
    const parent = Array.from({ length: n }, (_, i) => i);
    const find = (i) => {
      while (parent[i] !== i) {
        parent[i] = parent[parent[i]];
        i = parent[i];
      }
      return i;
    };
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        const a = greens[i];
        const b = greens[j];
        const touch = !(
          a.x + a.w < b.x - 0.002 ||
          b.x + b.w < a.x - 0.002 ||
          a.y + a.h < b.y - 0.002 ||
          b.y + b.h < a.y - 0.002
        );
        if (touch) {
          const ra = find(i);
          const rb = find(j);
          if (ra !== rb) parent[rb] = ra;
        }
      }
    }
    const groups = new Map();
    for (let i = 0; i < n; i++) {
      const r = find(i);
      if (!groups.has(r)) groups.set(r, []);
      groups.get(r).push(greens[i]);
    }
    const merged = [];
    for (const list of groups.values()) {
      let x0 = Infinity,
        y0 = Infinity,
        x1 = -Infinity,
        y1 = -Infinity;
      for (const g of list) {
        x0 = Math.min(x0, g.x);
        y0 = Math.min(y0, g.y);
        x1 = Math.max(x1, g.x + g.w);
        y1 = Math.max(y1, g.y + g.h);
      }
      merged.push({
        id: list[0].id,
        color: "green",
        shape: "rect",
        x: x0,
        y: y0,
        w: Math.max(0.01, x1 - x0),
        h: Math.max(0.01, y1 - y0),
      });
    }
    return [...others, ...merged];
  }

  function fileToDataUrl(file) {
    return new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = reject;
      r.readAsDataURL(file);
    });
  }

  async function createFromImage(dataUrl, name) {
    const res = await api("/admin/room-layouts", {
      method: "POST",
      body: JSON.stringify({
        name: name || "Raum",
        imageBase64: dataUrl,
      }),
    });
    return res.layout;
  }

  async function renderRooms() {
    const root = contentEl();
    if (!root) return;
    if (editor?.roomId) {
      await renderEditor(editor.roomId);
      return;
    }
    root.innerHTML = `<p class="muted">Lade…</p>`;
    const data = await api("/admin/room-layouts");
    const rooms = data.rooms || [];
    root.innerHTML = `
      <div class="panel">
        <div class="room-editor-bar">
          <h3 style="margin:0;flex:1">Räume</h3>
          <button type="button" class="btn" id="roomAdd">+ Raum</button>
          <input type="file" id="roomFile" accept="image/*" hidden />
        </div>
        <p class="help">Bild per Drag &amp; Drop, Strg+V oder Ordner. Dann Sichtbereich (weiß) und Zonen zeichnen. In der App unsichtbar.</p>
        <div id="roomDrop" class="room-drop">Bild hier ablegen oder einfügen (Strg+V)</div>
        <div class="room-list" style="margin-top:1rem">
          ${rooms
            .map(
              (r) => `
            <button type="button" class="room-card" data-room="${esc(r.id)}">
              <span class="room-card-title">${r.builtin ? "Raum: " : ""}${esc(r.name)}</span>
              <span class="muted">${r.zoneCount || 0} Bereiche${
                r.updatedAt ? " · " + new Date(r.updatedAt).toLocaleString("de-DE") : ""
              }${r.builtin ? " · Hochzeit" : r.pickable !== false ? " · Plus-Kachel" : " · nur Portal"}</span>
            </button>`
            )
            .join("") || `<p class="muted">Noch keine Räume — + Raum tippen.</p>`}
        </div>
      </div>`;

    const fileInput = root.querySelector("#roomFile");
    const openPicker = () => fileInput.click();
    root.querySelector("#roomAdd").addEventListener("click", openPicker);

    async function handleFiles(files) {
      const f = files && files[0];
      if (!f || !f.type.startsWith("image/")) return;
      const dataUrl = await fileToDataUrl(f);
      const name = (f.name || "Raum").replace(/\.[^.]+$/, "").slice(0, 40);
      try {
        const layout = await createFromImage(dataUrl, name);
        editor = { roomId: layout.id };
        renderRooms();
      } catch (e) {
        alert(e.message || "Upload fehlgeschlagen");
      }
    }

    fileInput.addEventListener("change", () => handleFiles(fileInput.files));
    const drop = root.querySelector("#roomDrop");
    drop.addEventListener("dragover", (e) => {
      e.preventDefault();
      drop.classList.add("over");
    });
    drop.addEventListener("dragleave", () => drop.classList.remove("over"));
    drop.addEventListener("drop", (e) => {
      e.preventDefault();
      drop.classList.remove("over");
      handleFiles(e.dataTransfer.files);
    });
    drop.addEventListener("click", openPicker);

    const onPaste = async (e) => {
      const items = e.clipboardData?.items;
      if (!items) return;
      for (const it of items) {
        if (it.type.startsWith("image/")) {
          e.preventDefault();
          await handleFiles([it.getAsFile()]);
          break;
        }
      }
    };
    document.addEventListener("paste", onPaste, { once: true });

    root.querySelectorAll("[data-room]").forEach((btn) => {
      btn.addEventListener("click", () => {
        editor = { roomId: btn.getAttribute("data-room") };
        renderRooms();
      });
    });
  }

  async function renderEditor(roomId) {
    const root = contentEl();
    root.innerHTML = `<p class="muted">Lade Raum…</p>`;
    const data = await api(`/admin/room-layouts/${encodeURIComponent(roomId)}`);
    const layout = data.layout;
    if (!layout) {
      root.innerHTML = `<p class="error">Nicht gefunden</p>`;
      return;
    }

    let zones = mergeGreens((layout.zones || []).map((z) => ({ ...z })));
    let portals = (layout.portals || []).map((p) => ({ ...p }));
    let actions = (layout.actions || []).map((a) => ({ ...a }));
    let pickable = layout.pickable !== false;
    let viewRect = { ...(layout.viewRect || { x: 0, y: 0, w: 1, h: 1 }) };
    let cameraRect = {
      w: layout.cameraRect?.w ?? viewRect.w,
      h: layout.cameraRect?.h ?? viewRect.h,
    };
    let tool = "view-rect";
    let selectedId = null;
    let draft = null;
    let dirty = false;
    let pendingImage = null;

    // Builtin-Flag vom Server ist maßgeblich (nicht nur ID-String)
    const weddingMode = Boolean(layout.builtin) || isWeddingBuiltin(roomId);

    const weddingSwitch = weddingMode
      ? `<select id="roomWeddingSwitch" class="room-wedding-switch" title="Hochzeits-Raum">
          ${WEDDING_ROOM_IDS.map(
            (id) =>
              `<option value="${id}" ${id === roomId ? "selected" : ""}>${
                id === "wedding_small"
                  ? "Klein"
                  : id === "wedding_grand"
                    ? "Prunk"
                    : "Kapelle"
              }</option>`
          ).join("")}
        </select>`
      : "";

    root.innerHTML = `
      <div class="room-editor">
        <div class="room-editor-bar">
          <button type="button" class="btn ghost" id="roomBack">← Räume</button>
          ${weddingSwitch}
          <input id="roomName" value="${esc(layout.name)}" style="flex:1;min-width:8rem" />
          ${
            layout.builtin
              ? ""
              : `<label style="display:flex;align-items:center;gap:0.35rem;white-space:nowrap;font-size:0.85rem">
                  <input type="checkbox" id="roomPickable" ${pickable ? "checked" : ""} /> Über Plus-Kachel wählbar
                </label>`
          }
          <button type="button" class="btn" id="roomSave">Speichern</button>
          ${
            layout.builtin
              ? ""
              : `<button type="button" class="btn ghost danger" id="roomDel">Löschen</button>`
          }
        </div>
        <p class="help">${
          weddingMode
            ? "Hochzeit: <b>Gelb</b> = Brautpaar ziehen · <b>+ Geldbaum / Flamme / Kerze</b> = einmal tippen setzt Prop. Grün = Laufen, Blau = Gäste."
            : "Weiß = ganze Karte · Schwarz = Kamera · Grün/Blau = Sitze/Laufen (ziehen)."
        }</p>
        <div class="room-tools" id="roomTools"></div>
        <p class="hint" id="roomToolHint" style="margin:0.35rem 0 0"></p>
        <div class="room-fit-wrap" id="roomFit">
          <canvas id="roomCanvas"></canvas>
        </div>
        <div class="room-side" style="margin-top:0.75rem">
          <p class="muted" id="roomStatus"></p>
          <label id="roomRadiusWrap" class="room-radius-wrap" style="display:none">
            Prop-Größe
            <input type="range" id="roomRadius" min="8" max="120" value="28" />
            <span id="roomRadiusVal">2.8%</span>
          </label>
          <ul class="room-zone-list" id="roomZoneList"></ul>
          <button type="button" class="btn ghost danger" id="roomDelete" disabled>Auswahl löschen</button>
          <button type="button" class="btn ghost danger" id="roomClearAll" style="margin-top:0.4rem">Alles löschen</button>
          <button type="button" class="btn ghost" id="roomReimg" style="margin-top:0.4rem">Bild ersetzen</button>
          <input type="file" id="roomReFile" accept="image/*" hidden />
        </div>
      </div>`;

    const canvas = root.querySelector("#roomCanvas");
    const ctx = canvas.getContext("2d");
    const fit = root.querySelector("#roomFit");
    const img = new Image();
    img.crossOrigin = "anonymous";
    let imgReady = false;

    function setStatus(msg) {
      root.querySelector("#roomStatus").textContent =
        (msg || "") + (dirty ? " · ungespeichert" : "");
    }

    function paintTools() {
      const box = root.querySelector("#roomTools");
      const baseTools = weddingMode
        ? TOOLS
        : TOOLS.filter((t) => !["yellow", "gold", "pink", "lime"].includes(t.color));
      const weddingPropIds = new Set(["yellow-rect", "gold-circle", "pink-circle", "lime-circle"]);
      const mainTools = baseTools.filter((t) => !weddingPropIds.has(t.id));
      const weddingTools = baseTools.filter((t) => weddingPropIds.has(t.id));
      const special = [
        { id: "link-portal", label: "Raum verknüpfen", color: "purple" },
        { id: "add-action", label: "+Aktion", color: "teal" },
      ];
      const btn = (t) => `
        <button type="button" class="room-tool ${tool === t.id ? "active" : ""}" data-tool="${t.id}" title="${esc(t.hint || "")}">
          <span class="swatch" style="background:${STROKE[t.color]}"></span>${esc(t.label)}
        </button>`;
      box.innerHTML =
        mainTools.map(btn).join("") +
        special.map(btn).join("") +
        (weddingMode && weddingTools.length
          ? `<div class="room-tools-wedding"><span class="room-tools-wedding-label">Hochzeit</span>${weddingTools
              .map(btn)
              .join("")}</div>`
          : "");
      box.querySelectorAll("[data-tool]").forEach((b) => {
        b.addEventListener("click", () => {
          tool = b.getAttribute("data-tool");
          selectedId = null;
          paintTools();
          paintList();
          updateHint();
          updateRadiusUi();
          draw();
        });
      });
      updateHint();
    }

    function updateHint() {
      const el = root.querySelector("#roomToolHint");
      if (!el) return;
      const t = currentTool();
      if (PLACE_PROP_COLORS.has(t.color)) {
        el.textContent = `${t.label}: einmal tippen setzt das Prop. Danach auswählen und ziehen / Größe ändern.`;
      } else {
        el.textContent = t.hint || "Ziehen zum Zeichnen.";
      }
    }

    function updateRadiusUi() {
      const wrap = root.querySelector("#roomRadiusWrap");
      const range = root.querySelector("#roomRadius");
      const val = root.querySelector("#roomRadiusVal");
      if (!wrap || !range || !val) return;
      const z = zones.find((x) => x.id === selectedId && x.shape === "circle");
      if (!z || !PLACE_PROP_COLORS.has(z.color)) {
        wrap.style.display = "none";
        return;
      }
      wrap.style.display = "flex";
      range.value = String(Math.round((z.r || 0.03) * 1000));
      val.textContent = `${((z.r || 0) * 100).toFixed(1)}%`;
    }

    function paintList() {
      const ul = root.querySelector("#roomZoneList");
      const items = [
        {
          id: "__view__",
          label: "Karte (weiß)",
          color: "white",
        },
        {
          id: "__camera__",
          label: `Kamera (schwarz) ${Math.round(cameraRect.w * 100)}×${Math.round(cameraRect.h * 100)}%`,
          color: "black",
        },
        ...zones.map((z, i) => ({
          id: z.id,
          label: `${i + 1}. ${zoneLabel(z)}`,
          color: z.color,
        })),
        ...portals.map((p, i) => ({
          id: p.id,
          label: `Portal ${i + 1}: ${p.label || p.targetRoomId || "?"}`,
          color: "purple",
        })),
        ...actions.map((a, i) => ({
          id: a.id,
          label: `Aktion ${i + 1}: ${a.label || a.actionType || "?"}`,
          color: "teal",
        })),
      ];
      ul.innerHTML = items
        .map(
          (it) => `<li class="${selectedId === it.id ? "sel" : ""}" data-zid="${esc(it.id)}">
          <span class="dot" style="background:${STROKE[it.color] || "#fff"}"></span>${esc(it.label)}
        </li>`
        )
        .join("");
      ul.querySelectorAll("[data-zid]").forEach((li) => {
        li.addEventListener("click", () => {
          selectedId = li.getAttribute("data-zid");
          paintList();
          updateRadiusUi();
          draw();
          root.querySelector("#roomDelete").disabled =
            selectedId === "__view__" ||
            selectedId === "__camera__" ||
            !selectedId;
        });
      });
      updateRadiusUi();
    }

    function layoutCanvas() {
      const maxW = fit.clientWidth || 640;
      const maxH = Math.min(window.innerHeight * 0.62, 720);
      if (!imgReady) {
        canvas.width = maxW;
        canvas.height = maxH;
        return;
      }
      const scale = Math.min(maxW / img.naturalWidth, maxH / img.naturalHeight);
      canvas.width = Math.max(1, Math.round(img.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(img.naturalHeight * scale));
      canvas.style.width = canvas.width + "px";
      canvas.style.height = canvas.height + "px";
    }

    function drawZoneShape(z, highlight, colorKey) {
      const col = colorKey || z.color;
      ctx.save();
      ctx.fillStyle = FILL[col] || "rgba(255,255,255,0.15)";
      ctx.strokeStyle = highlight ? "#fff" : STROKE[col] || "#fff";
      ctx.lineWidth = highlight ? 3 : col === "white" || col === "black" ? 3 : 2;
      if (z.shape === "circle") {
        const r = z.r * Math.min(canvas.width, canvas.height);
        ctx.beginPath();
        ctx.arc(z.cx * canvas.width, z.cy * canvas.height, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      } else {
        ctx.fillRect(z.x * canvas.width, z.y * canvas.height, z.w * canvas.width, z.h * canvas.height);
        ctx.strokeRect(z.x * canvas.width, z.y * canvas.height, z.w * canvas.width, z.h * canvas.height);
      }
      ctx.restore();
    }

    function drawLabeledRect(item, colorKey, highlight) {
      drawZoneShape({ shape: "rect", x: item.x, y: item.y, w: item.w, h: item.h }, highlight, colorKey);
      const label = String(item.label || "").trim();
      if (!label) return;
      ctx.save();
      ctx.fillStyle = "#ffffff";
      ctx.strokeStyle = "rgba(0,0,0,0.55)";
      ctx.lineWidth = 3;
      ctx.font = `600 ${Math.max(11, Math.round(canvas.width * 0.026))}px sans-serif`;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      const cx = (item.x + item.w / 2) * canvas.width;
      const cy = (item.y + item.h / 2) * canvas.height;
      const maxW = item.w * canvas.width * 0.9;
      ctx.strokeText(label, cx, cy, maxW);
      ctx.fillText(label, cx, cy, maxW);
      ctx.restore();
    }

    function draw() {
      layoutCanvas();
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      if (imgReady) ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      else {
        ctx.fillStyle = "#1a1520";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
      }
      drawZoneShape(
        { shape: "rect", ...viewRect },
        selectedId === "__view__",
        "white"
      );
      // Schwarz = Kameragröße (zur Orientierung zentriert in der Karte)
      const camW = Math.min(viewRect.w, Math.max(0.12, cameraRect.w || viewRect.w));
      const camH = Math.min(viewRect.h, Math.max(0.12, cameraRect.h || viewRect.h));
      drawZoneShape(
        {
          shape: "rect",
          x: viewRect.x + (viewRect.w - camW) / 2,
          y: viewRect.y + (viewRect.h - camH) / 2,
          w: camW,
          h: camH,
        },
        selectedId === "__camera__",
        "black"
      );
      zones.forEach((z) => drawZoneShape(z, z.id === selectedId));
      portals.forEach((p) => drawLabeledRect(p, "purple", p.id === selectedId));
      actions.forEach((a) => drawLabeledRect(a, "teal", a.id === selectedId));
      if (draft) {
        if (draft.color === "purple" || draft.color === "teal") {
          drawLabeledRect(draft, draft.color, true);
        } else {
          drawZoneShape(draft, true, draft.color);
        }
      }
    }

    function currentTool() {
      return TOOLS.find((t) => t.id === tool) || TOOLS[0];
    }

    function newId(color) {
      const prefix =
        color === "blue"
          ? "sit_"
          : color === "brown"
            ? "spawn_"
            : color === "orange"
              ? "avatar_"
              : color === "yellow"
                ? "altar_"
                : color === "gold"
                  ? "deco_"
                  : color === "pink"
                    ? "flame_"
                    : color === "lime"
                      ? "money_"
                      : color === "purple"
                        ? "portal_"
                        : color === "teal"
                          ? "action_"
                          : `${color}_`;
      return `${prefix}${Math.random().toString(36).slice(2, 8)}`;
    }

    async function pickPortalTarget(rect) {
      let data;
      try {
        data = await api(
          `/admin/room-layouts-link-targets?exclude=${encodeURIComponent(roomId)}`
        );
      } catch (e) {
        alert(e.message || "Ziele konnten nicht geladen werden");
        return;
      }
      const rooms = data.rooms || [];
      if (!rooms.length) {
        alert(
          "Keine Zielräume. Lege zuerst Räume an, die nicht über Plus-Kachel wählbar sind (nur Portal)."
        );
        return;
      }
      const lines = rooms.map((r, i) => `${i + 1}. ${r.name}`).join("\n");
      const ans = window.prompt(`Zielraum wählen (Nummer):\n${lines}`);
      if (ans == null || !String(ans).trim()) return;
      const idx = Number(String(ans).trim()) - 1;
      const room = rooms[idx];
      if (!room) {
        alert("Ungültige Nummer");
        return;
      }
      portals.push({
        id: newId("purple"),
        x: rect.x,
        y: rect.y,
        w: rect.w,
        h: rect.h,
        targetRoomId: room.id,
        label: room.name,
      });
      selectedId = portals[portals.length - 1].id;
      dirty = true;
    }

    async function pickActionType(rect) {
      let actionTypes = { cook: { label: "Kochen" } };
      try {
        const data = await api(
          `/admin/room-layouts-link-targets?exclude=${encodeURIComponent(roomId)}`
        );
        if (data.actionTypes && typeof data.actionTypes === "object") {
          actionTypes = data.actionTypes;
        }
      } catch (_) {
        /* Fallback cook */
      }
      const keys = Object.keys(actionTypes);
      if (!keys.length) {
        alert("Keine Aktionstypen verfügbar");
        return;
      }
      const lines = keys
        .map((k, i) => `${i + 1}. ${actionTypes[k].label || k}`)
        .join("\n");
      const ans = window.prompt(`Aktion wählen (Nummer):\n${lines}`);
      if (ans == null || !String(ans).trim()) return;
      const idx = Number(String(ans).trim()) - 1;
      const actionType = keys[idx];
      if (!actionType) {
        alert("Ungültige Nummer");
        return;
      }
      const def = actionTypes[actionType] || {};
      actions.push({
        id: newId("teal"),
        x: rect.x,
        y: rect.y,
        w: rect.w,
        h: rect.h,
        actionType,
        label: def.label || actionType,
      });
      selectedId = actions[actions.length - 1].id;
      dirty = true;
    }

    function norm(ev) {
      const rect = canvas.getBoundingClientRect();
      return {
        x: Math.min(1, Math.max(0, (ev.clientX - rect.left) / rect.width)),
        y: Math.min(1, Math.max(0, (ev.clientY - rect.top) / rect.height)),
      };
    }

    let drawing = false;
    let start = null;
    let draggingProp = null;
    let dragOff = null;

    function hitPropAt(p) {
      for (let i = zones.length - 1; i >= 0; i--) {
        const z = zones[i];
        if (z.shape !== "circle" || !PLACE_PROP_COLORS.has(z.color)) continue;
        if (Math.hypot(p.x - z.cx, p.y - z.cy) <= (z.r || 0.02) + 0.01) return z;
      }
      return null;
    }

    canvas.addEventListener("mousedown", (ev) => {
      const p = norm(ev);
      const t = currentTool();
      const hit = hitPropAt(p);

      // Bestehendes Prop: auswählen + ziehen (Place-Tool: Klick auf Prop = drag, leer = place)
      if (hit) {
        selectedId = hit.id;
        draggingProp = hit;
        dragOff = { x: p.x - hit.cx, y: p.y - hit.cy };
        drawing = false;
        draft = null;
        paintList();
        updateRadiusUi();
        draw();
        return;
      }

      // Click-to-place für Gold / Flamme / Geldbaum
      if (PLACE_PROP_COLORS.has(t.color)) {
        const z = {
          id: newId(t.color),
          color: t.color,
          shape: "circle",
          cx: p.x,
          cy: p.y,
          r: DEFAULT_PROP_R[t.color] || 0.03,
        };
        zones.push(z);
        selectedId = z.id;
        dirty = true;
        paintList();
        updateRadiusUi();
        setStatus(
          `${zones.length} Zonen · ${portals.length} Portale · ${actions.length} Aktionen`
        );
        draw();
        return;
      }

      drawing = true;
      start = p;
      if (tool === "link-portal") {
        draft = {
          id: newId("purple"),
          color: "purple",
          shape: "rect",
          x: start.x,
          y: start.y,
          w: 0.01,
          h: 0.01,
          label: "",
        };
      } else if (tool === "add-action") {
        draft = {
          id: newId("teal"),
          color: "teal",
          shape: "rect",
          x: start.x,
          y: start.y,
          w: 0.01,
          h: 0.01,
          label: "",
        };
      } else {
        draft = {
          id:
            t.id === "view-rect"
              ? "__view__"
              : t.id === "camera-rect"
                ? "__camera__"
                : newId(t.color),
          color: t.color,
          shape: t.shape,
          x: start.x,
          y: start.y,
          w: 0.01,
          h: 0.01,
          cx: start.x,
          cy: start.y,
          r: 0.01,
        };
      }
      draw();
    });
    canvas.addEventListener("mousemove", (ev) => {
      const p = norm(ev);
      if (draggingProp) {
        draggingProp.cx = Math.min(1, Math.max(0, p.x - (dragOff?.x || 0)));
        draggingProp.cy = Math.min(1, Math.max(0, p.y - (dragOff?.y || 0)));
        dirty = true;
        draw();
        return;
      }
      if (!drawing || !draft || !start) return;
      if (draft.shape === "circle") {
        draft.cx = start.x;
        draft.cy = start.y;
        draft.r = Math.min(0.45, Math.max(0.008, Math.hypot(p.x - start.x, p.y - start.y)));
      } else {
        draft.x = Math.min(start.x, p.x);
        draft.y = Math.min(start.y, p.y);
        draft.w = Math.max(0.02, Math.abs(p.x - start.x));
        draft.h = Math.max(0.02, Math.abs(p.y - start.y));
      }
      draw();
    });
    async function finish() {
      if (draggingProp) {
        draggingProp = null;
        dragOff = null;
        paintList();
        updateRadiusUi();
        setStatus(
          `${zones.length} Zonen · ${portals.length} Portale · ${actions.length} Aktionen`
        );
        draw();
        return;
      }
      if (!drawing) return;
      drawing = false;
      const d = draft;
      draft = null;
      start = null;
      if (d) {
        if (d.color === "purple" && d.w >= 0.02) {
          await pickPortalTarget({ x: d.x, y: d.y, w: d.w, h: d.h });
        } else if (d.color === "teal" && d.w >= 0.02) {
          await pickActionType({ x: d.x, y: d.y, w: d.w, h: d.h });
        } else if (d.id === "__view__" || d.color === "white") {
          viewRect = { x: d.x, y: d.y, w: d.w, h: d.h };
          selectedId = "__view__";
          dirty = true;
        } else if (d.id === "__camera__" || d.color === "black") {
          cameraRect = {
            w: Math.min(viewRect.w, Math.max(0.12, d.w)),
            h: Math.min(viewRect.h, Math.max(0.12, d.h)),
          };
          selectedId = "__camera__";
          dirty = true;
        } else if (d.shape === "circle" && d.r >= 0.008) {
          zones.push({
            id: d.id,
            color: d.color,
            shape: "circle",
            cx: d.cx,
            cy: d.cy,
            r: d.r,
          });
          dirty = true;
        } else if (d.shape === "rect" && d.w >= 0.02) {
          zones.push({
            id: d.id,
            color: d.color,
            shape: "rect",
            x: d.x,
            y: d.y,
            w: d.w,
            h: d.h,
          });
          dirty = true;
        }
        if (dirty && d.color !== "purple" && d.color !== "teal") {
          zones = mergeGreens(zones);
        }
      }
      paintList();
      updateRadiusUi();
      setStatus(
        `${zones.length} Zonen · ${portals.length} Portale · ${actions.length} Aktionen`
      );
      draw();
    }
    canvas.addEventListener("mouseup", () => {
      finish();
    });
    canvas.addEventListener("mouseleave", () => {
      finish();
    });

    root.querySelector("#roomBack").addEventListener("click", () => {
      if (dirty && !confirm("Verwerfen?")) return;
      editor = null;
      renderRooms();
    });
    const weddingSel = root.querySelector("#roomWeddingSwitch");
    if (weddingSel) {
      weddingSel.addEventListener("change", () => {
        const next = weddingSel.value;
        if (!next || next === roomId) return;
        if (dirty && !confirm("Ungespeicherte Änderungen verwerfen und Raum wechseln?")) {
          weddingSel.value = roomId;
          return;
        }
        editor = { roomId: next };
        renderRooms();
      });
    }
    const radiusRange = root.querySelector("#roomRadius");
    if (radiusRange) {
      radiusRange.addEventListener("input", () => {
        const z = zones.find((x) => x.id === selectedId && x.shape === "circle");
        if (!z) return;
        z.r = Math.min(0.12, Math.max(0.008, Number(radiusRange.value) / 1000));
        dirty = true;
        updateRadiusUi();
        paintList();
        draw();
      });
    }
    root.querySelector("#roomDelete").addEventListener("click", () => {
      if (!selectedId || selectedId === "__view__" || selectedId === "__camera__") return;
      const beforeZ = zones.length;
      const beforeP = portals.length;
      const beforeA = actions.length;
      zones = zones.filter((z) => z.id !== selectedId);
      portals = portals.filter((p) => p.id !== selectedId);
      actions = actions.filter((a) => a.id !== selectedId);
      if (
        zones.length === beforeZ &&
        portals.length === beforeP &&
        actions.length === beforeA
      ) {
        return;
      }
      selectedId = null;
      dirty = true;
      paintList();
      setStatus(
        `${zones.length} Zonen · ${portals.length} Portale · ${actions.length} Aktionen`
      );
      draw();
    });
    root.querySelector("#roomClearAll").addEventListener("click", () => {
      if (!confirm("Alle Zonen, Portale und Aktionen löschen?")) return;
      zones = [];
      portals = [];
      actions = [];
      selectedId = null;
      dirty = true;
      paintList();
      setStatus("Leer");
      draw();
    });
    const pickableEl = root.querySelector("#roomPickable");
    if (pickableEl) {
      pickableEl.addEventListener("change", () => {
        pickable = pickableEl.checked;
        dirty = true;
        setStatus(
          `${zones.length} Zonen · ${portals.length} Portale · ${actions.length} Aktionen`
        );
      });
    }
    const reFile = root.querySelector("#roomReFile");
    root.querySelector("#roomReimg").addEventListener("click", () => reFile.click());
    reFile.addEventListener("change", async () => {
      const f = reFile.files?.[0];
      if (!f) return;
      pendingImage = await fileToDataUrl(f);
      img.src = pendingImage;
      dirty = true;
      setStatus("Neues Bild");
    });
    const delBtn = root.querySelector("#roomDel");
    if (delBtn) {
      delBtn.addEventListener("click", async () => {
        if (!confirm("Raum wirklich löschen?")) return;
        await api(`/admin/room-layouts/${encodeURIComponent(roomId)}`, { method: "DELETE" });
        editor = null;
        renderRooms();
      });
    }
    root.querySelector("#roomSave").addEventListener("click", async () => {
      try {
        zones = mergeGreens(zones);
        if (pickableEl) pickable = pickableEl.checked;
        const body = {
          name: root.querySelector("#roomName").value.trim() || layout.name,
          zones,
          portals,
          actions,
          pickable,
          viewRect,
          cameraRect,
        };
        if (pendingImage) body.imageBase64 = pendingImage;
        const res = await api(`/admin/room-layouts/${encodeURIComponent(roomId)}`, {
          method: "PUT",
          body: JSON.stringify(body),
        });
        zones = mergeGreens((res.layout?.zones || zones).map((z) => ({ ...z })));
        portals = (res.layout?.portals || portals).map((p) => ({ ...p }));
        actions = (res.layout?.actions || actions).map((a) => ({ ...a }));
        if (res.layout?.pickable != null) pickable = res.layout.pickable !== false;
        if (pickableEl) pickableEl.checked = pickable;
        viewRect = res.layout?.viewRect || viewRect;
        if (res.layout?.cameraRect) {
          cameraRect = {
            w: res.layout.cameraRect.w,
            h: res.layout.cameraRect.h,
          };
        }
        pendingImage = null;
        dirty = false;
        setStatus(res.message || "Gespeichert");
        paintList();
        draw();
      } catch (e) {
        setStatus("Fehler: " + (e.message || e));
      }
    });

    paintTools();
    paintList();
    setStatus(
      zones.length || portals.length || actions.length
        ? `${zones.length} Zonen · ${portals.length} Portale · ${actions.length} Aktionen`
        : "Zonen / Portale / Aktionen einzeichnen"
    );
    window.addEventListener("resize", draw);
    img.onload = () => {
      imgReady = true;
      draw();
    };
    img.src = (pendingImage || layout.imageUrl) + (layout.imageUrl.includes("?") ? "" : `?v=${layout.updatedAt || Date.now()}`);
  }

  window.LuvAdmRooms = { renderRooms };
})();

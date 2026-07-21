/**
 * Admin: Räume — Layout-Editor (Hochzeit).
 * Zonen nur im Admin sichtbar; App nutzt sie unsichtbar.
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

  const TOOLS = [
    { id: "red-rect", label: "Rot eckig", color: "red", shape: "rect", hint: "Hindernis (Bänke) — drum herum" },
    { id: "green-rect", label: "Grün eckig", color: "green", shape: "rect", hint: "Nur hier laufen; Schnitt → ein Bereich" },
    { id: "yellow-circle", label: "Gelb rund", color: "yellow", shape: "circle", hint: "Sitz Eheleute" },
    { id: "blue-circle", label: "Blau rund", color: "blue", shape: "circle", hint: "Sitz (Tipp → hinlaufen & setzen)" },
    { id: "brown-circle", label: "Braun rund", color: "brown", shape: "circle", hint: "Spawn aller Avatare" },
    { id: "orange-circle", label: "Orange rund", color: "orange", shape: "circle", hint: "Avatar-Größe" },
  ];

  const FILL = {
    red: "rgba(229, 57, 53, 0.45)",
    green: "rgba(67, 160, 71, 0.4)",
    yellow: "rgba(255, 213, 79, 0.5)",
    blue: "rgba(66, 165, 245, 0.45)",
    brown: "rgba(141, 110, 99, 0.5)",
    orange: "rgba(255, 152, 0, 0.45)",
  };
  const STROKE = {
    red: "#e53935",
    green: "#43a047",
    yellow: "#ffd54f",
    blue: "#42a5f5",
    brown: "#8d6e63",
    orange: "#ff9800",
  };

  const LABEL = {
    red: "Rot Hindernis",
    green: "Grün laufen",
    yellow: "Gelb Eheleute",
    blue: "Blau Sitz",
    brown: "Braun Spawn",
    orange: "Orange Avatar-Größe",
  };

  let editor = null;

  function rectsOverlapOrTouch(a, b, eps = 0.002) {
    return !(
      a.x + a.w < b.x - eps ||
      b.x + b.w < a.x - eps ||
      a.y + a.h < b.y - eps ||
      b.y + b.h < a.y - eps
    );
  }

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
    const uni = (a, b) => {
      const ra = find(a);
      const rb = find(b);
      if (ra !== rb) parent[rb] = ra;
    };
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        if (rectsOverlapOrTouch(greens[i], greens[j])) uni(i, j);
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
      let x0 = Infinity;
      let y0 = Infinity;
      let x1 = -Infinity;
      let y1 = -Infinity;
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

  async function renderRooms() {
    const root = contentEl();
    if (!root) return;
    if (editor && editor.roomId) {
      await renderEditor(editor.roomId);
      return;
    }
    root.innerHTML = `<p class="muted">Lade Räume…</p>`;
    const data = await api("/admin/room-layouts");
    const rooms = data.rooms || [];
    root.innerHTML = `
      <div class="panel">
        <h3>Räume</h3>
        <p class="help">Bereiche selbst einzeichnen und speichern. In der App sind sie unsichtbar — nur Lauf-/Sitz-/Spawn-Logik.</p>
        <div class="room-list">
          ${rooms
            .map(
              (r) => `
            <button type="button" class="room-card" data-room="${esc(r.id)}">
              <span class="room-card-title">Raum: ${esc(r.name)}</span>
              <span class="muted">${r.zoneCount || 0} Bereiche${
                r.updatedAt
                  ? " · gespeichert " + new Date(r.updatedAt).toLocaleString("de-DE")
                  : " · leer"
              }</span>
            </button>`
            )
            .join("")}
        </div>
      </div>`;
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
      root.innerHTML = `<p class="error">Raum nicht gefunden.</p>`;
      return;
    }

    let zones = mergeGreens((layout.zones || []).map((z) => ({ ...z })));
    let tool = TOOLS[0].id;
    let selectedId = null;
    let draft = null;
    let dirty = false;

    root.innerHTML = `
      <div class="room-editor">
        <div class="room-editor-bar">
          <button type="button" class="btn ghost" id="roomBack">← Räume</button>
          <h3 style="margin:0">Raum: ${esc(layout.name)}</h3>
          <button type="button" class="btn" id="roomSave">Speichern</button>
        </div>
        <p class="help">
          Rot = Hindernis · Grün = laufen (Schnitt → ein Bereich) · Blau = Sitz · Gelb = Eheleute ·
          Braun = Spawn · Orange = Avatar-Größe. In der Hochzeit unsichtbar.
        </p>
        <div class="room-tools" id="roomTools"></div>
        <div class="room-editor-body">
          <div class="room-canvas-wrap">
            <canvas id="roomCanvas" width="900" height="1200"></canvas>
          </div>
          <div class="room-side">
            <p class="muted" id="roomStatus"></p>
            <h4>Bereiche</h4>
            <ul class="room-zone-list" id="roomZoneList"></ul>
            <button type="button" class="btn ghost danger" id="roomDelete" disabled>Ausgewählten löschen</button>
            <button type="button" class="btn ghost danger" id="roomClearAll" style="margin-top:0.5rem">Alle löschen</button>
          </div>
        </div>
      </div>`;

    const canvas = root.querySelector("#roomCanvas");
    const ctx = canvas.getContext("2d");
    const img = new Image();
    img.crossOrigin = "anonymous";
    let imgReady = false;

    function setStatus(msg) {
      const el = root.querySelector("#roomStatus");
      if (el) el.textContent = (msg || "") + (dirty ? " · ungespeichert" : "");
    }

    function paintTools() {
      const box = root.querySelector("#roomTools");
      box.innerHTML = TOOLS.map(
        (t) => `
        <button type="button" class="room-tool ${tool === t.id ? "active" : ""}" data-tool="${t.id}" title="${esc(t.hint)}">
          <span class="swatch" style="background:${STROKE[t.color]}"></span>
          ${esc(t.label)}
        </button>`
      ).join("");
      box.querySelectorAll("[data-tool]").forEach((b) => {
        b.addEventListener("click", () => {
          tool = b.getAttribute("data-tool");
          selectedId = null;
          paintTools();
          paintList();
          draw();
        });
      });
    }

    function paintList() {
      const ul = root.querySelector("#roomZoneList");
      ul.innerHTML = zones
        .map((z, i) => {
          const shape = z.shape === "circle" ? "rund" : "eckig";
          return `<li class="${selectedId === z.id ? "sel" : ""}" data-zid="${esc(z.id)}">
            <span class="dot" style="background:${STROKE[z.color] || "#fff"}"></span>
            ${i + 1}. ${LABEL[z.color] || z.color} (${shape}) <code>${esc(z.id)}</code>
          </li>`;
        })
        .join("");
      ul.querySelectorAll("[data-zid]").forEach((li) => {
        li.addEventListener("click", () => {
          selectedId = li.getAttribute("data-zid");
          paintList();
          draw();
          root.querySelector("#roomDelete").disabled = !selectedId;
        });
      });
      root.querySelector("#roomDelete").disabled = !selectedId;
    }

    function normFromEvent(ev) {
      const rect = canvas.getBoundingClientRect();
      return {
        x: Math.min(1, Math.max(0, (ev.clientX - rect.left) / rect.width)),
        y: Math.min(1, Math.max(0, (ev.clientY - rect.top) / rect.height)),
      };
    }

    function drawZone(z, highlight) {
      ctx.save();
      ctx.fillStyle = FILL[z.color] || "rgba(255,255,255,0.2)";
      ctx.strokeStyle = highlight ? "#fff" : STROKE[z.color] || "#fff";
      ctx.lineWidth = highlight ? 3 : 2;
      if (z.shape === "circle") {
        const cx = z.cx * canvas.width;
        const cy = z.cy * canvas.height;
        const r = z.r * Math.min(canvas.width, canvas.height);
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
      } else {
        ctx.fillRect(z.x * canvas.width, z.y * canvas.height, z.w * canvas.width, z.h * canvas.height);
        ctx.strokeRect(z.x * canvas.width, z.y * canvas.height, z.w * canvas.width, z.h * canvas.height);
      }
      ctx.restore();
    }

    function draw() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      if (imgReady) ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      else {
        ctx.fillStyle = "#1a1520";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
      }
      zones.forEach((z) => drawZone(z, z.id === selectedId));
      if (draft) drawZone(draft, true);
    }

    function currentTool() {
      return TOOLS.find((t) => t.id === tool) || TOOLS[0];
    }

    function newId(color) {
      const prefix =
        color === "yellow"
          ? "altar_"
          : color === "blue"
            ? "sit_"
            : color === "brown"
              ? "spawn_"
              : color === "orange"
                ? "avatar_"
                : `${color}_`;
      return `${prefix}${Math.random().toString(36).slice(2, 8)}`;
    }

    let drawing = false;
    let start = null;

    canvas.addEventListener("mousedown", (ev) => {
      drawing = true;
      start = normFromEvent(ev);
      selectedId = null;
      const t = currentTool();
      draft = {
        id: newId(t.color),
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
      paintList();
      draw();
    });

    canvas.addEventListener("mousemove", (ev) => {
      if (!drawing || !draft || !start) return;
      const p = normFromEvent(ev);
      if (draft.shape === "circle") {
        draft.cx = start.x;
        draft.cy = start.y;
        const minR = draft.color === "orange" ? 0.008 : 0.012;
        draft.r = Math.min(0.45, Math.max(minR, Math.hypot(p.x - start.x, p.y - start.y)));
      } else {
        draft.x = Math.min(start.x, p.x);
        draft.y = Math.min(start.y, p.y);
        draft.w = Math.max(0.015, Math.abs(p.x - start.x));
        draft.h = Math.max(0.015, Math.abs(p.y - start.y));
      }
      draw();
    });

    function finishDraw() {
      if (!drawing) return;
      drawing = false;
      if (draft) {
        const minR = draft.color === "orange" ? 0.008 : 0.012;
        if (draft.shape === "circle" && draft.r >= minR) {
          zones.push({
            id: draft.id,
            color: draft.color,
            shape: "circle",
            cx: draft.cx,
            cy: draft.cy,
            r: draft.r,
          });
          selectedId = draft.id;
          dirty = true;
        } else if (draft.shape === "rect" && draft.w >= 0.015 && draft.h >= 0.015) {
          zones.push({
            id: draft.id,
            color: draft.color,
            shape: "rect",
            x: draft.x,
            y: draft.y,
            w: draft.w,
            h: draft.h,
          });
          selectedId = draft.id;
          dirty = true;
        }
        if (dirty) {
          zones = mergeGreens(zones);
          selectedId = zones.find((z) => z.id === selectedId)?.id || selectedId;
        }
      }
      draft = null;
      start = null;
      paintList();
      setStatus(`${zones.length} Bereiche`);
      draw();
    }

    canvas.addEventListener("mouseup", finishDraw);
    canvas.addEventListener("mouseleave", finishDraw);

    root.querySelector("#roomBack").addEventListener("click", () => {
      if (dirty && !confirm("Ungespeicherte Änderungen verwerfen?")) return;
      editor = null;
      renderRooms();
    });

    root.querySelector("#roomDelete").addEventListener("click", () => {
      if (!selectedId) return;
      zones = zones.filter((z) => z.id !== selectedId);
      selectedId = null;
      dirty = true;
      paintList();
      setStatus(`${zones.length} Bereiche`);
      draw();
    });

    root.querySelector("#roomClearAll").addEventListener("click", () => {
      if (!zones.length) return;
      if (!confirm("Wirklich alle Bereiche löschen?")) return;
      zones = [];
      selectedId = null;
      dirty = true;
      paintList();
      setStatus("Leer — speichern, damit die App das übernimmt");
      draw();
    });

    root.querySelector("#roomSave").addEventListener("click", async () => {
      try {
        zones = mergeGreens(zones);
        const res = await api(`/admin/room-layouts/${encodeURIComponent(roomId)}`, {
          method: "PUT",
          body: JSON.stringify({ zones }),
        });
        zones = mergeGreens((res.layout?.zones || zones).map((z) => ({ ...z })));
        dirty = false;
        setStatus(res.message || "Gespeichert — gilt sofort im Trausaal (unsichtbar)");
        paintList();
        draw();
      } catch (e) {
        setStatus("Fehler: " + (e.message || e));
      }
    });

    paintTools();
    paintList();
    setStatus(zones.length ? `${zones.length} Bereiche` : "Leer — Bereiche selbst einzeichnen");
    draw();

    img.onload = () => {
      imgReady = true;
      const maxW = 900;
      const scale = maxW / img.naturalWidth;
      canvas.width = maxW;
      canvas.height = Math.round(img.naturalHeight * scale);
      draw();
    };
    img.onerror = () => {
      setStatus("Bild konnte nicht geladen werden — Zonen trotzdem editierbar");
      draw();
    };
    img.src = layout.imageUrl + "?v=" + (layout.updatedAt || Date.now());
  }

  window.LuvAdmRooms = { renderRooms };
})();

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

  const TOOLS = [
    { id: "view-rect", label: "Weiß Karte", color: "white", shape: "rect", hint: "Gesamte Lauffläche" },
    { id: "camera-rect", label: "Schwarz Kamera", color: "black", shape: "rect", hint: "Was auf dem Handy sichtbar ist" },
    { id: "red-rect", label: "Rot", color: "red", shape: "rect", hint: "Hindernis" },
    { id: "green-rect", label: "Grün", color: "green", shape: "rect", hint: "Laufen" },
    { id: "blue-circle", label: "Blau Sitz", color: "blue", shape: "circle", hint: "Hinsetzen" },
    { id: "brown-circle", label: "Braun Spawn", color: "brown", shape: "circle", hint: "Spawn" },
    { id: "orange-circle", label: "Orange Größe", color: "orange", shape: "circle", hint: "Avatar" },
    { id: "yellow-circle", label: "Gelb Ehe", color: "yellow", shape: "circle", hint: "Nur Hochzeit" },
  ];

  const FILL = {
    white: "rgba(255,255,255,0.12)",
    black: "rgba(0,0,0,0.08)",
    red: "rgba(229,57,53,0.4)",
    green: "rgba(67,160,71,0.35)",
    yellow: "rgba(255,213,79,0.45)",
    blue: "rgba(66,165,245,0.45)",
    brown: "rgba(141,110,99,0.45)",
    orange: "rgba(255,152,0,0.4)",
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
              }${r.builtin ? " · Hochzeit" : ""}</span>
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

    root.innerHTML = `
      <div class="room-editor">
        <div class="room-editor-bar">
          <button type="button" class="btn ghost" id="roomBack">← Räume</button>
          <input id="roomName" value="${esc(layout.name)}" style="flex:1;min-width:8rem" />
          <button type="button" class="btn" id="roomSave">Speichern</button>
          ${
            layout.builtin
              ? ""
              : `<button type="button" class="btn ghost danger" id="roomDel">Löschen</button>`
          }
        </div>
        <p class="help">Weiß = ganze Karte · Schwarz = Kamera-Fenster (Handy zeigt nur das; am Rand scrollt die Karte) · Grün/Rot/Blau = Logik (unsichtbar).</p>
        <div class="room-tools" id="roomTools"></div>
        <div class="room-fit-wrap" id="roomFit">
          <canvas id="roomCanvas"></canvas>
        </div>
        <div class="room-side" style="margin-top:0.75rem">
          <p class="muted" id="roomStatus"></p>
          <ul class="room-zone-list" id="roomZoneList"></ul>
          <button type="button" class="btn ghost danger" id="roomDelete" disabled>Auswahl löschen</button>
          <button type="button" class="btn ghost danger" id="roomClearAll" style="margin-top:0.4rem">Alle Zonen löschen</button>
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
      const tools =
        roomId === "wedding" ? TOOLS : TOOLS.filter((t) => t.color !== "yellow");
      box.innerHTML = tools
        .map(
          (t) => `
        <button type="button" class="room-tool ${tool === t.id ? "active" : ""}" data-tool="${t.id}">
          <span class="swatch" style="background:${STROKE[t.color]}"></span>${esc(t.label)}
        </button>`
        )
        .join("");
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
          label: `${i + 1}. ${z.color} ${z.shape}`,
          color: z.color,
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
          draw();
          root.querySelector("#roomDelete").disabled =
            selectedId === "__view__" ||
            selectedId === "__camera__" ||
            !selectedId;
        });
      });
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
      if (draft) drawZoneShape(draft, true, draft.color);
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
                : `${color}_`;
      return `${prefix}${Math.random().toString(36).slice(2, 8)}`;
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

    canvas.addEventListener("mousedown", (ev) => {
      drawing = true;
      start = norm(ev);
      const t = currentTool();
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
      draw();
    });
    canvas.addEventListener("mousemove", (ev) => {
      if (!drawing || !draft || !start) return;
      const p = norm(ev);
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
    function finish() {
      if (!drawing) return;
      drawing = false;
      if (draft) {
        if (draft.id === "__view__" || draft.color === "white") {
          viewRect = { x: draft.x, y: draft.y, w: draft.w, h: draft.h };
          selectedId = "__view__";
          dirty = true;
        } else if (draft.id === "__camera__" || draft.color === "black") {
          cameraRect = {
            w: Math.min(viewRect.w, Math.max(0.12, draft.w)),
            h: Math.min(viewRect.h, Math.max(0.12, draft.h)),
          };
          selectedId = "__camera__";
          dirty = true;
        } else if (draft.shape === "circle" && draft.r >= 0.008) {
          zones.push({
            id: draft.id,
            color: draft.color,
            shape: "circle",
            cx: draft.cx,
            cy: draft.cy,
            r: draft.r,
          });
          dirty = true;
        } else if (draft.shape === "rect" && draft.w >= 0.02) {
          zones.push({
            id: draft.id,
            color: draft.color,
            shape: "rect",
            x: draft.x,
            y: draft.y,
            w: draft.w,
            h: draft.h,
          });
          dirty = true;
        }
        if (dirty) zones = mergeGreens(zones);
      }
      draft = null;
      start = null;
      paintList();
      setStatus(`${zones.length} Zonen`);
      draw();
    }
    canvas.addEventListener("mouseup", finish);
    canvas.addEventListener("mouseleave", finish);

    root.querySelector("#roomBack").addEventListener("click", () => {
      if (dirty && !confirm("Verwerfen?")) return;
      editor = null;
      renderRooms();
    });
    root.querySelector("#roomDelete").addEventListener("click", () => {
      if (!selectedId || selectedId === "__view__" || selectedId === "__camera__") return;
      zones = zones.filter((z) => z.id !== selectedId);
      selectedId = null;
      dirty = true;
      paintList();
      setStatus(`${zones.length} Zonen`);
      draw();
    });
    root.querySelector("#roomClearAll").addEventListener("click", () => {
      if (!confirm("Alle Zonen löschen?")) return;
      zones = [];
      dirty = true;
      paintList();
      setStatus("Leer");
      draw();
    });
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
        const body = {
          name: root.querySelector("#roomName").value.trim() || layout.name,
          zones,
          viewRect,
          cameraRect,
        };
        if (pendingImage) body.imageBase64 = pendingImage;
        const res = await api(`/admin/room-layouts/${encodeURIComponent(roomId)}`, {
          method: "PUT",
          body: JSON.stringify(body),
        });
        zones = mergeGreens((res.layout?.zones || zones).map((z) => ({ ...z })));
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
    setStatus(zones.length ? `${zones.length} Zonen` : "Zonen einzeichnen");
    window.addEventListener("resize", draw);
    img.onload = () => {
      imgReady = true;
      draw();
    };
    img.src = (pendingImage || layout.imageUrl) + (layout.imageUrl.includes("?") ? "" : `?v=${layout.updatedAt || Date.now()}`);
  }

  window.LuvAdmRooms = { renderRooms };
})();

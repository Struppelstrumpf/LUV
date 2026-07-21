/**
 * LUV Admin — Shop Studio: Chromakey-Pipette, Glyph-Composer, Theme-Wizard, Emoji-Katalog.
 */
(() => {
  const ALL_EMOJIS = [
    // Smileys
    "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
    "🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢","🫣","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥",
    "😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵",
    "🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧",
    "😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀",
    "☠️","💩","🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾",
    // Gesten
    "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫳","🫴","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉",
    "👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅",
    "🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁","🦷","🦴","👀","👁️","👅","👄","🫦","💋",
    // Herzen & Symbole
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","♥️",
    "❤️‍🔥","❤️‍🩹","💋","💯","💢","💥","💫","💦","💨","🕳️","💣","💬","👁️‍🗨️","🗨️","🗯️","💭","💤","✨","⭐","🌟",
    "💫","⚡","🔥","❄️","🌈","☀️","🌤️","⛅","🌥️","☁️","🌦️","🌧️","⛈️","🌩️","🌨️","☃️","⛄","🌬️","💨","🌪️",
    "🌫️","🌊","💧","☔","☂️","🌙","🌚","🌛","🌜","🌝","🌞","🪐","⭐","🌟","✨","☄️","🌠","🌌","🌍","🌎","🌏",
    // Natur & Tiere
    "🌸","💮","🏵️","🌹","🥀","🌺","🌻","🌼","🌷","🌱","🪴","🌲","🌳","🌴","🌵","🌾","🌿","☘️","🍀","🍁",
    "🍂","🍃","🪺","🪹","🍄","🐚","🪸","🪨","🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨","🐯","🦁",
    "🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🐣","🐥","🦆","🦅","🦉","🦇","🐺","🐗",
    "🐴","🦄","🐝","🪱","🐛","🦋","🐌","🐞","🐜","🪰","🪲","🪳","🦟","🦗","🕷️","🕸️","🦂","🐢","🐍","🦎",
    "🦖","🦕","🐙","🦑","🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆","🦓","🦍","🦧",
    "🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐","🦌","🐕",
    "🐩","🦮","🐕‍🦺","🐈","🐈‍⬛","🪶","🪽","🦇","🐻","🦝","🦨","🦡","🦫","🦦","🦥","🐁","🐀","🐿️","🦔","🐾",
    "🐉","🐲","🌵","🎄","🎋","🎍",
    // Essen
    "🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦",
    "🥬","🥒","🌶️","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈",
    "🥞","🧇","🥓","🥩","🍗","🍖","🦴","🌭","🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯","🫔","🥗","🥘",
    "🫕","🥫","🍝","🍜","🍲","🍛","🍣","🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠","🥮","🍢","🍡","🍧",
    "🍨","🍦","🥧","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🍯","🥛","🍼","🫖","☕",
    "🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷","🥃","🍸","🍹","🧉","🍾","🧊",
    // Aktivitäten & Objekte
    "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳",
    "🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸️","🥌","🎿","⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️",
    "🤺","🤾","🏌️","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚴","🚵","🏆","🥇","🥈","🥉","🏅","🎖️","🏵️","🎗️",
    "🎫","🎟️","🎪","🤹","🎭","🩰","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🪘","🎷","🎺","🪗","🎸","🪕","🎻",
    "🎲","♟️","🎯","🎳","🎮","🎰","🧩","🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛",
    "🚜","🏍️","🛵","🚲","🛴","🚏","🛣️","🛤️","🛢️","⛽","🚨","🚥","🚦","🛑","🚧","⚓","🛟","⛵","🛶","🚤",
    "🛥️","🛳️","⛴️","🚢","✈️","🛩️","🛫","🛬","🪂","💺","🚁","🚟","🚠","🚡","🛰️","🚀","🛸","🛎️","🧳","⌛",
    "⏳","⌚","⏰","⏱️","⏲️","🕰️","🌡️","🗺️","🧭","🎃","🎄","🎆","🎇","🧨","✨","🎈","🎉","🎊","🎋","🎍",
    "🎎","🎏","🎐","🎑","🧧","🎀","🎁","🎗️","🎟️","🎫","🎖️","🏆","🏅","🥇","⚽","🏀","🎯","🎮","🕹️","🎲",
    // Flags & misc useful
    "🏳️","🏴","🏁","🚩","🎌","🏳️‍🌈","🏳️‍⚧️","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝",
    "💋","💌","💎","💍","💐","💒","⛪","🕌","🛕","🕍","⛩️","🕋","⛲","⛺","🌁","🌃","🏙️","🌄","🌅","🌆",
    "🌇","🌉","♨️","🎠","🛝","🎡","🎢","💈","🎪","🚂","🚃","🚄","🚅","🚆","🚇","🚈","🚉","🚊","🚝","🚞",
    "🚋","🚌","🚍","🚎","🚐","🚑","🚒","🚓","🚔","🚕","🚖","🚗","🚘","🚙","🚚","🚛","🚜","🏎️","🏍️","🛵",
    "🦽","🦼","🛺","🚲","🛴","🛹","🛼","🚏","🛣️","🛤️","🛢️","⛽","🛞","🚨","🚥","🚦","🛑","🚧","⚓","🛟",
    "🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯","🏰","💒","🗼","🗽","⛪","🕌",
    "🛕","🕍","⛩️","🕋","⛲","⛺","🌁","🌃","🏙️","🌄","🌅","🌆","🌇","🌉","🎠","🎡","🎢","💈","🎪",
  ];

  function uniqueEmojis(list) {
    const seen = new Set();
    const out = [];
    for (const e of list) {
      if (!e || seen.has(e)) continue;
      seen.add(e);
      out.push(e);
    }
    return out;
  }

  const EMOJI_LIST = uniqueEmojis(ALL_EMOJIS);

  function notifyStudioDismiss() {
    try {
      document.dispatchEvent(new CustomEvent("luv-studio-dismiss"));
    } catch (_) {
      /* ignore */
    }
  }

  /**
   * Overlay schließen ohne Ghost-Click auf Wizard (Abbrechen / Backdrop).
   * Wichtig: NIEMALS pointer-events:none — sonst fällt der Click/Touch durch.
   * Stattdessen unsichtbar lassen, Events weiter schlucken, dann entfernen.
   */
  function dismissOverlay(layer) {
    if (!layer) return;
    notifyStudioDismiss();
    try {
      layer.style.background = "transparent";
      layer.querySelectorAll(".pet-paste-card").forEach((card) => {
        card.style.visibility = "hidden";
      });
    } catch (_) {
      /* ignore */
    }
    const absorb = (e) => {
      e.preventDefault();
      e.stopPropagation();
    };
    [
      "pointerdown",
      "pointerup",
      "mousedown",
      "mouseup",
      "click",
      "touchstart",
      "touchend",
    ].forEach((t) => layer.addEventListener(t, absorb, true));
    // ~300ms Ghost-Click auf Mobile abfangen
    setTimeout(() => {
      try {
        layer.remove();
      } catch (_) {
        /* ignore */
      }
    }, 420);
  }

  /** Overlay schließen, Callback erst nach dem aktuellen Click-Zyklus. */
  function completeOverlay(layer, doneFn) {
    dismissOverlay(layer);
    if (typeof doneFn !== "function") return;
    setTimeout(() => {
      try {
        doneFn();
      } catch (err) {
        console.error(err);
      }
    }, 0);
  }

  function wireOverlayShield(layer) {
    if (!layer || layer.dataset.shielded === "1") return;
    layer.dataset.shielded = "1";
    // Verhindert Click-through auf den Wizard-Modal darunter
    const stop = (e) => e.stopPropagation();
    layer.addEventListener("pointerdown", stop);
    layer.addEventListener("mousedown", stop);
    layer.addEventListener("mouseup", stop);
    layer.addEventListener("click", stop);
  }

  /** Stabile Bild-Item-ID (nie img_new / Platzhalter). */
  function newImageItemId() {
    const bytes = new Uint8Array(4);
    if (crypto && crypto.getRandomValues) crypto.getRandomValues(bytes);
    else for (let i = 0; i < 4; i++) bytes[i] = (Math.random() * 256) | 0;
    return (
      "img_" +
      Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("")
    );
  }

  function isStableImageItemId(id) {
    return /^img_[a-z0-9]{6,28}$/i.test(String(id || "").trim()) &&
      String(id || "").trim().toLowerCase() !== "img_new";
  }

  function hexToRgb(hex) {
    const h = String(hex || "").replace("#", "").trim();
    if (h.length === 3) {
      return {
        r: parseInt(h[0] + h[0], 16),
        g: parseInt(h[1] + h[1], 16),
        b: parseInt(h[2] + h[2], 16),
      };
    }
    if (h.length >= 6) {
      return {
        r: parseInt(h.slice(0, 2), 16),
        g: parseInt(h.slice(2, 4), 16),
        b: parseInt(h.slice(4, 6), 16),
      };
    }
    return { r: 0, g: 255, b: 0 };
  }

  function rgbToHex(r, g, b) {
    const c = (n) => Math.max(0, Math.min(255, n | 0)).toString(16).padStart(2, "0");
    return `#${c(r)}${c(g)}${c(b)}`.toUpperCase();
  }

  function applyChromaKey(ctx, w, h, keyHex, thresh = 95) {
    const { r: keyR, g: keyG, b: keyB } = hexToRgb(keyHex);
    const data = ctx.getImageData(0, 0, w, h);
    const px = data.data;
    for (let i = 0; i < px.length; i += 4) {
      const r = px[i];
      const g = px[i + 1];
      const b = px[i + 2];
      const dist = Math.hypot(r - keyR, g - keyG, b - keyB);
      // Extra-Hilfe nur wenn Key selbst grün-dominiert ist
      const keyIsGreen = keyG > keyR + 30 && keyG > keyB + 30;
      const greenBias =
        keyIsGreen && g > 160 && g > r * 1.25 && g > b * 1.25;
      if (dist < thresh || greenBias) {
        const soft = Math.max(0, 1 - dist / (thresh + 45));
        px[i + 3] = Math.floor(px[i + 3] * (1 - soft));
      }
    }
    ctx.putImageData(data, 0, 0);
  }

  /**
   * Bild-Editor mit Pipette + Crop. onDone(dataUrl).
   */
  function openChromaImageEditor(onDone, opts = {}) {
    const title = opts.title || "Bild freistellen";
    let keyHex = (opts.keyHex || "#00FF00").toUpperCase();
    let thresh = Number(opts.thresh) || 95;
    let pickMode = false;

    const prev = document.getElementById("petPasteLayer");
    if (prev) prev.remove();
    const layer = document.createElement("div");
    layer.id = "petPasteLayer";
    layer.className = "pet-paste-layer";
    layer.innerHTML = `
      <div class="pet-paste-card">
        <h3 style="font-family:var(--display);margin:0 0 0.35rem">${title}</h3>
        <p class="help" style="margin:0 0 0.6rem">
          Bild einfügen (Strg+V / Datei). Mit der <strong>Pipette</strong> eine Farbe im Bild wählen — die wird transparent.
        </p>
        <div class="chroma-toolbar">
          <div class="chroma-swatch" id="chromaSwatch"><i id="chromaDot"></i> Key: <strong id="chromaHex">${keyHex}</strong></div>
          <button type="button" class="btn secondary" id="chromaPick">🔬 Pipette</button>
          <label class="field inline-field">Toleranz
            <input type="range" id="chromaThresh" min="40" max="160" value="${thresh}" />
            <span id="chromaThreshVal">${thresh}</span>
          </label>
        </div>
        <div class="pet-dropzone" id="petDrop" style="margin-top:0.75rem" tabindex="0">
          Hier klicken, dann <strong>Strg+V</strong> — oder Datei ablegen / wählen
          <div style="margin-top:0.6rem">
            <input type="file" id="petFile" accept="image/png,image/jpeg,image/jpg,image/webp,image/gif,image/*" />
          </div>
        </div>
        <div class="pet-paste-stage" id="petStage" hidden>
          <canvas id="petCanvas" width="512" height="512"></canvas>
          <div class="crop" id="petCrop"></div>
        </div>
        <p class="help" id="petCropHint" hidden>Ziehen = Ausschnitt · Scroll = Zoom · Pipette = Farbe tippen</p>
        <div class="pet-preview-row" id="petPrevRow" hidden>
          <div class="pet-preview-circle"><img id="petPrevImg" alt="Vorschau" /></div>
          <div class="muted">So erscheint es im Avatar / Shop</div>
        </div>
        <div class="actions" style="margin-top:1rem">
          <button type="button" class="btn teal" id="petAccept" disabled>Übernehmen</button>
          <button type="button" class="btn ghost" id="petCancel">Abbrechen</button>
        </div>
      </div>`;
    document.body.appendChild(layer);
    wireOverlayShield(layer);

    const canvas = layer.querySelector("#petCanvas");
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    const stage = layer.querySelector("#petStage");
    const cropEl = layer.querySelector("#petCrop");
    const drop = layer.querySelector("#petDrop");
    const prevRow = layer.querySelector("#petPrevRow");
    const prevImg = layer.querySelector("#petPrevImg");
    const acceptBtn = layer.querySelector("#petAccept");
    const hint = layer.querySelector("#petCropHint");
    const chromaDot = layer.querySelector("#chromaDot");
    const chromaHexEl = layer.querySelector("#chromaHex");
    const pickBtn = layer.querySelector("#chromaPick");
    let sourceImg = null;
    let crop = { x: 0.1, y: 0.1, s: 0.8 };
    let dragging = null;

    function syncSwatch() {
      chromaDot.style.background = keyHex;
      chromaHexEl.textContent = keyHex;
      pickBtn.classList.toggle("on", pickMode);
      stage.classList.toggle("pick-mode", pickMode);
    }
    syncSwatch();

    function close() {
      window.removeEventListener("paste", onPaste);
      dismissOverlay(layer);
    }
    layer.querySelector("#petCancel").onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      close();
    };

    function redrawKeyed() {
      if (!sourceImg) return;
      const w = 512;
      const h = 512;
      canvas.width = w;
      canvas.height = h;
      ctx.clearRect(0, 0, w, h);
      const scale = Math.min(w / sourceImg.width, h / sourceImg.height);
      const dw = sourceImg.width * scale;
      const dh = sourceImg.height * scale;
      const dx = (w - dw) / 2;
      const dy = (h - dh) / 2;
      ctx.drawImage(sourceImg, dx, dy, dw, dh);
      applyChromaKey(ctx, w, h, keyHex, thresh);
      refreshPreview();
    }

    function layoutCrop() {
      cropEl.style.left = `${crop.x * 100}%`;
      cropEl.style.top = `${crop.y * 100}%`;
      cropEl.style.width = `${crop.s * 100}%`;
      cropEl.style.height = `${crop.s * 100}%`;
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
      if (!fileOrBlob) {
        alert("Kein Bild gewählt.");
        return;
      }
      const applyImg = (img) => {
        sourceImg = img;
        stage.hidden = false;
        hint.hidden = false;
        drop.classList.add("on");
        crop = { x: 0.12, y: 0.12, s: 0.76 };
        layoutCrop();
        redrawKeyed();
      };
      const fromDataUrl = (dataUrl) =>
        new Promise((resolve, reject) => {
          const img = new Image();
          img.onload = () => resolve(img);
          img.onerror = () => reject(new Error("decode"));
          img.src = dataUrl;
        });
      const fromBlobBitmap = async () => {
        if (typeof createImageBitmap !== "function") throw new Error("no_cib");
        const bmp = await createImageBitmap(fileOrBlob);
        const c = document.createElement("canvas");
        c.width = bmp.width;
        c.height = bmp.height;
        c.getContext("2d").drawImage(bmp, 0, 0);
        try {
          bmp.close?.();
        } catch (_) {
          /* ignore */
        }
        const img = new Image();
        await new Promise((resolve, reject) => {
          img.onload = resolve;
          img.onerror = reject;
          img.src = c.toDataURL("image/png");
        });
        return img;
      };
      const reader = new FileReader();
      reader.onerror = async () => {
        try {
          applyImg(await fromBlobBitmap());
        } catch {
          alert("Bild konnte nicht geladen werden. Bitte PNG, JPG oder WebP nutzen (kein HEIC).");
        }
      };
      reader.onload = async () => {
        const dataUrl = String(reader.result || "");
        try {
          if (dataUrl.startsWith("data:image/") || dataUrl.startsWith("data:application/octet-stream")) {
            // octet-stream: manch. Handy-Picks — trotzdem versuchen
            const fixed = dataUrl.startsWith("data:application/")
              ? dataUrl.replace(/^data:application\/octet-stream/, "data:image/jpeg")
              : dataUrl;
            applyImg(await fromDataUrl(fixed));
            return;
          }
          applyImg(await fromBlobBitmap());
        } catch {
          try {
            applyImg(await fromBlobBitmap());
          } catch {
            alert("Bild konnte nicht geladen werden. Bitte PNG, JPG oder WebP nutzen (kein HEIC).");
          }
        }
      };
      reader.readAsDataURL(fileOrBlob);
    }

    function sampleKeyAtClient(clientX, clientY) {
      const rect = canvas.getBoundingClientRect();
      const x = Math.floor(((clientX - rect.left) / rect.width) * canvas.width);
      const y = Math.floor(((clientY - rect.top) / rect.height) * canvas.height);
      if (x < 0 || y < 0 || x >= canvas.width || y >= canvas.height) return;
      // Sample from unkeyed source
      const tmp = document.createElement("canvas");
      tmp.width = canvas.width;
      tmp.height = canvas.height;
      const tctx = tmp.getContext("2d", { willReadFrequently: true });
      const scale = Math.min(tmp.width / sourceImg.width, tmp.height / sourceImg.height);
      const dw = sourceImg.width * scale;
      const dh = sourceImg.height * scale;
      const dx = (tmp.width - dw) / 2;
      const dy = (tmp.height - dh) / 2;
      tctx.drawImage(sourceImg, dx, dy, dw, dh);
      const px = tctx.getImageData(x, y, 1, 1).data;
      keyHex = rgbToHex(px[0], px[1], px[2]);
      pickMode = false;
      syncSwatch();
      redrawKeyed();
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

    pickBtn.onclick = () => {
      if (!sourceImg) {
        alert("Zuerst ein Bild laden.");
        return;
      }
      pickMode = !pickMode;
      syncSwatch();
    };
    layer.querySelector("#chromaThresh").oninput = (e) => {
      thresh = Number(e.target.value) || 95;
      layer.querySelector("#chromaThreshVal").textContent = String(thresh);
      redrawKeyed();
    };

    stage.addEventListener("pointerdown", (e) => {
      if (!sourceImg) return;
      if (pickMode) {
        e.preventDefault();
        sampleKeyAtClient(e.clientX, e.clientY);
        return;
      }
      const rect = stage.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width;
      const y = (e.clientY - rect.top) / rect.height;
      dragging = { ox: x - crop.x, oy: y - crop.y };
      stage.setPointerCapture(e.pointerId);
    });
    stage.addEventListener("pointermove", (e) => {
      if (!dragging || pickMode) return;
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
    stage.addEventListener(
      "wheel",
      (e) => {
        if (!sourceImg || pickMode) return;
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
      },
      { passive: false }
    );

    acceptBtn.onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      const dataUrl = exportPng();
      if (!dataUrl) return;
      window.removeEventListener("paste", onPaste);
      completeOverlay(layer, () => onDone(dataUrl, { keyHex, thresh }));
    };
  }

  /**
   * Glyph-Composer: mehrere Emojis + optional Bilder → PNG.
   */
  function openGlyphComposer(onDone, opts = {}) {
    const title = opts.title || "Eigenes Emoji erstellen";
    const layers = []; // {type, emoji?, dataUrl?, x, y, scale, rotation, flipX, flipY}
    let selected = -1;

    const prev = document.getElementById("glyphComposerLayer");
    if (prev) prev.remove();
    const layer = document.createElement("div");
    layer.id = "glyphComposerLayer";
    layer.className = "pet-paste-layer";
    layer.innerHTML = `
      <div class="pet-paste-card wide-card">
        <h3 style="font-family:var(--display);margin:0 0 0.35rem">${title}</h3>
        <p class="help" style="margin:0 0 0.75rem">
          Emojis kombinieren, drehen, spiegeln und/oder Bilder freistellen. Ergebnis wird als transparentes PNG gespeichert.
        </p>
        <div class="glyph-layout">
          <div class="glyph-stage-wrap">
            <canvas id="glyphCanvas" width="256" height="256"></canvas>
            <div class="pet-preview-circle glyph-live-prev"><img id="glyphPrev" alt="" /></div>
          </div>
          <div class="glyph-side">
            <div class="actions" style="margin:0 0 0.65rem">
              <button type="button" class="btn teal round-plus" id="glyphAddEmoji" title="Emoji hinzufügen">＋</button>
              <button type="button" class="btn secondary" id="glyphAddImage">Bild freistellen</button>
              <button type="button" class="btn ghost" id="glyphRemove" disabled>Entfernen</button>
            </div>
            <div id="glyphLayerList" class="glyph-layer-list"></div>
            <div id="glyphControls" class="glyph-controls" hidden>
              <label class="field">Größe <input type="range" id="glyphScale" min="0.25" max="1.6" step="0.05" value="1" /></label>
              <label class="field">X <input type="range" id="glyphX" min="0" max="1" step="0.01" value="0.5" /></label>
              <label class="field">Y <input type="range" id="glyphY" min="0" max="1" step="0.01" value="0.5" /></label>
              <label class="field">Drehen
                <div class="glyph-rot-row">
                  <input type="range" id="glyphRot" min="0" max="360" step="1" value="0" />
                  <span id="glyphRotVal" class="muted mono">0°</span>
                </div>
              </label>
              <div class="actions glyph-flip-row">
                <button type="button" class="btn secondary" id="glyphFlipH" title="Horizontal spiegeln">⟷ Spiegeln</button>
                <button type="button" class="btn secondary" id="glyphFlipV" title="Vertikal spiegeln">↕ Spiegeln</button>
                <button type="button" class="btn ghost" id="glyphRot90" title="90° drehen">↻ 90°</button>
              </div>
            </div>
          </div>
        </div>
        <div class="actions" style="margin-top:1rem">
          <button type="button" class="btn teal" id="glyphAccept" disabled>Übernehmen</button>
          <button type="button" class="btn ghost" id="glyphCancel">Abbrechen</button>
        </div>
      </div>`;
    document.body.appendChild(layer);
    wireOverlayShield(layer);

    const canvas = layer.querySelector("#glyphCanvas");
    const ctx = canvas.getContext("2d");
    const prevImg = layer.querySelector("#glyphPrev");
    const acceptBtn = layer.querySelector("#glyphAccept");
    const removeBtn = layer.querySelector("#glyphRemove");
    const controls = layer.querySelector("#glyphControls");
    const listEl = layer.querySelector("#glyphLayerList");
    const imageCache = new Map();

    function close() {
      dismissOverlay(layer);
    }
    layer.querySelector("#glyphCancel").onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      close();
    };

    function loadImg(dataUrl) {
      if (imageCache.has(dataUrl)) return Promise.resolve(imageCache.get(dataUrl));
      return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => {
          imageCache.set(dataUrl, img);
          resolve(img);
        };
        img.onerror = reject;
        img.src = dataUrl;
      });
    }

    async function paint() {
      ctx.clearRect(0, 0, 256, 256);
      for (const L of layers) {
        const size = 256 * (Number(L.scale) || 1);
        const x = (Number(L.x) || 0.5) * 256;
        const y = (Number(L.y) || 0.5) * 256;
        const rot = ((Number(L.rotation) || 0) * Math.PI) / 180;
        const sx = L.flipX ? -1 : 1;
        const sy = L.flipY ? -1 : 1;
        ctx.save();
        ctx.translate(x, y);
        ctx.rotate(rot);
        ctx.scale(sx, sy);
        if (L.type === "emoji") {
          ctx.font = `${size * 0.85}px "Segoe UI Emoji","Apple Color Emoji",sans-serif`;
          ctx.textAlign = "center";
          ctx.textBaseline = "middle";
          ctx.fillText(L.emoji, 0, 0);
        } else if (L.type === "image" && L.dataUrl) {
          try {
            const img = await loadImg(L.dataUrl);
            ctx.drawImage(img, -size / 2, -size / 2, size, size);
          } catch {
            /* skip */
          }
        }
        ctx.restore();
      }
      const url = canvas.toDataURL("image/png");
      prevImg.src = url;
      acceptBtn.disabled = layers.length === 0;
    }

    function renderList() {
      listEl.innerHTML = layers
        .map((L, i) => {
          const label = L.type === "emoji" ? L.emoji : "🖼 Bild";
          return `<button type="button" class="glyph-layer-chip ${i === selected ? "on" : ""}" data-i="${i}">${label}</button>`;
        })
        .join("");
      listEl.querySelectorAll("[data-i]").forEach((btn) => {
        btn.onclick = () => {
          selected = Number(btn.getAttribute("data-i"));
          syncControls();
          renderList();
        };
      });
      removeBtn.disabled = selected < 0;
    }

    function syncControls() {
      if (selected < 0 || !layers[selected]) {
        controls.hidden = true;
        return;
      }
      controls.hidden = false;
      const L = layers[selected];
      layer.querySelector("#glyphScale").value = String(L.scale);
      layer.querySelector("#glyphX").value = String(L.x);
      layer.querySelector("#glyphY").value = String(L.y);
      layer.querySelector("#glyphRot").value = String(L.rotation || 0);
      layer.querySelector("#glyphRotVal").textContent = `${Math.round(L.rotation || 0)}°`;
      layer.querySelector("#glyphFlipH").classList.toggle("on", !!L.flipX);
      layer.querySelector("#glyphFlipV").classList.toggle("on", !!L.flipY);
    }

    function readSlidersIntoLayer() {
      if (selected < 0 || !layers[selected]) return;
      const L = layers[selected];
      L.scale = Number(layer.querySelector("#glyphScale").value);
      L.x = Number(layer.querySelector("#glyphX").value);
      L.y = Number(layer.querySelector("#glyphY").value);
      L.rotation = Number(layer.querySelector("#glyphRot").value) || 0;
      layer.querySelector("#glyphRotVal").textContent = `${Math.round(L.rotation)}°`;
    }

    ["glyphScale", "glyphX", "glyphY", "glyphRot"].forEach((id) => {
      layer.querySelector("#" + id).oninput = () => {
        readSlidersIntoLayer();
        paint();
      };
    });

    layer.querySelector("#glyphFlipH").onclick = () => {
      if (selected < 0 || !layers[selected]) return;
      layers[selected].flipX = !layers[selected].flipX;
      syncControls();
      paint();
    };
    layer.querySelector("#glyphFlipV").onclick = () => {
      if (selected < 0 || !layers[selected]) return;
      layers[selected].flipY = !layers[selected].flipY;
      syncControls();
      paint();
    };
    layer.querySelector("#glyphRot90").onclick = () => {
      if (selected < 0 || !layers[selected]) return;
      layers[selected].rotation = ((Number(layers[selected].rotation) || 0) + 90) % 360;
      syncControls();
      paint();
    };

    layer.querySelector("#glyphAddEmoji").onclick = () => {
      openEmojiPicker((emoji) => {
        layers.push({
          type: "emoji",
          emoji,
          x: 0.5,
          y: 0.5,
          scale: layers.length ? 0.7 : 1,
          rotation: 0,
          flipX: false,
          flipY: false,
        });
        selected = layers.length - 1;
        renderList();
        syncControls();
        paint();
      });
    };
    layer.querySelector("#glyphAddImage").onclick = () => {
      openChromaImageEditor((dataUrl) => {
        layers.push({
          type: "image",
          dataUrl,
          x: 0.5,
          y: 0.5,
          scale: layers.length ? 0.75 : 1,
          rotation: 0,
          flipX: false,
          flipY: false,
        });
        selected = layers.length - 1;
        renderList();
        syncControls();
        paint();
      }, { title: "Bild für Emoji freistellen" });
    };
    removeBtn.onclick = () => {
      if (selected < 0) return;
      layers.splice(selected, 1);
      selected = layers.length ? Math.min(selected, layers.length - 1) : -1;
      renderList();
      syncControls();
      paint();
    };
    acceptBtn.onclick = (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (!layers.length) return;
      // Frisch rendern, dann PNG — verhindert veraltetes Canvas
      const finish = () => {
        const dataUrl = canvas.toDataURL("image/png");
        completeOverlay(layer, () => onDone(dataUrl));
      };
      Promise.resolve(paint()).then(finish).catch(finish);
    };
  }

  function expandEmojiPool(extra) {
    return uniqueEmojis([...(extra || []), ...EMOJI_LIST]);
  }

  async function loadShopEmojisIntoPool() {
    try {
      const host = window.LuvAdmHost;
      if (!host || typeof host.api !== "function") return [];
      const [em, st] = await Promise.all([
        host.api("/admin/shop/items?kind=emojis").catch(() => null),
        host.api("/admin/shop/items?kind=stickers").catch(() => null),
      ]);
      const out = [];
      for (const pack of [em, st]) {
        for (const it of pack?.items || []) {
          const id = String(it.itemId || it.emoji || "").trim();
          if (id && !/^img_/i.test(id) && !/^theme_/i.test(id)) out.push(id);
        }
      }
      return out;
    } catch (_) {
      return [];
    }
  }

  async function openEmojiPicker(onPick) {
    const prev = document.getElementById("emojiPickerLayer");
    if (prev) prev.remove();
    const layer = document.createElement("div");
    layer.id = "emojiPickerLayer";
    layer.className = "pet-paste-layer";
    let q = "";
    let pool = EMOJI_LIST.slice();
    let filtered = pool;
    function paint() {
      const qq = q.toLowerCase();
      filtered = qq
        ? pool.filter(
            (e) =>
              e.includes(q) ||
              (e.codePointAt(0) && e.codePointAt(0).toString(16).includes(qq))
          )
        : pool;
      const shown = filtered; // alle Treffer — kein Truncate
      layer.innerHTML = `
        <div class="pet-paste-card wide-card">
          <h3 style="font-family:var(--display);margin:0 0 0.5rem">Emoji wählen</h3>
          <p class="help" style="margin:0 0 0.5rem">${shown.length} Emojis · auch einfügen/tippen möglich</p>
          <input id="emojiQ" class="emoji-search" placeholder="Suchen…" value="${q.replace(/"/g, "&quot;")}" />
          <div style="display:flex;gap:0.5rem;margin-bottom:0.65rem">
            <input id="emojiPaste" class="emoji-search" style="margin:0;flex:1" placeholder="Emoji einfügen oder tippen…" maxlength="16" />
            <button type="button" class="btn teal" id="emojiPasteGo">OK</button>
          </div>
          <div class="emoji-grid" id="emojiGrid">
            ${shown
              .map(
                (e, i) =>
                  `<button type="button" class="emoji-cell" data-i="${i}">${e}</button>`
              )
              .join("")}
          </div>
          <div class="actions" style="margin-top:0.75rem">
            <button type="button" class="btn ghost" id="emojiCancel">Abbrechen</button>
          </div>
        </div>`;
      layer.querySelector("#emojiCancel").onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        dismissOverlay(layer);
      };
      const usePaste = () => {
        const raw = String(layer.querySelector("#emojiPaste")?.value || "").trim();
        // Erstes Graphem (Emoji inkl. ZWJ)
        const m = raw.match(/\p{Extended_Pictographic}(?:\uFE0F|\u200D\p{Extended_Pictographic})*/u);
        const emoji = (m && m[0]) || raw.slice(0, 8);
        if (!emoji) return;
        completeOverlay(layer, () => onPick(emoji));
      };
      layer.querySelector("#emojiPasteGo").onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        usePaste();
      };
      layer.querySelector("#emojiPaste").onkeydown = (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          usePaste();
        }
      };
      const input = layer.querySelector("#emojiQ");
      input.focus();
      input.oninput = () => {
        q = input.value.trim();
        paint();
        const again = layer.querySelector("#emojiQ");
        if (again) {
          again.focus();
          again.setSelectionRange(q.length, q.length);
        }
      };
      layer.querySelectorAll(".emoji-cell").forEach((btn) => {
        btn.onclick = (ev) => {
          ev.preventDefault();
          ev.stopPropagation();
          const i = Number(btn.getAttribute("data-i"));
          const emoji = shown[i];
          if (!emoji) return;
          completeOverlay(layer, () => onPick(emoji));
        };
      });
    }
    document.body.appendChild(layer);
    wireOverlayShield(layer);
    paint();
    // Shop-Katalog nachladen und Pool erweitern (keine img_/theme_ IDs)
    loadShopEmojisIntoPool().then((extra) => {
      if (!extra.length) return;
      pool = expandEmojiPool(extra);
      paint();
    });
  }

  /**
   * Theme visual studio — returns visualConfig object + preview emoji label.
   */
  function openThemeStudio(initial, onDone) {
    const cfg = Object.assign(
      {
        skyTop: "#7EB8D8",
        skyBottom: "#B8D4E8",
        groundTop: "#2F5D2E",
        groundBottom: "#1E3D1E",
        emojis: ["✨"],
        motion: "fall",
        coverage: "full",
        speed: 1,
        density: 0.7,
        size: 1,
      },
      initial || {}
    );
    if (!Array.isArray(cfg.emojis) || !cfg.emojis.length) cfg.emojis = ["✨"];

    const prev = document.getElementById("themeStudioLayer");
    if (prev) prev.remove();
    const layer = document.createElement("div");
    layer.id = "themeStudioLayer";
    layer.className = "pet-paste-layer";

    function paint() {
      layer.innerHTML = `
        <div class="pet-paste-card wide-card">
          <h3 style="font-family:var(--display);margin:0 0 0.35rem">Hintergrund gestalten</h3>
          <p class="help" style="margin:0 0 0.75rem">Farbverläufe, Emoji-Partikel und Bewegung — live in der Vorschau.</p>
          <div class="theme-studio">
            <div class="theme-preview" id="themePreview">
              <canvas id="themeFx" width="320" height="480"></canvas>
            </div>
            <div class="theme-controls">
              <div class="grid-2">
                <label class="field">Himmel oben <input type="color" id="skyTop" value="${cfg.skyTop}" /></label>
                <label class="field">Himmel unten <input type="color" id="skyBottom" value="${cfg.skyBottom}" /></label>
                <label class="field">Boden oben <input type="color" id="groundTop" value="${cfg.groundTop}" /></label>
                <label class="field">Boden unten <input type="color" id="groundBottom" value="${cfg.groundBottom}" /></label>
              </div>
              <label class="field" style="margin-top:0.65rem">Bewegung
                <select id="motion">
                  ${[
                    ["fall", "Fallen"],
                    ["roll", "Kullern"],
                    ["sway", "Hin und her wehen"],
                    ["drift", "Schweben / treiben"],
                    ["rise", "Aufsteigen"],
                  ]
                    .map(
                      ([v, l]) =>
                        `<option value="${v}" ${cfg.motion === v ? "selected" : ""}>${l}</option>`
                    )
                    .join("")}
                </select>
              </label>
              <label class="field">Abdeckung
                <select id="coverage">
                  <option value="full" ${cfg.coverage === "full" ? "selected" : ""}>Ganzer Hintergrund</option>
                  <option value="band" ${cfg.coverage === "band" ? "selected" : ""}>Nur eine schmale Linie / Band</option>
                  <option value="sky" ${cfg.coverage === "sky" ? "selected" : ""}>Nur Himmel</option>
                  <option value="ground" ${cfg.coverage === "ground" ? "selected" : ""}>Nur Boden</option>
                </select>
              </label>
              <div class="grid-2" style="margin-top:0.5rem">
                <label class="field">Tempo <input type="range" id="speed" min="0.3" max="2.5" step="0.1" value="${cfg.speed}" /></label>
                <label class="field">Dichte <input type="range" id="density" min="0.2" max="1.5" step="0.1" value="${cfg.density}" /></label>
              </div>
              <label class="field">Emoji-Größe <input type="range" id="size" min="0.5" max="2" step="0.1" value="${cfg.size}" /></label>
              <div style="margin-top:0.75rem">
                <div class="muted" style="margin-bottom:0.35rem">Emojis (＋ hinzufügen)</div>
                <div class="theme-emoji-chips" id="themeEmojis">
                  ${cfg.emojis
                    .map(
                      (e, i) =>
                        `<button type="button" class="theme-emoji-chip" data-i="${i}" title="Entfernen">${e}</button>`
                    )
                    .join("")}
                  <button type="button" class="btn teal round-plus" id="themeAddEmoji">＋</button>
                </div>
              </div>
            </div>
          </div>
          <div class="actions" style="margin-top:1rem">
            <button type="button" class="btn teal" id="themeAccept">Übernehmen</button>
            <button type="button" class="btn ghost" id="themeCancel">Abbrechen</button>
          </div>
        </div>`;
      wire();
    }

    let raf = 0;
    let t0 = performance.now();

    function readControls() {
      cfg.skyTop = layer.querySelector("#skyTop").value;
      cfg.skyBottom = layer.querySelector("#skyBottom").value;
      cfg.groundTop = layer.querySelector("#groundTop").value;
      cfg.groundBottom = layer.querySelector("#groundBottom").value;
      cfg.motion = layer.querySelector("#motion").value;
      cfg.coverage = layer.querySelector("#coverage").value;
      cfg.speed = Number(layer.querySelector("#speed").value);
      cfg.density = Number(layer.querySelector("#density").value);
      cfg.size = Number(layer.querySelector("#size").value);
    }

    function drawPreview(now) {
      const canvas = layer.querySelector("#themeFx");
      if (!canvas) return;
      const ctx = canvas.getContext("2d");
      const w = canvas.width;
      const h = canvas.height;
      const g1 = ctx.createLinearGradient(0, 0, 0, h * 0.55);
      g1.addColorStop(0, cfg.skyTop);
      g1.addColorStop(1, cfg.skyBottom);
      ctx.fillStyle = g1;
      ctx.fillRect(0, 0, w, h * 0.55);
      const g2 = ctx.createLinearGradient(0, h * 0.48, 0, h);
      g2.addColorStop(0, cfg.groundTop);
      g2.addColorStop(1, cfg.groundBottom);
      ctx.fillStyle = g2;
      ctx.fillRect(0, h * 0.48, w, h * 0.52);

      const phase = ((now - t0) / 1000) * cfg.speed * 0.35;
      const count = Math.round(14 * cfg.density);
      const emojis = cfg.emojis.length ? cfg.emojis : ["✨"];
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.font = `${18 * cfg.size}px "Segoe UI Emoji","Apple Color Emoji",sans-serif`;

      for (let i = 0; i < count; i++) {
        const seed = (i * 17 + 3) % 100 / 100;
        let x = seed;
        let y = (seed * 0.7 + phase + i * 0.07) % 1;
        if (cfg.motion === "rise") y = 1 - y;
        if (cfg.motion === "sway") {
          x = (seed + Math.sin(phase * 4 + i) * 0.08 + 1) % 1;
          y = 0.2 + ((seed + phase * 0.15) % 0.6);
        }
        if (cfg.motion === "drift") {
          x = (seed + phase * 0.2 + Math.sin(i) * 0.05) % 1;
          y = (seed * 0.5 + 0.15 + Math.cos(phase + i) * 0.08 + 1) % 0.7;
        }
        if (cfg.motion === "roll") {
          x = (seed + phase * 0.4) % 1;
          y = 0.55 + Math.sin(phase * 6 + i) * 0.08;
        }
        if (cfg.coverage === "band") y = 0.42 + ((seed + phase) % 1) * 0.12;
        if (cfg.coverage === "sky") y = y * 0.48;
        if (cfg.coverage === "ground") y = 0.5 + y * 0.48;

        const glyph = emojis[i % emojis.length];
        ctx.globalAlpha = 0.85;
        ctx.fillText(glyph, x * w, y * h);
      }
      ctx.globalAlpha = 1;
      raf = requestAnimationFrame(drawPreview);
    }

    function wire() {
      const bind = (id) => {
        const el = layer.querySelector("#" + id);
        if (el) el.oninput = () => readControls();
      };
      ["skyTop", "skyBottom", "groundTop", "groundBottom", "motion", "coverage", "speed", "density", "size"].forEach(
        bind
      );
      layer.querySelector("#themeAddEmoji").onclick = () => {
        openEmojiPicker((emoji) => {
          cfg.emojis.push(emoji);
          cancelAnimationFrame(raf);
          paint();
        });
      };
      layer.querySelectorAll(".theme-emoji-chip").forEach((btn) => {
        btn.onclick = () => {
          const i = Number(btn.getAttribute("data-i"));
          cfg.emojis.splice(i, 1);
          if (!cfg.emojis.length) cfg.emojis = ["✨"];
          cancelAnimationFrame(raf);
          paint();
        };
      });
      layer.querySelector("#themeCancel").onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        cancelAnimationFrame(raf);
        dismissOverlay(layer);
      };
      layer.querySelector("#themeAccept").onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        readControls();
        cancelAnimationFrame(raf);
        const payload = { ...cfg, emojis: [...cfg.emojis] };
        completeOverlay(layer, () => onDone(payload));
      };
      t0 = performance.now();
      raf = requestAnimationFrame(drawPreview);
    }

    document.body.appendChild(layer);
    wireOverlayShield(layer);
    paint();
  }

  window.LuvShopStudio = {
    EMOJI_LIST,
    openChromaImageEditor,
    openGlyphComposer,
    openEmojiPicker,
    openThemeStudio,
    applyChromaKey,
    hexToRgb,
    rgbToHex,
    newImageItemId,
    isStableImageItemId,
  };
})();

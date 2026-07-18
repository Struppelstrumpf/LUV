(() => {
  const openImpressum = document.getElementById("openImpressum");
  const impressumDialog = document.getElementById("impressumDialog");
  openImpressum?.addEventListener("click", () => impressumDialog?.showModal());

  // Fake „live“ Nutzerzähler — ruhig im Bereich ~10–15k
  (function liveUsersHook() {
    const el = document.getElementById("liveCount");
    if (!el) return;

    const HARD_MIN = 10011;
    const HARD_MAX = 14987;

    // Zielniveau ~12.5k über den Tag (Lokalzeit) — nie glatte Tausender
    const HOUR_TARGET = [
      10847, 10483, 10271, 10093, 10627, 11111, // 0–5 Nacht
      11847, 12411, 12993, // 6–8 Morgen
      13217, 13647, 13813, 13407, 12973, // 9–13
      13141, 13573, 13919, // 14–16
      14287, 14621, 14317, 13673, 12889, // 17–21 Abend
      12183, 11497, // 22–23
    ];

    const rand = (a, b) => a + Math.random() * (b - a);
    const clamp = (n, a, b) => Math.min(b, Math.max(a, n));
    const pick = (arr) => arr[(Math.random() * arr.length) | 0];

    // Nur ungerade, keine …x00 / …x50
    function displayInt(n) {
      let v = Math.round(n);
      v = clamp(v, HARD_MIN, HARD_MAX);
      if (v % 2 === 0) v += 1;
      const mod100 = ((v % 100) + 100) % 100;
      if (mod100 === 0 || mod100 === 50) v += pick([3, 5, 7, 9, 11, 13]);
      if (v % 2 === 0) v += 1;
      return clamp(v, HARD_MIN, HARD_MAX) | 1;
    }

    function dayTarget() {
      const d = new Date();
      const h = d.getHours();
      const m = d.getMinutes() + d.getSeconds() / 60;
      const a = HOUR_TARGET[h];
      const b = HOUR_TARGET[(h + 1) % 24];
      const t = m / 60;
      const daySeed = d.getFullYear() * 1000 + d.getMonth() * 40 + d.getDate();
      const frac = Math.abs(Math.sin(daySeed * 12.9898) * 43758.5453) % 1;
      const dayJitter = frac * 0.05 - 0.025;
      return (a + (b - a) * t) * (1 + dayJitter);
    }

    let value = dayTarget() * rand(0.98, 1.02);
    let shown = displayInt(value);
    let waveRemaining = 0; // Ticks einer längeren Welle
    let waveStep = 0; // Delta pro Tick während der Welle
    let lastJumpAt = 0;

    function tick() {
      const now = Date.now();
      const target = dayTarget();
      const delay = rand(900, 1800); // ~40–65 Ticks/min
      // ~100 Veränderung / Minute → grob 1.5–3 pro Tick im Schnitt
      let delta = rand(-2.8, 2.8);

      // Sehr leichte Drift zum Tagesziel (kein hektisches Nachziehen)
      delta += (target - value) * 0.0008;

      // Seltene abrupte Sprünge (alle paar Minuten) — kleiner Maßstab
      if (now - lastJumpAt > 90000 && Math.random() < 0.04) {
        delta += pick([-1, 1]) * rand(18, 55);
        lastJumpAt = now;
        waveRemaining = 0;
      }

      // Oder eine Welle über einige Minuten (~2–5 min)
      if (waveRemaining <= 0 && now - lastJumpAt > 120000 && Math.random() < 0.025) {
        const dir = pick([-1, 1]);
        const ticks = (rand(80, 180)) | 0; // bei ~1.3s ≈ 2–4 min
        const total = rand(25, 80);
        waveStep = (dir * total) / ticks;
        waveRemaining = ticks;
        lastJumpAt = now;
      }
      if (waveRemaining > 0) {
        delta += waveStep + rand(-0.6, 0.6);
        waveRemaining -= 1;
      }

      value = clamp(value + delta, HARD_MIN, HARD_MAX);

      let next = displayInt(value);
      if (next === shown) next = displayInt(shown + pick([-2, 2, -4, 4]));
      if (next % 2 === 0) next += 1;
      shown = next;
      el.textContent = shown.toLocaleString("de-DE");

      setTimeout(tick, delay);
    }

    el.textContent = shown.toLocaleString("de-DE");
    setTimeout(tick, rand(600, 1200));
  })();

  document.getElementById("scrollCue")?.addEventListener("click", (e) => {
    const target = document.getElementById("entdecken");
    if (!target) return;
    e.preventDefault();
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  });

  const leftPhone = document.querySelector(".phone-him");
  const rightPhone = document.querySelector(".phone-her");
  if (!leftPhone || !rightPhone) return;
  const leftCanvas = leftPhone.querySelector("canvas.draw");
  const rightCanvas = rightPhone.querySelector("canvas.draw");
  const hand = leftPhone.querySelector(".hand");
  const spark = rightPhone.querySelector(".spark");
  if (!leftCanvas || !rightCanvas || !hand || !spark) return;

  const leftCtx = leftCanvas.getContext("2d");
  const rightCtx = rightCanvas.getContext("2d");

  // Heart path in normalized canvas coords (0..1)
  function heartPoint(t) {
    // Parametric heart, remapped into upper-mid screen area
    const a = Math.PI * 2 * t;
    const x = 16 * Math.pow(Math.sin(a), 3);
    const y =
      -(13 * Math.cos(a) -
        5 * Math.cos(2 * a) -
        2 * Math.cos(3 * a) -
        Math.cos(4 * a));
    return {
      x: 0.5 + x / 42,
      y: 0.52 + y / 42,
    };
  }

  function resizeCanvases() {
    for (const canvas of [leftCanvas, rightCanvas]) {
      const rect = canvas.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.max(1, Math.floor(rect.width * dpr));
      canvas.height = Math.max(1, Math.floor(rect.height * dpr));
      const ctx = canvas.getContext("2d");
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
  }

  function clear(ctx, canvas) {
    const rect = canvas.getBoundingClientRect();
    ctx.clearRect(0, 0, rect.width, rect.height);
  }

  function drawPolyline(ctx, canvas, points, progress) {
    const rect = canvas.getBoundingClientRect();
    const count = Math.max(2, Math.floor(points.length * progress));
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = "rgba(255,255,255,0.95)";
    ctx.lineWidth = Math.max(5, rect.width * 0.045);
    ctx.beginPath();
    for (let i = 0; i < count; i++) {
      const p = points[i];
      const x = p.x * rect.width;
      const y = p.y * rect.height;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  const STEPS = 140;
  const points = Array.from({ length: STEPS }, (_, i) => heartPoint(i / (STEPS - 1)));

  let raf = 0;
  let start = 0;
  const DRAW_MS = 2600;
  const HOLD_MS = 1400;
  const FADE_MS = 700;
  const GAP_MS = 900;

  function placeHand(progress) {
    const idx = Math.min(points.length - 1, Math.floor((points.length - 1) * progress));
    const p = points[idx];
    const screen = leftPhone.querySelector(".screen");
    const rect = screen.getBoundingClientRect();
    const bezel = leftPhone.querySelector(".phone-bezel").getBoundingClientRect();
    const x = (p.x * rect.width + (rect.left - bezel.left)) / bezel.width;
    const y = (p.y * rect.height + (rect.top - bezel.top)) / bezel.height;
    hand.style.left = `${x * 100}%`;
    hand.style.top = `${y * 100}%`;
    hand.style.opacity = progress > 0.02 && progress < 0.98 ? "1" : "0";
    hand.style.transform = `translate(-35%, -10%) rotate(${-18 + progress * 8}deg)`;
  }

  function placeSpark(progress) {
    const idx = Math.min(points.length - 1, Math.floor((points.length - 1) * progress));
    const p = points[idx];
    spark.style.left = `${p.x * 100}%`;
    spark.style.top = `${p.y * 100}%`;
    spark.style.opacity = progress > 0.05 && progress < 0.98 ? "0.85" : "0";
    spark.style.transform = "translate(-50%, -50%)";
  }

  function frame(now) {
    if (!start) start = now;
    const elapsed = now - start;
    const cycle = DRAW_MS + HOLD_MS + FADE_MS + GAP_MS;
    const t = elapsed % cycle;

    resizeCanvases();
    clear(leftCtx, leftCanvas);
    clear(rightCtx, rightCanvas);

    if (t < DRAW_MS) {
      const p = t / DRAW_MS;
      const ease = 1 - Math.pow(1 - p, 2.4);
      drawPolyline(leftCtx, leftCanvas, points, ease);
      // Partner follows with a soft delay
      const follow = Math.max(0, ease - 0.08);
      drawPolyline(rightCtx, rightCanvas, points, follow);
      placeHand(ease);
      placeSpark(follow);
    } else if (t < DRAW_MS + HOLD_MS) {
      drawPolyline(leftCtx, leftCanvas, points, 1);
      drawPolyline(rightCtx, rightCanvas, points, 1);
      hand.style.opacity = "0";
      spark.style.opacity = "0";
    } else if (t < DRAW_MS + HOLD_MS + FADE_MS) {
      const fade = 1 - (t - DRAW_MS - HOLD_MS) / FADE_MS;
      leftCtx.globalAlpha = fade;
      rightCtx.globalAlpha = fade;
      drawPolyline(leftCtx, leftCanvas, points, 1);
      drawPolyline(rightCtx, rightCanvas, points, 1);
      leftCtx.globalAlpha = 1;
      rightCtx.globalAlpha = 1;
      hand.style.opacity = "0";
      spark.style.opacity = "0";
    } else {
      hand.style.opacity = "0";
      spark.style.opacity = "0";
    }

    raf = requestAnimationFrame(frame);
  }

  function boot() {
    resizeCanvases();
    cancelAnimationFrame(raf);
    start = 0;
    raf = requestAnimationFrame(frame);
  }

  window.addEventListener("resize", () => {
    resizeCanvases();
  });

  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    resizeCanvases();
    drawPolyline(leftCtx, leftCanvas, points, 1);
    drawPolyline(rightCtx, rightCanvas, points, 1);
    return;
  }

  boot();
})();

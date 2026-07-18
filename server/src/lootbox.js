/**
 * Lootbox: Bucket-Gewichte.
 * ~30% Items um 10 Coins (±1); billiger/teurer seltener; Extrem ~0,1%.
 */

function bucketOf(price) {
  const p = Math.max(1, Number(price) || 1);
  if (p >= 9 && p <= 11) return "sweet";
  if (p >= 6 && p <= 8) return "midLow";
  if (p >= 12 && p <= 20) return "midHigh";
  if (p <= 5) return "cheap";
  if (p >= 80) return "ultra";
  return "expensive"; // 21–79
}

/** Ziel-Masse je Bucket (Summe 1.0). */
const BUCKET_MASS = {
  sweet: 0.3,
  midLow: 0.16,
  midHigh: 0.22,
  cheap: 0.08,
  expensive: 0.239,
  ultra: 0.001,
};

function buildPool({
  emojiPrices,
  themePrices,
  petPrices,
  stickerPrices,
  isKnown,
  defaultPet,
  starterEmojis,
}) {
  const raw = [];
  const push = (kind, itemId, emoji, label, price) => {
    const p = Math.max(1, Number(price) || 1);
    if (p < 1) return;
    if (!isKnown(kind, itemId)) return;
    raw.push({ kind, itemId, emoji, label, shopPrice: p, bucket: bucketOf(p) });
  };

  for (const [emoji, price] of Object.entries(emojiPrices || {})) {
    if ((starterEmojis || []).includes(emoji)) continue;
    if ((Number(price) || 0) < 1) continue;
    push("emojis", emoji, emoji, emoji, price);
  }
  for (const [id, price] of Object.entries(themePrices || {})) {
    if (id === "meadow") continue;
    if ((Number(price) || 0) < 1) continue;
    push("themes", id, "🖼️", id, price);
  }
  for (const [emoji, price] of Object.entries(petPrices || {})) {
    if (emoji === defaultPet) continue;
    if ((Number(price) || 0) < 1) continue;
    push("pets", emoji, emoji, emoji, price);
  }
  for (const [emoji, price] of Object.entries(stickerPrices || {})) {
    if ((Number(price) || 0) < 1) continue;
    push("stickers", emoji, emoji, emoji, price);
  }

  const byBucket = {};
  for (const item of raw) {
    if (!byBucket[item.bucket]) byBucket[item.bucket] = [];
    byBucket[item.bucket].push(item);
  }

  const pool = [];
  for (const [bucket, items] of Object.entries(byBucket)) {
    const mass = BUCKET_MASS[bucket] ?? 0.05;
    if (!items.length) continue;
    // Innerhalb des Buckets: näher an 10 etwas häufiger
    const inner = items.map((it) => {
      const dist = Math.abs(it.shopPrice - 10) + 1;
      return 1 / dist;
    });
    const innerSum = inner.reduce((s, w) => s + w, 0) || 1;
    items.forEach((it, i) => {
      pool.push({
        ...it,
        weight: (mass * inner[i]) / innerSum,
      });
    });
  }
  return pool;
}

function pickFromPool(pool) {
  if (!pool.length) return null;
  const total = pool.reduce((s, x) => s + x.weight, 0);
  let r = Math.random() * total;
  for (const item of pool) {
    r -= item.weight;
    if (r <= 0) {
      const chancePercent = Math.max(0.01, (item.weight / total) * 100);
      return { ...item, chancePercent: Number(chancePercent.toFixed(2)) };
    }
  }
  const last = pool[pool.length - 1];
  return {
    ...last,
    chancePercent: Number(((last.weight / total) * 100).toFixed(2)),
  };
}

function weightForPrice(price) {
  // Nur Debug/Tests — echte Gewichte kommen aus Bucket-Verteilung
  const b = bucketOf(price);
  return BUCKET_MASS[b] ?? 0.05;
}

module.exports = { buildPool, pickFromPool, weightForPrice, bucketOf, BUCKET_MASS };

/**
 * Lootbox: gewichteter Zufall aus Shop-Katalogen.
 * Teurer = seltener; Items >10 Coins möglich; sehr teuer ~0.1%.
 */

function buildPool({
  emojiPrices,
  themePrices,
  petPrices,
  stickerPrices,
  isKnown,
  defaultPet,
  starterEmojis,
}) {
  const pool = [];
  const push = (kind, itemId, emoji, label, price) => {
    const p = Math.max(1, Number(price) || 1);
    if (p < 1) return;
    if (!isKnown(kind, itemId)) return;
    let weight;
    if (p >= 80) weight = 0.001; // ~0.1% class
    else if (p > 10) weight = 1 / (p * p * 0.35);
    else if (p < 10) weight = 0.55 / Math.max(1, 11 - p);
    else weight = 1.2; // exactly ~10
    weight = Math.max(0.0005, weight);
    pool.push({ kind, itemId, emoji, label, shopPrice: p, weight });
  };

  for (const [emoji, price] of Object.entries(emojiPrices || {})) {
    if (starterEmojis.includes(emoji)) continue;
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
  const total2 = pool.reduce((s, x) => s + x.weight, 0);
  return {
    ...last,
    chancePercent: Number(((last.weight / total2) * 100).toFixed(2)),
  };
}

module.exports = { buildPool, pickFromPool };

/**
 * Deutsche Anzeigenamen für Emoji-/Sticker-Items (CLDR-nah + Keyword-Fallback).
 */
const { keywordsForEmoji } = require("./emoji_search_keywords");

const OVERRIDES = {
  "🌳": "Grüner Baum",
  "🌲": "Nadelbaum",
  "🎄": "Weihnachtsbaum",
  "🌴": "Palme",
  "🎋": "Tanabata-Baum",
  "🌵": "Kaktus",
  "❤️": "Rotes Herz",
  "♥️": "Herz",
  "🧡": "Oranges Herz",
  "💛": "Gelbes Herz",
  "💚": "Grünes Herz",
  "💙": "Blaues Herz",
  "💜": "Lila Herz",
  "🖤": "Schwarzes Herz",
  "🤍": "Weißes Herz",
  "🤎": "Braunes Herz",
  "💔": "Gebrochenes Herz",
  "❣️": "Herz-Ausrufezeichen",
  "💕": "Zwei Herzen",
  "💖": "Funkelndes Herz",
  "💗": "Wachsendes Herz",
  "💘": "Herz mit Pfeil",
  "💝": "Herz mit Schleife",
  "💞": "Kreisende Herzen",
  "💟": "Herzdekoration",
  "❤️‍🔥": "Brennendes Herz",
  "❤️‍🩹": "Heilendes Herz",
  "👍": "Daumen hoch",
  "👎": "Daumen runter",
  "❌": "Kreuz",
  "😂": "Freudentränen",
  "🤣": "Lachtränen",
  "😊": "Lächelndes Gesicht",
  "😍": "Herzaugen",
  "🥰": "Verliebtes Gesicht",
  "😘": "Kuss zuwerfen",
  "🔥": "Feuer",
  "✨": "Funkeln",
  "⭐": "Stern",
  "🌟": "Glitzerstern",
  "🦋": "Schmetterling",
  "🌸": "Kirschblüte",
  "🌹": "Rose",
  "🍉": "Wassermelone",
  "🍀": "Kleeblatt",
  "🌙": "Mond",
  "☀️": "Sonne",
  "🌈": "Regenbogen",
  "🐱": "Katze",
  "🐶": "Hund",
  "🐣": "Küken",
  "🧙": "Hexe",
  "🧙‍♀️": "Hexe",
  "🧙‍♂️": "Hexer",
};

const EN_SKIP = new Set([
  "heart", "hearts", "love", "emotion", "face", "smile", "smiling", "with", "the",
  "and", "for", "red", "blue", "green", "yellow", "orange", "purple", "black",
  "white", "brown", "tree", "plant", "animal", "food", "christmas", "evergreen",
  "forest", "pine", "deciduous", "celebration", "japanese", "game", "card",
  "suit", "ily", "xoxo", "adorbs", "bae", "eyes", "eye", "happy", "funny",
  "laugh", "joy", "tear", "tears", "crying", "good", "thumb", "thumbs", "yes",
  "no", "nope", "down", "up", "mark", "cross", "cancel", "multiply", "space",
  "moon", "star", "sparkle", "fire", "burn", "on", "two", "dating", "romance",
  "romantic", "valentine", "ribbon", "arrow", "cupid", "growing", "sparkling",
  "revolving", "decoration", "mending", "improving", "recovering", "well",
  "healthier", "sacred", "lust", "wicked", "evil", "cardiac", "heavy",
  "punctuation", "exclamation", "anniversary", "kisses", "kiss", "loving",
  "morning", "night", "excited", "nervous", "pulse", "heartpulse", "muah",
  "feeling", "feels", "bestest", "143",
]);

function nameFromKeywords(raw) {
  const words = String(raw || "")
    .toLowerCase()
    .split(/\s+/)
    .map((w) => w.trim())
    .filter((w) => w.length >= 3 && !EN_SKIP.has(w) && /^[a-z]+$/.test(w));
  const first = words[0];
  if (!first) return null;
  return first.charAt(0).toUpperCase() + first.slice(1);
}

function displayNameForEmoji(emoji) {
  const id = String(emoji || "").trim();
  if (!id) return "Item";
  if (OVERRIDES[id]) return OVERRIDES[id];
  const bare = id.replace(/\uFE0F/g, "");
  if (OVERRIDES[bare]) return OVERRIDES[bare];
  const fromKw = nameFromKeywords(keywordsForEmoji(id));
  if (fromKw) return fromKw;
  return id;
}

module.exports = { displayNameForEmoji, OVERRIDES };

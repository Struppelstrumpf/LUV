/**
 * Interaktive Lobby-Spiele (ohne words/ttt — die bleiben im Haupt-Index).
 */
const WORDS_DE = require("./words_de");

const CATALOG = [
  { id: "hangman", title: "Galgenmännchen", emoji: "🪢", desc: "Gemeinsam Buchstaben tippen — rettet das Wort." },
  { id: "connect4", title: "Vier gewinnt", emoji: "🔴", desc: "Abwechselnd Steine setzen. Wer vier in einer Reihe hat, gewinnt." },
  { id: "memory", title: "Memory Herzen", emoji: "🃏", desc: "Paare finden — wer das meiste sammelt, gewinnt." },
  { id: "quiz", title: "Herz-Quiz", emoji: "💜", desc: "Schnellfragen — tippt die Antwort, bevor die Zeit um ist." },
  { id: "reaction", title: "Blitz!", emoji: "⚡", desc: "Wartet… und tippt als Erste:r wenn es aufleuchtet." },
  { id: "categories", title: "Kategorie-Blitz", emoji: "🏷️", desc: "Nennt Wörter zur Kategorie — Doppelte zählen nicht." },
  { id: "emoji", title: "Emoji-Rätsel", emoji: "🧩", desc: "Was bedeuten die Emojis? Tippt die Lösung." },
  { id: "rather", title: "Was eher?", emoji: "⚖️", desc: "Zwei Optionen — stimmt ab, seht wer wie tickt." },
  { id: "taprace", title: "Herzchen-Jagd", emoji: "💓", desc: "15 Sekunden so viele Herzen tippen wie ihr könnt." },
  { id: "story", title: "Kettenwort", emoji: "📖", desc: "Jeder hängt ein Wort an — am Ende eure gemeinsame Story." },
];

const QUIZ_BANK = [
  { q: "Welche Farbe hat eine reife Banane?", a: ["Gelb", "Blau", "Lila", "Grau"], i: 0 },
  { q: "Wie viele Tage hat eine Woche?", a: ["5", "6", "7", "8"], i: 2 },
  { q: "Was sagt man morgens?", a: ["Gute Nacht", "Guten Morgen", "Prost", "Tschüss"], i: 1 },
  { q: "Welches Tier miaut?", a: ["Hund", "Kuh", "Katze", "Pferd"], i: 2 },
  { q: "1 + 1 = ?", a: ["1", "2", "3", "11"], i: 1 },
  { q: "Was braucht man zum Zeichnen?", a: ["Hammer", "Stift", "Reifen", "Salz"], i: 1 },
  { q: "Welche Jahreszeit ist am wärmsten?", a: ["Winter", "Herbst", "Sommer", "Frühling"], i: 2 },
  { q: "Herz-Emoji Farbe oft?", a: ["Grün", "Rot", "Braun", "Lila"], i: 1 },
  { q: "Wie heißt unser App?", a: ["LOVE", "LUV", "LIVE", "LILA"], i: 1 },
  { q: "Mond scheint nachts — wahr?", a: ["Ja", "Nein", "Manchmal", "Nie"], i: 0 },
];

const EMOJI_PUZZLES = [
  { e: "🌙 + 💡", a: "nachtlicht" },
  { e: "❤️ + 🔥", a: "liebe" },
  { e: "🐱 + 🏠", a: "katzhaus" },
  { e: "☕ + ⏰", a: "morgen" },
  { e: "🎵 + 🎤", a: "singen" },
  { e: "🌈 + ☀️", a: "sommer" },
  { e: "🍕 + ❤️", a: "pizzaliebe" },
  { e: "🧠 + 💥", a: "idee" },
  { e: "🌹 + 🎁", a: "geschenk" },
  { e: "🛏️ + 😴", a: "schlaf" },
];

const CATEGORIES = [
  "Tiere",
  "Essen",
  "Farben",
  "Filme",
  "Musik",
  "Orte",
  "Gefühle",
  "Sport",
  "Dinge in der Küche",
  "Kosenamen",
];

const RATHER = [
  ["Für immer Sommer", "Für immer Winter"],
  ["Früh aufstehen", "Lange wach bleiben"],
  ["Berge", "Meer"],
  ["Kochen", "Essen gehen"],
  ["Sms", "Anruf"],
  ["Tanzt aus", "Couch-Abend"],
  ["Katze", "Hund"],
  ["Stadt", "Dorf"],
];

const MEMORY_EMOJIS = ["❤️", "💙", "💜", "💛", "💚", "🧡", "🤍", "🖤"];

function normalize(text) {
  return String(text || "")
    .toLowerCase()
    .replace(/ä/g, "ae")
    .replace(/ö/g, "oe")
    .replace(/ü/g, "ue")
    .replace(/ß/g, "ss")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

function pickWord() {
  let guard = 0;
  while (guard < 40) {
    const w = WORDS_DE[Math.floor(Math.random() * WORDS_DE.length)];
    const n = normalize(w);
    if (n.length >= 4 && n.length <= 12) return w;
    guard += 1;
  }
  return "Liebe";
}

function catalog() {
  return CATALOG;
}

function isInteractive(type) {
  return CATALOG.some((c) => c.id === type);
}

function createGame(type, hostPeerId, hostNickname, peers) {
  const peerList = peers.map((p) => ({
    peerId: p.peerId,
    nickname: p.nickname || "Jemand",
  }));
  const base = {
    type,
    status: "playing",
    hostPeerId,
    hostNickname,
    createdAt: Date.now(),
    players: peerList,
    scores: Object.fromEntries(peerList.map((p) => [p.peerId, 0])),
    message: "",
    winner: null,
  };

  switch (type) {
    case "hangman": {
      const word = pickWord();
      return {
        ...base,
        word,
        wordNorm: normalize(word),
        guessed: [],
        wrong: [],
        lives: 7,
        reveal: false,
      };
    }
    case "connect4": {
      const board = Array.from({ length: 6 }, () => Array(7).fill(null));
      return {
        ...base,
        board,
        turnPeerId: hostPeerId,
        colors: {},
        nextColor: 0,
      };
    }
    case "memory": {
      const icons = [...MEMORY_EMOJIS, ...MEMORY_EMOJIS];
      for (let i = icons.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [icons[i], icons[j]] = [icons[j], icons[i]];
      }
      return {
        ...base,
        cards: icons.map((emoji, i) => ({ id: i, emoji, open: false, matched: false })),
        turnPeerId: hostPeerId,
        flip: [],
        lockUntil: 0,
      };
    }
    case "quiz": {
      const q = QUIZ_BANK[Math.floor(Math.random() * QUIZ_BANK.length)];
      return {
        ...base,
        round: 1,
        maxRounds: 5,
        question: q.q,
        answers: q.a,
        correctIndex: q.i,
        answered: {},
        endsAt: Date.now() + 12_000,
        used: [q.q],
      };
    }
    case "reaction": {
      const wait = 2000 + Math.floor(Math.random() * 3500);
      return {
        ...base,
        phase: "wait",
        goAt: Date.now() + wait,
        endsAt: Date.now() + wait + 8000,
        winnerPeerId: null,
        falseStarts: {},
      };
    }
    case "categories": {
      const cat = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
      return {
        ...base,
        category: cat,
        entries: {},
        endsAt: Date.now() + 35_000,
      };
    }
    case "emoji": {
      const p = EMOJI_PUZZLES[Math.floor(Math.random() * EMOJI_PUZZLES.length)];
      return {
        ...base,
        puzzle: p.e,
        answerNorm: normalize(p.a),
        solvedBy: null,
        endsAt: Date.now() + 60_000,
      };
    }
    case "rather": {
      const pair = RATHER[Math.floor(Math.random() * RATHER.length)];
      return {
        ...base,
        optionA: pair[0],
        optionB: pair[1],
        votes: {},
        endsAt: Date.now() + 20_000,
        revealed: false,
      };
    }
    case "taprace": {
      return {
        ...base,
        taps: {},
        endsAt: Date.now() + 15_000,
      };
    }
    case "story": {
      return {
        ...base,
        words: [],
        turnPeerId: hostPeerId,
        maxWords: 12,
      };
    }
    default:
      return null;
  }
}

function assignColor(game, peerId) {
  if (game.colors[peerId] != null) return game.colors[peerId];
  const c = game.nextColor % 2;
  game.colors[peerId] = c;
  game.nextColor += 1;
  return c;
}

function nextPeer(game, current) {
  const ids = game.players.map((p) => p.peerId);
  if (!ids.length) return current;
  const idx = Math.max(0, ids.indexOf(current));
  return ids[(idx + 1) % ids.length];
}

function checkConnect4(board, r, c, color) {
  const dirs = [
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1],
  ];
  for (const [dr, dc] of dirs) {
    let n = 1;
    for (const sign of [-1, 1]) {
      let rr = r + dr * sign;
      let cc = c + dc * sign;
      while (rr >= 0 && rr < 6 && cc >= 0 && cc < 7 && board[rr][cc] === color) {
        n += 1;
        rr += dr * sign;
        cc += dc * sign;
      }
    }
    if (n >= 4) return true;
  }
  return false;
}

function finish(game, winnerPeerId, message) {
  game.status = "ended";
  game.winner = winnerPeerId
    ? game.players.find((p) => p.peerId === winnerPeerId)?.nickname || "Jemand"
    : null;
  game.winnerPeerId = winnerPeerId || null;
  game.message = message || "";
  return game;
}

function applyAction(game, peerId, nickname, action, payload) {
  if (!game || game.status === "ended") {
    return { game, error: "game_over" };
  }
  const act = String(action || "");
  const nick = String(nickname || "Jemand").slice(0, 18);

  switch (game.type) {
    case "hangman": {
      if (act !== "letter") return { game, error: "bad_action" };
      const letter = String(payload?.letter || "")
        .toLowerCase()
        .replace(/[^a-zäöüß]/g, "")
        .slice(0, 1);
      if (!letter) return { game, error: "bad_letter" };
      const key = normalize(letter).slice(0, 1);
      if (!key || game.guessed.includes(key)) return { game, error: "used" };
      game.guessed.push(key);
      if (game.wordNorm.includes(key)) {
        const done = [...game.wordNorm].every(
          (ch) => ch === " " || game.guessed.includes(ch)
        );
        if (done) {
          game.reveal = true;
          game.scores[peerId] = (game.scores[peerId] || 0) + 1;
          finish(game, peerId, `${nick} hat das Wort gerettet!`);
        }
      } else {
        game.wrong.push(key);
        game.lives = Math.max(0, game.lives - 1);
        if (game.lives <= 0) {
          game.reveal = true;
          finish(game, null, `Leider verloren — Wort war „${game.word}“`);
        }
      }
      return { game };
    }

    case "connect4": {
      if (act !== "drop") return { game, error: "bad_action" };
      if (game.turnPeerId !== peerId) return { game, error: "not_your_turn" };
      const col = Number(payload?.col);
      if (!Number.isInteger(col) || col < 0 || col > 6) return { game, error: "bad_col" };
      const color = assignColor(game, peerId);
      let row = -1;
      for (let r = 5; r >= 0; r--) {
        if (game.board[r][col] == null) {
          row = r;
          break;
        }
      }
      if (row < 0) return { game, error: "full" };
      game.board[row][col] = color;
      if (checkConnect4(game.board, row, col, color)) {
        game.scores[peerId] = (game.scores[peerId] || 0) + 1;
        finish(game, peerId, `${nick} gewinnt mit Vier!`);
      } else if (game.board.every((rowArr) => rowArr.every((c) => c != null))) {
        finish(game, null, "Unentschieden — Brett voll.");
      } else {
        game.turnPeerId = nextPeer(game, peerId);
      }
      return { game };
    }

    case "memory": {
      if (act !== "flip") return { game, error: "bad_action" };
      if (Date.now() < (game.lockUntil || 0)) return { game, error: "locked" };
      if (game.turnPeerId !== peerId) return { game, error: "not_your_turn" };
      const id = Number(payload?.id);
      const card = game.cards.find((c) => c.id === id);
      if (!card || card.matched || card.open) return { game, error: "bad_card" };
      card.open = true;
      game.flip.push(id);
      if (game.flip.length === 2) {
        const [a, b] = game.flip.map((fid) => game.cards.find((c) => c.id === fid));
        if (a && b && a.emoji === b.emoji) {
          a.matched = true;
          b.matched = true;
          game.scores[peerId] = (game.scores[peerId] || 0) + 1;
          game.flip = [];
          if (game.cards.every((c) => c.matched)) {
            const best = Object.entries(game.scores).sort((x, y) => y[1] - x[1])[0];
            finish(game, best?.[0] || peerId, "Memory vorbei — Herzen gefunden!");
          }
        } else {
          game.lockUntil = Date.now() + 900;
          const flipIds = [...game.flip];
          game.flip = [];
          game._pendingClose = flipIds;
          game.turnPeerId = nextPeer(game, peerId);
        }
      }
      return { game };
    }

    case "quiz": {
      if (act !== "answer") return { game, error: "bad_action" };
      if (Date.now() > game.endsAt) return { game, error: "timeout" };
      if (game.answered[peerId] != null) return { game, error: "already" };
      const idx = Number(payload?.index);
      game.answered[peerId] = idx;
      if (idx === game.correctIndex) {
        game.scores[peerId] = (game.scores[peerId] || 0) + 1;
      }
      const allIn =
        game.players.length > 0 &&
        game.players.every((p) => game.answered[p.peerId] != null);
      if (allIn || Date.now() > game.endsAt) {
        advanceQuiz(game);
      }
      return { game };
    }

    case "reaction": {
      if (act !== "tap") return { game, error: "bad_action" };
      if (game.winnerPeerId) return { game, error: "done" };
      if (Date.now() < game.goAt) {
        game.falseStarts[peerId] = true;
        return { game, error: "early" };
      }
      game.winnerPeerId = peerId;
      game.scores[peerId] = (game.scores[peerId] || 0) + 1;
      game.phase = "done";
      finish(game, peerId, `${nick} war blitzschnell!`);
      return { game };
    }

    case "categories": {
      if (act !== "word") return { game, error: "bad_action" };
      if (Date.now() > game.endsAt) return { game, error: "timeout" };
      const word = String(payload?.text || "").trim().slice(0, 32);
      const norm = normalize(word);
      if (!norm) return { game, error: "empty" };
      if (!game.entries[peerId]) game.entries[peerId] = [];
      if (game.entries[peerId].some((e) => e.norm === norm)) return { game, error: "dup_self" };
      const taken = Object.values(game.entries)
        .flat()
        .some((e) => e.norm === norm);
      game.entries[peerId].push({ text: word, norm, unique: !taken });
      if (!taken) game.scores[peerId] = (game.scores[peerId] || 0) + 1;
      // mark previous owners as not unique if collision
      if (taken) {
        for (const list of Object.values(game.entries)) {
          for (const e of list) {
            if (e.norm === norm) e.unique = false;
          }
        }
      }
      return { game };
    }

    case "emoji": {
      if (act !== "guess") return { game, error: "bad_action" };
      if (game.solvedBy) return { game, error: "done" };
      const guess = normalize(payload?.text || "");
      if (!guess) return { game, error: "empty" };
      // Nur exakte Norm-Gleichheit — keine Teilstring-Treffer ("i" ≠ "liebe")
      if (guess === game.answerNorm) {
        game.solvedBy = nick;
        game.scores[peerId] = (game.scores[peerId] || 0) + 1;
        finish(game, peerId, `${nick} hat das Emoji-Rätsel gelöst!`);
      }
      return { game, chat: { nickname: nick, text: String(payload?.text || "").slice(0, 40), ok: game.status === "ended" } };
    }

    case "rather": {
      if (act !== "vote") return { game, error: "bad_action" };
      const side = String(payload?.side || "");
      if (side !== "a" && side !== "b") return { game, error: "bad_side" };
      game.votes[peerId] = side;
      const allIn =
        game.players.length > 0 &&
        game.players.every((p) => game.votes[p.peerId] != null);
      if (allIn) {
        game.revealed = true;
        finish(game, null, "So tickt ihr!");
      }
      return { game };
    }

    case "taprace": {
      if (act !== "tap") return { game, error: "bad_action" };
      if (Date.now() > game.endsAt) return { game, error: "timeout" };
      if (!game.tapAt) game.tapAt = {};
      const now = Date.now();
      const last = Number(game.tapAt[peerId]) || 0;
      // Max ~18 Taps/s — Auto-Clicker drosseln
      if (now - last < 55) return { game, error: "rate" };
      game.tapAt[peerId] = now;
      const next = (game.taps[peerId] || 0) + 1;
      // 15s-Runde · ~18 taps/s + kleiner Puffer
      if (next > 280) return { game, error: "cap" };
      game.taps[peerId] = next;
      game.scores[peerId] = next;
      return { game };
    }

    case "story": {
      if (act !== "word") return { game, error: "bad_action" };
      if (game.turnPeerId !== peerId) return { game, error: "not_your_turn" };
      const word = String(payload?.text || "").trim().slice(0, 24);
      if (!word) return { game, error: "empty" };
      game.words.push({ nickname: nick, text: word });
      if (game.words.length >= game.maxWords) {
        finish(game, null, game.words.map((w) => w.text).join(" "));
      } else {
        game.turnPeerId = nextPeer(game, peerId);
      }
      return { game };
    }

    default:
      return { game, error: "unknown_game" };
  }
}

function advanceQuiz(game) {
  if (game.round >= game.maxRounds) {
    const best = Object.entries(game.scores).sort((a, b) => b[1] - a[1])[0];
    finish(game, best?.[0] || null, "Quiz vorbei!");
    return;
  }
  let q = QUIZ_BANK[Math.floor(Math.random() * QUIZ_BANK.length)];
  let guard = 0;
  while (game.used.includes(q.q) && guard < 20) {
    q = QUIZ_BANK[Math.floor(Math.random() * QUIZ_BANK.length)];
    guard += 1;
  }
  game.used.push(q.q);
  game.round += 1;
  game.question = q.q;
  game.answers = q.a;
  game.correctIndex = q.i;
  game.answered = {};
  game.endsAt = Date.now() + 12_000;
}

/** Timer-Ticks für Spiele mit endsAt */
function tickGame(game) {
  if (!game || game.status !== "playing") return { changed: false, game };
  const now = Date.now();

  if (game.type === "memory" && game._pendingClose && now >= (game.lockUntil || 0)) {
    for (const id of game._pendingClose) {
      const c = game.cards.find((x) => x.id === id);
      if (c && !c.matched) c.open = false;
    }
    game._pendingClose = null;
    return { changed: true, game };
  }

  if (game.type === "reaction" && game.phase === "wait" && now >= game.goAt) {
    game.phase = "go";
    return { changed: true, game };
  }

  if (game.type === "quiz" && now > game.endsAt) {
    advanceQuiz(game);
    return { changed: true, game };
  }

  if (game.type === "categories" && now > game.endsAt) {
    // recount unique
    const all = Object.values(game.entries).flat();
    const counts = {};
    for (const e of all) counts[e.norm] = (counts[e.norm] || 0) + 1;
    for (const [pid, list] of Object.entries(game.entries)) {
      game.scores[pid] = list.filter((e) => counts[e.norm] === 1).length;
    }
    const best = Object.entries(game.scores).sort((a, b) => b[1] - a[1])[0];
    finish(game, best?.[0] || null, `Kategorie „${game.category}“ vorbei!`);
    return { changed: true, game };
  }

  if (game.type === "emoji" && now > game.endsAt && !game.solvedBy) {
    finish(game, null, "Zeit um — Rätsel ungelöst.");
    return { changed: true, game };
  }

  if (game.type === "rather" && now > game.endsAt && !game.revealed) {
    game.revealed = true;
    finish(game, null, "Abstimmung vorbei!");
    return { changed: true, game };
  }

  if (game.type === "taprace" && now > game.endsAt) {
    const best = Object.entries(game.scores).sort((a, b) => b[1] - a[1])[0];
    finish(game, best?.[0] || null, "Herzchen-Jagd vorbei!");
    return { changed: true, game };
  }

  return { changed: false, game };
}

function publicState(game) {
  if (!game) return null;
  const scores = game.players.map((p) => ({
    peerId: p.peerId,
    nickname: p.nickname,
    score: game.scores[p.peerId] || 0,
  }));

  const common = {
    type: game.type,
    status: game.status,
    hostPeerId: game.hostPeerId,
    hostNickname: game.hostNickname,
    message: game.message || "",
    winner: game.winner,
    winnerPeerId: game.winnerPeerId || null,
    scores,
    endsAt: game.endsAt || 0,
  };

  switch (game.type) {
    case "hangman": {
      const mask = [...(game.word || "")].map((ch) => {
        const n = normalize(ch);
        if (!n) return ch;
        return game.guessed.includes(n) || game.reveal ? ch : "_";
      });
      return {
        ...common,
        mask: mask.join(""),
        word: game.reveal || game.status === "ended" ? game.word : null,
        guessed: game.guessed,
        wrong: game.wrong,
        lives: game.lives,
      };
    }
    case "connect4":
      return {
        ...common,
        board: game.board,
        turnPeerId: game.turnPeerId,
        colors: game.colors,
      };
    case "memory":
      return {
        ...common,
        cards: game.cards.map((c) => ({
          id: c.id,
          open: c.open || c.matched,
          matched: c.matched,
          emoji: c.open || c.matched ? c.emoji : null,
        })),
        turnPeerId: game.turnPeerId,
      };
    case "quiz":
      return {
        ...common,
        round: game.round,
        maxRounds: game.maxRounds,
        question: game.question,
        answers: game.answers,
        answeredCount: Object.keys(game.answered || {}).length,
        // correct only when round advancing / ended — hide during play
        correctIndex: game.status === "ended" ? game.correctIndex : null,
      };
    case "reaction":
      return {
        ...common,
        phase: game.phase,
        goAt: game.goAt,
      };
    case "categories":
      return {
        ...common,
        category: game.category,
        entries: Object.fromEntries(
          Object.entries(game.entries || {}).map(([pid, list]) => {
            const nick =
              game.players.find((p) => p.peerId === pid)?.nickname || "Jemand";
            return [nick, list.map((e) => e.text)];
          })
        ),
      };
    case "emoji":
      return {
        ...common,
        puzzle: game.puzzle,
        solvedBy: game.solvedBy,
      };
    case "rather": {
      const a = Object.values(game.votes || {}).filter((v) => v === "a").length;
      const b = Object.values(game.votes || {}).filter((v) => v === "b").length;
      return {
        ...common,
        optionA: game.optionA,
        optionB: game.optionB,
        voteCount: Object.keys(game.votes || {}).length,
        revealed: game.revealed || game.status === "ended",
        tallyA: game.revealed || game.status === "ended" ? a : null,
        tallyB: game.revealed || game.status === "ended" ? b : null,
      };
    }
    case "taprace":
      return {
        ...common,
        taps: Object.fromEntries(
          (game.players || []).map((p) => [
            p.nickname,
            game.taps[p.peerId] || 0,
          ])
        ),
      };
    case "story":
      return {
        ...common,
        words: game.words,
        turnPeerId: game.turnPeerId,
        maxWords: game.maxWords,
      };
    default:
      return common;
  }
}

module.exports = {
  catalog,
  isInteractive,
  createGame,
  applyAction,
  tickGame,
  publicState,
};

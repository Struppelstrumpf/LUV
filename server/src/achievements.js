/**
 * Erfolge, Daily-Aufgaben, Day-Streak.
 * Belohnung 1–3 Coins (Tageslimit konfigurierbar) — seltene Erfolge können stattdessen ein Item geben.
 */

const dailyTasks = require("./daily_tasks");

const DEFAULT_ACHIEVEMENT_DAILY_CAP = 12;
const ACHIEVEMENT_DAILY_CAP_MIN = 0;
const ACHIEVEMENT_DAILY_CAP_MAX = 500;

/** @deprecated Nutze getAchievementDailyCap(db) — bleibt als Fallback-Export. */
const ACHIEVEMENT_DAILY_CAP = DEFAULT_ACHIEVEMENT_DAILY_CAP;

function ensureEconomySettings(db) {
  if (!db || typeof db !== "object") return { achievementDailyCap: DEFAULT_ACHIEVEMENT_DAILY_CAP };
  if (!db.economySettings || typeof db.economySettings !== "object") {
    db.economySettings = { achievementDailyCap: DEFAULT_ACHIEVEMENT_DAILY_CAP };
  }
  if (
    db.economySettings.achievementDailyCap === undefined ||
    db.economySettings.achievementDailyCap === null
  ) {
    db.economySettings.achievementDailyCap = DEFAULT_ACHIEVEMENT_DAILY_CAP;
  }
  return db.economySettings;
}

function getAchievementDailyCap(db) {
  const s = ensureEconomySettings(db);
  const n = Math.floor(Number(s.achievementDailyCap));
  if (!Number.isFinite(n)) return DEFAULT_ACHIEVEMENT_DAILY_CAP;
  return Math.max(ACHIEVEMENT_DAILY_CAP_MIN, Math.min(ACHIEVEMENT_DAILY_CAP_MAX, n));
}

function setAchievementDailyCap(db, raw) {
  const n = Math.floor(Number(raw));
  if (!Number.isFinite(n) || n < ACHIEVEMENT_DAILY_CAP_MIN || n > ACHIEVEMENT_DAILY_CAP_MAX) {
    return {
      ok: false,
      message: `Erlaubt: ${ACHIEVEMENT_DAILY_CAP_MIN}–${ACHIEVEMENT_DAILY_CAP_MAX} Coins/Tag.`,
    };
  }
  const s = ensureEconomySettings(db);
  s.achievementDailyCap = n;
  return { ok: true, achievementDailyCap: n };
}

/** Item-Belohnung (statt Coins). kind: pets|themes|stickers|emojis */
function itemReward(kind, itemId, emoji, label) {
  return { kind, itemId, emoji: emoji || itemId, label: label || itemId };
}

/**
 * @type {{ id: string, title: string, desc: string, category: string, metric: string, target: number, coins: number, rewardItem?: object, coinsFallback?: number }[]}
 */
const BASE_ACHIEVEMENTS = [
  // —— Sozial / Freunde (20) ——
  { id: "soc_first_friend", title: "Erster Freund", desc: "Füge jemanden als Freund hinzu.", category: "sozial", metric: "friends", target: 1, coins: 1 },
  { id: "soc_friends_3", title: "Kleiner Kreis", desc: "Habe 3 Freunde.", category: "sozial", metric: "friends", target: 3, coins: 1 },
  { id: "soc_friends_5", title: "Freundeskreis", desc: "Habe 5 Freunde.", category: "sozial", metric: "friends", target: 5, coins: 2 },
  { id: "soc_friends_10", title: "Beliebt", desc: "Habe 10 Freunde.", category: "sozial", metric: "friends", target: 10, coins: 2 },
  { id: "soc_friends_20", title: "Netzwerk", desc: "Habe 20 Freunde.", category: "sozial", metric: "friends", target: 20, coins: 0, rewardItem: itemReward("emojis", "👑", "👑", "Krone"), coinsFallback: 3 },
  { id: "soc_request_sent", title: "Hallo sagen", desc: "Sende eine Freundschaftsanfrage.", category: "sozial", metric: "friend_requests_sent", target: 1, coins: 1 },
  { id: "soc_request_5", title: "Gesellig", desc: "Sende 5 Anfragen.", category: "sozial", metric: "friend_requests_sent", target: 5, coins: 1 },
  { id: "soc_accept_1", title: "Willkommen", desc: "Nimm eine Anfrage an.", category: "sozial", metric: "friend_accepts", target: 1, coins: 1 },
  { id: "soc_accept_5", title: "Offene Arme", desc: "Nimm 5 Anfragen an.", category: "sozial", metric: "friend_accepts", target: 5, coins: 2 },
  { id: "soc_profile_view", title: "Neugierig", desc: "Öffne 5 Freundprofile.", category: "sozial", metric: "profile_views", target: 5, coins: 1 },
  { id: "soc_profile_20", title: "Umsichtig", desc: "Öffne 20 Freundprofile.", category: "sozial", metric: "profile_views", target: 20, coins: 2 },
  { id: "soc_tip_1", title: "Gastfreund", desc: "Gib jemandem ein Glas aus.", category: "sozial", metric: "tips_given", target: 1, coins: 1 },
  { id: "soc_tip_5", title: "Großzügig", desc: "Gib 5 Gläser aus.", category: "sozial", metric: "tips_given", target: 5, coins: 2 },
  { id: "soc_tip_recv", title: "Belohnt", desc: "Erhalte ein Glas.", category: "sozial", metric: "tips_received", target: 1, coins: 1 },
  { id: "soc_order_friends", title: "Sortiert", desc: "Ordne deine Freundesliste neu.", category: "sozial", metric: "friend_reorders", target: 1, coins: 1 },
  { id: "soc_streak_3", title: "Dabei bleiben", desc: "3 Tage Daily-Streak.", category: "sozial", metric: "daily_streak", target: 3, coins: 2 },
  { id: "soc_streak_7", title: "Wochenrhythmus", desc: "7 Tage Daily-Streak.", category: "sozial", metric: "daily_streak", target: 7, coins: 2 },
  { id: "soc_streak_14", title: "Treu", desc: "14 Tage Daily-Streak.", category: "sozial", metric: "daily_streak", target: 14, coins: 3 },
  { id: "soc_streak_30", title: "Unzertrennlich", desc: "30 Tage Daily-Streak.", category: "sozial", metric: "daily_streak", target: 30, coins: 0, rewardItem: itemReward("themes", "stars", "✨", "Sterne"), coinsFallback: 3 },
  { id: "soc_daily_done", title: "Pflicht erfüllt", desc: "Schließe Daily-Aufgaben einmal ab.", category: "sozial", metric: "dailies_completed", target: 1, coins: 1 },

  // —— Freundschaftslevel / Ehe ——
  { id: "fs_lvl_10", title: "Auf dem Weg", desc: "Erreiche Freundschaftslevel 10 mit jemandem.", category: "sozial", metric: "friendship_lvl_10", target: 1, coins: 1 },
  { id: "fs_lvl_25", title: "Gute Freunde", desc: "Erreiche Freundschaftslevel 25.", category: "sozial", metric: "friendship_lvl_25", target: 1, coins: 1 },
  { id: "fs_lvl_50", title: "Herzensfreund", desc: "Erreiche Freundschaftslevel 50.", category: "sozial", metric: "friendship_lvl_50", target: 1, coins: 2 },
  { id: "fs_lvl_75", title: "Unzertrennlich?", desc: "Erreiche Freundschaftslevel 75.", category: "sozial", metric: "friendship_lvl_75", target: 1, coins: 2 },
  { id: "fs_lvl_100", title: "Seelenverwandt", desc: "Erreiche Freundschaftslevel 100.", category: "sozial", metric: "friendship_lvl_100", target: 1, coins: 0, rewardItem: itemReward("emojis", "💞", "💞", "Zwei Herzen"), coinsFallback: 3 },
  { id: "fs_propose", title: "Willst du?", desc: "Stelle einen Heiratsantrag.", category: "sozial", metric: "marriage_proposals", target: 1, coins: 2 },
  { id: "fs_engaged", title: "Verlobt", desc: "Werde verlobt.", category: "sozial", metric: "engagements", target: 1, coins: 2 },
  { id: "fs_wedding", title: "Trausaal", desc: "Beginne die Hochzeitsleinwand.", category: "sozial", metric: "wedding_started", target: 1, coins: 2 },
  { id: "fs_married", title: "Für immer", desc: "Heirate jemanden.", category: "sozial", metric: "married", target: 1, coins: 0, rewardItem: itemReward("stickers", "💒", "💒", "Kapelle-Sticker"), coinsFallback: 3 },
  { id: "fs_guestbook", title: "Gästebuch", desc: "Schreibe einen Gästebucheintrag.", category: "sozial", metric: "guestbook_writes", target: 1, coins: 1 },

  // —— Begleiter / Kraulen (15) ——
  { id: "pet_kraul_1", title: "Erstes Streicheln", desc: "Kraul einen Begleiter.", category: "begleiter", metric: "krauls", target: 1, coins: 1 },
  { id: "pet_kraul_5", title: "Zärtlich", desc: "Kraul 5× Begleiter.", category: "begleiter", metric: "krauls", target: 5, coins: 1 },
  { id: "pet_kraul_15", title: "Tierlieb", desc: "Kraul 15× Begleiter.", category: "begleiter", metric: "krauls", target: 15, coins: 2 },
  { id: "pet_kraul_40", title: "Best Friend", desc: "Kraul 40× Begleiter.", category: "begleiter", metric: "krauls", target: 40, coins: 2 },
  { id: "pet_kraul_100", title: "Zoobesuch", desc: "Kraul 100× Begleiter.", category: "begleiter", metric: "krauls", target: 100, coins: 0, rewardItem: itemReward("stickers", "🦔", "🦔", "Igel-Sticker"), coinsFallback: 3 },
  { id: "pet_own_2", title: "Zweites Tier", desc: "Besitze 2 Begleiter.", category: "begleiter", metric: "pets_owned", target: 2, coins: 1 },
  { id: "pet_own_5", title: "Menagerie", desc: "Besitze 5 Begleiter.", category: "begleiter", metric: "pets_owned", target: 5, coins: 2 },
  { id: "pet_own_10", title: "Sammler", desc: "Besitze 10 Begleiter.", category: "begleiter", metric: "pets_owned", target: 10, coins: 0, rewardItem: itemReward("pets", "🦄", "🦄", "Einhorn"), coinsFallback: 3 },
  { id: "pet_equip", title: "Outfit gewechselt", desc: "Rüste einen Begleiter aus.", category: "begleiter", metric: "pet_equips", target: 1, coins: 1 },
  { id: "pet_equip_5", title: "Stilwechsel", desc: "Wechsle 5× den Begleiter.", category: "begleiter", metric: "pet_equips", target: 5, coins: 1 },
  { id: "pet_buy", title: "Tierkauf", desc: "Kaufe einen Begleiter im Shop.", category: "begleiter", metric: "pets_bought", target: 1, coins: 1 },
  { id: "pet_buy_3", title: "Nachschub", desc: "Kaufe 3 Begleiter.", category: "begleiter", metric: "pets_bought", target: 3, coins: 2 },
  { id: "pet_owl", title: "Nachtaktiv", desc: "Besitze die Eule.", category: "begleiter", metric: "pet_owl", target: 1, coins: 2 },
  { id: "pet_tiger", title: "Wildkatze", desc: "Besitze den Tiger.", category: "begleiter", metric: "pet_tiger", target: 1, coins: 2 },
  { id: "pet_kraul_unique", title: "Viele Freunde", desc: "Kraul 5 verschiedene Personen.", category: "begleiter", metric: "kraul_unique", target: 5, coins: 2 },

  // —— Malen / Leinwand (20) ——
  { id: "draw_first", title: "Erster Strich", desc: "Male einmal auf einer Leinwand.", category: "malen", metric: "draw_sessions", target: 1, coins: 1 },
  { id: "draw_5", title: "Warmgelaufen", desc: "Male an 5 Tagen/Sessions.", category: "malen", metric: "draw_sessions", target: 5, coins: 1 },
  { id: "draw_20", title: "Pinselheld", desc: "20 Mal-Sessions.", category: "malen", metric: "draw_sessions", target: 20, coins: 2 },
  { id: "draw_50", title: "Atelier", desc: "50 Mal-Sessions.", category: "malen", metric: "draw_sessions", target: 50, coins: 2 },
  { id: "draw_100", title: "Meisterklasse", desc: "100 Mal-Sessions.", category: "malen", metric: "draw_sessions", target: 100, coins: 0, rewardItem: itemReward("themes", "hearth", "🔥", "Kamin"), coinsFallback: 3 },
  { id: "stroke_50", title: "Liniengewitter", desc: "Setze 50 Striche.", category: "malen", metric: "strokes", target: 50, coins: 1 },
  { id: "stroke_200", title: "Volles Blatt", desc: "Setze 200 Striche.", category: "malen", metric: "strokes", target: 200, coins: 2 },
  { id: "stroke_1000", title: "Endlosband", desc: "Setze 1000 Striche.", category: "malen", metric: "strokes", target: 1000, coins: 0, rewardItem: itemReward("stickers", "🎨", "🎨", "Palette-Sticker"), coinsFallback: 3 },
  { id: "draw_duo", title: "Zu zweit", desc: "Male in einer Lobby mit mind. 2 Personen.", category: "malen", metric: "draw_with_peers", target: 1, coins: 1 },
  { id: "draw_group", title: "Familienbild", desc: "Male mit 4+ Personen in einer Lobby.", category: "malen", metric: "draw_group4", target: 1, coins: 2 },
  { id: "clear_vote", title: "Frischstart", desc: "Starte eine Lösch-Abstimmung.", category: "malen", metric: "clear_proposes", target: 1, coins: 1 },
  { id: "clear_yes", title: "Einverstanden", desc: "Stimme 3× fürs Löschen.", category: "malen", metric: "clear_votes", target: 3, coins: 1 },
  { id: "moment_1", title: "Moment festgehalten", desc: "Speichere einen Moment.", category: "malen", metric: "moments_saved", target: 1, coins: 1 },
  { id: "moment_10", title: "Erinnerungskiste", desc: "Speichere 10 Momente.", category: "malen", metric: "moments_saved", target: 10, coins: 2 },
  { id: "publish_1", title: "Öffentlich", desc: "Veröffentliche ein Bild.", category: "malen", metric: "publishes", target: 1, coins: 1 },
  { id: "publish_5", title: "Galerie-Star", desc: "Veröffentliche 5 Bilder.", category: "malen", metric: "publishes", target: 5, coins: 0, rewardItem: itemReward("stickers", "💌", "💌", "Brief-Sticker"), coinsFallback: 2 },
  { id: "sticker_place", title: "Sticker-Spaß", desc: "Platziere 10 Sticker.", category: "malen", metric: "stickers_placed", target: 10, coins: 1 },
  { id: "tpl_create_1", title: "Erste Vorlage", desc: "Speichere eine Zeichen-Vorlage.", category: "malen", metric: "templates_created", target: 1, coins: 1 },
  { id: "tpl_create_5", title: "Vorlagen-Mappe", desc: "Speichere 5 Vorlagen.", category: "malen", metric: "templates_created", target: 5, coins: 2 },
  { id: "tpl_place_1", title: "Stempel", desc: "Platziere eine Vorlage auf der Leinwand.", category: "malen", metric: "templates_placed", target: 1, coins: 1 },
  { id: "tpl_place_10", title: "Vorlagen-Meister", desc: "Platziere 10 Vorlagen.", category: "malen", metric: "templates_placed", target: 10, coins: 0, rewardItem: itemReward("stickers", "🪄", "🪄", "Zauberstab-Sticker"), coinsFallback: 2 },
  { id: "undo_10", title: "Oops", desc: "Nutze Undo 10×.", category: "malen", metric: "undos", target: 10, coins: 1 },
  { id: "color_change", title: "Farbenfroh", desc: "Wechsle 5× die Farbe.", category: "malen", metric: "recolors", target: 5, coins: 1 },
  { id: "game_play", title: "Spielrunde", desc: "Spiele ein Lobby-Spiel.", category: "malen", metric: "games_plays", target: 1, coins: 1 },

  // —— Lobbys (15) ——
  { id: "lobby_create", title: "Eigene Lobby", desc: "Erstelle eine Lobby.", category: "lobby", metric: "lobbies_created", target: 1, coins: 1 },
  { id: "lobby_create_3", title: "Gastgeber", desc: "Erstelle 3 Lobbys.", category: "lobby", metric: "lobbies_created", target: 3, coins: 2 },
  { id: "lobby_join", title: "Zu Besuch", desc: "Tritt einer Lobby bei.", category: "lobby", metric: "lobbies_joined", target: 1, coins: 1 },
  { id: "lobby_join_5", title: "Unterwegs", desc: "Tritt 5 Lobbys bei.", category: "lobby", metric: "lobbies_joined", target: 5, coins: 2 },
  { id: "lobby_invite", title: "Einladung", desc: "Lade jemanden ein (Platz freigeben).", category: "lobby", metric: "invites_sent", target: 1, coins: 1 },
  { id: "lobby_rename", title: "Umbenannt", desc: "Benenne eine Lobby um.", category: "lobby", metric: "lobby_renames", target: 1, coins: 1 },
  { id: "lobby_active_2", title: "Zwei Welten", desc: "Habe 2 Lobbys gleichzeitig.", category: "lobby", metric: "lobbies_active", target: 2, coins: 1 },
  { id: "lobby_active_5", title: "Volles Haus", desc: "Habe 5 Lobbys.", category: "lobby", metric: "lobbies_active", target: 5, coins: 2 },
  { id: "lobby_peak_4", title: "Vollbesetzt", desc: "Erreiche 4 Personen in einer Lobby.", category: "lobby", metric: "lobby_peak", target: 4, coins: 2 },
  { id: "lobby_peak_8", title: "Party", desc: "Erreiche 8 Personen in einer Lobby.", category: "lobby", metric: "lobby_peak", target: 8, coins: 0, rewardItem: itemReward("emojis", "🎉", "🎉", "Party"), coinsFallback: 3 },
  { id: "lobby_free", title: "Gratisplatz", desc: "Erstelle eine Free-Lobby.", category: "lobby", metric: "free_lobbies", target: 1, coins: 1 },
  { id: "lobby_paid", title: "Premium-Raum", desc: "Erstelle eine bezahlte Lobby.", category: "lobby", metric: "paid_lobbies", target: 1, coins: 2 },
  { id: "lobby_leave", title: "Auf Wiedersehen", desc: "Verlasse eine Lobby bewusst.", category: "lobby", metric: "lobby_leaves", target: 1, coins: 1 },
  { id: "lobby_host", title: "Host-Würde", desc: "Werde Host einer Lobby.", category: "lobby", metric: "host_times", target: 1, coins: 1 },
  { id: "lobby_memory", title: "Erinnerung", desc: "Öffne eine Canvas-Memory.", category: "lobby", metric: "memories_opened", target: 1, coins: 1 },

  // —— Markt / Shop / Coins (15) ——
  { id: "mkt_list", title: "Anbieter", desc: "Stelle ein Markt-Angebot ein.", category: "markt", metric: "market_listed", target: 1, coins: 1 },
  { id: "mkt_list_5", title: "Händler", desc: "Stelle 5 Angebote ein.", category: "markt", metric: "market_listed", target: 5, coins: 2 },
  { id: "mkt_buy", title: "Schnäppchen", desc: "Kaufe etwas auf dem Marktplatz.", category: "markt", metric: "market_bought", target: 1, coins: 1 },
  { id: "mkt_buy_5", title: "Einkaufsbummel", desc: "Kaufe 5 Markt-Artikel.", category: "markt", metric: "market_bought", target: 5, coins: 2 },
  { id: "mkt_sell", title: "Verkauf", desc: "Verkaufe etwas erfolgreich.", category: "markt", metric: "market_sold", target: 1, coins: 2 },
  { id: "mkt_sell_5", title: "Geschäftstüchtig", desc: "Verkaufe 5 Artikel.", category: "markt", metric: "market_sold", target: 5, coins: 0, rewardItem: itemReward("themes", "lavender", "💜", "Lavendel"), coinsFallback: 3 },
  { id: "mkt_trade", title: "Tauschhandel", desc: "Schließe einen Tausch ab.", category: "markt", metric: "market_trades", target: 1, coins: 2 },
  { id: "mkt_private", title: "Privatdeal", desc: "Erstelle ein privates Angebot.", category: "markt", metric: "market_private", target: 1, coins: 1 },
  { id: "shop_emoji", title: "Reaktion neu", desc: "Kaufe ein Reaktions-Emoji.", category: "markt", metric: "emojis_bought", target: 1, coins: 1 },
  { id: "shop_theme", title: "Neuer Look", desc: "Kaufe ein Profil-Theme.", category: "markt", metric: "themes_bought", target: 1, coins: 1 },
  { id: "shop_sticker", title: "Sticker-Shop", desc: "Kaufe einen Sticker.", category: "markt", metric: "stickers_bought", target: 1, coins: 1 },
  { id: "coin_daily", title: "Tagesgeld", desc: "Hole dir die Daily-Coins.", category: "markt", metric: "daily_claims", target: 1, coins: 1 },
  { id: "coin_daily_7", title: "Wochenlohn", desc: "Hole 7× Daily-Coins.", category: "markt", metric: "daily_claims", target: 7, coins: 2 },
  { id: "coin_spend_50", title: "Ausgegeben", desc: "Gib 50 Coins aus.", category: "markt", metric: "coins_spent", target: 50, coins: 2 },
  { id: "coin_earn_ach", title: "Erfolgsjäger", desc: "Verdiene 25 Coins durch Erfolge.", category: "markt", metric: "ach_coins_earned", target: 25, coins: 2 },

  // —— Profil / App (15) ——
  { id: "prof_nick", title: "Namensgebung", desc: "Wähle deinen Spitznamen.", category: "profil", metric: "nickname_set", target: 1, coins: 1 },
  { id: "prof_edit", title: "Profil-Artist", desc: "Speichere die Profil-Leinwand.", category: "profil", metric: "profile_saves", target: 1, coins: 1 },
  { id: "prof_edit_5", title: "Feinschliff", desc: "Speichere das Profil 5×.", category: "profil", metric: "profile_saves", target: 5, coins: 1 },
  { id: "tut_done", title: "Willkommen", desc: "Schließe das Tutorial ab.", category: "profil", metric: "tutorial_done", target: 1, coins: 1 },
  { id: "tut_draw", title: "Erste Skizze", desc: "Zeichne im Tutorial.", category: "profil", metric: "tutorial_draw", target: 1, coins: 1 },
  { id: "set_quiet", title: "Ruhezeiten", desc: "Stelle Ruhezeiten ein.", category: "profil", metric: "quiet_hours_set", target: 1, coins: 1 },
  { id: "set_emoji_bar", title: "Eigene Leiste", desc: "Passe die Reaktionsleiste an.", category: "profil", metric: "emoji_bar_edits", target: 1, coins: 1 },
  { id: "gal_open", title: "Galeriebesuch", desc: "Öffne die Galerie.", category: "profil", metric: "gallery_opens", target: 1, coins: 1 },
  { id: "gal_share", title: "Geteilt", desc: "Teile einen Moment.", category: "profil", metric: "moments_shared", target: 1, coins: 1 },
  { id: "ach_10", title: "Zehn Stück", desc: "Schalte 10 Erfolge frei.", category: "profil", metric: "achievements_unlocked", target: 10, coins: 2 },
  { id: "ach_25", title: "Viertel voll", desc: "Schalte 25 Erfolge frei.", category: "profil", metric: "achievements_unlocked", target: 25, coins: 2 },
  { id: "ach_50", title: "Halbzeit", desc: "Schalte 50 Erfolge frei.", category: "profil", metric: "achievements_unlocked", target: 50, coins: 0, rewardItem: itemReward("pets", "🐯", "🐯", "Tiger"), coinsFallback: 3 },
  { id: "ach_75", title: "Fast alles", desc: "Schalte 75 Erfolge frei.", category: "profil", metric: "achievements_unlocked", target: 75, coins: 0, rewardItem: itemReward("themes", "ocean", "🌊", "Meer"), coinsFallback: 3 },
  { id: "login_google", title: "Verbunden", desc: "Verknüpfe Google.", category: "profil", metric: "google_linked", target: 1, coins: 1 },
  { id: "app_return", title: "Wieder da", desc: "Öffne die App an 5 verschiedenen Tagen.", category: "profil", metric: "active_days", target: 5, coins: 2 },
];

/** @deprecated Alias — nutze listAchievements(). Bleibt für require-Kompatibilität. */
const ACHIEVEMENTS = BASE_ACHIEVEMENTS;

const CATEGORIES = [
  { id: "sozial", label: "Sozial" },
  { id: "begleiter", label: "Begleiter" },
  { id: "malen", label: "Malen" },
  { id: "markt", label: "Markt" },
  { id: "profil", label: "Profil" },
];

const METRIC_OPTIONS = [
  { id: "friends", label: "Freunde" },
  { id: "friend_requests_sent", label: "Anfragen gesendet" },
  { id: "friend_accepts", label: "Anfragen angenommen" },
  { id: "profile_views", label: "Freundprofile geöffnet" },
  { id: "tips_given", label: "Gläser ausgegeben" },
  { id: "tips_received", label: "Gläser erhalten" },
  { id: "friend_reorders", label: "Freundesliste sortiert" },
  { id: "daily_streak", label: "Daily-Streak (Tage)" },
  { id: "dailies_completed", label: "Dailies abgeschlossen" },
  { id: "friendship_lvl_10", label: "Freundschaftslevel 10" },
  { id: "friendship_lvl_25", label: "Freundschaftslevel 25" },
  { id: "friendship_lvl_50", label: "Freundschaftslevel 50" },
  { id: "friendship_lvl_75", label: "Freundschaftslevel 75" },
  { id: "friendship_lvl_100", label: "Freundschaftslevel 100" },
  { id: "marriage_proposals", label: "Heiratsanträge" },
  { id: "engagements", label: "Verlobungen" },
  { id: "wedding_started", label: "Hochzeitsleinwand" },
  { id: "married", label: "Verheiratet" },
  { id: "guestbook_writes", label: "Gästebuch-Einträge" },
  { id: "krauls", label: "Kraulen" },
  { id: "pets_owned", label: "Begleiter Besitz" },
  { id: "pet_equips", label: "Begleiter gewechselt" },
  { id: "pets_bought", label: "Begleiter gekauft" },
  { id: "pet_owl", label: "Eule besitzen" },
  { id: "pet_tiger", label: "Tiger besitzen" },
  { id: "kraul_unique", label: "Verschiedene Personen gekrault" },
  { id: "draw_sessions", label: "Mal-Sessions" },
  { id: "strokes", label: "Striche" },
  { id: "draw_with_peers", label: "Zu zweit gemalt" },
  { id: "draw_group4", label: "Mit 4+ gemalt" },
  { id: "clear_proposes", label: "Lösch-Abstimmungen gestartet" },
  { id: "clear_votes", label: "Lösch-Stimmen" },
  { id: "moments_saved", label: "Momente gespeichert" },
  { id: "moments_shared", label: "Momente geteilt" },
  { id: "reactions_sent", label: "Reaktionen gesendet" },
  { id: "lobby_opens", label: "Lobby geöffnet" },
  { id: "templates_placed", label: "Vorlagen platziert" },
  { id: "stickers_placed", label: "Sticker platziert" },
  { id: "market_opens", label: "Marktplatz geöffnet" },
  { id: "shop_buys", label: "Shop-Käufe" },
  { id: "market_sells", label: "Markt-Verkäufe" },
  { id: "market_buys", label: "Markt-Käufe" },
  { id: "codes_redeemed", label: "Codes eingelöst" },
  { id: "profile_saves", label: "Profil gespeichert" },
  { id: "tutorial_done", label: "Tutorial abgeschlossen" },
  { id: "tutorial_draw", label: "Tutorial gezeichnet" },
  { id: "quiet_hours_set", label: "Ruhezeiten gesetzt" },
  { id: "emoji_bar_edits", label: "Reaktionsleiste angepasst" },
  { id: "gallery_opens", label: "Galerie geöffnet" },
  { id: "social_opens", label: "Sozial geöffnet" },
  { id: "achievements_unlocked", label: "Erfolge freigeschaltet" },
  { id: "google_linked", label: "Google verknüpft" },
  { id: "active_days", label: "Aktive Tage" },
  { id: "ach_coins_earned", label: "Erfolgs-Coins gesamt" },
];

let getDbFn = () => null;

function bindGetDb(fn) {
  getDbFn = typeof fn === "function" ? fn : () => null;
}

function ensureAchievementDefs(db) {
  if (!db || typeof db !== "object") return {};
  if (!db.achievementDefs || typeof db.achievementDefs !== "object") {
    db.achievementDefs = {};
  }
  return db.achievementDefs;
}

function normalizeRewardItem(raw) {
  if (!raw || typeof raw !== "object") return null;
  const kind = String(raw.kind || "").trim();
  const itemId = String(raw.itemId || "").trim().slice(0, 32);
  if (!["emojis", "stickers", "themes", "pets"].includes(kind) || !itemId) return null;
  return {
    kind,
    itemId,
    emoji: String(raw.emoji || itemId).slice(0, 32),
    label: String(raw.label || itemId).trim().slice(0, 40) || itemId,
  };
}

function normalizeAchievementDef(raw, { requireId = true } = {}) {
  const id = String(raw?.id || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/_+/g, "_")
    .slice(0, 48);
  if (requireId && !id) {
    return { ok: false, error: "bad_id", message: "Erfolgs-ID fehlt." };
  }
  const title = String(raw?.title || "").trim().slice(0, 48);
  const desc = String(raw?.desc || "").trim().slice(0, 160);
  const category = String(raw?.category || "profil").trim().slice(0, 24);
  const metric = String(raw?.metric || "").trim().slice(0, 48);
  const target = Math.max(1, Math.min(1_000_000, Math.floor(Number(raw?.target) || 1)));
  const coins = Math.max(0, Math.min(50, Math.floor(Number(raw?.coins) || 0)));
  const coinsFallback = Math.max(0, Math.min(50, Math.floor(Number(raw?.coinsFallback) || 3)));
  const rewardItem = normalizeRewardItem(raw?.rewardItem);
  if (!title) return { ok: false, error: "bad_title", message: "Titel fehlt." };
  if (!desc) return { ok: false, error: "bad_desc", message: "Beschreibung fehlt." };
  if (!metric) return { ok: false, error: "bad_metric", message: "Metrik fehlt." };
  if (!CATEGORIES.some((c) => c.id === category)) {
    return { ok: false, error: "bad_category", message: "Unbekannte Kategorie." };
  }
  if (!rewardItem && coins <= 0) {
    return {
      ok: false,
      error: "bad_reward",
      message: "Belohnung: Coins oder Item angeben.",
    };
  }
  return {
    ok: true,
    def: {
      id,
      title,
      desc,
      category,
      metric,
      target,
      coins: rewardItem ? 0 : coins,
      rewardItem: rewardItem || undefined,
      coinsFallback: rewardItem ? coinsFallback : undefined,
      disabled: raw?.disabled === true,
      custom: raw?.custom === true,
    },
  };
}

function mergeAchievementDef(base, patch) {
  if (!base && !patch) return null;
  if (!base) {
    const n = normalizeAchievementDef({ ...patch, custom: true });
    return n.ok ? n.def : null;
  }
  if (!patch || typeof patch !== "object") {
    return { ...base, disabled: false, custom: false };
  }
  const merged = {
    ...base,
    ...patch,
    id: base.id,
    rewardItem:
      patch.rewardItem === null
        ? undefined
        : patch.rewardItem !== undefined
          ? normalizeRewardItem(patch.rewardItem) || undefined
          : base.rewardItem,
    custom: Boolean(patch.custom || base.custom),
    disabled: patch.disabled === true,
  };
  if (merged.rewardItem) merged.coins = 0;
  return merged;
}

/**
 * Aktive Erfolge (Spieler) oder inkl. deaktivierter (Admin).
 * @param {{ includeDisabled?: boolean }} [opts]
 */
function listAchievements(opts = {}) {
  const includeDisabled = Boolean(opts.includeDisabled);
  const db = getDbFn();
  const overrides = ensureAchievementDefs(db);
  const byId = new Map();
  for (const base of BASE_ACHIEVEMENTS) {
    const patch = overrides[base.id];
    const merged = mergeAchievementDef(base, patch);
    if (!merged) continue;
    if (!includeDisabled && merged.disabled) continue;
    byId.set(merged.id, { ...merged, builtin: true });
  }
  for (const [id, patch] of Object.entries(overrides)) {
    if (byId.has(id)) continue;
    if (!patch || typeof patch !== "object") continue;
    const merged = mergeAchievementDef(null, { ...patch, id, custom: true });
    if (!merged) continue;
    if (!includeDisabled && merged.disabled) continue;
    byId.set(merged.id, { ...merged, builtin: false, custom: true });
  }
  return [...byId.values()].sort((a, b) => {
    const ca = String(a.category).localeCompare(String(b.category), "de");
    if (ca) return ca;
    return String(a.title).localeCompare(String(b.title), "de");
  });
}

function findAchievement(id, opts = {}) {
  const want = String(id || "").trim();
  if (!want) return null;
  return listAchievements(opts).find((d) => d.id === want) || null;
}

function achievementMetaMap() {
  const map = {};
  for (const def of listAchievements({ includeDisabled: true })) {
    map[def.id] = {
      id: def.id,
      title: def.title,
      desc: def.desc,
      category: def.category,
      metric: def.metric,
      target: def.target,
      coins: def.coins,
      rewardItem: publicRewardItem(def),
      coinsFallback: def.coinsFallback ?? null,
      disabled: Boolean(def.disabled),
      custom: Boolean(def.custom),
      builtin: Boolean(def.builtin),
    };
  }
  return map;
}

function upsertAchievementDef(db, patch, { create = false } = {}) {
  const defs = ensureAchievementDefs(db);
  const id = String(patch?.id || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/_+/g, "_")
    .slice(0, 48);
  if (!id) return { ok: false, error: "bad_id", message: "Erfolgs-ID fehlt." };
  const base = BASE_ACHIEVEMENTS.find((d) => d.id === id) || null;
  const prev = defs[id] || null;
  if (create && (base || prev)) {
    return { ok: false, error: "exists", message: "Erfolg existiert bereits." };
  }
  if (!create && !base && !prev) {
    return { ok: false, error: "not_found", message: "Erfolg nicht gefunden." };
  }
  const source = mergeAchievementDef(base, prev) || { id, custom: true };
  const nextRaw = {
    ...source,
    ...patch,
    id,
    custom: Boolean(source.custom || !base),
  };
  const normalized = normalizeAchievementDef(nextRaw);
  if (!normalized.ok) return normalized;
  const stored = {
    ...normalized.def,
    updatedAt: Date.now(),
  };
  // Builtins: nur Diff speichern wo sinnvoll — volle Kopie ist ok und einfacher
  defs[id] = stored;
  return {
    ok: true,
    achievement: {
      ...stored,
      builtin: Boolean(base),
      custom: !base,
    },
  };
}

function setAchievementDisabled(db, id, disabled) {
  const defs = ensureAchievementDefs(db);
  const want = String(id || "").trim();
  const base = BASE_ACHIEVEMENTS.find((d) => d.id === want) || null;
  const prev = defs[want] || null;
  if (!base && !prev) {
    return { ok: false, error: "not_found", message: "Erfolg nicht gefunden." };
  }
  const merged = mergeAchievementDef(base, prev) || { id: want, custom: !base };
  defs[want] = {
    ...merged,
    disabled: Boolean(disabled),
    updatedAt: Date.now(),
  };
  return { ok: true, achievement: findAchievement(want, { includeDisabled: true }) };
}

function deleteCustomAchievement(db, id) {
  const defs = ensureAchievementDefs(db);
  const want = String(id || "").trim();
  const base = BASE_ACHIEVEMENTS.find((d) => d.id === want);
  if (base) {
    return {
      ok: false,
      error: "builtin",
      message: "Eingebaute Erfolge können nur deaktiviert werden.",
    };
  }
  if (!defs[want]) {
    return { ok: false, error: "not_found", message: "Erfolg nicht gefunden." };
  }
  delete defs[want];
  return { ok: true, deleted: want };
}

/** @deprecated — Pool liegt in daily_tasks.js (Admin-Planer). */
const DAILY_POOL = dailyTasks.BUILTIN_TEMPLATES.map((t) => ({
  id: t.id,
  title: t.title,
  metric: t.metric,
  target: t.target,
}));

function ensureAchievements(user) {
  if (!user.achievements || typeof user.achievements !== "object") {
    user.achievements = {
      progress: {},
      unlocked: {},
      daily: null,
      streak: 0,
      lastDailyCompleteDate: null,
      coinsEarnedToday: 0,
      coinsEarnedDate: null,
      totalAchCoins: 0,
    };
  }
  const a = user.achievements;
  if (!a.progress || typeof a.progress !== "object") a.progress = {};
  if (!a.unlocked || typeof a.unlocked !== "object") a.unlocked = {};
  if (typeof a.streak !== "number") a.streak = 0;
  if (typeof a.coinsEarnedToday !== "number") a.coinsEarnedToday = 0;
  if (typeof a.totalAchCoins !== "number") a.totalAchCoins = 0;
  // Migration: früher auto-gutgeschriebene Erfolge gelten als abgeholt
  for (const u of Object.values(a.unlocked)) {
    if (u && typeof u === "object" && u.claimed === undefined) {
      u.claimed = true;
    }
  }
  return a;
}

function hashDay(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return h;
}

function pickDailyTasks(dayKey) {
  return dailyTasks.pickDailyTasks(getDbFn(), dayKey);
}

function ensureDaily(user, dayKey) {
  const a = ensureAchievements(user);
  if (!a.daily || a.daily.date !== dayKey) {
    a.daily = {
      date: dayKey,
      tasks: pickDailyTasks(dayKey),
      rewardCoins: dailyTasks.getRewardCoins(getDbFn()),
      completed: false,
      rewardClaimed: false,
    };
    // Verpasster Tag (keine vollständigen Tagesaufgaben): Streak → 0
    const prev = a.lastDailyCompleteDate || null;
    if (prev && prev !== dayKey) {
      const gap = dayDiffBerlin(prev, dayKey);
      if (gap > 1) {
        a.streak = 0;
        a.progress.daily_streak = Math.max(Number(a.progress.daily_streak) || 0, 0);
      }
    }
  }
  if (a.coinsEarnedDate !== dayKey) {
    a.coinsEarnedDate = dayKey;
    a.coinsEarnedToday = 0;
  }
  return a;
}

/** Kalendertage-Differenz (YYYY-MM-DD), Europe/Berlin-Keys ohne TZ-Drift. */
function dayDiffBerlin(fromKey, toKey) {
  const a = String(fromKey || "").trim();
  const b = String(toKey || "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(a) || !/^\d{4}-\d{2}-\d{2}$/.test(b)) return null;
  const [ay, am, ad] = a.split("-").map(Number);
  const [by, bm, bd] = b.split("-").map(Number);
  const t0 = Date.UTC(ay, am - 1, ad);
  const t1 = Date.UTC(by, bm - 1, bd);
  return Math.round((t1 - t0) / 86400000);
}

/**
 * Day-Streak nur bei vollständigen Tagesaufgaben:
 * +1 wenn gestern auch komplett, sonst Start bei 1.
 * (Lücken → 0 passiert in ensureDaily beim nächsten Tag.)
 */
function onDailyTasksCompleted(user, dayKey) {
  const a = ensureAchievements(user);
  const day = String(dayKey || "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return a.streak || 0;
  if (a.lastDailyCompleteDate === day) {
    a.progress.daily_streak = Math.max(Number(a.progress.daily_streak) || 0, a.streak || 0);
    return a.streak || 0;
  }
  const prev = a.lastDailyCompleteDate || null;
  const diff = prev ? dayDiffBerlin(prev, day) : null;
  if (diff === 1) {
    a.streak = Math.max(1, (Number(a.streak) || 0) + 1);
  } else {
    a.streak = 1;
  }
  a.lastDailyCompleteDate = day;
  a.lastStreakDate = day;
  a.progress.daily_streak = Math.max(Number(a.progress.daily_streak) || 0, a.streak);
  return a.streak;
}

/** Früher App-Open-Streak — absichtlich no-op (Streak nur bei Tagesaufgaben). */
function touchDayStreak(user, _dayKey) {
  const a = ensureAchievements(user);
  return a.streak || 0;
}

function yesterdayKeyFrom(dayKey) {
  const diff = dayDiffBerlin("1970-01-01", dayKey);
  if (diff == null) return null;
  const [y, m, d] = String(dayKey).split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() - 1);
  const yy = dt.getUTCFullYear();
  const mm = String(dt.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(dt.getUTCDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}

function remainingAchCoinsToday(user, dayKey, dailyCap = DEFAULT_ACHIEVEMENT_DAILY_CAP) {
  const a = ensureDaily(user, dayKey);
  const cap = Math.max(
    ACHIEVEMENT_DAILY_CAP_MIN,
    Math.min(ACHIEVEMENT_DAILY_CAP_MAX, Math.floor(Number(dailyCap) || 0))
  );
  return Math.max(0, cap - (a.coinsEarnedToday || 0));
}

function publicRewardItem(def) {
  const ri = def?.rewardItem;
  if (!ri || !ri.kind || !ri.itemId) return null;
  return {
    kind: String(ri.kind),
    itemId: String(ri.itemId),
    emoji: String(ri.emoji || ri.itemId),
    label: String(ri.label || ri.itemId),
  };
}

function unlockAchievementEntry(a, def) {
  if (a.unlocked[def.id]) return false;
  // Belohnung erst beim Abholen — einmalig pro Erfolg/Konto
  const reward = publicRewardItem(def);
  a.unlocked[def.id] = {
    at: Date.now(),
    coins: reward ? 0 : Number(def.coins) || 0,
    rewardItem: reward,
    claimed: false,
  };
  return true;
}

/**
 * @returns {{ unlocked: object[], coinsGranted: number, dailyJustCompleted: boolean, streak: number }}
 */
function bumpMetric(user, metric, amount, dayKey, applyLedgerFn) {
  void applyLedgerFn; // Coins nur noch über claim*
  const a = ensureDaily(user, dayKey);
  const add = Math.max(0, Number(amount) || 0);
  if (add <= 0) {
    return { unlocked: [], coinsGranted: 0, dailyJustCompleted: false, streak: a.streak };
  }
  a.progress[metric] = (Number(a.progress[metric]) || 0) + add;

  // Daily tasks — Abschluss ohne Auto-Coins (Abholen in der App)
  let dailyJustCompleted = false;
  if (a.daily && !a.daily.completed) {
    for (const t of a.daily.tasks) {
      if (t.metric === metric && !t.done) {
        t.progress = Math.min(t.target, (Number(t.progress) || 0) + add);
        if (t.progress >= t.target) t.done = true;
      }
    }
    if (a.daily.tasks.every((t) => t.done)) {
      a.daily.completed = true;
      a.daily.rewardClaimed = false;
      dailyJustCompleted = true;
      a.progress.dailies_completed = (Number(a.progress.dailies_completed) || 0) + 1;
    }
  }

  a.progress.achievements_unlocked = Object.keys(a.unlocked).length;
  a.progress.daily_streak = Math.max(Number(a.progress.daily_streak) || 0, a.streak || 0);

  const unlockedNow = [];
  const tryUnlockAll = () => {
    for (const def of listAchievements()) {
      if (a.unlocked[def.id]) continue;
      const cur = Number(a.progress[def.metric]) || 0;
      if (cur < def.target) continue;
      if (unlockAchievementEntry(a, def)) {
        unlockedNow.push({ ...def, coinsGranted: 0, claimable: true });
      }
    }
    a.progress.achievements_unlocked = Object.keys(a.unlocked).length;
  };
  tryUnlockAll();
  // Meta-Erfolge (z. B. achievements_unlocked) in zweitem Durchlauf
  tryUnlockAll();

  return {
    unlocked: unlockedNow,
    coinsGranted: 0,
    dailyJustCompleted,
    streak: a.streak,
  };
}

/**
 * Einmalig Belohnung (Coins oder Item) für einen freigeschalteten Erfolg abholen.
 * @param {(user, kind, itemId) => boolean} [giveItemFn]
 * @param {(user, kind, itemId) => boolean} [ownsUniqueFn] true wenn Pet/Theme schon im Besitz
 */
function isAchievementRewardItem(kind, itemId) {
  const k = String(kind || "");
  const id = String(itemId || "").trim();
  if (!k || !id) return false;
  return listAchievements({ includeDisabled: true }).some((d) => {
    const r = d.rewardItem;
    return r && String(r.kind) === k && String(r.itemId) === id;
  });
}

function userHasRewardItem(user, kind, itemId) {
  const inv = user?.inventory;
  if (!inv) return false;
  const id = String(itemId || "").trim();
  if (kind === "pets") {
    if (Array.isArray(inv.pets)) return inv.pets.includes(id);
    return (Number(inv.pets?.[id]) || 0) > 0;
  }
  if (kind === "themes") return Array.isArray(inv.themes) && inv.themes.includes(id);
  if (kind === "stickers") return (Number(inv.stickers?.[id]) || 0) >= 1;
  if (kind === "emojis") return (Number(inv.emojis?.[id]) || 0) >= 1;
  return false;
}

/**
 * Nachgezogen: Claimed ohne Item (z. B. Katalog-Bug) → Item nachreichen.
 * @returns {number} Anzahl nachgereichter Items
 */
function repairMissingRewardItems(user, giveItemFn, ownsUniqueFn) {
  if (!user || typeof giveItemFn !== "function") return 0;
  const a = ensureAchievements(user);
  let repaired = 0;
  for (const def of listAchievements({ includeDisabled: true })) {
    const reward = publicRewardItem(def);
    if (!reward) continue;
    const entry = a.unlocked[def.id];
    if (!entry || !entry.claimed || entry.itemGranted) continue;
    const uniqueOwned =
      (reward.kind === "pets" || reward.kind === "themes") &&
      typeof ownsUniqueFn === "function" &&
      ownsUniqueFn(user, reward.kind, reward.itemId);
    if (uniqueOwned || userHasRewardItem(user, reward.kind, reward.itemId)) {
      entry.itemGranted = true;
      entry.rewardItem = reward;
      continue;
    }
    if (giveItemFn(user, reward.kind, reward.itemId)) {
      entry.itemGranted = true;
      entry.rewardItem = reward;
      repaired += 1;
    }
  }
  return repaired;
}

function claimAchievement(
  user,
  achievementId,
  dayKey,
  applyLedgerFn,
  giveItemFn,
  ownsUniqueFn,
  dailyCap = DEFAULT_ACHIEVEMENT_DAILY_CAP
) {
  const a = ensureDaily(user, dayKey);
  const id = String(achievementId || "").trim();
  const def = findAchievement(id);
  if (!def) return { ok: false, error: "not_found", message: "Erfolg unbekannt." };
  const entry = a.unlocked[id];
  if (!entry) {
    return { ok: false, error: "locked", message: "Noch nicht freigeschaltet." };
  }
  if (entry.claimed) {
    return { ok: false, error: "already_claimed", message: "Belohnung schon abgeholt." };
  }

  const reward = publicRewardItem(def);
  let grantItem = null;
  let grantCoins = 0;
  let requestedCoins = 0;

  if (reward && typeof giveItemFn === "function") {
    const uniqueOwned =
      (reward.kind === "pets" || reward.kind === "themes") &&
      typeof ownsUniqueFn === "function" &&
      ownsUniqueFn(user, reward.kind, reward.itemId);
    if (uniqueOwned) {
      // Unique schon da → Coin-Fallback
      grantCoins = Math.max(0, Number(def.coinsFallback) || 3);
    } else if (userHasRewardItem(user, reward.kind, reward.itemId)) {
      // Schon durch Heirats-Bonus o. Ä. erhalten — nicht nochmal stapeln
      grantItem = reward;
    } else if (giveItemFn(user, reward.kind, reward.itemId)) {
      grantItem = reward;
    } else {
      // Kein stiller Coin-Fallback mehr — sonst „abgeholt“ ohne Item
      return {
        ok: false,
        error: "grant_failed",
        message: "Item-Belohnung fehlgeschlagen — bitte gleich nochmal abholen.",
      };
    }
  } else {
    grantCoins = Math.max(0, Number(def.coins) || 0);
  }

  requestedCoins = grantCoins;
  let partial = false;
  if (grantCoins > 0) {
    const room = remainingAchCoinsToday(user, dayKey, dailyCap);
    if (room <= 0) {
      return {
        ok: false,
        error: "cap_reached",
        message: "Tageslimit erreicht — hol die Belohnung morgen ab.",
      };
    }
    // Differenz bis zum Tageslimit abholen (Erfolg gilt trotzdem als abgeholt)
    if (room < grantCoins) {
      grantCoins = room;
      partial = true;
    }
  }

  entry.claimed = true;
  entry.claimedAt = Date.now();
  entry.coins = grantCoins;
  entry.rewardItem = grantItem || reward || null;
  entry.itemGranted = Boolean(grantItem);

  if (grantCoins > 0 && applyLedgerFn) {
    applyLedgerFn(user.id, grantCoins, "achievement", id);
    a.coinsEarnedToday += grantCoins;
    a.totalAchCoins += grantCoins;
  }
  a.progress.ach_coins_earned = a.totalAchCoins;
  return {
    ok: true,
    coinsGranted: grantCoins,
    coinsRequested: requestedCoins,
    partial,
    itemGranted: grantItem,
    achievementId: id,
  };
}

/**
 * Tagesaufgaben-Belohnung (+3 Coins) einmalig abholen.
 * Unabhängig vom Achievement-Tageslimit (Cap) — immer abholbar wenn fertig.
 */
function claimDailyReward(user, dayKey, applyLedgerFn) {
  const a = ensureDaily(user, dayKey);
  if (!a.daily?.completed) {
    return { ok: false, error: "incomplete", message: "Tagesaufgaben noch nicht fertig." };
  }
  if (a.daily.rewardClaimed) {
    return { ok: false, error: "already_claimed", message: "Tagesbelohnung schon abgeholt." };
  }
  const grant = Math.max(
    0,
    Math.floor(Number(a.daily.rewardCoins) || dailyTasks.getRewardCoins(getDbFn()) || 3)
  );
  a.daily.rewardClaimed = true;
  a.daily.claimedAt = Date.now();
  const streak = onDailyTasksCompleted(user, dayKey);
  if (applyLedgerFn && grant > 0) {
    applyLedgerFn(user.id, grant, "daily_tasks", dayKey);
    a.coinsEarnedToday += grant;
    a.totalAchCoins += grant;
  }
  a.progress.ach_coins_earned = a.totalAchCoins;
  return { ok: true, coinsGranted: grant, streak };
}

function setMetricAtLeast(user, metric, value, dayKey, applyLedgerFn) {
  const a = ensureDaily(user, dayKey);
  const cur = Number(a.progress[metric]) || 0;
  const target = Math.max(0, Number(value) || 0);
  if (target <= cur) {
    return bumpMetric(user, metric, 0, dayKey, applyLedgerFn);
  }
  return bumpMetric(user, metric, target - cur, dayKey, applyLedgerFn);
}

function publicAchievementsState(user, dayKey, dailyCap = DEFAULT_ACHIEVEMENT_DAILY_CAP) {
  const a = ensureDaily(user, dayKey);
  const rawCap = Math.floor(Number(dailyCap));
  const cap = Number.isFinite(rawCap)
    ? Math.max(ACHIEVEMENT_DAILY_CAP_MIN, Math.min(ACHIEVEMENT_DAILY_CAP_MAX, rawCap))
    : DEFAULT_ACHIEVEMENT_DAILY_CAP;
  const unlockedIds = new Set(Object.keys(a.unlocked));
  const dailyClaimable =
    Boolean(a.daily?.completed) && !Boolean(a.daily?.rewardClaimed);
  const allDefs = listAchievements();
  const achievements = allDefs.map((def) => {
    const u = a.unlocked[def.id];
    const progress = Number(a.progress[def.metric]) || 0;
    const claimed = Boolean(u?.claimed);
    const claimable = Boolean(u) && !claimed;
    const rewardItem = publicRewardItem(def);
    return {
      id: def.id,
      title: def.title,
      desc: def.desc,
      category: def.category,
      target: def.target,
      progress: Math.min(progress, def.target),
      coins: rewardItem ? 0 : Number(def.coins) || 0,
      rewardItem,
      unlocked: Boolean(u),
      unlockedAt: u?.at || null,
      claimed,
      claimable,
    };
  });
  // Abholbare Erfolge immer ganz oben
  achievements.sort((x, y) => {
    if (x.claimable !== y.claimable) return x.claimable ? -1 : 1;
    if (x.unlocked !== y.unlocked) return x.unlocked ? -1 : 1;
    return 0;
  });
  const claimableAchievements = achievements.filter((x) => x.claimable).length;
  const hasClaimable = dailyClaimable || claimableAchievements > 0;
  return {
    streak: a.streak || 0,
    coinsEarnedToday: a.coinsEarnedToday || 0,
    coinsCapToday: cap,
    coinsRemainingToday: remainingAchCoinsToday(user, dayKey, cap),
    hasClaimable,
    claimableCount: claimableAchievements + (dailyClaimable ? 1 : 0),
    daily: {
      date: a.daily.date,
      completed: Boolean(a.daily.completed),
      rewardClaimed: Boolean(a.daily.rewardClaimed),
      claimable: dailyClaimable,
      rewardCoins: Math.max(
        0,
        Math.floor(
          Number(a.daily.rewardCoins) || dailyTasks.getRewardCoins(getDbFn()) || 3
        )
      ),
      tasks: (() => {
        const cfg = dailyTasks.ensureDailyTasksConfig(getDbFn());
        const byId = new Map((cfg.templates || []).map((x) => [x.id, x]));
        return a.daily.tasks.map((t) => {
          const tpl = byId.get(t.id);
          return {
            id: t.id,
            title: t.title || tpl?.title || t.id,
            hint: t.hint || tpl?.hint || "",
            target: t.target,
            progress: t.progress || 0,
            done: Boolean(t.done),
          };
        });
      })(),
    },
    achievements,
    unlockedCount: unlockedIds.size,
    totalCount: allDefs.length,
  };
}

/**
 * Beim Zusammenführen von Konten (z. B. Google-Link): Fortschritt nicht verlieren/cheaten.
 * Nimmt je Metrik das Maximum, unlocked union, Daily-Claim wenn schon abgeholt.
 */
function mergeAchievements(target, source) {
  if (!target || !source) return;
  const ta = ensureAchievements(target);
  const sa = ensureAchievements(source);
  for (const [k, v] of Object.entries(sa.progress || {})) {
    ta.progress[k] = Math.max(Number(ta.progress[k]) || 0, Number(v) || 0);
  }
  for (const [id, u] of Object.entries(sa.unlocked || {})) {
    if (!u || typeof u !== "object") continue;
    if (!ta.unlocked[id]) {
      ta.unlocked[id] = { ...u };
    } else {
      ta.unlocked[id].claimed = Boolean(ta.unlocked[id].claimed || u.claimed);
      const atA = Number(ta.unlocked[id].at) || 0;
      const atB = Number(u.at) || 0;
      if (atB > 0 && (atA <= 0 || atB < atA)) ta.unlocked[id].at = atB;
    }
  }
  ta.streak = Math.max(Number(ta.streak) || 0, Number(sa.streak) || 0);
  ta.totalAchCoins = Math.max(Number(ta.totalAchCoins) || 0, Number(sa.totalAchCoins) || 0);
  const dayA = ta.daily?.date || "";
  const dayB = sa.daily?.date || "";
  if (dayB && (!dayA || dayB >= dayA)) {
    if (dayB === dayA && ta.daily && sa.daily) {
      ta.daily.rewardClaimed = Boolean(ta.daily.rewardClaimed || sa.daily.rewardClaimed);
      ta.daily.completed = Boolean(ta.daily.completed || sa.daily.completed);
      const tasksA = Array.isArray(ta.daily.tasks) ? ta.daily.tasks : [];
      const tasksB = Array.isArray(sa.daily.tasks) ? sa.daily.tasks : [];
      ta.daily.tasks = tasksA.map((t, i) => {
        const o = tasksB[i];
        if (!o) return t;
        const progress = Math.max(Number(t.progress) || 0, Number(o.progress) || 0);
        return {
          ...t,
          progress,
          done: Boolean(t.done || o.done || progress >= (Number(t.target) || 1)),
        };
      });
      if (sa.coinsEarnedDate === dayA) {
        ta.coinsEarnedToday = Math.max(
          Number(ta.coinsEarnedToday) || 0,
          Number(sa.coinsEarnedToday) || 0
        );
      }
    } else if (dayB > dayA) {
      ta.daily = JSON.parse(JSON.stringify(sa.daily));
      ta.coinsEarnedDate = sa.coinsEarnedDate || ta.coinsEarnedDate;
      ta.coinsEarnedToday = Number(sa.coinsEarnedToday) || 0;
      ta.lastDailyCompleteDate = sa.lastDailyCompleteDate || ta.lastDailyCompleteDate;
    }
  }
  const lcdA = ta.lastDailyCompleteDate || "";
  const lcdB = sa.lastDailyCompleteDate || "";
  if (lcdB && (!lcdA || lcdB > lcdA)) ta.lastDailyCompleteDate = lcdB;
}

module.exports = {
  ACHIEVEMENTS,
  BASE_ACHIEVEMENTS,
  CATEGORIES,
  METRIC_OPTIONS,
  ACHIEVEMENT_DAILY_CAP,
  DEFAULT_ACHIEVEMENT_DAILY_CAP,
  ACHIEVEMENT_DAILY_CAP_MIN,
  ACHIEVEMENT_DAILY_CAP_MAX,
  getAchievementDailyCap,
  setAchievementDailyCap,
  ensureEconomySettings,
  ensureAchievements,
  ensureDaily,
  bumpMetric,
  setMetricAtLeast,
  claimAchievement,
  claimDailyReward,
  publicAchievementsState,
  remainingAchCoinsToday,
  mergeAchievements,
  isAchievementRewardItem,
  repairMissingRewardItems,
  publicRewardItem,
  bindGetDb,
  listAchievements,
  findAchievement,
  achievementMetaMap,
  upsertAchievementDef,
  setAchievementDisabled,
  deleteCustomAchievement,
  ensureAchievementDefs,
  itemReward,
  touchDayStreak,
  onDailyTasksCompleted,
  dayDiffBerlin,
  yesterdayKeyFrom,
};

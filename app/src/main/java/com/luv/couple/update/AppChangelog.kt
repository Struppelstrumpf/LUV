package com.luv.couple.update

/**
 * Öffentlicher Versionsverlauf — nur grundlegende neue Funktionen, keine Kleinschliffe.
 * Neueste zuerst.
 */
object AppChangelog {
    data class Entry(
        val version: String,
        val highlights: List<String>
    )

    val entries: List<Entry> = listOf(
        Entry(
            "2.2.126",
            listOf(
                "Start schneller: Splash nutzt wieder Cache, weniger Netz-Stau beim Öffnen",
            )
        ),
        Entry(
            "2.2.125",
            listOf(
                "Hochzeit: Sitzen leichter, Pastor lesbar, Laufen flüssiger, Musik am Altar",
            )
        ),
        Entry(
            "2.2.124",
            listOf(
                "Hochzeit: Avatar verschwindet sofort, wenn man den Raum verlässt",
            )
        ),
        Entry(
            "2.2.123",
            listOf(
                "Hochzeit: Live-Laufen, Sitz-Plus, Music-Box beim 30s-Timer, Geldbäume",
            )
        ),
        Entry(
            "2.2.122",
            listOf(
                "Hochzeit: Priester am Altar, Reaktionen über dem Raum, klassische Orgel-Musik",
                "Offline-Partner erscheint nicht mehr als Geist; Eintreten ohne API-Fehler",
            )
        ),
        Entry(
            "2.2.121",
            listOf(
                "Zur Hochzeit: Kapelle öffnet sofort (kein Warten mehr vor dem Raum)",
            )
        ),
        Entry(
            "2.2.120",
            listOf(
                "Hochzeitseinladung: großes Popup mit Timer — Beitritt zur Kapelle, nicht zur Mal-Lobby",
                "Brautpaar kann Gäste sofort einladen (nicht erst 10 Min. vorher)",
            )
        ),
        Entry(
            "2.2.119",
            listOf(
                "Lootbox: Kauf bestätigen sicher über der Gestenleiste",
            )
        ),
        Entry(
            "2.2.118",
            listOf(
                "Lootbox-Popup neu: Footer komplett sichtbar (Safe-Frame)",
            )
        ),
        Entry(
            "2.2.117",
            listOf(
                "Lootbox: Kauf bestätigen + AGB nicht mehr unter der Gestenleiste",
            )
        ),
        Entry(
            "2.2.116",
            listOf(
                "Itemshop „Meistgekauft“: keine Event-/abgelaufenen Items mehr",
            )
        ),
        Entry(
            "2.2.115",
            listOf(
                "Zeichenfarbe stimmt schon während des Malens (kein Blau→Lila mehr)",
            )
        ),
        Entry(
            "2.2.114",
            listOf(
                "Wartezeit überspringen aktualisiert Sozial sofort",
                "Sozial wieder schneller (Cache sofort, Sync parallel)",
            )
        ),
        Entry(
            "2.2.113",
            listOf(
                "Sozial-Tabs wechseln ohne Coroutine-/Timeout-Fehler",
            )
        ),
        Entry(
            "2.2.112",
            listOf(
                "Erfolge unter Sozial öffnen wieder sofort (Cache, kein erneutes Warten)",
            )
        ),
        Entry(
            "2.2.111",
            listOf(
                "App-Hinweis nennt den Grund (nicht mehr „1 neuer Hinweis“)",
            )
        ),
        Entry(
            "2.2.110",
            listOf(
                "Ehe erst nach beiderseitigem Ja am Altar",
                "Lobby-Umbenennen entfernt",
            )
        ),
        Entry(
            "2.2.109",
            listOf(
                "Hochzeit: Brautmarsch, Altar-Timer, Pastor-Rede & Ja/Nein",
                "Empfang 60 Min mit Geschenk, Gästebuch (+5 Coins) & Applaus",
                "Live-Countdown auf dem Hochzeit-Button · Lootbox-Footer Fix",
            )
        ),
        Entry(
            "2.2.108",
            listOf(
                "Lootbox: Kauf bestätigen auf allen Displays sichtbar",
                "App-Start: Ladevorgänge parallel statt nacheinander",
            )
        ),
        Entry(
            "2.2.107",
            listOf(
                "Hochzeitsteilen: feierliche Einladungskarte mit Datum & Namen",
            )
        ),
        Entry(
            "2.2.106",
            listOf(
                "Hochzeit teilen: Altar-Vorschau statt „zeichnen gerade“",
            )
        ),
        Entry(
            "2.2.105",
            listOf(
                "Nach Termin: Hinweis-Kachel + Benachrichtigung statt Popup",
            )
        ),
        Entry(
            "2.2.104",
            listOf(
                "Hochzeit: Zeiten vorschlagen & gemeinsam festlegen",
                "Hochzeits-Gäste: Custom-Begleiter als Bild statt img_…",
            )
        ),
        Entry(
            "2.2.103",
            listOf(
                "Sozial: Hochzeit als sichtbare Kachel mit Tippen-Button",
            )
        ),
        Entry(
            "2.2.102",
            listOf(
                "Nach Hochzeitsbild: klare Zeremonie-Karte statt nur „Hochzeit“",
            )
        ),
        Entry(
            "2.2.101",
            listOf(
                "Hochzeitsbild: ✓-Button nur noch beim echten Nachholen",
            )
        ),
        Entry(
            "2.2.100",
            listOf(
                "Heiratsantrag: Benachrichtigung heißt nicht mehr Freundschaftsanfrage",
            )
        ),
        Entry(
            "2.2.99",
            listOf(
                "Tote Mal-Lobbys: nur löschen wenn App online und Lobby auf dem Server weg",
            )
        ),
        Entry(
            "2.2.98",
            listOf(
                "Verwaiste Hochzeitsbild-Lobby (Verbinde…) wird entfernt",
            )
        ),
        Entry(
            "2.2.97",
            listOf(
                "Hochzeit: doppelte Hochzeitsbild-Lobby behoben",
            )
        ),
        Entry(
            "2.2.96",
            listOf(
                "Nach Eventende: schlanke Ansicht mit Abstimmung, Timer und Gewinnern",
            )
        ),
        Entry(
            "2.2.95",
            listOf(
                "Event-Abstimmung: Bilder laden deutlich schneller",
            )
        ),
        Entry(
            "2.2.94",
            listOf(
                "Kurz-Wartung: statt Minispiel eine Random-Mal-Lobby mit Pinsel-Leiste",
            )
        ),
        Entry(
            "2.2.93",
            listOf(
                "Events: Abstimmung und Gewinner bleiben nach Eventende sichtbar",
                "Hochzeitsbild-Lobby: kurzer Untertitel",
            )
        ),
        Entry(
            "2.2.92",
            listOf(
                "Hochzeitsleinwand: in mehreren Farben malen (Striche behalten Farbe)",
            )
        ),
        Entry(
            "2.2.91",
            listOf(
                "Hochzeit: leerer Geschenktopf → kleines Andenken für beide",
            )
        ),
        Entry(
            "2.2.90",
            listOf(
                "Hochzeit: gemeinsamer Geschenktopf 24h nach dem Ja",
                "Geschenke abholen: fair geteilt für beide Partner",
            )
        ),
        Entry(
            "2.2.89",
            listOf(
                "Hochzeit: LUV meldet allen das Ja-Wort — Tippen öffnet Gästebuch",
            )
        ),
        Entry(
            "2.2.88",
            listOf(
                "Trausaal: leise Kapellenmusik mit Lautsprecher-Steuerung",
            )
        ),
        Entry(
            "2.2.87",
            listOf(
                "Hochzeitsbild-Lobby erscheint sofort (ohne App-Neustart)",
                "Hochzeitslobby: Farben frei wechseln wie im Event",
            )
        ),
        Entry(
            "2.2.86",
            listOf(
                "Heiratsanfrage: wieder sichtbar zum Annehmen (Sozial + Profil)",
            )
        ),
        Entry(
            "2.2.85",
            listOf(
                "Hochzeit: besser laufen, blauer Sitz-Ring, Reaktionsleiste",
                "„Neuer Raum“ vorerst ausgeblendet",
            )
        ),
        Entry(
            "2.2.84",
            listOf(
                "Kochen: Herdplatte lädt wieder, Tippen auf Küche öffnet Popup",
            )
        ),
        Entry(
            "2.2.83",
            listOf(
                "Haus/Portal: kein API-Fehler-Spam mehr beim Betreten",
            )
        ),
        Entry(
            "2.2.82",
            listOf(
                "Haus betreten: Crash bei kleiner Kamera-/Kartengröße behoben",
            )
        ),
        Entry(
            "2.2.81",
            listOf(
                "Neuer Raum: Häuser betreten, Portale, Kochen, Inventar, Sit-Ring",
                "Einladung öffnet wieder das Dorf statt der Mal-Wand",
            )
        ),
        Entry(
            "2.2.80",
            listOf(
                "Neuer Raum: Zonen deckungsgleich mit Karte (wie im Admin)",
            )
        ),
        Entry(
            "2.2.79",
            listOf(
                "Neuer Raum: Gebiete (Grün/Rot/Blau) zum Prüfen einblendbar",
            )
        ),
        Entry(
            "2.2.78",
            listOf(
                "Neuer Raum: stabiles Laufen ohne Avatar-Zucken, freiere Wiese",
            )
        ),
        Entry(
            "2.2.77",
            listOf(
                "Neuer Raum: weicheres Laufen, Sitzen und Erreichen enger Bereiche",
            )
        ),
        Entry(
            "2.2.76",
            listOf(
                "Neuer Raum: Tippen am Rand/oben/unten und Laufen zu Zielen wieder zuverlässig",
            )
        ),
        Entry(
            "2.2.75",
            listOf(
                "Play-Anforderungen: Android 16 (API 36) und Billing Library 8",
            )
        ),
        Entry(
            "2.2.74",
            listOf(
                "Neuer Raum: Kamera-Fenster (schwarz) mit Scroll am Rand, weicheres Laufen",
            )
        ),
        Entry(
            "2.2.73",
            listOf(
                "Neuer Raum: Hintergrundbild wird wieder korrekt geladen",
            )
        ),
        Entry(
            "2.2.72",
            listOf(
                "Neuer Raum: Begleiter-Bild im Avatar, Reaktionsleiste wie in der Mal-Lobby",
            )
        ),
        Entry(
            "2.2.71",
            listOf(
                "Neuer Raum: eigene Räume aus dem Admin, laufen & sitzen ohne Malen",
            )
        ),
        Entry(
            "2.2.70",
            listOf(
                "Hochzeit: Laufen nur auf Grün, Umweg um Bänke, unsichtbare Sitze und Spawn",
            )
        ),
        Entry(
            "2.2.69",
            listOf(
                "Hochzeit: Admin-Raum-Layout gilt sofort im Trausaal (Laufen, Stopp, Sitze)",
            )
        ),
        Entry(
            "2.2.68",
            listOf(
                "Hochzeit: begehbare und blockierte Bereiche sowie Sitzplätze aus dem Admin-Raum-Editor",
            )
        ),
        Entry(
            "2.2.67",
            listOf(
                "Hochzeitskapelle: Trausaal sieht aus wie die Kapelle mit Altar, Bänken und Gang",
            )
        ),
        Entry(
            "2.2.66",
            listOf(
                "Hochzeit: Zeremonie-Lobby mit Gästen und Altar — zusätzlich zu Hochzeitsbild, Event und Random",
                "Verlobung: einmal gratis „Hochzeits-Lobby öffnen“, Coin-Skip erst nach je 10 Strichen",
            )
        ),
        Entry(
            "2.2.65",
            listOf(
                "Lootbox: Kauf-Bestätigen und Schalter wieder oberhalb der Navigationsleiste",
            )
        ),
        Entry(
            "2.2.64",
            listOf(
                "Itemshop: eigene Fix-Items und günstige Sticker wieder sichtbar und nach Preis sortiert",
            )
        ),
        Entry(
            "2.2.63",
            listOf(
                "Itemshop: Fix-Items (z. B. selbst angelegte Emojis) erscheinen wieder in der Liste, nicht nur über Suche",
            )
        ),
        Entry(
            "2.2.62",
            listOf(
                "Itemshop: alle Items (auch Custom) nach Preis sortiert",
                "Bug melden: „Hilfreich“ gibt sofort +10 Coins und schließt den Fall",
            )
        ),
        Entry(
            "2.2.61",
            listOf(
                "Stiftfarbe bleibt je Lobby (normal / Event / Random) — Wechsel in einer Lobby ändert die anderen nicht",
            )
        ),
        Entry(
            "2.2.60",
            listOf(
                "Sozial-Events: Event-Shop-Vorschau ohne überlappende Kacheln, Demnächst-Popup deckt das Menü ab, eigene Events wieder in der Vorschau",
            )
        ),
        Entry(
            "2.2.59",
            listOf(
                "Lootbox-Popup: Kauf-bestätigen-Schalter bleibt sichtbar (nicht unter dem Bildschirm)",
            )
        ),
        Entry(
            "2.2.58",
            listOf(
                "Bug melden (+10 Coins bei hilfreichen Meldungen) unter Konto — Abholen unter Sozial → Freunde",
            )
        ),
        Entry(
            "2.2.57",
            listOf(
                "Sozial-Events: Datum als TT.MM.JJJJ, schönere Event-Vorschau, feste Popup-Hintergründe",
                "Eigene Profil-Hintergründe: Animation bleibt nach Laden zuverlässig sichtbar",
            )
        ),
        Entry(
            "2.2.56",
            listOf(
                "Nächtlicher Wartungsmodus 02:59–03:09 mit Timer, Herz-Hüpfer-Minispiel und 2-Coin-Dankeschön",
            )
        ),
        Entry(
            "2.2.55",
            listOf(
                "Lootbox-Popup Vollbild mit Riss-/Explosions-Animation",
                "Event-Wizard: Item-Bilder, bis zu 6 Belohnungen, Kalender nur noch Wizard/Löschen",
            )
        ),
        Entry(
            "2.2.54",
            listOf(
                "Lootbox-Kachel mit eigenem Premium-Bild",
            )
        ),
        Entry(
            "2.2.53",
            listOf(
                "Event-Vorschau Vollbild mit Item-Preisen; Date-Night Fr 18–Sa 1 Uhr; neuer Event-Wizard",
            )
        ),
        Entry(
            "2.2.52",
            listOf(
                "Event-Items sind fest von der Lootbox ausgeschlossen",
            )
        ),
        Entry(
            "2.2.51",
            listOf(
                "Event-Shop-Kachel mit allen Event-Items; Admin: Event-Sticker/Emoji/Hintergrund; Hintergrund-Animationen bleiben beim Ausrüsten",
            )
        ),
        Entry(
            "2.2.50",
            listOf(
                "Update-Hinweis erst, wenn die Version wirklich im Play Store verfügbar ist",
            )
        ),
        Entry(
            "2.2.49",
            listOf(
                "Event-Vorschau unter Demnächst; Event-Begleiter im Shop nur während Events; Begleiter mehrfach kaufbar wie Sticker",
            )
        ),
        Entry(
            "2.2.48",
            listOf(
                "Probezeit 30 Sekunden mit Google-Popup; Marktplatz/Itemshop beim App-Start vorgeladen; Bildschirm bleibt in der App wach",
            )
        ),
        Entry(
            "2.2.47",
            listOf(
                "Einladungs-Gast: Zeichnung wird beim Google-Gate/Zurück entfernt; nach Login zurück in die Lobby oder eigene Lobby starten",
            )
        ),
        Entry(
            "2.2.46",
            listOf(
                "Tutorial-Inventar: nur Hund-Sticker; andere Tabs gesperrt; Hund größer und mit Pfeil markiert",
            )
        ),
        Entry(
            "2.2.45",
            listOf(
                "Coinshop wieder mit allen Paketen; 99‑ct-Kachel voll; Lootbox-Ergebnis unter dem Geschenk — weiter tippen möglich",
            )
        ),
        Entry(
            "2.2.44",
            listOf(
                "Tutorial-Profil 1:1 wie Profil gestalten inkl. echtem Inventar; keine grauen Coachmark-Flächen über der Zeichnung",
            )
        ),
        Entry(
            "2.2.43",
            listOf(
                "Markt: Coinshop und Lootbox nebeneinander; Lootbox-Bestand nach Kauf sofort sichtbar",
            )
        ),
        Entry(
            "2.2.42",
            listOf(
                "Team-Geschenke nur einmal als Hinweis — nicht dauerhaft unter Freunde; Absender immer „Team“",
            )
        ),
        Entry(
            "2.2.41",
            listOf(
                "Tutorial näher am echten Malen/Profil; „Ich habe ein Konto“; Freunde nach Google-Login wiederhergestellt",
            )
        ),
        Entry(
            "2.2.40",
            listOf(
                "Nach Speicher löschen wieder Name-Start statt toter Einladung; Lobby nicht gefunden nur mit Zurück",
            )
        ),
        Entry(
            "2.2.39",
            listOf(
                "Ohne Google: keine untere Leiste; Abmelden leert Inventar; Trial-Zurück startet Onboarding",
            )
        ),
        Entry(
            "2.2.38",
            listOf(
                "Lobby-Reihenfolge mit Google-Konto speichern; Käufe und Freunde reagieren sofort",
            )
        ),
        Entry(
            "2.2.37",
            listOf(
                "Einladungs-Link zeigt wieder Beitreten-Popup statt nur Home",
            )
        ),
        Entry(
            "2.2.36",
            listOf(
                "Einladungs-Link öffnet die App direkt; Beitreten mit Vorschau — Landing ohne Scrollen",
            )
        ),
        Entry(
            "2.2.35",
            listOf(
                "Neues Onboarding: zeichnen, Profil-Sticker und Google am Ende — dann kurze Home-Tipps",
            )
        ),
        Entry(
            "2.2.34",
            listOf(
                "Beim Einladen: aktuelle Leinwand in der Link-Vorschau (WhatsApp & Co.)",
            )
        ),
        Entry(
            "2.2.33",
            listOf(
                "Einladungs-Link zeigt Host und Leinwand; 60 Sekunden Probezeichnen ohne Anmeldung",
            )
        ),
        Entry(
            "2.2.32",
            listOf(
                "Coin-Käufe zusätzlich mit Geräteprüfung abgesichert",
            )
        ),
        Entry(
            "2.2.31",
            listOf(
                "App wieder flüssiger: Freunde, Markt, Käufe und Lootbox ohne Wartezeiten",
            )
        ),
        Entry(
            "2.2.30",
            listOf(
                "Radierer trifft nur noch die eingestellte Pinselbreite",
            )
        ),
        Entry(
            "2.2.29",
            listOf(
                "Farbe und Pinseldicke getrennt; Vorlagen wie Leinwand",
                "Radierer synct atomar — kein Löschen und Nachzeichnen bei anderen",
                "Galerie: Löschen oben neben Schließen; Auth-Code 40 Sekunden",
            )
        ),
        Entry(
            "2.2.28",
            listOf(
                "Radierer: Sync ohne Flackern, Dicke folgt dem Pinsel",
                "Gemeinsame Jahreszeiten-Farbpalette für Leinwand und Vorlagen",
                "Galerie-Popup und Löschen-Button stabil; mehr Striche auf Leinwand und Vorlagen",
            )
        ),
        Entry(
            "2.2.27",
            listOf(
                "Profil-Leinwand: bis zu 75 Sticker",
            )
        ),
        Entry(
            "2.2.26",
            listOf(
                "Admin-Web: Zugang per App-Auth-Code statt Browser-Passwort",
                "Admin-Menü: neuer Bereich Auth zum Authentifizieren",
            )
        ),
        Entry(
            "2.2.25",
            listOf(
                "Event-Animation fullscreen auf Home, Sozial, Markt und Zahnrad",
                "Event-Symbol neben +: Infos und Direktlink zu Events",
            )
        ),
        Entry(
            "2.2.24",
            listOf(
                "Event-Abstimmung: max. 100 Bilder, faire Verteilung, Restanzeige",
            )
        ),
        Entry(
            "2.2.23",
            listOf(
                "Event-Lobby: Schieberegler zum Verlassen, klarer Timer",
                "Seichte Menü-Animationen, schnellere Lootbox, Coins-Symbol im Shop",
            )
        ),
        Entry(
            "2.2.21",
            listOf(
                "Familien-Lobbys bleiben beim Sync erhalten",
                "Event-Lobbys sind Solo und vermischen sich nicht mehr mit normalen Lobbys",
            )
        ),
        Entry(
            "2.2.20",
            listOf(
                "Itemshop: Demnächst-Vorschau in allen Kategorien",
                "Event-Lobbys: keine Duplikate mehr, Begriff wird korrekt angezeigt",
            )
        ),
        Entry(
            "2.2.0",
            listOf(
                "Lobby-Einladung ohne Fehlermeldung, Mitspieler können einladen",
                "Bild-Begleiter korrekt in Freundesliste und beim Kraulen",
                "Münzglas zeigt Coins auch auf Fremdprofilen",
                "Eigene Hintergründe wieder im Inventar",
            )
        ),
        Entry(
            "2.1.9",
            listOf(
                "Marktplatz: Itemname und „DEINS“ bleiben in einer Zeile",
            )
        ),
        Entry(
            "2.1.8",
            listOf(
                "Geänderte Item-Namen aus dem Admin greifen jetzt zuverlässig",
            )
        ),
        Entry(
            "2.1.7",
            listOf(
                "Tagesaufgaben: klare Anleitung, was du tun musst",
                "Belohnung der Tagesaufgaben sichtbar (Coins einstellbar)",
            )
        ),
        Entry(
            "2.1.6",
            listOf(
                "Admin-Itemnamen erscheinen sofort in Inventar und Shop",
            )
        ),
        Entry(
            "2.1.5",
            listOf(
                "Day-Streak steigt wieder zuverlässig jeden aktiven Tag",
                "Day-Streak als Feuer-Kreis auf dem Profil platzierbar",
                "Push-Sprüche mit wählbarem Tap-Ziel (Admin)",
            )
        ),
        Entry(
            "2.1.4",
            listOf(
                "Profil gestalten: nach Platzieren aus dem Inventar zurück mit Speichern oder ✕",
                "Inventar: ausgegraute Items unten, freie Items oben",
                "Team-Verwarnungen unter Sozial · Freunde",
                "Klarere Item-Namen (z. B. Tiger statt Gesicht)",
            )
        ),
        Entry(
            "2.1.3",
            listOf(
                "Vorlagen & Leinwand: überall gleiche Größe ohne Verzerrung (9:16)",
                "Platzierte Vorlagen bei allen Mitspielern korrekt dargestellt",
            )
        ),
        Entry(
            "2.1.2",
            listOf(
                "Suche in Itemshop, Marktplatz & Inventar zuverlässiger (auch mehrere Wörter)",
                "Items mit klaren deutschen Namen (z. B. Grüner Baum, Rotes Herz)",
                "Inventar: platzierte Items sichtbar, ausgegraut wenn keines frei",
            )
        ),
        Entry(
            "2.1.1",
            listOf(
                "Papierkorb: Mitspieler werden wieder zuverlässig gefragt",
                "Vorlagen in der Übersicht im richtigen Hochformat",
                "Freunde in die Lobby einladen (Hinweis unter Sozial)",
                "Marktplatz & Inventar ohne kurzes Flackern",
                "Baum-Sticker wieder im Shop; Besitz weiter am Markt anbietbar",
            )
        ),
        Entry(
            "2.1",
            listOf(
                "Inventar: freie Items klar getrennt von platzierten/ausgerüsteten",
                "Eigene Composer-Bilder ohne technische IDs in Markt & Hinweisen",
                "Neuer Begleiter: Hexe (🧙) für 500 Coins",
                "Vorlagen: gesamte Zeichenfläche nutzbar",
            )
        ),
        Entry(
            "1.9.9944",
            listOf(
                "Release-Build kleiner und etwas besser gegen Reverse-Engineering abgesichert",
            )
        ),
        Entry(
            "1.9.9943",
            listOf(
                "Querformat: Leinwand und Vorlagen bleiben im Hochformat (Letterbox)",
            )
        ),
        Entry(
            "1.9.9942",
            listOf(
                "Sozial: neuer Tab „Bilder“ mit Splash-Bild und Community-Fotos",
                "Erfolge-Hinweis bleibt nach dem Ansehen weg (auch nach App-Neustart)",
                "Begleiter & ältere App-Versionen arbeiten zuverlässiger zusammen",
            )
        ),
        Entry(
            "1.9.9937",
            listOf(
                "Update-Hinweis erscheint wieder, wenn im Play Store eine neue Version wartet",
                "Gästebuch: Absenden-Button höher und leichter erreichbar",
                "Reaktionsleiste: bei vielen Emojis kompakter — Fertig bleibt sichtbar",
            )
        ),
        Entry(
            "1.9.9930",
            listOf(
                "Vorlagen: längere Zeichnungen speichern & bearbeiten",
                "Itemshop/Inventar: Suche, Preis-Sortierung, zeitlich begrenzte Angebote",
                "Marktplatz: Angebote bis 10.000 Coins",
            )
        ),
        Entry(
            "1.9.9929",
            listOf(
                "Erfolge: Item-Belohnungen zuverlässig (u. a. Kapelle nach Heirat)",
                "Heirats-Boni (Ehering, Kapelle) sind nicht handelbar",
            )
        ),
        Entry(
            "1.9.9928",
            listOf(
                "Freunde: falsche „Scheidungs-Wartezeit“-Karte entfernt",
            )
        ),
        Entry(
            "1.9.9927",
            listOf(
                "Verheiratet: Herzen & Ringe regnen auf dem Profil",
                "Eigenes Profil: keine Freundschafts-/Kraul-Buttons mehr",
            )
        ),
        Entry(
            "1.9.9926",
            listOf(
                "Tutorial neu gestaltet — inkl. Heirat & voller Hochzeitsleinwand",
                "Profil-Elemente behalten ihre Größe nach dem Speichern",
                "Heiraten: Level 100 gratis, Ablauf 7+7 Tage klar erklärt",
            )
        ),
        Entry(
            "1.9.95",
            listOf(
                "Tutorial neu gestaltet — inkl. Heirat & voller Hochzeitsleinwand",
                "Profil-Elemente behalten ihre Größe nach dem Speichern",
                "Heiraten: Level 100 gratis, Ablauf 7+7 Tage klar erklärt",
            )
        ),
        Entry(
            "1.9.94",
            listOf(
                "Random-Lobby zuverlässiger",
                "Hinweise: Freundesanfragen, Marktplatz-Verkäufe, App-Icon-Zahl",
                "Lobby-Kacheln leuchten bei neuer Zeichnung",
                "Lootbox: faire Chancen um 10 Coins",
            )
        ),
        Entry(
            "1.9.93",
            listOf(
                "Coinshop: größere Pakete (ca. 30/50/100 €), Mengenwahl, Angebote oben",
                "Vorlagen: ganze Zeichenfläche, Pinselstärke und Ansatz korrigiert",
            )
        ),
        Entry(
            "1.9.92",
            listOf(
                "Hauptmenü: Random-Lobby (Zufall, max. 5, blaue Kachel)",
                "Itemshop: Lootbox für 10 Coins mit Öffnen-Animation",
                "Vorlagen: Pinseldicke als Linie sichtbar",
                "Galerie: Löschen mit Hinweis bei veröffentlichten Bildern",
            )
        ),
        Entry(
            "1.9.91",
            listOf(
                "App-Start: öffentliches Ladebild zwei Sekunden sichtbar",
            )
        ),
        Entry(
            "1.9.90",
            listOf(
                "App-Start: Zwei-Handy-Animation beim Laden",
                "Ladebilder abwechslungsreicher",
            )
        ),
        Entry(
            "1.9.89",
            listOf(
                "Marktplatz und Inventar laufen ruhiger",
                "App-Start: abwechslungsreichere Ladebilder",
            )
        ),
        Entry(
            "1.9.88",
            listOf(
                "App-Start: kein langes Schwarzbild mehr vor dem Ladebild",
            )
        ),
        Entry(
            "1.9.87",
            listOf(
                "Vorlage zeichnen: Schwamm-Radierer wie auf der Leinwand",
            )
        ),
        Entry(
            "1.9.86",
            listOf(
                "Vorlage zeichnen: Vollbild, Rückgängig/Speichern oben, Schließen nur per ✕",
            )
        ),
        Entry(
            "1.9.85",
            listOf(
                "Profil gestalten: Avatar, Name, Sticker und Glas unabhängig verschieben",
                "Name-Box passt sich der Textbreite an",
            )
        ),
        Entry(
            "1.9.84",
            listOf(
                "Marktplatz: Angebot über Item wählen + Preis, ohne Tausch",
                "Meine Angebote: Zurück zur Übersicht",
            )
        ),
        Entry(
            "1.9.83",
            listOf(
                "Viele neue Hintergründe mit Animationen (Nordlicht, Vulkan, Sakura …)",
                "Itemshop: mehr Begleiter (alle Tiere) und mehr Reaktions-Emojis",
                "Vorlagen zeichnen: Pinseldicke einstellbar",
            )
        ),
        Entry(
            "1.9.82",
            listOf(
                "Itemshop: 500+ Sticker aus allen Kategorien, mit seltenen teuren Stufen",
                "Erfolgs-Sticker nur über Erfolge — nicht im Shop",
            )
        ),
        Entry(
            "1.9.81",
            listOf(
                "Seltene Erfolge belohnen mit Items (anklickbare Vorschau)",
                "Leinwand-Hinweise mittig, kurz und ohne Zeichnen zu blockieren",
            )
        ),
        Entry(
            "1.9.80",
            listOf(
                "Update-Hinweis auf der Leinwand wie im Hauptmenü lesbar",
            )
        ),
        Entry(
            "1.9.79",
            listOf(
                "Vorlagen: Löschen per Papierkorb mit Bestätigung",
            )
        ),
        Entry(
            "1.9.78",
            listOf(
                "Hauptmenü: Plus-Kreis für Neue Lobby / Beitreten",
                "Vorlagen: Platzierung sitzt genau dort, wo du sie hinziehst",
            )
        ),
        Entry(
            "1.9.77",
            listOf(
                "Markt-Kacheln mit Badges & Coinshop-Bildern",
                "Menü aufgeräumt, Inventar-Rucksack, Farbe live synchron",
            )
        ),
        Entry(
            "1.9.76",
            listOf(
                "Leinwand: Vorlagen statt Spiele — zeichnen, speichern, platzieren",
                "Vorlagen geräteübergreifend + neue Erfolge",
            )
        ),
        Entry(
            "1.9.75",
            listOf(
                "Markt-Menü: Kacheln mit Namen oben und Vorschau-Angeboten",
            )
        ),
        Entry(
            "1.9.74",
            listOf(
                "Leinwand-Avatare: Begleiter bleibt zuverlässig sichtbar",
                "Marktplatz: Angebote in der Liste statt Popup",
                "Inventar: Stückzahlen, Sticker verbrauchen beim Platzieren",
            )
        ),
        Entry(
            "1.9.73",
            listOf(
                "Marktplatz: ein Item mit günstigstem Preis, dann alle Angebote",
            )
        ),
        Entry(
            "1.9.72",
            listOf(
                "Münzglas: max. 10 Coins pro Profil und Tag (0 Uhr MEZ)",
                "Marktplatz-Kauf & Erfolge-Claim robuster",
            )
        ),
        Entry(
            "1.9.71",
            listOf(
                "Hintergründe: Vorschau wie auf der Profil-Leinwand",
            )
        ),
        Entry(
            "1.9.70",
            listOf(
                "Marktplatz: gleiche Tab-Namen wie Itemshop & Inventar",
            )
        ),
        Entry(
            "1.9.69",
            listOf(
                "Inventar und Käufe stabiler",
            )
        ),
        Entry(
            "1.9.68",
            listOf(
                "Marktplatz und Shop zuverlässiger",
            )
        ),
        Entry(
            "1.9.66",
            listOf(
                "Erfolge & Tagesaufgaben: Coins selbst abholen — Punkt bei Sozial",
                "Itemshop & Marktplatz: Vorschau für alle Items",
            )
        ),
        Entry(
            "1.9.65",
            listOf(
                "Sozial: Freunde und Erfolge als Tabs unter der Überschrift",
            )
        ),
        Entry(
            "1.9.64",
            listOf(
                "Marktplatz, Inventar & Erfolge: einheitliche Darstellung ohne Textkürzung",
            )
        ),
        Entry(
            "1.9.63",
            listOf(
                "Marktplatz: Angebot erstellen zeigt nur Items zum gewählten Tab",
            )
        ),
        Entry(
            "1.9.62",
            listOf(
                "Leinwand: kein kurzes Aufblinken mehr durch Sync/Reconnect",
            )
        ),
        Entry(
            "1.9.61",
            listOf(
                "Einstellungen: Hilfe-Nachricht an das Team senden",
                "Hilfe-Anfragen kommen zuverlässiger beim Team an",
            )
        ),
        Entry(
            "1.9.60",
            listOf(
                "Itemshop & Inventar: klare Tabs Sticker, Hintergründe, Begleiter, Emojis",
                "Sticker-Shop erweitert (u. a. Schmetterling, Blumen, Käfer)",
            )
        ),
        Entry(
            "1.9.59",
            listOf(
                "Sozial: 100 Erfolge, Daily-Aufgaben und Day-Streak",
                "Marktplatz: Handel mit anderen Spielern (Angebote, Kauf, Tausch)",
            )
        ),
        Entry(
            "1.9.58",
            listOf(
                "Zeichnen in der Lobby: alle sehen die Striche zuverlässig live",
            )
        ),
        Entry(
            "1.9.57",
            listOf(
                "Meldung „anderes Gerät auf der Leinwand“ nur bei offener Leinwand",
            )
        ),
        Entry(
            "1.9.56",
            listOf(
                "Tutorial neu: schönes Onboarding mit Zeichnen — wird zur ersten Lobby",
                "Tutorial erneut ansehen speichert keine neue Lobby",
            )
        ),
        Entry(
            "1.9.55",
            listOf(
                "Live-Hinweise: Absender mit Spitznamen, sichtbar auch beim späteren App-Öffnen",
            )
        ),
        Entry(
            "1.9.54",
            listOf(
                "Itemshop: Begleiter Eule und Tiger",
            )
        ),
        Entry(
            "1.9.53",
            listOf(
                "Galerie: veröffentlichte Bilder erscheinen auf allen Geräten mit demselben Konto",
                "Leinwand: nur ein Gerät gleichzeitig — anderes Gerät bleibt in der Lobby",
            )
        ),
        Entry(
            "1.9.52",
            listOf(
                "Kleinere Bugfixes und Stabilität",
            )
        ),
        Entry(
            "1.9.51",
            listOf(
                "Kleinere Bugfixes",
            )
        ),
        Entry(
            "1.9.50",
            listOf(
                "Spitzname nur einmal wählbar — mit Bestätigung im Tutorial",
                "Konto löschen funktioniert wieder zuverlässig",
            )
        ),
        Entry(
            "1.9.49",
            listOf(
                "Update-Hinweis: Text gut lesbar auf dunkler Karte",
            )
        ),
        Entry(
            "1.9.48",
            listOf(
                "Lobby-Glocke (Impulse) wird gespeichert und geräteübergreifend synchronisiert",
            )
        ),
        Entry(
            "1.9.47",
            listOf(
                "Verlassene Lobbys bleiben weg — kein Zurückholen nach Update oder Appstart",
            )
        ),
        Entry(
            "1.9.46",
            listOf(
                "Inventar & Menüs: mehr Abstand im Button-Text, bessere Anpassung an kleine Displays",
            )
        ),
        Entry(
            "1.9.45",
            listOf(
                "Sozial: Plus-Kachel zum Freunde-Suchen per Spitzname",
                "Spitznamen sind einzigartig — keine Doppelvergabe mehr",
            )
        ),
        Entry(
            "1.9.44",
            listOf(
                "Profil: „Avatar gestalten“ entfernt — Avatar zeigt nur den Begleiter",
            )
        ),
        Entry(
            "1.9.43",
            listOf(
                "Lobby-Kacheln: „Verbunden“ flackert nicht mehr bei kurzen Netz-Blips",
            )
        ),
        Entry(
            "1.9.42",
            listOf(
                "Begleiter kraulen: 2 Sekunden streichen, Hand-Anzeige, dann +1 Coin",
            )
        ),
        Entry(
            "1.9.41",
            listOf(
                "Update-Check bei Tab- und Menüwechseln — Käufe bleiben gespeichert",
                "Shop und Inventar speichern zuverlässiger",
            )
        ),
        Entry(
            "1.9.40",
            listOf(
                "Update-Hinweis: Hintergrund weiß verschwommen",
            )
        ),
        Entry(
            "1.9.39",
            listOf(
                "Begleiter mittig im Avatar — Home, Sozial, Profil & Leinwand",
                "Lobby-Einladung: Freunde in Sozial-Reihenfolge einladen",
            )
        ),
        Entry(
            "1.9.38",
            listOf(
                "Google-Konto: Lobbys, Einstellungen, Ruhezeiten & Inventar geräteübergreifend",
                "Profilgestaltung und Reaktionsleiste synchronisieren mit dem Konto",
            )
        ),
        Entry(
            "1.9.37",
            listOf(
                "Kleinere Bugfixes und Verbesserungen",
                "Hinweise erscheinen klarer und kürzer",
            )
        ),
        Entry(
            "1.9.36",
            listOf(
                "Sozial: Freunde, Anfragen, Reihenfolge — Galerie im Inventar",
                "Begleiter kraulen auf Fremdprofilen: süße Animation, +1 Coin/Tag",
            )
        ),
        Entry(
            "1.9.35",
            listOf(
                "Inventar-Emojis füllen die Reaktionsleiste (auch Ersetzen bei 8)",
                "Hintergründe, Sticker & Begleiter im Itemshop — Begleiter am Avatar",
            )
        ),
        Entry(
            "1.9.34",
            listOf(
                "Home: Lobbies per Drag über andere hinweg verschieben",
            )
        ),
        Entry(
            "1.9.33",
            listOf(
                "Bildschirm bleibt an, solange die App offen ist",
            )
        ),
        Entry(
            "1.9.32",
            listOf(
                "Inventar: Emojis-Tab, Zurück aus Markt behält Kontext",
                "Münzglas: bis 10 Coins/Tag an andere spenden (0 Uhr MEZ)",
            )
        ),
        Entry(
            "1.9.31",
            listOf(
                "Profil: Standard-Name wieder sichtbar auf der Leinwand",
            )
        ),
        Entry(
            "1.9.30",
            listOf(
                "Profil: Auswahl-Handles bleiben mittig auf den Ecken",
            )
        ),
        Entry(
            "1.9.29",
            listOf(
                "Profil-Laden: Pinsel malt Herz statt Standard-Flash",
            )
        ),
        Entry(
            "1.9.28",
            listOf(
                "Profil-Name immer vollständig (schrumpfende Schrift)",
                "Pinsel: Farbe antippen schließt sofort — Fertig nur für Dicke",
            )
        ),
        Entry(
            "1.9.27",
            listOf(
                "Fremde Profile zeigen wieder das gespeicherte Layout",
            )
        ),
        Entry(
            "1.9.26",
            listOf(
                "Inventar wie Profil-Truhe — Marktplatz, Itemshop, Platzieren mit Bestätigung",
            )
        ),
        Entry(
            "1.9.25",
            listOf(
                "Pinsel-Menü ohne Abdunklung dahinter",
            )
        ),
        Entry(
            "1.9.24",
            listOf(
                "Pinsel-Menü ohne Scrollen — skaliert nach Bildschirmgröße",
            )
        ),
        Entry(
            "1.9.23",
            listOf(
                "Profil: Auswahl-Handles sitzen mittig auf den Ecken des Rahmens",
            )
        ),
        Entry(
            "1.9.22",
            listOf(
                "Pinsel-Menü: „Fertig“ auf Pixel/Samsung nicht mehr unter der Gesture-Leiste",
            )
        ),
        Entry(
            "1.9.21",
            listOf(
                "Pinsel-Menü: „Fertig“ sitzt sicher über der System-/Gesture-Leiste",
            )
        ),
        Entry(
            "1.9.20",
            listOf(
                "Pinsel-Menü: „Fertig“ bleibt auf kleinen Displays sichtbar",
            )
        ),
        Entry(
            "1.9.19",
            listOf(
                "Profil: Elemente bis an den Rand ziehen, Name bleibt vollständig sichtbar",
            )
        ),
        Entry(
            "1.9.18",
            listOf(
                "Profil-Leinwand: stabiles Verschieben, Standard wie Bild, kein Element-Reset",
                "Truhe ohne Stimmung/Text; Markt sagt Begleiter statt Pets",
            )
        ),
        Entry(
            "1.9.17",
            listOf(
                "Profil: Vergrößern/Verkleinern skaliert jetzt Sticker und Inhalt mit",
            )
        ),
        Entry(
            "1.9.16",
            listOf(
                "Profil-Leinwand komplett: Truhe, Stimmung, Begleiter, Münzglas, Text",
                "Hintergründe mit Effekten, Schrift-Styles und Wiederherstellen",
            )
        ),
        Entry(
            "1.9.15",
            listOf(
                "Profil-Leinwand: Sticker, Hintergründe und Truhe",
                "Profil vom Menü-Avatar und von Leinwand-Avataren öffnen",
            )
        ),
        Entry(
            "1.8.99",
            listOf(
                "Schwamm ohne graues Kästchen — gelb nur wenn aktiv",
            )
        ),
        Entry(
            "1.8.98",
            listOf(
                "Etwas mehr Abstand zwischen Reaktions-Emojis",
            )
        ),
        Entry(
            "1.8.97",
            listOf(
                "Reaktion: Daumen hoch statt Haken",
            )
        ),
        Entry(
            "1.8.96",
            listOf(
                "Radierer unten wieder als Schwamm-Emoji",
            )
        ),
        Entry(
            "1.8.95",
            listOf(
                "Reaktions-Emojis im 🙂-Menü wieder sichtbar",
            )
        ),
        Entry(
            "1.8.94",
            listOf(
                "Galerie: Veröffentlichen mit AGB, Markierung & Zurücknehmen",
                "Leinwand: Reaktionen oben rechts, kein Öffentlich-Button",
            )
        ),
        Entry(
            "1.8.93",
            listOf(
                "Profil-Button unter Konto entfernt",
            )
        ),
        Entry(
            "1.8.92",
            listOf(
                "Einstellungen unter Konto — inkl. Ruhezeiten",
                "Konto löschen nur mit 10-Sekunden-Schieberegler",
            )
        ),
        Entry(
            "1.8.91",
            listOf(
                "Stimmungs-Hinweise nur noch alle 12 Stunden",
            )
        ),
        Entry(
            "1.8.90",
            listOf(
                "Alle Zeichner bleiben ausgegraut sichtbar, solange ihre Striche da sind",
            )
        ),
        Entry(
            "1.8.89",
            listOf(
                "Update-Schleife behoben — echte 1.8.89-APK statt falscher Datei",
            )
        ),
        Entry(
            "1.8.88",
            listOf(
                "Verlassen räumt den Slot wirklich — wieder mit + einladen",
                "Verlassen auf der Leinwand: ausgegraut statt „kurz offline“",
            )
        ),
        Entry(
            "1.8.87",
            listOf(
                "Lobby verlassen: Slot im Menü verschwindet sofort",
                "Wer schon gemalt hat, bleibt ausgegraut auf der Leinwand",
                "Avatar antippen: Profil sehen und Person melden (mit Galerie-Screenshot)",
            )
        ),
        Entry(
            "1.8.86",
            listOf(
                "Lobby bleibt zusammen — Host wechselt nicht mehr bei kurzem Drop",
                "WebSocket stabiler (HTTP/1.1) — gegenseitig sehen und malen",
            )
        ),
        Entry(
            "1.8.85",
            listOf(
                "Gemeinsames Malen wieder stabil",
                "Alle in derselben Lobby sehen sich und die Striche wieder",
            )
        ),
        Entry(
            "1.8.83",
            listOf(
                "Keine Verbindungs-Flackerei mehr in größeren Lobbys",
            )
        ),
        Entry(
            "1.8.82",
            listOf(
                "Gemeinsame Lobby wieder zuverlässig — niemand hängt mehr allein",
            )
        ),
        Entry(
            "1.8.81",
            listOf(
                "Alle Lobby-Mitglieder immer in Kachel und Avataren sichtbar",
            )
        ),
        Entry(
            "1.8.80",
            listOf(
                "Lobby-Reconnect stabil — niemand fliegt mehr unnötig raus",
                "Host-Wechsel auch bei Verbindungsabbruch",
                "Galerie: alle App-Bilder, unabhängig vom Konto",
            )
        ),
        Entry(
            "1.8.79",
            listOf(
                "Zeichenfarbe und Avatare bleiben synchron",
                "Lobby-Plätze zeigen nur noch echte Mitglieder",
                "Ruhezeiten: Uhr im Hauptmenü, Mo–So komplett still",
            )
        ),
        Entry(
            "1.8.78",
            listOf(
                "Avatare unten zeigen immer die echte Zeichenfarbe",
            )
        ),
        Entry(
            "1.8.77",
            listOf(
                "Leinwand bleibt beim Malen stabil — kein schwarzer Bildschirm mehr",
            )
        ),
        Entry(
            "1.8.76",
            listOf(
                "Öffentlich teilen: Lobby-Mitglieder ohne Leinwand werden nicht gefragt",
            )
        ),
        Entry(
            "1.8.75",
            listOf(
                "Öffentlich teilen: nur wer auf der Leinwand online ist, muss zustimmen",
            )
        ),
        Entry(
            "1.8.74",
            listOf(
                "Öffentlich teilen: Host kann feststeckende Abstimmung neu starten",
            )
        ),
        Entry(
            "1.8.73",
            listOf(
                "Leinwand leeren: zwei Ja reichen, egal wie viele in der Lobby sind",
            )
        ),
        Entry(
            "1.8.72",
            listOf(
                "Tutorial jederzeit unter Konto erneut ansehen",
            )
        ),
        Entry(
            "1.8.71",
            listOf(
                "Tutorial erklärt die Galerie",
            )
        ),
        Entry(
            "1.8.70",
            listOf(
                "Galerie: Löschen entfernt Bilder wirklich",
            )
        ),
        Entry(
            "1.8.69",
            listOf(
                "Galerie: Teilen-Button weiter oben, immer gut erreichbar",
            )
        ),
        Entry(
            "1.8.68",
            listOf(
                "Galerie ohne Berechtigungsabfrage, Mehrfachauswahl und Löschen",
            )
        ),
        Entry(
            "1.8.67",
            listOf(
                "Galerie im Hauptmenü: gespeicherte Momente ansehen und teilen",
            )
        ),
        Entry(
            "1.8.66",
            listOf(
                "Gratis-Lobby: 1 Einladung frei, weitere Plätze je 5 Coins",
            )
        ),
        Entry(
            "1.8.65",
            listOf(
                "Mal-Vorschau in der Benachrichtigung erst nach 5 Sekunden Ruhe",
            )
        ),
        Entry(
            "1.8.64",
            listOf(
                "Leinwand leeren: andere werden gefragt, du malst weiter — erst bei Zustimmung aller",
            )
        ),
        Entry(
            "1.8.63",
            listOf(
                "Lobby-Plätze zeigen den Namen statt „Online“",
            )
        ),
        Entry(
            "1.8.62",
            listOf(
                "Runder Online-Kreis um Avatare — Beitritt nur einmal melden",
            )
        ),
        Entry(
            "1.8.61",
            listOf(
                "Öffentlich teilen: andere werden gefragt, du malst weiter — Publish erst wenn alle zustimmen",
            )
        ),
        Entry(
            "1.8.60",
            listOf(
                "Radierer wischt nur die Stelle weg, nicht den ganzen Strich",
            )
        ),
        Entry(
            "1.8.59",
            listOf(
                "Lobby-Namen passen besser ins Menü — max. 16 Zeichen",
            )
        ),
        Entry(
            "1.8.58",
            listOf(
                "Enter auf der Tastatur bestätigt Eingaben",
            )
        ),
        Entry(
            "1.8.57",
            listOf(
                "Leinwand direkt aus Einladen und Lobby-Teilen öffnen",
            )
        ),
        Entry(
            "1.8.56",
            listOf(
                "Öffentliche Bilder können gemeldet werden",
            )
        ),
        Entry(
            "1.8.55",
            listOf(
                "Leinwände bleiben nach Updates erhalten",
            )
        ),
        Entry(
            "1.8.54",
            listOf(
                "Radierer auf der Leinwand für die eigene Malerei",
                "Ruhigeres Lobby-Menü ohne „malt“-Hinweis",
            )
        ),
        Entry(
            "1.8.53",
            listOf(
                "Öffentliche Bilder klar als Community-Leinwand von anderen gekennzeichnet",
            )
        ),
        Entry(
            "1.8.52",
            listOf(
                "Lebendige Nähe pro Lobby per Glocke (standardmäßig aus)",
                "Lobby-Namen syncen für alle",
                "Leinwand-Dock und Farbwahl auf kleinen Displays zuverlässiger",
            )
        ),
        Entry(
            "1.8.45",
            listOf(
                "Einladungs-Vorschau mit wechselnden, warmen Sprüchen",
            )
        ),
        Entry(
            "1.8.44",
            listOf(
                "Google-Verknüpfung führt getrennte Konten wieder zusammen (Coins & Lobbys)",
            )
        ),
        Entry(
            "1.8.43",
            listOf(
                "Hinweise wie Rückgängig nicht mehr über den Avataren auf der Leinwand",
            )
        ),
        Entry(
            "1.8.42",
            listOf(
                "Schöneres, schlichtes Popup zum Leinwand leeren",
            )
        ),
        Entry(
            "1.8.41",
            listOf(
                "Gemeinsame Lobby wieder zuverlässig (Zeichnen sichtbar)",
                "Einladen öffnet wieder ein klares Fenster mit Link",
            )
        ),
        Entry(
            "1.8.40",
            listOf(
                "Konto inkl. Google-Verknüpfung unter Konto löschen",
            )
        ),
        Entry(
            "1.8.39",
            listOf(
                "Tutorial-Texte weicher und klarer",
            )
        ),
        Entry(
            "1.8.38",
            listOf(
                "Start mit Google-Anmeldung, danach eigener Spitzname und kurzes Tutorial",
            )
        ),
        Entry(
            "1.8.37",
            listOf(
                "Viele neue warme Impuls-Texte statt leerem „LUV“ in Hinweisen",
            )
        ),
        Entry(
            "1.8.36",
            listOf(
                "Google-Konto-Sicherung produktiv für alle Nutzer",
            )
        ),
        Entry(
            "1.8.35",
            listOf(
                "Konto mit Google speichern und auf neuem Handy wiederherstellen",
                "Abmelden im Konto-Menü",
            )
        ),
        Entry(
            "1.8.34",
            listOf(
                "2–3 sanfte Tages-Impulse mit kurzen, warmen Texten",
            )
        ),
        Entry(
            "1.8.33",
            listOf(
                "Hinweise nur bei Beitritt, Zeichnen und Tagesbonus",
            )
        ),
        Entry(
            "1.8.32",
            listOf(
                "Update-Hinweis mit kurzem Überblick der Neuerungen",
            )
        ),
        Entry(
            "1.8.31",
            listOf(
                "Versionsverlauf im Konto",
            )
        ),
        Entry(
            "1.8.28",
            listOf(
                "Viele Minispiele auf der Leinwand",
                "Lobbys bleiben nach Updates erhalten",
                "Tagesbonus wird automatisch gutgeschrieben",
            )
        ),
        Entry(
            "1.8.3",
            listOf(
                "Neues schwebendes Dock auf der Leinwand mit Farbwahl unten",
            )
        ),
        Entry(
            "1.8.2",
            listOf(
                "In-App-Update: neue Versionen direkt in der App laden und installieren",
            )
        ),
        Entry(
            "1.8.0",
            listOf(
                "Einladen über Sitzplätze, Vorschau vor dem Beitreten",
                "Erste Lobby gratis, Extra-Plätze und weitere Lobbys gegen Coins",
            )
        ),
        Entry(
            "1.7.0",
            listOf(
                "Leinwand leeren kostet den Initiator 1 Coin",
                "Erste Lobby gratis, weitere für 5 Coins (bis zu 10 Lobbys)",
            )
        ),
        Entry(
            "1.6.9",
            listOf(
                "Tic-Tac-Toe synchron auf der Leinwand",
            )
        ),
        Entry(
            "1.6.6",
            listOf(
                "Impressum und AGB in der App",
            )
        ),
        Entry(
            "1.6.5",
            listOf(
                "Emoji-Reaktionen und freie Farben mit Live-Ummalen",
                "Dock-Steuerung auf der Sperrbildschirm-Leinwand",
            )
        ),
        Entry(
            "1.6.4",
            listOf(
                "Shop-Checkout führt zurück in die App",
            )
        ),
        Entry(
            "1.6.0",
            listOf(
                "Coins-System mit Tagesbonus und Gutscheinen",
                "Leinwand nur noch mit Zustimmung löschen",
                "Kurzes Tutorial beim ersten Start",
            )
        ),
        Entry(
            "1.5.0",
            listOf(
                "Mehrere Lobbys parallel",
                "Nicknames und Einladungslinks",
                "Bis zu mehreren Personen in einer Lobby",
            )
        ),
        Entry(
            "1.4.1",
            listOf(
                "Paar-Features, Landingpage und HTTPS-API",
                "Tipp-Punkte auf der Leinwand",
            )
        ),
        Entry(
            "1.2.0",
            listOf(
                "Erste LUV-Version: gemeinsam malen auf dem Sperrbildschirm",
            )
        ),
    )

    /**
     * Kurze Punkte fürs Update-Popup — max. [maxItems], kein Scroll nötig.
     * Nimmt die neueste Version (oder [versionName], falls vorhanden) und füllt ggf. auf.
     */
    fun teaserLines(versionName: String? = null, maxItems: Int = 3): List<String> {
        val preferred = versionName
            ?.let { name -> entries.firstOrNull { it.version == name } }
        val ordered = buildList {
            if (preferred != null) add(preferred)
            entries.filter { it !== preferred }.forEach { add(it) }
        }
        val out = ArrayList<String>(maxItems)
        for (entry in ordered) {
            for (line in entry.highlights) {
                val short = line.trim()
                if (short.isEmpty() || short in out) continue
                out += short
                if (out.size >= maxItems) return out
            }
        }
        return out
    }
}

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

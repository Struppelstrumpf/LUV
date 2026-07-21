# LUV — Agent-Handoff

> Lies diese Datei zuerst, wenn du an diesem Projekt weiterarbeitest.
> Sprache mit dem User: **Deutsch**. Antworten knapp und direkt.

**Stand:** App `com.luv.couple` · Version siehe `app/build.gradle.kts` (aktuell oft 2.2.x)  
**Produkt:** Paar-Zeichen-App — gemeinsame Leinwand (auch Lockscreen-Widget), Lobbys, Shop, Marktplatz, Events, Freunde/Hochzeit.

---

## Was ist LUV?

Android-App, mit der zwei (oder mehr) Personen **live auf einer gemeinsamen Leinwand zeichnen**. Einstieg oft über Einladungslink oder Code. Zusätzlich: Coin-Shop, Itemshop, Spieler-Marktplatz, Profil-Canvas, Tutorial, Erfolge, saisonale Events.

**Live-Stack**

| Teil | Wo | Öffentlich |
|------|-----|------------|
| Android-App | `app/` | Play + Sideload-APK |
| Node-API | `server/` (Docker Port 18780) | `https://reineke.pro/luv` |
| Web/Landing/Admin | `web/` | `https://reineke.pro/luv/` |
| APK + Update-Manifest | Deploy → Server | `https://reineke.pro/downloads/luv/` |

API-Base in `gradle.properties`: `luv.api.baseUrl=https://reineke.pro/luv`  
Client liest `BuildConfig.LUV_API_BASE_URL` über `LuvApiClient`.

---

## Top-Level

| Ordner | Inhalt |
|--------|--------|
| `app/` | Android-Modul (Kotlin/Compose) |
| `server/` | Express + WebSocket API, `store.js` Persistenz |
| `web/` | Landing, Join-Seiten, Admin-UI (`web/adm/`) |
| `scripts/` | Deploy & Ops; **Hauptweg:** `python scripts/full_deploy.py` |
| `docs/` | Notizen (Preise etc.) |
| `keystore/`, `secrets/` | Lokal — **nie committen** |
| `.cursor/rules/` | z. B. Deploy → Git-Backup Pflicht |

---

## Wo fängt man an? (Einstiegsdateien)

1. **`app/build.gradle.kts`** — `versionCode` / `versionName`
2. **`app/.../ui/LuvAppNav.kt`** — Navigation, Login, Lobby, Trial-Rejoin, Market-Warmup
3. **`app/.../net/LuvApiClient.kt`** — alle REST-Calls
4. **`app/.../net/PairConnectionService.kt`** — WebSocket / Live-Sync
5. **`app/.../lock/LockDrawActivity.kt`** — Zeichenfläche + Trial-Gate
6. **`server/src/index.js`** — API-Monolith
7. **`server/src/invite_trial.js`** — Invite-Landing + Trial-Dauer
8. **`scripts/full_deploy.py`** — APK + AAB + Server + Web + `version.json` + Git-Push

Changelog (öffentlich, in-App): `app/.../update/AppChangelog.kt`  
Updater: `app/.../update/AppUpdater.kt` liest remote `version.json`.

---

## Android-Paket `com.luv.couple`

### Root
| Datei | Rolle |
|-------|--------|
| `LuvApp.kt` | Application: Prefs, CanvasStore, Channels, Warmup |
| `MainActivity.kt` | Compose-Host, Deep Links, **Keep-Screen-On** (`onCreate`/`onResume`) |

### `ui/`
| Datei | Rolle |
|-------|--------|
| **`LuvAppNav.kt`** | Zentrale Orchestrierung: Tabs, Google-Login, Trial-Join/Rejoin, Tutorial-Finish, MarketHubCache.warm() |
| `screens/Screens.kt` | Home / Lobbys |
| `screens/TutorialScreens.kt` | Onboarding/Tutorial |
| `screens/MarketScreens.kt` | Markt-Tab; Objekt **`MarketHubCache`** (warm + latest) |
| `screens/PlayerMarketScreen.kt` | Spieler-Marktplatz |
| `screens/ProfileCanvasScreen.kt` | Profil gestalten |
| `screens/AccountScreens.kt` | Konto / Abmelden |
| `screens/SocialScreen.kt` | Freunde / Sozial |
| `screens/AdminHubScreen.kt` | Staff in der App |
| `security/PlayIntegrityGate.kt` | Play Integrity vor Signup/Google |
| `theme/Theme.kt` | Theme |

### `lock/` — Leinwand
| Datei | Rolle |
|-------|--------|
| **`LockDrawActivity.kt`** | Haupt-Canvas (auch Lockscreen); Trial-Popup nach Ablauf |
| `DrawingView.kt` | Striche / `inputBlocked` |
| `CanvasStore.kt` | Strokes pro Lobby |
| `LockScreenWidgetProvider.kt` | Widget → öffnet Canvas |
| `WidgetConfigureActivity.kt` | Widget → Lobby zuordnen |
| `TemplateUi.kt` | Vorlagen zum Vorzeichnen |
| `MemoryActivity.kt` | Memory/Galerie-Ansicht |

### `net/` — API & Session
| Datei | Rolle |
|-------|--------|
| `LuvApiClient.kt` | HTTP + WS-URL |
| `GoogleAuth.kt` | Google Sign-In |
| `AccountSession.kt` | Account-State, `trialExpired`, Economy-Blöcke |
| `PairConnectionService.kt` | Foreground-Service, WS-Reconnect |
| `PairProtocol.kt` / `PairSessionState.kt` | Protokoll / State |
| `NotificationBadges.kt` | Markt-/Sozial-Dots |
| `InstallReferrerJoin.kt` | Play-Referrer → Join-Code |

**Pending-Flags** (One-Shot zwischen Activities/Screens):

| Objekt | Bedeutung |
|--------|-----------|
| `PendingJoin` | Deep-Link / Invite-Code öffnen |
| `PendingInviteRejoin` | Nach Trial+Google zurück in dieselbe Lobby |
| `PendingOnboardingRestart` | Trial-Zurück: Session leeren → Tutorial von vorn |
| `PendingTutorialKeepAuth` | Trial-Gate + **neues** Google-Konto: Tutorial behalten, Session **nicht** löschen |
| `PendingShop` / `PendingMarketplace` / `PendingDeepLink` | Tabs/Screens aus Notify öffnen |
| `PendingSplashSkip` | Splash überspringen (z. B. aus Notification) |

### `data/`
| Datei | Rolle |
|-------|--------|
| `PrefsRepository.kt` | DataStore: Session, Lobbies, Inventar, Widgets, Tutorial-Flags |
| `Models.kt` | Lobby, Account, … |

### `shop/`
Client-Kataloge: `ShopCatalog.kt`, Labels, Emoji-Suche, Rotation. Server ist Quelle der Wahrheit für Preise/Käufe.

### `billing/`
`PlayBilling.kt` — Google Play IAP.

### `notify/`
`LuvAlertNotifier.kt`, Mood-Nudges, Partner-Stroke-Notify, Live-Proximity.

### `update/`
`AppChangelog.kt`, `AppUpdater.kt`.

---

## Server `server/src/`

| Modul | Rolle |
|-------|--------|
| **`index.js`** | Express + WS: Auth, Rooms, Economy, Staff, Trial-Endpoints |
| `store.js` | Persistenz (`data/luv-store.json`) |
| **`invite_trial.js`** | Invite-HTML/OG + **`TRIAL_DRAW_MS = 30_000`** (30 Sekunden) |
| `play_integrity.js` | Integrity-Check |
| `play_billing.js` | Kauf-Verifikation |
| `market.js` | Spieler-Marktplatz |
| `shop_catalog.js` / `shop_calendar.js` / `sticker_catalog.js` | Itemshop |
| `lootbox.js` | Lootbox |
| `marriage.js` | Freunde / Verlobung / Hochzeit |
| `achievements.js` / `daily_tasks.js` | Erfolge / Tagesaufgaben |
| `events.js` / `event_engine.js` | Saison-Events |
| `games.js` | Lobby-Spiele |
| `admin_web_auth.js` | Web-Admin Auth |

Wichtige Economy-Konstanten u. a. in `index.js`: `LOBBY_CREATE_COST = 4`, Start-Coins, Max-Lobbies, …

**Sideload / Play Integrity:** Google-Login verlangt Play Integrity außer Whitelist `SIDELOAD_LOGIN_EMAILS` (Env + Defaults in `index.js`). Typische Test-Mails: u. a. `xstruppelstrumpf@gmail.com`, weitere Team-Mails dort.

Trial-API (Auszug):
- `POST /v1/rooms/:code/trial-join`
- `POST /v1/rooms/:code/trial-exit` — Gast-Strokes weg + Kick; Client merkt Code in `PendingInviteRejoin`

---

## Wichtige Flows

### Invite-Trial (Probezeichnen)
1. Link `https://reineke.pro/luv/j/{CODE}` oder App-Deep-Link → `PendingJoin`.
2. Ohne Google: `LuvAppNav.trialJoinWithCode` → Server `trial-join` → `LockDrawActivity` mit `EXTRA_TRIAL_DRAW_UNTIL`.
3. Nach **30s**: Overlay „Probezeit vorbei“ / „Mit Google anmelden“; **`drawingView.inputBlocked = true`** (kein Zeichnen mehr).
4. **Bestehendes Konto** (`!auth.created` + echter Nickname): Tutorial fertig, `PendingInviteRejoin` → zurück in Lobby mit echtem Namen.
5. **Neues Konto**: `PendingTutorialKeepAuth` + `PendingInviteRejoin` → Name/Tutorial **mit Session**, danach Rejoin.
6. **Zurück** ohne Google: `trial-exit` + `PendingOnboardingRestart` (alles leeren → Tutorial).

### Google-Login (normal)
`LuvAppNav.completeGoogleLogin` / `connectGoogle` → Integrity → `LuvApiClient.authGoogle` → Prefs/AccountSession.

### Market-Hub-Cache
- `MarketHubCache` in `MarketScreens.kt`: `warm()` lädt `/v1/market/hub` (neueste Marktangebote + meistgekauft Itemshop).
- Wird in `LuvAppNav` bei **jedem App-Start** und bei `ON_START` aufgerufen — auch wenn User nur auf Home ist.
- Markt-Tab zeigt zuerst Cache, refreshed im Hintergrund.

### Keep-Screen-On
Solange die App im Vordergrund ist, soll der Bildschirm nicht von allein sperren:
- `MainActivity.keepScreenAwake()` + `onResume`
- `LuvAppNav` `view.keepScreenOn = true`
- Canvas/Memory ebenfalls mit Window-Flags  
Manuelles Sperren + Widget-Öffnen bleibt möglich.

### Lobby → Canvas
Prefs-Lobby → `openLobbyCanvas` → `LockDrawActivity` + `PairConnectionService` WS.

### Widget
`LockScreenWidgetProvider` → gebundene Lobby → Canvas. Config: `WidgetConfigureActivity`.

---

## Versionierung & Release

1. `versionCode` +1 und `versionName` in `app/build.gradle.kts`
2. Kurzen Eintrag oben in `AppChangelog.kt`
3. Deploy: `python scripts/full_deploy.py`  
   - baut **APK** (`assembleRelease`) und **AAB** (`bundleRelease`)  
   - uploaded Server-Src, Web, APK, `version.json`  
   - Caddy reload, Healthchecks  
   - **automatisch git commit + push** (Deploy-Backup)

Outputs:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

JDK: Deploy-Skript setzt `JAVA_HOME` auf Android-Studio-JBR (JDK 25 bricht Kotlin-DSL).

Regel: Nach jedem Deploy Git-Backup — siehe `.cursor/rules/deploy-git-backup.mdc`.

---

## Konventionen für Agenten

- **User-Sprache: Deutsch.**
- Keine unnötigen Markdown-Docs erzeugen — außer der User fragt (wie diese Datei).
- Keine Secrets/Keystores committen.
- Commits nur wenn User es will — **Ausnahme:** nach Deploy (Skript/Regel macht Backup).
- Frontend-Design: bestehende App-Optik beibehalten; keine generischen „AI-Purple“-Layouts auf bestehenden Screens.
- Scope klein halten: nur ändern, was die Aufgabe braucht.
- Trial-Dauer nur in `server/src/invite_trial.js` (`TRIAL_DRAW_MS`) ändern — Client bekommt `trialDrawUntil` vom Server.

---

## Typische Debug-Orte

| Problem | Zuerst schauen |
|---------|----------------|
| Invite / Probezeit | `invite_trial.js`, `LockDrawActivity` Trial-Gate, `LuvAppNav.trialJoin*` |
| Rejoin nach Google | `PendingInviteRejoin`, `PendingTutorialKeepAuth`, `tryInviteRejoin` |
| Zeichnen synct nicht | `PairConnectionService`, `CanvasStore`, WS in `index.js` |
| Markt lädt langsam | `MarketHubCache.warm`, `fetchMarketHub` |
| Google / Sideload | `PlayIntegrityGate`, `play_integrity.js`, `SIDELOAD_LOGIN_EMAILS` |
| Update-Schleife | `versionCode` in Gradle vs. APK vs. `version.json` |
| Prefs / Logout | `PrefsRepository.clearForLogout` |

---

## Kurz: „Ich will nur X“

| Aufgabe | Dateien |
|---------|---------|
| UI-Flow / Tabs / Login | `LuvAppNav.kt` |
| Zeichenfläche | `LockDrawActivity.kt`, `DrawingView.kt` |
| Neuer API-Endpoint | `server/src/index.js` (+ ggf. `LuvApiClient.kt`) |
| Shop-Preise | Server `shop_*.js` + ggf. Client `ShopCatalog.kt` / Sync-Scripts |
| Deploy shippen | `full_deploy.py` + Version bump + Changelog |
| Tutorial | `TutorialScreens.kt`, Coachmarks, Finish in `LuvAppNav` |

Viel Erfolg — bei Unklarheiten zuerst `LuvAppNav.kt` und `index.js` lesen, nicht raten.
`)

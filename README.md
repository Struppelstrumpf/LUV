# LUV

Android-App, die den Sperrbildschirm zum gemeinsamen Erlebnis für Paare macht.

## Features

- **Geschlecht wählen**: Mann → blauer Sperrbildschirm, Frau → lila
- **Hosten / Beitreten** über die **LUV-API** (Hetzner Docker)
- **Kurzer Code** (`LUV-AB12CD`) per WhatsApp — funktioniert über Distanz
- **Auto-Reconnect** über WebSocket
- **Gemeinsam malen**: Dicke weiße Striche, live synchronisiert
- **Leinwand löschen**: Beim Entsperren oder 2 Sek. gedrückt halten

## Server (3. Docker-Container)

Siehe [`server/README.md`](server/README.md). Eigenes Compose-File, eigener Port `18780`, berührt andere Container nicht.

```bash
cd server
docker compose up -d --build
```

In `gradle.properties` die API-URL setzen:

```
luv.api.baseUrl=http://DEINE_HETZNER_IP:18780
# oder https://luv.deinedomain.de
```

## Voraussetzungen

- Android 9+ (API 28)
- Erreichbare LUV-API
- Widget „LUV Leinwand“ auf dem Sperrbildschirm (gerätabhängig)

## Build

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Vor jedem Release `versionCode` in `app/build.gradle.kts` erhöhen. Alle Builds nutzen dieselbe Signatur (`keystore/luv.jks`), damit Updates über die bestehende Installation gehen.

## Update (ohne Neuinstallation)

1. Neue APK bauen/teilen  
2. In der App **Update installieren** tippen und die APK wählen  
   — oder die APK direkt öffnen  
3. Android zeigt **Aktualisieren** (nicht „Neu installieren“)  
4. Einstellungen, Pairing und Daten bleiben erhalten  

Nur bei Signaturwechsel müsste man deinstallieren.

## Nutzung

1. App öffnen → Mann oder Frau wählen  
2. Einer hostet und teilt den Code per WhatsApp  
3. Der andere tritt mit dem Code bei  
4. Widget auf den Sperrbildschirm legen und tippen zum Zeichnen  

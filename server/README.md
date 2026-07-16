# LUV API (Hetzner / Docker)

Eigenständiger dritter Container neben deinen bestehenden Services.  
Kein Eingriff in andere `docker-compose`-Projekte — eigener Port, eigenes Netzwerk.

## Was die API macht

- Raum erstellen → kurzer Code (`LUV-AB12CD`)
- Partner tritt mit Code bei
- WebSocket-Relay für Striche / Clear (funktioniert über Distanz, nicht nur WLAN)

## Deploy auf Hetzner

```bash
# Ordner hochladen, z.B. nach /opt/luv-api
cd /opt/luv-api
docker compose up -d --build
docker ps   # luv-api sollte zusätzlich zu den anderen 2 laufen
curl http://127.0.0.1:18780/health
```

Optional Reverse-Proxy (Caddy/Nginx) auf `https://luv.deinedomain.de` → `127.0.0.1:18780`.

## Ports

| Container | Host-Port | Zweck        |
|-----------|-----------|--------------|
| luv-api   | 18780     | HTTP + WS    |

Andere Container bleiben unverändert.

## API kurz

```
POST /v1/rooms              → { code, token, invite }
POST /v1/rooms/:code/join   → { code, token, invite }
GET  /health
WS   /v1/ws?code=...&token=...&role=host|join
```

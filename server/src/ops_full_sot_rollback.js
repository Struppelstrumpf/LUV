#!/usr/bin/env node
/**
 * Documentation-only helper: prints rollback steps for full SoT cutover.
 *
 * Restore (on LUV-net):
 * 1) Stop API: cd /opt/luv-api/server && docker compose stop luv-api
 * 2) Restore dump:
 *    docker compose exec -T postgres pg_restore -U luv -d luv --clean --if-exists \
 *      /path/or/copy of backups/pre-full-sot-<stamp>/luv.dump
 *    (or copy dump into a mounted path /data/backups/... and restore from there)
 * 3) Restore IAP ledger file to /data/iap_ledger/play_purchases.jsonl
 * 4) In .env: set new *_BACKEND back to blob OR redeploy previous git SHA;
 *    STORE_LIVE_WRITE=on
 * 5) docker compose up -d luv-api
 * 6) curl health; confirm rooms/users
 */
console.log(`Full-SoT rollback
================
1. docker compose stop luv-api
2. pg_restore --clean --if-exists from /data/backups/pre-full-sot-*/luv.dump
3. restore /data/iap_ledger/play_purchases.jsonl from same folder
4. .env: STORE_LIVE_WRITE=on; ROOMS_/ECONOMY_/MARRIAGES_/SHOP_/MISC_BACKEND=blob
   (or checkout previous git SHA) then docker compose up -d --build
5. curl http://10.0.0.3:8080/health
`);

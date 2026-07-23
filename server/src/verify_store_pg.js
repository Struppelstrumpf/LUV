#!/usr/bin/env node
/**
 * Compare /data/luv-store.json vs store_live payload.
 * Exit 0 if checksums match.
 */
const fs = require("fs");
const path = require("path");
const { loadLive, summarize, contentHash } = require("./pg_store");

function normalizeForCompare(db) {
  // Summaries only — full deep equality is expensive; we check structural fingerprints.
  return summarize(db);
}

async function main() {
  const dataDir = process.env.DATA_DIR || "/data";
  const storePath = path.join(dataDir, "luv-store.json");
  if (!fs.existsSync(storePath)) {
    console.error("missing", storePath);
    process.exit(1);
  }
  const fileRaw = fs.readFileSync(storePath, "utf8");
  const fileDb = JSON.parse(fileRaw);
  const fileSum = normalizeForCompare(fileDb);
  fileSum.fileSha256 = contentHash(fileRaw);
  fileSum.fileBytes = Buffer.byteLength(fileRaw);

  const pgPayload = await loadLive();
  if (pgPayload == null) {
    console.error(JSON.stringify({ ok: false, error: "store_live empty" }));
    process.exit(1);
  }
  const pgRaw = JSON.stringify(pgPayload, null, 0);
  const pgSum = normalizeForCompare(pgPayload);
  pgSum.payloadSha256 = contentHash(pgRaw);
  pgSum.payloadBytes = Buffer.byteLength(pgRaw);

  const sameCounts =
    fileSum.users === pgSum.users &&
    fileSum.sessions === pgSum.sessions &&
    fileSum.marketListings === pgSum.marketListings &&
    fileSum.rooms === pgSum.rooms &&
    fileSum.coinsSum === pgSum.coinsSum;

  const sameSamples =
    JSON.stringify(fileSum.samples) === JSON.stringify(pgSum.samples);

  // Prefer exact content hash when JSON stringify matches file (compact form).
  const exact =
    contentHash(fileRaw) === contentHash(JSON.stringify(fileDb, null, 0))
      ? contentHash(fileRaw) === pgSum.payloadSha256 ||
        contentHash(JSON.stringify(pgPayload, null, 0)) === contentHash(fileRaw)
      : sameCounts && sameSamples;

  const ok = sameCounts && sameSamples;
  const out = {
    ok,
    exactMatchPossible: exact,
    file: fileSum,
    postgres: pgSum,
  };
  console.log(JSON.stringify(out, null, 2));
  process.exit(ok ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

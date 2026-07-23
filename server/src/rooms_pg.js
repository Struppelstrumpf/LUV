/**
 * Phase 6: rooms / memories / public canvases SoT.
 * Env: ROOMS_BACKEND=table|blob
 */
const { createKvDomain } = require("./sot_kv");

const mod = createKvDomain({
  envName: "ROOMS_BACKEND",
  table: "rooms_domain",
  keys: ["rooms", "canvasMemories", "publicCanvases"],
  defaultForKey: () => ({}),
  cliFile: "rooms_pg_cli.js",
  metaStampKey: "last_rooms_sot_at",
  summarize: (data) => ({
    rooms: Object.keys(data.rooms || {}).length,
    canvasMemories: Object.keys(data.canvasMemories || {}).length,
    publicCanvases: Object.keys(data.publicCanvases || {}).length,
  }),
});

module.exports = {
  ROOMS_BACKEND: mod.BACKEND,
  USE_ROOMS_TABLE: mod.USE_TABLE,
  DOMAIN_KEYS: mod.DOMAIN_KEYS,
  extractFromDb: mod.extractFromDb,
  applyToDb: mod.applyToDb,
  summarizeRooms: mod.summarize,
  ensureRoomsTables: mod.ensureTables,
  replaceRoomsInClient: mod.replaceInClient,
  replaceAllRooms: mod.replaceAll,
  loadAllRooms: mod.loadAll,
  loadAllRoomsSync: mod.loadAllSync,
  verifyRooms: mod.verify,
  roomsRowCount: mod.rowCountSync,
};

/**
 * Phase 8: marriages SoT.
 * Env: MARRIAGES_BACKEND=table|blob
 */
const { createKvDomain } = require("./sot_kv");

const mod = createKvDomain({
  envName: "MARRIAGES_BACKEND",
  table: "marriages_domain",
  keys: ["marriages"],
  defaultForKey: () => ({}),
  cliFile: "marriages_pg_cli.js",
  metaStampKey: "last_marriages_sot_at",
  summarize: (data) => ({
    marriages: Object.keys(data.marriages || {}).length,
  }),
});

module.exports = {
  MARRIAGES_BACKEND: mod.BACKEND,
  USE_MARRIAGES_TABLE: mod.USE_TABLE,
  DOMAIN_KEYS: mod.DOMAIN_KEYS,
  extractFromDb: mod.extractFromDb,
  applyToDb: mod.applyToDb,
  summarizeMarriages: mod.summarize,
  ensureMarriagesTables: mod.ensureTables,
  replaceMarriagesInClient: mod.replaceInClient,
  replaceAllMarriages: mod.replaceAll,
  loadAllMarriages: mod.loadAll,
  loadAllMarriagesSync: mod.loadAllSync,
  verifyMarriages: mod.verify,
  marriagesRowCount: mod.rowCountSync,
};

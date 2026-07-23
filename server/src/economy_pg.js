/**
 * Phase 7: economy SoT (payments, ledger, vouchers, …).
 * Env: ECONOMY_BACKEND=table|blob
 */
const { createKvDomain } = require("./sot_kv");

const mod = createKvDomain({
  envName: "ECONOMY_BACKEND",
  table: "economy_domain",
  keys: [
    "payments",
    "ledger",
    "vouchers",
    "redeems",
    "introOffersByIp",
    "economySettings",
  ],
  defaultForKey: (key) => {
    if (key === "ledger") return [];
    if (key === "economySettings") return { achievementDailyCap: 12 };
    return {};
  },
  cliFile: "economy_pg_cli.js",
  metaStampKey: "last_economy_sot_at",
  summarize: (data) => ({
    payments: Object.keys(data.payments || {}).length,
    ledger: Array.isArray(data.ledger) ? data.ledger.length : 0,
    vouchers: Object.keys(data.vouchers || {}).length,
    redeems: Object.keys(data.redeems || {}).length,
    introOffersByIp: Object.keys(data.introOffersByIp || {}).length,
    hasEconomySettings: !!(
      data.economySettings && typeof data.economySettings === "object"
    ),
  }),
});

module.exports = {
  ECONOMY_BACKEND: mod.BACKEND,
  USE_ECONOMY_TABLE: mod.USE_TABLE,
  DOMAIN_KEYS: mod.DOMAIN_KEYS,
  extractFromDb: mod.extractFromDb,
  applyToDb: mod.applyToDb,
  summarizeEconomy: mod.summarize,
  ensureEconomyTables: mod.ensureTables,
  replaceEconomyInClient: mod.replaceInClient,
  replaceAllEconomy: mod.replaceAll,
  loadAllEconomy: mod.loadAll,
  loadAllEconomySync: mod.loadAllSync,
  verifyEconomy: mod.verify,
  economyRowCount: mod.rowCountSync,
};

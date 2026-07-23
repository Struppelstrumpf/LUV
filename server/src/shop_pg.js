/**
 * Phase 9: shop SoT.
 * Env: SHOP_BACKEND=table|blob
 */
const { createKvDomain } = require("./sot_kv");

const mod = createKvDomain({
  envName: "SHOP_BACKEND",
  table: "shop_domain",
  keys: [
    "shopCatalog",
    "shopStats",
    "shopStatsBackfilled",
    "shopChangeQueue",
    "shopRotationPlans",
  ],
  defaultForKey: (key) => {
    if (key === "shopCatalog") return { items: {}, version: 1 };
    if (key === "shopStats") return {};
    if (key === "shopStatsBackfilled") return false;
    if (key === "shopChangeQueue") return { jobs: [] };
    if (key === "shopRotationPlans") return { plans: {} };
    return null;
  },
  cliFile: "shop_pg_cli.js",
  metaStampKey: "last_shop_sot_at",
  summarize: (data) => {
    const items =
      data.shopCatalog && typeof data.shopCatalog === "object"
        ? data.shopCatalog.items || {}
        : {};
    const jobs =
      data.shopChangeQueue && Array.isArray(data.shopChangeQueue.jobs)
        ? data.shopChangeQueue.jobs.length
        : 0;
    const plans =
      data.shopRotationPlans &&
      data.shopRotationPlans.plans &&
      typeof data.shopRotationPlans.plans === "object"
        ? Object.keys(data.shopRotationPlans.plans).length
        : 0;
    return {
      shopItems: Object.keys(items).length,
      shopStats: Object.keys(data.shopStats || {}).length,
      shopStatsBackfilled: !!data.shopStatsBackfilled,
      shopChangeJobs: jobs,
      shopRotationPlans: plans,
    };
  },
});

module.exports = {
  SHOP_BACKEND: mod.BACKEND,
  USE_SHOP_TABLE: mod.USE_TABLE,
  DOMAIN_KEYS: mod.DOMAIN_KEYS,
  extractFromDb: mod.extractFromDb,
  applyToDb: mod.applyToDb,
  summarizeShop: mod.summarize,
  ensureShopTables: mod.ensureTables,
  replaceShopInClient: mod.replaceInClient,
  replaceAllShop: mod.replaceAll,
  loadAllShop: mod.loadAll,
  loadAllShopSync: mod.loadAllSync,
  verifyShop: mod.verify,
  shopRowCount: mod.rowCountSync,
};

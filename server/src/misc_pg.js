/**
 * Phase 10: layouts / reports / maintenance / item meta / achievements.
 * Env: MISC_BACKEND=table|blob
 */
const { createKvDomain } = require("./sot_kv");

const mod = createKvDomain({
  envName: "MISC_BACKEND",
  table: "misc_domain",
  keys: [
    "roomLayouts",
    "publicReports",
    "peerReports",
    "bugReports",
    "helpMessages",
    "guestbookReports",
    "maintenance",
    "maintenanceReports",
    "itemTradeFlags",
    "itemDisplayLabels",
    "achievementDefs",
  ],
  defaultForKey: (key) => {
    if (key === "guestbookReports") return [];
    if (key === "maintenance") {
      return {
        nightKey: null,
        joke: "",
        jobDone: false,
        jobStartedAt: null,
        jobFinishedAt: null,
        lastReportId: null,
      };
    }
    return {};
  },
  cliFile: "misc_pg_cli.js",
  metaStampKey: "last_misc_sot_at",
  summarize: (data) => ({
    roomLayouts: Object.keys(data.roomLayouts || {}).length,
    publicReports: Object.keys(data.publicReports || {}).length,
    peerReports: Object.keys(data.peerReports || {}).length,
    bugReports: Object.keys(data.bugReports || {}).length,
    helpMessages: Object.keys(data.helpMessages || {}).length,
    guestbookReports: Array.isArray(data.guestbookReports)
      ? data.guestbookReports.length
      : 0,
    maintenanceReports: Object.keys(data.maintenanceReports || {}).length,
    itemTradeFlags: Object.keys(data.itemTradeFlags || {}).length,
    itemDisplayLabels: Object.keys(data.itemDisplayLabels || {}).length,
    achievementDefs: Object.keys(data.achievementDefs || {}).length,
    hasMaintenance: !!(data.maintenance && typeof data.maintenance === "object"),
  }),
});

module.exports = {
  MISC_BACKEND: mod.BACKEND,
  USE_MISC_TABLE: mod.USE_TABLE,
  DOMAIN_KEYS: mod.DOMAIN_KEYS,
  extractFromDb: mod.extractFromDb,
  applyToDb: mod.applyToDb,
  summarizeMisc: mod.summarize,
  ensureMiscTables: mod.ensureTables,
  replaceMiscInClient: mod.replaceInClient,
  replaceAllMisc: mod.replaceAll,
  loadAllMisc: mod.loadAll,
  loadAllMiscSync: mod.loadAllSync,
  verifyMisc: mod.verify,
  miscRowCount: mod.rowCountSync,
};

// Cache TTLs from docs/api-contracts.md §10 — the ONE place they live.
// Seconds. Clients receive these as meta.ttlSeconds.
export const CACHE_TTLS = {
  trainInfo: 7 * 24 * 3600, // route/coords (getTrainInfo)
  history: 30 * 24 * 3600, // history summaries
  search: 12 * 3600, // A→B search
  fare: 20 * 60,
  availability: 10 * 60,
  pnr: 3 * 60,
  pnrChartWindow: 60, // PNR inside the chart window
  stationBoard: 90,
  trackTrain: 45,
} as const satisfies Record<string, number>;

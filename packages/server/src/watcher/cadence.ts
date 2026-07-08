// Adaptive polling cadence (FR-7.5): tight only when it matters, so watcher
// load stays inside the upstream budget (WS-E) while the hero moments
// (chart prep, arrival alarm) stay fresh.
import type { WatchRow } from "./repo.js";

export const CADENCE_S = {
  pnrDefault: 300, // 5 min
  pnrChartWindow: 60, // inside the chart window (FR-7.5)
  trainDefault: 300,
  trainNearAlarm: 60, // an arrival watch is closing in on its station
  trainImminentAlarm: 30, // inside the alarm lead window
} as const;

export interface EntitySnapshot {
  kind: "pnr" | "train";
  /** PNR: chart already prepared? Train: journey finished/cancelled? */
  settled: boolean;
  /** PNR: ms until departure. Train: ms until the soonest arrival-alarm trigger. */
  msToCriticalPoint: number | null;
}

/** Next poll delay for an entity, in seconds. */
export function nextPollDelayS(snapshot: EntitySnapshot, watches: WatchRow[]): number {
  if (snapshot.kind === "pnr") {
    if (snapshot.settled) return CADENCE_S.pnrDefault;
    const toDeparture = snapshot.msToCriticalPoint;
    // Chart window: departure within 6h and chart not prepared (contracts §10)
    if (toDeparture !== null && toDeparture > 0 && toDeparture <= 6 * 3600_000) {
      return CADENCE_S.pnrChartWindow;
    }
    return CADENCE_S.pnrDefault;
  }

  const hasArrivalWatch = watches.some((w) => w.type === "arrival");
  const toAlarm = snapshot.msToCriticalPoint;
  if (hasArrivalWatch && toAlarm !== null) {
    if (toAlarm <= 10 * 60_000) return CADENCE_S.trainImminentAlarm;
    if (toAlarm <= 45 * 60_000) return CADENCE_S.trainNearAlarm;
  }
  return CADENCE_S.trainDefault;
}

// Tatkal-open reminders (FR-6.4, contracts §5). Time-based, not state-diff:
// the reminder fires when the clock crosses the band's opening — 10:00 IST
// (ac) / 11:00 IST (nonac) on runDate − 1 — with NO upstream call (a future
// run's trackTrain would fail). Pure; the poller wires it in before fetching.
import type { PushPayload } from "@railcast/shared";
import type { WatchRow } from "./repo.js";

export type TatkalBand = "ac" | "nonac";

const BAND_OPEN_HOUR_IST: Record<TatkalBand, number> = { ac: 10, nonac: 11 };
const DAY_MS = 86_400_000;

/** Epoch ms when the band opens for a journey date (IST wall clock). */
export function tatkalOpensAtMs(runDate: string, band: TatkalBand): number {
  const journeyMidnightIst = Date.parse(`${runDate}T00:00:00+05:30`);
  return journeyMidnightIst - DAY_MS + BAND_OPEN_HOUR_IST[band] * 3600_000;
}

export interface TatkalEvent {
  payload: PushPayload;
  signature: string;
}

/** The reminder due for [watch] at [now], if any. Fires once the band is open
 *  (delivery dedup makes it once); a watch created post-open fires on the next
 *  poll — "Tatkal is open" is still true and useful (contracts §5). */
export function detectTatkalEvents(watch: WatchRow, now: Date): TatkalEvent[] {
  if (watch.type !== "tatkal" || watch.entity.kind !== "train") return [];
  const band = (watch.params?.tatkalBand ?? "ac") as TatkalBand;
  if (now.getTime() < tatkalOpensAtMs(watch.entity.runDate, band)) return [];
  return [
    {
      payload: {
        kind: "TATKAL_OPEN",
        trainNo: watch.entity.trainNo,
        runDate: watch.entity.runDate,
        band,
      },
      signature: `TATKAL_OPEN:${band}`,
    },
  ];
}

/**
 * Seconds until the next still-undelivered tatkal opening across [watches], so
 * the poll chain wakes at the right moment instead of spinning on a future run.
 * Null when nothing is pending (pure-tatkal chains can end). Clamped to
 * [30s, 6h] — the scheduler re-arms each poll, so long gaps stay cheap.
 */
export function nextTatkalDelayS(watches: WatchRow[], now: Date): number | null {
  let earliest: number | null = null;
  for (const w of watches) {
    if (w.type !== "tatkal" || w.entity.kind !== "train") continue;
    const band = (w.params?.tatkalBand ?? "ac") as TatkalBand;
    if (w.delivered.includes(`TATKAL_OPEN:${band}`)) continue;
    const opensAt = tatkalOpensAtMs(w.entity.runDate, band);
    if (earliest === null || opensAt < earliest) earliest = opensAt;
  }
  if (earliest === null) return null;
  const untilS = Math.ceil((earliest - now.getTime()) / 1000);
  return Math.max(30, Math.min(untilS, 6 * 3600));
}

// Normalize upstream state to the fields that MATTER for watches, then hash.
// Deliberately coarse: volatile noise (lastUpdate, interpolated position,
// minute-by-minute delay jitter) must not churn the hash. The 2.2 diff engine
// turns hash changes into typed events.
import { createHash } from "node:crypto";
import { parseDelayMin, parseUpstreamPnrDateTime } from "../railkit/dates.js";
import type { RawPnrStatus, RawTrackTrain } from "../railkit/types.js";

export interface NormalizedTrain {
  kind: "train";
  state: "not_started" | "running" | "arrived" | "cancelled" | "diverted" | "rescheduled";
  lastStationCode: string | null;
  delayBucket: number | null; // 15-min buckets — threshold events, not jitter
  platforms: Record<string, string>; // stationCode → platform (change events)
}

export interface NormalizedPnr {
  kind: "pnr";
  chartPrepared: boolean;
  passengerStatuses: string[]; // "CNF/A1/14" per passenger, in order
  departureIso: string | null;
  arrivalIso: string | null;
}

export type NormalizedEntity = NormalizedTrain | NormalizedPnr;

export function normalizeTrain(track: RawTrackTrain): NormalizedTrain {
  const note = track.statusNote.toLowerCase();
  const stoppages = track.timeline.filter((e) => e.type === "stoppage");
  const last = stoppages[stoppages.length - 1];
  const crossed = stoppages.filter((e) => e.status === "passed" || e.status === "current");
  const lastCrossed = crossed[crossed.length - 1];

  const state: NormalizedTrain["state"] = note.includes("cancel")
    ? "cancelled"
    : note.includes("divert")
      ? "diverted"
      : note.includes("reschedul")
        ? "rescheduled"
        : note.includes("yet to start")
          ? "not_started"
          : last?.status === "current"
            ? "arrived"
            : "running";

  const delay =
    parseDelayMin(lastCrossed?.arrival?.delay) ?? parseDelayMin(lastCrossed?.departure?.delay);

  const platforms: Record<string, string> = {};
  for (const s of stoppages) {
    if (s.platform && s.platform !== "-") platforms[s.stationCode] = s.platform;
  }

  return {
    kind: "train",
    state,
    lastStationCode: lastCrossed?.stationCode ?? null,
    delayBucket: delay === null ? null : Math.floor(delay / 15),
    platforms,
  };
}

export function normalizePnr(pnr: RawPnrStatus): NormalizedPnr {
  return {
    kind: "pnr",
    chartPrepared: /prepared/i.test(pnr.chart.status) && !/not/i.test(pnr.chart.status),
    passengerStatuses: pnr.passengers.map(
      (p) => `${p.current.status}/${p.current.coach ?? "-"}/${p.current.berthNo ?? "-"}`,
    ),
    departureIso: parseUpstreamPnrDateTime(pnr.journey.dateOfJourney),
    arrivalIso: parseUpstreamPnrDateTime(pnr.journey.arrivalDate),
  };
}

export function stateHash(state: NormalizedEntity): string {
  // Stable serialization: sort object keys so hashes are order-independent.
  const canonical = JSON.stringify(state, (_k, v: unknown) =>
    v && typeof v === "object" && !Array.isArray(v)
      ? Object.fromEntries(Object.entries(v as Record<string, unknown>).sort(([a], [b]) => (a < b ? -1 : 1)))
      : v,
  );
  return createHash("sha256").update(canonical).digest("hex").slice(0, 32);
}

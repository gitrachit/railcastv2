// Diff engine (backlog 2.2, FR-7.2): turn a prev→next normalized-state
// transition into typed push events (contracts §5 PushPayload). Pure and
// per-watch: the poller feeds each watch its entity's prev/next states and
// the raw payload (for fields the normalized form drops), and dedups by the
// returned `signature` so a standing condition never re-fires.
import type { PushPayload } from "@railcast/shared";
import { parseUpstreamTime } from "../railkit/dates.js";
import type { RawPnrStatus, RawTrackTrain } from "../railkit/types.js";
import { maskPnr } from "../privacy/mask.js";
import type { WatchRow } from "./repo.js";
import type { NormalizedEntity, NormalizedPnr, NormalizedTrain } from "./normalize.js";

export interface DetectedEvent {
  signature: string; // stable id for dedup ("CHART_PREPARED", "PLATFORM:ET:5", ...)
  payload: PushPayload;
}

export interface DiffInput {
  prev: NormalizedEntity;
  next: NormalizedEntity;
  raw: RawTrackTrain | RawPnrStatus;
  watch: WatchRow;
  now: Date;
}

export function detectWatchEvents(input: DiffInput): DetectedEvent[] {
  const { prev, next, watch } = input;
  if (prev.kind !== next.kind) return []; // entity kind can't change

  if (next.kind === "pnr" && prev.kind === "pnr" && watch.type === "chart") {
    return chartEvents(prev, next, input.raw as RawPnrStatus, watch);
  }
  if (next.kind === "train" && prev.kind === "train") {
    const raw = input.raw as RawTrackTrain;
    switch (watch.type) {
      case "delay":
        return delayEvents(prev, next, raw, watch);
      case "platform":
        return platformEvents(prev, next, watch);
      case "cancel":
        return cancelEvents(prev, next, watch);
      case "arrival":
        return arrivalEvents(next, raw, watch, input.now);
      default:
        return [];
    }
  }
  return [];
}

// ── chart (FR-4.2) ────────────────────────────────────────────────────────
function chartEvents(
  prev: NormalizedPnr,
  next: NormalizedPnr,
  raw: RawPnrStatus,
  watch: WatchRow,
): DetectedEvent[] {
  if (prev.chartPrepared || !next.chartPrepared) return [];
  const entity = watch.entity;
  const allConfirmed = raw.passengers.every((p) => /^CNF/i.test(p.current.status));
  const coaches = [...new Set(raw.passengers.map((p) => p.current.coach).filter(Boolean))];
  return [
    {
      signature: "CHART_PREPARED",
      payload: {
        kind: "CHART_PREPARED",
        pnrMasked: entity.kind === "pnr" ? maskPnr(entity.pnr) : "••••????",
        trainNo: raw.train.number,
        trainName: raw.train.name,
        allConfirmed,
        coachSummary: coaches.join(", "),
      },
    },
  ];
}

// ── delay threshold (FR-7.2) ──────────────────────────────────────────────
function delayEvents(
  prev: NormalizedTrain,
  next: NormalizedTrain,
  raw: RawTrackTrain,
  watch: WatchRow,
): DetectedEvent[] {
  const threshold = watch.params?.delayThresholdMin;
  if (!threshold) return [];
  const prevDelay = prev.delayMin ?? 0;
  const nextDelay = next.delayMin ?? 0;
  if (!(prevDelay < threshold && nextDelay >= threshold)) return []; // upward crossing only

  const nextStop = nextUpcomingStop(raw);
  return [
    {
      signature: `DELAY:${threshold}`,
      payload: {
        kind: "DELAY",
        trainNo: raw.trainNo,
        delayMin: nextDelay,
        nextStation: nextStop?.name ?? "",
      },
    },
  ];
}

// ── platform change (FR-7.2) ──────────────────────────────────────────────
function platformEvents(
  prev: NormalizedTrain,
  next: NormalizedTrain,
  watch: WatchRow,
): DetectedEvent[] {
  const only = watch.params?.stationCode; // optional: watch a specific station
  const events: DetectedEvent[] = [];
  for (const [code, platform] of Object.entries(next.platforms)) {
    if (only && code !== only) continue;
    const before = prev.platforms[code];
    if (before === undefined || before === platform) continue; // newly known ≠ changed
    events.push({
      signature: `PLATFORM:${code}:${platform}`,
      payload: {
        kind: "PLATFORM_CHANGE",
        trainNo: trainNoFromWatch(watch),
        stationCode: code,
        platform,
      },
    });
  }
  return events;
}

// ── cancelled / diverted (FR-2.4) ─────────────────────────────────────────
function cancelEvents(
  prev: NormalizedTrain,
  next: NormalizedTrain,
  watch: WatchRow,
): DetectedEvent[] {
  const wasBad = prev.state === "cancelled" || prev.state === "diverted";
  if (wasBad) return [];
  if (next.state !== "cancelled" && next.state !== "diverted") return [];
  const entity = watch.entity;
  const runDate = entity.kind === "train" ? entity.runDate : "";
  return [
    {
      signature: next.state === "cancelled" ? "CANCELLED" : "DIVERTED",
      payload: {
        kind: next.state === "cancelled" ? "CANCELLED" : "DIVERTED",
        trainNo: trainNoFromWatch(watch),
        runDate,
      },
    },
  ];
}

// ── arrival alarm (FR-7.3) — condition, deduped once per station ──────────
function arrivalEvents(
  next: NormalizedTrain,
  raw: RawTrackTrain,
  watch: WatchRow,
  now: Date,
): DetectedEvent[] {
  const station = watch.params?.stationCode;
  const leadMin = watch.params?.leadMin;
  if (!station || !leadMin) return [];
  if (next.state === "arrived" || next.state === "cancelled") return [];

  const eta = etaToStation(raw, station, next.delayMin ?? 0, watch);
  if (!eta) return [];
  const msToEta = Date.parse(eta) - now.getTime();
  if (msToEta > leadMin * 60_000) return []; // not yet inside the lead window

  return [
    {
      signature: `ARRIVAL:${station}`,
      payload: {
        kind: "ARRIVAL_ALARM",
        trainNo: trainNoFromWatch(watch),
        stationCode: station,
        etaActual: eta,
        leadMin,
      },
    },
  ];
}

// ── helpers ────────────────────────────────────────────────────────────────
function nextUpcomingStop(raw: RawTrackTrain): { code: string; name: string } | null {
  const stoppages = raw.timeline.filter((e) => e.type === "stoppage");
  const lastCrossed = stoppages.reduce(
    (acc, e, i) => (e.status === "passed" || e.status === "current" ? i : acc),
    -1,
  );
  const next = stoppages[lastCrossed + 1];
  return next ? { code: next.stationCode, name: next.stationName } : null;
}

function etaToStation(
  raw: RawTrackTrain,
  station: string,
  delayMin: number,
  watch: WatchRow,
): string | null {
  const stop = raw.timeline.find((e) => e.type === "stoppage" && e.stationCode === station);
  const scheduled = stop?.arrival?.scheduled ?? stop?.departure?.scheduled;
  const runDate = watch.entity.kind === "train" ? watch.entity.runDate : null;
  if (!scheduled || !runDate) return null;
  const iso = parseUpstreamTime(scheduled, runDate);
  if (!iso) return null;
  return new Date(Date.parse(iso) + delayMin * 60_000).toISOString();
}

function trainNoFromWatch(watch: WatchRow): string {
  return watch.entity.kind === "train" ? watch.entity.trainNo : "";
}

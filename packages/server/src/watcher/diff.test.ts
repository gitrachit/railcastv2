import { describe, expect, it } from "vitest";
import type { RawPnrStatus, RawTrackTrain } from "../railkit/types.js";
import { detectWatchEvents } from "./diff.js";
import type { NormalizedPnr, NormalizedTrain } from "./normalize.js";
import type { WatchRow } from "./repo.js";

process.env.PNR_ENCRYPTION_KEY = "d".repeat(64);
const NOW = new Date("2026-07-08T16:30:00+05:30");

function trainState(over: Partial<NormalizedTrain> = {}): NormalizedTrain {
  return {
    kind: "train",
    state: "running",
    lastStationCode: "JBP",
    delayMin: 0,
    delayBucket: 0,
    platforms: {},
    ...over,
  };
}

function pnrState(over: Partial<NormalizedPnr> = {}): NormalizedPnr {
  return {
    kind: "pnr",
    chartPrepared: false,
    passengerStatuses: [],
    departureIso: null,
    arrivalIso: null,
    ...over,
  };
}

function trainWatch(type: WatchRow["type"], params: WatchRow["params"] = {}): WatchRow {
  return {
    id: "w1",
    deviceId: "d1",
    type,
    entityKey: "train:22188:2026-07-08",
    entity: { kind: "train", trainNo: "22188", runDate: "2026-07-08" },
    params,
    delivered: [],
    expiresAt: new Date(),
  };
}

function pnrWatch(): WatchRow {
  return {
    id: "wp",
    deviceId: "d1",
    type: "chart",
    entityKey: "pnr:x",
    entity: { kind: "pnr", pnr: "8524132882" },
    params: {},
    delivered: [],
    expiresAt: new Date(),
  };
}

const emptyTrack = { trainNo: "22188", trainName: "INTERCITY EXP", timeline: [] } as unknown as RawTrackTrain;

describe("detectWatchEvents — chart (FR-4.2)", () => {
  const raw = {
    train: { number: "12780", name: "GOA EXPRESS" },
    passengers: [
      { current: { status: "CNF", coach: "A1" } },
      { current: { status: "CNF", coach: "A1" } },
    ],
  } as unknown as RawPnrStatus;

  it("fires CHART_PREPARED on false→true with allConfirmed and coach summary", () => {
    const events = detectWatchEvents({
      prev: pnrState({ chartPrepared: false }),
      next: pnrState({ chartPrepared: true }),
      raw,
      watch: pnrWatch(),
      now: NOW,
    });
    expect(events).toHaveLength(1);
    expect(events[0]!.signature).toBe("CHART_PREPARED");
    expect(events[0]!.payload).toMatchObject({
      kind: "CHART_PREPARED",
      pnrMasked: "••••2882",
      trainNo: "12780",
      trainName: "GOA EXPRESS",
      allConfirmed: true,
      coachSummary: "A1",
    });
  });

  it("does not fire when chart was already prepared", () => {
    expect(
      detectWatchEvents({
        prev: pnrState({ chartPrepared: true }),
        next: pnrState({ chartPrepared: true }),
        raw,
        watch: pnrWatch(),
        now: NOW,
      }),
    ).toEqual([]);
  });
});

describe("detectWatchEvents — delay threshold (FR-7.2)", () => {
  it("fires only on an upward crossing of the watch threshold", () => {
    const watch = trainWatch("delay", { delayThresholdMin: 15 });
    const raw = {
      trainNo: "22188",
      timeline: [
        { type: "stoppage", status: "current", stationCode: "JBP", stationName: "JABALPUR" },
        { type: "stoppage", status: "upcoming", stationCode: "MML", stationName: "MADAN MAHAL", arrival: { scheduled: "16:44 08-Jul" } },
      ],
    } as unknown as RawTrackTrain;

    const crossed = detectWatchEvents({
      prev: trainState({ delayMin: 10 }),
      next: trainState({ delayMin: 20 }),
      raw,
      watch,
      now: NOW,
    });
    expect(crossed).toHaveLength(1);
    expect(crossed[0]!.signature).toBe("DELAY:15");
    expect(crossed[0]!.payload).toMatchObject({ kind: "DELAY", delayMin: 20, nextStation: "MADAN MAHAL" });

    // already above → no re-fire
    expect(
      detectWatchEvents({ prev: trainState({ delayMin: 20 }), next: trainState({ delayMin: 25 }), raw, watch, now: NOW }),
    ).toEqual([]);
    // recovering below → no fire
    expect(
      detectWatchEvents({ prev: trainState({ delayMin: 20 }), next: trainState({ delayMin: 5 }), raw, watch, now: NOW }),
    ).toEqual([]);
  });
});

describe("detectWatchEvents — platform change (FR-7.2)", () => {
  it("fires on a changed platform, not on a newly-known one", () => {
    const watch = trainWatch("platform");
    const events = detectWatchEvents({
      prev: trainState({ platforms: { ET: "3", JBP: "4" } }),
      next: trainState({ platforms: { ET: "5", JBP: "4", NU: "2" } }),
      raw: emptyTrack,
      watch,
      now: NOW,
    });
    expect(events).toHaveLength(1); // ET 3→5 only; NU is newly known, JBP unchanged
    expect(events[0]!.signature).toBe("PLATFORM:ET:5");
    expect(events[0]!.payload).toMatchObject({ kind: "PLATFORM_CHANGE", stationCode: "ET", platform: "5" });
  });

  it("respects a station filter in params", () => {
    const watch = trainWatch("platform", { stationCode: "JBP" });
    expect(
      detectWatchEvents({
        prev: trainState({ platforms: { ET: "3" } }),
        next: trainState({ platforms: { ET: "5" } }),
        raw: emptyTrack,
        watch,
        now: NOW,
      }),
    ).toEqual([]); // ET changed but we only watch JBP
  });
});

describe("detectWatchEvents — cancelled/diverted (FR-2.4)", () => {
  it("fires CANCELLED on transition into cancelled", () => {
    const events = detectWatchEvents({
      prev: trainState({ state: "running" }),
      next: trainState({ state: "cancelled" }),
      raw: emptyTrack,
      watch: trainWatch("cancel"),
      now: NOW,
    });
    expect(events[0]!.payload).toEqual({ kind: "CANCELLED", trainNo: "22188", runDate: "2026-07-08" });
  });

  it("fires DIVERTED and not twice", () => {
    const events = detectWatchEvents({
      prev: trainState({ state: "running" }),
      next: trainState({ state: "diverted" }),
      raw: emptyTrack,
      watch: trainWatch("cancel"),
      now: NOW,
    });
    expect(events[0]!.signature).toBe("DIVERTED");
    expect(
      detectWatchEvents({
        prev: trainState({ state: "diverted" }),
        next: trainState({ state: "diverted" }),
        raw: emptyTrack,
        watch: trainWatch("cancel"),
        now: NOW,
      }),
    ).toEqual([]);
  });
});

describe("detectWatchEvents — arrival alarm (FR-7.3)", () => {
  const raw = {
    trainNo: "22188",
    timeline: [
      { type: "stoppage", status: "current", stationCode: "JBP", stationName: "JABALPUR" },
      { type: "stoppage", status: "upcoming", stationCode: "NU", stationName: "NARSINGHPUR", arrival: { scheduled: "16:45 08-Jul" } },
    ],
  } as unknown as RawTrackTrain;

  it("fires when ETA to the alarm station is within the lead window", () => {
    // now 16:30, NU sched 16:45 + 0 delay = 15 min out; lead 20 → inside window
    const events = detectWatchEvents({
      prev: trainState(),
      next: trainState({ delayMin: 0 }),
      raw,
      watch: trainWatch("arrival", { stationCode: "NU", leadMin: 20 }),
      now: NOW,
    });
    expect(events).toHaveLength(1);
    expect(events[0]!.signature).toBe("ARRIVAL:NU");
    expect(events[0]!.payload).toMatchObject({ kind: "ARRIVAL_ALARM", stationCode: "NU", leadMin: 20 });
  });

  it("does not fire while the train is still outside the lead window", () => {
    expect(
      detectWatchEvents({
        prev: trainState(),
        next: trainState({ delayMin: 0 }),
        raw,
        watch: trainWatch("arrival", { stationCode: "NU", leadMin: 5 }), // 15 min out > 5
        now: NOW,
      }),
    ).toEqual([]);
  });
});

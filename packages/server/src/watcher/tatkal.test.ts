// Tatkal-open reminder logic (FR-6.4) — pure, no Postgres needed.
import { describe, expect, it } from "vitest";
import type { WatchRow } from "./repo.js";
import { detectTatkalEvents, nextTatkalDelayS, tatkalOpensAtMs } from "./tatkal.js";

function watch(
  runDate: string,
  band: "ac" | "nonac",
  delivered: string[] = [],
  type: WatchRow["type"] = "tatkal",
): WatchRow {
  return {
    id: "w1",
    deviceId: "d1",
    type,
    entityKey: `train:22188:${runDate}`,
    entity: { kind: "train", trainNo: "22188", runDate },
    params: { tatkalBand: band },
    delivered,
    expiresAt: new Date("2027-01-01T00:00:00Z"),
  };
}

const ist = (s: string) => new Date(`${s}+05:30`);

describe("tatkalOpensAtMs", () => {
  it("opens 10:00 IST (ac) / 11:00 IST (nonac) on the day BEFORE the journey", () => {
    expect(tatkalOpensAtMs("2026-07-12", "ac")).toBe(ist("2026-07-11T10:00:00").getTime());
    expect(tatkalOpensAtMs("2026-07-12", "nonac")).toBe(ist("2026-07-11T11:00:00").getTime());
  });
});

describe("detectTatkalEvents", () => {
  it("is silent before the band opens", () => {
    expect(detectTatkalEvents(watch("2026-07-12", "ac"), ist("2026-07-11T09:59:59"))).toEqual([]);
  });

  it("fires the typed payload once the band is open", () => {
    const events = detectTatkalEvents(watch("2026-07-12", "ac"), ist("2026-07-11T10:00:00"));
    expect(events).toHaveLength(1);
    expect(events[0]!.signature).toBe("TATKAL_OPEN:ac");
    expect(events[0]!.payload).toEqual({
      kind: "TATKAL_OPEN",
      trainNo: "22188",
      runDate: "2026-07-12",
      band: "ac",
    });
  });

  it("nonac waits for its own 11:00 opening", () => {
    expect(detectTatkalEvents(watch("2026-07-12", "nonac"), ist("2026-07-11T10:30:00"))).toEqual([]);
    expect(detectTatkalEvents(watch("2026-07-12", "nonac"), ist("2026-07-11T11:00:00"))).toHaveLength(1);
  });

  it("ignores non-tatkal watches", () => {
    expect(detectTatkalEvents(watch("2026-07-12", "ac", [], "delay"), ist("2026-07-11T12:00:00"))).toEqual([]);
  });
});

describe("nextTatkalDelayS", () => {
  it("wakes at the opening, clamped to [30s, 6h]", () => {
    const w = watch("2026-07-12", "ac");
    // 5 minutes before opening → wake in 300s.
    expect(nextTatkalDelayS([w], ist("2026-07-11T09:55:00"))).toBe(300);
    // Days out → capped at 6h so the chain stays cheap.
    expect(nextTatkalDelayS([w], ist("2026-07-08T10:00:00"))).toBe(6 * 3600);
    // Moments before → floored at 30s.
    expect(nextTatkalDelayS([w], ist("2026-07-11T09:59:59"))).toBe(30);
  });

  it("returns null when every reminder is delivered — the chain can end", () => {
    const done = watch("2026-07-12", "ac", ["TATKAL_OPEN:ac"]);
    expect(nextTatkalDelayS([done], ist("2026-07-11T10:05:00"))).toBeNull();
  });

  it("picks the earliest pending opening across bands", () => {
    const acDone = watch("2026-07-12", "ac", ["TATKAL_OPEN:ac"]);
    const nonac = watch("2026-07-12", "nonac");
    // 10:30 IST: ac delivered, nonac opens at 11:00 → 1800s.
    expect(nextTatkalDelayS([acDone, nonac], ist("2026-07-11T10:30:00"))).toBe(1800);
  });
});

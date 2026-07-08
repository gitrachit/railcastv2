import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import type { RawPnrStatus, RawTrackTrain } from "../railkit/types.js";
import { normalizePnr, normalizeTrain, stateHash } from "./normalize.js";

function fixture<T>(name: string): T {
  return (
    JSON.parse(
      readFileSync(new URL(`../railkit/__fixtures__/${name}`, import.meta.url), "utf8"),
    ) as { data: T }
  ).data;
}

const running = fixture<RawTrackTrain>("trackTrain-22188-running.json");
const arrived = fixture<RawTrackTrain>("trackTrain-22188.json");
const pnr = fixture<RawPnrStatus>("checkPNRStatus-sample.json");

describe("normalizeTrain", () => {
  it("captures state, last station, delay bucket, platforms", () => {
    const n = normalizeTrain(running);
    expect(n.state).toBe("running");
    expect(n.lastStationCode).toBe("ADTL");
    expect(n.delayBucket).toBe(0);
    // The live timeline platform ("1"), not the route's static "3,4" —
    // platform-change watches must track what's announced NOW.
    expect(n.platforms["JBP"]).toBe("1");
  });

  it("distinguishes arrived from running", () => {
    expect(normalizeTrain(arrived).state).toBe("arrived");
  });
});

describe("normalizePnr", () => {
  it("captures chart flag, passenger states, journey times", () => {
    const n = normalizePnr(pnr);
    expect(n.chartPrepared).toBe(true);
    expect(n.passengerStatuses).toEqual([
      "CNF/A1/14",
      "CNF/A1/15",
      "CNF/A1/16",
      "CNF/A1/18",
    ]);
    expect(n.departureIso).toBe("2026-07-04T17:10:00+05:30");
    expect(n.arrivalIso).toBe("2026-07-05T07:00:00+05:30");
  });
});

describe("stateHash", () => {
  it("is stable for identical state and differs across transitions", () => {
    expect(stateHash(normalizeTrain(running))).toBe(stateHash(normalizeTrain(running)));
    expect(stateHash(normalizeTrain(running))).not.toBe(stateHash(normalizeTrain(arrived)));
  });

  it("ignores volatile noise (lastUpdate/statusNote wording within a state)", () => {
    const a = structuredClone(running);
    a.lastUpdate = "08-Jul-2026 16:59";
    expect(stateHash(normalizeTrain(a))).toBe(stateHash(normalizeTrain(running)));
  });
});

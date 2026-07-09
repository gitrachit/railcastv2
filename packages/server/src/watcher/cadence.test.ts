import { describe, expect, it } from "vitest";
import { CADENCE_S, nextPollDelayS } from "./cadence.js";
import type { WatchRow } from "./repo.js";

const arrivalWatch = { type: "arrival" } as WatchRow;
const chartWatch = { type: "chart" } as WatchRow;

describe("nextPollDelayS (FR-7.5)", () => {
  it("PNR: 5 min by default, 60 s inside the chart window", () => {
    const base = { kind: "pnr" as const, settled: false };
    expect(nextPollDelayS({ ...base, msToCriticalPoint: 24 * 3600_000 }, [chartWatch])).toBe(
      CADENCE_S.pnrDefault,
    );
    expect(nextPollDelayS({ ...base, msToCriticalPoint: 3 * 3600_000 }, [chartWatch])).toBe(
      CADENCE_S.pnrChartWindow,
    );
  });

  it("PNR: relaxes again once the chart is prepared", () => {
    expect(
      nextPollDelayS(
        { kind: "pnr", settled: true, msToCriticalPoint: 3 * 3600_000 },
        [chartWatch],
      ),
    ).toBe(CADENCE_S.pnrDefault);
  });

  it("train: tightens only when an arrival watch nears its station", () => {
    const near = { kind: "train" as const, settled: false, msToCriticalPoint: 30 * 60_000 };
    expect(nextPollDelayS(near, [chartWatch])).toBe(CADENCE_S.trainDefault); // no arrival watch
    expect(nextPollDelayS(near, [arrivalWatch])).toBe(CADENCE_S.trainNearAlarm);
    expect(
      nextPollDelayS({ ...near, msToCriticalPoint: 5 * 60_000 }, [arrivalWatch]),
    ).toBe(CADENCE_S.trainImminentAlarm);
    expect(
      nextPollDelayS({ ...near, msToCriticalPoint: 5 * 3600_000 }, [arrivalWatch]),
    ).toBe(CADENCE_S.trainDefault);
  });
});

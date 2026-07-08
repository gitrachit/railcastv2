import { describe, expect, it } from "vitest";
import { fromUpstreamDate, toUpstreamDate } from "./dates.js";
import { RailkitError } from "./errors.js";

describe("date conversion (invariant 6)", () => {
  it("converts API → upstream", () => {
    expect(toUpstreamDate("2026-07-08")).toBe("08-07-2026");
  });

  it("converts upstream → API", () => {
    expect(fromUpstreamDate("08-07-2026")).toBe("2026-07-08");
  });

  it("round-trips", () => {
    expect(fromUpstreamDate(toUpstreamDate("2026-12-31"))).toBe("2026-12-31");
  });

  it("rejects the wrong direction and garbage as INVALID_INPUT", () => {
    for (const bad of ["08-07-2026", "2026/07/08", "20260708", ""]) {
      expect(() => toUpstreamDate(bad)).toThrowError(RailkitError);
      try {
        toUpstreamDate(bad);
      } catch (e) {
        expect((e as RailkitError).code).toBe("INVALID_INPUT");
      }
    }
    expect(() => fromUpstreamDate("2026-07-08")).toThrowError(RailkitError);
  });
});

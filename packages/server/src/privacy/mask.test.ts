import { describe, expect, it } from "vitest";
import { maskPnr } from "./mask.js";

describe("maskPnr", () => {
  it("keeps only the last 4 digits [FR-4.3]", () => {
    expect(maskPnr("8524132882")).toBe("••••2882");
  });

  it("never contains the leading digits", () => {
    expect(maskPnr("8524132882")).not.toContain("852413");
  });
});

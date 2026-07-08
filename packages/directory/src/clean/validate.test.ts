import { describe, expect, it } from "vitest";
import { isValidStationCode, isValidTrainNo } from "./validate.js";

describe("isValidTrainNo", () => {
  it("accepts exactly 5 digits", () => {
    expect(isValidTrainNo("22188")).toBe(true);
  });

  it("rejects everything else", () => {
    expect(isValidTrainNo("2218")).toBe(false);
    expect(isValidTrainNo("221888")).toBe(false);
    expect(isValidTrainNo("22 88a")).toBe(false);
    expect(isValidTrainNo("")).toBe(false);
  });
});

describe("isValidStationCode", () => {
  it("accepts 2–5 uppercase letters", () => {
    expect(isValidStationCode("JBP")).toBe(true);
    expect(isValidStationCode("NU")).toBe(true);
    expect(isValidStationCode("NDLS")).toBe(true);
    expect(isValidStationCode("NZMTQ")).toBe(true);
  });

  it("rejects lowercase, digits, and wrong lengths", () => {
    expect(isValidStationCode("jbp")).toBe(false);
    expect(isValidStationCode("J")).toBe(false);
    expect(isValidStationCode("JBPNRX")).toBe(false);
    expect(isValidStationCode("JB1")).toBe(false);
    expect(isValidStationCode("")).toBe(false);
  });
});

import { describe, expect, it } from "vitest";
import { parseStations, parseTrains } from "./parse.js";

// Tiny hand-built GeoJSON fixtures mirror the shape of the datameet source
// (properties + geometry.coordinates as [lng, lat]) without needing the 8 MB
// download in tests.
const stationGeo = {
  features: [
    {
      geometry: { type: "Point", coordinates: [77.4395, 23.2219] },
      properties: { code: "rkmp", name: "rani kamlapati", state: "madhya pradesh", address: "Bhopal, MP" },
    },
    {
      // duplicate code — second occurrence is counted, not kept
      geometry: { type: "Point", coordinates: [77.44, 23.22] },
      properties: { code: "RKMP", name: "rani kamlapati again" },
    },
    {
      // sorts before RKMP
      geometry: { type: "Point", coordinates: [80.35, 26.44] },
      properties: { code: "CNB", name: "kanpur central", state: "uttar pradesh" },
    },
    {
      // invalid code (has a digit) → dropped
      geometry: { type: "Point", coordinates: [0, 0] },
      properties: { code: "JB1", name: "bad code" },
    },
    {
      // missing name → dropped
      geometry: null,
      properties: { code: "ND" },
    },
  ],
};

const trainGeo = {
  features: [
    {
      geometry: null,
      properties: { number: "22188", name: "intercity exp", from_station_code: "adtl", to_station_code: "rkmp" },
    },
    {
      geometry: null,
      properties: { number: "12780", name: "goa express", from_station_code: "pune", to_station_code: "vsg" },
    },
    {
      // duplicate number
      geometry: null,
      properties: { number: "22188", name: "dup" },
    },
    {
      // 4 digits → invalid → dropped
      geometry: null,
      properties: { number: "2218", name: "short number" },
    },
  ],
};

describe("parseStations", () => {
  const result = parseStations(stationGeo);

  it("keeps only valid, deduped records sorted by code", () => {
    expect(result.records.map((s) => s.code)).toEqual(["CNB", "RKMP"]);
  });

  it("counts drops and duplicates", () => {
    expect(result.dropped).toBe(2); // JB1 + missing-name
    expect(result.duplicates).toBe(1); // second RKMP
  });

  it("title-cases name, derives city from the first address segment, carries coords", () => {
    const rkmp = result.records.find((s) => s.code === "RKMP")!;
    expect(rkmp.name).toBe("Rani Kamlapati");
    expect(rkmp.city).toBe("Bhopal");
    expect(rkmp.state).toBe("Madhya Pradesh");
    expect(rkmp.lat).toBeCloseTo(23.2219);
    expect(rkmp.lng).toBeCloseTo(77.4395);
  });

  it("leaves city empty and coords null when absent", () => {
    const cnb = result.records.find((s) => s.code === "CNB")!;
    expect(cnb.city).toBe("");
    const rkmpDupKept = result.records.find((s) => s.code === "RKMP")!;
    expect(rkmpDupKept.name).toBe("Rani Kamlapati"); // first wins, not "again"
  });

  it("carries empty locale-name columns", () => {
    expect(result.records[0]!.names.hi).toBe("");
  });
});

describe("parseTrains", () => {
  const result = parseTrains(trainGeo);

  it("keeps only valid, deduped records sorted by number", () => {
    expect(result.records.map((t) => t.number)).toEqual(["12780", "22188"]);
  });

  it("counts drops and duplicates", () => {
    expect(result.dropped).toBe(1); // 2218
    expect(result.duplicates).toBe(1); // second 22188
  });

  it("title-cases name and upper-cases endpoint codes", () => {
    const t = result.records.find((r) => r.number === "22188")!;
    expect(t.name).toBe("Intercity Exp");
    expect(t.fromCode).toBe("ADTL");
    expect(t.toCode).toBe("RKMP");
  });
});

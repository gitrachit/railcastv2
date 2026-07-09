import { describe, expect, it } from "vitest";
import { applySupplement } from "./supplement.js";
import { emptyNames, type StationRecord, type TrainRecord } from "./types.js";

function station(code: string, name: string): StationRecord {
  return { code, name, city: "", state: "", lat: null, lng: null, names: emptyNames() };
}
function train(number: string, name: string): TrainRecord {
  return { number, name, fromCode: "AAA", toCode: "BBB", names: emptyNames() };
}

describe("applySupplement", () => {
  const baseStations = [station("CNB", "Kanpur Central"), station("NDLS", "New Delhi")];
  const baseTrains = [train("12780", "Goa Express")];

  it("adds new entries not present in the CC0 base", () => {
    const r = applySupplement(baseStations, baseTrains, {
      stations: [{ code: "RKMP", name: "Rani Kamlapati" }],
      trains: [{ number: "22188", name: "Intercity Exp" }],
    });
    expect(r.addedStations).toBe(1);
    expect(r.addedTrains).toBe(1);
    expect(r.stations.map((s) => s.code)).toContain("RKMP");
    expect(r.trains.map((t) => t.number)).toContain("22188");
  });

  it("overrides existing entries by key", () => {
    const r = applySupplement(baseStations, baseTrains, {
      stations: [{ code: "NDLS", name: "New Delhi (Renamed)" }],
    });
    expect(r.overriddenStations).toBe(1);
    expect(r.addedStations).toBe(0);
    expect(r.stations.find((s) => s.code === "NDLS")!.name).toBe("New Delhi (Renamed)");
    expect(r.stations).toHaveLength(2); // override, not duplicate
  });

  it("upper-cases supplied codes so keys stay canonical", () => {
    const r = applySupplement(baseStations, baseTrains, {
      stations: [{ code: "rkmp", name: "Rani Kamlapati" }],
    });
    expect(r.stations.map((s) => s.code)).toContain("RKMP");
    expect(r.stations.map((s) => s.code)).not.toContain("rkmp");
  });

  it("fills defaults for omitted station fields", () => {
    const r = applySupplement([], [], { stations: [{ code: "RKMP", name: "Rani Kamlapati" }] });
    const s = r.stations[0]!;
    expect(s.city).toBe("");
    expect(s.lat).toBeNull();
    expect(s.names.hi).toBe("");
  });

  it("keeps output sorted and deterministic", () => {
    const r = applySupplement(baseStations, baseTrains, {
      stations: [{ code: "RKMP", name: "Rani Kamlapati" }, { code: "AGC", name: "Agra Cantt" }],
    });
    expect(r.stations.map((s) => s.code)).toEqual(["AGC", "CNB", "NDLS", "RKMP"]);
  });

  it("is a no-op when the supplement is empty", () => {
    const r = applySupplement(baseStations, baseTrains, {});
    expect(r.addedStations + r.overriddenStations + r.addedTrains + r.overriddenTrains).toBe(0);
    expect(r.stations).toHaveLength(2);
    expect(r.trains).toHaveLength(1);
  });
});

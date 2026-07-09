import { describe, expect, it } from "vitest";
import { buildIndex, INDEX_VERSION, STATION_COLUMNS, TRAIN_COLUMNS } from "./format.js";
import { LOCALE_COLUMNS, emptyNames, type StationRecord, type TrainRecord } from "../clean/types.js";

const stations: StationRecord[] = [
  { code: "RKMP", name: "Rani Kamlapati", city: "Bhopal", state: "Madhya Pradesh", lat: 23.22, lng: 77.44, names: emptyNames() },
];
const trains: TrainRecord[] = [
  { number: "22188", name: "Intercity Exp", fromCode: "ADTL", toCode: "RKMP", names: emptyNames() },
];
const source = { name: "datameet/railways", url: "https://github.com/datameet/railways", license: "CC0-1.0" };

describe("buildIndex", () => {
  it("emits positional rows in the documented column order", () => {
    const idx = buildIndex(stations, trains, source);
    expect(idx.stationColumns).toEqual(STATION_COLUMNS);
    expect(idx.trainColumns).toEqual(TRAIN_COLUMNS);
    expect(idx.stations[0]).toEqual(["RKMP", "Rani Kamlapati", "Bhopal", "Madhya Pradesh", 23.22, 77.44]);
    expect(idx.trains[0]).toEqual(["22188", "Intercity Exp", "ADTL", "RKMP"]);
  });

  it("carries version, source, and the full locale column set", () => {
    const idx = buildIndex(stations, trains, source);
    expect(idx.version).toBe(INDEX_VERSION);
    expect(idx.source).toEqual(source);
    expect(idx.localeColumns).toEqual(LOCALE_COLUMNS);
    expect(idx.localeColumns).toHaveLength(11);
  });

  it("is deterministic — same input serializes byte-identically", () => {
    const a = JSON.stringify(buildIndex(stations, trains, source));
    const b = JSON.stringify(buildIndex(stations, trains, source));
    expect(a).toBe(b);
  });

  it("defaults generatedAt to the epoch so builds stay reproducible", () => {
    expect(buildIndex(stations, trains, source).generatedAt).toBe("1970-01-01T00:00:00.000Z");
  });

  it("honours an explicit generatedAt override", () => {
    const idx = buildIndex(stations, trains, { ...source, generatedAt: "2026-07-09T00:00:00.000Z" });
    expect(idx.generatedAt).toBe("2026-07-09T00:00:00.000Z");
  });
});

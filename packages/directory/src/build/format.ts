// Build stage: emit the compact bundled index. Positional-array rows keep it
// small; the app treats the whole file as opaque and swaps it atomically
// (packages/directory/CLAUDE.md, FR-1.2). Format is documented in FORMAT.md.
import { LOCALE_COLUMNS, type StationRecord, type TrainRecord } from "../clean/types.js";

export const INDEX_VERSION = 1;

export const STATION_COLUMNS = ["code", "name", "city", "state", "lat", "lng"] as const;
export const TRAIN_COLUMNS = ["number", "name", "fromCode", "toCode"] as const;

export type StationRow = [string, string, string, string, number | null, number | null];
export type TrainRow = [string, string, string, string];

export interface DirectoryIndex {
  version: number;
  generatedAt: string;
  source: { name: string; url: string; license: string };
  stationColumns: readonly string[];
  trainColumns: readonly string[];
  localeColumns: readonly string[]; // parallel name columns, empty in P1
  stations: StationRow[];
  trains: TrainRow[];
}

export interface BuildSource {
  name: string;
  url: string;
  license: string;
  generatedAt?: string;
}

export function buildIndex(
  stations: StationRecord[],
  trains: TrainRecord[],
  source: BuildSource,
): DirectoryIndex {
  return {
    version: INDEX_VERSION,
    // Deterministic unless a caller overrides — "same input → byte-identical".
    generatedAt: source.generatedAt ?? "1970-01-01T00:00:00.000Z",
    source: { name: source.name, url: source.url, license: source.license },
    stationColumns: STATION_COLUMNS,
    trainColumns: TRAIN_COLUMNS,
    localeColumns: LOCALE_COLUMNS,
    stations: stations.map((s) => [s.code, s.name, s.city, s.state, s.lat, s.lng]),
    trains: trains.map((t) => [t.number, t.name, t.fromCode, t.toCode]),
  };
}

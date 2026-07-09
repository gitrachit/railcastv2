// Merge a curated supplement over the CC0 base (add-or-override by key). Keeps
// the directory current for renames/new trains the 2016 datameet snapshot
// lacks, without forking the pipeline. Result stays sorted + deterministic.
import { emptyNames, type StationRecord, type TrainRecord } from "./types.js";

export interface Supplement {
  stations?: Array<Partial<StationRecord> & { code: string; name: string }>;
  trains?: Array<Partial<TrainRecord> & { number: string; name: string }>;
}

export interface MergeResult {
  stations: StationRecord[];
  trains: TrainRecord[];
  addedStations: number;
  overriddenStations: number;
  addedTrains: number;
  overriddenTrains: number;
}

export function applySupplement(
  baseStations: StationRecord[],
  baseTrains: TrainRecord[],
  supplement: Supplement,
): MergeResult {
  const stations = new Map(baseStations.map((s) => [s.code, s]));
  const trains = new Map(baseTrains.map((t) => [t.number, t]));
  let addedStations = 0;
  let overriddenStations = 0;
  let addedTrains = 0;
  let overriddenTrains = 0;

  for (const s of supplement.stations ?? []) {
    const code = s.code.toUpperCase();
    if (stations.has(code)) overriddenStations += 1;
    else addedStations += 1;
    stations.set(code, {
      code,
      name: s.name,
      city: s.city ?? "",
      state: s.state ?? "",
      lat: s.lat ?? null,
      lng: s.lng ?? null,
      names: s.names ?? emptyNames(),
    });
  }
  for (const t of supplement.trains ?? []) {
    if (trains.has(t.number)) overriddenTrains += 1;
    else addedTrains += 1;
    trains.set(t.number, {
      number: t.number,
      name: t.name,
      fromCode: (t.fromCode ?? "").toUpperCase(),
      toCode: (t.toCode ?? "").toUpperCase(),
      names: t.names ?? emptyNames(),
    });
  }

  return {
    stations: [...stations.values()].sort((a, b) => a.code.localeCompare(b.code)),
    trains: [...trains.values()].sort((a, b) => a.number.localeCompare(b.number)),
    addedStations,
    overriddenStations,
    addedTrains,
    overriddenTrains,
  };
}

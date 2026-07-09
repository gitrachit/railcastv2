// Clean stage: datameet GeoJSON → normalized, validated, deduped records.
import { emptyNames, type StationRecord, type TrainRecord } from "./types.js";
import { isValidStationCode, isValidTrainNo } from "./validate.js";

interface GeoFeature {
  geometry: { type: string; coordinates: number[] | number[][] } | null;
  properties: Record<string, unknown>;
}
interface GeoJson {
  features: GeoFeature[];
}

export interface CleanResult<T> {
  records: T[];
  dropped: number; // failed validation
  duplicates: number; // same key seen more than once
}

function title(value: string): string {
  return value
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase())
    .trim();
}

export function parseStations(geo: GeoJson): CleanResult<StationRecord> {
  const byCode = new Map<string, StationRecord>();
  let dropped = 0;
  let duplicates = 0;

  for (const f of geo.features) {
    const p = f.properties;
    const code = String(p.code ?? "").toUpperCase().trim();
    const name = String(p.name ?? "").trim();
    if (!name || !isValidStationCode(code)) {
      dropped += 1;
      continue;
    }
    if (byCode.has(code)) {
      duplicates += 1;
      continue;
    }
    const coords = Array.isArray(f.geometry?.coordinates) ? (f.geometry!.coordinates as number[]) : null;
    const address = String(p.address ?? "").trim();
    byCode.set(code, {
      code,
      name: title(name),
      city: address ? title(address.split(",")[0]!) : "",
      state: title(String(p.state ?? "")),
      lng: coords && typeof coords[0] === "number" ? coords[0] : null,
      lat: coords && typeof coords[1] === "number" ? coords[1] : null,
      names: emptyNames(),
    });
  }
  return { records: [...byCode.values()].sort((a, b) => a.code.localeCompare(b.code)), dropped, duplicates };
}

export function parseTrains(geo: GeoJson): CleanResult<TrainRecord> {
  const byNumber = new Map<string, TrainRecord>();
  let dropped = 0;
  let duplicates = 0;

  for (const f of geo.features) {
    const p = f.properties;
    const number = String(p.number ?? "").trim();
    const name = String(p.name ?? "").trim();
    if (!name || !isValidTrainNo(number)) {
      dropped += 1;
      continue;
    }
    if (byNumber.has(number)) {
      duplicates += 1;
      continue;
    }
    byNumber.set(number, {
      number,
      name: title(name),
      fromCode: String(p.from_station_code ?? "").toUpperCase().trim(),
      toCode: String(p.to_station_code ?? "").toUpperCase().trim(),
      names: emptyNames(),
    });
  }
  return { records: [...byNumber.values()].sort((a, b) => a.number.localeCompare(b.number)), dropped, duplicates };
}

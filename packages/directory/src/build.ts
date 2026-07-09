// Pipeline entry: ingest/raw → clean → dist/index.json + bundled Android asset.
// Run `pnpm -F directory ingest` first (downloads the CC0 source). Deterministic:
// same raw input → byte-identical index.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseStations, parseTrains } from "./clean/parse.js";
import { applySupplement } from "./clean/supplement.js";
import { buildIndex } from "./build/format.js";

const rawDir = fileURLToPath(new URL("../ingest/raw/", import.meta.url));
const distDir = fileURLToPath(new URL("../dist/", import.meta.url));
const assetPath = fileURLToPath(
  new URL("../../../android/app/src/main/assets/directory/index.json", import.meta.url),
);

const stationsPath = `${rawDir}stations.json`;
const trainsPath = `${rawDir}trains.json`;
if (!existsSync(stationsPath) || !existsSync(trainsPath)) {
  console.error("ingest/raw missing — run `pnpm -F directory ingest` first (downloads CC0 source)");
  process.exit(1);
}

const stationsGeo = JSON.parse(readFileSync(stationsPath, "utf8"));
const trainsGeo = JSON.parse(readFileSync(trainsPath, "utf8"));

const stations = parseStations(stationsGeo);
const trains = parseTrains(trainsGeo);

// Merge recent entities (renames, post-2016 trains) over the CC0 base.
const supplementPath = fileURLToPath(new URL("../ingest/supplement.json", import.meta.url));
const supplement = existsSync(supplementPath)
  ? JSON.parse(readFileSync(supplementPath, "utf8"))
  : { stations: [], trains: [] };
const merged = applySupplement(stations.records, trains.records, supplement);

const index = buildIndex(merged.stations, merged.trains, {
  name: "datameet/railways",
  url: "https://github.com/datameet/railways",
  license: "CC0-1.0",
});

const json = JSON.stringify(index);
mkdirSync(distDir, { recursive: true });
writeFileSync(`${distDir}index.json`, json);
mkdirSync(fileURLToPath(new URL("../../../android/app/src/main/assets/directory/", import.meta.url)), {
  recursive: true,
});
writeFileSync(assetPath, json);

console.log(
  `directory built: ${merged.stations.length} stations ` +
    `(cc0 ${stations.records.length}, dropped ${stations.dropped}, dup ${stations.duplicates}; ` +
    `supplement +${merged.addedStations}/~${merged.overriddenStations}), ` +
    `${merged.trains.length} trains ` +
    `(cc0 ${trains.records.length}, dropped ${trains.dropped}, dup ${trains.duplicates}; ` +
    `supplement +${merged.addedTrains}/~${merged.overriddenTrains})`,
);
console.log(`index: ${(json.length / 1024).toFixed(0)} KB → dist/index.json + android assets`);

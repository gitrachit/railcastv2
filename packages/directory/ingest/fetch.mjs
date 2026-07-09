// Downloads the datameet/railways CC0 source into ingest/raw/ (gitignored).
// Provenance + checksums are recorded in SOURCES.md; run before `build`.
import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const BASE = "https://raw.githubusercontent.com/datameet/railways/master";
const FILES = ["stations.json", "trains.json"];
const rawDir = fileURLToPath(new URL("./raw/", import.meta.url));

mkdirSync(rawDir, { recursive: true });
for (const file of FILES) {
  const res = await fetch(`${BASE}/${file}`);
  if (!res.ok) throw new Error(`fetch ${file}: HTTP ${res.status}`);
  const buf = Buffer.from(await res.arrayBuffer());
  writeFileSync(`${rawDir}${file}`, buf);
  const sha = createHash("sha256").update(buf).digest("hex");
  console.log(`${file}  ${buf.length} bytes  sha256=${sha}`);
}

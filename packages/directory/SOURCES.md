# Directory data sources & provenance

Licensing is a WS-D gate (packages/directory/CLAUDE.md) — this file records
where every row in the bundled index comes from and under what terms.

## Primary source — datameet/railways (CC0)

- **Repo:** https://github.com/datameet/railways
- **Files:** `stations.json`, `trains.json` (GeoJSON `FeatureCollection`s)
- **License:** CC0-1.0 (public domain dedication) — see the repo's `LICENSE`.
  No attribution required; safe to bundle and redistribute inside the app.
- **Fetched from:** `https://raw.githubusercontent.com/datameet/railways/master/{stations,trains}.json`
- **Retrieved:** 2026-07-09 via `pnpm -F directory ingest` (`ingest/fetch.mjs`).

### Checksums (sha256 of the raw download)

| file           | bytes      | sha256                                                             |
| -------------- | ---------- | ---------------------------------------------------------------- |
| stations.json  | 1,864,683  | `9bd5e1da3a859e5359a95f40b6009aa468efb177da4faac57ffdcd7daeb4df18` |
| trains.json    | 14,768,598 | `e434d9c56016ccdf2291ccd446586ef87a7fab101084efa5f68871ab27800167` |

`ingest/raw/` is gitignored (large, reproducible). Re-run `ingest` to restore
it; the checksums above pin the exact snapshot the current index was built
from. `fetch.mjs` prints the sha256 of each file it downloads — compare
against this table before trusting a rebuild.

### Coverage / known limitations

The datameet dataset is a **2016 snapshot**. After cleaning it yields ~8,945
stations and ~5,138 trains. It therefore lacks:

- Stations renamed after 2016 (e.g. Habibganj → **Rani Kamlapati / RKMP**).
- Trains introduced after 2016 (e.g. **22188** Jabalpur–Rani Kamlapati Intercity).

These gaps are filled by the supplement below rather than by forking the
upstream data.

## Supplement — `ingest/supplement.json` (curated overlay)

A small, hand-maintained overlay merged **over** the CC0 base by `build.ts`
(`clean/supplement.ts` → `applySupplement`). Add-or-override by key (station
`code`, train `number`); the merge keeps the index sorted and deterministic.

- **Origin:** fields transcribed from live RailKit API responses observed
  during development (station names/codes/coords, train name + endpoints).
- **Purpose:** keep the directory current for post-2016 renames and new trains
  without re-snapshotting the entire dataset.
- **Contents (current):** 14 stations + 2 trains — the Jabalpur–Bhopal
  corridor (ADTL, JBP, RKMP, ET/NDPM, …) plus PUNE/VSG for train 12780.
- **License:** factual reference data (station codes, names, coordinates,
  train numbers) — not copyrightable; carried as our own curated list.

To extend coverage, append entries to `supplement.json` and rebuild — no code
change required.

## Rebuild

```
pnpm -F directory ingest   # download CC0 source into ingest/raw/ (verify checksums)
pnpm -F directory build    # clean + merge supplement → dist/index.json + Android asset
pnpm -F directory test     # unit tests for the clean/build stages
```

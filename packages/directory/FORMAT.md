# Bundled index format (`dist/index.json`)

The build stage emits a single JSON file consumed by the Android directory
module. The app treats it as **opaque** and swaps it atomically
(packages/directory/CLAUDE.md, FR-1.2). This document is the contract between
`build/format.ts` and the app's reader.

## Why positional arrays

Rows are stored as positional arrays, not objects, to keep the file small
(~800 KB for ~14k rows). Column order is fixed and published in the header
(`stationColumns` / `trainColumns` / `localeColumns`) so a reader binds by
index, and adding a column is backwards-compatible.

## Top-level shape

```jsonc
{
  "version": 1,                                  // INDEX_VERSION; bump on a breaking format change
  "generatedAt": "1970-01-01T00:00:00.000Z",     // epoch by default → deterministic builds
  "source": { "name": "...", "url": "...", "license": "CC0-1.0" },
  "stationColumns": ["code","name","city","state","lat","lng"],
  "trainColumns":   ["number","name","fromCode","toCode"],
  "localeColumns":  ["hi","bn","ta","te","mr","ml","kn","pa","or","as","gu"],
  "stations": [ /* StationRow[] */ ],
  "trains":   [ /* TrainRow[]   */ ]
}
```

### `stations` — `StationRow`

`[code, name, city, state, lat, lng]`

| idx | column | type            | notes                                  |
| --- | ------ | --------------- | -------------------------------------- |
| 0   | code   | string          | 2–5 uppercase letters; sort key        |
| 1   | name   | string          | title-cased display name               |
| 2   | city   | string          | first address segment; may be `""`     |
| 3   | state  | string          | title-cased; may be `""`               |
| 4   | lat    | number \| null  | WGS84 latitude, `null` if unknown      |
| 5   | lng    | number \| null  | WGS84 longitude, `null` if unknown     |

Sorted ascending by `code`.

### `trains` — `TrainRow`

`[number, name, fromCode, toCode]`

| idx | column   | type   | notes                              |
| --- | -------- | ------ | ---------------------------------- |
| 0   | number   | string | exactly 5 digits; sort key         |
| 1   | name     | string | title-cased display name           |
| 2   | fromCode | string | origin station code (uppercase)    |
| 3   | toCode   | string | destination station code (uppercase) |

Sorted ascending by `number`.

## Localized names (`localeColumns`)

The 11 locale columns are carried from day one and are **empty in P1**
(packages/directory/CLAUDE.md). They are declared in the header so that
filling them later is a data change, not a schema/format change. When
populated, parallel name arrays keyed by these columns will be added
alongside `name` — readers must tolerate their absence.

## Invariants

- **Deterministic:** same `ingest/raw` + `supplement.json` → byte-identical
  `index.json` (`generatedAt` is fixed to the epoch unless overridden). Delta
  generation depends on this.
- **Sorted:** stations by `code`, trains by `number`.
- **Self-describing:** never hard-code column offsets in the reader — read
  `stationColumns` / `trainColumns` and bind by name→index.

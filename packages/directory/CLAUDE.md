# packages/directory — dataset pipeline (WS-B)

Builds the bundled train/station search index and versioned deltas. [FR-1.1–1.3]

## Commands
- `pnpm -F directory build` — source/ → clean → dist/index.bin + dist/vN.delta
- `pnpm -F directory test`

## Pipeline stages (keep separable)
1. ingest/ — raw source files (CSV/JSON). Record provenance + license in SOURCES.md — licensing is a WS-D gate, do not skip.
2. clean/ — normalize names, dedupe, validate codes (station 2–5 uppercase; train 5 digits).
3. build/ — emit compact index consumed by the Android module (format documented in FORMAT.md; app treats it as opaque, swaps atomically).

## Rules
- Schema carries name_hi, name_bn, name_ta, name_te, name_mr, ... from day one (may be empty in P1). Adding a language must never be a schema change.
- Deterministic builds: same input → byte-identical output (delta generation depends on it).
- Every build bumps version and emits a delta from the previous version.

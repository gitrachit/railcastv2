# Railcast monorepo

Ad-free Indian railways companion (Android + BFF backend). Source of truth: **docs/PRD.md** — requirements have IDs (FR-x.x / NFR-x); cite them in commits, e.g. `feat(track): run-date probe [FR-2.3]`.
Work order lives in **docs/backlog.md** (one item per session). API schemas live in **docs/api-contracts.md** — server AND app implement that document exactly.

## Layout
- packages/server — Fastify BFF + Watcher + web layer (TypeScript). Has its own CLAUDE.md.
- packages/directory — train/station dataset pipeline. Has its own CLAUDE.md.
- packages/shared — TS types generated from the contracts doc.
- android/ — Kotlin + Jetpack Compose app. Has its own CLAUDE.md.
- docs/fixtures/ — recorded upstream API payloads for tests.

## Commands
- Server: `pnpm -F server dev | test | typecheck`
- Directory: `pnpm -F directory build | test`
- Android: `./gradlew :app:testDebugUnitTest :app:lintDebug` (run from android/)
- All TS: `pnpm typecheck`

## Invariants — never violate
1. `RAILKIT_API_KEY` exists only in server env/secrets. Never in code, logs, tests, or anything client-side.
2. PNRs: masked in every response/log/UI (`••••2882`), encrypted at rest, purged post-journey. [FR-4.3]
3. Never ask the user for a raw date to track a live train — run-date probe with today/yesterday choice only. [FR-2.3]
4. Status is always icon + word + color, never color alone. [FR-10.2]
5. No ads, no dark patterns, no forced login for core features. Ever. [FR-10.5, §7]
6. Dates in API traffic are YYYY-MM-DD; the upstream's DD-MM-YYYY is converted inside packages/server/src/railkit only.
7. Contract change? Update docs/api-contracts.md in the same PR and regenerate packages/shared.

## Working rules
- Run the package's typecheck/tests after every change; fix before finishing.
- Minimal diffs — do not refactor unrelated code.
- Structural changes (new module, schema, cross-package): propose a plan first and wait for approval.
- Separate commits per logical change.
- When unsure between two approaches, present both briefly and let me choose.

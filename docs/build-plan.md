# Railcast — End-to-End Build Plan (Play Store Launch, Built with Claude Code)

**Version:** 1.0 · **Companion to:** Railcast PRD v1.0
**Goal:** Ship Railcast MVP (PRD Phase 1) to the Google Play Store, with the backend and data pipelines it depends on, using Claude Code as the primary engineering workflow.

---

## 1. Stack decisions (and why)

| Layer | Choice | Rationale |
|---|---|---|
| Android app | **Kotlin + Jetpack Compose** | PRD NFR-1 demands < 25 MB installed and smooth performance on ₹8,000 Androids — native wins on size, cold-start, and battery vs Flutter/RN. Compose gives fast UI iteration; Kotlin is a language Claude Code handles very well. iOS comes later via the shared web layer first, native Swift when justified. |
| Backend (BFF + Watcher) | **Node.js + TypeScript + Fastify** | One language for BFF, watcher, and web layer; Claude Code is strongest in TS; Fastify is light and fast. |
| Cache | **Redis** | Per-key TTL cache + single-flight locks + BullMQ backing store. |
| Database | **PostgreSQL** | Users, saved watches, PNR records (encrypted), watch-job state, report-wrong-data. |
| Job queue (Watcher) | **BullMQ** (Redis-based) | Scheduled/repeating watch jobs, retries, priorities — the FR-7 engine. |
| Push | **Firebase Cloud Messaging** | Android push standard; high-priority messages for the arrival alarm. |
| Shared-journey web | **Server-rendered pages from the BFF** (Fastify + templates) | FR-8 needs fast, no-JS-required pages; reuse the same cache. |
| Local app storage | **Room (SQLite) + DataStore** | Offline cache (FR-9), bundled directory (FR-1), settings. |
| Analytics/crash | **Self-hosted or privacy-light analytics + Crashlytics** | FR-11.3 anonymized metrics; crash-free-session NFR. |
| Hosting | **Single Docker host (Fly.io/Railway/Hetzner) → scale later** | One `docker-compose` for API+Redis+Postgres; NFR-5 festival surge handled by vertical bump + cache hit rate before real autoscaling. |
| CI/CD | **GitHub Actions** | Backend tests/deploy; Android build, test, and AAB artifact; Play publish via Gradle Play Publisher on tags. |

**Deliberately deferred:** iOS app, SEO pages (Phase 3), booking handoff (gated on WS-D), Kubernetes anything.

---

## 2. Monorepo layout (designed for Claude Code)

One repo so Claude Code sees API contracts, app, and infra together. Nested CLAUDE.md files give each package its own operating manual while the root stays small.

```
railcast/
├── CLAUDE.md                  # root: small & stable (see §6.2)
├── docs/
│   ├── PRD.md                 # the PRD v1.0 — the source of truth
│   ├── api-contracts.md       # /screen/* request & response schemas
│   └── decisions/             # ADRs (one file per irreversible choice)
├── packages/
│   ├── server/                # Fastify BFF + Watcher + web layer
│   │   ├── CLAUDE.md
│   │   └── src/
│   │       ├── railkit/       # upstream client: typed wrappers per endpoint
│   │       ├── cache/         # Redis TTL cache + single-flight
│   │       ├── screens/       # composed /screen/* endpoints
│   │       ├── watcher/       # BullMQ jobs, diff engine, push fan-out
│   │       ├── web/           # /t/<token> shared-journey pages
│   │       └── privacy/       # PNR masking/encryption/purge (FR-4.3)
│   ├── directory/             # WS-B dataset pipeline
│   │   ├── CLAUDE.md
│   │   └── src/               # source → clean → build binary index → delta files
│   └── shared/                # TS types for API contracts (server + tooling)
├── android/                   # Kotlin + Compose app (Gradle)
│   ├── CLAUDE.md
│   └── app/src/main/java/app/railcast/
│       ├── core/              # network, Room cache, DataStore, i18n, design system
│       ├── directory/         # bundled search index + fuzzy autocomplete (FR-1)
│       ├── feature/track/     # board hero, timeline, map, coach guide (FR-2, FR-3)
│       ├── feature/pnr/       # PNR, chart states (FR-4)
│       ├── feature/station/   # live board (FR-5)
│       ├── feature/plan/      # A→B, quota, fare (FR-6)
│       ├── feature/alerts/    # alert prefs, alarm full-screen UI (FR-7 client)
│       └── feature/onboarding/
├── infra/
│   ├── docker-compose.yml     # api + redis + postgres, local & prod
│   └── deploy/                # host config, secrets templates
└── .github/workflows/         # ci-server.yml, ci-android.yml, release.yml
```

---

## 3. Backend build spec (packages/server)

### 3.1 RailKit client layer
- One typed function per upstream endpoint; validates inputs (5-digit train, 10-digit PNR, DD-MM-YYYY) before calling.
- `x-api-key` from env/secret manager only; never logged. All calls logged as endpoint + hashed params.
- Known behaviors encoded: `trainHistory` 404 ⇒ `NOT_YET_AVAILABLE`; honor `cancelled` flags.

### 3.2 Cache layer
- Redis `GET key` → hit? serve. Miss/stale? acquire single-flight lock → one upstream call → write with per-endpoint TTL (PRD §6.3 table) → serve to all waiters.
- Stale-while-revalidate: serve stale immediately when upstream is down or slow; mark `"stale": true` in response envelope so clients show honest freshness.

### 3.3 Composed screen endpoints
- `GET /screen/train/:no?run=today|yesterday` — probes candidate run dates (FR-2.3 server side), merges trackTrain + cached route/coords + history summary. Response includes `runDateResolved`.
- `GET /screen/pnr/:pnr` — PNR + joined live train if running. PNR encrypted at rest, masked in any log line (FR-4.3).
- `GET /screen/station/:code?hrs=` and `GET /screen/plan?from&to&date&quota=` — per PRD; plan hydrates availability/fare per row with independent cache keys so slow rows never block the list.

### 3.4 Watcher service (FR-7 — the P1 crown jewel)
- Tables: `watch(id, user_id, type[chart|delay|platform|cancel|arrival], entity_key, params, state_hash, expires_at)`.
- BullMQ repeatable job per *entity* (not per user): one poll of PNR X serves every watcher of PNR X.
- Cadence: PNR ~5 min → 60 s inside chart window; train watch tightens as ETA to alarm station approaches; auto-expire post-journey.
- Diff engine: fetch → normalize → hash → compare → on change emit typed events → push fan-out via FCM (high priority for arrival alarms), respecting quiet hours + per-type opt-in (FR-7.4).
- Delivery log for the §2 metric (chart push ≤ 5 min at ≥ 95%).

### 3.5 Shared-journey web (FR-8, early P2 but scaffolded in P1)
- `GET /t/:token` renders live view from the same cache; unguessable token, journey-scoped expiry, revocation endpoint.

### 3.6 Backend testing
- Vitest unit tests: cache TTL/single-flight, date-probe logic, diff engine, PNR masking.
- Contract tests: recorded RailKit fixtures (the sample payloads from this project) replayed against screen endpoints.
- One load test script (k6) for the hot path before launch (NFR-5).

---

## 4. Android build spec (android/)

### 4.1 Architecture
- Single-activity Compose, MVVM + a thin repository layer; Kotlin coroutines/Flow.
- **Poll controller:** one lifecycle-aware component owning every refresh loop (PRD §6.4) — screens register interest, it schedules, backs off on identical payloads, stops on background, refreshes on resume.
- **Cache-first rendering:** every repository emits Room-cached value first, then network value (SWR), with freshness timestamps in the UI.

### 4.2 Feature → requirement map
| Module | Delivers | PRD |
|---|---|---|
| core/design | tokens from the prototype (colors, type incl. Indic fonts, board hero, status = icon+word+color), dark + sunlight themes | §7, FR-10.2/10.3 |
| directory | bundled index (from packages/directory), fuzzy name search, voice input via SpeechRecognizer | FR-1.x |
| feature/track | run-date sheet, board hero, interpolated map (osmdroid or Maps SDK), timeline w/ day labels, cancelled/diverted states | FR-2.x |
| feature/track (coach) | reversal-aware guide + GEN mode | FR-3.x |
| feature/pnr | masked PNR, chart states + celebration, SMS paste-import (P2) | FR-4.x |
| feature/station | live board, hrs toggle, filters, cancelled rows → alternatives | FR-5.x, FR-2.4 |
| feature/plan | unified list, quota picker, fare breakdown, progressive hydration | FR-6.x |
| feature/alerts | prefs, quiet hours, full-screen arrival alarm (FCM high-priority + `USE_FULL_SCREEN_INTENT`), OEM battery-manager guidance screens | FR-7.x, Open Q3 |
| core/offline | Room caches per entity, offline banner, SMS-139 composer (P2) | FR-9.x |
| core/i18n | string resources EN+HI(+3) full parity, runtime switch | FR-10.1 |

### 4.3 Android testing
- Unit: poll controller back-off, date-choice logic, directory search ranking.
- Compose UI tests for the five screens' states (loading/cached/offline/cancelled).
- Baseline profile + macrobenchmark for cold-start NFR; test matrix must include one low-RAM device profile.

---

## 5. Data workstream (packages/directory — WS-B)
1. Source an open trains/stations dataset (verify license — part of WS-D diligence).
2. Clean/normalize → emit: (a) compact binary/FlatBuffer index bundled in APK, (b) versioned delta files served by BFF (`/directory/version`, `/directory/delta/:from`).
3. P1 ships English; the pipeline carries `name_hi`, `name_ta`, … columns from day one so P2 localization is a data fill, not a schema change.

---

## 6. The Claude Code playbook

How this actually gets built. (Claude Code docs: https://code.claude.com/docs — memory system per current docs.)

### 6.1 Ground rules
- **PRD lives in the repo** (`docs/PRD.md`). Every feature session starts by referencing the FR-IDs it implements; commits mention them (`feat(track): run-date probe [FR-2.3]`).
- **Plan mode for anything structural.** New module, schema, or cross-package change: enter plan mode, review the plan, then execute. Never let a session invent architecture mid-edit.
- **One epic per session; `/clear` between epics.** Fresh context beats compacted context. Auto memory (on by default in current versions) accumulates build/debug learnings across sessions on its own.
- **Small root CLAUDE.md, nested package CLAUDE.md.** Current best practice: root stays a short index of invariants; deep, package-specific guidance lives in the package's own file, loaded when Claude works there.
- **Verification is non-negotiable:** every CLAUDE.md mandates running the package's typecheck/test command after changes.

### 6.2 Root CLAUDE.md (starter)
```markdown
# Railcast monorepo
Ad-free Indian railways companion. Source of truth: docs/PRD.md (requirement IDs FR-x.x — cite them in commits).

## Layout
- packages/server  — Fastify BFF + Watcher + web (TypeScript). See its CLAUDE.md.
- packages/directory — dataset pipeline. See its CLAUDE.md.
- android/         — Kotlin + Compose app. See its CLAUDE.md.
- docs/api-contracts.md — /screen/* schemas. Server AND app must match it; change contract → update doc in same PR.

## Commands
- Server: pnpm -F server test | typecheck | dev
- Android: ./gradlew :app:testDebugUnitTest :app:lintDebug
- All-TS: pnpm typecheck

## Invariants (do not violate)
- RAILKIT_API_KEY only via env; never in code, logs, or client.
- PNRs: mask in UI/logs (••••2882), encrypt at rest, purge post-journey. [FR-4.3]
- Never ask users a raw date for live tracking — run-date probe only. [FR-2.3]
- Status is icon + word + color, never color alone. [FR-10.2]
- No ads, no dark patterns, anywhere. Ever.
- Run typecheck/tests after every change. Minimal diffs; don't refactor unrelated code.
- Structural changes: propose a plan first and wait for approval.
```
Package CLAUDE.md files add: their stack conventions, their test command, the cache-TTL table (server), the poll-controller rule "all refresh loops go through core/PollController" (android), and pointers into `docs/`.

### 6.3 Build order — epics as Claude Code sessions

**M0 — Foundations (week 1)**
1. Scaffold monorepo, CI, docker-compose, CLAUDE.md set. *(Session: "scaffold per docs/build-plan §2; plan first.")*
2. `docs/api-contracts.md` written **before** code — Claude Code drafts it from the PRD; you approve; it becomes the contract both sides implement.

**M1 — Server core (weeks 1–3)**
3. RailKit client + validation + fixtures from our sample payloads.
4. Redis cache + single-flight + SWR (unit-tested).
5. `/screen/train` with run-date probe → `/screen/pnr` → `/screen/station` → `/screen/plan`.

**M2 — Watcher (weeks 3–5)** — build early; it's the riskiest novel piece.
6. Watch schema + BullMQ scheduling + diff engine (fixture-driven tests: chart flip, delay threshold, cancellation).
7. FCM fan-out + quiet hours + delivery logging.

**M3 — Android core (weeks 3–6, parallel)**
8. Design system from the prototype; app shell + nav + i18n EN/HI.
9. Poll controller + Room SWR repositories.
10. Directory pipeline v1 + bundled index + search UI with voice.

**M4 — Android features (weeks 6–10)**
11. Track (board hero, timeline, map interpolation, run-date sheet, cancelled state).
12. Coach guide incl. GEN mode. 13. PNR + chart celebration. 14. Station board. 15. Plan + quota + fares. 16. Alerts + full-screen alarm + OEM battery guidance.

**M5 — Hardening (weeks 10–12)**
17. Offline pass on every screen; error/empty states per PRD §7.
18. k6 load test; macrobenchmark; low-RAM device pass; accessibility (TalkBack) pass.
19. FR-11.3 analytics events for the §2 metrics; privacy policy page.

**M6 — Launch (weeks 12–14)** — §7 below.

Each numbered item ≈ one or two Claude Code sessions: state the FR-IDs, point at the contract doc, plan mode for the first of each kind, tests green before commit.

### 6.4 Session prompt template
```
Implement [epic] per docs/PRD.md [FR-x.x, FR-y.y] and docs/api-contracts.md.
Constraints: minimal diff; follow package CLAUDE.md; write/extend tests; run
[test command] and fix failures before finishing. If any contract change is
needed, stop and propose it first.
```

---

## 7. Play Store launch checklist (M6)

**Play Console & policy**
- Developer account; app identity `app.railcast`; **Play App Signing** enrolled; upload key in CI secrets.
- Target current required API level; AAB with resource shrinking + R8 (verify NFR-1 size budget in CI).
- **Data safety form** (matches FR-4.3/11.3 exactly: what's collected, encryption, deletion) + hosted **privacy policy** (DPDP-aware) — reuse the BFF web layer to host it.
- Content rating questionnaire; "not affiliated with Indian Railways" in the listing description (FR-11.1).
- Permissions review: notifications, location (in-context rationale), `USE_FULL_SCREEN_INTENT` justification for the arrival alarm, SpeechRecognizer.

**Release train**
1. **Internal testing** (team) → fix pre-launch report findings.
2. **Closed testing** — 30–50 real users across Hindi/English, incl. 55+ testers and low-end devices (this is the §2 usability metric, measured for real). Google's personal-account requirement for closed-testing graduation is satisfied here if applicable.
3. **Open testing** in launch geography → watch crash-free rate + chart-push latency dashboards.
4. **Production staged rollout** 5% → 20% → 50% → 100%, halting on any NFR breach.

**Store listing** — screenshots straight from the five real screens (board hero is the visual hook), short description leading with "Live train status. No ads, ever."; localized listing EN+HI at minimum.

**Launch gates (must be green)**
- WS-D verified: RailKit Advance terms permit watcher polling volume; pricing modeled (WS-E) against expected DAU.
- PRD §13 acceptance snapshot passes end-to-end on a real device with a real train.
- Chart-push metric instrumented and meeting target in open testing.
- Rollback plan: server is versioned/deployable independently; app killswitch flags for watcher-dependent features.

---

## 8. Ongoing (post-launch)
- Weekly release cadence via the same CI; Claude Code sessions per bugfix with crash logs pasted in.
- Phase 2 epics queue up exactly like M-epics: SMS-139 bridge, shared-journey web view public launch, 12 languages (directory data fill), prediction/confirmation intelligence.
- Festival-season load test 6 weeks before Diwali (NFR-5) — calendar it now.

---

## 9. What "done" means
The PRD §13 acceptance sentence, executed on a ₹8,000 phone from the Play Store build: language pick → speak "goa express" → correct yesterday-run auto-chosen → honest map position → coach-end guidance across the reversal → masked PNR saved → woken at 4 a.m. by the chart push with the app closed → cancellation alert with instant alternatives — no ads, working in sunlight, surviving a dead signal.

Ship that, and Railcast has earned the "killer" claim.

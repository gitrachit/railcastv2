# Railcast — Claude Code Session Backlog

Work top-to-bottom. **One session per item. `/clear` between items.** Check the box, commit with the FR-IDs, move on.
Prompt template for every session:

> Implement **[item]** per docs/PRD.md **[FR-IDs]** and docs/api-contracts.md.
> Constraints: minimal diff; follow the package CLAUDE.md; write/extend tests; run the package test command and fix failures before finishing. If a contract change is needed, STOP and propose it first.

Use **plan mode** for every item marked ⚠ (structural — review the plan before execution).

---

## M0 — Foundations
- [x] ⚠ **0.1 Scaffold monorepo**: pnpm workspace (`packages/server`, `packages/directory`, `packages/shared`), Android Gradle project (`android/`, package `app.railcast`, Compose, minSdk 24), `infra/docker-compose.yml` (postgres+redis), GitHub Actions stubs (`ci-server`, `ci-android`). Everything builds green.
- [x] **0.2 Shared types**: generate `packages/shared` TS types from docs/api-contracts.md §0–§8. Server imports them; add a CI check that fails when contracts doc and types drift (hash comment).

## M1 — Server core
- [x] ⚠ **1.1 RailKit client** [PRD §6]: typed wrapper per upstream endpoint; input validation (5-digit train, 10-digit PNR, date conversion YYYY-MM-DD ↔ upstream DD-MM-YYYY); `RAILKIT_API_KEY` from env only; fixtures in `src/railkit/__fixtures__/` (use the recorded sample payloads in docs/fixtures/); `trainHistory` 404 → `NOT_YET_AVAILABLE`.
- [x] ⚠ **1.2 Cache layer**: Redis TTL cache per §10 of contracts; single-flight (one upstream call per key across concurrent waiters); stale-while-revalidate with `meta.stale`; unit tests incl. concurrency test.
- [x] **1.3 Auth**: `POST /auth/device` anonymous tokens; Bearer middleware; per-device rate limit.
- [x] ⚠ **1.4 `/screen/train`** [FR-2.1–2.4]: run-date probe (`run=auto` checks today & yesterday, picks active), merge track+route+coords, position interpolation, cancelled/diverted states, coach guide with reversal detection from coach-position timeline [FR-3.1–3.2].
- [x] **1.5 `/screen/pnr`** [FR-4.1, FR-4.3]: masked responses, AES-encrypted at rest, purge job (post-journey + N days), join live status.
- [x] **1.6 `/screen/station`** [FR-5.1]: window param, cancelled rows.
- [ ] **1.7 `/screen/plan` + row hydration** [FR-6.1–6.4]: quota-aware, `pending` rows, `/screen/plan/row`.

## M2 — Watcher (the crown jewel — build before the app UI)
- [ ] ⚠ **2.1 Watch model + scheduler** [FR-7.1, FR-7.5]: Postgres schema, BullMQ repeatable job **per entity** (dedup across users), adaptive cadence (PNR 5 min → 60 s in chart window; arrival watches tighten near ETA), auto-expiry.
- [ ] ⚠ **2.2 Diff engine + events** [FR-7.2]: normalize → hash → diff → typed events (chart_prepared, delay_threshold, platform_change, cancelled, arrival_due). Fixture-driven tests for each transition.
- [ ] **2.3 Push fan-out** [FR-7.3, FR-7.4]: FCM data messages per contracts §5; quiet hours (arrival alarm bypasses); delivery latency logging (the ≥95% ≤5 min metric).
- [ ] **2.4 Watch API**: POST/GET/DELETE /watch, /device/push-token.
- [ ] **2.5 Share scaffold** [FR-8]: token create/revoke + minimal `/t/:token` HTML page from cache.

## M3 — Android core (parallel with M2)
- [ ] ⚠ **3.1 App shell**: single-activity Compose, bottom nav (Home/Track/Station/Plan/Alerts), design tokens ported from docs/prototype (colors, type, board-hero component, status chip = icon+word+color [FR-10.2]), dark theme.
- [ ] **3.2 i18n**: EN + HI full string parity, runtime switch, no hardcoded strings rule enforced by lint [FR-10.1].
- [ ] ⚠ **3.3 Networking + SWR**: Retrofit/Ktor client from contracts; Room entity cache per key; repository pattern "emit cached → fetch → emit fresh"; freshness timestamps surfaced [FR-2.5, FR-9.1].
- [ ] ⚠ **3.4 PollController**: single lifecycle-aware controller owning ALL refresh loops — register(screenKey, cadence), back-off on identical payloads, stop on background, refresh on resume [PRD §6.4]. Unit-tested.
- [ ] ⚠ **3.5 Directory v1** [FR-1.1–1.5]: `packages/directory` pipeline (source→clean→index), bundled index in assets, fuzzy search (name/number/code), voice input via SpeechRecognizer, client-side format validation.

## M4 — Android features
- [ ] **4.1 Onboarding** [FR-10.5]: native-script language picker → one intent question → in. No login, no tutorial.
- [ ] **4.2 Home**: search + results, saved cards with live refresh via PollController.
- [ ] ⚠ **4.3 Track** [FR-2.x]: run-date sheet (auto-detected default, never a raw date field), board hero, timeline w/ day labels, map with interpolated marker labelled "estimated", cancelled/diverted full-screen states → alternatives CTA.
- [ ] **4.4 Coach guide** [FR-3.x]: platform diagram, reversal note, GEN mode toggle.
- [ ] **4.5 PNR** [FR-4.x]: masked everywhere, chart states, chart-prepared celebration, save→creates chart watch.
- [ ] **4.6 Station** [FR-5.x]: live board, 2/4/8 toggle, filters, cancelled row → plan handoff.
- [ ] **4.7 Plan** [FR-6.x]: unified list, progressive row hydration, quota picker w/ Tatkal hint + reminder watch, fare breakdown.
- [ ] ⚠ **4.8 Alerts + arrival alarm** [FR-7 client]: prefs screen, quiet hours, FCM handling, full-screen alarm activity (`USE_FULL_SCREEN_INTENT`), OEM battery-settings guidance flow (Xiaomi/Oppo/Vivo).
- [ ] **4.9 Offline pass** [FR-9.1–9.2]: banner, cached rendering audit on every screen, directory offline.

## M5 — Hardening
- [ ] **5.1 Error/empty states audit** per PRD §7 on all screens.
- [ ] **5.2 Perf**: baseline profile, macrobenchmark cold start ≤2.5 s on low-RAM profile; APK size budget check in CI [NFR-1].
- [ ] **5.3 Load test** (k6) on hot paths; verify ≥90% cache hit [NFR-5].
- [ ] **5.4 Accessibility pass**: TalkBack labels, 48 dp targets, text-scaling reflow [FR-10.3].
- [ ] **5.5 Analytics** [FR-11.3]: anonymized events for time-to-answer, alert latency, first-session success. No PNR contents anywhere.
- [ ] **5.6 Privacy**: policy page on web layer; data-purge verification; DPDP checklist.

## M6 — Launch
- [ ] **6.1 Play Console**: app signing, data-safety form (mirror FR-4.3/11.3), content rating, listing (EN+HI) with "No ads, ever." and non-affiliation disclaimer.
- [ ] **6.2 Internal → Closed testing** (incl. 55+ / low-end testers; measure §2 usability metric) → fix pre-launch report.
- [ ] **6.3 Open testing** in launch geo; dashboards: crash-free, chart-push latency.
- [ ] **6.4 Staged production rollout** 5→20→50→100 with halt criteria = any NFR breach.

**Gates before 6.4:** WS-D (RailKit terms permit watcher volume; pricing modeled) and PRD §13 acceptance run end-to-end on a real device.

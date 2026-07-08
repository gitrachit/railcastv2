# android/ — Railcast app

Kotlin + Jetpack Compose, single-activity, MVVM + repositories, coroutines/Flow, Room + DataStore, Retrofit. minSdk 24, package app.railcast.

## Commands (run from android/)
- `./gradlew :app:testDebugUnitTest` · `./gradlew :app:lintDebug` · `./gradlew :app:assembleDebug`

## Module map
- core/design — tokens ported from docs/prototype (Railcast-v3.html): colors, type scale, board-hero, StatusChip (icon+word+color), dark + sunlight themes.
- core/net + core/db — API client generated against docs/api-contracts.md; Room cache; repositories emit cached→fresh (SWR) with freshness timestamps.
- core/poll — PollController: THE only owner of refresh loops. Screens register (key, cadence); it backs off on identical payloads, stops on background, refreshes on resume. No screen may run its own timer.
- directory/ — bundled index + fuzzy search + SpeechRecognizer voice input.
- feature/{onboarding,home,track,pnr,station,plan,alerts} — one module per tab/flow.

## Rules
- No hardcoded user-facing strings — resources only; EN and HI must stay at full parity (lint enforces). [FR-10.1]
- Kotlin models mirror docs/api-contracts.md field-for-field; if the contract lacks something, STOP and propose a contract change — never invent fields.
- All user-visible status uses StatusChip. Map position is always labelled "estimated". Freshness stamp on every live surface. [FR-2.2, FR-2.5, FR-10.2]
- PNR appears masked everywhere including logs/analytics. [FR-4.3]
- Touch targets ≥ 48dp; every interactive element has a contentDescription. [FR-10.3]
- Offline: screens render last cached data with the offline banner — never a blank error screen. [FR-9.1]
- Tests to keep green: PollController back-off/lifecycle, directory search ranking, run-date sheet logic, one Compose state test (loading/cached/offline/cancelled) per feature.

# Railcast — Android Architecture & Play Store Launch Engineering

**Status:** Engineering guide / execution plan · **Date:** July 2026
**Companion to:** [`build-plan.md`](./build-plan.md) §7 (launch checklist — this doc is its detailed successor), [`ux-redesign.md`](./ux-redesign.md) (the UI it ships), [`ui-architecture.md`](./ui-architecture.md) (how the app is built today), [`PRD.md`](./PRD.md) (requirements), [`dpdp-checklist.md`](./dpdp-checklist.md) (data-protection status), and the backlog **M6 — Launch**.

Two questions, one answer:

> **1. What is the best-possible Kotlin/Android design for Railcast?** (Part A)
> **2. What does it take to ship it on Google Play — every aspect?** (Parts B–C)

This is written to be *executed*, not admired: every recommendation is concrete (copy-pasteable Gradle/manifest/CI/ProGuard), grounded in the repo as it actually stands today, and honest about the gap between the two. It builds on the codebase's real strengths (`PollController`, `Resource<T>` SWR, MVVM-with-interfaces, manual DI, size discipline) rather than replacing them.

---

## Table of contents

- [Part 0 — Where we are today (honest audit)](#part-0--where-we-are-today-honest-audit)
- [Part A — The best-possible Android/Kotlin design](#part-a--the-best-possible-androidkotlin-design)
- [Part B — Quality gates & release engineering](#part-b--quality-gates--release-engineering)
- [Part C — Play Store readiness (every aspect)](#part-c--play-store-readiness-every-aspect)
- [Part D — Execution roadmap & the master launch checklist](#part-d--execution-roadmap--the-master-launch-checklist)

Rankings use **Impact / Effort / Launch-blocking?** where useful.

---

# Part 0 — Where we are today (honest audit)

Read from the actual repo (`android/app/build.gradle.kts`, `AndroidManifest.xml`, `libs.versions.toml`, `ci-android.yml`, `res/`, `proguard-rules.pro`). This is the truth the rest of the doc closes against.

| Area | State | Detail |
|---|---|---|
| Toolchain | ✅ current | AGP 8.11.1, Kotlin 2.1.21, KSP, Compose BOM 2025.05, JDK 17 |
| SDK levels | ✅ ok, treadmill | `compileSdk=35`, `targetSdk=35`, `minSdk=24`, `versionCode=1`, `versionName=0.1.0` |
| Release build | ✅ good bones | `isMinifyEnabled=true`, `isShrinkResources=true`, R8 optimize, signing wired via gitignored `keystore.properties` |
| **ProGuard/R8 keep rules** | ❌ **empty** | `proguard-rules.pro` = *"none yet"* — Retrofit/kotlinx-serialization/Room/Firebase reflection **will break in a minified release build**. Launch-blocking, and untested because CI only builds *debug*. |
| Signing in CI | ❌ missing | CI builds `assembleDebug` only; no `bundleRelease`, no upload-key secret, no Play publish |
| App bundle | 🔶 partial | `bundle { language { enableSplit=false } }` is right (ship all langs); but no AAB is ever built/verified in CI |
| Manifest perms | ✅ minimal & justified | `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `ACCESS_NETWORK_STATE`, `ACCESS_COARSE_LOCATION`, speech `<queries>`. No SMS/storage/contacts — keep it that way. |
| `USE_FULL_SCREEN_INTENT` | 🔶 needs Play form | Since Android 14 only alarm/calling apps get it auto-granted; Play requires a **declaration**. The arrival alarm qualifies — but it must be declared and justified (§C4). |
| Theme / splash | ❌ dated | `Theme.Railcast` = `android:Theme.Material.Light.NoActionBar` — not an AppCompat/Material3 theme, **no SplashScreen API**, no branded cold start |
| App icon | 🔶 partial | Adaptive icon present (`ic_launcher_foreground` + `anydpi-v26`); **no `monochrome` layer** → no Android 13+ themed icon |
| Baseline profile | ❌ missing | NFR-1 (cold start ≤ 2.5 s) has no profile shipped; build-plan 5.2 notes "no CI emulator" — solvable with Gradle Managed Devices (§B5) |
| Crash/ANR reporting | ❌ none | No Crashlytics / Play SDK; Play vitals thresholds can block a rollout |
| FCM | 🔶 inert by design | `firebase-messaging` compiled; runtime off until `google-services.json` lands. Correct staging — but push is a **P1 hero feature**, so this must land for launch. |
| i18n gate | ✅ enforced | Lint errors on `HardcodedText`/`MissingTranslation`/`ExtraTranslation` + `StringsParityTest` |
| CI | 🔶 solid but thin | lint → unit test → `assembleDebug` → 20 MiB debug-APK budget. No detekt/format, no screenshot tests, no release build, no publish |
| Store assets | ❌ none | No feature graphic, screenshots, 512² icon export, or listing copy in-repo |
| Data safety / privacy | 🔶 designed, not filed | `dpdp-checklist.md` done in code; `/privacy` hosted on BFF; Data-safety form + content rating + account-deletion declaration not yet filed |
| Modularity | 🔶 single Gradle module | Clean *package* structure (`core/`, `feature/`, `directory/`) but one `:app` module — fine for now, a scaling seam later (§A2) |
| DI | ✅ deliberate | Manual `AppContainer` composition root in `RailcastApplication` — testable, zero-dep, aligned with size ethos |

**One-line verdict:** the app is architecturally sound and ~70% of the way to a *build* that runs, but only ~30% of the way to a *release that survives R8 and passes Play review*. The five things that will bite first: (1) empty ProGuard rules, (2) no signed AAB ever built/tested, (3) plain theme/no splash, (4) no crash reporting, (5) the `USE_FULL_SCREEN_INTENT` + Data-safety declarations. Parts B–C close all of them.

---

# Part A — The best-possible Android/Kotlin design

The goal isn't novelty; it's a design that is **fast on a ₹8,000 phone, testable without a device, honest under bad conditions, and boring to operate** — because boring is what "world-class" feels like at 2 a.m. during a Diwali surge. Every choice below is measured against the PRD's NFRs, not against fashion.

## A1. Principles (keep the good, sharpen the rest)

1. **Unidirectional data flow, immutable state.** `UiState` is an immutable `data class`; the ViewModel is the only writer; the composable is a pure function of state. (Already the house style — formalize it as law.)
2. **Android types stop at the ViewModel boundary.** ViewModels take interfaces/lambdas; everything below is plain-JVM testable. (Already true — it's why `TrackViewModelTest` runs without Robolectric. Protect it.)
3. **One owner per cross-cutting concern.** Refresh → `PollController`. Time → `IsoTime`. Freshness → `Freshness`. Masking → `maskPnr`. Never a second copy.
4. **Dependencies earn their bytes.** NFR-1 (< 25 MB) is a design constraint, not an afterthought. Prefer a hand-rolled 200-line solution to a 2 MB library (the codebase already does this with `IsoTime` vs `java.time`).
5. **Degrade, never blank.** `Resource<T>` renders cache-then-fresh; failure with cache never blocks. (Shipped — keep it sacred.)
6. **Design for the glance, engineer for the wait** (from `ux-redesign.md`): the 2-second answer and the 20-minute dwell are different budgets; the glance always wins a conflict.

## A2. Modularization — the target graph (and *when* to bother)

Today's single `:app` module with clean packages is **correct for the current size**. Multi-module Gradle earns its keep at ~3+ engineers or when build times cross ~90 s incremental. Plan the seam now, cut it when it hurts:

```
build-logic/                 ← convention plugins (one place for compileSdk, Compose, test setup)
 └─ railcast.android.library, railcast.android.compose, railcast.jvm.library, railcast.android.feature

:app                         ← DI wiring (AppContainer), NavHost, Application, Manifest
:core:design                 ← Signalbox: tokens, Theme, JourneyBoard, Spine, StatusChip …  (android-compose)
:core:model                  ← pure Kotlin domain types (Journey, TrainScreen, Resource)   (jvm)
:core:data                   ← repositories, SWR flows, ScreenCache                          (android-library)
:core:network                ← Retrofit API, envelopes, auth interceptor, cert pinning       (android-library)
:core:database               ← Room entities/DAOs, journal table                             (android-library)
:core:datastore              ← prefs, saved journeys, alert prefs                             (android-library)
:core:poll                   ← PollController (no Android deps — pure)                        (jvm)
:core:common                 ← IsoTime, monoNumerals, FormatValidation, Dispatchers           (jvm)
:directory                   ← bundled index + DirectorySearch + VoiceSearchContract          (android-library)
:feature:home :feature:search :feature:journey :feature:station :feature:plan :feature:activity
```

- **Convention plugins in `build-logic/`** kill the copy-paste in every `build.gradle.kts` and make "bump compileSdk" a one-line change. This is the single highest-leverage build-infra move; do it *before* the module split, not after.
- **`:feature:journey`** is the merge of today's `track` + `pnr` (per `ux-redesign.md` §4.4). `:feature:search` is the new omnibox; `:feature:activity` is the journal.
- Dependency rule enforced by module visibility: `feature/*` may depend on `core/*` but **never** on each other; `core/model`/`core/common`/`core/poll` are pure-JVM leaves. Enforce with the `dependency-analysis` plugin (§B2).

**Ranking:** convention plugins — Impact H / Effort M / not launch-blocking. Module split — Impact M / Effort H / **defer past launch** (don't destabilize the tree pre-1.0).

## A3. Layering & unidirectional data flow

```
Compose (dumb) ─state→ ViewModel(StateFlow) ─calls→ Repository ─┬→ ScreenCache (Room)   [emit first]
      ▲                                                          └→ RailcastApi (Retrofit)[emit fresh]
      └──────────────── events (lambdas) ──────────────────────────
```

- **Repository is the SWR seam** (already the pattern): `fun trainScreen(no, date): Flow<Resource<TrainScreen>>` emits cache immediately, then the network result, normalizing every failure to a retryable `Resource` with the last-known value intact.
- **ViewModel** exposes exactly one `StateFlow<XUiState>`, built with `stateIn(scope, SharingStarted.WhileSubscribed(5_000), initial)` so it survives config changes and cancels 5 s after the screen leaves — battery-frugal by construction.
- **`ConsequenceLine` and `JourneyState` derivation are pure functions** (`ux-redesign.md` §9.2) unit-tested like `MonoNumerals` — the "answer→consequence→action" logic never lives in a composable.

## A4. Dependency injection — keep manual, formalize the seam

The manual `AppContainer` is a *feature*, not debt: it's zero-dependency, fully testable, and adds no APK weight or annotation-processing time. **Recommendation: keep it through launch.** Formalize it:

```kotlin
class AppContainer(app: Application) {
    // singletons: outlive any activity (push wakes the process with no UI)
    val dispatchers = AppDispatchers()                       // inject Dispatchers, never hardcode
    private val db by lazy { RailcastDb.build(app) }
    private val http by lazy { OkHttpProvider.build(app, session) }
    val poller = PollController(dispatchers.default)
    // factories: ViewModels get interfaces, not Android types
    fun journeyViewModel(trainNo: String, pnr: String?) =
        JourneyViewModel(repo.journey(trainNo, pnr), poller, dispatchers)
}
```

- **Inject `CoroutineDispatcher`s** (an `AppDispatchers` holder) everywhere — never call `Dispatchers.IO` inline. This is the one discipline that keeps `runTest` deterministic.
- **When to adopt a framework:** if the module split (A2) lands and wiring across modules gets painful, reach for **Metro** or **Hilt** — but only then, and measure the APK/KSP cost against NFR-1 first. Manual DI is not the thing to "fix" before 1.0.

## A5. Navigation — go type-safe

Today's string routes (`ui-architecture.md` §2) work but stringly-typed args are a latent bug class. Navigation-Compose 2.8+ supports `@Serializable` type-safe routes — adopt them for the redesign's routes:

```kotlin
@Serializable data object Home
@Serializable data object Search
@Serializable data object Activity
@Serializable data class Journey(val trainNo: String, val pnr: String? = null, val date: String? = null)
@Serializable data class Station(val code: String)
@Serializable data class Plan(val from: String? = null, val to: String? = null, val date: String? = null)

composable<Journey> { entry -> val a = entry.toRoute<Journey>(); JourneyScreen(a.trainNo, a.pnr) }
```

- Keeps the redesign's rule intact: **the 3 tabs are the only bottom-bar destinations**; `Journey/Station/Plan` are detail routes pushed above the shell (`ux-redesign.md` §3.2). `popUpTo(startDestination){saveState} + launchSingleTop + restoreState` stays.
- **Deep links** for shared journeys attach here: `composable<Journey>(deepLinks = listOf(navDeepLink { uriPattern = "https://railcast.app/t/{token}" }))` (wired to App Links in §C9).

## A6. State management & Compose performance (the NFR-1 workhorse)

The redesign adds a map, a live spine, and flap-roll animation — none of which may cost jank on an entry-level phone. Rules:

1. **Strong-skipping is on** (Compose compiler ≥ Kotlin 2.0 default). Keep every `UiState` and its members **stable/immutable** (`data class`, `kotlinx.collections.immutable` `ImmutableList` for lists) so recomposition skips unchanged subtrees. Add a Compose-compiler **stability report** to CI (§B2) and treat an unstable `UiState` as a bug.
2. **`collectAsStateWithLifecycle()`** (not `collectAsState()`) for every StateFlow — stops recomposition work when the screen isn't `STARTED`.
3. **Defer state reads to the lowest node.** Position/animation values read inside `graphicsLayer {}` / lambda-modifiers, never in a parent that would relayout the whole spine on every frame. The flap-roll and marker glide are `graphicsLayer`-only (translation/alpha) — **no relayout**.
4. **Stable `key`s in every `LazyColumn`** — spine nodes keyed by station code, journal rows by id, board rows by train number — so a poll tick recomposes only the changed row (this is *why* the "animate-only-on-change" rule works).
5. **The animation law holds** (`ui-architecture.md` §6): only `PollController` runs a timer; every visual transition reacts to a state change. A no-change poll tick must be visually silent.
6. **No new heavyweight deps for motion:** all of it is Compose-native (`AnimatedContent`, `animateColorAsState`, `AnchoredDraggable`, `SharedTransitionLayout`). No Lottie, no Maps SDK for v1 — the route "map" is a `Canvas` polyline over the bundled station coordinates (`ux-redesign.md` §9.2), which also keeps it offline and inside the size budget.

## A7. Design system — Signalbox (from `ux-redesign.md` Part V)

`:core:design` is the crown module. It already has the right shape (`Colors/Type/Dimens/Theme/RailcastIcons/BoardHero/StatusChip/MonoNumerals`). Extend, don't rebuild:

- **Three palettes** — Light / Dark / **Sunlight** (the FR-5.3 high-contrast set) — selected by user toggle ∨ ambient-light suggestion, exposed via the existing `CompositionLocal`.
- **Two-surface grammar** — *Paper* (read/decide) vs *Board* (live truth); Board is scarce and always mono-numeraled + freshness-stamped.
- **New tokens:** `boardRed`, `estimate` (dashed/60%-opacity — honesty rendered), `focus` (visible keyboard/switch-access ring, FR-10.3).
- **New components:** `JourneyBoard` (evolves `BoardHero` + consequence line), `Spine`/`MiniSpine`, `PassCard`, `CoachDiagram` (evolves `CoachLayout`), `BoardRow`, `OmniSearchField`, `ActionDock`, `DayScrubber`, `QuotaSheet`, `JournalRow`, `ContextRow`, `SheetScaffold`. Full contracts in `ux-redesign.md` §5.5.
- **Material 3 stays underneath** so stock switches/buttons inherit brand/signal colors, but the app never *looks* stock (the two-surface grammar + mono facts + the flap-roll are the identity).

## A8. Concurrency & `PollController` (unchanged, protected)

The single refresh-loop owner stays exactly as designed (`ui-architecture.md` §6): register `(key, cadence, signatureFn)`; back off ×2 (cap 8×) on identical signatures; reset on change; stop on `ON_STOP`, refresh on `ON_START`; **background delivery is push-only** (NFR-3). The redesign adds *zero* new timers — the Live Updates ongoing notification is fed by FCM + the existing foreground `Resource` flow, not a new poll.

## A9. Data layer — persistence, security, network

- **Room + DataStore** kept. New: a small `journal_entries` table for the Activity feed (`ux-redesign.md` §9.2), written by the FCM receiver and watch-registration, purged with the journey [FR-4.3].
- **PNR never persisted raw on device** (already true — SWR cache keyed by SHA-256; request/watch-body only). Nothing to encrypt on-device because nothing raw is stored. Keep it that way; do **not** add a raw-PNR column "for convenience".
- **Certificate pinning** on the BFF host via OkHttp `CertificatePinner` (pin the leaf/intermediate SPKI) — add a **backup pin** and a documented rotation runbook so a cert roll doesn't brick the fleet. Trade-off noted: pinning + no remote-config-of-pins means a bad pin is a hard outage; ship pins with a generous overlap window.
- **Network Security Config** (`res/xml/network_security_config.xml`): `cleartextTrafficPermitted=false`, pin as defense-in-depth, and a `debug-overrides` block trusting a local proxy CA **only** in the debug build. Reference it from `<application android:networkSecurityConfig=...>`.
- **`ApiResult`/`Resource`** normalization kept; anonymous device-token auth (`DeviceSession`/`AuthInterceptor`/`TokenAuthenticator`) kept, with single-flight mint + 401 re-mint-replay.

## A10. Feature flags & killswitch (operational safety for launch)

The build-plan's rollout gate says "app killswitch flags for watcher-dependent features." Make it real and privacy-clean:

- A tiny `/config` field on the existing `/auth/device` or a dedicated cached endpoint returns a `flags` map (e.g. `{ liveUpdates: true, sunlightAuto: true, sms139: false }`). No Firebase Remote Config needed (avoids another SDK + its data-safety surface); reuse the BFF + `ScreenCache`.
- Every risky new surface (Live Updates notification, cert-pin enforcement, watcher-dependent alarms) reads a flag with a safe default, so a bad launch day is a server toggle, not an app update. This is the difference between a 6-hour incident and a 6-day one.

---

# Part B — Quality gates & release engineering

"Best-ever design" is meaningless if a minified build crashes or a regression ships. These gates are what make the design *stay* good.

## B1. The testing pyramid (device-light by design)

| Layer | Tool | What it covers | Runs where |
|---|---|---|---|
| **Unit (bulk)** | JUnit + coroutines-test + fakes | ViewModels, `PollController` backoff/lifecycle, `DirectorySearch`, `IsoTime`, `monoNumerals`, run-date probe, `QueryClassifier`, `ConsequenceLine`, `JournalPolicy`, `SpineCollapse` | JVM (CI, fast) — **keep this the bulk** |
| **Screenshot** | **Roborazzi** (JVM, no device) | Every Signalbox component + each screen in Light/Dark/Sunlight × loading/cached/offline/cancelled; catches visual regressions the thin composables can't unit-test | JVM (CI) — **add** |
| **Compose UI** | `createAndroidComposeRule` | A handful of real interaction flows (omnisearch classify, sheet detents, run-date sheet) | `androidTest` on **Gradle Managed Device** (CI) — add sparingly |
| **Macrobenchmark + Baseline Profile** | `androidx.benchmark.macro` | Cold-start ≤ 2.5 s (NFR-1); generates the baseline profile shipped in release | GMD emulator (CI) — **add** (§B5) |
| **Accessibility** | accessibility-test-framework + manual TalkBack | 48 dp targets, labels, contrast, traversal order (FR-10.3) | CI check + manual pre-launch |

**Roborazzi is the biggest bang-for-buck addition:** JVM-fast, no emulator, and it turns "we verify visuals by eye on-device" (`ui-architecture.md` §10) into a CI gate — exactly what a design-heavy redesign needs to not regress.

## B2. Static analysis & hygiene

Add to CI, in this order of value:
1. **Spotless + ktlint** — format gate (zero-diff enforced). One-time reformat, then free forever.
2. **detekt** (with `detekt-compose` / **compose-lints** from Slack) — catches unstable params, missing `Modifier` defaults, `collectAsState` instead of `…WithLifecycle`, hoisting mistakes.
3. **Android Lint with a baseline** — promote `HardcodedText`/`MissingTranslation`/`ExtraTranslation` (already errors) and add `NewApi`, `Recycle`, `VisibleForTests`; freeze existing noise with `lint-baseline.xml`.
4. **Compose compiler metrics** — emit stability reports (`-Pcompose.reports`) and fail if a `UiState` becomes unstable.
5. **`com.autonomousapps.dependency-analysis`** — flags unused deps (size!) and illegal module edges (A2's dependency rule).
6. **Renovate/Dependabot** on the version catalog — the target-API/library treadmill (§C3) is easier when bumps are continuous.

## B3. Build variants, flavors & signing

Formalize the ad-hoc `baseUrl` override into flavors so QA can't accidentally point a release at staging:

```kotlin
flavorDimensions += "env"
productFlavors {
    create("dev")     { dimension = "env"; applicationIdSuffix = ".dev";     buildConfigField("String","BASE_URL","\"https://staging.api.railcast.in/\"") }
    create("prod")    { dimension = "env";                                   buildConfigField("String","BASE_URL","\"https://api.railcast.in/\"") }
}
```

- Keep the gitignored `keystore.properties` upload-key config (already wired). Add the **debug `networkSecurityConfig` override** only to `dev`.
- `applicationIdSuffix=".dev"` lets dev + prod coexist on one device for testers.

## B4. R8 / ProGuard keep rules — **the launch-blocker to fix first**

`proguard-rules.pro` is empty. With `isMinifyEnabled=true`, a **release** build will strip/rename classes that Retrofit, kotlinx-serialization, Room, and Firebase reach by reflection → runtime crashes that **never appear in the debug builds CI runs today**. Concrete rules:

```proguard
# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <fields>; }
-keep,includedescriptorclasses class app.railcast.**$$serializer { *; }
-keepclassmembers class app.railcast.** { *** Companion; }

# --- Retrofit / OkHttp ---
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**, okio.**, javax.annotation.**

# --- Room (KSP-generated impls are kept; DB class needs it) ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Firebase Messaging ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Models parsed by reflection/serialization ---
-keep class app.railcast.core.model.** { *; }

# --- Keep source file + line numbers for readable Crashlytics stacks ---
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
```

**And prove it:** CI must build **`bundleProdRelease`** (or `assembleProdRelease`) and run a smoke check, because R8 problems only exist in the release build (§B7). This is the #1 reason "it worked in debug" ships a crash.

## B5. Baseline profiles & startup (NFR-1)

The build-plan deferred this for "no CI emulator." **Gradle Managed Devices (GMD)** solves it — CI spins an ephemeral **ATD (Automotive/Aosp Test Device)** emulator, no physical device:

```kotlin
testOptions {
    managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api34") { device = "Pixel 6"; apiLevel = 34; systemImageSource = "aosp-atd" }
    }
}
```

- A `:baseline-profile` module (macrobenchmark) generates `baseline-prof.txt` for the **launch → Home → Journey** path; ship it via the `androidx.baselineprofile` plugin so R8 + ART pre-compile the hot path → measurable cold-start win on entry-level ARM.
- The same GMD runs the macrobenchmark that **asserts cold-start ≤ 2.5 s** and fails CI on regression — turning NFR-1 from a hope into a gate.
- Also enable **Startup**: `androidx.startup` for the few initializers, and keep `AppContainer` lazy (already is).

## B6. Versioning strategy

- **`versionCode`** must be monotonic and unique per uploaded AAB. Derive it in CI, don't hand-bump: `versionCode = (git commit count) * 100 + flavorOffset`, or a CI run number. Never reuse — Play rejects duplicates.
- **`versionName`** semantic (`0.1.0` → `1.0.0` at production). Track it in the version catalog or a single `version.properties` so tag ↔ build ↔ Play release are traceable.

## B7. CI/CD — the pipeline (extends `ci-android.yml`, adds `release-android.yml`)

**PR/push (`ci-android.yml`, extend):**
```
lint  →  spotlessCheck  →  detekt  →  testDebugUnitTest  →  verifyRoborazziDebug
      →  assembleProdRelease (with a CI-only debug-signing fallback so PRs need no secrets)
      →  R8 mapping produced  →  AAB/APK size budget on the RELEASE artifact (not debug)
      →  (nightly) GMD macrobenchmark: assert cold-start ≤ 2.5s
```
Key change: **build the release/minified artifact on every PR** (signed with a throwaway debug key if the upload secret is absent) so R8 breakage is caught at PR time, and move the size budget onto the *release* AAB.

**Tag `v*` (`release-android.yml`, new):**
```
checkout → decode upload keystore from base64 secret → bundleProdRelease (real signing)
        → upload mapping.txt to Crashlytics/Play → Gradle Play Publisher (triplet-gradle) OR fastlane supply
        → publish to the INTERNAL track (draft) → notify
```
- Upload key lives as a **base64 GitHub Actions secret** (`ANDROID_KEYSTORE_B64`, `ANDROID_KEY_ALIAS`, `…_PASSWORD`); decode to a temp file at build time; never commit the keystore.
- **Gradle Play Publisher** (`com.github.triplet.play`) or **fastlane `supply`** handles the upload + listing/graphics sync from the repo, so store metadata is version-controlled (§C5).
- Promotion internal → closed → open → production is a Play Console action (or `play` task with `--track`), gated by the launch checklist (Part D).

## B8. Crash/ANR & observability

- **Firebase Crashlytics** (pairs with the FCM you're already adding) or the **Play Console Android vitals SDK** — ship from day one so closed-testing produces real crash-free-session data (NFR: ≥ 99.5%). Upload the R8 `mapping.txt` in the release job (§B7) for deobfuscated stacks.
- **ANR watch:** keep the main thread free — all I/O on injected dispatchers, no synchronous Room/Room-migration on the main thread, `StrictMode` in debug to catch disk/network-on-main early.
- **Privacy-clean by construction:** Crashlytics custom keys must **never** carry a raw PNR (mask at the source; the existing log-redaction discipline extends here). This is a Data-safety commitment (§C6).

---

# Part C — Play Store readiness (every aspect)

Ordered roughly in the sequence you'll actually do them. ⛔ = launch-blocking.

## C1. Developer account & identity ⛔
- **Google Play Developer account** ($25 one-time). Choose **Organization** (needs a **D-U-N-S number**) vs **Individual** — Organization is worth it for a product with a brand and future team.
- **Identity + contact verification** (2023+ requirement): legal name, address, phone, email — verified before you can publish. Start this early; verification has lead time.
- **New personal accounts** created after Nov 2023 must run **Closed testing with ≥ 20 testers for ≥ 14 continuous days** before production access unlocks. Organization accounts are exempt but should still test. Plan the 14 days into the timeline (Part D).

## C2. App signing ⛔
- Enroll in **Play App Signing** (Google holds the app signing key; you hold an **upload key**). This is default and recommended — it enables key rotation and smaller optimized delivery.
- Generate the **upload keystore** once; back it up in a password manager + offline; put it in CI as a base64 secret (§B7). Losing the *upload* key is recoverable via Play support; losing it without Play App Signing would be fatal — another reason to enroll.
- `applicationId = app.railcast` is **permanent** once published — it's correct, leave it.

## C3. Target-API & platform treadmill ⛔ (several 2025–26 requirements)
- **Target API level:** Play requires new apps/updates to target within one year of the latest major release. `targetSdk=35` (Android 15) is acceptable **now**; budget to move to **36 (Android 16)** on the annual cadence. Add a calendar reminder + Renovate bump.
- **16 KB memory page support** (required for new apps on Play, Android 15+): native libs must be 16 KB-aligned. Railcast's only native code comes via Firebase/Room-bundled SQLite; **AGP 8.5.1+ aligns automatically** (you're on 8.11 — good), but **verify** the final AAB with the alignment check and the `check-16kb` tooling before submission.
- **Edge-to-edge** is enforced when targeting 35 — the app already calls `enableEdgeToEdge()` and pads with `WindowInsets` (`ui-architecture.md` §2). Verify no content sits under the bars on a gesture-nav device.
- **Predictive back:** add `android:enableOnBackInvokedCallback="true"` to `<application>` and adopt the predictive-back APIs in the sheet/overlay navigation for the modern back animation.
- **Splash screen:** migrate `Theme.Railcast` to a Material3/AppCompat parent and adopt `androidx.core:core-splashscreen` for a branded, instant cold-start splash (the current plain `Theme.Material.Light.NoActionBar` gives an ugly flash and no splash). Ranking: Impact M / Effort L / not strictly blocking but visibly cheap.
- **Themed app icon:** add a `monochrome` layer to the adaptive icon for Android 13+ theming.

## C4. Manifest & permission declarations ⛔
- **`USE_FULL_SCREEN_INTENT`** — since Android 14, only default calling/alarm apps are auto-granted it; others must request via `NotificationManager` and Play requires a **declaration in Console** justifying it. The arrival alarm (FR-7.3) is a legitimate alarm use — declare it as such; also add an in-app path to system settings if the user has revoked it.
- **Location:** `ACCESS_COARSE_LOCATION` only, requested **in context** on the "Trains near me" tap (already the design, FR-5.2). Complete Play's **Location permissions declaration** and **prominent-disclosure** requirement; because it's coarse + in-context + never stored server-side, this is the light path — **do not** add `ACCESS_FINE_LOCATION` or background location (they'd trigger the heavy review).
- **Notifications:** `POST_NOTIFICATIONS` runtime prompt (13+) requested at first save/alert, not on launch.
- **No SMS / storage / contacts / phone.** The SMS-139 bridge (FR-9.3) must stay **compose-only via an `ACTION_SENDTO` intent** — never request `SEND_SMS`/`READ_SMS`, which trigger the restricted-permission declaration + review. Keep the manifest as clean as it is today.
- **Foreground service:** none needed — background is push-only (NFR-3). If a future live-activity uses a FGS, it must declare a `foregroundServiceType` and justify it; avoid for launch.
- **Package visibility `<queries>`** for the speech recognizer is present and correct.

## C5. Store listing & assets ⛔
Version-control these under `fastlane/metadata/android/` (or the Play Publisher layout) so they ship from the repo, localized **EN + HI**:

| Asset | Spec | Railcast note |
|---|---|---|
| App name | ≤ 30 chars | "Railcast: Live Train Status" (must not imply official IR affiliation — §C12) |
| Short description | ≤ 80 chars | "Live train status & PNR. No ads, ever." |
| Full description | ≤ 4000 chars | Lead with the answer-first value; include the **"not affiliated with Indian Railways"** line (FR-11.1) |
| App icon | 512×512 32-bit PNG | Export from the adaptive icon |
| Feature graphic | 1024×500 | The **board hero** is the visual hook — use it |
| Phone screenshots | 2–8, ≥ 1080 px | Board hero, Journey surface (map + spine), coach guide, PNR pass, station board in Sunlight — straight from the redesign |
| Tablet screenshots | optional | Skip for launch |
| Promo video | optional YouTube | Skip for launch |
| Category | Maps & Navigation *or* Travel & Local | Pick Maps & Navigation (matches intent) |
| Contact + website + email | required | Grievance channel (DPDP) goes here too (§C7) |

## C6. Data safety form ⛔ (maps 1:1 to `dpdp-checklist.md`)
Fill it to *exactly* match code behavior (mismatches are a policy violation and a trust breach). Proposed answers:

| Question | Answer | Basis |
|---|---|---|
| Does the app collect/share user data? | **Yes, minimal** | Saved journeys/prefs on-device; PNR/watches server-side for the feature only |
| Data types collected | **Location (coarse, not stored)**; **App activity** (saved trains, anonymized metrics); **PNR** (as "personal identifiers" tied to a journey) | Manifest + `dpdp-checklist.md` |
| Collected or shared? | **Collected, not shared** with third parties (FCM is a processor, not "sharing" of PNR) | No ad SDKs, no data brokers |
| Encrypted in transit? | **Yes** (TLS everywhere, NFR-4) | Network security config |
| Data deletion mechanism? | **Yes** — uninstall clears on-device; PNR/watches **auto-purge post-journey** server-side; provide a deletion contact | `WatchRepo.purgeExpired()`, §C7 |
| Advertising ID? | **No** | No ads, ever (§7 product law) |
| Data used for tracking? | **No** | Anonymized numeric-only analytics, opt-out honored (FR-11.3) |
| Financial info? | **No** | PNR is a booking reference, **not** payment data — classify as identifier, not financial |

## C7. Privacy policy, account & data deletion ⛔
- **Public privacy policy URL** — already hosted at `GET /privacy` on the BFF; link it in the Console **and** in-app Settings (the `dpdp-checklist.md` 🔶 item — wire the Settings link at launch).
- **Account deletion:** Play requires an in-app + web deletion path **for apps with accounts**. Railcast has **no accounts / no login** (FR-10.5) → declare "app does not support account creation," and document that all personal data is either on-device (uninstall = erased) or server-side self-purging, with a **grievance/deletion email** published in the policy + listing (DPDP requirement, `dpdp-checklist.md` ⬜).
- **PNR masking** end-to-end (UI/logs/analytics/Crashlytics) is the load-bearing privacy invariant — reaffirm it as a Data-safety commitment.

## C8. Content rating, target audience, ads, app access
- **IARC content rating questionnaire** → expected **Everyone / PEGI 3** (no violence/gambling/user-gen content). ⛔ required before production.
- **Target audience & content:** not directed at children; select adult age bands → avoids Families policy overhead.
- **Ads declaration:** **contains no ads** — declare "No." (And it's product law, §7.)
- **App access:** all core features work with **no login** → tell reviewers "no credentials required; every feature is reachable without an account." This *shortens* review (no test-account dance).
- **Government/impersonation:** ensure name, icon, and copy never imply an official Indian Railways / IRCTC app (§C12).

## C9. App Links / deep links (for FR-8 shared journeys)
- Host **`https://railcast.app/.well-known/assetlinks.json`** with the app's signing-cert SHA-256 (the **Play App Signing** cert, obtainable from Console) to get **verified Android App Links** (open in-app without the chooser).
- Add `<intent-filter android:autoVerify="true">` for `railcast.app/t/*` on the Journey route (A5). This is the growth loop's last mile — links a relative opens land directly in a live journey view.

## C10. Play Integrity (anti-abuse, optional-but-recommended)
- The anonymous device-token mint (`/auth/device`, no login) is abusable at scale (festival surge, NFR-5). **Play Integrity API** lets the BFF verify a request comes from a genuine Play install before minting/serving expensive live keys — protecting cost (WS-E) without adding a login.
- Trade-off: Integrity ties usage to Play + GMS; keep a graceful degrade for non-GMS installs rather than hard-blocking (calm, no dark pattern). Recommend behind a killswitch flag (A10).

## C11. Pre-launch report & testing tracks ⛔
- **Pre-launch report:** Play auto-runs the AAB on real Firebase devices — fix its crash, accessibility, security, and performance findings before promoting. Free, high-signal.
- **Track ladder** (backlog M6.2–6.4): **Internal** (team) → **Closed** (≥ 20 testers / ≥ 14 days incl. **55+ and low-end testers** — this *is* the §2 usability metric, measured for real) → **Open** (launch geo; watch crash-free + chart-push-latency dashboards) → **Production staged 5 → 20 → 50 → 100%**, halting on any NFR breach.

## C12. Non-affiliation & policy compliance
- The **"not affiliated with Indian Railways"** disclaimer (FR-11.1) must appear in the listing description *and* in-app (About/Settings) — Play's impersonation policy is strict about apps that look official.
- **No dark patterns / no misleading claims** — the honesty register ("estimated," "usually," freshness stamps) is not just UX, it's Play-policy insurance against "misleading" data claims.

---

# Part D — Execution roadmap & the master launch checklist

## D1. Sequenced work packages (each shippable, each testable)

| # | Package | Contents | Blocking? | Effort |
|---|---|---|---|---|
| **1** | **Release-build integrity** | ProGuard keep rules (§B4); CI builds signed-ish `bundleProdRelease` on PRs; move size budget to release AAB | ⛔ | M |
| **2** | **Crash + FCM live** | Crashlytics + `google-services.json`; mapping upload; PNR-safe custom keys | ⛔ | M |
| **3** | **Platform polish** | SplashScreen API + Material3 theme; themed monochrome icon; predictive-back; verify 16 KB + edge-to-edge | 🔶 | M |
| **4** | **Perf gate** | GMD in CI; baseline profile; macrobenchmark asserts cold-start ≤ 2.5 s (NFR-1) | 🔶 | M |
| **5** | **Quality gates** | Spotless + detekt + compose-lints; Roborazzi screenshot suite (Light/Dark/Sunlight × states) | 🔶 | M |
| **6** | **Release pipeline** | `release-android.yml`: upload-key secret, `bundleProdRelease`, Gradle Play Publisher → internal track; version-controlled listing metadata | ⛔ | M |
| **7** | **Store presence** | Icon 512²/feature graphic/screenshots from the redesign; EN+HI listing; category; disclaimer | ⛔ | M |
| **8** | **Console compliance** | Data-safety form; content rating; target audience; ads=No; `USE_FULL_SCREEN_INTENT` + location declarations; privacy-policy + grievance links | ⛔ | M |
| **9** | **Growth/anti-abuse** | assetlinks + App Links for `/t/<token>`; Play Integrity behind a flag; killswitch `/config` | 🔶 | M |
| **10** | **Test train** | Internal → Closed (20/14d, incl. 55+/low-end) → Open → staged prod; fix pre-launch report | ⛔ | H (calendar) |

**Do in order 1 → 2 → 6 first** (a signed, non-crashing AAB that CI can publish is the spine); 3–5 and 7–9 parallelize; 10 is the timeline's long pole (the 14-day closed-testing window).

## D2. The master launch checklist

**Build & signing**
- [ ] ProGuard keep rules added; **release** build runs without reflection crashes (smoke-tested)
- [ ] Play App Signing enrolled; upload key backed up + in CI as base64 secret
- [ ] CI builds `bundleProdRelease` on PRs; size budget on the **release** AAB (< 25 MB installed, NFR-1)
- [ ] `versionCode` CI-derived & monotonic; `versionName` = `1.0.0` for production

**Platform**
- [ ] `targetSdk` compliant (35 now; plan 36); 16 KB alignment verified on the AAB
- [ ] SplashScreen API + Material3 theme; themed monochrome icon; predictive-back enabled; edge-to-edge verified

**Quality**
- [ ] Spotless/detekt/compose-lints green; lint baseline committed
- [ ] Roborazzi suite green (all themes × loading/cached/offline/cancelled)
- [ ] Baseline profile shipped; macrobenchmark cold-start ≤ 2.5 s
- [ ] Crashlytics live; crash-free ≥ 99.5% in closed testing; `mapping.txt` uploaded

**Permissions & privacy**
- [ ] `USE_FULL_SCREEN_INTENT` declared/justified; location declaration + prominent disclosure filed
- [ ] No SMS/storage/contacts perms; SMS-139 stays `ACTION_SENDTO`
- [ ] Data-safety form matches `dpdp-checklist.md` exactly; PNR classified as identifier (not financial)
- [ ] Privacy policy + grievance/deletion contact linked in Console **and** in-app
- [ ] PNR masked across UI/logs/analytics/Crashlytics (invariant re-verified)

**Listing & compliance**
- [ ] Icon 512² / feature graphic / ≥ 2 screenshots (EN+HI) uploaded
- [ ] Short + full description (EN+HI) with "No ads, ever." + non-affiliation disclaimer
- [ ] Content rating (IARC) completed; target audience = adults; ads = No; app-access note "no login required"
- [ ] assetlinks.json hosted; App Links verify for `railcast.app/t/*`

**Release train**
- [ ] Internal testing passed; pre-launch report findings fixed
- [ ] Closed testing ≥ 20 testers / ≥ 14 days (incl. 55+ & low-end); §2 usability metric ≥ 85%
- [ ] Open testing dashboards green (crash-free, chart-push latency < 5 min, FR-4.2)
- [ ] Staged production 5 → 20 → 50 → 100% with halt-on-NFR-breach

## D3. Launch gates (must be green — from build-plan §7, kept)
1. **WS-D:** RailKit Advance terms permit watcher polling volume; pricing modeled (WS-E) vs expected DAU.
2. **PRD §13 acceptance snapshot** passes end-to-end on a real ₹8,000 phone, from the Play build, on a real train.
3. **Chart-push latency** instrumented and meeting < 5 min in open testing (FR-4.2, §2 metric).
4. **Rollback ready:** server independently deployable; app killswitch flags (A10) cover every watcher-dependent feature.

---

*Bottom line: the design is already good; this plan makes it **shippable and survivable**. Fix the five things that bite first (ProGuard rules, a signed AAB in CI, theme/splash, crash reporting, the Console declarations), stand up the release pipeline, then run the test train with real elders on cheap phones — because the PRD's §13 sentence, executed from a Play install, is the only definition of "done" that matters.*

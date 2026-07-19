# Railcast — Android UI/UX Architecture

How the app's UI is actually built: the Compose structure, the design system, state management, and the conventions every screen follows. Companion to [`docs/app-guide.md`](./app-guide.md) (what each screen does) and [`android/CLAUDE.md`](../android/CLAUDE.md) (the terse working rules).

---

## 1. Stack

- **100% Jetpack Compose** — no XML layouts, no Views.
- **Single-activity** (`MainActivity`) — one Compose host for the whole app; Navigation Compose handles in-app routing, not separate Activities.
- **MVVM**: `feature/<x>/XViewModel` (state + logic, Android-light) → `feature/<x>/XScreen.kt` (pure composables, no logic).
- **Coroutines + `StateFlow`** for state; **`Flow`** for the network/cache pipeline.
- **Room** for local persistence (screen cache); **DataStore** for small key-value prefs (saved trains, language, alert prefs).
- **Retrofit + OkHttp** for networking, generated against `docs/api-contracts.md`.
- minSdk 24, Kotlin, no XML resources except `strings.xml` (two locales) and vector drawables for the launcher icon.

---

## 2. App shell

```
MainActivity (ComponentActivity)
 └─ enableEdgeToEdge()
 └─ LocalizedContent(language) { … }      // wraps everything below
      └─ RailcastTheme { … }              // palette + type + font-scale cap
           └─ Onboarding (first run) OR RailcastApp (returning)
                └─ Scaffold(bottomBar = RailcastBottomBar)
                     └─ NavHost(startDestination = …)
                          Home / Track / Station / Plan / Alerts / pnr (nested route)
```

- **`enableEdgeToEdge()`** — the app draws behind the system bars; every screen that needs it (bottom bar, onboarding) pads itself with `WindowInsets` explicitly, rather than relying on the system to reserve space.
- **`LocalizedContent`** — applies the chosen language by wrapping (not replacing) the Android `Context`. Early versions replaced the context outright, which silently broke `rememberLauncherForActivityResult` (voice search, location/notification permissions) because that lookup walks the `ContextWrapper` chain to find the hosting Activity — a real bug caught and fixed this session. The lesson embedded in the code: **never construct a bare `createConfigurationContext()`** for use as `LocalContext` — always wrap the original context and override only `getResources()`.
- **`RailcastTheme`** — resolves light/dark (`isSystemInDarkTheme()` by default), exposes the palette via a `CompositionLocal`, and — critically — **clamps OS font scaling to 1.3×** at the root via a patched `LocalDensity`, so large system text enlarges without breaking any layout.
- **Navigation**: 5 top-level routes + one nested route (`pnr`, reached only from Home, not a bottom tab). `popUpTo(graph.findStartDestination())` + `launchSingleTop` + `restoreState` on every tab switch — standard "don't stack duplicate destinations, keep each tab's scroll position" pattern.
- **`PNR` deliberately isn't a `Destination` enum entry** — it's a plain string route — because the bottom bar only ever renders the 5 real tabs; the enum and the bar are the same source of truth for tab identity.

---

## 3. Design system (`core/design/`)

One file per concern, no scattered magic numbers:

| File | Owns |
|---|---|
| `Colors.kt` | `RailcastColors` — light + dark palettes, identical keys (base surfaces, ink/text tones, brand accent, green/amber/red *signal* colors + their soft tints, and a separate "board" sub-palette for the dark departure-board surface) |
| `Type.kt` | `RailcastTypography` — a Material3 `Typography` with named roles (`displaySmall` = the board's big answer, `headlineLarge` = screen titles, …); `RailcastMono` = the tabular-numeral face |
| `Dimens.kt` | `Spacing` (4/8/12/16/24/32dp) and `Radius` (12/16/20/999dp) — the two token scales screens are expected to use instead of inline `.dp` literals |
| `Theme.kt` | `RailcastTheme{}` composable — resolves the above into a `CompositionLocal` + wires a Material3 `ColorScheme` underneath so stock Material components (buttons, switches) inherit the brand/signal colors automatically |
| `RailcastIcons.kt` | Every icon as an inlined `ImageVector` (Material glyph path data, hand-written) — **no icon font, no vector-asset files**; keeps the APK light and every icon themeable (tinted, not colored bitmaps) |
| `BoardHero.kt` | The one signature component — the dark departure-board card used on both Track and Home |
| `StatusChip.kt` / `TrainStatusUi.kt` | The icon+word+color status pill, and the state→visual mapping (running/delayed/cancelled/… → level + glyph) |
| `MonoNumerals.kt` | `monoNumerals(text)` — styles only the digit runs of a mixed string in the mono face, leaving words in the UI sans |

**Rule the whole design system exists to enforce:** *brand accent is for what you can tap; signal colors (green/amber/red) are for what the train is doing.* They never cross, and color never appears alone — every `StatusChip`/`BoardHero` pairs it with an icon and a word.

### `BoardHero` — the signature component

```kotlin
BoardHero(title, answer, answerIcon, level, freshness, stale)
```
- Renders on a dedicated dark "board" surface (`colors.board`), regardless of the app's light/dark theme — it's meant to look like a real station display in both.
- The **answer** (e.g. "▶ 12 min late") is wrapped in `AnimatedContent` with a vertical slide+fade — a departure-board "flap roll" — but **only animates when the text actually changes**, keyed on the string itself, so a poll tick that returns identical data never triggers motion. That distinction (state-driven vs timer-driven animation) is a hard rule across the whole app: `PollController` is the only thing allowed to run a timer; every visual transition is a reaction to a state change, never a `LaunchedEffect` loop.
- The signal **color eases** (`animateColorAsState`) rather than snapping, when severity changes (e.g. green → amber).
- **Freshness** is a small colored dot + label (green dot = live, muted dot = stale/offline) — never text alone.

### `monoNumerals()` — why it's a function, not a font swap

Numbers live inside mixed strings ("16:25 → 16:37   Platform 4"). Naively setting the whole `Text` to the mono font would make the *words* monospace too. `monoNumerals()` returns an `AnnotatedString` with `SpanStyle(fontFamily = mono)` applied only over regex-matched digit runs (`\d[\d:.,/]*`), so `Text(monoNumerals("12 min late"))` renders "12" in mono and "min late" in the UI sans, in one `Text` call. It's a pure function with its own unit test (`MonoNumeralsTest`) pinning the span math, which is what let it be adopted screen-by-screen without visually guessing at each site.

---

## 4. Time and freshness (`core/format/IsoTime.kt`, `ui/Freshness.kt`)

The backend sends ISO-8601 timestamps (`2026-07-18T02:50:00+05:30`). Rather than pull in `java.time` (which needs core-library desugaring on minSdk 24 — extra APK weight for two helpers), `IsoTime` is a **hand-rolled, dependency-free, unit-tested** parser:

- `clock(iso)` → wall-clock `"HH:mm"` — Indian train times arrive with the `+05:30` offset already baked in, so the time component *is* the IST clock; no timezone conversion needed.
- `age(iso, now)` → a sealed `Age` (`JustNow` / `Minutes(n)` / `Hours(n)` / `Days(n)` / `Unknown`) — the *maths* only; wording is supplied by the caller via string resources so it stays localized.
- `friendlyDate(dateIso)` → `"Sat, 18 Jul"`, via `SimpleDateFormat` with the app's locale (weekday/month names translate for free).
- `epochMillis(iso)` uses the Howard Hinnant civil-calendar algorithm to convert to epoch millis by hand (no `Calendar`/`java.time`), so `age()` is correct regardless of the device's timezone.

`ui/Freshness.kt` is the single composable (`freshnessLabel(freshness, stale)`) every screen calls to turn that into "just now" / "6 min ago" / "3 d ago · offline" — replacing what used to be two duplicated copies in Track and Home.

This layer exists because of a real on-device bug: screens originally rendered the raw ISO string directly, which also visually broke a station-board row (a long ISO string starved a "Platform 5" label of width until it wrapped one character per line). Every screen now routes through `IsoTime`/`Freshness`, verified by unit tests, not by eye.

---

## 5. State management pattern (every feature module)

```kotlin
data class XUiState(val query: String = "", val resource: Resource<T>? = null, …)

class XViewModel(/* interfaces only, no Android types */) {
    private val _state = MutableStateFlow(XUiState())
    val state: StateFlow<XUiState> = _state.asStateFlow()
    fun onQueryChange(q: String) { _state.update { it.copy(query = q) } }
}

@Composable
fun XScreen(vm: XViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    // pure rendering, no business logic
}
```

- **ViewModels take interfaces/lambdas, not Android types** (`TrainSearch`, `Flow<Resource<T>>` factories, `SavedTrains`) — every one is unit-testable on the plain JVM with a fake, no Robolectric/instrumentation needed. `TrackViewModelTest`, `HomeViewModelTest`, `PlanViewModelTest`, etc. all run as plain JUnit.
- **`Resource<T>`** (`core/data/Resource.kt`) is the one shape every live value flows through: `value` (last-known data, cached-then-fresh), `freshness`, `stale`, `loading`, `error`. Screens degrade uniformly: cached data renders immediately, an in-flight refresh doesn't blank it, and a failure with a live cached value never blocks the screen — only "no cache + error" shows the error card.
- **`ApiResult<T>`** (`core/net/Envelope.kt`) is the parsed shape of one network call: `Ok(data, meta)` or `Err(error, httpStatus)`. Any network failure normalizes to a synthetic retryable `UPSTREAM_DOWN` rather than throwing, so the repository layer above it always has something to fall back to.

---

## 6. The one refresh loop owner: `PollController`

No screen runs its own `LaunchedEffect(Unit) { while(true) { delay(...) } }` timer — that pattern is explicitly forbidden. Instead every screen **registers** with the single app-wide `PollController` (plain Kotlin, no Android deps, driven by a virtual clock in tests):

```kotlin
poller.register(key = "track:$trainNo", cadenceMs = 45_000) { fetchAndReturnASignature() }
```

- Refreshes immediately on register/resume.
- **Backs off exponentially (capped at 8×)** while the returned signature is unchanged — an idle screen polls less over time, saving battery/data — and **resets instantly** the moment the signature changes.
- **Stops every loop when the app backgrounds**, restarts all of them (with an immediate refresh) on foreground, via `PollLifecycleBridge` observing `ON_START`/`ON_STOP`. Background delivery is push-only (FCM), by design (NFR-3).

This is why the "board roll" animation rule above matters: with backoff active, most poll ticks return an *identical* signature, and the UI must visibly do nothing on those ticks — only a genuine change should ever animate.

---

## 7. Networking & caching (SWR)

```
Screen ⇄ ViewModel ⇄ Flow<Resource<T>> factory ⇄ ScreenCache (Room) ⇄ RailcastApi (Retrofit)
```

- **Cached-then-fresh ("SWR")**: every screen's data flow emits the Room-cached value first (instant paint, works offline), then the network result once it lands — never a blank loading screen if a cache entry exists.
- **`DeviceSession`/`AuthInterceptor`/`TokenAuthenticator`**: anonymous device-token auth (no login), single-flighted minting (a cold-start burst of screens triggers exactly one `/auth/device` call), and automatic re-mint-and-replay on a mid-session 401.
- **`Connectivity.kt`** exposes a simple online/offline `Flow` used for the global offline banner.

---

## 8. Directory search (`directory/`)

- **`DirectorySearch.search(index, query, limit)`** — pure, unit-tested, offline fuzzy ranking over a bundled train+station dataset. Weighted scoring (code match > name match > city match), a pure-digit query skips station matches entirely (it's a train number), results sorted by score then shortest-label-wins as a tiebreak.
- **`VoiceSearchContract`** — an `ActivityResultContract` wrapping Android's built-in `ACTION_RECOGNIZE_SPEECH` UI. No `RECORD_AUDIO` permission needed (the system handles the mic); recognized text feeds straight into the same search field. Prompt locale follows the app's chosen language.
- **`NearestStations` / `LocationResolver`** — coarse-location "trains near me," permission requested only at the point of tap (in-context), never pre-emptively.
- **`FormatValidation`** — pure validators (train number length/digits, PNR length/digits) shared by Home's inline hint and PNR's input field, so the rule can't drift between the two call sites.

---

## 9. Accessibility & i18n conventions (enforced, not aspirational)

- **No hardcoded user-facing strings** — everything is a string resource; a `StringsParityTest` (JVM, runs in `testDebugUnitTest`) fails CI if `values/strings.xml` and `values-hi/strings.xml` ever have different key sets, so English and Hindi can never silently drift apart.
- **Every interactive control ≥48dp**, decorative icons pass `contentDescription = null` and rely on an adjacent label for the accessible name; where icon+label are one tap target, `Modifier.clickable(role = Role.Button)` merges them into a single TalkBack node ("Save, button") instead of announcing the icon separately.
- **Status is never color-only** — `StatusChip` and `BoardHero` both hard-require an icon and a word alongside the color.
- **PNR is masked at every layer** it touches the UI or logs — never rendered in full client-side.

---

## 10. Testing shape

- **ViewModel/logic tests** (JVM, `app/src/test/`) — the bulk of coverage: every feature ViewModel, `PollController` backoff/lifecycle, `DirectorySearch` ranking, `IsoTime`/`monoNumerals` span-and-date math, the run-date probe logic. These are what actually gate every PR in CI (`ci-android.yml`: lint → `testDebugUnitTest` → `assembleDebug` → an APK size budget check).
- **No Robolectric/instrumented UI tests currently** — screen composables are kept thin/dumb by convention specifically so the logic they render is fully covered by the JVM tests above; visual correctness is verified on-device rather than by golden-image tests.

---

## 11. Conventions to keep following (for anyone touching this code next)

1. New geometry → reach for `Spacing`/`Radius` tokens, not a raw `.dp` literal.
2. New icon → add it to `RailcastIcons.kt` as an inlined `ImageVector`; never add an icon font or drawable-per-icon.
3. New live/time value → route it through `IsoTime` + `freshnessLabel()`; never render a raw ISO string.
4. New number inside a sentence → wrap it in `monoNumerals()`.
5. New refresh need → `poller.register(...)`; never a screen-local timer.
6. New user-facing string → add to both `values/strings.xml` and `values-hi/strings.xml` in the same commit (CI enforces parity).
7. New status → must carry an icon + word, `StatusLevel`-derived color only as reinforcement.
8. Prefer editing an existing screen's composables over introducing a new component; when a pattern repeats twice, extract it into `ui/` (e.g. `Skeleton`, `SegmentedControl`, `ErrorState`) rather than copy-pasting.

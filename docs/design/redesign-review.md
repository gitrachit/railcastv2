# Review — the "Semaphore" redesign that landed on `main`

**What this is:** an audit of everything merged into `main` since the pre-redesign baseline (`dca887c`), covering both the design-only work I contributed (`docs/ux-redesign.md`, the HTML prototype, `docs/android-release-engineering.md`) and a second, independent, much larger effort that landed via PRs #54 and #55 on branch `design/railcast-direction-study`: a full design-process doc set under `docs/design/` **plus real, tested, shipped Kotlin implementing most of it**. This document is about the second effort — it is substantial enough to warrant its own review, separate from the proposal I wrote.

**Scope read:** 31 commits, 77 files, +10,419/−125 lines, across `docs/design/*` (4 docs + 5 HTML prototypes), `android/app/src/main/**` (30 new/changed source files), and `android/app/src/test|androidTest/**` (19 new/changed test files, ~1,400 lines). I read the three design docs in full, the majority of the new/changed Kotlin source, the diffs on every touched existing file, the manifest/Gradle/resource changes, and spot-checked the test suite. I could not execute the build or test suite in this sandbox (no network access to Gradle's distribution server) — see [§6](#6-what-i-could-not-verify).

---

## 1. What this effort actually is

This is a **rigorous, independently-run design process** that arrived, by a different route, at a strikingly similar diagnosis to my own `ux-redesign.md` — and then went further: it built the thing.

### 1.1 The process

`docs/design/direction-study.md` runs a structured competition between **four organizing principles** for the whole app, each presented at its strongest and then attacked on its own terms:

| Direction | Mental model | Verdict |
|---|---|---|
| A — "The Board" | The departure board, as software | Loses as architecture (Q4 coach diagram and Q7 planning don't fit); **wins as a display grammar**, absorbed into station-board rows |
| B — "The Companion" | *(This is explicitly my prior redesign, `docs/ux-redesign.md`/`railcast-redesign-2026.md`, entered and judged as a candidate)* | Loses as an organizing principle — a shifting home screen is judged a real liability for elderly/vernacular-first users, and it "optimises app launch" when "the correct move is to make app launch unnecessary." **Confidence System and journey-as-atom survive wholesale.** |
| C — "The Thread" | A WhatsApp-style chronological journey feed | Loses — chronology buries the present, the most time-pressed question. **Wins the journey-detail view outright**, absorbed as the "spine." |
| D — "The Ambient Instrument" | A car dashboard; the answer lives outside the app (widget, lockscreen) | **Wins**, on the argument that it attacks the PRD's own headline metric (time-to-answer) at the root rather than optimizing around it, and that "sessions avoided" is a moat an ad-funded competitor cannot copy |

The reasoning is genuinely good-faith adversarial: each direction gets a real "where it breaks" section, direction B (my proposal, entered as a blind candidate) is credited accurately for what it does well before being rejected as the *organizing* principle, and the final selection is scored against both the seven canonical user questions and the PRD's own §2 success metrics with an explicit weighting rationale.

### 1.2 Where my proposal and this one actually differ

Worth being precise about this, since both exist in the repo now and a reader could reasonably ask "which one is Railcast doing?"

- **Agreement (independently derived, both times):** the Journey is the atom; render the consequence, not the datum; classify search input instead of asking the user to pick a mode; honesty must be a rendered, structural property, not a caption; three tabs, down from five; the alert journal/receipt problem is real and needs solving.
- **The real disagreement:** my proposal keeps a **fixed three-tab home** (Home · Search · Activity) where Home is always a journey *stack*. This effort's winning direction has **no fixed home at all** — the app resolves its entry point from state (0 journeys → invitation, 1 → that journey's full surface directly, 2+ → the stack), and pushes the state-resolution logic **out of the app entirely**, onto a home-screen widget and lockscreen notification, on the argument that a *shifting home screen* is a usability liability but a *widget that reflects state* is normal and expected. My document explicitly considered and reasoned about a state-resolved entry point too (it's discussed under Direction B here because it's substantially what my document does at the Home-tab level), and this study's counter-argument — "cold launch is not the segment to optimize, removing the launch is" — is a genuinely sharper take on the same PRD metric. I think it's the better answer, and it's the one that shipped.

**Practical consequence:** `docs/ux-redesign.md` (mine) is now a **superseded alternative**, not the direction of record. The PRD has been amended to match *this* effort's IA (see §2.2), not mine. I'd recommend either archiving/labelling `ux-redesign.md` as superseded or deleting it, so a future reader doesn't find two contradictory "the redesign" documents with no signal about which one shipped. I have not done this yet — it's your call, and I did not want to delete another effort's — or my own — work without you weighing in.

---

## 2. What actually shipped in code (verified, not just documented)

I did not take `docs/design/design-system.md`'s "Implementation status" table on faith — I traced each claim to the source. Everything below I confirmed by reading the file and, where relevant, its call sites.

### 2.1 The Confidence System — real, and enforced at compile time

`Confidence` (`CERTAIN | ESTIMATED | STALE | UNKNOWN`) is a required, no-default parameter on `ConfidenceValue` (`core/design/ConfidenceValue.kt`). A caller cannot render a live number without stating what it knows about it — the honesty requirement (FR-11.1) is a type signature, not a convention. `UNKNOWN` renders as an em-dash, never `0`, never blank. Confidence rides on **copy** (the `~` prefix), a **dashed edge**, and **semantics** (`"estimated $subject"` reaches TalkBack) — explicitly *not* on opacity, because a first draft that faded estimates to 60% ink measured 2.53:1 contrast, and the code comment says so, self-critically, in place. `JourneyAnswer.kt` and `StopConfidence.kt` apply this correctly in `TrackScreen`: a stop the train has already reached is `CERTAIN`; a future stop is `ESTIMATED`; and — a genuinely careful distinction — a projection made from *cached, stale* data is `STALE` rather than `ESTIMATED`, because presenting it as a live calculation would overclaim.

### 2.2 Three tabs, PRD amended in the same PR

`docs/PRD.md` §7 now reads "Three tabs: Journeys · Find · You" with an inline amendment note explaining the reasoning and pointing at `direction-study.md` — exactly the discipline this repo's own `CLAUDE.md` invariant 7 asks for ("contract change? update the doc in the same PR"), applied here to a UX requirement rather than an API contract. `ui/RailcastApp.kt`'s `Destination` enum has exactly three entries; `NavigationStructureTest` asserts this by count *and* by name (`track` and `alerts` must not reappear as routes), which is a nice piece of architecture-as-test — a future PR that casually adds a fourth tab fails a unit test with a message explaining why that's a decision, not a drive-by. Track and PNR are reached as nested routes from a journey card; Station and Plan live inside `Find` behind the query classifier.

### 2.3 The omnisearch classifier — built, wired, tested

`directory/QueryClassifier.kt` is a small, pure, well-reasoned shape-classifier (5 digits → train, 10 → PNR, "X to Y" → route, else → free text against the offline directory), correctly ordering route-detection before digit-detection so `"12951 to Goa"` isn't misread as a train number. `feature/find/FindViewModel.kt` and `ui/FindScreen.kt` wire it into a real screen with debounced search, inline format hints ("guidance while typing, never an error after submitting"), and routing into the existing `StationScreen`/`PlanScreen` composables. It's registered in `AppContainer` and reachable from the nav graph — this is not a dangling file.

### 2.4 PNR masking as a type, not a discipline

`core/data/Pnr.kt` introduces `RawPnr` (whose `toString()` returns the masked form — an accidental string-template interpolation in a log or exception message now *cannot* leak the full number) and `MaskedPnr`. `PnrViewModel` was migrated to use it; the raw digits reach the network layer only via an explicitly-named `.reveal()` call, which the comment correctly notes is "greppable in review." This is a strictly better enforcement of invariant 2 / FR-4.3 than the prior convention-based masking, and `PnrMaskingTest` covers it.

### 2.5 The ambient layer — widget + lockscreen notification, both real and defensively engineered

`feature/ambient/` is a full vertical slice: a pure content model (`AmbientState.kt`/`Ambient.resolve`), a `SharedPreferences`-backed snapshot store, a `RemoteViews` widget provider, a low-importance/dismissible/silent lockscreen notification, a mute broadcast receiver, per-journey suppression, and a manifest registration. Several details show real platform experience rather than a first pass:

- **Direct-boot safety.** `AmbientRepository`/`AmbientSuppression` explicitly check `UserManager.isUserUnlocked` before touching credential-encrypted `SharedPreferences`, because `JourneyWidgetProvider` is a `BroadcastReceiver` that *can* be invoked before first unlock after a reboot, and an uncaught exception there is a boot-time crash, not a missing widget. This was a **real bug that shipped and was fixed** — commit `08c3ec4` ("widget crashed the app on boot before unlock"), caught via a stray logcat dump during manual testing (see §4.3) — and the fix has a regression test, `AmbientDirectBootTest`.
- **ANR avoidance.** The snapshot store is deliberately `SharedPreferences`, not the app's normal `DataStore` convention, with the comment explaining exactly why: `onUpdate` is a synchronous broadcast callback and `DataStore` is suspend-only, so awaiting it there is how widgets get killed for ANR.
- **The widget never fetches.** It renders a snapshot the foreground app already wrote (`HomeViewModel.publishAmbient()` → `WidgetAmbientSink`), so the ambient surface adds zero new network/battery cost — correctly upholds NFR-3 ("background = push only").
- **`updatePeriodMillis` = 30 minutes**, stated as "the practical floor the framework honours," with a comment that anything tighter must come from a Watcher push, never polling.
- **Mutes are applied before urgency-resolution**, not after, so a muted journey cannot win the "most urgent" sort and reappear — a real bug class in naive implementations of this feature, avoided here.
- **The notification** is `IMPORTANCE_LOW` (never buzzes/peeks/sounds), explicitly non-ongoing (`setOngoing(false)`, "an undismissable notification is a dark pattern, and §7 lists those as product law"), and its `POST_NOTIFICATIONS`-revoked-mid-flight path catches `SecurityException` rather than crashing.

This is the strongest code in the change set. It reads like it was written by someone who has shipped an Android widget before and been burned by the direct-boot and ANR failure modes specifically.

### 2.6 Sunlight mode, contrast correction, and text-scale reflow

Three related, real fixes:

- **Sunlight (FR-5.3)** went from "palette exists, nothing selects it" to user-selectable and persisted (`SunlightStore`, `ThemeSelectionTest`), including on the ambient surfaces (`AmbientPalette` reads the same preference, mirrored into `SharedPreferences` because `RemoteViews` can't await `DataStore` — same pattern as §2.5).
- **A genuine WCAG bug was found and fixed**, and the commit trail is honest about the process: `f2ac264 fix(a11y): status colours failed WCAG on the pair that actually renders`. The root cause is documented precisely in `design-system.md` §1.3 — `StatusChip` composites signal text over a semi-transparent tint, and measuring text-on-opaque-surface instead of text-on-*composited*-tint overstated contrast by roughly a full stop (shipping GOOD at 3.66:1, WARN at 2.94:1/2.50:1 against a 4.5:1 requirement). `SemaphoreContrastTest` (145 lines, 20 assertions) now gates every `StatusLevel` × `{surface, bg}` × three themes on the *correct*, composited pair, with a stated 0.1 margin so a token sitting exactly at threshold doesn't flip under float rounding.
- **The 1.3× font-scale clamp was removed** in favor of `core/design/Reflow.kt` — a small, pure, monotonic line-budget function (`maxLines(base, fontScale)`) that grants extra lines as scale grows instead of capping scale to fit fixed layouts. The doc is unusually candid that the *old* clamp was a real accessibility failure ("WCAG 2.2 1.4.4 requires 200% without loss of content or function... the clamp does not meet it... the users most affected are exactly persona P1"), and equally candid that the *new* mechanism is unverified at 200% on a real device (see §6).

---

## 3. Documentation and process quality

Unusually high. A few things worth naming specifically because they're easy to skip past:

- **Self-correction is visible in the commit trail, not hidden.** `b4a58e6 docs(design): correct the confidence-opacity rule and the 200% text claim` is a doc-only commit whose entire purpose is walking back an earlier overclaim. That's the opposite of the failure mode where a design doc asserts something once and nobody revisits it.
- **A stray 3,171-line logcat dump** (`android/crash.txt`, from unrelated apps, captured during manual device testing) was accidentally committed via a broad `git add -A` in commit `63e7357`, caught, and cleanly reverted in `9a71830` with a commit message that names the mistake, credits what the file was actually useful for (it's what surfaced the direct-boot crash in §2.5), and adds a `.gitignore` rule so it can't recur. This is a minor process wobble — `git add -A` is exactly the pattern this repo's own git-safety conventions warn against — but it was self-caught and transparently fixed, which is the right outcome even if the ideal outcome is not committing it in the first place.
- **`design-system.md`'s "not yet built" list is now stale** (see §4.1 below) — the one real documentation gap I found.

---

## 4. Findings — things I'd flag before treating this as done

Ranked roughly by how much they'd surprise someone relying on the docs as ground truth.

### 4.1 Two "not yet built" items in `design-system.md` are actually built (doc drift)

`design-system.md` §11 lists, under "Not yet built": *(2) Live notification — "the ongoing notification does not exist yet"* and *(3) The omni-input classifier — "is not built."* Both are false as of `main` HEAD. Commits `47432e5` (live notification) and `3851da0` (omni-input) landed in PR #55, after `design-system.md`'s last status update (`9f05fc9`, which predates PR #55 entirely) — the doc simply wasn't revisited after the second PR's work. The same staleness shows up in a code comment: `values-night/design_tokens.xml` still says "Bringing Sunlight to the ambient layer needs the widget to read the preference and swap colours itself, which is tracked with the ambient work" — but `AmbientPalette.kt` already does exactly that (§2.6). Neither is a functional bug — the code is correct and ahead of its own changelog — but if `design-system.md` is going to be the living status document its own convention implies, it needs a pass to catch up to what actually shipped. This is a five-minute fix, not a design problem.

### 4.2 Accessibility instrumentation tests exist but verify nothing yet — and this is self-disclosed

`TextScalingTest.kt` and `ScreenReaderTest.kt` (194 lines together) are real, well-constructed Compose UI tests for exactly the two claims the rest of the system can't verify statically: whether text actually reflows instead of clipping at 200% scale, and whether TalkBack's traversal order and announced strings are correct. The test file's own docstring is unusually blunt about their status: *"NOT YET EXECUTED — this project has no emulator in CI and these were written without a device to hand. Treat a green run as the first real evidence."* `build.gradle.kts` and `libs.versions.toml` wire the `androidTest` source set and dependencies correctly, but there is no CI job that runs them (`ci-android.yml` is unchanged by this branch — confirmed by diff). **This is the same gap I identified independently in `docs/android-release-engineering.md` §B1/§B5** (recommending Gradle Managed Devices to close exactly this "no CI emulator" hole) — two independent efforts landed on the same missing piece of infrastructure. It's worth closing once, not per-feature.

### 4.3 This branch is entirely additive to the app layer; none of the release-engineering gaps moved

I checked directly: `proguard-rules.pro` and `themes.xml` are byte-for-byte unchanged by this branch, and `docs/backlog.md`/`docs/build-plan.md`/`docs/gap-analysis.md` are untouched. That means every launch-blocker I catalogued in `docs/android-release-engineering.md` — empty ProGuard/R8 keep rules, no signed release AAB ever built in CI, the plain `Theme.Material.Light.NoActionBar` with no SplashScreen API, no crash reporting — is **still exactly as open as it was**. This redesign work and that release-readiness work are genuinely orthogonal (one is "what does the app do," the other is "does the app survive being minified and reviewed by Play"), so this isn't a criticism of either effort — just a reminder that landing a large, good UX change doesn't by itself move the needle on ship-readiness, and the two tracks should both keep moving.

### 4.4 One orphaned string resource

`R.string.nav_home` (the old five-tab Home label) has no remaining reference anywhere in `android/app/src/main/java/`. The sibling strings `nav_track`, `nav_station`, `nav_plan`, `nav_alerts` are all still legitimately used — as in-screen titles for `TrackScreen`, `StationScreen`, `PlanScreen`, and `AlertsScreen` respectively, which still exist as nested surfaces even though they're no longer tabs — so this isn't a sign of sloppy cleanup, just one leftover key (and its Hindi counterpart) that `StringsParityTest` won't catch because it checks EN/HI *agree with each other*, not that every key is reachable. Trivial; a one-line deletion in both locales whenever someone's next in that file.

### 4.5 Genuinely correct, not a bug: `exported="false"` on the widget's `BroadcastReceiver`

Flagging this because it looks wrong at a glance and isn't. `JourneyWidgetProvider` declares `android:exported="false"` despite handling `android.appwidget.action.APPWIDGET_UPDATE`. This works because that action is on the Android framework's protected-broadcast list — only the system itself can send it, regardless of the receiver's exported flag — so `exported="false"` is the *more* correct, more restrictive choice here (a third-party app spoofing the broadcast is blocked at the OS level either way, but `exported="false"` also blocks anything else from reaching this receiver by accident). Worth noting explicitly so nobody "fixes" it to `true` believing the widget won't otherwise update.

### 4.6 The two redesign efforts should be reconciled in the docs, deliberately

Restating §1.2 as an action item: `docs/ux-redesign.md`, `docs/prototype/Railcast-redesign.html`, and this effort's `docs/design/*` + prototypes now all live in the same `docs/` tree, describe different (and in the tab-naming/entry-point sense, incompatible) visions, and only one of them is reflected in the PRD and the shipped code. A reader arriving cold has no way to know which is current. I'd suggest either an explicit "superseded by `docs/design/direction-study.md`" note at the top of `ux-redesign.md`, or moving it to an `archive/` location — happy to do either on your instruction, but I didn't want to unilaterally mark or remove someone else's (or my own) merged work without you weighing in first.

---

## 5. Net assessment

This is materially the strongest work in the repository's design/UX history. The direction-study process is honest adversarial reasoning rather than a foregone conclusion dressed up as analysis; the design-system spec ties every rule back to a testable enforcement mechanism instead of leaving it as prose; and the implementation is not a demo — it's defensively engineered against the specific Android failure modes (direct boot, ANR, mid-flight permission revocation, RemoteViews' inability to read Compose state) that separate "runs on my Pixel" from "survives a Xiaomi at 3 a.m." The self-correcting commit trail (the WCAG fix, the opacity-confidence walkback, the crash.txt cleanup) is a good sign about how this branch was actually worked, not just how it reads in hindsight.

The gaps are small and named above: two stale status claims in the docs, an accessibility verification step that's built but not yet run (and not yet CI-gated — same infrastructure hole my own release doc flagged), one dead string, and the standing reminder that this doesn't touch release-readiness at all. None of them are structural. The one decision that isn't mine to make unilaterally is §4.6 — what to do with the now-superseded parallel redesign proposal sitting next to this one.

---

## 6. What I could not verify

I read every file discussed above and traced call sites by hand, but I did not execute anything: this sandbox has no network path to Gradle's distribution server (`./gradlew` fails on the initial wrapper download), so I could not run `testDebugUnitTest`, `lintDebug`, or `assembleDebug` myself, and obviously could not run the `androidTest` suite (which needs a device/emulator regardless). Everything above is a static read of the code and its own tests' *intent* — not a green CI run I watched happen. Given the honest self-labeling already in the test files (§4.2), I'd treat "does this actually build and pass in CI" as the immediate next thing to confirm, not assume from this review.

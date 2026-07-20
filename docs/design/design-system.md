# Railcast Design System — "Semaphore"

**Phase 2 of the redesign.** The production specification for the Ambient-first direction selected in `direction-study.md`.
Version 1.0 · July 2026 · Android / Jetpack Compose · Every contrast figure in this document is machine-verified.

---

## 0 · The four laws

Everything below derives from these. When a decision is ambiguous, resolve it against these in order.

1. **Colour is never load-bearing.** Every status is icon + word + colour. Remove all colour and the app still works. (FR-10.2, invariant 4)
2. **Confidence is a data dimension.** No live value renders without an epistemic state. An estimate must *look* estimated before it is read. (FR-11.1, FR-2.2)
3. **The consequence outranks the datum.** "12 min late" is the train's fact. "You reach Bhopal ~16:49" is the user's answer. The answer gets the larger type.
4. **Degrade, never block.** Every component has a cached, an offline, and an unknown rendering. No component may be blank on no-network. (FR-9.1)

---

## 1 · Colour

### 1.1 Structure

Three themes, one shape. `RailcastColors` is a data class with identical fields across Light, Dark, and Sunlight, selected via CompositionLocal — so a theme switch is a value swap, never a branch in component code.

The palette separates two families that must never be confused:

- **Brand** (`brand`, `brandSoft`) — identity and tap affordance. Never used to signal state.
- **Signal** (`green`, `amber`, `red` + soft tints) — railway semaphore semantics. Never used for decoration.

*Brand ≠ signal* is a hard rule. A blue "Track" button and a green "ON TIME" chip must never trade colours, because the user learns signal meaning within one session and decorative reuse destroys it.

### 1.2 The verified palette

All values below are the **shipped, corrected** tokens. Figures are WCAG 2.2 contrast ratios computed on the pair that actually renders.

| Role | Light | Dark | Sunlight |
|---|---|---|---|
| `bg` | `#E7ECEF` | `#081115` | `#FFFFFF` |
| `surface` | `#FFFFFF` | `#0F2129` | `#FFFFFF` |
| `surface2` | `#F1F4F6` | `#15303A` | `#F2F2F2` |
| `ink` (primary) | `#0F2A33` · 15.01:1 | `#EAF3F4` · 14.67:1 | `#000000` · 21.00:1 |
| `ink2` (secondary) | `#54696F` · 5.79:1 | `#9DB4BB` · 7.62:1 | `#2A2A2A` · 14.35:1 |
| `ink3` (tertiary) | `#5B6E75` · 5.34:1 | `#708A92` · 4.52:1 | `#555555` · 7.46:1 |
| `line` | `#DCE3E7` | `#1E3B45` | `#BBBBBB` |
| `brand` | `#2743C4` · 7.82:1 | `#8098FF` · 6.18:1 | `#1D33A0` · 10.33:1 |
| `green` GOOD | `#126C40` | `#3DBB77` | `#095731` |
| `amber` WARN | `#8B5208` | `#E7A542` | `#6E4100` |
| `red` BAD | `#C03C3C` | `#E36868` | `#852424` |
| `board` | `#0F2A33` | `#06151A` | `#FFFFFF` |
| `boardGreen` | `#3DE08A` · 8.74:1 | `#3DE08A` · 10.82:1 | `#095731` |
| `boardAmber` | `#FFC24D` · 9.34:1 | `#FFC24D` · 11.57:1 | `#6E4100` |
| `boardInk` | `#7FA6AE` · 5.69:1 | `#6E969E` · 5.77:1 | `#333333` · 12.63:1 |

### 1.3 The composite rule *(the trap this system exists to prevent)*

`StatusChip` renders signal text on a **soft tint**, and in Light/Dark that tint is alpha-blended (`0x24` = 14%) over whatever sits beneath it. The pair that reaches the eye is therefore:

```
    text  ⟶  [ tint @14% ]  ⟶  surface OR bg
             └── composite this before measuring ──┘
```

Measuring signal-on-surface instead overstates contrast by roughly **one full stop**. Under the original tokens that produced GOOD at 3.66:1 and WARN at 2.94:1 (2.50:1 over `bg`) — all shipping, all failing, all passing a test that measured the wrong thing.

**Rule:** any token pair is measured *after compositing*, against the **darkest backdrop the component can sit on**. Enforced by `SemaphoreContrastTest`, which iterates every `StatusLevel` × `{surface, bg}` × three themes.

**Rule:** tokens carry ≥0.1 margin above their threshold. A token sitting exactly at 4.50 flips to 4.49 under `Float` math and fails in CI but not in review.

### 1.4 Thresholds

| Surface | Minimum | Rationale |
|---|---|---|
| Fact-bearing text (Light/Dark) | **4.5:1** | WCAG 2.2 AA normal text |
| Fact-bearing text (Sunlight) | **7:1** | AAA — the platform in direct sun, FR-5.3 |
| Brand as tap affordance | **3:1** | WCAG 2.2 non-text contrast (1.4.11) |
| Focus ring vs adjacent | **3:1** | WCAG 2.2 focus appearance (2.4.13) |

### 1.5 Colour-blind safety

Green/amber/red is the worst possible triad for deuteranopia and protanopia — and it is non-negotiable, because it is railway semaphore and the domain owns it. It is made safe by **three independent redundancies**, not by palette choice:

1. **Icon differs by shape** — ✓ circle, ⚠ triangle, ✕ cross. Distinguishable in pure luminance.
2. **The word is always present** — "ON TIME", "LATE 12m", "CANCELLED".
3. **Luminance differs across levels**, so the three are separable in greyscale.

Verification: render any status surface in greyscale. If you can still identify the state, it passes. Colour is the third signal, never the first.

---

## 2 · Typography

### 2.1 Two families, one job each

| Family | Use | Why |
|---|---|---|
| **Humanist system sans** (platform default) | All prose, labels, names, actions | Free Indic-script coverage across all 12 languages (FR-10.1) with zero APK cost, and it inherits the user's font. A bundled Latin-first face would fail Devanagari/Bengali/Tamil rendering — which would break the product's core inclusion promise to save 400KB. |
| **`RailcastMono`** (`FontFamily.Monospace`) | Every number that refreshes: times, delays, platforms, fares, PNR mask | **Tabular numerals.** A proportional `1` is narrower than `8`, so a live-refreshing ETA reflows on every poll. Jitter reads as instability, and instability reads as untrustworthy. |

`TabularNumberStyle` also pins `textDirection = Ltr` so numbers stay LTR inside RTL locales — a `16:49` must never render as `49:16`.

### 2.2 Scale

Mapped onto Material 3 `Typography` so Compose defaults work unmodified.

| Token | Size / Weight | Tracking | Use |
|---|---|---|---|
| `displaySmall` | 30sp · ExtraBold | −0.03em | **The answer.** One per screen, maximum. |
| `headlineLarge` | 28sp · Bold | −0.02em | Screen titles |
| `headlineMedium` | 22sp · Bold | −0.02em | Section heads, board hero secondary |
| `titleLarge` | 18sp · Bold | 0 | Card titles, train names |
| `titleMedium` | 15sp · SemiBold | 0 | Row primary |
| `bodyLarge` | 15sp · Normal | 0 | Prose, explanations |
| `bodyMedium` | 13sp · Normal | 0 | Supporting detail |
| `labelLarge` | 13sp · SemiBold | 0 | Buttons, chips |
| `labelSmall` | 10.5sp · SemiBold | 0 | Freshness stamps, meta |

Negative tracking on large sizes only — it tightens display type optically, and would harm legibility below ~20sp.

### 2.3 The one-answer rule

**At most one `displaySmall` per screen.** If two things are that large, neither is the answer. This is the typographic expression of Law 3, and it is the fastest review check available: open any screen, count the biggest text. If the count isn't exactly one, the hierarchy is wrong.

### 2.4 Scaling

All sizes in `sp`, honouring OS text size (FR-10.3).

**Known deviation — the app currently caps text scaling at 1.3×.** `RailcastTheme` clamps `LocalDensity.fontScale` to `MAX_FONT_SCALE = 1.3f` app-wide, because past that point existing layouts clip. So a user who sets 200% system text receives 130%.

This is a deliberate trade that is nonetheless **an accessibility failure**, and it should not be described as anything else:

- WCAG 2.2 **1.4.4 Resize text** requires 200% without loss of content or function. The clamp does not meet it.
- FR-10.3 requires OS text-size "honoured with reflow". The clamp honours it only up to 130%.
- The users most affected are exactly persona P1 (elderly, vernacular-first) — the primary persona.

**The correct fix is reflow, not clamping**: no fixed-height text containers, no `maxLines` without `ellipsis`, every card sized by content, and horizontal rows that wrap to vertical stacks past a threshold. That is per-screen work across all features and is tracked as its own change — it must not be done by simply raising the constant, which would trade clipping for silent content loss.

Until then, this system does **not** claim 1.4.4 conformance. See §9.2.

---

## 3 · Spacing & geometry

### 3.1 Spacing — 4dp base

| Token | Value | Use |
|---|---|---|
| `xs` | 4dp | icon ↔ label |
| `sm` | 8dp | inside chips |
| `md` | 12dp | list gaps |
| `lg` | 16dp | card padding |
| `xl` | 24dp | screen gutter |
| `xxl` | 32dp | section breaks |

One scale. Ad-hoc values are a review failure — before this scale existed the app used 12/16/20/24/32 arbitrarily and cards read as if from different kits.

### 3.2 Radius

| Token | Value | Use |
|---|---|---|
| `sm` | 12dp | inputs, rows |
| `md` | 16dp | cards |
| `lg` | 20dp | board hero |
| `full` | 999dp | chips, pills |

### 3.3 Touch & ergonomics

- **Minimum target 48dp**, always, including visually smaller elements — pad the touch area, not the pixels. (FR-10.3)
- **Minimum 8dp between adjacent targets** so a moving thumb on a moving train cannot straddle two.
- **Primary actions live in the bottom third.** Nothing requiring a decision sits in a top corner.
- **Destructive actions are never in the thumb arc.** Delete, mute, and revoke-share require deliberate reach — friction is the safety mechanism.

---

## 4 · The Confidence System

The system's signature, and the deepest available moat: no incumbent renders epistemic status at all.

### 4.1 Levels

```kotlin
enum class Confidence { CERTAIN, ESTIMATED, STALE, UNKNOWN }
```

| Level | Meaning | Render | Copy |
|---|---|---|---|
| `CERTAIN` | Observed and fresh | `ink`, crisp edge, mono numerals | `16:49` |
| `ESTIMATED` | Interpolated or predicted | `estimate` ink, **dashed underline**, slow breathe | `~16:49` |
| `STALE` | Cached, past TTL | `ink2` + freshness strip beside it | `16:49 · 14:02` |
| `UNKNOWN` | Upstream has no value | Em-dash, never zero, never blank | `—` |

### 4.1a Confidence never rides on opacity *(a correction)*

The obvious way to make an estimate *look* less certain is to fade it. **That is wrong, and this system explicitly forbids it.**

Fading costs legibility, and it takes it from precisely the values the user needs most — the ETA is usually the single most important number on the screen. A first draft of these tokens set `ESTIMATED` to 60% ink and measured **2.53:1 in Light, 3.63:1 in Dark**, far below the 4.5:1 floor. The design intent was right; the channel was the worst possible choice.

Confidence therefore rides only on channels that cost nothing:

| Channel | Carries | Survives on |
|---|---|---|
| **Copy** — the `~` prefix | ESTIMATED | Everywhere, including Glance widgets and RemoteViews |
| **Edge** — dashed underline | ESTIMATED | In-app only |
| **Motion** — the breathe | ESTIMATED position marker | In-app only, reduced-motion off |
| **Adjacency** — freshness strip | STALE | Everywhere |
| **Semantics** — the word "estimated" | All | Screen readers |

`estimate` is a **full-contrast** token (= `ink2`) in all three themes, gated by `light/dark/sunlight_estimates_stay_readable`. Note that copy is the only channel that survives on every surface — which is why the `~` is a *rule*, not a flourish.

### 4.2 Rules

**No live value renders without a confidence level.** This is enforced at the component API: the primitives that display live data take `Confidence` as a **required** parameter. There is no default. A developer cannot forget to label an estimate, because the code will not compile.

That is the mechanism that turns FR-11.1 from a policy into a property.

**`UNKNOWN` renders as `—`, never `0`, never blank.** A missing platform number shown as blank reads as "no platform"; shown as `0` reads as a real platform. Both are lies. The em-dash is the only honest rendering.

**Confidence is announced, not just shown.** Screen readers receive "estimated arrival 4:49 PM" — the word "estimated" is in the `contentDescription`, because a sighted user gets it from the dashed edge and a blind user must get it from somewhere.

### 4.3 Where it appears

Every interpolated map position (FR-2.2 — never implied as GPS), every predicted delay (FR-2.6, with basis), every confirmation probability (FR-4.4), every future timeline entry, every value on a cached screen (FR-9.1).

---

## 5 · Component library

### 5.1 Primitives

| Component | Contract | Notes |
|---|---|---|
| `StatusChip` | `icon, label, level` | The FR-10.2 atom. Single `contentDescription` for the whole chip so TalkBack reads "Late 12 minutes", not "warning triangle, late, 12". |
| `ConfidenceValue` | `value, confidence, ...` | Any live number. `Confidence` required. |
| `FreshnessStamp` | `updatedAt, isStale` | "updated 8s ago" / "cached 14:02". On every live surface (FR-2.5). |
| `LivePulse` | — | The one ambient animation. Respects reduced-motion. |
| `MonoNumber` | `text` | Tabular numerals, LTR-pinned. |

### 5.2 Composites

| Component | Purpose | Key rule |
|---|---|---|
| `BoardHero` | The answer block: state + consequence + freshness | Exactly one `displaySmall`. |
| `JourneyCard` | Stack row | Leads with **state**, not train identity. |
| `ThreadSpine` | Journey timeline | Sticky present; past crisp, future `ESTIMATED`. |
| `PlatformDiagram` | Coach position (FR-3.x) | Diagram is evidence; the *sentence* is the answer. |
| `PnrPass` | Ticket | Mask enforced at the type level (§5.4). |
| `BoardRow` | Station board row | Monospaced columns; cancelled rows keep position. |
| `PlanRow` | Comparison row | Hydrates progressively; never blocks the list. |

### 5.3 State vocabulary

Every data component implements four renderings. This is a completeness requirement, not a suggestion.

| State | Rendering |
|---|---|
| Loading | Skeleton matching final layout exactly — nothing reflows on arrival |
| Cached/offline | Real content + strip + `STALE` on every value |
| Error | Plain language + next step, never blames the user |
| Empty | Directive — always exactly one action |

### 5.4 The PNR mask is a type, not a discipline

```
PNR is never a String in UI or analytics code.
It is a MaskedPnr whose toString() yields ••••2882.
```

FR-4.3 and invariant 2 require masking in every surface *and every log*. A convention ("remember to mask") fails eventually. A type that **cannot express** the unmasked value fails never. The raw value exists only inside the encryption boundary.

---

## 6 · Iconography

### 6.1 Rules

- **Shape carries meaning before colour does** (§1.5). The status triad is circle / triangle / cross.
- **Icons are never alone for status.** Always icon + word. Icons may stand alone only for *navigation* affordances that also carry a visible label.
- **One optical weight** across the set. Mixed stroke weights are the fastest way to look unfinished.
- **24dp default**, 20dp inline with text, always inside a ≥48dp target.

### 6.2 Status set

| Level | Glyph | Shape logic |
|---|---|---|
| GOOD | ✓ in circle | Enclosed, settled |
| WARN | ! in triangle | Angular, attention |
| BAD | ✕ in circle | Crossed, terminal |
| NEUTRAL | • | No claim |
| ESTIMATED | ◐ | Half-filled — partial knowledge |

The current implementation uses emoji glyphs (keeping the APK lean — no icon font, serving NFR-1's <25MB budget). **Known limitation:** emoji rendering varies by OEM font, which weakens the shape-consistency guarantee. If this proves visually unstable across the device matrix, the fix is a small bundled vector set for the five status glyphs only — roughly 3KB, not a full icon font.

---

## 7 · Motion

### 7.1 Three laws

1. **Motion explains change; it never decorates.** If an animation doesn't teach where something came from or what became true, delete it.
2. **`PollController` is the only owner of time.** UI animates on *state change*, never on a `LaunchedEffect` timer. This is why refresh doesn't drain battery (NFR-3) and why animations can't desynchronise from data.
3. **Reduced-motion is honoured everywhere** (FR-10.3). Every animation degrades to an instant state swap. Nothing is lost — motion is never the only carrier of meaning.

### 7.2 Ladder

| Token | Duration | Use |
|---|---|---|
| `instantMs` | 90ms | chip select, toggle knob |
| `quickMs` | 180ms | crossfades, chip morphs |
| `settleMs` | 280ms | sheet detents, card expand, shared-element |
| `rollMs` | 420ms | the flap-roll; severity colour ease |
| `breatheMs` | 2400ms | the estimated-position marker |

Easing: `standardDecel` `(0, 0, 0.2, 1)` for entry; `emphasizedDecel` `(0.05, 0.7, 0.1, 1)` for anything the user should notice.

### 7.3 Signature interactions

**The flap-roll** (`rollMs`). When a delay figure changes, digits roll like a mechanical split-flap board. It is the one piece of deliberate character in the system — a nod to the departure board that Direction A was built around — and it does real work: it makes *change itself* perceptible, so a user glancing at a widget knows the number is new. Reduced-motion: instant swap.

**The breathe** (`breatheMs`, infinite). The estimated-position marker pulses slowly. This is the *only* looping animation in the app. Its slowness is the point: it says "alive but uncertain" without demanding attention. Reduced-motion: static marker with a dashed ring.

### 7.4 What we do not animate

Never: attention-grabbing loops on ads or upsells (there are none), bouncing CTAs, spinner theatre where a skeleton is honest, or celebratory motion on bad news. The chart-prepared celebration (FR-4.2) is the single exception, and it is small, one-shot, and earned.

---

## 8 · Haptics

Haptics are a **third redundant channel** alongside visual and audio — critical for a device in a pocket, in a noisy station, or held by a user with low vision.

| Pattern | Trigger | Feel |
|---|---|---|
| `TICK` | Chip select, toggle | Single light |
| `CONFIRM` | Save, alarm set | Light double |
| `ARRIVE` | Arrival alarm | Escalating, repeating until dismissed |
| `BAD_NEWS` | Cancellation received | Single heavy, once |
| `CELEBRATE` | Chart prepared, confirmed | Light triple |

**Rules.** Haptics accompany *state changes the user did not cause* — a passive refresh never buzzes. `BAD_NEWS` fires once and never repeats; repeated negative haptics are a dark pattern. All haptics respect system settings and quiet hours (FR-7.4), except `ARRIVE`, which is an alarm the user explicitly set and which is permitted to bypass (FR-7.3).

---

## 9 · Accessibility specification (WCAG 2.2 AA)

### 9.1 Verified now

| Criterion | Status |
|---|---|
| 1.4.3 Contrast (minimum) | ✅ All fact-bearing pairs ≥4.5:1, composited. 9 CI tests. |
| 1.4.1 Use of colour | ✅ icon + word + colour everywhere; greyscale-legible |
| 1.4.11 Non-text contrast | ✅ brand ≥3:1 in all themes |
| 2.4.13 Focus appearance (token) | ✅ `focus` ≥3:1 vs surface, bg and surface2, all themes |
| 2.5.8 Target size (minimum) | ✅ ≥48dp, exceeds the 24dp AA floor |
| 1.3.1 Info & relationships | ✅ semantic grouping; chips carry one description |
| 4.1.2 Name, role, value (confidence) | ✅ epistemic state reaches TalkBack in words |

### 9.2 Requires device verification

These cannot be asserted from static analysis, and I am not claiming them:

| Criterion | Status / what must be tested |
|---|---|
| **1.4.4 Resize text** | ⚠️ Clamp removed, full OS scale honoured, line budget tested. Per-screen behaviour at 200% still needs instrumentation. |
| **1.4.10 Reflow** | ⚠️ Same: mechanism in place, 200% × 320dp per screen unverified |
| 2.4.13 Focus appearance | ⚠️ Token verified; the *rendered ring* (thickness, offset, keyboard + switch access) is untested |
| 4.1.2 Name, role, value | ⚠️ Confidence path covered; full TalkBack pass on all screens in EN and HI outstanding |
| 2.5.7 Dragging movements | ⚠️ Map pan and sheet drag need non-drag alternatives |

### 9.3 Beyond compliance

WCAG is the floor. The users in personas P1 and P5 need more:

- **Spoken status** in the user's language (FR-10.4) — the same string the screen reader gets.
- **Sunlight mode** at 7:1 (FR-5.3), because "readable indoors" is not the use case.
- **Native-script language names** in the picker — a user who needs Hindi cannot read the word "Hindi".
- **Plain language everywhere.** "Reverses at Itarsi — your coach moves to the front" beats "rake reversal at ET". Reading level is an accessibility dimension the WCAG AA floor doesn't cover.

---

## 10 · Ambient layer constraints *(where this system meets Android reality)*

The selected direction puts the answer outside the app. That surface does **not** get this design system, and pretending otherwise would produce a spec that cannot be built.

| Surface | Framework | Real constraints |
|---|---|---|
| Widget | **Glance** (not Compose) | Restricted composables. No arbitrary shadows, limited custom fonts, no continuous animation. **The flap-roll and the breathe are unavailable.** |
| Live notification | RemoteViews / `Notification.Builder` | OS-controlled styling; Android 12+ enforces its own decoration. Limited layout vocabulary. |
| Full-screen alarm | Full Compose Activity | Full system available. Requires `USE_FULL_SCREEN_INTENT`. |

**Consequences that shape the design, not just the implementation:**

1. **Ambient surfaces carry the *fact*, in-app carries the *nuance*.** The widget shows `~16:49 · PF 4` and its confidence marker; the app shows the reasoning, the timeline, and the basis.
2. **Confidence on ambient surfaces degrades to typography, not motion** — the `~` prefix and a dimmed ink do the work the breathe does in-app. This is why `ESTIMATED` has a *copy* rule (`~16:49`) and not only a visual one: the copy rule is the only one that survives everywhere.
3. **Widget update cadence is OS-throttled** and the exact floor varies by OEM. The freshness stamp is therefore *mandatory* on ambient surfaces — a widget that cannot guarantee recency must state its recency.
4. **Colour tokens must be duplicated as Android XML resources** for RemoteViews, which cannot read the Compose CompositionLocal. This is a genuine duplication risk: the palette can drift between Compose and XML. **Mitigation: generate the XML from the Kotlin tokens, and assert equality in CI.**

**This is the direction's weakest joint** and it remains untested on real hardware (Xiaomi/Oppo/Vivo). Per `direction-study.md` §5, device verification should gate full ambient adoption. The in-app experience is specified to stand complete without it.

### 10.1 What shipped, and what that means

The home-screen widget is built (`feature/ambient/`) using RemoteViews rather than Glance — no new dependency, no APK cost against NFR-1, and it consumes the XML tokens above.

The split is deliberate: **every content decision is pure and unit-tested** (`Ambient.resolve`, `consequenceLine`, `platformLabel`, `freshnessLabel`), while the class that touches Android does nothing but bind those results to views. So the design is verified even though the surface is not.

Three properties are baked in rather than assumed:

- **`updatePeriodMillis` is 30 minutes**, the practical floor the framework honours. Anything tighter must arrive as a Watcher push, never as widget polling — a widget that polls breaks NFR-3 in the name of saving a launch.
- **The freshness stamp is mandatory.** A surface that cannot promise recency must state the recency it has.
- **The widget never fetches.** It renders a snapshot the app writes from data it already had.

What remains unknowable without hardware: whether the OS honours that cadence, whether OEM battery managers suppress redraws entirely, and how the layout behaves at large text on a real launcher. Do not read "shipped" as "verified."

---

## 11 · Implementation status

| Delivered | Location |
|---|---|
| Corrected palette, all three themes | `core/design/Colors.kt`, `Semaphore.kt` |
| Composite-aware contrast gate, **20 tests** | `test/SemaphoreContrastTest.kt` ✅ green |
| Unified token set — `boardRed`, `estimate`, `focus`, `brand2` folded into `RailcastColors` | `core/design/Colors.kt` |
| Sunlight theme selectable (FR-5.3) | `core/design/Theme.kt` |
| `ConfidenceValue` primitive, required `Confidence` | `core/design/ConfidenceValue.kt` |
| Confidence honesty rules as tests | `test/ConfidenceValueTest.kt` ✅ green |
| Sunlight selectable + persisted, EN/HI toggle | `core/design/SunlightStore.kt`, `feature/alerts/` |
| Theme precedence as a pure, tested rule | `test/ThemeSelectionTest.kt` ✅ green |
| Full OS text scale honoured; reflow line budget | `core/design/Reflow.kt`, `test/ReflowTest.kt` ✅ |
| Projected vs observed times distinguished | `feature/track/StopConfidence.kt` ✅ |
| PNR masking enforced by type | `core/data/Pnr.kt`, `test/PnrMaskingTest.kt` ✅ |
| XML tokens + CI drift gate | `res/values*/design_tokens.xml`, `test/DesignTokenParityTest.kt` ✅ |
| Three tabs (PRD §7 amended) | `ui/RailcastApp.kt`, `ui/FindScreen.kt`, `test/NavigationStructureTest.kt` ✅ |
| Ambient widget + tested content rules | `feature/ambient/`, `test/AmbientStateTest.kt` ✅ |
| Spacing / radius scales | `core/design/Dimens.kt` |
| Type scale, tabular numerals | `core/design/Type.kt` |
| `StatusChip`, `BoardHero`, `MonoNumerals` | `core/design/` |

### Not yet built

Ordered by dependency. Each is a separate reviewable change; the structural ones need a plan first per `android/CLAUDE.md`.

1. **Device verification of the ambient layer** — the one item that cannot be closed from here, and the direction's weakest joint. Redraw cadence, lockscreen persistence, and OEM battery-manager behaviour (PRD §12 Q3) need a real Xiaomi/Oppo/Vivo matrix. The content rules are unit-tested; *whether the OS redraws when asked* is not, and cannot be.
2. **Live notification** (the lockscreen surface of W1). The widget covers the home screen; the ongoing notification does not exist yet. It should render from the same `Ambient` model so the two cannot diverge.
3. **The omni-input classifier.** `FindScreen` consolidates the destination, but the single field that classifies train / PNR / station / route by shape is not built — it needs the directory classifier that Track and Station each own today.
4. **Ambient-light auto-suggest for Sunlight** (FR-5.3). The mode is user-selectable; the sensor-driven prompt is not built, and must never override an explicit choice.
5. **Sunlight on ambient surfaces.** Sunlight is a user choice, not a system configuration, so no resource qualifier selects it — RemoteViews renders the light tokens. The widget would need to read the preference and swap colours itself.
6. **`STALE` rendering on cached screens.** Offline shows the banner; individual values do not yet drop to `STALE`.
7. **Screen-level reflow verification.** The line budget is tested; per-screen behaviour at 200% × 320dp still needs instrumentation or screenshot tests.

---

## 12 · How to review a screen against this system

1. Count the `displaySmall`. Exactly one, or the hierarchy is wrong.
2. Screenshot it in greyscale. Every status still identifiable?
3. Find every live number. Does each carry a confidence?
4. Turn off the network. Does it degrade, or does it blank?
5. Set text size to 200%. Does it reflow, or clip?
6. Is the primary action reachable by a thumb? Is the destructive one *not*?
7. Is any number proportional-figured? It will jitter on refresh.

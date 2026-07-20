# Railcast — Direction Study & Lo-Fi Wireframes

**Phase 1 of the redesign.** Four organizing principles, competed. One winner, developed to low-fidelity wireframes.
Version 1.0 · July 2026 · No visual styling in this document — structure only.

---

## How to read this document

This is deliberately **pre-visual**. There are no colours, no typefaces, no shadows, no illustrations. Every wireframe is ASCII. This is not laziness — it is the point. A design direction that only wins once it is beautifully rendered has not won; it has been disguised. If the structure below does not already answer the user's question faster than the alternatives, no amount of styling will save it.

The design system (colour, type, spacing, icon, motion, haptics, a11y specification) is **Phase 2**, and is deliberately withheld until these wireframes are agreed.

**Notation:** `[ ]` tappable · `▓` high-emphasis surface · `·` whitespace · `◐` estimated/low-confidence value · `★` the user's own stop/coach · `↓` scroll continues · `⟳` live-refreshing element.

---

## 0 · The constraint set (what may not be reimagined)

Everything else is fair game. These are not.

| Locked | Source | Consequence for design |
|---|---|---|
| No raw journey date to track a live train | FR-2.3, invariant 3 | Date is a *probe result*, never an input. Any wireframe with a date field on the track path is invalid. |
| Status = icon + word + colour, never colour alone | FR-10.2, invariant 4 | Every state indicator is a three-part compound. No colour-only dots anywhere. |
| PNR masked in every surface (`••••2882`) | FR-4.3, invariant 2 | The full PNR literally cannot be rendered. Affects the "pass" metaphor. |
| No ads, interstitials, forced login, dark patterns | FR-10.5, §7, invariant 5 | No growth-hack surfaces. Share prompts are quiet and dismissible-forever. |
| Honesty rendering: interpolated position labelled, predictions labelled with basis, freshness stamps universal | FR-11.1, FR-2.2, FR-2.5 | Confidence is a required data dimension on every live value, not an optional flourish. |
| Degrade, never block, when offline | FR-9.1 | Every screen has a cached rendering. No screen may be blank-on-no-network. |
| One composed BFF call per screen (`/screen/*`) | §6 decision 2, api-contracts §1–4 | **An IA whose screen needs three endpoints is fighting the backend.** This is the sharpest architectural constraint on structure. |
| ≥48dp targets, OS text-size reflow, screen-reader labels, reduced-motion | FR-10.3 | One-handed reach and text scaling are structural, decided at wireframe time. |

### One deliberate deviation, flagged

PRD §7 mandates **five tabs: Home · Track · Station · Plan · Alerts.** Every direction below reduces this. §7 is a *UX requirement*, not vision, functionality, or architecture — and the brief instructs me to question assumptions of exactly this kind. The reduction is argued per-direction, and **adopting any of them requires amending PRD §7 in the same PR**, per CLAUDE.md invariant 7's spirit. I am not silently overriding the source of truth; I am proposing an edit to it.

Why five tabs is wrong in one line: *Track*, *Station*, and *Plan* are not three destinations — they are three **questions about the same object**, and *Alerts* is not a place, it is a property of a journey.

---

## 1 · The seven questions

Every direction is scored against the only thing that matters — how fast a real person gets a real answer. These seven cover ~95% of sessions, derived from personas P1–P6 and the §13 acceptance snapshot.

| # | Question | Persona | Emotional state | Shape of the answer |
|---|---|---|---|---|
| Q1 | How late is my train? | P1, P2 | Anxious, repeated | Single number + consequence |
| Q2 | Where is it right now? | P1 | Anxious, watching | Spatial, low-confidence |
| Q3 | Which platform? | P3 | Rushed, sunlight, one-handed | Single token, huge |
| Q4 | Where does my coach stop? | P3, P5 | Rushed, physical | Spatial diagram |
| Q5 | Is my ticket confirmed? | P2 | Waiting, hopeful | Status + odds |
| Q6 | What's leaving this station? | P3 | Scanning | Ranked list |
| Q7 | Which train should I take? | P4 | Deliberating, seated | Comparison table |

**The critical asymmetry:** Q1–Q5 concern *a journey the user already has*. Q6–Q7 concern *a journey they do not have yet*. These are different products glued together, and how a direction handles that seam is what separates the four.

Second asymmetry, less obvious and more important: **Q1–Q4 are asked repeatedly, in short bursts, under stress, often many times an hour.** Q5–Q7 are asked once or twice, calmly, seated. Optimising the whole app for the calm case is the incumbent's mistake.

---

# PART ONE — FOUR DIRECTIONS

Each is a genuinely different **organizing principle**, not a restyle. Each is presented at its strongest, then attacked.

---

## Direction A — "THE BOARD"

> *Mental model: the station departure board, reimagined as software.*
> Organizing principle: **the institutional artifact.**

Indian travellers have read mechanical and LED departure boards their entire lives — including travellers who cannot read fluently, including travellers who have never used a smartphone app. That grammar is already installed in the target user's head. Direction A does not invent a new language; it takes the one universally understood object in Indian rail and makes it personal, live, and pocket-sized.

The entire app is **one board surface**. Everything is a row. You filter it, you zoom into a row, you never leave the board.

```
┌──────────────────────────────────┐
│ ▓▓▓  MY BOARD              ⟳ 8s ▓│
│ ▓                               ▓│
│ ▓ 12951  MUMBAI RAJDHANI        ▓│
│ ▓ ★NDLS  16:55  PF 4   LATE 12m ▓│
│ ▓                               ▓│
│ ▓ 12002  SHATABDI               ▓│
│ ▓ ★BPL   06:12  PF 1   ON TIME  ▓│
│ ▓                               ▓│
│ ▓ 22470  ────────────────────── ▓│
│ ▓ ★JU    23:40  PF —  CANCELLED ▓│
│ ▓                               ▓│
│ ├─────────────────────────────── │
│ │ [ MY TRAINS ][ STATION ][ FIND ]│
└──────────────────────────────────┘
```

Zooming a row expands it *in place* into the full journey — the board never navigates away, it accordions.

**Strengths.** Unbeatable glanceability and sunlight legibility; the grammar is pre-learned by every persona including P5 and low-literacy users; monospaced fact columns make delay comparison trivial; it is culturally *right* in a way no Silicon Valley pattern is; trivially accessible (a board is a table, and tables are what screen readers do best).

**Where it breaks.** A board is a **list of facts, and consequence is not a fact.** "LATE 12m" is exactly the datum that PRD Finding-style analysis identifies as anxiety-inducing rather than relieving — the user still has to do the arithmetic. Boards have no room for "you'll reach Bhopal ~16:49." Worse: Q4 (coach position) is a *diagram*, and Q7 (plan) is a *comparison* — neither is a row, so both must break the metaphor, and the moment you break your organizing metaphor twice, you no longer have one. It is also structurally backward-looking: it designs around the artifact we are meant to be replacing, and inherits the board's coldness at precisely the moments (cancellation, waitlist) when the user needs warmth.

**Verdict: loses as an architecture, survives as a display grammar.** The monospaced, high-contrast, column-aligned fact rendering is the correct way to show *certain* values, and is absorbed into the winner.

---

## Direction B — "THE COMPANION"

> *Mental model: a knowledgeable friend travelling with you.*
> Organizing principle: **the journey lifecycle, with a state-resolved entry point.**

*(This is the direction proposed in the prior study, `railcast-redesign-2026.md`, entered here as an unnamed candidate and judged on merit.)*

There is no fixed home screen. On launch the app computes the highest-priority journey state and opens directly to the surface that answers it: zero journeys → an invitation; exactly one active → that journey's full surface; two or more → a stack. Confidence is rendered as a visual dimension — estimates look soft and breathing, stale values look desaturated, certain facts look crisp.

```
   0 journeys        1 active          2+ active
       │                │                  │
       ▼                ▼                  ▼
  ┌─────────┐     ┌─────────┐        ┌─────────┐
  │ ·       │     │▓ 12m ◐  │        │ [card]  │
  │ [search]│     │▓ ~16:49 │        │ [card]  │
  │ ·       │     │  PF 4   │        │ [card]  │
  └─────────┘     └─────────┘        └─────────┘
   INVITATION      LIVE JOURNEY       JOURNEY STACK
```

**Strengths.** For the dominant case — one train, running now — the answer is on screen with zero navigation. The Confidence System is a genuine and defensible innovation: no incumbent renders epistemic status, and doing so converts the PRD's honesty *requirements* (FR-11.1, FR-2.2) from small-print liability into a visible trust asset. The journey-as-atom IA correctly refuses to scatter track/ticket/platform/alerts across siblings.

**Where it breaks.** Two things, and the second is fatal to it as the *organizing* principle.

First, a shifting entry point is a real usability liability for the exact users the PRD prioritises. P1 is explicitly "may be elderly, vernacular-first"; P6 exists solely because elders need someone else to configure their phone. A home screen that is a different screen each launch is hostile to users who navigate by spatial memory and rote sequence rather than by reading. The proposal's mitigation (shared-element continuity, persistent home affordance) is thoughtful but mitigates a self-inflicted wound.

Second, and decisively: **B optimises app launch. The correct move is to make app launch unnecessary.** The §2 headline metric is *time-to-answer from cold launch ≤5s, ≤2 taps*. B attacks the "≤2 taps" term brilliantly and accepts the "cold launch" term as given. But cold launch on an entry-level Android is 2.5s of the 5s budget (NFR-1) that we spend *before showing anything*. B is solving the right problem one level too late in the stack.

**Verdict: loses as an organizing principle, but contributes the single most valuable subsystem.** The Confidence System survives wholesale. Journey-as-atom survives. The state-resolution logic survives — relocated, as we will see, to where it belongs.

---

## Direction C — "THE THREAD"

> *Mental model: a messaging thread.*
> Organizing principle: **chronology.**

A journey is a conversation that the train is having with you. Each journey is a scrolling thread: past events above, future scheduled events below, "now" pinned in the middle. Every alert the Watcher fires lands *in the thread* as a message.

```
┌──────────────────────────────────┐
│  12951 · MUMBAI RAJDHANI      ⋮  │
│ ─────────────────────────────────│
│  ┆ 04:02  Chart prepared          │
│  ┆        S4 · 32 · LOWER    ✓    │
│  ┆                                │
│  ┆ 14:20  Left Kota            ✓  │
│  ┆        6 min late              │
│  ┆                                │
│ ▓▓ NOW ▓ 12 min late ▓ PF 4 ▓⟳8s▓│
│  ┆                                │
│  ┆ ~16:49 ★ Arrives Bhopal     ◐  │
│  ┆        your stop · alarm set    │
│  ┆ 19:30   Arrives Nagpur      ◐  │
│                                 ↓ │
└──────────────────────────────────┘
```

**Strengths.** This is quietly brilliant on one axis: **it solves the alert-receipt problem natively.** The Watcher is the product's reason to exist, but a push notification is ephemeral — swipe it away and the proof that Railcast was watching is gone. In a thread, *the notification and the permanent record are the same object*. No separate journal screen needs to exist, because the history is the interface. That is a genuine structural insight.

It is also the most *familiar* grammar available: WhatsApp is near-universal among the target users, more so than any app-design convention. And it handles multi-day journeys (FR-2.3's Day 1/2/3) with zero special-casing, because chronology is already the axis.

**Where it breaks.** Chronology buries the present. Q1/Q3 — the most frequent, most time-pressed questions — require *scanning to find now*, and the pinned NOW bar is an admission that the organizing principle fails its own primary use case. Q7 (comparison) is not chronological in any sense and cannot live here at all. Q4 (coach diagram) is spatial. And threads grow monotonically: a 36-hour journey produces a very long scroll with the useful part in the middle.

**Verdict: loses as the app's organizing principle, wins the journey detail view outright.** The thread is the correct structure for *one journey's interior*, and it is absorbed as the journey spine. Its receipt property is preserved exactly.

---

## Direction D — "THE AMBIENT INSTRUMENT"

> *Mental model: a car dashboard — an instrument you glance at, never "use."*
> Organizing principle: **the answer lives outside the app.**

Design the lockscreen, the ongoing notification, and the home-screen widget **first**. The app is not the product; the app is the *expanded view* of an ambient surface that is already showing the answer. Success is measured by sessions **avoided**.

```
  LOCKSCREEN (the real home screen)
┌──────────────────────────────────┐
│  09:41                    Mon 20 │
│                                  │
│ ┌──────────────────────────────┐ │
│ │ RAJDHANI 12951        ⟳ 40s  │ │
│ │ ▓ 12 MIN LATE ▓              │ │
│ │ Bhopal ~16:49 · PF 4         │ │
│ │ [ Alarm 20m before ]  [ Mute]│ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘

  WIDGET 4×2                WIDGET 2×2
┌────────────────────┐   ┌──────────┐
│ ★BPL  PF 4      ⟳  │   │  PF 4    │
│ ▓ ~16:49 ▓ +12m    │   │ ~16:49   │
│ Rajdhani · 12951   │   │  +12m ⟳  │
└────────────────────┘   └──────────┘
```

**Strengths.** It attacks the §2 headline metric at the root: **time-to-answer becomes ~0 seconds and 0 taps**, because the answer is already rendered before the user forms the intent to check. Nothing else in this study can claim that. It is also the only direction that is *structurally* aligned with NFR-3 (battery/data: "background = push only") and with FR-7's server-side Watcher — the ambient surface is a rendering of push payloads the Watcher already sends, so the infrastructure exists and is built (backlog M2, complete).

It serves the anxious tracker (P1) most humanely of all four: the anxiety loop is *open app → check → close → repeat every ten minutes*. D breaks that loop by making the check free, which is a genuine reduction in human suffering, not a metric.

And it is the most defensible competitive position in the study. Incumbents cannot follow: an ad-funded app **cannot** optimise for sessions avoided, because sessions are its revenue. Ambient-first is a strategy structurally unavailable to WIMT and its clones. That is a moat made of business model, not of code.

**Where it breaks.** It cannot be the whole product. Q6 (station board) and Q7 (planning) have no ambient form. Q4 (coach diagram) is too complex for a notification. Android's ambient surfaces are also constrained and fragmented — widget update cadence is throttled by the OS, and the OEM battery-manager problem is a known live risk (PRD §12 open question 3, backlog 4.8). A direction that bets everything on the ambient layer bets on the least reliable part of the platform.

**Verdict: wins as the organizing principle — but only when honestly scoped as ambient-*first*, not ambient-only.**

---

# PART TWO — COMPARISON AND SELECTION

## 2.1 Scored against the seven questions

Taps-to-answer from a cold device, lower is better. **0** = answer visible without acting.

| | A · Board | B · Companion | C · Thread | D · Ambient |
|---|---|---|---|---|
| Q1 How late | 1 | 1 | 1 (scan) | **0** |
| Q2 Where is it | 2 | 1 | 2 | 1 |
| Q3 Which platform | 1 | 1 | 1 (scan) | **0** |
| Q4 Coach position | 3 (breaks metaphor) | 2 | 2 | 2 |
| Q5 Ticket confirmed | 2 | 1 | **0** (in thread) | **0** |
| Q6 Station board | 1 | 2 | 3 (alien) | 2 |
| Q7 Plan A→B | 3 (breaks metaphor) | 2 | 4 (alien) | 2 |
| **Weighted total** ¹ | 1.9 | 1.4 | 2.1 | **0.9** |

¹ Weighted by frequency × anxiety per §1's second asymmetry: Q1–Q4 ×3, Q5 ×2, Q6–Q7 ×1.

## 2.2 Scored against the PRD's own success metrics

| §2 Metric | Target | A | B | C | **D** |
|---|---|---|---|---|---|
| Time-to-answer from cold launch | ≤5s, ≤2 taps | ✓ | ✓✓ | ✓ | **✓✓✓ (bypasses launch)** |
| First-session unaided success, incl. 55+ / low-literacy | ≥85% | **✓✓✓** | ✗ (shifting entry) | ✓✓ | ✓✓ |
| 14-day return with a saved train | ≥45% | ✓ | ✓✓ | ✓✓ | **✓✓✓ (persistent presence)** |
| Installs from shared links | ≥15% | ✓ | ✓ | ✓✓ | ✓ |
| "Wrong info" reports | <2/1000 | ✓ | **✓✓✓ (confidence rendered)** | ✓ | ✓✓ |
| Crash-free on entry-level | ≥99.5% | ✓✓ | ✓ | ✓ | ✓✓ |
| Chart-push within 5 min | ≥95% | — | — | **✓✓✓ (receipt)** | ✓✓✓ |

## 2.3 The decision

**Direction D wins**, with C's thread absorbed as the journey interior, B's Confidence System absorbed as a subsystem, and A's board grammar absorbed as the fact-display language.

The single argument, stated plainly:

> Every other direction is a better *app*. D is a better *answer*. The user does not want to use Railcast; they want to know if the train is late. The design that best serves them is the one that most reliably makes itself unnecessary.

Three supporting reasons.

**It attacks the right term.** B's improvement is real but bounded: it removes taps from a session that still costs a cold launch. D removes the session. On the PRD's own headline metric this is the difference between an optimisation and a step change.

**It is the only strategy the incumbents cannot copy.** Ad-funded competitors monetise attention; "sessions avoided" is revenue destroyed. Every other direction here is copyable by WIMT in a redesign cycle. This one is structurally forbidden to them, and it is a direct expression of §1.3's bet — win on synthesis and experience, not raw data — and of the ad-free promise as brand law (FR-10.5).

**The infrastructure already exists.** The Watcher (M2, complete), push fan-out, adaptive cadence, quiet hours, and diff engine are built and tested. D is largely a *rendering* of payloads the server already computes. This is the direction with the **lowest** incremental backend cost, which matters acutely for a codebase at the launch gate.

### What I am explicitly rejecting, and why that costs something

Rejecting B as organizer means **giving up the state-resolved entry point** — a genuinely elegant idea. I am not discarding it; I am relocating it. State resolution is exactly right, but its correct home is the *ambient layer*, deciding what the widget and lockscreen show, where "the surface changes based on state" is the native and expected behaviour rather than a surprise. A widget that changes with state reads as intelligent; a home screen that does reads as unstable. Same mechanism, correct venue.

Rejecting A as organizer costs cultural familiarity, which is a real loss for P5 and low-literacy users. Mitigated by keeping the board grammar intact wherever facts are displayed, and by keeping the station board itself a literal board (§3.9).

### The honest risk

D's dependency on Android ambient surfaces is its weakest joint, and OEM battery managers are a known live risk. **The mitigation is architectural, and it is non-negotiable:** the in-app experience must be complete and excellent *standing alone*, with the ambient layer as pure enhancement. If every widget on earth failed, what remains must still beat every direction above. The wireframes in Part Three are built to that standard — which is why there are more in-app screens than ambient ones.

**Migration reality.** This does not require rebuilding the app. The existing `PollController`, `Resource<T>` SWR, Room cache, directory, and all `/screen/*` integrations are unaffected — this is a presentation-layer redesign over an intact data layer. The ambient layer is additive. The tab reduction is the only structural change, and it is a re-parenting of existing screens, not a rewrite.

---

# PART THREE — LOW-FIDELITY WIREFRAMES

**"Railcast Ambient" — structure only. No styling.**

## 3.0 The architecture in one diagram

```
        ┌────────────── AMBIENT LAYER (primary) ──────────────┐
        │  Lockscreen live update · Widgets · Alarm · Watch   │
        │  state-resolved · rendered from Watcher push        │
        └──────────────────────┬──────────────────────────────┘
                               │ tap = expand, never "open"
        ┌──────────────────────▼──────────────────────────────┐
        │                   THE APP (complete alone)          │
        │                                                     │
        │   TAB 1 · JOURNEYS      TAB 2 · FIND    TAB 3 · YOU │
        │   stack → journey        omni-input      controls   │
        │           └─ thread      ├─ station      + privacy  │
        │              spine       └─ plan                    │
        └─────────────────────────────────────────────────────┘
```

**Three tabs, down from five.** *Track* and *Alerts* were never destinations — tracking is what a journey **is**, and alerts are a *property* of one, now visible inside the journey thread where the evidence belongs (C's receipt insight). *Station* and *Plan* are both "find me something I don't have yet" — one intent, one input. This requires amending PRD §7.

Tabs are labelled always (FR-10.3), sit in the thumb arc, and never change identity between launches — the stability B sacrificed is recovered here, while state-resolution moves to the ambient layer where it belongs.

---

## 3.1 W1 — Lockscreen live update *(the real home screen)*

**Core goal:** Q1 + Q3 answered with zero taps and zero launches.

```
┌────────────────────────────────────┐
│  09:41                      Mon 20 │
│                                    │
│ ┌────────────────────────────────┐ │
│ │ RAJDHANI 12951          ⟳ 40s  │ │  ← freshness always (FR-2.5)
│ │                                │ │
│ │ ▓▓ 12 MIN LATE ▓▓              │ │  ← icon+word+colour (FR-10.2)
│ │                                │ │
│ │ You reach BHOPAL   ~16:49  ◐   │ │  ← CONSEQUENCE, not datum
│ │ Platform 4                     │ │
│ │                                │ │
│ │ [ Wake me 20m before ] [ Mute ]│ │  ← FR-7.3 / FR-7.4
│ └────────────────────────────────┘ │
└────────────────────────────────────┘
```

**Reasoning.** The largest element is not the delay — it is *what the delay means for you*. "12 min late" is the train's fact; "you reach Bhopal ~16:49" is the user's answer, and it is the line that ends the anxiety loop. The `◐` marks it as estimated, satisfying FR-11.1 without a disclaimer sentence.

Two actions only, both one-tap, both irreversible-safe. Mute is present because the PRD's default posture is "minimal, meaningful notifications" (FR-7.4) and the user must be able to end the relationship instantly from the surface that is bothering them.

**States:** cancelled → the whole card becomes the cancellation with a single `[ See alternatives ]` action (FR-2.4); offline → freshness reads `cached 14:02` and `◐` extends to all values (FR-9.1).

---

## 3.2 W2 — Widgets

**Core goal:** the same answer, on the home screen, at two densities.

```
  4×2 (default)                      2×2 (minimal)
┌────────────────────────────┐    ┌──────────────┐
│ ★ BHOPAL      PF 4      ⟳  │    │   PF 4       │
│ ▓ ~16:49 ▓  12 min late    │    │  ▓~16:49▓    │
│ Rajdhani 12951             │    │  +12m     ⟳  │
└────────────────────────────┘    └──────────────┘

  0 journeys (both sizes)
┌────────────────────────────┐
│  Railcast                  │
│  [ Track a train ]         │   ← never blank; always one action
└────────────────────────────┘
```

**Reasoning.** State resolution lives *here*: 0 journeys → invitation, 1 → that journey, 2+ → the most urgent, defined as nearest-in-time to a user-relevant event. The 2×2 keeps only platform and arrival because those are Q3 and the consequence — everything else is identification, which the user does not need when they only have one train.

---

## 3.3 W3 — Arrival alarm (full-screen)

**Core goal:** wake a sleeping person and orient them in under three seconds. FR-7.3.

```
┌────────────────────────────────────┐
│                                    │
│                                    │
│        ▓▓  BHOPAL  ▓▓              │
│                                    │
│        20 minutes away             │
│                                    │
│        Platform 4                  │
│        Coach S4 · front of train   │  ← Q4 pre-answered at the
│                                    │     one moment it is urgent
│                                    │
│                                    │
│   [    I'm awake    ]              │
│   [ Snooze 5 min ]                 │
└────────────────────────────────────┘
```

**Reasoning.** A person woken at 04:00 has no working memory. Everything needed to physically stand up and move is on one screen: where, when, which platform, which end. No navigation, no scrolling, nothing else competing. The coach hint appears here because "where do I stand" is *only* urgent in the ninety seconds before arrival — this is progressive disclosure across time rather than across screens.

Dismissal is a large, unambiguous, high-contrast target for someone half-asleep in the dark.

---

## 3.4 W4 — Journeys tab (the stack)

**Core goal:** all the user's journeys, most urgent first.

```
┌────────────────────────────────────┐
│  Journeys                       ⟳  │
│ ──────────────────────────────────│
│ ┌────────────────────────────────┐ │
│ │ ▓ 12 MIN LATE ▓          ⟳ 8s  │ │
│ │ Rajdhani 12951                 │ │
│ │ ★Bhopal ~16:49 · PF 4      ◐   │ │
│ │ ●●●●●●●○○○○  6 of 11 stops     │ │
│ └────────────────────────────────┘ │
│ ┌────────────────────────────────┐ │
│ │ ▓ CANCELLED ▓                  │ │
│ │ 22470 · Fri                    │ │
│ │ [ See alternatives ]           │ │  ← FR-2.4, inline
│ └────────────────────────────────┘ │
│ ┌────────────────────────────────┐ │
│ │ ▓ WAITLIST 12 ▓   ••••2882     │ │  ← masked (FR-4.3)
│ │ Chart ~4h · odds 78%       ◐   │ │
│ └────────────────────────────────┘ │
│ ──────────────────────────────────│
│ [ JOURNEYS ]  [ FIND ]   [ YOU ]  │
└────────────────────────────────────┘
```

**Reasoning.** Every card leads with **state**, not with train identity — the user knows which train is theirs; what they do not know is how it is doing. Bad news carries its remedy inline (`See alternatives`), so a cancellation is never a dead end. The PNR card is masked at the card level, before any detail screen exists, because FR-4.3 says *every* surface.

Ordering is by urgency, not recency: running now → chart imminent → upcoming → completed.

---

## 3.5 W5 — Journey surface (the thread spine)

**Core goal:** everything about one journey, present pinned, history above, future below. Absorbs C.

```
┌────────────────────────────────────┐
│ ‹ Rajdhani 12951 · Day 2        ⋮  │  ← FR-2.3 multi-day label
│ ──────────────────────────────────│
│ ▓▓ 12 MIN LATE ▓▓          ⟳ 8s   │  ← STICKY: always visible
│ ▓ You reach BHOPAL ~16:49 ◐       │     (fixes C's burial problem)
│ ▓ Platform 4 · Coach S4           │
│ ──────────────────────────────────│
│  ┆ 04:02  Chart prepared      ✓   │  ← the receipt (C's insight):
│  ┆        S4 · 32 · LOWER         │     alert and record are one
│  ┆ 14:20  Left Kota  +6m      ✓   │
│  ┆                                │
│ ▓┆ NOW ── estimated ──────── ◐    │  ← FR-2.2 labelled, never GPS
│  ┆                                │
│  ┆ ~16:49 ★ BHOPAL · PF 4     ◐   │
│  ┆         [ Stand here ]         │  ← Q4 entry, in context
│  ┆ ~19:30   Nagpur             ◐  │
│                                  ↓ │
│ ──────────────────────────────────│
│ [ Alarm ]  [ Share ]  [ Ticket ]  │  ← state-ranked dock
└────────────────────────────────────┘
```

**Reasoning.** The sticky header is the fix for C's fatal flaw: chronology organises the content, but *the present never scrolls away*. This is the single most important structural decision in the app — it gives the thread's receipt property without paying the thread's scanning cost.

Confidence (B's system) is visible as a gradient down the screen: past events are crisp and checked, the present is live, the future is `◐`. Honesty is legible pre-cognitively rather than read.

The dock ranks by state — running → Alarm first; pre-chart → Ticket first; cancelled → Alternatives first — so the most likely action is always leftmost, in the thumb arc.

---

## 3.6 W6 — "Stand here" (coach & platform)

**Core goal:** which physical end of the platform to walk to. FR-3.1–3.3. The most shareable answer in the category.

```
┌────────────────────────────────────┐
│ ‹ Stand here · BHOPAL · PF 4       │
│                                    │
│   ← front            rear →        │
│  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┐      │
│  │EN│G │G │A1│A2│★ │S5│S6│G │      │  ← ★ = your coach
│  └──┴──┴──┴──┴──┴──┴──┴──┴──┘      │      G = general (FR-3.3)
│         ▲                          │
│    ═════╧═══════ PLATFORM 4 ═══    │
│                                    │
│  ▓ Stand at the MIDDLE ▓           │  ← the actual answer, in words
│                                    │
│  ⚠ Reverses at Itarsi — your       │  ← FR-3.2, plain language
│     coach moves to the FRONT       │
│                                    │
│  [ General coaches ]  [ Share ]    │  ← FR-3.3 toggle, P5
└────────────────────────────────────┘
```

**Reasoning.** The diagram is *supporting evidence*; the sentence "Stand at the MIDDLE" is the answer, and it is rendered in words because a user running down a platform in sunlight cannot parse a diagram. Redundant encoding again (FR-10.2): position is shown spatially, stated in words, and marked with `★`.

Reversal is called out as a warning in plain language, not a data note — it is the single most consequential thing the app can tell a traveller, and getting it wrong means missing a train.

GEN mode is a first-class toggle, not a setting, serving P5 without requiring a PNR.

---

## 3.7 W7 — Ticket (PNR as a pass)

**Core goal:** Q5 — am I confirmed, and where do I sit. FR-4.1–4.4.

```
┌────────────────────────────────────┐
│ ‹ Ticket                        ⋮  │
│ ┌────────────────────────────────┐ │
│ │ ▓ CONFIRMED ▓        ••••2882  │ │  ← masked (FR-4.3, invariant 2)
│ │ ──────────────────────────────│ │
│ │  R SHARMA      S4 · 32 · LOWER │ │
│ │  A KUMAR       S4 · 33 · MIDDLE│ │
│ │ ──────────────────────────────│ │
│ │  3A · Tatkal · Chart prepared  │ │
│ └────────────────────────────────┘ │
│                                    │
│  [ Stand here ]   [ Track live ]   │
│                                    │
│  Your PNR is stored encrypted and  │  ← FR-4.3 plain-language note
│  deleted after the journey. [more] │
└────────────────────────────────────┘
```

**Waitlist variant** shows `▓ WAITLIST 12 ▓`, `Confirmation odds 78% ◐ — based on last 60 days` (FR-4.4, labelled with basis per FR-11.1), and an inline `[ See confirmed alternatives ]`.

**Reasoning.** The pass metaphor works precisely because the PNR is masked — the object of value is the *berth*, and that is what fills the card. Per-passenger rows are the unit because that is how a family reads a ticket. The privacy note is on the screen itself, not buried in settings, because trust must be shown where the sensitive data is.

---

## 3.8 W8 — Find (omni-input)

**Core goal:** one input for train, PNR, station, and route. Replaces two tabs.

```
┌────────────────────────────────────┐
│  ┌──────────────────────────────┐  │
│  │ Search or say a train…    🎤 │  │  ← FR-1.4 voice
│  └──────────────────────────────┘  │
│                                    │
│  [ Trains near me ]                │  ← FR-5.2, one tap
│  [ Plan a trip ]                   │
│                                    │
│  RECENT                            │
│  12951 Rajdhani                    │
│  BPL Bhopal Jn                     │
└────────────────────────────────────┘

  typing "goa"                typing "227"
┌──────────────────────┐  ┌──────────────────────┐
│ goa|                 │  │ 227|                 │
│ ─────────────────────│  │ ─────────────────────│
│ TRAINS               │  │ 22705 Tirupati Exp   │
│ 12779 Goa Express    │  │ 22470 Bikaner SF     │
│ STATIONS             │  │ ──────────────────── │
│ MAO  Madgaon         │  │ 10 digits = PNR      │  ← FR-1.5 inline
│ ROUTES               │  │ 5 digits  = train    │     guidance, never
│ Delhi → Goa          │  │                      │     a server error
└──────────────────────┘  └──────────────────────┘
```

**Reasoning.** The user should never have to classify their own query. Typing `12951`, `4512882882`, `Bhopal`, or `Delhi to Goa` all work in one field — the client classifies by shape and the offline directory resolves names to codes before any call (FR-1.1). Format validation is *guidance shown while typing*, never an error after submitting (FR-1.5).

Voice is on the field itself, not behind a menu, because P1 is explicitly vernacular-first and may not type comfortably.

Zero-state offers the two intents an empty field cannot express: "near me" and "plan a trip."

---

## 3.9 W9 — Station board *(kept as a literal board — Direction A preserved)*

**Core goal:** Q6, at a station, in sunlight, one-handed. FR-5.1–5.3.

```
┌────────────────────────────────────┐
│ ‹ BHOPAL JN            [2h][4h][8h]│
│ ──────────────────────────────────│
│ ▓ 16:49  12951 Rajdhani      PF 4 ▓│
│ ▓        NEW DELHI      LATE 12m  ▓│
│ ──────────────────────────────────│
│ ▓ 17:05  22470 Bikaner       PF — ▓│
│ ▓        BIKANER       CANCELLED  ▓│
│ ──────────────────────────────────│
│ ▓ 17:20  12002 Shatabdi      PF 1 ▓│
│ ▓        BHOPAL        ON TIME    ▓│
│ ──────────────────────────────────│
│ [ Filter ]              ⟳ 30s     │
└────────────────────────────────────┘
```

**Reasoning.** Direction A's grammar is retained *exactly* where it is strongest and nowhere else. Column alignment and monospaced numerals let the eye scan one axis at a time. Cancelled rows stay in position rather than being hidden — a traveller looking for a train that no longer runs must *find it* and see that it is cancelled, not fail to find it and wonder.

Time-window chips sit top-right; the filter is bottom-left in the thumb arc. Sunlight mode (FR-5.3) is a theme over this structure, not a different layout.

---

## 3.10 W10 — Plan (comparison)

**Core goal:** Q7 — choose between trains on price, seats, punctuality. FR-6.1–6.4.

```
┌────────────────────────────────────┐
│ ‹ DELHI → GOA      [ Fri 24 Jul ]  │  ← date picker legal here:
│  ‹ Thu · FRI · Sat · Sun ›         │     planning a future trip,
│ ──────────────────────────────────│     not tracking a live train
│  Sort [ Departs ][ Price ][ Seats ]│     (FR-2.3 unaffected)
│ ──────────────────────────────────│
│ ┌────────────────────────────────┐ │
│ │ 12779 Goa Express              │ │
│ │ 15:20 → 09:45  (18h 25m)       │ │
│ │ 3A ▓AVL 42▓  ₹1,845            │ │
│ │ Usually 20m late ◐             │ │  ← FR-6.2 punctuality
│ └────────────────────────────────┘ │
│ ┌────────────────────────────────┐ │
│ │ 22413 Rajdhani                 │ │
│ │ 3A ▓WL 8▓ 34% ◐   ₹2,190       │ │
│ │ ⟳ loading seats…               │ │  ← FR-6.2 progressive,
│ └────────────────────────────────┘ │     row never blocks
└────────────────────────────────────┘
```

**Reasoning.** This is the one genuinely *deliberative* screen — the user is seated and comparing, so density is correct here where it would be wrong elsewhere. Rows hydrate progressively and never block on the slowest call (FR-6.2); a pending row shows a spinner in place rather than holding the list.

A date field is legitimate here and **only** here: FR-2.3 forbids asking for a date to *track a live train*, which is a different act from planning a future trip. The day-scrubber makes date changes a swipe rather than a modal.

Expanding a row reveals the full fare breakdown in place (FR-6.3). Quota selection (FR-6.4) is a sheet with human labels, not codes.

---

## 3.11 W11 — You (controls)

**Core goal:** alert governance and trust, in one place. FR-7.4, FR-10.1, FR-11.3.

```
┌────────────────────────────────────┐
│  You                               │
│ ──────────────────────────────────│
│  ⟳ Watching 3 journeys             │  ← proof the Watcher is alive
│                                    │
│  ALERTS                            │
│  Chart prepared            [ on ]  │
│  Delays over        [ 15 min  ▾ ]  │
│  Platform changes          [ on ]  │
│  Cancellations             [ on ]  │
│  Quiet hours      [ 22:00–07:00 ]  │
│                                    │
│  Language              [ हिन्दी ▾ ] │  ← FR-10.1, no state loss
│  Sunlight mode            [ auto ] │  ← FR-5.3
│                                    │
│  Privacy & your data          ›    │
│  Alarms not arriving?         ›    │  ← OEM battery guidance
│                                    │
│  Not affiliated with Indian        │  ← FR-11.1
│  Railways.                         │
└────────────────────────────────────┘
```

**Reasoning.** "Watching 3 journeys" is the most important line: it is *evidence* that the invisible hero feature is running. Alert types are individually switchable per FR-7.4's opt-in requirement, with quiet hours adjacent. The OEM troubleshooting entry is surfaced rather than buried, because on Xiaomi/Oppo/Vivo it is the difference between the alarm firing and not (PRD §12 Q3).

---

## 3.12 W12 — Onboarding (one decision)

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Railcast    │  │ What first?  │  │  [ input ]   │
│              │  │              │  │              │
│  English     │  │[Track train] │  │  → straight  │
│  हिन्दी        │  │[Check PNR ] │  │    to value  │
│  বাংলা        │  │[Near me   ] │  │              │
│  ...native   │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
     language          intent           the answer
```

Native-script language names (a user who needs Hindi cannot read "Hindi"). One intent question. No carousel, no permission wall, no login (FR-10.5). Permissions are requested in context, with a reason, at the moment of use (FR-5.2).

---

## 3.13 W13 — System states (one vocabulary, every screen)

```
LOADING            OFFLINE             ERROR              EMPTY
┌────────────┐  ┌────────────────┐  ┌──────────────┐  ┌────────────┐
│ ▒▒▒▒▒▒     │  │ ⚠ Cached 14:02 │  │ Couldn't     │  │ No journeys│
│ ▒▒▒▒▒▒▒▒▒  │  │ ──────────────│  │ reach the    │  │ yet.       │
│ ▒▒▒▒       │  │ (full content, │  │ railways.    │  │            │
│            │  │  values ◐)     │  │ [ Retry ]    │  │ [ Track a  │
│ skeleton = │  │                │  │              │  │   train ]  │
│ final      │  │ degrade,       │  │ plain words, │  │            │
│ layout     │  │ never block    │  │ never blames │  │ directive  │
└────────────┘  └────────────────┘  └──────────────┘  └────────────┘
```

Skeletons match the final layout exactly so nothing reflows on arrival. Offline shows *real cached content* with a strip and `◐` on every value — never an error page (FR-9.1). Errors are plain-language with a next step and never blame the user (§7).

---

## 3.14 Ergonomics

```
┌──────────────┐   All primary actions sit in the
│   read-only  │   bottom third. Nothing requiring
│   ─────────  │   a decision lives in the top
│   stretch    │   corners. Destructive actions
│ ┌──────────┐ │   (delete, mute) are never in the
│ │  THUMB   │ │   thumb arc — deliberate friction.
│ │   ARC    │ │
│ └──────────┘ │   Targets ≥48dp (FR-10.3).
└──────────────┘   Tested at 200% font scale.
```

---

## 4 · What this eliminates

Being ruthless, per the brief:

| Removed | Why |
|---|---|
| Two tabs (5 → 3) | Track is what a journey *is*; Alerts is a *property* of one |
| The separate Alerts screen | Alerts appear in the journey thread as their own receipt |
| The separate journal/history screen | The thread *is* the history |
| Mode toggles (train / PNR / station) | The omni-input classifies by shape |
| The run-date question | Server probes and resolves (FR-2.3) |
| Any "open the app to check" loop | The ambient layer answers before intent forms |
| Tutorial carousel, permission wall, login gate | FR-10.5 |
| Separate coach-guide destination | Reached in context, from the stop it concerns |

---

## 5 · Open questions before Phase 2

1. **Ambient reliability testing.** Widget cadence and lockscreen persistence need a real device matrix (Xiaomi/Oppo/Vivo) before we commit to ambient-first as the *primary* surface. This is the winner's weakest joint and it is currently untested — it should gate full adoption, not follow it.
2. **PRD §7 amendment.** The three-tab structure needs to be written into the PRD in the same PR that implements it.
3. **Consequence computation.** "You reach Bhopal ~16:49" requires knowing the user's boarding/alighting station. Available from PNR; needs an explicit lightweight prompt for train-only watchers.

---

## 6 · Phase 2 (on approval of these wireframes)

Per the brief's own sequence — *"after the wireframes are finalized"* — Phase 2 delivers: the complete design system, component library, typography scale, colour palette, spacing system, iconography, motion language, haptic guidelines, and full WCAG 2.2 accessibility specification. The `Semaphore` stage-0 tokens already committed (`android/.../design/Semaphore.kt`) are a viable starting substrate and survive the direction change intact.

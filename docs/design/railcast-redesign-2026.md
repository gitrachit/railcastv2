# Railcast — The Companion Redesign

**A first-principles rebuild of the entire product experience**
Version 1.0 · July 2026 · Design lead brief · Android-first, platform-agnostic in spirit

> This document assumes the current Railcast UI never existed. It uses the shipped codebase and docs only to learn *what the product must do* and *what it may not violate* — never as a visual or structural baseline. Every functional requirement (FR-x), every invariant (PNR masking, no-raw-dates, icon+word+colour, server-only API key, no-ads/no-dark-patterns), and every technical fact (composed `/screen/*` BFF, shared cache, server-side Watcher, `PollController`, `Resource<T>` SWR, offline directory, contract-locked types) is preserved. Everything above that line is rebuilt from zero.

**The thesis, in one sentence:**

> A railway journey is a single anxiety that lasts hours. Railcast should be a companion that holds that anxiety for you — opening straight to the one answer you need, telling you what it *means*, and being honest about how much it knows.

Every decision below serves that sentence. Rankings use the repo's convention: **UI** = User Impact, **DC** = Dev Complexity, **BV** = Business Value, each High/Med/Low.

---

## Table of contents

1. [Complete UX Audit](#1--complete-ux-audit)
2. [Product Strategy](#2--product-strategy)
3. [New Information Architecture](#3--new-information-architecture)
4. [Navigation Flow](#4--navigation-flow)
5. [Screen-by-Screen Redesign](#5--screen-by-screen-redesign)
6. [Component Library — "Semaphore"](#6--component-library--semaphore)
7. [Design Tokens](#7--design-tokens)
8. [Typography System](#8--typography-system)
9. [Color Palette](#9--color-palette)
10. [Motion Guidelines](#10--motion-guidelines)
11. [Accessibility Guidelines](#11--accessibility-guidelines)
12. [Jetpack Compose Design System](#12--jetpack-compose-design-system)
13. [Future Scalability](#13--future-scalability)
14. [The Top 25 Changes](#14--the-top-25-changes)

---

# 1 · Complete UX Audit

The audit is not a critique of pixels. It is a critique of *structure* — because a railway companion lives or dies on whether its architecture matches how a frightened, rushed, or tired person actually thinks. I evaluated the product against fifteen lenses (core problem, cognitive load, hierarchy, interaction, motion, states, accessibility, discoverability, ergonomics, honesty, offline, latency, emotional register, glanceability, and lifecycle-fit). Ten structural findings emerged.

### Finding 1 — A menu is not an answer

Any railway app that opens to a home screen of tools, tabs, or a search box has already failed its most common user: the returning passenger whose train is *right now*, who wants one number (how late) and one instruction (which platform), and who is holding a phone one-handed on a moving platform. Opening to a menu forces a navigation act between the user and their answer. **The correct front door is the answer itself.** The app should resolve its own entry point from journey state, not present a lobby.

### Finding 2 — The journey is the atom; everything else is a molecule

A traveler holds one mental object: *"my train."* It has a number, a name, a run-date, maybe a ticket (PNR), a boarding station, a destination, a coach, and a live state. Any IA that scatters those facets across separate destinations ("track" here, "ticket" there, "platform" in a third place, "alerts" in a fourth) makes the human the integration layer. That is the single largest structural error a travel app can make. The atom must be a **Journey**, and every surface must be a *view onto* a journey, never a sibling of it.

### Finding 3 — Data without consequence is anxiety, not relief

"12 min late" is a fact. It is also useless — worse, it *raises* anxiety, because now the user must do arithmetic under stress: *late by 12, I board at Bhopal, scheduled 16:37, so ~16:49, and my cab is booked for 16:40…* A companion does that math. The unit of a railway UI is not the datum; it is the **consequence**: "You'll reach Bhopal ~16:49 · Platform 4 · alarm set." The interface must render consequence as a first-class line, computed for *this* user, everywhere live data appears.

### Finding 4 — Honesty must be *rendered*, not just written

Railway data is probabilistic. Positions are interpolated, ETAs are predictions, cached values are stale, some fields are simply unknown. Writing "estimated" in small type is table-stakes honesty; it is not enough. Confidence must be a **visual and haptic dimension** — an estimate should *look* estimated (soft-edged, breathing), a stale value should *look* relaxed (desaturated), a certain fact should *look* solid (crisp, board-mono). When the eye can read confidence pre-cognitively, trust becomes structural. No incumbent does this. It is the deepest available moat.

### Finding 5 — The hero feature is invisible

Proactive server-side alerts (chart-prepared at 4 a.m., arrival alarm, cancellation) are the product's reason to exist. Yet a push notification is ephemeral: swipe it away and the proof is gone. A product whose core promise is *"I was watching your train so you didn't have to"* must keep a **receipt** — a journal of everything it observed and sent, and visible evidence that it is *actively watching right now*. Without that, the hero feature is a magic trick the user can't verify, and unverifiable magic erodes trust instead of building it.

### Finding 6 — "Stand here" is the most shareable answer in the category and deserves to be a landmark

Telling an unreserved traveler where the GEN coaches stop, or a family where coach B4 will halt on a 22-coach platform — front, middle, or rear, *accounting for a rake reversal at Itarsi* — is the single most valuable, most screenshot-worthy answer Railcast can give. It is buried mid-scroll as a row of tiles in most designs. It should be a **platform-anchored diagram with the answer in words first**, elevated to a signature moment.

### Finding 7 — Modes are where errors live

Forcing a user to declare the *type* of their query before typing it ("Train or PNR?") is asking the human to do classification the code already does (5 digits = train, 10 = PNR). Multiple search fields for one directory multiply muscle memory and inconsistency. There must be **one input** that accepts anything — number, name, code, PNR, "ndls to bpl", a pasted SMS — and classifies intent itself.

### Finding 8 — The bad day is the brand, and it is usually undesigned

Cancellation, diversion, waitlist, dead signal, wrong-date-at-1 a.m.: these are the moments users switch apps forever, in either direction. They are also the moments most apps render as a dead-end or a raw error. Every bad-day state must be a **first-class, rehearsed flow** with its own copy, motion, haptic register, and — critically — a *rescue action* (cancellation → prefilled alternatives with seats). Designing the sunny day is the mistake every competitor makes.

### Finding 9 — Ergonomics are an accessibility requirement, not a nicety

The core persona includes elderly, vernacular-first, low-literacy, one-handed, in-sunlight, on-a-cheap-phone users. Anything fact-critical in the top third of the screen, any target under 48dp, any colour-only status, any hidden gesture for a core action, any layout that breaks at 1.3× font scale — each is a *failure*, not a polish item. The reachable thumb arc (bottom 40%) is where the product actually lives.

### Finding 10 — Calm is a feature with a mechanism

"Calm" is not a mood board; it is the product's structural answer to the hostility of ad-ridden incumbents. It has to be engineered: a notification budget (never more than one non-urgent push per journey per hour), a single accent colour used almost never, exactly one theatrical motion, no badges (a quiet dot instead of a red number), and a rule that every addition must make the answer *faster or calmer* or it doesn't ship. Calm is load-bearing; treat it as a requirement with acceptance criteria.

### Audit scorecard (the seven primary questions × how fast the *ideal* app answers them)

| The user's real question | Ideal answer latency | Where the redesign puts it |
|---|---|---|
| Where is my train? | 0 taps (app open) | Live Journey entry-point + map marker |
| How late is it? | 0 taps | Board hero, line 1 (the fact) |
| Which platform? | 0 taps | Board hero, line 2 (the consequence) |
| Chart prepared? | 0 taps (push) / 1 tap | Journal + pass chip + push |
| Where is my coach? | 1 tap | "Stand Here" diagram |
| When will I arrive? | 0 taps | Consequence line ("~16:49") |
| What do I do next? | 0 taps | Action dock (state-chosen) |

The design goal is that **six of seven are answered before a single tap**, on app open, for the returning user with an active journey.

---

# 2 · Product Strategy

### 2.1 The north star

**Eliminate journey uncertainty.** Not "display train data." Every metric, every screen, every animation is judged by whether it moved a user from *uncertain* to *confident* faster and more calmly than the moment before. This reframes the entire product from a *data browser* to an *anxiety instrument*.

### 2.2 Nine first principles (derived from the personas and the §2 metrics, not from aesthetics)

1. **The journey is the object.** One `Journey` entity; every surface is a lens on it. (Finding 2)
2. **Open to the answer, not the app.** The entry point is resolved from state. (Finding 1)
3. **Render the consequence, not the datum.** Fact → what it means for *you* → next action, in fixed order, everywhere. (Finding 3)
4. **Confidence is a visual dimension.** Certain / Estimated / Stale / Unknown each have a look, a motion, and a haptic. (Finding 4)
5. **Classify for the user, never ask them to.** One omni-input. (Finding 7)
6. **The bad day is the brand moment.** Every failure state is a designed, rescued flow. (Finding 8)
7. **Prove the magic.** The Watcher is made visible: a journal, and a "watching now" indicator. (Finding 5)
8. **Design for the glance, engineer for the wait.** Two reading modes — 2-second glance (platform, sunlight, one-handed) and 20-minute dwell (on the train). The glance always wins conflicts. (Finding 9)
9. **Calm is engineered.** Notification budget, one accent, one theatrical motion, no badges, a "faster-or-calmer" ship gate. (Finding 10)

### 2.3 What "premium" means here — principles borrowed, never visuals

| From | Principle taken | Where it lands in Railcast |
|---|---|---|
| **Flighty** | The trip is a living object; push the *delta*, not the data | Journey object; Journal; Live Updates |
| **Apple Wallet** | A ticket is a pass — one card, legible at arm's length, zero chrome | PNR pass |
| **Nothing OS** | Monochrome confidence; dot-matrix numerals as identity; accent used almost never | Board surface; type ramp; restrained accent |
| **Google Maps** | Progressive disclosure by zoom: overview → corridor → stop | Sheet-over-map detents; spine collapsing |
| **Uber** | During a live trip, the trip *is* the home screen | State-resolved entry point (taken further than Uber) |
| **Linear** | Speed is the feature; one keyboard-fast input; zero decorative chrome | Omni-input; 60fps budget |
| **Citymapper** | Context makes the UI — near a station, *be* the station app | Context band |
| **Tesla** | One canvas, layered panels, no "pages" | Sheet-over-map Journey surface |
| **Apple (Live Activities)** | The live thing follows you out of the app | Live Updates ongoing notification, lock-screen, widget |
| **Airbnb** | Warmth and trust through typography and whitespace, not ornament | Paper surface; humanist type; generous spacing |

### 2.4 The one genuinely new idea: the **Confidence System**

This is the strategic differentiator. Railway data is never fully trustworthy, and every competitor pretends otherwise. Railcast will instead make *its own certainty* legible. Four confidence levels, each a token bundle (colour treatment + edge + motion + copy prefix + haptic + TalkBack phrasing):

```
CERTAIN    crisp board-mono, full colour, no motion, "Platform 4"          — from live upstream
ESTIMATED  soft edge, breathing pulse, "~" prefix, "~16:49"                — interpolated / predicted
STALE      desaturated 20%, muted freshness dot, "6 min ago · offline"     — cache past TTL
UNKNOWN    hairline placeholder, no guess, "Platform — not yet posted"     — data absent (never faked)
```

The rule that makes it a *system*: **no value ever renders without a confidence level attached.** A number with no confidence treatment is a bug. This single discipline converts the product's honesty from copy into architecture, and it is impossible to copy without rebuilding from the same principle.

### 2.5 Success metrics this strategy moves (mapped to the PRD's §2)

| PRD metric | Strategic lever |
|---|---|
| Time-to-answer ≤ 5 s, ≤ 2 taps | State-resolved entry point → often **0 taps** |
| First-session unaided success ≥ 85% | Omni-input (no mode error) + one-question onboarding |
| 14-day return ≥ 45% | Journey stack + Live Updates habit loop |
| Shared-link installs ≥ 15% | Sheet-over-map as the shareable artifact + web parity |
| "Wrong info" reports < 2/1k | Confidence System (honest by construction) |
| Alert delivered ≤ 5 min ≥ 95% | (Backend already sized; UI makes it *provable* via Journal) |

---

# 3 · New Information Architecture

### 3.1 The Journey object (the keystone)

A **Journey** is a client-side entity composed from data that already exists — no contract change required. It is a lifecycle state machine, and that state drives *everything*: card size, primary action, poll cadence, notification posture, and entry-point priority.

```
Journey
├─ train        number · name · run-date        (run-date probe, FR-2.3)
├─ pnr?         masked · passengers · chart      (FR-4.1–4.3)
├─ myStations?  boarding · alighting             (from PNR, or user-set — powers "your" consequence)
├─ coach?       order · reversals · GEN          (FR-3.x)
├─ confidence   CERTAIN | ESTIMATED | STALE | UNKNOWN   (per field + a rolled-up journey level)
├─ state        upcoming → boarding → riding → arriving → completed
│               ⚠ orthogonal flags: delayed · diverted · cancelled · waitlisted
└─ watches      chart · delay · platform · cancel · arrival · tatkal · mute   (Watcher jobs, FR-7.x)
```

Consequences of making this the atom:

- **Saving is one verb.** "Save a train" and "save a PNR" both produce a Journey. Saving a PNR *implies* its train; tracking a train can later *absorb* a PNR ("Is this your train? Attach your ticket").
- **State drives the UI, deterministically.** `upcoming` leads with departure + chart; `riding` leads with position + delay; `arriving` leads with the alarm and "Stand Here"; `completed` quietly asks "was the info right?" (FR-11.2) then archives.
- **The seven questions become auditable slots.** Where / how-late / platform / chart / coach / arrival / next each map to a field on the Journey card or its detail — so the design can be tested against the PRD slot by slot.

### 3.2 The radical move: a **state-resolved entry point** (no fixed home screen)

Most apps have a fixed home. Railcast does not. On launch, the app computes the highest-priority journey state and *opens to the surface that answers it*:

```
        ┌─────────────────────────────────────────────┐
        │  App launch → resolve entry point            │
        └─────────────────────────────────────────────┘
                          │
   ┌──────────────────────┼───────────────────────────┐
   │                      │                            │
0 active journeys    exactly 1 active            2+ active journeys
   │                      │                            │
   ▼                      ▼                            ▼
INVITATION            LIVE JOURNEY                 JOURNEY STACK
(omni-input,          (that journey's              (cards, most-urgent
 calm, first-run      full surface —               first; tap → surface)
 or lapsed user)      the app IS the train)
```

Why this beats a fixed home (Finding 1): for the 80% case — one train you care about, running now — the app *is already showing the answer* with zero navigation. Uber does this for one ride; Railcast generalizes it to a lifecycle with 0/1/N resolution. The Journey Stack (the "home list") still exists — it is simply the *N-case*, not a mandatory lobby. A "⌂" affordance always returns to the Stack so the user is never trapped in a single journey.

> **Trade-off & mitigation.** A shifting entry point can feel unpredictable. Mitigation: the transition is always a *shared-element continuation* (the live card you'd see in the Stack is the same element that becomes the full surface), and the Stack is one tap away via the persistent home affordance. The entry point changes, but the *visual grammar* is constant — so it reads as intelligence, not instability. Verified against the "no hidden state" principle: the home affordance and the tab bar are always visible.

### 3.3 Three destinations, derived from three intents

Every task a user brings reduces to one of three verbs. These become the only three tabs — the minimum that keeps "nothing important deeper than two taps" while making every tab open to *state*, never an empty form.

```
NOW      "How is my journey?"     → Journeys   (stack / live journey — the state-resolved surface)
FIND     "Show me a thing."       → Search     (one omni-input over everything)
PROOF    "What happened? Settings" → Activity   (journal + all controls)
```

### 3.4 Full functional traceability (nothing is lost)

| Every capability today | Lives here in the redesign | FR |
|---|---|---|
| Saved trains list | Journeys → stack | FR-2.5, §2 habit |
| Live tracking | Journey surface (from any card/result) | FR-2.1–2.7 |
| Train/PNR mode toggle | Omni-input classifier | FR-1.5 |
| Coach guide + GEN | Journey surface → "Stand Here" | FR-3.1–3.3 |
| PNR screen | PNR pass inside Journey; lookup via Search | FR-4.1–4.5 |
| Station board | Station surface (Search → station / "Near me" / context band) | FR-5.1–5.3 |
| Journey planning | Plan surface (Search → route intent) | FR-6.1–6.4 |
| Alerts toggles / quiet hours / language / privacy | Activity → Controls | FR-7.4, FR-10.1, FR-11.3 |
| Arrival alarm setup | Journey action dock + Activity | FR-7.3 |
| Share journey | Journey overflow + Home-card swipe + web `/t/:token` | FR-2.7, FR-8 |
| Mute | Journey overflow + card swipe + Activity row | FR-7.4 |
| — (never existed) | **Alert journal** (proof of the hero feature) | FR-7.x made visible |
| — (never existed) | **Confidence System** (honesty rendered) | FR-2.2, FR-11.1 |
| — (never existed) | **Live Updates** ongoing notification | FR-7.x, NFR-3 |

### 3.5 IA at a glance

```
Railcast
├─ [state-resolved entry]
│    ├─ Invitation      (0 journeys)
│    ├─ Live Journey    (1 active)   ─┐
│    └─ Journey Stack   (2+)          │ all three reachable via the Journeys tab
│
├─ TAB: Journeys ───────────────────┘
│    └─ Journey surface  (detail; sheet-over-map)
│         ├─ Board hero (fact · consequence · confidence · freshness)
│         ├─ Spine (unified timeline)
│         ├─ Stand Here (coach diagram)
│         ├─ PNR pass (when attached)
│         └─ Action dock (state-chosen primary + overflow)
│
├─ TAB: Search
│    ├─ Omni-input (classifier: train/PNR/station/route/paste)
│    ├─ Zero-state: Near me · Plan A→B · recents
│    ├─→ Station surface
│    └─→ Plan surface (day scrubber, quota sheet, sort-settle)
│
└─ TAB: Activity
     ├─ Journal (per-journey feed of every alert observed/sent)
     └─ Controls (alert types · quiet hours · language · privacy · OEM guide)
```

---

# 4 · Navigation Flow

### 4.1 The shell

A three-destination bottom bar (48dp+ targets in the thumb arc), plus detail surfaces that push *above* the bar (never new tabs). The bar hides on the full-map detent and returns on scroll — content is king on the live surface.

```
┌───────────────────────────────────────────┐
│                                           │
│   content: state-resolved surface, or     │
│   a detail surface pushed above the bar   │
│                                           │
├───────────────────────────────────────────┤
│   ◫ Journeys     ⌕ Search     ◈ Activity  │  ← 3 tabs, always labelled (FR-10.3)
└───────────────────────────────────────────┘
```

### 4.2 The six canonical flows (with tap counts)

**Flow 1 — returning user, train running (the 80% case)**
`Open → Live Journey is already the screen → glance done` · **0 taps, < 2 s.**
Drag the sheet up for the timeline; tap "Stand Here" for coach. Compare to a fixed-home app: open → find card → tap → wait → scroll.

**Flow 2 — first-time user, "where's my train"**
`Open → language (1 tap) → Invitation → type/speak "goa express" → result → Journey surface → (if ambiguous) "Started yesterday?" evidence card → answer.`
Two onboarding questions removed; same taps-to-answer as a legacy app but one fewer *decision*.

**Flow 3 — PNR in hand (paper ticket, low-tech user)**
`Search → type 10 digits → field morphs to grouped PNR (439 2456 789) and self-classifies → pass card → "Save & watch" → Journey created (chart watch armed).`
Pasting an IRCTC SMS containing a PNR into the same field extracts it (FR-4.5).

**Flow 4 — at the station, rushing (P3)**
`Open → context band: "📍 You're at Itarsi Jn — Live board" → 1 tap → board in sunlight mode.`
Or from a push: "Platform 4 for 12952" → deep-link straight to the Journey.

**Flow 5 — the bad day (cancellation)**
`Push "12952 cancelled" → Journey opens in cancelled state: red board, plain words, one primary action "Find trains on this route" → Plan prefilled (route + date + class from the PNR) → alternatives sorted by seats.`
The Journal keeps the cancellation entry with a timestamp — proof of *when* the user knew, to show a TTE or family.

**Flow 6 — planning the return leg**
`Search zero-state → "BPL → NDLS · recent" chip → 1 tap → Plan with route reversed → day scrubber to Sunday → results cross-fade in place.`

### 4.3 Deep-linking & back-stack

Every notification deep-links to its exact surface (Journey / pass / board) and is journaled. Standard Android back-stack-per-tab (`popUpTo(start) + launchSingleTop + restoreState`); the run-date and pass sub-surfaces are nested routes, not tabs, so the bar always shows exactly the three real destinations.

---

# 5 · Screen-by-Screen Redesign

ASCII wireframes are lo-fi: `▓` = Board (dark live-truth surface), `·` = whitespace, `[ ]` = tappable, `◐` = estimated (breathing) marker, `★` = your stop.

## 5.0 The universal answer skeleton

Every live surface renders the same three-line contract, in the same order — learn one screen, you've learned them all. This is the shared spine of the whole system.

```
┌───────────────────────────────────────────┐
│  THE FACT           (board-mono, largest)  │  "▶ 12 min late"
│  THE CONSEQUENCE    (for-you line)         │  "You reach Bhopal ~16:49 · Platform 4"
│  ● confidence + freshness                  │  "live · updated just now"
├───────────────────────────────────────────┤
│  [ THE ONE ACTION ] (state-chosen)         │  "Set arrival alarm"
├───────────────────────────────────────────┤
│  detail, progressively disclosed           │
└───────────────────────────────────────────┘
```

The consequence line is computed, personal, and honest: it uses `myStations` when known ("**your** arrival"), the terminus otherwise; estimates carry `~`, scheduled facts don't; confidence treatment applies per value. This is the answer to Finding 3, and it is generated *once* (§12) and reused by screen, notification, widget, and TTS.

---

## 5.1 Journeys — the state-resolved surface

### 5.1a Invitation (0 journeys — first run or lapsed)

```
┌─────────────────────────────────────────┐
│                                         │
│              ◫  Railcast                │  ← quiet wordmark, no chrome
│                                         │
│         🚆 (calm single-line art)        │
│                                         │
│     Track any train in India — live.    │
│                                         │
│   ┌───────────────────────────────────┐ │
│   │ ⌕  Train, station, or PNR      🎙 │ │  ← the invitation IS the omni-input
│   └───────────────────────────────────┘ │
│                                         │
│   [ 📍 Trains near me ]                  │
│                                         │
│   No ads. No login. Your ticket stays   │  ← brand promise, once, on first run only
│   private.                              │
├─────────────────────────────────────────┤
│   ◫ Journeys     ⌕ Search     ◈ Activity│
└─────────────────────────────────────────┘
```

Directive, ≤ 2 taps to any first answer, voice first-class (FR-1.4). **UI H · DC L · BV H** (first-session success is a §2 metric).

### 5.1b Live Journey (exactly 1 active) — *the app is the train*

This is the state-resolved hero: the app boots straight into the one journey that matters, at its GLANCE detent (see 5.3 for the full sheet-over-map anatomy). No list, no navigation — the answer, immediately.

```
┌─────────────────────────────────────────┐
│  ⌂                    12952 · Tejas  ⋯  │  ← ⌂ returns to Stack; ⋯ overflow
│ ╭ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╮  │
│        route canvas (muted)             │  ← polyline + ◐ estimated marker (breathes)
│ ·           ◐ ~estimated                │
│ ╰ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╯  │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓ ▶ 12 min late                    ●  ▓ │  ← THE FACT (flap-roll, board-mono)
│ ▓ You reach Bhopal ~16:49 · Plat 4    ▓ │  ← THE CONSEQUENCE
│ ▓ live · updated 40s ago              ▓ │  ← confidence + freshness
│ ▓ NDLS ●━━━━━━━━◐───────○ BPL         ▓ │  ← mini-spine
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ [ 🔔 Set arrival alarm ]                │  ← the one action (state = riding, no alarm)
│ ───────────── drag up ─────────────────│
├─────────────────────────────────────────┤
│   ◫ Journeys     ⌕ Search     ◈ Activity│
└─────────────────────────────────────────┘
```

### 5.1c Journey Stack (2+ journeys)

```
┌─────────────────────────────────────────┐
│  Journeys                        ⌕      │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓ 12952 · Tejas Rajdhani     LIVE ●   ▓ │  ← hero: most-urgent journey, Board surface
│ ▓ ▶ 12 min late                       ▓ │
│ ▓ You reach Bhopal ~16:49 · Plat 4    ▓ │
│ ▓ NDLS ●━━━━━━◐─────○ BPL   40s ago    ▓ │
│ ▓ [ 🔔 Set arrival alarm ]             ▓ │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ┌─────────────────────────────────────┐ │
│ │ ••••2882 · Sat 26 Jul   ⏳ Waiting  │ │  ← compact: upcoming journey
│ │ 12622 Tamil Nadu Exp · chart ~21:00 │ │     (chart status leads)
│ └─────────────────────────────────────┘ │
│ 📍 You're near Itarsi Jn — [Live board] │  ← context band (situational, dismissible)
│ ⌕  Track a train, PNR, or station…      │  ← docked omni-input (thumb zone)
├─────────────────────────────────────────┤
│   ◫ Journeys     ⌕ Search     ◈ Activity│
└─────────────────────────────────────────┘
```

**Rules of the Stack:** (1) **Urgency sorts** — `riding/arriving` > `boarding` > today's `upcoming` > future > `completed` (archived after 24 h). Cancelled/diverted jump to top in signal colour. (2) **State sizes** — the top active journey is the Board hero; the rest are compact. (3) **The card *is* the Journey surface's first line** — identical component, so tapping through is a shared-element continuation, not a page load. (4) **One context band, max** (Citymapper), dismiss-and-stay-dismissed. (5) **Gestures as accelerators:** swipe-left Mute/Unsave (undo snackbar), swipe-right Share; all also in the `⋯` menu (no gesture-only core action).

| Aspect | UI | DC | BV |
|---|---|---|---|
| State-resolved entry point | **H** | M | **H** |
| Journey stack + hero | H | M | H |
| Context band | M | M | M |
| Card gestures + undo | M | L | M |

---

## 5.2 Search — the omni-input

**Job:** one field that takes anything and knows what you meant. (Finding 7)

```
┌─────────────────────────────────────────┐
│ [ ⌕  Train, station, PNR, or A to B  🎙 ]│  ← focused on tab open, keyboard up
│  Recent                                 │
│  ↻ 12952 Tejas Rajdhani                 │
│  ↻ NDLS → BPL                    (route)│
│  [ 📍 Near me ] [ ⇄ Plan A→B ] [ 🎫 PNR ]│  ← zero-state shortcuts
└─────────────────────────────────────────┘

 …typing "129"                    …typing 10 digits
┌──────────────────────────┐     ┌──────────────────────────┐
│ [ 🚆 129|            🎙 ] │     │ [ 🎫 439 2456 789|      ] │  ← glyph morphs ⌕→🚆→🎫
│ 🚆 12951 Mumbai Rajdhani  │     │   PNR — 1 digit to go    │     digits grouped,
│ 🚆 12952 Tejas Rajdhani   │     │   (masked after lookup)  │     type named in words
│ (stations suppressed —    │     └──────────────────────────┘
│  pure digits = train)     │
└──────────────────────────┘
```

**Classifier** (pure Kotlin over the existing `DirectorySearch` + `FormatValidation`, all offline):

| Input shape | Detected as | Surface |
|---|---|---|
| 5 digits | Train number | Journey surface (run-date probe if needed) |
| 10 digits | PNR | PNR pass (masked on resolve) |
| Text | Fuzzy trains + stations (weighted rank) | Result rows, 🚆/🚉 glyph + word |
| `X to Y` / `X → Y` / two station hits | Route intent | Plan surface, prefilled |
| Pasted text with a 10-digit run | PNR extraction (FR-4.5) | "Found PNR ••••2882 — check it?" |

The leading glyph morphs (⌕→🚆→🎫) and the type is *named* in the helper line (icon+word, FR-10.2) so the classifier teaches its own existence. A demoted "Search stations for '43924…' instead" row is always present — classification is a default, never a cage.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Omni-input + classifier | **H** | M | **H** |
| Field morph + inline count | M | L | M |
| Route-intent parse | M | M | M |
| Paste-to-extract PNR | M | L | M |

---

## 5.3 The Journey Surface — sheet over map (Track + PNR, merged)

**Job:** everything about one journey, layered by how far you'll scroll. It replaces both the old Track and standalone PNR. The map is the **canvas**; content is a **sheet** at three detents (Tesla/Maps grammar), finally realizing FR-2.2 and giving the shared web view its visual.

```
   Detent 1 — GLANCE (default)          Detent 2 — TIMELINE (drag up)
┌─────────────────────────────┐      ┌─────────────────────────────┐
│  ⌂  12952 Tejas Rajdhani  ⋯ │      │  ⌂ 12952 · ▶ 12 late  ●    │ ← board condenses
│ ╭ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╮ │      │    into a one-line header   │   fact persists
│      route canvas (muted)   │      ├─────────────────────────────┤
│ ·      polyline             │      │  ◐ You are here ~between     │
│ ·         ◐ ~estimated      │      │    Itarsi and Hoshangabad   │
│ ╰ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╯ │      │  │                          │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │      │  ● Itarsi Jn      16:02 ✓  │
│ ▓ ▶ 12 min late        ●  ▓ │      │  │ 15:50→16:02 · Plat 3    │
│ ▓ Bhopal ~16:49 · Plat 4  ▓ │      │  ◐ ─ ─ (you) ─ ─ ─ ─ ─ ─   │
│ ▓ Started yesterday       ▓ │      │  ○ Hoshangabad   ~16:31    │
│ ▓ live · 40s ago          ▓ │      │  │  ETA · Plat 1           │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │      │  ★ BHOPAL        ~16:49    │ ← YOUR stop:
│  NDLS ●━━━━━◐────○ BPL      │      │  │  your stop · alarm set  │   emphasized
│ [ 🔔 Set arrival alarm ]    │      │  ▾ 6 more to terminus      │
│ ─────────── drag ───────────│      └─────────────────────────────┘
└─────────────────────────────┘
```

**Detent 3 — FULL MAP** (drag down): map fills the screen; the estimated marker breathes; "estimated position" label pinned to it, always (FR-11.1). The bottom bar hides here.

**The board, upgraded.** Line 1 = the fact (flap-roll, signal-coloured, icon+word: `▶ 12 min late` / `⏸ Not started` / `⛔ Cancelled`). Line 2 = the consequence, computed for `myStations`, confidence-treated ("usually Platform 4" when from history — FR-2.6/11.1). Line 3 = run-date chip + amber diversion/reschedule banner + confidence/freshness. The **run-date probe** becomes two large evidence cards ("Left NDLS 17:55 yesterday · now near Itarsi" vs "Departs NDLS 17:55 today") — recognition, not recall (FR-2.3).

**The spine (timeline reborn).** One vertical line is the single spatial truth — the old progress line and flat stop-list merge. Past stops solid `●` with actual times (scheduled struck in ink3, actual in ink, mono); future `○` with `~`ETAs; **you-are-here** `◐` sits *between* nodes at the interpolated fraction, breathing (the only ambient motion). **Your stops** get `★` + the word "your stop" + a brand tint bar. Day boundaries are inline pills ("— Day 2 —"). Long routes collapse (`▾ 6 more stops`, Google-Maps disclosure). Times/platforms live in a fixed-width mono column (the ISO-width bug class is fixed by construction).

### "Stand Here" — the signature coach guide (Finding 6)

```
│  Your coach B4 stops near the FRONT      │  ← the answer, in WORDS, first
│  ▶ train approaches this way             │
│  ═══════════════════════════════════     │  ← platform edge
│  [ENG][A1][B4★][B3][SL][GEN][GEN]        │  ← B4 highlighted
│   front ·············· middle ····· rear │
│  ↺ Reverses at Itarsi — after that your  │
│    coach moves to the REAR       (FR-3.2)│
│  [ ⛃ Show GEN coaches ]                   │  (FR-3.3)
└──────────────────────────────────────────┘
```

Words first (P5/low-literacy read the sentence), diagram second. "Stand here" is the single most screenshot-shared answer in the category — it earns the visual investment and a `success` haptic on open.

### PNR as a pass (Apple Wallet grammar)

```
│ ┌───────────────────────────────────┐  │
│ │ 🎫 ••••2882          ✅ CNF · CHART│  │  ← masked always; chart chip
│ │ NDLS → BPL · Sat 26 Jul · 3A      │  │
│ │ ─ ─ ─ ─ ─ perforation ─ ─ ─ ─ ─ ─ │  │
│ │ P1  B4 · 32 · Lower       ✅ CNF  │  │
│ │ P2  B4 · 35 · Side Upper  ⚠ WL 4  │  │  ← per-passenger (FR-4.1)
│ │      ~72% likely to confirm       │  │  ← labelled prediction (FR-4.4)
│ └───────────────────────────────────┘  │
│  Shown masked · encrypted · deleted     │
│  after your journey →            (FR-4.3)│
```

Chart states: `⏳ Waiting · usually ~21:00` → `✅ Chart prepared` (green flap-roll + `confirm` haptic + one 600ms shimmer, then done — the celebration also lands in the Journal and the push, never only on-screen). Poor waitlist odds surface "confirmed alternatives on this route" inline (Plan invoked in place). A standalone PNR lookup is this pass + one action: **Save & watch** (which creates the Journey).

### Contextual action dock (the app ranks, so the user doesn't)

| Journey state | Primary action | Why |
|---|---|---|
| upcoming, no chart | 🔔 Watch this journey | The save *is* the alert opt-in |
| upcoming, chart window | 🎫 Chart status | The hour's real question |
| riding, alarm unset | ⏰ Set arrival alarm | The keep-installed feature (FR-7.3) |
| riding, alarm set | ✓ Alarm set · edit | Confirmation posture |
| arriving | 🧳 Get ready — coach B4, front | The final answer |
| cancelled | → Find alternatives | The bad-day rescue (FR-2.4) |

Overflow: Share, Mute, Report wrong info (FR-11.2), Unsave. One primary + one `⋯` replaces the old three equal buttons — the app knows the state, so it ranks.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Sheet-over-map + estimated marker | **H** | H | **H** |
| Consequence line | **H** | M | H |
| Unified spine | H | M | M |
| "Stand Here" coach guide | H | M | **H** |
| PNR pass absorption | H | M | H |
| Contextual action dock | M | L | M |

---

## 5.4 Station board — a real departure board in your pocket

**Job:** "what's leaving, which platform," in sunlight, one-handed, in seconds (P3). Full commitment to the board metaphor.

```
┌─────────────────────────────────────────┐
│ ⌂ Itarsi Junction        ☀ Sunlight  ⋯  │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓ 16:25  12952 Tejas Rajdhani      4  ▓ │  ← time · train · PLATFORM
│ ▓        → Mumbai   ✅ on time        ▓ │     mono, large, fixed columns
│ ▓ ──────────────────────────────────  ▓ │
│ ▓ 16:40  12138 Punjab Mail         1  ▓ │
│ ▓        → Firozpur ⚠ 25 min late     ▓ │
│ ▓ ──────────────────────────────────  ▓ │
│ ▓ 17:05  11058 Amritsar Exp        —  ▓ │
│ ▓        ⛔ cancelled  [→ alternatives]▓ │  (FR-2.4)
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ [ Next 4h ▾ ] [ ⚲ 3A · to Mumbai ]  45s│  ← controls BELOW content, in thumb arc
└─────────────────────────────────────────┘
```

Changes: (1) **Board-typographic rows** — departure time + platform in large mono at fixed columns, a ~72dp tap → Journey. (2) **Controls sink below content** — window becomes one `Next 4h ▾` chip; all filters fold into one sheet behind `⚲` with active filters summarised on the chip. (3) **Sunlight mode** (FR-5.3) — a fourth palette (≥7:1), toggle in header, auto-*suggested* (one dismissible chip, never forced) via ambient-light sensor. (4) **Platform-change emphasis** — the platform cell flap-rolls and holds a 10 s amber underline + `warning` haptic if foreground. (5) "Near me" stays permission-on-tap (FR-5.2).

| Aspect | UI | DC | BV |
|---|---|---|---|
| Board-typographic rows | H | L | M |
| Filters → one sheet | M | L | M |
| Sunlight mode | H (P3) | M | M |
| Platform-change flap+hold | M | L | M |

---

## 5.5 Plan — comparison, not form-filling

**Job:** decide between trains on time × price × seats × punctuality (P6) — a *dwell* screen: calm, comparative.

```
┌─────────────────────────────────────────┐
│ ⌂ NDLS → BPL                    [⇄]     │  ← route is the title
│ ‹ Fri 25 · [Sat 26] · Sun 27 · Mon › │  ← day scrubber, not a stepper
│ [ Booking normally ▾ ]                  │  ← quota sheet, human words
│ Sort:  [Departure]  Price  Seats        │
│ ┌─────────────────────────────────────┐ │
│ │ 12002 Shatabdi        06:00 → 13:58 │ │
│ │ 7h 58m · daily        ✅ AVL 42     │ │
│ │ ₹1,240 · CC           usually on time│ │  ← punctuality as words (P2)
│ ├─ tap: fare breakdown expands ────────┤ │
│ │ base 940 · reserv 40 · SF 45 ·      │ │
│ │ GST 65 · total ₹1,240        (FR-6.3)│ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ 12622 Tamil Nadu Exp  22:30 → 06:15 │ │
│ │ 7h 45m · Mon Thu      ⚠ WL 12 ~65%  │ │
│ │ ₹890 · 3A   [🔔 Tatkal opens 10:00] │ │  (FR-6.4)
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

(1) **Route as title, form collapsed** — after the first search the from/to folds into the header + swap; changing route reopens the omni-input route form. (2) **Day scrubber** replaces the `‹ ›` stepper — "what about Sunday?" is one tap, results **cross-fade in place** (never before today, FR-2.3 spirit). (3) **Sort-settle** — rows hydrate fare/seats progressively (FR-6.2); sorting before hydration completes sinks unhydrated rows to a "still checking…" tail with skeletons instead of sorting on nulls, then a quiet "sorted" tick. (4) **Quota in human words** (FR-6.4) — a sheet ("Booking normally / Tatkal — opens 10 AM AC · 11 AM non-AC / Ladies / Senior citizen") with the Tatkal countdown + inline "remind me" watch. (5) **Seats as odds** — "AVL 42" green; "WL 12 ~65% likely" as words when history exists, labelled prediction.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Route-as-title + day scrubber | H | M | M |
| Sort-settle hydration | M | M | M |
| Quota sheet in human words | H (P1/P5) | L | M |
| Recent-route memory | M | L | M |

---

## 5.6 Activity — the journal that makes alerts trustworthy (Finding 5)

**Job:** "what has Railcast done for me?" — the receipt for the hero promise. Journal first, controls second.

```
┌─────────────────────────────────────────┐
│ Activity                                │
│ Today                                   │
│ ● 04:12  ✅ Chart prepared  ••••2882    │  ← tap → Journey (pass)
│ ● 06:30  ⚠ 12952 now 25 min late       │
│ ● 07:02  🔁 Platform changed 4 → 6      │
│ Yesterday                               │
│ ● 21:40  🔔 Watching 12952 (you saved)  │  ← system actions logged too
│ ● now    ◉ Watching 12952 · every ~60s  │  ← the Watcher, made visible
│ ────────────────────────────────────────│
│ Controls                                │
│ Alerts [Chart✓][Delay✓][Platform✓]      │
│        [Cancel✓][Arrival✓][Tatkal✓]     │
│ 🌙 Quiet hours 22:00–06:30              │
│    (arrival alarms still come through)  │  (FR-7.4)
│ 🔕 Muted: 12138 · unmute                │
│ 🈯 Language · 🛡 Privacy · ❓ Not getting│
│    alerts? (OEM battery guide)          │
└─────────────────────────────────────────┘
```

Every push lands here first and stays for the journey's life + 7 days (then purged with the journey, FR-4.3) — dismissing a notification no longer deletes the fact. **"Watching now" entries** make the invisible Watcher visible — converting FR-7.x from magic into evidence. Controls keep every existing setting (toggles, quiet hours with the arrival-alarm exception stated in place, OEM guide, language, privacy). The tab shows a **quiet dot** (no number badge — calm, P5) when entries are unread.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Alert journal | **H** | M | **H** |
| "Watching now" honesty | M | L | H |
| Controls consolidation | M | L | M |

---

## 5.7 Onboarding — one decision

Today's flow asks language, then intent. The intent question is removed: its three answers (Track / PNR / Near me) *are* the Invitation's shortcuts. Instead of asking intent then routing, the Invitation *is* the intent picker — and it stays useful forever instead of being a one-shot survey.

```
Screen 1 — LANGUAGE                     Screen 2 — INVITATION (§5.1a)
┌───────────────────────┐               (the empty-state Journeys tab;
│ Choose your language  │   ─────────►   first keystroke = onboarding done)
│ [English] [हिन्दी]     │
│ [বাংলা] [தமிழ்] …      │  ← native script; re-renders live in the pick
└───────────────────────┘
```

One quiet line under the search on first run only: *"No ads. No login. Your ticket stays private."* — the brand promise, once, at peak skepticism, then never again. Permissions stay in-context (location on "near me", notifications on first save). **UI M · DC L · BV H.**

---

## 5.8 System states — loading, empty, error, offline (one vocabulary)

The SWR discipline (cached-first, never blank) is kept wholesale; the redesign standardises five states, one grammar, every surface. Each state is also a *confidence level* made visible.

| State | Rendering | Copy register |
|---|---|---|
| **Fresh (CERTAIN)** | Full colour · green freshness dot · "just now" | — |
| **Loading (no cache)** | Skeletons matching final layout; board surfaces skeleton in board-dark; never a full-screen spinner | — |
| **Stale/offline (STALE)** | Full data · muted dot · "6 min ago · offline" · signal colours desaturate 20% · one global `OfflineBanner`, not per-card | "Showing last known — we'll refresh when you're back" |
| **Empty** | One line-art illustration · one directive sentence · one action | "No journeys yet — track any train in India" |
| **Error (no cache)** | Plain words · one retry · one alternative path | "Couldn't reach the network. [Try again] · or [SMS 139]" |
| **Unknown field (UNKNOWN)** | Hairline placeholder, no guess | "Platform — not yet posted" |

New rules: (1) **Offline actions queue visibly** — save/mute/alarm made offline show "will apply when online" and reconcile via the repo layer; never a failed tap. (2) **SMS-139 bridge** (FR-9.3) surfaces *only* in offline error states on live surfaces — the honest no-data fallback. (3) **Stale-desaturation** — old urgency must not scream as loudly as live urgency (icon+word stay full-strength; only colour relaxes, so FR-10.2 redundancy holds).

---

# 6 · Component Library — "Semaphore"

The design system is named **Semaphore** — after railway semaphore signalling: mechanical, honest, discrete states, signal colours. It encodes the product's soul (honest, calm, board-like) into a buildable inventory.

### 6.1 The two-surface world (the visual identity)

Every screen composes exactly two surface families, and the *contrast between them is the brand*:

- **Paper** — the calm ground (bg / surface / surface2): where you read, decide, configure. Warm-neutral, generous whitespace.
- **Board** — the dark departure-board surface, reserved *exclusively* for **live operational truth** (board hero, station rows, ongoing notification, arrival alarm). If it's on Board, it's live, mono-numeraled, freshness-stamped. Never decorative — scarcity is what makes Board read as "the truth is here."

This gives users a pre-cognitive legibility shortcut: **dark panel = glance here.**

### 6.2 The inventory

✎ = evolve a proven idea · ＋ = new.

| Component | Essence (contract) |
|---|---|
| `JourneyBoard` ✎ | fact + consequence + confidence + freshness + level; flap-roll on fact change only |
| `JourneyCard` ＋ | hero / compact variants; state-driven layout; swipe actions; wraps `JourneyBoard` |
| `Spine` / `SpineNode` ＋ | past / future / you-are-here / your-stop nodes; collapse ranges; day pills |
| `MiniSpine` ＋ | one-line `●━━◐──○` progress strip (cards, notification) |
| `StandHere` ＋ | platform-anchored coach diagram; approach arrow; reversal note; GEN toggle; words-first |
| `PassCard` ＋ | masked PNR, chart chip, per-passenger rows, perforation rule |
| `BoardRow` ＋ | station-board row: fixed mono time/platform columns, status chip |
| `OmniInput` ＋ | classifier-driven glyph/hint morphing; voice; paste-extract |
| `ActionDock` ＋ | one primary (state-chosen) + overflow |
| `ConfidenceBadge` ＋ | the CERTAIN/ESTIMATED/STALE/UNKNOWN treatment applied to any value |
| `FreshnessDot` ＋ | dot + label; live / stale / offline |
| `StatusChip` ＋ | icon + word + colour — the non-negotiable status primitive |
| `FilterSheet` / `FilterChip` ＋ | consolidated filters with active summary |
| `DayScrubber` ＋ | horizontally scrubbed day chips; never past |
| `QuotaSheet` ＋ | human-language quota picker + Tatkal countdown/watch |
| `JournalRow` ＋ | Activity entry: time, icon+word, masked refs, deep link |
| `ContextBand` ＋ | one situational suggestion; dismiss-and-stay-dismissed |
| `SheetScaffold` ＋ | 3-detent sheet-over-map host for the Journey surface |
| `Skeleton` / `ErrorState` / `OfflineBanner` / `SegmentedControl` ＋ | the shared state set |

**Composition rule:** a pattern is extracted only on its *second* use; screens stay thin over ViewModels.

---

# 7 · Design Tokens

Tokens over literals — no magic numbers in screens. All named by *role*, not value.

### 7.1 Spacing (4pt base)

```
space-0  0     space-1  4    space-2  8    space-3  12
space-4  16    space-5  24   space-6  32   space-8  48   space-10 64
```
Rule: screen gutters `space-4` (16dp); card padding `space-4`; section gaps `space-5` (24dp); the reachable dock lives in the bottom `space-10` band.

### 7.2 Radius

```
radius-sm 8    radius-md 12   radius-lg 16   radius-xl 24   radius-pill 999
```
Cards `radius-lg`; sheets `radius-xl` (top corners); chips `radius-pill`; the Board hero `radius-lg` with a 1px inner hairline to read as a physical panel.

### 7.3 Elevation (Paper only — Board never elevates; it recedes)

```
elev-0  flat, hairline border         (default cards)
elev-1  y2 blur8  8% ink               (raised card / context band)
elev-2  y8 blur24 12% ink              (sheet, dialog)
```
Elevation is expressed as soft shadow in light, and as a **surface-lightness step** in dark (shadows are invisible on `#08…`), so depth survives both themes.

### 7.4 Confidence tokens (the system's signature)

Each confidence level is a *bundle*, applied by `ConfidenceBadge`/`JourneyBoard`:

| Level | Edge | Opacity | Motion | Copy prefix | Haptic on change |
|---|---|---|---|---|---|
| `conf-certain` | crisp 0px | 100% | flap-roll | none | light tick |
| `conf-estimated` | soft 1.5px blur / dashed | 100% | breathe 2.4s | `~` | — |
| `conf-stale` | crisp | 80% (desat 20%) | crossfade | "· offline" | — |
| `conf-unknown` | hairline placeholder | 60% | none | "not yet posted" | — |

### 7.5 Duration & easing ladder

```
instant  90ms  · linear                 chip select, toggle knob
quick    180ms · standard decel         crossfades, chip morphs, reorder start
settle   280ms · emphasized decel       sheet detents, card expand, shared-element
roll     420ms · spring (0.8 damping)   the flap-roll; severity colour ease
breathe  2400ms · sine ±12% opacity     ONLY the estimated marker (honesty motion)
```

### 7.6 Touch & sizing

```
touch-min 48dp (hard floor, FR-10.3)   ·   board-row 72dp   ·   dock-button 56dp
font-scale cap 1.3× at root            ·   hit-slop 8dp on <48dp visual glyphs
```

---

# 8 · Typography System

One humanist sans with first-class Indic coverage + one mono for numerals. **Noto Sans** (guaranteed parity across all 12 target scripts, FR-10.1) for text; a tabular mono (e.g. **Noto Sans Mono** / **JetBrains Mono**) for every digit that can change under the reader. Scale is named by **job**, not size.

| Role | Face / size / line / weight | Used for |
|---|---|---|
| `board-xl` | Mono · 44/48 · 600 · tabular | The fact — the biggest thing in the app |
| `board-m` | Mono · 22/28 · 500 · tabular | Station-board times & platforms; card facts |
| `title` | Sans · 24/30 · 650 | Screen titles, train names in headers |
| `body` | Sans · 16/24 · 450 | Everything readable |
| `label` | Sans · 14/20 · 550 | Chips, buttons, node labels |
| `caption` | Sans · 12/16 · 450 | Freshness, honesty labels, hints |
| `numerals` | Mono span inside prose | Every digit run inside a mixed string |

Rules: (1) **Tabular numerals everywhere digits change** — no jitter on refresh. (2) `board-xl` **auto-shrinks-to-fit** its card rather than wrapping (a delay reads "112 min late" in Hindi and must never wrap the flap-roll). (3) **Indic line-height +10%** on Devanagari/Tamil blocks (conjuncts clip at Latin heights), set once at theme level. (4) Digits inside sentences are wrapped by a `monoNumerals()`-style span so words stay in the sans and numbers in the mono, in one `Text`.

---

# 9 · Color Palette

Extends a signal-first palette. **Hard rule: brand ≠ signal, ever; colour never appears alone (icon+word always, FR-10.2); fact-bearing pairs ≥ 4.5:1 (≥ 7:1 in Sunlight).** Four themes: Light, Dark (true-dark OLED), Sunlight (high-contrast), and the Board sub-palette that overlays all three.

| Token | Light | Dark | Sunlight ☀ | Role |
|---|---|---|---|---|
| `bg` | `#E7ECEF` | `#081115` | `#FFFFFF` | App ground |
| `surface` | `#FFFFFF` | `#0F2129` | `#FFFFFF` | Cards |
| `surface2` | `#F1F4F6` | `#15303A` | `#F2F2F2` | Insets, chips |
| `ink` | `#0F2A33` | `#EAF3F4` | `#000000` | Primary text |
| `ink2` | `#54696F` | `#9DB4BB` | `#2A2A2A` | Secondary |
| `ink3` | `#8FA1A8` | `#6C868E` | `#555555` | Tertiary (never facts in ☀) |
| `line` | `#DCE3E7` | `#1E3B45` | `#BBBBBB` (2dp) | Hairlines |
| `brand` | `#2743C4` | `#8098FF` | `#1D33A0` | **Tappable only** |
| `brandSoft` | `#EAEEFB` | `#182747` | `#D8DEF6` | Selected / tint |
| `green` | `#178A52` | `#3DBB77` | `#0B6B3C` | On time / confirmed |
| `amber` | `#C9770B` | `#E7A542` | `#8F5400` | Delay / warning |
| `red` | `#C33F3F` | `#E36868` | `#9E2B2B` | Cancelled / alarm |
| `board` | `#0F2A33` | `#06151A` | inverted `#FFF`/`#000` | The live surface |
| `boardGreen` | `#3DE08A` | `#3DE08A` | `#0B6B3C` | Board on-time |
| `boardAmber` | `#FFC24D` | `#FFC24D` | `#8F5400` | Board delay |
| `boardRed` | `#FF7A7A` | `#FF7A7A` | `#9E2B2B` | Board cancellation |
| `boardInk` | `#7FA6AE` | `#6E969E` | `#333333` | Board secondary |
| `estimate` | ink2 @60% + dashed | same | `#2A2A2A` dashed | Interpolated / predicted (conf-estimated) |
| `focus` | `#2743C4` 2dp ring | `#8098FF` | `#000` 3dp | Keyboard / switch-access focus |

The **accent (`brand`) appears almost never** — only on tappable affordances and selection — so the eye learns "blue = I can touch it, green/amber/red = what the train is doing." Signal colours are validated to be colour-blind-distinguishable *and* always carry an icon + word, so no persona relies on hue.

---

# 10 · Motion Guidelines

### 10.1 The three laws

1. **Motion = meaning.** Every animation encodes a state change (data changed, surface continued, severity shifted). Zero decorative loops. The enforcement mechanism: only `PollController` owns time; UI animates only on state change — never a `LaunchedEffect(Unit){while(true)}` loop.
2. **The board is the soul.** The **flap-roll** is the one theatrical move, reserved for facts changing on Board surfaces. Everything else is quiet easing.
3. **Reduced-motion is a first-class rendering,** not a disabling: flap-roll → crossfade; marker glide → step; breathe → static `~`. Meaning survives; theatre goes (FR-10.3).

### 10.2 Signature micro-interactions

- **Card → Journey continuation:** the Stack card's `JourneyBoard` is the *same element* as the surface's board — `SharedTransitionLayout` keyed by journey id (`settle`); the sheet and map fade in beneath it. The user never "navigates"; the card *becomes* the screen. This one transition is most of the perceived premium.
- **Flap-roll:** vertical slide+fade keyed on the fact *string*; a no-change poll tick is visually silent (backoff makes most ticks no-ops). Also on station-board platform cells and the Live Updates notification.
- **Severity colour ease:** green→amber→red eases over `roll`, never snaps.
- **Estimated marker breathe:** the only ambient motion, and it *is* an honesty signal (alive-but-approximate).
- **Sort-settle (Plan):** rows reorder with `quick` translate when hydration completes — the list visibly *finds its truth* rather than sorting on nulls.
- **Chart-prepared:** one green flap-roll to "Chart prepared ✅", one `confirm` haptic, one 600ms shimmer across the pass — dosed, then done. No confetti (calm is the brand).
- **Pull-to-refresh** (FR-2.5): a signal-dot trio that settles into the freshness stamp.
- **Bad-day entrance:** cancellation never animates playfully — the board *crossfades* (no roll) to red `⛔ Cancelled`, one `warning` haptic ×2, alternatives slide up `settle`. Severity earns sobriety.

### 10.3 Haptic grammar (three channels — visual, haptic, and TTS — one source)

| Event | Haptic |
|---|---|
| Chip / toggle / segment | `tick` |
| Fact changed on a visible board | single light `tick` (the "flap" made physical) |
| Chart prepared / arrival at your stop | `confirm` double-tap |
| Platform change / delay past threshold | `warning` heavy single |
| Cancellation | `warning` ×2, spaced 120ms |
| Sheet detent snap | `segment tick` |
| Never | scroll ticks, keyboard, decorative anything |

Haptics respect the system setting and attach to the *same* state changes as motion — one grammar, three channels.

---

# 11 · Accessibility Guidelines

Accessibility is a *requirement with acceptance criteria*, not a pass. Everything proven is kept; the redesign hard-codes:

1. **Contrast as CI-checkable tokens.** Every Paper pair ≥ 4.5:1, Board ≥ 4.5:1, Sunlight ≥ 7:1 — encoded in a palette unit test so a token tweak can't silently fail a persona.
2. **TalkBack reads the answer skeleton in order:** fact → consequence → freshness → action. *"Twelve minutes late. You reach Bhopal around four forty-nine, platform four. Updated just now. Set arrival alarm, button."* The Journey surface sets traversal order explicitly; the spine names "your stop" nodes with the star spoken.
3. **Live regions, politely.** The board hero is a *polite* live region (announces on fact change, not on poll ticks — the change-keyed rule again); cancellation is *assertive*.
4. **Spoken status** (FR-10.4) shares the consequence-line generator: one sentence source → screen, notification, and TTS — parity across channels, in all languages, for free.
5. **One-handed by construction.** Primary actions (dock, docked search, filter chip, tab bar) live in the bottom 40%; nothing fact-critical *only* in the top third; sheet detents draggable from anywhere on the sheet.
6. **Switch access & keyboard.** Visible `focus` ring token; scrubbers and sheets fully operable via focus + select; each detent is a focusable stop.
7. **Icon + word + colour** on every status; **48dp** floor on every target; **1.3× font scale** honoured with reflow; **PNR masked** at every layer including logs/analytics.
8. **Low-literacy audit ritual.** Every new pictogram (coach diagram, spine nodes, quota icons) is tested with low-literacy users before shipping — the diagram-first "Stand Here" is designed to be readable with zero words.
9. **Confidence is redundant, too.** CERTAIN/ESTIMATED/STALE/UNKNOWN never rely on the visual treatment alone — each carries a copy prefix and a TalkBack phrasing ("estimated", "shown from cache, six minutes old", "not yet posted").

---

# 12 · Jetpack Compose Design System

### 12.1 What does *not* change (this is why the redesign is feasible)

`PollController` (cadence, backoff, lifecycle), `Resource<T>` SWR pipeline, `ScreenCache`/Room, `DeviceSession` auth, `DirectorySearch`, `IsoTime`/`Freshness`, `monoNumerals`, `StatusChip`, string-parity CI, and MVVM-with-interfaces testing. The redesign is a **presentation-layer reorganisation over the same data machinery.** Every existing ViewModel test carries forward.

### 12.2 Composition hierarchy

```
MainActivity
 └─ LocalizedContent → RailcastTheme(palette = Light | Dark | Sunlight)
     └─ Onboarding(language only)  OR  RailcastApp
         └─ Scaffold(bottomBar = RailcastBottomBar /* 3 tabs */)
             └─ NavHost(startDestination = resolveEntry(journeys))   ← state-resolved (§3.2)
                 ├─ journeys  → JourneyStackScreen | LiveJourneyScreen | InvitationScreen
                 │                (JourneyStackViewModel picks by 0/1/N active)
                 ├─ search    → SearchScreen        (OmniInputViewModel)
                 ├─ activity  → ActivityScreen      (JournalViewModel)
                 ├─ journey/{trainNo}?pnr&date      (JourneyViewModel)
                 │    └─ SheetScaffold(map = RouteCanvas, sheet = JourneySheet)
                 │         ├─ JourneyBoard → Spine → StandHere → PassCard
                 │         └─ ActionDock
                 ├─ station/{code}                  (StationViewModel)
                 └─ plan?from&to&date               (PlanViewModel)
```

### 12.3 Key implementation decisions

- **State-resolved entry** is a pure function `resolveEntry(journeys): EntryTarget` (0→Invitation, 1→LiveJourney, N→Stack) — JVM-unit-tested (`EntryResolveTest`), so the "no fixed home" behaviour is deterministic and doesn't depend on nav side effects.
- **`JourneyViewModel` = merge of the old Track + PNR ViewModels** behind one `JourneyUiState` (train `Resource` + optional pnr `Resource` + derived `JourneyState` + `ConsequenceLine` + `Confidence`). Both existing suites carry over; the derivations are pure functions with their own tests (`JourneyDeriveTest`).
- **`ConsequenceEngine`** — a pure formatter `(trainScreen, myStations?, now) → AnnotatedString`, unit-tested, shared by screen, notification builder, and TTS. One source of truth for "what it means for you."
- **`ConfidenceModel`** — a pure mapper `(field, meta) → Confidence` applied uniformly; a value rendered without it fails a lint/test rule.
- **`QueryClassifier`** — pure Kotlin over `FormatValidation` + `DirectorySearch`; exhaustively JVM-tested (digit shapes, route grammars in EN + transliteration, paste extraction).
- **Shared-element card→surface:** Compose 1.7+ `SharedTransitionLayout` keyed by journey id; fallback (older devices / reduced motion) is a crossfade — the API degrades cleanly.
- **`SheetScaffold`:** three anchored detents via `AnchoredDraggable`; the map is a plain `Canvas` polyline over the directory's station coordinates (already offline per FR-9.2) — **no Maps SDK for v1**: a stylised route canvas keeps APK ≤ 25 MB (NFR-1), works fully offline, and matches the calm aesthetic better than tiles. Tile maps can arrive later behind the same composable contract.
- **Live Updates notification:** `NotificationCompat` ongoing + `ProgressStyle` (Android 16+) with segments from the spine model; pre-16 falls back to a standard ongoing notification, same text grammar. Driven by FCM + (foreground) the same `Resource` flow — no new polling (NFR-3).
- **Journal storage:** a small Room table (`journal_entries`: time, type, journeyId, masked refs, deep link), written by the FCM receiver and watch-registration events; purged with journey purge (FR-4.3).
- **Palette third variant:** `RailcastColors.Sunlight` alongside Light/Dark via the existing `CompositionLocal`; selection = user toggle ∨ ambient-light suggestion.

### 12.4 Performance budget (NFR-1)

Cold launch ≤ 2.5 s on entry-level hardware: the resolved entry renders from Room on frame one (existing SWR); the map canvas defers until the Journey surface; flap-roll and shared-element are `graphicsLayer`-only (no relayout); LazyColumn keys are stable ids (spine nodes keyed by station code) so poll updates recompose only changed rows; baseline profile covers launch → entry → Journey. **No new heavyweight deps** (no Maps SDK, no Lottie — all motion is Compose-native).

### 12.5 Testing shape (unchanged philosophy, new coverage)

JVM-only, fakes over frameworks: `EntryResolveTest`, `JourneyDeriveTest`, `ConsequenceEngineTest`, `ConfidenceModelTest`, `QueryClassifierTest`, `JournalPolicyTest` (quiet-hours filtering, purge), `SpineCollapseTest`, palette-contrast test. Screens stay thin; visuals verified on-device.

---

# 13 · Future Scalability

The architecture is designed so today's decisions don't become tomorrow's ceiling.

**Design-system scale.** Semaphore is token-first and role-named, so: a new language is a font-coverage + line-height token change, never a component change; a new theme (e.g. an e-ink or a colour-blind-tuned palette) is a fourth `RailcastColors` variant behind the same `CompositionLocal`; a new component enters only on its second use. Ship the tokens as a versioned module (`core/design`) that iOS and web can consume as a spec.

**Platform scale.** The Journey object, `ConsequenceEngine`, `ConfidenceModel`, and `QueryClassifier` are all pure and Android-agnostic in *logic* — they port to a Kotlin Multiplatform `shared` module, so iOS (SwiftUI) and the web view render the *same consequence sentence* and confidence treatment. The API is already contract-locked (`packages/shared`), so a SwiftUI client and the `/t/:token` web page are new *presentation* layers, not new products.

**Surface scale (the Journey object pays off repeatedly).** The same Journey + `MiniSpine` + `ConsequenceEngine` feed, with near-zero incremental design: a **home-screen widget** (next departure / live position), a **Wear OS** tile (arrival countdown + platform), **Android Auto** (spoken arrival alarm), **Live Activities / lock-screen** (already specced in §12.3), and the **SEO web pages** (FR-8.3) reusing the sheet-over-map visual. Each is a new renderer over an existing model.

**Data scale.** Because screens never touch upstream directly (BFF invariant) and the confidence system already models "which source, how fresh," adding a *second* data provider (the PRD's existential-risk mitigation) is a server-side change the client sees only as improved confidence — no UI rework. The directory's day-one multilingual schema (`name_hi`, `name_ta`, …) means adding a language is a dataset build, never a schema or app change.

**Product scale.** The Journey lifecycle generalises beyond trains: a `Journey` whose legs include a metro hop or an intercity bus is the same object with typed legs — multi-modal is an additive lifecycle extension, not a rebuild. **Railcast Plus** (unlimited saves, richer prediction, family live-sharing, priority refresh) rides the existing watch/cadence model; family sharing is a Journey shared across accounts. Booking handoff (post-WS-D) is one action on the pass, clearly labelled, never a dark pattern.

**Governance.** A `faster-or-calmer` ship gate (every addition must measurably speed the answer or reduce load, or it doesn't merge), a design-token changelog, and the persona audit ritual keep the product from accreting the very clutter it was built to escape. Calm has to be *defended*, continuously.

---

# 14 · The Top 25 Changes

Ordered by (User Impact, then Business Value, then inverse Complexity). ★ = flagship differentiator no incumbent has.

| # | Change | Why it matters | UI | DC | BV |
|---|---|---|---|---|---|
| 1 | **The Journey object** (§3.1) | Unifies train + PNR + coach + alerts into the user's actual mental model; every surface hangs off it | H | H | H |
| 2 | **State-resolved entry point** (§3.2) ★ | The app opens to the *answer*, not a menu — 0 taps for the 80% case; no competitor does this | H | M | H |
| 3 | **The Confidence System** (§2.4) ★ | Honesty rendered as a visual/haptic dimension; the deepest, least-copyable moat | H | M | H |
| 4 | **Consequence line** (§5.0) ★ | Converts data into *your* answer — "when do I arrive" in zero taps; the 2-second goal met | H | M | H |
| 5 | **Omni-input with classifier** (§5.2) | Kills the mode toggle and four duplicate search boxes; removes the #1 first-session failure | H | M | H |
| 6 | **Activity journal** (§5.6) ★ | Makes the hero feature (proactive alerts) *provable* — alerts you can't lose | H | M | H |
| 7 | **"Watching now" honesty entries** (§5.6) | Turns the invisible Watcher into visible evidence — earns the trust metric emotionally | M | L | H |
| 8 | **Sheet-over-map Journey surface** (§5.3) | FR-2.2 finally real; the screenshot that markets the app | H | H | H |
| 9 | **"Stand Here" coach guide** (§5.3) ★ | The most shareable answer in the category — diagram-first, words-first, reversal-aware | H | M | H |
| 10 | **Live Updates ongoing notification** (§12.3) ★ | The train in your notification shade, Flighty-class; glanceable without opening | H | M | H |
| 11 | **Bad-day flow: cancellation → prefilled alternatives** (Flow 5) | The switch-moment handled with grace; trust compounding | H | M | H |
| 12 | **PNR as a pass** (§5.3) | Ticket-shaped ticket; chart + per-passenger clarity at arm's length | H | M | M |
| 13 | **Unified spine timeline** (§5.3) | One spatial truth; you-are-here anchored *in* the schedule | H | M | M |
| 14 | **Contextual action dock** (§5.3) | The app ranks actions by state — "what do I do next" answered | M | L | M |
| 15 | **Sunlight mode** (§5.4) | FR-5.3 shipped; P3's platform-in-June reality | H | M | M |
| 16 | **Board-typographic station rows** (§5.4) | Time + platform scannable in ~1 s; the board metaphor earns itself | H | L | M |
| 17 | **Onboarding to one question** (§5.7) | Every removed pre-value decision lifts first-session success | M | L | H |
| 18 | **Shared-element card → surface continuity** (§10.2) | The one transition that makes the whole app feel engineered | M | M | M |
| 19 | **Day scrubber + route-as-title Plan** (§5.5) | Planning becomes scrubbing, not form-resubmitting | H | M | M |
| 20 | **Chart-prepared, full-channel** (§10.2) | The emotional peak: push + journal + pass shimmer + haptic — never missed | M | L | H |
| 21 | **Context band** (§5.1c) | Citymapper-class situational intelligence, dosed to one line | M | M | M |
| 22 | **Sort-settle hydration** (§5.5) | Never sort on nulls; visible truth-finding; removes a trust papercut | M | M | M |
| 23 | **Stale-desaturation rule** (§5.8) | Old urgency shouldn't scream; honesty rendered system-wide | M | L | M |
| 24 | **Haptic grammar** (§10.3) | Status you can *feel* in a pocket; the third redundancy channel | M | L | M |
| 25 | **Semaphore design system + Confidence/Consequence engines as shared logic** (§6, §12) | One coherent, testable, multiplatform-ready system; the foundation everything else scales on | M | M | H |

### What Railcast never does (the product law, unchanged)

No ads. No interstitials. No forced login. No notification spam. No fake urgency. No dark patterns. No gesture-only core actions. No decorative motion. No unlabelled colour. No faked data (UNKNOWN is honest, not blank). The redesign adds **zero** exceptions — and several choices (journal over badges, one context band, coalesced notifications, celebration-then-done, the confidence system) exist specifically to keep the calm promise *while* the product grows more capable.

---

## Closing

Railcast already tells the truth faster than anyone. This redesign makes it feel like a companion who was watching your train before you asked:

- **one object** — the Journey,
- **one entry** — the answer, resolved from state, not a menu,
- **one input** — the omni-classifier,
- **one sentence** — the consequence, shared across every channel,
- **one honest dimension** — confidence, rendered not just written,
- **one receipt** — the journal,
- **one theatrical move** — the flap-roll,

and a calm so consistent it reads as luxury on a ₹8,000 phone. Not the best railway app in India. The best travel companion in the world — that happens to run on rails.

*— End of document.*

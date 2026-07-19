# Railcast UX Redesign — First Principles

**Status:** Proposal for review · **Date:** July 2026
**Companion to:** [`PRD.md`](./PRD.md) (requirements), [`ux-plan.md`](./ux-plan.md) (v1 UX thesis), [`app-guide.md`](./app-guide.md) (what ships today), [`ui-architecture.md`](./ui-architecture.md) (how it's built).

This document rethinks Railcast's entire experience from first principles while preserving every functional requirement in the PRD. It is a **proposal**: where it recommends amending the PRD (notably §7 "Five tabs" and the Home/Track/PNR split), the amendment is called out explicitly with the FR/§ it touches, and a conservative fallback is offered. Nothing here weakens an invariant — PNR masking [FR-4.3], no-raw-dates [FR-2.3], icon+word+colour [FR-10.2], and no-ads/no-dark-patterns [FR-10.5, §7] are treated as physics, not preferences.

**The one-sentence thesis:**

> Railcast today is five good tools in a row. It should be one companion that already knows which tool you need.

Everything below is in service of that sentence.

---

## Table of contents

- [Part I — UX audit of the current app](#part-i--ux-audit-of-the-current-app)
- [Part II — Redesign strategy](#part-ii--redesign-strategy)
- [Part III — Information architecture & navigation](#part-iii--information-architecture--navigation)
- [Part IV — Screen-by-screen redesign](#part-iv--screen-by-screen-redesign)
- [Part V — Design system 2.0](#part-v--design-system-20)
- [Part VI — Motion, micro-interactions & haptics](#part-vi--motion-micro-interactions--haptics)
- [Part VII — States: loading, empty, error, offline](#part-vii--states-loading-empty-error-offline)
- [Part VIII — Accessibility & inclusivity](#part-viii--accessibility--inclusivity)
- [Part IX — Android / Jetpack Compose implementation](#part-ix--android--jetpack-compose-implementation)
- [Part X — The top 25 improvements, ranked](#part-x--the-top-25-improvements-ranked)
- [Part XI — Migration plan](#part-xi--migration-plan)

Rankings throughout use: **UI** = User Impact, **DC** = Development Complexity, **BV** = Business Value, each H/M/L.

---

# Part I — UX audit of the current app

The current app is honest, fast, accessible, and disciplined — the engineering conventions (answer-first skeleton, `Resource<T>` cached-first, `PollController`, icon+word+colour) are genuinely better than most shipping travel apps. The problems are **structural**, not cosmetic. Beautifying the current structure would waste the opportunity.

## 1.1 The seven structural findings

### Finding 1 — The app is organized like the team, not like the traveler

Five tabs — Home · Track · Station · Plan · Alerts — is a **feature org chart**. Each backend endpoint (`/screen/train`, `/screen/station`, `/screen/plan`, `/screen/pnr`) got a tab or a toggle. But a traveler doesn't think in endpoints; they think in **journeys**: *"my train tomorrow,"* *"Amma's train tonight,"* *"the 16:25 to Bhopal."* A journey has a train, maybe a PNR, a boarding station, a destination, and a state (upcoming → boarding → riding → arriving → done). Today that one mental object is shredded across four surfaces: its status lives in Track, its ticket in PNR, its platform in Station, its alternatives in Plan, its notifications in Alerts. The user is the integration layer. **The app should be.**

- Evidence in the current design: a saved train on Home and the same train open in Track are two unconnected renderings of the same entity; a saved PNR doesn't appear on Home's saved list at all (only trains do); a cancellation seen on the Station board hands off to Plan, losing the context of *why* you were looking.

### Finding 2 — The Track tab is usually empty

Track is "the live board for one train — Railcast's signature screen," yet as a *tab* it spends most of its life showing a search box. A tab that is empty until you feed it is a **tool**, not a **place**. Signature screens deserve to be arrived at with content, not configured after arrival. (Flighty has no "Track tab" — you tap a flight and you're tracking. Uber has no "Ride tab" — the trip takes over Home.)

### Finding 3 — Four search boxes, one directory

Home, Track, Station, and Plan each carry their own search field over the same bundled directory. Four entry points to one capability means: four muscle memories, four inconsistent result lists, and a classification burden pushed onto the user — worst of all in Home's **Train · PNR toggle**, which forces the user to declare the *type* of their query before typing it. The app has a validator that already knows 5 digits = train and 10 digits = PNR (`FormatValidation`); making the human do the classification the code can do is backwards. Modes are where errors live.

### Finding 4 — The Alerts tab is an IA lie

The tab is named **Alerts** and contains **settings** (toggles, quiet hours, language, privacy). There is no actual *alert* anywhere in it. Meanwhile the most anxiety-relevant question — *"what did Railcast tell me while I slept?"* — is unanswerable, because pushes are ephemeral: swipe one away at 4 a.m. and the chart-prepared moment is gone forever. For a product whose hero feature is proactive alerting [FR-4.2, FR-7.x], the absence of an alert **journal** is the single biggest trust gap. The notification is the message; the inbox is the proof.

### Finding 5 — Status without consequence

The board hero answers *"how late is my train?"* ("▶ 12 min late") but not the question underneath it: *"so when do I actually arrive, and what should I do about it?"* The §2 goal is 2-second answers to seven questions — *where, how late, which platform, will I reach on time, chart status, coach, what next* — and today three of those seven ("will I reach on time," "what should I do next," and often "which platform") require scrolling and mental arithmetic. **A companion converts data into consequences.** "12 min late" is data. "Arriving Bhopal ~16:49 · your alarm is set" is a consequence.

### Finding 6 — The map requirement is unrealized

FR-2.2 (route polyline + interpolated position marker, labelled "estimated") exists in the PRD but the current Track screen renders position as a bare progress line between two station names. The progress line is honest but spatially mute — it can't show "we're crossing the Narmada," can't support the shared-journey web view's most shareable visual, and can't anchor the estimated-position claim in something the eye trusts. The map is also the one surface where Railcast can *look* like the premium product it is in a screenshot — which is how apps get chosen.

### Finding 7 — Ceremony where there should be flow

Small frictions that compound: onboarding's "What brought you here?" is a question the first search would answer implicitly; Plan forgets your last route (the return-leg case is *the* repeat case); Station's filters occupy prime board space; PNR is reachable only through Home's toggle (invisible from anywhere else); the celebration banner for chart-prepared fires only if you happen to be staring at the PNR screen at the moment of preparation.

## 1.2 Screen-by-screen audit matrix

Each screen scored against the fifteen lenses in the brief. ✅ = strong today, ⚠️ = weak, ❌ = missing. (Redesigns in Part IV address every ⚠️/❌.)

| Lens | Home | Track | Station | Plan | PNR | Alerts |
|---|---|---|---|---|---|---|
| 1. Core problem | Toggle-modal search; no journey object | Empty-tab problem; no consequence line | Filters above the fold; board isn't board-like | No route memory; sort vs. hydration race | Hidden entry; lookup-tool framing | Settings masquerading as alerts |
| 2. User frustration | "Why must I say train *or* PNR?" | "When do *I* arrive?" needs math | Rushed user scrolls past chips | Retyping the return leg | "Where do I check my ticket?" | "What did it send me last night?" |
| 3. Cognitive load | Medium (self-classification) | Medium (two position representations) | High at the worst moment (rushing) | Medium (quota jargon) | Low | Low but misplaced |
| 4. Information hierarchy | Saved cards below search even for returning users | ✅ board-first; ⚠️ run-date chip above position | ⚠️ controls > content | ✅ answer rows; ⚠️ fare buried one tap | ✅ chart chip first | ⚠️ flat toggle list |
| 5. Visual hierarchy | ✅ calm | ✅ board hero is genuinely great | ⚠️ rows too uniform to scan | ⚠️ card ≈ card ≈ card | ✅ | ✅ |
| 6. Interactions | ⚠️ toggle; ✅ voice | ✅ sticky bar; ⚠️ 3 equal-weight actions | ⚠️ two filter systems (chips + text field) | ✅ in-place expand | ✅ validation | ✅ |
| 7. Layout | Search-first even when saved cards are the need | Linear list; map absent | Table without tabular alignment | Form then list; form never collapses | Single column ✅ | List ✅ |
| 8. Animation | Freshness dot only | ✅ flap roll (signature); ⚠️ nothing else moves | ❌ none | ❌ none | ✅ celebration | ❌ |
| 9. Gestures | ❌ no swipe actions on cards | ❌ no pull-to-refresh (FR-2.5) | ❌ | ❌ | ❌ | — |
| 10. Empty states | ✅ directive | ⚠️ search box as empty state | ✅ | ✅ | ✅ | — |
| 11. Loading | ✅ SWR + skeletons | ✅ | ✅ | ✅ per-row hydration | ✅ | — |
| 12. Offline | ✅ exemplary (never blank) | ✅ | ✅ | ⚠️ fares can't cache long | ✅ | ✅ |
| 13. Accessibility | ✅ 48dp, labels | ✅; ⚠️ board contrast in sun | ⚠️ sunlight mode missing (FR-5.3) | ✅ | ✅ | ✅ |
| 14. Responsiveness | ✅ font-scale cap | ✅ | ⚠️ long ISO bug class fixed, but rows still fragile | ✅ | ✅ | ✅ |
| 15. Discoverability | ⚠️ PNR behind toggle | ⚠️ GEN toggle obscure | ⚠️ "near me" chip small | ⚠️ Tatkal reminder buried | ❌ only via Home | ⚠️ mute lives on Track only |

## 1.3 What must be preserved (the crown jewels)

These are *better than the industry* and the redesign builds **on** them, never over them:

1. **The board hero + flap roll** — the one component with a soul. It becomes the system-wide signature (Part V).
2. **Cached-then-fresh everywhere; offline never blank** — Flighty-grade behavior already shipped.
3. **Icon + word + colour discipline**, brand-vs-signal colour separation, freshness stamps.
4. **`PollController` single-owner refresh** with change-keyed animation — this is *why* the motion system in Part VI is even possible.
5. **The honesty register** — "estimated," "usually," "updated 6 min ago." No competitor talks like this. It is the brand.
6. **No-raw-dates run-date probe**, masked PNRs, format validation before network.
7. **Near-zero onboarding** (kept, minus one question — see §4.8).

---

# Part II — Redesign strategy

## 2.1 First principles

Derived from the PRD's §2 goals and §3 personas, not from aesthetics:

- **P1 — The journey is the object.** Trains, PNRs, stations, and plans are *facets* of a journey. One `Journey` concept in the IA, one journey surface in the UI, one card shape everywhere a journey appears. (This is Flighty's deepest lesson: the flight is the noun; everything else is a verb on it.)
- **P2 — Answer → consequence → action.** Every live surface renders three lines in fixed order: the fact ("12 min late"), what it means for *you* ("Bhopal ~16:49"), and the one next thing to do ("Set arrival alarm"). The PRD's answer-first skeleton (§7) gains the middle line.
- **P3 — Classify for the user, never ask the user to classify.** One search that detects train/PNR/station/route intent. Modes are moved from the human to the parser.
- **P4 — Places, not tools.** A tab must be worth opening with no input: it must show *state* (your journeys, your activity), not an empty form. Tools (plan, station lookup) are reached *from* places with context pre-filled.
- **P5 — Calm is load-bearing.** Restraint in colour, motion, and notification volume isn't styling — it is the product's answer to WIMT's hostility (§1.2 of the PRD). Every addition is tested against "does this make the answer faster or calmer?"
- **P6 — Design for the glance, engineer for the wait.** Two reading modes: 2-second glance (station platform, one-handed, sunlight) and 20-minute dwell (on the train, hours to kill). Every screen declares which mode it serves; the glance mode always wins conflicts.
- **P7 — The bad day is the brand moment.** Cancellation, diversion, waitlist, dead signal: these are when users switch apps forever — in either direction. Bad-day flows get first-class design (Part IV) and their own motion/haptic grammar (Part VI).
- **P8 — Honesty is rendered, not just written.** Estimates *look* estimated (soft-edged, breathing marker), facts look solid (crisp board type). The visual system itself encodes confidence.

## 2.2 What "premium" means here, concretely

Borrowed principles (never visuals):

| From | The principle we take | Where it lands |
|---|---|---|
| **Flighty** | The trip is a living object with a lifecycle; push the *delta*, not the data | Journey object (§3.1), Activity journal (§4.7) |
| **Apple Wallet** | A ticket is a pass: one card, scannable at arm's length, no chrome | PNR pass card (§4.5) |
| **Nothing OS** | Monochrome confidence; dot-matrix numerals as identity; one accent used almost never | Board type ramp, restrained accent (Part V) |
| **Google Maps** | Progressive disclosure by zoom: overview → corridor → stop | Map behavior (§4.4), spine collapsing (§4.4) |
| **Citymapper** | Context makes the UI: near a station, *be* the station app | Home context row (§4.2) |
| **Uber** | During a live trip, the trip owns the home screen | Live journey takeover (§4.2) |
| **Linear** | Speed as a feature; keyboard-fast interactions; zero decorative chrome | Omnisearch (§4.3), 60fps budget (Part IX) |
| **Tesla** | One canvas, layered panels, no "pages" | Sheet-over-map journey layout (§4.4) |
| **Dynamic Island / Live Updates** | The live thing follows you out of the app | Ongoing-journey notification & Live Updates (§4.9, Part IX) |
| **Notion Calendar** | Time is a first-class axis you scrub, not a field you fill | Plan's day scrubber (§4.6) |

## 2.3 The three-layer model

The entire app reduces to three layers, which map 1:1 to the new tabs:

```
┌────────────────────────────────────────────────────────┐
│  NOW      "How is my journey?"    → Home (journeys)    │
│  FIND     "Show me a thing."      → Search (omnibox)   │
│  CONTROL  "What happened / prefs" → Activity (journal) │
└────────────────────────────────────────────────────────┘
```

Everything the five old tabs did survives — relocated to where the traveler's mental model expects it. The mapping is exhaustive (see §3.3): nothing is cut, several things are merged.

---

# Part III — Information architecture & navigation

## 3.1 The Journey object (the IA keystone)

A **Journey** is the unifying entity. It is a client-side concept (no contract change required for v1 of the redesign; see Part XI) composed from what already exists:

```
Journey
├─ train        (number, name, run-date — from the run-date probe [FR-2.3])
├─ pnr?         (masked; passengers, chart status [FR-4.1..4.3])
├─ myStations?  (boarding, alighting — inferred from PNR, or user-chosen)
├─ state        upcoming → boarding → riding → arriving → completed
│               (+ orthogonal flags: delayed / diverted / cancelled)
└─ watches      (alarm, mute, delay threshold — the Watcher jobs [FR-7.x])
```

Why this matters mechanically:

- **Saving becomes one verb.** Today "save a train" and "save a PNR" are unrelated features with unrelated storage. In the redesign both produce a Journey card on Home. Saving a PNR *implies* its train; tracking a train can later *absorb* a PNR ("Is this your train? Attach your ticket").
- **State drives the UI.** A Journey's lifecycle state decides its card size, its primary action, its polling cadence (already how `PollController` + Watcher think), and its notification posture. `upcoming` cards lead with departure time + chart status; `riding` cards lead with position + delay; `arriving` cards lead with the alarm; `completed` cards quietly offer "was the info right?" [FR-11.2] then archive.
- **The seven 2-second questions become card fields.** Where/how-late/platform/on-time/chart/coach/next — every one is a slot on the Journey card or its detail surface, so the design can be *audited* against §2 of the PRD slot by slot.

## 3.2 Navigation: five tabs → three

### Option A — recommended: **Home · Search · Activity**

```
┌───────────────────────────────────────────┐
│                                           │
│         (content — one of the             │
│          three places, or a               │
│          detail surface above it)         │
│                                           │
├───────────────────────────────────────────┤
│    ⌂ Home        ⌕ Search       ≋ Activity│
└───────────────────────────────────────────┘
```

- **Home** — the journey stack. Your saved journeys as live cards, biggest first by urgency; a context row (near a station → its board; evening before a journey → "chart usually out by 21:00"). Empty Home = a beautiful invitation to search. *(Absorbs: old Home's saved list, old Track's "signature" status when live — the live journey takes over Home, Uber-style.)*
- **Search** — one omnibox over everything: train number/name, station name/code, 10-digit PNR, and route intent ("ndls to bpl", "delhi bhopal"). Zero-state shows: **Near me** (stations), **Plan A→B**, **Check PNR**, recents. *(Absorbs: all four search boxes, the Train·PNR toggle, Plan's from/to pickers as a structured chip form the omnibox can expand into, Station's search.)*
- **Activity** — the journal + controls. A chronological, per-journey feed of every alert Railcast sent or observed ("04:12 · Chart prepared · ••••2882"), with alert preferences, quiet hours, language, privacy, and the OEM battery-guide beneath. *(Absorbs: Alerts tab entirely, and gives it what it lied about having: actual alerts.)*

Detail surfaces (pushed above tabs, never tabs themselves): **Journey** (Track+PNR merged — §4.4), **Station board** (§4.5), **Plan results** (§4.6), **Settings** sub-screens.

**Why three and not five.** (1) Every tab now opens to *state* — nothing opens to an empty form (P4). (2) The thumb travels less: three 48dp+ targets in the reachable arc instead of five cramped ones — measurably fewer mis-taps for shaky hands (P1 persona, FR-10.3). (3) Track-as-tab and PNR-behind-toggle both dissolve into the Journey object, which is where users already believed they were. (4) Station and Plan remain two taps from anywhere — within the PRD's "nothing important deeper than two taps" (§7): Search → "Near me" → board; Search → "Plan A→B" → results.

**PRD impact:** amends §7 ("Five tabs") and the §4.1/4.4 *placement* language (not the requirements themselves). Every FR remains satisfied; a traceability table is in §3.3.

### Option B — conservative: keep five tabs, fix the lies

If the five-tab shell must stay (release risk, user retraining cost), the minimum honest fixes are: (1) rename **Alerts → Activity** and add the journal above the toggles; (2) unify search behind one shared omnibox component so all four fields behave identically and the Train·PNR toggle dies; (3) Track's empty state becomes the journey stack (your saved journeys) instead of a search box; (4) PNR results render as the pass card of §4.5 wherever they appear. This captures ~60% of the value at ~30% of the cost. **Recommendation stands with Option A** — Option B leaves Finding 1 (no journey object) unresolved, and that finding is the ceiling on how good the product can get.

*(Per repo working rules, both options are presented; A is recommended. The rest of this document assumes A, with notes where B diverges.)*

### Ranking

| Move | UI | DC | BV |
|---|---|---|---|
| 3-tab IA + Journey object | **H** | H | **H** — the habit loop (§2 "returning within 14 days") runs through Home-as-journey-stack |
| Omnisearch | **H** | M | H — kills the largest first-session failure mode (wrong mode) |
| Activity journal | **H** | M | H — converts the hero feature (alerts) from ephemeral to trusted |

## 3.3 Functional traceability (nothing lost)

| Today | In the redesign | FR |
|---|---|---|
| Home saved-train cards | Home journey stack | FR-2.5, §2 habit |
| Home Train·PNR toggle | Omnisearch input classification | FR-1.5 |
| Track tab (picked train) | Journey surface (from any card/result) | FR-2.1..2.7 |
| Track empty search | Search tab | FR-1.1 |
| Coach guide + GEN | Journey surface, coach section | FR-3.1..3.3 |
| Station tab | Station board surface (Search → station, "Near me", or Home context row) | FR-5.1..5.3 |
| Plan tab | Plan surface (Search → route intent or "Plan A→B") | FR-6.1..6.4 |
| PNR screen | PNR pass inside Journey; standalone lookup via Search | FR-4.1..4.5 |
| Alerts toggles / quiet hours / language / privacy | Activity → Controls section | FR-7.4, FR-10.1, FR-11.3 |
| — (didn't exist) | Activity journal | FR-7.x made visible |
| — (didn't exist) | Map with estimated marker | FR-2.2 realized |
| Mute (Track action bar) | Journey overflow **and** swipe action on Home card **and** per-journey row in Activity | FR-7.4 |
| "See alternatives" on cancellation | Same, from Journey/board/card — all routes to Plan with context | FR-2.4 |

## 3.4 The redesigned user flows

**Flow 1 — returning user, train running (the 80% case):**
`Open app → Home → live journey card is the hero → glance done (0 taps, <2 s)`
Tap card → Journey surface for depth. Previously: open → Home → find saved card → tap → Track → wait → scroll.

**Flow 2 — first-time user, "where's my train":**
`Open → language (1 tap) → Home (empty, invitation) → Search → type/speak "goa express" → result → Journey surface → "Started yesterday?" probe chip if ambiguous → answer`
Two questions removed from onboarding (§4.8); same tap count to first answer as today, one fewer decision.

**Flow 3 — PNR in hand (paper ticket, low-tech user):**
`Search → types 10 digits → field morphs to PNR grouping (392-4567890), auto-detects → pass card → "Save & watch" → journey created`
The IRCTC SMS paste [FR-4.5] lands here too: paste anything containing a PNR into the omnibox, it extracts.

**Flow 4 — at the station, rushing (P3):**
`Open → Home context row: "📍 You're near Itarsi Jn — Live board" → one tap → board in sunlight mode`
Or: notification "Platform 4 for 12952" → deep-link straight to Journey. Previously: Station tab → search → type → board.

**Flow 5 — the bad day (cancellation):**
`Push: "12952 cancelled" → Journey surface opens in cancelled state: red board, plain words, one primary action: "Find trains on this route" → Plan pre-filled with route + date + class from the PNR → alternatives sorted by seats`
The Activity journal keeps the cancellation entry with a timestamp, so the user can show the TTE/family *when* they knew.

**Flow 6 — planning the return leg:**
`Search zero-state shows "BPL → NDLS · recent" chip → tap → Plan with route reversed (one-tap swap preserved) → day scrubber to Sunday → results`

---

# Part IV — Screen-by-screen redesign

Layout sketches are lo-fi ASCII; `▓` = board-dark surface, `·` = whitespace, `[ ]` = tappable.

## 4.1 The shared answer skeleton, upgraded

The PRD's answer-first skeleton (§7) gains the **consequence line** (P2) and becomes the contract for every live surface:

```
┌─────────────────────────────────────────┐
│ THE FACT               (largest, board) │  "▶ 12 min late"
│ THE CONSEQUENCE        (for-you line)   │  "Bhopal ~16:49 · Platform 4"
│ ● freshness                             │  "live · just now"
├─────────────────────────────────────────┤
│ [ THE ONE ACTION ]     (contextual)     │  "Set arrival alarm"
├─────────────────────────────────────────┤
│ detail, expandable                      │
└─────────────────────────────────────────┘
```

The consequence line is computed, personal, and honest: it uses `myStations` when known ("**your** arrival"), the train's terminus otherwise, always `~`-prefixed and never more precise than the data (estimates get `~`, scheduled facts don't). **Why:** it closes the gap in Finding 5 — the user's real question is almost never the train's state; it's their own.

## 4.2 Home — the journey stack

**Job:** answer "how are my journeys?" in one glance, zero taps.

```
┌─────────────────────────────────────────┐
│ Railcast                        ⌕  ≋    │ ← quiet wordmark; shortcuts
│                                         │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓ 12952 · Tejas Rajdhani     LIVE ●   ▓ │ ← hero: the most urgent
│ ▓                                     ▓ │    journey, board surface
│ ▓ ▶ 12 min late                       ▓ │ ← THE FACT (flap-roll type)
│ ▓ Bhopal ~16:49 · Platform 4          ▓ │ ← THE CONSEQUENCE
│ ▓ NDLS ●━━━━━━━━━◐─────────○ BPL      ▓ │ ← mini-spine, est. marker
│ ▓ [ Set arrival alarm ]     updated 40s▓ │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ ••••2882 · Sat 26 Jul   ⏳ Waiting  │ │ ← upcoming journey, compact:
│ │ 12622 Tamil Nadu Exp · chart ~21:00 │ │    chart status leads
│ └─────────────────────────────────────┘ │
│                                         │
│ 📍 Near Itarsi Jn — [ Live board ]      │ ← context row (situational)
│                                         │
│ ⌕  Track a train, PNR, or station…      │ ← docked search entry
├─────────────────────────────────────────┤
│    ⌂ Home        ⌕ Search      ≋ Activity│
└─────────────────────────────────────────┘
```

**The rules of the stack:**

1. **Urgency sorts.** `riding/arriving` > `boarding` > today's `upcoming` > future > `completed` (archived after 24 h with a quiet "how was the info?" [FR-11.2]). Cancelled/diverted journeys jump to top in their signal colour regardless of state.
2. **State sizes.** The top journey in an active state renders as the **hero** on the board surface — the Uber "trip takeover," but calm. Everything else is a compact card. If nothing is live, the next upcoming journey is hero at reduced height, leading with departure time and chart status.
3. **The card *is* the Track screen's first line.** Identical `BoardHero`-derived component, identical flap-roll on change, so tapping through to the Journey surface is a **shared-element continuation**, not a new page (Part VI). The user learns one visual grammar.
4. **Context row** (Citymapper move): appears only when confidently useful — coarse location says you're within ~1 km of a station in the directory → offer its board; a saved journey departs within 12 h → surface it even if scrolled away. Never more than one context row. Dismissible, and it stays dismissed.
5. **Gestures:** swipe a card left → Mute / Unsave (with undo snackbar); swipe right → Share journey [FR-2.7]. Long-press → reorder pin. All three also available via the card's `⋯` menu — gestures are accelerators, never the only path (PRD §7 "no hidden gestures for core actions").
6. **Search is docked, not headlining.** For returning users the stack is the point; search sits reachable at the bottom of the content (thumb zone), and the `⌕` tab is always there. For empty-state users, the invitation *is* the search (below).

**Empty state (first run):**

```
│            🚆 (calm line art)            │
│   Track any train in India — live.       │
│   [ ⌕  Train number, name, or PNR ]      │
│   [ 🎙 Speak ]      [ 📍 Trains near me ] │
```

Directive, two taps max to any first answer, voice first-class [FR-1.4].

**Why this Home wins:** the §2 habit metric ("≥1 saved train/PNR returning within 14 days ≥45%") is a bet that Home-with-your-stuff beats Home-as-search. Flighty, Uber, and every transit app that cracked retention made the same move: the home screen is *your state*, not *their features*.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Journey stack + hero takeover | H | M | H |
| Context row | M | M | M |
| Card gestures + undo | M | L | M |
| Docked search | M | L | M |

## 4.3 Search — the omnibox

**Job:** one field that takes anything and knows what you meant.

```
┌─────────────────────────────────────────┐
│ [ ⌕  Train, station, PNR, or A to B  🎙 ]│ ← focused on tab open,
│                                         │    keyboard up
│  Recent                                 │
│  ↻ 12952 Tejas Rajdhani                 │
│  ↻ NDLS → BPL                    (route)│
│                                         │
│  [ 📍 Near me ] [ ⇄ Plan A→B ] [ 🎫 PNR ]│ ← zero-state shortcuts
└─────────────────────────────────────────┘

 …typing "129"                     …typing "4392…" (10 digits coming)
┌──────────────────────────┐      ┌──────────────────────────┐
│ [ ⌕ 129|              🎙 ]│      │ [ 🎫 439 2456 789|      ] │ ← field morphs:
│ 🚆 12951 Mumbai Rajdhani  │      │   PNR — 1 digit to go    │   ticket glyph,
│ 🚆 12952 Tejas Rajdhani   │      │                          │   grouped digits,
│ 🚉 (stations suppressed — │      │  masked after lookup     │   inline count
│     pure digits = train)  │      └──────────────────────────┘
└──────────────────────────┘
```

**The classifier** (extends the existing `DirectorySearch` + `FormatValidation`, all offline):

| Input shape | Detected as | Surface |
|---|---|---|
| 5 digits | Train number | Journey surface (run-date probe as needed) |
| 10 digits | PNR | PNR pass (masked immediately on resolve) |
| Text | Fuzzy trains + stations (existing weighted ranking) | Result rows, typed with 🚆/🚉 glyph + word |
| `X to Y` / `X → Y` / two station matches | Route intent | Plan surface, prefilled |
| Pasted text containing a 10-digit run | PNR extraction offer [FR-4.5] | "Found PNR ••••2882 — check it?" |

- **Progressive disclosure of type:** the field's leading glyph morphs (⌕ → 🚆 → 🎫) as classification firms up, with the type also *named* in the helper line ("PNR — 1 digit to go") — icon + word, per FR-10.2, and it teaches the classifier's existence.
- **Wrong-guess escape:** classification is a default, not a cage — result list always includes a demoted "Search stations for '43924…' instead" row.
- **Voice** feeds the same field, same classifier, prompt in app language (existing `VoiceSearchContract`).
- **"Plan A→B"** expands the omnibox into the structured two-field + swap + date form inline (it's the same surface, just unfolded) — so Plan stops being a *place you go* and becomes a *shape the search takes*.
- **Recents are typed** (train/station/route/PNR-masked) and long-press-deletable. PNR recents show masked only, and expire with the journey [FR-4.3].

**Why:** Finding 3. One muscle memory, one implementation, one ranking to tune. Linear's lesson: the fastest UI is one input that goes anywhere; speed *is* the premium feel. And for the P1/P5 personas, one box that accepts whatever they have — a number on a paper ticket, a half-remembered train name, a spoken phrase — is the difference between success and abandonment.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Omnibox + classifier | H | M | H |
| Field morphing + inline count | M | L | M |
| Route-intent parsing | M | M | M |
| Paste-to-extract PNR | M | L | M (P2 FR pulled forward cheaply) |

## 4.4 The Journey surface (Track + PNR, merged)

**Job:** everything about one journey, layered by how far you'll scroll. This is the signature surface; it replaces both the Track screen and the standalone PNR screen for any saved journey. (Bare lookups — a train you're just peeking at, a PNR you haven't saved — render the same surface without the "yours" affordances.)

### Layout: sheet over map

Tesla/Maps model: the map is the **canvas**, the content is a **sheet** floating over it at three detents. This finally realizes FR-2.2 and gives the shared-journey web view its visual.

```
   Detent 1 — GLANCE (default)          Detent 2 — TIMELINE (drag up)
┌─────────────────────────────┐      ┌─────────────────────────────┐
│  ← 12952 Tejas Rajdhani  ⋯  │      │  ← 12952 · ▶ 12 min late ●  │ ← header
│ ╭ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╮ │      │    (board collapses into    │   condenses,
│      m a p   (muted)        │      │     the header — one line)  │   fact stays
│ ·      route polyline       │      ├─────────────────────────────┤
│ ·         ◉ ~est.           │      │  ◉ You are here ~ between   │
│ ╰ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ╯ │      │    Itarsi and Hoshangabad   │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │      │  │                          │
│ ▓ ▶ 12 min late        ●  ▓ │      │  ● Itarsi Jn      16:02 ✓  │
│ ▓ Bhopal ~16:49 · Plat 4  ▓ │      │  │ 15:50 → 16:02 · Plat 3  │
│ ▓ Started yesterday       ▓ │      │  ◐ ─ ─ (you) ─ ─ ─ ─ ─ ─   │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │      │  ○ Hoshangabad   ~16:31    │
│  NDLS ●━━━━━━◐────○ BPL     │      │  │  ETA · Plat 1           │
│                             │      │  ○ BHOPAL ★      ~16:49    │ ← YOUR stop:
│ [ 🔔 Set arrival alarm ]    │      │  │  your stop · alarm set  │   emphasized,
│ ─────────── drag ───────────│      │  ▾ 6 more to terminus      │   ★+word
└─────────────────────────────┘      └─────────────────────────────┘
```

Detent 3 — FULL MAP (drag the sheet down): map takes the screen; estimated marker breathes; stations are dots; "estimated position" label pinned to the marker, always [FR-11.1].

### The board, upgraded

- Line 1 — **the fact**, flap-roll type, signal-coloured with icon+word: `▶ 12 min late` / `⏸ Not started` / `⛔ Cancelled`.
- Line 2 — **the consequence** (new): computed for `myStations` when known: "Bhopal ~16:49 · Platform 4". When the platform is from live data it's plain; when from history it reads "usually Platform 4" [FR-11.1]. Pre-departure with history available, this line carries the prediction: "usually ~30 min late by Itarsi" [FR-2.6].
- Line 3 — run-date chip ("Started yesterday"), diversion/reschedule amber banner when applicable [FR-2.4], freshness dot+label.
- The run-date **probe sheet** (today/yesterday, never a calendar [FR-2.3]) is unchanged in logic; visually it becomes two large board-styled cards with the *evidence* shown ("Left NDLS 17:55 yesterday · now near Itarsi" vs "Departs NDLS 17:55 today"), so the choice is a recognition task, not a memory task.

### The spine (timeline reborn)

One vertical line is the single spatial truth of the journey — the two disconnected representations (progress line + flat stop list) merge:

- **Nodes**: past stops solid `●` with actual times ("15:50 → 16:02" — scheduled struck through in ink3, actual in ink, mono digits); future stops hollow `○` with `~`ETAs; **you-are-here** `◐` sits *between* nodes at the interpolated fraction, labelled "estimated", with a slow breathing pulse (the only ambient motion on the screen — Part VI).
- **Your stops** get `★` + the word "your stop", larger type, and a subtle brand tint bar — the eye finds them in the scroll instantly. The sheet auto-opens scrolled so **now** is at the top; day boundaries are inline pills ("— Day 2 —") not section headers, saving vertical space [FR-2.3 multi-day].
- **Platform** is part of the node's line, mono, never truncated (the width bug class from `ui-architecture.md` §4 stays fixed by construction: times and platforms live in a fixed-width mono column).
- Collapsing: long routes show `▾ 6 more stops` accordions between your position and your stop's neighborhood — Google Maps' progressive disclosure; the full list is one tap.

### Coach guide (signature, elevated)

Today it's a row of tiles mid-scroll; it becomes a **platform-anchored diagram** — the platform edge drawn as a line, coaches sitting on it, an arrow for approach direction, and the plain-language answer *first*:

```
│  Your coach B4 stops near the FRONT      │ ← the answer, in words
│  ▶ approach                              │
│  ═══════════════════════════════════     │ ← platform edge
│  [ENG][A1][B4*][B3][SL][GEN][GEN]        │ ← B4 highlighted ★
│   front ·············· middle ····· rear │
│  ↺ Reverses at Itarsi — after that,      │
│    your coach is at the rear   [FR-3.2]  │
│  [ ⛃ GEN coaches ]  ← toggle highlights   │  [FR-3.3]
```

Words first, diagram second: the P5/low-literacy user reads the picture; everyone else gets the sentence. "Stand here" is the single most screenshot-shared answer in this category — it earns the visual investment.

### PNR as a pass (absorbed into Journey)

When a PNR is attached, the Journey surface grows a **pass section** — Apple Wallet grammar: one card, arm's-length legible, no chrome:

```
│ ┌───────────────────────────────────┐  │
│ │ 🎫 ••••2882          ✅ CNF ·CHART │  │ ← masked always; chart chip
│ │ NDLS → BPL · Sat 26 Jul · 3A      │  │
│ │ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │  │ ← perforation rule
│ │ P1  B4 · 32 · Lower     ✅ CNF    │  │
│ │ P2  B4 · 35 · Side Up   ⚠ WL 4    │  │ ← per-passenger [FR-4.1]
│ │     ~72% likely to confirm        │  │ ← labelled prediction [FR-4.4]
│ └───────────────────────────────────┘  │
│   Privacy: shown masked, encrypted,     │
│   deleted after your journey →          │  [FR-4.3]
```

- Chart states: `⏳ Waiting for chart · usually ~21:00` (neutral) → `✅ Chart prepared` (green, and the **celebration** now also lands in the Activity journal and as the push, not only if you're watching this screen).
- Waitlist: odds shown *as odds* with basis; poor odds surface "confirmed alternatives on this route" inline [FR-4.4] — the Plan pipeline invoked in place, not a navigation.
- A standalone PNR lookup (not yet saved) is this pass + a single action: **Save & watch** — which is what creates the Journey.

### Actions: one dock, contextual

The three equal sticky buttons (Mute/Coach/Save) become a **dock**: one primary action chosen by journey state + one `⋯` overflow:

| State | Primary | Why |
|---|---|---|
| upcoming, no chart | 🔔 Watch this journey (save) | The save *is* the alert opt-in |
| upcoming, chart window | 🎫 Chart status | The hour's actual question |
| riding, alarm unset | ⏰ Set arrival alarm | The keep-the-app-installed feature [FR-7.3] |
| riding, alarm set | ✓ Alarm set · edit | Confirmation posture |
| arriving | 🧳 Get ready — coach B4, front | The final answer |
| cancelled | → Find alternatives | The bad-day rescue [FR-2.4] |

Overflow: Share [FR-2.7], Mute [FR-7.4], Coach (jump), Report wrong info [FR-11.2], Unsave. **Why:** three equal buttons ask the user to rank; the app knows the state, so it should rank (P2/P3). "Coach" as a *jump* was navigation pretending to be an action — the dock frees it.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Sheet-over-map + estimated marker | H | H | H — FR-2.2 realized; the screenshot that sells the app |
| Consequence line | **H** | M | H |
| Unified spine | H | M | M |
| Platform-anchored coach guide | H | M | H — signature differentiator |
| PNR pass absorption | H | M | H |
| Contextual action dock | M | L | M |

## 4.5 Station board — glanceable like a real board

**Job:** "what's leaving, which platform," in sunlight, one-handed, in seconds (P3). The screen commits fully to the board metaphor — it *is* the departure board in your pocket.

```
┌─────────────────────────────────────────┐
│ ← Itarsi Junction        ☀ Sunlight  ⋯  │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓ 16:25  12952 Tejas Rajdhani      4  ▓ │ ← time · train · PLATFORM
│ ▓        → Mumbai   ✅ on time        ▓ │    mono, huge, aligned
│ ▓ ────────────────────────────────────▓ │
│ ▓ 16:40  12138 Punjab Mail         1  ▓ │
│ ▓        → Firozpur ⚠ 25 min late     ▓ │
│ ▓ ────────────────────────────────────▓ │
│ ▓ 17:05  11058 Amritsar Exp        —  ▓ │
│ ▓        ⛔ cancelled  [→ alternatives]▓ │  [FR-2.4]
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ [ Next 4h ▾ ] [ ⚲ filter ]   updated 45s│ ← controls BELOW content
└─────────────────────────────────────────┘
```

**The changes and why:**

1. **Rows on the board surface, board-typographic:** departure time and platform in large mono at fixed columns — the two facts a rushing person scans for. Train name second line, smaller. Status chip keeps icon+word+colour [FR-10.2]. A row is one ~72dp tap target → Journey surface.
2. **Controls sink below the content.** The window selector (2/4/8h) becomes one compact `Next 4h ▾` chip; all filters (class chips, destination text, on-time-only) fold into one **filter sheet** behind `⚲ filter` — with active filters summarized on the chip ("⚲ 3A · to Mumbai"). Prime pixels go to trains, and controls land in the thumb arc. The two competing filter systems (chips *and* a text field) become one.
3. **Sunlight mode** [FR-5.3, previously unshipped]: a fourth palette (Part V) — near-white ground, near-black ink, signal colours deepened for contrast ≥ 7:1, hairlines thickened, board surface inverted. Toggle in the header; auto-*suggested* (one dismissible chip, never auto-forced) when the ambient light sensor reads direct-sun levels.
4. **Platform-change emphasis:** when live data changes a platform, the platform cell flap-rolls and holds a 10-second amber underline — the board grammar for "look again," matched with a `warning` haptic tick if the app is foreground.
5. **"Near me"** stays permission-on-tap [FR-5.2]; when Home's context row launched the board, a quiet "📍 nearest to you" note explains why this station.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Board-typographic rows | H | L | M |
| Filter consolidation → sheet | M | L | M |
| Sunlight mode | H (for P3) | M | M — an FR gap closed |
| Platform-change flap+hold | M | L | M |

## 4.6 Plan — comparison, not form-filling

**Job:** decide between trains on time × price × seats (× punctuality, P2) — a *dwell* screen (P6), calm and comparative.

```
┌─────────────────────────────────────────┐
│ ← NDLS → BPL              [⇄]           │ ← route is the title;
│ ‹ Fri 25 · [Sat 26] · Sun 27 · Mon ›    │   day scrubber, not stepper
│ [ Booking normally ▾ ]                  │ ← quota, human words sheet
│ ────────────────────────────────────────│
│ Sort: [Departure] Price · Seats         │
│ ┌─────────────────────────────────────┐ │
│ │ 12002 Shatabdi        06:00 → 13:58 │ │
│ │ 7h 58m · daily        ✅ AVL 42     │ │
│ │ ₹1,240 · CC           usually on time│ │ ← punctuality (P2) as words
│ ├─ tap: fare breakdown expands ────────┤ │
│ │ base 940 · reserv. 40 · SF 45 ·     │ │
│ │ GST 65 · total ₹1,240        [FR-6.3]│ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ 12622 Tamil Nadu Exp  22:30 → 06:15 │ │
│ │ 7h 45m · Mon Thu      ⚠ WL 12 ~65%  │ │
│ │ ₹890 · 3A   [🔔 Tatkal opens 10:00] │ │  [FR-6.4]
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

1. **Route as title, form collapsed.** After the first search the from/to form folds into the header (`NDLS → BPL` + swap). Changing route = tap the title (reopens the omnibox route form). The screen becomes a results *place* you scrub, Notion-Calendar-style, instead of a form you resubmit.
2. **Day scrubber** replaces the ‹ › stepper: horizontally scrollable day chips (never before today [FR-2.3 spirit]), so "what about Sunday?" is one tap and the results **cross-fade in place** — comparison across days with zero re-entry.
3. **Sort vs hydration race, fixed:** rows hydrate fare/seats progressively [FR-6.2]; if the user sorts by price/seats before hydration completes, unhydrated rows sink to a "still checking…" tail with skeleton chips instead of sorting on nulls — the list visibly *settles* into order (motion: gentle reorder, Part VI) rather than lying, then a quiet "sorted" tick when complete.
4. **Quota in human words** [FR-6.4]: the chip row becomes a sheet — "Booking normally / Tatkal — opens 10 AM (AC) · 11 AM (non-AC) / Ladies / Senior citizen" with one line of explanation each; Tatkal selection shows the countdown *and* the "Remind me when it opens" watch inline.
5. **Seats as odds, not codes:** "AVL 42" reads green; "WL 12" pairs with its predicted-confirmation percentage as words ("~65% likely") when history exists [FR-4.4/P2], always labelled as prediction.
6. **Return-leg memory:** recent routes appear in Search's zero state (Flow 6); the swap button lives in the header, one tap, preserved from today.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Route-as-title + day scrubber | H | M | M |
| Sort-settle hydration | M | M | M — removes a trust papercut |
| Quota sheet in human words | H (P1/P5 personas) | L | M |
| Recent-route memory | M | L | M |

## 4.7 Activity — the journal that makes alerts trustworthy

**Job:** "what has Railcast done for me?" — the receipt for the product's hero promise. Two zones, journal first:

```
┌─────────────────────────────────────────┐
│ Activity                                │
│ Today                                   │
│ ● 04:12  ✅ Chart prepared  ••••2882    │ ← tap → Journey (pass)
│ ● 06:30  ⚠ 12952 now 25 min late       │
│ ● 07:02  🔁 Platform changed: 4 → 6     │
│ Yesterday                               │
│ ● 21:40  🔔 Watching 12952 (you saved) │ ← system actions logged too
│ ────────────────────────────────────────│
│ Controls                                │
│ Alerts: [Chart ✓][Delay ✓][Platform ✓]  │
│         [Cancel ✓][Arrival ✓][Tatkal ✓] │
│ 🌙 Quiet hours 22:00–06:30  (arrival    │
│    alarms still come through)   [FR-7.4]│
│ 🔕 Muted journeys: 12138 · unmute       │
│ 🈯 Language · 🛡 Privacy · ❓ Not getting │
│    alerts? (OEM guide)                  │
└─────────────────────────────────────────┘
```

- **Every push lands here first** and stays for the journey's life + 7 days (then purged with the journey's data [FR-4.3]) — dismissing the notification no longer deletes the fact. Entries are per-journey-grouped on the Journey surface too ("history" tab of the pass).
- **System honesty entries** ("Watching 12952 · checking every ~60 s near chart time") make the invisible Watcher visible — this converts FR-7.x from magic into evidence, which is what earns the ≥95% alert-trust goal emotionally, not just statistically.
- **Controls** keep every existing setting (toggles, quiet hours with the arrival-alarm exception stated *in place*, OEM battery guide for Xiaomi/Oppo/Vivo, language, privacy opt-out) — one screen down, still ≤ 2 taps from anywhere.
- The tab icon shows a **quiet dot** (no number badges — calm, P5) when unread entries exist.

| Aspect | UI | DC | BV |
|---|---|---|---|
| Alert journal | **H** | M | **H** — the trust receipt for the hero feature |
| Watcher honesty entries | M | L | H |
| Controls consolidation | M | L | M |

## 4.8 First launch & onboarding — from three decisions to one

Today: language → "what brought you here?" → tab. The second question is removed: its three answers (Track / PNR / Near me) are exactly the three shortcuts on empty Home. Instead of *asking* intent then routing, the empty Home *is* the intent picker — and it stays useful forever instead of being a one-shot survey.

1. **Screen 1 — language**, native-script cards (unchanged; it's excellent, and it re-renders onboarding live in the chosen language — keep that moment).
2. **Screen 2 — empty Home** (§4.2's empty state): big search, Speak, Trains near me. First keystroke = onboarding complete.

One quiet line under the search on first run only: "No ads. No login. Your ticket stays private." — the brand promise stated once, at the moment of maximum skepticism, then never again. Permissions stay strictly in-context (location on "near me" tap, notifications on first save) [PRD §7]. **Why:** every removed pre-value decision measurably lifts the ≥85% first-session success target; the fastest onboarding is the product itself being self-evident.

| UI | DC | BV |
|---|---|---|
| M | L | H (first-session success is a §2 metric) |

## 4.9 Notifications — the app's voice when closed

Notifications follow the same answer→consequence→action grammar:

- **Structure:** fact in the title ("Chart prepared ✅"), consequence in the body ("••••2882 · B4-32 confirmed · NDLS Sat 22:30"), actions as buttons ("View ticket", "Mute this train"). Never a bare "12952 update."
- **Ongoing journey notification (Android Live Updates):** while a journey is `riding`, one silently-updating ongoing notification renders the mini board: `▶ 12 min late · BPL ~16:49 · Plat 4` with the mini-spine as the progress bar. This is the Dynamic-Island-class surface Android now offers (`ProgressStyle` on 16+, graceful fallback to a standard ongoing notification), fed by the existing FCM path — no extra polling [NFR-3].
- **Arrival alarm** [FR-7.3]: full-screen intent, board-styled, one giant dismiss and one "Coach guide" button, spoken line in app language where enabled [FR-10.4]; pierces quiet hours by design and *says so* in Activity's controls.
- **Every notification deep-links** to its exact surface (Journey, pass section, board) and is journaled in Activity (§4.7).
- **Posture:** per-type opt-in, per-journey mute, quiet hours — all existing [FR-7.4]; plus a self-imposed budget: never more than one non-urgent notification per journey per hour (server-side coalescing) — silence is a feature (P5).

| Aspect | UI | DC | BV |
|---|---|---|---|
| Live Updates ongoing notification | **H** | M | **H** — glanceability without opening the app; a headline feature |
| Answer-grammar notifications + deep links | H | L | H |
| Coalescing budget | M | M | M |

---

# Part V — Design system 2.0

Evolution, not revolution: the existing `core/design/` structure (one file per concern, tokens over literals, brand-vs-signal separation) is correct. The system below extends it and gives it a name — **"Signalbox"** — so components, tokens, and rules can be referred to precisely.

## 5.1 The two-surface world

Every screen composes exactly two surface families, and the contrast between them *is* the visual identity:

- **Paper** — the app's calm ground (bg/surface/surface2): where you read, decide, configure. Quiet, warm-neutral, generous whitespace.
- **Board** — the dark departure-board surface: reserved exclusively for **live operational truth** (the board hero, station board rows, the ongoing notification, the arrival alarm). If it's on Board, it's live, mono-numeraled, and freshness-stamped. Never used decoratively — scarcity is what makes it read as "the truth is here."

This rule turns the existing `BoardHero` from one component into a *surface grammar*, and gives users a subconscious legibility shortcut: dark panel = glance here.

## 5.2 Colour tokens

Extends the existing palette keys (`Colors.kt`) — same names, plus the sunlight palette and three new roles. Values below are the proposed tuned set (existing values retained where they already pass contrast):

| Token | Light | Dark | Sunlight ☀ | Role |
|---|---|---|---|---|
| `bg` | `#E7ECEF` | `#081115` | `#FFFFFF` | App ground |
| `surface` | `#FFFFFF` | `#0F2129` | `#FFFFFF` | Cards |
| `surface2` | `#F1F4F6` | `#15303A` | `#F2F2F2` | Insets, chips |
| `ink` | `#0F2A33` | `#EAF3F4` | `#000000` | Primary text |
| `ink2` | `#54696F` | `#9DB4BB` | `#2A2A2A` | Secondary |
| `ink3` | `#8FA1A8` | `#6C868E` | `#555555` | Tertiary/decorative (never for facts in ☀) |
| `line` | `#DCE3E7` | `#1E3B45` | `#BBBBBB` (2dp) | Hairlines |
| `brand` | `#2743C4` | `#8098FF` | `#1D33A0` | **Tappable only** |
| `brandSoft` | `#EAEEFB` | `#182747` | `#D8DEF6` | Selected/tint |
| `green` | `#178A52` | `#3DBB77` | `#0B6B3C` | On time / confirmed |
| `amber` | `#C9770B` | `#E7A542` | `#8F5400` | Delay / warning |
| `red` | `#C33F3F` | `#E36868` | `#9E2B2B` | Cancelled / alarm |
| `*Soft` tints | (existing) | (existing) | omit — solid chips only | Chip fills |
| `board` | `#0F2A33` | `#06151A` | inverted: `#FFFFFF` ink `#000` | The live surface |
| `boardGreen/Amber` | `#3DE08A` / `#FFC24D` | same | `#0B6B3C` / `#8F5400` | Board signals |
| `boardRed` **(new)** | `#FF7A7A` | same | `#9E2B2B` | Board cancellation (today red is missing on board) |
| `boardInk` | `#7FA6AE` | `#6E969E` | `#333333` | Board secondary |
| `estimate` **(new)** | ink2 @ 60% + dashed | same | `#2A2A2A` dashed | Anything interpolated/predicted (P8) |
| `focus` **(new)** | `#2743C4` 2dp ring | `#8098FF` | `#000` 3dp | Keyboard/switch-access focus [FR-10.3] |

Rules (unchanged in spirit, now enumerated): brand ≠ signal, ever; colour never alone [FR-10.2]; all fact-bearing pairs ≥ 4.5:1 (≥ 7:1 in Sunlight); the `estimate` style (reduced opacity + dashed/soft edge) is **mandatory** on every interpolated value — honesty rendered (P8).

## 5.3 Typography

One humanist sans with first-class Indic coverage — **Noto Sans** family (guaranteed parity across all 12 target scripts [FR-10.1]) — plus the mono face for numerals. The scale is named by *job*, not size:

| Role | Face / size / weight | Used for |
|---|---|---|
| `board-xl` | Mono 44/48 · 600, tabular | The fact (board hero) — the biggest thing in the app |
| `board-m` | Mono 22/28 · 500 | Station-board times & platforms, card facts |
| `title` | Sans 24/30 · 650 | Screen titles, train names in headers |
| `body` | Sans 16/24 · 450 | Everything readable |
| `label` | Sans 14/20 · 550 | Chips, buttons, node labels |
| `caption` | Sans 12/16 · 450 | Freshness, honesty labels, hints |
| `numerals` | Mono via `monoNumerals()` | Every digit inside prose — existing rule, kept app-wide |

- Tabular numerals everywhere digits can change under the reader (no jitter on refresh — already a PRD requirement, now guaranteed by the mono ramp).
- Font-scale: honour OS setting, cap 1.3× at the root (existing, kept); `board-xl` additionally auto-shrinks-to-fit its card rather than wrapping (a delay can be "112 min late" in Hindi — it must never wrap the flap-roll).
- Indic line-height: +10% on Devanagari and Tamil blocks (vertical conjuncts clip at Latin line-heights) — set at the theme level once, not per screen.

## 5.4 Iconography

Keep the existing approach (hand-inlined `ImageVector`s, no icon font) and codify the style: **2dp stroke, rounded caps, 24dp grid, literal pictograms** (train, platform edge, berth, ticket, bell, moon). New glyphs needed by the redesign: ticket/pass, platform-edge, coach, alarm-clock, journal-dot, swap-arrows, sun (sunlight mode), spine-node set (●◐○★). Rule kept: every interactive icon has an adjacent word or a `contentDescription`-merged label; decorative icons are `null`-described [FR-10.3]. Icons are always tinted tokens, never baked colours.

## 5.5 Component library (Signalbox)

The buildable inventory. ✎ = evolve existing, ＋ = new.

| Component | Origin | Contract (essence) |
|---|---|---|
| `JourneyBoard` | ✎ `BoardHero` | fact + consequence + freshness + level; flap-roll on fact change only |
| `JourneyCard` | ＋ (uses `JourneyBoard`) | hero / compact variants; state-driven layout; swipe actions |
| `Spine` / `SpineNode` | ＋ | past/future/you-are-here/your-stop nodes; collapse ranges; day pills |
| `MiniSpine` | ＋ | the one-line ●━━◐──○ progress strip (cards, notification) |
| `PassCard` | ＋ | masked PNR, chart chip, passenger rows, perforation rule |
| `CoachDiagram` | ✎ `CoachLayout` | platform-anchored, approach arrow, reversal note, GEN toggle |
| `BoardRow` | ＋ | station-board row: mono time/platform columns, status chip |
| `OmniSearchField` | ✎ (4 fields → 1) | classifier-driven glyph/hint morphing; voice; paste-extract |
| `ActionDock` | ✎ sticky bar | one primary (state-chosen) + overflow |
| `FilterSheet` / `FilterChip` | ＋ | consolidated filters w/ active summary |
| `DayScrubber` | ＋ | horizontally scrubbed day chips; never past |
| `QuotaSheet` | ＋ | human-language quota picker + Tatkal countdown/watch |
| `JournalRow` | ＋ | Activity entry: time, icon+word, masked refs, deep link |
| `ContextRow` | ＋ | Home's single situational suggestion; dismiss-and-stay-dismissed |
| `StatusChip` | keep | icon+word+colour — untouched, it's right |
| `Freshness` | keep | dot+label — untouched |
| `Skeleton`, `SegmentedControl`, `ErrorState`, `OfflineBanner` | keep | existing `ui/` set |
| `SheetScaffold` | ＋ | 3-detent sheet-over-map host for Journey |

Composition rule (existing, kept): a pattern extracted only on its second use; screens stay thin over ViewModels.

---

# Part VI — Motion, micro-interactions & haptics

## 6.1 The three laws of Railcast motion

1. **Motion = meaning.** Every animation encodes a state change (data changed, surface continued, severity shifted). Zero decorative loops. The existing hard rule — only `PollController` owns time; UI animates only on state change — is the enforcement mechanism and stays law.
2. **The board is the soul.** The flap-roll is the one theatrical move, reserved for facts changing on Board surfaces (hero, platform cell, ongoing notification). Everything else is quiet easing.
3. **Reduced-motion is a first-class rendering,** not a disabling: flap-roll → crossfade, marker glide → step, breathing pulse → static `~` badge. Meaning survives; theatre goes [FR-10.3].

## 6.2 Duration & easing ladder

| Token | Value | Use |
|---|---|---|
| `instant` | 90ms · linear | Chip select, toggle knob |
| `quick` | 180ms · standard decel | Crossfades, chip morphs, list reorder start |
| `settle` | 280ms · emphasized decel | Sheet detents, card expand, shared-element continuation |
| `roll` | 420ms · spring (0.8 damping) | The flap-roll, colour ease on severity change |
| `breathe` | 2400ms · sine, ±12% opacity | *Only* the estimated-position marker (the one ambient motion, and it's an honesty signal: alive-but-approximate) |

## 6.3 The signature micro-interactions

- **Card → Journey continuation:** the Home card's `JourneyBoard` is the *same element* as the Journey surface's board — shared-element transition (`settle`); the sheet and map fade in beneath it. The user never "navigates"; the card *becomes* the screen. This one transition is most of the perceived premium.
- **Flap-roll** (kept, extended): vertical slide+fade keyed on the fact string; now also on station-board platform cells and the Live Updates notification. Change-driven only — a no-change poll tick must be visually silent (backoff makes most ticks no-ops).
- **Severity colour ease** (kept): green→amber→red eases over `roll`, never snaps.
- **Marker glide:** the map/spine marker eases position on data change (`settle`), with the breathing pulse between changes.
- **List settle** (Plan): rows reorder with `quick` translate when hydration completes a sort — the list is visibly *finding its truth*.
- **Chart-prepared celebration:** one green flap-roll to "Chart prepared ✅", one `success` haptic, one subtle 600ms shimmer across the pass — dosed, then done. (No confetti. Calm is the brand.)
- **Pull-to-refresh** [FR-2.5]: a small signal-dot trio that settles into the freshness stamp — train-themed restraint, on every live surface.
- **Bad-day entrance:** cancellation never animates playfully — the board crossfades (no roll) to red `⛔ Cancelled`, one `warning` haptic, and the alternatives action slides up `settle`. Severity earns sobriety.

## 6.4 Haptic map (Android `HapticFeedbackConstants` / vibration effects)

| Event | Haptic |
|---|---|
| Chip/toggle/segment | `tick` (system) |
| Fact changed on a visible board | single light `tick` — the "flap" made physical |
| Chart prepared / arrival at your stop | `confirm` double-tap |
| Platform change / delay past threshold | `warning` heavy single |
| Cancellation | `warning` ×2, spaced 120ms |
| Sheet detent snap | `segment tick` |
| Never | scroll ticks, keyboard, decorative anything |

Haptics respect the system setting and are attached to the *same* state changes as motion — one grammar, three channels (visual, haptic, and for FR-10.4, spoken).

---

# Part VII — States: loading, empty, error, offline

The existing SWR discipline (cached-first, never blank) is kept wholesale. The redesign standardizes the *vocabulary* — five states, one grammar, every surface:

| State | Rendering | Copy register |
|---|---|---|
| **Fresh** | Full colour, green freshness dot, "just now" | — |
| **Loading (no cache)** | Skeletons that exactly match final layout (existing `Skeleton`); board surfaces skeleton in board-dark | Never a spinner on a full screen |
| **Stale/offline** | Full data, muted freshness dot, "6 min ago · offline"; global `OfflineBanner` once, not per-card; signal colours desaturate 20% (data is old — its urgency is too) | "Showing last known — we'll refresh when you're back online" |
| **Empty** | One illustration line, one directive sentence, one action | "No journeys yet — track any train in India" |
| **Error (no cache)** | `ErrorState`: plain words + one retry + one alternative path | "Couldn't reach the network. [Try again] · or [SMS 139]" |

New in the redesign:

- **Offline actions queue visibly:** save/mute/alarm changes made offline show "will apply when online" on the control and reconcile via the existing repo layer — never a failed tap.
- **SMS-139 bridge** [FR-9.3] surfaces *only* in offline error states on live surfaces: "No signal? Get status by SMS →" composes the correctly formatted message — the honest no-data fallback, framed as such.
- **Desaturation-on-stale** is the one new global rule: stale red must not scream as loudly as live red. Urgency is a property of *fresh* information. (Icon+word remain full-strength — only colour relaxes, so FR-10.2 redundancy is preserved.)

---

# Part VIII — Accessibility & inclusivity

Everything existing is kept (48dp targets, merged TalkBack nodes, string parity CI, font-scale honoured/capped, icon+word+colour). The redesign adds:

1. **Glance-mode contrast audit as CI-checkable tokens:** every Paper pair ≥ 4.5:1, Board pairs ≥ 4.5:1, Sunlight ≥ 7:1 — encoded in a palette unit test (same spirit as `StringsParityTest`), so a token tweak can't silently fail a persona.
2. **TalkBack reads the answer skeleton in order:** fact → consequence → freshness → action ("Twelve minutes late. Arriving Bhopal around four forty-nine, platform four. Updated just now. Set arrival alarm, button."). The Journey surface sets traversal order explicitly; the spine announces "your stop" nodes with the star *named*.
3. **Live regions, politely:** the board hero is a polite live region (announces on fact change, not on poll ticks — the change-keyed rule again); cancellation is assertive.
4. **Spoken status** [FR-10.4] shares the consequence-line generator: one sentence source → screen, notification, and TTS — parity across channels for free, in all languages.
5. **One-handed by construction:** primary actions (dock, docked search, filter chip, tab bar) all in the bottom 40% of the screen; nothing fact-critical *only* in the top third; sheet detents draggable from anywhere on the sheet.
6. **Switch access & keyboard:** visible `focus` ring token (§5.2); scrubbers and sheets fully operable via focus + select (each detent is a focusable stop).
7. **Low-literacy audit ritual:** every new pictogram (coach diagram, spine nodes, quota icons) goes through the PRD's tested-with-low-literacy-users loop before shipping — the diagram-first coach guide (§4.4) is designed to be readable with zero words.

---

# Part IX — Android / Jetpack Compose implementation

## 9.1 What does *not* change

`PollController` (cadence, backoff, lifecycle), `Resource<T>` SWR pipeline, `ScreenCache`/Room, `DeviceSession` auth, `DirectorySearch`, `IsoTime`/`Freshness`, `monoNumerals`, `StatusChip`, string-parity CI, MVVM-with-interfaces testing shape. The redesign is a **presentation-layer** reorganization over the same data machinery — that is what makes it feasible.

## 9.2 New composition hierarchy

```
MainActivity
 └─ LocalizedContent → RailcastTheme(palette = Light|Dark|Sunlight)
     └─ Onboarding (language only)  OR  RailcastApp
         └─ Scaffold(bottomBar = RailcastBottomBar /* 3 tabs */)
             └─ NavHost
                 ├─ home      → HomeScreen        (JourneyStackViewModel)
                 ├─ search    → SearchScreen      (OmniSearchViewModel)
                 ├─ activity  → ActivityScreen    (JournalViewModel)
                 ├─ journey/{trainNo}?pnr&date    (JourneyViewModel)
                 │    └─ SheetScaffold(map = RouteMap, sheet = JourneySheet)
                 │         ├─ JourneyBoard → Spine → CoachDiagram → PassCard
                 │         └─ ActionDock
                 ├─ station/{code}                (StationViewModel — kept)
                 └─ plan?from&to&date             (PlanViewModel — kept)
```

- **`JourneyViewModel` = merge of `TrackViewModel` + `PnrViewModel`** behind one `JourneyUiState` (train `Resource` + optional pnr `Resource` + derived `JourneyState` + `ConsequenceLine`). Both existing test suites carry over; the derivation of state/consequence is a pure function with its own JVM test (`JourneyDeriveTest`) — same testing philosophy as `MonoNumeralsTest`.
- **`ConsequenceLine`** is a pure formatter: `(trainScreen, myStations?, now) → AnnotatedString` — unit-tested, shared by screen, notification builder, and TTS (§ Part VIII.4).
- **Shared-element card→surface:** Compose 1.7+ `SharedTransitionLayout` keying the `JourneyBoard` by journey id; fallback (older devices / reduced motion) is a crossfade — the API degrades cleanly.
- **`SheetScaffold`:** `BottomSheetScaffold`-style with three anchored detents via `AnchoredDraggable`; map underneath is a plain `Canvas` polyline over projected station coordinates (already in the static directory data per FR-9.2) — **no Maps SDK dependency** for v1: a stylized route canvas keeps APK ≤ 25 MB [NFR-1], works fully offline, and matches the calm aesthetic better than tile maps. Tile maps can arrive later behind the same composable contract.
- **Live Updates notification:** `NotificationCompat` ongoing + `ProgressStyle` (Android 16+) with segments from the spine model; pre-16 falls back to a standard ongoing notification with the same text grammar. Driven by FCM pushes and (foreground) the same `Resource` flow — no new polling [NFR-3].
- **Journal storage:** a small Room table (`journal_entries`: time, type, journeyId, masked refs, deep link); written by the FCM receiver and by watch-registration events; purged with journey purge [FR-4.3].
- **Omnibox classifier:** pure Kotlin (`QueryClassifier`) layered on `FormatValidation` + `DirectorySearch`; exhaustively JVM-tested (digit shapes, route grammars in EN + transliterated forms, paste extraction).
- **Palette third variant:** `RailcastColors.Sunlight` alongside Light/Dark; selection = user toggle ∨ ambient-light suggestion; plumbing already exists (palette via CompositionLocal).

## 9.3 Performance budget (NFR-1 enforcement)

Cold launch ≤ 2.5 s on entry-level hardware: Home renders from Room on frame one (existing SWR); the map canvas defers until the Journey surface; flap-roll and shared-element are `graphicsLayer`-only (no relayout); LazyColumn keys are stable ids everywhere (spine nodes keyed by station code) so poll updates recompose only changed rows; baseline profile covers launch → Home → Journey. APK: no new heavyweight deps (no Maps SDK, no Lottie — all motion is Compose-native), keeping < 25 MB installed.

## 9.4 Testing shape (unchanged philosophy, new coverage)

JVM-only, fakes over frameworks: `JourneyDeriveTest`, `ConsequenceLineTest`, `QueryClassifierTest`, `JournalPolicyTest` (quiet-hours filtering, purge), `SpineCollapseTest` (which ranges fold), palette-contrast test (Part VIII.1). Screens stay thin; on-device verification for visuals, as today.

---

# Part X — The top 25 improvements, ranked

Ordered by (User Impact, then Business Value, then inverse Complexity). ★ = flagship differentiator no incumbent has.

| # | Improvement | Why it matters | UI | DC | BV |
|---|---|---|---|---|---|
| 1 | **The Journey object** (§3.1) | Unifies train+PNR+alerts into the user's actual mental model; everything else hangs off it | H | H | H |
| 2 | **Consequence line** (§4.1) ★ | Converts data into *your* answer — "will I reach on time" in zero taps; the 2-second goal, met | H | M | H |
| 3 | **Home = journey stack with live takeover** (§4.2) | The habit loop: open app → answer, no navigation | H | M | H |
| 4 | **Omnisearch with input classification** (§4.3) | Kills the mode toggle and 4 duplicate search boxes; first-session success | H | M | H |
| 5 | **Activity journal** (§4.7) ★ | Makes the hero feature (proactive alerts) *provable*; alerts you can't lose | H | M | H |
| 6 | **Live Updates ongoing notification** (§4.9) ★ | The train in your notification shade, Flighty-class; zero-open glanceability | H | M | H |
| 7 | **Sheet-over-map Journey surface** (§4.4) | FR-2.2 finally real; the screenshot that markets the app | H | H | H |
| 8 | **Platform-anchored coach guide** (§4.4) ★ | The most shareable answer in the category, now diagram-first and reversal-aware | H | M | H |
| 9 | **PNR as a pass** (§4.4) | Ticket-shaped ticket; chart status + per-passenger clarity at arm's length | H | M | M |
| 10 | **Contextual action dock** (§4.4) | The app ranks actions by state; "what should I do next" answered | M | L | M |
| 11 | **Unified spine timeline** (§4.4) | One spatial truth; you-are-here anchored *in* the schedule | H | M | M |
| 12 | **Bad-day flow: cancellation → prefilled alternatives** (Flow 5) | The switch-moment handled with grace; trust compounding | H | M | H |
| 13 | **Sunlight mode** (§4.5) | FR-5.3 shipped; P3's platform-in-June reality | H | M | M |
| 14 | **Board-typographic station rows** (§4.5) | Time+platform scannable in ~1 s; the board metaphor earns itself | H | L | M |
| 15 | **Onboarding to one question** (§4.8) | Every removed decision lifts first-session success | M | L | H |
| 16 | **Shared-element card→surface continuity** (§6.3) | The single transition that makes the whole app feel engineered | M | M | M |
| 17 | **Day scrubber + route-as-title Plan** (§4.6) | Planning becomes scrubbing, not form-resubmitting | H | M | M |
| 18 | **Chart-prepared moment, full-channel** (§6.3) | The emotional peak: push + journal + pass shimmer + haptic — never missed again | M | L | H |
| 19 | **Home context row** (§4.2) | Citymapper-class situational intelligence, dosed to one row | M | M | M |
| 20 | **Sort-settle hydration in Plan** (§4.6) | Never sort on nulls; visible truth-finding; removes a trust papercut | M | M | M |
| 21 | **Stale-desaturation rule** (Part VII) | Old urgency shouldn't scream; honesty rendered system-wide | M | L | M |
| 22 | **Haptic grammar** (§6.4) | Status you can *feel* in a pocket; third redundancy channel | M | L | M |
| 23 | **Quota sheet in human words + Tatkal watch inline** (§4.6) | De-jargons the scariest part of Indian rail booking | M | L | M |
| 24 | **Run-date probe as evidence cards** (§4.4) | Recognition over recall for the overnight problem [FR-2.3] | M | L | M |
| 25 | **Offline action queue + SMS-139 placement** (Part VII) | Dead-signal moments still act; the fallback framed honestly | M | M | M |

## What we still never do

No ads. No interstitials. No forced login. No notification spam. No fake urgency. No dark patterns. No gesture-only core actions. No decorative motion. The redesign adds zero exceptions to the §7 product law — and several of its choices (journal over badges, one context row, coalesced notifications, celebration-then-done) exist specifically to keep the calm promise while the product gets more capable.

---

# Part XI — Migration plan

Sequenced so each stage ships alone, is testable alone, and never breaks an invariant. Contract changes (if any) follow the repo rule: `docs/api-contracts.md` + regenerated `packages/shared` in the same PR.

| Stage | Contents | Risk |
|---|---|---|
| **0. Tokens** | Sunlight palette, `boardRed`/`estimate`/`focus` tokens, duration ladder, type roles; palette-contrast test | Low — additive |
| **1. Components** | `JourneyBoard` (evolve `BoardHero`: + consequence line), `MiniSpine`, `PassCard`, `BoardRow`, `ActionDock`, `Spine` | Low — parallel to old UI |
| **2. Journey surface** | `JourneyViewModel` merge, sheet scaffold (map can start as the existing progress line, canvas map next), spine, dock; Track routes point here | Medium |
| **3. Omnisearch** | `QueryClassifier`, one field component replacing four; Train·PNR toggle removed | Medium |
| **4. IA switch** | 3-tab shell, Home journey stack, Activity journal (+ Room table, FCM receiver hook); old tabs' routes become detail surfaces | High — feature-flag the shell, keep Option-B fallback ready |
| **5. Delight** | Shared-element, haptic map, Live Updates notification, context row, sunlight auto-suggest | Low — additive polish |
| **6. PRD sync** | Amend §7 (tabs), §4 placement language; update `app-guide.md` + `ui-architecture.md` | Docs |

Each stage cites its FRs in commits per repo convention (e.g., `feat(journey): consequence line on board [FR-2.1, FR-2.6]`).

---

*The essence, restated: Railcast already tells the truth faster than anyone. This redesign makes it feel like a companion who was already watching your train before you asked — one object (the journey), one input (the omnibox), one receipt (the journal), one theatrical move (the flap-roll), and a calm so consistent it reads as luxury on a ₹8,000 phone.*




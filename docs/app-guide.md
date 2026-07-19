# Railcast — App Guide

What Railcast is, what every screen does, and every button/search a user can touch. Written so a non-engineer (support, product, a new teammate) can understand the whole app without reading code.

For *how it's built* (Kotlin/Compose architecture), see [`docs/ui-architecture.md`](./ui-architecture.md). For the API it talks to, see [`docs/api-contracts.md`](./api-contracts.md). For requirements, see [`docs/PRD.md`](./PRD.md).

---

## 1. What Railcast is

An **ad-free Indian Railways companion app**: track a live train, check a PNR, see a station's departure board, plan a journey, and get pushed alerts (delay, platform change, chart prepared, cancellation, arrival) — with no login, no ads, and no dark patterns, ever.

It has two halves:
- **Android app** (this guide) — Kotlin + Jetpack Compose.
- **Backend** (BFF + Watcher) — talks to the upstream RailKit API, caches responses, and watches trains/PNRs in the background to push alerts even when the app is closed.

---

## 2. First launch — Onboarding

Two screens, no login, no tutorial carousel:

1. **Language picker** — native-script buttons (English / हिन्दी). Tapping one re-renders the *rest of onboarding itself* live in that language, so the choice is felt immediately, not just remembered.
2. **"What brought you here?"** — one question, three cards:
   - **Track a train** → drops the user on the **Track** tab
   - **Check a PNR** → drops the user on **Home** (where PNR lookup lives)
   - **Trains near me** → drops the user on the **Station** tab

Whichever card is tapped becomes the app's starting tab for that first session; every later launch goes straight to **Home**.

---

## 3. The five tabs

Bottom navigation, always visible once past onboarding:

| Icon | Tab | One-line job |
|---|---|---|
| 🏠 | **Home** | Search a train or check a PNR; see your saved trains as live cards |
| 🚆 | **Track** | The live board for one train — status, position, timeline, coach guide |
| 📍 | **Station** | A station's live arrivals/departures board |
| 📅 | **Plan** | Find trains between two stations for a date, with fares and seat status |
| 🔔 | **Alerts** | Notification preferences, quiet hours, language switch, privacy |

Tapping a tab always returns to where you left it (Android's standard back-stack-per-tab behavior).

---

## 4. Screen-by-screen

### 4.1 Home

**Job:** the launcher — search, or jump straight to a saved train.

- **Greeting** — a quiet "Where to?" line.
- **Train · PNR toggle** — a two-way switch above the search box. **Train** (default) searches the train/station directory; tapping **PNR** opens the PNR screen (§4.5) directly.
- **Search field** — type a train name, train number, or station name/code. Has:
  - a **search glyph** (decorative)
  - a **microphone button** — taps into Android's built-in speech-to-text; whatever you say is typed into the box for you. No special permission dialog beyond Android's standard mic prompt.
  - **live results** appear below as you type (after a short debounce), each showing the match and whether it's a train or a station
  - typing a pure number is validated as a train-number-in-progress and shows an inline hint if it's the wrong length
- **Saved trains** — once you tap a train result, it's saved here as a live card: train name/number, current status (e.g. "▶ Running · 12 min late"), and a freshness stamp ("just now", "6 min ago", "3 d ago · offline"). A green dot means live data; a grey dot means the shown data is stale/cached (e.g., no internet right now) — but it's always shown, never a blank screen.
- **Remove** — under each saved card, un-saves that train.

### 4.2 Track

**Job:** the live status of one train — Railcast's signature screen.

**Before a train is picked:** a search box (same directory search as Home).

**Once a train is open**, top to bottom:
- **Back arrow + train name/number** header.
- **The board** — a dark "departure board" card: the current status in large numbers (e.g. "▶ 12 min late"), colored green/amber/red by severity, with a freshness dot+label underneath. When the status changes (e.g. delay goes from 12 to 14 minutes), the number **rolls** into place like a real split-flap station board.
- **Run-date chip** — e.g. "Started yesterday" or "Today · 18 Jul". The app auto-detects which day's run is currently active; it never asks the user to pick a raw calendar date. If genuinely ambiguous, a small sheet offers "today" vs "yesterday" as the only two choices.
- **Amber banner** — appears only if the train is diverted or rescheduled, in plain language.
- **Estimated position** — a labelled progress line between the last-seen station and the next one, always captioned "estimated" (never claims GPS-precision).
- **Coach guide** — a row of coach tiles (ENG, SLRD, GEN, your coach highlighted) with a reversal note if the train reverses direction at some station (so the coach order flips). A **GEN toggle** (seat icon + label) highlights all unreserved coaches when tapped.
- **Timeline** — every stop, grouped by "Day 1 / Day 2" headers, each with scheduled → actual time and platform.
- **Sticky action bar** (bottom, always visible while scrolling): three buttons —
  - **Mute** — silences push alerts for just this train.
  - **Coach** — jumps the screen down to the coach guide.
  - **Save/Pin** — saves this train to Home's saved list (or removes it).

### 4.3 Station

**Job:** what's arriving/departing at one station, right now.

- **Search box** for a station name or code.
- **"Trains near me"** chip — uses coarse device location (permission asked only when tapped, with the reason on-screen) to suggest the nearest stations.
- **Time-window toggle** — 2h / 4h / 8h, defaults to 4h.
- **Filter chips** — "On time only" plus one chip per travel class actually running through that station (1A, 2A, SL, …); tapping a class narrows the board to trains offering it.
- **Destination filter** — a text field (with a search glyph) to narrow the board to trains headed toward a particular place.
- **The board** — each row: train name/number, route (source → destination), scheduled/actual time, platform, and a status chip (icon + word + color: on time / delayed N min / cancelled).
- **Cancelled row** → a "See alternatives" button that hands off straight to Plan with this train's route pre-filled.

### 4.4 Plan

**Job:** find trains between two stations for a given date and see fares/seats.

- **From / To fields** — station search, same directory.
- **Swap button** — between the two fields; one tap reverses origin and destination (for planning the return leg without retyping).
- **Date stepper** — ‹ / › arrows around a friendly date ("Sat, 18 Jul"); can't step before today.
- **Quota chips** — General / Tatkal / Ladies / Senior.
  - Picking **Tatkal** shows a countdown hint for when the Tatkal booking window opens.
- **Find trains** button — enabled only once both stations are picked.
- **Sort row** — Departure / Price / Seats.
- **Results** — one card per train: name/number, departure–arrival with duration, which days it runs ("Runs: Mon, Thu" or "Runs daily"), seat availability (color-coded chip), and fare. Tapping a row expands it in place to a full fare breakdown (never navigates away). Fare/availability may show "Checking…" briefly since each row loads its own pricing after the list appears — the list itself never waits on the slowest row.
  - Where a **Tatkal-eligible** row exists, a **"Remind me when Tatkal opens"** chip creates a one-tap reminder watch for that booking window.

### 4.5 PNR (reached from Home)

**Job:** check a ticket's confirmation/chart status.

- **Input field** — the 10-digit PNR, with format validation and a hint on mistakes.
- **Privacy note** — a plain-language line explaining the PNR is shown masked, encrypted at rest, and deleted after the journey (linked from here).
- Once looked up: the PNR is shown **masked everywhere** (e.g. `••••2882`) — never in full, in the UI or anywhere else.
- **Chart status chip** — "Chart prepared" (green) or "Waiting for chart" (neutral clock icon).
- If the chart just got prepared while the user is looking, a **celebration banner** appears ("Chart's out! 🎉") — dismissible.
- **Save button** — saves this PNR and creates a background watch, so the user gets pushed the moment the chart is actually prepared (even app-closed).

### 4.6 Alerts

**Job:** control what pushes you get and when, plus app-wide settings.

- **Alert type toggles** — one switch each for: Chart prepared, Delay, Platform change, Cancelled/diverted, Arrival alarm, Tatkal open. All on by default.
- **Quiet hours** — a toggle plus a from/to time stepper (30-min steps); when on, non-urgent alerts are silenced in that window (an arrival alarm still breaks through — you don't want to miss your stop).
- **"Not getting alerts?"** — a collapsed disclosure, shown only on phone brands known for aggressive battery restrictions (Xiaomi/Oppo/Vivo). Expands to plain-language steps + a button straight to the app's system settings.
- **Privacy** — a toggle to opt out of anonymous usage analytics (no PNR contents are ever in that data, on or off).
- **Language** — switch English ⇄ हिन्दी; the whole app re-renders instantly, no restart.

---

## 5. Notifications (push alerts)

Even with the app closed, Railcast can push:
- **Chart prepared** — for a saved/watched PNR.
- **Delay** crossing your threshold.
- **Platform change.**
- **Cancelled / diverted.**
- **Arrival alarm** — a full-screen alert as your station approaches (bypasses quiet hours).
- **Tatkal window opening** — for a train you asked to be reminded about.

Every push is filtered on-device by your Alerts preferences and quiet hours before it's shown, and any train can be muted individually from its Track screen.

---

## 6. Cross-cutting behavior (every screen follows these)

- **Offline never means blank.** Every screen shows its last-known data with a small "offline" note, rather than an empty or broken screen.
- **Status is never color-only.** Every status always pairs a color with an icon *and* a word (so it's readable color-blind, and in a glance or a screen reader).
- **Freshness is always visible.** Any live figure carries "just now / 6 min ago / 3 d ago" so you know how current it is.
- **No raw dates asked of the user.** Track's run-date is auto-detected (today/yesterday only, never a calendar); Plan's date stepper never goes into the past.
- **No forced anything.** No login wall, no ad, no dark pattern, anywhere, for any core feature.
- **Dark and light themes** are both fully designed (not just inverted), and the OS's large-text accessibility setting is honored (capped so it can't break a layout).

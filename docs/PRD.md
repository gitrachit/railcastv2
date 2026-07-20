# Railcast — Product Requirements Document (PRD)

**Version:** 1.0 · **Date:** July 2026 · **Status:** Draft for review
**Supersedes:** Product Plan, API & Real-Time Plan, UX Plan, Gap Analysis (all folded in here)

---

## 1. Overview

### 1.1 Product summary
Railcast is an ad-free, multilingual Indian railways companion app (Android + iOS + lightweight web) built on the RailKit API. It provides live train tracking, PNR status with proactive alerts, station live boards, and unified journey planning — wrapped in a calm, answer-first experience usable by every age and literacy level.

### 1.2 The problem
Incumbents (Where is My Train and clones) have the data but a hostile experience: aggressive full-screen ads at the user's most time-pressed moments, cluttered screens, no intelligence beyond the live snapshot, and poor handling of bad-day scenarios (cancellations, overnight-date confusion, waitlists). Users tolerate them; nobody loves them.

### 1.3 The bet
Railcast does not out-track WIMT (its offline cell-tower positioning is a real moat we don't attack). Railcast out-thinks it: prediction from history, reversal-aware coach guidance, unified fare+seats+punctuality planning, proactive server-side alerts — delivered ad-free, in 12 languages, fast on a ₹8,000 phone. The moat is synthesis and experience, not raw data.

### 1.4 Non-goals (v1)
- No ticket booking in-app (informational handoff only, pending IRCTC/partner terms verification).
- No offline GPS/cell-tower positioning (we bridge via cache + SMS-139 instead).
- No food ordering / eCatering (potential Phase 4 partnership).
- No social features beyond journey sharing.

---

## 2. Goals & success metrics

| Goal | Metric | Target (12 mo post-launch) |
|---|---|---|
| Fast answers | Time-to-answer from cold launch (common case) | ≤ 5 s, ≤ 2 taps |
| All-ages usability | First-session unaided success ("track a train") in usability tests incl. 55+ and low-literacy users | ≥ 85% |
| Habit | Users with ≥1 saved train/PNR returning within 14 days | ≥ 45% |
| Growth loop | Installs attributed to shared-journey links | ≥ 15% of new installs |
| Trust | "Wrong info" reports per 1,000 sessions | < 2, trending down |
| Performance floor | Crash-free sessions on entry-level Android | ≥ 99.5% |
| Alert reliability | Chart-prepared push delivered within 5 min of chart prep | ≥ 95% |

---

## 3. Users

- **P1 The anxious tracker** — tracks a family member's train; wants honest ETA and a live view; may be elderly, vernacular-first.
- **P2 The frequent booker** — waitlist-watcher; wants confirmation odds, chart alerts, alternatives.
- **P3 The at-station traveler** — needs "what's leaving, which platform" in seconds, in sunlight, one-handed.
- **P4 The planner** — compares trains on price + seats + punctuality before booking elsewhere.
- **P5 The unreserved (GEN) traveler** — crores strong, ignored by every app; needs to know where GEN coaches stop on the platform.
- **P6 The setter-upper** — younger family member configuring the app for an elder; needs family sharing and one-time simple setup.

---

## 4. Scope — functional requirements

Requirements are numbered for traceability. **[P1/P2/P3]** = release phase (§9). "Must/Should" per usual convention.

### 4.1 Search & directory (gap fix — critical)

- **FR-1.1 [P1]** App MUST bundle a static directory dataset: all trains (number ↔ name) and stations (code ↔ name ↔ city), enabling fuzzy, typo-tolerant, client-side autocomplete. Users search by *name or number*; the directory resolves names → numbers/codes before any API call. Works offline.
- **FR-1.2 [P1]** Directory MUST update via small delta downloads (e.g., weekly check), never blocking app use.
- **FR-1.3 [P2]** Directory MUST carry train and station names in all 12 supported languages (dataset build workstream WS-B); search accepts native-script input.
- **FR-1.4 [P1]** Voice search MUST be available on all search fields (device speech-to-text feeding the same autocomplete).
- **FR-1.5 [P1]** Client MUST validate formats pre-call (train = 5 digits, PNR = 10 digits) with gentle inline correction, never a server error.

### 4.2 Live train tracking

- **FR-2.1 [P1]** Track screen MUST show: current status (running/arrived/not started/cancelled), last-crossed station, next station with ETA, per-station delay timeline (scheduled vs actual), platform numbers where available.
- **FR-2.2 [P1]** Map view MUST render the route polyline from station coordinates and an **interpolated position marker** between last-crossed and next station, explicitly labelled "estimated position" — never implied as GPS.
- **FR-2.3 [P1] Overnight-date handling (gap fix).** The app MUST NOT ask users for a raw journey date to track a live train. Primary UI offers "Started today / Started yesterday"; the BFF probes candidate run dates and auto-selects the active run; multi-day journeys display Day 1/2/3 labels in the timeline.
- **FR-2.4 [P1] Cancelled/diverted/rescheduled states (gap fix).** Cancellation MUST surface as red + icon + word on every surface (home card, board row, track screen); tracking a cancelled train MUST offer one-tap alternatives via the Plan pipeline ("These trains on your route still have seats"). Diversion/reschedule show amber banner with plain-language explanation. Push alert fires to anyone watching an affected train or holding a PNR on it.
- **FR-2.5 [P1]** Freshness stamp ("updated Xs ago") and a live-pulse indicator MUST appear on all live data; pull-to-refresh everywhere.
- **FR-2.6 [P2]** Predicted delay from history MUST display pre-departure ("usually ~30 min late by <station>"), computed from aggregated trainHistory, labelled as a prediction with basis.
- **FR-2.7 [P1]** Share journey: one tap generates a tokenized public web link (FR-8.x) via system share sheet.

### 4.3 Coach & platform guide (signature)

- **FR-3.1 [P1]** For trains with coach-position data, Track MUST show a platform diagram of coach order at the user's chosen station, with their coach highlighted and a "stand here" indicator (front/middle/rear).
- **FR-3.2 [P1] Reversal-aware.** Where the coach-position timeline shows order changes (rake reversal), the guide MUST show the correct order *for that station* and a plain-language note ("Reverses at Itarsi — your coach moves to the front").
- **FR-3.3 [P2] GEN mode (gap fix).** A first-class unreserved mode MUST highlight all GEN coach positions ("General coaches stop at the front and rear of the platform here") — serving P5 without requiring a PNR.

### 4.4 PNR & proactive alerts

- **FR-4.1 [P1]** PNR screen MUST show: booking + current status per passenger, coach/berth/berth-type, class, quota, chart status, journey summary; joins live train position when the train is running.
- **FR-4.2 [P1] Chart-prepared push (hero moment).** Users saving a PNR MUST receive a push within 5 minutes of chart preparation, delivered by the server-side Watcher (FR-7.x) — works with the app closed. In-app, the moment gets a small celebration animation.
- **FR-4.3 [P1] PNR privacy (gap fix).** PNRs MUST be masked in UI (••••2882), never logged in full server-side, encrypted at rest, and auto-purged N days after journey completion. A plain-language privacy note is linked from the PNR screen.
- **FR-4.4 [P2]** Waitlisted PNRs MUST show confirmation-prediction % and auto-suggested confirmed alternatives (same route/date) when odds are poor.
- **FR-4.5 [P2]** IRCTC SMS paste-to-import: pasting a booking SMS auto-extracts PNR/train and offers one-tap save.

### 4.5 Station live board

- **FR-5.1 [P1]** Board MUST list arrivals/departures for a station over 2/4/8 hr windows with live delay, platform, destination, and cancelled state; filters for destination/class/on-time-only.
- **FR-5.2 [P1]** "Trains near me" resolves nearest station via device location (permission asked in context, with reason).
- **FR-5.3 [P1]** Sunlight/high-contrast mode MUST be available (auto-suggest via ambient light where supported).

### 4.6 Journey planning

- **FR-6.1 [P1]** A→B search for a date MUST return all trains with times, duration, classes, running days.
- **FR-6.2 [P1→P2]** Each result row hydrates progressively with seat availability (+ prediction %) and total fare; list sortable by departure, price, seats, and (P2) punctuality score from history. Rows never block on the slowest call.
- **FR-6.3 [P1]** Expanding a row shows the full fare breakdown (base, reservation, superfast, GST, dynamic, tatkal where applicable, total).
- **FR-6.4 [P2] Quota-aware UX (gap fix).** A human quota picker ("Booking normally / Tatkal — opens 10 AM AC · 11 AM non-AC / Ladies / Senior citizen") MUST drive availability + fare queries; a "remind me when Tatkal opens" alert type is offered contextually.
- **FR-6.5 [P3]** Booking handoff to an authorized partner MAY be added only after terms verification (WS-D); it is informational, clearly labelled, never a dark pattern.

### 4.7 Watcher service & notifications (gap fix — new core infrastructure)

- **FR-7.1 [P1]** A server-side **Watcher** MUST exist: saving a PNR, tracking a train, or setting an alarm registers a watch job. A scheduler polls RailKit server-side (single-flight shared across all watchers of the same entity), diffs state, and fires FCM/APNs on change.
- **FR-7.2 [P1]** Watch triggers (minimum): chart prepared; delay crossing user threshold; platform change; train cancelled/diverted; train ~N min from user's saved station (arrival alarm).
- **FR-7.3 [P1]** Smart arrival alarm MUST be server-computed from live position + current delay (not device timetable), delivered as high-priority push + full-screen alarm UI; user sets "wake me X min before <station>".
- **FR-7.4 [P1]** Quiet hours, per-alert-type opt-in, and one-tap mute-this-journey MUST exist. Default posture: minimal, meaningful notifications only.
- **FR-7.5 [P1]** Watcher polling cadence adapts to context (e.g., PNR watch tightens to ~60 s inside the chart window; train watch tightens as it nears the alarm station) and expires automatically post-journey.

### 4.8 Shared journey web view (gap fix — growth loop)

- **FR-8.1 [P2]** A tokenized public web page (`railcast.app/t/<token>`) MUST render a recipient's live journey view — status, timeline, ETA — with no login and no install wall, served by the BFF from the same cache.
- **FR-8.2 [P2]** Tokens are unguessable, expire post-journey, and are revocable by the sharer. The page carries a single quiet install prompt (no interstitial).
- **FR-8.3 [P3]** SEO train-status pages MAY reuse this web layer for organic acquisition.

### 4.9 Offline & low-connectivity

- **FR-9.1 [P1]** All screens MUST render last-known cached data instantly when offline, with a visible "showing cached data from HH:MM" strip. Degrade, never block.
- **FR-9.2 [P1]** Static content (directory, saved trains' routes/schedules/coordinates) MUST be fully available offline.
- **FR-9.3 [P2] SMS-139 bridge (gap fix).** When offline and the user requests live status/PNR, the app MUST offer one tap to compose the correctly formatted SMS to 139 and, where OS permissions allow, parse the reply into the normal UI. Framed honestly as the no-data fallback.

### 4.10 Language, accessibility, inclusivity

- **FR-10.1 [P1]** Launch languages: English, Hindi + 3–4 more; **[P2]** full 12 (Bengali, Marathi, Malayalam, Kannada, Tamil, Telugu, Punjabi, Odia, Assamese, Gujarati). Full parity — every string, every screen; switchable anytime without state loss.
- **FR-10.2 [P1]** Redundant encoding everywhere: status = icon + word + colour, never colour-only. Colour-blind-safe palette.
- **FR-10.3 [P1]** Large tap targets (≥ 48 dp), OS text-size honoured with reflow, screen-reader labels on all interactive elements, visible keyboard focus, reduced-motion respected.
- **FR-10.4 [P2]** Spoken status in the user's language ("train now approaching Itarsi, platform 3") on demand and with alarms.
- **FR-10.5 [P1]** No forced login for core value (status/PNR/board/plan). Account only for saving/alerts/sync, offered in context.

### 4.11 Trust, feedback & data quality

- **FR-11.1 [P1]** Estimation honesty rules: interpolated position labelled; predictions labelled with basis; freshness stamps universal; "not affiliated with Indian Railways" disclaimer present.
- **FR-11.2 [P2] Report-wrong-data (gap fix).** One-tap "this was wrong" (platform/delay/coach) per surface; reports feed a data-quality dashboard and, over time, a correction layer.
- **FR-11.3 [P1]** Privacy-respecting analytics (anonymized, no PNR contents, opt-out honoured) instrument the §2 metrics — time-to-answer, first-session success, alert latency.

---

## 5. Non-functional requirements

- **NFR-1 Performance:** cold launch to interactive ≤ 2.5 s on entry-level Android; cached screen render on frame one; app size lean (target < 25 MB installed, directory included).
- **NFR-2 Reliability:** BFF availability ≥ 99.9%; graceful stale-serve on upstream failure; watcher at-least-once delivery with dedup.
- **NFR-3 Battery/data:** foreground polling only for on-screen volatile data; background = push only; true-dark OLED theme; typical tracking session ≤ a few MB.
- **NFR-4 Security:** RailKit `x-api-key` in server secret manager only; TLS everywhere; PNR handling per FR-4.3; app↔BFF auth tokens; read-only public surface.
- **NFR-5 Scale & surge:** architecture sized for festival peaks (Diwali/Chhath ≈ 10× normal); load-test before festival season; cache-hit targets ≥ 90% on hot live keys.
- **NFR-6 Compliance:** verify RailKit terms for our usage pattern (incl. watcher polling volume) and IRCTC rules before any booking handoff (WS-D). App-store and Indian data-protection (DPDP) compliance for personal data.

---

## 6. System architecture

```
┌──────────┐   HTTPS    ┌───────────────────────────────┐  x-api-key  ┌─────────┐
│ Railcast │ ─────────► │        Railcast BFF           │ ──────────► │ RailKit │
│ app/web  │ ◄───────── │  /screen/* composed endpoints │ ◄────────── │  REST   │
└──────────┘  screen    │  shared cache (per-key TTL)   │             └─────────┘
     ▲        JSON      │  single-flight & SWR          │
     │                  ├───────────────────────────────┤
     │   FCM / APNs     │  WATCHER SERVICE (new, P1)    │
     └───────────────── │  watch jobs · scheduler ·     │
                        │  state diff · push fan-out ·  │
                        │  quiet hours                  │
                        ├───────────────────────────────┤
                        │  PUBLIC WEB LAYER (P2)        │
                        │  /t/<token> shared journeys   │
                        └───────────────────────────────┘
```

**Key decisions (carried from API plan + gap fixes):**
1. App never calls RailKit directly — key safety, shared cache economics (500 viewers of one train ≈ one upstream call), aggregation.
2. Composed endpoints: `/screen/train/:no/:date`, `/screen/pnr/:pnr`, `/screen/station/:stn`, `/screen/plan?from&to&date` — one request per screen.
3. Cache TTLs by volatility: static (route/coords) 7–30 d · history 30 d+ · search 12–24 h · fare 15–30 min · availability 5–15 min · PNR 2–5 min (60 s near chart) · station board 60–120 s · trackTrain 30–60 s.
4. Client: SWR render-from-cache-then-refresh; adaptive foreground polling (30–45 s running train → back-off on no-change → stop on background → refresh on return); one central lifecycle-owned poll controller; one cached entity per key shared by all components.
5. Watcher shares the same cache/single-flight, so background watching adds minimal upstream load; its polls also warm the cache for foreground users.
6. Known upstream behaviors handled: trainHistory 404 until journey completes = "not yet available"; run-date probing for overnight trains; `cancelled` flag honored everywhere.

---

## 7. UX requirements (system-level)

- **Answer-first skeleton on every data screen:** the answer in the largest type → freshness + live dot → one primary action → expandable detail. Identical skeleton app-wide so learning one screen teaches all.
- **Three tabs:** Journeys · Find · You. Nothing important deeper than two taps. Icons always labelled.

  > **Amended July 2026 (was: five tabs — Home · Track · Station · Plan · Alerts).**
  > *Track* and *Alerts* were never destinations: tracking is what a journey **is**, not a place you go to do it, and an alert is a *property* of a journey rather than a sibling of it — so alerts now appear inside the journey they concern, where the evidence belongs. *Station* and *Plan* are both "find me something I don't have yet", which is one intent and takes one input. The five-tab layout also made the user the integration layer, scattering one mental object ("my train") across four destinations.
  > Practical benefit: each tab label gets a third of the bottom bar instead of a fifth, which is what makes "icons always labelled" survive at 200% text (FR-10.3).
  > Rationale and the rejected alternatives are in `docs/design/direction-study.md`.
- **Design language:** humanist sans with first-class Indic-script support; tabular numerals (no jitter on refresh); calm base + single brand accent; railway-signal status colours (green/amber/red) always paired with icon+word; generous whitespace; restrained meaningful motion; dark mode and sunlight mode as first-class themes.
- **Premium through restraint, not obscurity:** no hidden gestures for core actions; big and obvious, beautifully executed.
- **Onboarding:** native-script language picker → one question ("Track a train / Check PNR / Trains near me") → straight into value. No tutorial carousel, no permission wall, no forced login.
- **Every state designed:** loading (skeletons match final layout), empty (directive), error (plain-language + next step), offline (cached + strip), bad news (cancellation → alternatives). Copy is active-voice, consistent, never blames the user.
- **What we never do:** ads, interstitials, notification spam, fake urgency, forced sign-up, feature bloat. This list is product law.

---

## 8. Data workstreams (new, from gap analysis)

- **WS-A Watcher/push service** — the P1 infrastructure of §4.7. Owner: backend. Includes surge sizing (NFR-5).
- **WS-B Directory dataset** — trains + stations, English P1; 12-language localization P2 (§4.1). Sourcing, cleaning, delta-update pipeline, in-app fuzzy search index. Owner: data.
- **WS-C Public web layer** — shared journey pages P2; SEO pages P3 (§4.8). Owner: web.
- **WS-D Commercial/terms verification** — RailKit Advance pricing, rate limits, and permitted usage (watcher volume!); IRCTC/partner rules for handoff; DPDP review. Owner: founder/legal. **Gate for FR-6.5 and final polling cadences.**
- **WS-E Cost model** — calls/DAU under target cache-hit rates including watcher load; monthly upstream budget vs subscription pricing. Owner: backend + founder. Deliverable before P1 freeze.

---

## 9. Release plan

**Phase 1 — MVP (the wedge, done right on bad days too):**
Search by name/number (directory), live tracking with overnight-date handling + cancelled states + map estimate, coach & platform guide with reversal awareness, PNR with masking + chart-prepared push (Watcher live), smart arrival alarm, station board with sunlight mode, A→B planning with fare + availability, offline cached mode, EN + HI + 3–4 languages, dark mode, share link (app-side; web view lands early P2 if needed post-launch). Ad-free forever.

**Phase 2 — Intelligence & inclusion:**
Delay prediction from history, confirmation odds + alternatives, punctuality sorting, GEN-coach mode, quota-aware availability + Tatkal reminders, SMS-139 bridge, spoken status, full 12 languages with localized directory, shared-journey web view, SMS paste-import, report-wrong-data.

**Phase 3 — Growth & depth:**
SEO status pages, widgets/live activities, family sharing for the setter-upper flow, Railcast Plus subscription (unlimited saves, richer prediction, priority refresh, family live-sharing), booking handoff if WS-D clears, festival-surge features (special-trains surfacing).

---

## 10. Monetization

Free tier stays genuinely useful forever (the ad-free promise is the brand). **Railcast Plus** (low annual India-tuned price): unlimited saved trains/PNRs, deeper prediction history, priority refresh cadence, family live-sharing, advanced alert rules. Optional clean booking handoff revenue post-WS-D. No ads, ever — including "sponsored results."

---

## 11. Risks & mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Single upstream (RailKit) outage/terms change | Existential | Abstract data layer for a second source; cache-serve stale during outages; WS-D terms clarity up front |
| Watcher polling volume breaches rate limits/cost | Hero alerts degrade | Shared single-flight watches; adaptive cadence; WS-E budget; cap + prioritize near-chart/near-alarm watches |
| Upstream data wrong (platform/delay) | Trust erosion | Honesty labels; FR-11.2 feedback loop; never over-claim precision |
| Offline gap vs WIMT | Switch resistance | Cached-first + SMS-139 bridge + honest framing; win on intelligence not offline |
| Localized directory quality | Vernacular promise fails | WS-B with native-speaker review; ship languages only at full parity |
| Festival surge | Downtime at peak trust moment | NFR-5 load tests; autoscale BFF; pre-festival freeze |
| DPDP / PNR privacy misstep | Legal + trust | FR-4.3 by design; minimal retention; DPDP review in WS-D |

---

## 12. Open questions

1. RailKit Advance plan: exact pricing, rate limits, and whether server-side watcher polling at our projected volume is permitted (WS-D — blocks final cadence tuning).
2. Best source and license for the train/station directory + 12-language names (WS-B).
3. Arrival-alarm delivery guarantees on aggressive Android OEM battery managers (Xiaomi/Oppo/Vivo) — test matrix needed; may need high-priority FCM + user guidance.
4. SMS-139 reply parsing permissions on modern Android/iOS — degrade to "compose only" where reading is restricted.
5. Launch language set beyond EN+HI for P1 (pick by target launch geography).

---

## 13. Acceptance snapshot (what "done" means for MVP)

A first-time, low-tech user can: pick their language, type or speak "goa express," see it resolve, choose "started yesterday" correctly by default, watch an honest estimated position on a map, learn which end of the platform their coach stops at (even across a reversal), save a PNR that's masked everywhere, get woken by a push the moment the chart prepares at 4 a.m. with the app closed, be alerted if the train is cancelled and immediately shown alternatives with seats — all in under a handful of taps, with zero ads, on a cheap phone, in sunlight, and with yesterday's data still visible when the signal dies.

*That sentence is the product. Everything in this PRD exists to make it true.*

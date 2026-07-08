# Railcast — Gap Analysis

*A hard second look at the product, API, UX, and prototype plans. What's missing, tiered by severity.*

---

## Tier 1 — Critical: the product breaks without these

### 1. Push notifications need a server-side watcher — and nothing in the plan builds one
The "chart prepared" alert and the smart arrival alarm are our two hero moments. Both must fire **while the app is closed**. But the API plan's entire real-time model is *foreground polling*, and explicitly stops polling when the app backgrounds. Nobody is watching the PNR at 4 a.m. when the chart prepares.

**Fix:** a server-side **watcher service** in the BFF: when a user saves a PNR/train or sets an alarm, the server registers a watch job. A scheduler polls RailKit on the server (shared across all watchers of the same train/PNR — same single-flight economics), diffs against last state, and fires FCM/APNs push on change (chart prepared, delay crossed threshold, platform changed, train ~20 min from user's station). This is a new architectural component: job queue, diff engine, push fan-out, and quiet-hours logic. It must move from "Phase 3 detail" to **core Phase 1 infrastructure**, because the chart alert is in the MVP.

### 2. Users search by *name*; RailKit only accepts *numbers and codes*
Every RailKit endpoint takes a 5-digit train number or a station code. Real users type "goa express" and "jabalpur." There is no search/autocomplete endpoint in the API. As planned, the very first search a new user makes **fails** — fatal for the near-zero onboarding promise.

**Fix:** bundle a **static directory dataset** (all trains: number ↔ name; all stations: code ↔ name ↔ city) inside the app, with fuzzy client-side autocomplete that resolves names → numbers/codes *before* any API call. Ship it in the app binary, update it via a small periodic download. This also enables offline search. It's a data-sourcing task the plan never assigned.

### 3. The overnight-train date trap
Track Train requires a **journey start date**. A passenger boarding the Goa Express at 1 a.m. on the 5th is on the train that *started on the 4th* — and will pick the wrong date, get wrong/no data, and blame the app. This is one of the most common failure modes in this category and none of our plans mention it.

**Fix:** never ask raw dates for live tracking. Offer "Started today / Started yesterday" as the primary choice, auto-detect the likely run by checking which run is currently active (the BFF can probe both dates), and label multi-day journeys clearly (Day 1/2/3 in the timeline — our route data already carries a `day` field).

### 4. Cancelled, diverted, rescheduled — the bad-news states
The live-station sample data literally contains `"cancelled": true`, yet no plan designs for cancellation — let alone diversion or rescheduling. Bad news is when users need the app most and when trust is won or lost.

**Fix:** explicit UX states: a cancelled train shows red + icon + word across every surface (home card, board, track screen), pushes an alert to anyone tracking it or holding a PNR on it, and immediately offers alternatives via the Plan pipeline ("These 3 trains on your route still have seats"). That last move — turning cancellation into rebooking help — is a genuine differentiator.

---

## Tier 2 — Important: competitive and trust gaps

### 5. The share-a-journey growth loop has no web endpoint
The UX plan promises "no install wall" for shared live journeys, but the whole architecture is app-only. **Fix:** a public, tokenized web view (`railcast.app/t/abc123`) served by the BFF from the same cache — near-zero extra cost. Bonus: public SEO pages for train status are how ixigo-class apps win organic installs; the same web layer enables that later.

### 6. Offline still concedes too much to WIMT — add the SMS bridge
We cache last-known data, but on a zero-signal stretch we go quiet while WIMT's cell-tower mode keeps working. **Fix (cheap and clever):** Indian Railways' own **139 SMS service** works without data. When offline, Railcast offers one tap to compose the correctly-formatted SMS (spot/PNR query to 139) and can parse the reply into the UI. We don't match WIMT's offline magic, but we give users a working path — honestly framed.

### 7. Unreserved (GEN) travelers are a huge ignored segment — and we already have their data
Everything in our plans assumes a reserved passenger. But the coach-position data includes GEN coaches, meaning we can tell an unreserved traveler *exactly where the GEN coaches will stop on the platform* — front cluster vs rear cluster. For crores of daily unreserved travelers, that's the difference between boarding and not. Almost nobody serves them well. Make GEN-coach positioning a first-class mode of the coach guide.

### 8. Localized station names don't exist in the API
The vernacular-first promise needs "जबलपुर," not "Jabalpur" transliterated on the fly. RailKit returns English only. **Fix:** the static directory (gap #2) must carry station/train names in all 12 languages — a dataset build task with real effort behind it. Without it, the Hindi UI has English station names and the "native, not localised" claim collapses.

### 9. PNR privacy is a real obligation, not a footnote
A PNR reveals journey, berth, and co-passengers. **Fix:** mask PNRs in UI (••••2882 — the prototype already does this; make it policy), never log full PNRs server-side, encrypt saved PNRs at rest, auto-purge after journey completion, and publish a plain-language privacy note. Also verify RailKit's terms and IRCTC rules for the booking-handoff monetization before building it.

### 10. Quota complexity is real UX, not a dropdown
Tatkal opens 10:00/11:00 with its own fare and rules; there are ladies, senior-citizen, and other quotas. Availability answers differ wildly per quota. **Fix:** a human quota picker ("Booking normally / Tatkal (opens 10 AM) / …"), Tatkal-open reminders as an alert type, and Tatkal fare shown from the fare endpoint's tatkal field.

---

## Tier 3 — Strengthens the plan

- **Cost model:** we planned caching but never sized the bill. Estimate calls/DAU under target cache-hit rates (watcher service included — it polls even when users don't), and confirm RailKit Advance-plan pricing and rate limits before committing cadences.
- **Data-quality feedback loop:** one-tap "platform was wrong" report. Upstream data will sometimes be wrong; capturing that builds trust and a correction layer competitors lack.
- **Analytics & experimentation:** privacy-respecting product analytics (time-to-answer, first-session success) were defined as success metrics but nothing measures them. Pick a lightweight, anonymized stack.
- **Festival-surge readiness:** Diwali/Chhath is 10× load and peak waitlist anxiety — capacity-test the BFF and pre-build "special trains" surfacing (the data already shows TRAIN ON DEMAND types).
- **Prototype omissions to add next round:** voice search entry point, error/empty/offline states, the cancelled-train state, onboarding language picker, and the chart-prepared celebration moment.
- **eCatering/food-on-train:** out of API scope, but a partner integration later fills a gap users expect from RailYatri-class apps. Note it; don't build it yet.

---

## Revised priority impact

**Moves INTO Phase 1 (MVP):** server-side watcher + push (chart alert can't exist without it), static train/station directory with name search, overnight-date handling, cancelled-train states, PNR masking policy.

**Moves UP to Phase 2:** shareable web journey view, SMS-139 offline bridge, GEN-coach mode, localized directory, quota-aware availability UX.

**Explicit new workstreams:** (a) watcher/push service, (b) directory dataset build + localization, (c) public web layer, (d) cost model & upstream-terms verification.

---

*The theme across every gap: the plans designed the sunny day. The product wins or dies on the bad days — chart at 4 a.m., cancelled train, no signal, wrong date, a user who can't spell a station code. Design those and the "killer" claim gets real.*

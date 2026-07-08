# Railcast — API, UX & Real-Time Data Plan

*How data flows from RailKit → Railcast backend → screens, and how the app stays live while the user is in it.*

---

## 1. Architecture overview

```
┌────────────┐      HTTPS       ┌──────────────────┐   x-api-key    ┌────────────┐
│  Railcast  │ ───────────────► │  Railcast BFF    │ ─────────────► │  RailKit   │
│  app       │ ◄─────────────── │  (backend-for-   │ ◄───────────── │  REST API  │
│ (iOS/Andr) │   screen JSON    │   frontend)      │   endpoint JSON│            │
└────────────┘                  └──────────────────┘                └────────────┘
      │                               │
   local cache                  shared server cache
   (SWR, offline)               (Redis / KV, per-key TTL)
```

**Why a BFF (backend-for-frontend), not direct calls — three hard reasons:**

1. **Key safety.** RailKit's `x-api-key` must never ship in the client. The BFF holds it.
2. **Shared cache = cost control.** `liveAtStation/NDLS` or `trackTrain/12345/today` is identical for every user watching it. Cache it once on the server and one upstream call serves thousands. This is the single biggest lever on your API bill and your speed.
3. **Aggregation.** One screen often needs 2–4 endpoints. The BFF composes them into a single response so the app makes one request per screen, not four.

The app talks only to Railcast endpoints (e.g. `/screen/train/...`). RailKit is an internal detail.

---

## 2. API plan

### 2.1 Endpoint catalog

The eight RailKit endpoints, classed by how fast their data changes. **Volatility drives cache TTL and refresh mode.**

| RailKit endpoint | Powers | Volatility | Server cache TTL | Refresh mode |
|---|---|---|---|---|
| `getTrainInfo/:trainNo` | Route, coordinates, schedule, running days | Static | 7–30 days | On demand |
| `trainHistory/:trainNo/:date` | Past-journey actuals (delay patterns) | Immutable once complete | 30+ days | On demand |
| `searchTrainBetweenStations/:from/:to?date` | A→B train list | Slow | 12–24 h | On demand |
| `fareLookup/:trainNo/:date/:from/:to/:class/:quota` | Fare breakdown | Slow (dynamic part moves with demand) | 15–30 min | On demand |
| `getAvailability/:trainNo/:from/:to/:date/:coach/:quota` | Seats + prediction % | Medium | 5–15 min | On demand + pull |
| `checkPNRStatus/:pnr` | PNR, berths, chart flag | Medium (spikes at chart prep) | 2–5 min (30–60 s near chart window) | Adaptive poll |
| `liveAtStation/:stn?hrs` | Station live board | High | 60–120 s | Poll (foreground) |
| `trackTrain/:trainNo/:date` | Live status, position, delay | High | 30–60 s | Adaptive poll |

**Reading this table:** anything "static/immutable" is fetched once and cached hard — the route and coordinates for train 22188 don't change, so never re-fetch them during a session. Only the two "high" rows (`trackTrain`, `liveAtStation`) and PNR near chart prep actually poll.

### 2.2 Auth & security
- BFF injects `x-api-key` server-side; the key lives in a secret manager, never in app code or logs.
- App authenticates to the BFF with its own session token (rate-limits abusive clients, enables Plus entitlements).
- All requests are `GET`; the BFF exposes only read endpoints.

### 2.3 Composed (aggregation) endpoints the BFF exposes

Instead of the app calling RailKit shapes, it calls screen-shaped endpoints:

- **`GET /screen/train/:trainNo/:date`** → merges `trackTrain` (live) + `getTrainInfo` (route/coords, cached) + a cached `trainHistory` delay summary → returns one payload with status, map geometry, timeline, coach guide, predicted delay.
- **`GET /screen/pnr/:pnr`** → `checkPNRStatus`; if the train is currently running, joins live `trackTrain` so the PNR card shows live position too.
- **`GET /screen/station/:stn?hrs=`** → passthrough of `liveAtStation` with light shaping (sort, filter flags).
- **`GET /screen/plan?from&to&date`** → `searchTrainBetweenStations`, then fans out cached `getAvailability` + `fareLookup` per train, returns a unified sortable list (price / seats / punctuality).

Aggregation happens server-side where the cache lives, so fan-out is cheap.

### 2.4 Caching, rate-limiting, resilience
- **Two-layer cache:** server (Redis/KV, shared, authoritative TTLs above) + client (last-known payload for instant render and offline).
- **Request coalescing / dedup:** if 50 users request the same live train inside the TTL window, the BFF makes at most one upstream call and shares the result (single-flight).
- **Stale-while-revalidate (SWR):** always return cached immediately; if stale, trigger a background refresh and push the update. The user never stares at a spinner for data we already have.
- **Retries:** exponential backoff on 5xx; cap at 2–3 tries; fall back to last good cache and label it stale.
- **Known error cases:** `trainHistory` returns 404 until the journey completes — treat as "not available yet," not an error. PNR must be 10 digits, train 5 digits — validate client-side before calling.

---

## 3. Real-time strategy (the core of the request)

RailKit is REST with no push/websocket, so "real-time while in the app" = **adaptive foreground polling + SWR**, not a live socket. The trick is polling *only what's volatile*, *only while it's on screen*, and *at a cadence that matches the data*.

### 3.1 Adaptive polling cadence

| Context | `trackTrain` | `liveAtStation` | `checkPNRStatus` |
|---|---|---|---|
| Screen in foreground, train running | every 30–45 s | every 60–90 s | — |
| Foreground, train not yet started / idle | every 90–120 s | every 120 s | 60 s near chart window, else 3–5 min |
| App backgrounded | stop; refresh on return | stop | rely on push (Phase 3) |
| No change detected N times | back off ×1.5 (cap ~2–3 min) | back off | back off |

**Rules that keep it cheap and battery-friendly:**
- Poll only the screen the user is looking at. Home cards refresh on a slower shared timer.
- Pause polling when the app backgrounds; do one immediate refresh when it returns to foreground.
- Back off automatically when successive responses are identical (a stopped/held train doesn't need 30 s polls).
- Every poll goes through the BFF cache, so a poll inside the TTL costs zero upstream calls — it just returns the shared cached value.

### 3.2 Stale-while-revalidate render flow (every data screen)

```
open screen
   │
   ├─ show cached payload instantly  ──►  UI renders with "updated 40s ago"
   │
   ├─ fire fetch to BFF
   │        └─ BFF: cache fresh? → return it
   │                 cache stale? → single-flight upstream → return fresh
   │
   ├─ on fresh data → update UI, reset "updated just now"
   │
   └─ start poll loop at cadence for this screen (§3.1)
        └─ on background → stop loop;  on foreground → immediate refresh + restart
```

The user always sees data on frame 1 (from cache), then it silently sharpens. This is what makes the app feel faster than WIMT despite similar upstream data.

---

## 4. UX plan

### 4.1 Global real-time UX rules (apply to every live screen)
- **Freshness stamp** on any live data: "updated just now / 40s ago." Non-negotiable for trust.
- **Live indicator:** a subtle pulsing dot while a poll loop is active; it goes grey when polling is paused (backgrounded/offline).
- **Instant cached render + skeleton only for never-seen data.** Returning users never see a blank screen.
- **Pull-to-refresh** everywhere, which also resets the poll cadence to fast.
- **Offline banner, not offline block:** show last-known data with a clear "showing cached data from HH:MM — no connection" strip. Degrade, don't die.
- **Estimation honesty:** map position is labelled "estimated from last station + delay," never implied as GPS.

### 4.2 Screen-by-screen (data need · states · refresh behavior)

**Home**
- *Data:* saved trains (`/screen/train`) + saved PNRs (`/screen/pnr`) as live cards.
- *States:* cached cards on launch → background refresh all → update.
- *Refresh:* shared slow timer (~90–120 s) while foreground; tap a card to enter its fast-polling detail.

**Track (Train detail)**
- *Data:* `/screen/train/:trainNo/:date` (live status + route geometry + timeline + coach guide + predicted delay).
- *States:* cached instant render → live update; map draws route line from cached coordinates immediately, position marker updates on each poll.
- *Refresh:* adaptive 30–45 s while running (§3.1); pull-to-refresh; pause on background.

**PNR**
- *Data:* `/screen/pnr/:pnr`; joins live train position if running.
- *States:* status + berths render from cache; chart-prepared flag highlighted; if running, live strip on top.
- *Refresh:* 3–5 min normally; accelerate to ~60 s inside the chart-preparation window; push alert on chart prepared (Phase 3).

**Station (live board)**
- *Data:* `/screen/station/:stn?hrs=2|4|8`; up to 50 trains.
- *States:* cached list instant; lazy-render rows; filters (destination / class / on-time only) apply client-side.
- *Refresh:* 60–90 s foreground; `hrs` toggle re-queries; pull-to-refresh.

**Plan (A→B)**
- *Data:* `/screen/plan?from&to&date` → unified list (price / seats / punctuality), each row expandable to fare detail.
- *States:* train list renders first (cached/fast); availability + fare hydrate per row as they resolve (progressive, never block the list).
- *Refresh:* on demand only — this is planning, not live; re-query on date change or pull-to-refresh.

**Alerts / Settings** — non-live; standard forms.

### 4.3 The signature screens get special UX
- **Coach & platform guide** (inside Track): a simple platform diagram showing which end your coach lands at *for this station*, flipping correctly after a reversal. Big, glanceable, language-independent icon.
- **Predicted delay** (inside Track/Plan): one sentence — "usually ~30 min late by Gadarwara" — sourced from cached history, shown before departure.

---

## 5. API ↔ UI connection map

The explicit wiring: component → BFF endpoint → underlying RailKit call(s) → when it fires → cadence.

| UI component | BFF endpoint | RailKit call(s) | Fires when | Cadence |
|---|---|---|---|---|
| Home live cards | `/screen/train`, `/screen/pnr` | `trackTrain`, `checkPNRStatus` (+cached `getTrainInfo`) | Home foreground | shared ~90–120 s |
| Track: status + timeline | `/screen/train/:no/:date` | `trackTrain` | Screen open | 30–45 s adaptive |
| Track: route line + map | (same payload) | `getTrainInfo` coords | Screen open | once (cached) |
| Track: position marker | (same payload) | `trackTrain` (interpolated) | each poll | 30–45 s |
| Track: predicted delay | (same payload) | `trainHistory` (aggregated) | Screen open | once (cached) |
| Track: coach/platform guide | (same payload) | `trackTrain` coach timeline + `getTrainInfo` | Screen open | on poll if changed |
| PNR card | `/screen/pnr/:pnr` | `checkPNRStatus` (+`trackTrain` if running) | Screen open | 3–5 min / 60 s near chart |
| Station board | `/screen/station/:stn?hrs` | `liveAtStation` | Screen open / `hrs` toggle | 60–90 s |
| Plan list | `/screen/plan?from&to&date` | `searchTrainBetweenStations` | Search submit | on demand |
| Plan row: seats | (hydrates row) | `getAvailability` | Row resolves/expands | on demand (cached 5–15 min) |
| Plan row: fare | (hydrates row) | `fareLookup` | Row resolves/expands | on demand (cached 15–30 min) |

### 5.1 Client data-layer rules
- **One source of truth per key.** All components reading train 22188 share the same cached entity; a single poll updates every component at once (dedup on the client too).
- **Progressive hydration.** Lists render structure first, then fill availability/fare per row so the screen is never blocked on the slowest call.
- **Cache keys mirror endpoints** (`train:22188:today`, `station:JBP:4`, `pnr:2458692882`) so server and client caches align and invalidate cleanly.
- **Foreground/background lifecycle hook** owns all poll loops centrally — one place starts/stops/backs-off every timer.

---

## 6. Worked example — user opens a running train

1. Tap saved card → Track opens, **instantly** rendering last-known status + route line from client cache ("updated 55s ago").
2. App calls `GET /screen/train/22188/today`.
3. BFF: `getTrainInfo` is cache-hit (static) → returned free; `trackTrain` is 70 s old (stale) → single-flight upstream refresh; `trainHistory` summary cache-hit.
4. Fresh payload returns → position marker advances, timeline updates, stamp resets to "just now," live dot pulses.
5. Poll loop starts at 40 s. Train is held at a signal → three identical responses → cadence backs off to 90 s.
6. User backgrounds the app → loop stops. Returns 4 min later → immediate refresh, loop restarts at 40 s.

Total upstream cost for this session with 500 concurrent viewers of the same train: roughly **one `trackTrain` call per 40–90 s for everyone combined**, thanks to the shared cache — versus 500× that if the app called RailKit directly.

---

## 7. Build checklist

- [ ] Stand up BFF with secret-managed `x-api-key` and per-endpoint TTL cache.
- [ ] Implement single-flight + SWR at the BFF.
- [ ] Ship composed `/screen/*` endpoints.
- [ ] Client cache keyed to endpoints; one-entity-one-source dedup.
- [ ] Central foreground/background poll controller with adaptive back-off.
- [ ] Freshness stamps, live dot, offline banner, estimation labels in the design system.
- [ ] Validate PNR (10 digits) / train (5 digits) before calling; handle `trainHistory` 404 as "not yet available."

---

*Principle throughout: fetch static data once, poll volatile data only while it's on screen, cache everything on the server so scale doesn't multiply cost, and always show the user something real on the first frame.*

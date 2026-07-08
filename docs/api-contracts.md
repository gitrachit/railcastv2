# Railcast API Contracts (v1)

The single source of truth for every request/response between the Android app and the Railcast BFF.
**Rule: server and app both implement THIS document. Any change to a schema = update this file in the same PR, and say so in the PR description.**

Types below are TypeScript-style for precision; Kotlin data classes must mirror them field-for-field.

---

## 0. Conventions

### Base
- Base URL: `https://api.railcast.app` (env-configurable). All endpoints are `GET` unless stated.
- Auth: `Authorization: Bearer <deviceToken>` on every request (see §7).
- All timestamps ISO-8601 with timezone (`2026-07-04T16:15:00+05:30`). All dates `YYYY-MM-DD` **in API traffic** (the DD-MM-YYYY quirk of the upstream is the server's problem, never the client's).

### Response envelope (every endpoint)
```ts
interface Meta {
  fetchedAt: string;      // when the underlying data was fetched upstream
  stale: boolean;         // true when served from cache past TTL (upstream down/slow)
  ttlSeconds: number;     // client hint: don't re-request sooner than this
}
type Ok<T>  = { ok: true;  data: T; meta: Meta };
type Err    = { ok: false; error: { code: ErrorCode; message: string; retryable: boolean } };

type ErrorCode =
  | "INVALID_INPUT"        // failed validation (client bug — should be pre-validated)
  | "NOT_FOUND"            // unknown train/station/PNR
  | "NOT_YET_AVAILABLE"    // e.g. history for an incomplete journey (upstream 404 semantics)
  | "UPSTREAM_DOWN"        // upstream unreachable AND no cache to serve
  | "RATE_LIMITED"
  | "UNAUTHORIZED";
```
`message` is developer-facing; the app renders its own localized copy per error code (PRD §7 states).

### Validation (client pre-validates; server re-validates)
- Train number: exactly 5 digits. PNR: exactly 10 digits. Station code: 2–5 uppercase letters.
- `hrs` ∈ {2,4,8}. `quota` ∈ {"GN","TQ","SS","LD"}. `run` ∈ {"auto","today","yesterday"}.

---

## 1. Track — `GET /screen/train/:trainNo?run=auto`

Implements FR-2.x, FR-3.x. `run=auto` (default) makes the server probe candidate run dates and pick the active one (FR-2.3).

```ts
interface TrainScreen {
  trainNo: string;
  name: string;                       // English; client localizes via directory
  runDateResolved: string;            // YYYY-MM-DD of the run being shown
  runDateChoices: Array<{             // for the "Started today/yesterday" sheet
    runDate: string;
    label: "today" | "yesterday";
    active: boolean;                  // server-detected: is this run currently live?
  }>;
  status: {
    state: "not_started" | "running" | "arrived" | "cancelled" | "diverted" | "rescheduled";
    summary: string;                  // e.g. "Running · 17 min late" (EN; client may rebuild)
    delayMin: number | null;
    lastStation: StationRef | null;
    nextStation: (StationRef & { etaScheduled: string; etaActual: string | null }) | null;
    lastUpdate: string;
  };
  route: RouteStop[];
  position: {                         // FR-2.2 — interpolated, honesty required in UI
    kind: "interpolated";
    lat: number; lng: number;
    betweenCodes: [string, string];
    progress: number;                 // 0..1 between the two stations
  } | null;
  coach: CoachGuide | null;           // null when upstream has no coach data
  prediction: {                       // FR-2.6 — null until Phase 2
    typicalDelayMin: number;
    atStationCode: string;
    basisRuns: number;                // "based on last N runs"
  } | null;
}

interface StationRef { code: string; name: string; }

interface RouteStop {
  code: string; name: string;
  km: number; day: number;            // day 1..3 for multi-day trains
  platform: string | null;
  scheduled: { arr: string | null; dep: string | null };
  actual:    { arr: string | null; dep: string | null };
  delayMin: number | null;
  state: "passed" | "departed" | "next" | "upcoming" | "destination";
  lat: number | null; lng: number | null;
}

interface CoachGuide {                // FR-3.1–3.3
  referenceStation: string;           // station this ordering applies to
  order: Array<{ type: string; number: string; position: number }>;
  reversals: Array<{ atStationCode: string; atStationName: string }>;
  // Client renders "stand front/middle/rear" from position/order.length,
  // GEN mode highlights all type==="GEN".
}
```
Cancelled/diverted (FR-2.4): `status.state` drives the red/amber treatment; when `cancelled`, the app offers alternatives via §4 with the same from/to/date.

---

## 2. PNR — `GET /screen/pnr/:pnr`

Implements FR-4.x. The full PNR travels **only** in the request path over TLS; responses and logs carry the masked form.

```ts
interface PnrScreen {
  pnrMasked: string;                  // "••••2882"
  train: { no: string; name: string };
  journey: {
    date: string;                     // YYYY-MM-DD
    from: StationRef; to: StationRef;
    boardingPoint: StationRef;
    cls: string;                      // "2A"
    quota: string;                    // "TQ"
    arrivalEta: string | null;
  };
  chart: { prepared: boolean };       // the FR-4.2 hero flag
  passengers: Array<{
    idx: number;                      // "Passenger 1" → 1
    bookingStatus: string;            // "CNF" | "WL 12" | "RAC 4" ...
    currentStatus: string;
    coach: string | null; berth: number | null; berthType: string | null;
  }>;
  fare: { total: number } | null;
  live: TrainScreen["status"] | null; // joined when the train is currently running
}
```

---

## 3. Station — `GET /screen/station/:code?hrs=4`

Implements FR-5.x.

```ts
interface StationScreen {
  station: StationRef;
  windowHrs: 2 | 4 | 8;
  trains: Array<{
    no: string; name: string;
    source: StationRef; dest: StationRef;
    platform: string | null;
    arrival:   { scheduled: string; actual: string | null; delayMin: number | null } | null; // null = originates here
    departure: { scheduled: string; actual: string | null; delayMin: number | null } | null; // null = terminates here
    status: "ontime" | "late" | "cancelled";
    classes: string[];
  }>;
}
```

---

## 4. Plan — `GET /screen/plan?from=JBP&to=NU&date=2026-07-04&quota=GN`

Implements FR-6.x with progressive hydration: the list returns fast; per-row seats/fare hydrate separately so slow rows never block (PRD §6.4).

```ts
interface PlanScreen {
  from: StationRef; to: StationRef; date: string; quota: string;
  trains: PlanRow[];
}
interface PlanRow {
  no: string; name: string;
  dep: string; arr: string; durationMin: number;
  classes: string[];
  runsOn: boolean[];                  // [Sun..Sat]
  punctuality: { pct: number; basisRuns: number } | null;   // null until Phase 2
  availability: RowAvailability | "pending";
  fare: RowFare | "pending";
}
interface RowAvailability {
  status: "available" | "rac" | "waitlist" | "not_available";
  text: string;                       // "AVL 56", "WL 12"
  predictionPct: number | null;
  canBook: boolean;
}
interface RowFare {
  total: number;
  breakdown: { base: number; reservation: number; superfast: number;
               tatkal: number; gst: number; dynamic: number; other: number };
}
```

**Row hydration:** `GET /screen/plan/row/:trainNo?from&to&date&cls&quota` → `Ok<{ availability: RowAvailability; fare: RowFare }>`
The client requests rows visible on screen first.

---

## 5. Watches & push — the FR-7 surface

```ts
// Register/replace the device push token
POST /device/push-token        body: { fcmToken: string }        → Ok<{}>

// Create a watch
POST /watch
body: {
  type: "chart" | "delay" | "platform" | "cancel" | "arrival";
  entity: { kind: "pnr"; pnr: string } | { kind: "train"; trainNo: string; runDate: string };
  params?: {
    delayThresholdMin?: number;      // type=delay
    stationCode?: string;            // type=arrival — alarm station
    leadMin?: number;                // type=arrival — "wake me N min before"
  };
}
→ Ok<{ watchId: string; expiresAt: string }>

GET    /watch                    → Ok<{ watches: WatchSummary[] }>
DELETE /watch/:watchId           → Ok<{}>
```

**Push payloads (FCM `data` messages; app renders localized notifications):**
```ts
type PushPayload =
  | { kind: "CHART_PREPARED"; pnrMasked: string; trainNo: string; trainName: string;
      allConfirmed: boolean; coachSummary: string }                       // FR-4.2
  | { kind: "DELAY"; trainNo: string; delayMin: number; nextStation: string }
  | { kind: "PLATFORM_CHANGE"; trainNo: string; stationCode: string; platform: string }
  | { kind: "CANCELLED" | "DIVERTED"; trainNo: string; runDate: string }  // FR-2.4
  | { kind: "ARRIVAL_ALARM"; trainNo: string; stationCode: string;
      etaActual: string; leadMin: number };                               // high-priority + full-screen intent
```
Server enforces quiet hours and per-type opt-in (FR-7.4); `ARRIVAL_ALARM` bypasses quiet hours by design.

---

## 6. Shared journey (FR-8; scaffolded in P1, public in P2)

```ts
POST   /share/journey   body: { trainNo: string; runDate: string } → Ok<{ token: string; url: string; expiresAt: string }>
DELETE /share/:token                                               → Ok<{}>
GET    /t/:token        → HTML page (server-rendered, no auth), 410 after expiry
```

---

## 7. Auth & device

```ts
POST /auth/device   body: { platform: "android"; appVersion: string }
→ Ok<{ deviceToken: string }>       // anonymous device identity; no login required (FR-10.5)
```
Optional account linking is Phase 2+ and additive — nothing in P1 may require it.

---

## 8. Directory delta (WS-B / FR-1.2)

```ts
GET /directory/version                → Ok<{ version: number; fullUrl: string }>
GET /directory/delta/:fromVersion     → Ok<{ toVersion: number; deltaUrl: string } | { full: true; fullUrl: string }>
```
Payload format is owned by `packages/directory` (documented there); the app treats it as opaque and swaps indexes atomically.

---

## 9. Misc

```ts
POST /report            // FR-11.2 (Phase 2): { surface: "platform"|"delay"|"coach"; entityKey: string; note?: string }
GET  /health            → Ok<{ uptimeS: number; upstream: "ok"|"degraded" }>
```

---

## 10. Cache TTLs (server-side; client respects `meta.ttlSeconds`)

| Data | TTL |
|---|---|
| route/coords (`getTrainInfo`) | 7 d |
| history summaries | 30 d |
| A→B search | 12 h |
| fare | 20 min |
| availability | 10 min |
| PNR | 3 min (60 s inside chart window) |
| station board | 90 s |
| live track | 45 s |

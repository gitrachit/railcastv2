// GENERATED from docs/api-contracts.md — the contracts doc is the source of truth.
// Any change there must be mirrored here in the same PR, then run:
//   pnpm -F @railcast/shared check:drift --update
// CI fails when the hash below no longer matches the doc (root CLAUDE.md invariant 7).
// contracts-sha256: 2cab34ae1068aebfbdd4f540ad2a34aa2ac8f71b8a53d32ebc1b52a4277817e9

// ─── §0 Conventions ─────────────────────────────────────────────────────────

export interface Meta {
  fetchedAt: string; // when the underlying data was fetched upstream
  stale: boolean; // true when served from cache past TTL (upstream down/slow)
  ttlSeconds: number; // client hint: don't re-request sooner than this
}

export type Ok<T> = { ok: true; data: T; meta: Meta };
export type Err = {
  ok: false;
  error: { code: ErrorCode; message: string; retryable: boolean };
};
export type ApiResponse<T> = Ok<T> | Err;

export type ErrorCode =
  | "INVALID_INPUT" // failed validation (client bug — should be pre-validated)
  | "NOT_FOUND" // unknown train/station/PNR
  | "NOT_YET_AVAILABLE" // e.g. history for an incomplete journey (upstream 404 semantics)
  | "UPSTREAM_DOWN" // upstream unreachable AND no cache to serve
  | "RATE_LIMITED"
  | "UNAUTHORIZED";

// Validation (client pre-validates; server re-validates)
export const TRAIN_NO_PATTERN = /^\d{5}$/;
export const PNR_PATTERN = /^\d{10}$/;
export const STATION_CODE_PATTERN = /^[A-Z]{2,5}$/;

export type WindowHrs = 2 | 4 | 8;
export type Quota = "GN" | "TQ" | "SS" | "LD";
export type RunChoice = "auto" | "today" | "yesterday";

// ─── §1 Track — GET /screen/train/:trainNo?run=auto ─────────────────────────

export interface StationRef {
  code: string;
  name: string;
}

export interface TrainStatus {
  state: "not_started" | "running" | "arrived" | "cancelled" | "diverted" | "rescheduled";
  summary: string; // e.g. "Running · 17 min late" (EN; client may rebuild)
  delayMin: number | null;
  lastStation: StationRef | null;
  nextStation: (StationRef & { etaScheduled: string; etaActual: string | null }) | null;
  lastUpdate: string;
}

export interface TrainScreen {
  trainNo: string;
  name: string; // English; client localizes via directory
  runDateResolved: string; // YYYY-MM-DD of the run being shown
  runDateChoices: Array<{
    // for the "Started today/yesterday" sheet
    runDate: string;
    label: "today" | "yesterday";
    active: boolean; // server-detected: is this run currently live?
  }>;
  status: TrainStatus;
  route: RouteStop[];
  position: {
    // FR-2.2 — interpolated, honesty required in UI
    kind: "interpolated";
    lat: number;
    lng: number;
    betweenCodes: [string, string];
    progress: number; // 0..1 between the two stations
  } | null;
  coach: CoachGuide | null; // null when upstream has no coach data
  prediction: {
    // FR-2.6 — null until Phase 2
    typicalDelayMin: number;
    atStationCode: string;
    basisRuns: number; // "based on last N runs"
  } | null;
}

export interface RouteStop {
  code: string;
  name: string;
  km: number;
  day: number; // day 1..3 for multi-day trains
  platform: string | null;
  scheduled: { arr: string | null; dep: string | null };
  actual: { arr: string | null; dep: string | null };
  delayMin: number | null;
  state: "passed" | "departed" | "next" | "upcoming" | "destination";
  lat: number | null;
  lng: number | null;
}

export interface CoachGuide {
  // FR-3.1–3.3
  referenceStation: string; // station this ordering applies to
  order: Array<{ type: string; number: string; position: number }>;
  reversals: Array<{ atStationCode: string; atStationName: string }>;
  // Client renders "stand front/middle/rear" from position/order.length,
  // GEN mode highlights all type==="GEN".
}

// ─── §2 PNR — GET /screen/pnr/:pnr ──────────────────────────────────────────

export interface PnrScreen {
  pnrMasked: string; // "••••2882"
  train: { no: string; name: string };
  journey: {
    date: string; // YYYY-MM-DD
    from: StationRef;
    to: StationRef;
    boardingPoint: StationRef;
    cls: string; // "2A"
    quota: string; // "TQ"
    arrivalEta: string | null;
  };
  chart: { prepared: boolean }; // the FR-4.2 hero flag
  passengers: Array<{
    idx: number; // "Passenger 1" → 1
    bookingStatus: string; // "CNF" | "WL 12" | "RAC 4" ...
    currentStatus: string;
    coach: string | null;
    berth: number | null;
    berthType: string | null;
  }>;
  fare: { total: number } | null;
  live: TrainStatus | null; // joined when the train is currently running
}

// ─── §3 Station — GET /screen/station/:code?hrs=4 ───────────────────────────

export interface StationBoardRow {
  no: string;
  name: string;
  source: StationRef;
  dest: StationRef;
  platform: string | null;
  arrival: { scheduled: string; actual: string | null; delayMin: number | null } | null; // null = originates here
  departure: { scheduled: string; actual: string | null; delayMin: number | null } | null; // null = terminates here
  status: "ontime" | "late" | "cancelled";
  classes: string[];
}

export interface StationScreen {
  station: StationRef;
  windowHrs: WindowHrs;
  trains: StationBoardRow[];
}

// ─── §4 Plan — GET /screen/plan?from&to&date&quota ──────────────────────────

export interface PlanScreen {
  from: StationRef;
  to: StationRef;
  date: string;
  quota: string;
  trains: PlanRow[];
}

export interface PlanRow {
  no: string;
  name: string;
  dep: string;
  arr: string;
  durationMin: number;
  classes: string[];
  runsOn: boolean[]; // [Sun..Sat]
  punctuality: { pct: number; basisRuns: number } | null; // null until Phase 2
  availability: RowAvailability | "pending";
  fare: RowFare | "pending";
}

export interface RowAvailability {
  status: "available" | "rac" | "waitlist" | "not_available";
  text: string; // "AVL 56", "WL 12"
  predictionPct: number | null;
  canBook: boolean;
}

export interface RowFare {
  total: number;
  breakdown: {
    base: number;
    reservation: number;
    superfast: number;
    tatkal: number;
    gst: number;
    dynamic: number;
    other: number;
  };
}

// GET /screen/plan/row/:trainNo?from&to&date&cls&quota
export interface PlanRowHydration {
  availability: RowAvailability;
  fare: RowFare;
}

// ─── §5 Watches & push ──────────────────────────────────────────────────────

// POST /device/push-token
export interface PushTokenRequest {
  fcmToken: string;
}

export type WatchType = "chart" | "delay" | "platform" | "cancel" | "arrival";

export type WatchEntity =
  | { kind: "pnr"; pnr: string }
  | { kind: "train"; trainNo: string; runDate: string };

export interface WatchParams {
  delayThresholdMin?: number; // type=delay
  stationCode?: string; // type=arrival — alarm station
  leadMin?: number; // type=arrival — "wake me N min before"
}

// POST /watch
export interface CreateWatchRequest {
  type: WatchType;
  entity: WatchEntity;
  params?: WatchParams;
}

export interface CreateWatchResponse {
  watchId: string;
  expiresAt: string;
}

// GET /watch → Ok<{ watches: WatchSummary[] }>
export interface WatchSummary {
  watchId: string;
  type: WatchType;
  entity:
    | { kind: "pnr"; pnrMasked: string } // masked per FR-4.3
    | { kind: "train"; trainNo: string; runDate: string };
  params?: WatchParams;
  expiresAt: string;
}

// Push payloads (FCM `data` messages; app renders localized notifications)
export type PushPayload =
  | {
      kind: "CHART_PREPARED"; // FR-4.2
      pnrMasked: string;
      trainNo: string;
      trainName: string;
      allConfirmed: boolean;
      coachSummary: string;
    }
  | { kind: "DELAY"; trainNo: string; delayMin: number; nextStation: string }
  | { kind: "PLATFORM_CHANGE"; trainNo: string; stationCode: string; platform: string }
  | { kind: "CANCELLED" | "DIVERTED"; trainNo: string; runDate: string } // FR-2.4
  | {
      kind: "ARRIVAL_ALARM"; // high-priority + full-screen intent
      trainNo: string;
      stationCode: string;
      etaActual: string;
      leadMin: number;
    };

// ─── §6 Shared journey ──────────────────────────────────────────────────────

// POST /share/journey
export interface ShareJourneyRequest {
  trainNo: string;
  runDate: string;
}

export interface ShareJourneyResponse {
  token: string;
  url: string;
  expiresAt: string;
}

// ─── §7 Auth & device ───────────────────────────────────────────────────────

// POST /auth/device
export interface DeviceAuthRequest {
  platform: "android";
  appVersion: string;
}

export interface DeviceAuthResponse {
  deviceToken: string; // anonymous device identity; no login required (FR-10.5)
}

// ─── §8 Directory delta ─────────────────────────────────────────────────────

// GET /directory/version
export interface DirectoryVersion {
  version: number;
  fullUrl: string;
}

// GET /directory/delta/:fromVersion
export type DirectoryDelta =
  | { toVersion: number; deltaUrl: string }
  | { full: true; fullUrl: string };

// ─── §9 Misc ────────────────────────────────────────────────────────────────

// POST /report (FR-11.2, Phase 2)
export interface ReportRequest {
  surface: "platform" | "delay" | "coach";
  entityKey: string;
  note?: string;
}

// GET /health
export interface Health {
  uptimeS: number;
  upstream: "ok" | "degraded";
}

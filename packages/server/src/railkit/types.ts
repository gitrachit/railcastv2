// Upstream response shapes, modeled from the recorded payloads in
// __fixtures__/ (mirrors docs/fixtures/). These are RAW upstream types —
// DD-MM-YYYY dates and all — and must never leak past the screens layer.

// /api/getTrainInfo/:trainNo
export interface RawTrainInfo {
  trainInfo: {
    train_no: string;
    train_name: string;
    from_stn_name: string;
    from_stn_code: string;
    to_stn_name: string;
    to_stn_code: string;
    from_time: string;
    to_time: string;
    travel_time: string;
    running_days: string; // "1111111", Mon-first per upstream
    type: string;
    train_id: string;
  };
  route: RawRouteStop[];
}

export interface RawRouteStop {
  stnCode: string;
  stnName: string;
  arrival: string; // "16:27" | "--"
  departure: string; // "16:35" | "--"
  halt: string;
  haltMinutes: number;
  distance: string;
  day: string;
  platform: string | number; // "" | "3,4" | 1
  coordinates: { latitude: number; longitude: number };
}

// /api/trackTrain/:trainNo/:date
export interface RawCoachPositionItem {
  type: string; // "ENG" | "GEN" | "2S" | "CC" | "3A" | ...
  number: string; // "C1" | "GEN" | ...
  position: string; // "0".."21"
}

export interface RawTrackTrain {
  trainNo: string;
  trainName: string;
  date: string; // "07-Jul-2026"
  statusNote: string; // "Arrived at ... (On Time)" | "Yet to start from its source"
  lastUpdate: string; // "07-Jul-2026 22:19" | ""
  totalStations: number;
  coachPosition: RawCoachPositionItem[];
  coachPositionTimeline: Array<{
    fromStationCode: string;
    fromStationName: string;
    coachPosition: RawCoachPositionItem[];
  }>;
  timeline: RawTimelineEntry[];
  currentStationCode: string;
}

export interface RawTimelineEntry {
  type: string; // "stoppage" | passing points
  status: string; // "passed" | ...
  stationCode: string;
  stationName: string;
  platform: string;
  distanceKm: string;
  arrival: { scheduled: string; actual: string; delay: string }; // "SRC" at origin
  departure: { scheduled: string; actual: string; delay: string }; // "16:15 07-Jul"
}

// /api/trainHistory/:trainNo/:date — 404 until the journey completes
export interface RawTrainHistory {
  trainNo: string;
  trainName: string;
  journeyDate: string; // "07-07-2026"
  retryCount: number;
  lastError: string;
  lastErrorAt: string | null;
  sourceStationCode: string;
  sourceStationName: string;
  destinationStationCode: string;
  destinationStationName: string;
  coachPosition: RawCoachPositionItem[];
  stations: Array<{
    stationCode: string;
    stationName: string;
    platform: string;
    arrival: { scheduled: string; actual: string };
    departure: { scheduled: string; actual: string; delay: string };
  }>;
  lastUpdate: string; // "07-07-2026 23:30:07 IST"
}

// /api/liveAtStation/:code?hrs=
export interface RawStationBoard {
  summary: string;
  totalTrains: number;
  trains: RawStationBoardTrain[];
}

export interface RawStationBoardTrain {
  trainNo: string;
  trainName: string;
  source: string;
  sourceName: string;
  dest: string;
  destName: string;
  trainType: string;
  classes: string; // "3A,CC,SL,2S,GEN,PWD"
  runDate: string; // "08-Jul-2026"
  platform: string;
  cancelled: unknown; // null when running; exact cancelled shape TBD from a cancelled capture
  arrival: { actual: string; scheduled: string; delay: string; delayed: boolean }; // actual "SRC" when originates here
  departure: { actual: string; scheduled: string; delay: string; delayed: boolean };
}

// /api/searchTrainBetweenStations/:from/:to
export interface RawSearchBetweenItem {
  train_no: string;
  train_name: string;
  source_stn_name: string;
  source_stn_code: string;
  dstn_stn_name: string;
  dstn_stn_code: string;
  from_stn_name: string;
  from_stn_code: string;
  to_stn_name: string;
  to_stn_code: string;
  from_time: string;
  to_time: string;
  travel_time: string;
  running_days: string;
  distance: string;
  halts: number;
}

// /api/getAvailability/:trainNo/:from/:to/:date/:coach/:quota
export interface RawAvailability {
  train: {
    trainNo: string;
    trainName: string;
    from: string;
    to: string;
    fromStationName: string;
    toStationName: string;
    distance: number;
    travelClass: string;
    quota: string;
  };
  fare: {
    baseFare: number;
    reservationCharge: number;
    superfastCharge: number;
    serviceTax: number;
    totalFare: number;
  };
  availability: Array<{
    date: string; // "15-7-2026" (upstream is sloppy about zero-padding here)
    status: string; // "AVAILABLE" | ...
    availabilityText: string; // "AVL 107" | "WL 12"
    rawStatus: string;
    prediction: string;
    predictionPercentage: number;
    canBook: boolean;
  }>;
}

// /api/fareLookup/:trainNo/:date/:from/:to/:class/:quota
export interface RawFareLookup {
  trainNo: string;
  trainName: string;
  from: string;
  to: string;
  class: string;
  distance: number;
  baseFare: number;
  reservation: number;
  superfast: number;
  fuelAmount: number;
  concession: number;
  tatkalFare: number;
  gst: number;
  otherCharge: number;
  catering: number;
  dynamicFare: number;
  totalFare: number;
}

// /api/checkPNRStatus/:pnr — TODO(fixtures): type from checkPNRStatus-sample.json
// once a real PNR is recorded; until then callers get an opaque object.
export type RawPnrStatus = Record<string, unknown>;

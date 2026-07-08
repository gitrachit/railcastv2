// GET /screen/train/:trainNo?run=auto — the Track screen [FR-2.1–2.4, FR-3.1–3.2].
// Composes trackTrain (probed run dates, FR-2.3) + getTrainInfo (route/coords)
// through the cache; never calls railkit directly.
import type { CoachGuide, RouteStop, RunChoice, TrainScreen, TrainStatus } from "@railcast/shared";
import { TRAIN_NO_PATTERN } from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { Cache, CACHE_TTLS, type CachedResult } from "../cache/index.js";
import { err, HTTP_STATUS, ok } from "../lib/envelope.js";
import { istDateString, shiftIsoMinutes } from "../lib/ist.js";
import { parseDelayMin, parseUpstreamDateTime, parseUpstreamTime } from "../railkit/dates.js";
import { getTrainInfo, trackTrain } from "../railkit/endpoints.js";
import { RailkitError } from "../railkit/errors.js";
import type { RawTimelineEntry, RawTrackTrain, RawTrainInfo } from "../railkit/types.js";

export interface TrainScreenDeps {
  cache: Cache;
  now?: () => Date;
}

export function registerTrainScreen(app: FastifyInstance, deps: TrainScreenDeps): void {
  app.get("/screen/train/:trainNo", async (req: FastifyRequest, reply: FastifyReply) => {
    const { trainNo } = req.params as { trainNo: string };
    const run = ((req.query as { run?: string }).run ?? "auto") as RunChoice;

    if (!TRAIN_NO_PATTERN.test(trainNo)) {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "train number must be exactly 5 digits", false));
    }
    if (run !== "auto" && run !== "today" && run !== "yesterday") {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "run must be auto, today or yesterday", false));
    }

    const now = deps.now?.() ?? new Date();
    const today = istDateString(now);
    const yesterday = istDateString(now, -1);

    try {
      // Probe both candidate runs (FR-2.3); the cache makes the second look cheap.
      // allSettled: a flaky probe for the run we don't end up showing must not
      // fail the screen — it just reads as active:false.
      const [todaySettled, yesterdaySettled, infoRes] = await Promise.all([
        settle(fetchTrack(deps.cache, trainNo, today)),
        settle(fetchTrack(deps.cache, trainNo, yesterday)),
        deps.cache.getOrFetch(`rk:trainInfo:${trainNo}`, CACHE_TTLS.trainInfo, () =>
          getTrainInfo(trainNo),
        ),
      ]);

      const choices: TrainScreen["runDateChoices"] = [
        {
          runDate: today,
          label: "today",
          active: todaySettled.ok && isActiveRun(todaySettled.result.value),
        },
        {
          runDate: yesterday,
          label: "yesterday",
          active: yesterdaySettled.ok && isActiveRun(yesterdaySettled.result.value),
        },
      ];

      const resolved =
        run === "today"
          ? today
          : run === "yesterday"
            ? yesterday
            : choices.find((c) => c.active)?.runDate ??
              (todaySettled.ok ? today : yesterday);
      const settled = resolved === today ? todaySettled : yesterdaySettled;
      if (!settled.ok) throw settled.error; // the run we must show is unavailable
      const trackRes = settled.result;

      const screen = composeTrainScreen({
        trainNo,
        runDate: resolved,
        choices,
        track: trackRes.value,
        info: infoRes.value,
        now,
      });

      return ok(screen, {
        fetchedAt: trackRes.fetchedAt,
        stale: trackRes.stale || infoRes.stale,
        ttlSeconds: CACHE_TTLS.trackTrain,
      });
    } catch (e) {
      if (e instanceof RailkitError) {
        return reply.status(HTTP_STATUS[e.code]).send(err(e.code, e.message, e.retryable));
      }
      req.log.error(e);
      return reply
        .status(HTTP_STATUS.UPSTREAM_DOWN)
        .send(err("UPSTREAM_DOWN", "unexpected error composing train screen", true));
    }
  });
}

type Settled<T> = { ok: true; result: T } | { ok: false; error: unknown };

async function settle<T>(p: Promise<T>): Promise<Settled<T>> {
  try {
    return { ok: true, result: await p };
  } catch (error) {
    return { ok: false, error };
  }
}

function fetchTrack(
  cache: Cache,
  trainNo: string,
  runDate: string,
): Promise<CachedResult<RawTrackTrain>> {
  return cache.getOrFetch(`rk:trackTrain:${trainNo}:${runDate}`, CACHE_TTLS.trackTrain, () =>
    trackTrain(trainNo, runDate),
  );
}

/** A run is active when it has started and its destination stop is not yet current. */
export function isActiveRun(track: RawTrackTrain): boolean {
  if (/yet to start/i.test(track.statusNote)) return false;
  return !hasArrived(track);
}

function hasArrived(track: RawTrackTrain): boolean {
  const stoppages = track.timeline.filter((e) => e.type === "stoppage");
  const last = stoppages[stoppages.length - 1];
  return last?.status === "current";
}

/** TrainStatus for a given run — reused by /screen/pnr's live join. */
export function composeStatus(info: RawTrainInfo, track: RawTrackTrain, runDate: string): TrainStatus {
  return buildStatus(track, buildRoute(info, track, runDate));
}

interface ComposeArgs {
  trainNo: string;
  runDate: string;
  choices: TrainScreen["runDateChoices"];
  track: RawTrackTrain;
  info: RawTrainInfo;
  now: Date;
}

export function composeTrainScreen(args: ComposeArgs): TrainScreen {
  const { trainNo, runDate, choices, track, info, now } = args;
  const route = buildRoute(info, track, runDate);
  const status = buildStatus(track, route);

  return {
    trainNo,
    name: info.trainInfo.train_name,
    runDateResolved: runDate,
    runDateChoices: choices,
    status,
    route,
    position: status.state === "running" ? interpolatePosition(route, now) : null,
    coach: buildCoachGuide(track, info),
    prediction: null, // FR-2.6 — Phase 2
  };
}

function buildRoute(info: RawTrainInfo, track: RawTrackTrain, runDate: string): RouteStop[] {
  const timelineByCode = new Map<string, RawTimelineEntry>(
    track.timeline.filter((e) => e.type === "stoppage").map((e) => [e.stationCode, e]),
  );

  const stops: RouteStop[] = info.route.map((r, i) => {
    const t = timelineByCode.get(r.stnCode);
    const isDest = i === info.route.length - 1;
    const passedOrCurrent = t?.status === "passed" || t?.status === "current";

    const state: RouteStop["state"] = isDest
      ? "destination"
      : t?.status === "passed"
        ? "passed"
        : t?.status === "current"
          ? "departed"
          : "upcoming";

    return {
      code: r.stnCode,
      name: r.stnName,
      km: Number(r.distance) || 0,
      day: Number(r.day) || 1,
      platform: platformString(r.platform, t?.platform),
      scheduled: {
        arr: parseUpstreamTime(t?.arrival?.scheduled, runDate),
        dep: parseUpstreamTime(t?.departure?.scheduled, runDate),
      },
      // Upstream copies scheduled times into "actual" for stops not yet
      // reached — honesty (FR-11.1) means actuals only for crossed stops.
      actual: passedOrCurrent
        ? {
            arr: parseUpstreamTime(t?.arrival?.actual, runDate),
            dep: parseUpstreamTime(t?.departure?.actual, runDate),
          }
        : { arr: null, dep: null },
      delayMin: passedOrCurrent
        ? parseDelayMin(t?.arrival?.delay) ?? parseDelayMin(t?.departure?.delay)
        : null,
      state,
      lat: r.coordinates?.latitude ?? null,
      lng: r.coordinates?.longitude ?? null,
    };
  });

  // The first upcoming stop after the last crossed one is "next".
  const lastCrossed = stops.reduce(
    (acc, s, i) => (s.state === "passed" || s.state === "departed" ? i : acc),
    -1,
  );
  const next = stops[lastCrossed + 1];
  if (lastCrossed >= 0 && next && next.state === "upcoming") next.state = "next";

  return stops;
}

function platformString(
  routePlatform: string | number | undefined,
  timelinePlatform: string | undefined,
): string | null {
  const value = timelinePlatform ?? routePlatform;
  if (value === undefined || value === null || value === "") return null;
  return String(value);
}

function buildStatus(track: RawTrackTrain, route: RouteStop[]): TrainStatus {
  const state = statusState(track);
  const lastIdx = route.reduce(
    (acc, s, i) => (s.delayMin !== null || s.actual.arr || s.actual.dep ? i : acc),
    -1,
  );
  const last = route[lastIdx] ?? null;
  const delayMin = last?.delayMin ?? null;

  // The stop after the last crossed one — covers both "next" and the
  // destination when the train is on its final leg.
  const nextStop = state === "running" && lastIdx >= 0 ? route[lastIdx + 1] ?? null : null;
  const etaScheduled = nextStop?.scheduled.arr ?? nextStop?.scheduled.dep ?? null;

  return {
    state,
    summary: summarize(state, delayMin, last, track),
    delayMin,
    lastStation: last ? { code: last.code, name: last.name } : null,
    nextStation:
      nextStop && etaScheduled
        ? {
            code: nextStop.code,
            name: nextStop.name,
            etaScheduled,
            etaActual: delayMin !== null ? shiftIsoMinutes(etaScheduled, delayMin) : null,
          }
        : null,
    lastUpdate: parseUpstreamDateTime(track.lastUpdate) ?? "",
  };
}

function statusState(track: RawTrackTrain): TrainStatus["state"] {
  const note = track.statusNote.toLowerCase();
  // TODO(fixtures): confirm exact upstream wording for cancelled/diverted/
  // rescheduled runs once one is captured; matching stays conservative.
  if (note.includes("cancel")) return "cancelled";
  if (note.includes("divert")) return "diverted";
  if (note.includes("reschedul")) return "rescheduled";
  if (note.includes("yet to start")) return "not_started";
  if (hasArrived(track)) return "arrived";
  return "running";
}

function summarize(
  state: TrainStatus["state"],
  delayMin: number | null,
  last: RouteStop | null,
  track: RawTrackTrain,
): string {
  switch (state) {
    case "running":
      if (delayMin === null) return "Running";
      return delayMin === 0 ? "Running · on time" : `Running · ${delayMin} min late`;
    case "arrived":
      return last ? `Arrived at ${last.name}` : "Arrived";
    case "not_started":
      return "Not started yet";
    case "cancelled":
      return "Cancelled";
    case "diverted":
      return "Diverted";
    case "rescheduled":
      return "Rescheduled";
  }
}

/** FR-2.2: time-based linear interpolation between the last crossed and next stop. */
export function interpolatePosition(route: RouteStop[], now: Date): TrainScreen["position"] {
  const lastIdx = route.reduce(
    (acc, s, i) => (s.state === "passed" || s.state === "departed" ? i : acc),
    -1,
  );
  const last = route[lastIdx];
  const next = route[lastIdx + 1];
  if (!last || !next) return null;
  if (last.lat === null || last.lng === null || next.lat === null || next.lng === null) return null;

  const departed = last.actual.dep ?? last.scheduled.dep;
  const expected = next.scheduled.arr ?? next.scheduled.dep;
  if (!departed || !expected) return null;

  const start = Date.parse(departed);
  const end = Date.parse(expected);
  const progress =
    end > start ? Math.min(1, Math.max(0, (now.getTime() - start) / (end - start))) : 0.5;

  return {
    kind: "interpolated",
    lat: last.lat + (next.lat - last.lat) * progress,
    lng: last.lng + (next.lng - last.lng) * progress,
    betweenCodes: [last.code, next.code],
    progress,
  };
}

/** FR-3.1/3.2: coach order at origin + reversal stations from the coach timeline. */
export function buildCoachGuide(track: RawTrackTrain, info: RawTrainInfo): CoachGuide | null {
  const segments = track.coachPositionTimeline;
  if (!segments || segments.length === 0 || segments[0]!.coachPosition.length === 0) return null;

  const nameByCode = new Map(info.route.map((r) => [r.stnCode, r.stnName]));
  const orderKey = (coaches: { type: string; number: string }[]) =>
    coaches.map((c) => `${c.type}:${c.number}`).join("|");

  const reversals: CoachGuide["reversals"] = [];
  for (let i = 1; i < segments.length; i += 1) {
    if (orderKey(segments[i]!.coachPosition) !== orderKey(segments[i - 1]!.coachPosition)) {
      reversals.push({
        atStationCode: segments[i]!.fromStationCode,
        atStationName: nameByCode.get(segments[i]!.fromStationCode) ?? segments[i]!.fromStationName,
      });
    }
  }

  return {
    referenceStation: segments[0]!.fromStationCode,
    order: segments[0]!.coachPosition.map((c) => ({
      type: c.type,
      number: c.number,
      position: Number(c.position),
    })),
    reversals,
  };
}

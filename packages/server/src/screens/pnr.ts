// GET /screen/pnr/:pnr — the PNR screen [FR-4.1, FR-4.3].
// The raw PNR exists only in the request path and inside this handler; the
// response, logs, and cache all carry masked/encrypted forms. Nothing PNR-
// related persists beyond the cache retention window (ttl + 24h, encrypted);
// the durable store + purge job arrive with saved watches (backlog 2.1).
import { PNR_PATTERN, type PnrScreen, type TrainStatus } from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { Cache, CACHE_TTLS, type CachedResult } from "../cache/index.js";
import { err, HTTP_STATUS, ok } from "../lib/envelope.js";
import { istDateString } from "../lib/ist.js";
import { parseUpstreamPnrDateTime } from "../railkit/dates.js";
import { checkPnrStatus, getTrainInfo, trackTrain } from "../railkit/endpoints.js";
import { RailkitError } from "../railkit/errors.js";
import type { RawPnrStatus } from "../railkit/types.js";
import { decryptPnrBlob, encryptPnrBlob, pnrCacheKey } from "../privacy/crypto.js";
import { maskPnr } from "../privacy/mask.js";
import { composeStatus, isActiveRun } from "./train.js";

export interface PnrScreenDeps {
  cache: Cache;
  now?: () => Date;
}

export function registerPnrScreen(app: FastifyInstance, deps: PnrScreenDeps): void {
  app.get("/screen/pnr/:pnr", async (req: FastifyRequest, reply: FastifyReply) => {
    const { pnr } = req.params as { pnr: string };
    if (!PNR_PATTERN.test(pnr)) {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "PNR must be exactly 10 digits", false));
    }

    const now = deps.now?.() ?? new Date();
    try {
      let res = await fetchPnr(deps.cache, pnr, CACHE_TTLS.pnr);
      let raw = JSON.parse(decryptPnrBlob(res.value)) as RawPnrStatus;

      // Contracts §10: 60s TTL inside the chart window. The window is known
      // only after a fetch, so re-evaluate freshness with the tighter TTL.
      let ttl: number = CACHE_TTLS.pnr;
      if (isInChartWindow(raw, now)) {
        ttl = CACHE_TTLS.pnrChartWindow;
        res = await fetchPnr(deps.cache, pnr, ttl);
        raw = JSON.parse(decryptPnrBlob(res.value)) as RawPnrStatus;
      }

      const screen = await composePnrScreen(raw, deps, now);
      return ok(screen, { fetchedAt: res.fetchedAt, stale: res.stale, ttlSeconds: ttl });
    } catch (e) {
      if (e instanceof RailkitError) {
        return reply.status(HTTP_STATUS[e.code]).send(err(e.code, e.message, e.retryable));
      }
      req.log.error(e);
      return reply
        .status(HTTP_STATUS.UPSTREAM_DOWN)
        .send(err("UPSTREAM_DOWN", "unexpected error composing PNR screen", true));
    }
  });
}

// Cache stores an AES-GCM blob under an HMAC key — no raw PNR at rest (FR-4.3).
function fetchPnr(cache: Cache, pnr: string, ttl: number): Promise<CachedResult<string>> {
  return cache.getOrFetch(pnrCacheKey(pnr), ttl, async () =>
    encryptPnrBlob(JSON.stringify(await checkPnrStatus(pnr))),
  );
}

/** Chart window: chart not prepared yet and departure within the next 6 hours. */
export function isInChartWindow(raw: RawPnrStatus, now: Date): boolean {
  if (chartPrepared(raw)) return false;
  const dep = parseUpstreamPnrDateTime(raw.journey.dateOfJourney);
  if (!dep) return false;
  const untilDeparture = Date.parse(dep) - now.getTime();
  return untilDeparture > 0 && untilDeparture <= 6 * 3600_000;
}

function chartPrepared(raw: RawPnrStatus): boolean {
  return /prepared/i.test(raw.chart.status) && !/not/i.test(raw.chart.status);
}

async function composePnrScreen(
  raw: RawPnrStatus,
  deps: PnrScreenDeps,
  now: Date,
): Promise<PnrScreen> {
  const departure = parseUpstreamPnrDateTime(raw.journey.dateOfJourney);
  const journeyDate = departure?.slice(0, 10) ?? "";

  return {
    pnrMasked: maskPnr(raw.pnr),
    train: { no: raw.train.number, name: raw.train.name },
    journey: {
      date: journeyDate,
      from: { code: raw.journey.source.code, name: raw.journey.source.name },
      to: { code: raw.journey.destination.code, name: raw.journey.destination.name },
      boardingPoint: {
        code: raw.journey.boardingPoint.code,
        name: raw.journey.boardingPoint.name,
      },
      cls: raw.journey.class,
      quota: raw.journey.quota,
      arrivalEta: parseUpstreamPnrDateTime(raw.journey.arrivalDate),
    },
    chart: { prepared: chartPrepared(raw) },
    passengers: raw.passengers.map((p, i) => ({
      idx: Number(p.serialNumber.replace(/\D+/g, "")) || i + 1,
      bookingStatus: p.booking.status,
      currentStatus: p.current.status,
      coach: p.current.coach || null,
      berth: p.current.berthNo ?? null,
      berthType: p.current.berthCode || null,
    })),
    fare: raw.booking ? { total: raw.booking.fare } : null,
    live: await joinLiveStatus(raw.train.number, journeyDate, deps, now),
  };
}

/** Best-effort live join (contracts §2): only for today/yesterday runs, never fails the screen. */
async function joinLiveStatus(
  trainNo: string,
  journeyDate: string,
  deps: PnrScreenDeps,
  now: Date,
): Promise<TrainStatus | null> {
  const today = istDateString(now);
  const yesterday = istDateString(now, -1);
  if (journeyDate !== today && journeyDate !== yesterday) return null;

  try {
    const [trackRes, infoRes] = await Promise.all([
      deps.cache.getOrFetch(`rk:trackTrain:${trainNo}:${journeyDate}`, CACHE_TTLS.trackTrain, () =>
        trackTrain(trainNo, journeyDate),
      ),
      deps.cache.getOrFetch(`rk:trainInfo:${trainNo}`, CACHE_TTLS.trainInfo, () =>
        getTrainInfo(trainNo),
      ),
    ]);
    if (!isActiveRun(trackRes.value)) return null;
    return composeStatus(infoRes.value, trackRes.value, journeyDate);
  } catch {
    return null;
  }
}

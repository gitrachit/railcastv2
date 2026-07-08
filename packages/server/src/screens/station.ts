// GET /screen/station/:code?hrs=4 — the station live board [FR-5.1, FR-2.4].
import {
  STATION_CODE_PATTERN,
  type StationBoardRow,
  type StationScreen,
  type WindowHrs,
} from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { Cache, CACHE_TTLS } from "../cache/index.js";
import { err, HTTP_STATUS, ok } from "../lib/envelope.js";
import { nearestIstIsoForTime } from "../lib/ist.js";
import { parseDelayMin } from "../railkit/dates.js";
import { liveAtStation } from "../railkit/endpoints.js";
import { RailkitError } from "../railkit/errors.js";
import type { RawStationBoard, RawStationBoardTrain } from "../railkit/types.js";

export interface StationScreenDeps {
  cache: Cache;
  now?: () => Date;
}

export function registerStationScreen(app: FastifyInstance, deps: StationScreenDeps): void {
  app.get("/screen/station/:code", async (req: FastifyRequest, reply: FastifyReply) => {
    const { code } = req.params as { code: string };
    const hrsRaw = (req.query as { hrs?: string }).hrs ?? "4";
    const hrs = Number(hrsRaw);

    if (!STATION_CODE_PATTERN.test(code)) {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "station code must be 2-5 uppercase letters", false));
    }
    if (hrs !== 2 && hrs !== 4 && hrs !== 8) {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "hrs must be one of 2, 4, 8", false));
    }

    const now = deps.now?.() ?? new Date();
    try {
      const res = await deps.cache.getOrFetch(
        `rk:station:${code}:${hrs}`,
        CACHE_TTLS.stationBoard,
        () => liveAtStation(code, hrs as WindowHrs),
      );

      const screen: StationScreen = {
        station: { code, name: stationNameFromSummary(res.value, code) },
        windowHrs: hrs as WindowHrs,
        trains: res.value.trains.map((t) => buildRow(t, now)),
      };

      return ok(screen, {
        fetchedAt: res.fetchedAt,
        stale: res.stale,
        ttlSeconds: CACHE_TTLS.stationBoard,
      });
    } catch (e) {
      if (e instanceof RailkitError) {
        return reply.status(HTTP_STATUS[e.code]).send(err(e.code, e.message, e.retryable));
      }
      req.log.error(e);
      return reply
        .status(HTTP_STATUS.UPSTREAM_DOWN)
        .send(err("UPSTREAM_DOWN", "unexpected error composing station board", true));
    }
  });
}

// The board payload has no station-name field; the summary line carries it:
// "23 Trains departing from/arriving at JBP- JABALPUR in next 4 Hrs."
function stationNameFromSummary(board: RawStationBoard, code: string): string {
  const m = new RegExp(`at ${code}-\\s*(.+?) in next`).exec(board.summary);
  return m?.[1]?.trim() ?? code;
}

function buildRow(t: RawStationBoardTrain, now: Date): StationBoardRow {
  const cancelled =
    t.cancelled === true || t.arrival.actual === "Cancelled" || t.departure.actual === "Cancelled";
  const arrival = buildSection(t.arrival, now);
  const departure = buildSection(t.departure, now);
  const late =
    t.arrival.delayed ||
    t.departure.delayed ||
    (arrival?.delayMin ?? 0) > 0 ||
    (departure?.delayMin ?? 0) > 0;

  return {
    no: t.trainNo,
    name: t.trainName,
    source: { code: t.source, name: t.sourceName },
    dest: { code: t.dest, name: t.destName },
    platform: t.platform && t.platform !== "-" ? t.platform : null,
    arrival, // null = originates here (contracts §3)
    departure, // null = terminates here
    status: cancelled ? "cancelled" : late ? "late" : "ontime",
    classes: t.classes ? t.classes.split(",").filter(Boolean) : [],
  };
}

function buildSection(
  sec: RawStationBoardTrain["arrival"],
  now: Date,
): StationBoardRow["arrival"] {
  // No scheduled time = the train originates/terminates at this station.
  if (!sec.scheduled) return null;
  const scheduled = nearestIstIsoForTime(sec.scheduled, now);
  if (!scheduled) return null;
  const actual = /^\d{1,2}:\d{2}$/.test(sec.actual) ? nearestIstIsoForTime(sec.actual, now) : null;
  return { scheduled, actual, delayMin: parseDelayMin(sec.delay) };
}

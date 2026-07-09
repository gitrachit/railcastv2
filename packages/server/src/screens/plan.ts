// GET /screen/plan + GET /screen/plan/row/:trainNo — journey planning
// [FR-6.1–6.4]. The list returns fast with availability/fare "pending";
// each visible row hydrates via the row endpoint on independent cache keys
// so a slow fare lookup never blocks the list (PRD §6.4).
import {
  STATION_CODE_PATTERN,
  TRAIN_NO_PATTERN,
  type Err,
  type PlanRow,
  type PlanRowHydration,
  type PlanScreen,
  type RowAvailability,
  type RowFare,
} from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { Cache, CACHE_TTLS } from "../cache/index.js";
import { err, HTTP_STATUS, ok } from "../lib/envelope.js";
import { shiftIsoMinutes } from "../lib/ist.js";
import { fareLookup, getAvailability, searchTrainsBetween } from "../railkit/endpoints.js";
import { RailkitError } from "../railkit/errors.js";
import type { RawAvailability, RawFareLookup, RawSearchBetweenItem } from "../railkit/types.js";

const QUOTAS: ReadonlySet<string> = new Set(["GN", "TQ", "SS", "LD"]);
const API_DATE = /^\d{4}-\d{2}-\d{2}$/;
const CLS = /^[A-Z0-9]{1,3}$/;

export interface PlanScreenDeps {
  cache: Cache;
}

export function registerPlanScreen(app: FastifyInstance, deps: PlanScreenDeps): void {
  app.get("/screen/plan", async (req: FastifyRequest, reply: FastifyReply) => {
    const q = req.query as { from?: string; to?: string; date?: string; quota?: string };
    const quota = q.quota ?? "GN";
    const invalid = validate(q.from, q.to, q.date, quota);
    if (invalid) return reply.status(HTTP_STATUS.INVALID_INPUT).send(invalid);
    const { from, to, date } = q as { from: string; to: string; date: string };

    try {
      const res = await deps.cache.getOrFetch(`rk:search:${from}:${to}`, CACHE_TTLS.search, () =>
        searchTrainsBetween(from, to),
      );

      const screen: PlanScreen = {
        from: refFrom(res.value, "from", from),
        to: refFrom(res.value, "to", to),
        date,
        quota,
        trains: res.value.map((t) => buildRow(t, date)),
      };

      return ok(screen, {
        fetchedAt: res.fetchedAt,
        stale: res.stale,
        ttlSeconds: CACHE_TTLS.search,
      });
    } catch (e) {
      return sendScreenError(req, reply, e, "plan");
    }
  });

  // Row hydration (FR-6.2): availability + fare on independent cache keys.
  app.get("/screen/plan/row/:trainNo", async (req: FastifyRequest, reply: FastifyReply) => {
    const { trainNo } = req.params as { trainNo: string };
    const q = req.query as {
      from?: string;
      to?: string;
      date?: string;
      cls?: string;
      quota?: string;
    };
    const quota = q.quota ?? "GN";
    const invalid =
      validate(q.from, q.to, q.date, quota) ??
      (!TRAIN_NO_PATTERN.test(trainNo)
        ? err("INVALID_INPUT", "train number must be exactly 5 digits", false)
        : !q.cls || !CLS.test(q.cls)
          ? err("INVALID_INPUT", "cls must be a class code like 2A, 3A, SL, CC", false)
          : null);
    if (invalid) return reply.status(HTTP_STATUS.INVALID_INPUT).send(invalid);
    const { from, to, date, cls } = q as { from: string; to: string; date: string; cls: string };

    try {
      const key = `${trainNo}:${from}:${to}:${date}:${cls}:${quota}`;
      const [availRes, fareRes] = await Promise.all([
        deps.cache.getOrFetch(`rk:avail:${key}`, CACHE_TTLS.availability, () =>
          getAvailability(trainNo, from, to, date, cls, quota),
        ),
        deps.cache.getOrFetch(`rk:fare:${key}`, CACHE_TTLS.fare, () =>
          fareLookup(trainNo, date, from, to, cls, quota),
        ),
      ]);

      const hydration: PlanRowHydration = {
        availability: buildAvailability(availRes.value, date),
        fare: buildFare(fareRes.value),
      };

      return ok(hydration, {
        fetchedAt: availRes.fetchedAt, // availability is the volatile half
        stale: availRes.stale || fareRes.stale,
        ttlSeconds: CACHE_TTLS.availability,
      });
    } catch (e) {
      return sendScreenError(req, reply, e, "plan row");
    }
  });
}

function validate(
  from: string | undefined,
  to: string | undefined,
  date: string | undefined,
  quota: string,
): Err | null {
  if (!from || !STATION_CODE_PATTERN.test(from) || !to || !STATION_CODE_PATTERN.test(to)) {
    return err("INVALID_INPUT", "from and to must be 2-5 uppercase station codes", false);
  }
  if (!date || !API_DATE.test(date)) {
    return err("INVALID_INPUT", "date must be YYYY-MM-DD", false);
  }
  if (!QUOTAS.has(quota)) {
    return err("INVALID_INPUT", "quota must be one of GN, TQ, SS, LD", false);
  }
  return null;
}

function sendScreenError(
  req: FastifyRequest,
  reply: FastifyReply,
  e: unknown,
  what: string,
): FastifyReply {
  if (e instanceof RailkitError) {
    return reply.status(HTTP_STATUS[e.code]).send(err(e.code, e.message, e.retryable));
  }
  req.log.error(e);
  return reply
    .status(HTTP_STATUS.UPSTREAM_DOWN)
    .send(err("UPSTREAM_DOWN", `unexpected error composing ${what}`, true));
}

function refFrom(
  items: RawSearchBetweenItem[],
  side: "from" | "to",
  code: string,
): { code: string; name: string } {
  const first = items[0];
  const name = side === "from" ? first?.from_stn_name : first?.to_stn_name;
  return { code, name: name ?? code };
}

function buildRow(t: RawSearchBetweenItem, date: string): PlanRow {
  const durationMin = parseTravelTime(t.travel_time);
  const dep = `${date}T${t.from_time}:00+05:30`;

  return {
    no: t.train_no,
    name: t.train_name,
    dep,
    arr: shiftIsoMinutes(dep, durationMin),
    durationMin,
    // searchTrainBetweenStations carries no class list; the directory dataset
    // (WS-B, backlog 3.5) fills this — hydration still works per chosen class.
    classes: [],
    // Upstream running_days is Sun-first (verified: 22938 "0100000" ran Monday),
    // which is exactly the contract's [Sun..Sat].
    runsOn: t.running_days.split("").map((c) => c === "1"),
    punctuality: null, // Phase 2 (FR-6.2)
    availability: "pending",
    fare: "pending",
  };
}

/** "01:03 hrs" → 63, "12:30 hrs" → 750. */
function parseTravelTime(value: string): number {
  const m = /^(\d{1,2}):(\d{2})/.exec(value.trim());
  if (!m) return 0;
  return Number(m[1]) * 60 + Number(m[2]);
}

function buildAvailability(raw: RawAvailability, date: string): RowAvailability {
  // Upstream is sloppy about zero-padding ("15-7-2026") — normalize to compare.
  const wanted = date.split("-").reverse().map(Number).join("-"); // "2026-07-15" → "15-7-2026"
  const slot =
    raw.availability.find((a) => a.date.split("-").map(Number).join("-") === wanted) ??
    raw.availability[0];

  const text = slot?.availabilityText ?? "";
  const status = /RAC/i.test(text)
    ? "rac"
    : /WL|WAIT/i.test(text + (slot?.status ?? ""))
      ? "waitlist"
      : /AVL|AVAILABLE/i.test(text + (slot?.status ?? ""))
        ? "available"
        : "not_available";

  return {
    status,
    text,
    predictionPct: slot?.predictionPercentage ?? null,
    canBook: slot?.canBook ?? false,
  };
}

function buildFare(raw: RawFareLookup): RowFare {
  return {
    total: raw.totalFare,
    breakdown: {
      base: raw.baseFare,
      reservation: raw.reservation,
      superfast: raw.superfast,
      tatkal: raw.tatkalFare,
      gst: raw.gst,
      dynamic: raw.dynamicFare,
      other: raw.fuelAmount + raw.concession + raw.otherCharge + raw.catering,
    },
  };
}

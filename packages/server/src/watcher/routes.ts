// Watch & push-token API (backlog 2.4, contracts §5). All routes run behind
// the Bearer hook and are scoped to req.deviceId. Creating a watch also arms
// the scheduler chain so it polls immediately (not just on the next reboot).
import {
  PNR_PATTERN,
  STATION_CODE_PATTERN,
  TRAIN_NO_PATTERN,
  type CreateWatchRequest,
  type CreateWatchResponse,
  type PushTokenRequest,
  type WatchType,
} from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { err, HTTP_STATUS, immediateMeta, ok } from "../lib/envelope.js";
import { entityKeyFor, type WatchRepo } from "./repo.js";

const WATCH_TYPES: ReadonlySet<string> = new Set([
  "chart",
  "delay",
  "platform",
  "cancel",
  "arrival",
  "tatkal",
]);
const API_DATE = /^\d{4}-\d{2}-\d{2}$/;

export interface WatchRoutesDeps {
  repo: WatchRepo;
  /** Arm/refresh the scheduler poll chain for an entity (idempotent). */
  armEntity?: (entityKey: string) => Promise<void>;
}

export function registerWatchRoutes(app: FastifyInstance, deps: WatchRoutesDeps): void {
  app.post("/watch", async (req: FastifyRequest, reply: FastifyReply) => {
    const deviceId = req.deviceId!;
    const body = req.body as Partial<CreateWatchRequest> | null;

    const invalid = validateCreate(body);
    if (invalid) return reply.status(HTTP_STATUS.INVALID_INPUT).send(err("INVALID_INPUT", invalid, false));
    const request = body as CreateWatchRequest;

    const { watchId, expiresAt } = await deps.repo.create(deviceId, request);
    // Arm the poll chain now so the watch takes effect immediately (FR-7.1).
    await deps.armEntity?.(entityKeyFor(request.entity));

    const data: CreateWatchResponse = { watchId, expiresAt };
    return ok(data, immediateMeta());
  });

  app.get("/watch", async (req: FastifyRequest) => {
    const watches = await deps.repo.listForDevice(req.deviceId!);
    return ok({ watches }, immediateMeta());
  });

  app.delete("/watch/:watchId", async (req: FastifyRequest, reply: FastifyReply) => {
    const { watchId } = req.params as { watchId: string };
    const removed = await deps.repo.delete(req.deviceId!, watchId);
    if (!removed) {
      return reply.status(HTTP_STATUS.NOT_FOUND).send(err("NOT_FOUND", "watch not found", false));
    }
    return ok({}, immediateMeta());
  });

  app.post("/device/push-token", async (req: FastifyRequest, reply: FastifyReply) => {
    const body = req.body as Partial<PushTokenRequest> | null;
    if (!body || typeof body.fcmToken !== "string" || body.fcmToken.length === 0) {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", "body must be { fcmToken: string }", false));
    }
    await deps.repo.setPushToken(req.deviceId!, body.fcmToken);
    return ok({}, immediateMeta());
  });
}

function validateCreate(body: Partial<CreateWatchRequest> | null): string | null {
  if (!body || !WATCH_TYPES.has(body.type as string)) {
    return "type must be one of chart, delay, platform, cancel, arrival, tatkal";
  }
  const entity = body.entity;
  if (!entity || (entity.kind !== "pnr" && entity.kind !== "train")) {
    return "entity must be a pnr or train reference";
  }
  if (entity.kind === "pnr" && !PNR_PATTERN.test(entity.pnr)) {
    return "pnr must be exactly 10 digits";
  }
  if (entity.kind === "train") {
    if (!TRAIN_NO_PATTERN.test(entity.trainNo)) return "trainNo must be exactly 5 digits";
    if (!API_DATE.test(entity.runDate)) return "runDate must be YYYY-MM-DD";
  }
  // Type/entity coherence + required params.
  const type = body.type as WatchType;
  if (type === "chart" && entity.kind !== "pnr") return "chart watches require a pnr entity";
  if ((type === "delay" || type === "platform" || type === "cancel" || type === "arrival" || type === "tatkal") && entity.kind !== "train") {
    return `${type} watches require a train entity`;
  }
  if (type === "delay" && !(body.params?.delayThresholdMin! > 0)) {
    return "delay watches require params.delayThresholdMin > 0";
  }
  if (type === "arrival") {
    if (!body.params?.stationCode || !STATION_CODE_PATTERN.test(body.params.stationCode)) {
      return "arrival watches require a valid params.stationCode";
    }
    if (!(body.params?.leadMin! > 0)) return "arrival watches require params.leadMin > 0";
  }
  if (type === "tatkal" && body.params?.tatkalBand !== "ac" && body.params?.tatkalBand !== "nonac") {
    return "tatkal watches require params.tatkalBand of ac or nonac";
  }
  return null;
}

import type { DeviceAuthRequest, DeviceAuthResponse } from "@railcast/shared";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { err, HTTP_STATUS, immediateMeta, ok } from "../lib/envelope.js";
import { RateLimiter, type RateStore } from "./rate-limit.js";
import { issueDeviceToken, verifyDeviceToken } from "./tokens.js";

declare module "fastify" {
  interface FastifyRequest {
    deviceId: string | null;
  }
}

export interface AuthOptions {
  rateStore: RateStore;
  /** Authenticated requests per device per minute. */
  deviceLimitPerMin?: number;
  /** Token mints per IP per minute on /auth/device. */
  authLimitPerMin?: number;
}

// Routes that must work without a token: health, token minting, and the
// public shared-journey pages (FR-8.1, FR-10.5 — no forced login, ever).
function isPublic(url: string): boolean {
  const path = url.split("?")[0] ?? url;
  return (
    path === "/health" ||
    path === "/privacy" ||
    path === "/auth/device" ||
    path.startsWith("/t/")
  );
}

export function registerAuth(app: FastifyInstance, opts: AuthOptions): void {
  const deviceLimiter = new RateLimiter(opts.rateStore, opts.deviceLimitPerMin ?? 120);
  const authLimiter = new RateLimiter(opts.rateStore, opts.authLimitPerMin ?? 10);

  app.decorateRequest("deviceId", null);

  app.post("/auth/device", async (req: FastifyRequest, reply: FastifyReply) => {
    if (!(await authLimiter.allow(`ip:${req.ip}`))) {
      return reply
        .status(HTTP_STATUS.RATE_LIMITED)
        .send(err("RATE_LIMITED", "too many token requests, retry later", true));
    }

    const body = req.body as Partial<DeviceAuthRequest> | null;
    if (body?.platform !== "android" || typeof body.appVersion !== "string") {
      return reply
        .status(HTTP_STATUS.INVALID_INPUT)
        .send(err("INVALID_INPUT", 'body must be { platform: "android", appVersion: string }', false));
    }

    const { deviceId, token } = issueDeviceToken();
    req.log.info({ deviceId, appVersion: body.appVersion }, "device token issued");
    const data: DeviceAuthResponse = { deviceToken: token };
    return ok(data, immediateMeta());
  });

  app.addHook("onRequest", async (req: FastifyRequest, reply: FastifyReply) => {
    if (isPublic(req.url)) return;

    const header = req.headers.authorization;
    const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
    const deviceId = token ? verifyDeviceToken(token) : null;
    if (!deviceId) {
      return reply
        .status(HTTP_STATUS.UNAUTHORIZED)
        .send(err("UNAUTHORIZED", "missing or invalid device token", false));
    }

    if (!(await deviceLimiter.allow(`dev:${deviceId}`))) {
      return reply
        .status(HTTP_STATUS.RATE_LIMITED)
        .send(err("RATE_LIMITED", "rate limit exceeded, respect meta.ttlSeconds", true));
    }

    req.deviceId = deviceId;
  });
}

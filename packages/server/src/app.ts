import type { Health, Ok } from "@railcast/shared";
import Fastify, { type FastifyInstance } from "fastify";
import { registerAuth, type AuthOptions } from "./auth/plugin.js";
import { MemoryRateStore } from "./auth/rate-limit.js";

const startedAt = Date.now();

export interface AppOptions {
  auth?: Partial<AuthOptions>;
}

export function buildApp(opts: AppOptions = {}): FastifyInstance {
  const app = Fastify({ logger: process.env.NODE_ENV !== "test" });

  registerAuth(app, {
    rateStore: opts.auth?.rateStore ?? new MemoryRateStore(),
    ...opts.auth,
  });

  // Contracts §9. `upstream` becomes a real check when the railkit client lands (backlog 1.1).
  app.get("/health", async (): Promise<Ok<Health>> => ({
    ok: true,
    data: {
      uptimeS: Math.round((Date.now() - startedAt) / 1000),
      upstream: "ok",
    },
    meta: {
      fetchedAt: new Date().toISOString(),
      stale: false,
      ttlSeconds: 0,
    },
  }));

  return app;
}

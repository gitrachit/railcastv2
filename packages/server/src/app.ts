import type { Health, Ok } from "@railcast/shared";
import Fastify, { type FastifyInstance } from "fastify";
import { registerAuth, type AuthOptions } from "./auth/plugin.js";
import { MemoryRateStore } from "./auth/rate-limit.js";
import { Cache, MemoryStore, type CacheStore } from "./cache/index.js";
import { registerTrainScreen } from "./screens/train.js";

const startedAt = Date.now();

export interface AppOptions {
  auth?: Partial<AuthOptions>;
  cacheStore?: CacheStore;
  /** Injectable clock for tests (run-date probing is IST-time-dependent). */
  now?: () => Date;
}

export function buildApp(opts: AppOptions = {}): FastifyInstance {
  const app = Fastify({ logger: process.env.NODE_ENV !== "test" });
  const cache = new Cache(opts.cacheStore ?? new MemoryStore());

  registerAuth(app, {
    rateStore: opts.auth?.rateStore ?? new MemoryRateStore(),
    ...opts.auth,
  });

  registerTrainScreen(app, { cache, now: opts.now });

  // Contracts §9. `upstream` becomes a real probe when watcher health lands (M2).
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

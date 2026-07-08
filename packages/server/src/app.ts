import type { Health, Ok } from "@railcast/shared";
import Fastify, { type FastifyInstance } from "fastify";
import { registerAuth, type AuthOptions } from "./auth/plugin.js";
import { MemoryRateStore } from "./auth/rate-limit.js";
import { Cache, MemoryStore, type CacheStore } from "./cache/index.js";
import { registerPnrScreen } from "./screens/pnr.js";
import { registerTrainScreen } from "./screens/train.js";

const startedAt = Date.now();

export interface AppOptions {
  auth?: Partial<AuthOptions>;
  cacheStore?: CacheStore;
  /** Injectable clock for tests (run-date probing is IST-time-dependent). */
  now?: () => Date;
}

/** PNRs never reach log lines, including request-URL logging (FR-4.3). */
export function redactPnrInUrl(url: string): string {
  return url.replace(/(\/screen\/pnr\/)\d*(\d{4})/g, (_m, prefix: string, tail: string) => {
    return `${prefix}••••${tail}`;
  });
}

export function buildApp(opts: AppOptions = {}): FastifyInstance {
  const app = Fastify({
    logger:
      process.env.NODE_ENV === "test"
        ? false
        : {
            serializers: {
              req: (req: { method: string; url: string }) => ({
                method: req.method,
                url: redactPnrInUrl(req.url),
              }),
            },
          },
  });
  const cache = new Cache(opts.cacheStore ?? new MemoryStore());

  registerAuth(app, {
    rateStore: opts.auth?.rateStore ?? new MemoryRateStore(),
    ...opts.auth,
  });

  registerTrainScreen(app, { cache, now: opts.now });
  registerPnrScreen(app, { cache, now: opts.now });

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

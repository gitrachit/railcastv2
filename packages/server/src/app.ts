import type { Health, Ok } from "@railcast/shared";
import Fastify, { type FastifyInstance } from "fastify";

const startedAt = Date.now();

export function buildApp(): FastifyInstance {
  const app = Fastify({ logger: process.env.NODE_ENV !== "test" });

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

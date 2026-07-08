import Fastify, { type FastifyInstance } from "fastify";

const startedAt = Date.now();

export function buildApp(): FastifyInstance {
  const app = Fastify({ logger: process.env.NODE_ENV !== "test" });

  // Contracts §9. `upstream` becomes a real check when the railkit client lands (backlog 1.1).
  app.get("/health", async () => ({
    ok: true as const,
    data: {
      uptimeS: Math.round((Date.now() - startedAt) / 1000),
      upstream: "ok" as const,
    },
    meta: {
      fetchedAt: new Date().toISOString(),
      stale: false,
      ttlSeconds: 0,
    },
  }));

  return app;
}

import { Redis } from "ioredis";
import { RedisRateStore } from "./auth/rate-limit.js";
import { buildApp } from "./app.js";
import { Cache, RedisStore } from "./cache/index.js";
import { migrate } from "./db/migrate.js";
import { createPool } from "./db/pool.js";
import { createSender, EntityPoller, PushFanout, WatchRepo, WatchScheduler } from "./watcher/index.js";
import { ShareRepo } from "./web/share-repo.js";

// Secrets are read lazily at call sites; check them here too so a deploy
// missing one fails at boot instead of passing /health and 500ing later.
for (const name of ["RAILKIT_API_KEY", "AUTH_TOKEN_SECRET"]) {
  if (!process.env[name]) throw new Error(`${name} is not configured`);
}
if (!/^[0-9a-f]{64}$/i.test(process.env.PNR_ENCRYPTION_KEY ?? "")) {
  throw new Error("PNR_ENCRYPTION_KEY must be 64 hex characters (32 bytes)");
}

const redisUrl = process.env.REDIS_URL ?? "redis://127.0.0.1:6379";
const redis = new Redis(redisUrl);
const pool = createPool();
await migrate(pool);

// Watcher service (FR-7.1): entity poll chains; the diff engine (2.2) feeds
// typed events to the push fan-out (2.3).
const repo = new WatchRepo(pool);
const scheduler = new WatchScheduler(redisUrl);
const fanout = new PushFanout({ store: repo, sender: createSender((msg) => console.info(msg)) });
const poller = new EntityPoller({
  cache: new Cache(new RedisStore(redis)),
  repo,
  onEvent: (event) => fanout.deliver(event),
});

const app = buildApp({
  auth: { rateStore: new RedisRateStore(redis) },
  cacheStore: new RedisStore(redis),
  // Creating a watch arms its poll chain immediately (2.4).
  watch: { repo, armEntity: (entityKey) => scheduler.ensureScheduled(entityKey) },
  share: { repo: new ShareRepo(pool) },
});

scheduler.start((entityKey) => poller.poll(entityKey));

// Serve as soon as the schema is ready — /health must NOT wait on the watcher
// warm-up below. Resuming every active watch's poll-chain touches Redis/BullMQ,
// and a slow cold-start there would otherwise stall boot past the platform
// healthcheck window and fail an otherwise-fine deploy.
const port = Number(process.env.PORT ?? 3000);
try {
  await app.listen({ port, host: "0.0.0.0" });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}

// Warm existing watch poll-chains in the background (does not gate /health).
repo
  .activeEntityKeys()
  .then((keys) => scheduler.resume(keys))
  .catch((err) => app.log.error({ err }, "watch scheduler resume failed"));

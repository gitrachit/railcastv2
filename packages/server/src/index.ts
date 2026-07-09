import { Redis } from "ioredis";
import { RedisRateStore } from "./auth/rate-limit.js";
import { buildApp } from "./app.js";
import { Cache, RedisStore } from "./cache/index.js";
import { migrate } from "./db/migrate.js";
import { createPool } from "./db/pool.js";
import { createSender, EntityPoller, PushFanout, WatchRepo, WatchScheduler } from "./watcher/index.js";
import { ShareRepo } from "./web/share-repo.js";

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
await scheduler.resume(await repo.activeEntityKeys());

const port = Number(process.env.PORT ?? 3000);
app.listen({ port, host: "0.0.0.0" }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});

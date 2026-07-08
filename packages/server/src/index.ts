import { Redis } from "ioredis";
import { RedisRateStore } from "./auth/rate-limit.js";
import { buildApp } from "./app.js";
import { RedisStore } from "./cache/index.js";

const redis = new Redis(process.env.REDIS_URL ?? "redis://127.0.0.1:6379");
const app = buildApp({
  auth: { rateStore: new RedisRateStore(redis) },
  cacheStore: new RedisStore(redis),
});
const port = Number(process.env.PORT ?? 3000);

app.listen({ port, host: "0.0.0.0" }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});

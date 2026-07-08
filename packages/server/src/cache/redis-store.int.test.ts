// Integration test against real Redis. Runs in CI (redis service on 6379)
// and locally via infra/docker-compose.yml; skips when Redis is unreachable.
import { Redis } from "ioredis";
import { afterAll, describe, expect, it } from "vitest";
import { Cache } from "./cache.js";
import { RedisStore } from "./store.js";

const redis = new Redis(process.env.REDIS_URL ?? "redis://127.0.0.1:6379", {
  lazyConnect: true,
  connectTimeout: 500,
  retryStrategy: () => null,
  maxRetriesPerRequest: 0,
});

const available = await redis
  .connect()
  .then(() => true)
  .catch(() => false);

afterAll(() => redis.disconnect());

describe.skipIf(!available)("RedisStore (integration)", () => {
  const key = `test:railcast:${Date.now()}`;

  it("round-trips entries with PX expiry and NX locks", async () => {
    const store = new RedisStore(redis);
    await store.set(key, "v1", 5_000);
    expect(await store.get(key)).toBe("v1");

    expect(await store.setNx(`${key}:lock`, "1", 5_000)).toBe(true);
    expect(await store.setNx(`${key}:lock`, "1", 5_000)).toBe(false); // held
    await store.del(`${key}:lock`);
    expect(await store.setNx(`${key}:lock`, "1", 5_000)).toBe(true); // released

    await store.del(key);
    await store.del(`${key}:lock`);
    expect(await store.get(key)).toBeNull();
  });

  it("backs the Cache end-to-end", async () => {
    const cache = new Cache(new RedisStore(redis));
    const k = `${key}:cache`;
    const r1 = await cache.getOrFetch(k, 60, async () => ({ hello: "redis" }));
    const r2 = await cache.getOrFetch(k, 60, async () => {
      throw new Error("must be served from redis");
    });
    expect(r1.stale).toBe(false);
    expect(r2.value).toEqual({ hello: "redis" });
    await redis.del(k);
  });
});

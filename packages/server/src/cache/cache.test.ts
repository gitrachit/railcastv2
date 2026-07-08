import { describe, expect, it, vi } from "vitest";
import { RailkitError } from "../railkit/errors.js";
import { Cache } from "./cache.js";
import { MemoryStore } from "./store.js";
import { CACHE_TTLS } from "./ttls.js";

function seed(store: MemoryStore, key: string, value: unknown, ageMs: number): Promise<void> {
  return store.set(key, JSON.stringify({ v: value, at: Date.now() - ageMs }), 3600_000);
}

describe("Cache.getOrFetch", () => {
  it("fetches on miss and serves from cache within TTL", async () => {
    const cache = new Cache(new MemoryStore());
    const fetcher = vi.fn().mockResolvedValue({ n: 1 });

    const first = await cache.getOrFetch("k", 60, fetcher);
    const second = await cache.getOrFetch("k", 60, fetcher);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(first.stale).toBe(false);
    expect(second.value).toEqual({ n: 1 });
    expect(second.ttlSeconds).toBe(60);
    expect(Date.parse(second.fetchedAt)).not.toBeNaN();
  });

  it("single-flight: 50 concurrent waiters share one upstream call", async () => {
    const cache = new Cache(new MemoryStore());
    let calls = 0;
    const fetcher = async () => {
      calls += 1;
      await new Promise((r) => setTimeout(r, 50));
      return { n: calls };
    };

    const results = await Promise.all(
      Array.from({ length: 50 }, () => cache.getOrFetch("hot", 60, fetcher)),
    );

    expect(calls).toBe(1);
    for (const r of results) expect(r.value).toEqual({ n: 1 });
  });

  it("refetches once the TTL has lapsed", async () => {
    const store = new MemoryStore();
    const cache = new Cache(store);
    await seed(store, "k", { n: 1 }, 61_000); // fetched 61s ago, ttl 60s
    const fetcher = vi.fn().mockResolvedValue({ n: 2 });

    const result = await cache.getOrFetch("k", 60, fetcher);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(result.value).toEqual({ n: 2 });
    expect(result.stale).toBe(false);
  });

  it("serves stale with stale=true when the refresh fails (upstream down)", async () => {
    const store = new MemoryStore();
    const cache = new Cache(store);
    await seed(store, "k", { n: 1 }, 61_000);
    const fetcher = vi
      .fn()
      .mockRejectedValue(new RailkitError("UPSTREAM_DOWN", "unreachable", true));

    const result = await cache.getOrFetch("k", 60, fetcher);

    expect(result.value).toEqual({ n: 1 });
    expect(result.stale).toBe(true);
  });

  it("rethrows when the fetch fails and there is nothing to serve", async () => {
    const cache = new Cache(new MemoryStore());
    const fetcher = vi
      .fn()
      .mockRejectedValue(new RailkitError("UPSTREAM_DOWN", "unreachable", true));

    await expect(cache.getOrFetch("k", 60, fetcher)).rejects.toMatchObject({
      code: "UPSTREAM_DOWN",
    });
  });

  it("SWR: serves stale immediately while a refresh for the same key is in flight", async () => {
    const store = new MemoryStore();
    const cache = new Cache(store);
    await seed(store, "k", { n: 1 }, 61_000);

    let release!: (v: { n: number }) => void;
    const slowFetch = new Promise<{ n: number }>((r) => {
      release = r;
    });
    const refreshing = cache.getOrFetch("k", 60, () => slowFetch); // takes the flight

    const servedWhileRefreshing = await cache.getOrFetch("k", 60, async () => {
      throw new Error("second fetcher must not run");
    });
    expect(servedWhileRefreshing.value).toEqual({ n: 1 });
    expect(servedWhileRefreshing.stale).toBe(true);

    release({ n: 2 });
    const refreshed = await refreshing;
    expect(refreshed.value).toEqual({ n: 2 });
    expect(refreshed.stale).toBe(false);
  });

  it("cross-instance single-flight: a lockless waiter picks up the leader's write", async () => {
    const store = new MemoryStore(); // shared, like one Redis behind two processes
    const leader = new Cache(store);
    const follower = new Cache(store);

    let release!: (v: { n: number }) => void;
    const slowFetch = new Promise<{ n: number }>((r) => {
      release = r;
    });
    const leaderCall = leader.getOrFetch("k", 60, () => slowFetch);
    await new Promise((r) => setTimeout(r, 10)); // let the leader take the lock

    const followerFetcher = vi.fn();
    const followerCall = follower.getOrFetch("k", 60, followerFetcher);
    release({ n: 7 });

    const [a, b] = await Promise.all([leaderCall, followerCall]);
    expect(followerFetcher).not.toHaveBeenCalled();
    expect(a.value).toEqual({ n: 7 });
    expect(b.value).toEqual({ n: 7 });
  });

  it("exposes the contracts §10 TTL table", () => {
    expect(CACHE_TTLS.trackTrain).toBe(45);
    expect(CACHE_TTLS.stationBoard).toBe(90);
    expect(CACHE_TTLS.pnr).toBe(180);
    expect(CACHE_TTLS.pnrChartWindow).toBe(60);
    expect(CACHE_TTLS.availability).toBe(600);
    expect(CACHE_TTLS.fare).toBe(1200);
    expect(CACHE_TTLS.search).toBe(12 * 3600);
    expect(CACHE_TTLS.trainInfo).toBe(7 * 24 * 3600);
    expect(CACHE_TTLS.history).toBe(30 * 24 * 3600);
  });
});

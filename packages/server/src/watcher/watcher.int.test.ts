// Integration: repo against real Postgres, scheduler against real Redis,
// poller lifecycle with fixture-stubbed upstream. Skips locally when the
// backing service is absent; CI provides both.
import { readFileSync } from "node:fs";
import { Redis } from "ioredis";
import pg from "pg";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Cache, MemoryStore } from "../cache/index.js";
import { migrate } from "../db/migrate.js";
import { EntityPoller } from "./poller.js";
import { entityKeyFor, WatchRepo } from "./repo.js";
import { WatchScheduler } from "./scheduler.js";

process.env.PNR_ENCRYPTION_KEY = "c".repeat(64);
process.env.RAILKIT_API_KEY = "test-api-key";

const pool = new pg.Pool({
  connectionString:
    process.env.DATABASE_URL ?? "postgres://railcast:railcast_dev@127.0.0.1:5432/railcast",
  max: 4,
  connectionTimeoutMillis: 800,
});
const pgUp = await pool
  .query("SELECT 1")
  .then(() => true)
  .catch(() => false);
if (pgUp) await migrate(pool);

const redis = new Redis(process.env.REDIS_URL ?? "redis://127.0.0.1:6379", {
  lazyConnect: true,
  connectTimeout: 500,
  retryStrategy: () => null,
  maxRetriesPerRequest: 0,
});
const redisUp = await redis
  .connect()
  .then(() => true)
  .catch(() => false);

afterAll(async () => {
  await pool.end();
  redis.disconnect();
});

function fixture(name: string): string {
  return readFileSync(new URL(`../railkit/__fixtures__/${name}`, import.meta.url), "utf8");
}

describe.skipIf(!pgUp)("WatchRepo (postgres)", () => {
  const repo = new WatchRepo(pool);

  beforeEach(async () => {
    await pool.query("TRUNCATE watch, device_push_token");
  });

  it("creates, dedups, lists (masked), deletes", async () => {
    const req = {
      type: "chart" as const,
      entity: { kind: "pnr" as const, pnr: "8524132882" },
    };
    const first = await repo.create("device-1", req);
    const again = await repo.create("device-1", req); // same (device,type,entity) → upsert
    expect(again.watchId).toBe(first.watchId);

    const listed = await repo.listForDevice("device-1");
    expect(listed).toHaveLength(1);
    expect(listed[0]!.entity).toEqual({ kind: "pnr", pnrMasked: "••••2882" });
    expect(JSON.stringify(listed)).not.toContain("8524132882");

    expect(await repo.delete("other-device", first.watchId)).toBe(false); // not yours
    expect(await repo.delete("device-1", first.watchId)).toBe(true);
    expect(await repo.listForDevice("device-1")).toHaveLength(0);
  });

  it("stores no raw PNR in any column", async () => {
    await repo.create("device-1", {
      type: "chart",
      entity: { kind: "pnr", pnr: "8524132882" },
    });
    const rows = await pool.query("SELECT * FROM watch");
    expect(JSON.stringify(rows.rows)).not.toContain("8524132882");
  });

  it("finds active watches per entity and updates state hashes", async () => {
    const entity = { kind: "train" as const, trainNo: "22188", runDate: "2026-07-08" };
    await repo.create("d1", { type: "delay", entity, params: { delayThresholdMin: 15 } });
    await repo.create("d2", { type: "cancel", entity });

    const key = entityKeyFor(entity);
    expect(key).toBe("train:22188:2026-07-08");
    const active = await repo.activeForEntity(key);
    expect(active).toHaveLength(2);
    expect(active[0]!.entity).toEqual(entity);
    expect(await repo.activeEntityKeys()).toEqual([key]);

    await repo.updateStateHash(
      active.map((w) => w.id),
      "abc123",
    );
    const after = await repo.activeForEntity(key);
    expect(after.every((w) => w.stateHash === "abc123")).toBe(true);
  });

  it("tightens expiry and purges past the grace window (FR-4.3)", async () => {
    const entity = { kind: "train" as const, trainNo: "22188", runDate: "2026-07-08" };
    await repo.create("d1", { type: "cancel", entity });
    const key = entityKeyFor(entity);

    await repo.setEntityExpiry(key, new Date(Date.now() - 3 * 86_400_000));
    expect(await repo.activeForEntity(key)).toHaveLength(0); // expired → inactive
    expect(await repo.purgeExpired(2)).toBe(1); // hard-deleted with its encrypted blob
  });

  it("upserts push tokens", async () => {
    await repo.setPushToken("d1", "tok-1");
    await repo.setPushToken("d1", "tok-2");
    expect(await repo.pushTokensFor(["d1"])).toEqual(new Map([["d1", "tok-2"]]));
  });
});

describe.skipIf(!pgUp)("EntityPoller lifecycle", () => {
  const repo = new WatchRepo(pool);
  const entity = { kind: "train" as const, trainNo: "22188", runDate: "2026-07-08" };
  const key = entityKeyFor(entity);

  beforeEach(async () => {
    await pool.query("TRUNCATE watch, device_push_token");
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(new Response(fixture("trackTrain-22188-running.json")))),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("baselines silently, fires on transition, expires on arrival, stops when empty", async () => {
    await repo.create("d1", { type: "cancel", entity });
    const changes: string[] = [];
    const poller = new EntityPoller({
      cache: new Cache(new MemoryStore()),
      repo,
      onStateChange: async (c) => {
        changes.push(...c.changedWatches.map((w) => `${w.type}:${c.state.kind}`));
      },
    });

    // Poll 1: baseline — hash stored, no notification for a brand-new watch.
    const r1 = await poller.poll(key);
    expect(r1?.nextDelayS).toBe(300);
    expect(changes).toHaveLength(0);

    // Poll 2: same state — no change, no notification. Fresh cache instance
    // forces a refetch of the same fixture.
    const r2 = await poller.poll(key);
    expect(r2?.nextDelayS).toBe(300);
    expect(changes).toHaveLength(0);

    // Poll 3: the run completes → transition fires + expiry tightens to ~+24h.
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(new Response(fixture("trackTrain-22188.json")))),
    );
    const freshPoller = new EntityPoller({
      cache: new Cache(new MemoryStore()),
      repo,
      onStateChange: async (c) => {
        changes.push(...c.changedWatches.map((w) => `${w.type}:${c.state.kind}`));
      },
    });
    await freshPoller.poll(key);
    expect(changes).toEqual(["cancel:train"]);

    const rows = await pool.query("SELECT expires_at FROM watch");
    const expiry = new Date(rows.rows[0].expires_at).getTime();
    expect(expiry).toBeLessThan(Date.now() + 25 * 3600_000);

    // No active watches (delete them) → the chain reports "stop".
    await pool.query("TRUNCATE watch");
    expect(await freshPoller.poll(key)).toBeNull();
  });
});

describe.skipIf(!redisUp)("WatchScheduler (bullmq on redis)", () => {
  it("dedups ensureScheduled and chains polls with adaptive delays", async () => {
    const scheduler = new WatchScheduler(process.env.REDIS_URL ?? "redis://127.0.0.1:6379");
    const key = `test-entity-${Date.now()}`;
    const polled: number[] = [];

    scheduler.start(async () => {
      polled.push(Date.now());
      return polled.length < 3 ? { nextDelayS: 0.05 as number } : null; // stop after 3
    }, 1);

    await scheduler.ensureScheduled(key, 0.05);
    await scheduler.ensureScheduled(key, 0.05); // dedup: same jobId while pending

    await vi.waitFor(
      () => {
        expect(polled.length).toBe(3);
      },
      { timeout: 8000, interval: 100 },
    );

    // Give the final (null) outcome a beat, then confirm the chain ended.
    await new Promise((r) => setTimeout(r, 300));
    expect(polled.length).toBe(3);
    await scheduler.close();
  }, 15_000);
});

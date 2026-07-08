// Redis TTL cache + single-flight + stale-while-revalidate (backlog 1.2).
// Every upstream call in screens/watcher goes through getOrFetch — no direct
// railkit calls elsewhere (packages/server/CLAUDE.md).
import { RailkitError } from "../railkit/errors.js";
import type { CacheStore } from "./store.js";

export interface CachedResult<T> {
  value: T;
  fetchedAt: string; // ISO — feeds meta.fetchedAt
  stale: boolean; // feeds meta.stale
  ttlSeconds: number; // feeds meta.ttlSeconds
}

interface Entry<T> {
  v: T;
  at: number; // epoch ms when fetched upstream
}

// How long a stale value stays servable after its TTL lapses (upstream-down insurance).
const STALE_RETENTION_MS = 24 * 3600 * 1000;
const LOCK_TTL_MS = 15_000;
const WAITER_TIMEOUT_MS = 12_000;
const WAITER_POLL_MS = 150;

/** Thrown internally when another process holds the fetch lock and we have a stale value to serve. */
class OthersRefreshing extends Error {}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export class Cache {
  private readonly pending = new Map<string, Promise<Entry<unknown>>>();

  constructor(private readonly store: CacheStore) {}

  async getOrFetch<T>(
    key: string,
    ttlSeconds: number,
    fetcher: () => Promise<T>,
  ): Promise<CachedResult<T>> {
    const ttlMs = ttlSeconds * 1000;
    const existing = await this.read<T>(key);
    if (existing && Date.now() - existing.at < ttlMs) {
      return this.toResult(existing, false, ttlSeconds);
    }

    // Single-flight within this process: one refresh per key, waiters share it.
    const inflight = this.pending.get(key) as Promise<Entry<T>> | undefined;
    if (inflight) {
      if (existing) return this.toResult(existing, true, ttlSeconds); // SWR: don't block on the refresh
      const entry = await inflight;
      return this.toResult(entry, false, ttlSeconds);
    }

    const refresh = this.refresh<T>(key, ttlMs, fetcher, existing !== null);
    this.pending.set(key, refresh as Promise<Entry<unknown>>);
    try {
      const entry = await refresh;
      return this.toResult(entry, false, ttlSeconds);
    } catch (err) {
      // Serve stale on upstream failure or a cross-process refresh in flight;
      // UPSTREAM_DOWN is only for "nothing to serve" (server CLAUDE.md).
      if (existing) return this.toResult(existing, true, ttlSeconds);
      throw err;
    } finally {
      this.pending.delete(key);
    }
  }

  private async refresh<T>(
    key: string,
    ttlMs: number,
    fetcher: () => Promise<T>,
    hasStale: boolean,
  ): Promise<Entry<T>> {
    const lockKey = `${key}:lock`;
    const gotLock = await this.store.setNx(lockKey, "1", LOCK_TTL_MS);

    if (!gotLock) {
      if (hasStale) throw new OthersRefreshing();
      // Nothing to serve — wait for the lock holder's write.
      const deadline = Date.now() + WAITER_TIMEOUT_MS;
      while (Date.now() < deadline) {
        await sleep(WAITER_POLL_MS);
        const entry = await this.read<T>(key);
        if (entry && Date.now() - entry.at < ttlMs) return entry;
      }
      throw new RailkitError("UPSTREAM_DOWN", `timed out waiting for refresh of ${key}`, true);
    }

    try {
      const value = await fetcher();
      const entry: Entry<T> = { v: value, at: Date.now() };
      await this.store.set(key, JSON.stringify(entry), ttlMs + STALE_RETENTION_MS);
      return entry;
    } finally {
      await this.store.del(lockKey);
    }
  }

  private async read<T>(key: string): Promise<Entry<T> | null> {
    const raw = await this.store.get(key);
    if (raw === null) return null;
    try {
      return JSON.parse(raw) as Entry<T>;
    } catch {
      return null; // corrupt entry — treat as miss
    }
  }

  private toResult<T>(entry: Entry<T>, stale: boolean, ttlSeconds: number): CachedResult<T> {
    return {
      value: entry.v,
      fetchedAt: new Date(entry.at).toISOString(),
      stale,
      ttlSeconds,
    };
  }
}

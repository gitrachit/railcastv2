// Fixed-window rate limiting (backlog 1.3). Counters are transient — Redis in
// production, in-memory for tests — keyed per device (or per IP for the
// unauthenticated /auth/device route).
import type { Redis } from "ioredis";

export interface RateStore {
  /** Increments the window counter, creating it with the given TTL. Returns the new count. */
  incr(key: string, windowMs: number): Promise<number>;
}

export class RedisRateStore implements RateStore {
  constructor(private readonly redis: Redis) {}

  async incr(key: string, windowMs: number): Promise<number> {
    const count = await this.redis.incr(key);
    if (count === 1) await this.redis.pexpire(key, windowMs);
    return count;
  }
}

export class MemoryRateStore implements RateStore {
  private readonly windows = new Map<string, { count: number; expiresAt: number }>();

  async incr(key: string, windowMs: number): Promise<number> {
    const now = Date.now();
    const window = this.windows.get(key);
    if (!window || now >= window.expiresAt) {
      this.windows.set(key, { count: 1, expiresAt: now + windowMs });
      return 1;
    }
    window.count += 1;
    return window.count;
  }
}

const WINDOW_MS = 60_000;

export class RateLimiter {
  constructor(
    private readonly store: RateStore,
    private readonly limitPerMinute: number,
  ) {}

  async allow(bucket: string): Promise<boolean> {
    const minute = Math.floor(Date.now() / WINDOW_MS);
    const count = await this.store.incr(`rl:${bucket}:${minute}`, WINDOW_MS);
    return count <= this.limitPerMinute;
  }
}

import type { Redis } from "ioredis";

// Minimal KV surface the cache needs. RedisStore is production; MemoryStore
// backs unit tests and keeps the cache logic store-agnostic.
export interface CacheStore {
  get(key: string): Promise<string | null>;
  set(key: string, value: string, pxMs: number): Promise<void>;
  /** SET NX PX — returns true when the key was set (lock acquired). */
  setNx(key: string, value: string, pxMs: number): Promise<boolean>;
  del(key: string): Promise<void>;
}

export class RedisStore implements CacheStore {
  constructor(private readonly redis: Redis) {}

  get(key: string): Promise<string | null> {
    return this.redis.get(key);
  }

  async set(key: string, value: string, pxMs: number): Promise<void> {
    await this.redis.set(key, value, "PX", pxMs);
  }

  async setNx(key: string, value: string, pxMs: number): Promise<boolean> {
    return (await this.redis.set(key, value, "PX", pxMs, "NX")) === "OK";
  }

  async del(key: string): Promise<void> {
    await this.redis.del(key);
  }
}

export class MemoryStore implements CacheStore {
  private readonly entries = new Map<string, { value: string; expiresAt: number }>();

  async get(key: string): Promise<string | null> {
    const entry = this.entries.get(key);
    if (!entry) return null;
    if (Date.now() >= entry.expiresAt) {
      this.entries.delete(key);
      return null;
    }
    return entry.value;
  }

  async set(key: string, value: string, pxMs: number): Promise<void> {
    this.entries.set(key, { value, expiresAt: Date.now() + pxMs });
  }

  async setNx(key: string, value: string, pxMs: number): Promise<boolean> {
    if ((await this.get(key)) !== null) return false;
    this.entries.set(key, { value, expiresAt: Date.now() + pxMs });
    return true;
  }

  async del(key: string): Promise<void> {
    this.entries.delete(key);
  }
}

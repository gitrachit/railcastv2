// Watch persistence (backlog 2.1). Raw PNRs live only inside entity_encrypted
// AES blobs; entity_key is an HMAC digest usable for dedup and scheduling.
import type { CreateWatchRequest, WatchEntity, WatchSummary } from "@railcast/shared";
import type pg from "pg";
import { decryptPnrBlob, encryptPnrBlob, pnrEntityKey } from "../privacy/crypto.js";
import { maskPnr } from "../privacy/mask.js";

export interface WatchRow {
  id: string;
  deviceId: string;
  type: WatchSummary["type"];
  entityKey: string;
  entity: WatchEntity;
  params: WatchSummary["params"];
  stateHash: string | null;
  expiresAt: Date;
}

export function entityKeyFor(entity: WatchEntity): string {
  return entity.kind === "pnr"
    ? pnrEntityKey(entity.pnr)
    : `train:${entity.trainNo}:${entity.runDate}`;
}

/** Default watch lifetimes; the worker tightens PNR expiry once it learns the journey date. */
export function defaultExpiry(entity: WatchEntity, now: Date): Date {
  if (entity.kind === "train") {
    // run date + 3 days covers the longest multi-day journeys
    return new Date(Date.parse(`${entity.runDate}T00:00:00+05:30`) + 3 * 86_400_000);
  }
  return new Date(now.getTime() + 35 * 86_400_000);
}

export class WatchRepo {
  constructor(private readonly pool: pg.Pool) {}

  async create(
    deviceId: string,
    req: CreateWatchRequest,
    now = new Date(),
  ): Promise<{ watchId: string; expiresAt: string }> {
    const entityKey = entityKeyFor(req.entity);
    const expiresAt = defaultExpiry(req.entity, now);
    const encrypted = encryptPnrBlob(JSON.stringify(req.entity));

    const res = await this.pool.query<{ id: string; expires_at: Date }>(
      `INSERT INTO watch (device_id, type, entity_key, entity_encrypted, params, expires_at)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (device_id, type, entity_key)
       DO UPDATE SET params = EXCLUDED.params, expires_at = EXCLUDED.expires_at
       RETURNING id, expires_at`,
      [deviceId, req.type, entityKey, encrypted, JSON.stringify(req.params ?? {}), expiresAt],
    );
    return { watchId: res.rows[0]!.id, expiresAt: res.rows[0]!.expires_at.toISOString() };
  }

  async listForDevice(deviceId: string): Promise<WatchSummary[]> {
    const res = await this.pool.query(
      "SELECT id, type, entity_encrypted, params, expires_at FROM watch WHERE device_id = $1 AND expires_at > now() ORDER BY created_at",
      [deviceId],
    );
    return res.rows.map((r) => {
      const entity = JSON.parse(decryptPnrBlob(r.entity_encrypted)) as WatchEntity;
      return {
        watchId: r.id,
        type: r.type,
        // FR-4.3: the summary never carries the raw PNR
        entity:
          entity.kind === "pnr"
            ? { kind: "pnr", pnrMasked: maskPnr(entity.pnr) }
            : { kind: "train", trainNo: entity.trainNo, runDate: entity.runDate },
        params: r.params,
        expiresAt: r.expires_at.toISOString(),
      };
    });
  }

  async delete(deviceId: string, watchId: string): Promise<boolean> {
    const res = await this.pool.query("DELETE FROM watch WHERE id = $1 AND device_id = $2", [
      watchId,
      deviceId,
    ]);
    return (res.rowCount ?? 0) > 0;
  }

  /** Active (unexpired) watches for one entity — the fan-out set for a poll. */
  async activeForEntity(entityKey: string): Promise<WatchRow[]> {
    const res = await this.pool.query(
      "SELECT * FROM watch WHERE entity_key = $1 AND expires_at > now()",
      [entityKey],
    );
    return res.rows.map(rowToWatch);
  }

  /** Distinct entities that still have active watches — the scheduler's world. */
  async activeEntityKeys(): Promise<string[]> {
    const res = await this.pool.query<{ entity_key: string }>(
      "SELECT DISTINCT entity_key FROM watch WHERE expires_at > now()",
    );
    return res.rows.map((r) => r.entity_key);
  }

  async updateStateHash(watchIds: string[], stateHash: string): Promise<void> {
    if (watchIds.length === 0) return;
    await this.pool.query("UPDATE watch SET state_hash = $1 WHERE id = ANY($2::uuid[])", [
      stateHash,
      watchIds,
    ]);
  }

  /** Tighten expiry (e.g. once the journey's arrival is known). Purge job support. */
  async setEntityExpiry(entityKey: string, expiresAt: Date): Promise<void> {
    await this.pool.query(
      "UPDATE watch SET expires_at = $1 WHERE entity_key = $2 AND expires_at > $1",
      [expiresAt, entityKey],
    );
  }

  /** FR-4.3 purge: hard-delete expired watches (their encrypted PNR blobs go with them). */
  async purgeExpired(graceDays = 2): Promise<number> {
    const res = await this.pool.query(
      `DELETE FROM watch WHERE expires_at < now() - make_interval(days => $1)`,
      [graceDays],
    );
    return res.rowCount ?? 0;
  }

  async setPushToken(deviceId: string, fcmToken: string): Promise<void> {
    await this.pool.query(
      `INSERT INTO device_push_token (device_id, fcm_token, updated_at)
       VALUES ($1, $2, now())
       ON CONFLICT (device_id) DO UPDATE SET fcm_token = EXCLUDED.fcm_token, updated_at = now()`,
      [deviceId, fcmToken],
    );
  }

  async pushTokensFor(deviceIds: string[]): Promise<Map<string, string>> {
    if (deviceIds.length === 0) return new Map();
    const res = await this.pool.query<{ device_id: string; fcm_token: string }>(
      "SELECT device_id, fcm_token FROM device_push_token WHERE device_id = ANY($1)",
      [deviceIds],
    );
    return new Map(res.rows.map((r) => [r.device_id, r.fcm_token]));
  }
}

function rowToWatch(r: {
  id: string;
  device_id: string;
  type: WatchRow["type"];
  entity_key: string;
  entity_encrypted: string;
  params: WatchRow["params"];
  state_hash: string | null;
  expires_at: Date;
}): WatchRow {
  return {
    id: r.id,
    deviceId: r.device_id,
    type: r.type,
    entityKey: r.entity_key,
    entity: JSON.parse(decryptPnrBlob(r.entity_encrypted)) as WatchEntity,
    params: r.params,
    stateHash: r.state_hash,
    expiresAt: r.expires_at,
  };
}

// Watch API (2.4) end-to-end through buildApp against real Postgres.
import pg from "pg";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { buildApp } from "../app.js";
import { migrate } from "../db/migrate.js";
import { WatchRepo } from "./repo.js";

process.env.PNR_ENCRYPTION_KEY = "e".repeat(64);
process.env.AUTH_TOKEN_SECRET = "test-secret";

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

afterAll(() => pool.end());

function makeApp(armed: string[]) {
  return buildApp({
    watch: { repo: new WatchRepo(pool), armEntity: async (k) => void armed.push(k) },
  });
}

async function token(app: ReturnType<typeof buildApp>): Promise<string> {
  const res = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  return res.json().data.deviceToken as string;
}

describe.skipIf(!pgUp)("watch API (contracts §5)", () => {
  beforeEach(() => pool.query("TRUNCATE watch, device_push_token, watch_entity_state"));
  afterEach(() => vi.restoreAllMocks());

  it("requires auth", async () => {
    const res = await makeApp([]).inject({ method: "GET", url: "/watch" });
    expect(res.statusCode).toBe(401);
  });

  it("creates a watch, arms the chain, lists it masked, deletes it", async () => {
    const armed: string[] = [];
    const app = makeApp(armed);
    const auth = { authorization: `Bearer ${await token(app)}` };

    const created = await app.inject({
      method: "POST",
      url: "/watch",
      headers: auth,
      payload: { type: "chart", entity: { kind: "pnr", pnr: "8524132882" } },
    });
    expect(created.statusCode).toBe(200);
    const { watchId, expiresAt } = created.json().data;
    expect(watchId).toBeTruthy();
    expect(Date.parse(expiresAt)).not.toBeNaN();
    expect(armed).toHaveLength(1); // scheduler armed (FR-7.1)

    const listed = await app.inject({ method: "GET", url: "/watch", headers: auth });
    const watches = listed.json().data.watches;
    expect(watches).toHaveLength(1);
    expect(watches[0].entity).toEqual({ kind: "pnr", pnrMasked: "••••2882" });
    expect(listed.body).not.toContain("8524132882"); // FR-4.3

    const del = await app.inject({ method: "DELETE", url: `/watch/${watchId}`, headers: auth });
    expect(del.statusCode).toBe(200);
    const after = await app.inject({ method: "GET", url: "/watch", headers: auth });
    expect(after.json().data.watches).toHaveLength(0);
  });

  it("scopes watches to the calling device", async () => {
    const app = makeApp([]);
    const a = { authorization: `Bearer ${await token(app)}` };
    const b = { authorization: `Bearer ${await token(app)}` };

    const created = await app.inject({
      method: "POST",
      url: "/watch",
      headers: a,
      payload: { type: "cancel", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-09" } },
    });
    const { watchId } = created.json().data;

    expect((await app.inject({ method: "GET", url: "/watch", headers: b })).json().data.watches).toHaveLength(0);
    const delOther = await app.inject({ method: "DELETE", url: `/watch/${watchId}`, headers: b });
    expect(delOther.statusCode).toBe(404); // not yours
  });

  it("validates type/entity coherence and required params", async () => {
    const app = makeApp([]);
    const auth = { authorization: `Bearer ${await token(app)}` };
    const bad = (payload: Record<string, unknown>) =>
      app.inject({ method: "POST", url: "/watch", headers: auth, payload });

    expect((await bad({ type: "nope", entity: { kind: "pnr", pnr: "8524132882" } })).statusCode).toBe(400);
    expect((await bad({ type: "chart", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-09" } })).statusCode).toBe(400);
    expect((await bad({ type: "delay", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-09" } })).statusCode).toBe(400); // missing threshold
    expect((await bad({ type: "arrival", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-09" }, params: { leadMin: 10 } })).statusCode).toBe(400); // missing stationCode
    expect((await bad({ type: "chart", entity: { kind: "pnr", pnr: "123" } })).statusCode).toBe(400);
    // tatkal (FR-6.4): train entity + a valid band are required; a good one is accepted.
    expect((await bad({ type: "tatkal", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-12" } })).statusCode).toBe(400); // missing band
    expect((await bad({ type: "tatkal", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-12" }, params: { tatkalBand: "sleeper" } })).statusCode).toBe(400);
    const okTatkal = await bad({ type: "tatkal", entity: { kind: "train", trainNo: "22188", runDate: "2026-07-12" }, params: { tatkalBand: "nonac" } });
    expect(okTatkal.statusCode).toBe(200);
    expect(okTatkal.json().data.watchId).toBeTruthy();
  });

  it("registers a push token", async () => {
    const app = makeApp([]);
    const deviceToken = await token(app);
    const res = await app.inject({
      method: "POST",
      url: "/device/push-token",
      headers: { authorization: `Bearer ${deviceToken}` },
      payload: { fcmToken: "fcm-abc" },
    });
    expect(res.statusCode).toBe(200);

    const rows = await pool.query("SELECT fcm_token FROM device_push_token");
    expect(rows.rows.some((r) => r.fcm_token === "fcm-abc")).toBe(true);

    const bad = await app.inject({
      method: "POST",
      url: "/device/push-token",
      headers: { authorization: `Bearer ${deviceToken}` },
      payload: {},
    });
    expect(bad.statusCode).toBe(400);
  });
});

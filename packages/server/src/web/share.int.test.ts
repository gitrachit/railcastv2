// Share scaffold (2.5) end-to-end: create token → public page → revoke → 410.
import { readFileSync } from "node:fs";
import pg from "pg";
import { afterAll, afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { buildApp } from "../app.js";
import { migrate } from "../db/migrate.js";
import { ShareRepo } from "./share-repo.js";

process.env.PNR_ENCRYPTION_KEY = "f".repeat(64);
process.env.AUTH_TOKEN_SECRET = "test-secret";
process.env.RAILKIT_API_KEY = "test-api-key";

const NOW = new Date("2026-07-08T16:21:00+05:30");

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

function fixture(name: string): string {
  return readFileSync(new URL(`../railkit/__fixtures__/${name}`, import.meta.url), "utf8");
}

let fetchMock: Mock;

beforeEach(async () => {
  if (pgUp) await pool.query("TRUNCATE share_token");
  fetchMock = vi.fn((url: string) => {
    const body = url.includes("/api/getTrainInfo/22188")
      ? fixture("getTrainInfo-22188.json")
      : url.includes("/api/trackTrain/22188/08-07-2026") // today's run (share pins 2026-07-08)
        ? fixture("trackTrain-22188-running.json")
        : null;
    return Promise.resolve(
      body
        ? new Response(body, { status: 200 })
        : new Response('{"success":false,"error":"Route not found"}', { status: 404 }),
    );
  });
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => vi.unstubAllGlobals());

function makeApp() {
  return buildApp({ now: () => NOW, share: { repo: new ShareRepo(pool) } });
}

async function auth(app: ReturnType<typeof buildApp>): Promise<Record<string, string>> {
  const res = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  return { authorization: `Bearer ${res.json().data.deviceToken}` };
}

describe.skipIf(!pgUp)("shared journey (FR-8)", () => {
  it("creates a token, renders the public page, then revokes → 410", async () => {
    const app = makeApp();
    const headers = await auth(app);

    const created = await app.inject({
      method: "POST",
      url: "/share/journey",
      headers,
      payload: { trainNo: "22188", runDate: "2026-07-08" },
    });
    expect(created.statusCode).toBe(200);
    const { token, url } = created.json().data;
    expect(url).toContain(`/t/${token}`);

    // Public page — no auth header.
    const page = await app.inject({ method: "GET", url: `/t/${token}` });
    expect(page.statusCode).toBe(200);
    expect(page.headers["content-type"]).toContain("text/html");
    expect(page.body).toContain("INTERCITY EXP");
    expect(page.body).toContain("Running");
    expect(page.body.toLowerCase()).toContain("estimated position"); // FR-2.2 honesty
    expect(page.body).toContain("Not affiliated with Indian Railways");

    // Revoke (owner) → the link 410s.
    const del = await app.inject({ method: "DELETE", url: `/share/${token}`, headers });
    expect(del.statusCode).toBe(200);
    const gone = await app.inject({ method: "GET", url: `/t/${token}` });
    expect(gone.statusCode).toBe(410);
    expect(gone.body).toContain("expired");
  });

  it("only the sharer can revoke", async () => {
    const app = makeApp();
    const owner = await auth(app);
    const other = await auth(app);
    const created = await app.inject({
      method: "POST",
      url: "/share/journey",
      headers: owner,
      payload: { trainNo: "22188", runDate: "2026-07-08" },
    });
    const { token } = created.json().data;
    expect((await app.inject({ method: "DELETE", url: `/share/${token}`, headers: other })).statusCode).toBe(404);
  });

  it("unknown token → 410", async () => {
    const gone = await makeApp().inject({ method: "GET", url: "/t/does-not-exist" });
    expect(gone.statusCode).toBe(410);
  });

  it("rejects a malformed share request", async () => {
    const app = makeApp();
    const headers = await auth(app);
    const res = await app.inject({
      method: "POST",
      url: "/share/journey",
      headers,
      payload: { trainNo: "123", runDate: "08-07-2026" },
    });
    expect(res.statusCode).toBe(400);
  });
});

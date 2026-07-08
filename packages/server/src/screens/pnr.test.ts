import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { buildApp, redactPnrInUrl } from "../app.js";
import { MemoryStore, type CacheStore } from "../cache/index.js";

// The sanitized fixture: GOA EXPRESS 12780, journey Jul 4 2026, chart prepared,
// 4 CNF passengers. Tests pin "now" to 2026-07-08 (journey in the past → no live join).
const PNR = "8524132882";
const NOW = new Date("2026-07-08T16:21:00+05:30");

function fixture(name: string): string {
  return readFileSync(new URL(`../railkit/__fixtures__/${name}`, import.meta.url), "utf8");
}

/** CacheStore wrapper recording everything written, to prove no raw PNR at rest. */
class RecordingStore implements CacheStore {
  readonly writes: Array<{ key: string; value: string }> = [];
  private readonly inner = new MemoryStore();

  get(key: string) {
    return this.inner.get(key);
  }
  async set(key: string, value: string, pxMs: number) {
    this.writes.push({ key, value });
    await this.inner.set(key, value, pxMs);
  }
  async setNx(key: string, value: string, pxMs: number) {
    this.writes.push({ key, value });
    return this.inner.setNx(key, value, pxMs);
  }
  del(key: string) {
    return this.inner.del(key);
  }
}

let fetchMock: Mock;

beforeEach(() => {
  process.env.RAILKIT_API_KEY = "test-api-key";
  process.env.AUTH_TOKEN_SECRET = "test-secret";
  process.env.PNR_ENCRYPTION_KEY = "b".repeat(64);
  fetchMock = vi.fn((url: string) => {
    const body = url.includes(`/api/checkPNRStatus/${PNR}`)
      ? fixture("checkPNRStatus-sample.json")
      : url.includes("/api/getTrainInfo/22188")
        ? fixture("getTrainInfo-22188.json")
        : url.includes("/api/trackTrain/22188/08-07-2026")
          ? fixture("trackTrain-22188-running.json")
          : null;
    return Promise.resolve(
      body
        ? new Response(body, { status: 200, headers: { "content-type": "application/json" } })
        : new Response('{"success":false,"error":"No PNR data found"}', { status: 400 }),
    );
  });
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.RAILKIT_API_KEY;
  delete process.env.AUTH_TOKEN_SECRET;
  delete process.env.PNR_ENCRYPTION_KEY;
});

async function getPnr(pnr: string, options: Parameters<typeof buildApp>[0] = {}) {
  const app = buildApp({ now: () => NOW, ...options });
  const mint = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  return app.inject({
    method: "GET",
    url: `/screen/pnr/${pnr}`,
    headers: { authorization: `Bearer ${mint.json().data.deviceToken}` },
  });
}

describe("GET /screen/pnr/:pnr (FR-4.1, FR-4.3)", () => {
  it("composes the PNR screen with the PNR masked", async () => {
    const res = await getPnr(PNR);
    expect(res.statusCode).toBe(200);
    const { data, meta } = res.json();

    expect(data.pnrMasked).toBe("••••2882");
    expect(data.train).toEqual({ no: "12780", name: "GOA EXPRESS" });
    expect(data.journey).toMatchObject({
      date: "2026-07-04",
      from: { code: "PUNE", name: "PUNE JN" },
      to: { code: "VSG", name: "VASCO DA GAMA" },
      cls: "2A",
      quota: "TQ",
      arrivalEta: "2026-07-05T07:00:00+05:30",
    });
    expect(data.chart.prepared).toBe(true);
    expect(data.passengers).toHaveLength(4);
    expect(data.passengers[0]).toEqual({
      idx: 1,
      bookingStatus: "CNF",
      currentStatus: "CNF",
      coach: "A1",
      berth: 14,
      berthType: "UB",
    });
    expect(data.fare).toEqual({ total: 7380 });
    expect(data.live).toBeNull(); // journey was days ago
    expect(meta.ttlSeconds).toBe(180);
  });

  it("never returns the raw PNR anywhere in the response", async () => {
    const res = await getPnr(PNR);
    expect(res.body).not.toContain(PNR);
    expect(res.body).not.toContain("2458692882");
  });

  it("stores only encrypted blobs under HMAC keys — no raw PNR at rest", async () => {
    const store = new RecordingStore();
    const res = await getPnr(PNR, { cacheStore: store });
    expect(res.statusCode).toBe(200);
    expect(store.writes.length).toBeGreaterThan(0);
    for (const w of store.writes) {
      expect(w.key).not.toContain(PNR);
      expect(w.value).not.toContain(PNR);
      expect(w.value).not.toContain("GOA EXPRESS"); // payload unreadable, not just the PNR
    }
  });

  it("joins live status when the journey date is the active run", async () => {
    const doctored = JSON.parse(fixture("checkPNRStatus-sample.json"));
    doctored.data.train.number = "22188";
    doctored.data.journey.dateOfJourney = "Jul 8, 2026 4:15:00 PM";
    fetchMock.mockImplementation((url: string) => {
      const body = url.includes("/api/checkPNRStatus/")
        ? JSON.stringify(doctored)
        : url.includes("/api/getTrainInfo/22188")
          ? fixture("getTrainInfo-22188.json")
          : url.includes("/api/trackTrain/22188/08-07-2026")
            ? fixture("trackTrain-22188-running.json")
            : null;
      return Promise.resolve(
        body
          ? new Response(body, { status: 200 })
          : new Response('{"success":false}', { status: 404 }),
      );
    });

    const { data } = (await getPnr(PNR)).json();
    expect(data.journey.date).toBe("2026-07-08");
    expect(data.live).toMatchObject({ state: "running", delayMin: 0 });
  });

  it("tightens the TTL inside the chart window (contracts §10)", async () => {
    const doctored = JSON.parse(fixture("checkPNRStatus-sample.json"));
    doctored.data.chart.status = "Chart Not Prepared";
    doctored.data.journey.dateOfJourney = "Jul 8, 2026 8:00:00 PM"; // departs in ~3.5h
    doctored.data.train.number = "99999"; // avoid live-join fixtures
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.includes("/api/checkPNRStatus/")
          ? new Response(JSON.stringify(doctored), { status: 200 })
          : new Response('{"success":false}', { status: 404 }),
      ),
    );

    const { meta, data } = (await getPnr(PNR)).json();
    expect(data.chart.prepared).toBe(false);
    expect(meta.ttlSeconds).toBe(60);
  });

  it("rejects a malformed PNR without touching upstream or echoing it", async () => {
    const res = await getPnr("12345");
    expect(res.statusCode).toBe(400);
    expect(res.json().error.code).toBe("INVALID_INPUT");
    expect(res.body).not.toContain("12345");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("maps an unknown PNR to NOT_FOUND with a masked message", async () => {
    const res = await getPnr("9999999999");
    expect(res.statusCode).toBe(404);
    const body = res.json();
    expect(body.error.code).toBe("NOT_FOUND");
    expect(JSON.stringify(body)).not.toContain("9999999999");
  });
});

describe("redactPnrInUrl (log hygiene)", () => {
  it("masks the PNR in request-log URLs", () => {
    expect(redactPnrInUrl("/screen/pnr/8524132882")).toBe("/screen/pnr/••••2882");
    expect(redactPnrInUrl("/screen/train/22188")).toBe("/screen/train/22188");
  });
});

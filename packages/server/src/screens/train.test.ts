import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { buildApp } from "../app.js";

// Fixture reality: run date 2026-07-08 (running, departed ADTL 16:15 IST),
// 2026-07-07 (arrived RKMP 22:18 IST). Tests pin "now" inside that evening.
const NOW = new Date("2026-07-08T16:21:00+05:30");

function fixture(name: string): string {
  return readFileSync(new URL(`../railkit/__fixtures__/${name}`, import.meta.url), "utf8");
}

function fixtureRouter(url: string): Response {
  const body = url.includes("/api/getTrainInfo/22188")
    ? fixture("getTrainInfo-22188.json")
    : url.includes("/api/trackTrain/22188/08-07-2026")
      ? fixture("trackTrain-22188-running.json")
      : url.includes("/api/trackTrain/22188/07-07-2026")
        ? fixture("trackTrain-22188.json")
        : null;
  if (!body) return new Response('{"success":false,"error":"Route not found"}', { status: 404 });
  return new Response(body, { status: 200, headers: { "content-type": "application/json" } });
}

let fetchMock: Mock;

beforeEach(() => {
  process.env.RAILKIT_API_KEY = "test-api-key";
  process.env.AUTH_TOKEN_SECRET = "test-secret";
  fetchMock = vi.fn((url: string) => Promise.resolve(fixtureRouter(url)));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.RAILKIT_API_KEY;
  delete process.env.AUTH_TOKEN_SECRET;
});

async function getScreen(query = "", options: Parameters<typeof buildApp>[0] = {}) {
  const app = buildApp({ now: () => NOW, ...options });
  const mint = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  const token = mint.json().data.deviceToken as string;
  return app.inject({
    method: "GET",
    url: `/screen/train/22188${query}`,
    headers: { authorization: `Bearer ${token}` },
  });
}

describe("GET /screen/train/:trainNo (FR-2.x, FR-3.x)", () => {
  it("requires auth", async () => {
    const app = buildApp({ now: () => NOW });
    const res = await app.inject({ method: "GET", url: "/screen/train/22188" });
    expect(res.statusCode).toBe(401);
  });

  it("run=auto probes both runs and picks the active one (FR-2.3)", async () => {
    const res = await getScreen();
    expect(res.statusCode).toBe(200);
    const { data, meta } = res.json();

    expect(data.runDateResolved).toBe("2026-07-08"); // running now
    expect(data.runDateChoices).toEqual([
      { runDate: "2026-07-08", label: "today", active: true },
      { runDate: "2026-07-07", label: "yesterday", active: false }, // arrived
    ]);
    expect(meta.stale).toBe(false);
    expect(meta.ttlSeconds).toBe(45);
  });

  it("composes the running screen: status, route states, day labels", async () => {
    const { data } = (await getScreen()).json();

    expect(data.name).toBe("INTERCITY EXP");
    expect(data.status.state).toBe("running");
    expect(data.status.summary).toBe("Running · on time");
    expect(data.status.delayMin).toBe(0);
    expect(data.status.lastStation).toEqual({ code: "ADTL", name: "Adhartal" });
    expect(data.status.nextStation.code).toBe("JBP");
    expect(data.status.nextStation.etaScheduled).toBe("2026-07-08T16:27:00+05:30");
    expect(data.status.nextStation.etaActual).toBe("2026-07-08T16:27:00+05:30"); // on time
    expect(data.status.lastUpdate).toBe("2026-07-08T16:17:00+05:30");

    expect(data.route).toHaveLength(12);
    expect(data.route[0]).toMatchObject({
      code: "ADTL",
      state: "departed",
      day: 1,
      km: 0,
      actual: { dep: "2026-07-08T16:15:00+05:30" },
      delayMin: 0,
    });
    expect(data.route[1].state).toBe("next");
    expect(data.route[2].state).toBe("upcoming");
    expect(data.route[11].state).toBe("destination");
    // honesty: upcoming stops carry no "actual" times even though upstream fakes them
    expect(data.route[2].actual).toEqual({ arr: null, dep: null });
    expect(data.route[1].lat).toBeCloseTo(23.1649, 3);
  });

  it("interpolates the position between last crossed and next stop (FR-2.2)", async () => {
    const { data } = (await getScreen()).json();
    const pos = data.position;

    expect(pos.kind).toBe("interpolated");
    expect(pos.betweenCodes).toEqual(["ADTL", "JBP"]);
    // departed 16:15, next arr 16:27, now 16:21 → halfway
    expect(pos.progress).toBeCloseTo(0.5, 2);
    expect(pos.lat).toBeCloseTo((23.2277805299293 + 23.1649725156114) / 2, 4);
  });

  it("builds the coach guide with the Itarsi reversal (FR-3.1/3.2)", async () => {
    const { data } = (await getScreen()).json();

    expect(data.coach.referenceStation).toBe("ADTL");
    expect(data.coach.order).toHaveLength(22);
    expect(data.coach.order[0]).toEqual({ type: "ENG", number: "ENG", position: 0 });
    expect(data.coach.reversals).toEqual([
      { atStationCode: "ET", atStationName: "Itarsi Jn (Rev)" },
    ]);
  });

  it("run=yesterday returns the completed run as arrived", async () => {
    const { data } = (await getScreen("?run=yesterday")).json();

    expect(data.runDateResolved).toBe("2026-07-07");
    expect(data.status.state).toBe("arrived");
    expect(data.status.summary).toMatch(/^Arrived at Rani Kamlapati/);
    expect(data.position).toBeNull(); // not running — no estimated marker
    const rkmp = data.route[11];
    expect(rkmp.state).toBe("destination");
    expect(rkmp.actual.arr).toBe("2026-07-07T22:18:00+05:30");
  });

  it("rejects bad inputs before touching upstream", async () => {
    const app = buildApp({ now: () => NOW });
    const mint = await app.inject({
      method: "POST",
      url: "/auth/device",
      payload: { platform: "android", appVersion: "0.1.0" },
    });
    const headers = { authorization: `Bearer ${mint.json().data.deviceToken}` };

    const badTrain = await app.inject({ method: "GET", url: "/screen/train/123", headers });
    expect(badTrain.statusCode).toBe(400);
    expect(badTrain.json().error.code).toBe("INVALID_INPUT");

    const badRun = await app.inject({
      method: "GET",
      url: "/screen/train/22188?run=2026-07-01",
      headers,
    });
    expect(badRun.statusCode).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("survives a flaky probe for the run it is not showing (live-seen upstream 400)", async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes("/api/trackTrain/22188/07-07-2026")) {
        return Promise.resolve(
          new Response('{"success":false,"error":"transient"}', { status: 400 }),
        );
      }
      return Promise.resolve(fixtureRouter(url));
    });

    const res = await getScreen();
    expect(res.statusCode).toBe(200);
    const { data } = res.json();
    expect(data.runDateResolved).toBe("2026-07-08");
    expect(data.runDateChoices[1]).toEqual({
      runDate: "2026-07-07",
      label: "yesterday",
      active: false,
    });
  });

  it("maps an unknown train to NOT_FOUND", async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(
        new Response('{"success":false,"error":"Train not found"}', { status: 404 }),
      ),
    );
    const res = await getScreen();
    expect(res.statusCode).toBe(404);
    expect(res.json().error.code).toBe("NOT_FOUND");
  });
});

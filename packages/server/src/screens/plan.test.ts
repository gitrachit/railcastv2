import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { buildApp } from "../app.js";

let fetchMock: Mock;

function fixture(name: string): string {
  return readFileSync(new URL(`../railkit/__fixtures__/${name}`, import.meta.url), "utf8");
}

beforeEach(() => {
  process.env.RAILKIT_API_KEY = "test-api-key";
  process.env.AUTH_TOKEN_SECRET = "test-secret";
  fetchMock = vi.fn((url: string) => {
    const body = url.includes("/api/searchTrainBetweenStations/JBP/NU")
      ? fixture("searchBetween-JBP.json")
      : url.includes("/api/getAvailability/22188/JBP/RKMP/15-07-2026/CC/GN")
        ? fixture("getAvailability-22188.json")
        : url.includes("/api/fareLookup/22188/15-07-2026/JBP/RKMP/CC/GN")
          ? fixture("fareLookup-22188.json")
          : null;
    return Promise.resolve(
      body
        ? new Response(body, { status: 200 })
        : new Response('{"success":false,"error":"Route not found"}', { status: 404 }),
    );
  });
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.RAILKIT_API_KEY;
  delete process.env.AUTH_TOKEN_SECRET;
});

async function get(url: string) {
  const app = buildApp();
  const mint = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  return app.inject({
    method: "GET",
    url,
    headers: { authorization: `Bearer ${mint.json().data.deviceToken}` },
  });
}

describe("GET /screen/plan (FR-6.1)", () => {
  it("returns all trains fast with pending availability/fare", async () => {
    const res = await get("/screen/plan?from=JBP&to=NU&date=2026-07-13&quota=GN");
    expect(res.statusCode).toBe(200);
    const { data, meta } = res.json();

    expect(data.from).toEqual({ code: "JBP", name: "Jabalpur" });
    expect(data.to).toEqual({ code: "NU", name: "Narsinghpur" });
    expect(data.trains).toHaveLength(47);
    expect(meta.ttlSeconds).toBe(12 * 3600);

    const rewa = data.trains.find((t: { no: string }) => t.no === "22938");
    expect(rewa).toMatchObject({
      name: "REWA RJT SUP EXP",
      dep: "2026-07-13T00:25:00+05:30",
      arr: "2026-07-13T01:28:00+05:30",
      durationMin: 63,
      availability: "pending",
      fare: "pending",
      punctuality: null,
    });
    // "0100000" is Sun-first upstream (verified live: it ran on a Monday)
    expect(rewa.runsOn).toEqual([false, true, false, false, false, false, false]);
  });

  it("validates from/to/date/quota", async () => {
    expect((await get("/screen/plan?from=JBP&to=NU&date=13-07-2026")).statusCode).toBe(400);
    expect((await get("/screen/plan?from=jbp&to=NU&date=2026-07-13")).statusCode).toBe(400);
    expect(
      (await get("/screen/plan?from=JBP&to=NU&date=2026-07-13&quota=XX")).statusCode,
    ).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("maps an unknown route to NOT_FOUND", async () => {
    const res = await get("/screen/plan?from=JBP&to=XX&date=2026-07-13");
    expect(res.statusCode).toBe(404);
    expect(res.json().error.code).toBe("NOT_FOUND");
  });
});

describe("GET /screen/plan/row/:trainNo (FR-6.2, FR-6.3)", () => {
  it("hydrates availability + fare for the requested date", async () => {
    const res = await get(
      "/screen/plan/row/22188?from=JBP&to=RKMP&date=2026-07-15&cls=CC&quota=GN",
    );
    expect(res.statusCode).toBe(200);
    const { data, meta } = res.json();

    expect(data.availability).toEqual({
      status: "available",
      text: "AVL 107",
      predictionPct: 100,
      canBook: true,
    });
    expect(data.fare).toEqual({
      total: 545,
      breakdown: {
        base: 434,
        reservation: 40,
        superfast: 45,
        tatkal: 0,
        gst: 26,
        dynamic: 0,
        other: 0,
      },
    });
    expect(meta.ttlSeconds).toBe(600);
  });

  it("requires a class code", async () => {
    const res = await get("/screen/plan/row/22188?from=JBP&to=RKMP&date=2026-07-15&quota=GN");
    expect(res.statusCode).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

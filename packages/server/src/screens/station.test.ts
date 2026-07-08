import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { buildApp } from "../app.js";

// Board captured 2026-07-08 early afternoon IST (first departure 12:45).
const NOW = new Date("2026-07-08T12:50:00+05:30");

let fetchMock: Mock;

beforeEach(() => {
  process.env.RAILKIT_API_KEY = "test-api-key";
  process.env.AUTH_TOKEN_SECRET = "test-secret";
  fetchMock = vi.fn((url: string) =>
    Promise.resolve(
      url.includes("/api/liveAtStation/JBP")
        ? new Response(
            readFileSync(
              new URL("../railkit/__fixtures__/liveAtStation-JBP.json", import.meta.url),
              "utf8",
            ),
            { status: 200 },
          )
        : new Response('{"success":false,"error":"not found"}', { status: 404 }),
    ),
  );
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.RAILKIT_API_KEY;
  delete process.env.AUTH_TOKEN_SECRET;
});

async function getBoard(pathSuffix: string) {
  const app = buildApp({ now: () => NOW });
  const mint = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  return app.inject({
    method: "GET",
    url: `/screen/station/${pathSuffix}`,
    headers: { authorization: `Bearer ${mint.json().data.deviceToken}` },
  });
}

describe("GET /screen/station/:code (FR-5.1)", () => {
  it("composes the board with the window param", async () => {
    const res = await getBoard("JBP?hrs=4");
    expect(res.statusCode).toBe(200);
    const { data, meta } = res.json();

    expect(data.station).toEqual({ code: "JBP", name: "JABALPUR" });
    expect(data.windowHrs).toBe(4);
    expect(data.trains).toHaveLength(23);
    expect(meta.ttlSeconds).toBe(90);
    expect(fetchMock.mock.calls[0]?.[0]).toContain("/api/liveAtStation/JBP?hrs=4");
  });

  it("marks originating trains with arrival=null and ISO departure times", async () => {
    const { data } = (await getBoard("JBP")).json();
    const originating = data.trains.find((t: { no: string }) => t.no === "11265");

    expect(originating.arrival).toBeNull(); // originates at JBP (contracts §3)
    expect(originating.departure).toEqual({
      scheduled: "2026-07-08T12:45:00+05:30",
      actual: "2026-07-08T12:45:00+05:30",
      delayMin: 0,
    });
    expect(originating.status).toBe("ontime");
    expect(originating.classes).toContain("SL");
  });

  it("parses HH:MM-Hrs delays on late terminating trains", async () => {
    const { data } = (await getBoard("JBP")).json();
    const late = data.trains.find((t: { no: string }) => t.no === "02197");

    expect(late.departure).toBeNull(); // terminates at JBP
    expect(late.arrival.delayMin).toBe(280); // "04:40 Hrs."
    expect(late.status).toBe("late");
    expect(late.classes).toEqual([]);
  });

  it("surfaces cancelled trains as status=cancelled with icon-ready state (FR-2.4)", async () => {
    const { data } = (await getBoard("JBP")).json();
    const cancelled = data.trains.find((t: { no: string }) => t.no === "01144");

    expect(cancelled.status).toBe("cancelled");
    expect(cancelled.arrival.scheduled).toBe("2026-07-08T13:45:00+05:30");
    expect(cancelled.arrival.actual).toBeNull(); // "Cancelled" is not a time
    expect(cancelled.platform).toBeNull();
  });

  it("rejects bad codes and windows before touching upstream", async () => {
    expect((await getBoard("jbp")).statusCode).toBe(400);
    expect((await getBoard("JBP?hrs=3")).statusCode).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

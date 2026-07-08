import { readFileSync } from "node:fs";
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import {
  checkPnrStatus,
  fareLookup,
  getAvailability,
  getTrainHistory,
  getTrainInfo,
  liveAtStation,
  searchTrainsBetween,
  trackTrain,
} from "./endpoints.js";
import { RailkitError } from "./errors.js";

const TEST_KEY = "test-api-key-123";

function fixture(name: string): unknown {
  return JSON.parse(readFileSync(new URL(`./__fixtures__/${name}`, import.meta.url), "utf8"));
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

async function expectRailkitError(p: Promise<unknown>, code: string): Promise<RailkitError> {
  try {
    await p;
  } catch (e) {
    expect(e).toBeInstanceOf(RailkitError);
    expect((e as RailkitError).code).toBe(code);
    return e as RailkitError;
  }
  throw new Error(`expected RailkitError ${code}, but the call succeeded`);
}

let fetchMock: Mock;

beforeEach(() => {
  process.env.RAILKIT_API_KEY = TEST_KEY;
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.RAILKIT_API_KEY;
});

function requestedUrl(): string {
  return fetchMock.mock.calls[0]?.[0] as string;
}

function requestedHeaders(): Record<string, string> {
  return (fetchMock.mock.calls[0]?.[1] as { headers: Record<string, string> }).headers;
}

describe("getTrainInfo", () => {
  it("builds the URL, sends the key header, unwraps data", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("getTrainInfo-22188.json")));
    const info = await getTrainInfo("22188");
    expect(requestedUrl()).toBe("https://railkit-api.rajivdubey.dev/api/getTrainInfo/22188");
    expect(requestedHeaders()["x-api-key"]).toBe(TEST_KEY);
    expect(info.trainInfo.train_name).toBe("INTERCITY EXP");
    expect(info.route.length).toBeGreaterThan(10);
    expect(info.route[0]?.coordinates.latitude).toBeTypeOf("number");
  });

  it("rejects a bad train number without calling upstream", async () => {
    await expectRailkitError(getTrainInfo("2218"), "INVALID_INPUT");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("trackTrain", () => {
  it("converts the run date to DD-MM-YYYY and parses coach timeline", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("trackTrain-22188.json")));
    const track = await trackTrain("22188", "2026-07-07");
    expect(requestedUrl()).toBe(
      "https://railkit-api.rajivdubey.dev/api/trackTrain/22188/07-07-2026",
    );
    expect(track.coachPositionTimeline).toHaveLength(2); // rake reversal captured
    expect(track.timeline.length).toBeGreaterThan(track.totalStations);
  });

  it("rejects a DD-MM-YYYY date (upstream format must never cross the boundary)", async () => {
    await expectRailkitError(trackTrain("22188", "07-07-2026"), "INVALID_INPUT");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("getTrainHistory", () => {
  it("returns the persisted record for a completed journey", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("trainHistory-22188.json")));
    const history = await getTrainHistory("22188", "2026-07-07");
    expect(history.stations).toHaveLength(12);
    expect(history.destinationStationCode).toBe("RKMP");
  });

  it("maps upstream 404 to NOT_YET_AVAILABLE, never NOT_FOUND", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("trainHistory-notfound.json"), 404));
    await expectRailkitError(getTrainHistory("22188", "2026-07-08"), "NOT_YET_AVAILABLE");
  });
});

describe("liveAtStation", () => {
  it("passes the hrs window through", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("liveAtStation-JBP.json")));
    const board = await liveAtStation("JBP", 4);
    expect(requestedUrl()).toBe("https://railkit-api.rajivdubey.dev/api/liveAtStation/JBP?hrs=4");
    expect(board.trains.length).toBe(board.totalTrains);
  });

  it("rejects an out-of-contract window", async () => {
    await expectRailkitError(liveAtStation("JBP", 3 as never), "INVALID_INPUT");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("searchTrainsBetween", () => {
  it("returns the train list", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("searchBetween-JBP.json")));
    const trains = await searchTrainsBetween("JBP", "NU");
    expect(requestedUrl()).toBe(
      "https://railkit-api.rajivdubey.dev/api/searchTrainBetweenStations/JBP/NU",
    );
    expect(trains.length).toBeGreaterThan(0);
    expect(trains[0]?.train_no).toMatch(/^\d{5}$/);
  });

  it("maps upstream 'Route not found' to NOT_FOUND", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ success: false, error: "Route not found" }, 404),
    );
    await expectRailkitError(searchTrainsBetween("JBP", "XXXX"), "NOT_FOUND");
  });
});

describe("getAvailability", () => {
  it("orders path segments train/from/to/date/coach/quota", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("getAvailability-22188.json")));
    const avail = await getAvailability("22188", "JBP", "RKMP", "2026-07-15", "CC", "GN");
    expect(requestedUrl()).toBe(
      "https://railkit-api.rajivdubey.dev/api/getAvailability/22188/JBP/RKMP/15-07-2026/CC/GN",
    );
    expect(avail.availability[0]?.canBook).toBe(true);
  });
});

describe("fareLookup", () => {
  it("orders path segments train/date/from/to/class/quota (date second)", async () => {
    fetchMock.mockResolvedValue(jsonResponse(fixture("fareLookup-22188.json")));
    const fare = await fareLookup("22188", "2026-07-15", "JBP", "RKMP", "CC", "GN");
    expect(requestedUrl()).toBe(
      "https://railkit-api.rajivdubey.dev/api/fareLookup/22188/15-07-2026/JBP/RKMP/CC/GN",
    );
    expect(fare.totalFare).toBeGreaterThan(fare.baseFare);
  });
});

describe("checkPnrStatus", () => {
  it("rejects a malformed PNR without calling upstream", async () => {
    await expectRailkitError(checkPnrStatus("12345"), "INVALID_INPUT");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("maps upstream 400 (unknown PNR) to NOT_FOUND", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ success: false, error: "No PNR data found or invalid PNR number" }, 400),
    );
    await expectRailkitError(checkPnrStatus("1234567890"), "NOT_FOUND");
  });
});

describe("redaction (invariants 1 & 2)", () => {
  it("log lines never contain the raw PNR or the API key", async () => {
    const infoSpy = vi.spyOn(console, "info").mockImplementation(() => {});
    fetchMock.mockResolvedValue(jsonResponse({ success: true, data: {} }));
    await checkPnrStatus("8524132882");

    expect(infoSpy).toHaveBeenCalled();
    const logged = infoSpy.mock.calls.flat().join("\n");
    expect(logged).not.toContain("8524132882");
    expect(logged).not.toContain(TEST_KEY);
    expect(logged).toContain("••••2882");
  });

  it("error messages never contain the API key", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ success: false, error: `boom ${TEST_KEY}` }, 500));
    vi.spyOn(console, "info").mockImplementation(() => {});
    const err = await expectRailkitError(getTrainInfo("22188"), "UPSTREAM_DOWN");
    expect(err.message).not.toContain(TEST_KEY);
    expect(err.retryable).toBe(true);
  });
});

describe("upstream failure handling", () => {
  it("network errors become retryable UPSTREAM_DOWN", async () => {
    vi.spyOn(console, "info").mockImplementation(() => {});
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const err = await expectRailkitError(getTrainInfo("22188"), "UPSTREAM_DOWN");
    expect(err.retryable).toBe(true);
  });

  it("429 becomes RATE_LIMITED", async () => {
    vi.spyOn(console, "info").mockImplementation(() => {});
    fetchMock.mockResolvedValue(jsonResponse({ success: false, error: "slow down" }, 429));
    await expectRailkitError(getTrainInfo("22188"), "RATE_LIMITED");
  });

  it("missing RAILKIT_API_KEY fails closed without calling upstream", async () => {
    delete process.env.RAILKIT_API_KEY;
    await expectRailkitError(getTrainInfo("22188"), "UPSTREAM_DOWN");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

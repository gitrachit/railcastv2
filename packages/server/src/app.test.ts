import { describe, expect, it } from "vitest";
import { buildApp } from "./app.js";

describe("GET /health", () => {
  it("returns the Ok envelope from contracts §0/§9", async () => {
    const app = buildApp();
    const res = await app.inject({ method: "GET", url: "/health" });

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.ok).toBe(true);
    expect(typeof body.data.uptimeS).toBe("number");
    expect(body.data.upstream).toBe("ok");
    expect(body.meta.stale).toBe(false);
    expect(typeof body.meta.fetchedAt).toBe("string");
    expect(typeof body.meta.ttlSeconds).toBe("number");
  });
});

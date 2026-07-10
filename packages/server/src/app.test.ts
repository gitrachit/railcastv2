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

describe("GET /privacy", () => {
  it("serves the public privacy page (no auth) with the required disclosures", async () => {
    const app = buildApp();
    const res = await app.inject({ method: "GET", url: "/privacy" });

    expect(res.statusCode).toBe(200);
    expect(res.headers["content-type"]).toContain("text/html");
    const html = res.body;
    // Key FR-4.3 / 11.1 / 11.3 disclosures must be present.
    expect(html).toContain("encrypted at rest");
    expect(html).toContain("automatically deleted");
    expect(html).toMatch(/not affiliated with Indian Railways/i);
    expect(html).toContain("turn them off"); // analytics opt-out
    // The policy page must never carry a real PNR.
    expect(html).not.toMatch(/\b\d{10}\b/);
  });
});

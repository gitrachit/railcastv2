import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { buildApp } from "../app.js";

beforeEach(() => {
  process.env.AUTH_TOKEN_SECRET = "test-secret";
});

afterEach(() => {
  delete process.env.AUTH_TOKEN_SECRET;
});

async function mintToken(app: ReturnType<typeof buildApp>): Promise<string> {
  const res = await app.inject({
    method: "POST",
    url: "/auth/device",
    payload: { platform: "android", appVersion: "0.1.0" },
  });
  return res.json().data.deviceToken as string;
}

describe("POST /auth/device (contracts §7)", () => {
  it("issues an anonymous token in the Ok envelope", async () => {
    const app = buildApp();
    const res = await app.inject({
      method: "POST",
      url: "/auth/device",
      payload: { platform: "android", appVersion: "0.1.0" },
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.ok).toBe(true);
    expect(body.data.deviceToken).toMatch(/^rcd1\.[0-9a-f]{32}\./);
  });

  it("rejects a malformed body with INVALID_INPUT", async () => {
    const app = buildApp();
    const res = await app.inject({
      method: "POST",
      url: "/auth/device",
      payload: { platform: "ios" },
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().error.code).toBe("INVALID_INPUT");
  });

  it("rate limits token minting per IP", async () => {
    const app = buildApp({ auth: { authLimitPerMin: 2 } });
    const payload = { platform: "android", appVersion: "0.1.0" } as const;
    await app.inject({ method: "POST", url: "/auth/device", payload });
    await app.inject({ method: "POST", url: "/auth/device", payload });
    const third = await app.inject({ method: "POST", url: "/auth/device", payload });
    expect(third.statusCode).toBe(429);
    expect(third.json().error).toMatchObject({ code: "RATE_LIMITED", retryable: true });
  });
});

describe("Bearer middleware", () => {
  it("keeps /health public (FR-10.5)", async () => {
    const app = buildApp();
    const res = await app.inject({ method: "GET", url: "/health" });
    expect(res.statusCode).toBe(200);
  });

  it("rejects protected routes without a token", async () => {
    const app = buildApp();
    app.get("/protected", async () => ({ ok: true }));
    const res = await app.inject({ method: "GET", url: "/protected" });
    expect(res.statusCode).toBe(401);
    expect(res.json().error.code).toBe("UNAUTHORIZED");
  });

  it("rejects a forged token", async () => {
    const app = buildApp();
    app.get("/protected", async () => ({ ok: true }));
    const res = await app.inject({
      method: "GET",
      url: "/protected",
      headers: { authorization: "Bearer rcd1.deadbeefdeadbeefdeadbeefdeadbeef.forged" },
    });
    expect(res.statusCode).toBe(401);
  });

  it("passes a valid token through and exposes deviceId", async () => {
    const app = buildApp();
    app.get("/protected", async (req) => ({ deviceId: req.deviceId }));
    const token = await mintToken(app);
    const res = await app.inject({
      method: "GET",
      url: "/protected",
      headers: { authorization: `Bearer ${token}` },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().deviceId).toMatch(/^[0-9a-f]{32}$/);
  });

  it("rate limits per device", async () => {
    const app = buildApp({ auth: { deviceLimitPerMin: 3 } });
    app.get("/protected", async () => ({}));
    const token = await mintToken(app);
    const headers = { authorization: `Bearer ${token}` };

    for (let i = 0; i < 3; i += 1) {
      const okRes = await app.inject({ method: "GET", url: "/protected", headers });
      expect(okRes.statusCode).toBe(200);
    }
    const limited = await app.inject({ method: "GET", url: "/protected", headers });
    expect(limited.statusCode).toBe(429);
    expect(limited.json().error).toMatchObject({ code: "RATE_LIMITED", retryable: true });
  });
});

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { issueDeviceToken, verifyDeviceToken } from "./tokens.js";

beforeEach(() => {
  process.env.AUTH_TOKEN_SECRET = "test-secret";
});

afterEach(() => {
  delete process.env.AUTH_TOKEN_SECRET;
});

describe("device tokens", () => {
  it("round-trips issue → verify", () => {
    const { deviceId, token } = issueDeviceToken();
    expect(verifyDeviceToken(token)).toBe(deviceId);
  });

  it("rejects tampered device ids", () => {
    const { token } = issueDeviceToken();
    const [v, id, sig] = token.split(".");
    const flipped = id!.startsWith("a") ? `b${id!.slice(1)}` : `a${id!.slice(1)}`;
    expect(verifyDeviceToken(`${v}.${flipped}.${sig}`)).toBeNull();
  });

  it("rejects garbage and wrong versions", () => {
    expect(verifyDeviceToken("")).toBeNull();
    expect(verifyDeviceToken("Bearer nonsense")).toBeNull();
    expect(verifyDeviceToken("rcd9.aaaa.bbbb")).toBeNull();
  });

  it("tokens signed with a different secret do not verify", () => {
    const { token } = issueDeviceToken();
    process.env.AUTH_TOKEN_SECRET = "rotated-secret";
    expect(verifyDeviceToken(token)).toBeNull();
  });

  it("fails closed when the secret is missing", () => {
    delete process.env.AUTH_TOKEN_SECRET;
    expect(() => issueDeviceToken()).toThrowError(/AUTH_TOKEN_SECRET/);
  });
});

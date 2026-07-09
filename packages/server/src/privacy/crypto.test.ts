import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { decryptPnrBlob, encryptPnrBlob, pnrCacheKey } from "./crypto.js";

const TEST_KEY = "a".repeat(64);

beforeEach(() => {
  process.env.PNR_ENCRYPTION_KEY = TEST_KEY;
});

afterEach(() => {
  delete process.env.PNR_ENCRYPTION_KEY;
});

describe("PNR encryption at rest (FR-4.3)", () => {
  it("round-trips and never contains the plaintext", () => {
    const blob = encryptPnrBlob('{"pnr":"8524132882"}');
    expect(blob).not.toContain("8524132882");
    expect(decryptPnrBlob(blob)).toBe('{"pnr":"8524132882"}');
  });

  it("uses a fresh IV per encryption", () => {
    expect(encryptPnrBlob("x")).not.toBe(encryptPnrBlob("x"));
  });

  it("rejects tampered ciphertext (GCM auth)", () => {
    const blob = encryptPnrBlob("secret");
    const parts = blob.split(".");
    const c = parts[2]!;
    parts[2] = (c.startsWith("A") ? "B" : "A") + c.slice(1);
    expect(() => decryptPnrBlob(parts.join("."))).toThrow();
  });

  it("fails closed without a well-formed key", () => {
    process.env.PNR_ENCRYPTION_KEY = "tooshort";
    expect(() => encryptPnrBlob("x")).toThrowError(/PNR_ENCRYPTION_KEY/);
  });

  it("derives deterministic cache keys that hide the PNR", () => {
    const k = pnrCacheKey("8524132882");
    expect(k).toBe(pnrCacheKey("8524132882"));
    expect(k).not.toContain("8524132882");
    expect(k).not.toContain("2882");
    expect(k).toMatch(/^rk:pnr:[0-9a-f]{32}$/);
    expect(pnrCacheKey("8524132883")).not.toBe(k);
  });
});

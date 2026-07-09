import { createHmac } from "node:crypto";
import { afterEach, describe, expect, it } from "vitest";
import { sdkSignatureHeaders } from "./signature.js";

afterEach(() => {
  delete process.env.RAILKIT_SDK_SIGNING_SECRET;
});

describe("sdkSignatureHeaders", () => {
  it("produces a verifiable HMAC over method/path/ts/nonce/payload/key", () => {
    const secret = "test-secret";
    process.env.RAILKIT_SDK_SIGNING_SECRET = secret;
    const h = sdkSignatureHeaders("GET", "/api/liveAtStation/JBP?hrs=4", "key-123");

    expect(h["x-irctc-sdk-version"]).toBe("1");
    expect(h["x-irctc-sdk-nonce"]).toMatch(/^[0-9a-f]{64}$/);
    // sha256("") for a bodyless GET
    expect(h["x-irctc-sdk-payload-sha256"]).toBe(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    );

    const expected = createHmac("sha256", secret)
      .update(
        [
          "GET",
          "/api/liveAtStation/JBP?hrs=4",
          h["x-irctc-sdk-ts"],
          h["x-irctc-sdk-nonce"],
          h["x-irctc-sdk-payload-sha256"],
          "key-123",
        ].join("\n"),
      )
      .digest("hex");
    expect(h["x-irctc-sdk-signature"]).toBe(expected);
  });

  it("uses a fresh nonce and timestamp each call", () => {
    const a = sdkSignatureHeaders("GET", "/api/x", "k");
    const b = sdkSignatureHeaders("GET", "/api/x", "k");
    expect(a["x-irctc-sdk-nonce"]).not.toBe(b["x-irctc-sdk-nonce"]);
    expect(a["x-irctc-sdk-signature"]).not.toBe(b["x-irctc-sdk-signature"]);
  });
});

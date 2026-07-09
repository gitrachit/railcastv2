// RailKit began requiring per-request SDK signature headers (the same scheme
// the official `railkit` npm client sends). We call the REST API directly
// rather than depend on that obfuscated package, so we reproduce the headers:
//   HMAC-sha256(signingSecret, [METHOD, path+query, ts, nonce, sha256(body), apiKey].join("\n"))
// The default signing secret is a client-side constant (not a per-account
// credential); it's overridable via env if the upstream rotates it.
import { createHash, createHmac, randomBytes } from "node:crypto";

const DEFAULT_SIGNING_SECRET =
  "97c56e08b27b161124f88acd4f24d1bd50f48075f11dc23b9ea6c0bc9b2f8794";
const SDK_VERSION = "1";

function signingSecret(): string {
  return process.env.RAILKIT_SDK_SIGNING_SECRET ?? DEFAULT_SIGNING_SECRET;
}

/**
 * @param signedPath path plus query string, exactly as it appears after the
 *        host (e.g. "/api/liveAtStation/JBP?hrs=4").
 */
export function sdkSignatureHeaders(
  method: string,
  signedPath: string,
  apiKey: string,
  body = "",
): Record<string, string> {
  const ts = String(Date.now());
  const nonce = randomBytes(32).toString("hex");
  const payloadHash = createHash("sha256").update(body).digest("hex");
  const signature = createHmac("sha256", signingSecret())
    .update([method.toUpperCase(), signedPath, ts, nonce, payloadHash, apiKey].join("\n"))
    .digest("hex");

  return {
    "x-irctc-sdk-ts": ts,
    "x-irctc-sdk-nonce": nonce,
    "x-irctc-sdk-payload-sha256": payloadHash,
    "x-irctc-sdk-signature": signature,
    "x-irctc-sdk-version": SDK_VERSION,
  };
}

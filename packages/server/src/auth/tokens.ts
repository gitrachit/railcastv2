// Anonymous device identity (contracts §7, FR-10.5): a random device id
// HMAC-signed with a server secret. Stateless — nothing stored per device,
// nothing personal to leak. Token format: rcd1.<deviceId>.<signature>
import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

const VERSION = "rcd1";

function secret(): string {
  const s = process.env.AUTH_TOKEN_SECRET;
  if (!s) throw new Error("AUTH_TOKEN_SECRET is not configured");
  return s;
}

function sign(deviceId: string): string {
  return createHmac("sha256", secret()).update(deviceId).digest("base64url");
}

export function issueDeviceToken(): { deviceId: string; token: string } {
  const deviceId = randomBytes(16).toString("hex");
  return { deviceId, token: `${VERSION}.${deviceId}.${sign(deviceId)}` };
}

/** Returns the deviceId for a valid token, null otherwise. */
export function verifyDeviceToken(token: string): string | null {
  const parts = token.split(".");
  if (parts.length !== 3 || parts[0] !== VERSION) return null;
  const [, deviceId, signature] = parts;
  if (!deviceId || !signature || !/^[0-9a-f]{32}$/.test(deviceId)) return null;
  const expected = Buffer.from(sign(deviceId));
  const provided = Buffer.from(signature);
  if (expected.length !== provided.length) return null;
  return timingSafeEqual(expected, provided) ? deviceId : null;
}

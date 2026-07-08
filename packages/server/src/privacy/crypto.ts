// PNR data at rest (FR-4.3): AES-256-GCM blobs + HMAC-derived cache keys, so
// neither a raw PNR nor readable PNR data ever sits in Redis. Key from env
// only; 64 hex chars (32 bytes). This module and mask.ts are the only places
// that handle PNR secrecy.
import { createCipheriv, createDecipheriv, createHmac, randomBytes } from "node:crypto";

const VERSION = "penc1";

function key(): Buffer {
  const hex = process.env.PNR_ENCRYPTION_KEY;
  if (!hex || !/^[0-9a-f]{64}$/i.test(hex)) {
    throw new Error("PNR_ENCRYPTION_KEY must be 64 hex characters (32 bytes)");
  }
  return Buffer.from(hex, "hex");
}

export function encryptPnrBlob(plaintext: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  return [
    VERSION,
    iv.toString("base64url"),
    ciphertext.toString("base64url"),
    cipher.getAuthTag().toString("base64url"),
  ].join(".");
}

export function decryptPnrBlob(blob: string): string {
  const [version, iv, ciphertext, tag] = blob.split(".");
  if (version !== VERSION || !iv || !ciphertext || !tag) {
    throw new Error("malformed encrypted PNR blob");
  }
  const decipher = createDecipheriv("aes-256-gcm", key(), Buffer.from(iv, "base64url"));
  decipher.setAuthTag(Buffer.from(tag, "base64url"));
  return Buffer.concat([
    decipher.update(Buffer.from(ciphertext, "base64url")),
    decipher.final(),
  ]).toString("utf8");
}

/** Deterministic cache key that never exposes the PNR. */
export function pnrCacheKey(pnr: string): string {
  const digest = createHmac("sha256", key()).update(`pnr-cache-key:${pnr}`).digest("hex");
  return `rk:pnr:${digest.slice(0, 32)}`;
}

/** Watch entity dedup key (watch.entity_key) — same PNR, same key, never the PNR. */
export function pnrEntityKey(pnr: string): string {
  const digest = createHmac("sha256", key()).update(`pnr-entity-key:${pnr}`).digest("hex");
  return `pnr:${digest.slice(0, 32)}`;
}

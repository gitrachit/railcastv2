// Response envelope helpers (contracts §0). Every endpoint replies Ok<T> | Err.
import type { Err, ErrorCode, Meta, Ok } from "@railcast/shared";

export function ok<T>(data: T, meta: Meta): Ok<T> {
  return { ok: true, data, meta };
}

/** Meta for server-generated (non-upstream) data like tokens. */
export function immediateMeta(ttlSeconds = 0): Meta {
  return { fetchedAt: new Date().toISOString(), stale: false, ttlSeconds };
}

export function err(code: ErrorCode, message: string, retryable: boolean): Err {
  return { ok: false, error: { code, message, retryable } };
}

export const HTTP_STATUS: Record<ErrorCode, number> = {
  INVALID_INPUT: 400,
  UNAUTHORIZED: 401,
  NOT_FOUND: 404,
  NOT_YET_AVAILABLE: 404,
  RATE_LIMITED: 429,
  UPSTREAM_DOWN: 503,
};

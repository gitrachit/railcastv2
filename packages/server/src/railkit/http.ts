import type { ErrorCode } from "@railcast/shared";
import { railkitApiKey, railkitBaseUrl } from "./config.js";
import { RailkitError } from "./errors.js";

const TIMEOUT_MS = 10_000;

interface GetOptions {
  /** Path for log lines — callers mask sensitive segments (PNRs) before passing. */
  logPath?: string;
  /** What an upstream 404 means for this endpoint (trainHistory 404 = journey not finished). */
  notFoundCode?: "NOT_FOUND" | "NOT_YET_AVAILABLE";
}

type UpstreamBody =
  | { success: true; data: unknown }
  | { success: false; error?: string }
  | null;

export async function upstreamGet<T>(path: string, opts: GetOptions = {}): Promise<T> {
  const key = railkitApiKey();
  // Log lines carry the (pre-masked) path only — never headers, never the key.
  const logPath = (opts.logPath ?? path).replaceAll(key, "[redacted]");
  const startedAt = Date.now();

  let res: Response;
  try {
    res = await fetch(railkitBaseUrl() + path, {
      headers: { "x-api-key": key, accept: "application/json" },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch {
    console.info(`railkit GET ${logPath} -> network_error ${Date.now() - startedAt}ms`);
    throw new RailkitError("UPSTREAM_DOWN", `upstream unreachable: GET ${logPath}`, true);
  }

  console.info(`railkit GET ${logPath} -> ${res.status} ${Date.now() - startedAt}ms`);

  const body = (await res.json().catch(() => null)) as UpstreamBody;
  if (res.ok && body?.success) return body.data as T;

  const detail = (body && !body.success && body.error) || `HTTP ${res.status}`;
  throw new RailkitError(
    mapStatus(res.status, opts.notFoundCode ?? "NOT_FOUND"),
    `GET ${logPath}: ${detail.replaceAll(key, "[redacted]")}`,
    res.status === 429 || res.status >= 500,
    res.status,
  );
}

function mapStatus(status: number, notFoundCode: "NOT_FOUND" | "NOT_YET_AVAILABLE"): ErrorCode {
  if (status === 404) return notFoundCode;
  // Inputs are validated before every call, so an upstream 400 means the
  // entity doesn't exist (e.g. unknown PNR), not that we sent garbage.
  if (status === 400) return "NOT_FOUND";
  if (status === 429) return "RATE_LIMITED";
  return "UPSTREAM_DOWN";
}

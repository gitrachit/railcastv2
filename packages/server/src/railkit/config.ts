import { RailkitError } from "./errors.js";

export function railkitBaseUrl(): string {
  return process.env.RAILKIT_BASE_URL ?? "https://railkit-api.rajivdubey.dev";
}

// Key comes from env/secrets only (root CLAUDE.md invariant 1). Read at call
// time, never cached into module state, never logged.
export function railkitApiKey(): string {
  const key = process.env.RAILKIT_API_KEY;
  if (!key) {
    throw new RailkitError("UPSTREAM_DOWN", "RAILKIT_API_KEY is not configured", false);
  }
  return key;
}
